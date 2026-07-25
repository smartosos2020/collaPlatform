import { createHash } from 'node:crypto'
import { join, relative, resolve } from 'node:path'
import { stableStringify } from '../../tools/capacity/src/contract.mjs'
import { validateCapacityRunManifest } from '../../tools/capacity/src/provenance.mjs'

const RUN_ID_PATTERN = /^s05-m1-[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$/
export const CAPACITY_STACK_SERVICES = [
  'postgres',
  'redis',
  'minio',
  'maintenance',
  'api-a',
  'api-b',
  'worker-a',
  'worker-b',
  'event-gateway-a',
  'event-gateway-b',
  'collaboration-a',
  'collaboration-b',
  'web',
  'nginx',
]
const REQUIRED_RUNNING_SERVICES = CAPACITY_STACK_SERVICES.filter(
  (service) => service !== 'maintenance',
)

export function createCapacityRunPlan(options) {
  const {
    repositoryRoot,
    evidenceRoot,
    envFile,
    environment,
    composePrefix,
    stackFile,
    runId,
    confirmed,
    reason,
  } = options

  if (environment.COMPOSE_PROJECT_NAME !== 'colla-s05-capacity') {
    throw new Error(
      'The M1 run requires COMPOSE_PROJECT_NAME=colla-s05-capacity because the checked-in runtime names that disposable stack explicitly.',
    )
  }
  for (const name of ['POSTGRES_USER', 'POSTGRES_DB']) {
    if (typeof environment[name] !== 'string' || environment[name].trim().length === 0) {
      throw new Error(`run requires ${name} in the capacity environment`)
    }
  }
  if (!/^[0-9a-f]{40,64}$/i.test(environment.SOURCE_COMMIT ?? '')) {
    throw new Error('run requires SOURCE_COMMIT to be an exact Git commit')
  }
  assertGuardedReason('run', confirmed, reason)
  if (!RUN_ID_PATTERN.test(runId)) {
    throw new Error('run --run-id must match s05-m1-[a-z0-9-] and be at most 70 characters')
  }

  const runRoot = resolve(evidenceRoot, 'runs', runId)
  const relativeRunRoot = relative(resolve(evidenceRoot), runRoot)
  if (!relativeRunRoot || relativeRunRoot.startsWith('..')) {
    throw new Error('run evidence path must stay below the capacity evidence root')
  }

  const capacityCli = join(repositoryRoot, 'tools', 'capacity', 'src', 'cli.mjs')
  const seedConfig = join(repositoryRoot, 'tools', 'capacity', 'config', 'seed.v1.json')
  const runnerDockerfile = join(repositoryRoot, 'tools', 'capacity', 'Dockerfile')
  const baseline = join(runRoot, 'preflight-baseline.json')
  const current = join(runRoot, 'preflight-current.json')
  const seedPlan = join(runRoot, 'seed-plan.json')
  const seedApplySql = join(runRoot, 'seed-apply.sql')
  const seedVerifySql = join(runRoot, 'seed-verify.sql')
  const seedCleanCheckSql = join(runRoot, 'seed-clean-check.sql')
  const seedCleanupSql = join(runRoot, 'seed-cleanup.sql')
  const stackHealth = join(runRoot, 'stack-health.json')
  const seedCleanEvidence = join(runRoot, 'seed-clean-before-first-apply.json')
  const firstSeedEvidence = join(runRoot, 'seed-first-initialization.json')
  const idempotentSeedEvidence = join(runRoot, 'seed-idempotent-reapply.json')
  const cleanupSeedEvidence = join(runRoot, 'seed-cleanup.json')
  const secondSeedEvidence = join(runRoot, 'seed-second-initialization.json')
  const seedCycleEvidence = join(runRoot, 'seed-cycle-evidence.json')
  const provenance = join(runRoot, 'provenance.json')
  const runManifest = join(runRoot, 'run-manifest.json')
  const scenarioEvidence = join(runRoot, 'scenario')
  const runnerRunRoot = `/evidence/runs/${runId}`
  const postgresArgs = [
    ...composePrefix,
    'exec',
    '-T',
    'postgres',
    'psql',
    '-X',
    '--set',
    'ON_ERROR_STOP=1',
    '--no-psqlrc',
    '--tuples-only',
    '--no-align',
    '--quiet',
    '--username',
    environment.POSTGRES_USER,
    '--dbname',
    environment.POSTGRES_DB,
  ]

  const steps = [
    {
      label: 'validate capacity contract and rendered topology',
      command: process.execPath,
      args: [stackFile, 'config', '--env-file', envFile],
    },
    {
      label: 'require the disposable stack services to be healthy',
      command: 'docker',
      args: [...composePrefix, 'ps', '--format', 'json'],
      capture: 'stack-health',
      outputPath: stackHealth,
    },
    {
      label: 'build the capacity runner image from the current source',
      command: 'docker',
      args: [
        'build',
        '--build-arg',
        `SOURCE_COMMIT=${environment.SOURCE_COMMIT}`,
        '--tag',
        environment.CAPACITY_RUNNER_IMAGE ?? 'colla-s05-capacity-capacity-runner',
        '--file',
        runnerDockerfile,
        repositoryRoot,
      ],
    },
    {
      label: 'create the deterministic seed plan',
      command: process.execPath,
      args: [
        capacityCli,
        'seed',
        'plan',
        '--seed-id',
        's05-c1',
        '--config',
        seedConfig,
        '--output',
        seedPlan,
      ],
    },
    {
      label: 'capture the immutable host preflight baseline',
      command: process.execPath,
      args: [
        capacityCli,
        'preflight',
        'capture',
        '--repo-root',
        repositoryRoot,
        '--output',
        baseline,
      ],
    },
    {
      label: 'capture the immutable host preflight comparison',
      command: process.execPath,
      args: [
        capacityCli,
        'preflight',
        'capture',
        '--repo-root',
        repositoryRoot,
        '--output',
        current,
      ],
    },
    {
      label: 'create and enforce immutable provenance before database mutation',
      command: process.execPath,
      args: [
        stackFile,
        'provenance',
        '--env-file',
        envFile,
        '--preflight-baseline',
        baseline,
        '--preflight-current',
        current,
        '--seed-plan',
        seedPlan,
        '--output',
        provenance,
      ],
    },
    {
      label: 'generate the first-initialization clean-state check',
      command: process.execPath,
      args: [capacityCli, 'seed', 'clean-check', '--plan', seedPlan, '--sql', seedCleanCheckSql],
    },
    {
      label: 'prove zero named fixture residue before the first apply',
      command: 'docker',
      args: postgresArgs,
      stdinPath: seedCleanCheckSql,
      capture: 'seed-clean-verification',
      cycleStep: 'clean-before-first-apply',
      outputPath: seedCleanEvidence,
    },
    {
      label: 'generate seed apply SQL through the seed CLI',
      command: process.execPath,
      args: [capacityCli, 'seed', 'apply', '--plan', seedPlan, '--sql', seedApplySql],
    },
    {
      label: 'apply the first clean seed initialization',
      command: 'docker',
      args: postgresArgs,
      stdinPath: seedApplySql,
    },
    {
      label: 'generate seed verification SQL through the seed CLI',
      command: process.execPath,
      args: [capacityCli, 'seed', 'verify', '--plan', seedPlan, '--sql', seedVerifySql],
    },
    {
      label: 'verify the first clean seed initialization',
      command: 'docker',
      args: postgresArgs,
      stdinPath: seedVerifySql,
      capture: 'seed-verification',
      cycleStep: 'first-initialization',
      outputPath: firstSeedEvidence,
    },
    {
      label: 'reapply the same seed to prove idempotency and resume safety',
      command: 'docker',
      args: postgresArgs,
      stdinPath: seedApplySql,
    },
    {
      label: 'verify the idempotent seed reapply',
      command: 'docker',
      args: postgresArgs,
      stdinPath: seedVerifySql,
      capture: 'seed-verification',
      cycleStep: 'idempotent-reapply',
      outputPath: idempotentSeedEvidence,
    },
    {
      label: 'generate checksum-guarded seed cleanup SQL',
      command: process.execPath,
      args: [capacityCli, 'seed', 'cleanup', '--plan', seedPlan, '--sql', seedCleanupSql],
    },
    {
      label: 'clean and verify only the named disposable fixture',
      command: 'docker',
      args: postgresArgs,
      stdinPath: seedCleanupSql,
      capture: 'seed-cleanup-verification',
      cycleStep: 'cleanup',
      outputPath: cleanupSeedEvidence,
    },
    {
      label: 'apply the second clean seed initialization',
      command: 'docker',
      args: postgresArgs,
      stdinPath: seedApplySql,
    },
    {
      label: 'verify the second clean seed initialization',
      command: 'docker',
      args: postgresArgs,
      stdinPath: seedVerifySql,
      capture: 'seed-verification',
      cycleStep: 'second-initialization',
      outputPath: secondSeedEvidence,
    },
    {
      label: 'bind seed-cycle evidence to the immutable run manifest',
      command: process.execPath,
      args: [
        stackFile,
        'manifest',
        '--run-id',
        runId,
        '--provenance',
        provenance,
        '--seed-clean',
        seedCleanEvidence,
        '--seed-first',
        firstSeedEvidence,
        '--seed-idempotent',
        idempotentSeedEvidence,
        '--seed-cleanup',
        cleanupSeedEvidence,
        '--seed-second',
        secondSeedEvidence,
        '--seed-evidence',
        seedCycleEvidence,
        '--output',
        runManifest,
      ],
    },
    {
      label: 'run the M1 scenario in the capacity-runner profile',
      command: 'docker',
      args: [
        ...composePrefix,
        '--profile',
        'capacity',
        'run',
        '--rm',
        '--no-deps',
        'capacity-runner',
        'scenario',
        'run',
        '--config',
        '/workspace/tools/capacity/config/scenarios/s05-m1-unified.v1.json',
        '--runtime',
        '/workspace/tools/capacity/config/runtime/s05-m1.v1.json',
        '--manifest',
        `${runnerRunRoot}/run-manifest.json`,
        '--evidence-dir',
        `${runnerRunRoot}/scenario`,
      ],
    },
    {
      label: 'verify the scenario evidence bundle',
      command: process.execPath,
      args: [capacityCli, 'evidence', 'verify', '--directory', scenarioEvidence],
    },
  ]

  return {
    runId,
    runRoot,
    reason: reason.trim(),
    requiredRunningServices: REQUIRED_RUNNING_SERVICES,
    seedEvidenceFiles: [
      seedCleanEvidence,
      firstSeedEvidence,
      idempotentSeedEvidence,
      cleanupSeedEvidence,
      secondSeedEvidence,
    ],
    steps,
  }
}

export function createCapacityDryRun(plan, projectName, evidenceRoot) {
  return {
    dryRun: true,
    projectName,
    evidenceRoot,
    runId: plan.runId,
    steps: plan.steps.map(({ label, command, args, stdinPath, outputPath, cycleStep }) => ({
      label,
      command,
      args,
      ...(stdinPath ? { stdinPath } : {}),
      ...(outputPath ? { outputPath } : {}),
      ...(cycleStep ? { cycleStep } : {}),
    })),
  }
}

export function assertGuardedReason(action, confirmed, reason) {
  if (!confirmed) throw new Error(`${action} requires --confirm`)
  if (String(reason ?? '').trim().length < 12) {
    throw new Error(`${action} requires a specific --reason of at least 12 characters`)
  }
}

export function validateCapturedStep(step, stdout, requiredRunningServices) {
  if (step.capture === 'stack-health') {
    const containers = parseComposePs(stdout)
    const byService = new Map()
    const invalidEntries = []
    const duplicateServices = []
    for (const [index, container] of containers.entries()) {
      if (!container || typeof container !== 'object' || Array.isArray(container)) {
        invalidEntries.push(String(index))
        continue
      }
      const service = String(container.Service ?? container.service ?? '').trim()
      if (!service) {
        invalidEntries.push(String(index))
        continue
      }
      if (byService.has(service)) {
        duplicateServices.push(service)
        continue
      }
      byService.set(service, container)
    }
    const missing = requiredRunningServices.filter((service) => !byService.has(service))
    const unhealthy = requiredRunningServices.filter((service) => {
      const container = byService.get(service)
      if (!container) return false
      const state = String(container.State ?? container.state ?? '').toLowerCase()
      const health = String(container.Health ?? container.health ?? '').toLowerCase()
      return state !== 'running' || health !== 'healthy'
    })
    if (
      invalidEntries.length > 0
      || duplicateServices.length > 0
      || missing.length > 0
      || unhealthy.length > 0
    ) {
      const details = [
        ...(invalidEntries.length > 0 ? [`invalid service records: ${invalidEntries.join(', ')}`] : []),
        ...(duplicateServices.length > 0 ? [`duplicate services: ${duplicateServices.join(', ')}`] : []),
        ...(missing.length > 0 ? [`missing services: ${missing.join(', ')}`] : []),
        ...(unhealthy.length > 0 ? [`not healthy services: ${unhealthy.join(', ')}`] : []),
      ]
      throw new Error(`Capacity stack is not ready; ${details.join('; ')}`)
    }
    return {
      ok: true,
      services: requiredRunningServices.map((service) => {
        const container = byService.get(service)
        return {
          service,
          state: String(container.State ?? container.state ?? '').toLowerCase(),
          health: String(container.Health ?? container.health ?? '').toLowerCase(),
        }
      }),
    }
  }
  if ([
    'seed-clean-verification',
    'seed-verification',
    'seed-cleanup-verification',
  ].includes(step.capture)) {
    const verification = parseLastJson(stdout, 'Seed verification')
    if (verification.ok !== true) {
      const label = step.capture === 'seed-cleanup-verification'
        ? 'Seed cleanup verification'
        : step.capture === 'seed-clean-verification'
          ? 'Seed clean-state verification'
          : 'Seed verification'
      throw new Error(`${label} failed: ${JSON.stringify(verification)}`)
    }
    return verification
  }
  return undefined
}

function parseComposePs(stdout) {
  const text = String(stdout).trim()
  if (!text) return []
  try {
    const parsed = JSON.parse(text)
    return Array.isArray(parsed) ? parsed : [parsed]
  } catch {
    try {
      return text.split(/\r?\n/).filter(Boolean).map((line) => JSON.parse(line))
    } catch {
      throw new Error('Capacity stack health check did not return Docker Compose JSON')
    }
  }
}

function parseLastJson(stdout, label) {
  const lines = String(stdout).split(/\r?\n/).map((value) => value.trim()).filter(Boolean)
  try {
    return JSON.parse(lines.at(-1))
  } catch {
    throw new Error(`${label} did not return the expected JSON result`)
  }
}

export function defaultCapacityRunId(now = new Date()) {
  return `s05-m1-${now.toISOString().replace(/[-:.]/g, '').toLowerCase()}`
}

export function createSeedCycleManifest({
  runId,
  provenance,
  checks,
  generatedAt = new Date().toISOString(),
}) {
  if (provenance?.status !== 'Pass' || provenance?.blocked === true) {
    throw new Error('Cannot create a run manifest from blocked provenance')
  }
  const expectedCheckNames = [
    'cleanBeforeFirstApply',
    'firstInitialization',
    'idempotentReapply',
    'cleanup',
    'secondInitialization',
  ]
  const suppliedCheckNames = Object.keys(checks ?? {}).sort()
  if (stableStringify(suppliedCheckNames) !== stableStringify([...expectedCheckNames].sort())) {
    throw new Error('Run manifest requires the exact five seed-cycle evidence records')
  }
  const normalizedChecks = {}
  const evidenceFiles = {}
  for (const name of expectedCheckNames) {
    const evidence = checks[name]
    if (evidence?.result?.ok !== true) {
      throw new Error(`Cannot create a run manifest from failed seed evidence: ${name}`)
    }
    let parsedRaw
    try {
      parsedRaw = JSON.parse(Buffer.from(evidence.raw).toString('utf8'))
    } catch {
      throw new Error(`Cannot create a run manifest from non-JSON seed evidence: ${name}`)
    }
    if (stableStringify(parsedRaw) !== stableStringify(evidence.result)) {
      throw new Error(`Seed evidence content does not match parsed result: ${name}`)
    }
    normalizedChecks[name] = {
      path: evidence.path,
      sha256: createHash('sha256').update(evidence.raw).digest('hex'),
      result: evidence.result,
    }
    evidenceFiles[evidence.path] = evidence.raw
  }
  const immutableSeedEvidence = {
    schemaVersion: 'colla.capacity-seed-cycle/v1',
    runId,
    provenanceFingerprint: provenance.provenanceFingerprint,
    checks: normalizedChecks,
  }
  const seedExecutionFingerprint = createHash('sha256')
    .update(stableStringify(immutableSeedEvidence))
    .digest('hex')
  const seedExecution = {
    ...immutableSeedEvidence,
    generatedAt,
    status: 'Pass',
    blocked: false,
    seedExecutionFingerprint,
  }
  const result = {
    seedExecution,
    runManifest: {
      ...provenance,
      seedExecution,
    },
  }
  const validation = validateCapacityRunManifest(result.runManifest, {
    evidenceFiles,
    requireEvidenceFiles: true,
  })
  if (!validation.ok) {
    throw new Error(`Run manifest validation failed: ${validation.errors.join('; ')}`)
  }
  return result
}
