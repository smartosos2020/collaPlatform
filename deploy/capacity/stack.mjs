#!/usr/bin/env node
import { randomBytes } from 'node:crypto'
import {
  appendFileSync,
  closeSync,
  existsSync,
  mkdirSync,
  openSync,
  readFileSync,
  writeFileSync,
} from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { createCapacityProvenance } from '../../tools/capacity/src/provenance.mjs'
import {
  assertGuardedReason,
  CAPACITY_STACK_SERVICES,
  createCapacityDryRun,
  createCapacityRunPlan,
  createSeedCycleManifest,
  defaultCapacityRunId,
  validateCapturedStep,
} from './orchestrate.mjs'

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..')
const capacityRoot = join(repositoryRoot, 'deploy', 'capacity')
const productionCompose = join(repositoryRoot, 'deploy', 'docker-compose.prod.yml')
const capacityCompose = join(capacityRoot, 'docker-compose.capacity.yml')
const topologyFile = join(repositoryRoot, 'tools', 'capacity', 'config', 'topology.v1.json')
const contractFile = join(repositoryRoot, 'tools', 'capacity', 'config', 'c1.v1.json')
const defaultEnvFile = join(capacityRoot, 'capacity.env')
const args = process.argv.slice(2)
const action = args[0] ?? 'status'
const envFile = resolve(repositoryRoot, option('--env-file', defaultEnvFile))
const evidenceRoot = join(repositoryRoot, '.local-reports', 'capacity')
const allowedServices = new Set(CAPACITY_STACK_SERVICES)

if (action === 'init') {
  initializeEnvironment(envFile)
  process.exit(0)
}

if (!existsSync(envFile)) {
  fail(`Capacity environment is missing: ${envFile}. Run "pnpm capacity:stack init" first.`)
}

const environment = parseEnv(readFileSync(envFile, 'utf8'))
const projectName = environment.COMPOSE_PROJECT_NAME ?? ''
assertDisposableProject(projectName)

const composePrefix = [
  'compose',
  '--project-name', projectName,
  '--env-file', envFile,
  '-f', productionCompose,
  '-f', capacityCompose,
]

if (action === 'config') {
  validateRenderedTopology()
  console.log(`Capacity Compose contract parsed successfully for ${projectName}.`)
} else if (action === 'provenance') {
  await captureProvenance()
} else if (action === 'manifest') {
  createRunManifest()
} else if (action === 'status') {
  executeDocker([...composePrefix, 'ps'])
} else if (action === 'up') {
  validateRenderedTopology()
  const buildOption = args.includes('--build') ? ['--build'] : []
  executeDocker([
    ...composePrefix,
    'up', '-d', ...buildOption, '--wait', '--wait-timeout', '300',
    ...CAPACITY_STACK_SERVICES,
  ])
  recordOperation('up', 'capacity stack started after contract validation')
} else if (action === 'run') {
  await runCapacityWorkflow()
} else if (action === 'down') {
  requireConfirmedReason()
  executeDocker([...composePrefix, 'down', '--volumes', '--remove-orphans'])
  recordOperation('down', option('--reason', ''))
} else if (action === 'fault') {
  const service = requiredOption('--service')
  const mode = option('--mode', 'stop')
  const durationMs = positiveInteger(option('--duration-ms', '10000'), '--duration-ms')
  if (!['stop', 'kill', 'pause'].includes(mode)) fail('--mode must be stop, kill or pause')
  if (!allowedServices.has(service)) fail(`Fault service is not allowed: ${service}`)
  requireConfirmedReason()
  const start = new Date().toISOString()
  const faultCommand = mode === 'kill' ? ['kill', '-s', 'SIGKILL', service] : [mode, service]
  executeDocker([...composePrefix, ...faultCommand])
  recordOperation('fault-start', option('--reason', ''), { service, mode, start, durationMs })
  await sleep(durationMs)
  const recoveryCommand = mode === 'pause' ? ['unpause', service] : ['up', '-d', '--no-deps', service]
  executeDocker([...composePrefix, ...recoveryCommand])
  recordOperation('fault-recover', option('--reason', ''), {
    service,
    mode,
    start,
    recoveredAt: new Date().toISOString(),
  })
} else if (['start', 'stop', 'restart', 'kill'].includes(action)) {
  const service = requiredOption('--service')
  if (!allowedServices.has(service)) fail(`Service action is not allowed: ${service}`)
  requireConfirmedReason()
  const command = action === 'start' ? ['up', '-d', '--no-deps', service] : [action, service]
  executeDocker([...composePrefix, ...command])
  recordOperation(action, option('--reason', ''), { service })
} else {
  fail('Action must be init, config, provenance, manifest, status, up, run, down, fault, start, stop, restart or kill')
}

