import assert from 'node:assert/strict'
import { EventEmitter } from 'node:events'
import test from 'node:test'

import { runCollaborationScenario } from '../src/load/collaboration.mjs'

test('sustained collaboration load edits across nodes and passes only after durable observer reload', async () => {
  const network = new DurableCollaborationNetwork()
  const result = await runCollaborationScenario({
    collaborationNodes: collaborationNodeTargets(),
    nodeIdentityResolver: resolveNodeIdentity,
    rooms: [
      { name: 'room-a', clients: 4, weight: 2 },
      { name: 'room-b', clients: 2, weight: 1 },
    ],
    durationMs: 90,
    editsPerSecond: 100,
    connectionConcurrency: 6,
    editConcurrency: 3,
    reconnectsPerRoom: 1,
    syncTimeoutMs: 100,
    convergenceTimeoutMs: 100,
    reloadTimeoutMs: 100,
    pollIntervalMs: 1,
    durableSettleMs: 1,
    dependencies: collaborationDependencies(),
    providerFactory: (configuration, context) => network.provider(configuration, context),
  })

  assert.equal(result.ok, true, JSON.stringify(result.errors))
  assert.ok(result.metrics.edits >= 5, result.metrics.edits)
  assert.deepEqual(result.metrics.editNodes, [
    'collaboration-a',
    'collaboration-b',
  ])
  assert.equal(result.metrics.reconnects, 2)
  assert.equal(result.metrics.durableReloadRequired, true)
  assert.equal(result.metrics.durableReloads, 2)
  assert.equal(result.metrics.durableReloadFailures, 0)
  assert.equal(result.metrics.observerClients, 2)
  assert.equal(result.metrics.durableReloadLatency.count, 2)
  assert.notEqual(result.metrics.durableReloadLatency.p95, null)
  assert.ok(result.metrics.convergenceLatency.count >= 4)
  assert.ok(network.observerConnections.every((entry) => entry.wasRoomEmpty))
  assert.ok(network.reconnects.every((entry) => entry.url !== entry.previousUrl))
  assert.ok(network.allProviders.every((provider) => provider.destroyed))
  assert.ok(network.allDocuments.every((document) => document.destroyed))
})

test('in-memory convergence cannot pass when a fresh observer cannot reload durable state', async () => {
  const network = new VolatileCollaborationNetwork()
  const result = await runCollaborationScenario({
    collaborationNodes: collaborationNodeTargets(),
    nodeIdentityResolver: resolveNodeIdentity,
    rooms: [{ name: 'volatile-room', clients: 2 }],
    durationMs: 60,
    editsPerSecond: 80,
    reconnectsPerRoom: 1,
    syncTimeoutMs: 100,
    convergenceTimeoutMs: 40,
    reloadTimeoutMs: 15,
    pollIntervalMs: 1,
    dependencies: collaborationDependencies(),
    providerFactory: (configuration, context) => network.provider(configuration, context),
  })

  assert.equal(result.ok, false)
  assert.equal(result.metrics.convergenceFailures, 0)
  assert.equal(result.metrics.durableReloads, 0)
  assert.equal(result.metrics.durableReloadFailures, 1)
  assert.ok(result.errors.some((error) => error.code === 'durable_reload_failure'))
  assert.ok(network.observerConnections.every((entry) => entry.wasRoomEmpty))
})

