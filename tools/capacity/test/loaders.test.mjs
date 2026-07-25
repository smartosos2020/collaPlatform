import assert from 'node:assert/strict'
import { EventEmitter } from 'node:events'
import test from 'node:test'

import {
  quantile,
  summarizeSamples,
  validateHttpResponse,
} from '../src/load/common.mjs'
import { runHttpScenario } from '../src/load/http.mjs'
import { runWebSocketScenario } from '../src/load/websocket.mjs'
import { runCollaborationScenario } from '../src/load/collaboration.mjs'
import { runWorkerScenario } from '../src/load/worker.mjs'

test('common computes percentiles and detects semantic permission failures', () => {
  assert.equal(quantile([40, 10, 30, 20], 0.5), 25)
  assert.deepEqual(summarizeSamples([
    { latencyMs: 10, ok: true },
    { latencyMs: 20, ok: false },
  ]), {
    count: 2,
    success: 1,
    failure: 1,
    min: 10,
    max: 20,
    mean: 15,
    p50: 15,
    p95: 19.5,
    p99: 19.9,
  })

  const validation = validateHttpResponse(
    { status: 200 },
    { items: [] },
    { permission: 'deny' },
  )
  assert.equal(validation.ok, false)
  assert.match(validation.errors.join(' '), /permission denial expected/)
})

test('HTTP loader covers login, read, write, idempotency and three-step file flow', async () => {
  const idempotentBodies = new Map()
  const fetch = async (url, init = {}) => {
    const path = new URL(url).pathname
    if (path === '/api/auth/login') return jsonResponse({ accessToken: 'token-1' })
    if (path === '/api/read') return jsonResponse({ items: [{ id: 'item-1' }] })
    if (path === '/api/write') return jsonResponse({ id: 'write-1', version: 1 }, 201)
    if (path === '/api/idempotent') {
      const key = init.headers['X-Colla-Request-Id']
      if (!idempotentBodies.has(key)) idempotentBodies.set(key, { id: 'command-1', version: 2 })
      return jsonResponse(idempotentBodies.get(key))
    }
    if (path === '/api/files/upload-url') {
      return jsonResponse({ fileId: 'file-1', uploadUrl: 'https://storage.test/upload' })
    }
    if (url === 'https://storage.test/upload') return new Response(null, { status: 200 })
    if (path === '/api/files/complete') return jsonResponse({ id: 'file-1', state: 'available' })
    return jsonResponse({ error: 'unknown path' }, 404)
  }

  const result = await runHttpScenario({
    apiBaseUrl: 'https://api.test',
    users: [{ username: 'alice', password: 'secret' }],
    fetch,
    iterations: 1,
    targets: {
      login: { path: '/api/auth/login', requiredPaths: ['accessToken'] },
      read: { name: 'items', path: '/api/read', requiredPaths: ['items'] },
      write: { name: 'command', path: '/api/write', body: { value: 1 }, expectedStatus: 201, requiredPaths: ['id'] },
      idempotency: {
        name: 'idempotent-command',
        path: '/api/idempotent',
        body: { value: 1 },
        requiredPaths: ['id', 'version'],
      },
      file: {
        prepare: {
          path: '/api/files/upload-url',
          body: { fileName: 'sample.txt', sizeBytes: 13 },
          requiredPaths: ['fileId', 'uploadUrl'],
        },
        upload: { method: 'PUT', content: 'capacity-file' },
        complete: { path: '/api/files/complete', requiredPaths: ['id'], equals: { state: 'available' } },
      },
    },
  })

  assert.equal(result.ok, true, JSON.stringify(result.errors))
  assert.equal(result.samples.length, 8)
  assert.equal(result.summary.operations['idempotent-command'].p99 !== null, true)
  assert.equal(result.errors.length, 0)
})

