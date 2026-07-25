import { randomUUID } from 'node:crypto'

import {
  deriveCollaborationRooms,
  deriveFixtureUsers,
  validateFixtureConfig,
} from './fixture.mjs'

const DEFAULT_LOGIN_PATH = '/api/auth/login'
const DEFAULT_TICKET_PATH = '/api/knowledge-bases/{spaceId}/items/{itemId}/collaboration/ticket'

export async function bootstrapCapacityRuntime(runtime, options = {}) {
  const fetchImpl = options.fetch ?? globalThis.fetch
  if (typeof fetchImpl !== 'function') throw new TypeError('bootstrap fetch implementation is required')
  if (!runtime || typeof runtime !== 'object' || Array.isArray(runtime)) {
    throw new TypeError('capacity runtime must be an object')
  }

  const preparedRuntime = materializeFixtureRuntime(runtime)
  const loaders = structuredClone(preparedRuntime.loaders ?? preparedRuntime.loaderOptions ?? {})
  const probeRunId = options.probeRunId ?? randomUUID()
  const probeRunIds = createProbeRunIds(options.probeRunIds, probeRunId)
  injectRuntimeValues(loaders, { probeRunId, probeRunIds })
  const summary = {
    authenticatedUsers: 0,
    collaborationTickets: 0,
    probeRunId,
    probeRunIds,
  }
  const bootstrap = preparedRuntime.bootstrap
  if (!bootstrap) {
    return { loaders, summary }
  }
  const baseUrl = absoluteBaseUrl(bootstrap.baseUrl)
  const users = await authenticateUsers(fetchImpl, baseUrl, bootstrap.authentication, options.signal)
  summary.authenticatedUsers = users.length
  applyAuthenticatedUsers(loaders, users, bootstrap.authentication)
  prepareCollaborationTicketIssuer(
    fetchImpl,
    baseUrl,
    bootstrap.collaboration,
    users,
    loaders,
    options.signal,
    () => {
      summary.collaborationTickets += 1
    },
  )
  return { loaders, summary }
}

function materializeFixtureRuntime(runtime) {
  const prepared = structuredClone(runtime)
  const fixture = prepared.fixture
  if (!fixture) return prepared
  assertPlainObject(fixture, 'runtime fixture')
  assertOnlyKeys(
    fixture,
    new Set(['seedId', 'workspaceWeights', 'users', 'collaboration']),
    'runtime fixture',
  )
  const fixtureConfig = {
    seedId: fixture.seedId,
    workspaceWeights: fixture.workspaceWeights,
  }
  const validation = validateFixtureConfig(fixtureConfig)
  if (!validation.ok) {
    throw new Error(`runtime fixture is invalid: ${validation.errors.join('; ')}`)
  }
  if (!prepared.bootstrap?.authentication) {
    throw new Error('runtime fixture requires bootstrap authentication')
  }
  const authentication = prepared.bootstrap.authentication
  if (authentication.users !== undefined) {
    throw new Error('runtime fixture cannot be combined with explicit authentication users')
  }
  if (typeof authentication.password !== 'string' || authentication.password.length === 0) {
    throw new Error('runtime fixture authentication password is required')
  }
  assertPlainObject(fixture.users, 'runtime fixture users')
  const users = deriveFixtureUsers(fixtureConfig, fixture.users)
  authentication.users = users.map((user) => ({
    ...user,
    password: authentication.password,
  }))

  if (fixture.collaboration !== undefined) {
    if (!prepared.bootstrap.collaboration) {
      throw new Error('runtime fixture collaboration requires bootstrap collaboration')
    }
    if (prepared.bootstrap.collaboration.rooms !== undefined) {
      throw new Error('runtime fixture cannot be combined with explicit collaboration rooms')
    }
    assertPlainObject(fixture.collaboration, 'runtime fixture collaboration')
    assertOnlyKeys(
      fixture.collaboration,
      new Set(['workspaceOrdinal', 'knowledgeItemOrdinals', 'clientsPerRoom']),
      'runtime fixture collaboration',
    )
    const clients = positiveInteger(fixture.collaboration.clientsPerRoom, 0)
    if (clients < 1) {
      throw new Error('runtime fixture collaboration clientsPerRoom must be a positive integer')
    }
    const rooms = deriveCollaborationRooms(fixtureConfig, {
      workspaceOrdinal: fixture.collaboration.workspaceOrdinal,
      knowledgeItemOrdinals: fixture.collaboration.knowledgeItemOrdinals,
    })
    prepared.bootstrap.collaboration.rooms = rooms.map((room) => ({
      ...room,
      clients,
    }))
  }
  return prepared
}

function injectRuntimeValues(loaders, values) {
  for (const loader of Object.values(loaders)) {
    if (!loader || typeof loader !== 'object' || Array.isArray(loader)) continue
    loader.runtimeValues = {
      ...(loader.runtimeValues ?? {}),
      ...values,
    }
  }
}

