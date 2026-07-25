import {
  addAbortError,
  addErrors,
  createError,
  createScenarioResult,
  defaultClock,
  executeHttpRequest,
  finalizeScenarioResult,
  getPath,
  resolveTemplate,
  runRateSchedule,
  runWithConcurrency,
  waitForDelay,
} from './common.mjs'

export async function runWorkerScenario(options = {}) {
  const fetchImpl = options.fetch ?? globalThis.fetch
  if (typeof fetchImpl !== 'function') throw new TypeError('options.fetch or global fetch is required')

  const clock = options.clock ?? defaultClock
  const signal = options.signal
  const startedAt = clock()
  const samples = []
  const errors = []
  const targets = options.targets ?? {}
  const producers = normalizeProducers(targets.producers ?? targets.producer)
  const diagnosticsTarget = targets.diagnostics
  const ledgerTarget = targets.ledger
  const token = options.token
  const produced = []
  const diagnostics = []
  const correctnessMode = normalizeCorrectnessMode(options.correctnessMode, errors)
  const templateContext = createTemplateContext(options)

  if (producers.length === 0) errors.push(createError('configuration', 'at least one named Worker producer is required'))
  if (!diagnosticsTarget) errors.push(createError('configuration', 'Worker diagnostics target is required'))

  if (diagnosticsTarget && !signal?.aborted) {
    const baseline = await pollDiagnostics('baseline')
    if (baseline) diagnostics.push(baseline)
  }

  const production = await runProduction()

  async function produce(job, index, phase = job.producer.mode ?? 'produce') {
    const context = {
      ...templateContext,
      producer: job.producer,
      producerIndex: job.producerIndex,
      iteration: job.iteration,
      index,
      token,
      options,
      clock,
      signal,
    }
    const result = await requestTarget(fetchImpl, options.apiBaseUrl ?? options.baseUrl, job.producer, context)
    const sample = {
      phase,
      operation: `produce.${job.producer.name}`,
      producer: job.producer.name,
      iteration: job.iteration,
      status: result.status,
      latencyMs: result.latencyMs,
      ok: result.ok,
      aborted: result.aborted === true,
    }
    samples.push(sample)
    if (!result.aborted) {
      addErrors(errors, result.errors, {
        code: 'producer_semantic_failure',
        operation: sample.operation,
        producer: job.producer.name,
        iteration: job.iteration,
        status: result.status,
      })
    }
    if (result.ok) {
      const acknowledged = {
        ...extractProduced(job.producer, result.body, context),
        phase,
        requestIndex: index,
      }
      sample.eventId = acknowledged.eventId
      if (!isNonEmptyIdentifier(acknowledged.eventId)) {
        sample.ok = false
        errors.push(createError('missing_event_id', 'successful producer response did not contain a non-empty eventId', {
          operation: sample.operation,
          producer: job.producer.name,
          iteration: job.iteration,
          status: result.status,
        }))
      }
      produced.push(acknowledged)
    }
  }

  if (diagnosticsTarget && !signal?.aborted) {
    const timeoutMs = Number(options.drainTimeoutMs ?? options.durationMs ?? 5_000)
    const pollIntervalMs = Number(options.pollIntervalMs ?? 100)
    const pollStarted = clock()
    let drained = false
    do {
      const observation = await pollDiagnostics('drain')
      if (observation) {
        diagnostics.push(observation)
        if (observation.metrics.backlog <= Number(options.maxBacklog ?? 0)) {
          drained = true
          break
        }
      }
      if (timeoutMs <= 0) break
      try {
        await waitForDelay(pollIntervalMs, { signal, sleep: options.sleep })
      } catch {
        break
      }
    } while (clock() - pollStarted < timeoutMs)

    if (!drained && diagnostics.length > 0) {
      errors.push(createError('backlog_not_drained', 'Worker backlog did not recover within the configured timeout', {
        backlog: diagnostics.at(-1).metrics.backlog,
        maxBacklog: Number(options.maxBacklog ?? 0),
        timeoutMs,
      }))
    }
  }

  const acknowledged = validateProduced(produced, correctnessMode, errors)
  if (ledgerTarget && diagnostics.length > 0 && !signal?.aborted) {
    const ledger = await readFinalLedger()
    if (ledger) {
      const finalObservation = diagnostics.at(-1)
      finalObservation.metrics = { ...finalObservation.metrics, ...ledger }
      finalObservation.reported = {
        ...finalObservation.reported,
        processedEventIds: true,
        sideEffects: true,
        retriedEventIds: true,
        deadLetterEventIds: true,
        pendingEventIds: true,
      }
    }
  }
  validateDiagnostics(diagnostics, acknowledged, correctnessMode, options, errors)
  addAbortError(errors, signal, { loader: 'worker' })

  const finalMetrics = diagnostics.at(-1)?.metrics ?? emptyMetrics()
  const producedSamples = samples.filter((sample) => sample.operation?.startsWith('produce.'))
  const acknowledgedEventIds = acknowledged.map((item) => item.eventId)
  const ratePhases = production.phases.map((phase) => {
    const acknowledgedInPhase = acknowledged.filter((item) => item.phase === phase.name).length
    return {
      ...phase,
      acknowledgedEvents: acknowledgedInPhase,
      achievedEventsPerSecond: phase.durationMs > 0
        ? acknowledgedInPhase * 1_000 / phase.durationMs
        : 0,
    }
  })
  const result = createScenarioResult('worker', startedAt, samples, errors, {
    aborted: signal?.aborted === true,
    correctnessMode,
    producedRequests: producedSamples.length,
    successfulProducerRequests: produced.length,
    targetEventsPerSecond: production.targetEventsPerSecond,
    achievedEventsPerSecond: production.durationMs > 0
      ? acknowledged.length * 1_000 / production.durationMs
      : 0,
    achievedRequestRate: production.durationMs > 0
      ? producedSamples.length * 1_000 / production.durationMs
      : 0,
    productionDurationMs: production.durationMs,
    ratePhases,
    acknowledgedEvents: acknowledged.length,
    acknowledgedEventIds,
    diagnosticsPolls: diagnostics.length,
    ...finalMetrics,
  }, clock)
  return finalizeScenarioResult(result, options.outputDir, options.outputFileName)

  async function pollDiagnostics(phase) {
    const result = await requestTarget(
      fetchImpl,
      options.apiBaseUrl ?? options.baseUrl,
      diagnosticsTarget,
      {
        ...templateContext,
        token,
        phase,
        produced,
        iteration: diagnostics.length,
        index: diagnostics.length,
        clock,
        signal,
      },
    )
    const sample = {
      phase: 'diagnostics',
      operation: `diagnostics.${phase}`,
      status: result.status,
      latencyMs: result.latencyMs,
      ok: result.ok,
      aborted: result.aborted === true,
    }
    samples.push(sample)
    if (!result.aborted) {
      addErrors(errors, result.errors, {
        code: 'diagnostics_semantic_failure',
        operation: `diagnostics.${phase}`,
        status: result.status,
      })
    }
    if (!result.ok) return null
    const metrics = extractDiagnostics(diagnosticsTarget, result.body)
    const metricErrors = validateDiagnosticShape(metrics)
    if (metricErrors.length) {
      sample.ok = false
      addErrors(errors, metricErrors, {
        code: 'diagnostics_semantic_failure',
        operation: `diagnostics.${phase}`,
        status: result.status,
      })
      return null
    }
    return {
      phase,
      atMs: clock() - startedAt,
      metrics,
      body: result.body,
      reported: {
        processedEventIds: hasConfiguredOrFallbackPath(
          result.body,
          diagnosticsTarget.metricPaths?.processedEventIds,
          ['processedEventIds'],
        ),
        sideEffects: hasConfiguredOrFallbackPath(
          result.body,
          diagnosticsTarget.metricPaths?.sideEffects,
          ['sideEffects'],
        ),
        retriedEventIds: hasConfiguredOrFallbackPath(
          result.body,
          diagnosticsTarget.metricPaths?.retriedEventIds,
          ['retriedEventIds'],
        ),
        deadLetterEventIds: hasConfiguredOrFallbackPath(
          result.body,
          diagnosticsTarget.metricPaths?.deadLetterEventIds,
          ['deadLetterEventIds'],
        ),
        pendingEventIds: hasConfiguredOrFallbackPath(
          result.body,
          diagnosticsTarget.metricPaths?.pendingEventIds,
          ['pendingEventIds'],
        ),
      },
    }
  }

  async function readFinalLedger() {
    const entries = []
    const seenCursors = new Set()
    const pageSize = positiveInteger(ledgerTarget.pageSize, 1_000)
    const maxPages = positiveInteger(ledgerTarget.maxPages ?? options.maxLedgerPages, 10_000)
    let cursor = null
    for (let page = 0; page < maxPages && !signal?.aborted; page += 1) {
      const result = await requestTarget(
        fetchImpl,
        options.apiBaseUrl ?? options.baseUrl,
        {
          ...ledgerTarget,
          url: ledgerPageUrl(ledgerTarget.url ?? ledgerTarget.path, cursor, pageSize, ledgerTarget),
        },
        {
          ...templateContext,
          token,
          phase: 'ledger',
          produced,
          cursor,
          page,
          pageSize,
          iteration: page,
          index: page,
          clock,
          signal,
        },
      )
      const sample = {
        phase: 'diagnostics',
        operation: 'diagnostics.ledger',
        page,
        status: result.status,
        latencyMs: result.latencyMs,
        ok: result.ok,
        aborted: result.aborted === true,
      }
      samples.push(sample)
      if (!result.aborted) {
        addErrors(errors, result.errors, {
          code: 'ledger_semantic_failure',
          operation: 'diagnostics.ledger',
          page,
          status: result.status,
        })
      }
      if (!result.ok) return null
      const pageEntries = valueFrom(result.body, ledgerTarget.entriesPath, ['entries'])
      if (!Array.isArray(pageEntries)) {
        sample.ok = false
        errors.push(createError('ledger_semantic_failure', 'Worker ledger response entries must be an array', { page }))
        return null
      }
      entries.push(...pageEntries)
      const nextCursor = valueFrom(result.body, ledgerTarget.nextCursorPath, ['nextCursor'])
      if (!nextCursor) return ledgerMetrics(entries)
      if (!isNonEmptyIdentifier(nextCursor) || seenCursors.has(String(nextCursor))) {
        sample.ok = false
        errors.push(createError('ledger_cursor_failure', 'Worker ledger cursor did not advance', { page }))
        return null
      }
      seenCursors.add(String(nextCursor))
      cursor = String(nextCursor)
    }
    if (signal?.aborted) return null
    errors.push(createError('ledger_page_limit', 'Worker ledger exceeded the configured page limit', {
      maxPages,
    }))
    return null
  }

  async function runProduction() {
    if (producers.length === 0 || signal?.aborted) {
      return { durationMs: 0, targetEventsPerSecond: null, phases: [] }
    }
    const ratePhases = normalizeRatePhases(options, producers.length)
    if (ratePhases.length === 0) {
      const jobs = buildProductionJobs(producers, options)
      const productionStarted = clock()
      await runWithConcurrency(jobs, options.concurrency, (job, index) => produce(job, index), { signal })
      return {
        durationMs: Math.max(0, clock() - productionStarted),
        targetEventsPerSecond: null,
        phases: [],
      }
    }

    const iterations = new Array(producers.length).fill(0)
    const phaseResults = []
    const productionStarted = clock()
    let globalIndex = 0
    for (const phase of ratePhases) {
      if (signal?.aborted) break
      const phaseResult = await runRateSchedule({
        durationMs: phase.durationMs,
        maxItems: phase.maxItems,
        ratePerSecond: phase.eventsPerSecond,
        concurrency: options.concurrency,
        signal,
        sleep: options.sleep,
        clock,
        handler: async () => {
          const producerIndex = globalIndex % producers.length
          const job = {
            producer: producers[producerIndex],
            producerIndex,
            iteration: iterations[producerIndex],
          }
          iterations[producerIndex] += 1
          const index = globalIndex
          globalIndex += 1
          await produce(job, index, phase.name)
        },
      })
      phaseResults.push({
        name: phase.name,
        targetEventsPerSecond: phase.eventsPerSecond,
        achievedEventsPerSecond: phaseResult.durationMs > 0
          ? phaseResult.scheduled * 1_000 / phaseResult.durationMs
          : 0,
        durationMs: phaseResult.durationMs,
        scheduled: phaseResult.scheduled,
        aborted: phaseResult.aborted,
      })
    }
    return {
      durationMs: Math.max(0, clock() - productionStarted),
      targetEventsPerSecond: phaseResults.length === 1
        ? phaseResults[0].targetEventsPerSecond
        : null,
      phases: phaseResults,
    }
  }
}

