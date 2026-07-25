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
  waitForDelay,
} from './common.mjs'

export async function runWebSocketScenario(options = {}) {
  const Socket = options.WebSocket ?? globalThis.WebSocket
  const socketFactory = options.socketFactory ??
    (Socket ? ({ url }) => new Socket(url) : null)
  if (!socketFactory) throw new TypeError('options.WebSocket or options.socketFactory is required')

  const clock = options.clock ?? defaultClock
  const signal = options.signal
  const startedAt = clock()
  const samples = []
  const errors = []
  const targets = options.targets ?? {}
  const users = normalizeUsers(options.users, options.token)
  const connectionCount = positiveInteger(options.connections ?? options.concurrency, users.length || 1)
  const states = []
  const liveSockets = new Set()
  const eventFanout = new Map()
  const deferredLedgerFrames = []
  const expectedEvents = new Map()
  const expectedSequences = new Map()
  const gaps = new Map()
  const convergenceChecks = new Set()
  const strictLedger = Boolean(targets.trigger)
  const triggerAggregateLanes = positiveInteger(
    options.triggerAggregateLanes ?? targets.trigger?.aggregateLanes,
    1,
  )
  const templateContext = createTemplateContext(options)
  const metrics = {
    attemptedConnections: connectionCount,
    openedConnections: 0,
    readyConnections: 0,
    closedConnections: 0,
    messages: 0,
    envelopes: 0,
    duplicates: 0,
    stale: 0,
    gaps: 0,
    reconnects: 0,
    triggerRequests: 0,
    triggerRatePerSecond: 0,
    achievedTriggerRatePerSecond: 0,
    triggerAggregateLanes,
    expectedEvents: 0,
    expectedSequences: 0,
    missingEvents: 0,
    recoveredGaps: 0,
    calibrationRequests: 0,
    calibrationAttempts: 0,
    calibrationFailures: 0,
    reconnectCalibrationRequests: 0,
    reconnectConvergenceFailures: 0,
    fanoutEvents: 0,
    fanoutMisses: 0,
  }

  const connectionInputs = Array.from({ length: connectionCount }, (_, index) => ({
    index,
    user: users[index % Math.max(1, users.length)] ?? { token: options.token },
  }))
  const abortSockets = () => {
    for (const socket of liveSockets) closeSocket(socket)
    liveSockets.clear()
  }
  signal?.addEventListener('abort', abortSockets, { once: true })

  try {
    const connected = await runWithConcurrency(
      connectionInputs,
      options.connectConcurrency ?? options.concurrency,
      (input) => openConnection(input, false),
      { signal },
    )
    states.push(...connected.filter(Boolean))

    const connectionsReady = await waitForReadyConnections()
    const triggerRun = connectionsReady
      ? await runTriggers()
      : { scheduled: 0, durationMs: 0, targetRatePerSecond: 0, rateScheduled: false }
    metrics.triggerRequests = triggerRun.scheduled
    metrics.triggerRatePerSecond = triggerRun.targetRatePerSecond
    metrics.achievedTriggerRatePerSecond = triggerRun.durationMs > 0
      ? triggerRun.scheduled / (triggerRun.durationMs / 1_000)
      : triggerRun.scheduled

    if (strictLedger) {
      await settleExpectedLedger(
        triggerRun.rateScheduled ? options.settleMs : options.durationMs ?? options.settleMs,
      )
    } else {
      await settle(triggerRun.rateScheduled ? options.settleMs : options.durationMs ?? options.settleMs)
    }
    if (strictLedger) {
      replayExpectedLedgerFrames()
      recordMissingExpectedEvents()
    }
    if (!signal?.aborted) await reconcileGaps('gap')

    const reconnectCount = signal?.aborted ? 0 : Math.min(
      states.length,
      Math.max(0, Number(targets.reconnects ?? options.reconnects ?? 1) || 0),
    )
    for (let index = 0; index < reconnectCount && !signal?.aborted; index += 1) {
      const previous = states[index]
      closeSocket(previous.socket)
      const replacement = await openConnection({ index: previous.index, user: previous.user }, true)
      if (replacement) {
        states[index] = replacement
        metrics.reconnects += 1
      }
    }

    if (reconnectCount > 0 && !signal?.aborted) {
      await settle(options.reconnectSettleMs ?? options.settleMs)
      await reconcileExpectedEventsAfterReconnect()
    }
    if (!signal?.aborted) {
      validateReadyConnections()
      validateFanout()
    }
  } finally {
    signal?.removeEventListener('abort', abortSockets)
    abortSockets()
    liveSockets.clear()
  }

  addAbortError(errors, signal, { loader: 'websocket' })
  const result = createScenarioResult('websocket', startedAt, samples, errors, {
    aborted: signal?.aborted === true,
    ...metrics,
    fanoutEvents: eventFanout.size,
    activeConnections: states.length,
  }, clock)
  return finalizeScenarioResult(result, options.outputDir, options.outputFileName)

  async function openConnection(input, reconnect) {
    const state = {
      ...input,
      socket: null,
      openedAt: clock(),
      ready: false,
      eventIds: new Set(),
      watermarks: new Map(),
    }
    try {
      const url = buildSocketUrl(options, input.user, input.index, reconnect)
      const created = socketFactory({
        url,
        user: input.user,
        index: input.index,
        reconnect,
        signal,
      })
      state.socket = created && typeof created.then === 'function' ? await created : created
      liveSockets.add(state.socket)
    } catch (error) {
      recordConnectionFailure(error, input, reconnect)
      return null
    }

    const opened = waitForOpen(state.socket, options.connectTimeoutMs ?? 5_000, clock, signal)
    onSocket(state.socket, 'message', (event) => handleMessage(state, event))
    onSocket(state.socket, 'close', () => {
      metrics.closedConnections += 1
      liveSockets.delete(state.socket)
    })
    onSocket(state.socket, 'error', (error) => {
      if (signal?.aborted) return
      errors.push(createError('websocket_error', socketErrorMessage(error), {
        connectionIndex: input.index,
      }))
    })

    const outcome = await opened
    const operation = reconnect ? 'connection.reconnect' : 'connection.open'
    samples.push({
      phase: reconnect ? 'reconnect' : 'connect',
      operation,
      connectionIndex: input.index,
      latencyMs: outcome.latencyMs,
      ok: outcome.ok,
    })
    if (!outcome.ok) {
      if (!outcome.aborted && !signal?.aborted) {
        errors.push(createError('connection_failure', outcome.error, {
          operation,
          connectionIndex: input.index,
        }))
      }
      closeSocket(state.socket)
      return null
    }
    metrics.openedConnections += 1
    return state
  }

  function handleMessage(state, event) {
    const receivedAt = clock()
    metrics.messages += 1
    let frame
    try {
      frame = JSON.parse(toText(event?.data ?? event))
    } catch {
      errors.push(createError('invalid_frame', 'WebSocket message is not valid JSON', {
        connectionIndex: state.index,
      }))
      return
    }

    if (frame?.type === 'connection.ready') {
      if (typeof frame.instanceId !== 'string' || !frame.instanceId) {
        errors.push(createError('invalid_ready', 'connection.ready requires instanceId', {
          connectionIndex: state.index,
        }))
        return
      }
      if (!state.ready) metrics.readyConnections += 1
      state.ready = true
      samples.push({
        phase: 'connect',
        operation: 'connection.ready',
        connectionIndex: state.index,
        instanceId: frame.instanceId,
        latencyMs: Math.max(0, receivedAt - state.openedAt),
        ok: true,
      })
      return
    }

    const semanticErrors = validateEnvelope(frame)
    if (semanticErrors.length) {
      addErrors(errors, semanticErrors, {
        code: 'invalid_envelope',
        connectionIndex: state.index,
      })
      samples.push({
        phase: 'fanout',
        operation: 'envelope.receive',
        connectionIndex: state.index,
        latencyMs: 0,
        ok: false,
      })
      return
    }

    metrics.envelopes += 1
    if (strictLedger) {
      deferredLedgerFrames.push({ state, frame, receivedAt })
      return
    }
    recordTrackedFrame(state, frame, receivedAt)
  }

  function recordTrackedFrame(state, frame, receivedAt) {
    const fanout = eventFanout.get(frame.eventId) ?? new Map()
    const deliveries = (fanout.get(state.index) ?? 0) + 1
    fanout.set(state.index, deliveries)
    eventFanout.set(frame.eventId, fanout)

    const watermarkKey = `${frame.workspaceId}\u0000${frame.sequenceScope}\u0000${frame.sequenceKey}`
    const previous = state.watermarks.get(watermarkKey)
    let sequenceOutcome = 'accepted'
    if (state.eventIds.has(frame.eventId) || previous === frame.sequence) {
      sequenceOutcome = 'duplicate'
      metrics.duplicates += 1
      errors.push(createError('duplicate_event', 'event was delivered more than once to the same connection', {
        connectionIndex: state.index,
        eventId: frame.eventId,
        sequence: frame.sequence,
      }))
    } else if (previous !== undefined && frame.sequence < previous) {
      sequenceOutcome = 'stale'
      metrics.stale += 1
    } else if (previous !== undefined && frame.sequence > previous + 1) {
      sequenceOutcome = 'gap'
      for (let sequence = previous + 1; sequence < frame.sequence; sequence += 1) {
        recordGap(state, frame, watermarkKey, sequence)
      }
    }
    state.eventIds.add(frame.eventId)
    if (previous === undefined || frame.sequence > previous) state.watermarks.set(watermarkKey, frame.sequence)

    const serverTime = Date.parse(frame.serverTime ?? frame.occurredAt)
    const messageLatency = Number.isFinite(serverTime) ? Math.max(0, Date.now() - serverTime) : 0
    samples.push({
      phase: 'fanout',
      operation: `sequence.${sequenceOutcome}`,
      connectionIndex: state.index,
      eventId: frame.eventId,
      sequence: frame.sequence,
      latencyMs: messageLatency,
      ok: sequenceOutcome !== 'stale' && sequenceOutcome !== 'duplicate',
    })
    if (sequenceOutcome === 'stale') {
      errors.push(createError('stale_sequence', 'received sequence lower than the connection watermark', {
        connectionIndex: state.index,
        eventId: frame.eventId,
        sequence: frame.sequence,
        previousSequence: previous,
      }))
    }
  }

  function replayExpectedLedgerFrames() {
    for (const observation of deferredLedgerFrames) {
      if (!expectedEvents.has(observation.frame.eventId)) continue
      recordTrackedFrame(observation.state, observation.frame, observation.receivedAt)
    }
  }

  function recordMissingExpectedEvents() {
    for (const expected of expectedEvents.values()) {
      const deliveries = eventFanout.get(expected.eventId)
      for (const state of states) {
        if (deliveries?.has(state.index)) continue
        const key = `${state.index}\u0000${expected.streamKey}\u0000${expected.sequence}`
        if (gaps.has(key)) continue
        gaps.set(key, {
          key,
          state,
          frame: expected,
          streamKey: expected.streamKey,
          missingSequence: expected.sequence,
          expected,
        })
        metrics.gaps += 1
      }
    }
  }

  function recordGap(state, frame, streamKey, missingSequence) {
    const key = `${state.index}\u0000${streamKey}\u0000${missingSequence}`
    if (gaps.has(key)) return
    gaps.set(key, {
      key,
      state,
      frame,
      streamKey,
      missingSequence,
      expected: expectedSequences.get(`${streamKey}\u0000${missingSequence}`),
    })
    metrics.gaps += 1
  }

  async function runTriggers() {
    if (!targets.trigger || signal?.aborted) {
      return {
        scheduled: 0,
        durationMs: 0,
        targetRatePerSecond: 0,
        rateScheduled: false,
      }
    }
    const fetchImpl = options.fetch ?? globalThis.fetch
    if (typeof fetchImpl !== 'function') {
      errors.push(createError('configuration', 'targets.trigger requires injectable fetch or global fetch'))
      return {
        scheduled: 0,
        durationMs: 0,
        targetRatePerSecond: 0,
        rateScheduled: false,
      }
    }

    const configuredRate = Number(
      options.triggerRatePerSecond ??
      options.messagesPerSecond ??
      targets.trigger.triggerRatePerSecond ??
      targets.trigger.messagesPerSecond,
    )
    const iterationsConfigured = options.iterations ?? targets.trigger.iterations
    const iterations = positiveInteger(iterationsConfigured, 1)
    const durationMs = Math.max(0, Number(options.durationMs) || 0)
    if (configuredRate > 0) {
      const started = clock()
      const outcome = await runRateSchedule({
        clock,
        sleep: options.sleep,
        signal,
        ratePerSecond: configuredRate,
        durationMs: durationMs > 0 ? durationMs : Math.max(1, Math.ceil(iterations / configuredRate * 1_000)),
        maxItems: iterationsConfigured === undefined && durationMs > 0 ? Infinity : iterations,
        concurrency: options.triggerConcurrency ?? options.concurrency ?? 1,
        handler: (iteration) => triggerOnce(fetchImpl, iteration),
      })
      return {
        ...outcome,
        durationMs: Math.max(outcome.durationMs, clock() - started),
        rateScheduled: true,
      }
    }

    const started = clock()
    for (let iteration = 0; iteration < iterations && !signal?.aborted; iteration += 1) {
      await triggerOnce(fetchImpl, iteration)
    }
    return {
      scheduled: signal?.aborted ? expectedEvents.size : iterations,
      durationMs: Math.max(0, clock() - started),
      targetRatePerSecond: 0,
      rateScheduled: false,
    }
  }

  async function waitForReadyConnections() {
    if (options.requireReady === false) return true
    const timeoutMs = Math.max(1, Number(options.readyTimeoutMs ?? options.connectTimeoutMs ?? 5_000) || 5_000)
    const started = clock()
    while (!signal?.aborted && states.some((state) => !state.ready) && clock() - started < timeoutMs) {
      try {
        await waitForDelay(Math.min(10, timeoutMs), {
          signal,
          sleep: options.sleep,
        })
      } catch {
        break
      }
    }
    const notReady = states.filter((state) => !state.ready)
    if (states.length !== connectionCount || notReady.length > 0) {
      errors.push(createError('connections_not_ready', 'all WebSocket connections must be ready before trigger production', {
        expectedConnections: connectionCount,
        openedConnections: states.length,
        notReadyConnections: notReady.map((state) => state.index),
      }))
      return false
    }
    return true
  }

  async function triggerOnce(fetchImpl, iteration) {
    const context = {
      ...templateContext,
      users,
      states,
      iteration,
      index: iteration,
      triggerIndex: iteration,
      triggerLane: (iteration % triggerAggregateLanes) + 1,
      requestId: options.requestId ?? templateContext.requestId,
      token: options.token,
      clock,
      signal,
      options,
    }
    const trigger = await requestTarget(
      fetchImpl,
      options.apiBaseUrl ?? options.baseUrl,
      targets.trigger,
      context,
    )
    samples.push(sampleFromRequest('fanout.trigger', targets.trigger.name ?? 'trigger', trigger))
    if (trigger.aborted) return
    if (!trigger.ok) {
      addErrors(errors, trigger.errors, {
        code: 'fanout_trigger_failure',
        operation: targets.trigger.name ?? 'trigger',
        triggerIndex: iteration,
        status: trigger.status,
      })
      return
    }

    const descriptors = extractExpectedEvents(trigger.body, targets.trigger, {
      ...context,
      response: trigger,
    })
    if (descriptors.length === 0) {
      errors.push(createError(
        'expected_event_missing',
        'successful trigger did not return an expected event descriptor',
        { triggerIndex: iteration },
      ))
      return
    }
    for (const descriptor of descriptors) registerExpectedEvent(descriptor, iteration)
  }

  function registerExpectedEvent(descriptor, triggerIndex) {
    const validationErrors = validateExpectedEvent(descriptor)
    if (validationErrors.length > 0) {
      addErrors(errors, validationErrors, {
        code: 'invalid_expected_event',
        triggerIndex,
      })
      return
    }
    if (expectedEvents.has(descriptor.eventId)) {
      errors.push(createError('duplicate_expected_event', 'trigger returned a duplicate expected eventId', {
        triggerIndex,
        eventId: descriptor.eventId,
      }))
      return
    }
    const streamKey = streamKeyFor(descriptor)
    const expected = {
      ...descriptor,
      streamKey,
      triggerIndex,
      expectedFanout: resolveExpectedFanout(descriptor),
    }
    expectedEvents.set(expected.eventId, expected)
    expectedSequences.set(`${streamKey}\u0000${expected.sequence}`, expected)
    metrics.expectedEvents = expectedEvents.size
    metrics.expectedSequences = expectedSequences.size
  }

  async function reconcileGaps(reason) {
    for (const gap of gaps.values()) {
      if (signal?.aborted || convergenceChecks.has(`${reason}\u0000${gap.key}`)) continue
      convergenceChecks.add(`${reason}\u0000${gap.key}`)
      gap.expected ??= expectedSequences.get(`${gap.streamKey}\u0000${gap.missingSequence}`)
      if (strictLedger && !gap.expected) {
        metrics.calibrationFailures += 1
        errors.push(createError('gap_unmapped', 'sequence gap has no expected business event in the trigger ledger', {
          connectionIndex: gap.state.index,
          missingSequence: gap.missingSequence,
          streamKey: gap.streamKey,
        }))
        continue
      }
      const reconciled = await calibrate(gap.state, gap.expected, {
        reason,
        gap,
        frame: gap.frame,
      })
      gap.reconciled = reconciled
      if (reconciled) metrics.recoveredGaps += 1
    }
  }

  async function reconcileExpectedEventsAfterReconnect() {
    if (!strictLedger) return
    for (const expected of expectedEvents.values()) {
      if (signal?.aborted) break
      metrics.reconnectCalibrationRequests += 1
      const ok = await calibrate(states[0], expected, {
        reason: 'reconnect',
        expected,
      })
      if (!ok) metrics.reconnectConvergenceFailures += 1
    }
  }

  async function calibrate(state, expected, details) {
    metrics.calibrationRequests += 1
    const fetchImpl = options.fetch ?? globalThis.fetch
    if (typeof fetchImpl !== 'function') {
      metrics.calibrationFailures += 1
      errors.push(createError('calibration_unavailable', 'sequence gap requires fetch for REST calibration', {
        connectionIndex: state?.index,
        eventId: expected?.eventId ?? details.frame?.eventId,
        reason: details.reason,
      }))
      return false
    }
    const target = targets.calibration ?? {}
    const calibrationContext = {
      ...templateContext,
      state,
      expectedEvent: expected,
      iteration: expected?.triggerIndex,
      index: state?.index,
      requestId: expected?.requestId ?? templateContext.requestId,
      ...details,
      options,
    }
    const calibrationPath = resolveTemplate(
      target.path ??
      target.url ??
      expected?.calibrationPath ??
      details.frame?.calibrationPath,
      calibrationContext,
    )
    if (!calibrationPath) {
      metrics.calibrationFailures += 1
      errors.push(createError('calibration_unavailable', 'calibration path is unavailable', {
        connectionIndex: state?.index,
        eventId: expected?.eventId,
        reason: details.reason,
      }))
      return false
    }
    const convergenceTimeoutMs = Math.max(
      0,
      Number(target.convergenceTimeoutMs ?? options.calibrationTimeoutMs) || 0,
    )
    const pollIntervalMs = Math.max(
      1,
      Number(target.convergencePollIntervalMs ?? options.calibrationPollIntervalMs) || 250,
    )
    const calibrationStartedAt = clock()
    let result
    let proof
    while (!signal?.aborted) {
      metrics.calibrationAttempts += 1
      result = await requestTarget(fetchImpl, options.apiBaseUrl ?? options.baseUrl, {
        name: target.name ?? 'calibration',
        method: target.method ?? 'GET',
        ...target,
        path: calibrationPath,
      }, {
        ...calibrationContext,
        token: state?.user?.token ?? options.token,
        clock,
        signal,
      }, { resolvedPath: calibrationPath })
      proof = result.ok
        ? proveConvergence(result.body, expected, target, { state, ...details, response: result })
        : { ok: false, errors: result.errors }
      if (result.aborted || !result.ok || proof.ok) break

      const elapsedMs = Math.max(0, clock() - calibrationStartedAt)
      if (elapsedMs >= convergenceTimeoutMs) break
      try {
        await waitForDelay(Math.min(pollIntervalMs, convergenceTimeoutMs - elapsedMs), {
          signal,
          sleep: options.sleep,
        })
      } catch {
        break
      }
    }

    const converged = result?.ok === true && proof?.ok === true
    samples.push({
      ...sampleFromRequest('calibration', target.name ?? 'calibration', result ?? {
        ok: false,
        aborted: signal?.aborted === true,
        latencyMs: 0,
        status: null,
      }, state?.index),
      latencyMs: Math.max(0, clock() - calibrationStartedAt),
      operation: details.reason === 'reconnect' ? 'calibration.reconnect' : target.name ?? 'calibration',
      ok: converged,
    })
    if (!result?.aborted && result?.ok === false) {
      metrics.calibrationFailures += 1
      addErrors(errors, result.errors, {
        code: 'calibration_failure',
        operation: target.name ?? 'calibration',
        connectionIndex: state?.index,
        eventId: expected?.eventId ?? details.frame?.eventId,
        reason: details.reason,
        status: result.status,
      })
      return false
    }
    if (!signal?.aborted && !converged) {
      metrics.calibrationFailures += 1
      addErrors(errors, proof?.errors ?? ['calibration did not converge before timeout'], {
        code: 'calibration_not_converged',
        operation: target.name ?? 'calibration',
        connectionIndex: state?.index,
        eventId: expected?.eventId ?? details.frame?.eventId,
        businessObjectId: expected?.businessObjectId,
        missingSequence: details.gap?.missingSequence,
        reason: details.reason,
        status: result?.status,
      })
      return false
    }
    return !signal?.aborted
  }

  function validateFanout() {
    const relevantEventIds = strictLedger ? new Set(expectedEvents.keys()) : new Set(eventFanout.keys())
    const observedRelevantEvents = [...relevantEventIds].filter((eventId) => eventFanout.has(eventId)).length
    const minimum = Number(targets.minEvents ?? (targets.trigger ? 1 : 0))
    if (observedRelevantEvents < minimum) {
      metrics.fanoutMisses += minimum - observedRelevantEvents
      errors.push(createError('fanout_missing', `received ${observedRelevantEvents} tracked fanout events, expected at least ${minimum}`, {
        actualEvents: observedRelevantEvents,
        minimumEvents: minimum,
      }))
    }
    for (const [eventId, deliveries] of eventFanout) {
      if (strictLedger && !expectedEvents.has(eventId)) continue
      const recipients = new Set(deliveries.keys())
      const expected = typeof targets.expectedFanout === 'function'
        ? targets.expectedFanout(eventId, recipients)
        : targets.expectedFanout ?? options.expectedFanout
      if (expected !== undefined && recipients.size !== Number(expected)) {
        metrics.fanoutMisses += 1
        errors.push(createError('fanout_mismatch', `event fanout was ${recipients.size}, expected ${expected}`, {
          eventId,
          actualFanout: recipients.size,
          expectedFanout: Number(expected),
        }))
      }
    }
    for (const expected of expectedEvents.values()) {
      const deliveries = eventFanout.get(expected.eventId)
      const actualFanout = deliveries?.size ?? 0
      const recoveredRecipients = new Set(
        [...gaps.values()]
          .filter((gap) => gap.reconciled && gap.expected?.eventId === expected.eventId)
          .map((gap) => gap.state.index),
      )
      for (const recipient of deliveries?.keys() ?? []) recoveredRecipients.delete(recipient)
      const convergedFanout = actualFanout + recoveredRecipients.size
      if (convergedFanout !== expected.expectedFanout) {
        metrics.fanoutMisses += 1
        if (actualFanout === 0) metrics.missingEvents += 1
        errors.push(createError('expected_event_fanout_mismatch', 'expected event did not reach or calibrate on the required connections', {
          eventId: expected.eventId,
          businessObjectId: expected.businessObjectId,
          sequence: expected.sequence,
          actualFanout,
          recoveredFanout: recoveredRecipients.size,
          expectedFanout: expected.expectedFanout,
        }))
      }
      if (deliveries && [...deliveries.values()].some((count) => count !== 1)) {
        errors.push(createError('expected_event_duplicate', 'expected event has duplicate per-connection deliveries', {
          eventId: expected.eventId,
        }))
      }
      for (const state of states) {
        const frame = deferredLedgerFrames.find((entry) =>
          entry.state.index === state.index && entry.frame.eventId === expected.eventId)?.frame
        if (!frame) continue
        const mismatches = expectedEnvelopeMismatches(frame, expected)
        if (mismatches.length > 0) {
          errors.push(createError('expected_event_envelope_mismatch', mismatches.join('; '), {
            eventId: expected.eventId,
            connectionIndex: state.index,
          }))
        }
      }
    }
  }

  function validateReadyConnections() {
    if (options.requireReady === false) return
    for (const state of states) {
      if (!state.ready) {
        errors.push(createError('ready_frame_missing', 'opened WebSocket did not receive connection.ready', {
          connectionIndex: state.index,
        }))
      }
    }
  }

  function recordConnectionFailure(error, input, reconnect) {
    samples.push({
      phase: reconnect ? 'reconnect' : 'connect',
      operation: reconnect ? 'connection.reconnect' : 'connection.open',
      connectionIndex: input.index,
      latencyMs: 0,
      ok: false,
    })
    errors.push(createError('connection_failure', socketErrorMessage(error), {
      connectionIndex: input.index,
    }))
  }

  function resolveExpectedFanout(descriptor) {
    const configured = typeof targets.expectedFanout === 'function'
      ? targets.expectedFanout(descriptor.eventId, descriptor)
      : descriptor.expectedFanout ?? targets.expectedFanout ?? options.expectedFanout
    return positiveInteger(configured, states.length || connectionCount)
  }

  async function settle(milliseconds) {
    try {
      await waitForDelay(Math.max(0, Number(milliseconds) || 0), {
        signal,
        sleep: options.sleep,
      })
    } catch {
      // Abort is represented once in the scenario result below.
    }
  }

  async function settleExpectedLedger(milliseconds) {
    const timeoutMs = Math.max(0, Number(milliseconds) || 0)
    const started = clock()
    while (!signal?.aborted && clock() - started < timeoutMs && !expectedLedgerComplete()) {
      try {
        await waitForDelay(Math.min(20, timeoutMs - (clock() - started)), {
          signal,
          sleep: options.sleep,
        })
      } catch {
        break
      }
    }
  }

  function expectedLedgerComplete() {
    return expectedEvents.size > 0 && [...expectedEvents.values()].every((expected) =>
      (eventFanout.get(expected.eventId)?.size ?? 0) >= expected.expectedFanout)
  }
}

