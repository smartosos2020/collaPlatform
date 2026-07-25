import assert from 'node:assert/strict'
import test from 'node:test'

import { resolveRuntimeEnvironment } from '../src/runtime.mjs'

test('resolves nested Env and Envs references without retaining reference keys', () => {
  const input = {
    loaders: [{
      urlEnv: 'CAPACITY_API_URL',
      credentials: {
        passwordEnv: 'CAPACITY_PASSWORD',
        tokenEnv: 'CAPACITY_TOKEN',
      },
      replicaUrlsEnvs: ['CAPACITY_REPLICA_A_URL', 'CAPACITY_REPLICA_B_URL'],
    }],
  }

  const result = resolveRuntimeEnvironment(input, {
    CAPACITY_API_URL: 'https://capacity.internal',
    CAPACITY_PASSWORD: 'runtime-password',
    CAPACITY_TOKEN: 'runtime-token',
    CAPACITY_REPLICA_A_URL: 'https://replica-a.internal',
    CAPACITY_REPLICA_B_URL: 'https://replica-b.internal',
  })

  assert.deepEqual(result, {
    loaders: [{
      url: 'https://capacity.internal',
      credentials: {
        password: 'runtime-password',
        token: 'runtime-token',
      },
      replicaUrls: ['https://replica-a.internal', 'https://replica-b.internal'],
    }],
  })
  assert.equal('urlEnv' in result.loaders[0], false)
  assert.equal('replicaUrlsEnvs' in result.loaders[0], false)
})

test('fails closed for missing and empty environment variables', () => {
  assert.throws(
    () => resolveRuntimeEnvironment({ tokenEnv: 'CAPACITY_TOKEN' }, {}),
    /missing or empty/,
  )
  assert.throws(
    () => resolveRuntimeEnvironment({ tokenEnv: 'CAPACITY_TOKEN' }, { CAPACITY_TOKEN: '   ' }),
    /missing or empty/,
  )
})

test('rejects resolved-key collisions including Env versus Envs references', () => {
  assert.throws(
    () => resolveRuntimeEnvironment(
      { url: 'checked-in-url', urlEnv: 'CAPACITY_URL' },
      { CAPACITY_URL: 'runtime-url' },
    ),
    /collision/,
  )
  assert.throws(
    () => resolveRuntimeEnvironment(
      { endpointEnv: 'CAPACITY_ENDPOINT', endpointEnvs: ['CAPACITY_ENDPOINT_A'] },
      { CAPACITY_ENDPOINT: 'runtime-endpoint', CAPACITY_ENDPOINT_A: 'runtime-endpoint-a' },
    ),
    /collision/,
  )
})

test('rejects non-string references and malformed Envs arrays', () => {
  assert.throws(
    () => resolveRuntimeEnvironment({ urlEnv: 42 }, {}),
    /non-empty string/,
  )
  assert.throws(
    () => resolveRuntimeEnvironment({ urlsEnvs: ['CAPACITY_URL', null] }, {}),
    /non-empty strings/,
  )
  assert.throws(
    () => resolveRuntimeEnvironment({ urlsEnvs: 'CAPACITY_URL' }, {}),
    /non-empty strings/,
  )
})

test('returns a deep new value without mutating the runtime template', () => {
  const input = {
    nested: {
      urlEnv: 'CAPACITY_URL',
      values: [{ enabled: true }],
    },
  }
  const snapshot = structuredClone(input)

  const result = resolveRuntimeEnvironment(input, { CAPACITY_URL: 'runtime-url' })

  assert.deepEqual(input, snapshot)
  assert.notStrictEqual(result, input)
  assert.notStrictEqual(result.nested, input.nested)
  assert.notStrictEqual(result.nested.values, input.nested.values)
  assert.notStrictEqual(result.nested.values[0], input.nested.values[0])
})

test('preserves functions and non-plain injected values', () => {
  const injectedFunction = () => 'injected'
  const injectedDate = new Date('2026-07-25T00:00:00.000Z')
  const injectedMap = new Map([['node', 'api-a']])

  const result = resolveRuntimeEnvironment({
    injectedFunction,
    injectedDate,
    injectedMap,
  })

  assert.strictEqual(result.injectedFunction, injectedFunction)
  assert.strictEqual(result.injectedDate, injectedDate)
  assert.strictEqual(result.injectedMap, injectedMap)
})

test('rejects checked-in raw secret fields while allowing environment references', () => {
  for (const key of ['password', 'db_password', 'authToken', 'client-secret', 'apiKey', 'credentials']) {
    assert.throws(
      () => resolveRuntimeEnvironment({ [key]: 'must-not-persist' }),
      /Raw secret value field is not allowed/,
      key,
    )
  }

  assert.deepEqual(
    resolveRuntimeEnvironment(
      { passwordEnv: 'CAPACITY_PASSWORD', authTokenEnv: 'CAPACITY_TOKEN' },
      { CAPACITY_PASSWORD: 'runtime-password', CAPACITY_TOKEN: 'runtime-token' },
    ),
    { password: 'runtime-password', authToken: 'runtime-token' },
  )
})

test('errors never disclose actual environment or checked-in secret values', () => {
  const actualSecret = 'super-secret-runtime-value'
  let missingError
  let collisionError
  let rawSecretError

  try {
    resolveRuntimeEnvironment({ tokenEnv: 'CAPACITY_TOKEN' }, {})
  } catch (error) {
    missingError = error
  }
  try {
    resolveRuntimeEnvironment(
      { token: 'checked-in-token-value', tokenEnv: 'CAPACITY_TOKEN' },
      { CAPACITY_TOKEN: actualSecret },
    )
  } catch (error) {
    collisionError = error
  }
  try {
    resolveRuntimeEnvironment({ password: 'checked-in-password-value' })
  } catch (error) {
    rawSecretError = error
  }

  for (const error of [missingError, collisionError, rawSecretError]) {
    assert.ok(error instanceof Error)
    assert.doesNotMatch(error.message, new RegExp(actualSecret))
    assert.doesNotMatch(error.message, /checked-in-(?:token|password)-value/)
  }
})
