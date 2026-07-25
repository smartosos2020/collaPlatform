import assert from 'node:assert/strict'
import { EventEmitter } from 'node:events'
import test from 'node:test'

import { runCollaborationScenario } from '../src/load/collaboration.mjs'
import { runHttpScenario } from '../src/load/http.mjs'
import { runWebSocketScenario } from '../src/load/websocket.mjs'
import { runWorkerScenario } from '../src/load/worker.mjs'

test('HTTP loader shapes one fixed read/write window at the total target RPS', async () => {
  const controller = new AbortController()
  const observedSignals = []
  let now = 0
  const result = await runHttpScenario({
    apiBaseUrl: 'https://api.test',
    token: 'rate-token',
    targetRps: 50,
    durationMs: 300,
    concurrency: 4,
    clock: () => now,
    sleep: async (milliseconds) => {
      now += milliseconds
    },
    signal: controller.signal,
    phaseWeights: { read: 3, write: 1 },
    fetch: (url, init) => {
      observedSignals.push(init.signal)
      return rateHttpFetch(url)
    },
    targets: rateHttpTargets(),
  })

  const loadCount = result.metrics.phaseCounts.read + result.metrics.phaseCounts.write
  assert.equal(result.ok, true, JSON.stringify(result.errors))
  assert.equal(result.metrics.targetRps, 50)
  assert.equal(result.metrics.loadDurationMs, 300)
  assert.equal(loadCount, 15)
  assert.equal(result.metrics.scheduledRequests, loadCount)
  assert.ok(result.metrics.phaseCounts.read > result.metrics.phaseCounts.write)
  assert.equal(result.metrics.achievedRps, 50)
  assert.ok(observedSignals.every((signal) => signal === controller.signal))
})

test('HTTP loader propagates AbortSignal, stops pacing, and cannot pass', async () => {
  const controller = new AbortController()
  const observedSignals = []
  const pendingFetch = async (_url, init) => {
    observedSignals.push(init.signal)
    await rejectOnAbort(init.signal)
  }
  const running = runHttpScenario({
    apiBaseUrl: 'https://api.test',
    token: 'abort-token',
    targetRps: 100,
    durationMs: 5_000,
    concurrency: 2,
    signal: controller.signal,
    fetch: pendingFetch,
    targets: rateHttpTargets(),
  })
  setTimeout(() => controller.abort('test timeout'), 30)
  const result = await running

  assert.equal(result.ok, false)
  assert.equal(result.aborted, true)
  assert.ok(result.errors.some((error) => error.code === 'aborted'))
  assert.ok(observedSignals.length > 0)
  assert.ok(observedSignals.every((signal) => signal === controller.signal))
  assert.ok(result.metrics.loadDurationMs < 1_000, result.metrics.loadDurationMs)
})

test('Worker loader shapes sustained and burst phases and reports achieved rates', async () => {
  const controller = new AbortController()
  const observedSignals = []
  let produced = 0
  let now = 0
  const clock = () => now
  const sleep = async (milliseconds) => {
    now += milliseconds
  }
  const fetch = async (url, init) => {
    observedSignals.push(init.signal)
    const path = new URL(url).pathname
    if (path === '/api/diagnostics') {
      const processedEventIds = Array.from({ length: produced }, (_, index) => `event-${index + 1}`)
      return jsonResponse({
        backlog: 0,
        oldestAgeSeconds: 0,
        retries: 0,
        deadLetters: 0,
        sideEffects: processedEventIds.map((eventId, index) => ({
          eventId,
          effectId: `effect-${index + 1}`,
        })),
        processedEventIds,
      })
    }
    produced += 1
    return jsonResponse({
      eventId: `event-${produced}`,
      sideEffectId: `effect-${produced}`,
      aggregateId: 'rate-test-aggregate',
      sequence: produced,
    }, 202)
  }
  const result = await runWorkerScenario({
    apiBaseUrl: 'https://api.test',
    fetch,
    rateMode: 'both',
    eventsPerSecond: 30,
    durationMs: 120,
    burstEventsPerSecond: 60,
    burstSeconds: 0.1,
    concurrency: 4,
    clock,
    sleep,
    signal: controller.signal,
    targets: workerTargets(),
  })

  assert.equal(result.ok, true, JSON.stringify(result.errors))
  assert.deepEqual(result.metrics.ratePhases.map((phase) => phase.name), ['sustained', 'burst'])
  assert.equal(result.metrics.ratePhases[0].targetEventsPerSecond, 30)
  assert.equal(result.metrics.ratePhases[1].targetEventsPerSecond, 60)
  assert.ok(result.metrics.ratePhases.every((phase) => phase.durationMs > 0))
  assert.equal(result.metrics.producedRequests, produced)
  assert.equal(result.metrics.producedRequests, 10)
  assert.ok(observedSignals.every((signal) => signal === controller.signal))
})

