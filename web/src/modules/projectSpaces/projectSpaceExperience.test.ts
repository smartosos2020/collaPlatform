import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

import {
  PROJECT_SPACE_EXPERIENCE_BASELINE,
  canRecordProjectSpaceExperience,
  effectiveProjectSpaceExperienceRollout,
  normalizeProjectSpaceExperienceRollout,
  projectSpaceExperienceFreshness,
  projectSpaceExperiencePreferenceQueryKey,
  projectSpaceExperienceQueryKey,
  projectSpaceExperienceRouteKey,
  sanitizeProjectSpaceExperienceEvents,
} from './projectSpaceExperience.ts'

const enabledRollout = {
  schemaVersion: 1,
  policyVersion: 'policy-7',
  enabled: true,
  state: 'enabled',
  fallbackContext: 'canonical_project_space',
  evaluatedAt: '2026-07-30T00:00:00.000Z',
  cacheMaxAgeSeconds: 30,
  telemetry: {
    schemaVersion: 1,
    enabled: true,
    sampleBasisPoints: 2_500,
    maxBatchSize: 10,
  },
} as const

describe('project space rollout contract', () => {
  it('accepts the frozen DTO and forces non-enabled states to baseline presentation', () => {
    assert.deepEqual(normalizeProjectSpaceExperienceRollout(enabledRollout), enabledRollout)
    assert.equal(normalizeProjectSpaceExperienceRollout({
      ...enabledRollout,
      state: 'temporarily_disabled',
    }).enabled, false)
  })

  it('fails closed for malformed or future-shaped decisions', () => {
    assert.deepEqual(
      normalizeProjectSpaceExperienceRollout({
        ...enabledRollout,
        schemaVersion: 2,
        telemetry: { ...enabledRollout.telemetry, sampleBasisPoints: 20_000 },
      }),
      PROJECT_SPACE_EXPERIENCE_BASELINE,
    )
  })

  it('makes decision freshness explicit', () => {
    assert.equal(
      projectSpaceExperienceFreshness(enabledRollout, Date.parse('2026-07-30T00:00:20.000Z')),
      'fresh',
    )
    assert.equal(
      projectSpaceExperienceFreshness(enabledRollout, Date.parse('2026-07-30T00:01:00.000Z')),
      'stale',
    )
    assert.equal(projectSpaceExperienceFreshness(PROJECT_SPACE_EXPERIENCE_BASELINE), 'unknown')
  })

  it('caps freshness at the local receive-time TTL when the server clock is ahead', () => {
    const futureServerDecision = {
      ...enabledRollout,
      evaluatedAt: '2026-07-30T00:10:00.000Z',
    }
    const receivedAt = Date.parse('2026-07-30T00:00:00.000Z')
    assert.equal(
      projectSpaceExperienceFreshness(
        futureServerDecision,
        Date.parse('2026-07-30T00:00:31.000Z'),
        receivedAt,
      ),
      'stale',
    )
    assert.deepEqual(
      effectiveProjectSpaceExperienceRollout(futureServerDecision, {
        now: Date.parse('2026-07-30T00:00:31.000Z'),
        receivedAt,
      }),
      PROJECT_SPACE_EXPERIENCE_BASELINE,
    )
  })

  it('fails the effective UI closed after TTL or any refresh failure', () => {
    assert.equal(
      effectiveProjectSpaceExperienceRollout(enabledRollout, {
        now: Date.parse('2026-07-30T00:00:20.000Z'),
      }).enabled,
      true,
    )
    assert.deepEqual(
      effectiveProjectSpaceExperienceRollout(enabledRollout, {
        now: Date.parse('2026-07-30T00:01:00.000Z'),
      }),
      PROJECT_SPACE_EXPERIENCE_BASELINE,
    )
    assert.deepEqual(
      effectiveProjectSpaceExperienceRollout(enabledRollout, {
        now: Date.parse('2026-07-30T00:00:20.000Z'),
        requestFailed: true,
      }),
      PROJECT_SPACE_EXPERIENCE_BASELINE,
    )
    assert.deepEqual(
      effectiveProjectSpaceExperienceRollout(
        { ...enabledRollout, cacheMaxAgeSeconds: 0 },
        { now: Date.parse(enabledRollout.evaluatedAt) },
      ),
      PROJECT_SPACE_EXPERIENCE_BASELINE,
    )
    const kill = effectiveProjectSpaceExperienceRollout(
      {
        ...enabledRollout,
        enabled: false,
        state: 'temporarily_disabled',
        cacheMaxAgeSeconds: 0,
      },
      { now: Date.parse(enabledRollout.evaluatedAt) },
    )
    assert.equal(kill.state, 'temporarily_disabled')
    assert.equal(kill.enabled, false)
    assert.equal(kill.telemetry.enabled, false)
  })

  it('scopes cached decisions and opt-out state by workspace, user and space', () => {
    assert.notDeepEqual(
      projectSpaceExperienceQueryKey('workspace-1', 'user-a', 'space-1', 'experience-rollout'),
      projectSpaceExperienceQueryKey('workspace-1', 'user-b', 'space-1', 'experience-rollout'),
    )
    assert.deepEqual(
      projectSpaceExperienceQueryKey('workspace-1', 'user-a', 'space-1', 'onboarding'),
      ['project-space-experience', 'workspace-1', 'user-a', 'space-1', 'onboarding'],
    )
    assert.notDeepEqual(
      projectSpaceExperiencePreferenceQueryKey('workspace-1', 'user-a', 'space-1'),
      projectSpaceExperiencePreferenceQueryKey('workspace-1', 'user-b', 'space-1'),
    )
    assert.deepEqual(
      projectSpaceExperiencePreferenceQueryKey('workspace-1', 'user-a', 'space-1'),
      [
        'project-space-experience',
        'workspace-1',
        'user-a',
        'space-1',
        'experience-preference',
      ],
    )
  })
})