function extractExpectedEvents(body, target, context) {
  const extractor = target.extractExpectedEvents ?? target.expectedEvents ?? target.expectedEvent
  let value = typeof extractor === 'function' ? extractor(body, context) : extractor
  if (value === undefined) {
    value = body?.expectedEvents ??
      body?.events ??
      body?.expectedEvent ??
      body?.event ??
      body?.data ??
      body
  }
  const values = Array.isArray(value) ? value : [value]
  return values
    .filter((entry) => entry && typeof entry === 'object')
    .map((entry) => {
      const realtime = entry.realtime ?? entry.envelope ?? entry
      return {
        eventId: realtime.eventId ?? entry.eventId,
        sourceEventId: realtime === entry ? undefined : entry.eventId,
        workspaceId: realtime.workspaceId ?? entry.workspaceId,
        sequenceScope: realtime.sequenceScope ?? entry.sequenceScope,
        sequenceKey: realtime.sequenceKey ?? entry.sequenceKey,
        sequence: realtime.sequence ?? entry.sequence,
        businessObjectId: entry.businessObjectId ??
          entry.objectId ??
          entry.resourceId ??
          entry.aggregateId ??
          realtime.businessObjectId ??
          realtime.objectId,
        calibrationPath: entry.calibrationPath ?? realtime.calibrationPath,
        expectedFanout: entry.expectedFanout ?? realtime.expectedFanout,
      }
    })
}

