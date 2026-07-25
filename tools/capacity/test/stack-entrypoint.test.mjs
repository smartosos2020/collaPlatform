import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

import {
  assertGuardedReason,
  CAPACITY_STACK_SERVICES,
  createCapacityDryRun,
  createCapacityRunPlan,
  createSeedCycleManifest,
  defaultCapacityRunId,
  validateCapturedStep,
} from '../../../deploy/capacity/orchestrate.mjs'
import { sha256, stableStringify } from '../src/contract.mjs'
import { validateCapacityRunManifest } from '../src/provenance.mjs'

const repositoryRoot = fileURLToPath(new URL('../../..', import.meta.url))
const stackFile = path.join(repositoryRoot, 'deploy', 'capacity', 'stack.mjs')
const evidenceRoot = path.join(repositoryRoot, '.local-reports', 'capacity')
const envFile = path.join(repositoryRoot, 'deploy', 'capacity', 'capacity.env')
const composePrefix = [
  'compose',
  '--project-name',
  'colla-s05-capacity',
  '--env-file',
  envFile,
  '-f',
  path.join(repositoryRoot, 'deploy', 'docker-compose.prod.yml'),
  '-f',
  path.join(repositoryRoot, 'deploy', 'capacity', 'docker-compose.capacity.yml'),
]

function plan(overrides = {}) {
  return createCapacityRunPlan({
    repositoryRoot,
    evidenceRoot,
    envFile,
    environment: {
      COMPOSE_PROJECT_NAME: 'colla-s05-capacity',
      POSTGRES_USER: 'colla',
      POSTGRES_DB: 'colla_platform',
      SOURCE_COMMIT: 'a'.repeat(40),
      CAPACITY_STACK_INSTANCE_NONCE: 'n'.repeat(32),
    },
    composePrefix,
    stackFile,
    runId: 's05-m1-entrypoint-test',
    confirmed: true,
    reason: 'execute the isolated M1 capacity workflow',
    ...overrides,
  })
}

test('M1 run plan covers seed, preflight, provenance, runner, and evidence verification', () => {
  const result = plan()
  const serialized = result.steps.map((step) => [step.command, ...step.args].join(' ')).join('\n')

  assert.match(serialized, /seed plan --seed-id s05-c1/)
  assert.match(serialized, /seed apply --plan .*seed-plan\.json --sql .*seed-apply\.sql/)
  assert.match(serialized, /seed verify --plan .*seed-plan\.json --sql .*seed-verify\.sql/)
  assert.match(serialized, /seed clean-check --plan .*seed-plan\.json --sql .*seed-clean-check\.sql/)
  assert.match(serialized, /seed cleanup --plan .*seed-plan\.json --sql .*seed-cleanup\.sql/)
  assert.match(serialized, /preflight capture .*preflight-baseline\.json/)
  assert.match(serialized, /preflight capture .*preflight-current\.json/)
  assert.match(serialized, /provenance .*--seed-plan .*seed-plan\.json/)
  assert.match(serialized, /--profile capacity run --rm --no-deps capacity-runner scenario run/)
  assert.match(
    serialized,
    /--runtime \/workspace\/tools\/capacity\/config\/runtime\/s05-m1\.v1\.json/,
  )
  assert.match(
    serialized,
    /--manifest \/evidence\/runs\/s05-m1-entrypoint-test\/run-manifest\.json/,
  )
  assert.match(serialized, /--expected-seed-run-id s05-m1-entrypoint-test/)
  assert.match(serialized, new RegExp(`--expected-source-commit ${'a'.repeat(40)}`))
  assert.match(serialized, new RegExp(`--expected-stack-instance-nonce ${'n'.repeat(32)}`))
  assert.match(
    serialized,
    /evidence verify --directory .*s05-m1-entrypoint-test.*scenario/,
  )

  const databaseSteps = result.steps.filter((step) => step.stdinPath)
  assert.equal(databaseSteps.length, 8)
  assert.ok(databaseSteps.every((step) => step.command === 'docker'))
  assert.ok(databaseSteps.every((step) => step.args.includes('postgres')))
  assert.ok(databaseSteps.every((step) => step.args.includes('--username')))
  assert.ok(databaseSteps.every((step) => step.args.includes('colla')))
  assert.ok(databaseSteps.every((step) => step.args.includes('colla_platform')))
  assert.equal(
    databaseSteps.filter((step) => step.capture === 'seed-verification').length,
    3,
  )
  assert.equal(
    databaseSteps.filter((step) => step.capture === 'seed-cleanup-verification').length,
    1,
  )
  assert.equal(
    databaseSteps.filter((step) => step.capture === 'seed-clean-verification').length,
    1,
  )
  assert.ok(databaseSteps.filter((step) => step.capture).every((step) => step.outputPath))
  assert.deepEqual(
    databaseSteps.filter((step) => step.capture).map((step) => step.cycleStep),
    [
      'clean-before-first-apply',
      'first-initialization',
      'idempotent-reapply',
      'cleanup',
      'second-initialization',
    ],
  )

  const provenanceIndex = result.steps.findIndex((step) => step.label.includes('immutable provenance'))
  const firstMutationIndex = result.steps.findIndex((step) => step.label === 'apply the first clean seed initialization')
  assert.ok(provenanceIndex > 0)
  assert.ok(provenanceIndex < firstMutationIndex)
})

