import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'
import { fileSignatures, gitHead } from '../src/lib/git.js'
import { runSync } from '../src/lib/process.js'
import { assertWorkCycleDocuments } from '../src/workcycle/quality.js'

test('strict finish rejects an unchanged required document', () => {
  const root = mkdtempSync(join(tmpdir(), 'colla-stage-contract-'))
  mkdirSync(join(root, 'docs/02-roadmap'), { recursive: true }); mkdirSync(join(root, 'docs/90-reports'), { recursive: true }); mkdirSync(join(root, '.local-reports'), { recursive: true })
  writeFileSync(join(root, 'docs/02-roadmap/current-roadmap.md'), '| TEST-M1-T01 | x | x | Done |')
  writeFileSync(join(root, 'docs/90-reports/test-m1-execution-report.md'), '# report')
  runSync('git', ['init', '-q'], { cwd: root }); runSync('git', ['config', 'user.email', 'test@example.invalid'], { cwd: root }); runSync('git', ['config', 'user.name', 'Test'], { cwd: root }); runSync('git', ['add', '.'], { cwd: root }); runSync('git', ['commit', '-q', '-m', 'baseline'], { cwd: root })
  const requiredDocs = ['docs/02-roadmap/current-roadmap.md', 'docs/90-reports/test-m1-execution-report.md']
  writeFileSync(join(root, '.local-reports/work-cycle-current.json'), JSON.stringify({ docMode: 'code-doc-report', startedAt: new Date().toISOString(), baselineCommit: gitHead(root), baselineChangedPaths: [], baselineFileSignatures: fileSignatures(root, requiredDocs), requiredDocs, workScope: { scopeValid: true, expectedTasks: ['TEST-M1-T01'], milestoneCount: 1, maxMilestonesPerCycle: 1 } }))
  writeFileSync(join(root, 'docs/90-reports/test-m1-execution-report.md'), '# changed')
  assert.throws(() => assertWorkCycleDocuments(root, true), /current-roadmap\.md/)
})