function validateExpectedEvent(event) {
  const errors = []
  if (typeof event?.eventId !== 'string' || !event.eventId) errors.push('expected event requires eventId')
  if (typeof event?.workspaceId !== 'string' || !event.workspaceId) errors.push('expected event requires workspaceId')
  if (event?.sequenceScope !== 'object' && event?.sequenceScope !== 'audience') {
    errors.push('expected event sequenceScope is invalid')
  }
  if (typeof event?.sequenceKey !== 'string' || !event.sequenceKey) {
    errors.push('expected event requires sequenceKey')
  }
  if (!Number.isSafeInteger(event?.sequence) || event.sequence < 0) {
    errors.push('expected event sequence is invalid')
  }
  if (typeof event?.businessObjectId !== 'string' || !event.businessObjectId) {
    errors.push('expected event requires businessObjectId')
  }
  if (typeof event?.calibrationPath !== 'string' || !event.calibrationPath.startsWith('/api/')) {
    errors.push('expected event requires an API calibrationPath')
  }
  return errors
}

function expectedEnvelopeMismatches(frame, expected) {
  const fields = [
    ['workspaceId', frame.workspaceId, expected.workspaceId],
    ['sequenceScope', frame.sequenceScope, expected.sequenceScope],
    ['sequenceKey', frame.sequenceKey, expected.sequenceKey],
    ['sequence', frame.sequence, expected.sequence],
  ]
  return fields
    .filter(([, actual, configured]) => actual !== configured)
    .map(([name, actual, configured]) =>
      `${name} was ${String(actual)}, expected ${String(configured)}`)
}

