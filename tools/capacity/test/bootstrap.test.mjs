import assert from 'node:assert/strict'
import test from 'node:test'

import { bootstrapCapacityRuntime } from '../src/bootstrap.mjs'

test('authenticates fixture users and injects runtime-only loader credentials', async () => {
  const calls = []
  const fetch = async (url, init) => {
    calls.push({ url, init })
    const body = JSON.parse(init.body)
    return jsonResponse({ accessToken: `jwt-${body.username}` })
  }
  const result = await bootstrapCapacityRuntime({
    bootstrap: {
      baseUrl: 'http://capacity.test',
      authentication: {
        concurrency: 2,
        users: [
          { username: 'cap_user_1', password: 'runtime-one', ordinal: 1, workspaceOrdinal: 1 },
          { username: 'cap_user_2', password: 'runtime-two', ordinal: 2, workspaceOrdinal: 2 },
        ],
      },
    },
    loaders: {
      http: {},
      websocket: {},
      worker: {},
    },
  }, { fetch })

  assert.equal(result.summary.authenticatedUsers, 2)
  assert.deepEqual(
    result.loaders.http.users.map((user) => user.token),
    ['jwt-cap_user_1', 'jwt-cap_user_2'],
  )
  assert.deepEqual(
    result.loaders.websocket.users.map((user) => user.workspaceOrdinal),
    [1, 2],
  )
  assert.equal(result.loaders.worker.token, 'jwt-cap_user_1')
  assert.match(result.summary.probeRunId, /^[0-9a-f-]{36}$/)
  assert.match(result.summary.probeRunIds.warmup, /^[0-9a-f-]{36}$/)
  assert.match(result.summary.probeRunIds.measured, /^[0-9a-f-]{36}$/)
  assert.notEqual(result.summary.probeRunIds.warmup, result.summary.probeRunIds.measured)
  assert.notEqual(result.summary.probeRunIds.warmup, result.summary.probeRunId)
  assert.notEqual(result.summary.probeRunIds.measured, result.summary.probeRunId)
  assert.equal(result.loaders.http.runtimeValues.probeRunId, result.summary.probeRunId)
  assert.deepEqual(result.loaders.http.runtimeValues.probeRunIds, result.summary.probeRunIds)
  assert.equal(result.loaders.websocket.runtimeValues.probeRunId, result.summary.probeRunId)
  assert.equal(result.loaders.worker.runtimeValues.probeRunId, result.summary.probeRunId)
  assert.equal(calls.length, 2)
  assert.ok(calls.every((call) => call.url === 'http://capacity.test/api/auth/login'))
})

test('derives seeded users and collaboration rooms without checked-in credentials', async () => {
  const calls = []
  const fetch = async (url, init) => {
    calls.push({ url, init })
    if (url.endsWith('/api/auth/login')) {
      return jsonResponse({ accessToken: `jwt-${JSON.parse(init.body).username}` })
    }
    const itemId = url.split('/items/')[1].split('/')[0]
    return jsonResponse({
      ticket: `ticket-${itemId}-${calls.length}`,
      documentName: `knowledge:2063f9f3-572a-5dc7-ae7a-6b67aaf38e08:${itemId}`,
      url: '/collaboration',
    })
  }
  const result = await bootstrapCapacityRuntime({
    fixture: {
      seedId: 's05-c1',
      workspaceWeights: [50, 25, 15, 10],
      users: {
        count: 3,
        startOrdinal: 1,
        activeOnly: true,
        workspaceOrdinal: 1,
      },
      collaboration: {
        workspaceOrdinal: 1,
        knowledgeItemOrdinals: [2, 3],
        clientsPerRoom: 2,
      },
    },
    bootstrap: {
      baseUrl: 'http://capacity.test',
      authentication: { password: 'runtime-only' },
      collaboration: {},
    },
    loaders: {
      http: {},
      websocket: {},
      worker: {},
      collaboration: { collaborationUrl: 'ws://collaboration.test/base' },
    },
  }, { fetch, probeRunId: '11111111-1111-1111-1111-111111111111' })

  assert.equal(result.summary.authenticatedUsers, 3)
  assert.equal(result.summary.collaborationTickets, 0)
  assert.equal(result.summary.probeRunId, '11111111-1111-1111-1111-111111111111')
  assert.deepEqual(
    result.loaders.http.users.map((user) => user.username),
    [
      'cap_045d23add5_u0000001',
      'cap_045d23add5_u0000002',
      'cap_045d23add5_u0000003',
    ],
  )
  assert.equal(result.loaders.collaboration.rooms.length, 2)
  assert.ok(result.loaders.collaboration.rooms.every((room) => room.users.length === 2))
  assert.ok(result.loaders.collaboration.rooms.every((room) =>
    room.users.every((user) => !('token' in user))))
  const issued = []
  for (const [roomIndex, room] of result.loaders.collaboration.rooms.entries()) {
    for (const clientIndex of room.users.keys()) {
      issued.push(await result.loaders.collaboration.ticketIssuer({ roomIndex, clientIndex }))
    }
  }
  assert.equal(result.summary.collaborationTickets, 4)
  assert.ok(issued.every((ticket) => ticket.url === 'ws://collaboration.test/collaboration'))
  assert.equal(new Set(issued.map((ticket) => ticket.ticket)).size, 4)
  assert.equal(
    result.loaders.collaboration.runtimeValues.probeRunId,
    '11111111-1111-1111-1111-111111111111',
  )
  const serialized = JSON.stringify(result)
  assert.doesNotMatch(serialized, /runtime-only/)
})

