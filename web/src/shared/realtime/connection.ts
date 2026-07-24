import {
  parseRealtimeFrame,
  type KnownRealtimeType,
  type RealtimeEnvelope,
  type RealtimeFrameParseResult,
} from './protocol'
import { RealtimeSequencer, type RealtimeSequenceDecision } from './sequencer'

export type RealtimeConnectionStatus =
  | 'stopped'
  | 'connecting'
  | 'ready'
  | 'degraded'
  | 'reconnecting'

export type RealtimeConnectionContext = {
  accessToken: string
  workspaceId: string
  userId: string
  url: string
}

export type RealtimeSocket = {
  onopen: ((event: unknown) => void) | null
  onmessage: ((event: { data: unknown }) => void) | null
  onclose: ((event: { code?: number; reason?: string }) => void) | null
  onerror: ((event: unknown) => void) | null
  close: (code?: number, reason?: string) => void
}

export type RealtimeScheduler = {
  setTimeout: (callback: () => void, delayMs: number) => unknown
  clearTimeout: (timer: unknown) => void
}

export type RealtimeOnlineSource = {
  isOnline: () => boolean
  subscribe: (onOnline: () => void, onOffline: () => void) => () => void
}

export type RealtimeCalibrationReason =
  | 'initial-ready'
  | 'reconnected'
  | 'gap'
  | 'legacy'
  | 'protocol'

export type RealtimeCalibrationRequest = {
  reason: RealtimeCalibrationReason
  type: KnownRealtimeType | null
  envelope?: RealtimeEnvelope
  decision?: RealtimeSequenceDecision
}

export type RealtimeDiagnosticsSnapshot = Readonly<{
  status: RealtimeConnectionStatus
  generation: number
  reconnectAttempt: number
  instanceId: string | null
  acceptedEvents: number
  duplicateEvents: number
  staleEvents: number
  gapEvents: number
  rejectedFrames: number
  legacyFrames: number
  calibrationRequests: number
  lastEventType: KnownRealtimeType | null
  lastSequence: number | null
  lastSequenceDecision: RealtimeSequenceDecision['kind'] | null
  lastCalibrationReason: RealtimeCalibrationReason | null
  eventIdCount: number
  watermarkCount: number
}>

export type RealtimeConnectionOptions = {
  socketFactory: (url: string) => RealtimeSocket
  scheduler: RealtimeScheduler
  onlineSource: RealtimeOnlineSource
  random?: () => number
  readyTimeoutMs?: number
  reconnectBaseMs?: number
  reconnectMaxMs?: number
  reconnectJitterMs?: number
  parseFrame?: (raw: unknown, context: RealtimeConnectionContext) => RealtimeFrameParseResult
  sequencer?: RealtimeSequencer
}

type StatusListener = (status: RealtimeConnectionStatus) => void
type EnvelopeListener = (envelope: RealtimeEnvelope) => void
type CalibrationListener = (request: RealtimeCalibrationRequest) => void
type DiagnosticsListener = (snapshot: RealtimeDiagnosticsSnapshot) => void

export class RealtimeConnection {
  private readonly socketFactory: RealtimeConnectionOptions['socketFactory']
  private readonly scheduler: RealtimeScheduler
  private readonly onlineSource: RealtimeOnlineSource
  private readonly random: () => number
  private readonly readyTimeoutMs: number
  private readonly reconnectBaseMs: number
  private readonly reconnectMaxMs: number
  private readonly reconnectJitterMs: number
  private readonly frameParser: NonNullable<RealtimeConnectionOptions['parseFrame']>
  private readonly sequencer: RealtimeSequencer
  private readonly statusListeners = new Set<StatusListener>()
  private readonly envelopeListeners = new Set<EnvelopeListener>()
  private readonly calibrationListeners = new Set<CalibrationListener>()
  private readonly diagnosticsListeners = new Set<DiagnosticsListener>()
  private readonly calibrationKeys = new Map<string, true>()
  private context: RealtimeConnectionContext | null = null
  private contextKey: string | null = null
  private socket: RealtimeSocket | null = null
  private reconnectTimer: unknown = null
  private readyTimer: unknown = null
  private unsubscribeOnline: (() => void) | null = null
  private status: RealtimeConnectionStatus = 'stopped'
  private generation = 0
  private reconnectAttempt = 0
  private hasEverReady = false
  private socketReady = false
  private started = false
  private instanceId: string | null = null
  private acceptedEvents = 0
  private duplicateEvents = 0
  private staleEvents = 0
  private gapEvents = 0
  private rejectedFrames = 0
  private legacyFrames = 0
  private calibrationRequests = 0
  private lastEventType: KnownRealtimeType | null = null
  private lastSequence: number | null = null
  private lastSequenceDecision: RealtimeSequenceDecision['kind'] | null = null
  private lastCalibrationReason: RealtimeCalibrationReason | null = null