async function authenticateUsers(fetchImpl, baseUrl, config, signal) {
  if (!config) return []
  const configuredUsers = config.users
  if (!Array.isArray(configuredUsers) || configuredUsers.length === 0) {
    throw new Error('runtime bootstrap authentication requires at least one user')
  }
  const loginPath = config.loginPath ?? DEFAULT_LOGIN_PATH
  const concurrency = positiveInteger(config.concurrency, 16)
  return mapConcurrent(configuredUsers, concurrency, async (user, index) => {
    if (!user || typeof user !== 'object' || Array.isArray(user)
      || typeof user.username !== 'string' || user.username.length === 0
      || typeof user.password !== 'string' || user.password.length === 0) {
      throw new Error(`runtime bootstrap authentication user ${index} is invalid`)
    }
    const body = {
      username: user.username,
      password: user.password,
      deviceType: user.deviceType ?? 'WEB',
      deviceFingerprint: user.deviceFingerprint ?? `capacity-${user.username}-${index + 1}`,
      deviceName: user.deviceName ?? 'Capacity Runner',
      appVersion: user.appVersion ?? 'capacity-v1',
    }
    const response = await requestJson(fetchImpl, absoluteUrl(baseUrl, loginPath), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal,
    }, `authentication user ${index}`)
    if (typeof response.accessToken !== 'string' || response.accessToken.length === 0) {
      throw new Error(`runtime bootstrap authentication user ${index} returned no access token`)
    }
    return {
      username: user.username,
      ordinal: user.ordinal,
      workspaceOrdinal: user.workspaceOrdinal,
      email: user.email,
      token: response.accessToken,
    }
  })
}

function applyAuthenticatedUsers(loaders, users, config = {}) {
  if (users.length === 0) return
  const applyTo = new Set(config.applyTo ?? ['http', 'websocket', 'worker'])
  const safeUsers = users.map((user) => ({
    username: user.username,
    ordinal: user.ordinal,
    workspaceOrdinal: user.workspaceOrdinal,
    email: user.email,
    token: user.token,
  }))
  if (applyTo.has('http') && loaders.http) loaders.http.users = safeUsers
  if (applyTo.has('websocket') && loaders.websocket) {
    loaders.websocket.users = safeUsers
    loaders.websocket.token = safeUsers[0].token
  }
  if (applyTo.has('worker') && loaders.worker) loaders.worker.token = safeUsers[0].token
}