test('Worker loader cancels in-flight producers and skips drain polling after abort', async () => {
  const controller = new AbortController()
  const producerSignals = []
  let diagnosticsCalls = 0
  const fetch = async (url, init) => {
    const path = new URL(url).pathname
    if (path === '/api/diagnostics') {
      diagnosticsCalls += 1
      return jsonResponse({
        backlog: 0,
        oldestAgeSeconds: 0,
        retries: 0,
        deadLetters: 0,
        sideEffects: [],
        processedEventIds: [],
      })
    }
    producerSignals.push(init.signal)
    await rejectOnAbort(init.signal)
  }
  const running = runWorkerScenario({
    apiBaseUrl: 'https://api.test',
    fetch,
    eventsPerSecond: 100,
    durationMs: 5_000,
    concurrency: 2,
    signal: controller.signal,
    targets: workerTargets(),
  })
  setTimeout(() => controller.abort('worker timeout'), 30)
  const result = await running

  assert.equal(result.ok, false)
  assert.equal(result.aborted, true)
  assert.ok(producerSignals.length > 0)
  assert.ok(producerSignals.every((signal) => signal === controller.signal))
  assert.equal(diagnosticsCalls, 1)
})

test('WebSocket loader closes sockets that are still opening when aborted', async () => {
  const controller = new AbortController()
  const socket = new PendingSocket()
  const running = runWebSocketScenario({
    wsUrl: 'ws://gateway.test/ws/events',
    connections: 1,
    connectTimeoutMs: 5_000,
    signal: controller.signal,
    socketFactory: ({ signal }) => {
      assert.equal(signal, controller.signal)
      return socket
    },
  })
  setTimeout(() => controller.abort('websocket timeout'), 20)
  const result = await running

  assert.equal(result.ok, false)
  assert.equal(result.aborted, true)
  assert.equal(socket.closed, true)
  assert.equal(socket.terminated, true)
})

test('Collaboration loader destroys pending providers and documents when aborted', async () => {
  const controller = new AbortController()
  const providers = []
  const documents = []
  class TrackingDoc {
    constructor() {
      this.destroyed = false
      documents.push(this)
    }

    destroy() {
      this.destroyed = true
    }
  }
  const running = runCollaborationScenario({
    collaborationUrl: 'ws://collaboration.test',
    rooms: [{ name: 'abort-room', clients: 1 }],
    syncTimeoutMs: 5_000,
    signal: controller.signal,
    dependencies: {
      Provider: class {},
      Y: { Doc: TrackingDoc },
      WebSocket: class {},
    },
    providerFactory: (_configuration, context) => {
      assert.equal(context.signal, controller.signal)
      const provider = new EventEmitter()
      provider.synced = false
      provider.disconnected = false
      provider.destroyed = false
      provider.disconnect = () => {
        provider.disconnected = true
      }
      provider.destroy = () => {
        provider.destroyed = true
      }
      providers.push(provider)
      return provider
    },
  })
  setTimeout(() => controller.abort('collaboration timeout'), 20)
  const result = await running

  assert.equal(result.ok, false)
  assert.equal(result.aborted, true)
  assert.ok(providers.every((provider) => provider.disconnected && provider.destroyed))
  assert.ok(documents.every((document) => document.destroyed))
})

function rateHttpTargets() {
  return {
    read: { name: 'read', path: '/api/read', requiredPaths: ['items'] },
    write: { name: 'write', path: '/api/write', body: {}, requiredPaths: ['id'] },
    idempotency: { name: 'idempotency', path: '/api/idempotency', body: {}, requiredPaths: ['id'] },
    file: {
      prepare: { path: '/api/files/prepare', requiredPaths: ['fileId', 'uploadUrl'] },
      upload: { method: 'PUT', content: 'capacity' },
      complete: { path: '/api/files/complete', requiredPaths: ['id'] },
    },
  }
}

async function rateHttpFetch(url) {
  const path = new URL(url).pathname
  if (path === '/api/read') return jsonResponse({ items: [] })
  if (path === '/api/write') return jsonResponse({ id: 'write-1' })
  if (path === '/api/idempotency') return jsonResponse({ id: 'idempotent-1' })
  if (path === '/api/files/prepare') {
    return jsonResponse({ fileId: 'file-1', uploadUrl: 'https://storage.test/upload' })
  }
  if (url === 'https://storage.test/upload') return new Response(null, { status: 200 })
  if (path === '/api/files/complete') return jsonResponse({ id: 'file-1' })
  return jsonResponse({ error: 'not found' }, 404)
}

function workerTargets() {
  return {
    producers: [{
      name: 'events',
      path: '/api/produce',
      method: 'POST',
      expectedStatus: 202,
      body: ({ iteration }) => ({ iteration }),
      requiredPaths: ['eventId', 'sideEffectId'],
    }],
    diagnostics: {
      path: '/api/diagnostics',
      requiredPaths: ['backlog', 'oldestAgeSeconds', 'retries', 'deadLetters'],
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

class PendingSocket extends EventEmitter {
  readyState = 0
  closed = false
  terminated = false

  close() {
    this.closed = true
    this.readyState = 3
    this.emit('close')
  }

  terminate() {
    this.terminated = true
  }
}
