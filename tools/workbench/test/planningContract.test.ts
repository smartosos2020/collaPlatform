import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'
import { assertTaskScopeInPlanning, loadActivePlanningContract } from '../src/workcycle/planning.js'

function fixture(options: { secondActive?: boolean; foreignTask?: boolean; completed?: boolean; staleCurrentStage?: boolean } = {}): string {
  const root = mkdtempSync(join(tmpdir(), 'colla-planning-contract-'))
  mkdirSync(join(root, 'docs/00-product/initiatives'), { recursive: true })
  mkdirSync(join(root, 'docs/01-architecture'), { recursive: true })
  mkdirSync(join(root, 'docs/02-roadmap'), { recursive: true })
  const completed = Boolean(options.completed)
  writeFileSync(join(root, 'docs/00-product/initiatives/test-program.md'), [
    '---', 'status: active', 'program: TEST', 'revision: 3', `current_stage: ${completed && !options.staleCurrentStage ? 'none' : 'TEST-S01'}`,
    'target_architecture_doc: docs/01-architecture/test-target.md', '---', '',
    '| Stage | Goal | Status |', '| --- | --- | --- |',
    `| TEST-S01 | Foundation | ${completed ? 'Completed' : 'Active'} |`,
    `| TEST-S02 | Runtime | ${options.secondActive ? 'Active' : 'Planned'} |`, '',
  ].join('\n'))
  writeFileSync(join(root, 'docs/01-architecture/test-target.md'), ['---', 'status: target', 'program: TEST', '---', '', '# Target'].join('\n'))
  const task = options.foreignTask ? 'TEST-S02-M1-T01' : 'TEST-S01-M1-T01'
  writeFileSync(join(root, 'docs/02-roadmap/current-roadmap.md'), [
    '---', 'title: Test route', `status: ${completed ? 'completed' : 'active'}`, 'route: TEST-S01', 'program: TEST',
    'program_doc: docs/00-product/initiatives/test-program.md', 'program_revision: 3', 'stage: TEST-S01',
    'stage_final_milestone: TEST-S01-M1', '---', '',
    '| Task | Content | Acceptance | Status |', '| --- | --- | --- | --- |', `| ${task} | Check | Pass | Pending |`, '',
  ].join('\n'))
  return root
}

test('accepts one active Stage linked to one current roadmap', () => {
  const contract = loadActivePlanningContract(fixture())
  assert.equal(contract.stage, 'TEST-S01')
  assert.equal(contract.programRevision, 3)
  assert.doesNotThrow(() => assertTaskScopeInPlanning(contract, 'TEST-S01-M1', ['TEST-S01-M1-T01']))
})

test('rejects multiple active Stages', () => {
  assert.throws(() => loadActivePlanningContract(fixture({ secondActive: true })), /exactly one Active Stage/)
})

test('rejects a task outside the current Stage', () => {
  assert.throws(() => loadActivePlanningContract(fixture({ foreignTask: true })), /outside TEST-S01/)
})

test('accepts a completed Stage with no other active Stage', () => {
  assert.equal(loadActivePlanningContract(fixture({ completed: true })).roadmapStatus, 'completed')
})

test('rejects a completed Stage that remains current', () => {
  assert.throws(() => loadActivePlanningContract(fixture({ completed: true, staleCurrentStage: true })), /current_stage must be none/)
})

test('rejects task ranges that are absent or already complete', () => {
  const contract = loadActivePlanningContract(fixture())
  assert.throws(() => assertTaskScopeInPlanning(contract, 'TEST-S01-M1', ['TEST-S01-M1-T02']), /not declared/)
  contract.taskRows.set('TEST-S01-M1-T01', 'Done')
  assert.throws(() => assertTaskScopeInPlanning(contract, 'TEST-S01-M1', ['TEST-S01-M1-T01']), /already Done/)
})
