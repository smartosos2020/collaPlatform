import assert from 'node:assert/strict'
import { EventEmitter } from 'node:events'
import test from 'node:test'

import { runWebSocketScenario } from '../src/load/websocket.mjs'

test('WebSocket loader paces sustained triggers and proves ledger fanout', async () => {
  const harness = createHarness()
  const clock = createClock()
  const result = await runWebSocketScenario({
    wsUrl: 'ws://gateway.test/ws/events',
    apiBaseUrl: 'https://api.test',
    connections: 2,
    durationMs: 1_000,
    triggerRatePerSecond: 4,
    settleMs: 1,
    reconnects: 0,
    clock: clock.now,
    sleep: clock.sleep,
    socketFactory: harness.socketFactory,
    fetch: harness.fetch,
    targets: targets({ expectedFanout: 2 }),
  })

  assert.equal(result.ok, true, JSON.stringify(result.errors))
  assert.equal(result.metrics.triggerRequests, 4)
  assert.equal(result.metrics.expectedEvents, 4)
  assert.equal(result.metrics.fanoutEvents, 4)
  assert.equal(result.metrics.fanoutMisses, 0)
  assert.equal(result.metrics.duplicates, 0)
  assert.equal(result.metrics.gaps, 0)
  assert.equal(result.metrics.triggerRatePerSecond, 4)
  assert.ok(result.metrics.achievedTriggerRatePerSecond >= 3.9)
})

test('WebSocket loader rejects a 2xx calibration that does not contain the missing business object', async () => {
  const harness = createHarness({
    omitSequence: 2,
    calibrationBody: { items: [{ id: 'unrelated-object', sequence: 99 }] },
  })
  const result = await runWebSocketScenario({
    wsUrl: 'ws://gateway.test/ws/events',
    apiBaseUrl: 'https://api.test',
    connections: 1,
    iterations: 3,
    settleMs: 1,
    reconnects: 0,
    socketFactory: harness.socketFactory,
    fetch: harness.fetch,
    targets: targets({ expectedFanout: 1 }),
  })

  assert.equal(result.ok, false)
  assert.equal(result.metrics.gaps, 1)
  assert.ok(result.errors.some((error) => error.code === 'calibration_not_converged'))
  assert.ok(result.errors.some((error) => error.businessObjectId === 'object-2'))
})

test('WebSocket loader accepts a gap only when REST proves the exact business object and sequence', async () => {
  const harness = createHarness({ omitSequence: 2 })
  const result = await runWebSocketScenario({
    wsUrl: 'ws://gateway.test/ws/events',
    apiBaseUrl: 'https://api.test',
    connections: 1,
    iterations: 3,
    settleMs: 1,
    reconnects: 0,
    socketFactory: harness.socketFactory,
    fetch: harness.fetch,
    targets: targets({ expectedFanout: 1 }),
  })

  assert.equal(result.ok, true, JSON.stringify(result.errors))
  assert.equal(result.metrics.gaps, 1)
  assert.equal(result.metrics.recoveredGaps, 1)
  assert.equal(result.metrics.calibrationFailures, 0)
  assert.equal(result.metrics.fanoutMisses, 0)
  assert.ok(!result.errors.some((error) => error.code === 'calibration_not_converged'))
})

test('WebSocket loader requires both ledger fanout and REST convergence after reconnect', async () => {
  const harness = createHarness()
  const result = await runWebSocketScenario({
    wsUrl: 'ws://gateway.test/ws/events',
    apiBaseUrl: 'https://api.test',
    connections: 1,
    iterations: 2,
    settleMs: 1,
    reconnects: 1,
    reconnectSettleMs: 1,
    socketFactory: harness.socketFactory,
    fetch: harness.fetch,
    targets: targets({ expectedFanout: 1 }),
  })

  assert.equal(result.ok, true, JSON.stringify(result.errors))
  assert.equal(result.metrics.reconnects, 1)
  assert.equal(result.metrics.reconnectCalibrationRequests, 2)
  assert.equal(result.metrics.reconnectConvergenceFailures, 0)
  assert.equal(harness.calibratedIds.size, 2)
  assert.ok(harness.sockets.every((socket) => socket.closed))
})

test('WebSocket loader fails reconnect when REST cannot prove one ledger object', async () => {
  const harness = createHarness({ unavailableObjectId: 'object-2' })
  const result = await runWebSocketScenario({
    wsUrl: 'ws://gateway.test/ws/events',
    apiBaseUrl: 'https://api.test',
    connections: 1,
    iterations: 2,
    settleMs: 1,
    reconnects: 1,
    reconnectSettleMs: 1,
    socketFactory: harness.socketFactory,
    fetch: harness.fetch,
    targets: targets({ expectedFanout: 1 }),
  })

  assert.equal(result.ok, false)
  assert.equal(result.metrics.reconnectConvergenceFailures, 1)
  assert.ok(result.errors.some((error) =>
    error.code === 'calibration_not_converged' &&
    error.reason === 'reconnect' &&
    error.businessObjectId === 'object-2'))
})

