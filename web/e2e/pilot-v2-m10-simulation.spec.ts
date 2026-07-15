import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'

import { expect, test, type APIRequestContext, type APIResponse, type Browser, type Page } from '@playwright/test'

import { apiBaseUrl, bearer, loginByApi, type E2eSession } from './support/api'
import { LoginPage, UserWorkspacePage } from './support/pageObjects'

type Phase = 'baseline' | 'retry' | 'fault' | 'recovery'
type ModuleName = 'im' | 'project' | 'knowledge' | 'base' | 'approval' | 'search'
type FailureClass = 'product' | 'automation-harness' | 'environment' | 'expected-fault'

type StepEvidence = {
  stepId: string
  roundId: string
  phase: Phase
  category: 'authentication' | 'scenario' | 'fault' | 'consistency' | 'ui'
  module?: ModuleName
  persona?: string
  target: string
  startedAt: string
  finishedAt: string
  durationMs: number
  status: 'passed' | 'failed'
  failureClass?: FailureClass
  severity?: 'P0' | 'P1' | 'P2' | 'P3'
  detail: string
}

type RunState = {
  runId: string
  dataPrefix: string
  users: Record<string, string>
  im: { conversationId: string }
  project: { projectId: string; issueId: string }
  knowledge: { spaceId: string; itemId: string }
  base: { baseId: string; tableId: string; recordId: string }
  approval: { instanceId: string }
  search: { token: string }
}

const phase = (process.env.COLLA_E2E_M10_PHASE ?? 'baseline') as Phase
const runId = process.env.COLLA_E2E_M10_RUN_ID ?? `m10-${Date.now()}`
const evidencePath = process.env.COLLA_E2E_M10_EVIDENCE_PATH
const statePath = process.env.COLLA_E2E_M10_STATE_PATH
const syntheticPassword = process.env.COLLA_E2E_MEMBER_PASSWORD
const personas = ['pilot-owner', 'pilot-admin', 'pilot-member-01', 'pilot-member-02', 'pilot-member-03'] as const
const roundId = phase === 'baseline'
  ? 'round-1-baseline'
  : phase === 'retry'
    ? 'round-2-retry'
    : 'round-3-fault-recovery'
const steps: StepEvidence[] = []

function required(value: string | undefined, label: string) {
  if (!value) throw new Error(`${label} is required`)
  return value
}

async function responseJson<T>(response: APIResponse, target: string): Promise<T> {
  if (!response.ok()) {
    throw new Error(`${target} returned HTTP ${response.status()}: ${(await response.text()).slice(0, 300)}`)
  }
  return await response.json() as T
}

async function recordStep<T>(
  input: Omit<StepEvidence, 'startedAt' | 'finishedAt' | 'durationMs' | 'status' | 'detail'>,
  action: () => Promise<T>,
  describe: (value: T) => string,
): Promise<T | undefined> {
  const started = Date.now()
  const startedAt = new Date(started).toISOString()
  try {
    const value = await action()
    const finished = Date.now()
    steps.push({
      ...input,
      startedAt,
      finishedAt: new Date(finished).toISOString(),
      durationMs: finished - started,
      status: 'passed',
      detail: describe(value),
    })
    return value
  } catch (error) {
    const finished = Date.now()
    steps.push({
      ...input,
      startedAt,
      finishedAt: new Date(finished).toISOString(),
      durationMs: finished - started,
      status: 'failed',
      failureClass: input.failureClass === 'expected-fault' ? 'product' : input.failureClass,
      detail: error instanceof Error ? error.message : String(error),
    })
    return undefined
  }
}

async function loginAll(request: APIRequestContext) {
  const password = required(syntheticPassword, 'COLLA_E2E_MEMBER_PASSWORD')
  const sessions = new Map<string, E2eSession>()
  for (const username of personas) {
    const session = await recordStep({
      stepId: `${phase}-auth-${username}`,
      roundId,
      phase,
      category: 'authentication',
      persona: username,
      target: '/api/auth/login',
      failureClass: 'product',
      severity: 'P1',
    }, () => loginByApi(request, username, password), () => 'real API authentication succeeded')
    if (session) sessions.set(username, session)
  }
  return sessions
}