function initializeEnvironment(target) {
  if (existsSync(target)) {
    if (!args.includes('--force')) {
      fail(`Refusing to overwrite existing capacity environment: ${target}`)
    }
    requireConfirmedReason()
    assertNoActiveCapacityStackForEnvironment(target)
  }
  const source = readFileSync(join(capacityRoot, 'capacity.env.example'), 'utf8')
  const head = capture('git', ['rev-parse', 'HEAD'], repositoryRoot)
  const replacements = new Map([
    ['replace-with-disposable-capacity-password', secret()],
    ['replace-with-disposable-capacity-minio-secret', secret()],
    ['replace-with-64-byte-capacity-access-secret', secret(64)],
    ['replace-with-64-byte-capacity-refresh-secret', secret(64)],
    ['replace-with-disposable-capacity-admin-password', secret()],
    ['replace-with-disposable-capacity-collaboration-secret', secret()],
    ['replace-with-disposable-capacity-probe-secret', secret()],
    ['replace-with-exact-git-commit', head],
    ['replace-with-capacity-stack-instance-nonce', secret(32)],
  ])
  let content = source
  for (const [placeholder, value] of replacements) content = content.replaceAll(placeholder, value)
  mkdirSync(dirname(target), { recursive: true })
  writeFileSync(target, content)
  console.log(`Capacity environment initialized: ${target}`)
}

function assertNoActiveCapacityStackForEnvironment(target) {
  const existing = parseEnv(readFileSync(target, 'utf8'))
  const existingProject = existing.COMPOSE_PROJECT_NAME ?? ''
  assertDisposableProject(existingProject)
  const activeContainers = capture('docker', [
    'compose',
    '--project-name', existingProject,
    '--env-file', target,
    '-f', productionCompose,
    '-f', capacityCompose,
    'ps', '-q',
  ], repositoryRoot)
  if (activeContainers) {
    fail(`Refusing to rotate credentials while ${existingProject} has active containers. Run the guarded down action first.`)
  }
}

function executeDocker(dockerArgs) {
  run('docker', dockerArgs, repositoryRoot, { env: composeEnvironment() })
}

async function runCapacityWorkflow() {
  let plan
  try {
    plan = createCapacityRunPlan({
      repositoryRoot,
      evidenceRoot,
      envFile,
      environment,
      composePrefix,
      stackFile: fileURLToPath(import.meta.url),
      runId: option('--run-id', defaultCapacityRunId()),
      confirmed: args.includes('--confirm'),
      reason: option('--reason', ''),
    })
  } catch (error) {
    fail(error.message)
  }

  if (args.includes('--dry-run')) {
    console.log(JSON.stringify(createCapacityDryRun(plan, projectName, evidenceRoot), null, 2))
    return
  }

  mkdirSync(plan.runRoot, { recursive: true })
  recordOperation('run-start', plan.reason, { runId: plan.runId })
  for (const step of plan.steps) {
    console.log(`\n[capacity] ${step.label}`)
    if (step.capture) {
      const stdout = capture(step.command, step.args, repositoryRoot, {
        env: composeEnvironment(),
        stdinPath: step.stdinPath,
      })
      let capturedEvidence
      try {
        capturedEvidence = validateCapturedStep(step, stdout, plan.requiredRunningServices)
      } catch (error) {
        fail(error.message)
      }
      if (step.cycleStep) {
        capturedEvidence = {
          ...capturedEvidence,
          runId: plan.runId,
          cycleStep: step.cycleStep,
        }
      }
      if (step.outputPath) {
        writeEvidence(step.outputPath, capturedEvidence)
      }
    } else {
      run(step.command, step.args, repositoryRoot, {
        env: composeEnvironment(),
        stdinPath: step.stdinPath,
      })
    }
  }
  recordOperation('run-complete', plan.reason, { runId: plan.runId })
  console.log(`Capacity M1 run completed and verified: ${plan.runRoot}`)
}