test('WebSocket loader detects duplicate delivery from the expected-event ledger', async () => {
  const harness = createHarness({ duplicateSequence: 1 })
  const result = await runWebSocketScenario({
    wsUrl: 'ws://gateway.test/ws/events',
    apiBaseUrl: 'https://api.test',
    connections: 1,
    iterations: 1,
    settleMs: 1,
    reconnects: 0,
    socketFactory: harness.socketFactory,
    fetch: harness.fetch,
    targets: targets({ expectedFanout: 1 }),
  })

  assert.equal(result.ok, false)
  assert.equal(result.metrics.duplicates, 1)
  assert.ok(result.errors.some((error) => error.code === 'duplicate_event'))
})

test('WebSocket loader ignores unrelated workspace fanout when validating its expected ledger', async () => {
  const harness = createHarness({ emitForeignEvent: true })
  const result = await runWebSocketScenario({
    wsUrl: 'ws://gateway.test/ws/events',
    apiBaseUrl: 'https://api.test',
    connections: 2,
    iterations: 2,
    settleMs: 1,
    reconnects: 0,
    socketFactory: harness.socketFactory,
    fetch: harness.fetch,
    targets: targets({ expectedFanout: 2 }),
  })

  assert.equal(result.ok, true, JSON.stringify(result.errors))
  assert.equal(result.metrics.expectedEvents, 2)
  assert.equal(result.metrics.fanoutEvents, 2)
  assert.equal(result.metrics.fanoutMisses, 0)
})

test('capacity ledger calibration requires processed receipt and exact side effect', async () => {
  const descriptor = expectedEvent(1)
  const harness = createHarness({
    omitSequence: 1,
    calibrationBody: {
      entries: [{
        eventId: 'source-event-1',
        sideEffectId: 'wrong-side-effect',
        aggregateId: descriptor.businessObjectId,
        sequence: descriptor.sequence,
        deliveryStatus: 'processed',
        receiptRecorded: true,
      }],
    },
  })
  const result = await runWebSocketScenario({
    wsUrl: 'ws://gateway.test/ws/events',
    apiBaseUrl: 'https://api.test',
    connections: 1,
    iterations: 1,
    settleMs: 1,
    reconnects: 0,
    socketFactory: harness.socketFactory,
    fetch: harness.fetch,
    targets: targets({ expectedFanout: 1, requireCapacityReceipt: true }),
  })

  assert.equal(result.ok, false)
  assert.ok(result.errors.some((error) =>
    error.code === 'calibration_not_converged' &&
    /side effect/.test(error.message)))
})

test('WebSocket loader does not produce events before every connection reports ready', async () => {
  const harness = createHarness({ suppressReady: true })
  const result = await runWebSocketScenario({
    wsUrl: 'ws://gateway.test/ws/events',
    apiBaseUrl: 'https://api.test',
    connections: 1,
    iterations: 1,
    readyTimeoutMs: 10,
    settleMs: 1,
    reconnects: 0,
    socketFactory: harness.socketFactory,
    fetch: harness.fetch,
    targets: targets({ expectedFanout: 1 }),
  })

  assert.equal(result.ok, false)
  assert.equal(harness.triggerCalls, 0)
  assert.ok(result.errors.some((error) => error.code === 'connections_not_ready'))
})

test('WebSocket loader aborts a sustained trigger run, closes sockets and cannot pass', async () => {
  const controller = new AbortController()
  const harness = createHarness({ pendingTrigger: true })
  const running = runWebSocketScenario({
    wsUrl: 'ws://gateway.test/ws/events',
    apiBaseUrl: 'https://api.test',
    connections: 1,
    durationMs: 5_000,
    messagesPerSecond: 100,
    signal: controller.signal,
    socketFactory: harness.socketFactory,
    fetch: harness.fetch,
    targets: targets({ expectedFanout: 1 }),
  })
  setTimeout(() => controller.abort('test timeout'), 20)
  const result = await running

  assert.equal(result.ok, false)
  assert.equal(result.aborted, true)
  assert.ok(result.errors.some((error) => error.code === 'aborted'))
  assert.ok(harness.sockets.every((socket) => socket.closed))
})

