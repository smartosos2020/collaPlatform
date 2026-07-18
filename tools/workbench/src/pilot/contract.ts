import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { type PilotManifest, readPilotManifest, validatePilotManifest } from './manifest.js'

export interface PilotContractOptions {
  generateRehearsalManifest?: boolean
  rehearsalManifestPath?: string
  rehearsalPilotId?: string
  rehearsalProjectName?: string
  rehearsalBaseUrl?: string
}

function clone<T>(value: T): T { return structuredClone(value) }
function assertValid(manifest: PilotManifest, level: Parameters<typeof validatePilotManifest>[1], description: string, results: string[]): void {
  const result = validatePilotManifest(manifest, level)
  if (!result.valid) throw new Error(`${description} failed: ${result.errors.join('; ')}`)
  results.push(description)
}
function assertInvalid(manifest: PilotManifest, level: Parameters<typeof validatePilotManifest>[1], pattern: RegExp, description: string, results: string[]): void {
  const result = validatePilotManifest(manifest, level)
  if (result.valid || !pattern.test(result.errors.join('; '))) throw new Error(`${description} did not produce ${pattern}: ${result.errors.join('; ')}`)
  results.push(description)
}

export function makeInitializationReadyManifest(example: PilotManifest): PilotManifest {
  const raw = JSON.stringify(example)
    .replaceAll('replace-with-pilot-id', 'pilot-v2-contract')
    .replaceAll('https://replace-with-pilot-host', 'https://pilot.internal')
    .replaceAll('replace-with-feedback-channel', 'Pilot feedback project')
    .replace(/replace-with-(owner|admin|member-0[1-3])-username/g, 'pilot-$1')
    .replace(/replace-with-(owner|admin|member-0[1-3])-name/g, 'Pilot $1')
    .replace(/replace-with-(owner|admin|member-0[1-3])-email/g, 'pilot-$1@colla.local')
  const manifest = JSON.parse(raw)
  for (const participant of manifest.participants) participant.consentConfirmed = true
  return manifest
}

export function runPilotContract(root: string, options: PilotContractOptions = {}): string[] {
  const example = readPilotManifest(join(root, 'deploy/pilot-v2/manifest.example.json'))
  const results: string[] = []
  assertValid(example, 'structural', 'versioned example satisfies structural contract', results)
  assertInvalid(example, 'initialization', /consent|concrete|identify/i, 'example cannot masquerade as initialization-ready', results)
  const valid = makeInitializationReadyManifest(example)
  assertValid(valid, 'initialization', 'complete five-person roster is initialization-ready', results)

  const duplicate = clone(valid); duplicate.participants[1].username = duplicate.participants[0].username
  assertInvalid(duplicate, 'initialization', /username values must be unique/i, 'duplicate account is rejected', results)
  const missingSearch = clone(valid); missingSearch.scenarios = missingSearch.scenarios.filter((item: any) => item.module !== 'search')
  assertInvalid(missingSearch, 'initialization', /search.*scenario/i, 'missing required module scenario is rejected', results)
  const unsupportedGroup = clone(valid); unsupportedGroup.organization.userGroup.groupType = 'static'
  assertInvalid(unsupportedGroup, 'initialization', /groupType normal or permission/i, 'unsupported user group type is rejected', results)
  assertInvalid(valid, 'freeze', /kickoffApproval|confirmation|releaseCommit|backupManifest|sourceSnapshot/i, 'human kickoff confirmations are mandatory for freeze', results)

  const simulation = clone(valid)
  simulation.mode = 'rehearsal'
  const participantIds = simulation.participants.map((item: any) => item.participantId)
  for (const participant of simulation.participants) participant.participantKind = 'synthetic'
  Object.assign(simulation.kickoffApproval, {
    confirmationBasis: 'synthetic-personas', scopeConfirmedBy: participantIds, feedbackConfirmedBy: participantIds,
    stopConditionsConfirmedBy: participantIds, acceptedAt: '2026-07-15T09:00:00+08:00', releaseCommit: 'a'.repeat(40),
    backupManifest: '/pilot/manifest.json', sourceSnapshot: 'b'.repeat(64),
    limitationsAcknowledged: ['no-real-user-feedback', 'no-human-satisfaction-evidence', 'not-production-release-approval'], decision: 'go',
  })
  assertValid(simulation, 'simulation-freeze', 'synthetic personas can complete a limited simulation freeze', results)
  assertInvalid(simulation, 'freeze', /human|mode 'real'|human-participants/i, 'simulation evidence cannot masquerade as a real freeze', results)

  if (options.generateRehearsalManifest) {
    const rehearsal = clone(valid)
    rehearsal.mode = 'rehearsal'; rehearsal.pilotId = options.rehearsalPilotId ?? 'pilot-v2-m9-rehearsal'
    rehearsal.environment.projectName = options.rehearsalProjectName ?? 'colla-platform-pilot-m9r1'
    rehearsal.environment.baseUrl = options.rehearsalBaseUrl ?? 'http://127.0.0.1:18090'
    rehearsal.kickoffApproval.releaseCommit = 'replace-with-40-character-release-commit'
    const output = resolve(root, options.rehearsalManifestPath ?? '.local-pilot/m9-rehearsal.json')
    mkdirSync(dirname(output), { recursive: true }); writeFileSync(output, `${JSON.stringify(rehearsal, null, 2)}\n`)
    if (!readFileSync(output, 'utf8').includes(rehearsal.pilotId)) throw new Error('Rehearsal manifest was not written')
    results.push(`rehearsal manifest generated at ${output}`)
  }
  return results
}