async function loginThroughUi(page: Page, username: string, password: string) {
  const loginPage = new LoginPage(page)
  await loginPage.open()
  await loginPage.signIn(username, password)
  await expect(page).not.toHaveURL(/\/login(?:\?|$)/)
  await new UserWorkspacePage(page).expectVisible()
}

async function verifyAllPersonasThroughUi(browser: Browser) {
  const password = required(syntheticPassword, 'COLLA_E2E_MEMBER_PASSWORD')
  for (const username of personas) {
    await recordStep({
      stepId: `baseline-ui-${username}`,
      roundId,
      phase,
      category: 'ui',
      persona: username,
      target: '/login -> workspace',
      failureClass: 'automation-harness',
      severity: 'P2',
    }, async () => {
      const context = await browser.newContext()
      try {
        await loginThroughUi(await context.newPage(), username, password)
      } finally {
        await context.close()
      }
      return username
    }, (value) => `${value} reached the real member workspace through the login UI`)
  }
}

async function currentUserId(request: APIRequestContext, session: E2eSession) {
  const response = await request.get(`${apiBaseUrl}/auth/me`, { headers: bearer(session) })
  return (await responseJson<{ id: string }>(response, '/auth/me')).id
}

async function runBaseline(request: APIRequestContext, browser: Browser) {
  const sessions = await loginAll(request)
  await verifyAllPersonasThroughUi(browser)
  if (sessions.size !== personas.length) return

  const userIds: Record<string, string> = {}
  for (const username of personas) userIds[username] = await currentUserId(request, sessions.get(username)!)
  const token = runId.replace(/[^a-zA-Z0-9]/g, '').slice(-14).toLowerCase()
  const dataPrefix = `M10-${token}`
  const state: Partial<RunState> = { runId, dataPrefix, users: userIds, search: { token } }

  const conversation = await recordStep({
    stepId: 'baseline-im', roundId, phase, category: 'scenario', module: 'im', persona: 'pilot-member-03',
    target: '/api/conversations + /messages', failureClass: 'product', severity: 'P1',
  }, async () => {
    const session = sessions.get('pilot-member-03')!
    const created = await responseJson<{ id: string }>(await request.post(`${apiBaseUrl}/conversations`, {
      headers: bearer(session),
      data: { conversationType: 'group', title: `${dataPrefix} continuous run`, memberIds: [userIds['pilot-member-01'], userIds['pilot-member-02']] },
    }), 'create conversation')
    await responseJson(await request.post(`${apiBaseUrl}/conversations/${created.id}/messages`, {
      headers: bearer(session),
      data: { clientMessageId: crypto.randomUUID(), messageType: 'text', content: `${dataPrefix} baseline collaboration message` },
    }), 'send baseline message')
    return { conversationId: created.id }
  }, (value) => `conversation ${value.conversationId} and baseline message created`)
  if (conversation) state.im = conversation

  const project = await recordStep({
    stepId: 'baseline-project', roundId, phase, category: 'scenario', module: 'project', persona: 'pilot-member-01',
    target: '/api/projects + /issues', failureClass: 'product', severity: 'P1',
  }, async () => {
    const session = sessions.get('pilot-member-01')!
    const created = await responseJson<{ id: string }>(await request.post(`${apiBaseUrl}/projects`, {
      headers: bearer(session),
      data: { projectKey: `M10${token.slice(-8).toUpperCase()}`, name: `${dataPrefix} project`, description: 'M10 synthetic continuous run', memberIds: [userIds['pilot-member-02']] },
    }), 'create project')
    const issue = await responseJson<{ issue: { id: string } }>(await request.post(`${apiBaseUrl}/projects/${created.id}/issues`, {
      headers: bearer(session),
      data: { issueType: 'task', title: `${dataPrefix} baseline issue`, description: 'Traceable M10 scenario', priority: 'medium' },
    }), 'create issue')
    return { projectId: created.id, issueId: issue.issue.id }
  }, (value) => `project ${value.projectId} and issue ${value.issueId} created`)
  if (project) state.project = project

  const knowledge = await recordStep({
    stepId: 'baseline-knowledge', roundId, phase, category: 'scenario', module: 'knowledge', persona: 'pilot-member-02',
    target: '/api/knowledge-bases + /items', failureClass: 'product', severity: 'P1',
  }, async () => {
    const session = sessions.get('pilot-member-02')!
    const created = await responseJson<{ space: { id: string; rootItemId: string } }>(await request.post(`${apiBaseUrl}/knowledge-bases`, {
      headers: bearer(session),
      data: { name: `${dataPrefix} knowledge`, code: `m10-${token}`.slice(0, 64), description: 'M10 synthetic continuous run', visibility: 'private' },
    }), 'create knowledge base')
    const item = await responseJson<{ item: { id: string } }>(await request.post(`${apiBaseUrl}/knowledge-bases/${created.space.id}/items`, {
      headers: bearer(session),
      data: { parentId: created.space.rootItemId, title: `${dataPrefix} note`, contentType: 'markdown', content: `# ${dataPrefix}\n\nBaseline structured knowledge scenario.` },
    }), 'create knowledge item')
    return { spaceId: created.space.id, itemId: item.item.id }
  }, (value) => `knowledge space ${value.spaceId} and item ${value.itemId} created`)
  if (knowledge) state.knowledge = knowledge

  const base = await recordStep({
    stepId: 'baseline-base', roundId, phase, category: 'scenario', module: 'base', persona: 'pilot-member-02',
    target: '/api/bases + /tables + /fields + /records', failureClass: 'product', severity: 'P1',
  }, async () => {
    const session = sessions.get('pilot-member-02')!
    const created = await responseJson<{ base: { id: string } }>(await request.post(`${apiBaseUrl}/bases`, {
      headers: bearer(session), data: { name: `${dataPrefix} base`, description: 'M10 synthetic continuous run' },
    }), 'create base')
    const table = await responseJson<{ table: { id: string } }>(await request.post(`${apiBaseUrl}/bases/${created.base.id}/tables`, {
      headers: bearer(session), data: { name: `${dataPrefix} observations` },
    }), 'create base table')
    const fields = await responseJson<{ fields: Array<{ id: string }> }>(await request.post(`${apiBaseUrl}/bases/${created.base.id}/tables/${table.table.id}/fields`, {
      headers: bearer(session), data: { name: 'Title', fieldType: 'text', config: {}, required: true },
    }), 'create base field')
    const field = fields.fields.at(-1)
    if (!field) throw new Error('created Base field is missing')
    const record = await responseJson<{ id: string }>(await request.post(`${apiBaseUrl}/bases/${created.base.id}/tables/${table.table.id}/records`, {
      headers: bearer(session), data: { values: { [field.id]: `${dataPrefix} baseline record` } },
    }), 'create base record')
    return { baseId: created.base.id, tableId: table.table.id, recordId: record.id }
  }, (value) => `base ${value.baseId}, table ${value.tableId}, and record ${value.recordId} created`)
  if (base) state.base = base

  const approval = await recordStep({
    stepId: 'baseline-approval', roundId, phase, category: 'scenario', module: 'approval', persona: 'pilot-member-01',
    target: '/api/approvals/instances', failureClass: 'product', severity: 'P1',
  }, async () => {
    const session = sessions.get('pilot-member-01')!
    const forms = await responseJson<Array<{ id: string; formKey: string }>>(await request.get(`${apiBaseUrl}/approvals/forms`, { headers: bearer(session) }), 'list approval forms')
    const form = forms.find((candidate) => candidate.formKey === 'leave')
    if (!form) throw new Error('leave approval form is missing')
    const created = await responseJson<{ instance: { id: string; status: string } }>(await request.post(`${apiBaseUrl}/approvals/instances`, {
      headers: bearer(session),
      data: { formId: form.id, title: `${dataPrefix} approval`, payload: { leaveType: 'synthetic', startAt: '2026-07-20T09:00:00', endAt: '2026-07-20T18:00:00', reason: 'M10 synthetic continuous run' } },
    }), 'create approval instance')
    if (created.instance.status !== 'pending') throw new Error(`approval status is ${created.instance.status}, expected pending`)
    return { instanceId: created.instance.id }
  }, (value) => `pending approval ${value.instanceId} created`)
  if (approval) state.approval = approval

  await recordStep({
    stepId: 'baseline-search', roundId, phase, category: 'scenario', module: 'search', persona: 'pilot-member-01/02/03',
    target: '/api/admin/search-governance/reindex + /api/search', failureClass: 'product', severity: 'P1',
  }, async () => {
    const ownerSession = sessions.get('pilot-owner')!
    const reindex = await request.post(`${apiBaseUrl}/admin/search-governance/reindex`, { headers: bearer(ownerSession) })
    if (!reindex.ok()) throw new Error(`reindex search returned HTTP ${reindex.status()}`)
    const objectTypes = new Set<string>()
    for (const username of ['pilot-member-01', 'pilot-member-02', 'pilot-member-03']) {
      const result = await responseJson<{ items: Array<{ objectType: string }> }>(await request.get(`${apiBaseUrl}/search`, {
        headers: bearer(sessions.get(username)!), params: { q: token, limit: '50' },
      }), `search M10 token as ${username}`)
      result.items.forEach((item) => objectTypes.add(item.objectType))
    }
    if (objectTypes.size < 6) throw new Error(`authorized searches returned ${objectTypes.size} object types, expected at least 6: ${[...objectTypes].join(',')}`)
    return [...objectTypes].sort()
  }, (types) => `${types.length} indexed object types found through their authorized personas: ${types.join(',')}`)

  const missing = ['im', 'project', 'knowledge', 'base', 'approval'].filter((key) => !(key in state))
  if (missing.length === 0) {
    const target = required(statePath, 'COLLA_E2E_M10_STATE_PATH')
    mkdirSync(dirname(target), { recursive: true })
    writeFileSync(target, `${JSON.stringify(state as RunState, null, 2)}\n`, 'utf8')
  }
}

