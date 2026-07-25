import assert from 'node:assert/strict'
import { EventEmitter } from 'node:events'
import test from 'node:test'

import { resolveTemplate } from '../src/load/common.mjs'
import { runHttpScenario } from '../src/load/http.mjs'
import { runWebSocketScenario } from '../src/load/websocket.mjs'
import { runWorkerScenario } from '../src/load/worker.mjs'

test('resolveTemplate deeply resolves values and preserves whole-placeholder types', () => {
  const context = {
    account: {
      id: 42,
      enabled: true,
      profile: { name: 'alice' },
      roles: ['owner', 'editor'],
    },
  }
  const resolved = resolveTemplate({
    path: '/accounts/{{account.id}}?enabled={{account.enabled}}',
    headers: [{ name: 'x-owner', value: '{{account.profile.name}}' }],
    body: {
      id: '{{account.id}}',
      enabled: '{{account.enabled}}',
      profile: '{{account.profile}}',
      roles: '{{account.roles}}',
      summary: 'profile={{account.profile}}',
      legacy: ({ account }) => ({
        firstRole: '{{account.roles.0}}',
        name: account.profile.name,
      }),
    },
  }, context)

  assert.equal(resolved.path, '/accounts/42?enabled=true')
  assert.equal(resolved.body.id, 42)
  assert.equal(resolved.body.enabled, true)
  assert.deepEqual(resolved.body.profile, { name: 'alice' })
  assert.deepEqual(resolved.body.roles, ['owner', 'editor'])
  assert.equal(resolved.body.summary, 'profile={"name":"alice"}')
  assert.deepEqual(resolved.body.legacy, { firstRole: 'owner', name: 'alice' })
  assert.notStrictEqual(resolved.body.profile, context.account.profile)
  assert.notStrictEqual(resolved.body.roles, context.account.roles)
})

test('resolveTemplate fails closed without leaking context values', () => {
  const secret = 'private-context-value'
  const cyclic = { value: 'ok' }
  cyclic.self = cyclic
  const dangerousObject = JSON.parse('{"__proto__":{"polluted":true}}')
  const accessor = {}
  Object.defineProperty(accessor, 'secret', {
    enumerable: true,
    get: () => secret,
  })
  const selfReturning = () => selfReturning
  const attempts = [
    () => resolveTemplate('{{missing.value}}', { secret }),
    () => resolveTemplate('{{safe.__proto__.value}}', { safe: {}, secret }),
    () => resolveTemplate('{{safe.prototype.value}}', { safe: {}, secret }),
    () => resolveTemplate('{{safe.constructor.name}}', { safe: {}, secret }),
    () => resolveTemplate('{{safe.value}}', { safe: new Date(), secret }),
    () => resolveTemplate('prefix {{safe.value', { safe: { value: secret } }),
    () => resolveTemplate('prefix safe.value}}', { safe: { value: secret } }),
    () => resolveTemplate('{{safe..value}}', { safe: { value: secret } }),
    () => resolveTemplate(new Date(), { secret }),
    () => resolveTemplate(cyclic, { secret }),
    () => resolveTemplate(dangerousObject, { secret }),
    () => resolveTemplate(accessor, { secret }),
    () => resolveTemplate('{{safe.value}}', {
      safe: new Proxy({}, {
        getOwnPropertyDescriptor: () => {
          throw new Error(secret)
        },
      }),
    }),
    () => resolveTemplate(() => {
      throw new Error(secret)
    }, { secret }),
    () => resolveTemplate(selfReturning, { secret }),
  ]

  for (const attempt of attempts) {
    const error = captureError(attempt)
    assert.match(error.message, /template resolution failed/)
    assert.equal(error.message.includes(secret), false)
  }
  assert.equal({}.polluted, undefined)
})