test('fixture materialization rejects ambiguity and secret-shaped extensions', async () => {
  const base = {
    fixture: {
      seedId: 's05-c1',
      workspaceWeights: [50, 25, 15, 10],
      users: { count: 1 },
    },
    bootstrap: {
      baseUrl: 'http://capacity.test',
      authentication: { password: 'runtime-only' },
    },
    loaders: {},
  }
  await assert.rejects(
    bootstrapCapacityRuntime({
      ...base,
      fixture: { ...base.fixture, token: 'must-not-be-accepted' },
    }, { fetch: async () => jsonResponse({}) }),
    /unsupported fields: token/,
  )
  await assert.rejects(
    bootstrapCapacityRuntime({
      ...base,
      bootstrap: {
        ...base.bootstrap,
        authentication: {
          ...base.bootstrap.authentication,
          users: [{ username: 'ambiguous', password: 'runtime-only' }],
        },
      },
    }, { fetch: async () => jsonResponse({}) }),
    /cannot be combined/,
  )
})

test('dynamically issues one collaboration ticket per connection and keeps room users isolated', async () => {
  let ticketNumber = 0
  const fetch = async (url, init) => {
    if (url.endsWith('/api/auth/login')) {
      const body = JSON.parse(init.body)
      return jsonResponse({ accessToken: `jwt-${body.username}` })
    }
    ticketNumber += 1
    const itemId = url.split('/items/')[1].split('/')[0]
    return jsonResponse({
      ticket: `ticket-${ticketNumber}`,
      documentName: `knowledge:${url.split('/knowledge-bases/')[1].split('/')[0]}:${itemId}`,
      url: '/collaboration',
    })
  }
  const result = await bootstrapCapacityRuntime({
    bootstrap: {
      baseUrl: 'http://capacity.test',
      authentication: {
        users: [
          { username: 'workspace-one', password: 'runtime', workspaceOrdinal: 1 },
          { username: 'workspace-two', password: 'runtime', workspaceOrdinal: 2 },
        ],
      },
      collaboration: {
        rooms: [
          { spaceId: 'space-1', itemId: 'item-1', clients: 2, workspaceOrdinal: 1 },
          { spaceId: 'space-2', itemId: 'item-2', clients: 1, workspaceOrdinal: 2 },
        ],
      },
    },
    loaders: {
      collaboration: { collaborationUrl: 'wss://collaboration.test/root' },
    },
  }, { fetch })

  assert.equal(result.summary.collaborationTickets, 0)
  assert.equal(result.loaders.collaboration.collaborationUrl, 'wss://collaboration.test/root')
  assert.deepEqual(
    result.loaders.collaboration.rooms[0].users.map((user) => user.username),
    ['workspace-one', 'workspace-one'],
  )
  assert.deepEqual(
    result.loaders.collaboration.rooms[1].users.map((user) => user.username),
    ['workspace-two'],
  )
  const tickets = await Promise.all([
    result.loaders.collaboration.ticketIssuer({ roomIndex: 0, clientIndex: 0 }),
    result.loaders.collaboration.ticketIssuer({ roomIndex: 0, clientIndex: 1 }),
    result.loaders.collaboration.ticketIssuer({ roomIndex: 1, clientIndex: 0 }),
  ])
  assert.equal(result.summary.collaborationTickets, 3)
  assert.equal(new Set(tickets.map((entry) => entry.ticket)).size, 3)
  assert.ok(tickets.every((entry) => entry.url === 'wss://collaboration.test/collaboration'))
  assert.equal(result.loaders.collaboration.rooms[0].name, 'knowledge:space-1:item-1')
})

