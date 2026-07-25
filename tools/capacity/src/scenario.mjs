import { execFile as execFileCallback } from 'node:child_process'
import { createHash, randomUUID } from 'node:crypto'
import { existsSync } from 'node:fs'
import { readFile } from 'node:fs/promises'
import { request as httpRequest } from 'node:http'
import { promisify } from 'node:util'

import { createEvidenceBundle, verifyEvidenceBundle } from './evidence.mjs'
import { runCollaborationScenario } from './load/collaboration.mjs'
import { runHttpScenario } from './load/http.mjs'
import { runWebSocketScenario } from './load/websocket.mjs'
import { runWorkerScenario } from './load/worker.mjs'
import { quantile, stableStringify } from './load/common.mjs'
import { redactSecrets } from './preflight.mjs'
import { validateCapacityRunManifest } from './provenance.mjs'

export const SCENARIO_SCHEMA_VERSION = 'colla.capacity-scenario/v1'

const loaderNames = Object.freeze(['http', 'websocket', 'collaboration', 'worker'])
const defaultLoaders = Object.freeze({
  http: runHttpScenario,
  websocket: runWebSocketScenario,
  collaboration: runCollaborationScenario,
  worker: runWorkerScenario,
})
const execFile = promisify(execFileCallback)

export async function loadScenarioConfig(file) {
  return JSON.parse(await readFile(file, 'utf8'))
}

export function validateScenarioConfig(config) {
  const errors = []
  if (!config || typeof config !== 'object' || Array.isArray(config)) {
    return { ok: false, errors: ['scenario config must be an object'] }
  }
  if (config.schemaVersion !== SCENARIO_SCHEMA_VERSION) {
    errors.push(`schemaVersion must be ${SCENARIO_SCHEMA_VERSION}`)
  }
  if (typeof config.id !== 'string' || config.id.trim().length === 0) {
    errors.push('id must be a non-empty string')
  }
  if (!Number.isInteger(config.revision) || config.revision < 1) {
    errors.push('revision must be a positive integer')
  }

  const mode = config.execution?.mode
  if (!['serial', 'concurrent'].includes(mode)) {
    errors.push('execution.mode must be serial or concurrent')
  }
  validateNonNegative(config.execution?.warmup?.durationMs, 'execution.warmup.durationMs', errors)
  validateNonNegative(config.execution?.durationMs, 'execution.durationMs', errors)
  validatePositive(config.execution?.abort?.maxDurationMs, 'execution.abort.maxDurationMs', errors, true)
  validateNonNegative(config.execution?.abort?.maxErrorCount, 'execution.abort.maxErrorCount', errors, true)
  validateRatio(config.execution?.abort?.maxErrorRate, 'execution.abort.maxErrorRate', errors, true)
  validateNonNegative(
    config.execution?.abort?.maxCollectorFailures,
    'execution.abort.maxCollectorFailures',
    errors,
    true,
  )

  const enabled = loaderNames.filter((name) => config.loaders?.[name]?.enabled === true)
  if (enabled.length === 0) errors.push('at least one loader must be enabled')
  for (const name of Object.keys(config.loaders ?? {})) {
    if (!loaderNames.includes(name)) errors.push(`unsupported loader: ${name}`)
  }

  validateRatio(config.thresholds?.maxErrorRate, 'thresholds.maxErrorRate', errors, true)
  validateNonNegative(config.thresholds?.maxP95Ms, 'thresholds.maxP95Ms', errors, true)
  validateNonNegative(config.thresholds?.minSamples, 'thresholds.minSamples', errors, true)
  if (config.metrics?.intervalMs !== undefined) {
    validatePositive(config.metrics.intervalMs, 'metrics.intervalMs', errors)
  }
  for (const source of config.metrics?.prometheus ?? []) {
    if (typeof source.id !== 'string' || !source.id) errors.push('Prometheus source id is required')
    if ((!source.endpoint || typeof source.endpoint !== 'string')
      && (!source.endpointEnv || typeof source.endpointEnv !== 'string')) {
      errors.push(`Prometheus source ${source.id ?? '<unknown>'} endpoint or endpointEnv is required`)
    }
    validateHeaderEnvironment(source, 'Prometheus', errors)
  }
  for (const source of config.metrics?.json ?? []) {
    if (typeof source.id !== 'string' || !source.id) errors.push('JSON metric source id is required')
    if ((!source.endpoint || typeof source.endpoint !== 'string')
      && (!source.endpointEnv || typeof source.endpointEnv !== 'string')) {
      errors.push(`JSON metric source ${source.id ?? '<unknown>'} endpoint or endpointEnv is required`)
    }
    validateHeaderEnvironment(source, 'JSON metric', errors)
  }
  if (config.metrics?.docker?.enabled && typeof config.metrics.docker.id !== 'string') {
    errors.push('Docker stats source id is required when enabled')
  }
  if (config.evidence?.requireProvenance !== undefined
    && typeof config.evidence.requireProvenance !== 'boolean') {
    errors.push('evidence.requireProvenance must be a boolean')
  }

  return { ok: errors.length === 0, errors }
}

