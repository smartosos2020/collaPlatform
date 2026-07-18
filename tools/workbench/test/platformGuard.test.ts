import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'
import { activePlatformViolations } from '../src/security/platformGuard.js'

test('rejects PowerShell anywhere active while allowing archived history', () => {
  const root = mkdtempSync(join(tmpdir(), 'colla-platform-guard-'))
  mkdirSync(join(root, 'web', 'e2e'), { recursive: true })
  mkdirSync(join(root, 'scripts', 'archive', 'windows-powershell'), { recursive: true })
  writeFileSync(join(root, 'web', 'e2e', 'hidden.ps1'), 'Write-Host hidden')
  writeFileSync(join(root, 'scripts', 'archive', 'windows-powershell', 'old.ps1'), 'Write-Host old')
  writeFileSync(join(root, 'package.json'), '{}')
  assert.deepEqual(activePlatformViolations(root), ['active PowerShell file: web/e2e/hidden.ps1'])
})

test('rejects Windows-only invocations in active entry points', () => {
  const root = mkdtempSync(join(tmpdir(), 'colla-platform-entry-'))
  mkdirSync(join(root, 'deploy'), { recursive: true })
  writeFileSync(join(root, 'package.json'), JSON.stringify({ scripts: { verify: 'pwsh scripts/gate.ps1' } }))
  writeFileSync(join(root, 'deploy', 'README.md'), 'Use pnpm verify')
  const violations = activePlatformViolations(root)
  assert.equal(violations.length, 1)
  assert.match(violations[0], /package\.json/)
})
