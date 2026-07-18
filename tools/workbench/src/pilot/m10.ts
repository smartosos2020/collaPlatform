import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { basename, join } from 'node:path'
import { runSync } from '../lib/process.js'
import { composeArgs, composeProjectName, resolveFrom, type OperationPaths } from '../operations/common.js'
import { readPilotManifest, validatePilotManifest } from './manifest.js'

const expectedPersonas = ['pilot-owner', 'pilot-admin', 'pilot-member-01', 'pilot-member-02', 'pilot-member-03']
const expectedModules = ['im', 'project', 'knowledge', 'base', 'approval', 'search']
const expectedRounds = ['round-1-baseline', 'round-2-retry', 'round-3-fault-recovery']
const expectedMetrics = ['personaAuthenticationRate', 'scenarioAttemptRate', 'scenarioSuccessRate', 'moduleRoundCoverage', 'unexpectedAuthorizationCount', 'dataConsistencyViolationCount', 'openCriticalIssueCount', 'faultRecoveryRate', 'automationFlakeRate']
function stamp(): string { return new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, '') }
function readJson(path: string): any { return JSON.parse(readFileSync(path, 'utf8').replace(/^\uFEFF/, '')) }
function equalOrder(actual: unknown, expected: string[]): boolean { return Array.isArray(actual) && actual.join(',') === expected.join(',') }
function rate(numerator: number, denominator: number): number { return denominator <= 0 ? 0 : Math.round((numerator / denominator) * 10000) / 10000 }

export function validateM10Contract(contract: any): string[] {
  const errors: string[] = []
  if (contract.schemaVersion !== 1) errors.push('schemaVersion must be 1')
  if (contract.evidenceKind !== 'synthetic-engineering') errors.push('evidenceKind must be synthetic-engineering')
  if (Number(contract.minimumRoundCount) < 3) errors.push('minimumRoundCount must be at least 3')
  if (!equalOrder(contract.personas, expectedPersonas)) errors.push('personas must define the five synthetic identities in order')
  if (!equalOrder(contract.modules, expectedModules)) errors.push('modules must cover im, project, knowledge, base, approval, search in order')
  if (!equalOrder(contract.rounds?.map((item: any) => item.roundId), expectedRounds)) errors.push('rounds must define baseline, retry, and fault-recovery')
  for (const metric of expectedMetrics) if (!(metric in (contract.metrics ?? {}))) errors.push(`missing metric: ${metric}`)
  if ((contract.stopConditions?.length ?? 0) < 4) errors.push('at least four stop conditions are required')
  if ((contract.limitations?.length ?? 0) < 3) errors.push('synthetic evidence limitations are incomplete')
  return errors
}

export function checkM10Contract(root: string, contractPath = 'deploy/pilot-v2/m10-simulation-contract.json', reportDirectory = '.local-reports'): string {
  const resolved = resolveFrom(root, contractPath); if (!existsSync(resolved)) throw new Error(`M10 simulation contract not found: ${resolved}`)
  const contract = readJson(resolved); const errors = validateM10Contract(contract); const directory = resolveFrom(root, reportDirectory); mkdirSync(directory, { recursive: true })
  const report = join(directory, `pilot-v2-m10-contract-${stamp()}.json`)
  writeFileSync(report, `${JSON.stringify({ contractId: contract.contractId, checkedAt: new Date().toISOString(), decision: errors.length ? 'FAIL' : 'PASS', personaCount: contract.personas?.length ?? 0, moduleCount: contract.modules?.length ?? 0, roundCount: contract.rounds?.length ?? 0, errors }, null, 2)}\n`)
  if (errors.length) throw new Error(errors.join('; ')); return report
}