test('initial connections, reconnects and durable observers each receive a fresh one-time ticket', async () => {
  const network = new DurableCollaborationNetwork()
  const issued = []
  const result = await runCollaborationScenario({
    collaborationNodes: collaborationNodeTargets(),
    nodeIdentityResolver: resolveNodeIdentity,
    rooms: [{ name: 'ticket-room', clients: 2, editsPerClient: 1 }],
    reconnectsPerRoom: 1,
    requireDurableReload: true,
    requireCrossNode: true,
    syncTimeoutMs: 100,
    convergenceTimeoutMs: 100,
    reloadTimeoutMs: 100,
    pollIntervalMs: 1,
    dependencies: collaborationDependencies(),
    ticketIssuer: async (context) => {
      const ticket = `ticket-${issued.length + 1}`
      issued.push({ ticket, ...context })
      return { ticket, documentName: context.room.name, url: '/collaboration' }
    },
    providerFactory: (configuration, context) => network.provider(configuration, context),
  })

  assert.equal(result.ok, true, JSON.stringify(result.errors))
  assert.equal(issued.length, 4)
  assert.equal(new Set(issued.map(({ ticket }) => ticket)).size, 4)
  assert.deepEqual(
    Object.fromEntries(['connect', 'reconnect', 'durable-reload'].map((phase) => [
      phase,
      issued.filter((entry) => entry.phase === phase).length,
    ])),
    { connect: 2, reconnect: 1, 'durable-reload': 1 },
  )
  assert.deepEqual(network.tokens.sort(), issued.map(({ ticket }) => ticket).sort())
})

test('fails when a ticket issuer reuses a consumed one-time ticket', async () => {
  const network = new DurableCollaborationNetwork()
  const result = await runCollaborationScenario({
    collaborationUrl: 'ws://collaboration.test',
    rooms: [{ name: 'reused-ticket-room', clients: 2 }],
    reconnectsPerRoom: 0,
    requireDurableReload: false,
    syncTimeoutMs: 50,
    convergenceTimeoutMs: 50,
    pollIntervalMs: 1,
    dependencies: collaborationDependencies(),
    ticketIssuer: async (context) => ({
      ticket: 'same-ticket',
      documentName: context.room.name,
      url: 'ws://collaboration.test',
    }),
    providerFactory: (configuration, context) => network.provider(configuration, context),
  })

  assert.equal(result.ok, false)
  assert.ok(result.errors.some((error) => error.code === 'ticket_reuse'))
})

test('two target URLs do not prove cross-node execution when both report the same identity', async () => {
  const network = new DurableCollaborationNetwork()
  const result = await runCollaborationScenario({
    collaborationNodes: collaborationNodeTargets().map(({ url }) => ({ url })),
    nodeIdentityResolver: async () => ({ nodeId: 'same-node' }),
    rooms: [{ name: 'same-node-room', clients: 2 }],
    durationMs: 50,
    editsPerSecond: 100,
    reconnectsPerRoom: 0,
    requireDurableReload: false,
    requireCrossNode: true,
    syncTimeoutMs: 100,
    convergenceTimeoutMs: 100,
    pollIntervalMs: 1,
    dependencies: collaborationDependencies(),
    providerFactory: (configuration, context) => network.provider(configuration, context),
  })

  assert.equal(result.ok, false)
  assert.deepEqual(result.metrics.editNodes, ['same-node'])
  assert.ok(result.errors.some((error) => error.code === 'cross_node_edit_failure'))
})

test('fails closed when a selected target reports a different server node identity', async () => {
  const network = new DurableCollaborationNetwork()
  const result = await runCollaborationScenario({
    collaborationNodes: [{
      url: 'ws://collaboration-a.test',
      nodeId: 'collaboration-a',
    }],
    nodeIdentityResolver: async () => ({ nodeId: 'collaboration-b' }),
    rooms: [{ name: 'identity-mismatch-room', clients: 1 }],
    reconnectsPerRoom: 0,
    requireDurableReload: false,
    dependencies: collaborationDependencies(),
    providerFactory: (configuration, context) => network.provider(configuration, context),
  })

  assert.equal(result.ok, false)
  assert.ok(result.errors.some((error) => error.code === 'node_identity_failure'))
  assert.equal(network.allProviders.length, 0)
})

