import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { changedSinceBaseline, fileSignatures, gitHead, gitStatusPaths, type GitBaseline } from '../lib/git.js'
import { repositoryRoot } from '../lib/paths.js'
import { run } from '../lib/process.js'
import { assertRealBrowserEvidence } from '../security/browserEvidence.js'
import { auditSnapshot } from '../audit/snapshot.js'
import { runQualityGate, type BackendStrategy, type FrontendStrategy, type QualityGateEvidence } from './quality.js'
import { assertTaskScopeInPlanning, loadActivePlanningContract } from './planning.js'

export interface WorkCycleOptions {
  stage: 'start' | 'checkpoint' | 'finish'
  goal?: string
  taskRange?: string
  docMode?: 'code-doc-report' | 'archive-only'
  validationProfile?: 'light' | 'stage' | 'route-final'
  backendTestPattern?: string
  browserSpecs?: string[]
  browserGrep?: string
  browserEvidenceKind?: 'real' | 'mock'
  browserEvidenceEnvironment?: 'isolated' | 'shared-readonly' | 'mock'
  browserNotRequiredReason?: string
  force?: boolean
}

interface WorkContext extends GitBaseline {
  goal: string; status: string; taskRange: string; milestone: string; docMode: string; startedAt: string
  completedAt?: string
  requiredDocs: string[]; workScope: { scopeValid: boolean; expectedTasks: string[]; milestoneCount: number; maxMilestonesPerCycle: number }
  allowedActiveDocs: string[]; allowedReportDir: string
  evidencePolicy: { contractVersion: number }
  browserEvidence?: Record<string, string>
  lastQualityGate?: QualityGateEvidence
  auditSnapshots?: string[]
  planning: {
    program: string
    programDoc: string
    initiativeIndexDoc: string
    targetArchitectureDoc: string
    programRevision: number
    stage: string
    stageFinalMilestone: string
    isStageFinalMilestone: boolean
  }
}

const reportDir = join(repositoryRoot, '.local-reports')
const contextPath = join(reportDir, 'work-cycle-current.json')

export function parseTaskScope(range: string): WorkContext['workScope'] & { milestone: string } {
  const refs = [...range.toUpperCase().matchAll(/(?<![A-Z0-9])((?:[A-Z][A-Z0-9]*-)*M\d{1,3})-T(\d{2})(?!\d)/g)]
  const milestones = [...new Set(refs.map((match) => match[1]))]
  const valid = !range || (refs.length > 0 && milestones.length === 1)
  const expectedTasks = refs.length && milestones.length === 1
    ? Array.from({ length: Math.abs(Number(refs.at(-1)![2]) - Number(refs[0][2])) + 1 }, (_, index) => `${milestones[0]}-T${String(Math.min(Number(refs[0][2]), Number(refs.at(-1)![2])) + index).padStart(2, '0')}`)
    : []
  return { scopeValid: valid, expectedTasks, milestoneCount: milestones.length, maxMilestonesPerCycle: 1, milestone: milestones[0] ?? '' }
}

function writeContext(context: WorkContext): void {
  mkdirSync(reportDir, { recursive: true })
  writeFileSync(contextPath, JSON.stringify(context, null, 2))
}

function reportTemplate(context: WorkContext): string {
  return [`# ${context.milestone} Execution Report`, '', '## Scope', context.taskRange, '', '## Verification Contract', '| Task | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |', '| --- | --- | --- | --- | --- | --- |', ...context.workScope.expectedTasks.map((task) => `| ${task} | Pending | Pending | Pending | Pending | Pending |`), '', '## Completed Items', '- Pending', '', '## Acceptance Evidence', '| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |', '| --- | --- | --- | --- | --- | --- |', ...context.workScope.expectedTasks.map((task) => `| ${task} | Pending | Pending | Pending | Pending | Pending |`), '', '## Code Changes', '- Pending', '', '## Validation', '- Backend tests: Pending', '- Frontend build: Pending', '- Local quality gate: Pending', '- Browser smoke: Pending', '', '## Remaining Gaps', '| Related task | Gap | Acceptance effect | Tracking |', '| --- | --- | --- | --- |', '| N/A | None | non-blocking | Closed |', '', '## Next Steps', '- Pending', ''].join('\n')
}