export async function runCapacityScenario(config, options = {}) {
  const validation = validateScenarioConfig(config)
  if (!validation.ok) {
    throw new Error(`invalid scenario config: ${validation.errors.join('; ')}`)
  }
  if (typeof options.evidenceDirectory !== 'string' || options.evidenceDirectory.length === 0) {
    throw new Error('options.evidenceDirectory is required')
  }
  let provenanceBinding = { required: false }
  let provenanceAttachments = {}
  if (config.evidence?.requireProvenance === true) {
    const provenanceValidation = validateCapacityRunManifest(options.manifest, {
      evidenceFiles: options.provenanceEvidenceFiles,
      requireEvidenceFiles: true,
      expectedRunId: options.expectedSeedRunId,
      expectedSourceCommit: options.expectedSourceCommit,
      expectedStackInstanceNonce: options.expectedStackInstanceNonce,
    })
    if (!provenanceValidation.ok) {
      throw new Error(
        `scenario requires a passing immutable provenance manifest: ${provenanceValidation.errors.join('; ')}`,
      )
    }
    const protectedEvidence = createProtectedProvenanceEvidence(
      options.manifest,
      options.provenanceEvidenceFiles,
    )
    provenanceBinding = protectedEvidence.binding
    provenanceAttachments = protectedEvidence.attachments
  }

  const clock = options.clock ?? Date.now
  const now = options.now ?? (() => new Date())
  const runId = options.runId ?? `${config.id}-${randomUUID()}`
  const startedAt = now().toISOString()
  const startedTick = clock()
  const controller = new AbortController()
  const externalSignal = options.signal
  let externalAbortListener
  let abortReason = null
  let status = 'RUNNING'
  const raw = []
  const evidenceErrors = []
  const loaderResults = {}
  const warmupResults = {}
  const metricSnapshots = []
  const enabledLoaders = loaderNames.filter((name) => config.loaders[name]?.enabled === true)
  const loaders = { ...defaultLoaders, ...(options.loaders ?? {}) }
  const adapters = resolveMetricAdapters(options.adapters)

  const abort = (reason, details = {}) => {
    if (controller.signal.aborted) return
    abortReason = reason
    controller.abort({ reason, ...details })
  }
  const collector = createMetricCollector(config.metrics, adapters, {
    clock,
    raw,
    snapshots: metricSnapshots,
    evidenceErrors,
    onSnapshot: () => {
      const decision = evaluateCollectorAbort(
        metricSnapshots,
        config.execution.abort?.maxCollectorFailures,
      )
      if (decision.abort) abort(decision.reason, decision.details)
    },
  })
  if (externalSignal) {
    externalAbortListener = () => abort('external-signal')
    if (externalSignal.aborted) externalAbortListener()
    else externalSignal.addEventListener('abort', externalAbortListener, { once: true })
  }

  const timeoutMs = Number(config.execution.abort?.maxDurationMs ?? 0)
  const timeout = timeoutMs > 0
    ? setTimeout(() => abort('max-duration-exceeded', { timeoutMs }), timeoutMs)
    : null

  raw.push(lifecycle('run.started', clock(), { runId, scenarioId: config.id }))
  try {
    await collector.capture('before-warmup')
    if (!controller.signal.aborted && config.execution.warmup?.enabled !== false) {
      const warmup = await executeLoaderSet({
        config,
        options,
        loaders,
        enabledLoaders,
        mode: config.execution.warmup?.mode ?? config.execution.mode,
        phase: 'warmup',
        runId,
        signal: controller.signal,
        durationMs: Number(config.execution.warmup?.durationMs ?? 0),
        raw,
      })
      Object.assign(warmupResults, warmup.results)
      if (warmup.thrown.length > 0) evidenceErrors.push(...warmup.thrown)
      const warmupFailed = Object.values(warmup.results).some((result) => result?.ok === false)
      if (warmupFailed && config.execution.warmup?.required !== false) {
        evidenceErrors.push({
          code: 'warmup_failed',
          message: 'required warmup did not complete successfully',
        })
        abort('required-warmup-failed')
      }
    }

    if (!controller.signal.aborted) {
      await collector.capture('before-measured-run')
      collector.start()
      const measured = await executeLoaderSet({
        config,
        options,
        loaders,
        enabledLoaders,
        mode: config.execution.mode,
        phase: 'measured',
        runId,
        signal: controller.signal,
        durationMs: Number(config.execution.durationMs ?? 0),
        raw,
        onResult: (name, result) => {
          const decision = evaluateAbortRules(
            [...Object.values(loaderResults), result],
            config.execution.abort ?? {},
          )
          if (decision.abort) abort(decision.reason, decision.details)
        },
      })
      Object.assign(loaderResults, measured.results)
      evidenceErrors.push(...measured.thrown)
      if (!controller.signal.aborted) {
        const decision = evaluateAbortRules(Object.values(loaderResults), config.execution.abort ?? {})
        if (decision.abort) abort(decision.reason, decision.details)
      }
    }
  } catch (error) {
    status = 'FAILED'
    evidenceErrors.push({
      code: 'scenario_failure',
      message: safeDiagnostic(error),
    })
  } finally {
    collector.stop()
    await collector.settle()
    await collector.capture('after-run')
    if (timeout) clearTimeout(timeout)
    if (externalSignal && externalAbortListener) {
      externalSignal.removeEventListener('abort', externalAbortListener)
    }
  }

  const collectorDecision = evaluateCollectorAbort(
    metricSnapshots,
    config.execution.abort?.maxCollectorFailures,
  )
  if (collectorDecision.abort && status !== 'FAILED') {
    abort(collectorDecision.reason, collectorDecision.details)
  }
  if (controller.signal.aborted && status !== 'FAILED') {
    status = 'ABORTED'
    evidenceErrors.push({
      code: 'scenario_aborted',
      message: `scenario aborted: ${abortReason ?? 'unspecified'}`,
    })
  } else if (status === 'RUNNING') {
    status = 'COMPLETED'
  }

  const evaluations = evaluateThresholds(loaderResults, metricSnapshots, config.thresholds ?? {})
  const loaderErrors = collectLoaderErrors(loaderResults)
  evidenceErrors.push(...loaderErrors)
  for (const evaluation of evaluations.filter((entry) => !entry.passed)) {
    evidenceErrors.push({
      code: 'threshold_failed',
      message: evaluation.message,
      threshold: evaluation.id,
    })
  }

  const conclusion = status === 'COMPLETED' && evidenceErrors.length === 0
    ? 'Pass'
    : 'Fail'
  const finishedAt = now().toISOString()
  raw.push(lifecycle('run.finished', clock(), { status, conclusion, abortReason }))

  const redactedErrors = redactSecrets(evidenceErrors)
  const scenarioManifest = createManifest(config, options, {
    runId,
    startedAt,
    finishedAt,
    enabledLoaders,
    metricSnapshots,
    provenanceBinding,
  })
  const bundleInput = {
    run: {
      schemaVersion: 'colla.capacity-scenario-run/v1',
      runId,
      scenarioId: config.id,
      scenarioRevision: config.revision,
      status,
      startedAt,
      finishedAt,
      abortReason,
      provenanceBindingDigest: provenanceBinding.identityDigest ?? null,
    },
    manifest: scenarioManifest,
    threshold: {
      schemaVersion: 'colla.capacity-scenario-thresholds/v1',
      configured: redactSecrets(config.thresholds ?? {}),
      evaluations,
    },
    raw: redactSecrets(raw),
    summary: {
      schemaVersion: 'colla.capacity-scenario-summary/v1',
      conclusion,
      status,
      loaders: summarizeLoaderResults(loaderResults),
      warmup: summarizeLoaderResults(warmupResults),
      metrics: summarizeMetricSnapshots(metricSnapshots),
      thresholdsPassed: evaluations.every((entry) => entry.passed),
      durationMs: Math.max(0, clock() - startedTick),
    },
    errors: redactedErrors,
    attachments: provenanceAttachments,
  }

  const bundle = await createEvidenceBundle(options.evidenceDirectory, bundleInput)
  const verification = await verifyEvidenceBundle(options.evidenceDirectory, {
    expectedSeedRunId: options.expectedSeedRunId,
    expectedSourceCommit: options.expectedSourceCommit,
    expectedStackInstanceNonce: options.expectedStackInstanceNonce,
  })
  if (!verification.ok) {
    throw new Error(`created evidence bundle failed verification: ${verification.errors.join('; ')}`)
  }
  return {
    run: bundleInput.run,
    summary: bundleInput.summary,
    errors: redactedErrors,
    loaderResults: redactSecrets(loaderResults),
    metricSnapshots: redactSecrets(metricSnapshots),
    bundle,
    verification,
  }
}

