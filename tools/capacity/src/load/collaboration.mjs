import { createRequire } from 'node:module'
import { pathToFileURL } from 'node:url'
import {
  addAbortError,
  createError,
  createScenarioResult,
  defaultClock,
  errorMessage,
  finalizeScenarioResult,
  runRateSchedule,
  stableStringify,
  summarizeSamples,
  waitForDelay,
} from './common.mjs'

const collaborationRequire = createRequire(import.meta.url)

export async function runCollaborationScenario(options = {}) {
  const dependencies = await loadDependencies(options)
  const Provider = dependencies.Provider
  const Y = dependencies.Y
  const WebSocketPolyfill = dependencies.WebSocket
  const providerFactory = options.providerFactory ??
    ((configuration) => new Provider(configuration))
  const observerFactory = options.observerFactory ?? providerFactory
  const clock = options.clock ?? defaultClock
  const signal = options.signal
  const startedAt = clock()
  const samples = []
  const errors = []
  const rooms = normalizeRooms(options.rooms ?? options.targets?.rooms, options)
  const roomStates = rooms.map((room, roomIndex) => ({
    room,
    roomIndex,
    clients: [],
    expectedMarkers: [],
    expectedSnapshot: undefined,
    editCursor: 0,
    lastEditTarget: undefined,
  }))
  const liveProviders = new Set()
  const liveDocuments = new Set()
  const issuedTickets = new Set()
  const durationMs = nonNegativeNumber(
    options.durationMs ?? options.targets?.durationMs,
    0,
  )
  const editsPerSecond = positiveNumber(
    options.editsPerSecond ?? options.targets?.editsPerSecond,
    0,
  )
  const sustainedMode = durationMs > 0 || editsPerSecond > 0
  const requireDurableReload = options.requireDurableReload ?? sustainedMode
  const hasExplicitNodeTargets = options.collaborationNodes !== undefined
    || options.nodeTargets !== undefined
  const nodeTargets = normalizeNodeTargets(
    options.collaborationNodes ?? options.nodeTargets ?? options.collaborationUrl,
  )
  const requireCrossNode = options.requireCrossNode ??
    (sustainedMode && nodeTargets.length >= 2)
  const editNodes = new Set()
  const metrics = {
    rooms: rooms.length,
    clients: 0,
    edits: 0,
    targetEditsPerSecond: editsPerSecond || null,
    achievedEditsPerSecond: null,
    editDurationMs: 0,
    reconnects: 0,
    convergedRooms: 0,
    convergenceFailures: 0,
    durableReloadRequired: requireDurableReload,
    durableReloads: 0,
    durableReloadFailures: 0,
    observerClients: 0,
    roomIsolationFailures: 0,
    editNodes: [],
    convergenceLatency: summarizeSamples([]),
    durableReloadLatency: summarizeSamples([]),
  }

  if (rooms.length === 0) {
    errors.push(createError('configuration', 'at least one collaboration room is required'))
  }
  if (sustainedMode && !(durationMs > 0 && editsPerSecond > 0)) {
    errors.push(createError(
      'configuration',
      'durationMs and editsPerSecond must both be greater than zero for sustained collaboration load',
    ))
  }
  if (requireCrossNode && nodeTargets.length < 2) {
    errors.push(createError(
      'configuration',
      'cross-node collaboration requires at least two explicit node targets',
    ))
  }

  const abortResources = () => {
    for (const provider of liveProviders) destroyProvider(provider)
    for (const document of liveDocuments) destroyDocument(document)
    liveProviders.clear()
    liveDocuments.clear()
  }
  signal?.addEventListener('abort', abortResources, { once: true })

  try {
    await connectInitialClients()
    if (!signal?.aborted && errors.length === 0) {
      if (sustainedMode) await runSustainedEdits()
      else await runFixedEdits()
    }

    await settle(options.editSettleMs)
    if (!signal?.aborted) {
      await Promise.all(roomStates.map((state) => checkConvergence(state, 'edit')))
    }

    if (!signal?.aborted) await reconnectAcrossNodes()
    await settle(options.reconnectSettleMs)
    if (!signal?.aborted) {
      await Promise.all(roomStates.map((state) => checkConvergence(state, 'reconnect')))
      checkRoomIsolation()
      freezeExpectedSnapshots()
    }

    if (!signal?.aborted && requireCrossNode && editNodes.size < 2) {
      errors.push(createError(
        'cross_node_edit_failure',
        'sustained collaboration edits did not execute through at least two nodes',
        { editNodes: [...editNodes].sort() },
      ))
    }

    if (!signal?.aborted && requireDurableReload) {
      disconnectActiveClients()
      await settle(options.durableSettleMs ?? options.editSettleMs)
      if (!signal?.aborted) {
        await Promise.all(roomStates.map((state) => verifyDurableReload(state)))
      }
    }
  } finally {
    signal?.removeEventListener('abort', abortResources)
    abortResources()
  }

  metrics.editNodes = [...editNodes].sort()
  metrics.convergenceLatency = summarizeSamples(
    samples.filter((sample) => sample.operation === 'document.converge'),
  )
  metrics.durableReloadLatency = summarizeSamples(
    samples.filter((sample) => sample.operation === 'document.durable-reload'),
  )
  addAbortError(errors, signal, { loader: 'collaboration' })
  const result = createScenarioResult('collaboration', startedAt, samples, errors, {
    ...metrics,
    aborted: signal?.aborted === true,
  }, clock)
  return finalizeScenarioResult(result, options.outputDir, options.outputFileName)

  async function connectInitialClients() {
    const plans = roomStates.flatMap((state) =>
      Array.from({ length: state.room.clients }, (_, clientIndex) => ({ state, clientIndex })))
    const concurrency = positiveInteger(
      options.connectionConcurrency ?? options.concurrency,
      plans.length || 1,
    )
    await runConcurrent(plans, concurrency, async ({ state, clientIndex }) => {
      if (signal?.aborted) return
      const target = selectNodeTarget(nodeTargets, {
        phase: 'connect',
        roomIndex: state.roomIndex,
        clientIndex,
        attempt: 0,
      })
      const client = await createClient({
        state,
        clientIndex,
        target,
        phase: 'connect',
        factory: providerFactory,
      })
      if (client) {
        state.clients.push(client)
        metrics.clients += 1
      }
    }, signal)
    for (const state of roomStates) {
      state.clients.sort((left, right) => left.clientIndex - right.clientIndex)
    }
  }

  async function createClient({
    state,
    clientIndex,
    target,
    phase,
    factory,
    document = new Y.Doc(),
    observer = false,
  }) {
    liveDocuments.add(document)
    const user = selectUser(state.room.users ?? options.users, options.token, clientIndex)
    const syncStarted = clock()
    let provider
    let nodeId = target.nodeId ?? target.url
    try {
      const connectionToken = await createConnectionToken({
        state,
        clientIndex,
        phase,
        observer,
        target,
        user,
      })
      const connectionTarget = !hasExplicitNodeTargets && connectionToken.url
        ? { ...target, url: absoluteWebSocketUrl(connectionToken.url) }
        : target
      nodeId = await resolveNodeIdentity(connectionTarget, {
        room: state.room,
        roomIndex: state.roomIndex,
        clientIndex,
        phase,
        observer,
        signal,
      })
      const providerConfig = {
        url: connectionTarget.url,
        name: state.room.name,
        document,
        token: connectionToken.token,
        ...(WebSocketPolyfill ? { WebSocketPolyfill } : {}),
        ...(options.providerOptions ?? {}),
        ...(state.room.providerOptions ?? {}),
      }
      provider = await factory(providerConfig, {
        room: state.room,
        roomIndex: state.roomIndex,
        clientIndex,
        user,
        Y,
        signal,
        phase,
        observer,
        url: connectionTarget.url,
        nodeId,
        nodeTarget: connectionTarget,
      })
      if (signal?.aborted) throw createAbortException(signal.reason)
      liveProviders.add(provider)
      await waitForSync(provider, options.syncTimeoutMs ?? 10_000, clock, false, signal)
      samples.push({
        phase,
        operation: observer ? 'observer.sync' : 'provider.sync',
        room: state.room.name,
        clientIndex,
        node: nodeId,
        targetUrl: connectionTarget.url,
        latencyMs: Math.max(0, clock() - syncStarted),
        ok: true,
      })
      return {
        document,
        provider,
        clientIndex,
        user,
        target: connectionTarget,
        url: connectionTarget.url,
        nodeId,
      }
    } catch (error) {
      samples.push({
        phase,
        operation: observer ? 'observer.sync' : 'provider.sync',
        room: state.room.name,
        clientIndex,
        node: nodeId,
        targetUrl: target.url,
        latencyMs: Math.max(0, clock() - syncStarted),
        ok: false,
        aborted: signal?.aborted === true,
      })
      if (!signal?.aborted) {
        const code = error?.code === 'node_identity_failure'
          ? 'node_identity_failure'
          : error?.code === 'ticket_reuse'
            ? 'ticket_reuse'
            : observer
              ? 'observer_sync_failure'
              : 'provider_sync_failure'
        errors.push(createError(
          code,
          errorMessage(error),
          { room: state.room.name, clientIndex, node: nodeId, targetUrl: target.url },
        ))
      }
      releaseProvider(provider)
      releaseDocument(document)
      return undefined
    }
  }

  async function createConnectionToken({ state, clientIndex, phase, observer, target, user }) {
    if (typeof options.ticketIssuer !== 'function') {
      return { token: user?.token ?? options.token }
    }
    let initial = await issueFreshTicket()
    return {
      url: initial.url,
      token: async () => {
        if (initial !== undefined) {
          const ticket = initial.ticket
          initial = undefined
          return ticket
        }
        return (await issueFreshTicket()).ticket
      },
    }

    async function issueFreshTicket() {
      const issued = await options.ticketIssuer({
        room: state.room,
        roomIndex: state.roomIndex,
        clientIndex,
        phase,
        observer,
        username: user?.username,
        nodeId: target.nodeId,
        nodeTarget: target,
        targetUrl: target.url,
        signal,
      })
      const ticket = typeof issued === 'string' ? issued : issued?.ticket ?? issued?.token
      if (typeof ticket !== 'string' || ticket.length === 0) {
        throw new Error('collaboration ticket issuer returned no ticket')
      }
      if (issuedTickets.has(ticket)) {
        const error = new Error('collaboration ticket issuer reused a one-time ticket')
        error.code = 'ticket_reuse'
        throw error
      }
      if (issued?.documentName) {
        if (state.room.ticketDocumentName === true) {
          state.room.name = issued.documentName
          state.room.ticketDocumentName = false
        } else if (issued.documentName !== state.room.name) {
          throw new Error('collaboration ticket document name does not match the room')
        }
      }
      issuedTickets.add(ticket)
      return {
        ticket,
        url: typeof issued === 'object' ? issued?.url : undefined,
      }
    }
  }

  async function resolveNodeIdentity(target, context) {
    const mustVerify = requireCrossNode || target.nodeId !== undefined || target.identityUrl !== undefined
    if (!mustVerify) return target.nodeId ?? target.url
    let identity
    try {
      if (typeof options.nodeIdentityResolver === 'function') {
        identity = await options.nodeIdentityResolver(target, context)
      } else {
        const fetchImpl = options.fetch ?? globalThis.fetch
        if (typeof fetchImpl !== 'function') throw new Error('node identity fetch implementation is required')
        const response = await fetchImpl(target.identityUrl ?? defaultNodeIdentityUrl(target.url), {
          method: 'GET',
          signal,
        })
        if (!response || response.ok !== true) {
          throw new Error(`node identity endpoint returned HTTP ${response?.status ?? 'unknown'}`)
        }
        identity = await response.json()
      }
    } catch (cause) {
      const error = new Error(`collaboration node identity verification failed: ${errorMessage(cause)}`)
      error.code = 'node_identity_failure'
      throw error
    }
    const actualNodeId = typeof identity === 'string' ? identity : identity?.nodeId
    if (typeof actualNodeId !== 'string' || actualNodeId.length === 0
      || (target.nodeId !== undefined && actualNodeId !== target.nodeId)) {
      const error = new Error('collaboration node identity did not match the selected target')
      error.code = 'node_identity_failure'
      throw error
    }
    return actualNodeId
  }

  async function runSustainedEdits() {
    const availableStates = roomStates.filter((state) => state.clients.length > 0)
    const schedule = weightedRoomSchedule(availableStates)
    if (schedule.length === 0) return
    const editStarted = clock()
    const scheduleResult = await runRateSchedule({
      ratePerSecond: editsPerSecond,
      durationMs,
      concurrency: positiveInteger(options.editConcurrency ?? options.concurrency, 1),
      signal,
      clock,
      sleep: options.sleep,
      handler: async (editIndex) => {
        const state = schedule[editIndex % schedule.length]
        const client = state.clients[state.editCursor % state.clients.length]
        state.editCursor += 1
        executeEdit(state, client, editIndex)
      },
    })
    metrics.editDurationMs = Math.max(0, clock() - editStarted)
    metrics.achievedEditsPerSecond = metrics.editDurationMs > 0
      ? metrics.edits * 1_000 / metrics.editDurationMs
      : 0
    metrics.scheduledEdits = scheduleResult.scheduled
  }

  async function runFixedEdits() {
    const plans = roomStates.flatMap((state) =>
      state.clients.flatMap((client) =>
        Array.from({ length: state.room.editsPerClient }, (_, editIndex) => ({
          state,
          client,
          editIndex,
        }))))
    const editStarted = clock()
    await runConcurrent(
      plans,
      positiveInteger(options.editConcurrency ?? options.concurrency, plans.length || 1),
      async ({ state, client, editIndex }) => executeEdit(state, client, editIndex),
      signal,
    )
    metrics.editDurationMs = Math.max(0, clock() - editStarted)
    metrics.achievedEditsPerSecond = metrics.editDurationMs > 0
      ? metrics.edits * 1_000 / metrics.editDurationMs
      : null
  }

  function executeEdit(state, client, editIndex) {
    if (signal?.aborted) return
    const marker = `${state.room.markerPrefix}:${client.clientIndex}:${editIndex}:${metrics.edits}`
    const editStarted = clock()
    try {
      applyEdit(client.document, state.room, marker, {
        clientIndex: client.clientIndex,
        editIndex,
        Y,
        node: client.nodeId,
      })
      state.expectedMarkers.push(marker)
      state.lastEditTarget = client.target
      editNodes.add(client.nodeId)
      metrics.edits += 1
      samples.push({
        phase: 'edit',
        operation: 'document.edit',
        room: state.room.name,
        clientIndex: client.clientIndex,
        node: client.nodeId,
        targetUrl: client.url,
        latencyMs: Math.max(0, clock() - editStarted),
        ok: true,
      })
    } catch (error) {
      samples.push({
        phase: 'edit',
        operation: 'document.edit',
        room: state.room.name,
        clientIndex: client.clientIndex,
        node: client.nodeId,
        targetUrl: client.url,
        latencyMs: Math.max(0, clock() - editStarted),
        ok: false,
      })
      errors.push(createError('edit_failure', errorMessage(error), {
        room: state.room.name,
        clientIndex: client.clientIndex,
        node: client.nodeId,
        targetUrl: client.url,
      }))
    }
  }

  async function checkConvergence(state, phase) {
    if (state.clients.length === 0 || signal?.aborted) return
    const convergenceStarted = clock()
    const timeoutMs = options.convergenceTimeoutMs ?? 10_000
    const pollMs = options.pollIntervalMs ?? 20
    let snapshots = []
    while (!signal?.aborted && clock() - convergenceStarted <= timeoutMs) {
      snapshots = state.clients.map((client) => snapshotDocument(client.document, state.room))
      const first = stableStringify(snapshots[0])
      const equal = snapshots.every((snapshot) => stableStringify(snapshot) === first)
      const markersPresent = state.expectedMarkers.every((marker) =>
        snapshots.every((snapshot) => stableStringify(snapshot).includes(marker)))
      if (equal && markersPresent) {
        metrics.convergedRooms += 1
        samples.push({
          phase,
          operation: 'document.converge',
          room: state.room.name,
          latencyMs: Math.max(0, clock() - convergenceStarted),
          ok: true,
        })
        return
      }
      try {
        await waitForDelay(pollMs, { signal, sleep: options.sleep })
      } catch {
        return
      }
    }
    if (signal?.aborted) return

    metrics.convergenceFailures += 1
    samples.push({
      phase,
      operation: 'document.converge',
      room: state.room.name,
      latencyMs: Math.max(0, clock() - convergenceStarted),
      ok: false,
    })
    errors.push(createError(
      'convergence_failure',
      'collaboration clients did not reach the same final document',
      {
        room: state.room.name,
        phase,
        snapshotCount: new Set(snapshots.map(stableStringify)).size,
      },
    ))
  }

  async function reconnectAcrossNodes() {
    const reconnectsPerRoom = Math.max(
      0,
      Number(options.reconnectsPerRoom ?? options.targets?.reconnectsPerRoom ?? 1) || 0,
    )
    const plans = roomStates.flatMap((state) =>
      state.clients.slice(0, reconnectsPerRoom).map((client) => ({ state, client })))
    await runConcurrent(
      plans,
      positiveInteger(options.connectionConcurrency ?? options.concurrency, plans.length || 1),
      async ({ state, client }) => {
        if (signal?.aborted) return
        const reconnectStarted = clock()
        const previousTarget = client.target
        const previousNodeId = client.nodeId
        const nextTarget = selectAlternateNodeTarget(nodeTargets, previousTarget, {
          phase: 'reconnect',
          roomIndex: state.roomIndex,
          clientIndex: client.clientIndex,
          attempt: metrics.reconnects + 1,
        })
        releaseProvider(client.provider)
        client.provider = undefined
        try {
          const replacement = await createClient({
            state,
            clientIndex: client.clientIndex,
            target: nextTarget,
            phase: 'reconnect',
            factory: providerFactory,
            document: client.document,
          })
          if (!replacement) return
          client.provider = replacement.provider
          client.target = replacement.target
          client.url = replacement.url
          client.nodeId = replacement.nodeId
          metrics.reconnects += 1
          samples.push({
            phase: 'reconnect',
            operation: 'provider.reconnect',
            room: state.room.name,
            clientIndex: client.clientIndex,
            previousNode: previousNodeId,
            previousTargetUrl: previousTarget.url,
            node: replacement.nodeId,
            targetUrl: nextTarget.url,
            latencyMs: Math.max(0, clock() - reconnectStarted),
            ok: true,
          })
        } catch (error) {
          samples.push({
            phase: 'reconnect',
            operation: 'provider.reconnect',
            room: state.room.name,
            clientIndex: client.clientIndex,
            previousNode: previousTarget.nodeId ?? previousTarget.url,
            previousTargetUrl: previousTarget.url,
            node: nextTarget.nodeId ?? nextTarget.url,
            targetUrl: nextTarget.url,
            latencyMs: Math.max(0, clock() - reconnectStarted),
            ok: false,
          })
          if (!signal?.aborted) {
            errors.push(createError('provider_reconnect_failure', errorMessage(error), {
              room: state.room.name,
              clientIndex: client.clientIndex,
              previousNode: previousTarget.nodeId ?? previousTarget.url,
              previousTargetUrl: previousTarget.url,
              node: nextTarget.nodeId ?? nextTarget.url,
              targetUrl: nextTarget.url,
            }))
          }
        }
      },
      signal,
    )
  }

  function freezeExpectedSnapshots() {
    for (const state of roomStates) {
      if (state.clients.length === 0) continue
      const snapshots = state.clients.map((client) => snapshotDocument(client.document, state.room))
      const first = stableStringify(snapshots[0])
      if (snapshots.every((snapshot) => stableStringify(snapshot) === first)) {
        state.expectedSnapshot = snapshots[0]
      }
    }
  }

  function disconnectActiveClients() {
    for (const state of roomStates) {
      for (const client of state.clients) {
        releaseProvider(client.provider)
        releaseDocument(client.document)
      }
      state.clients = []
    }
  }

  async function verifyDurableReload(state) {
    const reloadStarted = clock()
    if (state.expectedSnapshot === undefined) {
      recordDurableFailure(state, reloadStarted, 'no converged expected snapshot was available')
      return
    }
    const observerTarget = selectAlternateNodeTarget(
      nodeTargets,
      state.lastEditTarget,
      {
        phase: 'durable-reload',
        roomIndex: state.roomIndex,
        clientIndex: 0,
        attempt: 0,
      },
    )
    const observer = await createClient({
      state,
      clientIndex: -1,
      target: observerTarget,
      phase: 'durable-reload',
      factory: observerFactory,
      observer: true,
    })
    if (!observer) {
      metrics.durableReloadFailures += 1
      return
    }
    metrics.observerClients += 1
    const timeoutMs = options.reloadTimeoutMs ?? options.convergenceTimeoutMs ?? 10_000
    const pollMs = options.pollIntervalMs ?? 20
    const expected = stableStringify(state.expectedSnapshot)
    let observed
    while (!signal?.aborted && clock() - reloadStarted <= timeoutMs) {
      observed = snapshotDocument(observer.document, state.room)
      const serialized = stableStringify(observed)
      const markersPresent = state.expectedMarkers.every((marker) => serialized.includes(marker))
      if (serialized === expected && markersPresent) {
        metrics.durableReloads += 1
        samples.push({
          phase: 'durable-reload',
          operation: 'document.durable-reload',
          room: state.room.name,
          node: observer.nodeId,
          targetUrl: observer.url,
          latencyMs: Math.max(0, clock() - reloadStarted),
          ok: true,
        })
        releaseProvider(observer.provider)
        releaseDocument(observer.document)
        return
      }
      try {
        await waitForDelay(pollMs, { signal, sleep: options.sleep })
      } catch {
        break
      }
    }
    releaseProvider(observer.provider)
    releaseDocument(observer.document)
    if (!signal?.aborted) {
      recordDurableFailure(
        state,
        reloadStarted,
        'a fresh observer did not reload the durable final document',
        observed,
      )
    }
  }

  function recordDurableFailure(state, reloadStarted, message, observed) {
    metrics.durableReloadFailures += 1
    samples.push({
      phase: 'durable-reload',
      operation: 'document.durable-reload',
      room: state.room.name,
      latencyMs: Math.max(0, clock() - reloadStarted),
      ok: false,
    })
    errors.push(createError('durable_reload_failure', message, {
      room: state.room.name,
      expectedMarkerCount: state.expectedMarkers.length,
      observedSnapshot: observed === undefined ? undefined : stableStringify(observed),
    }))
  }

  function checkRoomIsolation() {
    for (const state of roomStates) {
      const snapshots = state.clients.map((client) =>
        stableStringify(snapshotDocument(client.document, state.room)))
      for (const other of roomStates) {
        if (other === state) continue
        const leaked = other.expectedMarkers.some((marker) =>
          snapshots.some((snapshot) => snapshot.includes(marker)))
        if (leaked) {
          metrics.roomIsolationFailures += 1
          errors.push(createError(
            'room_isolation_failure',
            'document contains an edit marker from another room',
            { room: state.room.name, sourceRoom: other.room.name },
          ))
        }
      }
    }
  }

  async function settle(value) {
    const milliseconds = nonNegativeNumber(value, 0)
    if (milliseconds <= 0 || signal?.aborted) return
    try {
      await waitForDelay(milliseconds, { signal, sleep: options.sleep })
    } catch {
      // The aborted scenario is recorded exactly once below.
    }
  }

  function releaseProvider(provider) {
    destroyProvider(provider)
    liveProviders.delete(provider)
  }

  function releaseDocument(document) {
    destroyDocument(document)
    liveDocuments.delete(document)
  }
}

