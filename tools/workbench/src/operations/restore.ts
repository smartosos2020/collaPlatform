import { mkdirSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { assertWithin } from '../lib/paths.js'
import { runSync } from '../lib/process.js'
import { compareCounts, compose, composeProjectName, composeServices, databaseCounts, minioObjectCount, readBackupManifest, resolveFrom, type OperationPaths } from './common.js'
import { healthCheck } from './health.js'

const helperDefault = 'alpine:3.20@sha256:d9e853e87e55526f6b2917df91a2115c36dd7c696a35be12163d44e6e2a4b6bc'
export interface RestoreOptions { backupPath: string; composeFile?: string; envFile?: string; backupHelperImage?: string; baseUrl?: string; expectedProjectName: string; confirmationText: string; confirmRestore: boolean; skipHealthCheck?: boolean }

export async function restore(root: string, options: RestoreOptions): Promise<string> {
  if (!options.confirmRestore || options.confirmationText !== `RESTORE:${options.expectedProjectName}`) throw new Error(`Restore requires --confirm-restore and exact --confirmation-text RESTORE:${options.expectedProjectName}`)
  const paths: OperationPaths = { root, composePath: resolveFrom(root, options.composeFile ?? 'deploy/docker-compose.prod.yml'), envPath: resolveFrom(root, options.envFile ?? 'deploy/.env.prod') }
  const actualProject = composeProjectName(paths); if (actualProject !== options.expectedProjectName) throw new Error(`Restore target mismatch: ${actualProject}`)
  const backupDir = resolve(options.backupPath); const manifest = readBackupManifest(backupDir, true)
  const dump = assertWithin(backupDir, join(backupDir, 'postgres.sql'), 'database dump')
  const hasMinio = manifest.files.some((file) => file.name === 'minio-data.tgz')
  const services = composeServices(paths); const app = ['nginx', 'web', 'server'].filter((name) => services.includes(name)); const dependencies = ['postgres', 'redis', 'minio'].filter((name) => services.includes(name))
  const reportDir = join(root, '.local-reports'); mkdirSync(reportDir, { recursive: true }); const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, '')
  const results: string[] = []; let passed = false
  try {
    if (app.length) compose(paths, ['stop', ...app]); compose(paths, ['up', '-d', '--wait', ...dependencies])
    compose(paths, ['exec', '-T', 'postgres', 'sh', '-c', 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" "$POSTGRES_DB" -c "select pg_terminate_backend(pid) from pg_stat_activity where datname = current_database() and pid <> pg_backend_pid()"'])
    compose(paths, ['cp', dump, 'postgres:/tmp/colla-restore-postgres.sql'])
    const output = compose(paths, ['exec', '-T', 'postgres', 'sh', '-c', 'psql -v ON_ERROR_STOP=1 --single-transaction -U "$POSTGRES_USER" "$POSTGRES_DB" < /tmp/colla-restore-postgres.sql'])
    writeFileSync(join(reportDir, `restore-${stamp}-postgres.log`), output); compose(paths, ['exec', '-T', 'postgres', 'rm', '-f', '/tmp/colla-restore-postgres.sql']); results.push('PostgreSQL restore completed')
    if (hasMinio) {
      compose(paths, ['stop', 'minio']); const container = compose(paths, ['ps', '-q', '--all', 'minio']).trim(); if (!container) throw new Error('Unable to resolve stopped MinIO container')
      runSync('docker', ['run', '--rm', '--volumes-from', container, '-v', `${backupDir}:/backup:ro`, options.backupHelperImage ?? helperDefault, 'sh', '-c', 'find /data -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + && tar -C /data -xzf /backup/minio-data.tgz'])
      compose(paths, ['up', '-d', '--wait', 'minio']); results.push('MinIO restore completed')
    }
    compareCounts(manifest.databaseCounts, databaseCounts(paths))
    if (hasMinio && manifest.minioObjectCount !== null && minioObjectCount(paths, options.backupHelperImage ?? helperDefault) !== manifest.minioObjectCount) throw new Error('Restored MinIO object count mismatch')
    if (app.length) compose(paths, ['up', '-d', '--wait', ...app])
    if (!options.skipHealthCheck && services.includes('server')) await healthCheck(root, { composeFile: options.composeFile, envFile: options.envFile, baseUrl: options.baseUrl, expectedProjectName: options.expectedProjectName })
    passed = true; return join(reportDir, `restore-${stamp}.md`)
  } finally {
    const report = join(reportDir, `restore-${stamp}.md`); writeFileSync(report, ['# Restore Evidence', '', `- Target project: ${actualProject}`, `- Source project: ${manifest.projectName}`, `- Backup path: ${backupDir}`, `- Decision: ${passed ? 'PASS' : 'FAIL'}`, '', ...results.map((value) => `- PASS: ${value}`), ''].join('\n'))
  }
}
