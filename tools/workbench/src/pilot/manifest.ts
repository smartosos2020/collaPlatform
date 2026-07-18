import { createHash } from 'node:crypto'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { runSync } from '../lib/process.js'

export type ValidationLevel = 'structural' | 'initialization' | 'freeze' | 'simulation-freeze'
export interface PilotValidation { valid: boolean; level: ValidationLevel; errors: string[]; warnings: string[] }
export type PilotManifest = Record<string, any>

function placeholder(value: unknown): boolean { return value == null || !String(value).trim() || /replace-with|example\.com|placeholder|\btodo\b/i.test(String(value)) }
function names(value: unknown): string[] {
  if (Array.isArray(value)) return value.flatMap(names)
  if (value && typeof value === 'object') return Object.entries(value).flatMap(([name, nested]) => [name, ...names(nested)])
  return []
}
function validDate(value: unknown): boolean { return !placeholder(value) && !Number.isNaN(Date.parse(String(value))) }

export function readPilotManifest(path: string): PilotManifest {
  const manifest = JSON.parse(readFileSync(path, 'utf8'))
  const sensitive = names(manifest).filter((name) => /password|secret|accessToken|refreshToken|credential/i.test(name))
  if (sensitive.length) throw new Error(`Pilot manifest must not contain credentials or secret fields: ${[...new Set(sensitive)].join(', ')}`)
  return manifest
}

export function sourceSnapshot(root: string): string {
  const scopes = ['server', 'web', 'deploy', 'tools', 'package.json', 'pnpm-lock.yaml']
  const tracked = runSync('git', ['ls-files', '-z', '--', ...scopes], { cwd: root, trimOutput: false }).split('\0').filter(Boolean)
  const untracked = runSync('git', ['ls-files', '--others', '--exclude-standard', '-z', '--', ...scopes], { cwd: root, trimOutput: false }).split('\0').filter(Boolean)
  const entries = [...new Set([...tracked, ...untracked])].sort().map((path) => `${path.replaceAll('\\', '/')}\t${createHash('sha256').update(readFileSync(join(root, path))).digest('hex')}`)
  if (!entries.length) throw new Error('Pilot source snapshot contains no files')
  return createHash('sha256').update(entries.join('\n')).digest('hex')
}