async function loadDependencies(options) {
  const injected = options.dependencies ?? {}
  let Provider = options.Provider ?? injected.Provider ?? injected.HocuspocusProvider
  let Y = options.Y ?? options.Yjs ?? injected.Y ?? injected.Yjs
  let WebSocket = options.WebSocket ?? injected.WebSocket

  if (!Provider) {
    const module = await importResolved('@hocuspocus/provider')
    Provider = module.HocuspocusProvider ?? module.default
  }
  if (!Y) Y = await importResolved('yjs')
  if (!WebSocket) {
    const module = await importResolved('ws')
    WebSocket = module.WebSocket ?? module.default
  }
  if (typeof Provider !== 'function') throw new TypeError('Hocuspocus Provider constructor is required')
  if (typeof Y?.Doc !== 'function') throw new TypeError('Yjs module with Doc is required')
  return { Provider, Y, WebSocket }
}

async function importResolved(name) {
  const resolved = collaborationRequire.resolve(name)
  return import(pathToFileURL(resolved).href)
}

function normalizeRooms(value, options) {
  if (!value) return []
  const rooms = Array.isArray(value) ? value : [value]
  return rooms.map((room, index) => {
    const configuration = typeof room === 'string' ? { name: room } : room
    const name = configuration.name ?? `capacity-room-${index + 1}`
    return {
      ...configuration,
      name,
      clients: positiveInteger(configuration.clients ?? options.clientsPerRoom ?? options.concurrency, 2),
      editsPerClient: positiveInteger(configuration.editsPerClient ?? options.editsPerClient, 1),
      weight: positiveInteger(configuration.weight, 1),
      markerPrefix: configuration.markerPrefix ?? `capacity:${name}`,
      field: configuration.field ?? 'content',
    }
  })
}

