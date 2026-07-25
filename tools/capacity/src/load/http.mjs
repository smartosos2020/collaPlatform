import {
  addAbortError,
  addErrors,
  createError,
  createScenarioResult,
  defaultClock,
  executeHttpRequest,
  finalizeScenarioResult,
  resolveTemplate,
  runRateSchedule,
  runWithConcurrency,
  stableStringify,
} from './common.mjs'

export async function runHttpScenario(options = {}) {
  const fetchImpl = options.fetch ?? globalThis.fetch
  if (typeof fetchImpl !== 'function') throw new TypeError('options.fetch or global fetch is required')

  const clock = options.clock ?? defaultClock
  const startedAt = clock()
  const samples = []
  const errors = []
  const signal = options.signal
  const baseUrl = trimSlash(options.apiBaseUrl ?? options.baseUrl ?? '')
  const targets = options.targets ?? {}
  const users = normalizeUsers(options.users, options.token)
  const contexts = users.map((user, index) => ({
    ...createTemplateContext(options),
    user,
    userIndex: index,
    token: user.token ?? options.token ?? null,
  }))

  if (contexts.length === 0) {
    errors.push(createError('configuration', 'at least one user or token is required'))
  }

  await runWithConcurrency(contexts, options.concurrency, async (context, index) => {
    const loginTarget = targets.login
    if (!loginTarget) {
      if (!context.token) errors.push(createError('configuration', 'login target is required when a user has no token'))
      return
    }
    const result = await executeTarget(fetchImpl, baseUrl, 'login', loginTarget, context, clock, {
      iteration: index,
      index,
      signal,
    })
    recordResult(samples, errors, result, 'login', loginTarget.name ?? 'login', context.userIndex)
    if (result.ok) {
      const token = (loginTarget.extractToken ?? defaultTokenExtractor)(result.body)
      if (typeof token !== 'string' || token.length === 0) {
        errors.push(createError('login_semantics', 'login response did not contain a usable access token', {
          operation: loginTarget.name ?? 'login',
          userIndex: context.userIndex,
        }))
      } else {
        context.token = token
      }
    }
  }, { signal })

  const rounds = normalizeRounds(options)
  const readTargets = normalizeTargets(targets.read)
  const writeTargets = normalizeTargets(targets.write)
  if (readTargets.length === 0) errors.push(createError('configuration', 'at least one read target is required'))
  if (writeTargets.length === 0) errors.push(createError('configuration', 'at least one write target is required'))

  const workload = await runTargetWorkload({
    rounds,
    durationMs: options.durationMs,
    targetRps: options.targetRps ?? options.requestsPerSecond,
    phaseWeights: options.phaseWeights,
    signal,
    sleep: options.sleep,
    clock,
    contexts,
    concurrency: options.concurrency,
    phases: [
      { name: 'read', targets: readTargets },
      { name: 'write', targets: writeTargets },
    ],
    handler: async (phase, target, context, index) => {
      const result = await executeTarget(
        fetchImpl,
        baseUrl,
        phase,
        target,
        context,
        clock,
        { iteration: index, index, signal },
      )
      recordResult(samples, errors, result, phase, target.name, context.userIndex)
    },
  })

  if (!signal?.aborted) await runIdempotencyTargets({
    fetchImpl,
    baseUrl,
    targets: normalizeTargets(targets.idempotency),
    contexts,
    clock,
    samples,
    errors,
    concurrency: options.concurrency,
    requestId: options.requestId,
    signal,
  })

  if (!signal?.aborted) await runFileTargets({
    fetchImpl,
    baseUrl,
    target: targets.file,
    contexts,
    clock,
    samples,
    errors,
    concurrency: options.concurrency,
    signal,
  })

  addAbortError(errors, signal, { loader: 'http' })
  const loadSamples = samples.filter((sample) => sample.phase === 'read' || sample.phase === 'write')
  const loadDurationMs = workload.durationMs
  const result = createScenarioResult('http', startedAt, samples, errors, {
    aborted: signal?.aborted === true,
    users: contexts.length,
    targetRps: workload.targetRps,
    achievedRps: loadDurationMs > 0 ? loadSamples.length * 1_000 / loadDurationMs : 0,
    loadDurationMs,
    scheduledRequests: workload.scheduled,
    phaseCounts: {
      read: loadSamples.filter((sample) => sample.phase === 'read').length,
      write: loadSamples.filter((sample) => sample.phase === 'write').length,
    },
    configuredTargets: {
      read: readTargets.length,
      write: writeTargets.length,
      idempotency: normalizeTargets(targets.idempotency).length,
      file: targets.file ? 1 : 0,
    },
  }, clock)
  return finalizeScenarioResult(result, options.outputDir, options.outputFileName)
}

