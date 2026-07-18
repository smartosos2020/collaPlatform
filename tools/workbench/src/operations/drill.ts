import { mkdirSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { composeProjectName, readBackupManifest, resolveFrom, type OperationPaths } from './common.js'
import { restore } from './restore.js'

export interface DrillOptions { backupPath: string; composeFile?: string; envFile?: string; baseUrl?: string; expectedProjectName?: string; runRestore?: boolean; confirmRestore?: boolean }
export async function restoreDrill(root: string, options: DrillOptions): Promise<string> {
  const directory = join(root, '.local-reports'); mkdirSync(directory, { recursive: true }); const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, '')
  const paths: OperationPaths = { root, composePath: resolveFrom(root, options.composeFile ?? 'deploy/docker-compose.prod.yml'), envPath: resolveFrom(root, options.envFile ?? 'deploy/.env.prod') }
  const manifest = readBackupManifest(resolve(options.backupPath), true); const project = composeProjectName(paths); const results = manifest.files.map((file) => `${file.name} size and SHA-256 verified`)
  if (options.expectedProjectName && project !== options.expectedProjectName) throw new Error(`Restore drill target mismatch: ${project}`)
  let decision = 'DRY-RUN-PASS'
  if (options.runRestore) {
    if (!options.confirmRestore || !options.expectedProjectName) throw new Error('Restore drill requires --confirm-restore and --expected-project-name')
    if (!/^colla-platform-drill-[a-z0-9-]+$/.test(project) || project === manifest.projectName) throw new Error('Restore drill target must be an isolated colla-platform-drill-* project different from source')
    await restore(root, { backupPath: options.backupPath, composeFile: options.composeFile, envFile: options.envFile, baseUrl: options.baseUrl, expectedProjectName: project, confirmationText: `RESTORE:${project}`, confirmRestore: true }); results.push('isolated restore and health verification completed'); decision = 'PASS'
  } else results.push('dry-run completed; no target data was changed')
  const report = join(directory, `restore-drill-${stamp}.md`); writeFileSync(report, ['# Restore Drill', '', `- Backup path: ${resolve(options.backupPath)}`, `- Mode: ${options.runRestore ? 'isolated-restore' : 'dry-run'}`, `- Decision: ${decision}`, '', ...results.map((value) => `- PASS: ${value}`), ''].join('\n')); return report
}