function applyEdit(document, room, marker, context) {
  if (typeof room.edit === 'function') {
    room.edit(document, marker, context)
    return
  }
  const text = document.getText(room.field)
  text.insert(text.length, `${marker}\n`)
}

function snapshotDocument(document, room) {
  if (typeof room.snapshot === 'function') return room.snapshot(document)
  if (typeof document.getText === 'function') return document.getText(room.field).toString()
  if (typeof document.toJSON === 'function') return document.toJSON()
  return undefined
}

function waitForSync(provider, timeoutMs, clock, requireNewEvent = false, signal) {
  if (signal?.aborted) return Promise.reject(createAbortException(signal.reason))
  if (!requireNewEvent && (provider?.synced === true || provider?.isSynced === true)) {
    return Promise.resolve(0)
  }
  const started = clock()
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(createAbortException(signal.reason))
      return
    }
    let settled = false
    let timer
    let unsubscribe = () => {}
    const finish = (error) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      signal?.removeEventListener('abort', onAbort)
      unsubscribe()
      if (error) reject(error)
      else resolve(Math.max(0, clock() - started))
    }
    const onSynced = (event) => {
      if (event?.state === false) return
      finish()
    }
    const onStatus = (event) => {
      if (event?.status === 'connected' && (provider.synced === true || provider.isSynced === true)) finish()
    }
    const unsubscribeSync = subscribeProvider(provider, 'synced', onSynced)
    const unsubscribeStatus = subscribeProvider(provider, 'status', onStatus)
    unsubscribe = () => {
      unsubscribeSync()
      unsubscribeStatus()
    }
    const onAbort = () => finish(createAbortException(signal.reason))
    timer = setTimeout(() => finish(new Error(`provider sync timed out after ${timeoutMs}ms`)), timeoutMs)
    signal?.addEventListener('abort', onAbort, { once: true })
  })
}