export function parsePrometheusText(text, options = {}) {
  const allowedMetrics = new Set(options.metricNames ?? options.allowMetrics ?? [])
  const allowedLabels = new Set(options.labelAllowlist ?? options.allowLabels ?? [])
  const samples = []
  for (const rawLine of String(text ?? '').split(/\r?\n/)) {
    const line = rawLine.trim()
    if (!line || line.startsWith('#')) continue
    const match = line.match(/^([a-zA-Z_:][a-zA-Z0-9_:]*)(?:\{(.*)\})?\s+([^\s]+)(?:\s+\d+)?$/)
    if (!match) continue
    const [, metric, labelText, rawValue] = match
    if (allowedMetrics.size > 0 && !allowedMetrics.has(metric)) continue
    const value = Number(rawValue)
    if (!Number.isFinite(value)) continue
    const labels = {}
    for (const [key, labelValue] of parsePrometheusLabels(labelText)) {
      if (allowedLabels.has(key)) labels[key] = labelValue
    }
    samples.push({ metric, value, ...(Object.keys(labels).length ? { labels } : {}) })
  }
  return samples
}

export function createPrometheusMetricsAdapter(options = {}) {
  const fetchImpl = options.fetch ?? globalThis.fetch
  if (typeof fetchImpl !== 'function') {
    throw new TypeError('Prometheus metrics adapter requires fetch')
  }
  return {
    async collect(source, context = {}) {
      const response = await fetchImpl(resolveSourceEndpoint(source, options.environment), {
        method: 'GET',
        headers: resolveSourceHeaders(source, options.environment),
        signal: context.signal,
      })
      if (!response.ok) throw new Error(`metrics endpoint returned HTTP ${response.status}`)
      const text = await response.text()
      return parsePrometheusText(text, source)
    },
  }
}

export function createJsonMetricsAdapter(options = {}) {
  const fetchImpl = options.fetch ?? globalThis.fetch
  if (typeof fetchImpl !== 'function') {
    throw new TypeError('JSON metrics adapter requires fetch')
  }
  return {
    async collect(source, context = {}) {
      const response = await fetchImpl(resolveSourceEndpoint(source, options.environment), {
        method: 'GET',
        headers: resolveSourceHeaders(source, options.environment),
        signal: context.signal,
      })
      if (!response.ok) throw new Error(`metrics endpoint returned HTTP ${response.status}`)
      return flattenJsonMetrics(await response.json(), source.metricNames ?? source.allowMetrics)
    },
  }
}

