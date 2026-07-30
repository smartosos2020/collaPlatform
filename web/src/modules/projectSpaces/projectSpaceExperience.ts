export type ProjectSpaceExperienceRolloutState =
  | 'enabled'
  | 'baseline'
  | 'temporarily_disabled'
  | 'unknown'

export type ProjectSpaceExperienceRollout = Readonly<{
  schemaVersion: 1
  policyVersion: string
  enabled: boolean
  state: ProjectSpaceExperienceRolloutState
  fallbackContext: 'canonical_project_space'
  evaluatedAt: string
  cacheMaxAgeSeconds: number
  telemetry: Readonly<{
    schemaVersion: 1
    enabled: boolean
    sampleBasisPoints: number
    maxBatchSize: number
  }>
}>

export type ProjectSpaceExperienceEventKind =
  | 'entry'
  | 'mode'
  | 'help'
  | 'task_result'
  | 'route_error'
  | 'recovery'
export type ProjectSpaceExperienceRouteKey =
  | 'overview'
  | 'work_items'
  | 'management'
  | 'members'
  | 'settings'
  | 'advanced_configuration'
  | 'notifications'
  | 'unknown'
export type ProjectSpaceExperienceEventMode =
  | 'simple'
  | 'advanced'
  | 'baseline'
  | 'unknown'
export type ProjectSpaceExperienceEventOutcome =
  | 'shown'
  | 'opened'
  | 'changed'
  | 'succeeded'
  | 'blocked'
  | 'failed'
  | 'recovered'
  | 'unknown'
export type ProjectSpaceExperienceDurationBucket =
  | 'under_5s'
  | '5_to_30s'
  | '30_to_120s'
  | '2_to_10m'
  | 'over_10m'
  | 'unknown'
export type ProjectSpaceExperienceErrorCode =
  | 'none'
  | 'not_found_or_hidden'
  | 'capability_denied'
  | 'space_read_only'
  | 'offline'
  | 'timeout'
  | 'version_conflict'
  | 'server_error'
  | 'unknown'
export type ProjectSpaceExperienceFreshness = 'fresh' | 'stale' | 'unknown'

export type ProjectSpaceExperienceTelemetryEvent = Readonly<{
  eventId: string
  eventKind: ProjectSpaceExperienceEventKind
  routeKey: ProjectSpaceExperienceRouteKey
  mode: ProjectSpaceExperienceEventMode
  outcome: ProjectSpaceExperienceEventOutcome
  durationBucket: ProjectSpaceExperienceDurationBucket
  errorCode: ProjectSpaceExperienceErrorCode
  freshness: ProjectSpaceExperienceFreshness
}>

const ROLLOUT_STATES = new Set<ProjectSpaceExperienceRolloutState>([
  'enabled',
  'baseline',
  'temporarily_disabled',
  'unknown',
])
const EVENT_KINDS = new Set<ProjectSpaceExperienceEventKind>([
  'entry',
  'mode',
  'help',
  'task_result',
  'route_error',
  'recovery',
])
const ROUTE_KEYS = new Set<ProjectSpaceExperienceRouteKey>([
  'overview',
  'work_items',
  'management',
  'members',
  'settings',
  'advanced_configuration',
  'notifications',
  'unknown',
])
const MODES = new Set<ProjectSpaceExperienceEventMode>([
  'simple',
  'advanced',
  'baseline',
  'unknown',
])
const OUTCOMES = new Set<ProjectSpaceExperienceEventOutcome>([
  'shown',
  'opened',
  'changed',
  'succeeded',
  'blocked',
  'failed',
  'recovered',
  'unknown',
])
const DURATION_BUCKETS = new Set<ProjectSpaceExperienceDurationBucket>([
  'under_5s',
  '5_to_30s',
  '30_to_120s',
  '2_to_10m',
  'over_10m',
  'unknown',
])
const ERROR_CODES = new Set<ProjectSpaceExperienceErrorCode>([
  'none',
  'not_found_or_hidden',
  'capability_denied',
  'space_read_only',
  'offline',
  'timeout',
  'version_conflict',
  'server_error',
  'unknown',
])
const FRESHNESS_VALUES = new Set<ProjectSpaceExperienceFreshness>([
  'fresh',
  'stale',
  'unknown',
])
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
let fallbackUuidSequence = 0