async function requestTarget(fetchImpl, baseUrl, target, context) {
  const path = resolveTemplate(target.url ?? target.path, context)
  if (!path || typeof path !== 'string') {
    return { ok: false, status: null, latencyMs: 0, body: undefined, errors: ['target URL or path is required'] }
  }
  const headers = { ...resolveTemplate(target.headers, context) }
  if (context.token && target.auth !== false && !headers.Authorization) {
    headers.Authorization = `Bearer ${context.token}`
  }
  let body = resolveTemplate(target.body, context)
  if (body !== undefined && typeof body !== 'string' && !(body instanceof Uint8Array)) {
    body = JSON.stringify(body)
    if (!headers['Content-Type']) headers['Content-Type'] = 'application/json'
  }
  return executeHttpRequest(
    fetchImpl,
    absoluteUrl(baseUrl, path),
    {
      method: target.method ?? (body === undefined ? 'GET' : 'POST'),
      headers,
      body,
      signal: context.signal,
    },
    target,
    context,
  )
}

function normalizeRatePhases(options, producerCount) {
  const sustainedRate = positiveNumber(options.eventsPerSecond ?? options.sustainedEventsPerSecond)
  const burstRate = positiveNumber(options.burstEventsPerSecond)
  const inferredMode = options.burst === true || (!sustainedRate && burstRate) ? 'burst' : 'sustained'
  const requestedMode = String(options.rateMode ?? options.mode ?? inferredMode)
    .toLowerCase()
  const phases = []

  if ((requestedMode === 'sustained' || requestedMode === 'both') && sustainedRate) {
    phases.push({
      name: 'sustained',
      eventsPerSecond: sustainedRate,
      durationMs: durationMilliseconds(options.durationMs, options.durationSeconds),
      maxItems: options.durationMs > 0 || options.durationSeconds > 0
        ? Infinity
        : totalConfiguredItems(options, producerCount),
    })
  }
  if ((requestedMode === 'burst' || requestedMode === 'both') && burstRate) {
    phases.push({
      name: 'burst',
      eventsPerSecond: burstRate,
      durationMs: durationMilliseconds(undefined, options.burstSeconds),
      maxItems: options.burstSeconds > 0 ? Infinity : totalConfiguredItems(options, producerCount),
    })
  }
  return phases.filter((phase) => phase.durationMs > 0 || phase.maxItems > 0)
}