function validateHeaderEnvironment(source, label, errors) {
  if (source.headerEnv === undefined) return
  if (!source.headerEnv || typeof source.headerEnv !== 'object' || Array.isArray(source.headerEnv)) {
    errors.push(`${label} source ${source.id ?? '<unknown>'} headerEnv must be an object`)
    return
  }
  for (const [header, environmentName] of Object.entries(source.headerEnv)) {
    if (!header || typeof environmentName !== 'string' || !environmentName) {
      errors.push(`${label} source ${source.id ?? '<unknown>'} headerEnv entries must name an environment variable`)
    }
  }
}

function resolveSourceHeaders(source, environment = process.env) {
  const headers = { ...(source.headers ?? {}) }
  for (const [header, environmentName] of Object.entries(source.headerEnv ?? {})) {
    const value = environment?.[environmentName]
    if (!value) {
      throw new Error(`metrics source ${source.id ?? '<unknown>'} requires environment variable ${environmentName}`)
    }
    headers[header] = value
  }
  return Object.keys(headers).length > 0 ? headers : undefined
}

function resolveSourceEndpoint(source, environment = process.env) {
  if (source.endpointEnv) {
    const value = environment?.[source.endpointEnv]
    if (!value) {
      throw new Error(`metrics source ${source.id ?? '<unknown>'} requires environment variable ${source.endpointEnv}`)
    }
    return value
  }
  return source.endpoint
}

export function flattenJsonMetrics(value, metricNames = [], prefix = '') {
  const allowed = new Set(metricNames)
  const samples = []
  const visit = (candidate, pathName) => {
    if (typeof candidate === 'number' && Number.isFinite(candidate)) {
      if (allowed.size === 0 || allowed.has(pathName)) samples.push({ metric: pathName, value: candidate })
      return
    }
    if (!candidate || typeof candidate !== 'object' || Array.isArray(candidate)) return
    for (const [key, child] of Object.entries(candidate)) {
      visit(child, pathName ? `${pathName}.${key}` : key)
    }
  }
  visit(value, prefix)
  return samples
}

export function createDockerStatsAdapter(options = {}) {
  const execute = options.execFile ?? execFile
  return {
    async collect(source = {}) {
      const socketPath = source.socketPath ?? '/var/run/docker.sock'
      if (options.forceSocket === true || (process.platform !== 'win32' && existsSync(socketPath))) {
        return collectDockerSocketStats(source, {
          socketPath,
          request: options.socketRequest,
        })
      }
      const args = ['stats', '--no-stream', '--format', '{{json .}}']
      for (const container of source.containers ?? []) args.push(container)
      const result = await execute(options.command ?? 'docker', args, {
        windowsHide: true,
        maxBuffer: 4 * 1024 * 1024,
      })
      const stdout = typeof result === 'string' ? result : result.stdout
      return String(stdout ?? '')
        .split(/\r?\n/)
        .filter(Boolean)
        .map((line) => sanitizeDockerStat(JSON.parse(line)))
    },
  }
}

async function collectDockerSocketStats(source, options) {
  const containers = source.containers ?? []
  if (containers.length === 0) {
    throw new Error('Docker socket metrics require an explicit container allowlist')
  }
  return Promise.all(containers.map(async (container) => {
    const stats = await dockerSocketJson(
      options.socketPath,
      `/containers/${encodeURIComponent(container)}/stats?stream=false`,
      options.request,
    )
    return sanitizeDockerStat(formatDockerApiStat(container, stats))
  }))
}

function dockerSocketJson(socketPath, requestPath, requestImpl = httpRequest) {
  return new Promise((resolveRequest, rejectRequest) => {
    const request = requestImpl({
      socketPath,
      path: requestPath,
      method: 'GET',
      headers: { Accept: 'application/json' },
    }, (response) => {
      const chunks = []
      response.on('data', (chunk) => chunks.push(chunk))
      response.on('end', () => {
        if ((response.statusCode ?? 500) >= 400) {
          rejectRequest(new Error(`Docker stats API returned HTTP ${response.statusCode}`))
          return
        }
        try {
          resolveRequest(JSON.parse(Buffer.concat(chunks).toString('utf8')))
        } catch {
          rejectRequest(new Error('Docker stats API returned invalid JSON'))
        }
      })
    })
    request.on('error', rejectRequest)
    request.end()
  })
}

