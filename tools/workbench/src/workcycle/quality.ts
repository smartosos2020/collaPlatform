import { existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs'
import { basename, join } from 'node:path'
import { changedSinceBaseline, type GitBaseline } from '../lib/git.js'
import { runSync } from '../lib/process.js'
import { namingGuard } from '../knowledge/checks.js'
import { runSecurityAudit } from '../security/audit.js'
import { scanSensitiveData } from '../security/sensitiveScan.js'
import { activePlatformViolations } from '../security/platformGuard.js'
import { loadActivePlanningContract, planningSummary } from './planning.js'

export type BackendStrategy = 'compile' | 'targeted' | 'full' | 'skip'
export type FrontendStrategy = 'lint' | 'full' | 'skip'
export type CollaborationStrategy = 'test' | 'skip'
export interface QualityOptions {
  mode: 'quick' | 'stage' | 'full'
  backend: BackendStrategy
  backendTestPattern?: string
  frontend: FrontendStrategy
  collaboration: CollaborationStrategy
  skipDocker?: boolean
  skipAudit?: boolean
  compact?: boolean
}

interface WorkCycleContext extends GitBaseline {
  docMode: string
  status?: string
  startedAt: string
  taskRange?: string
  requiredDocs: string[]
  allowedActiveDocs?: string[]
  allowedReportDir?: string
  evidencePolicy?: { contractVersion?: number }
  browserEvidence?: {
    status: 'passed' | 'not_required' | string
    kind?: 'real' | 'mock' | 'not-required' | string
    environment?: 'isolated' | 'shared-readonly' | 'mock' | 'not-required' | string
    command?: string
    logPath?: string
    reason?: string
    completedAt?: string
  }
  workScope: { scopeValid: boolean; expectedTasks: string[]; milestoneCount: number; maxMilestonesPerCycle: number }
  planning?: {
    program: string
    programDoc: string
    targetArchitectureDoc: string
    programRevision: number
    stage: string
    stageFinalMilestone: string
    isStageFinalMilestone: boolean
  }
}

interface VerificationContract {
  level: 'static' | 'unit' | 'integration' | 'e2e-real' | 'e2e-real-isolated'
  browserKind: 'real' | 'mock' | 'not-required'
  environment: 'isolated' | 'shared-readonly' | 'mock' | 'not-required'
  mockAllowed: 'yes' | 'no'
  realFlow: string
}

function timestamp(): string {
  return new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, '')
}

function markdownCells(line: string): string[] {
  if (!line.trim().startsWith('|')) return []
  return line.trim().slice(1, -1).split(/(?<!\\)\|/).map((cell) => cell.replaceAll('\\|', '|').trim())
}

function assertConcrete(value: string, label: string): void {
  if (!value || /^(?:todo|tbd|pending|n\/?a)$/i.test(value) || /待补|待执行|稍后|占位/.test(value)) throw new Error(`${label} requires concrete evidence`)
}

function section(content: string, start: string, end: string): string {
  const match = content.match(new RegExp(`^${start.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*([\\s\\S]+?)\\s*^${end.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*$`, 'm'))
  if (!match) throw new Error(`Execution report section cannot be parsed: ${start}`)
  return match[1]
}

function isCoreClosure(criterion: string): boolean {
  return /(登录|认证|权限|创建|新增|修改|删除|停用|启用|密码|安全策略|会话|设备|交接|导出|审计|login|auth|permission|create|update|delete|disable|enable|password|security|session|device|handover|offboard|export|audit)/i.test(criterion)
}

