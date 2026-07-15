import { collaborationConfig } from './config.js'
import { protocolError } from './protocol.js'

export class CollaborationBackendGateway {
  constructor(config = collaborationConfig) {
    this.config = config
  }

  authenticate(ticket, documentName) {
    return this.request('/authenticate', { ticket, documentName })
  }

  load(ticket, documentName) {
    return this.request('/document/load', { ticket, documentName })
  }

  appendUpdate(ticket, documentName, update, clientId, updateId, schemaVersion) {
    return this.request('/document/update', { ticket, documentName, update, clientId, updateId, schemaVersion })
  }

  storeSnapshot(ticket, documentName, snapshot, stateVector, canonicalDocument, schemaVersion, clientId, title) {
    return this.request('/document/snapshot', {
      ticket, documentName, snapshot, stateVector, canonicalDocument, schemaVersion, clientId, title,
    })
  }

  async request(path, body) {
    const controller = new AbortController()
    const timeout = setTimeout(() => controller.abort(), 5000)
    try {
      const response = await fetch(`${this.config.backendUrl}${path}`, {
        method: 'POST',
        headers: {
          'content-type': 'application/json',
          'x-colla-collaboration-secret': this.config.internalSecret,
        },
        body: JSON.stringify(body),
        signal: controller.signal,
      })
      const payload = await response.json().catch(() => ({}))
      if (!response.ok) {
        throw protocolError(payload.code ?? 'COLLAB_BACKEND_REJECTED', payload.message ?? `Backend rejected collaboration request (${response.status})`)
      }
      return payload
    } catch (error) {
      if (error?.name === 'AbortError') {
        throw protocolError('COLLAB_PERSISTENCE_UNAVAILABLE', 'Collaboration backend timed out')
      }
      throw error
    } finally {
      clearTimeout(timeout)
    }
  }
}