function formatDockerApiStat(container, stats) {
  const cpuDelta = Number(stats.cpu_stats?.cpu_usage?.total_usage ?? 0)
    - Number(stats.precpu_stats?.cpu_usage?.total_usage ?? 0)
  const systemDelta = Number(stats.cpu_stats?.system_cpu_usage ?? 0)
    - Number(stats.precpu_stats?.system_cpu_usage ?? 0)
  const onlineCpus = Number(stats.cpu_stats?.online_cpus
    ?? stats.cpu_stats?.cpu_usage?.percpu_usage?.length
    ?? 1)
  const cpuPercent = systemDelta > 0 ? cpuDelta / systemDelta * onlineCpus * 100 : 0
  const memoryUsage = Number(stats.memory_stats?.usage ?? 0)
    - Number(stats.memory_stats?.stats?.cache ?? 0)
  const memoryLimit = Number(stats.memory_stats?.limit ?? 0)
  const memoryPercent = memoryLimit > 0 ? memoryUsage / memoryLimit * 100 : 0
  const networks = Object.values(stats.networks ?? {})
  const networkRx = networks.reduce((sum, item) => sum + Number(item.rx_bytes ?? 0), 0)
  const networkTx = networks.reduce((sum, item) => sum + Number(item.tx_bytes ?? 0), 0)
  const blockEntries = stats.blkio_stats?.io_service_bytes_recursive ?? []
  const blockRead = blockEntries
    .filter((item) => String(item.op).toLowerCase() === 'read')
    .reduce((sum, item) => sum + Number(item.value ?? 0), 0)
  const blockWrite = blockEntries
    .filter((item) => String(item.op).toLowerCase() === 'write')
    .reduce((sum, item) => sum + Number(item.value ?? 0), 0)
  return {
    Name: container,
    CPUPerc: `${cpuPercent.toFixed(2)}%`,
    MemPerc: `${memoryPercent.toFixed(2)}%`,
    MemUsage: `${formatBytes(memoryUsage)} / ${formatBytes(memoryLimit)}`,
    NetIO: `${formatBytes(networkRx)} / ${formatBytes(networkTx)}`,
    BlockIO: `${formatBytes(blockRead)} / ${formatBytes(blockWrite)}`,
    PIDs: Number(stats.pids_stats?.current ?? 0),
  }
}

function formatBytes(value) {
  if (!Number.isFinite(value) || value <= 0) return '0B'
  const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB']
  const index = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1)
  return `${(value / 1024 ** index).toFixed(index === 0 ? 0 : 2)}${units[index]}`
}

export async function collectMetricsSnapshot(metricsConfig = {}, adapters = {}, context = {}) {
  const capturedAt = (context.now ?? (() => new Date()))().toISOString()
  const snapshots = []
  for (const source of metricsConfig.prometheus ?? []) {
    snapshots.push(await collectOneMetricSource(
      'prometheus',
      source,
      adapters.prometheus,
      { ...context, capturedAt },
    ))
  }
  for (const source of metricsConfig.json ?? []) {
    snapshots.push(await collectOneMetricSource(
      'json',
      source,
      adapters.json,
      { ...context, capturedAt },
    ))
  }
  if (metricsConfig.docker?.enabled) {
    snapshots.push(await collectOneMetricSource(
      'docker',
      metricsConfig.docker,
      adapters.docker,
      { ...context, capturedAt },
    ))
  }
  return snapshots
}

function resolveMetricAdapters(adapters = {}) {
  return {
    prometheus: adapters.prometheus ?? createPrometheusMetricsAdapter({
      ...(adapters.fetch ? { fetch: adapters.fetch } : {}),
      environment: adapters.prometheusEnvironment ?? adapters.environment ?? process.env,
    }),
    json: adapters.json ?? createJsonMetricsAdapter({
      ...(adapters.fetch ? { fetch: adapters.fetch } : {}),
      environment: adapters.jsonEnvironment ?? adapters.environment ?? process.env,
    }),
    docker: adapters.docker ?? createDockerStatsAdapter({
      ...(adapters.execFile ? { execFile: adapters.execFile } : {}),
    }),
  }
}

function createMetricCollector(metricsConfig = {}, adapters, state) {
  const intervalMs = Number(metricsConfig?.intervalMs ?? 0)
  let timer = null
  let pending = Promise.resolve()

  async function capture(stage) {
    if (!hasMetricSources(metricsConfig)) return
    const snapshots = await collectMetricsSnapshot(metricsConfig, adapters, {
      stage,
      signal: undefined,
    })
    for (const snapshot of snapshots) {
      state.snapshots.push(snapshot)
      state.raw.push({
        kind: 'metric.snapshot',
        atMs: state.clock(),
        stage,
        ...snapshot,
      })
      if (!snapshot.ok && snapshot.required) {
        state.evidenceErrors.push({
          code: 'metric_collection_failed',
          source: snapshot.source,
          message: `required metric source ${snapshot.source} could not be collected`,
        })
      }
      state.onSnapshot?.(snapshot)
    }
  }

  return {
    capture,
    start() {
      if (timer || intervalMs <= 0 || !hasMetricSources(metricsConfig)) return
      timer = setInterval(() => {
        pending = pending.then(() => capture('measured-interval'))
      }, intervalMs)
      timer.unref?.()
    },
    stop() {
      if (timer) clearInterval(timer)
      timer = null
    },
    settle() {
      return pending
    },
  }
}

