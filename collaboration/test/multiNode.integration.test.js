import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import test from 'node:test'

import { HocuspocusProvider } from '@hocuspocus/provider'
import WebSocket from 'ws'
import * as Y from 'yjs'

import { createCollaborationServer } from '../src/server.js'

test('two collaboration nodes converge through Redis without duplicate persistence', { timeout: 20_000 }, async (t) => {
  const workspaceId = randomUUID()
  const itemId = randomUUID()
  const documentName = `knowledge:${workspaceId}:${itemId}`
  const gateway = new MemoryGateway()
  const prefix = `colla:test:${randomUUID()}`
  const nodeA = createCollaborationServer({ config: testConfig(12431, 'test-node-a', prefix), gateway })
  const nodeB = createCollaborationServer({ config: testConfig(12432, 'test-node-b', prefix), gateway })
  await nodeA.listen()
  await nodeB.listen()
  t.after(async () => {
    await Promise.allSettled([nodeA.destroy(), nodeB.destroy()])
  })

  await eventually(() => {
    assert.equal(nodeA.collaRuntime.metrics.redisStatus, 'ready')
    assert.equal(nodeB.collaRuntime.metrics.redisStatus, 'ready')
  })

  const left = new Y.Doc()
  const right = new Y.Doc()
  const leftProvider = provider(12431, documentName, left, 'left-ticket')
  const rightProvider = provider(12432, documentName, right, 'right-ticket')
  t.after(() => {
    leftProvider.destroy()
    rightProvider.destroy()
    left.destroy()
    right.destroy()
  })
  await Promise.all([synced(leftProvider), synced(rightProvider)])

  left.getText('title').insert(0, 'left')
  await eventually(() => assert.equal(right.getText('title').toString(), 'left'))
  right.getText('title').insert(right.getText('title').length, '-right')
  await eventually(() => assert.equal(left.getText('title').toString(), 'left-right'))
  await eventually(() => assert.equal(gateway.updateIds.size, 2))

  assert.equal(gateway.appendCalls, 2)
  assert.equal(nodeA.collaRuntime.metrics.snapshot(nodeA.hocuspocus, true).rooms[0].latestSequence > 0, true)
  assert.equal(nodeB.collaRuntime.metrics.snapshot(nodeB.hocuspocus, true).rooms[0].connections, 1)
})

test('a disconnected client receives missing Yjs updates without replacing newer state', { timeout: 20_000 }, async (t) => {
  const documentName = `knowledge:${randomUUID()}:${randomUUID()}`
  const gateway = new MemoryGateway()
  const prefix = `colla:test:${randomUUID()}`
  const nodeA = createCollaborationServer({ config: testConfig(12433, 'test-node-c', prefix), gateway })
  const nodeB = createCollaborationServer({ config: testConfig(12434, 'test-node-d', prefix), gateway })
  await nodeA.listen()
  await nodeB.listen()
  t.after(async () => Promise.allSettled([nodeA.destroy(), nodeB.destroy()]))

  const left = new Y.Doc()
  const right = new Y.Doc()
  const leftProvider = provider(12433, documentName, left, 'left-ticket')
  let rightProvider = provider(12434, documentName, right, 'right-ticket')
  t.after(() => {
    leftProvider.destroy()
    rightProvider.destroy()
    left.destroy()
    right.destroy()
  })
  await Promise.all([synced(leftProvider), synced(rightProvider)])
  left.getText('title').insert(0, 'base')
  await eventually(() => assert.equal(right.getText('title').toString(), 'base'))

  rightProvider.disconnect()
  await new Promise((resolve) => setTimeout(resolve, 100))
  left.getText('title').insert(4, '-remote')
  right.getText('title').insert(4, '-offline')
  rightProvider.connect()
  await synced(rightProvider)

  await eventually(() => {
    assert.equal(left.getText('title').toString(), right.getText('title').toString())
    assert.match(left.getText('title').toString(), /remote/)
    assert.match(left.getText('title').toString(), /offline/)
  })
})

test('Redis degradation is observable and database recovery repairs a missed broadcast', { timeout: 20_000 }, async (t) => {
  const documentName = `knowledge:${randomUUID()}:${randomUUID()}`
  const gateway = new MemoryGateway()
  const prefix = `colla:test:${randomUUID()}`
  const nodeA = createCollaborationServer({ config: testConfig(12435, 'test-node-e', prefix), gateway })
  const nodeB = createCollaborationServer({ config: testConfig(12436, 'test-node-f', prefix), gateway })
  await nodeA.listen()
  await nodeB.listen()
  t.after(async () => Promise.allSettled([nodeA.destroy(), nodeB.destroy()]))

  const left = new Y.Doc()
  const right = new Y.Doc()
  const leftProvider = provider(12435, documentName, left, 'left-ticket')
  const rightProvider = provider(12436, documentName, right, 'right-ticket')
  t.after(() => {
    leftProvider.destroy()
    rightProvider.destroy()
    left.destroy()
    right.destroy()
  })
  await Promise.all([synced(leftProvider), synced(rightProvider)])
  left.getText('title').insert(0, 'before')
  await eventually(() => assert.equal(right.getText('title').toString(), 'before'))

  nodeB.collaRuntime.redisExtension.pub.disconnect(false)
  nodeB.collaRuntime.redisExtension.sub.disconnect(false)
  await eventually(() => assert.equal(nodeB.collaRuntime.metrics.redisStatus, 'degraded'))
  const degraded = await fetch('http://127.0.0.1:12436/ready')
  assert.equal(degraded.status, 503)
  assert.equal((await fetch('http://127.0.0.1:12436/health')).status, 200)
  assert.equal((await fetch('http://127.0.0.1:12436/metrics')).status, 401)
  assert.equal((await fetch('http://127.0.0.1:12436/metrics', {
    headers: { 'x-colla-collaboration-secret': 'test-secret' },
  })).status, 200)

  left.getText('title').insert(left.getText('title').length, '-missed')
  await eventually(() => assert.equal(gateway.updateIds.size >= 2, true))
  await Promise.all([
    nodeB.collaRuntime.redisExtension.pub.connect(),
    nodeB.collaRuntime.redisExtension.sub.connect(),
  ])
  await eventually(() => assert.equal(nodeB.collaRuntime.metrics.redisStatus, 'ready'))
  await nodeB.collaRuntime.recoverAll()
  await eventually(() => assert.equal(right.getText('title').toString(), 'before-missed'))
  assert.equal(nodeB.collaRuntime.metrics.snapshot(nodeB.hocuspocus, true).rooms[0].recoveryCount > 0, true)
})

