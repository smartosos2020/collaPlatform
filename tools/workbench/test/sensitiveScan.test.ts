import assert from 'node:assert/strict'
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'
import { scanSensitiveData } from '../src/security/sensitiveScan.js'

function fixture(): string {
  const root = mkdtempSync(join(tmpdir(), 'colla-secret-'))
  mkdirSync(join(root, 'scripts'), { recursive: true })
  writeFileSync(join(root, 'scripts', 'sensitive-scan-allowlist.tsv'), '')
  return root
}

test('detects unquoted dotenv, YAML and JSON credential values', () => {
  const root = fixture()
  writeFileSync(join(root, '.env'), 'PASSWORD=hunter2\n')
  writeFileSync(join(root, 'config.yaml'), 'secret-key: hunter2\n')
  writeFileSync(join(root, 'config.json'), '{"token":"hunter2"}')
  const result = scanSensitiveData(root, { writeReport: false })
  assert.deepEqual(result.hits.map((hit) => hit.path).sort(), ['.env', 'config.json', 'config.yaml'])
})

test('does not flag environment references or exact allowlisted fixtures', () => {
  const root = fixture()
  writeFileSync(join(root, '.env'), 'PASSWORD=${APP_PASSWORD}\n')
  writeFileSync(join(root, 'config.yaml'), 'secret: ${APP_SECRET}\n')
  writeFileSync(join(root, 'fixture.ts'), `const password = 'hunter2'`)
  writeFileSync(join(root, 'scripts', 'sensitive-scan-allowlist.tsv'), `fixture.ts\tcredential-assignment\tisolated fixture\n`)
  const result = scanSensitiveData(root, { writeReport: false })
  assert.equal(result.hits.length, 0)
  assert.equal(result.waived, 1)
})