async function executeLoaderSet(input) {
  const results = {}
  const thrown = []
  const execute = async (name) => {
    if (input.signal.aborted) return
    const handler = input.loaders[name]
    if (typeof handler !== 'function') {
      const error = { code: 'loader_missing', loader: name, message: `loader ${name} is unavailable` }
      thrown.push(error)
      results[name] = failedLoaderResult(name, error.message)
      return
    }
    const entry = input.config.loaders[name]
    const baseOptions = entry.options ?? {}
    const runtimeOptions = input.options.loaderOptions?.[name] ?? {}
    const phaseOptions = input.phase === 'warmup' ? entry.warmupOptions ?? {} : {}
    const durationOptions = input.durationMs > 0 ? { durationMs: input.durationMs } : {}
    const mergedOptions = {
      ...baseOptions,
      ...runtimeOptions,
      ...phaseOptions,
      ...durationOptions,
    }
    const runtimeValues = mergedOptions.runtimeValues
    const phaseProbeRunId = runtimeValues?.probeRunIds?.[input.phase]
    const phaseRuntimeValues = phaseProbeRunId
      ? { ...runtimeValues, probeRunId: phaseProbeRunId }
      : runtimeValues
    try {
      const result = await raceWithSignal(
        Promise.resolve(handler({
          ...mergedOptions,
          ...(phaseRuntimeValues ? { runtimeValues: phaseRuntimeValues } : {}),
          scenarioPhase: input.phase,
          scenarioRunId: input.runId,
          signal: input.signal,
        })),
        input.signal,
      )
      results[name] = result
      appendLoaderRaw(input.raw, input.phase, name, result)
      input.onResult?.(name, result)
    } catch (error) {
      if (input.signal.aborted) {
        results[name] = failedLoaderResult(name, 'loader cancelled by scenario abort')
        appendLoaderRaw(input.raw, input.phase, name, results[name])
        return
      }
      const finding = {
        code: 'loader_threw',
        loader: name,
        message: safeDiagnostic(error),
      }
      thrown.push(finding)
      results[name] = failedLoaderResult(name, finding.message)
      appendLoaderRaw(input.raw, input.phase, name, results[name])
      input.onResult?.(name, results[name])
    }
  }

  if (input.mode === 'serial') {
    for (const name of input.enabledLoaders) {
      if (input.signal.aborted) break
      await execute(name)
    }
  } else {
    await Promise.all(input.enabledLoaders.map(execute))
  }
  return { results, thrown }
}

function appendLoaderRaw(raw, phase, loader, result) {
  raw.push({
    kind: 'loader.result',
    phase,
    loader,
    ok: result?.ok === true,
    durationMs: result?.durationMs ?? null,
    summary: result?.summary ?? {},
    metrics: result?.metrics ?? {},
  })
  for (const sample of result?.samples ?? []) {
    raw.push({ kind: 'loader.sample', phase, loader, ...sample })
  }
}

function evaluateAbortRules(results, rules) {
  const summary = aggregateLoaderResults(results)
  if (rules.stopOnLoaderFailure === true && results.some((result) => result?.ok === false)) {
    return { abort: true, reason: 'loader-failure', details: summary }
  }
  if (rules.maxErrorCount !== undefined && summary.errors > Number(rules.maxErrorCount)) {
    return { abort: true, reason: 'max-error-count-exceeded', details: summary }
  }
  if (rules.maxErrorRate !== undefined && summary.errorRate > Number(rules.maxErrorRate)) {
    return { abort: true, reason: 'max-error-rate-exceeded', details: summary }
  }
  return { abort: false }
}

function evaluateCollectorAbort(snapshots, configuredLimit) {
  if (configuredLimit === undefined) return { abort: false }
  const failures = snapshots.filter((snapshot) => !snapshot.ok).length
  if (failures > Number(configuredLimit)) {
    return {
      abort: true,
      reason: 'max-collector-failures-exceeded',
      details: { collectorFailures: failures },
    }
  }
  return { abort: false }
}

function evaluateThresholds(results, metricSnapshots, thresholds) {
  const evaluations = []
  const aggregate = aggregateLoaderResults(Object.values(results))
  addMaximumEvaluation(evaluations, 'global.error-rate', aggregate.errorRate, thresholds.maxErrorRate)
  addMaximumEvaluation(evaluations, 'global.p95-ms', aggregate.p95Ms, thresholds.maxP95Ms)
  addMinimumEvaluation(evaluations, 'global.samples', aggregate.samples, thresholds.minSamples)

  for (const [loader, configured] of Object.entries(thresholds.loaders ?? {})) {
    const result = results[loader]
    const summary = aggregateLoaderResults(result ? [result] : [])
    if (configured.required === true) {
      evaluations.push({
        id: `loader.${loader}.required`,
        passed: Boolean(result),
        actual: Boolean(result),
        expected: true,
        message: result ? `${loader} loader ran` : `${loader} loader did not run`,
      })
    }
    addMaximumEvaluation(evaluations, `loader.${loader}.error-rate`, summary.errorRate, configured.maxErrorRate)
    addMaximumEvaluation(evaluations, `loader.${loader}.p95-ms`, summary.p95Ms, configured.maxP95Ms)
    addMinimumEvaluation(evaluations, `loader.${loader}.samples`, summary.samples, configured.minSamples)
  }

  for (const assertion of thresholds.metrics ?? []) {
    const values = metricSnapshots
      .filter((snapshot) => snapshot.source === assertion.source && snapshot.ok)
      .flatMap((snapshot) => metricValues(snapshot.data, assertion.metric))
    const actual = metricAggregate(values, assertion.aggregate ?? 'max')
    const passed = compareMetric(actual, assertion.operator ?? '<=', Number(assertion.value))
    evaluations.push({
      id: assertion.id ?? `metric.${assertion.source}.${assertion.metric}`,
      passed,
      actual,
      expected: { operator: assertion.operator ?? '<=', value: Number(assertion.value) },
      message: passed
        ? `metric ${assertion.metric} satisfied its threshold`
        : `metric ${assertion.metric} did not satisfy ${assertion.operator ?? '<='} ${assertion.value}`,
    })
  }
  return evaluations
}

function summarizeLoaderResults(results) {
  return Object.fromEntries(Object.entries(results).map(([name, result]) => [
    name,
    {
      ok: result?.ok === true,
      durationMs: result?.durationMs ?? null,
      samples: result?.samples?.length ?? result?.summary?.overall?.count ?? 0,
      errors: result?.errors?.length ?? 0,
      p50Ms: result?.summary?.overall?.p50 ?? null,
      p95Ms: result?.summary?.overall?.p95 ?? null,
      p99Ms: result?.summary?.overall?.p99 ?? null,
    },
  ]))
}

