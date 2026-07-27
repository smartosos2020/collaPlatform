import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { changedSinceBaseline, fileSignatures, gitHead, gitStatusPaths } from '../lib/git.js'
import { repositoryRoot } from '../lib/paths.js'
import { run } from '../lib/process.js'
import { auditSnapshot } from '../audit/snapshot.js'
import { currentWorkContextSchemaVersion, migrateWorkContext, type WorkContext } from './contracts.js'
import { affectedAreas } from './gatePlan.js'
import { runQualityGate, type BackendStrategy, type FrontendStrategy } from './quality.js'
import { assertTaskScopeInPlanning, loadActivePlanningContract } from './planning.js'
import { executeSystemEvidence } from './systemEvidence.js'
import { parseVerificationContracts, systemRealTaskIds } from './verification.js'

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
  systemEvidenceCommand?: string
  systemEvidenceArgs?: string[]
  systemEvidenceCwd?: string
  force?: boolean
}

export function requiresTaskEvidence(
  stage: WorkCycleOptions['stage'],
  docMode: string,
): boolean {
  return stage === 'finish' && docMode !== 'archive-only'
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
  writeFileSync(contextPath, JSON.stringify({ ...context, schemaVersion: currentWorkContextSchemaVersion }, null, 2))
}

function readContext(): WorkContext {
  const context = JSON.parse(readFileSync(contextPath, 'utf8').replace(/^\uFEFF/, '')) as WorkContext
  return migrateWorkContext(context)
}

function reportTemplate(context: WorkContext): string {
  return [`# ${context.milestone} Execution Report`, '', '## Scope', context.taskRange, '', '## Verification Contract', '| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |', '| --- | --- | --- | --- | --- | --- | --- |', ...context.workScope.expectedTasks.map((task) => `| ${task} | Pending | Pending | Pending | Pending | Pending | Pending |`), '', '## Completed Items', '- Pending', '', '## Acceptance Evidence', '| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |', '| --- | --- | --- | --- | --- | --- |', ...context.workScope.expectedTasks.map((task) => `| ${task} | Pending | Pending | Pending | Pending | Pending |`), '', '## Code Changes', '- Pending', '', '## Validation', '- Backend tests: Pending', '- Frontend build: Pending', '- Local quality gate: Pending', '- Browser smoke: Pending', '', '## Remaining Gaps', '| Related task | Gap | Acceptance effect | Tracking |', '| --- | --- | --- | --- |', '| N/A | None | non-blocking | Closed |', '', '## Next Steps', '- Pending', ''].join('\n')
}

