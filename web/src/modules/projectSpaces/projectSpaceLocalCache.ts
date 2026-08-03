export const LEGACY_PROJECT_SPACE_RECENT_KEY = 'colla.project-spaces.recent'

export type ProjectSpaceCacheScope = Readonly<{
  workspaceId: string
  userId: string
  spaceId?: string
}>

export type ProjectSpaceDraftKind =
  | 'metric-semantics-draft'
  | 'metric-risk-policy-draft'
  | 'metric-dashboard-draft'

export type ProjectSpaceStorage = Pick<
  Storage,
  'getItem' | 'setItem' | 'removeItem'
>

type CacheEnvelope<T> = Readonly<{
  schemaVersion: 2
  createdAt: number
  updatedAt: number
  expiresAt: number | null
  value: T
}>

type OwnedLegacyDraftEnvelope<T> = Readonly<{
  schemaVersion: 1
  kind: ProjectSpaceDraftKind
  scope: Readonly<{
    workspaceId: string
    userId: string
    spaceId: string
  }>
  value: T
}>

type CacheRead<T> =
  | Readonly<{ state: 'ok'; value: T }>
  | Readonly<{ state: 'missing' | 'invalid' | 'expired' }>

const CACHE_SCHEMA_VERSION = 2
const RECENT_TTL_MS = 30 * 24 * 60 * 60 * 1_000
const MAX_RECENT_SPACES = 8
const MAX_PINNED_SPACES = 50
const MAX_STORAGE_VALUE_LENGTH = 128 * 1024
const SAFE_ID = /^[a-zA-Z0-9][a-zA-Z0-9_-]{0,127}$/

export function projectSpaceCacheKey(
  scope: ProjectSpaceCacheScope,
  kind: 'recent' | 'pinned' | ProjectSpaceDraftKind,
): string {
  const space = scope.spaceId ? `.${encodeScope(scope.spaceId)}` : ''
  return `colla.project-space-ui.v2.${encodeScope(scope.workspaceId)}.${encodeScope(scope.userId)}${space}.${kind}`
}

export function readPinnedProjectSpaceIds(
  storage: ProjectSpaceStorage,
  scope: Omit<ProjectSpaceCacheScope, 'spaceId'>,
  accessibleSpaceIds: readonly string[],
  now = Date.now(),
): string[] {
  const accessible = new Set(accessibleSpaceIds.filter(isSafeId))
  const result = readCache(storage, projectSpaceCacheKey(scope, 'pinned'), isRecentIds, now)
  return result.state === 'ok'
    ? result.value.filter((id) => accessible.has(id)).slice(0, MAX_PINNED_SPACES)
    : []
}

export function setProjectSpacePinned(
  storage: ProjectSpaceStorage,
  scope: Omit<ProjectSpaceCacheScope, 'spaceId'>,
  spaceId: string,
  pinned: boolean,
  accessibleSpaceIds: readonly string[],
  now = Date.now(),
): boolean {
  if (!isSafeId(spaceId) || !accessibleSpaceIds.includes(spaceId)) return false
  const current = readPinnedProjectSpaceIds(storage, scope, accessibleSpaceIds, now)
  const next = pinned
    ? [spaceId, ...current.filter((id) => id !== spaceId)].slice(0, MAX_PINNED_SPACES)
    : current.filter((id) => id !== spaceId)
  return writeCache(storage, projectSpaceCacheKey(scope, 'pinned'), next, now, null)
}

export function readRecentProjectSpaceIds(
  storage: ProjectSpaceStorage,
  scope: Omit<ProjectSpaceCacheScope, 'spaceId'>,
  accessibleSpaceIds: readonly string[],
  now = Date.now(),
): string[] {
  const accessible = new Set(accessibleSpaceIds.filter(isSafeId))
  const key = projectSpaceCacheKey(scope, 'recent')
  const scoped = readCache(storage, key, isRecentIds, now)
  if (scoped.state === 'ok') {
    return scoped.value.filter((id) => accessible.has(id)).slice(0, MAX_RECENT_SPACES)
  }
  if (safeGet(storage, recentMigrationKey(scope)) !== null) return []

  const legacy = readLegacyValue(storage, LEGACY_PROJECT_SPACE_RECENT_KEY, isRecentIds)
  if (!legacy) return []
  return legacy.filter((id) => accessible.has(id)).slice(0, MAX_RECENT_SPACES)
}

