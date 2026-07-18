#!/usr/bin/env node
import { parseCliArgs, optionBoolean, optionString } from './lib/args.js'
import { repositoryRoot } from './lib/paths.js'
import { auditSnapshot } from './audit/snapshot.js'
import { consistencyCheck, inspectObjectReferences, namingGuard } from './knowledge/checks.js'
import { repairKnowledgeReference } from './knowledge/repair.js'
import { backup } from './operations/backup.js'
import { restoreDrill } from './operations/drill.js'
import { healthCheck } from './operations/health.js'
import { releaseCheck } from './operations/release.js'
import { restore } from './operations/restore.js'
import { rollback } from './operations/rollback.js'
import { resolveFrom } from './operations/common.js'
import { browserSmoke, isolatedM5Smoke } from './browser/smoke.js'
import { initializePilot } from './pilot/initialize.js'
import { simulationKickoff } from './pilot/kickoff.js'
import { readPilotManifest, validatePilotManifest, writeValidationReport } from './pilot/manifest.js'
import { pilotReadiness } from './pilot/readiness.js'
import { runPilotContract } from './pilot/contract.js'
import { checkM10Contract, runM10Simulation } from './pilot/m10.js'
import { runSecurityAudit } from './security/audit.js'
import { assertRealBrowserEvidence } from './security/browserEvidence.js'
import { scanSensitiveData } from './security/sensitiveScan.js'
import { runWorkCycle } from './workcycle/cycle.js'
import { runQualityGate, type BackendStrategy, type CollaborationStrategy, type FrontendStrategy } from './workcycle/quality.js'
import { loadActivePlanningContract, planningSummary } from './workcycle/planning.js'

function optionStrings(options: ReturnType<typeof parseCliArgs>['options'], name: string): string[] {
  const value = options[name]
  if (Array.isArray(value)) return value
  return typeof value === 'string' ? [value] : []
}
function optionNumber(options: ReturnType<typeof parseCliArgs>['options'], name: string, fallback: number): number {
  const value = Number(optionString(options, name, String(fallback))); if (!Number.isFinite(value)) throw new Error(`--${name} must be numeric`); return value
}

