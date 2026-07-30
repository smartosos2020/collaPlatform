import {
  expect,
  test,
  type APIRequestContext,
  type Browser,
  type Page,
} from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = {
  id: string
  username: string
  displayName: string
  password: string
}

type WorkItem = {
  id: string
  title: string
  version: number
  fieldValues: Record<string, unknown>
}

type Workflow = {
  capability: string
  currentStateKey?: string
  currentStateLabel?: string
  availableActions: Array<{ actionKey: string; requiredFieldKeys: string[] }>
}

test.describe('PROJECT-PLATFORM-S08 route final', () => {
  test('state configuration, six identities, recovery and lifecycle remain coherent @route-final', async ({
    page,
    request,
    browser,
  }, testInfo) => {
    test.setTimeout(420_000)
    page.setDefaultTimeout(20_000)
    requireIsolatedIdentityFixture()
    const enterpriseAdmin = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(
      request,
      `${apiBaseUrl}/auth/me`,
      enterpriseAdmin,
    )
    const suffix = `s08m4_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_owner`, 'S08 Owner', 'member')
    const adminIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_admin`, 'S08 Space Admin', 'member')
    const memberIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_member`, 'S08 Member', 'member')
    const guestIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_guest`, 'S08 Guest', 'member')
    const outsiderIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_outsider`, 'S08 Outsider', 'member')
    const owner = await loginByApi(request, ownerIdentity.username, ownerIdentity.password)
    const spaceAdmin = await loginByApi(request, adminIdentity.username, adminIdentity.password)
    const member = await loginByApi(request, memberIdentity.username, memberIdentity.password)
    const guest = await loginByApi(request, guestIdentity.username, guestIdentity.password)
    const outsider = await loginByApi(request, outsiderIdentity.username, outsiderIdentity.password)
    let spaceId: string | undefined

    try {
      spaceId = await createSpace(request, owner, suffix)
      await addMember(request, owner, spaceId, adminIdentity.id, 'admin')
      await addMember(request, owner, spaceId, memberIdentity.id, 'member')
      await addMember(request, owner, spaceId, guestIdentity.id, 'guest')
      const fixture = await createWorkflowFixture(request, owner, spaceId, suffix)

      await installSession(page, owner)
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto(`/project-spaces/${spaceId}/types/${fixture.typeId}`)
      const editor = page.getByTestId('work-item-state-flow-editor')
      await expect(editor).toBeVisible()
      await expect(editor).toContainText('轻量状态流配置')
      await editor.getByRole('tab', { name: /状态/ }).click()
      await editor.locator('.ant-collapse-header').first().click()
      const longStateLabel = `待处理-${'长状态名称'.repeat(18)}`
      await editor.getByLabel('展示名').first().fill(longStateLabel)
      const saveDraftResponse = page.waitForResponse((response) =>
        response.url().endsWith(`/configuration/types/${fixture.typeId}/draft`)
          && response.request().method() === 'PUT')
      await editor.getByRole('button', { name: '保存到草稿' }).click()
      expect((await saveDraftResponse).ok()).toBeTruthy()
      await expect(page.getByText('状态流已保存到配置草稿')).toBeVisible()
      await page.getByTestId('project-space-types-secondary-tabs')
        .getByRole('tab', { name: '配置发布', exact: true }).click()
      await page.getByRole('button', { name: '校验配置' }).click()
      await expect(page.getByRole('region', { name: '配置草稿状态' })).toContainText('校验通过')
      await expect(page.getByRole('region', { name: '配置草稿状态' })).toContainText(/兼容性/)

      await page.getByTestId('project-space-types-secondary-tabs')
        .getByRole('tab', { name: '类型目录', exact: true }).click()
      const backfillPanel = page.getByTestId('work-item-state-backfill-panel')
      await expect(backfillPanel).toBeVisible()
      await backfillPanel.getByText('显式 manifest').locator('..').getByRole('combobox').click()
      await page.getByText(`${fixture.oldItem.displayKey} · ${fixture.oldItem.title}`, { exact: true }).click()
      await backfillPanel.getByLabel('操作原因（10–500 个字符）').fill('S08 M4 真实浏览器存量初始化验收')
      const backfillResponse = page.waitForResponse((response) =>
        response.url().endsWith(`/project-spaces/${spaceId}/workflow-backfills`)
          && response.request().method() === 'POST')
      await backfillPanel.getByRole('button', { name: '确认初始化' }).click()
      expect((await backfillResponse).ok()).toBeTruthy()
      await expect(backfillPanel).toContainText('完成 1/1')
      const verifyResponse = page.waitForResponse((response) =>
        response.url().includes('/workflow-backfills/')
          && response.url().endsWith(':verify')
          && response.request().method() === 'GET')
      await backfillPanel.getByRole('button', { name: '验证批次' }).click()
      expect((await verifyResponse).ok()).toBeTruthy()
      await expect(backfillPanel).toContainText('已核对 1 项')

      await installSession(page, member)
      await page.goto(`/project-spaces/${spaceId}/work-items/${fixture.item.id}`)
      await page.getByTestId('project-work-item-detail-secondary-tabs')
        .getByRole('tab', { name: '状态流', exact: true }).click()
      const workflowPanel = page.getByTestId('work-item-workflow-panel')
      await expect(workflowPanel.locator('.work-item-workflow-state-tag')).toHaveText('待处理')
      await expect(workflowPanel.getByRole('button', { name: /开始处理/ })).toBeVisible()
      await workflowPanel.getByRole('button', { name: /开始处理/ }).click()
      await expect(workflowPanel.locator('.work-item-workflow-state-tag')).toHaveText('处理中')
      await expect(workflowPanel.getByRole('button', { name: /完成/ })).toContainText('需 处理结论')
      await page.getByLabel('处理结论').fill('verified in isolated browser')
      await workflowPanel.getByRole('button', { name: /完成/ }).click()
      await expect(workflowPanel.locator('.work-item-workflow-state-tag')).toHaveText('已完成')
      await workflowPanel.getByRole('button', { name: /重新打开/ }).click()
      await expect(workflowPanel.locator('.work-item-workflow-state-tag')).toHaveText('待处理')
      await workflowPanel.getByRole('button', { name: /终\s*止/ }).click()
      await page.getByRole('dialog').getByRole('button', { name: '确认终止' }).click()
      await expect(workflowPanel.locator('.work-item-workflow-state-tag')).toHaveText('已取消')
      await workflowPanel.getByRole('button', { name: /恢\s*复/ }).click()
      await expect(workflowPanel.locator('.work-item-workflow-state-tag')).toHaveText('待处理')

      await installSession(page, spaceAdmin)
      await page.goto(`/project-spaces/${spaceId}/work-items/${fixture.item.id}`)
      await page.getByTestId('project-work-item-detail-secondary-tabs')
        .getByRole('tab', { name: '状态流', exact: true }).click()
      await page.getByTestId('work-item-workflow-panel').getByRole('button', { name: '受控纠错' }).click()
      const correctionDialog = page.getByRole('dialog', { name: '受控状态纠错' })
      await correctionDialog.getByLabel('目标状态永久 key').fill('in_progress')
      await correctionDialog.getByLabel('纠错原因（至少 10 个字符）').fill('S08 M4 isolated correction evidence')
      const correctionResponse = page.waitForResponse((response) =>
        response.url().endsWith('/workflow/corrections')
          && response.request().method() === 'POST')
      await correctionDialog.getByRole('button', { name: '确认纠错' }).click()
      expect((await correctionResponse).ok()).toBeTruthy()
      await expect(page.getByTestId('work-item-workflow-panel').locator('.work-item-workflow-state-tag')).toHaveText('处理中')

      await assertIdentityBoundaries(
        page,
        request,
        { spaceId, workItemId: fixture.item.id },
        { owner, spaceAdmin, member, guest, outsider, enterpriseAdmin },
      )

      await assertConcurrentAction(browser, spaceId, fixture.item.id, member, spaceAdmin)

      await installSession(page, member)
      await page.goto(`/project-spaces/${spaceId}/work-items/${fixture.item.id}`)
      await page.getByTestId('project-work-item-detail-secondary-tabs')
        .getByRole('tab', { name: '状态流', exact: true }).click()
      await expect(page.getByTestId('work-item-workflow-panel').locator('.work-item-workflow-state-tag')).toHaveText('已完成')
      await page.getByTestId('work-item-workflow-panel').getByRole('button', { name: /重新打开/ }).click()
      await expect(page.getByTestId('work-item-workflow-panel').locator('.work-item-workflow-state-tag')).toHaveText('待处理')
      await page.context().setOffline(true)
      await page.evaluate(() => {
        Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => false })
        window.dispatchEvent(new Event('offline'))
      })
      await expect(page.getByTestId('work-item-workflow-panel')).toContainText('字段输入和待重试动作会保留')
      await expect(page.getByTestId('work-item-workflow-panel').getByRole('button', { name: /开始处理/ })).toBeDisabled()
      await page.context().setOffline(false)
      await page.evaluate(() => {
        Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => true })
        window.dispatchEvent(new Event('online'))
      })
      const keyboardAction = page.getByTestId('work-item-workflow-panel').getByRole('button', { name: /开始处理/ })
      await keyboardAction.focus()
      await keyboardAction.press('Enter')
      await expect(page.getByTestId('work-item-workflow-panel').locator('.work-item-workflow-state-tag')).toHaveText('处理中')

      for (const width of [1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        await page.goto(`/project-spaces/${spaceId}/work-items/${fixture.item.id}`)
        await page.getByTestId('project-work-item-detail-secondary-tabs')
          .getByRole('tab', { name: '状态流', exact: true }).click()
        await expect(page.getByTestId('work-item-workflow-panel')).toBeVisible()
        expect(await page.evaluate(() =>
          document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1)
      }
      await installSession(page, owner)
      await page.setViewportSize({ width: 820, height: 900 })
      await page.goto(`/project-spaces/${spaceId}/types/${fixture.typeId}`)
      const narrowEditor = page.getByTestId('work-item-state-flow-editor')
      await narrowEditor.getByRole('tab', { name: /状态/ }).click()
      await narrowEditor.locator('.ant-collapse-header').first().click()
      await expect(narrowEditor.getByLabel('展示名').first()).toHaveValue(longStateLabel)
      expect(await page.evaluate(() =>
        document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1)
      await page.screenshot({
        path: testInfo.outputPath('s08-state-flow-820.png'),
        fullPage: true,
      })
    } finally {
      await page.context().setOffline(false).catch(() => undefined)
      if (spaceId) {
        await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`, {
          headers: bearer(owner),
        }).catch(() => undefined)
      }
      for (const identity of [
        adminIdentity,
        memberIdentity,
        guestIdentity,
        outsiderIdentity,
        ownerIdentity,
      ]) {
        await request.post(`${apiBaseUrl}/admin/users/${identity.id}/offboard`, {
          headers: bearer(enterpriseAdmin),
          data: { handoverToUserId: enterpriseProfile.id },
        }).catch(() => undefined)
      }
    }
  })
})

async function assertIdentityBoundaries(
  page: Page,
  request: APIRequestContext,
  target: { spaceId: string; workItemId: string },
  sessions: {
    owner: E2eSession
    spaceAdmin: E2eSession
    member: E2eSession
    guest: E2eSession
    outsider: E2eSession
    enterpriseAdmin: E2eSession
  },
) {
  const workflowUrl = `${apiBaseUrl}/project-spaces/${target.spaceId}/work-items/${target.workItemId}/workflow`
  for (const session of [sessions.owner, sessions.spaceAdmin, sessions.member]) {
    const workflow = await getJson<Workflow>(request, workflowUrl, session)
    expect(workflow.capability).toBe('available')
    expect(workflow.availableActions.length).toBeGreaterThan(0)
  }
  const guestWorkflow = await getJson<Workflow>(request, workflowUrl, sessions.guest)
  expect(guestWorkflow.capability).toBe('available')
  expect(guestWorkflow.availableActions).toEqual([])
  await installSession(page, sessions.guest)
  await page.goto(`/project-spaces/${target.spaceId}/work-items/${target.workItemId}`)
  await page.getByTestId('project-work-item-detail-secondary-tabs')
    .getByRole('tab', { name: '状态流', exact: true }).click()
  await expect(page.getByTestId('work-item-workflow-panel')).toBeVisible()
  await expect(page.getByTestId('work-item-workflow-panel').getByRole('button', { name: '受控纠错' })).toHaveCount(0)
  await expect(page.getByTestId('work-item-workflow-panel')).toContainText('当前没有服务端允许的动作')

  for (const session of [sessions.outsider, sessions.enterpriseAdmin]) {
    const response = await request.get(workflowUrl, { headers: bearer(session) })
    expect(response.status()).toBe(404)
    expect(await response.text()).not.toContain('currentStateKey')
    expect(await response.text()).not.toContain('availableActions')
  }
}

async function assertConcurrentAction(
  browser: Browser,
  spaceId: string,
  workItemId: string,
  member: E2eSession,
  spaceAdmin: E2eSession,
) {
  const memberContext = await browser.newContext()
  const adminContext = await browser.newContext()
  const memberPage = await memberContext.newPage()
  const adminPage = await adminContext.newPage()
  try {
    await installSession(memberPage, member)
    await installSession(adminPage, spaceAdmin)
    await Promise.all([
      memberPage.goto(`/project-spaces/${spaceId}/work-items/${workItemId}`),
      adminPage.goto(`/project-spaces/${spaceId}/work-items/${workItemId}`),
    ])
    await Promise.all([
      memberPage.getByTestId('project-work-item-detail-secondary-tabs')
        .getByRole('tab', { name: '状态流', exact: true }).click(),
      adminPage.getByTestId('project-work-item-detail-secondary-tabs')
        .getByRole('tab', { name: '状态流', exact: true }).click(),
    ])
    const memberResponse = memberPage.waitForResponse((response) =>
      response.url().endsWith('/workflow/actions/complete'))
    const adminResponse = adminPage.waitForResponse((response) =>
      response.url().endsWith('/workflow/actions/complete'))
    await Promise.all([
      memberPage.getByTestId('work-item-workflow-panel').getByRole('button', { name: /完成/ }).click(),
      adminPage.getByTestId('work-item-workflow-panel').getByRole('button', { name: /完成/ }).click(),
    ])
    const statuses = [(await memberResponse).status(), (await adminResponse).status()].sort()
    expect(statuses).toEqual([200, 409])
    await expect(memberPage.getByTestId('work-item-workflow-panel')).toContainText(/已完成|动作请求已保留/)
    await expect(adminPage.getByTestId('work-item-workflow-panel')).toContainText(/已完成|动作请求已保留/)
  } finally {
    await memberContext.close()
    await adminContext.close()
  }
}

async function createWorkflowFixture(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  suffix: string,
) {
  const type = await postJson<{ id: string }>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`,
    owner,
    { typeKey: `${suffix}_task`, name: 'S08 状态事项', sortOrder: 10 },
    `s08-type-${suffix}`,
  )
  const resolution = await createField(
    request, owner, spaceId, type.id, 'resolution', '处理结论', 10,
  )
  await putLayout(request, owner, spaceId, type.id, 'create', resolution)
  await putLayout(request, owner, spaceId, type.id, 'detail', resolution)
  await validateAndPublish(request, owner, spaceId, type.id, suffix, false)
  const oldItem = await createItem(
    request, owner, spaceId, type.id, 'S08 存量无状态实例', {},
    `s08-old-item-${suffix}`,
  )

  const draftUrl = `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${type.id}/draft`
  const draft = await getJson<{ aggregateVersion: number; snapshot: Record<string, unknown> }>(
    request, draftUrl, owner,
  )
  const snapshot = structuredClone(draft.snapshot)
  snapshot.stateFlow = standardStateFlow()
  const save = await request.put(draftUrl, {
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s08-flow-save-${suffix}` },
    data: { snapshot, expectedAggregateVersion: draft.aggregateVersion },
  })
  expect(save.ok(), await save.text()).toBeTruthy()
  const published = await validateAndPublish(request, owner, spaceId, type.id, suffix, true)
  const item = await createItem(
    request, owner, spaceId, type.id, 'S08 新状态实例', {},
    `s08-new-item-${suffix}`,
  )
  return {
    typeId: type.id,
    currentVersionId: published.version.id,
    oldItem: { ...oldItem, displayKey: oldItem.displayKey },
    item,
  }
}

function standardStateFlow() {
  const roles = ['owner', 'admin', 'member']
  return {
    states: [
      state('open', '待处理', 'initial', 100),
      state('in_progress', '处理中', 'active', 200),
      state('done', '已完成', 'terminal', 300),
      state('canceled', '已取消', 'canceled', 400),
    ],
    actions: [
      action('start_progress', '开始处理', 'forward', roles, [], 100),
      action('complete', '完成', 'forward', roles, ['resolution'], 200),
      action('reopen', '重新打开', 'reopen', roles, [], 300),
      action('terminate', '终止', 'terminate', roles, [], 400),
      action('restore', '恢复', 'restore', roles, [], 500),
    ],
    transitions: [
      transition('open_start', 'start_progress', 'open', 'in_progress', 100),
      transition('progress_complete', 'complete', 'in_progress', 'done', 200),
      transition('done_reopen', 'reopen', 'done', 'open', 300),
      transition('open_terminate', 'terminate', 'open', 'canceled', 400),
      transition('progress_terminate', 'terminate', 'in_progress', 'canceled', 500),
      transition('canceled_restore', 'restore', 'canceled', 'open', 600),
    ],
    guards: [],
  }
}

function state(stateKey: string, label: string, category: string, sortOrder: number) {
  return { stateKey, label, description: '', color: '', category, sortOrder }
}

function action(
  actionKey: string,
  label: string,
  kind: string,
  authorizedRoles: string[],
  requiredFieldKeys: string[],
  sortOrder: number,
) {
  return {
    actionKey,
    label,
    description: '',
    kind,
    authorizedRoles,
    requiredFieldKeys,
    fieldPatch: {},
    sideEffectKeys: [],
    sortOrder,
  }
}

function transition(
  transitionKey: string,
  actionKey: string,
  fromStateKey: string,
  toStateKey: string,
  sortOrder: number,
) {
  return { transitionKey, actionKey, fromStateKey, toStateKey, guardKey: null, sortOrder }
}

async function validateAndPublish(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  typeId: string,
  suffix: string,
  breakingConfirmed: boolean,
) {
  const base = `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}`
  let draft = await getJson<{ aggregateVersion: number }>(request, `${base}/draft`, owner)
  draft = await postJson(
    request,
    `${base}/draft:validate`,
    owner,
    { expectedAggregateVersion: draft.aggregateVersion },
    `s08-validate-${typeId}-${draft.aggregateVersion}-${suffix}`,
  )
  return postJson<{ version: { id: string } }>(
    request,
    `${base}/draft:publish`,
    owner,
    { expectedDraftAggregateVersion: draft.aggregateVersion, breakingConfirmed },
    `s08-publish-${typeId}-${draft.aggregateVersion}-${suffix}`,
  )
}

async function createItem(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  typeId: string,
  title: string,
  fieldValues: Record<string, unknown>,
  requestId: string,
) {
  return postJson<WorkItem & { displayKey: string }>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/work-items`,
    owner,
    { typeId, title, fieldValues },
    requestId,
  )
}

async function putLayout(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  typeId: string,
  layoutKind: 'create' | 'detail',
  field: { id: string; fieldKey: string },
) {
  const sectionId = crypto.randomUUID()
  const response = await request.put(
    `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}/layouts/${layoutKind}`,
    {
      headers: {
        ...bearer(owner),
        'X-Colla-Request-Id': `s08-layout-${layoutKind}-${typeId}`,
      },
      data: {
        nodes: [
          {
            id: sectionId,
            parentId: null,
            nodeKey: 'main',
            nodeType: 'section',
            fieldId: null,
            fieldKey: null,
            sortOrder: 0,
            config: { title: '状态事项信息' },
            visibilityCondition: { schemaVersion: 1 },
          },
          {
            id: crypto.randomUUID(),
            parentId: sectionId,
            nodeKey: `field_${field.fieldKey}`,
            nodeType: 'field',
            fieldId: field.id,
            fieldKey: field.fieldKey,
            sortOrder: 10,
            config: {},
            visibilityCondition: { schemaVersion: 1 },
          },
        ],
        policies: [{
          id: crypto.randomUUID(),
          fieldId: field.id,
          fieldKey: field.fieldKey,
          policyKey: 'resolution_access',
          policy: {
            schemaVersion: 1,
            default: { mode: 'write', required: false },
            rules: [{ ruleKey: 'guest_read', roles: ['guest'], mode: 'read', required: false }],
          },
        }],
        aggregateVersion: 0,
      },
    },
  )
  expect(response.ok(), `save ${layoutKind} layout failed: ${await response.text()}`).toBeTruthy()
}

async function createField(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  typeId: string,
  fieldKey: string,
  name: string,
  sortOrder: number,
) {
  return postJson<{ id: string; fieldKey: string }>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}/fields`,
    owner,
    { fieldKey, name, fieldType: 'text', sortOrder, config: {} },
    `s08-field-${fieldKey}-${typeId}`,
  )
}

async function createSpace(request: APIRequestContext, owner: E2eSession, suffix: string) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(owner),
    data: {
      spaceKey: `s08-m4-${suffix.replaceAll('_', '-')}`,
      name: `S08 状态流验收 ${suffix}`,
      visibility: 'private',
    },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json() as { id: string }).id
}

async function addMember(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  userId: string,
  roleKey: string,
) {
  const response = await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/members`, {
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s08-member-${userId}` },
    data: { userId, roleKey },
  })
  expect(response.ok()).toBeTruthy()
}

async function createIdentity(
  request: APIRequestContext,
  administrator: E2eSession,
  username: string,
  displayName: string,
  roleCode: 'member' | 'admin',
) {
  const password = 'member123456'
  const response = await request.post(`${apiBaseUrl}/admin/users`, {
    headers: bearer(administrator),
    data: { username, password, displayName, email: `${username}@example.com`, roleCode },
  })
  expect(response.ok()).toBeTruthy()
  return { ...(await response.json() as Omit<Identity, 'password'>), password }
}

async function getJson<T>(
  request: APIRequestContext,
  url: string,
  session: E2eSession,
) {
  const response = await request.get(url, { headers: bearer(session) })
  expect(response.ok(), `GET ${url} failed: ${await response.text()}`).toBeTruthy()
  return await response.json() as T
}

async function postJson<T>(
  request: APIRequestContext,
  url: string,
  session: E2eSession,
  data: unknown,
  requestId: string,
) {
  const response = await request.post(url, {
    headers: { ...bearer(session), 'X-Colla-Request-Id': requestId },
    data,
  })
  expect(response.ok(), `POST ${url} failed: ${await response.text()}`).toBeTruthy()
  return await response.json() as T
}