test('HTTP loader fails a 2xx response with incorrect application semantics', async () => {
  const fetch = async (url) => {
    const path = new URL(url).pathname
    if (path === '/api/auth/login') return jsonResponse({ accessToken: 'token-1' })
    if (path === '/api/read') return jsonResponse({ items: [] })
    if (path === '/api/write') return jsonResponse({ id: 'write-1' })
    if (path === '/api/idempotent') return jsonResponse({ id: 'same' })
    if (path === '/api/files/upload-url') {
      return jsonResponse({ fileId: 'file-1', uploadUrl: 'https://storage.test/upload' })
    }
    if (url === 'https://storage.test/upload') return new Response(null, { status: 200 })
    if (path === '/api/files/complete') return jsonResponse({ id: 'file-1' })
    return jsonResponse({}, 404)
  }
  const result = await runHttpScenario({
    apiBaseUrl: 'https://api.test',
    users: [{ username: 'alice', password: 'secret' }],
    fetch,
    targets: {
      login: { path: '/api/auth/login', requiredPaths: ['accessToken'] },
      read: {
        name: 'non-empty-read',
        path: '/api/read',
        validate: (body) => body.items.length > 0 || 'read returned no visible objects',
      },
      write: { path: '/api/write', body: {}, requiredPaths: ['id'] },
      idempotency: { path: '/api/idempotent', body: {}, requiredPaths: ['id'] },
      file: {
        prepare: { path: '/api/files/upload-url' },
        upload: { method: 'PUT' },
        complete: { path: '/api/files/complete', requiredPaths: ['id'] },
      },
    },
  })

  assert.equal(result.ok, false)
  assert.ok(result.errors.some((error) => error.message.includes('read returned no visible objects')))
  assert.equal(result.samples.find((sample) => sample.operation === 'non-empty-read').status, 200)
})

test('WebSocket loader tracks fanout, gaps, calibration and reconnects', async () => {
  const frameOne = realtimeFrame('event-1', 1)
  const frameThree = realtimeFrame('event-3', 3)
  const sockets = []
  const socketFactory = ({ index, reconnect }) => {
    const socket = new FakeSocket(index, reconnect ? [] : [frameOne, frameThree])
    sockets.push(socket)
    return socket
  }
  const result = await runWebSocketScenario({
    wsUrl: 'ws://gateway.test/ws/events',
    apiBaseUrl: 'https://api.test',
    users: [{ token: 'a' }, { token: 'b' }],
    connections: 2,
    reconnects: 1,
    settleMs: 10,
    reconnectSettleMs: 5,
    socketFactory,
    fetch: async () => jsonResponse({ items: [{ id: 'durable-1' }] }),
    targets: {
      expectedFanout: 2,
      calibration: { requiredPaths: ['items'] },
    },
  })

  assert.equal(result.ok, true, JSON.stringify(result.errors))
  assert.equal(result.metrics.openedConnections, 3)
  assert.equal(result.metrics.reconnects, 1)
  assert.equal(result.metrics.gaps, 2)
  assert.equal(result.metrics.calibrationRequests, 2)
  assert.equal(result.metrics.fanoutMisses, 0)
  assert.ok(sockets.every((socket) => socket.closed))
})

test('WebSocket loader detects fanout mismatch despite valid frames', async () => {
  const socketFactory = ({ index }) => new FakeSocket(index, [realtimeFrame('event-1', 1)])
  const result = await runWebSocketScenario({
    wsUrl: 'ws://gateway.test/ws/events',
    connections: 2,
    settleMs: 10,
    socketFactory,
    targets: { expectedFanout: 3 },
  })

  assert.equal(result.ok, false)
  assert.ok(result.errors.some((error) => error.code === 'fanout_mismatch'))
})