function durationMilliseconds(milliseconds, seconds) {
  if (Number(milliseconds) > 0) return Number(milliseconds)
  if (Number(seconds) > 0) return Number(seconds) * 1_000
  return 0
}

function totalConfiguredItems(options, producerCount) {
  return positiveInteger(options.iterations, 1) * positiveInteger(producerCount, 1)
}

function positiveNumber(value) {
  const number = Number(value)
  return Number.isFinite(number) && number > 0 ? number : null
}

function normalizeProducers(value) {
  if (!value) return []
  const producers = Array.isArray(value) ? value : [value]
  return producers.map((producer, index) => ({
    ...producer,
    name: producer.name ?? `producer-${index + 1}`,
  }))
}

function buildProductionJobs(producers, options) {
  const jobs = []
  producers.forEach((producer, producerIndex) => {
    const count = positiveInteger(producer.count ?? options.iterations, 1)
    for (let iteration = 0; iteration < count; iteration += 1) {
      jobs.push({ producer, producerIndex, iteration })
    }
  })
  return jobs
}

function extractProduced(producer, body, context) {
  const custom = producer.extract
  if (custom) return { producer: producer.name, ...custom(body, context) }
  return {
    producer: producer.name,
    eventId: valueFrom(body, producer.eventIdPath, ['eventId', 'id']),
    sideEffectId: valueFrom(body, producer.sideEffectIdPath, ['sideEffectId', 'effectId', 'receiptId']),
    aggregateId: valueFrom(body, producer.aggregateIdPath, ['aggregateId']),
    sequence: valueFrom(body, producer.sequencePath, ['sequence']),
  }
}