  constructor(options: RealtimeConnectionOptions) {
    this.socketFactory = options.socketFactory
    this.scheduler = options.scheduler
    this.onlineSource = options.onlineSource
    this.random = options.random ?? Math.random
    this.readyTimeoutMs = positiveNumber(options.readyTimeoutMs, 10_000)
    this.reconnectBaseMs = positiveNumber(options.reconnectBaseMs, 1_000)
    this.reconnectMaxMs = positiveNumber(options.reconnectMaxMs, 15_000)
    this.reconnectJitterMs = nonNegativeNumber(options.reconnectJitterMs, 300)
    this.frameParser = options.parseFrame ?? ((raw, context) => parseRealtimeFrame(raw, {
      expectedWorkspaceId: context.workspaceId,
      expectedUserId: context.userId,
    }))
    this.sequencer = options.sequencer ?? new RealtimeSequencer()
  }

  getStatus() {
    return this.status
  }

  getDiagnostics(): RealtimeDiagnosticsSnapshot {
    const sequencer = this.sequencer.snapshot()
    return Object.freeze({
      status: this.status,
      generation: this.generation,
      reconnectAttempt: this.reconnectAttempt,
      instanceId: this.instanceId,
      acceptedEvents: this.acceptedEvents,
      duplicateEvents: this.duplicateEvents,
      staleEvents: this.staleEvents,
      gapEvents: this.gapEvents,
      rejectedFrames: this.rejectedFrames,
      legacyFrames: this.legacyFrames,
      calibrationRequests: this.calibrationRequests,
      lastEventType: this.lastEventType,
      lastSequence: this.lastSequence,
      lastSequenceDecision: this.lastSequenceDecision,
      lastCalibrationReason: this.lastCalibrationReason,
      eventIdCount: sequencer.eventIds,
      watermarkCount: sequencer.watermarks,
    })
  }

  start(context: RealtimeConnectionContext) {
    const nextContextKey = contextIdentity(context)
    if (this.started && nextContextKey === this.contextKey) {
      return
    }
    this.stopConnection(false)
    if (nextContextKey !== this.contextKey) {
      this.sequencer.reset()
      this.calibrationKeys.clear()
      this.hasEverReady = false
      this.resetDiagnostics()
    }
    this.context = context
    this.contextKey = nextContextKey
    this.started = true
    this.reconnectAttempt = 0
    this.unsubscribeOnline = this.onlineSource.subscribe(
      () => this.handleOnline(),
      () => this.handleOffline(),
    )
    if (this.onlineSource.isOnline()) {
      this.connect()
    } else {
      this.setStatus('degraded')
    }
  }

  stop() {
    this.stopConnection(true)
  }

  retry() {
    if (!this.started || !this.context) {
      return
    }
    this.reconnectAttempt = 0
    this.clearReconnectTimer()
    this.invalidateSocket()
    if (this.onlineSource.isOnline()) {
      this.connect()
    } else {
      this.setStatus('degraded')
    }
  }

  markCalibrated() {
    if (this.started && this.socket && this.socketReady && this.status === 'degraded') {
      this.setStatus('ready')
    }
  }

  subscribeStatus(listener: StatusListener) {
    this.statusListeners.add(listener)
    listener(this.status)
    return () => {
      this.statusListeners.delete(listener)
    }
  }

  subscribe(listener: EnvelopeListener) {
    this.envelopeListeners.add(listener)
    return () => {
      this.envelopeListeners.delete(listener)
    }
  }

  subscribeCalibration(listener: CalibrationListener) {
    this.calibrationListeners.add(listener)
    return () => {
      this.calibrationListeners.delete(listener)
    }
  }

  subscribeDiagnostics(listener: DiagnosticsListener) {
    this.diagnosticsListeners.add(listener)
    listener(this.getDiagnostics())
    return () => {
      this.diagnosticsListeners.delete(listener)
    }
  }