function parseVerificationContracts(report: string, tasks: string[]): Map<string, VerificationContract> {
  const rows = section(report, '## Verification Contract', '## Completed Items').split(/\r?\n/).map(markdownCells)
    .filter((cells) => cells.length === 6 && cells[0] !== 'Task' && !/^-+$/.test(cells[0]))
  const contracts = new Map<string, VerificationContract>()
  for (const task of tasks) {
    const matches = rows.filter((cells) => cells[0] === task)
    if (matches.length !== 1) throw new Error(`Verification Contract must contain exactly one six-column row for ${task}; found ${matches.length}`)
    const cells = matches[0]
    cells.slice(1).forEach((value, index) => assertConcrete(value, `${task} verification contract field ${index + 1}`))
    const level = cells[1].toLowerCase() as VerificationContract['level']
    const browserKind = cells[2].toLowerCase() as VerificationContract['browserKind']
    const environment = cells[3].toLowerCase() as VerificationContract['environment']
    const mockAllowed = cells[4].toLowerCase() as VerificationContract['mockAllowed']
    if (!['static', 'unit', 'integration', 'e2e-real', 'e2e-real-isolated'].includes(level)) throw new Error(`${task} has an invalid verification level: ${cells[1]}`)
    if (!['real', 'mock', 'not-required'].includes(browserKind)) throw new Error(`${task} has an invalid browser evidence kind: ${cells[2]}`)
    if (!['isolated', 'shared-readonly', 'mock', 'not-required'].includes(environment)) throw new Error(`${task} has an invalid verification environment: ${cells[3]}`)
    if (!['yes', 'no'].includes(mockAllowed)) throw new Error(`${task} mock browser allowed must be Yes or No`)
    if (browserKind === 'real' && (!['isolated', 'shared-readonly'].includes(environment) || mockAllowed !== 'no')) throw new Error(`${task} real browser evidence requires isolated/shared-readonly environment and Mock browser allowed = No`)
    if (browserKind === 'mock' && (environment !== 'mock' || mockAllowed !== 'yes')) throw new Error(`${task} mock browser evidence requires Environment = mock and Mock browser allowed = Yes`)
    if (browserKind === 'not-required' && environment !== 'not-required') throw new Error(`${task} not-required browser evidence requires Environment = not-required`)
    if (level === 'e2e-real' && (browserKind !== 'real' || mockAllowed !== 'no')) throw new Error(`${task} e2e-real requires real browser evidence and no mock`)
    if (level === 'e2e-real-isolated' && (browserKind !== 'real' || environment !== 'isolated' || mockAllowed !== 'no')) throw new Error(`${task} e2e-real-isolated requires real browser evidence in an isolated environment and no mock`)
    contracts.set(task, { level, browserKind, environment, mockAllowed, realFlow: cells[5] })
  }
  return contracts
}

function assertBrowserEvidence(root: string, context: WorkCycleContext, contracts: Map<string, VerificationContract>): void {
  const evidence = context.browserEvidence
  if (!evidence) throw new Error('Finish requires fresh browserEvidence in the active work-cycle context')
  if (!['passed', 'not_required'].includes(evidence.status)) throw new Error('Browser evidence status must be passed or not_required')
  const started = Date.parse(context.startedAt)
  const completed = Date.parse(evidence.completedAt ?? '')
  if (!Number.isFinite(completed) || completed < started) throw new Error('Browser evidence predates the active work cycle')
  if (evidence.status === 'passed') {
    const logPath = evidence.logPath ? (existsSync(evidence.logPath) ? evidence.logPath : join(root, evidence.logPath)) : ''
    if (!evidence.command?.trim() || !logPath || !existsSync(logPath)) throw new Error('Passed browser evidence must include the executed command and an existing log path')
    if (statSync(logPath).mtimeMs < started) throw new Error('Browser evidence log predates the active work cycle')
  } else if ((evidence.reason?.trim().length ?? 0) < 20) {
    throw new Error('Not-required browser evidence must include a specific reason of at least 20 characters')
  }
  if (!contracts.size) return
  const kinds = [...new Set([...contracts.values()].map((value) => value.browserKind).filter((value) => value !== 'not-required'))]
  if (kinds.length > 1) throw new Error('One finish command cannot close mixed real and mock browser contracts')
  if (!kinds.length) {
    if (evidence.status !== 'not_required') throw new Error('All Verification Contract rows are not-required, so finish must use a not-required reason')
    return
  }
  if (evidence.status !== 'passed' || evidence.kind !== kinds[0]) throw new Error(`Browser evidence must pass and be declared '${kinds[0]}' to satisfy the Verification Contract`)
  if ([...contracts.values()].some((value) => value.environment === 'isolated') && evidence.environment !== 'isolated') throw new Error('Verification Contract requires an isolated real browser environment')
  if (kinds[0] === 'mock' && evidence.environment !== 'mock') throw new Error('Mock Verification Contract requires browser evidence environment = mock')
}

