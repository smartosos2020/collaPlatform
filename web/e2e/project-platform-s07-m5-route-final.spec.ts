import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

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
  runtime: {
    typeVersionId: string
    configHash: string
    snapshot: unknown
    accessProjection: Record<string, { mode: string }>
  }
  availableActions: string[]
}

test.describe('PROJECT-PLATFORM-S07 route final', () => {
  test('canonical work items, identity boundaries and legacy cutover remain coherent @route-final', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(420_000)
    page.setDefaultTimeout(20_000)
    requireIsolatedIdentityFixture()
    const enterpriseAdmin = await loginByApi(request)
    const enterpriseAdminProfile = await getJson<{ id: string }>(
      request,
      `${apiBaseUrl}/auth/me`,
      enterpriseAdmin,
    )
    const suffix = `s07m5_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_owner`, 'S07 Owner')
    const adminIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_admin`, 'S07 Space Admin')
    const memberIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_member`, 'S07 Member')
    const guestIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_guest`, 'S07 Guest')
    const outsiderIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_outsider`, 'S07 Outsider')
    const owner = await loginByApi(request, ownerIdentity.username, ownerIdentity.password)
    const spaceAdmin = await loginByApi(request, adminIdentity.username, adminIdentity.password)
    const member = await loginByApi(request, memberIdentity.username, memberIdentity.password)
    const guest = await loginByApi(request, guestIdentity.username, guestIdentity.password)
    const outsider = await loginByApi(request, outsiderIdentity.username, outsiderIdentity.password)
    let spaceId: string | undefined
    let workItemId: string | undefined
    let legacySpaceBatchId: string | undefined
    let legacyWorkItemBatchId: string | undefined

    try {
      spaceId = await createSpace(request, owner, suffix)
      await addMember(request, owner, spaceId, adminIdentity.id, 'admin')
      await addMember(request, owner, spaceId, memberIdentity.id, 'member')
      await addMember(request, owner, spaceId, guestIdentity.id, 'guest')
      const typeId = await createPublishedType(request, owner, spaceId, suffix)

      await installSession(page, owner)
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto(`/project-spaces/${spaceId}/work-items?typeId=${typeId}&create=1`)
      const createDialog = page.getByRole('dialog')
      await expect(createDialog).toContainText('新建交付事项')
      await createDialog.getByLabel('标题', { exact: true }).fill(`S07 长标题 ${'验收'.repeat(80)}`)
      await createDialog.getByLabel('验收说明').fill('绑定版本创建内容')
      await createDialog.getByLabel('内部备注').fill('guest-must-not-disclose')
      const createResponse = page.waitForResponse((response) =>
        response.url().endsWith(`/project-spaces/${spaceId}/work-items`)
          && response.request().method() === 'POST')
      await createDialog.getByRole('button', { name: /创\s*建/ }).click()
      const createdResponse = await createResponse
      expect(createdResponse.ok()).toBeTruthy()
      const created = await createdResponse.json() as WorkItem
      workItemId = created.id
      await expect(page).toHaveURL(new RegExp(`/project-spaces/${spaceId}/work-items/${workItemId}$`))
      await expect(page.getByTestId('project-work-item-detail')).toBeVisible()
      await expect(page.getByLabel('验收说明')).toHaveValue('绑定版本创建内容')

      await page.getByLabel('验收说明').fill('保存后的动态字段内容')
      await page.getByRole('button', { name: /保\s*存/ }).click()
      await expect(page.getByText('工作项已保存')).toBeVisible()

      const comment = `真实评论 ${'long-content-'.repeat(30)}`
      const commentBox = page.getByPlaceholder('输入评论，Ctrl/⌘ + Enter 发布')
      await commentBox.fill(comment)
      await commentBox.press(process.platform === 'darwin' ? 'Meta+Enter' : 'Control+Enter')
      await expect(page.getByText('评论已发布')).toBeVisible()
      await expect(page.getByText(comment)).toBeVisible()

      await page.getByRole('tab', { name: '参与者' }).click()
      await page.getByLabel('添加参与者').click()
      await page.getByText(memberIdentity.displayName, { exact: true }).click()
      await expect(page.getByText('参与者已添加')).toBeVisible()
      await expect(page.getByRole('heading', { name: memberIdentity.displayName })).toBeVisible()

      await page.getByRole('tab', { name: '附件' }).click()
      const attachmentResponse = page.waitForResponse((response) =>
        response.url().endsWith(`/work-items/${workItemId}/attachments`)
          && response.request().method() === 'POST')
      await page.locator('input[type="file"]').setInputFiles({
        name: 's07-acceptance.txt',
        mimeType: 'text/plain',
        buffer: Buffer.from('S07 canonical attachment acceptance'),
      })
      expect((await attachmentResponse).ok()).toBeTruthy()
      await expect(page.getByText('附件已上传')).toBeVisible()
      await expect(page.getByText('s07-acceptance.txt')).toBeVisible()
      const attachmentList = await getJson<{ items: Array<{ fileId: string }> }>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-items/${workItemId}/attachments`,
        owner,
      )
      const download = await getJson<{ downloadUrl: string }>(
        request,
        `${apiBaseUrl}/files/${attachmentList.items[0].fileId}/download-url`,
        owner,
      )
      expect((await request.get(download.downloadUrl)).ok()).toBeTruthy()

      await page.getByRole('button', { name: /归\s*档/ }).click()
      await page.getByRole('dialog').getByRole('button', { name: /归\s*档/ }).click()
      await expect(page.getByText('工作项已归档')).toBeVisible()
      await page.getByRole('button', { name: /恢\s*复/ }).click()
      await expect(page.getByText('工作项已恢复')).toBeVisible()

      const titleInput = page.getByLabel('标题', { exact: true })
      await titleInput.fill('离线输入必须保留')
      await page.context().setOffline(true)
      await page.evaluate(() => {
        Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => false })
        window.dispatchEvent(new Event('offline'))
      })
      await expect(page.getByText('当前处于离线状态，已打开页面可继续查看，新的保存操作会失败。')).toBeVisible()
      await expect(page.getByRole('button', { name: /保\s*存/ })).toBeDisabled()
      await expect(titleInput).toHaveValue('离线输入必须保留')
      await page.context().setOffline(false)
      await page.evaluate(() => {
        Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => true })
        window.dispatchEvent(new Event('online'))
      })

      const stale = await getJson<WorkItem>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-items/${workItemId}`,
        owner,
      )
      const concurrent = await request.patch(
        `${apiBaseUrl}/project-spaces/${spaceId}/work-items/${workItemId}`,
        {
          headers: { ...bearer(owner), 'X-Colla-Request-Id': `s07-m5-concurrent-${suffix}` },
          data: {
            title: '并发更新后的服务端标题',
            fieldValues: stale.fieldValues,
            expectedVersion: stale.version,
          },
        },
      )
      expect(concurrent.ok()).toBeTruthy()
      await titleInput.fill('冲突时保留的本地标题')
      const staleWrite = await request.patch(
        `${apiBaseUrl}/project-spaces/${spaceId}/work-items/${workItemId}`,
        {
          headers: { ...bearer(owner), 'X-Colla-Request-Id': `s07-m5-stale-${suffix}` },
          data: {
            title: '不得覆盖并发事实',
            fieldValues: stale.fieldValues,
            expectedVersion: stale.version,
          },
        },
      )
      expect(staleWrite.status()).toBe(409)
      expect(await staleWrite.text()).toContain('work_item_version_conflict')
      await expect(titleInput).toHaveValue('冲突时保留的本地标题')
      expect((await getJson<WorkItem>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-items/${workItemId}`,
        owner,
      )).title).toBe('并发更新后的服务端标题')

      await assertIdentityBoundaries(
        page,
        request,
        { spaceId, workItemId },
        { owner, spaceAdmin, member, guest, outsider, enterpriseAdmin },
      )

      await installSession(page, owner)
      for (const width of [1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        await page.goto(`/project-spaces/${spaceId}/work-items/${workItemId}`)
        await expect(page.getByTestId('project-work-item-detail')).toBeVisible()
        expect(await page.evaluate(() =>
          document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1)
      }
      const backButton = page.getByRole('button', { name: '返回工作项列表' })
      await backButton.focus()
      await backButton.press('Enter')
      await expect(page).toHaveURL(new RegExp(`/project-spaces/${spaceId}/work-items$`))
      await page.screenshot({
        path: testInfo.outputPath('s07-work-item-820.png'),
        fullPage: true,
      })

      const legacy = await createLegacyProjectAndIssue(request, enterpriseAdmin, suffix)
      await installSession(page, enterpriseAdmin)
      await page.goto(`/issues/${legacy.issueId}`)
      await expect(page).toHaveURL(new RegExp(`/issues/${legacy.issueId}$`))

      const spaceMigration = await request.post(`${apiBaseUrl}/admin/project-migrations/spaces:execute`, {
        headers: bearer(enterpriseAdmin),
        data: { confirmation: 'EXECUTE' },
      })
      expect(spaceMigration.ok()).toBeTruthy()
      legacySpaceBatchId = (await spaceMigration.json() as { batchId: string }).batchId
      const resolvedSpace = await getJson<{ spaceId: string }>(
        request,
        `${apiBaseUrl}/project-spaces/legacy-resolve/${legacy.projectId}`,
        enterpriseAdmin,
      )
      await publishLegacyMigrationTypes(
        request,
        enterpriseAdmin,
        resolvedSpace.spaceId,
        suffix,
      )
      const planResponse = await request.post(`${apiBaseUrl}/admin/project-migrations/work-items:plan`, {
        headers: bearer(enterpriseAdmin),
        data: { dryRun: false, throttleMillis: 0, projectIds: [legacy.projectId] },
      })
      expect(planResponse.ok()).toBeTruthy()
      const plan = await planResponse.json() as { id: string; failures: unknown[] }
      expect(plan.failures).toEqual([])
      legacyWorkItemBatchId = plan.id
      const execution = await request.post(
        `${apiBaseUrl}/admin/project-migrations/work-items/batches/${plan.id}:execute`,
        {
          headers: bearer(enterpriseAdmin),
          data: { confirmation: 'EXECUTE', workerId: `browser-${suffix}` },
        },
      )
      expect(execution.ok()).toBeTruthy()
      const verification = await request.post(
        `${apiBaseUrl}/admin/project-migrations/work-items/batches/${plan.id}:verify`,
        { headers: bearer(enterpriseAdmin) },
      )
      expect(verification.ok()).toBeTruthy()
      expect((await verification.json() as { matched: boolean }).matched).toBe(true)

      await page.goto(`/issues/${legacy.issueId}`)
      await expect(page).toHaveURL(new RegExp(
        `/project-spaces/${resolvedSpace.spaceId}/work-items/[0-9a-f-]+$`,
      ))
      await expect(page.getByTestId('project-work-item-detail')).toBeVisible()
      await expect(page.getByLabel('标题', { exact: true })).toHaveValue(legacy.title)
    } finally {
      await page.context().setOffline(false).catch(() => undefined)
      if (legacyWorkItemBatchId) {
        await request.post(
          `${apiBaseUrl}/admin/project-migrations/work-items/batches/${legacyWorkItemBatchId}:rollback`,
          { headers: bearer(enterpriseAdmin), data: { confirmation: 'ROLLBACK' } },
        ).catch(() => undefined)
      }
      if (legacySpaceBatchId) {
        await request.post(
          `${apiBaseUrl}/admin/project-migrations/batches/${legacySpaceBatchId}:rollback`,
          { headers: bearer(enterpriseAdmin), data: { confirmation: 'ROLLBACK' } },
        ).catch(() => undefined)
      }
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
          data: { handoverToUserId: enterpriseAdminProfile.id },
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
  const url = `${apiBaseUrl}/project-spaces/${target.spaceId}/work-items/${target.workItemId}`
  for (const session of [sessions.owner, sessions.spaceAdmin, sessions.member]) {
    const item = await getJson<WorkItem>(request, url, session)
    expect(item.availableActions).toContain('edit')
    expect(item.fieldValues).toHaveProperty('internal_note')
  }

  const guestItem = await getJson<WorkItem>(request, url, sessions.guest)
  expect(guestItem.availableActions).toEqual(['view'])
  expect(guestItem.fieldValues).not.toHaveProperty('internal_note')
  expect(guestItem.runtime.accessProjection).not.toHaveProperty('internal_note')
  expect(JSON.stringify(guestItem.runtime.snapshot)).not.toContain('internal_note')
  expect(JSON.stringify(guestItem.runtime.snapshot)).not.toContain('内部备注')
  await installSession(page, sessions.guest)
  await page.goto(`/project-spaces/${target.spaceId}/work-items/${target.workItemId}`)
  await expect(page.getByTestId('project-work-item-detail')).toBeVisible()
  await expect(page.getByRole('button', { name: /保\s*存/ })).toHaveCount(0)
  await expect(page.getByText('内部备注')).toHaveCount(0)
  await expect(page.getByText('保存后的动态字段内容')).toBeVisible()

  for (const session of [sessions.outsider, sessions.enterpriseAdmin]) {
    const response = await request.get(url, { headers: bearer(session) })
    expect(response.status()).toBe(404)
    expect(await response.text()).not.toContain('internal_note')
    expect(await response.text()).not.toContain('guest-must-not-disclose')
  }
}

async function createPublishedType(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  suffix: string,
) {
  const type = await postJson<{ id: string }>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`,
    owner,
    { typeKey: `${suffix}_delivery`, name: '交付事项', sortOrder: 10 },
    `s07-m5-type-${suffix}`,
  )
  const summary = await createField(request, owner, spaceId, type.id, 'acceptance_summary', '验收说明', 10)
  const secret = await createField(request, owner, spaceId, type.id, 'internal_note', '内部备注', 20)
  await putLayout(request, owner, spaceId, type.id, 'create', summary, secret)
  await putLayout(request, owner, spaceId, type.id, 'detail', summary, secret)
  let draft = await getJson<{ aggregateVersion: number }>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${type.id}/draft`,
    owner,
  )
  draft = await postJson(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${type.id}/draft:validate`,
    owner,
    { expectedAggregateVersion: draft.aggregateVersion },
    `s07-m5-validate-${suffix}`,
  )
  await postJson(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${type.id}/draft:publish`,
    owner,
    { expectedDraftAggregateVersion: draft.aggregateVersion, breakingConfirmed: false },
    `s07-m5-publish-${suffix}`,
  )
  return type.id
}

async function publishLegacyMigrationTypes(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  suffix: string,
) {
  const types = await getJson<Array<{ id: string; typeKey: string }>>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/work-item-types`,
    owner,
  )
  for (const typeKey of ['project', 'task']) {
    const type = types.find((candidate) => candidate.typeKey === typeKey)
    expect(type, `missing migrated ${typeKey} configuration`).toBeTruthy()
    let draft = await getJson<{ aggregateVersion: number }>(
      request,
      `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${type!.id}/draft`,
      owner,
    )
    draft = await postJson(
      request,
      `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${type!.id}/draft:validate`,
      owner,
      { expectedAggregateVersion: draft.aggregateVersion },
      `s07-m5-legacy-validate-${typeKey}-${suffix}`,
    )
    await postJson(
      request,
      `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${type!.id}/draft:publish`,
      owner,
      { expectedDraftAggregateVersion: draft.aggregateVersion, breakingConfirmed: false },
      `s07-m5-legacy-publish-${typeKey}-${suffix}`,
    )
  }
}