function loadState() {
  return JSON.parse(readFileSync(required(statePath, 'COLLA_E2E_M10_STATE_PATH'), 'utf8')) as RunState
}

async function stableJson(request: APIRequestContext, session: E2eSession, url: string) {
  return await responseJson<unknown>(await request.get(url, { headers: bearer(session) }), url)
}

async function runRetry(request: APIRequestContext) {
  const sessions = await loginAll(request)
  if (sessions.size !== personas.length) return
  const state = loadState()
  const member03 = sessions.get('pilot-member-03')!
  const retryClientId = `m10-retry-${state.runId}`

  await recordStep({
    stepId: 'retry-im', roundId, phase, category: 'scenario', module: 'im', persona: 'pilot-member-03',
    target: '/api/conversations/{id}/messages duplicate clientMessageId', failureClass: 'product', severity: 'P0',
  }, async () => {
    const payload = { clientMessageId: retryClientId, messageType: 'text', content: `${state.dataPrefix} idempotent retry message` }
    const first = await responseJson<{ id: string }>(await request.post(`${apiBaseUrl}/conversations/${state.im.conversationId}/messages`, { headers: bearer(member03), data: payload }), 'first retry message')
    const second = await responseJson<{ id: string }>(await request.post(`${apiBaseUrl}/conversations/${state.im.conversationId}/messages`, { headers: bearer(member03), data: payload }), 'duplicate retry message')
    const listed = await responseJson<{ items: Array<{ id: string; clientMessageId: string }> }>(await request.get(`${apiBaseUrl}/conversations/${state.im.conversationId}/messages`, { headers: bearer(member03) }), 'list retry messages')
    const matches = listed.items.filter((item) => item.clientMessageId === retryClientId)
    if (first.id !== second.id || matches.length !== 1) throw new Error(`idempotency violation: first=${first.id}, second=${second.id}, matches=${matches.length}`)
    return first.id
  }, (id) => `duplicate clientMessageId resolved to one message ${id}`)

  const retryReads: Array<{ module: ModuleName; persona: string; url: string; marker: string }> = [
    { module: 'project', persona: 'pilot-member-01', url: `${apiBaseUrl}/projects`, marker: state.project.projectId },
    { module: 'knowledge', persona: 'pilot-member-02', url: `${apiBaseUrl}/knowledge-bases/${state.knowledge.spaceId}/items/${state.knowledge.itemId}`, marker: state.knowledge.itemId },
    { module: 'base', persona: 'pilot-member-02', url: `${apiBaseUrl}/base-records/${state.base.recordId}`, marker: state.base.recordId },
    { module: 'approval', persona: 'pilot-member-01', url: `${apiBaseUrl}/approvals/instances/${state.approval.instanceId}`, marker: state.approval.instanceId },
    { module: 'search', persona: 'pilot-member-01', url: `${apiBaseUrl}/search?q=${encodeURIComponent(state.search.token)}&limit=50`, marker: state.search.token },
  ]
  for (const item of retryReads) {
    await recordStep({
      stepId: `retry-${item.module}`, roundId, phase, category: 'scenario', module: item.module, persona: item.persona,
      target: item.url.replace(apiBaseUrl, '/api'), failureClass: 'product', severity: 'P1',
    }, async () => {
      const session = sessions.get(item.persona)!
      const first = JSON.stringify(await stableJson(request, session, item.url))
      const second = JSON.stringify(await stableJson(request, session, item.url))
      if (first !== second) throw new Error(`repeat read changed state for ${item.module}`)
      if (!first.toLowerCase().includes(item.marker.toLowerCase())) throw new Error(`repeat read lost marker ${item.marker}`)
      return first.length
    }, (length) => `two consecutive reads were identical (${length} serialized bytes)`)
  }
}