function extractDiagnostics(target, body) {
  if (typeof target.extract === 'function') {
    return { ...emptyMetrics(), ...target.extract(body) }
  }
  const paths = target.metricPaths ?? {}
  return {
    backlog: finiteMetric(valueFrom(body, paths.backlog, ['backlog', 'pending'])),
    oldestAgeSeconds: finiteMetric(
      valueFrom(body, paths.oldestAgeSeconds, ['oldestAgeSeconds', 'oldestPendingAgeSeconds']),
    ),
    retries: finiteMetric(valueFrom(body, paths.retries, ['retries', 'retryCount'])),
    deadLetters: finiteMetric(valueFrom(body, paths.deadLetters, ['deadLetters', 'deadLetterCount'])),
    processing: finiteMetric(valueFrom(body, paths.processing, ['processing']), true),
    expiredLeases: finiteMetric(valueFrom(body, paths.expiredLeases, ['expiredLeases']), true),
    sideEffects: valueFrom(body, paths.sideEffects, ['sideEffects']) ?? [],
    processedEventIds: valueFrom(body, paths.processedEventIds, ['processedEventIds']) ?? [],
    retriedEventIds: valueFrom(body, paths.retriedEventIds, ['retriedEventIds']) ?? [],
    deadLetterEventIds: valueFrom(body, paths.deadLetterEventIds, ['deadLetterEventIds']) ?? [],
    pendingEventIds: valueFrom(body, paths.pendingEventIds, ['pendingEventIds']) ?? [],
  }
}