export function rememberRecentProjectSpace(
  storage: ProjectSpaceStorage,
  scope: Omit<ProjectSpaceCacheScope, 'spaceId'>,
  spaceId: string,
  accessibleSpaceIds: readonly string[],
  now = Date.now(),
): boolean {
  if (!isSafeId(spaceId) || !accessibleSpaceIds.includes(spaceId)) return false
  const current = readRecentProjectSpaceIds(storage, scope, accessibleSpaceIds, now)
  const next = [spaceId, ...current.filter((id) => id !== spaceId)].slice(0, MAX_RECENT_SPACES)
  const written = writeCache(
    storage,
    projectSpaceCacheKey(scope, 'recent'),
    next,
    now,
    now + RECENT_TTL_MS,
  )
  if (!written) return false
  return safeSet(storage, recentMigrationKey(scope), String(now))
}

export function readProjectSpaceDraft<T>(
  storage: ProjectSpaceStorage,
  scope: ProjectSpaceCacheScope,
  kind: ProjectSpaceDraftKind,
  validate: (value: unknown) => value is T,
  now = Date.now(),
): T | undefined {
  const result = readCache(storage, projectSpaceCacheKey(scope, kind), validate, now)
  return result.state === 'ok' ? result.value : undefined
}

export function writeProjectSpaceDraft<T>(
  storage: ProjectSpaceStorage,
  scope: ProjectSpaceCacheScope,
  kind: ProjectSpaceDraftKind,
  value: T,
  now = Date.now(),
): boolean {
  return writeCache(storage, projectSpaceCacheKey(scope, kind), value, now, null)
}

export function removeProjectSpaceDraft(
  storage: ProjectSpaceStorage,
  scope: ProjectSpaceCacheScope,
  kind: ProjectSpaceDraftKind,
): void {
  safeRemove(storage, projectSpaceCacheKey(scope, kind))
}

export function hasRecoverableLegacyDraft<T>(
  storage: ProjectSpaceStorage,
  scope: ProjectSpaceCacheScope,
  kind: ProjectSpaceDraftKind,
  legacyKey: string,
  validate: (value: unknown) => value is T,
): boolean {
  if (safeGet(storage, legacyHandledKey(scope, kind)) !== null) return false
  return readOwnedLegacyDraft(
    storage,
    scope,
    kind,
    legacyKey,
    validate,
  ) !== undefined
}

export function recoverLegacyProjectSpaceDraft<T>(
  storage: ProjectSpaceStorage,
  scope: ProjectSpaceCacheScope,
  kind: ProjectSpaceDraftKind,
  legacyKey: string,
  validate: (value: unknown) => value is T,
  now = Date.now(),
): T | undefined {
  if (safeGet(storage, legacyHandledKey(scope, kind)) !== null) return undefined
  const legacy = readOwnedLegacyDraft(
    storage,
    scope,
    kind,
    legacyKey,
    validate,
  )
  if (legacy === undefined) return undefined
  if (!writeProjectSpaceDraft(storage, scope, kind, legacy, now)) return undefined
  markLegacyDraftHandled(storage, scope, kind, now)
  return legacy
}

export function markLegacyDraftHandled(
  storage: ProjectSpaceStorage,
  scope: ProjectSpaceCacheScope,
  kind: ProjectSpaceDraftKind,
  now = Date.now(),
): void {
  safeSet(storage, legacyHandledKey(scope, kind), String(now))
}

export function isProjectSpaceDraftRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value)
    && typeof value === 'object'
    && !Array.isArray(value)
    && Object.getPrototypeOf(value) === Object.prototype
}

function readCache<T>(
  storage: ProjectSpaceStorage,
  key: string,
  validate: (value: unknown) => value is T,
  now: number,
): CacheRead<T> {
  const raw = safeGet(storage, key)
  if (raw === null) return { state: 'missing' }
  const parsed = safeParse(raw)
  if (!isEnvelope(parsed) || !validate(parsed.value)) return { state: 'invalid' }
  if (parsed.expiresAt !== null && parsed.expiresAt <= now) return { state: 'expired' }
  return { state: 'ok', value: parsed.value }
}