async function runIdempotencyTargets(config) {
  const { targets, contexts, concurrency } = config
  if (targets.length === 0) {
    config.errors.push(createError('configuration', 'at least one idempotency target is required'))
    return
  }
  const jobs = crossProduct(contexts, targets)
  await runWithConcurrency(jobs, concurrency, async ({ context, target }, index) => {
    const requestId = typeof config.requestId === 'function'
      ? config.requestId({ context, target, index })
      : `capacity-${Date.now()}-${context.userIndex}-${index}`
    const headers = { [target.idempotencyHeader ?? 'X-Colla-Request-Id']: requestId }
    const first = await executeTarget(
      config.fetchImpl,
      config.baseUrl,
      'idempotency',
      target,
      context,
      config.clock,
      { requestId, iteration: index, index, extraHeaders: headers, replay: false, signal: config.signal },
    )
    recordResult(config.samples, config.errors, first, 'idempotency.first', target.name, context.userIndex)
    const replay = await executeTarget(
      config.fetchImpl,
      config.baseUrl,
      'idempotency',
      target,
      context,
      config.clock,
      { requestId, iteration: index, index, extraHeaders: headers, replay: true, signal: config.signal },
    )
    recordResult(config.samples, config.errors, replay, 'idempotency.replay', target.name, context.userIndex)

    if (first.ok && replay.ok) {
      const select = target.idempotencyValue ?? ((body) => body)
      if (stableStringify(select(first.body)) !== stableStringify(select(replay.body))) {
        config.errors.push(createError('idempotency_mismatch', 'idempotent replay returned different semantics', {
          operation: target.name,
          userIndex: context.userIndex,
          requestId,
        }))
      }
    }
  }, { signal: config.signal })
}

async function runFileTargets(config) {
  if (!config.target) {
    config.errors.push(createError('configuration', 'file target with prepare, upload and complete steps is required'))
    return
  }
  const requiredSteps = ['prepare', 'upload', 'complete']
  for (const step of requiredSteps) {
    if (!config.target[step]) {
      config.errors.push(createError('configuration', `file target is missing ${step} step`))
      return
    }
  }

  await runWithConcurrency(config.contexts, config.concurrency, async (context, index) => {
    const state = {}
    for (const step of requiredSteps) {
      const target = config.target[step]
      const stage = `file.${step}`
      const result = await executeTarget(
        config.fetchImpl,
        config.baseUrl,
        stage,
        target,
        context,
        config.clock,
        { file: state, iteration: index, index, signal: config.signal },
      )
      recordResult(config.samples, config.errors, result, stage, target.name ?? stage, context.userIndex)
      if (!result.ok) return
      state[step] = result.body
      if (step === 'prepare') {
        state.uploadUrl = (config.target.extractUploadUrl ?? defaultUploadUrlExtractor)(result.body)
        state.fileId = (config.target.extractFileId ?? defaultFileIdExtractor)(result.body)
        if (!state.uploadUrl || !state.fileId) {
          config.errors.push(createError('file_semantics', 'file prepare response requires uploadUrl and fileId', {
            operation: target.name ?? stage,
            userIndex: context.userIndex,
          }))
          return
        }
      }
    }
  }, { signal: config.signal })
}

