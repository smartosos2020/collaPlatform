import { mkdirSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { assertWithin } from '../lib/paths.js'
import { readBackupManifest } from '../operations/common.js'
import { readPilotManifest, sourceSnapshot, validatePilotManifest } from './manifest.js'

export interface KickoffOptions { manifestPath: string; backupPath: string; confirmationText: string; reportDirectory?: string }
export function simulationKickoff(root: string, options: KickoffOptions): string {
  const manifestPath = assertWithin(join(root, '.local-pilot'), resolve(root, options.manifestPath), 'simulation manifest')
  const manifest = readPilotManifest(manifestPath); const initial = validatePilotManifest(manifest, 'initialization'); if (!initial.valid) throw new Error(initial.errors.join('; '))
  if (manifest.mode !== 'rehearsal' || options.confirmationText !== `SIMULATE:${manifest.pilotId}`) throw new Error(`Simulation kickoff requires rehearsal mode and SIMULATE:${manifest.pilotId}`)
  const backupPath = resolve(root, options.backupPath); const backup = readBackupManifest(backupPath, true); if (backup.projectName !== manifest.environment.projectName) throw new Error('Backup project does not match pilot project')
  const ids = manifest.participants.map((item: any) => item.participantId); for (const participant of manifest.participants) participant.participantKind = 'synthetic'
  Object.assign(manifest.kickoffApproval, { confirmationBasis: 'synthetic-personas', scopeConfirmedBy: ids, feedbackConfirmedBy: ids, stopConditionsConfirmedBy: ids, acceptedAt: new Date().toISOString(), releaseCommit: backup.sourceGitCommit, backupManifest: join(backupPath, 'manifest.json'), sourceSnapshot: sourceSnapshot(root), limitationsAcknowledged: ['no-real-user-feedback', 'no-human-satisfaction-evidence', 'not-production-release-approval'], decision: 'go' })
  const freeze = validatePilotManifest(manifest, 'simulation-freeze'); if (!freeze.valid) throw new Error(freeze.errors.join('; '))
  writeFileSync(manifestPath, JSON.stringify(manifest, null, 2))
  const directory = resolve(root, options.reportDirectory ?? '.local-reports'); mkdirSync(directory, { recursive: true }); const report = join(directory, `pilot-v2-simulation-kickoff-${new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, '')}.md`)
  writeFileSync(report, ['# PILOT-V2 Synthetic Kickoff', '', `- Pilot: ${manifest.pilotId}`, '- Decision: SIMULATION-GO', '- Confirmation basis: synthetic personas', `- Participants: ${ids.length}`, `- Source snapshot: ${manifest.kickoffApproval.sourceSnapshot}`, `- Backup manifest: ${manifest.kickoffApproval.backupManifest}`, '', '## Limitations', '', '- No real-user feedback was collected.', '- No human satisfaction evidence was collected.', '- This is not production release approval.', ''].join('\n')); return report
}