function start(options: WorkCycleOptions): void {
  if (existsSync(contextPath) && !options.force) {
    const existing = JSON.parse(readFileSync(contextPath, 'utf8')) as WorkContext
    if (existing.status === 'in-progress') throw new Error(`A work cycle is already active: ${existing.goal}`)
  }
  const taskRange = options.taskRange ?? ''
  const scope = parseTaskScope(taskRange)
  if (options.docMode !== 'archive-only' && !scope.scopeValid) throw new Error('Task range must remain within one milestone and use PREFIX-MX-TYY references')
  const planning = options.docMode === 'archive-only' ? undefined : loadActivePlanningContract(repositoryRoot)
  if (planning) assertTaskScopeInPlanning(planning, scope.milestone, scope.expectedTasks)
  const report = scope.milestone ? `docs/90-reports/${scope.milestone.toLowerCase()}-execution-report.md` : ''
  const isStageFinalMilestone = planning?.stageFinalMilestone === scope.milestone
  const requiredDocs = options.docMode === 'archive-only' ? [] : ['docs/02-roadmap/current-roadmap.md', report, ...(isStageFinalMilestone && planning ? [planning.programDoc, planning.initiativeIndexDoc, planning.targetArchitectureDoc] : [])].filter(Boolean)
  const baselineChangedPaths = gitStatusPaths(repositoryRoot)
  const context: WorkContext = {
    goal: options.goal ?? '', status: 'in-progress', taskRange, milestone: scope.milestone, docMode: options.docMode ?? 'code-doc-report', startedAt: new Date().toISOString(),
    baselineCommit: gitHead(repositoryRoot), baselineChangedPaths, baselineFileSignatures: fileSignatures(repositoryRoot, [...baselineChangedPaths, ...requiredDocs]), requiredDocs, workScope: scope,
    allowedActiveDocs: [
      'docs/README.md',
      'docs/00-product/current-product-scope.md',
      ...(planning ? [planning.initiativeIndexDoc] : []),
      ...(planning ? [planning.programDoc] : []),
      'docs/01-architecture/current-architecture.md',
      ...(planning ? [planning.targetArchitectureDoc] : []),
      'docs/01-architecture/technology-selection.md',
      'docs/01-architecture/platform-object-model.md',
      'docs/02-roadmap/current-roadmap.md',
      'docs/03-engineering/ai-engineering-governance.md',
    ],
    allowedReportDir: 'docs/90-reports',
    evidencePolicy: { contractVersion: 2 },
    planning: planning ? {
      program: planning.program,
      programDoc: planning.programDoc,
      initiativeIndexDoc: planning.initiativeIndexDoc,
      targetArchitectureDoc: planning.targetArchitectureDoc,
      programRevision: planning.programRevision,
      stage: planning.stage,
      stageFinalMilestone: planning.stageFinalMilestone,
      isStageFinalMilestone: Boolean(isStageFinalMilestone),
    } : {
      program: '', programDoc: '', initiativeIndexDoc: '', targetArchitectureDoc: '', programRevision: 0, stage: '', stageFinalMilestone: '', isStageFinalMilestone: false,
    },
  }
  if (report && !existsSync(join(repositoryRoot, report))) {
    mkdirSync(join(repositoryRoot, 'docs/90-reports'), { recursive: true })
    writeFileSync(join(repositoryRoot, report), reportTemplate(context))
  }
  writeContext(context)
  context.auditSnapshots = [auditSnapshot(repositoryRoot, `start-${context.goal}`, 'full')]
  writeContext(context)
  console.log(`Work cycle started: ${context.goal}; milestone=${context.milestone}; tasks=${context.workScope.expectedTasks.length}`)
}