export function validatePilotManifest(manifest: PilotManifest, level: ValidationLevel): PilotValidation {
  const errors: string[] = []; const warnings: string[] = []
  const requiredRoot = ['schemaVersion', 'pilotId', 'mode', 'environment', 'schedule', 'participants', 'organization', 'templates', 'scenarios', 'dataPolicy', 'issuePolicy', 'metrics', 'kickoffApproval']
  for (const name of requiredRoot) if (!(name in manifest)) errors.push(`Missing root field: ${name}`)
  if (errors.length) return { valid: false, level, errors, warnings }
  if (manifest.schemaVersion !== 1) errors.push('schemaVersion must be 1')
  if (!['real', 'rehearsal'].includes(manifest.mode)) errors.push('mode must be real or rehearsal')
  if (level !== 'structural' && placeholder(manifest.pilotId)) errors.push('pilotId must be concrete before initialization')
  for (const field of ['projectName', 'baseUrl', 'dataPrefix', 'workspaceLabel']) if (!String(manifest.environment?.[field] ?? '').trim()) errors.push(`environment.${field} is required`)
  if (level !== 'structural' && placeholder(manifest.environment?.baseUrl)) errors.push('environment.baseUrl must identify the real or isolated pilot target')
  if (!String(manifest.environment?.dataPrefix ?? '').endsWith('-')) errors.push("environment.dataPrefix must end with '-'")
  for (const field of ['kickoffAt', 'startAt', 'endAt']) if (!validDate(manifest.schedule?.[field])) errors.push(`schedule.${field} must be an ISO-8601 timestamp`)
  if (validDate(manifest.schedule?.kickoffAt) && validDate(manifest.schedule?.startAt) && Date.parse(manifest.schedule.kickoffAt) > Date.parse(manifest.schedule.startAt)) errors.push('schedule.kickoffAt must not be after startAt')
  if (validDate(manifest.schedule?.startAt) && validDate(manifest.schedule?.endAt) && Date.parse(manifest.schedule.startAt) >= Date.parse(manifest.schedule.endAt)) errors.push('schedule.endAt must be after startAt')
  for (const field of ['dailyWindow', 'feedbackChannel']) if (placeholder(manifest.schedule?.[field])) (level === 'structural' ? warnings : errors).push(`schedule.${field} must be concrete`)
  const participants: any[] = Array.isArray(manifest.participants) ? manifest.participants : []
  if (participants.length < 5 || participants.length > 10) errors.push('participants must contain 5-10 people')
  for (const field of ['participantId', 'username', 'displayName', 'email']) {
    const values = participants.map((item) => String(item[field] ?? '')); if (values.some((value) => !value) || new Set(values).size !== values.length) errors.push(`Participant ${field} values must be unique and present`)
  }
  for (const participant of participants) {
    if (!['owner', 'admin', 'member'].includes(participant.pilotRole)) errors.push(`Participant '${participant.participantId}' has invalid pilotRole`)
    if (!['admin', 'member'].includes(participant.platformRole)) errors.push(`Participant '${participant.participantId}' has invalid platformRole`)
    if (!participant.responsibilities?.length || placeholder(participant.availability) || placeholder(participant.feedbackChannel)) (level === 'structural' ? warnings : errors).push(`Participant '${participant.participantId}' requires concrete responsibilities, availability, and feedbackChannel`)
    if (level !== 'structural' && (!participant.consentConfirmed || ['username', 'displayName', 'email'].some((field) => placeholder(participant[field])))) errors.push(`Participant '${participant.participantId}' requires concrete identity and consent`)
    if (level === 'freeze' && participant.participantKind !== 'human') errors.push(`Participant '${participant.participantId}' must be marked human`)
    if (level === 'simulation-freeze' && participant.participantKind !== 'synthetic') errors.push(`Participant '${participant.participantId}' must be marked synthetic`)
  }
  if (participants.filter((item) => item.pilotRole === 'owner').length !== 1) errors.push('Exactly one pilot owner is required')
  if (participants.filter((item) => item.pilotRole === 'admin').length < 1 || participants.filter((item) => item.pilotRole === 'member').length < 3) errors.push('At least one admin and three members are required')
  const departments: any[] = manifest.organization?.departments ?? []; const departmentCodes = departments.map((item) => String(item.code ?? ''))
  if (!departmentCodes.length || new Set(departmentCodes).size !== departmentCodes.length) errors.push('organization.departments requires unique department codes')
  for (const participant of participants) if (!departmentCodes.includes(String(participant.departmentCode))) errors.push(`Participant '${participant.participantId}' references unknown department`)
  for (const department of departments) if (department.parentCode && !departmentCodes.includes(String(department.parentCode))) errors.push(`Department '${department.code}' references unknown parent`)
  const group = manifest.organization?.userGroup; if (!group || placeholder(group.code) || placeholder(group.name) || !['normal', 'permission'].includes(group.groupType)) errors.push('organization.userGroup requires concrete code/name and groupType normal or permission')
  for (const [section, fields] of Object.entries({ project: ['projectKey', 'name', 'description'], knowledge: ['code', 'name', 'templateTitle', 'templateContent'], base: ['name', 'tableName'], approval: ['formKey', 'formName'] })) for (const field of fields) if (placeholder(manifest.templates?.[section]?.[field])) errors.push(`templates.${section}.${field} is required`)
  const scenarios: any[] = manifest.scenarios ?? []; const scenarioIds = scenarios.map((item) => item.scenarioId); if (new Set(scenarioIds).size !== scenarioIds.length) errors.push('Scenario ids must be unique')
  for (const module of ['im', 'project', 'knowledge', 'base', 'approval', 'search']) if (!scenarios.some((item) => item.module === module)) errors.push(`At least one '${module}' scenario is required`)
  const participantIds = participants.map((item) => item.participantId)
  for (const scenario of scenarios) if (!participantIds.includes(scenario.ownerParticipantId) || placeholder(scenario.title) || placeholder(scenario.successCriteria) || Number(scenario.targetMinutes) <= 0) errors.push(`Scenario '${scenario.scenarioId}' is invalid`)
  const forbidden: string[] = manifest.dataPolicy?.forbiddenCategories ?? []; for (const category of ['production credentials', 'access tokens', 'government identifiers', 'payment data', 'medical data', 'unapproved customer data']) if (!forbidden.includes(category)) errors.push(`dataPolicy.forbiddenCategories must include '${category}'`)
  if (manifest.dataPolicy?.retentionDays < 1 || manifest.dataPolicy?.retentionDays > 90 || !manifest.dataPolicy?.testDataPrefixRequired || !participantIds.includes(manifest.dataPolicy?.cleanupOwnerParticipantId)) errors.push('dataPolicy retention, prefix, or cleanup owner is invalid')
  const levels: any[] = manifest.issuePolicy?.levels ?? []; if (levels.map((item) => item.code).join(',') !== 'P0,P1,P2,P3') errors.push('issuePolicy.levels must define P0,P1,P2,P3 in order')
  if ((manifest.issuePolicy?.requiredFields?.length ?? 0) < 8 || (manifest.issuePolicy?.stopConditions?.length ?? 0) < 3) errors.push('issuePolicy requires complete fields and stop conditions')
  for (const code of ['activeParticipantRate', 'scenarioCompletionRate', 'criticalErrorCount', 'blockedParticipantCount', 'satisfactionScore']) if ((manifest.metrics ?? []).filter((item: any) => item.code === code).length !== 1) errors.push(`Metric '${code}' must be defined exactly once`)
  for (const metric of manifest.metrics ?? []) if (!['>=', '<=', '='].includes(metric.operator) || placeholder(metric.description) || placeholder(metric.source)) errors.push(`Metric '${metric.code}' is invalid`)
  if (level === 'freeze' || level === 'simulation-freeze') {
    for (const field of ['scopeConfirmedBy', 'feedbackConfirmedBy', 'stopConditionsConfirmedBy']) for (const id of participantIds) if (!(manifest.kickoffApproval?.[field] ?? []).includes(id)) errors.push(`kickoffApproval.${field} is missing '${id}'`)
    if (!validDate(manifest.kickoffApproval?.acceptedAt) || !/^[0-9a-f]{40}$/i.test(manifest.kickoffApproval?.releaseCommit ?? '') || placeholder(manifest.kickoffApproval?.backupManifest) || manifest.kickoffApproval?.decision !== 'go') errors.push('kickoffApproval freeze evidence is incomplete')
  }
  if (level === 'freeze' && (manifest.mode !== 'real' || manifest.kickoffApproval?.confirmationBasis !== 'human-participants')) errors.push("A real freeze requires mode 'real' and human-participants")
  if (level === 'simulation-freeze') {
    if (manifest.mode !== 'rehearsal' || manifest.kickoffApproval?.confirmationBasis !== 'synthetic-personas' || !/^[0-9a-f]{64}$/i.test(manifest.kickoffApproval?.sourceSnapshot ?? '')) errors.push('A simulation freeze requires rehearsal mode, synthetic personas, and source snapshot')
    for (const limitation of ['no-real-user-feedback', 'no-human-satisfaction-evidence', 'not-production-release-approval']) if (!(manifest.kickoffApproval?.limitationsAcknowledged ?? []).includes(limitation)) errors.push(`Simulation freeze must acknowledge '${limitation}'`)
  }
  return { valid: errors.length === 0, level, errors, warnings }
}