function streamKeyFor(event) {
  return `${event.workspaceId}\u0000${event.sequenceScope}\u0000${event.sequenceKey}`
}

function proveConvergence(body, expected, target, context) {
  if (!expected) return { ok: true, errors: [] }
  const customProof = target.proveConvergence ?? target.validateConvergence
  if (typeof customProof === 'function') {
    return normalizeProof(customProof(body, expected, context))
  }

  const candidates = collectObjects(body)
  const matchingObjects = candidates.filter((candidate) => {
    const id = candidate.id ??
      candidate.objectId ??
      candidate.businessObjectId ??
      candidate.resourceId ??
      candidate.aggregateId
    return String(id ?? '') === expected.businessObjectId
  })
  const object = target.requireCapacityReceipt === true
    ? matchingObjects.find((candidate) =>
      String(candidate.sideEffectId ?? '') === expected.eventId &&
      (!expected.sourceEventId || String(candidate.eventId ?? '') === expected.sourceEventId))
    : matchingObjects[0]
  if (!object) {
    const message = target.requireCapacityReceipt === true && matchingObjects.length > 0
      ? `REST calibration did not contain exact side effect ${expected.eventId} for source event ${expected.sourceEventId ?? 'any'}`
      : `REST calibration did not contain business object ${expected.businessObjectId}`
    return {
      ok: false,
      errors: [message],
    }
  }
  if (target.requireCapacityReceipt === true) {
    const strictErrors = []
    if (String(object.deliveryStatus ?? '').toLowerCase() !== 'processed') {
      strictErrors.push(`delivery status was ${String(object.deliveryStatus ?? 'missing')}, expected processed`)
    }
    if (object.receiptRecorded !== true) {
      strictErrors.push('delivery receipt was not recorded')
    }
    if (String(object.sideEffectId ?? '') !== expected.eventId) {
      strictErrors.push(`side effect was ${String(object.sideEffectId ?? 'missing')}, expected ${expected.eventId}`)
    }
    if (String(object.aggregateId ?? '') !== expected.businessObjectId) {
      strictErrors.push(`aggregate was ${String(object.aggregateId ?? 'missing')}, expected ${expected.businessObjectId}`)
    }
    if (expected.sourceEventId && String(object.eventId ?? '') !== expected.sourceEventId) {
      strictErrors.push(`source event was ${String(object.eventId ?? 'missing')}, expected ${expected.sourceEventId}`)
    }
    const strictSequence = Number(object.sequence)
    if (!Number.isSafeInteger(strictSequence) || strictSequence !== expected.sequence) {
      strictErrors.push(
        `sequence was ${Number.isFinite(strictSequence) ? strictSequence : 'missing'}, expected ${expected.sequence}`,
      )
    }
    return { ok: strictErrors.length === 0, errors: strictErrors }
  }
  const sequence = Number(
    object.sequence ??
    object.currentSequence ??
    object.eventSequence ??
    object.version,
  )
  if (!Number.isSafeInteger(sequence) || sequence < expected.sequence) {
    return {
      ok: false,
      errors: [
        `REST calibration object ${expected.businessObjectId} has sequence ${Number.isFinite(sequence) ? sequence : 'missing'}, expected at least ${expected.sequence}`,
      ],
    }
  }
  return { ok: true, errors: [] }
}