async function putLayout(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  typeId: string,
  layoutKind: 'create' | 'detail',
  summary: { id: string; fieldKey: string },
  secret: { id: string; fieldKey: string },
) {
  const sectionId = crypto.randomUUID()
  const response = await request.put(
    `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}/layouts/${layoutKind}`,
    {
      headers: {
        ...bearer(owner),
        'X-Colla-Request-Id': `s07-m5-layout-${layoutKind}-${typeId}`,
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
            config: { title: '工作项信息' },
            visibilityCondition: { schemaVersion: 1 },
          },
          ...[summary, secret].map((field, index) => ({
            id: crypto.randomUUID(),
            parentId: sectionId,
            nodeKey: `field_${field.fieldKey}`,
            nodeType: 'field',
            fieldId: field.id,
            fieldKey: field.fieldKey,
            sortOrder: index,
            config: { title: field.fieldKey },
            visibilityCondition: { schemaVersion: 1 },
          })),
        ],
        policies: [
          {
            id: crypto.randomUUID(),
            fieldId: summary.id,
            fieldKey: summary.fieldKey,
            policyKey: 'summary_access',
            policy: {
              schemaVersion: 1,
              default: { mode: 'write', required: true },
              rules: [{ ruleKey: 'guest_read', roles: ['guest'], mode: 'read', required: false }],
            },
          },
          {
            id: crypto.randomUUID(),
            fieldId: secret.id,
            fieldKey: secret.fieldKey,
            policyKey: 'internal_access',
            policy: {
              schemaVersion: 1,
              default: { mode: 'write', required: false },
              rules: [{ ruleKey: 'guest_hidden', roles: ['guest'], mode: 'hidden', required: false }],
            },
          },
        ],
        aggregateVersion: 0,
      },
    },
  )
  expect(response.ok(), `save ${layoutKind} layout failed`).toBeTruthy()
}