async function runTargetWorkload(config) {
  const phases = config.phases.filter((phase) => phase.targets.length > 0)
  if (phases.length === 0 || config.contexts.length === 0 || config.signal?.aborted) {
    return { scheduled: 0, durationMs: 0, targetRps: Number(config.targetRps) || null }
  }

  const phasePicker = createWeightedPhasePicker(phases, config.phaseWeights)
  const phaseCursors = new Map(phases.map((phase) => [phase.name, 0]))
  const jobsByPhase = new Map(phases.map((phase) => [
    phase.name,
    crossProduct(config.contexts, phase.targets),
  ]))
  let invoked = 0
  const invoke = async (index) => {
    invoked += 1
    const phase = phasePicker()
    const jobs = jobsByPhase.get(phase.name)
    const cursor = phaseCursors.get(phase.name)
    phaseCursors.set(phase.name, cursor + 1)
    const job = jobs[cursor % jobs.length]
    await config.handler(phase.name, job.target, job.context, index)
  }

  const targetRps = Number(config.targetRps)
  if (config.durationMs > 0 && targetRps > 0) {
    const pacing = await runRateSchedule({
      durationMs: config.durationMs,
      ratePerSecond: targetRps,
      concurrency: config.concurrency,
      signal: config.signal,
      sleep: config.sleep,
      clock: config.clock,
      handler: invoke,
    })
    return { ...pacing, scheduled: invoked, targetRps }
  }

  const workloadStartedAt = config.clock()
  let scheduled = 0
  if (config.durationMs > 0) {
    const deadline = workloadStartedAt + Number(config.durationMs)
    while (config.clock() < deadline && !config.signal?.aborted) {
      const jobs = Array.from({ length: Math.max(1, Number(config.concurrency) || 1) }, (_, offset) =>
        scheduled + offset)
      await runWithConcurrency(jobs, config.concurrency, invoke, { signal: config.signal })
      scheduled += jobs.length
    }
  } else {
    const rounds = config.rounds ?? 1
    for (let round = 0; round < rounds && !config.signal?.aborted; round += 1) {
      for (const phase of phases) {
        const jobs = jobsByPhase.get(phase.name)
        await runWithConcurrency(jobs, config.concurrency, ({ context, target }, index) =>
          config.handler(phase.name, target, context, round * jobs.length + index), { signal: config.signal })
        scheduled += jobs.length
      }
    }
  }
  return {
    scheduled: invoked,
    durationMs: Math.max(0, config.clock() - workloadStartedAt),
    targetRps: targetRps > 0 ? targetRps : null,
  }
}

async function executeTarget(fetchImpl, baseUrl, stage, target, context, clock, extra = {}) {
  const requestContext = { ...context, ...extra, stage, target }
  const path = resolveTemplate(target.url ?? target.path, requestContext)
    ?? (stage === 'login' ? '/api/auth/login' : undefined)
  const preparedUploadUrl = stage === 'file.upload' ? extra.file?.uploadUrl : undefined
  if ((!path || typeof path !== 'string') && !preparedUploadUrl) {
    return { ok: false, status: null, latencyMs: 0, body: undefined, errors: ['target URL or path is required'] }
  }
  const url = preparedUploadUrl
    ? preparedUploadUrl
    : absoluteUrl(baseUrl, path)
  const method = target.method ?? defaultMethod(stage)
  const preparedHeaders = stage === 'file.upload'
    ? extra.file?.prepare?.headers
    : undefined
  const headers = mergeHeaders(
    isPlainObject(preparedHeaders) ? preparedHeaders : undefined,
    resolveTemplate(target.headers, requestContext),
    extra.extraHeaders,
  )
  if (context.token && target.auth !== false && !headers.Authorization && !headers.authorization) {
    headers.Authorization = `Bearer ${context.token}`
  }
  let body = resolveTemplate(target.body, requestContext)
  if (stage === 'login' && body === undefined) body = defaultLoginBody(context.user)
  if (stage === 'file.complete' && body === undefined) {
    body = {
      fileId: extra.file.fileId,
      ...resolveTemplate(target.target ?? {}, requestContext),
    }
  }
  if (stage === 'file.upload' && body === undefined) {
    body = resolveTemplate(target.content ?? 'capacity-file', requestContext)
  }
  const rawBody = typeof body === 'string' || body instanceof Uint8Array || body == null
  if (!rawBody && !(typeof FormData !== 'undefined' && body instanceof FormData)) {
    body = JSON.stringify(body)
    if (!headers['Content-Type'] && !headers['content-type']) headers['Content-Type'] = 'application/json'
  }

  return executeHttpRequest(fetchImpl, url, {
    method,
    headers,
    body,
    signal: extra.signal ?? context.signal,
  }, target, {
    ...requestContext,
    clock,
  })
}

