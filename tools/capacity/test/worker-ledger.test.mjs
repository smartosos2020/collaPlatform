import assert from 'node:assert/strict'
import test from 'node:test'

import { runWorkerScenario } from '../src/load/worker.mjs'

test('Worker strict-v1 closes the produced, processed, side-effect and sequence ledgers', async () => {
  const harness = createHarness()
  const result = await runWorkerScenario({
    ...harness.options,
    iterations: 2,
    expectedEventIds: ['caller-controlled-id-must-not-be-used'],
  })

  assert.equal(result.ok, true, JSON.stringify(result.errors))
  assert.deepEqual(result.metrics.correctnessMode, { version: 1, mode: 'strict' })
  assert.deepEqual(result.metrics.acknowledgedEventIds, ['event-1', 'event-2'])
  assert.equal(result.metrics.acknowledgedEvents, 2)
  assert.equal(result.metrics.producedRequests, 2)
  assert.equal(result.metrics.successfulProducerRequests, 2)
})

test('Worker strict-v1 reads the final production ledger through bounded cursor pages', async () => {
  const produced = []
  const requestedCursors = []
  const fetch = async (url, init = {}) => {
    const parsed = new URL(url)
    if (parsed.pathname === '/api/produce') {
      const request = JSON.parse(init.body)
      const body = {
        eventId: `event-${request.iteration + 1}`,
        sideEffectId: `effect-${request.iteration + 1}`,
        aggregateId: 'aggregate-1',
        sequence: request.iteration + 1,
      }
      produced.push(body)
      return jsonResponse(body, 202)
    }
    if (parsed.pathname === '/api/summary') return jsonResponse(baseDiagnostics())
    if (parsed.pathname === '/api/ledger') {
      requestedCursors.push(parsed.searchParams.get('cursor'))
      const index = parsed.searchParams.get('cursor') ? 1 : 0
      const item = produced[index]
      return jsonResponse({
        entries: [{
          eventId: item.eventId,
          sideEffectId: item.sideEffectId,
          aggregateId: item.aggregateId,
          sequence: item.sequence,
          deliveryStatus: 'processed',
          attemptCount: 1,
          replayCount: 0,
          receiptRecorded: true,
        }],
        nextCursor: index === 0 ? item.eventId : null,
      })
    }
    return jsonResponse({ error: 'not found' }, 404)
  }
  const result = await runWorkerScenario({
    apiBaseUrl: 'https://api.test',
    fetch,
    iterations: 2,
    targets: {
      producers: [{
        name: 'capacity-probe',
        path: '/api/produce',
        method: 'POST',
        expectedStatus: 202,
        body: ({ iteration }) => ({ iteration }),
      }],
      diagnostics: {
        path: '/api/summary',
        requiredPaths: ['backlog', 'oldestAgeSeconds', 'retries', 'deadLetters'],
      },
      ledger: {
        path: '/api/ledger',
        pageSize: 1,
        maxPages: 3,
      },
    },
  })

  assert.equal(result.ok, true, JSON.stringify(result.errors))
  assert.deepEqual(requestedCursors, [null, 'event-1'])
  assert.deepEqual(result.metrics.processedEventIds, ['event-1', 'event-2'])
  assert.deepEqual(result.metrics.pendingEventIds, [])
})

test('Worker rejects a successful producer response without a non-empty eventId', async () => {
  const harness = createHarness({
    producerBody: () => ({
      eventId: ' ',
      sideEffectId: 'effect-1',
      aggregateId: 'aggregate-1',
      sequence: 1,
    }),
  })
  const result = await runWorkerScenario(harness.options)

  assert.equal(result.ok, false)
  assert.equal(result.metrics.acknowledgedEvents, 0)
  assert.ok(result.errors.some((error) => error.code === 'missing_event_id'))
})

test('Worker rejects duplicate producer event IDs and excludes them from acknowledged rate', async () => {
  const harness = createHarness({
    producerBody: ({ iteration }) => ({
      eventId: 'event-duplicate',
      sideEffectId: `effect-${iteration + 1}`,
      aggregateId: 'aggregate-1',
      sequence: iteration + 1,
    }),
  })
  const result = await runWorkerScenario({ ...harness.options, iterations: 2 })

  assert.equal(result.ok, false)
  assert.equal(result.metrics.producedRequests, 2)
  assert.equal(result.metrics.acknowledgedEvents, 0)
  assert.equal(result.metrics.achievedEventsPerSecond, 0)
  assert.ok(result.metrics.achievedRequestRate >= result.metrics.achievedEventsPerSecond)
  assert.ok(result.errors.some((error) => error.code === 'duplicate_event'))
})

test('Worker derives the expected ledger from produced IDs instead of caller expectedEventIds', async () => {
  const harness = createHarness({
    finalDiagnostics: () => strictDiagnostics({
      processedEventIds: ['caller-id'],
      sideEffects: [{ eventId: 'caller-id', idempotencyKey: 'caller-effect' }],
    }),
  })
  const result = await runWorkerScenario({
    ...harness.options,
    expectedEventIds: ['caller-id'],
  })

  assert.equal(result.ok, false)
  assert.ok(result.errors.some((error) =>
    error.code === 'missing_processed_event' && error.missingEventIds?.includes('event-1')))
})

