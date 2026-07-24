export const REALTIME_ENVELOPE_VERSION = 1
export const REALTIME_SIGNAL_VERSION = 1
export const REALTIME_MAX_FRAME_BYTES = 16 * 1024

export const KNOWN_REALTIME_TYPES = [
  'notification.created',
  'notification.read',
  'notification.unread.changed',
  'message.created',
  'message.edited',
  'message.revoked',
  'message.pinned',
  'message.unpinned',
  'message.reaction.toggled',
  'conversation.updated',
  'conversation.read',
  'unread.changed',
  'project.changed',
  'project.invalidated',
  'issue.changed',
  'issue.invalidated',
  'project_space.changed',
  'project_space.invalidated',
  'permission.invalidated',
  'identity.invalidated',
] as const

export type KnownRealtimeType = (typeof KNOWN_REALTIME_TYPES)[number]
export type RealtimeAudienceType = 'user' | 'workspace'
export type RealtimeSequenceScope = 'object' | 'audience'

export type RealtimeEnvelope<TPayload extends Record<string, unknown> = Record<string, unknown>> = {
  envelopeVersion: 1
  type: KnownRealtimeType
  signalVersion: 1
  eventId: string
  serverTime: string
  occurredAt: string
  workspaceId: string
  audienceType: RealtimeAudienceType
  recipientId: string | null
  objectType: string
  objectId: string
  sequenceScope: RealtimeSequenceScope
  sequenceKey: string
  sequence: number
  correlationId: string
  calibrationPath: string
  payload: TPayload
}

export type RealtimeReadyFrame = {
  type: 'connection.ready'
  instanceId: string
}

export type RealtimeFrameRejection =
  | 'non-text-frame'
  | 'oversized-frame'
  | 'malformed-json'
  | 'invalid-structure'
  | 'unknown-envelope-version'
  | 'unknown-signal-version'
  | 'unknown-type'
  | 'workspace-mismatch'
  | 'recipient-mismatch'

export type RealtimeFrameParseResult =
  | { kind: 'control'; frame: RealtimeReadyFrame }
  | { kind: 'envelope'; envelope: RealtimeEnvelope }
  | { kind: 'legacy'; type: KnownRealtimeType | null }
  | { kind: 'rejected'; reason: RealtimeFrameRejection; type?: string }

export type RealtimeParserOptions = {
  expectedWorkspaceId?: string | null
  expectedUserId?: string | null
  maxFrameBytes?: number
}

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
const IDENTIFIER_PATTERN = /^[a-z][a-z0-9._-]{1,95}$/
const INSTANT_PATTERN = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})$/
const knownRealtimeTypes = new Set<string>(KNOWN_REALTIME_TYPES)
const sensitivePayloadKeys = new Set([
  'token',
  'password',
  'secret',
  'title',
  'body',
  'content',
  'acl',
  'members',
  'authorization',
])

