import { optionBoolean, optionString } from '../lib/args.js'
import type { CommandContext } from './types.js'

export async function runCommand({ command, options, root }: CommandContext): Promise<void> {
  if (command === 'pilot check') {
    const { readPilotManifest, validatePilotManifest, writeValidationReport } = await import('../pilot/manifest.js')
    type ValidationLevel = Parameters<typeof validatePilotManifest>[1]
    const { resolveFrom } = await import('../operations/common.js')
    const manifest = readPilotManifest(resolveFrom(root, optionString(options, 'manifest-path')))
    const validation = validatePilotManifest(manifest, optionString(options, 'level', 'structural') as ValidationLevel)
    const reports = writeValidationReport(root, manifest, validation, optionString(options, 'report-directory', '.local-reports'))
    console.log(`Manifest report: ${reports.markdown}`)
    if (!validation.valid) process.exitCode = 2
    return
  }
  if (command === 'pilot contract-check') {
    const { runPilotContract } = await import('../pilot/contract.js')
    const results = runPilotContract(root, {
      generateRehearsalManifest: optionBoolean(options, 'generate-rehearsal-manifest'),
      rehearsalManifestPath: optionString(options, 'rehearsal-manifest-path') || undefined,
      rehearsalPilotId: optionString(options, 'rehearsal-pilot-id') || undefined,
      rehearsalProjectName: optionString(options, 'rehearsal-project-name') || undefined,
      rehearsalBaseUrl: optionString(options, 'rehearsal-base-url') || undefined,
    })
    results.forEach((value) => console.log(`PASS: ${value}`))
    console.log(`PILOT-V2 contract check passed (${results.length} checks)`)
    return
  }
  if (command === 'pilot m10-contract-check') {
    const { checkM10Contract } = await import('../pilot/m10.js')
    console.log(`M10 contract report: ${checkM10Contract(root, optionString(options, 'contract-path', 'deploy/pilot-v2/m10-simulation-contract.json'), optionString(options, 'report-directory', '.local-reports'))}`)
    return
  }
  if (command === 'pilot m10-simulation') {
    const { runM10Simulation } = await import('../pilot/m10.js')
    console.log(`M10 simulation summary: ${runM10Simulation(root, {
      manifestPath: optionString(options, 'manifest-path'),
      envFile: optionString(options, 'env-file'),
      contractPath: optionString(options, 'contract-path') || undefined,
      composeFile: optionString(options, 'compose-file') || undefined,
      reportDirectory: optionString(options, 'report-directory') || undefined,
      confirmationText: optionString(options, 'confirmation-text'),
    })}`)
    return
  }
  if (command === 'pilot initialize') {
    const { initializePilot } = await import('../pilot/initialize.js')
    const result = await initializePilot(root, {
      manifestPath: optionString(options, 'manifest-path'),
      apiBaseUrl: optionString(options, 'api-base-url') || undefined,
      reportDirectory: optionString(options, 'report-directory') || undefined,
      apply: optionBoolean(options, 'apply'),
      confirmationText: optionString(options, 'confirmation-text') || undefined,
    })
    console.log(`Initialization receipt: ${result.receipt}`)
    return
  }
  if (command === 'pilot simulate-kickoff') {
    const { simulationKickoff } = await import('../pilot/kickoff.js')
    console.log(`Simulation kickoff report: ${simulationKickoff(root, {
      manifestPath: optionString(options, 'manifest-path'),
      backupPath: optionString(options, 'backup-path'),
      confirmationText: optionString(options, 'confirmation-text'),
      reportDirectory: optionString(options, 'report-directory') || undefined,
    })}`)
    return
  }
  if (command === 'pilot readiness') {
    const { pilotReadiness } = await import('../pilot/readiness.js')
    const result = await pilotReadiness(root, {
      manifestPath: optionString(options, 'manifest-path'),
      initializationReceiptPath: optionString(options, 'initialization-receipt-path'),
      backupPath: optionString(options, 'backup-path'),
      restoreDrillReportPath: optionString(options, 'restore-drill-report-path'),
      qualityGateReportPath: optionString(options, 'quality-gate-report-path'),
      apiBaseUrl: optionString(options, 'api-base-url') || undefined,
      reportDirectory: optionString(options, 'report-directory') || undefined,
      freeze: optionBoolean(options, 'freeze'),
      simulationFreeze: optionBoolean(options, 'simulation-freeze'),
    })
    console.log(`Readiness: ${result.decision}; report=${result.report}`)
    if (result.decision === 'BLOCKED') process.exitCode = 2
    return
  }
  throw new Error(`Unknown pilot command: ${command}`)
}