function normalizeProof(value) {
  if (value === true || value === undefined) return { ok: true, errors: [] }
  if (value === false) return { ok: false, errors: ['custom convergence proof failed'] }
  if (typeof value === 'string') return { ok: false, errors: [value] }
  if (Array.isArray(value)) {
    const errors = value.filter(Boolean).map(String)
    return { ok: errors.length === 0, errors }
  }
  if (value && typeof value === 'object') {
    if (value.ok === true) return { ok: true, errors: [] }
    const errors = Array.isArray(value.errors)
      ? value.errors.filter(Boolean).map(String)
      : [String(value.message ?? 'custom convergence proof failed')]
    return { ok: false, errors }
  }
  return { ok: false, errors: ['custom convergence proof returned an invalid result'] }
}

function collectObjects(value, output = [], seen = new Set()) {
  if (!value || typeof value !== 'object' || seen.has(value)) return output
  seen.add(value)
  if (!Array.isArray(value)) output.push(value)
  for (const child of Array.isArray(value) ? value : Object.values(value)) {
    collectObjects(child, output, seen)
  }
  return output
}

async function requestTarget(fetchImpl, baseUrl, target, context, prepared = {}) {
  const path = Object.hasOwn(prepared, 'resolvedPath')
    ? prepared.resolvedPath
    : resolveTemplate(target.url ?? target.path, context)
  if (!path || typeof path !== 'string') {
    return { ok: false, status: null, latencyMs: 0, body: undefined, errors: ['target URL or path is required'] }
  }
  const headers = { ...resolveTemplate(target.headers, context) }
  const token = context.token ?? context.user?.token
  if (token && target.auth !== false && !headers.Authorization) headers.Authorization = `Bearer ${token}`
  let body = resolveTemplate(target.body, context)
  if (body !== undefined && typeof body !== 'string' && !(body instanceof Uint8Array)) {
    body = JSON.stringify(body)
    if (!headers['Content-Type']) headers['Content-Type'] = 'application/json'
  }
  return executeHttpRequest(
    fetchImpl,
    absoluteUrl(baseUrl, path),
    { method: target.method ?? 'POST', headers, body, signal: context.signal },
    target,
    context,
  )
}

