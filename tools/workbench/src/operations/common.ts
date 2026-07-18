import { createHash } from 'node:crypto'
import { existsSync, readFileSync, statSync } from 'node:fs'
import { basename, isAbsolute, resolve } from 'node:path'
import { assertWithin } from '../lib/paths.js'
import { runSync } from '../lib/process.js'

export interface OperationPaths { root: string; composePath: string; envPath: string }
export interface BackupFile { name: string; kind: string; bytes: number; sha256: string }
export interface BackupManifest { manifestVersion: number; createdAt: string; projectName: string; sourceGitCommit: string; composeFile: string; consistencyMode: string; flywayVersion: string; databaseCounts: Record<string, number>; minioObjectCount: number | null; files: BackupFile[] }

export function resolveFrom(root: string, path: string): string { return isAbsolute(path) ? resolve(path) : resolve(root, path) }
export function sha256(path: string): string { return createHash('sha256').update(readFileSync(path)).digest('hex').toUpperCase() }

export function composeArgs(paths: OperationPaths, args: string[]): string[] {
  return ['compose', '--env-file', paths.envPath, '-f', paths.composePath, ...args]
}
export function compose(paths: OperationPaths, args: string[], stdin?: string): string {
  return runSync('docker', composeArgs(paths, args), { cwd: paths.root, stdin })
}
export function composeModel(paths: OperationPaths): Record<string, any> {
  return JSON.parse(compose(paths, ['config', '--format', 'json']))
}
export function composeProjectName(paths: OperationPaths): string {
  const name = composeModel(paths).name
  if (!name) throw new Error('Compose configuration does not expose a project name')
  return String(name)
}
export function composeServices(paths: OperationPaths): string[] { return Object.keys(composeModel(paths).services ?? {}) }

export function readEnvironment(path: string): Record<string, string> {
  return Object.fromEntries(readFileSync(path, 'utf8').split(/\r?\n/).flatMap((line) => {
    const value = line.trim(); if (!value || value.startsWith('#') || !value.includes('=')) return []
    const index = value.indexOf('='); return [[value.slice(0, index).trim(), value.slice(index + 1).trim()]]
  }))
}

export function assertProductionEnvironment(path: string): Record<string, string> {
  const values = readEnvironment(path)
  const required = ['POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD', 'MINIO_ACCESS_KEY', 'MINIO_SECRET_KEY', 'MINIO_BUCKET', 'JWT_ACCESS_SECRET', 'JWT_REFRESH_SECRET', 'INIT_ADMIN_USERNAME', 'INIT_ADMIN_PASSWORD', 'CORS_ALLOWED_ORIGINS', 'APP_BASE_URL', 'SERVER_IMAGE', 'WEB_IMAGE', 'COLLABORATION_IMAGE', 'SOURCE_COMMIT']
  for (const name of required) {
    if (!values[name]) throw new Error(`Required environment value is missing: ${name}`)
    if (/replace-with|change-?me|example\.com/i.test(values[name])) throw new Error(`Environment value still contains a placeholder: ${name}`)
  }
  if (values.JWT_ACCESS_SECRET.length < 32 || values.JWT_REFRESH_SECRET.length < 32) throw new Error('JWT secrets must contain at least 32 characters')
  for (const name of ['SERVER_IMAGE', 'WEB_IMAGE', 'COLLABORATION_IMAGE']) if (!/^[^\s:]+(?:\/[^\s:]+)*:[^\s:]+$/.test(values[name]) || values[name].endsWith(':latest')) throw new Error(`${name} must use an explicit immutable release tag`)
  if (!/^[0-9a-f]{40}$/i.test(values.SOURCE_COMMIT)) throw new Error('SOURCE_COMMIT must be a full 40-character Git commit')
  return values
}

export function readBackupManifest(backupPath: string, verifyFiles = false): BackupManifest {
  const directory = resolve(backupPath)
  const manifestPath = resolve(directory, 'manifest.json')
  if (!existsSync(manifestPath)) throw new Error(`Backup manifest not found: ${manifestPath}`)
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8')) as BackupManifest
  if (manifest.manifestVersion !== 2) throw new Error('Unsupported backup manifest version; expected version 2')
  if (!manifest.projectName || !manifest.files?.length) throw new Error('Backup manifest is incomplete')
  const names = new Set<string>()
  for (const file of manifest.files) {
    if (!file.name || isAbsolute(file.name) || basename(file.name) !== file.name || names.has(file.name)) throw new Error(`Unsafe or duplicate backup file: ${file.name}`)
    names.add(file.name)
    const path = assertWithin(directory, resolve(directory, file.name), 'backup file')
    if (!existsSync(path) || statSync(path).size !== file.bytes) throw new Error(`Backup size mismatch or missing file: ${file.name}`)
    if (verifyFiles && sha256(path) !== file.sha256.toUpperCase()) throw new Error(`Backup hash mismatch: ${file.name}`)
  }
  if (!names.has('postgres.sql')) throw new Error('Backup manifest does not contain postgres.sql')
  return manifest
}

export function databaseCounts(paths: OperationPaths): Record<string, number> {
  const sql = `select json_build_object('users',(select count(*) from users),'workspaces',(select count(*) from workspaces),'projects',(select count(*) from projects),'bases',(select count(*) from bases),'knowledge_base_spaces',(select count(*) from knowledge_base_spaces),'audit_logs',(select count(*) from audit_logs))::text;`
  const output = compose(paths, ['exec', '-T', 'postgres', 'sh', '-c', 'psql -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" "$POSTGRES_DB"'], sql)
  const json = output.split(/\r?\n/).findLast((line) => line.trim().startsWith('{'))
  if (!json) throw new Error('Database count query returned no JSON')
  return JSON.parse(json)
}
export function flywayVersion(paths: OperationPaths): string {
  return compose(paths, ['exec', '-T', 'postgres', 'sh', '-c', 'psql -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" "$POSTGRES_DB"'], `select coalesce(max(version), '') from flyway_schema_history where success;`).trim()
}
export function minioObjectCount(paths: OperationPaths, helperImage: string): number {
  const container = compose(paths, ['ps', '-q', 'minio']).trim(); if (!container) throw new Error('Unable to resolve running MinIO container')
  return Number(runSync('docker', ['run', '--rm', '--volumes-from', container, helperImage, 'sh', '-c', "find /data -type f ! -path '/data/.minio.sys/*' | wc -l"]))
}
export function compareCounts(expected: Record<string, number>, actual: Record<string, number>): void {
  for (const [name, value] of Object.entries(expected)) if (Number(actual[name]) !== Number(value)) throw new Error(`Restored count mismatch for ${name}. Expected ${value}, got ${actual[name]}`)
}
