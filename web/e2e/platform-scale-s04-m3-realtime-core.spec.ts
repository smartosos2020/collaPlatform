import { expect, test } from '@playwright/test'

import {
  RealtimeConnection,
  type RealtimeOnlineSource,
  type RealtimeScheduler,
  type RealtimeSocket,
} from '../src/shared/realtime/connection'
import { parseRealtimeFrame, type RealtimeEnvelope } from '../src/shared/realtime/protocol'
import { RealtimeSequencer } from '../src/shared/realtime/sequencer'

const WORKSPACE_ID = '11111111-1111-4111-8111-111111111111'
const USER_ID = '22222222-2222-4222-8222-222222222222'

test('@smoke @client-contract parser accepts only safe v1 envelopes and the ready control frame', () => {
  const envelope = realtimeEnvelope()
  expect(parseRealtimeFrame(JSON.stringify(envelope), parserContext())).toEqual({
    kind: 'envelope',
    envelope,
  })
  expect(parseRealtimeFrame(JSON.stringify({
    type: 'connection.ready',
    instanceId: 'event-gateway-a',
  }), parserContext())).toEqual({
    kind: 'control',
    frame: { type: 'connection.ready', instanceId: 'event-gateway-a' },
  })

  expect(parseRealtimeFrame('{', parserContext())).toMatchObject({ kind: 'rejected', reason: 'malformed-json' })
  expect(parseRealtimeFrame(JSON.stringify({ ...envelope, envelopeVersion: 2 }), parserContext()))
    .toMatchObject({ kind: 'rejected', reason: 'unknown-envelope-version' })
  expect(parseRealtimeFrame(JSON.stringify({ ...envelope, type: 'future.changed' }), parserContext()))
    .toMatchObject({ kind: 'rejected', reason: 'unknown-type' })
  expect(parseRealtimeFrame(JSON.stringify({ ...envelope, workspaceId: '33333333-3333-4333-8333-333333333333' }), parserContext()))
    .toMatchObject({ kind: 'rejected', reason: 'workspace-mismatch' })
  expect(parseRealtimeFrame(JSON.stringify({ ...envelope, payload: { content: 'unsafe' } }), parserContext()))
    .toMatchObject({ kind: 'rejected', reason: 'invalid-structure' })
  const withoutOccurredAt: Partial<RealtimeEnvelope> = { ...envelope }
  delete withoutOccurredAt.occurredAt
  expect(parseRealtimeFrame(JSON.stringify(withoutOccurredAt), parserContext()))
    .toMatchObject({ kind: 'rejected', reason: 'invalid-structure' })
  expect(parseRealtimeFrame('x'.repeat(16 * 1024 + 1), parserContext()))
    .toMatchObject({ kind: 'rejected', reason: 'oversized-frame' })
  expect(parseRealtimeFrame(JSON.stringify({ ...envelope, envelopeVersion: 0 }), parserContext()))
    .toEqual({ kind: 'legacy', type: 'message.created' })
})

test('@smoke @client-contract sequencer bounds event ids and detects duplicate, stale and gap per workspace scope key', () => {
  const sequencer = new RealtimeSequencer({ eventCapacity: 2, watermarkCapacity: 2 })
  expect(sequencer.inspect(realtimeEnvelope({ sequence: 10 }))).toMatchObject({ kind: 'accepted' })
  expect(sequencer.inspect(realtimeEnvelope({ sequence: 10 }))).toMatchObject({ kind: 'duplicate' })
  expect(sequencer.inspect(realtimeEnvelope({
    eventId: '44444444-4444-4444-8444-444444444444',
    sequence: 9,
  }))).toMatchObject({ kind: 'stale', previousSequence: 10 })
  expect(sequencer.inspect(realtimeEnvelope({
    eventId: '55555555-5555-4555-8555-555555555555',
    sequence: 12,
  }))).toMatchObject({ kind: 'gap', previousSequence: 10 })
  expect(sequencer.inspect(realtimeEnvelope({
    eventId: '66666666-6666-4666-8666-666666666666',
    sequenceKey: 'im:other-user',
    sequence: 12,
  }))).toMatchObject({ kind: 'accepted', previousSequence: null })
  expect(sequencer.snapshot()).toEqual({ eventIds: 2, watermarks: 2 })
})