async function runFault(request: APIRequestContext) {
  const sessions = await loginAll(request)
  const state = loadState()
  const member03 = sessions.get('pilot-member-03')
  if (!member03) return

  await recordStep({
    stepId: 'fault-invalid-session', roundId, phase, category: 'fault', persona: 'pilot-member-03',
    target: '/api/auth/me with invalid token', failureClass: 'expected-fault', severity: 'P0',
  }, async () => {
    const response = await request.get(`${apiBaseUrl}/auth/me`, { headers: { Authorization: 'Bearer expired.synthetic.token' } })
    if (![401, 403].includes(response.status())) throw new Error(`invalid token returned HTTP ${response.status()}, expected 401/403`)
    return response.status()
  }, (status) => `expected HTTP ${status} observed`)

  await recordStep({
    stepId: 'fault-admin-boundary', roundId, phase, category: 'fault', persona: 'pilot-member-03',
    target: '/api/admin/users as ordinary member', failureClass: 'expected-fault', severity: 'P0',
  }, async () => {
    const response = await request.get(`${apiBaseUrl}/admin/users`, { headers: bearer(member03) })
    if (response.status() !== 403) throw new Error(`ordinary member admin request returned HTTP ${response.status()}, expected 403`)
    return response.status()
  }, (status) => `expected HTTP ${status} observed`)

  await recordStep({
    stepId: 'fault-private-knowledge', roundId, phase, category: 'fault', persona: 'pilot-member-03',
    target: '/api/knowledge-bases/{private-space}/items/{id}', failureClass: 'expected-fault', severity: 'P0',
  }, async () => {
    const response = await request.get(`${apiBaseUrl}/knowledge-bases/${state.knowledge.spaceId}/items/${state.knowledge.itemId}`, { headers: bearer(member03) })
    if (![403, 404].includes(response.status())) throw new Error(`restricted knowledge request returned HTTP ${response.status()}, expected 403/404`)
    return response.status()
  }, (status) => `restricted object stayed hidden with HTTP ${status}`)
}