function prepareCollaborationTicketIssuer(
  fetchImpl,
  baseUrl,
  config,
  authenticatedUsers,
  loaders,
  signal,
  onIssued,
) {
  if (!config) return
  if (!loaders.collaboration) {
    throw new Error('runtime bootstrap collaboration requires the collaboration loader')
  }
  if (!Array.isArray(config.rooms) || config.rooms.length === 0) {
    throw new Error('runtime bootstrap collaboration requires at least one room')
  }
  const pathTemplate = config.ticketPath ?? DEFAULT_TICKET_PATH
  const roomPlans = config.rooms.map((room, roomIndex) => {
    if (!room || typeof room !== 'object' || Array.isArray(room)
      || typeof room.spaceId !== 'string' || room.spaceId.length === 0
      || typeof room.itemId !== 'string' || room.itemId.length === 0) {
      throw new Error(`runtime bootstrap collaboration room ${roomIndex} is invalid`)
    }
    const clients = positiveInteger(room.clients, 1)
    const eligible = authenticatedUsers.filter((user) =>
      room.workspaceOrdinal === undefined || user.workspaceOrdinal === room.workspaceOrdinal)
    if (eligible.length === 0) {
      throw new Error(`runtime bootstrap collaboration room ${roomIndex} has no authenticated workspace user`)
    }
    return {
      room,
      clients,
      users: eligible,
      documentName: room.name,
    }
  })
  loaders.collaboration.rooms = roomPlans.map((plan) => ({
    ...plan.room,
    ...(plan.documentName === undefined
      ? { ticketDocumentName: true }
      : { name: plan.documentName }),
    clients: plan.clients,
    users: Array.from({ length: plan.clients }, (_, clientIndex) => ({
      username: plan.users[clientIndex % plan.users.length].username,
    })),
  }))
  loaders.collaboration.ticketIssuer = async (context = {}) => {
    const roomIndex = Number(context.roomIndex)
    const clientIndex = Number(context.clientIndex)
    const plan = roomPlans[roomIndex]
    if (!plan || !Number.isSafeInteger(clientIndex)) {
      throw new Error('runtime bootstrap collaboration ticket context is invalid')
    }
    const user = plan.users[Math.abs(clientIndex) % plan.users.length]
    const path = pathTemplate
      .replaceAll('{spaceId}', encodeURIComponent(plan.room.spaceId))
      .replaceAll('{itemId}', encodeURIComponent(plan.room.itemId))
    const response = await requestJson(fetchImpl, absoluteUrl(baseUrl, path), {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${user.token}`,
        'Content-Type': 'application/json',
      },
      body: '{}',
      signal: context.signal ?? signal,
    }, `collaboration ticket ${roomIndex}:${clientIndex}`)
    if (typeof response.ticket !== 'string' || response.ticket.length === 0
      || typeof response.documentName !== 'string' || response.documentName.length === 0) {
      throw new Error(
        `runtime bootstrap collaboration ticket ${roomIndex}:${clientIndex} is invalid`,
      )
    }
    if (plan.documentName === undefined) {
      plan.documentName = response.documentName
      loaders.collaboration.rooms[roomIndex].name = response.documentName
    } else if (response.documentName !== plan.documentName) {
      throw new Error(
        `runtime bootstrap collaboration ticket ${roomIndex}:${clientIndex} returned an unexpected document name`,
      )
    }
    const collaborationUrl = resolveCollaborationTicketUrl(
      response.url,
      loaders.collaboration.collaborationUrl,
    )
    onIssued()
    return {
      ticket: response.ticket,
      documentName: response.documentName,
      url: collaborationUrl,
      username: user.username,
    }
  }
}

async function requestJson(fetchImpl, url, init, label) {
  let response
  try {
    response = await fetchImpl(url, init)
  } catch (error) {
    throw new Error(`${label} request failed: ${safeErrorCode(error)}`)
  }
  if (!response || response.ok !== true) {
    throw new Error(`${label} request returned HTTP ${response?.status ?? 'unknown'}`)
  }
  try {
    return await response.json()
  } catch {
    throw new Error(`${label} response was not valid JSON`)
  }
}

async function mapConcurrent(items, concurrency, mapper) {
  const results = new Array(items.length)
  let cursor = 0
  const workers = Array.from(
    { length: Math.min(positiveInteger(concurrency, 1), Math.max(items.length, 1)) },
    async () => {
      while (cursor < items.length) {
        const index = cursor
        cursor += 1
        results[index] = await mapper(items[index], index)
      }
    },
  )
  await Promise.all(workers)
  return results
}

function absoluteBaseUrl(value) {
  if (typeof value !== 'string' || value.length === 0) {
    throw new Error('runtime bootstrap baseUrl is required')
  }
  const parsed = new URL(value)
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw new Error('runtime bootstrap baseUrl must use HTTP or HTTPS')
  }
  return parsed.toString().replace(/\/$/, '')
}

function absoluteUrl(baseUrl, value) {
  if (typeof value !== 'string' || value.length === 0) throw new Error('bootstrap target path is required')
  return new URL(value, `${baseUrl}/`).toString()
}

function resolveCollaborationTicketUrl(value, base) {
  if (typeof value !== 'string' || value.length === 0) {
    throw new Error('runtime bootstrap collaboration ticket returned no URL')
  }
  if (typeof base !== 'string' || base.length === 0) {
    throw new Error('runtime bootstrap collaboration loader collaborationUrl is required')
  }
  let baseUrl
  let resolved
  try {
    baseUrl = new URL(base)
    resolved = new URL(value, baseUrl)
  } catch {
    throw new Error('runtime bootstrap collaboration ticket URL is invalid')
  }
  if (!['ws:', 'wss:'].includes(baseUrl.protocol)) {
    throw new Error('runtime bootstrap collaboration loader collaborationUrl must use WS or WSS')
  }
  if (!['ws:', 'wss:'].includes(resolved.protocol)) {
    throw new Error('runtime bootstrap collaboration ticket URL must use WS or WSS')
  }
  return resolved.toString()
}

function createProbeRunIds(configured, probeRunId) {
  const warmup = configured?.warmup ?? randomUUID()
  let measured = configured?.measured ?? randomUUID()
  while (measured === warmup || measured === probeRunId) measured = randomUUID()
  if (warmup === probeRunId) {
    throw new Error('runtime warmup probe run ID must differ from probeRunId')
  }
  return { warmup, measured }
}

function positiveInteger(value, fallback) {
  const number = Number(value)
  return Number.isInteger(number) && number > 0 ? number : fallback
}

function safeErrorCode(error) {
  const code = error?.code ?? error?.name
  return typeof code === 'string' && /^[A-Za-z0-9_.-]{1,64}$/.test(code)
    ? code
    : 'request-error'
}

function assertPlainObject(value, label) {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    throw new TypeError(`${label} must be a plain object`)
  }
  const prototype = Object.getPrototypeOf(value)
  if (prototype !== Object.prototype && prototype !== null) {
    throw new TypeError(`${label} must be a plain object`)
  }
}

function assertOnlyKeys(value, allowed, label) {
  const unsupported = Object.keys(value).filter((key) => !allowed.has(key))
  if (unsupported.length > 0) {
    throw new Error(`${label} contains unsupported fields: ${unsupported.sort().join(', ')}`)
  }
}
