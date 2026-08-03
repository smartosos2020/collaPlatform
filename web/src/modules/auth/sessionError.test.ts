import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

import { ApiRequestError } from '../../shared/api/apiError.ts'
import { isSessionExpiredError } from './sessionError.ts'

describe('isSessionExpiredError', () => {
  it('401/403 视为会话失效', () => {
    assert.equal(isSessionExpiredError(new ApiRequestError(401)), true)
    assert.equal(isSessionExpiredError(new ApiRequestError(403)), true)
  })

  it('瞬时故障不得视为会话失效', () => {
    assert.equal(isSessionExpiredError(new ApiRequestError(500)), false)
    assert.equal(isSessionExpiredError(new ApiRequestError(502)), false)
    assert.equal(isSessionExpiredError(new ApiRequestError(404)), false)
    assert.equal(isSessionExpiredError(new Error('API request timed out')), false)
    assert.equal(isSessionExpiredError('unauthorized'), false)
    assert.equal(isSessionExpiredError(null), false)
  })
})
