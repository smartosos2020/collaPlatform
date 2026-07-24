import { randomUUID } from 'node:crypto'

import { parseDocumentName, protocolError } from './protocol.js'

export class CollaborationMetrics {
  constructor(nodeId, redisEnabled = true) {
    this.nodeId = nodeId
    this.startedAt = new Date().toISOString()
    this.redisEnabled = redisEnabled
    this.redisClients = new Map()
    this.rooms = new Map()
    this.acceptedConnections = 0
    this.capacityRejections = { connections: 0, rooms: 0 }
    this.reservations = new Map()
    this.staleWrites = { snapshot: 0, generation: 0 }
    this.authorizationGraceUses = 0
    this.failures = { backend: 0, redis: 0, recovery: 0, store: 0 }
    this.durableQueue = {
      updates: 0, bytes: 0, retryAttempts: 0, recoveredUpdates: 0, backpressureRejections: 0,
    }
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
    this.acceptedConnections += 1
    room.connections.set(socketId, userId)
    room.lastActivityAt = new Date().toISOString()
  }

  reserve(documentName, maxConnections, maxRooms, ttlMs) {
    this.purgeReservations(ttlMs)
    const activeConnections = this.connectionCount()
    if (activeConnections + this.reservations.size >= maxConnections) {
      this.capacityRejections.connections += 1
      throw protocolError('COLLAB_CONNECTION_CAPACITY', 'Collaboration connection capacity reached')
    }
    const reservedRooms = new Set([...this.reservations.values()].map((entry) => entry.documentName))
    if (!this.rooms.has(documentName) && !reservedRooms.has(documentName)
        && this.rooms.size + reservedRooms.size >= maxRooms) {
      this.capacityRejections.rooms += 1
      throw protocolError('COLLAB_ROOM_CAPACITY', 'Collaboration room capacity reached')
    }
    const id = randomUUID()
    this.reservations.set(id, { documentName, createdAt: Date.now() })
    return id
  }

  activateReservation(reservationId, documentName, socketId, userId) {
    this.releaseReservation(reservationId)
    this.connect(documentName, socketId, userId)
  }

  releaseReservation(reservationId) {
    if (reservationId) this.reservations.delete(reservationId)
  }

  purgeReservations(ttlMs) {
    const cutoff = Date.now() - ttlMs
    for (const [id, reservation] of this.reservations) {
      if (reservation.createdAt < cutoff) this.reservations.delete(id)
    }
  }

  connectionCount() {
    return [...this.rooms.values()].reduce((sum, room) => sum + room.connections.size, 0)
  }

  disconnect(documentName, socketId) {
    const room = this.rooms.get(documentName)
    if (!room) return
    room.connections.delete(socketId)
    room.lastActivityAt = new Date().toISOString()
  }

  loaded(documentName, loaded) {
    const room = this.room(documentName)
    const updates = loaded.updates ?? []
    room.generation = Number(loaded.generation) || 0
    room.latestSequence = Math.max(
      Number(loaded.snapshotSequence) || 0,
      ...updates.map((update) => Number(update.sequence) || 0),
    )
    room.pendingUpdates = updates.length
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

  staleWrite(code) {
    const key = code === 'COLLAB_GENERATION_STALE' ? 'generation' : 'snapshot'
    this.staleWrites[key] += 1
  }

  authorizationGrace(documentName, error) {
    this.authorizationGraceUses += 1
    this.lastFailure = {
      kind: 'backend',
      documentName,
      message: error instanceof Error ? error.message : String(error ?? 'Authorization backend unavailable'),
      at: new Date().toISOString(),
    }
  }

  durableQueued(documentName, bytes) {
    this.durableQueue.updates += 1
    this.durableQueue.bytes += bytes
    this.room(documentName).lastActivityAt = new Date().toISOString()
  }

  durableDequeued(documentName, bytes) {
    this.durableQueue.updates = Math.max(0, this.durableQueue.updates - 1)
    this.durableQueue.bytes = Math.max(0, this.durableQueue.bytes - bytes)
    const room = this.room(documentName)
    room.pendingUpdates = Math.max(0, room.pendingUpdates - 1)
  }

  durableRetry(documentName) {
    this.durableQueue.retryAttempts += 1
    this.room(documentName).lastRecoveryAt = new Date().toISOString()
  }

  durableRecovered(documentName) {
    this.durableQueue.recoveredUpdates += 1
    this.room(documentName).recoveryCount += 1
  }

  durableBackpressure(documentName) {
    this.durableQueue.backpressureRejections += 1
    this.room(documentName).lastActivityAt = new Date().toISOString()
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
        generation: room.generation,
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
      acceptedConnections: this.acceptedConnections,
      reservedConnections: this.reservations.size,
      capacityRejections: { ...this.capacityRejections },
      documents: instance?.getDocumentsCount?.() ?? rooms.length,
      staleWrites: { ...this.staleWrites },
      authorizationGraceUses: this.authorizationGraceUses,
      durableQueue: { ...this.durableQueue },
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
        generation: 0,
        lastPersistenceLatencyMs: 0, maxPersistenceLatencyMs: 0,
        storeCount: 0, recoveryCount: 0, lastActivityAt: null, lastLoadAt: null,
        lastStoreAt: null, lastRecoveryAt: null,
      }
      this.rooms.set(documentName, room)
    }
    return room
  }
}