export const PROJECT_SPACE_EXPERIENCE_BASELINE: ProjectSpaceExperienceRollout = {
  schemaVersion: 1,
  policyVersion: 'unknown',
  enabled: false,
  state: 'unknown',
  fallbackContext: 'canonical_project_space',
  evaluatedAt: '1970-01-01T00:00:00.000Z',
  cacheMaxAgeSeconds: 0,
  telemetry: {
    schemaVersion: 1,
    enabled: false,
    sampleBasisPoints: 0,
    maxBatchSize: 1,
  },
}

export function projectSpaceExperienceQueryKey(
  workspaceId: string,
  userId: string,
  spaceId: string,
  kind: 'experience-rollout' | 'experience-preference' | 'onboarding',
) {
  return [
    'project-space-experience',
    workspaceId,
    userId,
    spaceId,
    kind,
  ] as const
}

export function projectSpaceExperiencePreferenceQueryKey(
  workspaceId: string,
  userId: string,
  spaceId: string,
) {
  return projectSpaceExperienceQueryKey(
    workspaceId,
    userId,
    spaceId,
    'experience-preference',
  )
}

export function normalizeProjectSpaceExperienceRollout(
  value: unknown,
): ProjectSpaceExperienceRollout {
  if (!isRecord(value) || !isRecord(value.telemetry)) {
    return PROJECT_SPACE_EXPERIENCE_BASELINE
  }
  const state = value.state
  const evaluatedAt = value.evaluatedAt
  const telemetry = value.telemetry
  if (
    value.schemaVersion !== 1
    || typeof value.policyVersion !== 'string'
    || value.policyVersion.length < 1
    || value.policyVersion.length > 128
    || typeof value.enabled !== 'boolean'
    || typeof state !== 'string'
    || !ROLLOUT_STATES.has(state as ProjectSpaceExperienceRolloutState)
    || value.fallbackContext !== 'canonical_project_space'
    || typeof evaluatedAt !== 'string'
    || !Number.isFinite(Date.parse(evaluatedAt))
    || !integerInRange(value.cacheMaxAgeSeconds, 0, 3_600)
    || telemetry.schemaVersion !== 1
    || typeof telemetry.enabled !== 'boolean'
    || !integerInRange(telemetry.sampleBasisPoints, 0, 10_000)
    || !integerInRange(telemetry.maxBatchSize, 1, 20)
  ) {
    return PROJECT_SPACE_EXPERIENCE_BASELINE
  }
  const normalizedState = state as ProjectSpaceExperienceRolloutState
  return {
    schemaVersion: 1,
    policyVersion: value.policyVersion,
    enabled: value.enabled && normalizedState === 'enabled',
    state: normalizedState,
    fallbackContext: 'canonical_project_space',
    evaluatedAt,
    cacheMaxAgeSeconds: value.cacheMaxAgeSeconds,
    telemetry: {
      schemaVersion: 1,
      enabled: telemetry.enabled,
      sampleBasisPoints: telemetry.sampleBasisPoints,
      maxBatchSize: telemetry.maxBatchSize,
    },
  }
}

export function projectSpaceExperienceFreshness(
  rollout: ProjectSpaceExperienceRollout,
  now = Date.now(),
  receivedAt?: number,
): ProjectSpaceExperienceFreshness {
  const evaluatedAt = Date.parse(rollout.evaluatedAt)
  if (!Number.isFinite(evaluatedAt) || rollout.policyVersion === 'unknown') return 'unknown'
  if (rollout.cacheMaxAgeSeconds <= 0) return 'stale'
  const ttlMs = rollout.cacheMaxAgeSeconds * 1_000
  const serverDeadline = evaluatedAt + ttlMs
  const clientDeadline = typeof receivedAt === 'number' && Number.isFinite(receivedAt)
    ? receivedAt + ttlMs
    : Number.POSITIVE_INFINITY
  return now < Math.min(serverDeadline, clientDeadline) ? 'fresh' : 'stale'
}

