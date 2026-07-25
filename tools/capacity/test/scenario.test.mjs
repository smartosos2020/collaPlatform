import assert from 'node:assert/strict'
import { EventEmitter } from 'node:events'
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import os from 'node:os'
import path from 'node:path'
import test from 'node:test'

import { verifyEvidenceBundle } from '../src/evidence.mjs'
import {
  collectMetricsSnapshot,
  createDockerStatsAdapter,
  createJsonMetricsAdapter,
  loadScenarioConfig,
  parsePrometheusText,
  runCapacityScenario,
  validateScenarioConfig,
} from '../src/scenario.mjs'

test('scenario config requires explicit execution and abort contracts', () => {
  const invalid = validateScenarioConfig({
    schemaVersion: 'wrong',
    id: '',
    revision: 0,
    execution: {
      mode: 'random',
      warmup: { durationMs: -1 },
      abort: { maxErrorRate: 2 },
    },
    loaders: {},
  })
  assert.equal(invalid.ok, false)
  assert.match(invalid.errors.join('\n'), /schemaVersion/)
  assert.match(invalid.errors.join('\n'), /at least one loader/)

  const valid = validateScenarioConfig(baseConfig())
  assert.deepEqual(valid, { ok: true, errors: [] })
})

test('checked-in versioned scenario configs satisfy the scenario contract', async () => {
  const configDirectory = fileURLToPath(new URL('../config/scenarios/', import.meta.url))
  for (const file of ['s05-m1-unified.v1.json', 's05-smoke.v1.json']) {
    const config = await loadScenarioConfig(path.join(configDirectory, file))
    assert.deepEqual(validateScenarioConfig(config), { ok: true, errors: [] }, file)
  }
})

test('M1 gateway metrics observe the event-gateway consume path', async () => {
  const configDirectory = fileURLToPath(new URL('../config/scenarios/', import.meta.url))
  const config = await loadScenarioConfig(path.join(configDirectory, 's05-m1-unified.v1.json'))
  const gateways = config.metrics.prometheus.filter((source) => source.id.startsWith('gateway-'))
  assert.equal(gateways.length, 2)
  for (const source of gateways) {
    assert.ok(source.metricNames.includes('colla_realtime_redis_consume_total'))
    assert.ok(!source.metricNames.includes('colla_realtime_redis_publish_total'))
  }
})

test('formal scenarios reject execution before warmup without passing provenance', async () => {
  await assert.rejects(
    runCapacityScenario({
      ...baseConfig(),
      evidence: { requireProvenance: true },
    }, {
      evidenceDirectory: 'unused',
      manifest: { status: 'Blocked', blocked: true },
    }),
    /passing immutable provenance manifest/,
  )
  await assert.rejects(
    runCapacityScenario({
      ...baseConfig(),
      evidence: { requireProvenance: true },
    }, {
      evidenceDirectory: 'unused',
      manifest: { status: 'Pass', blocked: false },
    }),
    /passing immutable provenance manifest/,
  )
  await assert.rejects(
    runCapacityScenario({
      ...baseConfig(),
      evidence: { requireProvenance: true },
    }, {
      evidenceDirectory: 'unused',
      manifest: {
        schemaVersion: 'colla.capacity-provenance/v1',
        sourceCommit: 'a'.repeat(40),
        stack: { instanceNonce: 'n'.repeat(32) },
        seedExecution: { runId: 's05-m1-previous' },
      },
      provenanceEvidenceFiles: {},
      expectedSeedRunId: 's05-m1-current',
      expectedSourceCommit: 'b'.repeat(40),
      expectedStackInstanceNonce: 'x'.repeat(32),
    }),
    /expected seed runId|expected sourceCommit|expected runtime/,
  )
})

test('Prometheus parser keeps metric and label allowlists only', () => {
  const result = parsePrometheusText(`
# HELP http_requests_total Total requests
http_requests_total{method="GET",tenant="secret-tenant",status="200"} 12
ignored_metric{method="POST"} 99
process_cpu_usage 0.25
`, {
    metricNames: ['http_requests_total', 'process_cpu_usage'],
    labelAllowlist: ['method', 'status'],
  })
  assert.deepEqual(result, [
    {
      metric: 'http_requests_total',
      value: 12,
      labels: { method: 'GET', status: '200' },
    },
    { metric: 'process_cpu_usage', value: 0.25 },
  ])
  assert.doesNotMatch(JSON.stringify(result), /secret-tenant/)
})