function assertRemainingGaps(report: string, tasks: string[], roadmap: string[], contractV2: boolean): void {
  const gaps = section(report, '## Remaining Gaps', '## Next Steps').trim()
  if (!gaps || gaps === '-' || /^-\s*(?:todo|tbd|pending)?\s*$/i.test(gaps)) throw new Error('Execution report Remaining Gaps must explicitly state None or list concrete residual risks')
  if (!contractV2) return
  const rows = gaps.split(/\r?\n/).map(markdownCells).filter((cells) => cells.length === 4 && cells[0] !== 'Related task' && !/^-+$/.test(cells[0]))
  if (!rows.length) throw new Error('Verification contract v2 requires Remaining Gaps to use the four-column gap table')
  const blocking = /(未完成|尚未|仍未|缺失|缺少|不支持|未实现|待补|仍保留|仅(?:有|支持)|TODO|TBD|Pending|not implemented|not supported|still missing|remaining implementation|existing single)/i
  for (const [relatedTask, gap, effectValue, tracking] of rows) {
    const effect = effectValue.toLowerCase()
    if (relatedTask === 'N/A') {
      if (effect !== 'non-blocking') throw new Error('A non-task Remaining Gaps row must use Acceptance effect = non-blocking')
      continue
    }
    if (!tasks.includes(relatedTask)) {
      const matches = roadmap.map(markdownCells).filter((cells) => cells.length >= 4 && cells[0] === relatedTask)
      if (matches.length !== 1) throw new Error(`Remaining Gaps related task must be N/A, an expected task, or one unresolved roadmap task: '${relatedTask}'`)
      if (matches[0].at(-1) === 'Done') throw new Error(`${relatedTask} is already Done but still has a Remaining Gaps row`)
      continue
    }
    if (effect !== 'non-blocking') throw new Error(`${relatedTask} has an acceptance-blocking Remaining Gap and cannot finish as Done`)
    if (blocking.test(gap)) throw new Error(`${relatedTask} Remaining Gap describes incomplete acceptance work and cannot be classified as non-blocking`)
    assertConcrete(tracking, `${relatedTask} Remaining Gaps tracking`)
  }
}

function assertDocumentBoundary(root: string, context: WorkCycleContext, changed: string[]): void {
  const allowed = new Set([...(context.requiredDocs ?? []), ...(context.allowedActiveDocs ?? [])])
  for (const path of changed.filter((value) => value.startsWith('docs/') && value.endsWith('.md'))) {
    if (!existsSync(join(root, path))) continue
    if (path.startsWith('docs/99-archive/')) throw new Error(`docs/99-archive can only be edited in archive-only mode: ${path}`)
    if (/^docs\/.*roadmap.*\.md$/i.test(path) && path !== 'docs/02-roadmap/current-roadmap.md') throw new Error(`Changed roadmap files are not allowed outside current-roadmap.md: ${path}`)
    if (/^docs\/m\d+.*\.md$/i.test(path)) throw new Error(`Milestone documents are not allowed in docs root: ${path}`)
    const reportDir = `${context.allowedReportDir ?? 'docs/90-reports'}/`
    if (!allowed.has(path) && !path.startsWith(reportDir) && !path.startsWith('docs/05-runbooks/')) throw new Error(`Document change is outside the work-cycle allowlist: ${path}`)
  }
}

