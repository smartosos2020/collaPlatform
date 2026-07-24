import assert from 'node:assert/strict'
import test from 'node:test'

import { CollaborationMetrics } from '../src/metrics.js'

const documentName = 'knowledge:00000000-0000-0000-0000-000000000001:00000000-0000-0000-0000-000000000002'

test('node metrics retain accepted connections and classify stale stores without failure noise', () => {
  const metrics = new CollaborationMetrics('test-node', false)

  metrics.connect(documentName, 'socket-a', 'user-a')
  metrics.disconnect(documentName, 'socket-a')
  metrics.remove(documentName)
  metrics.staleWrite('COLLAB_SNAPSHOT_STALE')
  metrics.staleWrite('COLLAB_GENERATION_STALE')

  const snapshot = metrics.snapshot()
  assert.equal(snapshot.acceptedConnections, 1)
  assert.deepEqual(snapshot.staleWrites, { snapshot: 1, generation: 1 })
  assert.deepEqual(snapshot.failures, { backend: 0, redis: 0, recovery: 0, store: 0 })
})

test('connection and room reservations enforce bounded collaboration capacity', () => {
  const metrics = new CollaborationMetrics('node-capacity', false)
  const first = metrics.reserve(documentName, 1, 1, 30_000)
  assert.throws(
    () => metrics.reserve(documentName, 1, 1, 30_000),
    (error) => error.code === 'COLLAB_CONNECTION_CAPACITY',
  )
  metrics.activateReservation(first, documentName, 'socket-a', 'user-a')
  assert.throws(
    () => metrics.reserve(
      'knowledge:00000000-0000-0000-0000-000000000003:00000000-0000-0000-0000-000000000004',
      2,
      1,
      30_000,
    ),
    (error) => error.code === 'COLLAB_ROOM_CAPACITY',
  )
  const snapshot = metrics.snapshot()
  assert.deepEqual(snapshot.capacityRejections, { connections: 1, rooms: 1 })
  assert.equal(snapshot.reservedConnections, 0)
})