test('does not disclose passwords or tokens through bootstrap errors', async () => {
  const password = 'runtime-password-must-not-leak'
  const token = 'runtime-token-must-not-leak'
  const fetch = async (url) => {
    if (url.endsWith('/api/auth/login')) return jsonResponse({ accessToken: token })
    throw new Error(`${password}:${token}`)
  }

  await assert.rejects(
    bootstrapCapacityRuntime({
      bootstrap: {
        baseUrl: 'http://capacity.test',
        authentication: {
          users: [{ username: 'fixture-user', password, workspaceOrdinal: 1 }],
        },
        collaboration: {
          rooms: [{ spaceId: 'space-1', itemId: 'item-1', clients: 1, workspaceOrdinal: 1 }],
        },
      },
      loaders: { collaboration: { collaborationUrl: 'ws://collaboration.test' } },
    }, { fetch }).then((result) =>
      result.loaders.collaboration.ticketIssuer({ roomIndex: 0, clientIndex: 0 })),
    (error) => {
      assert.doesNotMatch(error.message, new RegExp(password))
      assert.doesNotMatch(error.message, new RegExp(token))
      return true
    },
  )
})

test('fails closed when a relative collaboration ticket URL has no WS base', async () => {
  const result = await bootstrapCapacityRuntime({
    bootstrap: {
      baseUrl: 'http://capacity.test',
      authentication: {
        users: [{ username: 'user', password: 'runtime', workspaceOrdinal: 1 }],
      },
      collaboration: {
        rooms: [{ spaceId: 'space-1', itemId: 'item-1', workspaceOrdinal: 1 }],
      },
    },
    loaders: { collaboration: {} },
  }, {
    fetch: async (url) => url.endsWith('/api/auth/login')
      ? jsonResponse({ accessToken: 'runtime-token' })
      : jsonResponse({
          ticket: 'one-time-ticket',
          documentName: 'knowledge:space-1:item-1',
          url: '/collaboration',
        }),
  })

  await assert.rejects(
    result.loaders.collaboration.ticketIssuer({ roomIndex: 0, clientIndex: 0 }),
    /collaborationUrl is required/,
  )
})

test('fails closed for missing users, invalid base URLs, and workspace mismatches', async () => {
  await assert.rejects(
    bootstrapCapacityRuntime({
      bootstrap: {
        baseUrl: 'file:///tmp/runtime',
        authentication: { users: [{ username: 'user', password: 'runtime' }] },
      },
      loaders: {},
    }, { fetch: async () => jsonResponse({}) }),
    /HTTP or HTTPS/,
  )
  await assert.rejects(
    bootstrapCapacityRuntime({
      bootstrap: {
        baseUrl: 'http://capacity.test',
        authentication: { users: [] },
      },
      loaders: {},
    }, { fetch: async () => jsonResponse({}) }),
    /at least one user/,
  )
  await assert.rejects(
    bootstrapCapacityRuntime({
      bootstrap: {
        baseUrl: 'http://capacity.test',
        authentication: {
          users: [{ username: 'user', password: 'runtime', workspaceOrdinal: 1 }],
        },
        collaboration: {
          rooms: [{ spaceId: 'space-2', itemId: 'item-2', workspaceOrdinal: 2 }],
        },
      },
      loaders: { collaboration: {} },
    }, {
      fetch: async (url) => url.endsWith('/api/auth/login')
        ? jsonResponse({ accessToken: 'runtime-token' })
        : jsonResponse({}),
    }),
    /no authenticated workspace user/,
  )
})

function jsonResponse(body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    async json() {
      return body
    },
  }
}
