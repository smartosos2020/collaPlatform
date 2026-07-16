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
  const gateway = new CollaborationBackendGateway(config({ backendRetries: 2 }))

  await assert.rejects(() => gateway.request('/authenticate', {}), /denied/)
  assert.equal(attempts, 1)
})

test('backend gateway aborts database latency after its configured timeout', async (t) => {
  const originalFetch = globalThis.fetch
  t.after(() => { globalThis.fetch = originalFetch })
  globalThis.fetch = async (url, options) => new Promise((resolve, reject) => {
    options.signal.addEventListener('abort', () => reject(Object.assign(new Error('aborted'), { name: 'AbortError' })))
  })
  const failures = []
  const gateway = new CollaborationBackendGateway(config({ backendTimeoutMs: 10, backendRetries: 0 }), (path, error) => failures.push({ path, error }))

  await assert.rejects(() => gateway.request('/document/load', {}), /timed out/)
  assert.equal(failures.length, 1)
  assert.equal(failures[0].path, '/document/load')
})

function config(overrides = {}) {
  return {
    backendUrl: 'http://backend.invalid', internalSecret: 'test-secret',
    backendTimeoutMs: 1000, backendRetries: 0, ...overrides,
  }
}