test('aborting sustained collaboration destroys active resources and cannot pass', async () => {
  const controller = new AbortController()
  const network = new DurableCollaborationNetwork()
  const running = runCollaborationScenario({
    collaborationNodes: collaborationNodeTargets(),
    nodeIdentityResolver: resolveNodeIdentity,
    rooms: [{ name: 'abort-room', clients: 4 }],
    durationMs: 5_000,
    editsPerSecond: 100,
    connectionConcurrency: 4,
    editConcurrency: 2,
    signal: controller.signal,
    syncTimeoutMs: 100,
    convergenceTimeoutMs: 100,
    dependencies: collaborationDependencies(),
    providerFactory: (configuration, context) => network.provider(configuration, context),
  })
  setTimeout(() => controller.abort('capacity test abort'), 30)
  const result = await running

  assert.equal(result.ok, false)
  assert.equal(result.aborted, true)
  assert.ok(result.errors.some((error) => error.code === 'aborted'))
  assert.ok(network.allProviders.length > 0)
  assert.ok(network.allProviders.every((provider) => provider.destroyed))
  assert.ok(network.allDocuments.every((document) => document.destroyed))
})

function collaborationDependencies() {
  return {
    Provider: class {},
    Y: { Doc: TrackingDoc },
    WebSocket: class {},
  }
}

function collaborationNodeTargets() {
  return [
    { url: 'ws://collaboration-a.test', nodeId: 'collaboration-a' },
    { url: 'ws://collaboration-b.test', nodeId: 'collaboration-b' },
  ]
}

async function resolveNodeIdentity(target) {
  return { nodeId: target.nodeId }
}

class TrackingDoc {
  static instances = []

  constructor() {
    this.value = ''
    this.onEdit = undefined
    this.destroyed = false
    TrackingDoc.instances.push(this)
  }

  getText() {
    return {
      get length() {
        return this.owner.value.length
      },
      owner: this,
      insert: (_index, value) => {
        this.value += value
        this.onEdit?.(this.value)
      },
      toString: () => this.value,
    }
  }

  toJSON() {
    return { content: this.value }
  }

  destroy() {
    this.destroyed = true
  }
}

class DurableCollaborationNetwork {
  rooms = new Map()
  allProviders = []
  allDocuments = []
  observerConnections = []
  reconnects = []
  tokens = []

  provider(configuration, context) {
    const room = this.rooms.get(configuration.name) ?? {
      durable: '',
      documents: new Set(),
    }
    this.rooms.set(configuration.name, room)
    const wasRoomEmpty = room.documents.size === 0
    configuration.document.value = room.durable
    configuration.document.onEdit = (value) => {
      room.durable = value
      for (const document of room.documents) document.value = value
    }
    room.documents.add(configuration.document)
    this.allDocuments.push(configuration.document)

    if (context.observer) {
      this.observerConnections.push({
        room: configuration.name,
        url: configuration.url,
        wasRoomEmpty,
      })
    }
    if (context.phase === 'reconnect') {
      this.reconnects.push({
        room: configuration.name,
        url: configuration.url,
        previousUrl: alternateUrl(configuration.url),
      })
    }

    const provider = new EventEmitter()
    provider.synced = false
    provider.destroyed = false
    provider.connect = () => queueMicrotask(async () => {
      if (provider.destroyed) return
      if (typeof configuration.token === 'function') {
        this.tokens.push(await configuration.token())
      }
      configuration.document.value = room.durable
      provider.synced = true
      provider.emit('synced', { state: true })
    })
    provider.disconnect = () => {
      provider.synced = false
    }
    provider.destroy = () => {
      if (provider.destroyed) return
      provider.destroyed = true
      room.documents.delete(configuration.document)
    }
    this.allProviders.push(provider)
    provider.connect()
    return provider
  }
}

class VolatileCollaborationNetwork extends DurableCollaborationNetwork {
  provider(configuration, context) {
    const provider = super.provider(configuration, context)
    const originalDestroy = provider.destroy
    provider.destroy = () => {
      originalDestroy()
      const room = this.rooms.get(configuration.name)
      if (room?.documents.size === 0) room.durable = ''
    }
    return provider
  }
}

function alternateUrl(url) {
  return url.includes('collaboration-a')
    ? 'ws://collaboration-b.test'
    : 'ws://collaboration-a.test'
}