function validateEnvelope(frame) {
  const errors = []
  if (!frame || typeof frame !== 'object') return ['realtime envelope must be an object']
  if (typeof frame.eventId !== 'string' || !frame.eventId) errors.push('realtime envelope requires eventId')
  if (typeof frame.workspaceId !== 'string' || !frame.workspaceId) errors.push('realtime envelope requires workspaceId')
  if (frame.sequenceScope !== 'object' && frame.sequenceScope !== 'audience') {
    errors.push('realtime envelope sequenceScope is invalid')
  }
  if (typeof frame.sequenceKey !== 'string' || !frame.sequenceKey) errors.push('realtime envelope requires sequenceKey')
  if (!Number.isSafeInteger(frame.sequence) || frame.sequence < 0) errors.push('realtime envelope sequence is invalid')
  if (typeof frame.calibrationPath !== 'string' || !frame.calibrationPath.startsWith('/api/')) {
    errors.push('realtime envelope requires an API calibrationPath')
  }
  return errors
}

function waitForOpen(socket, timeoutMs, clock, signal) {
  if (socket?.readyState === 1 || socket?.OPEN === socket?.readyState) {
    return Promise.resolve({ ok: true, latencyMs: 0 })
  }
  const started = clock()
  if (signal?.aborted) {
    return Promise.resolve({ ok: false, aborted: true, error: 'WebSocket open aborted', latencyMs: 0 })
  }
  return new Promise((resolve) => {
    let settled = false
    let timer
    let unsubscribes = []
    const finish = (result) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      signal?.removeEventListener('abort', onAbort)
      for (const unsubscribe of unsubscribes) unsubscribe()
      resolve({ ...result, latencyMs: Math.max(0, clock() - started) })
    }
    const onAbort = () => finish({ ok: false, aborted: true, error: 'WebSocket open aborted' })
    unsubscribes = [
      onceSocket(socket, 'open', () => finish({ ok: true })),
      onceSocket(socket, 'error', (error) => finish({ ok: false, error: socketErrorMessage(error) })),
      onceSocket(socket, 'close', () => finish({ ok: false, error: 'WebSocket closed before opening' })),
    ]
    timer = setTimeout(() => finish({ ok: false, error: `WebSocket open timed out after ${timeoutMs}ms` }), timeoutMs)
    signal?.addEventListener('abort', onAbort, { once: true })
  })
}