function summarizeMetricSnapshots(snapshots) {
  const bySource = {}
  for (const snapshot of snapshots) {
    const current = bySource[snapshot.source] ?? { snapshots: 0, failures: 0 }
    current.snapshots += 1
    if (!snapshot.ok) current.failures += 1
    bySource[snapshot.source] = current
  }
  return bySource
}

function aggregateLoaderResults(results) {
  const samples = results.flatMap((result) => result?.samples ?? [])
  const errors = results.reduce((count, result) => count + (result?.errors?.length ?? 0), 0)
  const failedSamples = samples.filter((sample) => sample?.ok === false).length
  const latencies = samples.map((sample) => Number(sample?.latencyMs)).filter(Number.isFinite)
  const denominator = samples.length || (errors > 0 ? errors : 1)
  return {
    samples: samples.length,
    errors,
    failedSamples,
    errorRate: Math.min(1, Math.max(errors, failedSamples) / denominator),
    p95Ms: quantile(latencies, 0.95),
  }
}

function collectLoaderErrors(results) {
  return Object.entries(results).flatMap(([loader, result]) =>
    (result?.errors ?? []).map((error) => ({
      code: error?.code ?? 'loader_error',
      loader,
      message: error?.message ?? String(error),
    })))
}

function createManifest(config, options, context) {
  const safeConfig = redactSecrets(config)
  const safeRuntimeShape = Object.fromEntries(context.enabledLoaders.map((name) => [
    name,
    Object.keys(options.loaderOptions?.[name] ?? {}).sort(),
  ]))
  const runtimeFingerprints = Object.fromEntries(context.enabledLoaders.map((name) => [
    name,
    digest(stableStringify(redactSecrets(options.loaderOptions?.[name] ?? {}))),
  ]))
  return {
    schemaVersion: 'colla.capacity-scenario-manifest/v1',
    runId: context.runId,
    scenario: {
      id: config.id,
      revision: config.revision,
      schemaVersion: config.schemaVersion,
      fingerprint: digest(stableStringify(safeConfig)),
    },
    execution: {
      mode: config.execution.mode,
      warmup: config.execution.warmup ?? {},
      durationMs: config.execution.durationMs ?? 0,
      abort: config.execution.abort ?? {},
    },
    loaders: context.enabledLoaders,
    runtimeOptionKeys: safeRuntimeShape,
    runtimeFingerprints,
    runtimeBootstrap: options.bootstrapSummary ?? {},
    metricSources: metricSourceDescriptors(config.metrics),
    metricSnapshotCount: context.metricSnapshots.length,
    startedAt: context.startedAt,
    finishedAt: context.finishedAt,
    provenanceBinding: context.provenanceBinding,
    provenance: options.manifest ?? {},
  }
}

function createProtectedProvenanceEvidence(manifest, evidenceFiles = {}) {
  const checkpoints = {}
  const attachments = {}
  for (const [name, check] of Object.entries(manifest.seedExecution.checks).sort(([left], [right]) =>
    left.localeCompare(right))) {
    const bundlePath = `provenance/checkpoints/${name}.json`
    const raw = evidenceFiles[check.path]
    if (raw === undefined) {
      throw new Error(`scenario provenance checkpoint is missing: ${name}`)
    }
    checkpoints[name] = {
      sourcePath: check.path,
      bundlePath,
      sha256: check.sha256,
    }
    attachments[bundlePath] = raw
  }
  const identity = {
    seedRunId: manifest.seedExecution.runId,
    sourceCommit: manifest.sourceCommit,
    stackInstanceNonce: manifest.stack.instanceNonce,
    provenanceFingerprint: manifest.provenanceFingerprint,
    seedExecutionFingerprint: manifest.seedExecution.seedExecutionFingerprint,
  }
  return {
    binding: {
      schemaVersion: 'colla.capacity-scenario-provenance-binding/v1',
      required: true,
      ...identity,
      identityDigest: digest(stableStringify(identity)),
      checkpoints,
    },
    attachments,
  }
}

function metricSourceDescriptors(config = {}) {
  return [
    ...(config.prometheus ?? []).map((source) => ({
      id: source.id,
      kind: 'prometheus',
      required: source.required !== false,
      metricNames: source.metricNames ?? source.allowMetrics ?? [],
      labelAllowlist: source.labelAllowlist ?? source.allowLabels ?? [],
    })),
    ...(config.json ?? []).map((source) => ({
      id: source.id,
      kind: 'json',
      required: source.required !== false,
      metricNames: source.metricNames ?? source.allowMetrics ?? [],
    })),
    ...(config.docker?.enabled
      ? [{ id: config.docker.id, kind: 'docker', required: config.docker.required !== false }]
      : []),
  ]
}