test('stack up service set excludes the one-shot profile runner', () => {
  assert.ok(CAPACITY_STACK_SERVICES.includes('nginx'))
  assert.ok(CAPACITY_STACK_SERVICES.includes('maintenance'))
  assert.ok(!CAPACITY_STACK_SERVICES.includes('capacity-runner'))
})

test('run plan fails closed for unsafe projects, missing confirmation, reason, and run id', () => {
  assert.throws(
    () => plan({
      environment: {
        COMPOSE_PROJECT_NAME: 'collaplatform',
        POSTGRES_USER: 'colla',
        POSTGRES_DB: 'colla_platform',
        SOURCE_COMMIT: 'a'.repeat(40),
        CAPACITY_STACK_INSTANCE_NONCE: 'n'.repeat(32),
      },
    }),
    /requires COMPOSE_PROJECT_NAME=colla-s05-capacity/,
  )
  assert.throws(
    () => plan({
      environment: {
        COMPOSE_PROJECT_NAME: 'colla-s05-capacity-other',
        POSTGRES_USER: 'colla',
        POSTGRES_DB: 'colla_platform',
        SOURCE_COMMIT: 'a'.repeat(40),
        CAPACITY_STACK_INSTANCE_NONCE: 'n'.repeat(32),
      },
    }),
    /requires COMPOSE_PROJECT_NAME=colla-s05-capacity/,
  )
  assert.throws(() => plan({ confirmed: false }), /requires --confirm/)
  assert.throws(() => plan({ reason: 'too short' }), /specific --reason/)
  assert.throws(() => plan({ runId: '../collaplatform' }), /run --run-id must match/)
  assert.throws(
    () => plan({
      environment: {
        COMPOSE_PROJECT_NAME: 'colla-s05-capacity',
        POSTGRES_USER: 'colla',
        POSTGRES_DB: 'colla_platform',
        SOURCE_COMMIT: 'a'.repeat(40),
      },
    }),
    /requires a valid CAPACITY_STACK_INSTANCE_NONCE/,
  )
  assert.throws(
    () => plan({
      environment: {
        COMPOSE_PROJECT_NAME: 'colla-s05-capacity',
        POSTGRES_USER: 'colla',
        POSTGRES_DB: 'colla_platform',
        SOURCE_COMMIT: 'a'.repeat(40),
        CAPACITY_STACK_INSTANCE_NONCE: 'too-short',
      },
    }),
    /requires a valid CAPACITY_STACK_INSTANCE_NONCE/,
  )
})