test('Worker strict-v1 requires diagnostics to explicitly report processedEventIds', async () => {
  const harness = createHarness({
    finalDiagnostics: () => ({
      backlog: 0,
      oldestAgeSeconds: 0,
      retries: 0,
      deadLetters: 0,
      sideEffects: [{ eventId: 'event-1', idempotencyKey: 'effect-1' }],
    }),
  })
  const result = await runWorkerScenario(harness.options)

  assert.equal(result.ok, false)
  assert.ok(result.errors.some((error) => error.code === 'processed_event_ledger_missing'))
})

test('Worker strict-v1 validates per-event side effects and failure ledgers', async () => {
  const harness = createHarness({
    finalDiagnostics: () => strictDiagnostics({
      retries: 1,
      sideEffects: [
        { eventId: 'event-1', idempotencyKey: 'effect-1' },
        { eventId: 'event-1', idempotencyKey: 'effect-extra' },
      ],
      retriedEventIds: ['event-1'],
    }),
  })
  const result = await runWorkerScenario(harness.options)

  assert.equal(result.ok, false)
  assert.ok(result.errors.some((error) => error.code === 'multiple_side_effects'))
  assert.ok(result.errors.some((error) => error.code === 'event_retried'))
})

test('Worker permits summary diagnostics only through an explicit versioned mode', async () => {
  const harness = createHarness({
    producerBody: () => ({ eventId: 'event-1' }),
    finalDiagnostics: () => baseDiagnostics(),
  })
  const summary = await runWorkerScenario({
    ...harness.options,
    correctnessMode: { version: 1, mode: 'summary' },
  })
  const invalid = await runWorkerScenario({
    ...harness.options,
    correctnessMode: 'summary',
  })

  assert.equal(summary.ok, true, JSON.stringify(summary.errors))
  assert.deepEqual(summary.metrics.correctnessMode, { version: 1, mode: 'summary' })
  assert.equal(invalid.ok, false)
  assert.ok(invalid.errors.some((error) => error.code === 'invalid_correctness_mode'))
})

test('Worker AbortSignal always prevents Pass', async () => {
  const controller = new AbortController()
  controller.abort('test abort')
  const harness = createHarness()
  const result = await runWorkerScenario({
    ...harness.options,
    signal: controller.signal,
  })

  assert.equal(result.ok, false)
  assert.equal(result.aborted, true)
  assert.ok(result.errors.some((error) => error.code === 'aborted'))
})

function createHarness(overrides = {}) {
  const produced = []
  let diagnosticsCalls = 0
  const producerBody = overrides.producerBody ?? (({ iteration }) => ({
    eventId: `event-${iteration + 1}`,
    sideEffectId: `effect-${iteration + 1}`,
    aggregateId: 'aggregate-1',
    sequence: iteration + 1,
  }))
  const fetch = async (url, init = {}) => {
    const path = new URL(url).pathname
    if (path === '/api/produce') {
      const request = JSON.parse(init.body)
      const body = producerBody(request)
      produced.push(body)
      return jsonResponse(body, 202)
    }
    if (path === '/api/diagnostics') {
      diagnosticsCalls += 1
      if (diagnosticsCalls === 1) return jsonResponse(strictDiagnostics())
      const finalDiagnostics = overrides.finalDiagnostics?.(produced) ?? strictDiagnostics({
        processedEventIds: produced.map((item) => item.eventId).filter(nonEmpty),
        sideEffects: produced.filter((item) => nonEmpty(item.sideEffectId)).map((item) => ({
          eventId: item.eventId,
          idempotencyKey: item.sideEffectId,
        })),
      })
      return jsonResponse(finalDiagnostics)
    }
    return jsonResponse({ error: 'not found' }, 404)
  }

  return {
    options: {
      apiBaseUrl: 'https://api.test',
      fetch,
      iterations: 1,
      targets: {
        producers: [{
          name: 'ledger-events',
          path: '/api/produce',
          method: 'POST',
          expectedStatus: 202,
          body: ({ iteration }) => ({ iteration }),
        }],
        diagnostics: {
          path: '/api/diagnostics',
          requiredPaths: ['backlog', 'oldestAgeSeconds', 'retries', 'deadLetters'],
        },
      },
    },
  }
}

function strictDiagnostics(overrides = {}) {
  return {
    ...baseDiagnostics(),
    processedEventIds: [],
    sideEffects: [],
    retriedEventIds: [],
    deadLetterEventIds: [],
    pendingEventIds: [],
    ...overrides,
  }
}

function baseDiagnostics() {
  return {
    backlog: 0,
    oldestAgeSeconds: 0,
    retries: 0,
    deadLetters: 0,
  }
}

function nonEmpty(value) {
  return value !== undefined && value !== null && String(value).trim().length > 0
}

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