export function effectiveProjectSpaceExperienceRollout(
  value: unknown,
  options: Readonly<{
    now?: number
    receivedAt?: number
    requestFailed?: boolean
  }> = {},
): ProjectSpaceExperienceRollout {
  if (options.requestFailed) return PROJECT_SPACE_EXPERIENCE_BASELINE
  const rollout = normalizeProjectSpaceExperienceRollout(value)
  if (
    projectSpaceExperienceFreshness(
      rollout,
      options.now,
      options.receivedAt,
    ) === 'fresh'
  ) {
    return rollout
  }
  if (rollout.state === 'baseline' || rollout.state === 'temporarily_disabled') {
    return {
      ...rollout,
      enabled: false,
      telemetry: {
        ...rollout.telemetry,
        enabled: false,
      },
    }
  }
  return PROJECT_SPACE_EXPERIENCE_BASELINE
}

export function projectSpaceExperienceRouteKey(
  pathname: string,
): ProjectSpaceExperienceRouteKey {
  if (pathname === '/notifications') return 'notifications'
  if (/^\/project-spaces\/[^/]+\/work-items(?:\/[^/]+)?$/.test(pathname)) return 'work_items'
  if (/^\/project-spaces\/[^/]+\/management$/.test(pathname)) return 'management'
  if (/^\/project-spaces\/[^/]+\/members$/.test(pathname)) return 'members'
  if (/^\/project-spaces\/[^/]+\/settings$/.test(pathname)) return 'settings'
  if (/^\/project-spaces\/[^/]+\/types(?:\/.*)?$/.test(pathname)) return 'advanced_configuration'
  if (/^\/project-spaces\/[^/]+$/.test(pathname)) return 'overview'
  return 'unknown'
}

export function createProjectSpaceExperienceEvent(
  input: Omit<ProjectSpaceExperienceTelemetryEvent, 'eventId'>,
): ProjectSpaceExperienceTelemetryEvent {
  return { eventId: createUuid(), ...input }
}

export function sanitizeProjectSpaceExperienceEvents(
  events: readonly ProjectSpaceExperienceTelemetryEvent[],
  maxBatchSize: number,
): ProjectSpaceExperienceTelemetryEvent[] {
  const limit = integerInRange(maxBatchSize, 1, 20) ? maxBatchSize : 1
  return events
    .filter(isProjectSpaceExperienceEvent)
    .slice(0, limit)
    .map((event) => ({
      eventId: event.eventId,
      eventKind: event.eventKind,
      routeKey: event.routeKey,
      mode: event.mode,
      outcome: event.outcome,
      durationBucket: event.durationBucket,
      errorCode: event.errorCode,
      freshness: event.freshness,
    }))
}

export function canRecordProjectSpaceExperience(input: Readonly<{
  online: boolean
  optOut: boolean
  telemetryEnabled: boolean
}>): boolean {
  return input.online && !input.optOut && input.telemetryEnabled
}

function isProjectSpaceExperienceEvent(
  event: ProjectSpaceExperienceTelemetryEvent,
): boolean {
  return UUID.test(event.eventId)
    && EVENT_KINDS.has(event.eventKind)
    && ROUTE_KEYS.has(event.routeKey)
    && MODES.has(event.mode)
    && OUTCOMES.has(event.outcome)
    && DURATION_BUCKETS.has(event.durationBucket)
    && ERROR_CODES.has(event.errorCode)
    && FRESHNESS_VALUES.has(event.freshness)
}

function createUuid(): string {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  const bytes = new Uint8Array(16)
  if (globalThis.crypto?.getRandomValues) {
    globalThis.crypto.getRandomValues(bytes)
  } else {
    fallbackUuidSequence += 1
    const timestamp = BigInt(Date.now())
    const monotonic = BigInt(Math.floor(globalThis.performance?.now?.() ?? 0))
    const seed = timestamp ^ (monotonic << 12n) ^ BigInt(fallbackUuidSequence)
    for (let index = 0; index < bytes.length; index += 1) {
      bytes[index] = Number((seed >> BigInt((index % 8) * 8)) & 0xffn)
    }
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = [...bytes].map((byte) => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function integerInRange(value: unknown, minimum: number, maximum: number): value is number {
  return typeof value === 'number'
    && Number.isInteger(value)
    && value >= minimum
    && value <= maximum
}