describe('low-sensitivity experience telemetry', () => {
  it('maps paths to bounded route keys without retaining ids or query content', () => {
    assert.equal(projectSpaceExperienceRouteKey('/project-spaces/space-1'), 'overview')
    assert.equal(
      projectSpaceExperienceRouteKey('/project-spaces/secret-space/work-items/private-item'),
      'work_items',
    )
    assert.equal(projectSpaceExperienceRouteKey('/project-spaces/space-1/types/type-1'), 'advanced_configuration')
    assert.equal(projectSpaceExperienceRouteKey('/search?q=secret'), 'unknown')
  })

  it('drops invalid events and respects the server batch limit', () => {
    const valid = {
      eventId: '123e4567-e89b-42d3-a456-426614174000',
      eventKind: 'entry',
      routeKey: 'overview',
      mode: 'simple',
      outcome: 'shown',
      durationBucket: 'unknown',
      errorCode: 'none',
      freshness: 'fresh',
    } as const
    const invalid = { ...valid, routeKey: '/project-spaces/secret' as 'overview' }
    assert.deepEqual(sanitizeProjectSpaceExperienceEvents([invalid, valid, valid], 1), [valid])
    const sensitive = {
      ...valid,
      title: 'must-not-leave-browser',
      spaceId: 'space-secret',
    } as typeof valid
    const [sanitized] = sanitizeProjectSpaceExperienceEvents([sensitive], 1)
    assert.equal(Object.hasOwn(sanitized, 'title'), false)
    assert.equal(Object.hasOwn(sanitized, 'spaceId'), false)
  })

  it('fails closed before POST while offline, opted out, or disabled', () => {
    assert.equal(canRecordProjectSpaceExperience({
      online: true,
      optOut: false,
      telemetryEnabled: true,
    }), true)
    assert.equal(canRecordProjectSpaceExperience({
      online: false,
      optOut: false,
      telemetryEnabled: true,
    }), false)
    assert.equal(canRecordProjectSpaceExperience({
      online: true,
      optOut: true,
      telemetryEnabled: true,
    }), false)
  })
})
