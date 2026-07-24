import { pathToFileURL } from 'node:url'

import { Redis as RedisExtension } from '@hocuspocus/extension-redis'
import { Server } from '@hocuspocus/server'
import { TiptapTransformer } from '@hocuspocus/transformer'
import { getSchema } from '@tiptap/core'
import { prosemirrorJSONToYXmlFragment } from 'y-prosemirror'
import * as Y from 'yjs'

import { CollaborationBackendGateway } from './backendGateway.js'
import { collaborationConfig } from './config.js'
import { DurableUpdateExtension } from './durableUpdateExtension.js'
import { CollaborationMetrics } from './metrics.js'
import {
  assertUpdateSize,
  COLLABORATION_PROTOCOL_VERSION,
  COLLABORATION_SCHEMA_VERSION,
  decodeBinary,
  encodeBinary,
  mergeDocumentUpdates,
  parseDocumentName,
  permissionStateMessage,
} from './protocol.js'
import { collaborationExtensions } from './schema.js'

export function createCollaborationServer(options = {}) {
  const config = options.config ?? collaborationConfig
  const metrics = options.metrics ?? new CollaborationMetrics(config.nodeId, config.redis.enabled)
  const gateway = options.gateway ?? new CollaborationBackendGateway(config, (path, error) => metrics.failure('backend', error, path))
  const lastContexts = new Map()
  const authCache = new Map()
  let recoveryTimer

  const redisExtension = config.redis.enabled ? createRedisExtension(config, metrics) : null
  const durableUpdates = new DurableUpdateExtension(gateway, metrics, config.maxUpdateBytes, config)
  const extensions = [durableUpdates]
  if (redisExtension) extensions.push(redisExtension)

  const cacheAuthorization = (ticket, documentName, value) => {
    const now = Date.now()
    const sessionExpiry = Date.parse(value?.expiresAt)
    const configuredGraceEnd = now + (config.authorizationGraceMs ?? 120_000)
    authCache.set(`${ticket}:${documentName}`, {
      value,
      expiresAt: now + config.authorizationCacheMs,
      staleUntil: Number.isFinite(sessionExpiry)
        ? Math.min(configuredGraceEnd, sessionExpiry)
        : configuredGraceEnd,
      retryAfter: 0,
    })
  }

  const authorize = async (ticket, documentName, force = false) => {
    const key = `${ticket}:${documentName}`
    const cached = authCache.get(key)
    const now = Date.now()
    if (!force && cached && cached.expiresAt > now) return cached.value
    if (cached?.retryAfter > now && cached.staleUntil > now) return cached.value
    try {
      const value = await gateway.authorize(ticket, documentName)
      cacheAuthorization(ticket, documentName, value)
      return value
    } catch (error) {
      if (error?.retryable === true && cached?.staleUntil > Date.now()) {
        cached.retryAfter = Date.now() + (config.authorizationRetryMs ?? 5000)
        metrics.authorizationGrace(documentName, error)
        return cached.value
      }
      throw error
    }
  }

  const recoverDocument = async (instance, documentName) => {
    const document = instance.documents.get(documentName)
    const context = lastContexts.get(documentName)
    if (!document || !context?.ticket) return false
    try {
      const loaded = await gateway.load(context.ticket, documentName)
      metrics.loaded(documentName, loaded)
      const update = collaborationLoadUpdate(loaded, documentName)
      if (update.byteLength > 0) {
        Y.applyUpdate(document, update, redisExtension?.redisTransactionOrigin ?? { source: 'local', skipStoreHooks: true })
      }
      metrics.recovered(documentName, loaded.updates?.length ?? 0)
      return true
    } catch (error) {
      metrics.failure('recovery', error, documentName)
      return false
    }
  }

  const recoverAll = async (instance) => {
    await Promise.allSettled([...instance.documents.keys()].map((name) => recoverDocument(instance, name)))
  }

  const server = new Server({
    name: `colla-knowledge-collaboration-${config.nodeId}`,
    port: config.port,
    address: config.host,
    debounce: config.debounceMs,
    maxDebounce: config.maxDebounceMs,
    unloadImmediately: config.roomUnloadImmediately,
    quiet: true,
    extensions,
    websocketOptions: { maxPayload: config.maxUpdateBytes },
    async onConfigure({ instance }) {
      if (redisExtension) {
        observeRedisClient(redisExtension.pub, 'publisher', metrics, () => void recoverAll(instance))
        observeRedisClient(redisExtension.sub, 'subscriber', metrics, () => void recoverAll(instance))
      }
      recoveryTimer = setInterval(() => {
        void durableUpdates.retryPending()
        if (redisExtension && metrics.redisStatus === 'degraded') void recoverAll(instance)
      }, config.recoveryIntervalMs)
      recoveryTimer.unref?.()
    },
    async onAuthenticate(data) {
      parseDocumentName(data.documentName)
      const capacityReservation = metrics.reserve(
        data.documentName, config.maxConnections, config.maxRooms, config.reservationTtlMs,
      )
      try {
        const authorization = await gateway.authenticate(data.token, data.documentName)
        cacheAuthorization(data.token, data.documentName, authorization)
        data.connectionConfig.readOnly = !authorization.canEdit
        const context = {
          ...authorization,
          ticket: data.token,
          capacityReservation,
          protocolVersion: COLLABORATION_PROTOCOL_VERSION,
        }
        lastContexts.set(data.documentName, context)
        return context
      } catch (error) {
        metrics.releaseReservation(capacityReservation)
        throw error
      }
    },
    async onTokenSync(data) {
      const establishedSession = data.token === data.context?.ticket
      const authorization = establishedSession
        ? await authorize(data.token, data.documentName, true)
        : await gateway.authenticate(data.token, data.documentName)
      if (!establishedSession) cacheAuthorization(data.token, data.documentName, authorization)
      data.connection.readOnly = !authorization.canEdit
      data.connection.sendStateless(permissionStateMessage(authorization))
      const context = { ...data.context, ...authorization, ticket: data.token }
      lastContexts.set(data.documentName, context)
      return context
    },
    async connected({ context, documentName, socketId }) {
      lastContexts.set(documentName, context)
      metrics.activateReservation(context.capacityReservation, documentName, socketId, context.userId)
    },
    async beforeHandleMessage({ context, documentName, update, connection }) {
      assertUpdateSize(update, config.maxUpdateBytes)
      const authorization = await authorize(context.ticket, documentName)
      connection.readOnly = !authorization.canEdit
      if (!authorization.canView) throw new Error('COLLAB_FORBIDDEN')
    },
    async beforeHandleAwareness({ context, states }) {
      if (!context) return
      for (const state of states.values()) {
        state.user = { id: context.userId, name: context.displayName, color: context.color }
        delete state.permission
        delete state.content
      }
    },
    async onLoadDocument({ context, documentName }) {
      lastContexts.set(documentName, context)
      const loaded = await gateway.load(context.ticket, documentName)
      metrics.loaded(documentName, loaded)
      return collaborationLoadUpdate(loaded, documentName)
    },
    async onStoreDocument({ instance, document, documentName, lastContext }) {
      if (instance.documents.get(documentName) !== document) {
        // The in-memory document was invalidated (or superseded) after this store was debounced;
        // persisting it would resurrect stale content over the canonical REST state.
        return
      }
      const context = lastContext ?? lastContexts.get(documentName)
      if (!context?.ticket) return
      try {
        const room = metrics.room(documentName)
        const snapshot = Y.encodeStateAsUpdate(document)
        const stateVector = Y.encodeStateVector(document)
        const canonicalDocument = TiptapTransformer.fromYdoc(document, 'default')
        await gateway.storeSnapshot(
          context.ticket,
          documentName,
          encodeBinary(snapshot),
          encodeBinary(stateVector),
          canonicalDocument,
          COLLABORATION_SCHEMA_VERSION,
          context.clientId,
          document.getText('title').toString(),
          config.nodeId,
          room.generation,
          room.latestSequence,
        )
        durableUpdates.clearDocument(documentName)
        metrics.stored(documentName)
      } catch (error) {
        if (error?.code === 'COLLAB_SNAPSHOT_STALE' || error?.code === 'COLLAB_GENERATION_STALE') {
          metrics.staleWrite(error.code)
          await recoverDocument(instance, documentName)
          return
        }
        metrics.failure('store', error, documentName)
        throw error
      }
    },
    async onDisconnect({ documentName, socketId }) {
      metrics.disconnect(documentName, socketId)
    },
    async onRequest({ request, response, instance }) {
      const url = new URL(request.url ?? '/', 'http://collaboration.local')
      if (url.pathname === '/health' || url.pathname === '/ready') {
        const snapshot = metrics.snapshot(instance)
        let backendReady = true
        if (url.pathname === '/ready') {
          try {
            const backend = await gateway.health()
            backendReady = backend?.protocolVersion === COLLABORATION_PROTOCOL_VERSION
              && backend?.schemaVersion === COLLABORATION_SCHEMA_VERSION
              && backend?.persistenceReady === true
          } catch (error) {
            backendReady = false
            metrics.failure('backend', error, 'readiness')
          }
        }
        const ready = url.pathname === '/health' || (snapshot.ready && backendReady)
        response.writeHead(ready ? 200 : 503, { 'content-type': 'application/json' })
        response.end(JSON.stringify({ ...snapshot, backendReady }))
        throw undefined
      }
      if (url.pathname === '/metrics') {
        const supplied = request.headers['x-colla-collaboration-secret']
        if (supplied !== config.internalSecret) {
          response.writeHead(401, { 'content-type': 'application/json' })
          response.end(JSON.stringify({ code: 'COLLAB_INTERNAL_UNAUTHORIZED' }))
        } else {
          response.writeHead(200, { 'content-type': 'application/json' })
          response.end(JSON.stringify(metrics.snapshot(instance, true)))
        }
        throw undefined
      }
      if (url.pathname === '/internal/invalidate') {
        const supplied = request.headers['x-colla-collaboration-secret']
        if (supplied !== config.internalSecret) {
          response.writeHead(401, { 'content-type': 'application/json' })
          response.end(JSON.stringify({ code: 'COLLAB_INTERNAL_UNAUTHORIZED' }))
          throw undefined
        }
        if (request.method !== 'POST') {
          response.writeHead(405, { 'content-type': 'application/json' })
          response.end(JSON.stringify({ code: 'COLLAB_METHOD_NOT_ALLOWED' }))
          throw undefined
        }
        let documentName
        try {
          documentName = JSON.parse(await readRequestBody(request))?.documentName
          parseDocumentName(documentName)
        } catch {
          documentName = null
        }
        if (!documentName) {
          response.writeHead(400, { 'content-type': 'application/json' })
          response.end(JSON.stringify({ code: 'COLLAB_INVALID_DOCUMENT' }))
          throw undefined
        }
        const invalidated = await invalidateDocument(instance, documentName)
        response.writeHead(200, { 'content-type': 'application/json' })
        response.end(JSON.stringify({ invalidated, documentName }))
        throw undefined
      }
    },
    async afterUnloadDocument({ documentName }) {
      lastContexts.delete(documentName)
      metrics.remove(documentName)
      for (const key of authCache.keys()) {
        if (key.endsWith(`:${documentName}`)) authCache.delete(key)
      }
    },
    async onDestroy() {
      if (recoveryTimer) clearInterval(recoveryTimer)
    },
  })

  server.collaRuntime = {
    config, gateway, metrics, redisExtension, durableUpdates, recoverAll: () => recoverAll(server.hocuspocus),
  }
  return server
}