  private connect() {
    if (!this.started || !this.context || !this.onlineSource.isOnline()) {
      return
    }
    this.clearReconnectTimer()
    const generation = ++this.generation
    this.setStatus(this.hasEverReady || this.reconnectAttempt > 0 ? 'reconnecting' : 'connecting')
    let socket: RealtimeSocket
    try {
      socket = this.socketFactory(withAccessToken(this.context.url, this.context.accessToken))
    } catch {
      this.scheduleReconnect()
      return
    }
    this.socket = socket
    this.socketReady = false
    socket.onopen = () => {
      if (!this.isCurrent(socket, generation)) return
      this.clearReadyTimer()
      this.readyTimer = this.scheduler.setTimeout(
        () => this.handleReadyTimeout(socket, generation),
        this.readyTimeoutMs,
      )
    }
    socket.onmessage = (event) => {
      if (this.isCurrent(socket, generation)) {
        this.handleMessage(event.data)
      }
    }
    socket.onerror = () => {
      if (this.isCurrent(socket, generation)) {
        this.failSocket(socket)
      }
    }
    socket.onclose = () => {
      if (!this.isCurrent(socket, generation)) return
      this.socket = null
      this.clearReadyTimer()
      if (!this.started) {
        this.setStatus('stopped')
      } else if (!this.onlineSource.isOnline()) {
        this.setStatus('degraded')
      } else {
        this.scheduleReconnect()
      }
    }
  }

  private handleMessage(raw: unknown) {
    if (!this.context) return
    const parsed = this.frameParser(raw, this.context)
    if (parsed.kind === 'control') {
      this.clearReadyTimer()
      this.socketReady = true
      this.instanceId = parsed.frame.instanceId
      const calibrationReason: RealtimeCalibrationReason = this.hasEverReady ? 'reconnected' : 'initial-ready'
      this.hasEverReady = true
      this.reconnectAttempt = 0
      this.setStatus('ready')
      this.emitCalibration(
        { reason: calibrationReason, type: null },
        `${calibrationReason}:${this.generation}`,
      )
      return
    }
    if (parsed.kind === 'legacy') {
      this.legacyFrames += 1
      this.notifyDiagnostics()
      if (parsed.type) {
        this.setStatus('degraded')
        this.emitCalibration({ reason: 'legacy', type: parsed.type }, `legacy:${parsed.type}`)
      }
      return
    }
    if (parsed.kind === 'rejected') {
      this.rejectedFrames += 1
      this.notifyDiagnostics()
      if (['unknown-envelope-version', 'unknown-signal-version', 'unknown-type'].includes(parsed.reason)) {
        this.setStatus('degraded')
        this.emitCalibration(
          { reason: 'protocol', type: null },
          `protocol:${parsed.reason}:${parsed.type ?? 'unknown'}`,
        )
      }
      return
    }
    if (this.status !== 'ready' && this.status !== 'degraded') {
      this.setStatus('degraded')
      this.emitCalibration({ reason: 'protocol', type: parsed.envelope.type }, 'protocol:before-ready')
      return
    }

    const decision = this.sequencer.inspect(parsed.envelope)
    this.lastEventType = parsed.envelope.type
    this.lastSequence = parsed.envelope.sequence
    this.lastSequenceDecision = decision.kind
    if (decision.kind === 'duplicate' || decision.kind === 'stale') {
      if (decision.kind === 'duplicate') this.duplicateEvents += 1
      if (decision.kind === 'stale') this.staleEvents += 1
      this.notifyDiagnostics()
      return
    }
    if (decision.kind === 'gap') {
      this.gapEvents += 1
      this.setStatus('degraded')
      this.emitCalibration(
        { reason: 'gap', type: parsed.envelope.type, envelope: parsed.envelope, decision },
        `gap:${parsed.envelope.workspaceId}:${parsed.envelope.sequenceScope}:${parsed.envelope.sequenceKey}:${parsed.envelope.sequence}`,
      )
    } else {
      this.acceptedEvents += 1
    }
    this.notifyDiagnostics()
    for (const listener of this.envelopeListeners) {
      listener(parsed.envelope)
    }
  }

  private handleReadyTimeout(socket: RealtimeSocket, generation: number) {
    if (!this.isCurrent(socket, generation)) return
    this.setStatus('degraded')
    this.emitCalibration({ reason: 'protocol', type: null }, 'protocol:ready-timeout')
    this.failSocket(socket)
  }

  private handleOffline() {
    if (!this.started) return
    this.clearReconnectTimer()
    this.invalidateSocket()
    this.setStatus('degraded')
  }

  private handleOnline() {
    if (!this.started || this.socket || this.reconnectTimer !== null) return
    this.connect()
  }