export interface M10SimulationOptions { manifestPath: string; envFile: string; contractPath?: string; composeFile?: string; reportDirectory?: string; confirmationText: string }
export function runM10Simulation(root: string, options: M10SimulationOptions): string {
  const manifestPath = resolveFrom(root, options.manifestPath); const envPath = resolveFrom(root, options.envFile)
  const contractPath = resolveFrom(root, options.contractPath ?? 'deploy/pilot-v2/m10-simulation-contract.json'); const composePath = resolveFrom(root, options.composeFile ?? 'deploy/docker-compose.prod.yml')
  for (const path of [manifestPath, envPath, contractPath, composePath]) if (!existsSync(path)) throw new Error(`Required M10 file not found: ${path}`)
  const manifest = readPilotManifest(manifestPath); const validation = validatePilotManifest(manifest, 'initialization'); if (!validation.valid) throw new Error(`Pilot manifest failed initialization validation: ${validation.errors.join('; ')}`)
  if (manifest.mode !== 'rehearsal' || manifest.participants.some((item: any) => item.participantKind !== 'synthetic')) throw new Error('M10 runner requires rehearsal mode and synthetic participants')
  const projectName = String(manifest.environment.projectName); if (!/^colla-platform-pilot-m10[a-z0-9-]*$/.test(projectName)) throw new Error(`M10 runner requires isolated project colla-platform-pilot-m10*: ${projectName}`)
  const expected = `RUN-SIMULATION:${manifest.pilotId}:${projectName}`; if (options.confirmationText !== expected) throw new Error(`Confirmation mismatch. Expected ${expected}`)
  const paths: OperationPaths = { root, composePath, envPath }; if (composeProjectName(paths) !== projectName) throw new Error('Compose project does not match manifest project')
  const reportRoot = resolveFrom(root, options.reportDirectory ?? '.local-reports'); checkM10Contract(root, contractPath, reportRoot)
  const contract = readJson(contractPath); const runId = `m10-${stamp()}Z`; const directory = join(reportRoot, `pilot-v2-m10-${runId}`); mkdirSync(directory, { recursive: true })
  const statePath = join(directory, 'state.json'); const evidenceFiles: string[] = []; let runnerFailure: string | null = null; const startedAt = new Date().toISOString()
  const phase = (name: string): void => {
    const evidence = join(directory, `${name}.json`); const log = join(directory, `${name}-playwright.log`)
    const env = { ...process.env, COLLA_E2E_SUITE: 'pilot-m10', COLLA_E2E_M10_PHASE: name, COLLA_E2E_M10_RUN_ID: runId, COLLA_E2E_M10_EVIDENCE_PATH: evidence, COLLA_E2E_M10_STATE_PATH: statePath, COLLA_E2E_API_BASE_URL: `${String(manifest.environment.baseUrl).replace(/\/$/, '')}/api`, COLLA_E2E_WEB_BASE_URL: String(manifest.environment.baseUrl).replace(/\/$/, ''), COLLA_E2E_ADMIN_USERNAME: String(manifest.participants[0].username), COLLA_E2E_MEMBER_USERNAME: 'pilot-member-01' }
    try { writeFileSync(log, runSync('pnpm', ['exec', 'playwright', 'test', '--config', 'e2e/playwright.config.ts', 'e2e/pilot-v2-m10-simulation.spec.ts'], { cwd: join(root, 'web'), env })) }
    catch (error) { writeFileSync(log, error instanceof Error ? error.message : String(error)); if (existsSync(evidence)) evidenceFiles.push(evidence); throw error }
    if (!existsSync(evidence)) throw new Error(`M10 phase '${name}' did not produce evidence: ${evidence}`); evidenceFiles.push(evidence)
  }
  try {
    phase('baseline'); phase('retry'); phase('fault')
    const restartStartedAt = new Date().toISOString(); runSync('docker', composeArgs(paths, ['restart', 'server']), { cwd: root }); runSync('docker', composeArgs(paths, ['up', '-d', '--wait', 'server', 'nginx']), { cwd: root })
    writeFileSync(join(directory, 'service-restart.json'), `${JSON.stringify({ projectName, service: 'server', startedAt: restartStartedAt, finishedAt: new Date().toISOString(), decision: 'PASS' }, null, 2)}\n`)
    phase('recovery')
  } catch (error) { runnerFailure = error instanceof Error ? error.message : String(error) }
  const steps = evidenceFiles.flatMap((path) => readJson(path).steps ?? []); const failed = steps.filter((item: any) => item.status === 'failed'); const authentication = steps.filter((item: any) => item.category === 'authentication'); const scenarios = steps.filter((item: any) => item.category === 'scenario'); const faults = steps.filter((item: any) => item.category === 'fault')
  const productCritical = failed.filter((item: any) => item.failureClass === 'product' && ['P0', 'P1'].includes(item.severity)); const harness = failed.filter((item: any) => item.failureClass === 'automation-harness'); const consistency = failed.filter((item: any) => item.stepId === 'retry-im' || /idempotency|changed state|lost marker|regression|duplicate/i.test(item.detail ?? '')); const recovery = scenarios.filter((item: any) => item.phase === 'recovery')
  const coverage = Object.fromEntries(contract.rounds.map((round: any) => [round.roundId, rate(new Set(scenarios.filter((item: any) => item.roundId === round.roundId).map((item: any) => item.module)).size, contract.modules.length)])); const minimumCoverage = Math.min(...Object.values(coverage).map(Number))
  const metrics: Record<string, number> = { personaAuthenticationRate: rate(authentication.filter((item: any) => item.status === 'passed').length, authentication.length), scenarioAttemptRate: rate(scenarios.length, contract.modules.length * contract.minimumRoundCount), scenarioSuccessRate: rate(scenarios.filter((item: any) => item.status === 'passed').length, scenarios.length), moduleRoundCoverage: minimumCoverage, unexpectedAuthorizationCount: faults.filter((item: any) => item.status === 'failed').length, dataConsistencyViolationCount: consistency.length, openCriticalIssueCount: productCritical.length, faultRecoveryRate: rate(recovery.filter((item: any) => item.status === 'passed').length, contract.modules.length), automationFlakeRate: rate(harness.length, steps.length) }
  const metricPass: Record<string, boolean> = { personaAuthenticationRate: metrics.personaAuthenticationRate === 1, scenarioAttemptRate: metrics.scenarioAttemptRate === 1, scenarioSuccessRate: metrics.scenarioSuccessRate >= 0.95, moduleRoundCoverage: metrics.moduleRoundCoverage === 1, unexpectedAuthorizationCount: metrics.unexpectedAuthorizationCount === 0, dataConsistencyViolationCount: metrics.dataConsistencyViolationCount === 0, openCriticalIssueCount: metrics.openCriticalIssueCount === 0, faultRecoveryRate: metrics.faultRecoveryRate === 1, automationFlakeRate: metrics.automationFlakeRate <= 0.05 }
  const roundDecisions = Object.fromEntries(contract.rounds.map((round: any) => [round.roundId, failed.some((item: any) => item.roundId === round.roundId && ['P0', 'P1'].includes(item.severity)) ? 'STOP' : 'CONTINUE']))
  const decision = !runnerFailure && Object.values(metricPass).every(Boolean) ? 'PASS' : 'FAIL'; const summary = { schemaVersion: 1, evidenceKind: 'synthetic-engineering', runId, pilotId: manifest.pilotId, projectName, startedAt, finishedAt: new Date().toISOString(), decision, runnerFailure, metrics, metricPass, moduleRoundCoverage: coverage, roundDecisions, phaseEvidence: evidenceFiles.map((path) => basename(path)), failedSteps: failed, limitations: contract.limitations }
  const summaryPath = join(directory, 'summary.json'); writeFileSync(summaryPath, `${JSON.stringify(summary, null, 2)}\n`)
  writeFileSync(join(directory, 'summary.md'), ['# PILOT-V2 M10 Synthetic Continuous Run', '', `- Run: ${runId}`, '- Evidence: synthetic engineering evidence; not real participant evidence', `- Compose project: ${projectName}`, `- Decision: ${decision}`, '', '## Metrics', '', '| Metric | Value | Pass |', '| --- | ---: | --- |', ...Object.keys(metrics).map((name) => `| ${name} | ${metrics[name]} | ${metricPass[name]} |`), '', '## Limitations', ...contract.limitations.map((item: string) => `- ${item}`), ''].join('\n'))
  if (decision !== 'PASS') throw new Error(`M10 synthetic run failed. See ${summaryPath}`); return summaryPath
}