test('collaboration loader edits multiple isolated rooms, reconnects and converges', async () => {
  const network = new InMemoryCollaborationNetwork()
  const result = await runCollaborationScenario({
    collaborationUrl: ['ws://collaboration-a.test', 'ws://collaboration-b.test'],
    rooms: [
      { name: 'room-a', clients: 2, editsPerClient: 2 },
      { name: 'room-b', clients: 2, editsPerClient: 1 },
    ],
    reconnectsPerRoom: 1,
    syncTimeoutMs: 50,
    convergenceTimeoutMs: 50,
    pollIntervalMs: 1,
    dependencies: {
      Provider: class {},
      Y: { Doc: FakeDoc },
      WebSocket: class {},
    },
    providerFactory: (configuration) => network.provider(configuration),
  })

  assert.equal(result.ok, true, JSON.stringify(result.errors))
  assert.equal(result.metrics.rooms, 2)
  assert.equal(result.metrics.clients, 4)
  assert.equal(result.metrics.edits, 6)
  assert.equal(result.metrics.reconnects, 2)
  assert.equal(result.metrics.roomIsolationFailures, 0)
})

test('collaboration loader detects clients that do not converge', async () => {
  const providerFactory = (configuration) => {
    const provider = new EventEmitter()
    provider.synced = false
    provider.connect = () => queueMicrotask(() => {
      provider.synced = true
      provider.emit('synced', { state: true })
    })
    provider.disconnect = () => {
      provider.synced = false
    }
    provider.destroy = () => {}
    provider.connect()
    return provider
  }
  const result = await runCollaborationScenario({
    collaborationUrl: 'ws://collaboration.test',
    rooms: [{ name: 'broken-room', clients: 2 }],
    reconnectsPerRoom: 0,
    syncTimeoutMs: 50,
    convergenceTimeoutMs: 5,
    pollIntervalMs: 1,
    dependencies: {
      Provider: class {},
      Y: { Doc: FakeDoc },
      WebSocket: class {},
    },
    providerFactory,
  })

  assert.equal(result.ok, false)
  assert.ok(result.errors.some((error) => error.code === 'convergence_failure'))
})

test('Worker loader validates named producers and drained diagnostics', async () => {
  let diagnosticsPoll = 0
  const fetch = async (url, init = {}) => {
    const path = new URL(url).pathname
    if (path === '/api/diagnostics') {
      diagnosticsPoll += 1
      return jsonResponse(diagnosticsPoll === 1
        ? { backlog: 0, oldestAgeSeconds: 0, retries: 4, deadLetters: 1, sideEffects: [] }
        : {
            backlog: 0,
            oldestAgeSeconds: 0,
            retries: 4,
            deadLetters: 1,
            sideEffects: [{ idempotencyKey: 'effect-1' }, { idempotencyKey: 'effect-2' }],
            processedEventIds: ['event-1', 'event-2'],
          })
    }
    if (path === '/api/produce') {
      const body = JSON.parse(init.body)
      const number = body.iteration + 1
      return jsonResponse({
        eventId: `event-${number}`,
        sideEffectId: `effect-${number}`,
        aggregateId: 'aggregate-1',
        sequence: number,
      }, 202)
    }
    return jsonResponse({}, 404)
  }
  const result = await runWorkerScenario({
    apiBaseUrl: 'https://api.test',
    token: 'worker-token',
    fetch,
    iterations: 2,
    drainTimeoutMs: 20,
    expectedEventIds: ['event-1', 'event-2'],
    targets: {
      producers: [{
        name: 'notification-burst',
        path: '/api/produce',
        method: 'POST',
        expectedStatus: 202,
        body: ({ iteration }) => ({ iteration }),
        requiredPaths: ['eventId', 'sideEffectId', 'aggregateId', 'sequence'],
      }],
      diagnostics: {
        path: '/api/diagnostics',
        requiredPaths: ['backlog', 'oldestAgeSeconds', 'retries', 'deadLetters'],
      },
    },
  })

  assert.equal(result.ok, true, JSON.stringify(result.errors))
  assert.equal(result.metrics.producedRequests, 2)
  assert.equal(result.metrics.backlog, 0)
  assert.equal(result.metrics.diagnosticsPolls, 2)
})