function targets({ expectedFanout, requireCapacityReceipt = false }) {
  return {
    expectedFanout,
    trigger: {
      name: 'create-notification',
      path: '/api/trigger',
      method: 'POST',
      body: ({ iteration }) => ({ iteration }),
      requiredPaths: [
        'eventId',
        'workspaceId',
        'sequenceScope',
        'sequenceKey',
        'sequence',
        'businessObjectId',
        'calibrationPath',
      ],
    },
    calibration: {
      name: 'notification-calibration',
      path: ({ expectedEvent }) =>
        `/api/notifications?objectId=${encodeURIComponent(expectedEvent.businessObjectId)}`,
      requiredPaths: [requireCapacityReceipt ? 'entries' : 'items'],
      requireCapacityReceipt,
    },
  }
}

function createHarness(options = {}) {
  const sockets = []
  const durable = new Map()
  const calibratedIds = new Set()
  let triggerCalls = 0
  const socketFactory = ({ index }) => {
    const socket = new FakeSocket(index, options)
    sockets.push(socket)
    return socket
  }
  const fetch = async (url, init = {}) => {
    const parsed = new URL(url)
    if (parsed.pathname === '/api/trigger') {
      triggerCalls += 1
      if (options.pendingTrigger) return rejectOnAbort(init.signal)
      const iteration = JSON.parse(init.body).iteration
      const sequence = iteration + 1
      const descriptor = expectedEvent(sequence)
      durable.set(descriptor.businessObjectId, descriptor)
      if (options.emitForeignEvent) {
        const foreign = {
          ...expectedEvent(sequence + 100),
          eventId: `foreign-${sequence}`,
          sequenceKey: 'foreign-loader',
        }
        for (const socket of sockets.filter((entry) => entry.readyState === 1)) {
          queueMicrotask(() => socket.emit('message', JSON.stringify(realtimeFrame(foreign))))
        }
      }
      if (sequence !== options.omitSequence) {
        for (const socket of sockets.filter((entry) => entry.readyState === 1)) {
          queueMicrotask(() => {
            socket.emit('message', JSON.stringify(realtimeFrame(descriptor)))
            if (sequence === options.duplicateSequence) {
              socket.emit('message', JSON.stringify(realtimeFrame(descriptor)))
            }
          })
        }
      }
      return jsonResponse(descriptor, 202)
    }
    if (parsed.pathname === '/api/notifications') {
      const objectId = parsed.searchParams.get('objectId')
      calibratedIds.add(objectId)
      if (options.calibrationBody) return jsonResponse(options.calibrationBody)
      const descriptor = objectId === options.unavailableObjectId ? undefined : durable.get(objectId)
      return jsonResponse({
        items: descriptor ? [{ id: objectId, sequence: descriptor.sequence }] : [],
      })
    }
    return jsonResponse({ error: 'not found' }, 404)
  }
  return {
    sockets,
    durable,
    calibratedIds,
    socketFactory,
    fetch,
    get triggerCalls() {
      return triggerCalls
    },
  }
}

function expectedEvent(sequence) {
  return {
    eventId: `event-${sequence}`,
    workspaceId: 'workspace-1',
    sequenceScope: 'audience',
    sequenceKey: 'notifications:user-1',
    sequence,
    businessObjectId: `object-${sequence}`,
    calibrationPath: '/api/notifications',
  }
}

function realtimeFrame(descriptor) {
  return {
    envelopeVersion: 1,
    signalVersion: 1,
    type: 'notification.created',
    ...descriptor,
    serverTime: new Date().toISOString(),
    occurredAt: new Date().toISOString(),
  }
}

function createClock() {
  let now = 0
  return {
    now: () => now,
    sleep: async (milliseconds, signal) => {
      if (signal?.aborted) throw abortError()
      now += milliseconds
    },
  }
}

function rejectOnAbort(signal) {
  if (signal?.aborted) return Promise.reject(abortError())
  return new Promise((_, reject) => {
    signal?.addEventListener('abort', () => reject(abortError()), { once: true })
  })
}

function abortError() {
  const error = new Error('aborted')
  error.name = 'AbortError'
  return error
}

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

class FakeSocket extends EventEmitter {
  constructor(index, options = {}) {
    super()
    this.index = index
    this.readyState = 0
    this.closed = false
    queueMicrotask(() => {
      if (this.closed) return
      this.readyState = 1
      this.emit('open')
      if (!options.suppressReady) {
        this.emit('message', JSON.stringify({
          type: 'connection.ready',
          instanceId: `gateway-${index}`,
        }))
      }
    })
  }

  close() {
    if (this.closed) return
    this.closed = true
    this.readyState = 3
    this.emit('close')
  }

  terminate() {
    this.close()
  }
}
