import assert from 'node:assert/strict'
import { readFile, readdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import test from 'node:test'

import { validateScenarioConfig } from '../src/scenario.mjs'

const capacityConfigDirectory = fileURLToPath(new URL('../config/', import.meta.url))
const scenarioDirectory = path.join(capacityConfigDirectory, 'scenarios')
const expectedFiles = [
  's05-m2-01-idle-warmup.v1.json',
  's05-m2-02-http-read.v1.json',
  's05-m2-03-http-write-file.v1.json',
  's05-m2-04-worker-sustained-burst.v1.json',
  's05-m2-05-websocket-1000.v1.json',
  's05-m2-06-collaboration-100.v1.json',
  's05-m2-07-mixed-c1.v1.json',
  's05-m2-08-million-query-security.v1.json',
  's05-m2-09-stair-saturation-downshift.v1.json',
  's05-m2-10-tuning-comparison.v1.json',
]
const expectedContainers = [
  'colla-s05-capacity-postgres-1',
  'colla-s05-capacity-redis-1',
  'colla-s05-capacity-minio-1',
  'colla-s05-capacity-api-a-1',
  'colla-s05-capacity-api-b-1',
  'colla-s05-capacity-worker-a-1',
  'colla-s05-capacity-worker-b-1',
  'colla-s05-capacity-event-gateway-a-1',
  'colla-s05-capacity-event-gateway-b-1',
  'colla-s05-capacity-collaboration-a-1',
  'colla-s05-capacity-collaboration-b-1',
  'colla-s05-capacity-web-1',
  'colla-s05-capacity-nginx-1',
]

const c1 = await readJson(path.join(capacityConfigDirectory, 'c1.v1.json'))
const topology = await readJson(path.join(capacityConfigDirectory, 'topology.v1.json'))
const seed = await readJson(path.join(capacityConfigDirectory, 'seed.v1.json'))
const configs = new Map(await Promise.all(expectedFiles.map(async (file) => [
  file,
  await readJson(path.join(scenarioDirectory, file)),
])))
const c1Thresholds = new Map(c1.thresholds.map((threshold) => [threshold.id, threshold]))

test('S05 M2 scenario contract file set is complete and versioned', async () => {
  const actual = (await readdir(scenarioDirectory))
    .filter((file) => /^s05-m2-.*\.v1\.json$/.test(file))
    .sort()
  assert.deepEqual(actual, expectedFiles)

  for (const [file, config] of configs) {
    assert.equal(config.schemaVersion, 'colla.capacity-scenario/v1', file)
    assert.equal(config.revision, 1, file)
    assert.equal(config.id, file.replace('.v1.json', ''), file)
    assert.match(config.contract.milestoneTask, /^PLATFORM-SCALE-S05-M2-T\d{2}/, file)
    assert.equal(config.contract.c1ContractId, c1.id, file)
    assert.equal(config.evidence?.requireProvenance, true, `${file} must require immutable provenance`)
    assert.deepEqual(validateScenarioConfig(config), { ok: true, errors: [] }, file)
  }
})

test('S05 M2 contracts use the frozen C1 conclusions and never loosen C1 thresholds', () => {
  const c1Unexpected5xx = c1Thresholds.get('unexpected-5xx-rate')
  for (const [file, config] of configs) {
    assert.deepEqual(config.contract.conclusionPolicy.allowed, c1.conclusions.allowed, file)
    assert.equal(config.contract.conclusionPolicy.aborted, 'Fail', file)
    assert.equal(config.contract.conclusionPolicy.incompleteEvidence, 'Not-Committed', file)
    assert.equal(config.contract.conclusionPolicy.targetMiss, 'Fail', file)
    assert.equal(config.contract.conclusionPolicy.lowerEnvelope, 'Bounded', file)
    assert.ok(config.execution.abort.maxErrorRate <= c1Unexpected5xx.value, file)

    for (const acceptance of config.contract.acceptance ?? []) {
      const frozen = c1Thresholds.get(acceptance.id)
      if (!frozen) continue
      assert.deepEqual(
        pick(acceptance, ['id', 'metric', 'operator', 'value', 'unit']),
        frozen,
        `${file}:${acceptance.id}`,
      )
    }
  }
})

test('S05 M2 metric sources are required, protected through headerEnv and scoped to the capacity stack', () => {
  const requiredSources = new Map([
    ['s05-m2-01-idle-warmup.v1.json', ['api-a', 'api-b', 'worker-a', 'worker-b', 'gateway-a', 'gateway-b', 'collaboration-a', 'collaboration-b']],
    ['s05-m2-02-http-read.v1.json', ['api-a', 'api-b']],
    ['s05-m2-03-http-write-file.v1.json', ['api-a', 'api-b', 'worker-a', 'worker-b']],
    ['s05-m2-04-worker-sustained-burst.v1.json', ['worker-a', 'worker-b']],
    ['s05-m2-05-websocket-1000.v1.json', ['gateway-a', 'gateway-b']],
    ['s05-m2-06-collaboration-100.v1.json', ['collaboration-a', 'collaboration-b']],
    ['s05-m2-07-mixed-c1.v1.json', ['api-a', 'api-b', 'worker-a', 'worker-b', 'gateway-a', 'gateway-b', 'collaboration-a', 'collaboration-b']],
    ['s05-m2-08-million-query-security.v1.json', ['api-a', 'api-b']],
    ['s05-m2-09-stair-saturation-downshift.v1.json', ['api-a', 'api-b']],
    ['s05-m2-10-tuning-comparison.v1.json', ['api-a', 'api-b', 'worker-a', 'worker-b', 'gateway-a', 'gateway-b', 'collaboration-a', 'collaboration-b']],
  ])

  for (const [file, config] of configs) {
    const sources = [...(config.metrics.prometheus ?? []), ...(config.metrics.json ?? [])]
    assert.deepEqual(sources.map((source) => source.id).sort(), requiredSources.get(file).sort(), file)
    assert.equal(sources.every((source) => source.required === true), true, file)
    assert.equal(sources.every((source) => Array.isArray(source.metricNames) && source.metricNames.length > 0), true, file)

    for (const source of config.metrics.json ?? []) {
      assert.deepEqual(source.headerEnv, {
        'x-colla-collaboration-secret': 'COLLA_COLLABORATION_INTERNAL_SECRET',
      }, `${file}:${source.id}`)
      assert.equal(Object.hasOwn(source, 'headers'), false, `${file}:${source.id}`)
    }

    assert.equal(config.metrics.docker.enabled, true, file)
    assert.equal(config.metrics.docker.required, true, file)
    assert.deepEqual(config.metrics.docker.containers, expectedContainers, file)
    assert.equal(
      config.metrics.docker.containers.every((name) => name.startsWith('colla-s05-capacity-')),
      true,
      file,
    )
  }
})

test('S05 M2 idle, HTTP and Worker contracts lock target values and measured durations', () => {
  const idle = configs.get('s05-m2-01-idle-warmup.v1.json')
  assert.deepEqual(idle.contract.phases, [
    { name: 'idle', durationMs: 300000, load: 'none' },
    { name: 'warmup', durationMs: 300000, httpRps: 10 },
    { name: 'stable', durationMs: 300000, httpRps: 10 },
  ])
  assert.deepEqual(idle.contract.preconditions, {
    workerBacklog: 0,
    ordinaryWebSockets: 0,
    collaborationConnections: 0,
    requiredHealthyReplicas: Object.values(topology.roles)
      .filter((role) => role.lifecycle === 'service')
      .reduce((sum, role) => sum + role.replicas, 0),
  })

  const read = configs.get('s05-m2-02-http-read.v1.json')
  assert.equal(read.contract.targets.httpRps, c1.targets.httpRps)
  assert.equal(read.loaders.http.options.targetRps, c1.targets.httpRps)
  assert.deepEqual(read.loaders.http.options.phaseWeights, { read: 1, write: 0 })
  assert.equal(read.execution.durationMs, 600000)
  assert.equal(read.thresholds.maxP95Ms, c1Thresholds.get('http-read-p95').value)

  const write = configs.get('s05-m2-03-http-write-file.v1.json')
  assert.equal(write.contract.targets.httpRps, c1.targets.httpRps)
  assert.deepEqual(write.loaders.http.options.phaseWeights, { read: 0, write: 1 })
  assert.equal(write.execution.durationMs, 600000)
  assert.equal(write.thresholds.maxP95Ms, c1Thresholds.get('http-write-p95').value)
  assert.deepEqual(write.contract.requiredAssertions, [
    'idempotency-replay-same-fact',
    'outbox-and-audit-complete',
    'file-metadata-object-consistent',
    'no-partial-business-fact',
  ])

  const worker = configs.get('s05-m2-04-worker-sustained-burst.v1.json')
  assert.deepEqual(worker.contract.targets, {
    sustainedEventsPerSecond: c1.targets.workerSustainedEventsPerSecond,
    sustainedSeconds: 300,
    burstEventsPerSecond: c1.targets.workerBurstEventsPerSecond,
    burstSeconds: c1.targets.workerBurstSeconds,
  })
  assert.equal(worker.loaders.worker.options.rateMode, 'both')
  assert.equal(worker.execution.durationMs, 300000)
})

test('S05 M2 realtime and mixed contracts lock C1 concurrency targets', () => {
  const websocket = configs.get('s05-m2-05-websocket-1000.v1.json')
  assert.equal(websocket.contract.targets.ordinaryWebSockets, c1.targets.ordinaryWebSockets)
  assert.equal(websocket.loaders.websocket.options.connections, c1.targets.ordinaryWebSockets)
  assert.equal(websocket.execution.durationMs, 600000)

  const collaboration = configs.get('s05-m2-06-collaboration-100.v1.json')
  assert.equal(collaboration.contract.targets.yjsClients, c1.targets.yjsClients)
  assert.equal(collaboration.contract.targets.yjsRooms, c1.targets.yjsRooms)
  assert.equal(
    collaboration.contract.targets.yjsRooms * collaboration.loaders.collaboration.options.clientsPerRoom,
    c1.targets.yjsClients,
  )
  assert.equal(collaboration.execution.durationMs, 600000)

  const mixed = configs.get('s05-m2-07-mixed-c1.v1.json')
  assert.equal(mixed.execution.mode, 'concurrent')
  assert.equal(mixed.execution.durationMs, 900000)
  assert.deepEqual(mixed.contract.targets, {
    httpRps: c1.targets.httpRps,
    httpReadWeight: 0.8,
    httpWriteWeight: 0.2,
    workerSustainedEventsPerSecond: c1.targets.workerSustainedEventsPerSecond,
    ordinaryWebSockets: c1.targets.ordinaryWebSockets,
    yjsClients: c1.targets.yjsClients,
    yjsRooms: c1.targets.yjsRooms,
  })
  assert.deepEqual(
    new Set(mixed.contract.acceptance.map((entry) => entry.id)),
    new Set(c1.thresholds.map((entry) => entry.id)),
  )
})

test('S05 M2 million-scale query and security contract matches the frozen seed', () => {
  const config = configs.get('s05-m2-08-million-query-security.v1.json')
  assert.deepEqual(config.contract.dataset, {
    workItems: c1.targets.workItems,
    knowledgeItems: c1.targets.knowledgeItems,
    knowledgeBlocks: c1.targets.knowledgeBlocks,
    workspaceCount: seed.workspaceCount,
  })
  assert.deepEqual(config.contract.targets.securityStates, [
    'cross-workspace',
    'disabled-member',
    'revoked-permission',
    'deleted-resource',
  ])
  assert.deepEqual(
    config.contract.acceptance.find((entry) => entry.id === 'authorization-errors'),
    {
      id: 'authorization-errors',
      metric: 'security.authorization_error.count',
      operator: 'eq',
      value: 0,
      unit: 'count',
    },
  )
})

test('S05 M2 stair and tuning contracts cannot silently change the phase plan or comparison budget', () => {
  const stair = configs.get('s05-m2-09-stair-saturation-downshift.v1.json')
  assert.equal(stair.contract.phaseRunnerRequired, true)
  assert.deepEqual(
    stair.contract.phases.map(({ name, durationMs, httpRps }) => ({ name, durationMs, httpRps })),
    [
      { name: 'stair-050', durationMs: 300000, httpRps: 50 },
      { name: 'stair-100', durationMs: 300000, httpRps: 100 },
      { name: 'c1-150', durationMs: 300000, httpRps: 150 },
      { name: 'saturation-200', durationMs: 300000, httpRps: 200 },
      { name: 'saturation-250', durationMs: 300000, httpRps: 250 },
      { name: 'downshift-075', durationMs: 300000, httpRps: 75 },
    ],
  )
  assert.equal(stair.contract.conclusionPolicy.phasePlanIgnored, 'Not-Committed')

  const tuning = configs.get('s05-m2-10-tuning-comparison.v1.json')
  assert.deepEqual(tuning.contract.comparison.arms, ['baseline', 'candidate'])
  assert.equal(tuning.contract.comparison.sameCommitRequired, true)
  assert.equal(tuning.contract.comparison.sameSeedChecksumRequired, true)
  assert.equal(tuning.contract.comparison.sameTopologyBudgetRequired, true)
  assert.equal(tuning.contract.comparison.sameLoadRequired, true)
  assert.equal(tuning.contract.comparison.minimumRunsPerArm, 2)
  assert.equal(tuning.contract.comparison.preserveFailedEvidence, true)
  assert.equal(tuning.contract.conclusionPolicy.singleArmOnly, 'Not-Committed')
})

test('S05 M2 checked-in configs contain no plaintext secret or runtime credential field', () => {
  const obviousSecret = /(bearer\s+|-----begin [^-]+ private key-----|akia[0-9a-z]{16}|ghp_[0-9a-z]+|sk-[0-9a-z]+|eyj[a-z0-9_-]*\.[a-z0-9_-]+\.)/i
  for (const [file, config] of configs) {
    visit(config, (key, value) => {
      assert.notEqual(key.toLowerCase(), 'headers', `${file}:${key}`)
      if (typeof value !== 'string') return
      assert.doesNotMatch(value, obviousSecret, `${file}:${key}`)
      if (/(password|secret|token)/i.test(key)) {
        assert.match(value, /^[A-Z][A-Z0-9_]+$/, `${file}:${key}`)
      }
    })
  }
})

async function readJson(file) {
  return JSON.parse(await readFile(file, 'utf8'))
}

function pick(value, keys) {
  return Object.fromEntries(keys.map((key) => [key, value[key]]))
}

function visit(value, visitor) {
  if (Array.isArray(value)) {
    for (const item of value) visit(item, visitor)
    return
  }
  if (!value || typeof value !== 'object') return
  for (const [key, entry] of Object.entries(value)) {
    visitor(key, entry)
    visit(entry, visitor)
  }
}