function createRunManifest() {
  const runId = requiredOption('--run-id')
  const provenancePath = resolveEvidenceOption('--provenance')
  const seedEvidencePath = resolveEvidenceOption('--seed-evidence')
  const outputPath = resolveEvidenceOption('--output')
  const evidenceInputs = [
    ['cleanBeforeFirstApply', '--seed-clean'],
    ['firstInitialization', '--seed-first'],
    ['idempotentReapply', '--seed-idempotent'],
    ['cleanup', '--seed-cleanup'],
    ['secondInitialization', '--seed-second'],
  ]
  const provenance = readJsonEvidence(provenancePath)
  if (provenance.status !== 'Pass' || provenance.blocked === true) {
    fail('Cannot create a run manifest from blocked provenance')
  }

  const checks = {}
  for (const [name, optionName] of evidenceInputs) {
    const evidencePath = resolveEvidenceOption(optionName)
    const raw = readFileSync(evidencePath)
    const result = readJsonEvidence(evidencePath)
    checks[name] = {
      path: relative(dirname(seedEvidencePath), evidencePath).replaceAll('\\', '/'),
      raw,
      result,
    }
  }

  let manifests
  try {
    manifests = createSeedCycleManifest({
      runId,
      provenance,
      checks,
    })
  } catch (error) {
    fail(error.message)
  }
  writeEvidence(seedEvidencePath, manifests.seedExecution)
  writeEvidence(outputPath, manifests.runManifest)
  console.log(JSON.stringify({
    output: outputPath,
    seedEvidence: seedEvidencePath,
    status: 'Pass',
    provenanceFingerprint: provenance.provenanceFingerprint,
    seedExecutionFingerprint: manifests.seedExecution.seedExecutionFingerprint,
  }, null, 2))
}

function validateRenderedTopology() {
  run(process.execPath, [
    join(repositoryRoot, 'tools', 'capacity', 'src', 'cli.mjs'),
    'contract',
    'validate',
  ], repositoryRoot)
  const rendered = JSON.parse(capture('docker', [
    ...composePrefix,
    '--profile', 'capacity',
    'config', '--format', 'json',
  ], repositoryRoot, { env: composeEnvironment() }))
  const topology = JSON.parse(readFileSync(topologyFile, 'utf8'))
  const roleServices = {
    postgresql: ['postgres'],
    redis: ['redis'],
    minio: ['minio'],
    maintenance: ['maintenance'],
    api: ['api-a', 'api-b'],
    worker: ['worker-a', 'worker-b'],
    'event-gateway': ['event-gateway-a', 'event-gateway-b'],
    collaboration: ['collaboration-a', 'collaboration-b'],
    web: ['web'],
    edge: ['nginx'],
    'load-source': ['capacity-runner'],
  }
  const errors = []
  for (const [roleName, serviceNames] of Object.entries(roleServices)) {
    const role = topology.roles?.[roleName]
    if (!role) {
      errors.push(`topology role is missing: ${roleName}`)
      continue
    }
    if (serviceNames.length !== role.replicas) {
      errors.push(`${roleName} declares ${role.replicas} replicas but maps ${serviceNames.length} services`)
    }
    for (const serviceName of serviceNames) {
      const service = rendered.services?.[serviceName]
      if (!service) {
        errors.push(`${roleName} service is missing from rendered Compose: ${serviceName}`)
        continue
      }
      const limits = service.deploy?.resources?.limits ?? {}
      if (Number(limits.cpus) !== role.resources.cpu) {
        errors.push(`${serviceName} CPU ${limits.cpus ?? 'unset'} != ${role.resources.cpu}`)
      }
      const memoryMiB = Number(limits.memory) / 1024 / 1024
      if (memoryMiB !== role.resources.memoryMiB) {
        errors.push(`${serviceName} memory ${memoryMiB || 'unset'} MiB != ${role.resources.memoryMiB} MiB`)
      }
      validateRuntime(roleName, serviceName, role.runtime, service, errors)
      if (role.connections?.postgresql > 0 && role.runtime?.kind === 'jvm') {
        const actualPool = Number(service.environment?.SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE)
        if (actualPool !== role.connections.postgresql) {
          errors.push(`${serviceName} PostgreSQL pool ${actualPool || 'unset'} != ${role.connections.postgresql}`)
        }
      }
    }
  }
  if (errors.length > 0) {
    fail(`Rendered capacity topology differs from topology.v1.json:\n- ${errors.join('\n- ')}`)
  }
}