async function main(): Promise<void> {
  const { positionals, options } = parseCliArgs(process.argv.slice(2))
  const command = positionals.join(' ')
  if (command === 'audit snapshot') {
    const profile = optionString(options, 'profile', 'full') as 'light' | 'full'
    console.log(`Audit snapshot: ${auditSnapshot(repositoryRoot, optionString(options, 'label', 'manual'), profile)}`)
    return
  }
  if (command === 'work start' || command === 'work checkpoint' || command === 'work finish') {
    await runWorkCycle({
      stage: command.split(' ')[1] as 'start' | 'checkpoint' | 'finish', goal: optionString(options, 'goal'), taskRange: optionString(options, 'task-range'),
      docMode: optionString(options, 'doc-mode', 'code-doc-report') as 'code-doc-report' | 'archive-only', validationProfile: (optionString(options, 'validation-profile') || undefined) as 'light' | 'stage' | 'route-final' | undefined,
      backendTestPattern: optionString(options, 'backend-test-pattern') || undefined, browserSpecs: optionStrings(options, 'browser-spec'), browserGrep: optionString(options, 'browser-grep') || undefined,
      browserEvidenceKind: (optionString(options, 'browser-evidence-kind') || undefined) as 'real' | 'mock' | undefined, browserEvidenceEnvironment: (optionString(options, 'browser-evidence-environment') || undefined) as 'isolated' | 'shared-readonly' | 'mock' | undefined,
      browserNotRequiredReason: optionString(options, 'browser-not-required-reason') || undefined, force: optionBoolean(options, 'force'),
    })
    return
  }
  if (command === 'planning check') {
    console.log(planningSummary(loadActivePlanningContract(repositoryRoot)))
    return
  }
  if (command === 'verify') {
    const mode = optionString(options, 'mode', 'quick') as 'quick' | 'stage' | 'full'
    await runQualityGate(repositoryRoot, {
      mode,
      backend: optionString(options, 'backend-strategy', mode === 'full' ? 'full' : 'compile') as BackendStrategy,
      backendTestPattern: optionString(options, 'backend-test-pattern') || undefined,
      frontend: optionString(options, 'frontend-strategy', 'full') as FrontendStrategy,
      collaboration: optionString(options, 'collaboration-strategy', mode === 'full' ? 'test' : 'skip') as CollaborationStrategy,
      skipDocker: optionBoolean(options, 'skip-docker'), skipAudit: optionBoolean(options, 'skip-audit'), compact: optionBoolean(options, 'compact-output'),
    })
    return
  }
  if (command === 'security scan') {
    const result = scanSensitiveData(repositoryRoot, { writeReport: !optionBoolean(options, 'skip-report') })
    console.log(`Sensitive data scan: ${result.hits.length ? 'FAIL' : 'PASS'}; findings=${result.hits.length}; waived=${result.waived}`)
    if (result.report) console.log(`Report: ${result.report}`)
    for (const hit of result.hits) console.error(`${hit.path}:${hit.line} [${hit.rule}]`)
    if (result.hits.length) process.exitCode = 1
    return
  }
  if (command === 'security browser-evidence') {
    const browserCommand = optionString(options, 'command')
    if (!browserCommand) throw new Error('--command is required')
    const result = assertRealBrowserEvidence(browserCommand, repositoryRoot)
    console.log(`Real browser evidence verified: references=${result.references.length}; closure=${result.files.length}`)
    return
  }
  if (command === 'security audit') {
    const result = runSecurityAudit(repositoryRoot, !optionBoolean(options, 'skip-report'))
    result.results.forEach((value) => console.log(`PASS: ${value}`)); result.failures.forEach((value) => console.error(`FAIL: ${value}`))
    if (result.failures.length) process.exitCode = 1
    return
  }
  if (command === 'knowledge naming-guard') {
    const findings = namingGuard(repositoryRoot)
    findings.forEach((value) => console.error(value)); if (findings.length) process.exitCode = 1; else console.log('Knowledge naming guard passed.')
    return
  }
  if (command === 'knowledge consistency-check') {
    const result = consistencyCheck(repositoryRoot, { container: optionString(options, 'container', 'colla-postgres'), database: optionString(options, 'database', 'colla_platform'), user: optionString(options, 'database-user', 'colla') }, optionString(options, 'output-dir', '.local-reports'))
    console.log(`Knowledge consistency: ${result.failures ? 'FAIL' : 'PASS'}; report=${result.report}`); if (result.failures) process.exitCode = 2
    return
  }
  if (command === 'knowledge inspect-object-references') {
    inspectObjectReferences({ container: optionString(options, 'container', 'colla-postgres'), database: optionString(options, 'database', 'colla_platform'), user: optionString(options, 'user', 'colla') }).forEach((value) => console.log(value))
    return
  }
  if (command === 'knowledge repair-reference') {
    const result = repairKnowledgeReference(repositoryRoot, { referenceId: optionString(options, 'reference-id'), action: optionString(options, 'action', 'preview') as 'preview' | 'repair', container: optionString(options, 'container', 'colla-postgres'), database: optionString(options, 'database', 'colla_platform'), user: optionString(options, 'database-user', 'colla'), backupPath: optionString(options, 'backup-path') || undefined, createBackup: optionBoolean(options, 'create-backup'), confirm: optionBoolean(options, 'confirm'), outputDir: optionString(options, 'output-dir', '.local-reports') }); console.log(`Knowledge reference repair: ${result.report}`); return
  }
  if (command === 'browser smoke-im' || command === 'browser smoke-ui-split') {
    await browserSmoke(repositoryRoot, command.endsWith('smoke-im') ? 'e2e/im-smoke.spec.ts' : 'e2e/ui-split-v1-smoke.spec.ts', { webBaseUrl: optionString(options, 'web-base-url') || undefined, apiBaseUrl: optionString(options, 'api-base-url') || undefined, username: optionString(options, 'username') || undefined, password: optionString(options, 'password') || undefined, headed: optionBoolean(options, 'headed') }); return
  }
  if (command === 'browser smoke-m5-isolated') { await isolatedM5Smoke(repositoryRoot, optionNumber(options, 'database-port', 5432), optionNumber(options, 'api-port', 18080), optionNumber(options, 'web-port', 15173)); return }
  if (command === 'operations backup') { console.log(`Backup completed: ${backup(repositoryRoot, { composeFile: optionString(options, 'compose-file') || undefined, envFile: optionString(options, 'env-file') || undefined, backupDir: optionString(options, 'backup-dir') || undefined, backupHelperImage: optionString(options, 'backup-helper-image') || undefined, retentionDays: optionNumber(options, 'retention-days', 0), skipMinio: optionBoolean(options, 'skip-minio'), skipQuiesce: optionBoolean(options, 'skip-quiesce'), allowExternalBackupRoot: optionBoolean(options, 'allow-external-backup-root') })}`); return }
  if (command === 'operations health') { console.log(`Health report: ${await healthCheck(repositoryRoot, { composeFile: optionString(options, 'compose-file') || undefined, envFile: optionString(options, 'env-file') || undefined, baseUrl: optionString(options, 'base-url') || undefined, metricsBaseUrl: optionString(options, 'metrics-base-url') || undefined, expectedProjectName: optionString(options, 'expected-project-name') || undefined, skipCompose: optionBoolean(options, 'skip-compose'), requirePrometheus: optionBoolean(options, 'require-prometheus'), requireLogCorrelation: optionBoolean(options, 'require-log-correlation') })}`); return }
  if (command === 'operations restore-drill') { console.log(`Restore drill report: ${await restoreDrill(repositoryRoot, { backupPath: optionString(options, 'backup-path'), composeFile: optionString(options, 'compose-file') || undefined, envFile: optionString(options, 'env-file') || undefined, baseUrl: optionString(options, 'base-url') || undefined, expectedProjectName: optionString(options, 'expected-project-name') || undefined, runRestore: optionBoolean(options, 'run-restore'), confirmRestore: optionBoolean(options, 'confirm-restore') })}`); return }
  if (command === 'operations restore') { console.log(`Restore report: ${await restore(repositoryRoot, { backupPath: optionString(options, 'backup-path'), composeFile: optionString(options, 'compose-file') || undefined, envFile: optionString(options, 'env-file') || undefined, backupHelperImage: optionString(options, 'backup-helper-image') || undefined, baseUrl: optionString(options, 'base-url') || undefined, expectedProjectName: optionString(options, 'expected-project-name'), confirmationText: optionString(options, 'confirmation-text'), confirmRestore: optionBoolean(options, 'confirm-restore'), skipHealthCheck: optionBoolean(options, 'skip-health-check') })}`); return }
  if (command === 'operations rollback') { console.log(`Rollback report: ${await rollback(repositoryRoot, { composeFile: optionString(options, 'compose-file') || undefined, envFile: optionString(options, 'env-file') || undefined, serverImage: optionString(options, 'server-image'), webImage: optionString(options, 'web-image'), collaborationImage: optionString(options, 'collaboration-image'), expectedSourceCommit: optionString(options, 'expected-source-commit') || undefined, backupPath: optionString(options, 'backup-path') || undefined, baseUrl: optionString(options, 'base-url') || undefined, expectedProjectName: optionString(options, 'expected-project-name'), confirmationText: optionString(options, 'confirmation-text'), confirmRollback: optionBoolean(options, 'confirm-rollback'), restoreData: optionBoolean(options, 'restore-data') })}`); return }
  if (command === 'operations release-check') { console.log(`Release report: ${await releaseCheck(repositoryRoot, { composeFile: optionString(options, 'compose-file') || undefined, envFile: optionString(options, 'env-file') || undefined, gateMode: optionString(options, 'gate-mode', 'full') as 'quick' | 'full', expectedProjectName: optionString(options, 'expected-project-name') || undefined, backupPath: optionString(options, 'backup-path') || undefined, backupDir: optionString(options, 'backup-dir') || undefined, maxBackupAgeHours: optionNumber(options, 'max-backup-age-hours', 24), createBackup: optionBoolean(options, 'create-backup'), allowDirty: optionBoolean(options, 'allow-dirty'), skipQualityGate: optionBoolean(options, 'skip-quality-gate'), skipImageBuild: optionBoolean(options, 'skip-image-build'), skipBackupCheck: optionBoolean(options, 'skip-backup-check'), allowPartial: optionBoolean(options, 'allow-partial') })}`); return }
  if (command === 'operations contract-check') { const { runSync } = await import('./lib/process.js'); runSync('pnpm', ['--dir', 'tools/workbench', 'test'], { cwd: repositoryRoot, capture: false }); return }
  if (command === 'pilot check') { const manifest = readPilotManifest(resolveFrom(repositoryRoot, optionString(options, 'manifest-path'))); const validation = validatePilotManifest(manifest, optionString(options, 'level', 'structural') as any); const reports = writeValidationReport(repositoryRoot, manifest, validation, optionString(options, 'report-directory', '.local-reports')); console.log(`Manifest report: ${reports.markdown}`); if (!validation.valid) process.exitCode = 2; return }
  if (command === 'pilot contract-check') {
    const results = runPilotContract(repositoryRoot, {
      generateRehearsalManifest: optionBoolean(options, 'generate-rehearsal-manifest'), rehearsalManifestPath: optionString(options, 'rehearsal-manifest-path') || undefined,
      rehearsalPilotId: optionString(options, 'rehearsal-pilot-id') || undefined, rehearsalProjectName: optionString(options, 'rehearsal-project-name') || undefined,
      rehearsalBaseUrl: optionString(options, 'rehearsal-base-url') || undefined,
    })
    results.forEach((value) => console.log(`PASS: ${value}`)); console.log(`PILOT-V2 contract check passed (${results.length} checks)`); return
  }
  if (command === 'pilot m10-contract-check') { console.log(`M10 contract report: ${checkM10Contract(repositoryRoot, optionString(options, 'contract-path', 'deploy/pilot-v2/m10-simulation-contract.json'), optionString(options, 'report-directory', '.local-reports'))}`); return }
  if (command === 'pilot m10-simulation') { console.log(`M10 simulation summary: ${runM10Simulation(repositoryRoot, { manifestPath: optionString(options, 'manifest-path'), envFile: optionString(options, 'env-file'), contractPath: optionString(options, 'contract-path') || undefined, composeFile: optionString(options, 'compose-file') || undefined, reportDirectory: optionString(options, 'report-directory') || undefined, confirmationText: optionString(options, 'confirmation-text') })}`); return }
  if (command === 'pilot initialize') { const result = await initializePilot(repositoryRoot, { manifestPath: optionString(options, 'manifest-path'), apiBaseUrl: optionString(options, 'api-base-url') || undefined, reportDirectory: optionString(options, 'report-directory') || undefined, apply: optionBoolean(options, 'apply'), confirmationText: optionString(options, 'confirmation-text') || undefined }); console.log(`Initialization receipt: ${result.receipt}`); return }
  if (command === 'pilot simulate-kickoff') { console.log(`Simulation kickoff report: ${simulationKickoff(repositoryRoot, { manifestPath: optionString(options, 'manifest-path'), backupPath: optionString(options, 'backup-path'), confirmationText: optionString(options, 'confirmation-text'), reportDirectory: optionString(options, 'report-directory') || undefined })}`); return }
  if (command === 'pilot readiness') { const result = await pilotReadiness(repositoryRoot, { manifestPath: optionString(options, 'manifest-path'), initializationReceiptPath: optionString(options, 'initialization-receipt-path'), backupPath: optionString(options, 'backup-path'), restoreDrillReportPath: optionString(options, 'restore-drill-report-path'), qualityGateReportPath: optionString(options, 'quality-gate-report-path'), apiBaseUrl: optionString(options, 'api-base-url') || undefined, reportDirectory: optionString(options, 'report-directory') || undefined, freeze: optionBoolean(options, 'freeze'), simulationFreeze: optionBoolean(options, 'simulation-freeze') }); console.log(`Readiness: ${result.decision}; report=${result.report}`); if (result.decision === 'BLOCKED') process.exitCode = 2; return }
  throw new Error(`Unknown workbench command: ${command || '(none)'}`)
}

main().catch((error: unknown) => {
  console.error(error instanceof Error ? error.message : String(error))
  process.exitCode = 1
})