test('HTTP templates cover runtime request state and real upload headers', async () => {
  const requests = []
  const fetch = async (url, init = {}) => {
    const parsed = new URL(url)
    const body = typeof init.body === 'string' && init.body.startsWith('{')
      ? JSON.parse(init.body)
      : init.body
    requests.push({ url, path: parsed.pathname, headers: init.headers ?? {}, body })

    if (parsed.pathname === '/api/auth/alice') return jsonResponse({ accessToken: 'token-1' })
    if (parsed.pathname === '/api/read/0') return jsonResponse({ items: [] })
    if (parsed.pathname === '/api/write/0') return jsonResponse({ id: 'write-1' })
    if (parsed.pathname === '/api/idempotent/request-0') return jsonResponse({ id: 'same' })
    if (parsed.pathname === '/api/files/prepare') {
      return jsonResponse({
        uploadId: 'upload-9',
        uploadUrl: 'https://storage.test/object',
        headers: {
          'x-signed': 'prepare-signature',
          'X-Override': 'prepare-value',
        },
      })
    }
    if (url === 'https://storage.test/object') return new Response(null, { status: 200 })
    if (parsed.pathname === '/api/files/upload-9/complete') return jsonResponse({ id: 'upload-9' })
    return jsonResponse({ error: 'not found' }, 404)
  }

  const result = await runHttpScenario({
    apiBaseUrl: 'https://api.test',
    templateHeader: 'from-options',
    runtimeValues: {
      prefix: 'api',
      tenantId: 'tenant-7',
    },
    users: [{ username: 'alice', password: 'secret' }],
    iterations: 1,
    requestId: () => 'request-0',
    fetch,
    targets: {
      login: {
        path: '/{{runtimeValues.prefix}}/auth/{{user.username}}',
        headers: { 'x-option': '{{options.templateHeader}}' },
        body: { username: '{{user.username}}', password: '{{user.password}}' },
      },
      read: {
        name: 'read',
        path: '/{{prefix}}/read/{{iteration}}',
        headers: { 'x-index': '{{index}}' },
      },
      write: {
        name: 'write',
        path: '/{{prefix}}/write/{{index}}',
        body: {
          iteration: '{{iteration}}',
          tenant: 'tenant={{runtimeValues.tenantId}}',
        },
      },
      idempotency: {
        name: 'idempotency',
        path: '/{{prefix}}/idempotent/{{requestId}}',
        body: { requestId: '{{requestId}}' },
      },
      file: {
        prepare: {
          path: '/{{prefix}}/files/prepare',
          body: { tenantId: '{{tenantId}}' },
        },
        upload: {
          method: 'PUT',
          headers: {
            'x-override': 'target-value',
            'x-upload-id': '{{file.fileId}}',
          },
          body: '{{file.fileId}}',
        },
        complete: {
          path: '/{{prefix}}/files/{{file.fileId}}/complete',
          headers: { 'x-prepare-id': '{{file.prepare.uploadId}}' },
          body: {
            uploadId: '{{file.fileId}}',
            prepareUploadId: '{{file.prepare.uploadId}}',
          },
        },
      },
    },
  })

  assert.equal(result.ok, true, JSON.stringify(result.errors))
  const login = requests.find((request) => request.path === '/api/auth/alice')
  assert.equal(login.headers['x-option'], 'from-options')
  assert.deepEqual(login.body, { username: 'alice', password: 'secret' })
  assert.deepEqual(
    requests.find((request) => request.path === '/api/write/0').body,
    { iteration: 0, tenant: 'tenant=tenant-7' },
  )
  assert.deepEqual(
    requests.filter((request) => request.path === '/api/idempotent/request-0').map((request) => request.body),
    [{ requestId: 'request-0' }, { requestId: 'request-0' }],
  )
  const upload = requests.find((request) => request.url === 'https://storage.test/object')
  assert.equal(upload.headers['x-signed'], 'prepare-signature')
  assert.equal(upload.headers['x-override'], 'target-value')
  assert.equal(upload.headers['X-Override'], undefined)
  assert.equal(upload.headers['x-upload-id'], 'upload-9')
  assert.equal(upload.body, 'upload-9')
  const complete = requests.find((request) => request.path === '/api/files/upload-9/complete')
  assert.equal(complete.headers['x-prepare-id'], 'upload-9')
  assert.deepEqual(complete.body, { uploadId: 'upload-9', prepareUploadId: 'upload-9' })
})

test('Worker request templates cover options, runtime values, iteration and index', async () => {
  const producerRequests = []
  let diagnosticsPoll = 0
  const result = await runWorkerScenario({
    apiBaseUrl: 'https://api.test',
    routeOption: 'option-header',
    runtimeValues: { producerRoute: 'events', tenantId: 'tenant-3' },
    correctnessMode: { version: 1, mode: 'summary' },
    iterations: 1,
    drainTimeoutMs: 0,
    fetch: async (url, init = {}) => {
      const path = new URL(url).pathname
      if (path === '/api/diagnostics/tenant-3') {
        diagnosticsPoll += 1
        return jsonResponse({
          backlog: 0,
          oldestAgeSeconds: 0,
          retries: 0,
          deadLetters: 0,
          sideEffects: [],
          processedEventIds: diagnosticsPoll > 1 ? ['event-0'] : [],
        })
      }
      producerRequests.push({
        path,
        headers: init.headers,
        body: JSON.parse(init.body),
      })
      return jsonResponse({
        eventId: 'event-0',
        sideEffectId: 'effect-0',
        aggregateId: 'aggregate-0',
        sequence: 0,
      })
    },
    targets: {
      producers: [{
        name: 'templated',
        path: '/api/{{producerRoute}}/{{iteration}}',
        headers: { 'x-option': '{{options.routeOption}}' },
        body: {
          index: '{{index}}',
          iteration: '{{iteration}}',
          tenantId: '{{runtimeValues.tenantId}}',
        },
      }],
      diagnostics: {
        path: '/api/diagnostics/{{tenantId}}',
        headers: { 'x-poll': '{{index}}' },
      },
    },
  })

  assert.equal(result.ok, true, JSON.stringify(result.errors))
  assert.deepEqual(producerRequests, [{
    path: '/api/events/0',
    headers: {
      'x-option': 'option-header',
      'Content-Type': 'application/json',
    },
    body: { index: 0, iteration: 0, tenantId: 'tenant-3' },
  }])
})

