import { Server } from '@hocuspocus/server'
import { TiptapTransformer } from '@hocuspocus/transformer'
import * as Y from 'yjs'

import { CollaborationBackendGateway } from './backendGateway.js'
import { collaborationConfig } from './config.js'
import {
  assertUpdateSize,
  COLLABORATION_PROTOCOL_VERSION,
  COLLABORATION_SCHEMA_VERSION,
  decodeBinary,
  encodeBinary,
  mergeDocumentUpdates,
  parseDocumentName,
  permissionStateMessage,
  updateHash,
} from './protocol.js'
import { collaborationExtensions } from './schema.js'

export function createCollaborationServer({ config = collaborationConfig, gateway = new CollaborationBackendGateway(config) } = {}) {
  const lastContexts = new Map()
  const authCache = new Map()

  const authorize = async (ticket, documentName, force = false) => {
    const key = `${ticket}:${documentName}`
    const cached = authCache.get(key)
    if (!force && cached && cached.expiresAt > Date.now()) {
      return cached.value
    }
    const value = await gateway.authenticate(ticket, documentName)
    authCache.set(key, { value, expiresAt: Date.now() + config.authorizationCacheMs })
    return value
  }

  return new Server({
    name: 'colla-knowledge-collaboration',
    port: config.port,
    address: config.host,
    debounce: config.debounceMs,
    maxDebounce: config.maxDebounceMs,
    quiet: true,
    websocketOptions: { maxPayload: config.maxUpdateBytes },
    async onAuthenticate(data) {
      parseDocumentName(data.documentName)
      const authorization = await authorize(data.token, data.documentName, true)
      data.connectionConfig.readOnly = !authorization.canEdit
      return { ...authorization, ticket: data.token, protocolVersion: COLLABORATION_PROTOCOL_VERSION }
    },
    async onTokenSync(data) {
      const authorization = await authorize(data.token, data.documentName, true)
      data.connection.readOnly = !authorization.canEdit
      data.connection.sendStateless(permissionStateMessage(authorization))
      return { ...data.context, ...authorization, ticket: data.token }
    },
    async beforeHandleMessage({ context, documentName, update, connection }) {
      assertUpdateSize(update, config.maxUpdateBytes)
      const authorization = await authorize(context.ticket, documentName)
      connection.readOnly = !authorization.canEdit
      if (!authorization.canView) {
        throw new Error('COLLAB_FORBIDDEN')
      }
    },
    async beforeHandleAwareness({ context, states }) {
      for (const state of states.values()) {
        state.user = {
          id: context.userId,
          name: context.displayName,
          color: context.color,
        }
        delete state.permission
        delete state.content
      }
    },
    async onLoadDocument({ context, documentName }) {
      const loaded = await gateway.load(context.ticket, documentName)
      const persisted = decodeBinary(loaded.snapshot)
      if (persisted.byteLength > 0) {
        const pending = (loaded.updates ?? []).map((entry) => decodeBinary(entry.update)).filter((update) => update.byteLength > 0)
        return mergeDocumentUpdates(persisted, pending)
      }
      const ydoc = TiptapTransformer.toYdoc(loaded.canonicalDocument, 'default', collaborationExtensions)
      const title = ydoc.getText('title')
      if (title.length === 0 && loaded.title) {
        title.insert(0, loaded.title)
      }
      const initial = Y.encodeStateAsUpdate(ydoc)
      const pending = (loaded.updates ?? []).map((entry) => decodeBinary(entry.update)).filter((update) => update.byteLength > 0)
      return mergeDocumentUpdates(initial, pending)
    },
    async onChange({ context, documentName, update }) {
      if (!context?.ticket || !(update instanceof Uint8Array) || update.byteLength === 0) {
        return
      }
      assertUpdateSize(update, config.maxUpdateBytes)
      lastContexts.set(documentName, context)
      await gateway.appendUpdate(
        context.ticket,
        documentName,
        encodeBinary(update),
        context.clientId,
        updateHash(update),
        COLLABORATION_SCHEMA_VERSION,
      )
    },
    async onStoreDocument({ document, documentName, lastContext }) {
      const context = lastContext ?? lastContexts.get(documentName)
      if (!context?.ticket) {
        return
      }
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
      )
    },
    async onRequest({ request, response }) {
      const url = new URL(request.url ?? '/', 'http://collaboration.local')
      if (url.pathname === '/health') {
        response.writeHead(200, { 'content-type': 'application/json' })
        response.end(JSON.stringify({ status: 'UP', protocolVersion: COLLABORATION_PROTOCOL_VERSION }))
        throw undefined
      }
    },
    async afterUnloadDocument({ documentName }) {
      lastContexts.delete(documentName)
      for (const key of authCache.keys()) {
        if (key.endsWith(`:${documentName}`)) authCache.delete(key)
      }
    },
  })
}

const server = createCollaborationServer()
await server.listen()

const shutdown = async () => {
  await server.destroy()
  process.exit(0)
}
process.once('SIGINT', shutdown)
process.once('SIGTERM', shutdown)