export function collaborationLoadUpdate(loaded, documentName) {
  const pending = (loaded.updates ?? []).map((entry) => decodeBinary(entry.update)).filter((update) => update.byteLength > 0)
  const persisted = decodeBinary(loaded.snapshot)
  if (persisted.byteLength > 0) return mergeDocumentUpdates(persisted, pending)
  const ydoc = canonicalSeedDocument(loaded.canonicalDocument, documentName, loaded.generation)
  const title = ydoc.getText('title')
  if (title.length === 0 && loaded.title) title.insert(0, loaded.title)
  return mergeDocumentUpdates(Y.encodeStateAsUpdate(ydoc), pending)
}

function canonicalSeedDocument(canonicalDocument, documentName, generation) {
  const ydoc = new Y.Doc()
  // The same empty-snapshot document can be loaded concurrently on multiple nodes.
  // A stable client id makes those independently generated canonical updates identical
  // instead of duplicating every initial block when Redis merges them.
  ydoc.clientID = stableCanonicalClientId(documentName, generation)
  prosemirrorJSONToYXmlFragment(
    getSchema(collaborationExtensions),
    canonicalDocument,
    ydoc.getXmlFragment('default'),
  )
  return ydoc
}

function stableCanonicalClientId(documentName, generation) {
  const value = `${documentName ?? 'unknown'}:${Number(generation ?? 0)}:canonical`
  let hash = 2166136261
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index)
    hash = Math.imul(hash, 16777619)
  }
  return (hash >>> 0) || 1
}

