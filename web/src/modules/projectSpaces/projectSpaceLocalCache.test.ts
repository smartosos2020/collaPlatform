import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

import {
  LEGACY_PROJECT_SPACE_RECENT_KEY,
  hasRecoverableLegacyDraft,
  projectSpaceCacheKey,
  readPinnedProjectSpaceIds,
  readProjectSpaceDraft,
  readRecentProjectSpaceIds,
  recoverLegacyProjectSpaceDraft,
  rememberRecentProjectSpace,
  removeProjectSpaceDraft,
  setProjectSpacePinned,
  writeProjectSpaceDraft,
  type ProjectSpaceStorage,
} from './projectSpaceLocalCache.ts'

class MemoryStorage implements ProjectSpaceStorage {
  readonly values = new Map<string, string>()
  failWrites = false

  getItem(key: string) {
    return this.values.get(key) ?? null
  }

  setItem(key: string, value: string) {
    if (this.failWrites) throw new Error('quota')
    this.values.set(key, value)
  }

  removeItem(key: string) {
    this.values.delete(key)
  }
}

const userA = { workspaceId: 'workspace-1', userId: 'user-a' }
const userB = { workspaceId: 'workspace-1', userId: 'user-b' }
const draftScope = { ...userA, spaceId: 'space-1' }
const record = (value: unknown): value is Record<string, unknown> =>
  Boolean(value) && typeof value === 'object' && !Array.isArray(value)

describe('project space recent cache', () => {
  it('migrates only currently accessible legacy ids and keeps the legacy source', () => {
    const storage = new MemoryStorage()
    storage.setItem(LEGACY_PROJECT_SPACE_RECENT_KEY, JSON.stringify(['space-2', 'private-space', 'space-1']))

    assert.deepEqual(
      readRecentProjectSpaceIds(storage, userA, ['space-1', 'space-2'], 1_000),
      ['space-2', 'space-1'],
    )
    assert.notEqual(storage.getItem(LEGACY_PROJECT_SPACE_RECENT_KEY), null)
    assert.equal(storage.getItem(projectSpaceCacheKey(userA, 'recent')), null)
    assert.equal(
      rememberRecentProjectSpace(
        storage,
        userA,
        'space-2',
        ['space-1', 'space-2'],
        1_000,
      ),
      true,
    )
    assert.notEqual(storage.getItem(projectSpaceCacheKey(userA, 'recent')), null)
  })

  it('isolates users and safely falls back for corrupt or expired values', () => {
    const storage = new MemoryStorage()
    storage.setItem(projectSpaceCacheKey(userA, 'recent'), '{broken')
    storage.setItem(projectSpaceCacheKey(userB, 'recent'), JSON.stringify({
      schemaVersion: 2,
      createdAt: 1,
      updatedAt: 1,
      expiresAt: 2,
      value: ['space-2'],
    }))

    assert.deepEqual(readRecentProjectSpaceIds(storage, userA, ['space-1'], 10), [])
    assert.deepEqual(readRecentProjectSpaceIds(storage, userB, ['space-2'], 10), [])
  })

  it('keeps bounded recency and treats quota failure as a safe no-op', () => {
    const storage = new MemoryStorage()
    assert.equal(
      rememberRecentProjectSpace(storage, userA, 'space-2', ['space-1', 'space-2'], 1_000),
      true,
    )
    storage.failWrites = true
    assert.equal(
      rememberRecentProjectSpace(storage, userA, 'space-1', ['space-1', 'space-2'], 2_000),
      false,
    )
  })

  it('does not resurrect retained legacy ids after the scoped cache expires', () => {
    const storage = new MemoryStorage()
    storage.setItem(
      LEGACY_PROJECT_SPACE_RECENT_KEY,
      JSON.stringify(['space-2', 'space-1']),
    )
    assert.equal(
      rememberRecentProjectSpace(
        storage,
        userA,
        'space-1',
        ['space-1', 'space-2'],
        1_000,
      ),
      true,
    )
    const key = projectSpaceCacheKey(userA, 'recent')
    const envelope = JSON.parse(storage.getItem(key) as string) as {
      expiresAt: number
    }
    storage.setItem(key, JSON.stringify({ ...envelope, expiresAt: 1_500 }))

    assert.deepEqual(
      readRecentProjectSpaceIds(
        storage,
        userA,
        ['space-1', 'space-2'],
        2_000,
      ),
      [],
    )
  })
})