function writeCache<T>(
  storage: ProjectSpaceStorage,
  key: string,
  value: T,
  now: number,
  expiresAt: number | null,
): boolean {
  const existing = safeParse(safeGet(storage, key))
  const createdAt = isEnvelope(existing) ? existing.createdAt : now
  const envelope: CacheEnvelope<T> = {
    schemaVersion: CACHE_SCHEMA_VERSION,
    createdAt,
    updatedAt: now,
    expiresAt,
    value,
  }
  return safeSet(storage, key, JSON.stringify(envelope))
}

function readLegacyValue<T>(
  storage: ProjectSpaceStorage,
  key: string,
  validate: (value: unknown) => value is T,
): T | undefined {
  const parsed = safeParse(safeGet(storage, key))
  return validate(parsed) ? parsed : undefined
}

function readOwnedLegacyDraft<T>(
  storage: ProjectSpaceStorage,
  scope: ProjectSpaceCacheScope,
  kind: ProjectSpaceDraftKind,
  legacyKey: string,
  validate: (value: unknown) => value is T,
): T | undefined {
  if (!scope.spaceId) return undefined
  const parsed = safeParse(safeGet(storage, legacyKey))
  if (!isProjectSpaceDraftRecord(parsed) || parsed.schemaVersion !== 1) {
    return undefined
  }
  if (parsed.kind !== kind || !isProjectSpaceDraftRecord(parsed.scope)) {
    return undefined
  }
  const legacyScope = parsed.scope
  if (
    legacyScope.workspaceId !== scope.workspaceId
    || legacyScope.userId !== scope.userId
    || legacyScope.spaceId !== scope.spaceId
  ) {
    return undefined
  }
  return validate(parsed.value)
    ? (parsed as OwnedLegacyDraftEnvelope<T>).value
    : undefined
}

function isEnvelope(value: unknown): value is CacheEnvelope<unknown> {
  if (!isProjectSpaceDraftRecord(value)) return false
  return value.schemaVersion === CACHE_SCHEMA_VERSION
    && typeof value.createdAt === 'number'
    && Number.isFinite(value.createdAt)
    && typeof value.updatedAt === 'number'
    && Number.isFinite(value.updatedAt)
    && (value.expiresAt === null
      || (typeof value.expiresAt === 'number' && Number.isFinite(value.expiresAt)))
    && Object.hasOwn(value, 'value')
}

function isRecentIds(value: unknown): value is string[] {
  return Array.isArray(value)
    && value.length <= 50
    && value.every(isSafeId)
}

function isSafeId(value: unknown): value is string {
  return typeof value === 'string' && SAFE_ID.test(value)
}

function safeParse(raw: string | null): unknown {
  if (raw === null || raw.length === 0 || raw.length > MAX_STORAGE_VALUE_LENGTH) return undefined
  try {
    return JSON.parse(raw)
  } catch {
    return undefined
  }
}

function safeGet(storage: ProjectSpaceStorage, key: string): string | null {
  try {
    return storage.getItem(key)
  } catch {
    return null
  }
}

function safeSet(storage: ProjectSpaceStorage, key: string, value: string): boolean {
  try {
    storage.setItem(key, value)
    return true
  } catch {
    return false
  }
}

function safeRemove(storage: ProjectSpaceStorage, key: string): void {
  try {
    storage.removeItem(key)
  } catch {
    // Storage access can be denied; the draft remains recoverable.
  }
}

function legacyHandledKey(
  scope: ProjectSpaceCacheScope,
  kind: ProjectSpaceDraftKind,
): string {
  return `${projectSpaceCacheKey(scope, kind)}.legacy-handled`
}

function recentMigrationKey(
  scope: Omit<ProjectSpaceCacheScope, 'spaceId'>,
): string {
  return `${projectSpaceCacheKey(scope, 'recent')}.legacy-migrated`
}

function encodeScope(value: string): string {
  return encodeURIComponent(value.trim())
}