function onSocket(socket, event, handler) {
  if (typeof socket?.addEventListener === 'function') socket.addEventListener(event, handler)
  else if (typeof socket?.on === 'function') socket.on(event, handler)
  else socket[`on${event}`] = handler
}

function onceSocket(socket, event, handler) {
  if (typeof socket?.addEventListener === 'function') {
    socket.addEventListener(event, handler, { once: true })
    return () => socket.removeEventListener?.(event, handler)
  }
  if (typeof socket?.once === 'function') {
    socket.once(event, handler)
    return () => socket.off?.(event, handler)
  }
  onSocket(socket, event, handler)
  return () => {
    if (socket?.[`on${event}`] === handler) socket[`on${event}`] = null
  }
}

function closeSocket(socket) {
  try {
    if (socket && socket.readyState !== 3) {
      socket.close()
      socket.terminate?.()
    }
  } catch {
    // Cleanup errors do not change scenario semantics.
  }
}

function buildSocketUrl(options, user, index, reconnect) {
  if (typeof options.buildUrl === 'function') return options.buildUrl({ user, index, reconnect })
  const raw = options.wsUrl
  if (!raw) throw new TypeError('options.wsUrl is required')
  if (!user?.token || options.tokenInQuery === false) return raw
  const separator = raw.includes('?') ? '&' : '?'
  return `${raw}${separator}${encodeURIComponent(options.tokenQueryName ?? 'token')}=${encodeURIComponent(user.token)}`
}

function sampleFromRequest(phase, operation, result, connectionIndex) {
  return {
    phase,
    operation,
    ...(connectionIndex === undefined ? {} : { connectionIndex }),
    status: result.status,
    latencyMs: result.latencyMs,
    ok: result.ok,
    aborted: result.aborted === true,
  }
}

function normalizeUsers(users, token) {
  if (Array.isArray(users) && users.length) {
    return users.map((user) => typeof user === 'string' ? { token: user } : user)
  }
  return token ? [{ token }] : [{}]
}

function positiveInteger(value, fallback) {
  return Number.isSafeInteger(Number(value)) && Number(value) > 0 ? Number(value) : fallback
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

function toText(value) {
  if (typeof value === 'string') return value
  if (value instanceof ArrayBuffer) return new TextDecoder().decode(value)
  if (ArrayBuffer.isView(value)) return new TextDecoder().decode(value)
  return String(value)
}

function socketErrorMessage(error) {
  return error?.message ?? error?.error?.message ?? 'WebSocket error'
}