test('metric collection supports injected Prometheus and Docker adapters', async () => {
  const calls = []
  const result = await collectMetricsSnapshot({
    prometheus: [{ id: 'api', endpoint: 'https://metrics.invalid', required: true }],
    docker: { id: 'stack', enabled: true, required: false },
  }, {
    prometheus: async (source) => {
      calls.push(`prometheus:${source.id}`)
      return [{ metric: 'up', value: 1 }]
    },
    docker: {
      async collect(source) {
        calls.push(`docker:${source.id}`)
        return [{ Name: 'api-1', CPUPerc: '1.00%' }]
      },
    },
  }, {
    stage: 'test',
    now: () => new Date('2026-07-25T00:00:00.000Z'),
  })
  assert.deepEqual(calls, ['prometheus:api', 'docker:stack'])
  assert.equal(result.every((snapshot) => snapshot.ok), true)
  assert.equal(result[0].capturedAt, '2026-07-25T00:00:00.000Z')
})

test('required metric sources fail when the endpoint omits a declared metric', async () => {
  const result = await collectMetricsSnapshot({
    prometheus: [{
      id: 'api',
      endpoint: 'https://metrics.invalid',
      required: true,
      metricNames: ['required_metric'],
    }],
  }, {
    prometheus: async () => [],
  }, {
    stage: 'test',
    now: () => new Date('2026-07-25T00:00:00.000Z'),
  })
  assert.equal(result[0].ok, false)
  assert.equal(result[0].data.length, 0)
})

test('Docker socket metrics only query the explicit capacity container allowlist', async () => {
  const requestedPaths = []
  const adapter = createDockerStatsAdapter({
    forceSocket: true,
    socketRequest: (options, callback) => {
      requestedPaths.push(options.path)
      const request = new EventEmitter()
      request.end = () => {
        const response = new EventEmitter()
        response.statusCode = 200
        callback(response)
        queueMicrotask(() => {
          response.emit('data', Buffer.from(JSON.stringify({
            cpu_stats: {
              cpu_usage: { total_usage: 200, percpu_usage: [1, 1] },
              system_cpu_usage: 1000,
              online_cpus: 2,
            },
            precpu_stats: {
              cpu_usage: { total_usage: 100 },
              system_cpu_usage: 500,
            },
            memory_stats: { usage: 1024, limit: 2048, stats: { cache: 0 } },
            networks: { eth0: { rx_bytes: 10, tx_bytes: 20 } },
            blkio_stats: { io_service_bytes_recursive: [] },
            pids_stats: { current: 3 },
          })))
          response.emit('end')
        })
      }
      return request
    },
  })
  const result = await adapter.collect({
    containers: ['colla-s05-capacity-api-a-1'],
    socketPath: '/var/run/docker.sock',
  })
  assert.deepEqual(requestedPaths, [
    '/containers/colla-s05-capacity-api-a-1/stats?stream=false',
  ])
  assert.equal(result[0].Name, 'colla-s05-capacity-api-a-1')
  assert.equal(result[0].CPUPerc, '40.00%')
})

test('JSON metrics resolve protected headers from environment without storing the secret', async () => {
  let requestHeaders
  const result = await collectMetricsSnapshot({
    json: [{
      id: 'collaboration',
      endpoint: 'https://metrics.invalid',
      required: true,
      metricNames: ['connections'],
      headerEnv: {
        'x-colla-collaboration-secret': 'COLLA_TEST_COLLABORATION_SECRET',
      },
    }],
  }, {
    json: createJsonMetricsAdapter({
      fetch: async (_endpoint, options) => {
        requestHeaders = options.headers
        return {
          ok: true,
          async json() {
            return { connections: 4, ignored: 9 }
          },
        }
      },
      environment: {
        COLLA_TEST_COLLABORATION_SECRET: 'runtime-only-secret',
      },
    }),
  }, {
    stage: 'test',
    now: () => new Date('2026-07-25T00:00:00.000Z'),
  })
  assert.deepEqual(requestHeaders, {
    'x-colla-collaboration-secret': 'runtime-only-secret',
  })
  assert.equal(result[0].ok, true)
  assert.deepEqual(result[0].data, [{ metric: 'connections', value: 4 }])
})

