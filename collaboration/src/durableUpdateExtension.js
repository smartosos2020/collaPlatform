import { isTransactionOrigin } from '@hocuspocus/server'

import { assertUpdateSize, COLLABORATION_SCHEMA_VERSION, encodeBinary, updateHash } from './protocol.js'

export class DurableUpdateExtension {
  priority = 2000

  constructor(gateway, metrics, maxUpdateBytes, options = {}) {
    this.gateway = gateway
    this.metrics = metrics
    this.maxUpdateBytes = maxUpdateBytes
    this.maxPendingUpdates = options.maxPendingUpdates ?? 1024
    this.maxPendingBytes = options.maxPendingBytes ?? 32 * 1024 * 1024
    this.retryBatchSize = options.retryBatchSize ?? 64
    this.pending = new Map()
    this.pendingBytes = 0
    this.retrying = false
  }

  async onChange({ context, documentName, update, transactionOrigin }) {
    if (isTransactionOrigin(transactionOrigin) && transactionOrigin.source === 'redis') return
    if (!context?.ticket || !(update instanceof Uint8Array) || update.byteLength === 0) return
    assertUpdateSize(update, this.maxUpdateBytes)
    const startedAt = performance.now()
    const room = this.metrics.room(documentName)
    const entry = {
      ticket: context.ticket,
      documentName,
      update: encodeBinary(update),
      clientId: context.clientId,
      updateId: updateHash(update),
      schemaVersion: COLLABORATION_SCHEMA_VERSION,
      generation: room.generation,
      bytes: update.byteLength,
    }
    try {
      const ack = await this.append(entry)
      this.metrics.update(documentName, ack.sequence, Math.round(performance.now() - startedAt))
    } catch (error) {
      if (error?.code === 'COLLAB_SNAPSHOT_STALE' || error?.code === 'COLLAB_GENERATION_STALE') {
        this.metrics.staleWrite(error.code)
        return
      }
      if (error?.retryable === false) throw error
      this.metrics.failure('backend', error, documentName)
      if (!this.enqueue(entry)) {
        const capacityError = new Error('Durable collaboration retry queue capacity exceeded')
        capacityError.code = 'COLLAB_PERSISTENCE_BACKPRESSURE'
        capacityError.retryable = true
        this.metrics.durableBackpressure(documentName)
        throw capacityError
      }
      room.pendingUpdates += 1
    }
  }

  async retryPending() {
    if (this.retrying || this.pending.size === 0) return
    this.retrying = true
    try {
      const batch = [...this.pending.values()].slice(0, this.retryBatchSize)
      for (const entry of batch) {
        this.metrics.durableRetry(entry.documentName)
        try {
          const ack = await this.append(entry)
          this.remove(entry)
          this.metrics.update(entry.documentName, ack.sequence)
          this.metrics.durableRecovered(entry.documentName)
        } catch (error) {
          if (error?.code === 'COLLAB_SNAPSHOT_STALE' || error?.code === 'COLLAB_GENERATION_STALE') {
            this.remove(entry)
            this.metrics.staleWrite(error.code)
            continue
          }
          if (error?.retryable === false) {
            this.remove(entry)
            this.metrics.failure('recovery', error, entry.documentName)
          }
        }
      }
    } finally {
      this.retrying = false
    }
  }

  clearDocument(documentName) {
    for (const entry of [...this.pending.values()]) {
      if (entry.documentName === documentName) this.remove(entry)
    }
  }

  snapshot() {
    return { updates: this.pending.size, bytes: this.pendingBytes }
  }

  append(entry) {
    return this.gateway.appendUpdate(
      entry.ticket,
      entry.documentName,
      entry.update,
      entry.clientId,
      entry.updateId,
      entry.schemaVersion,
      entry.generation,
    )
  }

  enqueue(entry) {
    const key = `${entry.documentName}:${entry.generation}:${entry.updateId}`
    if (this.pending.has(key)) return true
    if (this.pending.size >= this.maxPendingUpdates || this.pendingBytes + entry.bytes > this.maxPendingBytes) {
      return false
    }
    entry.key = key
    this.pending.set(key, entry)
    this.pendingBytes += entry.bytes
    this.metrics.durableQueued(entry.documentName, entry.bytes)
    return true
  }

  remove(entry) {
    if (!this.pending.delete(entry.key)) return
    this.pendingBytes = Math.max(0, this.pendingBytes - entry.bytes)
    this.metrics.durableDequeued(entry.documentName, entry.bytes)
  }
}