async function runRecovery(request: APIRequestContext) {
  const sessions = await loginAll(request)
  if (sessions.size !== personas.length) return
  const state = loadState()
  const checks: Array<{ module: ModuleName; persona: string; url: string; marker: string }> = [
    { module: 'im', persona: 'pilot-member-03', url: `${apiBaseUrl}/conversations/${state.im.conversationId}/messages`, marker: state.dataPrefix },
    { module: 'project', persona: 'pilot-member-01', url: `${apiBaseUrl}/projects`, marker: state.project.projectId },
    { module: 'knowledge', persona: 'pilot-member-02', url: `${apiBaseUrl}/knowledge-bases/${state.knowledge.spaceId}/items/${state.knowledge.itemId}`, marker: state.knowledge.itemId },
    { module: 'base', persona: 'pilot-member-02', url: `${apiBaseUrl}/base-records/${state.base.recordId}`, marker: state.base.recordId },
    { module: 'approval', persona: 'pilot-member-01', url: `${apiBaseUrl}/approvals/instances/${state.approval.instanceId}`, marker: state.approval.instanceId },
    { module: 'search', persona: 'pilot-member-01', url: `${apiBaseUrl}/search?q=${encodeURIComponent(state.search.token)}&limit=50`, marker: state.search.token },
  ]
  for (const item of checks) {
    await recordStep({
      stepId: `recovery-${item.module}`, roundId, phase, category: 'scenario', module: item.module, persona: item.persona,
      target: item.url.replace(apiBaseUrl, '/api'), failureClass: 'product', severity: 'P0',
    }, async () => {
      const payload = JSON.stringify(await stableJson(request, sessions.get(item.persona)!, item.url))
      if (!payload.toLowerCase().includes(item.marker.toLowerCase())) throw new Error(`recovery read lost marker ${item.marker}`)
      return payload.length
    }, (length) => `post-restart object verified (${length} serialized bytes)`)
  }
}

