import { parseDocumentName } from './protocol.js'

export class CollaborationMetrics {
  constructor(nodeId, redisEnabled = true) {
    this.nodeId = nodeId
    this.startedAt = new Date().toISOString()
    this.redisEnabled = redisEnabled
    this.redisClients = new Map()
    this.rooms = new Map()
    this.failures = { backend: 0, redis: 0, recovery: 0, store: 0 }
    this.lastFailure = null
  }

  redisState(client, state, error) {
    this.redisClients.set(client, state)
    if (error) this.failure('redis', error)
  }

  get redisStatus() {
    if (!this.redisEnabled) return 'disabled'
    return this.redisClients.size >= 2 && [...this.redisClients.values()].every((value) => value === 'ready') ? 'ready' : 'degraded'
  }

  connect(documentName, socketId, userId) {
    const room = this.room(documentName)
    room.connections.set(socketId, userId)
    room.lastActivityAt = new Date().toISOString()
  }

  disconnect(documentName, socketId) {
    const room = this.rooms.get(documentName)
    if (!room) return
    room.connections.delete(socketId)
    room.lastActivityAt = new Date().toISOString()
  }

  loaded(documentName, pendingUpdates) {
    const room = this.room(documentName)
    room.pendingUpdates = pendingUpdates
    room.lastLoadAt = new Date().toISOString()
  }

  update(documentName, sequence, persistenceLatencyMs = 0) {
    const room = this.room(documentName)
    room.updateCount += 1
    room.latestSequence = Math.max(room.latestSequence, Number(sequence) || 0)
    room.pendingUpdates += 1
    room.lastPersistenceLatencyMs = persistenceLatencyMs
    room.maxPersistenceLatencyMs = Math.max(room.maxPersistenceLatencyMs, persistenceLatencyMs)
    room.lastActivityAt = new Date().toISOString()
  }

  stored(documentName) {
    const room = this.room(documentName)
    room.storeCount += 1
    room.pendingUpdates = 0
    room.lastStoreAt = new Date().toISOString()
  }

  recovered(documentName, pendingUpdates) {
    const room = this.room(documentName)
    room.recoveryCount += 1
    room.pendingUpdates = pendingUpdates
    room.lastRecoveryAt = new Date().toISOString()
  }

  failure(kind, error, documentName) {
    const key = Object.hasOwn(this.failures, kind) ? kind : 'backend'
    this.failures[key] += 1
    this.lastFailure = {
      kind: key,
      documentName: documentName ?? null,
      message: error instanceof Error ? error.message : String(error ?? 'Unknown failure'),
      at: new Date().toISOString(),
    }
  }

  snapshot(instance, detailed = false) {
    const redisStatus = this.redisStatus
    const rooms = [...this.rooms.entries()].map(([documentName, room]) => {
      const key = parseDocumentName(documentName)
      return {
        workspaceId: key.workspaceId,
        itemId: key.itemId,
        connections: room.connections.size,
        updateCount: room.updateCount,
        latestSequence: room.latestSequence,
        lastPersistenceLatencyMs: room.lastPersistenceLatencyMs,
        maxPersistenceLatencyMs: room.maxPersistenceLatencyMs,
        pendingUpdates: room.pendingUpdates,
        storeCount: room.storeCount,
        recoveryCount: room.recoveryCount,
        lastActivityAt: room.lastActivityAt,
        lastLoadAt: room.lastLoadAt,
        lastStoreAt: room.lastStoreAt,
        lastRecoveryAt: room.lastRecoveryAt,
      }
    })
    const result = {
      status: redisStatus === 'degraded' ? 'DEGRADED' : 'UP',
      ready: redisStatus !== 'degraded',
      protocolVersion: 'colla-yjs-v1',
      nodeId: this.nodeId,
      startedAt: this.startedAt,
      redisStatus,
      connections: instance?.getConnectionsCount?.() ?? rooms.reduce((sum, room) => sum + room.connections, 0),
      documents: instance?.getDocumentsCount?.() ?? rooms.length,
      failures: { ...this.failures },
      lastFailure: this.lastFailure,
    }
    return detailed ? { ...result, rooms } : result
  }

  remove(documentName) {
    this.rooms.delete(documentName)
  }

  room(documentName) {
    let room = this.rooms.get(documentName)
    if (!room) {
      room = {
        connections: new Map(), updateCount: 0, latestSequence: 0, pendingUpdates: 0,
        lastPersistenceLatencyMs: 0, maxPersistenceLatencyMs: 0,
        storeCount: 0, recoveryCount: 0, lastActivityAt: null, lastLoadAt: null,
        lastStoreAt: null, lastRecoveryAt: null,
      }
      this.rooms.set(documentName, room)
    }
    return room
  }
}
