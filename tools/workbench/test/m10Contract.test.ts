import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'
import { repositoryRoot } from '../src/lib/paths.js'
import { runM10Simulation, validateM10Contract } from '../src/pilot/m10.js'
import { makeInitializationReadyManifest } from '../src/pilot/contract.js'

const contract = JSON.parse(readFileSync(`${repositoryRoot}/deploy/pilot-v2/m10-simulation-contract.json`, 'utf8'))

test('M10 contract preserves persona, module, round, metric and limitation boundaries', () => {
  assert.deepEqual(validateM10Contract(contract), [])
  assert.match(validateM10Contract({ ...contract, personas: contract.personas.slice(1) }).join('; '), /five synthetic identities/)
  assert.match(validateM10Contract({ ...contract, metrics: {} }).join('; '), /missing metric/)
  assert.match(validateM10Contract({ ...contract, limitations: [] }).join('; '), /limitations are incomplete/)
})

test('M10 simulation rejects confirmation before invoking Docker', () => {
  const root = mkdtempSync(join(tmpdir(), 'colla-m10-runner-')); mkdirSync(join(root, 'deploy', 'pilot-v2'), { recursive: true })
  const example = JSON.parse(readFileSync(`${repositoryRoot}/deploy/pilot-v2/manifest.example.json`, 'utf8')); const manifest = makeInitializationReadyManifest(example)
  manifest.mode = 'rehearsal'; manifest.environment.projectName = 'colla-platform-pilot-m10-test'; manifest.participants.forEach((item: any) => { item.participantKind = 'synthetic' })
  writeFileSync(join(root, 'manifest.json'), JSON.stringify(manifest)); writeFileSync(join(root, '.env'), 'COMPOSE_PROJECT_NAME=colla-platform-pilot-m10-test'); writeFileSync(join(root, 'compose.yml'), 'services: {}')
  writeFileSync(join(root, 'deploy', 'pilot-v2', 'm10-simulation-contract.json'), JSON.stringify(contract))
  assert.throws(() => runM10Simulation(root, { manifestPath: 'manifest.json', envFile: '.env', composeFile: 'compose.yml', confirmationText: 'wrong' }), /Confirmation mismatch/)
})