async function invalidateDocument(instance, documentName) {
  const document = instance.documents.get(documentName)
  if (!document) return false
  for (const connection of document.getConnections()) {
    try {
      // Close the underlying socket as well: the provider only re-subscribes (and therefore
      // reloads the document fresh from the backend) after a websocket-level reconnect.
      connection.close({ reason: 'Document invalidated' })
      connection.webSocket?.close(1012, 'Document invalidated')
    } catch {
      // The connection may already be closing; invalidation continues regardless.
    }
  }
  instance.documents.delete(documentName)
  document.destroy()
  await instance.hooks('afterUnloadDocument', { instance, documentName })
  return true
}

function readRequestBody(request, limit = 16 * 1024) {
  return new Promise((resolve, reject) => {
    const chunks = []
    let received = 0
    request.on('data', (chunk) => {
      received += chunk.length
      if (received > limit) {
        reject(new Error('request body too large'))
        request.destroy()
        return
      }
      chunks.push(chunk)
    })
    request.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')))
    request.on('error', reject)
  })
}

function createRedisExtension(config, metrics) {
  const options = {
    db: config.redis.db,
    maxRetriesPerRequest: 1,
    enableOfflineQueue: false,
    retryStrategy: (attempt) => Math.min(2000, attempt * 100),
  }
  if (config.redis.password) options.password = config.redis.password
  try {
    const extension = new RedisExtension({
      host: config.redis.host,
      port: config.redis.port,
      options,
      identifier: config.nodeId,
      prefix: config.redis.prefix,
      lockTimeout: config.redis.lockTimeoutMs,
      awaitInitialSyncTimeout: config.redis.initialSyncTimeoutMs,
    })
    for (const hookName of ['onAwarenessUpdate', 'onChange', 'beforeBroadcastStateless']) {
      const publish = extension[hookName]?.bind(extension)
      if (!publish) continue
      extension[hookName] = async (data) => {
        try {
          return await publish(data)
        } catch (error) {
          // Redis carries only transient fanout and awareness. A local edit must
          // still reach the durable update queue while Redis is unavailable.
          metrics.redisState('publisher', 'error', error)
          return undefined
        }
      }
    }
    return extension
  } catch (error) {
    metrics.failure('redis', error)
    throw error
  }
}

function observeRedisClient(client, name, metrics, onReady) {
  metrics.redisState(name, client.status === 'ready' ? 'ready' : 'connecting')
  client.on('ready', () => {
    metrics.redisState(name, 'ready')
    onReady()
  })
  client.on('close', () => metrics.redisState(name, 'closed'))
  client.on('reconnecting', () => metrics.redisState(name, 'reconnecting'))
  client.on('error', (error) => metrics.redisState(name, 'error', error))
}

async function startMain() {
  const server = createCollaborationServer()
  await server.listen()
  const shutdown = async () => {
    await server.destroy()
    process.exit(0)
  }
  process.once('SIGINT', shutdown)
  process.once('SIGTERM', shutdown)
}

const isMain = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href
if (isMain) await startMain()
