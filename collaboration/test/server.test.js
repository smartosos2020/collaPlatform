import assert from 'node:assert/strict'
import test from 'node:test'

import { TiptapTransformer } from '@hocuspocus/transformer'
import * as Y from 'yjs'

import { collaborationConfig } from '../src/config.js'
import { DurableUpdateExtension } from '../src/durableUpdateExtension.js'
import { collaborationLoadUpdate, createCollaborationServer } from '../src/server.js'

const documentName = 'knowledge:00000000-0000-0000-0000-000000000001:00000000-0000-0000-0000-000000000002'

test('concurrent canonical loads from two nodes merge without duplicating initial content', () => {
  const loaded = {
    title: 'Stable title',
    generation: 4,
    snapshot: '',
    updates: [],
    canonicalDocument: {
      type: 'doc',
      content: [{
        type: 'paragraph',
        content: [{ type: 'text', text: 'Stable body' }],
      }],
    },
  }

  const firstNodeUpdate = collaborationLoadUpdate(loaded, documentName)
  const secondNodeUpdate = collaborationLoadUpdate(loaded, documentName)
  const merged = new Y.Doc()
  Y.applyUpdate(merged, firstNodeUpdate)
  Y.applyUpdate(merged, secondNodeUpdate)

  assert.equal(merged.getText('title').toString(), 'Stable title')
  assert.equal(TiptapTransformer.fromYdoc(merged, 'default').content.length, 1)
  assert.equal(TiptapTransformer.fromYdoc(merged, 'default').content[0].content[0].text, 'Stable body')
})

test('a transient durable update failure is observable without rejecting the room change hook', async () => {
  const failures = []
  const queue = []
  let available = false
  const metrics = {
    room: () => ({ generation: 2, pendingUpdates: 0 }),
    update: () => {},
    failure: (kind, error, name) => failures.push({ kind, error, name }),
    staleWrite: () => {},
    durableQueued: (name, bytes) => queue.push({ action: 'queued', name, bytes }),
    durableDequeued: (name, bytes) => queue.push({ action: 'dequeued', name, bytes }),
    durableRetry: (name) => queue.push({ action: 'retry', name }),
    durableRecovered: (name) => queue.push({ action: 'recovered', name }),
    durableBackpressure: () => assert.fail('bounded queue should have capacity'),
  }
  const extension = new DurableUpdateExtension({
    appendUpdate: async () => {
      if (!available) {
        const error = new Error('backend unavailable')
        error.retryable = true
        throw error
      }
      return { sequence: 8 }
    },
  }, metrics, 1024)

  await extension.onChange({
    context: { ticket: 'ticket', clientId: 'client' },
    documentName,
    update: Y.encodeStateAsUpdate(new Y.Doc()),
    transactionOrigin: { source: 'local' },
  })

  assert.equal(failures.length, 1)
  assert.equal(failures[0].kind, 'backend')
  assert.equal(failures[0].name, documentName)
  assert.deepEqual(extension.snapshot(), { updates: 1, bytes: 2 })

  available = true
  await extension.retryPending()

  assert.deepEqual(extension.snapshot(), { updates: 0, bytes: 0 })
  assert.deepEqual(queue.map((entry) => entry.action), ['queued', 'retry', 'dequeued', 'recovered'])
})

test('token sync authorizes the established session and consumes only a rotated ticket', async (t) => {
  const calls = []
  let authorizationFailure = null
  const authorization = (canEdit) => ({
    userId: '00000000-0000-0000-0000-000000000003',
    displayName: 'Collaborator',
    clientId: 'test-client',
    canView: true,
    canEdit,
  })
  const gateway = {
    authorize: async (ticket, name) => {
      calls.push({ operation: 'authorize', ticket, name })
      if (authorizationFailure) throw authorizationFailure
      return authorization(false)
    },
    authenticate: async (ticket, name) => {
      calls.push({ operation: 'authenticate', ticket, name })
      return authorization(true)
    },
  }
  const server = createCollaborationServer({
    config: {
      ...collaborationConfig,
      nodeId: 'token-sync-test',
      recoveryIntervalMs: 60_000,
      authorizationRetryMs: 1,
      redis: { ...collaborationConfig.redis, enabled: false },
    },
    gateway,
  })
  t.after(async () => server.hocuspocus.configuration.onDestroy())
  const sent = []
  const connection = {
    readOnly: false,
    sendStateless: (payload) => sent.push(JSON.parse(payload)),
  }
  const tokenSync = server.hocuspocus.configuration.onTokenSync

  const refreshed = await tokenSync({
    token: 'established-ticket',
    documentName,
    context: { ticket: 'established-ticket' },
    connection,
  })
  const rotated = await tokenSync({
    token: 'rotated-ticket',
    documentName,
    context: refreshed,
    connection,
  })
  authorizationFailure = Object.assign(new Error('database unavailable'), { retryable: true })
  const tolerated = await tokenSync({
    token: 'rotated-ticket',
    documentName,
    context: rotated,
    connection,
  })
  const throttled = await tokenSync({
    token: 'rotated-ticket',
    documentName,
    context: tolerated,
    connection,
  })
  authorizationFailure = Object.assign(new Error('permission revoked'), { retryable: false })
  await new Promise((resolve) => setTimeout(resolve, 5))
  await assert.rejects(
    () => tokenSync({
      token: 'rotated-ticket',
      documentName,
      context: tolerated,
      connection,
    }),
    /permission revoked/,
  )

  assert.deepEqual(calls, [
    { operation: 'authorize', ticket: 'established-ticket', name: documentName },
    { operation: 'authenticate', ticket: 'rotated-ticket', name: documentName },
    { operation: 'authorize', ticket: 'rotated-ticket', name: documentName },
    { operation: 'authorize', ticket: 'rotated-ticket', name: documentName },
  ])
  assert.equal(refreshed.ticket, 'established-ticket')
  assert.equal(refreshed.canEdit, false)
  assert.equal(rotated.ticket, 'rotated-ticket')
  assert.equal(rotated.canEdit, true)
  assert.equal(tolerated.ticket, 'rotated-ticket')
  assert.equal(tolerated.canEdit, true)
  assert.equal(throttled.ticket, 'rotated-ticket')
  assert.equal(throttled.canEdit, true)
  assert.equal(connection.readOnly, false)
  assert.deepEqual(sent.map(({ canEdit }) => canEdit), [false, true, true, true])
  assert.equal(server.collaRuntime.metrics.snapshot().authorizationGraceUses, 1)
})