describe('project space pinned cache', () => {
  it('pins explicitly, keeps the newest pin first, and unpins without affecting access order', () => {
    const storage = new MemoryStorage()
    const accessible = ['space-1', 'space-2', 'space-3']

    assert.equal(setProjectSpacePinned(storage, userA, 'space-1', true, accessible, 1_000), true)
    assert.equal(setProjectSpacePinned(storage, userA, 'space-2', true, accessible, 2_000), true)
    assert.deepEqual(readPinnedProjectSpaceIds(storage, userA, accessible, 3_000), ['space-2', 'space-1'])

    assert.equal(setProjectSpacePinned(storage, userA, 'space-2', false, accessible, 4_000), true)
    assert.deepEqual(readPinnedProjectSpaceIds(storage, userA, accessible, 5_000), ['space-1'])
  })

  it('isolates pinned spaces by user and drops spaces that are no longer accessible', () => {
    const storage = new MemoryStorage()
    assert.equal(setProjectSpacePinned(storage, userA, 'space-1', true, ['space-1'], 1_000), true)

    assert.deepEqual(readPinnedProjectSpaceIds(storage, userB, ['space-1'], 2_000), [])
    assert.deepEqual(readPinnedProjectSpaceIds(storage, userA, [], 2_000), [])
  })
})

describe('project space draft cache', () => {
  it('round-trips only the scoped versioned value', () => {
    const storage = new MemoryStorage()
    assert.equal(writeProjectSpaceDraft(
      storage,
      draftScope,
      'metric-semantics-draft',
      { name: 'draft' },
      1_000,
    ), true)
    assert.deepEqual(
      readProjectSpaceDraft(storage, draftScope, 'metric-semantics-draft', record, 2_000),
      { name: 'draft' },
    )
    assert.equal(
      readProjectSpaceDraft(
        storage,
        { ...draftScope, userId: 'user-b' },
        'metric-semantics-draft',
        record,
        2_000,
      ),
      undefined,
    )
  })

  it('rejects ownerless or cross-user legacy drafts and only recovers a matching owner envelope', () => {
    const storage = new MemoryStorage()
    const legacyKey = 'colla.metric-draft.space-1'
    storage.setItem(legacyKey, JSON.stringify({ name: 'legacy draft' }))

    assert.equal(
      hasRecoverableLegacyDraft(
        storage,
        draftScope,
        'metric-semantics-draft',
        legacyKey,
        record,
      ),
      false,
    )
    assert.equal(
      recoverLegacyProjectSpaceDraft(
        storage,
        draftScope,
        'metric-semantics-draft',
        legacyKey,
        record,
        1_000,
      ),
      undefined,
    )
    assert.notEqual(storage.getItem(legacyKey), null)

    storage.setItem(legacyKey, JSON.stringify({
      schemaVersion: 1,
      kind: 'metric-semantics-draft',
      scope: {
        workspaceId: draftScope.workspaceId,
        userId: draftScope.userId,
        spaceId: draftScope.spaceId,
      },
      value: { name: 'owned legacy draft' },
    }))
    assert.equal(
      hasRecoverableLegacyDraft(
        storage,
        { ...draftScope, userId: 'user-b' },
        'metric-semantics-draft',
        legacyKey,
        record,
      ),
      false,
    )
    assert.equal(
      hasRecoverableLegacyDraft(
        storage,
        draftScope,
        'metric-semantics-draft',
        legacyKey,
        record,
      ),
      true,
    )
    assert.deepEqual(
      recoverLegacyProjectSpaceDraft(
        storage,
        draftScope,
        'metric-semantics-draft',
        legacyKey,
        record,
        1_000,
      ),
      { name: 'owned legacy draft' },
    )
    removeProjectSpaceDraft(storage, draftScope, 'metric-semantics-draft')
    assert.notEqual(storage.getItem(legacyKey), null)
    assert.equal(
      hasRecoverableLegacyDraft(
        storage,
        draftScope,
        'metric-semantics-draft',
        legacyKey,
        record,
      ),
      false,
    )
  })
})