export function writeValidationReport(root: string, manifest: PilotManifest, validation: PilotValidation, outputDir: string): { json: string; markdown: string } {
  const directory = resolve(root, outputDir); mkdirSync(directory, { recursive: true }); const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, '')
  const json = join(directory, `pilot-v2-${validation.level}-${stamp}.json`); const markdown = join(directory, `pilot-v2-${validation.level}-${stamp}.md`)
  const result = { pilotId: manifest.pilotId, checkedAt: new Date().toISOString(), level: validation.level, decision: validation.valid ? 'PASS' : 'BLOCKED', participantCount: manifest.participants?.length ?? 0, scenarioCount: manifest.scenarios?.length ?? 0, errors: validation.errors, warnings: validation.warnings }
  writeFileSync(json, JSON.stringify(result, null, 2)); writeFileSync(markdown, ['# PILOT-V2 Manifest Check', '', `- Pilot: ${manifest.pilotId}`, `- Level: ${validation.level}`, `- Decision: ${result.decision}`, '', '## Errors', ...(validation.errors.length ? validation.errors.map((value) => `- ${value}`) : ['- None']), '', '## Warnings', ...(validation.warnings.length ? validation.warnings.map((value) => `- ${value}`) : ['- None']), ''].join('\n')); return { json, markdown }
}