test('concurrent four-loader run creates a verifiable evidence bundle without secrets', async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'colla-capacity-scenario-'))
  const order = []
  try {
    const result = await runCapacityScenario(baseConfig(), {
      evidenceDirectory: directory,
      runId: 'scenario-pass-001',
      manifest: {
        gitCommit: 'abc123',
        token: 'must-not-be-written',
      },
      loaderOptions: {
        http: { password: 'must-not-be-written' },
      },
      loaders: Object.fromEntries(['http', 'websocket', 'collaboration', 'worker'].map((name) => [
        name,
        async (options) => {
          order.push(`${name}:${options.durationMs ?? 0}`)
          return successfulLoader(name)
        },
      ])),
      adapters: {
        prometheus: async () => [{ metric: 'up', value: 1, password: 'must-not-be-written' }],
        docker: async () => [{ Name: 'api-1', CPUPerc: '2.00%' }],
      },
    })
    assert.equal(result.run.status, 'COMPLETED')
    assert.equal(result.summary.conclusion, 'Pass')
    assert.equal(result.verification.ok, true)
    assert.equal(order.length, 4)
    assert.equal((await verifyEvidenceBundle(directory)).ok, true)

    const serialized = await readBundle(directory)
    assert.doesNotMatch(serialized, /must-not-be-written/)
    assert.match(serialized, /\[REDACTED\]/)
    const manifest = JSON.parse(await readFile(path.join(directory, 'manifest.json'), 'utf8'))
    assert.match(manifest.runtimeFingerprints.http, /^[0-9a-f]{64}$/)
  } finally {
    await rm(directory, { recursive: true, force: true })
  }
})

test('required warmup failure aborts before the measured run and can never Pass', async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'colla-capacity-aborted-scenario-'))
  let calls = 0
  const config = baseConfig({
    execution: {
      mode: 'serial',
      warmup: { enabled: true, required: true, mode: 'serial', durationMs: 1 },
      durationMs: 10,
      abort: {
        maxDurationMs: 1000,
        stopOnLoaderFailure: true,
        maxErrorCount: 0,
        maxErrorRate: 0,
        maxCollectorFailures: 0,
      },
    },
    loaders: {
      http: { enabled: true, options: {} },
      websocket: { enabled: false },
      collaboration: { enabled: false },
      worker: { enabled: false },
    },
  })
  try {
    const result = await runCapacityScenario(config, {
      evidenceDirectory: directory,
      runId: 'scenario-aborted-001',
      loaders: {
        http: async () => {
          calls += 1
          return failedLoader('http')
        },
      },
      adapters: {
        prometheus: async () => [],
        docker: async () => [],
      },
    })
    assert.equal(calls, 1)
    assert.equal(result.run.status, 'ABORTED')
    assert.equal(result.summary.conclusion, 'Fail')
    assert.equal(result.verification.ok, true)
    assert.ok(result.errors.some((error) => error.code === 'scenario_aborted'))
    const raw = await readFile(path.join(directory, 'raw', 'metrics.jsonl'), 'utf8')
    const loaderResult = raw
      .split(/\r?\n/)
      .filter(Boolean)
      .map((line) => JSON.parse(line))
      .find((entry) => entry.kind === 'loader.result')
    assert.deepEqual(loaderResult.errors, failedLoader('http').errors)
  } finally {
    await rm(directory, { recursive: true, force: true })
  }
})

