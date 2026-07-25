import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

import { bootstrapCapacityRuntime } from '../src/bootstrap.mjs'
import { resolveRuntimeEnvironment } from '../src/runtime.mjs'

const runtimePath = new URL('../config/runtime/s05-m1.v1.json', import.meta.url)

test('checked-in M1 runtime resolves only environment credentials and materializes named fixtures', async () => {
  const source = await readFile(runtimePath, 'utf8')
  const template = JSON.parse(source)
  const environment = {
    COLLA_CAPACITY_BASE_URL: 'http://capacity.test',
    COLLA_CAPACITY_ADMIN_PASSWORD: 'runtime-password',
    COLLA_CAPACITY_PROBE_SECRET: 'runtime-probe-secret',
    COLLA_CAPACITY_WS_URL: 'ws://gateway.test/ws/events',
    COLLA_CAPACITY_COLLABORATION_URL: 'ws://collaboration.test/collaboration',
  }
  const runtime = resolveRuntimeEnvironment(template, environment)
  let tickets = 0
  const result = await bootstrapCapacityRuntime(runtime, {
    probeRunId: '11111111-1111-1111-1111-111111111111',
    fetch: async (url, init) => {
      if (url.endsWith('/api/auth/login')) {
        const body = JSON.parse(init.body)
        assert.equal(body.password, environment.COLLA_CAPACITY_ADMIN_PASSWORD)
        return jsonResponse({ accessToken: `jwt-${body.username}` })
      }
      tickets += 1
      const itemId = url.split('/items/')[1].split('/')[0]
      return jsonResponse({
        ticket: `ticket-${tickets}`,
        documentName: `knowledge:${itemId}`,
        url: tickets % 2 === 0
          ? 'ws://collaboration-b.test'
          : 'ws://collaboration-a.test',
      })
    },
  })

  assert.equal(result.summary.authenticatedUsers, 5)
  assert.equal(result.summary.collaborationTickets, 0)
  assert.equal(result.loaders.http.users.length, 5)
  assert.equal(result.loaders.websocket.users.length, 5)
  assert.equal(result.loaders.collaboration.rooms.length, 4)
  assert.deepEqual(
    result.loaders.collaboration.rooms.map((room) => room.knowledgeItemOrdinal),
    [2, 3, 4, 5],
  )
  assert.equal(
    result.loaders.collaboration.collaborationUrl,
    environment.COLLA_CAPACITY_COLLABORATION_URL,
  )
  assert.equal(typeof result.loaders.collaboration.ticketIssuer, 'function')
  assert.equal(
    result.loaders.worker.runtimeValues.probeRunId,
    '11111111-1111-1111-1111-111111111111',
  )
  assert.notEqual(
    result.loaders.worker.runtimeValues.probeRunIds.warmup,
    result.loaders.worker.runtimeValues.probeRunIds.measured,
  )
  assert.equal(result.loaders.websocket.triggerAggregateLanes, 4)
  assert.equal(
    result.loaders.websocket.targets.trigger.body.aggregateKey,
    'websocket-lane-{{triggerLane}}',
  )
  const workerProducers = result.loaders.worker.targets.producers
  assert.equal(workerProducers.length, 4)
  assert.equal(new Set(workerProducers.map((producer) => producer.name)).size, 4)
  assert.equal(new Set(workerProducers.map((producer) => producer.body.aggregateKey)).size, 4)
  for (const producer of workerProducers) {
    assert.match(producer.path, /{{options\.runtimeValues\.probeRunId}}/)
    assert.match(producer.body.requestKey, new RegExp(`^${producer.body.aggregateKey}-\\{\\{iteration\\}\\}$`))
  }
  assert.doesNotMatch(source, /runtime-password|runtime-probe-secret|jwt-/)
})

function jsonResponse(body) {
  return {
    ok: true,
    status: 200,
    async json() {
      return body
    },
  }
}