test('@smoke @client-contract state machine waits for ready, filters replay, reports gaps and stops old generations', () => {
  const harness = connectionHarness()
  const statuses: string[] = []
  const events: RealtimeEnvelope[] = []
  const calibrations: string[] = []
  harness.connection.subscribeStatus((status) => statuses.push(status))
  harness.connection.subscribe((event) => events.push(event))
  harness.connection.subscribeCalibration((request) => calibrations.push(request.reason))

  harness.connection.start(connectionContext())
  const socket = harness.sockets[0]
  expect(harness.connection.getStatus()).toBe('connecting')
  socket.emitOpen()
  expect(harness.connection.getStatus()).toBe('connecting')
  socket.emitMessage(JSON.stringify({ type: 'connection.ready', instanceId: 'event-gateway-a' }))
  expect(harness.connection.getStatus()).toBe('ready')

  const first = realtimeEnvelope({ sequence: 4 })
  socket.emitMessage(JSON.stringify(first))
  socket.emitMessage(JSON.stringify(first))
  socket.emitMessage(JSON.stringify(realtimeEnvelope({
    eventId: '77777777-7777-4777-8777-777777777777',
    sequence: 3,
  })))
  socket.emitMessage(JSON.stringify(realtimeEnvelope({
    eventId: '88888888-8888-4888-8888-888888888888',
    sequence: 6,
  })))
  expect(events.map((event) => event.sequence)).toEqual([4, 6])
  expect(calibrations).toEqual(['initial-ready', 'gap'])
  expect(harness.connection.getStatus()).toBe('degraded')
  const diagnostics = harness.connection.getDiagnostics()
  expect(diagnostics).toMatchObject({
    acceptedEvents: 1,
    duplicateEvents: 1,
    staleEvents: 1,
    gapEvents: 1,
    lastSequence: 6,
    lastSequenceDecision: 'gap',
    lastCalibrationReason: 'gap',
  })
  expect(Object.keys(diagnostics)).not.toEqual(
    expect.arrayContaining(['accessToken', 'workspaceId', 'userId', 'payload']),
  )

  const oldMessage = socket.onmessage
  harness.connection.stop()
  oldMessage?.({ data: JSON.stringify(realtimeEnvelope({
    eventId: '99999999-9999-4999-8999-999999999999',
    sequence: 7,
  })) })
  expect(events).toHaveLength(2)
  expect(harness.connection.getStatus()).toBe('stopped')
  expect(statuses).toContain('ready')
})

test('@smoke @client-contract REST calibration cannot mark a reconnecting socket ready before its handshake', () => {
  const harness = connectionHarness()
  harness.connection.start(connectionContext())
  const initialSocket = harness.sockets[0]
  initialSocket.emitOpen()
  initialSocket.emitMessage(JSON.stringify({ type: 'connection.ready', instanceId: 'event-gateway-a' }))
  expect(harness.connection.getStatus()).toBe('ready')

  initialSocket.emitClose()
  expect(harness.connection.getStatus()).toBe('reconnecting')
  harness.clock.runNext()
  expect(harness.sockets).toHaveLength(2)

  harness.connection.markCalibrated()
  expect(harness.connection.getStatus()).toBe('reconnecting')

  const replacementSocket = harness.sockets[1]
  replacementSocket.emitOpen()
  harness.connection.markCalibrated()
  expect(harness.connection.getStatus()).toBe('reconnecting')

  replacementSocket.emitMessage(JSON.stringify({ type: 'connection.ready', instanceId: 'event-gateway-b' }))
  expect(harness.connection.getStatus()).toBe('ready')
})

test('@smoke @client-contract state machine keeps one retry timer and responds to offline, online, timeout and retry', () => {
  const harness = connectionHarness({ readyTimeoutMs: 100, random: () => 0.5 })
  harness.connection.start(connectionContext())
  const first = harness.sockets[0]
  first.emitOpen()
  harness.clock.runNext()
  expect(harness.connection.getStatus()).toBe('reconnecting')
  expect(harness.clock.pending()).toHaveLength(1)
  expect(harness.clock.pending()[0].delayMs).toBe(1_150)

  harness.online.goOffline()
  expect(harness.connection.getStatus()).toBe('degraded')
  expect(harness.clock.pending()).toHaveLength(0)
  harness.online.goOnline()
  expect(harness.sockets).toHaveLength(2)
  expect(harness.connection.getStatus()).toBe('reconnecting')

  harness.sockets[1].emitClose()
  harness.sockets[1].emitClose()
  expect(harness.clock.pending()).toHaveLength(1)
  harness.connection.retry()
  expect(harness.clock.pending()).toHaveLength(0)
  expect(harness.sockets).toHaveLength(3)
  harness.sockets[2].emitOpen()
  harness.sockets[2].emitMessage(JSON.stringify({ type: 'connection.ready', instanceId: 'event-gateway-b' }))
  expect(harness.connection.getStatus()).toBe('ready')
})

