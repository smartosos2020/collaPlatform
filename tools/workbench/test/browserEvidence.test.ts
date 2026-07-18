import assert from 'node:assert/strict'
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'
import { assertRealBrowserEvidence } from '../src/security/browserEvidence.js'

function fixture(): string {
  const root = mkdtempSync(join(tmpdir(), 'colla-evidence-'))
  mkdirSync(join(root, 'web', 'e2e', 'support'), { recursive: true })
  mkdirSync(join(root, 'web', 'shared'), { recursive: true })
  writeFileSync(join(root, 'web', 'tsconfig.json'), JSON.stringify({ compilerOptions: { baseUrl: '.', paths: { '@/*': ['*'] } } }))
  writeFileSync(join(root, 'web', 'e2e', 'support', 'api.ts'), 'export const ok = true')
  return root
}

test('scans dynamic imports, require and export-from outside e2e through aliases', () => {
  const root = fixture()
  writeFileSync(join(root, 'web', 'e2e', 'flow.spec.ts'), `import('@/shared/dynamic'); require('../shared/required'); export * from '@/shared/exported'`)
  writeFileSync(join(root, 'web', 'shared', 'dynamic.ts'), `page['route']('/api', () => {})`)
  writeFileSync(join(root, 'web', 'shared', 'required.ts'), 'export const required = true')
  writeFileSync(join(root, 'web', 'shared', 'exported.ts'), 'export const exported = true')
  assert.throws(() => assertRealBrowserEvidence('pnpm playwright test web/e2e/flow.spec.ts', root), /page\[route\]/)
})

test('rejects bracket-notation interception in a launcher and nested spec', () => {
  const root = fixture()
  writeFileSync(join(root, 'web', 'e2e', 'launcher.ps1'), `pnpm playwright test hidden.spec.ts\npage["route"]('/api', {})`)
  writeFileSync(join(root, 'web', 'e2e', 'hidden.spec.ts'), 'test("ok", () => {})')
  assert.throws(() => assertRealBrowserEvidence('web/e2e/launcher.ps1', root), /launcher\.ps1/)
})

test('accepts a real spec and sanctioned session installer', () => {
  const root = fixture()
  writeFileSync(join(root, 'web', 'e2e', 'flow.spec.ts'), `import { install } from './support/api'`)
  writeFileSync(join(root, 'web', 'e2e', 'support', 'api.ts'), 'export const install = (page: any) => page.addInitScript(() => {})')
  const result = assertRealBrowserEvidence('pnpm playwright test web/e2e/flow.spec.ts', root)
  assert.equal(result.files.length, 2)
})
