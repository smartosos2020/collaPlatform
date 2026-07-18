import { mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { runSync } from '../lib/process.js'
import { compose, composeProjectName, resolveFrom, type OperationPaths } from './common.js'
import { healthCheck } from './health.js'
import { restore } from './restore.js'

interface ImageEvidence { image: string; id: string; revision: string }
function imageEvidence(image: string): ImageEvidence {
  if (!/^[^\s:]+(?:\/[^\s:]+)*:[^\s:]+$/.test(image) || image.endsWith(':latest')) throw new Error(`Rollback image must use immutable tag: ${image}`)
  const details = JSON.parse(runSync('docker', ['image', 'inspect', image, '--format', '{{json .}}']))
  const revision = String(details.Config?.Labels?.['org.opencontainers.image.revision'] ?? '').toLowerCase(); if (!/^[0-9a-f]{40}$/.test(revision)) throw new Error(`Image has no valid revision: ${image}`)
  return { image, id: String(details.Id), revision }
}
export interface RollbackOptions { composeFile?: string; envFile?: string; serverImage: string; webImage: string; collaborationImage: string; expectedSourceCommit?: string; backupPath?: string; baseUrl?: string; expectedProjectName: string; confirmationText: string; confirmRollback: boolean; restoreData?: boolean }
export async function rollback(root: string, options: RollbackOptions): Promise<string> {
  const expected = `ROLLBACK:${options.expectedProjectName}:${options.serverImage}:${options.webImage}:${options.collaborationImage}`
  if (!options.confirmRollback || options.confirmationText !== expected) throw new Error(`Rollback requires exact confirmation: ${expected}`)
  if (options.restoreData && !options.backupPath) throw new Error('--backup-path is required with --restore-data')
  const images = [imageEvidence(options.serverImage), imageEvidence(options.webImage), imageEvidence(options.collaborationImage)]
  if (new Set(images.map((item) => item.revision)).size !== 1 || (options.expectedSourceCommit && images[0].revision !== options.expectedSourceCommit.toLowerCase())) throw new Error('Rollback image revisions do not match')
  const paths: OperationPaths = { root, composePath: resolveFrom(root, options.composeFile ?? 'deploy/docker-compose.prod.yml'), envPath: resolveFrom(root, options.envFile ?? 'deploy/.env.prod') }
  const env = { ...process.env, SERVER_IMAGE: options.serverImage, WEB_IMAGE: options.webImage, COLLABORATION_IMAGE: options.collaborationImage, SOURCE_COMMIT: images[0].revision }
  const project = composeProjectName(paths); if (project !== options.expectedProjectName) throw new Error(`Rollback target mismatch: ${project}`)
  if (options.restoreData) await restore(root, { backupPath: options.backupPath!, composeFile: options.composeFile, envFile: options.envFile, baseUrl: options.baseUrl, expectedProjectName: project, confirmationText: `RESTORE:${project}`, confirmRestore: true })
  else { runSync('docker', ['compose', '--env-file', paths.envPath, '-f', paths.composePath, 'up', '-d', '--no-build', '--wait'], { cwd: root, env }); await healthCheck(root, { composeFile: options.composeFile, envFile: options.envFile, baseUrl: options.baseUrl, expectedProjectName: project, requireLogCorrelation: true }) }
  const directory = join(root, '.local-reports'); mkdirSync(directory, { recursive: true }); const report = join(directory, `rollback-${new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, '')}.md`)
  writeFileSync(report, ['# Rollback Evidence', '', `- Project: ${project}`, `- Revision: ${images[0].revision}`, `- Restore data: ${Boolean(options.restoreData)}`, '- Decision: PASS', ''].join('\n')); return report
}