function parserContext() {
  return { expectedWorkspaceId: WORKSPACE_ID, expectedUserId: USER_ID }
}

function connectionContext() {
  return {
    accessToken: 'access-token',
    workspaceId: WORKSPACE_ID,
    userId: USER_ID,
    url: 'ws://localhost/ws/events',
  }
}

function realtimeEnvelope(overrides: Partial<RealtimeEnvelope> = {}): RealtimeEnvelope {
  return {
    envelopeVersion: 1,
    type: 'message.created',
    signalVersion: 1,
    eventId: '33333333-3333-4333-8333-333333333333',
    serverTime: '2026-07-24T08:00:00Z',
    occurredAt: '2026-07-24T07:59:58.123456789Z',
    workspaceId: WORKSPACE_ID,
    audienceType: 'user',
    recipientId: USER_ID,
    objectType: 'message',
    objectId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    sequenceScope: 'audience',
    sequenceKey: `im:${USER_ID}`,
    sequence: 1,
    correlationId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    calibrationPath: '/api/conversations/cccccccc-cccc-4ccc-8ccc-cccccccccccc/messages?afterSeq=0',
    payload: {
      conversationId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
      messageId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    },
    ...overrides,
  }
}

function connectionHarness(options: { readyTimeoutMs?: number; random?: () => number } = {}) {
  const sockets: FakeSocket[] = []
  const clock = new FakeClock()
  const online = new FakeOnlineSource()
  const connection = new RealtimeConnection({
    socketFactory: () => {
      const socket = new FakeSocket()
      sockets.push(socket)
      return socket
    },
    scheduler: clock,
    onlineSource: online,
    readyTimeoutMs: options.readyTimeoutMs,
    random: options.random ?? (() => 0),
  })
  return { connection, sockets, clock, online }
}

class FakeSocket implements RealtimeSocket {
  onopen: ((event: unknown) => void) | null = null
  onmessage: ((event: { data: unknown }) => void) | null = null
  onclose: ((event: { code?: number; reason?: string }) => void) | null = null
  onerror: ((event: unknown) => void) | null = null
  closed = false

  close() {
    this.closed = true
  }

  emitOpen() {
    this.onopen?.({})
  }

  emitMessage(data: string) {
    this.onmessage?.({ data })
  }

  emitClose() {
    this.onclose?.({ code: 1006 })
  }
}

class FakeClock implements RealtimeScheduler {
  private nextId = 1
  private readonly tasks = new Map<number, { callback: () => void; delayMs: number }>()

  setTimeout = (callback: () => void, delayMs: number) => {
    const id = this.nextId++
    this.tasks.set(id, { callback, delayMs })
    return id
  }

  clearTimeout = (timer: unknown) => {
    this.tasks.delete(timer as number)
  }

  pending() {
    return [...this.tasks.entries()].map(([id, task]) => ({ id, ...task }))
  }

  runNext() {
    const next = this.tasks.entries().next().value as [number, { callback: () => void; delayMs: number }] | undefined
    if (!next) throw new Error('No scheduled task')
    this.tasks.delete(next[0])
    next[1].callback()
  }
}

class FakeOnlineSource implements RealtimeOnlineSource {
  private online = true
  private onOnline: (() => void) | null = null
  private onOffline: (() => void) | null = null

  isOnline = () => this.online

  subscribe = (onOnline: () => void, onOffline: () => void) => {
    this.onOnline = onOnline
    this.onOffline = onOffline
    return () => {
      this.onOnline = null
      this.onOffline = null
    }
  }

  goOnline() {
    this.online = true
    this.onOnline?.()
  }

  goOffline() {
    this.online = false
    this.onOffline?.()
  }
}