function affected(context: WorkContext): Set<string> {
  const areas = new Set<string>()
  for (const path of changedSinceBaseline(repositoryRoot, context)) {
    if (/^server\//.test(path)) areas.add('backend')
    if (/^(web\/|package\.json|pnpm-lock\.yaml|tools\/workbench\/)/.test(path)) areas.add('frontend')
    if (/^collaboration\//.test(path)) areas.add('collaboration')
    if (/^(docs\/|scripts\/|deploy\/|tools\/workbench\/)/.test(path)) areas.add('workbench')
  }
  return areas
}

export function assertFinishBrowserOptions(options: WorkCycleOptions, docMode: string): void {
  const hasSpecs = (options.browserSpecs?.length ?? 0) > 0
  const reason = options.browserNotRequiredReason?.trim() ?? ''
  if (options.stage === 'finish' && docMode === 'code-doc-report' && hasSpecs === Boolean(reason)) throw new Error('Finish requires exactly one of --browser-spec or --browser-not-required-reason')
  if (reason && (options.browserEvidenceKind || options.browserEvidenceEnvironment)) throw new Error('Browser evidence kind/environment are only valid with --browser-spec')
  if (reason && reason.length < 20) throw new Error('--browser-not-required-reason must be specific and at least 20 characters long')
  if (hasSpecs && (!options.browserEvidenceKind || !options.browserEvidenceEnvironment)) throw new Error('--browser-spec requires --browser-evidence-kind and --browser-evidence-environment')
  if (options.browserEvidenceKind === 'real' && !['isolated', 'shared-readonly'].includes(options.browserEvidenceEnvironment ?? '')) throw new Error('Real browser evidence must use an isolated or shared-readonly environment')
  if (options.browserEvidenceKind === 'mock' && options.browserEvidenceEnvironment !== 'mock') throw new Error('Mock browser evidence must use the mock environment')
}

export function assertStageFinalValidationProfile(stage: WorkCycleOptions['stage'], isStageFinalMilestone: boolean, profile: NonNullable<WorkCycleOptions['validationProfile']>, milestone: string): void {
  if (stage === 'finish' && isStageFinalMilestone && profile !== 'route-final') throw new Error(`The final milestone ${milestone} must finish with --validation-profile route-final`)
}

async function verify(options: WorkCycleOptions): Promise<void> {
  if (!existsSync(contextPath)) throw new Error('No active work cycle; run work:start first')
  const context = JSON.parse(readFileSync(contextPath, 'utf8')) as WorkContext
  if (context.status !== 'in-progress') throw new Error(`Work cycle is ${context.status}, not in-progress`)
  const areas = affected(context)
  const profile = options.validationProfile ?? (options.stage === 'finish' ? 'stage' : 'light')
  const planning = loadActivePlanningContract(repositoryRoot)
  if (context.planning?.program && (planning.program !== context.planning.program || planning.stage !== context.planning.stage)) throw new Error('Active Program or Stage changed during the work cycle; restart the cycle after reviewing the planning change')
  if (context.planning?.program && planning.programRevision !== context.planning.programRevision && !(options.stage === 'finish' && context.planning.isStageFinalMilestone)) throw new Error('Program revision changed during the work cycle; restart after reviewing the planning change')
  assertStageFinalValidationProfile(options.stage, Boolean(context.planning?.isStageFinalMilestone), profile, context.milestone)
  let backend: BackendStrategy = areas.has('backend') ? 'compile' : 'skip'
  let frontend: FrontendStrategy = areas.has('frontend') ? 'lint' : 'skip'
  if (profile === 'stage' && areas.has('backend')) backend = 'targeted'
  if (profile === 'stage' && areas.has('frontend')) frontend = 'full'
  if (profile === 'route-final') { backend = 'full'; frontend = 'full' }
  if (backend === 'targeted' && !options.backendTestPattern) throw new Error('Stage finish with backend changes requires --backend-test-pattern')

  const browserSpecs = options.browserSpecs ?? []
  const hasSpecs = browserSpecs.length > 0
  const hasReason = Boolean(options.browserNotRequiredReason?.trim())
  assertFinishBrowserOptions(options, context.docMode)
  if (hasSpecs) {
    const evidenceKind = options.browserEvidenceKind!
    const evidenceEnvironment = options.browserEvidenceEnvironment!
    const args = ['--dir', 'web', 'exec', 'playwright', 'test', ...browserSpecs]
    if (options.browserGrep) args.push('--grep', options.browserGrep)
    if (evidenceKind === 'real') assertRealBrowserEvidence(browserSpecs.join(' '), repositoryRoot)
    const output = await run('pnpm', args, { cwd: repositoryRoot, capture: true, trimOutput: false })
    const browserLog = join(reportDir, `work-cycle-browser-${new Date().toISOString().replace(/[-:]/g, '').replace(/\..+$/, '')}.log`)
    writeFileSync(browserLog, output)
    if (output) console.log(output)
    context.browserEvidence = {
      status: 'passed', kind: evidenceKind, environment: evidenceEnvironment,
      command: `pnpm ${args.join(' ')}`, logPath: browserLog, completedAt: new Date().toISOString(),
    }
  } else if (hasReason) {
    const reason = options.browserNotRequiredReason!.trim()
    context.browserEvidence = { status: 'not_required', kind: 'not-required', environment: 'not-required', reason, completedAt: new Date().toISOString() }
  }
  writeContext(context)
  const quality = await runQualityGate(repositoryRoot, { mode: profile === 'route-final' ? 'full' : options.stage === 'finish' ? 'stage' : 'quick', backend, backendTestPattern: options.backendTestPattern, frontend, collaboration: areas.has('collaboration') || profile === 'route-final' ? 'test' : 'skip', skipDocker: profile !== 'route-final', skipAudit: profile !== 'route-final', compact: true })
  context.lastQualityGate = quality.evidence
  if (options.stage === 'finish') { context.status = 'complete'; context.completedAt = new Date().toISOString() }
  const snapshot = auditSnapshot(repositoryRoot, `${options.stage}-${context.goal}`, profile === 'route-final' ? 'full' : 'light')
  context.auditSnapshots = [...(context.auditSnapshots ?? []), snapshot]
  writeContext(context)
  console.log(`Work cycle ${options.stage} completed; affected=${[...areas].join(',') || 'none'}; profile=${profile}`)
}

export async function runWorkCycle(options: WorkCycleOptions): Promise<void> {
  if (options.stage === 'start') start(options)
  else await verify(options)
}