function subscribeProvider(provider, event, handler) {
  if (typeof provider?.on !== 'function') return () => {}
  provider.on(event, handler)
  return () => {
    if (typeof provider.off === 'function') provider.off(event, handler)
  }
}

function destroyProvider(provider) {
  try {
    provider?.disconnect?.()
    provider?.destroy?.()
  } catch {
    // Cleanup errors do not alter durable convergence evidence.
  }
}

function destroyDocument(document) {
  try {
    document?.destroy?.()
  } catch {
    // Cleanup errors do not alter durable convergence evidence.
  }
}

function createAbortException(reason) {
  const error = new Error(reason == null ? 'operation aborted' : String(reason))
  error.name = 'AbortError'
  return error
}

function selectUser(users, token, index) {
  if (!Array.isArray(users) || users.length === 0) return { token }
  const user = users[Math.abs(index) % users.length]
  return typeof user === 'string' ? { token: user } : user
}

function normalizeNodeTargets(value) {
  const entries = Array.isArray(value) ? value : value == null ? [] : [value]
  return entries.map((entry, index) => {
    const target = typeof entry === 'string' ? { url: entry } : entry
    if (!target || typeof target !== 'object' || Array.isArray(target)
      || typeof target.url !== 'string' || target.url.length === 0) {
      throw new TypeError(`collaboration node target ${index} must provide a URL`)
    }
    let parsed
    try {
      parsed = new URL(target.url)
    } catch {
      throw new TypeError(`collaboration node target ${index} URL is invalid`)
    }
    if (!['ws:', 'wss:'].includes(parsed.protocol)) {
      throw new TypeError(`collaboration node target ${index} URL must use WS or WSS`)
    }
    if (target.nodeId !== undefined
      && (typeof target.nodeId !== 'string' || target.nodeId.length === 0)) {
      throw new TypeError(`collaboration node target ${index} nodeId is invalid`)
    }
    return {
      url: target.url,
      ...(target.nodeId === undefined ? {} : { nodeId: target.nodeId }),
      ...(target.identityUrl === undefined ? {} : { identityUrl: target.identityUrl }),
      index,
    }
  })
}

