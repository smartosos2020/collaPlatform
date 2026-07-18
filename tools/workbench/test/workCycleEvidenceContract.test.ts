import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'
import { fileSignatures, gitHead } from '../src/lib/git.js'
import { runSync } from '../src/lib/process.js'
import { assertFinishBrowserOptions } from '../src/workcycle/cycle.js'
import { assertGitDiffClean, assertWorkCycleDocuments } from '../src/workcycle/quality.js'

const task = 'TEST-M1-T01'

function fixture(contract = '| TEST-M1-T01 | static | not-required | not-required | No | No real flow required |', browserEvidence?: object): string {
  const root = mkdtempSync(join(tmpdir(), 'colla-evidence-contract-'))
  mkdirSync(join(root, 'docs/02-roadmap'), { recursive: true })
  mkdirSync(join(root, 'docs/90-reports'), { recursive: true })
  mkdirSync(join(root, '.local-reports'), { recursive: true })
  const roadmapPath = 'docs/02-roadmap/current-roadmap.md'
  const reportPath = 'docs/90-reports/test-m1-execution-report.md'
  writeFileSync(join(root, roadmapPath), `| ${task} | Verify module | Static check | Pending |\n`)
  writeFileSync(join(root, reportPath), '# placeholder\n')
  runSync('git', ['init', '-q'], { cwd: root })
  runSync('git', ['config', 'user.email', 'test@example.invalid'], { cwd: root })
  runSync('git', ['config', 'user.name', 'Test'], { cwd: root })
  runSync('git', ['add', '.'], { cwd: root })
  runSync('git', ['commit', '-q', '-m', 'baseline'], { cwd: root })
  const baselineCommit = gitHead(root)
  const startedAt = new Date(Date.now() - 60_000).toISOString()
  writeFileSync(join(root, roadmapPath), `| ${task} | Verify module | Static check | Done |\n`)
  const qualityLog = 'quality-gate-20260718T120000-test.log'
  writeFileSync(join(root, '.local-reports', qualityLog), 'passed')
  writeFileSync(join(root, reportPath), [
    '# TEST-M1 Execution Report', '', '## Scope', task, '', '## Verification Contract',
    '| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |',
    '| --- | --- | --- | --- | --- | --- |', contract, '', '## Completed Items', '- Completed', '',
    '## Acceptance Evidence',
    '| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |',
    '| --- | --- | --- | --- | --- | --- |',
    `| ${task} | Render dashboard | src/module.ts | unit test passed | No browser required for static module | Done |`, '',
    '## Code Changes', '- src/module.ts', '', '## Validation', '- Backend tests: targeted tests passed',
    '- Frontend build: build passed', `- Local quality gate: ${qualityLog}`, '- Browser smoke: explicitly not required for static module', '',
    '## Remaining Gaps', '| Related task | Gap | Acceptance effect | Tracking |', '| --- | --- | --- | --- |',
    '| N/A | None | non-blocking | Closed |', '', '## Next Steps', '- Merge after review', '',
  ].join('\n'))
  const defaultEvidence = { status: 'not_required', kind: 'not-required', environment: 'not-required', reason: 'Static-only change has no browser-visible behavior.', completedAt: new Date().toISOString() }
  writeFileSync(join(root, '.local-reports/work-cycle-current.json'), JSON.stringify({
    docMode: 'code-doc-report', startedAt, taskRange: task, baselineCommit, baselineChangedPaths: [],
    baselineFileSignatures: fileSignatures(root, []), requiredDocs: [roadmapPath, reportPath],
    allowedActiveDocs: [roadmapPath], allowedReportDir: 'docs/90-reports', evidencePolicy: { contractVersion: 2 },
    browserEvidence: browserEvidence ?? defaultEvidence,
    workScope: { scopeValid: true, expectedTasks: [task], milestoneCount: 1, maxMilestonesPerCycle: 1 },
  }, null, 2))
  return root
}

test('verification contract v2 accepts a complete non-browser task', () => {
  assert.doesNotThrow(() => assertWorkCycleDocuments(fixture(), true))
})

test('verification contract requires exactly one row per expected task', () => {
  const root = fixture('')
  assert.throws(() => assertWorkCycleDocuments(root, true), /exactly one six-column row/)
})

test('core closure acceptance requires isolated real E2E evidence', () => {
  const root = fixture()
  const report = join(root, 'docs/90-reports/test-m1-execution-report.md')
  writeFileSync(report, readFileSync(report, 'utf8').replace('Render dashboard', 'Update user permission'))
  assert.throws(() => assertWorkCycleDocuments(root, true), /requires e2e-real-isolated/)
})

test('remaining acceptance gaps block a Done task', () => {
  const root = fixture()
  const report = join(root, 'docs/90-reports/test-m1-execution-report.md')
  writeFileSync(report, readFileSync(report, 'utf8').replace('| N/A | None | non-blocking | Closed |', `| ${task} | Still missing implementation | blocking | issue-1 |`))
  assert.throws(() => assertWorkCycleDocuments(root, true), /acceptance-blocking Remaining Gap/)
})

test('browser evidence must match the declared kind and environment', () => {
  const root = fixture('| TEST-M1-T01 | e2e-real | real | shared-readonly | No | Open dashboard in a real browser |', {
    status: 'passed', kind: 'mock', environment: 'mock', command: 'playwright test', logPath: '.local-reports/browser.log', completedAt: new Date().toISOString(),
  })
  writeFileSync(join(root, '.local-reports/browser.log'), 'passed')
  const report = join(root, 'docs/90-reports/test-m1-execution-report.md')
  writeFileSync(report, readFileSync(report, 'utf8').replace('No browser required for static module', 'real browser flow passed'))
  assert.throws(() => assertWorkCycleDocuments(root, true), /must pass and be declared 'real'/)
})

test('document boundary rejects unrelated active document changes', () => {
  const root = fixture()
  mkdirSync(join(root, 'docs/04-reference'), { recursive: true })
  writeFileSync(join(root, 'docs/04-reference/unrelated.md'), 'unexpected')
  assert.throws(() => assertWorkCycleDocuments(root, true), /outside the work-cycle allowlist/)
})

test('finish always requires browser evidence or a specific reason', () => {
  assert.throws(() => assertFinishBrowserOptions({ stage: 'finish' }, 'code-doc-report'), /exactly one/)
  assert.throws(() => assertFinishBrowserOptions({ stage: 'finish', browserNotRequiredReason: 'too short' }, 'code-doc-report'), /at least 20/)
  assert.doesNotThrow(() => assertFinishBrowserOptions({ stage: 'finish', browserNotRequiredReason: 'Backend-only schema change has no browser-visible behavior.' }, 'code-doc-report'))
})

test('git diff check covers committed changes since the cycle baseline', () => {
  const root = fixture()
  writeFileSync(join(root, 'bad.txt'), 'trailing whitespace   \n')
  runSync('git', ['add', 'bad.txt'], { cwd: root })
  runSync('git', ['commit', '-q', '-m', 'bad whitespace'], { cwd: root })
  assert.throws(() => assertGitDiffClean(root), /whitespace errors|trailing whitespace|failed/)
})
