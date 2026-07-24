import { collaborationConfig } from './config.js'
import { protocolError } from './protocol.js'

export class CollaborationBackendGateway {
  constructor(config = collaborationConfig, onFailure = () => {}) {
    this.config = config
    this.onFailure = onFailure
    this.backendIndex = 0
  }

  authenticate(ticket, documentName) {
    return this.request('/authenticate', { ticket, documentName })
  }

  authorize(ticket, documentName) {
    return this.request('/authorize', { ticket, documentName })
  }

  health() {
    return this.request('/health', undefined, 'GET')
  }

  load(ticket, documentName) {
    return this.request('/document/load', { ticket, documentName })
  }

  appendUpdate(ticket, documentName, update, clientId, updateId, schemaVersion, generation) {
    return this.request('/document/update', {
      ticket, documentName, update, clientId, updateId, schemaVersion, generation,
    })
  }

  storeSnapshot(
    ticket,
    documentName,
    snapshot,
    stateVector,
    canonicalDocument,
    schemaVersion,
    clientId,
    title,
    nodeId,
    generation,
    snapshotSequence,
  ) {
    return this.request('/document/snapshot', {
      ticket, documentName, snapshot, stateVector, canonicalDocument, schemaVersion, clientId, title, nodeId,
      generation, snapshotSequence,
    })
  }

  async request(path, body, method = 'POST') {
    let lastError
    const backendUrls = this.config.backendUrls?.length
      ? this.config.backendUrls
      : [this.config.backendUrl]
    const maxAttempts = Math.max(backendUrls.length, this.config.backendRetries + 1)
    const startIndex = this.backendIndex % backendUrls.length
    for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
      const backendIndex = (startIndex + attempt) % backendUrls.length
      const backendUrl = backendUrls[backendIndex]
      const controller = new AbortController()
      const timeout = setTimeout(() => controller.abort(), this.config.backendTimeoutMs)
      try {
        const response = await fetch(`${backendUrl}${path}`, {
          method,
          headers: {
            'content-type': 'application/json',
            'x-colla-collaboration-secret': this.config.internalSecret,
          },
          body: body === undefined ? undefined : JSON.stringify(body),
          signal: controller.signal,
        })
        const payload = await response.json().catch(() => ({}))
        if (!response.ok) {
          const error = protocolError(payload.code ?? 'COLLAB_BACKEND_REJECTED', payload.message ?? `Backend rejected collaboration request (${response.status})`)
          error.retryable = response.status === 429 || response.status >= 500
          throw error
        }
        this.backendIndex = backendIndex
        return payload
      } catch (error) {
        lastError = error?.name === 'AbortError'
          ? protocolError('COLLAB_PERSISTENCE_UNAVAILABLE', 'Collaboration backend timed out')
          : error
        const retryable = error?.name === 'AbortError' || error?.retryable === true || error instanceof TypeError
        if (retryable && lastError) lastError.retryable = true
        if (!retryable || attempt + 1 >= maxAttempts) break
        await new Promise((resolve) => setTimeout(resolve, Math.min(1000, 100 * (2 ** attempt))))
      } finally {
        clearTimeout(timeout)
      }
    }
    if (lastError?.retryable !== false) {
      this.onFailure(path, lastError)
    }
    throw lastError
  }
}