export function assertWorkCycleDocuments(root: string, strict: boolean, freshLogs: string[] = []): void {
  const contextPath = join(root, '.local-reports', 'work-cycle-current.json')
  if (!existsSync(contextPath)) return
  const context = JSON.parse(readFileSync(contextPath, 'utf8').replace(/^\uFEFF/, '')) as WorkCycleContext
  if (context.status && !['in-progress', 'in_progress'].includes(context.status)) return
  if (context.docMode === 'archive-only') return
  if (!context.workScope?.scopeValid || context.workScope.milestoneCount > (context.workScope.maxMilestonesPerCycle ?? 1)) throw new Error('Active work-cycle scope is invalid')
  if (context.planning?.program) {
    const planning = loadActivePlanningContract(root)
    if (planning.program !== context.planning.program || planning.stage !== context.planning.stage) throw new Error('Work-cycle planning context no longer matches the active Program and Stage')
    if (context.planning.isStageFinalMilestone && strict && planning.roadmapStatus !== 'completed') throw new Error(`Final Stage milestone requires ${planning.roadmapPath} status = completed before finish`)
    if (context.planning.isStageFinalMilestone && strict && planning.programRevision <= context.planning.programRevision) throw new Error('Final Stage milestone must increment program_revision and update the Program change record')
    if (!context.planning.isStageFinalMilestone && planning.programRevision !== context.planning.programRevision) throw new Error('A non-final milestone cannot close against a changed Program revision; restart the cycle')
    if (!context.planning.isStageFinalMilestone && planning.roadmapStatus !== 'active') throw new Error('A non-final milestone cannot finish against a completed Stage roadmap')
  }
  const changed = changedSinceBaseline(root, context)
  for (const doc of context.requiredDocs) {
    if (!existsSync(join(root, doc))) throw new Error(`Required work-cycle document is missing: ${doc}`)
    if (strict && !changed.includes(doc)) throw new Error(`Full work-cycle finish requires updating ${doc}`)
  }
  if (!strict) return
  const reportPath = context.requiredDocs.find((path) => path.startsWith('docs/90-reports/'))
  if (!reportPath) throw new Error('Work-cycle execution report path is missing')
  const report = readFileSync(join(root, reportPath), 'utf8')
  for (const heading of ['## Scope', '## Verification Contract', '## Completed Items', '## Acceptance Evidence', '## Code Changes', '## Validation', '## Remaining Gaps', '## Next Steps']) {
    if (!report.includes(heading)) throw new Error(`Execution report is missing required heading: ${heading}`)
  }
  const expectedTasks = context.workScope.expectedTasks ?? []
  if (!expectedTasks.length) throw new Error('Active work-cycle context has no expectedTasks')
  if (context.taskRange && !report.includes(context.taskRange) && expectedTasks.some((task) => !report.includes(task))) {
    throw new Error('Execution report Scope must include the active TaskRange or every expected task ID')
  }
  const contractV2 = (context.evidencePolicy?.contractVersion ?? 0) >= 2
  const contracts = contractV2 ? parseVerificationContracts(report, expectedTasks) : new Map<string, VerificationContract>()
  const roadmap = readFileSync(join(root, 'docs/02-roadmap/current-roadmap.md'), 'utf8').split(/\r?\n/)
  const acceptanceRows = section(report, '## Acceptance Evidence', '## Code Changes').split(/\r?\n/).map(markdownCells)
  for (const task of expectedTasks) {
    const acceptance = acceptanceRows.filter((cells) => cells.length === 6 && cells[0] === task)
    if (acceptance.length !== 1) throw new Error(`Acceptance Evidence must contain exactly one six-column row for ${task}; found ${acceptance.length}`)
    acceptance[0].slice(1, 5).forEach((value, index) => assertConcrete(value, `${task} evidence ${index + 1}`))
    if (acceptance[0][5] !== 'Done') throw new Error(`${task} cannot finish because Acceptance Evidence status is '${acceptance[0][5]}'`)
    if (contractV2) {
      const contract = contracts.get(task)!
      if (isCoreClosure(acceptance[0][1]) && contract.level !== 'e2e-real-isolated') throw new Error(`${task} acceptance criterion describes a core closure and requires e2e-real-isolated evidence`)
      if (contract.browserKind === 'real' && !/\breal\b/i.test(acceptance[0][4])) throw new Error(`${task} Browser evidence must explicitly state real`)
      if (contract.browserKind === 'mock' && !/\bmock\b/i.test(acceptance[0][4])) throw new Error(`${task} Browser evidence must explicitly state mock`)
    }
    const roadmapRows = roadmap.map(markdownCells).filter((cells) => cells.length >= 4 && cells[0] === task)
    if (roadmapRows.length !== 1 || roadmapRows[0].at(-1) !== 'Done') throw new Error(`${task} roadmap status must be Done before finish`)
  }
  const validation = report.match(/## Validation\s*([\s\S]+?)\s*## Remaining Gaps/)?.[1] ?? ''
  for (const label of ['Backend tests', 'Frontend build', 'Local quality gate', 'Browser smoke']) {
    const value = validation.match(new RegExp(`- ${label}:\\s*([^\\r\\n]+)`))?.[1] ?? ''
    assertConcrete(value, `Validation ${label}`)
  }
  const referenced = [...validation.matchAll(/quality-gate-\d{8}T?\d{6}[A-Za-z0-9_.-]*\.log/g)].map((match) => match[0])
  const started = new Date(context.startedAt).getTime()
  const validLog = referenced.some((name) => {
    const path = join(root, '.local-reports', name)
    return existsSync(path) && statSync(path).mtimeMs >= started
  })
  if (!validLog) throw new Error(`Execution report Validation must reference a fresh quality-gate log. Available: ${freshLogs.map((path) => basename(path)).join(', ')}`)
  assertRemainingGaps(report, expectedTasks, roadmap, contractV2)
  assertBrowserEvidence(root, context, contracts)
  assertDocumentBoundary(root, context, changed)
}

export function assertGitDiffClean(root: string): void {
  const contextPath = join(root, '.local-reports', 'work-cycle-current.json')
  if (existsSync(contextPath)) {
    const context = JSON.parse(readFileSync(contextPath, 'utf8').replace(/^\uFEFF/, '')) as Partial<WorkCycleContext>
    if (context.baselineCommit) runSync('git', ['diff', '--check', `${context.baselineCommit}..HEAD`, '--'], { cwd: root })
  }
  runSync('git', ['diff', '--check'], { cwd: root })
  runSync('git', ['diff', '--cached', '--check'], { cwd: root })
}

function flywayCheck(root: string): void {
  const names = readdirSync(join(root, 'server/src/main/resources/db/migration')).filter((name) => /^V.*\.sql$/.test(name)).sort()
  const versions = names.map((name) => {
    const match = /^V(\d{3})__.+\.sql$/.exec(name)
    if (!match) throw new Error(`Invalid migration name: ${name}`)
    return Number(match[1])
  })
  versions.forEach((value, index) => { if (value !== index + 1) throw new Error(`Expected V${String(index + 1).padStart(3, '0')}, found V${String(value).padStart(3, '0')}`) })
}

export async function runQualityGate(root: string, options: QualityOptions): Promise<{ report: string; logs: string[] }> {
  const directory = join(root, '.local-reports')
  mkdirSync(directory, { recursive: true })
  const stamp = timestamp()
  const results: string[] = []
  const failures: string[] = []
  const logs: string[] = []
  const step = (name: string, action: () => void): void => {
    try { action(); results.push(`- PASS: ${name}`); console.log(`PASS: ${name}`) }
    catch (error) { const message = error instanceof Error ? error.message : String(error); failures.push(`- FAIL: ${name} - ${message}`); console.error(`FAIL: ${name} - ${message}`) }
  }
  const command = (name: string, executable: string, args: string[], cwd = root): void => step(name, () => {
    const output = runSync(executable, args, { cwd })
    const path = join(directory, `quality-gate-${stamp}-${name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}.log`)
    writeFileSync(path, output)
    logs.push(path)
    if (!options.compact && output) console.log(output)
  })

  step('Toolchain', () => { for (const [cmd, args] of [['java', ['-version']], ['mvn', ['-version']], ['node', ['--version']], ['pnpm', ['--version']], ['docker', ['--version']]] as const) runSync(cmd, [...args], { cwd: root }) })
  step('Active planning contract', () => console.log(planningSummary(loadActivePlanningContract(root))))
  if (!options.skipDocker) command('Docker dependencies', 'docker', ['compose', 'up', '-d', 'postgres', 'redis', 'minio'])
  if (options.backend === 'compile') command('Backend compile', 'mvn', ['-DskipTests', 'test'], join(root, 'server'))
  if (options.backend === 'targeted') {
    if (!options.backendTestPattern) throw new Error('Targeted backend verification requires --backend-test-pattern')
    command('Backend targeted tests', 'mvn', [`-Dtest=${options.backendTestPattern}`, 'test'], join(root, 'server'))
  }
  if (options.backend === 'full') command('Backend tests', 'mvn', ['test'], join(root, 'server'))
  if (options.mode === 'full' && options.backend !== 'skip') command('Backend package', 'mvn', ['-DskipTests', 'package'], join(root, 'server'))
  if (options.frontend !== 'skip') command('Frontend lint', 'pnpm', ['web:lint'])
  if (options.frontend === 'full') {
    command('Frontend build', 'pnpm', ['web:build'])
    step('Frontend chunk budget', () => {
      const oversized = readdirSync(join(root, 'web/dist/assets')).filter((name) => name.endsWith('.js') && statSync(join(root, 'web/dist/assets', name)).size > 500 * 1024)
      if (oversized.length) throw new Error(`JavaScript chunks exceed 500KB: ${oversized.join(', ')}`)
    })
  }
  if (options.collaboration === 'test') command('Collaboration tests', 'pnpm', ['collaboration:test'])
  if (!options.skipAudit) {
    step('Sensitive data scan', () => { const value = scanSensitiveData(root, { writeReport: false }); if (value.hits.length) throw new Error(value.hits.map((hit) => `${hit.path}:${hit.line}`).join(', ')) })
    step('Security audit guardrails', () => { const value = runSecurityAudit(root); if (value.failures.length) throw new Error(value.failures.join('; ')) })
    step('Flyway migration order', () => flywayCheck(root))
    step('Knowledge naming guard', () => { const findings = namingGuard(root); if (findings.length) throw new Error(findings.join('; ')) })
    step('Active script platform', () => {
      const violations = activePlatformViolations(root)
      if (violations.length) throw new Error(violations.join('; '))
    })
  }
  step('Work-cycle documentation contract', () => assertWorkCycleDocuments(root, ['stage', 'full'].includes(options.mode), logs))
  if (['stage', 'full'].includes(options.mode)) step('Git diff whitespace and conflict check', () => assertGitDiffClean(root))
  const report = join(directory, `quality-gate-${stamp}.md`)
  writeFileSync(report, ['# AI Quality Gate', '', `- Status: ${failures.length ? 'FAIL' : 'PASS'}`, `- Mode: ${options.mode}`, `- Time: ${new Date().toISOString()}`, '', '## Results', ...results, '', '## Failures', ...(failures.length ? failures : ['- None']), ''].join('\n'))
  if (failures.length) throw new Error(`Quality gate failed (${failures.length}); report: ${report}`)
  console.log(`Quality gate report: ${report}`)
  return { report, logs }
}