test('captured step validation requires healthy services and passing seed results', () => {
  const result = plan()
  const runningStep = result.steps.find((step) => step.capture === 'stack-health')
  assert.throws(
    () => validateCapturedStep(
      runningStep,
      JSON.stringify([{ Service: 'postgres', State: 'running', Health: 'healthy' }]),
      result.requiredRunningServices,
    ),
    /missing services/,
  )
  const healthyContainers = result.requiredRunningServices.map((service) => ({
    Service: service,
    State: 'running',
    Health: 'healthy',
  }))
  assert.deepEqual(validateCapturedStep(
    runningStep,
    JSON.stringify(healthyContainers),
    result.requiredRunningServices,
  ), {
    ok: true,
    services: result.requiredRunningServices.map((service) => ({
      service,
      state: 'running',
      health: 'healthy',
    })),
  })
  const unhealthyContainers = healthyContainers.map((container) => (
    container.Service === 'redis' ? { ...container, Health: 'starting' } : container
  ))
  assert.throws(
    () => validateCapturedStep(
      runningStep,
      unhealthyContainers.map(JSON.stringify).join('\n'),
      result.requiredRunningServices,
    ),
    /not healthy services: redis/,
  )
  assert.throws(
    () => validateCapturedStep(
      runningStep,
      JSON.stringify([...healthyContainers, healthyContainers[0]]),
      result.requiredRunningServices,
    ),
    /duplicate services: postgres/,
  )
  assert.throws(
    () => validateCapturedStep(
      runningStep,
      JSON.stringify([...healthyContainers, { State: 'running', Health: 'healthy' }]),
      result.requiredRunningServices,
    ),
    /invalid service records/,
  )

  const seedStep = result.steps.find((step) => step.capture === 'seed-verification')
  assert.doesNotThrow(() => validateCapturedStep(seedStep, '{"ok":true,"records":1}', []))
  assert.throws(
    () => validateCapturedStep(seedStep, '{"ok":false,"records":0}', []),
    /Seed verification failed/,
  )
  assert.throws(() => validateCapturedStep(seedStep, 'not-json', []), /expected JSON/)

  const cleanStep = result.steps.find((step) => step.capture === 'seed-clean-verification')
  assert.doesNotThrow(() => validateCapturedStep(
    cleanStep,
    '{"ok":true,"fixtureRuns":0,"conflictingRuns":0}',
    [],
  ))
  assert.throws(
    () => validateCapturedStep(cleanStep, '{"ok":false,"fixtureRuns":1}', []),
    /Seed clean-state verification failed/,
  )

  const cleanupStep = result.steps.find((step) => step.capture === 'seed-cleanup-verification')
  assert.doesNotThrow(() => validateCapturedStep(
    cleanupStep,
    '{"ok":true,"fixtureRuns":0,"fixtureRecords":0,"fixtureWorkspaces":0}',
    [],
  ))
  assert.throws(
    () => validateCapturedStep(cleanupStep, '{"ok":false,"fixtureRuns":1}', []),
    /Seed cleanup verification failed/,
  )
})