function recordResult(samples, errors, result, phase, operation, userIndex) {
  const sample = {
    phase,
    operation,
    userIndex,
    status: result.status,
    latencyMs: result.latencyMs,
    ok: result.ok,
    aborted: result.aborted === true,
  }
  samples.push(sample)
  if (!result.aborted) {
    addErrors(errors, result.errors, {
      code: 'http_semantic_failure',
      phase,
      operation,
      userIndex,
      status: result.status,
    })
  }
}

function createWeightedPhasePicker(phases, weights = {}) {
  const entries = phases.map((phase) => ({
    phase,
    weight: Math.max(0, Number(weights?.[phase.name] ?? 1) || 0),
    current: 0,
  }))
  if (entries.every((entry) => entry.weight === 0)) {
    for (const entry of entries) entry.weight = 1
  }
  const total = entries.reduce((sum, entry) => sum + entry.weight, 0)
  return () => {
    for (const entry of entries) entry.current += entry.weight
    const selected = entries.reduce((best, entry) => entry.current > best.current ? entry : best)
    selected.current -= total
    return selected.phase
  }
}

function normalizeUsers(users, token) {
  if (Array.isArray(users) && users.length) {
    return users.map((user) => typeof user === 'string' ? { token: user } : { ...user })
  }
  return token ? [{ token }] : []
}

function normalizeTargets(value) {
  if (!value) return []
  const targets = Array.isArray(value) ? value : [value]
  return targets.map((target, index) => ({
    name: target.name ?? `target-${index + 1}`,
    ...target,
  }))
}

function normalizeRounds(options) {
  if (options.iterations !== undefined) return Math.max(1, Number(options.iterations) || 1)
  return options.durationMs > 0 ? null : 1
}

function crossProduct(left, right) {
  return left.flatMap((context) => right.map((target) => ({ context, target })))
}

function createTemplateContext(options) {
  const runtimeValues = isPlainObject(options.runtimeValues) ? options.runtimeValues : {}
  return {
    ...runtimeValues,
    options,
    runtimeValues,
  }
}

function isPlainObject(value) {
  if (!value || typeof value !== 'object') return false
  const prototype = Object.getPrototypeOf(value)
  return prototype === Object.prototype || prototype === null
}

function mergeHeaders(...sources) {
  const headers = Object.create(null)
  const names = new Map()
  for (const source of sources) {
    if (!source) continue
    for (const [name, value] of Object.entries(source)) {
      const normalized = name.toLowerCase()
      const previous = names.get(normalized)
      if (previous !== undefined) delete headers[previous]
      headers[name] = value
      names.set(normalized, name)
    }
  }
  return headers
}

function defaultMethod(stage) {
  return stage === 'read' ? 'GET' : stage === 'file.upload' ? 'PUT' : 'POST'
}

function defaultLoginBody(user) {
  return {
    username: user.username,
    password: user.password,
    deviceType: user.deviceType ?? 'WEB',
    deviceFingerprint: user.deviceFingerprint ?? `capacity-${user.username ?? 'user'}`,
    deviceName: user.deviceName ?? 'capacity-loader',
    appVersion: user.appVersion ?? 's05-m1',
  }
}

function defaultTokenExtractor(body) {
  return body?.accessToken ?? body?.token
}

function defaultUploadUrlExtractor(body) {
  return body?.uploadUrl ?? body?.url
}

function defaultFileIdExtractor(body) {
  return body?.fileId ?? body?.uploadId ?? body?.id
}

function absoluteUrl(baseUrl, path) {
  if (/^https?:\/\//i.test(path)) return path
  return `${baseUrl}${path.startsWith('/') ? '' : '/'}${path}`
}

function trimSlash(value) {
  return String(value).replace(/\/+$/, '')
}