async function collectOneMetricSource(kind, source, adapter, context) {
  const required = source.required !== false
  if (!adapter) {
    return {
      source: source.id,
      kind,
      required,
      capturedAt: context.capturedAt,
      stage: context.stage,
      ok: false,
      error: 'adapter unavailable',
      data: [],
    }
  }
  try {
    const collect = typeof adapter === 'function' ? adapter : adapter.collect?.bind(adapter)
    if (!collect) throw new Error('adapter has no collect function')
    const data = await collect(source, context)
    const requiredMetrics = source.requiredMetricNames ?? source.metricNames ?? source.allowMetrics ?? []
    const collectedMetrics = new Set((Array.isArray(data) ? data : [])
      .map((sample) => sample?.metric)
      .filter(Boolean))
    const missingMetrics = requiredMetrics.filter((metric) => !collectedMetrics.has(metric))
    if (required && missingMetrics.length > 0) {
      throw new Error(`required metrics missing: ${missingMetrics.join(', ')}`)
    }
    return redactSecrets({
      source: source.id,
      kind,
      required,
      capturedAt: context.capturedAt,
      stage: context.stage,
      ok: true,
      data,
    })
  } catch {
    return {
      source: source.id,
      kind,
      required,
      capturedAt: context.capturedAt,
      stage: context.stage,
      ok: false,
      error: 'collection failed',
      data: [],
    }
  }
}

function raceWithSignal(promise, signal) {
  if (!signal) return promise
  if (signal.aborted) return Promise.reject(createAbortError())
  return new Promise((resolve, reject) => {
    const onAbort = () => {
      signal.removeEventListener('abort', onAbort)
      reject(createAbortError())
    }
    signal.addEventListener('abort', onAbort, { once: true })
    promise.then(
      (value) => {
        signal.removeEventListener('abort', onAbort)
        resolve(value)
      },
      (error) => {
        signal.removeEventListener('abort', onAbort)
        reject(error)
      },
    )
  })
}

function createAbortError() {
  const error = new Error('scenario aborted')
  error.name = 'AbortError'
  return error
}

function hasMetricSources(config = {}) {
  return (config.prometheus?.length ?? 0) > 0 ||
    (config.json?.length ?? 0) > 0 ||
    config.docker?.enabled === true
}

function sanitizeDockerStat(value) {
  const allowed = [
    'BlockIO',
    'CPUPerc',
    'Container',
    'ID',
    'MemPerc',
    'MemUsage',
    'Name',
    'NetIO',
    'PIDs',
  ]
  return Object.fromEntries(allowed.filter((key) => value[key] !== undefined).map((key) => [key, value[key]]))
}

function parsePrometheusLabels(text) {
  if (!text) return []
  const labels = []
  const pattern = /([a-zA-Z_][a-zA-Z0-9_]*)="((?:\\.|[^"\\])*)"/g
  let match
  while ((match = pattern.exec(text)) !== null) {
    labels.push([match[1], match[2].replace(/\\"/g, '"').replace(/\\\\/g, '\\')])
  }
  return labels
}

function metricValues(data, metric) {
  if (!Array.isArray(data)) return []
  return data
    .filter((entry) => entry?.metric === metric)
    .map((entry) => Number(entry.value))
    .filter(Number.isFinite)
}

function metricAggregate(values, aggregate) {
  if (values.length === 0) return null
  if (aggregate === 'min') return Math.min(...values)
  if (aggregate === 'mean') return values.reduce((sum, value) => sum + value, 0) / values.length
  if (aggregate === 'last') return values.at(-1)
  if (aggregate === 'p95') return quantile(values, 0.95)
  return Math.max(...values)
}

function compareMetric(actual, operator, expected) {
  if (!Number.isFinite(actual) || !Number.isFinite(expected)) return false
  if (operator === '<') return actual < expected
  if (operator === '>=') return actual >= expected
  if (operator === '>') return actual > expected
  if (operator === '==') return actual === expected
  return actual <= expected
}

function addMaximumEvaluation(evaluations, id, actual, expected) {
  if (expected === undefined) return
  const passed = Number.isFinite(actual) && actual <= Number(expected)
  evaluations.push({
    id,
    passed,
    actual,
    expected: { operator: '<=', value: Number(expected) },
    message: passed ? `${id} passed` : `${id} exceeded ${expected}`,
  })
}

function addMinimumEvaluation(evaluations, id, actual, expected) {
  if (expected === undefined) return
  const passed = Number.isFinite(actual) && actual >= Number(expected)
  evaluations.push({
    id,
    passed,
    actual,
    expected: { operator: '>=', value: Number(expected) },
    message: passed ? `${id} passed` : `${id} was below ${expected}`,
  })
}

function failedLoaderResult(name, message) {
  return {
    scenario: name,
    ok: false,
    durationMs: 0,
    samples: [],
    summary: { overall: { count: 0, p50: null, p95: null, p99: null }, operations: {} },
    metrics: {},
    errors: [{ code: 'loader_failure', message }],
  }
}

function lifecycle(event, atMs, details = {}) {
  return { kind: 'lifecycle', event, atMs, ...details }
}

function digest(value) {
  return createHash('sha256').update(value).digest('hex')
}

function safeDiagnostic(error) {
  return error instanceof Error
    ? `${error.name || 'Error'} while executing scenario component`
    : 'non-Error value thrown while executing scenario component'
}

function validatePositive(value, name, errors, optional = false) {
  if (optional && value === undefined) return
  if (!Number.isFinite(Number(value)) || Number(value) <= 0) errors.push(`${name} must be greater than zero`)
}

function validateNonNegative(value, name, errors, optional = false) {
  if (optional && value === undefined) return
  if (!Number.isFinite(Number(value)) || Number(value) < 0) errors.push(`${name} must be zero or greater`)
}

function validateRatio(value, name, errors, optional = false) {
  if (optional && value === undefined) return
  if (!Number.isFinite(Number(value)) || Number(value) < 0 || Number(value) > 1) {
    errors.push(`${name} must be between zero and one`)
  }
}