test('a restarted node reloads the persisted room and accepts the existing client', { timeout: 20_000 }, async (t) => {
  const documentName = `knowledge:${randomUUID()}:${randomUUID()}`
  const gateway = new MemoryGateway()
  const prefix = `colla:test:${randomUUID()}`
  const configA = testConfig(12437, 'test-node-g', prefix)
  const configB = testConfig(12438, 'test-node-h', prefix)
  const nodeA = createCollaborationServer({ config: configA, gateway })
  let nodeB = createCollaborationServer({ config: configB, gateway })
  await nodeA.listen()
  await nodeB.listen()
  t.after(async () => Promise.allSettled([nodeA.destroy(), nodeB.destroy()]))

  const left = new Y.Doc()
  const right = new Y.Doc()
  const leftProvider = provider(12437, documentName, left, 'left-ticket')
  let rightProvider = provider(12438, documentName, right, 'right-ticket')
  t.after(() => {
    leftProvider.destroy()
    rightProvider.destroy()
    left.destroy()
    right.destroy()
  })
  await Promise.all([synced(leftProvider), synced(rightProvider)])
  left.getText('title').insert(0, 'persisted-before-restart')
  await eventually(() => assert.equal(right.getText('title').toString(), 'persisted-before-restart'))
  await new Promise((resolve) => setTimeout(resolve, 300))

  await nodeB.destroy()
  rightProvider.destroy()
  left.getText('title').insert(left.getText('title').length, '-while-away')
  await eventually(() => assert.equal(gateway.updateIds.size >= 2, true))
  nodeB = createCollaborationServer({ config: configB, gateway })
  await nodeB.listen()
  rightProvider = provider(12438, documentName, right, 'right-ticket')
  await synced(rightProvider)
  await eventually(() => assert.equal(right.getText('title').toString(), 'persisted-before-restart-while-away'), 8000)
})

class MemoryGateway {
  constructor() {
    this.sequence = 0
    this.updateIds = new Map()
    this.updates = []
    this.snapshot = ''
    this.appendCalls = 0
  }

  async authenticate(ticket) {
    return {
      userId: ticket, displayName: ticket, color: '#5b5bd6', clientId: ticket,
      canView: true, canEdit: true, expiresAt: new Date(Date.now() + 60_000).toISOString(),
    }
  }

  async load() {
    return {
      title: '', snapshot: this.snapshot, updates: this.updates,
      canonicalDocument: { type: 'doc', schemaVersion: 3, content: [] },
    }
  }

  async appendUpdate(ticket, documentName, update, clientId, updateId) {
    this.appendCalls += 1
    if (!this.updateIds.has(updateId)) {
      this.sequence += 1
      this.updateIds.set(updateId, this.sequence)
      this.updates.push({ sequence: this.sequence, update })
    }
    return { sequence: this.updateIds.get(updateId), updateId, accepted: true }
  }

  async storeSnapshot(ticket, documentName, snapshot) {
    this.snapshot = snapshot
    this.updates = []
    return { snapshotHash: 'test', savedAt: new Date().toISOString() }
  }
}

function provider(port, name, document, token) {
  return new HocuspocusProvider({
    url: `ws://127.0.0.1:${port}`,
    name,
    document,
    token,
    WebSocketPolyfill: WebSocket,
    delay: 50,
    minDelay: 50,
    maxDelay: 200,
    jitter: false,
  })
}

function synced(value) {
  if (value.isSynced) return Promise.resolve()
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('provider sync timeout')), 5000)
    value.on('synced', ({ state }) => {
      if (!state) return
      clearTimeout(timer)
      resolve()
    })
  })
}

async function eventually(assertion, timeout = 5000) {
  const expires = Date.now() + timeout
  let lastError
  while (Date.now() < expires) {
    try {
      assertion()
      return
    } catch (error) {
      lastError = error
      await new Promise((resolve) => setTimeout(resolve, 25))
    }
  }
  throw lastError
}

function testConfig(port, nodeId, prefix) {
  return {
    host: '127.0.0.1', port, nodeId, maxUpdateBytes: 1024 * 1024,
    debounceMs: 50, maxDebounceMs: 200, authorizationCacheMs: 10,
    backendTimeoutMs: 1000, backendRetries: 0, recoveryIntervalMs: 100,
    roomUnloadImmediately: false, internalSecret: 'test-secret',
    redis: {
      enabled: true, host: '127.0.0.1', port: 6379, db: 0, prefix,
      lockTimeoutMs: 1000, initialSyncTimeoutMs: 500,
    },
  }
}