test('warmup and measured loaders receive distinct probe run ids and explicit phase identity', async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'colla-capacity-phase-scenario-'))
  const invocations = []
  const config = baseConfig({
    execution: {
      mode: 'serial',
      warmup: { enabled: true, required: true, mode: 'serial', durationMs: 1 },
      durationMs: 1,
      abort: {
        maxDurationMs: 1000,
        stopOnLoaderFailure: true,
        maxErrorCount: 0,
        maxErrorRate: 0,
        maxCollectorFailures: 0,
      },
    },
    loaders: {
      http: { enabled: true, options: {}, warmupOptions: { iterations: 1 } },
      websocket: { enabled: false },
      collaboration: { enabled: false },
      worker: { enabled: true, options: {}, warmupOptions: { iterations: 1 } },
    },
    thresholds: {
      maxErrorRate: 0,
      minSamples: 1,
      loaders: {
        http: { required: true, maxErrorRate: 0, minSamples: 1 },
        worker: { required: true, maxErrorRate: 0, minSamples: 1 },
      },
    },
  })
  try {
    const result = await runCapacityScenario(config, {
      evidenceDirectory: directory,
      runId: 'scenario-phase-001',
      loaderOptions: {
        http: {
          runtimeValues: {
            probeRunId: 'legacy-run',
            probeRunIds: {
              warmup: '11111111-1111-4111-8111-111111111111',
              measured: '22222222-2222-4222-8222-222222222222',
            },
          },
        },
        worker: {
          runtimeValues: {
            probeRunId: 'legacy-run',
            probeRunIds: {
              warmup: '11111111-1111-4111-8111-111111111111',
              measured: '22222222-2222-4222-8222-222222222222',
            },
          },
        },
      },
      loaders: {
        http: async (options) => {
          invocations.push({
            phase: options.scenarioPhase,
            scenarioRunId: options.scenarioRunId,
            probeRunId: options.runtimeValues.probeRunId,
          })
          return successfulLoader('http')
        },
        worker: async (options) => {
          invocations.push({
            phase: options.scenarioPhase,
            scenarioRunId: options.scenarioRunId,
            probeRunId: options.runtimeValues.probeRunId,
          })
          return successfulLoader('worker')
        },
      },
      adapters: {
        prometheus: async () => [],
        docker: async () => [],
      },
    })
    assert.equal(result.summary.conclusion, 'Pass')
    assert.deepEqual(invocations.map(({ phase, scenarioRunId }) => ({ phase, scenarioRunId })), [
      { phase: 'warmup', scenarioRunId: 'scenario-phase-001' },
      { phase: 'warmup', scenarioRunId: 'scenario-phase-001' },
      { phase: 'measured', scenarioRunId: 'scenario-phase-001' },
      { phase: 'measured', scenarioRunId: 'scenario-phase-001' },
    ])
    assert.ok(invocations.every(({ probeRunId }) => /^[0-9a-f-]{36}$/.test(probeRunId)))
    assert.notEqual(invocations[0].probeRunId, '11111111-1111-4111-8111-111111111111')
    assert.notEqual(invocations[2].probeRunId, '22222222-2222-4222-8222-222222222222')
    assert.notEqual(invocations[0].probeRunId, invocations[1].probeRunId)
    assert.notEqual(invocations[2].probeRunId, invocations[3].probeRunId)
    assert.notEqual(invocations[0].probeRunId, invocations[2].probeRunId)
  } finally {
    await rm(directory, { recursive: true, force: true })
  }
})

test('loader option overlays merge nested targets without dropping runtime contracts', async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'colla-capacity-merge-scenario-'))
  const invocations = []
  const config = baseConfig({
    execution: {
      mode: 'serial',
      warmup: { enabled: true, required: true, mode: 'serial', durationMs: 1 },
      durationMs: 1,
      abort: {
        maxDurationMs: 1000,
        stopOnLoaderFailure: true,
        maxErrorCount: 0,
        maxErrorRate: 0,
        maxCollectorFailures: 0,
      },
    },
    loaders: {
      websocket: {
        enabled: true,
        options: { targets: { expectedFanout: 8 } },
        warmupOptions: { connections: 2, targets: { expectedFanout: 2 } },
      },
      http: { enabled: false },
      collaboration: { enabled: false },
      worker: { enabled: false },
    },
    thresholds: {
      maxErrorRate: 0,
      minSamples: 1,
      loaders: {
        websocket: { required: true, maxErrorRate: 0, minSamples: 1 },
      },
    },
  })
  try {
    const result = await runCapacityScenario(config, {
      evidenceDirectory: directory,
      loaderOptions: {
        websocket: {
          targets: {
            trigger: { name: 'runtime-trigger' },
            calibration: { name: 'runtime-calibration' },
          },
        },
      },
      loaders: {
        websocket: async (options) => {
          invocations.push(options)
          return successfulLoader('websocket')
        },
      },
      adapters: {
        prometheus: async () => [],
        docker: async () => [],
      },
    })
    assert.equal(result.summary.conclusion, 'Pass')
    assert.equal(invocations[0].targets.expectedFanout, 2)
    assert.equal(invocations[0].targets.trigger.name, 'runtime-trigger')
    assert.equal(invocations[0].targets.calibration.name, 'runtime-calibration')
    assert.equal(invocations[1].targets.expectedFanout, 8)
  } finally {
    await rm(directory, { recursive: true, force: true })
  }
})

test('serial mode preserves loader ordering', async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'colla-capacity-serial-scenario-'))
  const order = []
  const config = baseConfig({
    execution: {
      mode: 'serial',
      warmup: { enabled: false, required: false, durationMs: 0 },
      durationMs: 0,
      abort: {
        maxDurationMs: 1000,
        stopOnLoaderFailure: false,
        maxErrorCount: 0,
        maxErrorRate: 0,
        maxCollectorFailures: 0,
      },
    },
  })
  try {
    const loaders = Object.fromEntries(['http', 'websocket', 'collaboration', 'worker'].map((name) => [
      name,
      async () => {
        order.push(name)
        return successfulLoader(name)
      },
    ]))
    const result = await runCapacityScenario(config, {
      evidenceDirectory: directory,
      loaders,
      adapters: {
        prometheus: async () => [],
        docker: async () => [],
      },
    })
    assert.deepEqual(order, ['http', 'websocket', 'collaboration', 'worker'])
    assert.equal(result.summary.conclusion, 'Pass')
  } finally {
    await rm(directory, { recursive: true, force: true })
  }
})