function start(options: WorkCycleOptions): void {
  if (existsSync(contextPath) && !options.force) {
    const existing = readContext()
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
    schemaVersion: currentWorkContextSchemaVersion,
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
      'docs/01-architecture/platform-module-contracts.md',
      'docs/01-architecture/event-side-effect-matrix.md',
      'docs/01-architecture/project-work-item-configuration-compatibility-matrix.md',
      'docs/02-roadmap/current-roadmap.md',
      'docs/03-engineering/ai-engineering-governance.md',
    ],
    allowedReportDir: 'docs/90-reports',
    evidencePolicy: { contractVersion: 3 },
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
  const context = readContext()
  if (context.status !== 'in-progress') throw new Error(`Work cycle is ${context.status}, not in-progress`)
  const areas = affectedAreas(changedSinceBaseline(repositoryRoot, context))
  const profile = options.validationProfile ?? (options.stage === 'finish' ? 'stage' : 'light')
  const planning = loadActivePlanningContract(repositoryRoot)
  context.allowedActiveDocs = [...new Set([
    ...(context.allowedActiveDocs ?? []),
    'docs/01-architecture/platform-module-contracts.md',
    'docs/01-architecture/event-side-effect-matrix.md',
    'docs/01-architecture/project-work-item-configuration-compatibility-matrix.md',
  ])]
  if (context.planning?.program && (planning.program !== context.planning.program || planning.stage !== context.planning.stage)) throw new Error('Active Program or Stage changed during the work cycle; restart the cycle after reviewing the planning change')
  if (context.planning?.program && planning.programRevision !== context.planning.programRevision && !(options.stage === 'finish' && context.planning.isStageFinalMilestone)) throw new Error('Program revision changed during the work cycle; restart after reviewing the planning change')
  assertStageFinalValidationProfile(options.stage, Boolean(context.planning?.isStageFinalMilestone), profile, context.milestone ?? '')
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
  if (requiresTaskEvidence(options.stage, context.docMode)) {
    const reportPath = context.requiredDocs.find((path) => path.startsWith('docs/90-reports/'))
    if (!reportPath) throw new Error('System evidence requires an execution report in requiredDocs')
    const report = readFileSync(join(repositoryRoot, reportPath), 'utf8')
    const contracts = parseVerificationContracts(report, context.workScope.expectedTasks, context.evidencePolicy?.contractVersion ?? 1)
    const systemTasks = systemRealTaskIds(contracts)
    if (systemTasks.length && !options.systemEvidenceCommand) {
      throw new Error(`system-real-isolated tasks require --system-evidence-command: ${systemTasks.join(', ')}`)
    }
    if (!systemTasks.length && options.systemEvidenceCommand) {
      throw new Error('--system-evidence-command was provided but no task declares system-real-isolated')
    }
    if (options.systemEvidenceCommand) {
      context.systemEvidence = await executeSystemEvidence(repositoryRoot, {
        executable: options.systemEvidenceCommand,
        args: options.systemEvidenceArgs,
        cwd: options.systemEvidenceCwd,
        tasks: systemTasks,
      })
    }
  }
  if (hasSpecs) {
    const evidenceKind = options.browserEvidenceKind!
    const evidenceEnvironment = options.browserEvidenceEnvironment!
    const webRoot = join(repositoryRoot, 'web')
    const playwrightCli = join(webRoot, 'node_modules', '@playwright', 'test', 'cli.js')
    if (!existsSync(playwrightCli)) throw new Error(`Playwright CLI is not installed: ${playwrightCli}`)
    const args = [playwrightCli, 'test', ...browserSpecs, '--config', 'e2e/playwright.config.ts']
    if (options.browserGrep) args.push('--grep', options.browserGrep)
    if (evidenceKind === 'real') {
      const { assertRealBrowserEvidence } = await import('../security/browserEvidence.js')
      assertRealBrowserEvidence(browserSpecs.join(' '), repositoryRoot)
    }
    const isolatedS11Route = evidenceKind === 'real'
      && evidenceEnvironment === 'isolated'
      && browserSpecs.length === 1
      && browserSpecs[0].replaceAll('\\', '/').endsWith('project-platform-s11-m5-route-final.spec.ts')
    let command = `node ${args.join(' ')}`
    let output: string
    if (isolatedS11Route) {
      const { isolatedProjectPlatformS11Smoke } = await import('../browser/smoke.js')
      await isolatedProjectPlatformS11Smoke(repositoryRoot)
      command = 'pnpm smoke:s11-m5-isolated'
      output = 'PROJECT-PLATFORM-S11 isolated route-final browser smoke passed.\n'
    } else {
      output = await run('node', args, {
        cwd: webRoot,
        env: { COLLA_E2E_SUITE: 'all' },
        capture: true,
        trimOutput: false,
      })
    }
    const browserLog = join(reportDir, `work-cycle-browser-${new Date().toISOString().replace(/[-:]/g, '').replace(/\..+$/, '')}.log`)
    writeFileSync(browserLog, output)
    if (output) console.log(output)
    context.browserEvidence = {
      status: 'passed', kind: evidenceKind, environment: evidenceEnvironment,
      command, logPath: browserLog, completedAt: new Date().toISOString(),
    }
  } else if (hasReason) {
    const reason = options.browserNotRequiredReason!.trim()
    context.browserEvidence = { status: 'not_required', kind: 'not-required', environment: 'not-required', reason, completedAt: new Date().toISOString() }
  }
  writeContext(context)
  const quality = await runQualityGate(repositoryRoot, {
    mode: profile === 'route-final' ? 'full' : options.stage === 'finish' ? 'stage' : 'quick',
    backend,
    backendTestPattern: options.backendTestPattern,
    frontend,
    collaboration: areas.has('collaboration') || profile === 'route-final' ? 'test' : 'skip',
    areas: [...areas],
    skipDocker: profile !== 'route-final',
    compact: true,
  })
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