function ledgerPageUrl(value, cursor, pageSize, target) {
  const path = String(value ?? '')
  const separator = path.includes('?') ? '&' : '?'
  const cursorParam = encodeURIComponent(target.cursorParam ?? 'cursor')
  const limitParam = encodeURIComponent(target.limitParam ?? 'limit')
  const cursorQuery = cursor
    ? `${cursorParam}=${encodeURIComponent(cursor)}&`
    : ''
  return `${path}${separator}${cursorQuery}${limitParam}=${pageSize}`
}

function ledgerMetrics(entries) {
  const processedEventIds = []
  const sideEffects = []
  const retriedEventIds = []
  const deadLetterEventIds = []
  const pendingEventIds = []
  for (const entry of entries) {
    const eventId = entry?.eventId
    if (!isNonEmptyIdentifier(eventId)) continue
    const status = String(entry.deliveryStatus ?? entry.status ?? '').toLowerCase()
    const receiptRecorded = entry.receiptRecorded === true
    const sideEffectId = entry.sideEffectId ?? entry.effectId
    if (status === 'processed' && receiptRecorded && isNonEmptyIdentifier(sideEffectId)) {
      processedEventIds.push(eventId)
      sideEffects.push({ eventId, sideEffectId })
    }
    if (Number(entry.attemptCount) > 1 || Number(entry.replayCount) > 0) {
      retriedEventIds.push(eventId)
    }
    if (status === 'dead_letter' || status === 'abandoned') {
      deadLetterEventIds.push(eventId)
    }
    if (status === 'pending' || status === 'processing' || status === 'missing'
      || (status === 'processed' && (!receiptRecorded || !isNonEmptyIdentifier(sideEffectId)))) {
      pendingEventIds.push(eventId)
    }
  }
  return {
    processedEventIds,
    sideEffects,
    retriedEventIds,
    deadLetterEventIds,
    pendingEventIds,
  }
}