test('max duration aborts a cooperative loader and records a non-Pass bundle', async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'colla-capacity-timeout-scenario-'))
  const config = baseConfig({
    execution: {
      mode: 'serial',
      warmup: { enabled: false, required: false, durationMs: 0 },
      durationMs: 0,
      abort: {
        maxDurationMs: 15,
        stopOnLoaderFailure: false,
        maxErrorCount: 0,
        maxErrorRate: 0,
        maxCollectorFailures: 0,
      },
    },
    loaders: {
      http: { enabled: true, options: {} },
      websocket: { enabled: false },
      collaboration: { enabled: false },
      worker: { enabled: false },
    },
    metrics: {
      intervalMs: 1000,
      prometheus: [],
      docker: { id: 'stack', enabled: false },
    },
    thresholds: {
      maxErrorRate: 0,
      minSamples: 1,
      loaders: { http: { required: true, minSamples: 1 } },
    },
  })
  try {
    const result = await runCapacityScenario(config, {
      evidenceDirectory: directory,
      runId: 'scenario-timeout-001',
      loaders: {
        http: async ({ signal }) => new Promise((resolve) => {
          signal.addEventListener('abort', () => resolve(successfulLoader('http')), { once: true })
        }),
      },
    })
    assert.equal(result.run.status, 'ABORTED')
    assert.equal(result.run.abortReason, 'max-duration-exceeded')
    assert.equal(result.summary.conclusion, 'Fail')
    assert.equal(result.verification.ok, true)
  } finally {
    await rm(directory, { recursive: true, force: true })
  }
})

function baseConfig(overrides = {}) {
  const base = {
    schemaVersion: 'colla.capacity-scenario/v1',
    id: 'test-unified',
    revision: 1,
    execution: {
      mode: 'concurrent',
      warmup: { enabled: false, required: false, durationMs: 0 },
      durationMs: 5,
      abort: {
        maxDurationMs: 1000,
        stopOnLoaderFailure: true,
        maxErrorCount: 0,
        maxErrorRate: 0,
        maxCollectorFailures: 0,
      },
    },
    loaders: {
      http: { enabled: true, options: {} },
      websocket: { enabled: true, options: {} },
      collaboration: { enabled: true, options: {} },
      worker: { enabled: true, options: {} },
    },
    metrics: {
      intervalMs: 1000,
      prometheus: [{ id: 'api', endpoint: 'https://metrics.invalid', required: true }],
      docker: { id: 'stack', enabled: true, required: true },
    },
    thresholds: {
      maxErrorRate: 0,
      minSamples: 4,
      loaders: Object.fromEntries(['http', 'websocket', 'collaboration', 'worker'].map((name) => [
        name,
        { required: true, maxErrorRate: 0, minSamples: 1 },
      ])),
    },
  }
  return {
    ...base,
    ...overrides,
    execution: overrides.execution ?? base.execution,
    loaders: overrides.loaders ?? base.loaders,
    metrics: overrides.metrics ?? base.metrics,
    thresholds: overrides.thresholds ?? base.thresholds,
  }
}

function successfulLoader(name) {
  return {
    scenario: name,
    ok: true,
    durationMs: 5,
    samples: [{ operation: `${name}.sample`, latencyMs: 5, ok: true }],
    summary: {
      overall: { count: 1, success: 1, failure: 0, p50: 5, p95: 5, p99: 5 },
      operations: {},
    },
    metrics: { completed: 1 },
    errors: [],
  }
}

function failedLoader(name) {
  return {
    scenario: name,
    ok: false,
    durationMs: 1,
    samples: [{ operation: `${name}.sample`, latencyMs: 1, ok: false }],
    summary: {
      overall: { count: 1, success: 0, failure: 1, p50: 1, p95: 1, p99: 1 },
      operations: {},
    },
    metrics: {},
    errors: [{ code: 'warmup_error', message: 'warmup failed' }],
  }
}

async function readBundle(directory) {
  const files = [
    'run.json',
    'manifest.json',
    'threshold.json',
    'raw/metrics.jsonl',
    'summary.json',
    'errors.json',
    'checksums.json',
  ]
  return (await Promise.all(files.map((file) => readFile(path.join(directory, file), 'utf8')))).join('\n')
}