async function captureProvenance() {
  const baselinePath = resolve(repositoryRoot, requiredOption('--preflight-baseline'))
  const currentPath = resolve(repositoryRoot, requiredOption('--preflight-current'))
  const seedPlanPath = resolve(repositoryRoot, requiredOption('--seed-plan'))
  const outputPath = resolve(repositoryRoot, requiredOption('--output'))
  const renderedCompose = capture('docker', [
    ...composePrefix,
    '--profile', 'capacity',
    'config', '--format', 'json',
  ], repositoryRoot, { env: composeEnvironment() })
  const rendered = JSON.parse(renderedCompose)
  const requiredImages = Object.keys(rendered.services ?? {}).sort()
  const externalImages = new Set(['postgres', 'redis', 'minio', 'nginx'])
  const sourceBoundImages = requiredImages.filter((serviceName) => !externalImages.has(serviceName))
  const imageInspect = {}
  for (const serviceName of requiredImages) {
    const image = rendered.services[serviceName]?.image
    if (!image) fail(`Rendered service ${serviceName} has no image`)
    const inspected = JSON.parse(capture('docker', ['image', 'inspect', image], repositoryRoot, {
      env: composeEnvironment(),
    }))
    imageInspect[serviceName] = inspected[0]
  }
  const provenance = await createCapacityProvenance({
    repoRoot: repositoryRoot,
    sourceCommit: environment.SOURCE_COMMIT,
    stackInstanceNonce: environment.CAPACITY_STACK_INSTANCE_NONCE,
    preflight: {
      baseline: JSON.parse(readFileSync(baselinePath, 'utf8')),
      current: JSON.parse(readFileSync(currentPath, 'utf8')),
    },
    contract: JSON.parse(readFileSync(contractFile, 'utf8')),
    topology: JSON.parse(readFileSync(topologyFile, 'utf8')),
    seedPlan: JSON.parse(readFileSync(seedPlanPath, 'utf8')),
    renderedCompose,
    requiredImages,
    sourceBoundImages,
    imageInspect,
  })
  mkdirSync(dirname(outputPath), { recursive: true })
  writeFileSync(outputPath, `${JSON.stringify(provenance, null, 2)}\n`)
  console.log(JSON.stringify({
    output: outputPath,
    status: provenance.status,
    provenanceFingerprint: provenance.provenanceFingerprint,
    blockers: provenance.blockers,
  }, null, 2))
  if (provenance.blocked) process.exitCode = 8
}

function validateRuntime(roleName, serviceName, runtime, service, errors) {
  if (runtime.kind === 'jvm') {
    const options = service.environment?.JAVA_TOOL_OPTIONS ?? ''
    const required = [`-Xms${runtime.xmsMiB}m`, `-Xmx${runtime.xmxMiB}m`, ...runtime.parameters]
    for (const parameter of required) {
      if (!options.includes(parameter)) errors.push(`${serviceName} JAVA_TOOL_OPTIONS misses ${parameter}`)
    }
  } else if (runtime.kind === 'node') {
    const options = service.environment?.NODE_OPTIONS ?? ''
    const required = [`--max-old-space-size=${runtime.maxOldSpaceMiB}`, ...runtime.parameters]
    for (const parameter of required) {
      if (!options.includes(parameter)) errors.push(`${serviceName} NODE_OPTIONS misses ${parameter}`)
    }
  } else if (roleName === 'postgresql') {
    const command = (service.command ?? []).join(' ')
    for (const parameter of runtime.parameters) {
      if (!command.includes(parameter)) errors.push(`${serviceName} command misses ${parameter}`)
    }
    const shmSizeMiB = Number(service.shm_size) / 1024 / 1024
    if (shmSizeMiB !== runtime.shmSizeMiB) {
      errors.push(`${serviceName} shared memory ${shmSizeMiB || 'unset'} MiB != ${runtime.shmSizeMiB} MiB`)
    }
  } else if (roleName === 'redis') {
    const command = (service.command ?? []).join(' ')
    for (const parameter of runtime.parameters) {
      const [key, value] = parameter.split('=')
      if (!command.includes(key) || !command.includes(value)) {
        errors.push(`${serviceName} command misses ${parameter}`)
      }
    }
  }
}

