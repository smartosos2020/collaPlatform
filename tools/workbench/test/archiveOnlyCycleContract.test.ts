import assert from 'node:assert/strict'
import test from 'node:test'
import { requiresTaskEvidence } from '../src/workcycle/cycle.js'

test('archive-only finish does not require a milestone execution report', () => {
  assert.equal(requiresTaskEvidence('finish', 'archive-only'), false)
  assert.equal(requiresTaskEvidence('finish', 'code-doc-report'), true)
  assert.equal(requiresTaskEvidence('checkpoint', 'code-doc-report'), false)
})