function validateProduced(produced, correctnessMode, errors) {
  const valid = produced.filter((item) => isNonEmptyIdentifier(item.eventId))
  const eventIds = valid.map((item) => item.eventId)
  const duplicateEvents = duplicates(eventIds)
  if (duplicateEvents.length) {
    errors.push(createError('duplicate_event', 'producer responses contain duplicate event identifiers', {
      duplicateEventIds: duplicateEvents,
    }))
  }

  const acknowledged = valid.filter((item) => !duplicateEvents.includes(item.eventId))
  if (correctnessMode.mode !== 'strict') return acknowledged

  const sideEffects = acknowledged.map((item) => item.sideEffectId).filter(isNonEmptyIdentifier)
  const duplicateEffects = duplicates(sideEffects)
  if (duplicateEffects.length) {
    errors.push(createError('duplicate_side_effect', 'producer responses contain duplicate side-effect identifiers', {
      duplicateSideEffectIds: duplicateEffects,
    }))
  }

  const byAggregate = new Map()
  for (const item of acknowledged) {
    if (!isNonEmptyIdentifier(item.sideEffectId)) {
      errors.push(createError('missing_side_effect_id', 'producer acknowledgement did not identify its side effect', {
        eventId: item.eventId,
      }))
    }
    if (!isNonEmptyIdentifier(item.aggregateId) || !Number.isSafeInteger(Number(item.sequence))) {
      errors.push(createError('missing_aggregate_sequence', 'producer acknowledgement did not identify aggregate sequence', {
        eventId: item.eventId,
        aggregateId: item.aggregateId,
        sequence: item.sequence,
      }))
      continue
    }
    if (!byAggregate.has(item.aggregateId)) byAggregate.set(item.aggregateId, [])
    byAggregate.get(item.aggregateId).push({
      eventId: item.eventId,
      requestIndex: item.requestIndex,
      sequence: Number(item.sequence),
    })
  }
  for (const [aggregateId, entries] of byAggregate) {
    entries.sort((left, right) => left.requestIndex - right.requestIndex)
    for (let index = 1; index < entries.length; index += 1) {
      if (entries[index].sequence !== entries[index - 1].sequence + 1) {
        errors.push(createError('aggregate_sequence_failure', 'aggregate sequence is not contiguous and increasing', {
          aggregateId,
          previousEventId: entries[index - 1].eventId,
          eventId: entries[index].eventId,
          previousSequence: entries[index - 1].sequence,
          sequence: entries[index].sequence,
        }))
        break
      }
    }
  }
  return acknowledged
}

