import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'
import { assertProductionEnvironment, readBackupManifest } from '../src/operations/common.js'
import { restore } from '../src/operations/restore.js'
import { rollback } from '../src/operations/rollback.js'

test('backup contract rejects traversal, duplicate names and hash mismatches', () => {
  const root = mkdtempSync(join(tmpdir(), 'colla-backup-contract-'))
  const dump = 'select 1;\n'; writeFileSync(join(root, 'postgres.sql'), dump)
  const file = { name: 'postgres.sql', kind: 'postgres', bytes: Buffer.byteLength(dump), sha256: createHash('sha256').update(dump).digest('hex') }
  const manifest = { manifestVersion: 2, createdAt: new Date().toISOString(), projectName: 'colla-platform-prod', sourceGitCommit: 'a'.repeat(40), composeFile: 'deploy/docker-compose.prod.yml', consistencyMode: 'application-quiesced', flywayVersion: '53', databaseCounts: {}, minioObjectCount: null, files: [file] }
  writeFileSync(join(root, 'manifest.json'), JSON.stringify(manifest))
  assert.equal(readBackupManifest(root, true).files.length, 1)
  writeFileSync(join(root, 'manifest.json'), JSON.stringify({ ...manifest, files: [{ ...file, name: '../postgres.sql' }] }))
  assert.throws(() => readBackupManifest(root, true), /Unsafe or duplicate/)
  writeFileSync(join(root, 'manifest.json'), JSON.stringify({ ...manifest, files: [{ ...file, sha256: '0'.repeat(64) }] }))
  assert.throws(() => readBackupManifest(root, true), /hash mismatch/)
})

test('production environment contract rejects placeholders and mutable images', () => {
  const root = mkdtempSync(join(tmpdir(), 'colla-env-contract-')); const path = join(root, '.env')
  const required = {
    POSTGRES_DB: 'colla', POSTGRES_USER: 'colla', POSTGRES_PASSWORD: 'strong-value', MINIO_ACCESS_KEY: 'access', MINIO_SECRET_KEY: 'secret-value', MINIO_BUCKET: 'files',
    JWT_ACCESS_SECRET: 'a'.repeat(32), JWT_REFRESH_SECRET: 'b'.repeat(32), INIT_ADMIN_USERNAME: 'admin', INIT_ADMIN_PASSWORD: 'strong-password',
    CORS_ALLOWED_ORIGINS: 'https://colla.invalid', APP_BASE_URL: 'https://colla.invalid', SERVER_IMAGE: 'registry/colla/server:1.0.0', WEB_IMAGE: 'registry/colla/web:1.0.0',
    COLLABORATION_IMAGE: 'registry/colla/collaboration:1.0.0', SOURCE_COMMIT: 'c'.repeat(40),
  }
  writeFileSync(path, Object.entries(required).map(([name, value]) => `${name}=${value}`).join('\n'))
  assert.equal(assertProductionEnvironment(path).SOURCE_COMMIT, 'c'.repeat(40))
  writeFileSync(path, Object.entries({ ...required, SERVER_IMAGE: 'registry/colla/server:latest' }).map(([name, value]) => `${name}=${value}`).join('\n'))
  assert.throws(() => assertProductionEnvironment(path), /immutable release tag/)
})

test('destructive operations require exact confirmation before touching Docker', async () => {
  await assert.rejects(() => restore('.', { backupPath: '.', expectedProjectName: 'colla-platform-prod', confirmationText: 'RESTORE:wrong', confirmRestore: true }), /exact --confirmation-text/)
  await assert.rejects(() => rollback('.', { serverImage: 'server:1', webImage: 'web:1', collaborationImage: 'collab:1', expectedProjectName: 'colla-platform-prod', confirmationText: 'wrong', confirmRollback: true }), /exact confirmation/)
})
