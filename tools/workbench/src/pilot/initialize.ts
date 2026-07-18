import { mkdirSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { PilotApi } from './api.js'
import { readPilotManifest, validatePilotManifest } from './manifest.js'

interface Entry { kind: string; code: string; status: string; id?: string; detail: string }
export interface InitializeOptions { manifestPath: string; apiBaseUrl?: string; reportDirectory?: string; apply?: boolean; confirmationText?: string }
function flattenDepartments(nodes: any[]): any[] { return nodes.flatMap((node) => node?.department ? [node.department, ...flattenDepartments(node.children ?? [])] : []) }

export async function ensureGroupResourcePermission(api: PilotApi, resourceType: 'knowledge_base' | 'base', resourceId: string, group: any, receipt: (kind: string, code: string, status: string, value: any, detail: string) => void): Promise<void> {
  const permissions = await api.request<any[]>('GET', `/resource-permissions/${resourceType}/${resourceId}`)
  const existing = permissions.find((item) => item.subjectType === 'user_group' && item.subjectId === group.id && item.effectiveStatus === 'active')
  if (existing) {
    if (existing.permissionLevel !== 'edit') throw new Error(`Existing ${resourceType} permission for group '${group.code}' is '${existing.permissionLevel}', expected 'edit'`)
    receipt(`${resourceType}-permission`, group.code, 'VERIFIED', existing, 'group has edit permission')
    return
  }
  const created = await api.request('POST', `/resource-permissions/${resourceType}/${resourceId}`, { subjectType: 'user_group', subjectId: group.id, permissionLevel: 'edit', confirmHighRisk: true })
  receipt(`${resourceType}-permission`, group.code, 'CREATED', created, 'granted edit permission to participant group')
}

export async function initializePilot(root: string, options: InitializeOptions): Promise<{ receipt: string; report: string }> {
  const manifest = readPilotManifest(resolve(root, options.manifestPath)); const validation = validatePilotManifest(manifest, 'initialization'); if (!validation.valid) throw new Error(validation.errors.join('; '))
  const expected = `INITIALIZE:${manifest.pilotId}:${manifest.environment.projectName}`; if (options.apply && options.confirmationText !== expected) throw new Error(`Confirmation must equal ${expected}`)
  const username = process.env.COLLA_PILOT_ADMIN_USERNAME; const password = process.env.COLLA_PILOT_ADMIN_PASSWORD; const initialPassword = process.env.COLLA_PILOT_INITIAL_PASSWORD
  if (!username || !password) throw new Error('COLLA_PILOT_ADMIN_USERNAME and COLLA_PILOT_ADMIN_PASSWORD are required')
  if (options.apply && initialPassword && initialPassword.length < 12) throw new Error('COLLA_PILOT_INITIAL_PASSWORD must contain at least 12 characters')
  const anonymous = new PilotApi(options.apiBaseUrl ?? manifest.environment.baseUrl); const session = await anonymous.request<any>('POST', '/auth/login', { username, password, deviceType: 'web', deviceFingerprint: `pilot-v2-initializer-${manifest.pilotId}`, deviceName: 'PILOT-V2 initializer', appVersion: 'pilot-v2' }); const api = anonymous.withToken(session.accessToken)
  const entries: Entry[] = [{ kind: 'authentication', code: username, status: 'VERIFIED', detail: 'admin API session established' }]
  const receipt = (kind: string, code: string, status: string, value: any, detail: string): void => { entries.push({ kind, code, status, id: value?.id, detail }) }
  const tree = await api.request<any[]>('GET', '/admin/departments/tree'); const departments = new Map(flattenDepartments(tree).map((item) => [item.code, item]))
  const pending = [...manifest.organization.departments]
  while (pending.length) {
    let progress = false
    for (const department of [...pending]) {
      const existing = departments.get(department.code)
      const parent = department.parentCode ? departments.get(department.parentCode) : undefined
      if (!existing && options.apply && department.parentCode && !parent) continue
      let value = existing; let status = existing ? 'VERIFIED' : 'PLANNED'
      if (!value && options.apply) { value = await api.request('POST', '/admin/departments', { parentId: parent?.id ?? null, code: department.code, name: department.name, sortOrder: department.sortOrder }); departments.set(department.code, value); status = 'CREATED' }
      receipt('department', department.code, status, value, value ? 'department available' : `create${department.parentCode ? ` after parent '${department.parentCode}'` : ''}`)
      pending.splice(pending.indexOf(department), 1); progress = true
    }
    if (!progress) throw new Error(`Department hierarchy could not be resolved: ${pending.map((item) => item.code).join(', ')}`)
  }
  const users = new Map((await api.request<any[]>('GET', '/admin/users')).map((item) => [item.username, item]))
  for (const participant of manifest.participants) {
    let value = users.get(participant.username)
    if (!value && options.apply) { if (!initialPassword) throw new Error(`COLLA_PILOT_INITIAL_PASSWORD is required for ${participant.username}`); value = await api.request('POST', '/admin/users', { username: participant.username, password: initialPassword, displayName: participant.displayName, email: participant.email, roleCode: participant.platformRole, primaryDepartmentId: departments.get(participant.departmentCode)?.id }); users.set(participant.username, value) }
    if (value && (value.status !== 'active' || !(value.roles ?? []).includes(participant.platformRole))) throw new Error(`Participant account is inactive or has wrong role: ${participant.username}`)
    receipt('participant', participant.username, value ? 'VERIFIED' : 'PLANNED', value, value ? 'existing or created account' : 'create account')
  }
  const groupTemplate = manifest.organization.userGroup; let group = (await api.request<any[]>('GET', '/admin/user-groups')).find((item) => item.code === groupTemplate.code)
  if (!group && options.apply) group = await api.request('POST', '/admin/user-groups', { code: groupTemplate.code, name: groupTemplate.name, description: groupTemplate.description, groupType: groupTemplate.groupType })
  receipt('user-group', groupTemplate.code, group ? 'VERIFIED' : 'PLANNED', group, group ? 'existing or created group' : 'create group')
  if (options.apply && group) {
    const members = await api.request<any[]>('GET', `/admin/user-groups/${group.id}/members`)
    for (const participant of manifest.participants) { const user = users.get(participant.username); if (!members.some((item) => item.subjectType === 'user' && item.subjectId === user.id)) await api.request('POST', `/admin/user-groups/${group.id}/members`, { subjectType: 'user', subjectId: user.id }); receipt('group-member', participant.username, 'VERIFIED', user, 'participant belongs to group') }
  }
  const projectTemplate = manifest.templates.project; let project = (await api.request<any[]>('GET', '/projects')).find((item) => item.projectKey === projectTemplate.projectKey)
  if (!project && options.apply) project = await api.request('POST', '/projects', { projectKey: projectTemplate.projectKey, name: projectTemplate.name, description: projectTemplate.description, memberIds: manifest.participants.map((item: any) => users.get(item.username)?.id).filter(Boolean) })
  receipt('project', projectTemplate.projectKey, project ? 'VERIFIED' : 'PLANNED', project, project ? 'pilot project available' : 'create pilot project')
  const knowledge = manifest.templates.knowledge; let space = (await api.request<any[]>('GET', '/knowledge-bases')).find((item) => item.code === knowledge.code)
  if (!space && options.apply) { const detail = await api.request<any>('POST', '/knowledge-bases', { name: knowledge.name, code: knowledge.code, description: knowledge.description, icon: 'book', coverUrl: null, visibility: 'private', defaultPermissionLevel: 'view' }); space = detail.space }
  receipt('knowledge-space', knowledge.code, space ? 'VERIFIED' : 'PLANNED', space, space ? 'knowledge space available' : 'create knowledge space')
  if (options.apply && space) {
    const templates = await api.request<any[]>('GET', `/knowledge-bases/${space.id}/templates`)
    let template = templates.find((item) => item.title === knowledge.templateTitle)
    if (!template) template = await api.request('POST', `/knowledge-bases/${space.id}/templates`, { title: knowledge.templateTitle, description: knowledge.templateDescription, category: knowledge.templateCategory, content: knowledge.templateContent })
    receipt('knowledge-template', knowledge.templateTitle, templates.some((item) => item.title === knowledge.templateTitle) ? 'VERIFIED' : 'CREATED', template, 'knowledge template available')
    if (!group) throw new Error('Participant group is required before knowledge permission setup')
    await ensureGroupResourcePermission(api, 'knowledge_base', space.id, group, receipt)
  } else receipt('knowledge_base-permission', groupTemplate.code, 'PLANNED', undefined, 'grant edit permission to participant group')
  const baseTemplate = manifest.templates.base; let base = (await api.request<any[]>('GET', '/bases')).find((item) => item.name === baseTemplate.name)
  if (!base && options.apply) base = (await api.request<any>('POST', '/bases', { name: baseTemplate.name, description: baseTemplate.description })).base
  receipt('base', baseTemplate.name, base ? 'VERIFIED' : 'PLANNED', base, base ? 'Base available' : 'create Base')
  if (options.apply && base) {
    let detail = await api.request<any>('GET', `/bases/${base.id}`)
    let table = (detail.tables ?? []).find((item: any) => item.name === baseTemplate.tableName)
    if (!table) table = (await api.request<any>('POST', `/bases/${base.id}/tables`, { name: baseTemplate.tableName })).table
    receipt('base-table', baseTemplate.tableName, (detail.tables ?? []).some((item: any) => item.name === baseTemplate.tableName) ? 'VERIFIED' : 'CREATED', table, 'table available')
    if (!group) throw new Error('Participant group is required before Base permission setup')
    await ensureGroupResourcePermission(api, 'base', base.id, group, receipt)
    detail = await api.request<any>('GET', `/bases/${base.id}`)
    for (const participant of manifest.participants) {
      const user = users.get(participant.username)
      if (!user) throw new Error(`Participant account is unavailable: ${participant.username}`)
      const member = (detail.members ?? []).find((item: any) => item.userId === user.id)
      if (member?.permissionLevel === 'edit') {
        receipt('base-member', participant.username, 'VERIFIED', member, 'member has edit permission')
        continue
      }
      detail = await api.request<any>('POST', `/bases/${base.id}/members`, { userId: user.id, permissionLevel: 'edit' })
      const granted = (detail.members ?? []).find((item: any) => item.userId === user.id)
      receipt('base-member', participant.username, 'CREATED', granted ?? user, 'granted edit permission')
    }
  } else {
    receipt('base-permission', groupTemplate.code, 'PLANNED', undefined, 'grant edit permission to participant group')
    receipt('base-members', baseTemplate.name, 'PLANNED', undefined, 'grant edit permission to every participant')
  }
  const approval = manifest.templates.approval; const form = (await api.request<any[]>('GET', '/approvals/forms')).find((item) => item.formKey === approval.formKey && item.enabled); if (!form) throw new Error(`Required approval form '${approval.formKey}' is unavailable`); receipt('approval-form', form.formKey, 'VERIFIED', form, form.name)
  const directory = resolve(root, options.reportDirectory ?? '.local-reports'); mkdirSync(directory, { recursive: true }); const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, '')
  const receiptPath = join(directory, `pilot-v2-initialize-${stamp}.json`); const reportPath = join(directory, `pilot-v2-initialize-${stamp}.md`); const decision = options.apply ? 'APPLIED' : 'PLAN-READY'
  writeFileSync(receiptPath, JSON.stringify({ schemaVersion: 1, pilotId: manifest.pilotId, targetProject: manifest.environment.projectName, apiBaseUrl: anonymous.root, mode: options.apply ? 'apply' : 'plan', decision, startedAt: new Date().toISOString(), finishedAt: new Date().toISOString(), entries }, null, 2)); writeFileSync(reportPath, ['# PILOT-V2 Initialization', '', `- Pilot: ${manifest.pilotId}`, `- Decision: ${decision}`, '', '| Kind | Code | Status | Id | Detail |', '| --- | --- | --- | --- | --- |', ...entries.map((item) => `| ${item.kind} | ${item.code} | ${item.status} | ${item.id ?? ''} | ${item.detail} |`), ''].join('\n'))
  return { receipt: receiptPath, report: reportPath }
}