function selectNodeTarget(targets, context) {
  if (targets.length === 0) throw new TypeError('options.collaborationUrl or collaborationNodes is required')
  return targets[
    (context.roomIndex + Math.max(0, context.clientIndex) + context.attempt) % targets.length
  ]
}

function selectAlternateNodeTarget(targets, previousTarget, context) {
  if (targets.length <= 1) return selectNodeTarget(targets, context)
  const previousIndex = targets.findIndex((target) => target.index === previousTarget?.index)
  return targets[(Math.max(0, previousIndex) + 1) % targets.length]
}

function defaultNodeIdentityUrl(value) {
  const url = new URL(value)
  url.protocol = url.protocol === 'wss:' ? 'https:' : 'http:'
  url.pathname = '/health'
  url.search = ''
  url.hash = ''
  return url.toString()
}

function absoluteWebSocketUrl(value) {
  let url
  try {
    url = new URL(value)
  } catch {
    throw new TypeError('collaboration ticket URL must be absolute')
  }
  if (!['ws:', 'wss:'].includes(url.protocol)) {
    throw new TypeError('collaboration ticket URL must use WS or WSS')
  }
  return value
}

function weightedRoomSchedule(states) {
  return states.flatMap((state) => Array.from({ length: state.room.weight }, () => state))
}

async function runConcurrent(items, concurrency, handler, signal) {
  const queue = Array.from(items)
  if (queue.length === 0) return
  let cursor = 0
  const width = Math.max(1, Math.min(queue.length, concurrency))
  await Promise.all(Array.from({ length: width }, async () => {
    while (cursor < queue.length && !signal?.aborted) {
      const index = cursor
      cursor += 1
      await handler(queue[index], index)
    }
  }))
}

function positiveInteger(value, fallback) {
  const number = Number(value)
  return Number.isSafeInteger(number) && number > 0 ? number : fallback
}

function positiveNumber(value, fallback) {
  const number = Number(value)
  return Number.isFinite(number) && number > 0 ? number : fallback
}

function nonNegativeNumber(value, fallback) {
  const number = Number(value)
  return Number.isFinite(number) && number >= 0 ? number : fallback
}