function validateDiagnostics(observations, produced, correctnessMode, options, errors) {
  if (observations.length === 0) {
    if (produced.length > 0) {
      errors.push(createError('missing_diagnostics', 'Worker correctness requires a final diagnostics observation'))
    }
    return
  }
  const baseline = observations[0].metrics
  const final = observations.at(-1).metrics
  const maxOldest = Number(options.maxOldestAgeSeconds ?? Infinity)
  const maxRetryIncrease = Number(options.maxRetryIncrease ?? Infinity)
  const maxDeadLetterIncrease = Number(options.maxDeadLetterIncrease ?? 0)
  if (final.oldestAgeSeconds > maxOldest) {
    errors.push(createError('oldest_age_exceeded', 'oldest pending event age exceeded the configured limit', {
      actual: final.oldestAgeSeconds,
      limit: maxOldest,
    }))
  }
  if (final.retries - baseline.retries > maxRetryIncrease) {
    errors.push(createError('retry_increase_exceeded', 'Worker retry increase exceeded the configured limit', {
      actual: final.retries - baseline.retries,
      limit: maxRetryIncrease,
    }))
  }
  if (final.deadLetters - baseline.deadLetters > maxDeadLetterIncrease) {
    errors.push(createError('dead_letter_increase', 'Worker dead-letter count increased beyond the configured limit', {
      actual: final.deadLetters - baseline.deadLetters,
      limit: maxDeadLetterIncrease,
    }))
  }

  const sideEffectKeys = (final.sideEffects ?? []).map((effect) =>
    typeof effect === 'string'
      ? effect
      : effect.idempotencyKey ?? effect.sideEffectId ?? effect.effectId ?? effect.id ?? effect.eventId).filter(Boolean)
  const duplicateEffects = duplicates(sideEffectKeys)
  if (duplicateEffects.length) {
    errors.push(createError('duplicate_side_effect', 'diagnostics reported duplicate side effects', {
      duplicateSideEffectIds: duplicateEffects,
    }))
  }

  if (correctnessMode.mode !== 'strict') return

  const finalObservation = observations.at(-1)
  if (!finalObservation.reported.processedEventIds) {
    errors.push(createError(
      'processed_event_ledger_missing',
      'strict-v1 diagnostics must explicitly report processedEventIds',
    ))
  }
  if (!finalObservation.reported.sideEffects) {
    errors.push(createError(
      'side_effect_ledger_missing',
      'strict-v1 diagnostics must explicitly report sideEffects',
    ))
  }

  const processedEventIds = (final.processedEventIds ?? []).filter(isNonEmptyIdentifier)
  const duplicateProcessed = duplicates(processedEventIds)
  if (duplicateProcessed.length) {
    errors.push(createError('duplicate_processed_event', 'diagnostics reported duplicate processed event identifiers', {
      duplicateEventIds: duplicateProcessed,
    }))
  }
  const processed = new Set(processedEventIds)
  const expectedEventIds = produced.map((item) => item.eventId)
  const missing = expectedEventIds.filter((eventId) => !processed.has(eventId))
  if (missing.length) {
    errors.push(createError('missing_processed_event', 'diagnostics did not report all produced events', {
      missingEventIds: missing,
    }))
  }

  const normalizedEffects = normalizeSideEffects(final.sideEffects)
  for (const item of produced) {
    const matchingEffects = normalizedEffects.filter((effect) =>
      effect.eventId === item.eventId || effect.key === item.sideEffectId)
    if (matchingEffects.length === 0) {
      errors.push(createError('missing_side_effect', 'diagnostics did not report a side effect for the produced event', {
        eventId: item.eventId,
        sideEffectId: item.sideEffectId,
      }))
    } else if (matchingEffects.length > 1) {
      errors.push(createError('multiple_side_effects', 'diagnostics reported multiple side effects for one event', {
        eventId: item.eventId,
        sideEffectIds: matchingEffects.map((effect) => effect.key),
      }))
    }
  }

  validateEventFailureLedger(final, finalObservation.reported, expectedEventIds, baseline, errors)
}

function validateDiagnosticShape(metrics) {
  const errors = []
  for (const field of ['backlog', 'oldestAgeSeconds', 'retries', 'deadLetters', 'processing', 'expiredLeases']) {
    if (!Number.isFinite(metrics[field]) || metrics[field] < 0) {
      errors.push(`diagnostics field ${field} must be a non-negative number`)
    }
  }
  if (!Array.isArray(metrics.sideEffects)) errors.push('diagnostics field sideEffects must be an array')
  if (!Array.isArray(metrics.processedEventIds)) errors.push('diagnostics field processedEventIds must be an array')
  return errors
}

function valueFrom(body, configuredPath, fallbackPaths) {
  if (typeof configuredPath === 'function') return configuredPath(body)
  if (configuredPath) return getPath(body, configuredPath)
  for (const path of fallbackPaths) {
    const value = getPath(body, path)
    if (value !== undefined) return value
  }
  return undefined
}