test('Worker loader detects duplicate side effects and dead-letter growth on 2xx responses', async () => {
  let diagnosticsPoll = 0
  const fetch = async (url) => {
    const path = new URL(url).pathname
    if (path === '/api/diagnostics') {
      diagnosticsPoll += 1
      return jsonResponse({
        backlog: 0,
        oldestAgeSeconds: 1,
        retries: diagnosticsPoll,
        deadLetters: diagnosticsPoll === 1 ? 0 : 1,
        sideEffects: diagnosticsPoll === 1 ? [] : ['duplicate-effect', 'duplicate-effect'],
      })
    }
    return jsonResponse({
      eventId: 'duplicate-event',
      sideEffectId: 'duplicate-effect',
      aggregateId: 'aggregate-1',
      sequence: 1,
    })
  }
  const result = await runWorkerScenario({
    apiBaseUrl: 'https://api.test',
    fetch,
    iterations: 2,
    maxDeadLetterIncrease: 0,
    targets: {
      producers: [{
        name: 'poison-burst',
        path: '/api/produce',
        method: 'POST',
        body: {},
        requiredPaths: ['eventId', 'sideEffectId'],
      }],
      diagnostics: {
        path: '/api/diagnostics',
        requiredPaths: ['backlog', 'deadLetters'],
      },
    },
  })

  assert.equal(result.ok, false)
  assert.ok(result.errors.some((error) => error.code === 'duplicate_side_effect'))
  assert.ok(result.errors.some((error) => error.code === 'dead_letter_increase'))
  assert.ok(result.samples.every((sample) => sample.status === 200))
})

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function realtimeFrame(eventId, sequence) {
  return {
    envelopeVersion: 1,
    signalVersion: 1,
    type: 'notification.created',
    eventId,
    workspaceId: 'workspace-1',
    sequenceScope: 'audience',
    sequenceKey: 'notifications:user-1',
    sequence,
    calibrationPath: '/api/notifications',
    serverTime: new Date().toISOString(),
    occurredAt: new Date().toISOString(),
  }
}

class FakeSocket extends EventEmitter {
  constructor(index, frames) {
    super()
    this.index = index
    this.readyState = 0
    this.closed = false
    queueMicrotask(() => {
      this.readyState = 1
      this.emit('open')
      this.emit('message', JSON.stringify({ type: 'connection.ready', instanceId: `gateway-${index}` }))
      setTimeout(() => {
        for (const frame of frames) this.emit('message', JSON.stringify(frame))
      }, 0)
    })
  }

  close() {
    if (this.closed) return
    this.closed = true
    this.readyState = 3
    this.emit('close')
  }
}

class FakeDoc {
  constructor() {
    this.value = ''
    this.onEdit = null
  }

  getText() {
    return {
      get length() {
        return this.owner.value.length
      },
      owner: this,
      insert: (_index, value) => {
        this.value += value
        this.onEdit?.(this.value)
      },
      toString: () => this.value,
    }
  }

  toJSON() {
    return { content: this.value }
  }

  destroy() {}
}

class InMemoryCollaborationNetwork {
  rooms = new Map()

  provider(configuration) {
    const provider = new EventEmitter()
    const documents = this.rooms.get(configuration.name) ?? new Set()
    this.rooms.set(configuration.name, documents)
    documents.add(configuration.document)
    const current = [...documents].find((document) => document !== configuration.document)
    if (current) configuration.document.value = current.value
    configuration.document.onEdit = (value) => {
      for (const document of documents) document.value = value
    }
    provider.synced = false
    provider.connect = () => queueMicrotask(() => {
      provider.synced = true
      provider.emit('synced', { state: true })
    })
    provider.disconnect = () => {
      provider.synced = false
    }
    provider.destroy = () => {
      documents.delete(configuration.document)
    }
    provider.connect()
    return provider
  }
}
