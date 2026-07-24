import assert from 'node:assert/strict'
import test from 'node:test'

import { CollaborationBackendGateway } from '../src/backendGateway.js'

test('backend gateway retries bounded transient failures and then succeeds', async (t) => {
  const originalFetch = globalThis.fetch
  t.after(() => { globalThis.fetch = originalFetch })
  let attempts = 0
  globalThis.fetch = async () => {
    attempts += 1
    if (attempts < 3) throw new TypeError('temporary network failure')
    return new Response(JSON.stringify({ accepted: true }), { status: 200 })
  }
  const gateway = new CollaborationBackendGateway(config({ backendRetries: 2 }))

  assert.deepEqual(await gateway.request('/document/update', {}), { accepted: true })
  assert.equal(attempts, 3)
})

test('backend gateway does not retry authorization rejection', async (t) => {
  const originalFetch = globalThis.fetch
  t.after(() => { globalThis.fetch = originalFetch })
  let attempts = 0
  globalThis.fetch = async () => {
    attempts += 1
    return new Response(JSON.stringify({ code: 'COLLAB_FORBIDDEN', message: 'denied' }), { status: 403 })
  }
  const failures = []
  const gateway = new CollaborationBackendGateway(
    config({ backendRetries: 2 }),
    (path, error) => failures.push({ path, error }),
  )

  await assert.rejects(() => gateway.request('/authenticate', {}), /denied/)
  assert.equal(attempts, 1)
  assert.equal(failures.length, 0)
})

test('backend gateway separates single-use ticket consumption from session authorization', async (t) => {
  const originalFetch = globalThis.fetch
  t.after(() => { globalThis.fetch = originalFetch })
  const requested = []
  globalThis.fetch = async (url) => {
    requested.push(String(url))
    return new Response(JSON.stringify({ canEdit: true }), { status: 200 })
  }
  const gateway = new CollaborationBackendGateway(config())

  await gateway.authenticate('single-use', 'knowledge:workspace:item')
  await gateway.authorize('established-session', 'knowledge:workspace:item')

  assert.deepEqual(requested, [
    'http://backend.invalid/authenticate',
    'http://backend.invalid/authorize',
  ])
})

test('backend gateway aborts database latency after its configured timeout', async (t) => {
  const originalFetch = globalThis.fetch
  t.after(() => { globalThis.fetch = originalFetch })
  globalThis.fetch = async (url, options) => new Promise((resolve, reject) => {
    options.signal.addEventListener('abort', () => reject(Object.assign(new Error('aborted'), { name: 'AbortError' })))
  })
  const failures = []
  const gateway = new CollaborationBackendGateway(config({ backendTimeoutMs: 10, backendRetries: 0 }), (path, error) => failures.push({ path, error }))

  await assert.rejects(
    () => gateway.request('/document/load', {}),
    (error) => error.message.includes('timed out') && error.retryable === true,
  )
  assert.equal(failures.length, 1)
  assert.equal(failures[0].path, '/document/load')
})

test('backend gateway fails over across configured API nodes', async (t) => {
  const originalFetch = globalThis.fetch
  t.after(() => { globalThis.fetch = originalFetch })
  const requested = []
  globalThis.fetch = async (url) => {
    requested.push(String(url))
    if (String(url).startsWith('http://api-a')) throw new TypeError('api-a unavailable')
    return new Response(JSON.stringify({ node: 'api-b' }), { status: 200 })
  }
  const gateway = new CollaborationBackendGateway(config({
    backendUrls: ['http://api-a/internal', 'http://api-b/internal'],
    backendRetries: 0,
  }))

  assert.deepEqual(await gateway.request('/authenticate', {}), { node: 'api-b' })
  assert.deepEqual(requested, [
    'http://api-a/internal/authenticate',
    'http://api-b/internal/authenticate',
  ])
})

function config(overrides = {}) {
  return {
    backendUrls: ['http://backend.invalid'], internalSecret: 'test-secret',
    backendTimeoutMs: 1000, backendRetries: 0, ...overrides,
  }
}