function run(command, commandArgs, cwd, options = {}) {
  const input = openInput(options.stdinPath)
  const result = spawnSync(command, commandArgs, {
    cwd,
    stdio: [input ?? 'inherit', 'inherit', 'inherit'],
    env: options.env,
    shell: process.platform === 'win32' && command.endsWith('.cmd'),
  })
  if (input !== null) closeSync(input)
  if (result.error) fail(`${command} failed: ${result.error.message}`)
  if (result.status !== 0) process.exit(result.status ?? 1)
}

function capture(command, commandArgs, cwd, options = {}) {
  const input = openInput(options.stdinPath)
  const result = spawnSync(command, commandArgs, {
    cwd,
    encoding: 'utf8',
    env: options.env,
    stdio: [input ?? 'ignore', 'pipe', 'pipe'],
    shell: false,
    maxBuffer: 16 * 1024 * 1024,
  })
  if (input !== null) closeSync(input)
  if (result.error || result.status !== 0) fail(`${command} ${commandArgs.join(' ')} failed`)
  return result.stdout.trim()
}

function openInput(path) {
  return path ? openSync(path, 'r') : null
}

function composeEnvironment() {
  return {
    ...process.env,
    CAPACITY_EVIDENCE_DIR: evidenceRoot,
  }
}

function parseEnv(content) {
  return Object.fromEntries(content.split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith('#') && line.includes('='))
    .map((line) => {
      const separator = line.indexOf('=')
      return [line.slice(0, separator), line.slice(separator + 1)]
    }))
}

function assertDisposableProject(value) {
  if (!/^colla-s05-capacity(?:-[a-z0-9-]+)?$/.test(value)) {
    fail(`Unsafe Compose project "${value}". S05 operations require a colla-s05-capacity* project.`)
  }
  if (value === 'collaplatform') fail('The long-lived collaplatform stack is never a capacity target.')
}

function requireConfirmedReason() {
  try {
    assertGuardedReason(action, args.includes('--confirm'), option('--reason', ''))
  } catch (error) {
    fail(error.message)
  }
}

function resolveEvidenceOption(name) {
  return assertEvidencePath(resolve(repositoryRoot, requiredOption(name)))
}

function assertEvidencePath(filePath) {
  const resolvedPath = resolve(filePath)
  const pathFromEvidenceRoot = relative(resolve(evidenceRoot), resolvedPath)
  if (!pathFromEvidenceRoot || pathFromEvidenceRoot.startsWith('..') || pathFromEvidenceRoot.includes(':')) {
    fail(`Capacity evidence path must stay below ${evidenceRoot}: ${resolvedPath}`)
  }
  return resolvedPath
}

function readJsonEvidence(filePath) {
  try {
    return JSON.parse(readFileSync(filePath, 'utf8'))
  } catch (error) {
    fail(`Capacity evidence is not valid JSON: ${filePath} (${error.message})`)
  }
}

function writeEvidence(filePath, value) {
  const resolvedPath = assertEvidencePath(filePath)
  if (value === undefined) fail(`No normalized evidence was produced for ${resolvedPath}`)
  mkdirSync(dirname(resolvedPath), { recursive: true })
  writeFileSync(resolvedPath, `${JSON.stringify(value, null, 2)}\n`)
}

function recordOperation(operation, reason, details = {}) {
  mkdirSync(evidenceRoot, { recursive: true })
  appendFileSync(join(evidenceRoot, 'operations.jsonl'), `${JSON.stringify({
    schemaVersion: 1,
    operation,
    reason,
    projectName,
    actor: process.env.USERNAME ?? process.env.USER ?? 'unknown',
    at: new Date().toISOString(),
    ...details,
  })}\n`)
}

function option(name, fallback) {
  const index = args.indexOf(name)
  return index === -1 ? fallback : args[index + 1]
}

function requiredOption(name) {
  const value = option(name, '')
  if (!value) fail(`${name} is required`)
  return value
}

function positiveInteger(value, label) {
  const parsed = Number.parseInt(value, 10)
  if (!Number.isFinite(parsed) || parsed <= 0) fail(`${label} must be a positive integer`)
  return parsed
}

function secret(size = 48) {
  return randomBytes(size).toString('base64url')
}

function sleep(milliseconds) {
  return new Promise((resolveSleep) => setTimeout(resolveSleep, milliseconds))
}

function fail(message) {
  console.error(message)
  process.exit(2)
}
