import { pathToFileURL } from 'node:url'

import { Redis as RedisExtension } from '@hocuspocus/extension-redis'
import { Server } from '@hocuspocus/server'
import { TiptapTransformer } from '@hocuspocus/transformer'
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
  const extensions = [new DurableUpdateExtension(gateway, metrics, config.maxUpdateBytes)]
  if (redisExtension) extensions.push(redisExtension)

  const authorize = async (ticket, documentName, force = false) => {
    const key = `${ticket}:${documentName}`
    const cached = authCache.get(key)
    if (!force && cached && cached.expiresAt > Date.now()) return cached.value
    const value = await gateway.authenticate(ticket, documentName)
    authCache.set(key, { value, expiresAt: Date.now() + config.authorizationCacheMs })
    return value
  }

  const recoverDocument = async (instance, documentName) => {
    const document = instance.documents.get(documentName)
    const context = lastContexts.get(documentName)
    if (!document || !context?.ticket) return false
    try {
      const loaded = await gateway.load(context.ticket, documentName)
      const update = collaborationLoadUpdate(loaded)
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
        recoveryTimer = setInterval(() => {
          if (metrics.redisStatus === 'degraded') void recoverAll(instance)
        }, config.recoveryIntervalMs)
        recoveryTimer.unref?.()
      }
    },
    async onAuthenticate(data) {
      parseDocumentName(data.documentName)
      const authorization = await authorize(data.token, data.documentName, true)
      data.connectionConfig.readOnly = !authorization.canEdit
      const context = { ...authorization, ticket: data.token, protocolVersion: COLLABORATION_PROTOCOL_VERSION }
      lastContexts.set(data.documentName, context)
      return context
    },
    async onTokenSync(data) {
      const authorization = await authorize(data.token, data.documentName, true)
      data.connection.readOnly = !authorization.canEdit
      data.connection.sendStateless(permissionStateMessage(authorization))
      const context = { ...data.context, ...authorization, ticket: data.token }
      lastContexts.set(data.documentName, context)
      return context
    },
    async connected({ context, documentName, socketId }) {
      lastContexts.set(documentName, context)
      metrics.connect(documentName, socketId, context.userId)
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
      metrics.loaded(documentName, loaded.updates?.length ?? 0)
      return collaborationLoadUpdate(loaded)
    },
    async onStoreDocument({ document, documentName, lastContext }) {
      const context = lastContext ?? lastContexts.get(documentName)
      if (!context?.ticket) return
      try {
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
        )
        metrics.stored(documentName)
      } catch (error) {
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
        const ready = url.pathname === '/health' || snapshot.ready
        response.writeHead(ready ? 200 : 503, { 'content-type': 'application/json' })
        response.end(JSON.stringify(snapshot))
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

  server.collaRuntime = { config, gateway, metrics, redisExtension, recoverAll: () => recoverAll(server.hocuspocus) }
  return server
}

export function collaborationLoadUpdate(loaded) {
  const pending = (loaded.updates ?? []).map((entry) => decodeBinary(entry.update)).filter((update) => update.byteLength > 0)
  const persisted = decodeBinary(loaded.snapshot)
  if (persisted.byteLength > 0) return mergeDocumentUpdates(persisted, pending)
  const ydoc = TiptapTransformer.toYdoc(loaded.canonicalDocument, 'default', collaborationExtensions)
  const title = ydoc.getText('title')
  if (title.length === 0 && loaded.title) title.insert(0, loaded.title)
  return mergeDocumentUpdates(Y.encodeStateAsUpdate(ydoc), pending)
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
    return new RedisExtension({
      host: config.redis.host,
      port: config.redis.port,
      options,
      identifier: config.nodeId,
      prefix: config.redis.prefix,
      lockTimeout: config.redis.lockTimeoutMs,
      awaitInitialSyncTimeout: config.redis.initialSyncTimeoutMs,
    })
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