export function parseRealtimeFrame(
  rawFrame: unknown,
  options: RealtimeParserOptions = {},
): RealtimeFrameParseResult {
  if (typeof rawFrame !== 'string') {
    return { kind: 'rejected', reason: 'non-text-frame' }
  }
  const maxFrameBytes = options.maxFrameBytes ?? REALTIME_MAX_FRAME_BYTES
  if (new TextEncoder().encode(rawFrame).byteLength > maxFrameBytes) {
    return { kind: 'rejected', reason: 'oversized-frame' }
  }

  let value: unknown
  try {
    value = JSON.parse(rawFrame)
  } catch {
    return { kind: 'rejected', reason: 'malformed-json' }
  }
  if (!isRecord(value)) {
    return { kind: 'rejected', reason: 'invalid-structure' }
  }

  if (value.type === 'connection.ready') {
    return Object.keys(value).length === 2 &&
      typeof value.instanceId === 'string' &&
      value.instanceId.length > 0 &&
      value.instanceId.length <= 128
      ? { kind: 'control', frame: { type: 'connection.ready', instanceId: value.instanceId } }
      : { kind: 'rejected', reason: 'invalid-structure', type: 'connection.ready' }
  }

  const type = typeof value.type === 'string' ? value.type : undefined
  if (value.envelopeVersion === 0) {
    return { kind: 'legacy', type: type && knownRealtimeTypes.has(type) ? type as KnownRealtimeType : null }
  }
  if (value.envelopeVersion !== REALTIME_ENVELOPE_VERSION) {
    return { kind: 'rejected', reason: 'unknown-envelope-version', type }
  }
  if (value.signalVersion !== REALTIME_SIGNAL_VERSION) {
    return { kind: 'rejected', reason: 'unknown-signal-version', type }
  }
  if (!type || !knownRealtimeTypes.has(type)) {
    return { kind: 'rejected', reason: 'unknown-type', type }
  }
  if (!isUuid(value.workspaceId) || !matchesExpected(value.workspaceId, options.expectedWorkspaceId)) {
    return { kind: 'rejected', reason: 'workspace-mismatch', type }
  }

  const audienceType = value.audienceType
  const recipientId = value.recipientId
  if (audienceType !== 'user' && audienceType !== 'workspace') {
    return { kind: 'rejected', reason: 'invalid-structure', type }
  }
  if (
    (audienceType === 'user' && (!isUuid(recipientId) || !matchesExpected(recipientId, options.expectedUserId))) ||
    (audienceType === 'workspace' && recipientId !== null)
  ) {
    return {
      kind: 'rejected',
      reason: audienceType === 'user' ? 'recipient-mismatch' : 'invalid-structure',
      type,
    }
  }

  if (
    !isUuid(value.eventId) ||
    !isIsoInstant(value.serverTime) ||
    !isIsoInstant(value.occurredAt) ||
    !isIdentifier(value.objectType) ||
    !isUuid(value.objectId) ||
    (value.sequenceScope !== 'object' && value.sequenceScope !== 'audience') ||
    typeof value.sequenceKey !== 'string' ||
    value.sequenceKey.length === 0 ||
    value.sequenceKey.length > 192 ||
    !Number.isSafeInteger(value.sequence) ||
    (value.sequence as number) < 0 ||
    !isUuid(value.correlationId) ||
    !isCalibrationPath(value.calibrationPath) ||
    !isSafePayload(value.payload, 0)
  ) {
    return { kind: 'rejected', reason: 'invalid-structure', type }
  }

  return {
    kind: 'envelope',
    envelope: {
      envelopeVersion: 1,
      type: type as KnownRealtimeType,
      signalVersion: 1,
      eventId: value.eventId,
      serverTime: value.serverTime,
      occurredAt: value.occurredAt,
      workspaceId: value.workspaceId,
      audienceType,
      recipientId: audienceType === 'user' ? recipientId as string : null,
      objectType: value.objectType,
      objectId: value.objectId,
      sequenceScope: value.sequenceScope,
      sequenceKey: value.sequenceKey,
      sequence: value.sequence as number,
      correlationId: value.correlationId,
      calibrationPath: value.calibrationPath,
      payload: value.payload,
    },
  }
}

function matchesExpected(value: string, expected: string | null | undefined) {
  return expected == null || value === expected
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isUuid(value: unknown): value is string {
  return typeof value === 'string' && UUID_PATTERN.test(value)
}

function isIdentifier(value: unknown): value is string {
  return typeof value === 'string' && IDENTIFIER_PATTERN.test(value)
}

function isIsoInstant(value: unknown): value is string {
  return typeof value === 'string' &&
    value.length <= 64 &&
    INSTANT_PATTERN.test(value) &&
    Number.isFinite(Date.parse(value))
}

function isCalibrationPath(value: unknown): value is string {
  return typeof value === 'string' &&
    value.startsWith('/api/') &&
    value.length <= 1024 &&
    !value.includes('://')
}

function isSafePayload(value: unknown, depth: number): value is Record<string, unknown> {
  if (!isRecord(value) || depth > 3 || Object.keys(value).length > 32) {
    return false
  }
  return Object.entries(value).every(([key, item]) => {
    if (!/^[a-z][A-Za-z0-9._-]{0,63}$/.test(key) || sensitivePayloadKeys.has(key.toLowerCase())) {
      return false
    }
    return isSafePayloadValue(item, depth + 1)
  })
}

function isSafePayloadValue(value: unknown, depth: number): boolean {
  if (value === null || typeof value === 'boolean' || typeof value === 'number') {
    return true
  }
  if (typeof value === 'string') {
    return value.length <= 1024
  }
  if (Array.isArray(value)) {
    return depth <= 3 && value.length <= 32 && value.every((item) => isSafePayloadValue(item, depth + 1))
  }
  return isSafePayload(value, depth)
}