function emptyMetrics() {
  return {
    backlog: 0,
    oldestAgeSeconds: 0,
    retries: 0,
    deadLetters: 0,
    processing: 0,
    expiredLeases: 0,
    sideEffects: [],
    processedEventIds: [],
    retriedEventIds: [],
    deadLetterEventIds: [],
    pendingEventIds: [],
  }
}

function normalizeCorrectnessMode(value, errors) {
  if (value === undefined || value === null) return { version: 1, mode: 'strict' }
  if (
    typeof value === 'object' &&
    value.version === 1 &&
    (value.mode === 'strict' || value.mode === 'summary')
  ) {
    return { version: 1, mode: value.mode }
  }
  errors.push(createError(
    'invalid_correctness_mode',
    'correctnessMode must be a versioned object: { version: 1, mode: "strict" | "summary" }',
  ))
  return { version: 1, mode: 'strict' }
}

function normalizeSideEffects(sideEffects) {
  return (sideEffects ?? []).map((effect) => {
    if (typeof effect === 'string') return { key: effect, eventId: null }
    return {
      key: effect.idempotencyKey ?? effect.sideEffectId ?? effect.effectId ?? effect.id ?? effect.eventId,
      eventId: effect.eventId ?? null,
    }
  }).filter((effect) => isNonEmptyIdentifier(effect.key))
}

function validateEventFailureLedger(final, reported, expectedEventIds, baseline, errors) {
  const expected = new Set(expectedEventIds)
  const failureLedgers = [
    ['retriedEventIds', 'event_retried', 'produced event appeared in the retry ledger'],
    ['deadLetterEventIds', 'event_dead_lettered', 'produced event appeared in the dead-letter ledger'],
    ['pendingEventIds', 'event_still_pending', 'produced event remained in the backlog ledger'],
  ]
  for (const [field, code, message] of failureLedgers) {
    const identifiers = Array.isArray(final[field]) ? final[field].filter(isNonEmptyIdentifier) : []
    const affected = identifiers.filter((eventId) => expected.has(eventId))
    if (affected.length) errors.push(createError(code, message, { eventIds: affected }))
  }

  if (final.retries > baseline.retries && !reported.retriedEventIds) {
    errors.push(createError('retry_ledger_missing', 'retry count increased without a retriedEventIds ledger'))
  }
  if (final.deadLetters > baseline.deadLetters && !reported.deadLetterEventIds) {
    errors.push(createError('dead_letter_ledger_missing', 'dead-letter count increased without a deadLetterEventIds ledger'))
  }
  if (final.backlog > 0 && !reported.pendingEventIds) {
    errors.push(createError('backlog_ledger_missing', 'backlog is non-zero without a pendingEventIds ledger'))
  }
}

function isNonEmptyIdentifier(value) {
  return (typeof value === 'string' || typeof value === 'number') && String(value).trim().length > 0
}

function hasConfiguredOrFallbackPath(body, configuredPath, fallbackPaths) {
  if (typeof configuredPath === 'function') return configuredPath(body) !== undefined
  if (configuredPath) return getPath(body, configuredPath) !== undefined
  return fallbackPaths.some((path) => getPath(body, path) !== undefined)
}

function duplicates(values) {
  const seen = new Set()
  const duplicate = new Set()
  for (const value of values) {
    if (seen.has(value)) duplicate.add(value)
    seen.add(value)
  }
  return [...duplicate]
}

function finiteMetric(value, optional = false) {
  if (value === undefined && optional) return 0
  const number = Number(value)
  return Number.isFinite(number) && number >= 0 ? number : null
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

function absoluteUrl(baseUrl, path) {
  if (/^https?:\/\//i.test(path)) return path
  const base = String(baseUrl ?? '').replace(/\/+$/, '')
  return `${base}${String(path).startsWith('/') ? '' : '/'}${path}`
}

function positiveInteger(value, fallback) {
  const number = Number(value)
  return Number.isSafeInteger(number) && number > 0 ? number : fallback
}