  private failSocket(socket: RealtimeSocket) {
    if (socket !== this.socket) return
    this.socket = null
    this.clearReadyTimer()
    detachSocket(socket)
    safeClose(socket)
    if (this.started && this.onlineSource.isOnline()) {
      this.scheduleReconnect()
    } else if (this.started) {
      this.setStatus('degraded')
    }
  }

  private scheduleReconnect() {
    if (!this.started || this.reconnectTimer !== null || !this.onlineSource.isOnline()) {
      return
    }
    this.reconnectAttempt += 1
    this.setStatus('reconnecting')
    const exponent = Math.max(0, this.reconnectAttempt - 1)
    const base = Math.min(this.reconnectMaxMs, this.reconnectBaseMs * 2 ** exponent)
    const jitter = Math.floor(this.random() * this.reconnectJitterMs)
    this.reconnectTimer = this.scheduler.setTimeout(() => {
      this.reconnectTimer = null
      this.connect()
    }, base + jitter)
  }

  private stopConnection(clearContext: boolean) {
    this.started = false
    this.generation += 1
    this.clearReconnectTimer()
    this.clearReadyTimer()
    this.unsubscribeOnline?.()
    this.unsubscribeOnline = null
    this.invalidateSocket()
    if (clearContext) {
      this.context = null
      this.contextKey = null
      this.reconnectAttempt = 0
      this.sequencer.reset()
      this.calibrationKeys.clear()
      this.hasEverReady = false
      this.resetDiagnostics()
    }
    this.setStatus('stopped')
  }

  private invalidateSocket() {
    const socket = this.socket
    this.socket = null
    this.socketReady = false
    this.generation += 1
    if (socket) {
      detachSocket(socket)
      safeClose(socket)
    }
  }

  private isCurrent(socket: RealtimeSocket, generation: number) {
    return this.started && this.socket === socket && this.generation === generation
  }

  private clearReconnectTimer() {
    if (this.reconnectTimer !== null) {
      this.scheduler.clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  private clearReadyTimer() {
    if (this.readyTimer !== null) {
      this.scheduler.clearTimeout(this.readyTimer)
      this.readyTimer = null
    }
  }

  private setStatus(status: RealtimeConnectionStatus) {
    if (status === this.status) return
    this.status = status
    for (const listener of this.statusListeners) {
      listener(status)
    }
    this.notifyDiagnostics()
  }

  private emitCalibration(request: RealtimeCalibrationRequest, key: string) {
    if (this.calibrationKeys.has(key)) return
    this.calibrationKeys.set(key, true)
    while (this.calibrationKeys.size > 1_024) {
      const oldest = this.calibrationKeys.keys().next().value
      if (oldest === undefined) break
      this.calibrationKeys.delete(oldest)
    }
    this.calibrationRequests += 1
    this.lastCalibrationReason = request.reason
    for (const listener of this.calibrationListeners) {
      listener(request)
    }
    this.notifyDiagnostics()
  }

  private resetDiagnostics() {
    this.instanceId = null
    this.acceptedEvents = 0
    this.duplicateEvents = 0
    this.staleEvents = 0
    this.gapEvents = 0
    this.rejectedFrames = 0
    this.legacyFrames = 0
    this.calibrationRequests = 0
    this.lastEventType = null
    this.lastSequence = null
    this.lastSequenceDecision = null
    this.lastCalibrationReason = null
    this.notifyDiagnostics()
  }

  private notifyDiagnostics() {
    if (this.diagnosticsListeners.size === 0) return
    const snapshot = this.getDiagnostics()
    for (const listener of this.diagnosticsListeners) {
      listener(snapshot)
    }
  }
}

function withAccessToken(url: string, accessToken: string) {
  const parsed = new URL(url)
  parsed.searchParams.set('token', accessToken)
  return parsed.toString()
}

function contextIdentity(context: RealtimeConnectionContext) {
  return `${context.workspaceId}\u0000${context.userId}\u0000${context.accessToken}`
}

function detachSocket(socket: RealtimeSocket) {
  socket.onopen = null
  socket.onmessage = null
  socket.onclose = null
  socket.onerror = null
}

function safeClose(socket: RealtimeSocket) {
  try {
    socket.close()
  } catch {
    // A failed socket is already detached from the active generation.
  }
}

function positiveNumber(value: number | undefined, fallback: number) {
  return Number.isFinite(value) && (value as number) > 0 ? value as number : fallback
}

function nonNegativeNumber(value: number | undefined, fallback: number) {
  return Number.isFinite(value) && (value as number) >= 0 ? value as number : fallback
}
