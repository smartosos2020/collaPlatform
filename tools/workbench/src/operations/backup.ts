import { mkdirSync, readdirSync, rmSync, statSync, unlinkSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { assertWithin } from '../lib/paths.js'
import { runSync } from '../lib/process.js'
import { compose, composeProjectName, composeServices, databaseCounts, flywayVersion, minioObjectCount, readBackupManifest, resolveFrom, sha256, type BackupFile, type OperationPaths } from './common.js'

const helperDefault = 'alpine:3.20@sha256:d9e853e87e55526f6b2917df91a2115c36dd7c696a35be12163d44e6e2a4b6bc'
function stamp(): string { return new Date().toISOString().replace(/[-:]/g, '').slice(0, 8) + '-' + new Date().toISOString().replace(/[-:]/g, '').slice(9, 15) }

export interface BackupOptions { composeFile?: string; envFile?: string; backupDir?: string; backupHelperImage?: string; retentionDays?: number; skipMinio?: boolean; skipQuiesce?: boolean; allowExternalBackupRoot?: boolean }

export function backup(root: string, options: BackupOptions): string {
  const paths: OperationPaths = { root, composePath: resolveFrom(root, options.composeFile ?? 'deploy/docker-compose.prod.yml'), envPath: resolveFrom(root, options.envFile ?? 'deploy/.env.prod') }
  const backupRoot = resolveFrom(root, options.backupDir ?? '.local-backups')
  if (!options.allowExternalBackupRoot) assertWithin(root, backupRoot, 'backup root')
  mkdirSync(backupRoot, { recursive: true })
  const target = assertWithin(backupRoot, join(backupRoot, stamp()), 'backup target')
  mkdirSync(target); writeFileSync(join(target, '.backup-in-progress'), new Date().toISOString())
  const helper = options.backupHelperImage ?? helperDefault
  const services = composeServices(paths)
  const running = compose(paths, ['ps', '--status', 'running', '--services']).split(/\r?\n/).filter(Boolean)
  let resume: string[] = []; let resumeMinio = false; let minioContainer = ''; let objectCount: number | null = null
  let consistencyMode = 'operator-managed'
  try {
    if (!options.skipQuiesce && services.includes('server') && running.includes('server')) {
      resume = services.includes('nginx') && running.includes('nginx') ? ['nginx', 'server'] : ['server']; compose(paths, ['stop', ...resume]); consistencyMode = 'application-quiesced'
    } else if (options.skipQuiesce) consistencyMode = 'non-quiesced-explicit'
    if (!options.skipMinio) {
      if (!running.includes('minio')) throw new Error('MinIO must be running before a full backup')
      objectCount = minioObjectCount(paths, helper); minioContainer = compose(paths, ['ps', '-q', 'minio']).trim(); compose(paths, ['stop', 'minio']); resumeMinio = true
    }
    const counts = databaseCounts(paths); const flyway = flywayVersion(paths)
    const dump = join(target, 'postgres.sql')
    compose(paths, ['exec', '-T', 'postgres', 'sh', '-c', 'pg_dump --clean --if-exists --no-owner --no-privileges --serializable-deferrable -U "$POSTGRES_USER" "$POSTGRES_DB" > /tmp/colla-postgres.sql'])
    compose(paths, ['cp', 'postgres:/tmp/colla-postgres.sql', dump]); compose(paths, ['exec', '-T', 'postgres', 'rm', '-f', '/tmp/colla-postgres.sql'])
    const files: BackupFile[] = [{ name: 'postgres.sql', kind: 'postgres', bytes: statSync(dump).size, sha256: sha256(dump) }]
    if (!options.skipMinio) {
      const archive = join(target, 'minio-data.tgz')
      runSync('docker', ['run', '--rm', '--volumes-from', minioContainer, '-v', `${target}:/backup`, helper, 'sh', '-c', 'tar -C /data -czf /backup/minio-data.tgz .'])
      files.push({ name: 'minio-data.tgz', kind: 'minio', bytes: statSync(archive).size, sha256: sha256(archive) })
    }
    const manifest = { manifestVersion: 2, createdAt: new Date().toISOString(), projectName: composeProjectName(paths), sourceGitCommit: runSync('git', ['rev-parse', 'HEAD'], { cwd: root, allowFailure: true }) || 'unknown', composeFile: options.composeFile ?? 'deploy/docker-compose.prod.yml', consistencyMode, flywayVersion: flyway, databaseCounts: counts, minioObjectCount: objectCount, files }
    writeFileSync(join(target, 'manifest.json'), JSON.stringify(manifest, null, 2)); writeFileSync(join(target, 'manifest.md'), ['# Colla Backup', '', `- Time: ${manifest.createdAt}`, `- Compose project: ${manifest.projectName}`, `- Source commit: ${manifest.sourceGitCommit}`, `- Consistency mode: ${consistencyMode}`, '', '| File | Kind | Bytes | SHA-256 |', '| --- | --- | ---: | --- |', ...files.map((file) => `| ${file.name} | ${file.kind} | ${file.bytes} | ${file.sha256} |`), ''].join('\n'))
    unlinkSync(join(target, '.backup-in-progress')); readBackupManifest(target, true)
    if ((options.retentionDays ?? 0) > 0) {
      const cutoff = Date.now() - options.retentionDays! * 86400000
      for (const name of readdirSync(backupRoot)) {
        const candidate = resolve(backupRoot, name); if (candidate === target || !/^\d{8}-\d{6}$/.test(name) || statSync(candidate).mtimeMs >= cutoff) continue
        try { readBackupManifest(candidate); assertWithin(backupRoot, candidate, 'retention candidate'); rmSync(candidate, { recursive: true }) } catch { /* Unverified directories are never pruned. */ }
      }
    }
    return target
  } finally {
    if (resumeMinio) compose(paths, ['up', '-d', '--wait', 'minio'])
    if (resume.length) compose(paths, ['up', '-d', '--wait', ...resume])
  }
}
