import { mkdirSync, readdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { gitHead, gitStatusPaths } from '../lib/git.js'
import { runSync } from '../lib/process.js'
import { runQualityGate } from '../workcycle/quality.js'
import { backup } from './backup.js'
import { assertProductionEnvironment, composeModel, composeProjectName, readBackupManifest, resolveFrom, type OperationPaths } from './common.js'

export interface ReleaseOptions { composeFile?: string; envFile?: string; gateMode?: 'quick' | 'full'; expectedProjectName?: string; backupPath?: string; backupDir?: string; maxBackupAgeHours?: number; createBackup?: boolean; allowDirty?: boolean; skipQualityGate?: boolean; skipImageBuild?: boolean; skipBackupCheck?: boolean; allowPartial?: boolean }
export async function releaseCheck(root: string, options: ReleaseOptions): Promise<string> {
  const partial = options.allowDirty || options.skipQualityGate || options.skipImageBuild || options.skipBackupCheck
  if (partial && !options.allowPartial) throw new Error('Skipped release gates require --allow-partial')
  if (options.createBackup && options.backupPath) throw new Error('Use either --create-backup or --backup-path')
  const paths: OperationPaths = { root, composePath: resolveFrom(root, options.composeFile ?? 'deploy/docker-compose.prod.yml'), envPath: resolveFrom(root, options.envFile ?? 'deploy/.env.prod') }
  runSync('docker', ['info', '--format', '{{.ServerVersion}}'])
  const dirty = gitStatusPaths(root).length > 0; if (dirty && !options.allowDirty) throw new Error('Working tree is dirty')
  const environment = assertProductionEnvironment(paths.envPath); const head = gitHead(root).toLowerCase(); if (environment.SOURCE_COMMIT.toLowerCase() !== head) throw new Error('SOURCE_COMMIT does not match HEAD')
  const model = composeModel(paths); const project = composeProjectName(paths); if (options.expectedProjectName && project !== options.expectedProjectName) throw new Error(`Release target mismatch: ${project}`)
  for (const service of ['postgres', 'redis', 'minio', 'server', 'collaboration-a', 'collaboration-b', 'web', 'nginx']) if (!model.services?.[service]?.healthcheck) throw new Error(`Missing service or health check: ${service}`)
  let backupPath = options.backupPath
  if (!options.skipBackupCheck) {
    if (options.createBackup) backupPath = backup(root, { composeFile: options.composeFile, envFile: options.envFile, backupDir: options.backupDir })
    if (!backupPath) throw new Error('A verified recent backup is required')
    const manifest = readBackupManifest(backupPath, true); if (manifest.projectName !== project || Date.parse(manifest.createdAt) < Date.now() - (options.maxBackupAgeHours ?? 24) * 3600000) throw new Error('Backup is stale or belongs to another project')
  }
  if (!options.skipQualityGate) await runQualityGate(root, { mode: options.gateMode ?? 'full', backend: options.gateMode === 'quick' ? 'compile' : 'full', frontend: 'full', collaboration: options.gateMode === 'quick' ? 'skip' : 'test' })
  let artifact: Record<string, unknown> | undefined
  if (!options.skipImageBuild) {
    runSync('docker', ['compose', '--env-file', paths.envPath, '-f', paths.composePath, 'build', 'server', 'web', 'collaboration-a'], { cwd: root })
    const inspect = (image: string): any => JSON.parse(runSync('docker', ['image', 'inspect', image, '--format', '{{json .}}']))
    const images = [environment.SERVER_IMAGE, environment.WEB_IMAGE, environment.COLLABORATION_IMAGE].map(inspect)
    if (images.some((item) => item.Config?.Labels?.['org.opencontainers.image.revision'] !== head)) throw new Error('Built image revision does not match HEAD')
    artifact = { createdAt: new Date().toISOString(), sourceCommit: head, worktreeDirty: dirty, composeProject: project, images: images.map((item) => ({ id: item.Id, revision: item.Config.Labels['org.opencontainers.image.revision'] })) }
  }
  const directory = join(root, '.local-reports'); mkdirSync(directory, { recursive: true }); const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, '')
  if (artifact) writeFileSync(join(directory, `release-artifacts-${stamp}.json`), JSON.stringify(artifact, null, 2))
  const report = join(directory, `release-check-${stamp}.md`); writeFileSync(report, ['# Release Check', '', `- Decision: ${partial ? 'PARTIAL' : 'PASS'}`, `- Project: ${project}`, `- Backup: ${backupPath ?? 'skipped'}`, ''].join('\n')); return report
}