test('capacity runner has an explicit image and repository-root build context', () => {
  const compose = readFileSync(
    path.join(repositoryRoot, 'deploy', 'capacity', 'docker-compose.capacity.yml'),
    'utf8',
  )
  assert.match(compose, /capacity-runner:\r?\n\s+profiles: \[capacity\]\r?\n\s+image: \$\{CAPACITY_RUNNER_IMAGE/)
  assert.match(compose, /capacity-runner:[\s\S]*?build:\r?\n\s+context: \.\.\r?\n\s+dockerfile: tools\/capacity\/Dockerfile/)
  assert.doesNotMatch(compose, /capacity-runner:[\s\S]*?context: \.\.\/\.\./)
  const dockerfile = readFileSync(
    path.join(repositoryRoot, 'tools', 'capacity', 'Dockerfile'),
    'utf8',
  )
  assert.match(dockerfile, /ARG SOURCE_COMMIT=unknown/)
  assert.match(dockerfile, /LABEL org\.opencontainers\.image\.revision=\$SOURCE_COMMIT/)

  const environmentExample = readFileSync(
    path.join(repositoryRoot, 'deploy', 'capacity', 'capacity.env.example'),
    'utf8',
  )
  assert.match(
    environmentExample,
    /^CAPACITY_STACK_INSTANCE_NONCE=replace-with-capacity-stack-instance-nonce$/m,
  )
  assert.match(environmentExample, /^CAPACITY_EVENT_LEASE_DURATION=120s$/m)
  assert.match(
    compose,
    /COLLA_EVENT_LEASE_DURATION: \$\{CAPACITY_EVENT_LEASE_DURATION:-120s\}/,
  )
  const stackEntrypoint = readFileSync(stackFile, 'utf8')
  assert.match(
    stackEntrypoint,
    /\['replace-with-capacity-stack-instance-nonce', secret\(32\)\]/,
  )
  assert.match(
    stackEntrypoint,
    /stackInstanceNonce: environment\.CAPACITY_STACK_INSTANCE_NONCE/,
  )
})

test('seed-cycle evidence is checksum-bound to passing immutable provenance', () => {
  const seedIdentity = {
    seedId: 's05-evidence-seed',
    checksum: 'c'.repeat(64),
    fixtureName: 'capacity-s05-evidence-seed',
  }
  const provenanceImmutable = {
    schemaVersion: 'colla.capacity-provenance/v1',
    git: { commit: 'a'.repeat(40), dirty: false },
    sourceCommit: 'a'.repeat(40),
    preflight: {
      baselineFingerprint: 'f'.repeat(64),
      currentFingerprint: 'f'.repeat(64),
      drifted: false,
      resourceEligibility: {
        hostFreeMemorySatisfied: true,
        repositoryDiskFreeSatisfied: true,
        tempDiskFreeSatisfied: true,
        dockerDataDiskFreeSatisfied: true,
        clockSynchronizationSatisfied: true,
      },
    },
    contract: { digest: 'b'.repeat(64) },
    topology: { digest: 'c'.repeat(64) },
    seedPlan: { ...seedIdentity, fingerprint: 'd'.repeat(64) },
    compose: { sha256: 'e'.repeat(64) },
    stack: { instanceNonce: 'n'.repeat(32) },
    images: [{
      name: 'api-a',
      id: `sha256:${'1'.repeat(64)}`,
      repoDigests: [],
      revision: 'a'.repeat(40),
      sourceBound: true,
      fingerprint: '2'.repeat(64),
    }],
  }
  const provenance = {
    ...provenanceImmutable,
    status: 'Pass',
    blocked: false,
    blockers: [],
    provenanceFingerprint: sha256(stableStringify(provenanceImmutable)),
  }
  const runId = 's05-m1-evidence-test'
  const verification = (cycleStep) => ({
    schemaVersion: 'colla.capacity-seed-verification/v1',
    evidenceKind: 'verification',
    runId,
    cycleStep,
    ...seedIdentity,
    ok: true,
    runStateMatches: true,
    workspaceIsolationLeaks: 0,
    countMismatches: [],
    registryCountMismatches: [],
    relationshipLeaks: [],
    supportMismatches: [],
    duplicateUsernames: [],
    credentialSource: {
      matchedUsers: 1,
      fixtureFingerprintMatches: true,
    },
  })
  const results = {
    cleanBeforeFirstApply: {
      schemaVersion: 'colla.capacity-seed-clean-check/v1',
      evidenceKind: 'clean-state',
      runId,
      cycleStep: 'clean-before-first-apply',
      ...seedIdentity,
      ok: true,
      fixtureRuns: 0,
      fixturePhases: 0,
      fixtureRecords: 0,
      fixtureWorkspaces: 0,
      conflictingRuns: 0,
      businessRecords: 0,
    },
    firstInitialization: verification('first-initialization'),
    idempotentReapply: verification('idempotent-reapply'),
    cleanup: {
      schemaVersion: 'colla.capacity-seed-cleanup/v1',
      evidenceKind: 'cleanup',
      runId,
      cycleStep: 'cleanup',
      ...seedIdentity,
      ok: true,
      fixtureRuns: 0,
      fixturePhases: 0,
      fixtureRecords: 0,
      fixtureWorkspaces: 0,
      businessRecords: 0,
    },
    secondInitialization: verification('second-initialization'),
  }
  const checks = Object.fromEntries(Object.entries(results).map(([name, result]) => [name, {
    path: `${name}.json`,
    raw: Buffer.from(JSON.stringify(result)),
    result,
  }]))
  const result = createSeedCycleManifest({
    runId,
    provenance,
    checks,
    generatedAt: '2026-07-25T00:00:00.000Z',
  })
  assert.equal(result.seedExecution.status, 'Pass')
  assert.equal(result.seedExecution.provenanceFingerprint, provenance.provenanceFingerprint)
  assert.equal(result.runManifest.seedExecution, result.seedExecution)
  assert.match(result.seedExecution.seedExecutionFingerprint, /^[0-9a-f]{64}$/)
  assert.ok(Object.values(result.seedExecution.checks).every((entry) => /^[0-9a-f]{64}$/.test(entry.sha256)))
  const tampered = structuredClone(result.runManifest)
  tampered.seedExecution.checks.cleanup.result.fixtureRuns = 1
  assert.equal(validateCapacityRunManifest(tampered).ok, false)

  assert.throws(
    () => createSeedCycleManifest({
      runId: 's05-m1-evidence-test',
      provenance: { ...provenance, status: 'Blocked', blocked: true },
      checks,
    }),
    /blocked provenance/,
  )
  assert.throws(
    () => createSeedCycleManifest({
      runId: 's05-m1-evidence-test',
      provenance,
      checks: { ...checks, cleanup: { ...checks.cleanup, result: { ok: false } } },
    }),
    /failed seed evidence: cleanup/,
  )
  assert.throws(
    () => createSeedCycleManifest({
      runId: 's05-m1-evidence-test',
      provenance,
      checks: Object.fromEntries(Object.entries(checks).slice(0, 4)),
    }),
    /exact five seed-cycle evidence records/,
  )
  assert.throws(
    () => createSeedCycleManifest({
      runId,
      provenance,
      checks: {
        ...checks,
        idempotentReapply: {
          ...checks.idempotentReapply,
          path: checks.firstInitialization.path,
        },
      },
    }),
    /path is not unique/,
  )

  const blockedImmutable = {
    ...provenanceImmutable,
    git: { ...provenanceImmutable.git, dirty: true },
  }
  const statusFlippedProvenance = {
    ...blockedImmutable,
    status: 'Pass',
    blocked: false,
    blockers: [{ code: 'GIT_DIRTY', path: 'git.dirty' }],
    provenanceFingerprint: sha256(stableStringify(blockedImmutable)),
  }
  assert.throws(
    () => createSeedCycleManifest({
      runId,
      provenance: statusFlippedProvenance,
      checks,
    }),
    /provenance blockers must be empty|Git state/,
  )
})

test('default run id is safe and stable for a supplied timestamp', () => {
  assert.equal(
    defaultCapacityRunId(new Date('2026-07-25T08:09:10.123Z')),
    's05-m1-20260725t080910123z',
  )
})

test('stack dry-run output exposes protected commands without executing them', () => {
  const output = createCapacityDryRun(plan(), 'colla-s05-capacity', evidenceRoot)
  assert.equal(output.dryRun, true)
  assert.equal(output.projectName, 'colla-s05-capacity')
  assert.ok(output.steps.some((step) => step.args.includes('capacity-runner')))
  assert.ok(output.steps.every((step) => !step.args.includes('collaplatform')))
})

test('cleanup guard requires explicit confirmation and a specific reason', () => {
  assert.throws(() => assertGuardedReason('down', false, ''), /down requires --confirm/)
  assert.throws(() => assertGuardedReason('down', true, 'cleanup'), /specific --reason/)
  assert.doesNotThrow(() => assertGuardedReason(
    'down',
    true,
    'S05 capacity validation is complete',
  ))
})