test('WebSocket trigger and calibration templates include options and expectedEvent', async () => {
  const sockets = []
  const requests = []
  const expected = {
    eventId: 'event-7',
    workspaceId: 'workspace-1',
    sequenceScope: 'object',
    sequenceKey: 'object-7',
    sequence: 1,
    businessObjectId: 'object-7',
    calibrationPath: '/api/fallback/object-7',
  }
  const result = await runWebSocketScenario({
    wsUrl: 'ws://gateway.test/events',
    apiBaseUrl: 'https://api.test',
    triggerOption: 'option-value',
    token: 'admin-token',
    runtimeValues: { triggerRoute: 'trigger', tenantId: 'tenant-8' },
    iterations: 1,
    connections: 1,
    reconnects: 1,
    settleMs: 5,
    reconnectSettleMs: 1,
    socketFactory: ({ index }) => {
      const socket = new TemplateSocket(index)
      sockets.push(socket)
      return socket
    },
    fetch: async (url, init = {}) => {
      const path = new URL(url).pathname
      const body = init.body ? JSON.parse(init.body) : undefined
      requests.push({ path, headers: init.headers, body })
      if (path === '/api/trigger/0') {
        setTimeout(() => sockets[0].emit('message', JSON.stringify({
          envelopeVersion: 1,
          signalVersion: 1,
          type: 'object.changed',
          ...expected,
          serverTime: new Date().toISOString(),
          occurredAt: new Date().toISOString(),
        })), 0)
        return jsonResponse(expected)
      }
      if (path === '/api/calibration/object-7') {
        return jsonResponse({ items: [{ id: 'object-7', sequence: 1 }] })
      }
      return jsonResponse({ error: 'not found' }, 404)
    },
    targets: {
      trigger: {
        path: '/api/{{runtimeValues.triggerRoute}}/{{index}}',
        headers: {
          Authorization: 'Bearer {{token}}',
          'x-option': '{{options.triggerOption}}',
        },
        body: {
          iteration: '{{iteration}}',
          tenantId: '{{tenantId}}',
        },
      },
      calibration: {
        method: 'POST',
        path: '/api/calibration/{{expectedEvent.businessObjectId}}',
        headers: {
          'x-event-id': '{{expectedEvent.eventId}}',
          'x-tenant': '{{runtimeValues.tenantId}}',
        },
        body: {
          eventId: '{{expectedEvent.eventId}}',
          sequence: '{{expectedEvent.sequence}}',
        },
      },
    },
  })

  assert.equal(result.ok, true, JSON.stringify(result.errors))
  assert.deepEqual(requests[0].body, { iteration: 0, tenantId: 'tenant-8' })
  assert.equal(requests[0].headers.Authorization, 'Bearer admin-token')
  assert.equal(requests[0].headers['x-option'], 'option-value')
  const calibration = requests.find((request) => request.path === '/api/calibration/object-7')
  assert.equal(calibration.headers['x-event-id'], 'event-7')
  assert.equal(calibration.headers['x-tenant'], 'tenant-8')
  assert.deepEqual(calibration.body, { eventId: 'event-7', sequence: 1 })
})

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function captureError(callback) {
  try {
    callback()
  } catch (error) {
    return error
  }
  assert.fail('expected template resolution to throw')
}

class TemplateSocket extends EventEmitter {
  constructor(index) {
    super()
    this.readyState = 0
    queueMicrotask(() => {
      this.readyState = 1
      this.emit('open')
      this.emit('message', JSON.stringify({
        type: 'connection.ready',
        instanceId: `gateway-${index}`,
      }))
    })
  }

  close() {
    if (this.readyState === 3) return
    this.readyState = 3
    this.emit('close')
  }
}
