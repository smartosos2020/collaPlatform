import assert from 'node:assert/strict'
import test from 'node:test'
import { affectedAreas, createQualityGatePlan } from '../src/workcycle/gatePlan.js'

test('workbench changes do not trigger the frontend affected area', () => {
  const areas = affectedAreas([
    'tools/workbench/src/workcycle/quality.ts',
    'tools/workbench/test/gatePlan.test.ts',
  ])
  assert.deepEqual([...areas], ['workbench'])
})

test('unknown paths fall back to the conservative workspace area', () => {
  const areas = affectedAreas([
    '.github/workflows/ci.yml',
    'tools/capacity/src/runner.ts',
  ])
  assert.deepEqual([...areas], ['workspace'])
})

test('stage plans select affected audits without Flyway or Docker', () => {
  const plan = createQualityGatePlan({ mode: 'stage', areas: ['workbench'] })
  assert.equal(plan.has('workbench-typecheck'), true)
  assert.equal(plan.has('workbench-tests'), true)
  assert.equal(plan.has('active-platform'), true)
  assert.equal(plan.has('sensitive-data'), true)
  assert.equal(plan.has('flyway'), false)
  assert.equal(plan.has('docker'), false)
})

test('full plans retain the complete closure gate set', () => {
  const plan = createQualityGatePlan({ mode: 'full', areas: ['backend'] })
  for (const gate of ['docker', 'flyway', 'security-audit', 'documentation-structure', 'git-diff'] as const) {
    assert.equal(plan.has(gate), true, gate)
  }
})

test('legacy skip-audit suppresses audit gates without suppressing core contracts', () => {
  const plan = createQualityGatePlan({ mode: 'full', areas: ['backend'], skipAudit: true })
  assert.equal(plan.has('security-audit'), false)
  assert.equal(plan.has('flyway'), false)
  assert.equal(plan.has('work-cycle-documents'), true)
  assert.equal(plan.has('architecture'), true)
})