function writeEvidence() {
  const target = required(evidencePath, 'COLLA_E2E_M10_EVIDENCE_PATH')
  mkdirSync(dirname(target), { recursive: true })
  const failed = steps.filter((step) => step.status === 'failed')
  writeFileSync(target, `${JSON.stringify({
    schemaVersion: 1,
    evidenceKind: 'synthetic-engineering',
    runId,
    roundId,
    phase,
    startedAt: steps.at(0)?.startedAt ?? new Date().toISOString(),
    finishedAt: steps.at(-1)?.finishedAt ?? new Date().toISOString(),
    decision: failed.length === 0 ? 'PASS' : 'FAIL',
    steps,
  }, null, 2)}\n`, 'utf8')
  return failed
}

test(`@pilot-m10 ${phase} synthetic continuous-run phase`, async ({ request, browser }) => {
  required(evidencePath, 'COLLA_E2E_M10_EVIDENCE_PATH')
  required(statePath, 'COLLA_E2E_M10_STATE_PATH')
  try {
    if (phase === 'baseline') await runBaseline(request, browser)
    else if (phase === 'retry') await runRetry(request)
    else if (phase === 'fault') await runFault(request)
    else if (phase === 'recovery') await runRecovery(request)
    else throw new Error(`Unsupported M10 phase: ${phase as string}`)
  } finally {
    const failed = writeEvidence()
    expect(failed, failed.map((step) => `${step.stepId}: ${step.detail}`).join('\n')).toHaveLength(0)
  }
})
