import { createHash } from 'node:crypto'
import * as Y from 'yjs'

const DOCUMENT_NAME = /^knowledge:([0-9a-f-]{36}):([0-9a-f-]{36})$/i

export const COLLABORATION_PROTOCOL_VERSION = 'colla-yjs-v1'
export const COLLABORATION_SCHEMA_VERSION = 3

export function parseDocumentName(documentName) {
  const match = DOCUMENT_NAME.exec(documentName ?? '')
  if (!match) {
    throw protocolError('COLLAB_INVALID_DOCUMENT', 'Invalid collaboration document name')
  }
  return { workspaceId: match[1].toLowerCase(), itemId: match[2].toLowerCase() }
}

export function encodeBinary(value) {
  return Buffer.from(value).toString('base64')
}

export function decodeBinary(value) {
  return value ? new Uint8Array(Buffer.from(value, 'base64')) : new Uint8Array()
}

export function updateHash(update) {
  return createHash('sha256').update(update).digest('hex')
}

export function assertUpdateSize(update, maxBytes) {
  if (!(update instanceof Uint8Array) || update.byteLength === 0) {
    throw protocolError('COLLAB_INVALID_UPDATE', 'Collaboration update is empty')
  }
  if (update.byteLength > maxBytes) {
    throw protocolError('COLLAB_UPDATE_TOO_LARGE', `Collaboration update exceeds ${maxBytes} bytes`)
  }
}

export function mergeDocumentUpdates(baseUpdate, pendingUpdates = []) {
  const updates = [baseUpdate, ...pendingUpdates].filter((update) => update instanceof Uint8Array && update.byteLength > 0)
  return updates.length === 0 ? new Uint8Array() : Y.mergeUpdates(updates)
}

export function permissionStateMessage(authorization) {
  return JSON.stringify({
    type: 'permission',
    protocolVersion: COLLABORATION_PROTOCOL_VERSION,
    canView: Boolean(authorization.canView),
    canEdit: Boolean(authorization.canEdit),
    expiresAt: authorization.expiresAt ?? null,
  })
}

export function protocolError(code, message) {
  const error = new Error(message)
  error.code = code
  return error
}
