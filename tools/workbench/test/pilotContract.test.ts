import assert from 'node:assert/strict'
import { existsSync, mkdtempSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'
import { makeInitializationReadyManifest, runPilotContract } from '../src/pilot/contract.js'
import { ensureGroupResourcePermission } from '../src/pilot/initialize.js'

test('pilot contract retains all positive and negative validation scenarios', () => {
  const root = mkdtempSync(join(tmpdir(), 'colla-pilot-contract-'))
  mkdirSync(join(root, 'deploy', 'pilot-v2'), { recursive: true })
  const source = join(process.cwd(), '..', '..', 'deploy', 'pilot-v2', 'manifest.example.json')
  writeFileSync(join(root, 'deploy', 'pilot-v2', 'manifest.example.json'), readFileSync(source))
  const results = runPilotContract(root, { generateRehearsalManifest: true })
  assert.equal(results.length, 10)
  assert(existsSync(join(root, '.local-pilot', 'm9-rehearsal.json')))
  assert.doesNotThrow(() => makeInitializationReadyManifest(JSON.parse(readFileSync(source, 'utf8'))))
})

test('group resource permission verifies exact edit grants and creates missing grants', async () => {
  const receipts: string[] = []
  const calls: Array<{ method: string; path: string; body?: unknown }> = []
  const api = {
    request: async (method: string, path: string, body?: unknown): Promise<any> => {
      calls.push({ method, path, body })
      if (method === 'GET') return []
      return { id: 'permission-1' }
    },
  }
  await ensureGroupResourcePermission(api as any, 'base', 'base-1', { id: 'group-1', code: 'pilot-group' }, (kind, _code, status) => receipts.push(`${kind}:${status}`))
  assert.deepEqual(receipts, ['base-permission:CREATED'])
  assert.deepEqual(calls[1], { method: 'POST', path: '/resource-permissions/base/base-1', body: { subjectType: 'user_group', subjectId: 'group-1', permissionLevel: 'edit', confirmHighRisk: true } })
})
