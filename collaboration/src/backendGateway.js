import { collaborationConfig } from './config.js'
import { protocolError } from './protocol.js'

export class CollaborationBackendGateway {
  constructor(config = collaborationConfig, onFailure = () => {}) {
    this.config = config
    this.onFailure = onFailure
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

  storeSnapshot(ticket, documentName, snapshot, stateVector, canonicalDocument, schemaVersion, clientId, title, nodeId) {
    return this.request('/document/snapshot', {
      ticket, documentName, snapshot, stateVector, canonicalDocument, schemaVersion, clientId, title, nodeId,
    })
  }

  async request(path, body) {
    let lastError
    for (let attempt = 0; attempt <= this.config.backendRetries; attempt += 1) {
      const controller = new AbortController()
      const timeout = setTimeout(() => controller.abort(), this.config.backendTimeoutMs)
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
          const error = protocolError(payload.code ?? 'COLLAB_BACKEND_REJECTED', payload.message ?? `Backend rejected collaboration request (${response.status})`)
          error.retryable = response.status === 429 || response.status >= 500
          throw error
        }
        return payload
      } catch (error) {
        lastError = error?.name === 'AbortError'
          ? protocolError('COLLAB_PERSISTENCE_UNAVAILABLE', 'Collaboration backend timed out')
          : error
        const retryable = error?.name === 'AbortError' || error?.retryable === true || error instanceof TypeError
        if (!retryable || attempt >= this.config.backendRetries) break
        await new Promise((resolve) => setTimeout(resolve, Math.min(1000, 100 * (2 ** attempt))))
      } finally {
        clearTimeout(timeout)
      }
    }
    this.onFailure(path, lastError)
    throw lastError
  }
}