async function createLegacyProjectAndIssue(
  request: APIRequestContext,
  session: E2eSession,
  suffix: string,
) {
  const project = await postJson<{ id: string }>(
    request,
    `${apiBaseUrl}/projects`,
    session,
    {
      projectKey: `LEG-${suffix.slice(-10)}`,
      name: `S07 Legacy ${suffix}`,
      description: 'S07 mixed mode route acceptance',
      memberIds: [],
    },
    `s07-m5-legacy-project-${suffix}`,
  )
  const title = `S07 legacy mapped issue ${suffix}`
  const issue = await postJson<{ issue: { id: string } }>(
    request,
    `${apiBaseUrl}/projects/${project.id}/issues`,
    session,
    {
      issueType: 'task',
      title,
      description: 'legacy compatibility route',
      priority: 'medium',
    },
    `s07-m5-legacy-issue-${suffix}`,
  )
  return { projectId: project.id, issueId: issue.issue.id, title }
}

async function createSpace(request: APIRequestContext, owner: E2eSession, suffix: string) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(owner),
    data: {
      spaceKey: `s07-m5-${suffix.replaceAll('_', '-')}`,
      name: `S07 工作项验收 ${suffix}`,
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s07-m5-member-${userId}` },
    data: { userId, roleKey },
  })
  expect(response.ok()).toBeTruthy()
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
    `s07-m5-field-${fieldKey}-${typeId}`,
  )
}

async function createIdentity(
  request: APIRequestContext,
  administrator: E2eSession,
  username: string,
  displayName: string,
) {
  const password = 'member123456'
  const response = await request.post(`${apiBaseUrl}/admin/users`, {
    headers: bearer(administrator),
    data: {
      username,
      password,
      displayName,
      email: `${username}@example.com`,
      roleCode: 'member',
    },
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
  expect(response.ok(), `GET ${url} failed`).toBeTruthy()
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
