import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

import {
  isWorkItemConfigurationCompatibilityReady,
  requiresWorkItemConfigurationCompatibility,
} from './workItemConfigurationPublication.ts'

describe('work-item configuration publication compatibility gate', () => {
  it('allows the first complete publication over a legacy partial baseline', () => {
    const legacyPartialVersion = { completeSnapshot: false }

    assert.equal(requiresWorkItemConfigurationCompatibility(legacyPartialVersion), false)
    assert.equal(
      isWorkItemConfigurationCompatibilityReady(legacyPartialVersion, {
        versionsQuerySucceeded: true,
        compatibilityQuerySucceeded: false,
      }),
      true,
    )
  })

  it('keeps compatibility mandatory for a complete published baseline', () => {
    const completeVersion = { completeSnapshot: true }

    assert.equal(requiresWorkItemConfigurationCompatibility(completeVersion), true)
    assert.equal(
      isWorkItemConfigurationCompatibilityReady(completeVersion, {
        versionsQuerySucceeded: true,
        compatibilityQuerySucceeded: false,
      }),
      false,
    )
    assert.equal(
      isWorkItemConfigurationCompatibilityReady(completeVersion, {
        versionsQuerySucceeded: true,
        compatibilityQuerySucceeded: true,
      }),
      true,
    )
  })

  it('does not require compatibility when there is no published baseline', () => {
    assert.equal(requiresWorkItemConfigurationCompatibility(undefined), false)
    assert.equal(isWorkItemConfigurationCompatibilityReady(undefined, {
      versionsQuerySucceeded: true,
      compatibilityQuerySucceeded: false,
    }), true)
  })

  it('fails closed while the version list is unavailable', () => {
    assert.equal(isWorkItemConfigurationCompatibilityReady(undefined, {
      versionsQuerySucceeded: false,
      compatibilityQuerySucceeded: false,
    }), false)
    assert.equal(isWorkItemConfigurationCompatibilityReady(
      { completeSnapshot: false },
      {
        versionsQuerySucceeded: false,
        compatibilityQuerySucceeded: false,
      },
    ), false)
  })
})
