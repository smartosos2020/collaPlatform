import { expect, test, type APIRequestContext, type BrowserContext, type Page } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

test.describe('PROJECT-PLATFORM-S02-M5 stage acceptance', () => {
  test('user, space administrator, and enterprise governor complete the S02 stage flow @smoke', async ({ browser, page, request }) => {
    test.setTimeout(360_000)
    requireIsolatedIdentityFixture()
    const suffix = `s02m5_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const owner = await loginByApi(request)
    const ownerProfile = await getJson<{ id: string; displayName: string }>(request, `${apiBaseUrl}/auth/me`, owner)
    const memberA = await createIdentity(request, owner, `${suffix}_member_a`, 'S02 M5 Member A', 'member')
    const memberB = await createIdentity(request, owner, `${suffix}_member_b`, 'S02 M5 Member B', 'member')
    const governor = await createIdentity(request, owner, `${suffix}_governor`, 'S02 M5 Governor', 'admin')
    const privateName = `S02 M5 Private ${suffix}`
    const workspaceName = `S02 M5 Workspace ${suffix}`
    const discoverableName = `S02 M5 Discoverable ${suffix}`
    const legacyProjectName = `S02 M5 Legacy ${suffix}`
    let memberAContext: BrowserContext | undefined
    let memberBContext: BrowserContext | undefined
    let governorContext: BrowserContext | undefined
    let privateSpaceId: string | undefined
    let workspaceSpaceId: string | undefined
    let discoverableSpaceId: string | undefined
    let batchId: string | undefined
    let batchRolledBack = false

    try {
      // Section 1: the space administrator creates spaces through the production UI and API.
      await installSession(page, owner)
      await page.goto('/project-spaces')
      await expect(page.getByTestId('project-spaces-page')).toBeVisible()
      privateSpaceId = await createSpaceViaUi(page, privateName, `s02m5-p-${suffix.replaceAll('_', '-')}`, '仅成员可见')
      workspaceSpaceId = await createSpaceViaUi(page, workspaceName, `s02m5-w-${suffix.replaceAll('_', '-')}`, '企业内可发现')
      await expect(page.locator('.project-space-hero').getByText('企业内可发现')).toBeVisible()
      const discoverableResponse = await request.post(`${apiBaseUrl}/project-spaces`, {
        headers: bearer(owner),
        data: { name: discoverableName, spaceKey: `s02m5-d-${suffix.replaceAll('_', '-')}`, description: 'S02 M5 discoverable visibility fixture', visibility: 'discoverable' },
      })
      expect(discoverableResponse.ok(), 'discoverable space creation failed').toBeTruthy()
      discoverableSpaceId = (await discoverableResponse.json() as { id: string }).id

      // Section 2: invitation, acceptance, role change, and last-owner protection.
      await page.goto(`/project-spaces/${privateSpaceId}/members`)
      await page.getByRole('button', { name: '邀请成员' }).click()
      await page.getByLabel('搜索候选成员').fill(memberA.username)
      await page.getByRole('combobox', { name: /候选成员/ }).click()
      await page.locator('.ant-select-dropdown:visible').getByText(memberA.displayName, { exact: false }).click()
      const inviteResponsePromise = page.waitForResponse((response) => response.url().endsWith(`/api/project-spaces/${privateSpaceId}/invitations`) && response.request().method() === 'POST')
      await page.getByRole('button', { name: '发送邀请' }).click()
      expect((await inviteResponsePromise).ok()).toBeTruthy()
      await expect(page.getByText('邀请已发送')).toBeVisible()

      const invitations = await getJson<Array<{ id: string; inviteeUserId: string }>>(request, `${apiBaseUrl}/project-spaces/${privateSpaceId}/invitations`, owner)
      const invitation = invitations.find((item) => item.inviteeUserId === memberA.id)
      expect(invitation, 'memberA invitation missing').toBeTruthy()
      const memberASession = await loginByApi(request, memberA.username, memberA.password)
      const acceptResponse = await request.post(`${apiBaseUrl}/project-space-invitations/${invitation?.id}/accept`, { headers: bearer(memberASession) })
      expect(acceptResponse.ok(), 'memberA accept failed').toBeTruthy()
      await page.goto(`/project-spaces/${privateSpaceId}/members`)
      await expect(page.getByTestId('project-space-members-card').getByRole('row').filter({ hasText: memberA.displayName })).toBeVisible()

      const leaveResponsePromise = page.waitForResponse((response) => response.url().endsWith(`/api/project-spaces/${privateSpaceId}/members/leave`) && response.request().method() === 'POST')
      await page.getByRole('button', { name: '退出空间' }).click()
      await page.getByRole('button', { name: '确认退出' }).click()
      const leaveResponse = await leaveResponsePromise
      expect(leaveResponse.status()).toBe(409)
      await page.getByRole('button', { name: 'Cancel' }).click()
      await expect(page.getByRole('dialog', { name: '退出项目空间？' })).toHaveCount(0)
      const membersCard = page.getByTestId('project-space-members-card')
      await expect(membersCard.getByRole('row').filter({ hasText: ownerProfile.displayName })).toBeVisible()
      await expect(page).toHaveURL(new RegExp(`/project-spaces/${privateSpaceId}/members$`))

      const memberARow = membersCard.getByRole('row').filter({ hasText: memberA.displayName })
      const roleResponsePromise = page.waitForResponse((response) => response.url().includes(`/api/project-spaces/${privateSpaceId}/members/`) && response.url().endsWith('/role') && response.request().method() === 'PATCH')
      await memberARow.getByRole('combobox').click()
      await page.locator('.ant-select-dropdown:visible').getByText('访客', { exact: true }).click()
      expect((await roleResponsePromise).ok()).toBeTruthy()
      await expect(page.getByText('成员角色已更新')).toBeVisible()

      // Section 3: an ordinary member keeps the execution view without governance entries.
      memberAContext = await browser.newContext()
      const memberAPage = await memberAContext.newPage()
      await installSession(memberAPage, memberASession)
      await memberAPage.goto(`/project-spaces/${privateSpaceId}`)
      await expect(memberAPage.getByRole('heading', { name: privateName })).toBeVisible()
      await expect(memberAPage.getByRole('button', { name: '协作概览' })).toBeVisible()
      await expect(memberAPage.getByRole('button', { name: '空间设置' })).toHaveCount(0)
      await expect(memberAPage.getByRole('button', { name: '成员', exact: true })).toHaveCount(0)
      await memberAPage.goto(`/project-spaces/${privateSpaceId}/settings`)
      await expect(memberAPage.getByText('无权访问空间设置')).toBeVisible()

      // Section 4: a non-member receives only the discoverable surface and minimal disclosure.
      memberBContext = await browser.newContext()
      const memberBPage = await memberBContext.newPage()
      const memberBSession = await loginByApi(request, memberB.username, memberB.password)
      await installSession(memberBPage, memberBSession)
      await memberBPage.goto('/project-spaces')
      const memberBList = memberBPage.locator('.project-space-list')
      await expect(memberBList.getByText(workspaceName)).toBeVisible()
      await expect(memberBList.getByText(discoverableName)).toBeVisible()
      await expect(memberBList.getByText(privateName)).toHaveCount(0)
      await memberBList.getByText(workspaceName).click()
      await expect(memberBPage.getByRole('heading', { name: workspaceName })).toBeVisible()
      await expect(memberBPage.locator('.project-space-hero').getByText('非成员')).toBeVisible()
      await expect(memberBPage.getByRole('button', { name: '空间设置' })).toHaveCount(0)
      const privateDetail = await request.get(`${apiBaseUrl}/project-spaces/${privateSpaceId}`, { headers: bearer(memberBSession) })
      expect(privateDetail.status()).toBe(404)
      const discoverableDetail = await request.get(`${apiBaseUrl}/project-spaces/${discoverableSpaceId}`, { headers: bearer(memberBSession) })
      expect(discoverableDetail.ok()).toBeTruthy()
      expect((await discoverableDetail.json() as { visibility: string }).visibility).toBe('discoverable')
      await memberBPage.goto(`/project-spaces/${privateSpaceId}`)
      await expect(memberBPage.getByText('空间不存在或你无权访问')).toBeVisible()
      await expect(memberBPage.getByText(privateName)).toHaveCount(0)

      // Section 5: lifecycle transitions enforce read-only states for the space administrator.
      await page.goto(`/project-spaces/${workspaceSpaceId}/settings`)
      await page.getByRole('button', { name: '停用' }).click()
      await page.getByRole('button', { name: '确认停用' }).click()
      await expect(page.getByText('空间已停用，写入和成员变更已关闭。')).toBeVisible()
      await expect(page.getByRole('button', { name: '保存设置' })).toBeDisabled()
      await page.getByRole('button', { name: '恢复' }).click()
      await page.getByRole('button', { name: '确认恢复' }).click()
      await expect(page.getByText('空间已停用，写入和成员变更已关闭。')).toHaveCount(0)
      await page.getByRole('button', { name: '归档' }).click()
      await page.getByRole('button', { name: '确认归档' }).click()
      await expect(page.getByText('空间已归档，当前为只读状态。')).toBeVisible()
      await expect(page.getByRole('button', { name: '保存设置' })).toBeDisabled()

      // Section 6: the enterprise governor manages metadata without content access.
      const governorSession = await loginByApi(request, governor.username, governor.password)
      governorContext = await browser.newContext()
      const governorPage = await governorContext.newPage()
      await installSession(governorPage, governorSession)
      await governorPage.goto('/admin/project-spaces')
      await expect(governorPage.getByTestId('admin-project-spaces-page')).toBeVisible()
      await expect(governorPage.getByText(privateName)).toBeVisible()
      await governorPage.goto(`/admin/project-spaces/${privateSpaceId}`)
      await expect(governorPage.getByTestId('admin-project-spaces-page')).toBeVisible()
      await expect(governorPage.getByText('企业治理权限不授予协作内容访问')).toBeVisible()
      await expect(governorPage.getByText('后台不提供协作内容打开按钮')).toBeVisible()
      const governorContentResponse = await request.get(`${apiBaseUrl}/project-spaces/${privateSpaceId}`, { headers: bearer(governorSession) })
      expect(governorContentResponse.status()).toBe(404)

      // Section 7: a migrated legacy deep link routes the member to the mapped space.
      const legacyProject = await createLegacyProject(request, owner, `S02M5${randomKey()}`, legacyProjectName, [memberA.id])
      const dryRunResponse = await request.post(`${apiBaseUrl}/admin/project-migrations/spaces:dry-run`, { headers: bearer(owner) })
      expect(dryRunResponse.ok(), 'stage dry-run failed').toBeTruthy()
      const executeResponse = await request.post(`${apiBaseUrl}/admin/project-migrations/spaces:execute`, {
        headers: bearer(owner),
        data: { confirmation: 'EXECUTE' },
      })
      expect(executeResponse.ok(), 'stage migration execute failed').toBeTruthy()
      const executeBatch = await executeResponse.json() as {
        id: string
        summary: { projects: Array<{ projectId: string; outcome: string; spaceId?: string }> }
      }
      batchId = executeBatch.id
      const projectOutcome = executeBatch.summary.projects.find((item) => item.projectId === legacyProject.id)
      expect(projectOutcome, 'legacy project missing from stage batch').toBeTruthy()
      expect(['CREATED', 'REUSED']).toContain(projectOutcome?.outcome)
      const resolution = await getJson<{ status: string; spaceId?: string | null }>(request, `${apiBaseUrl}/project-spaces/legacy-resolve/${legacyProject.id}`, memberASession)
      expect(resolution.status).toBe('mapped')
      expect(resolution.spaceId).toBeTruthy()
      await memberAPage.goto(`/projects/${legacyProject.id}`)
      await expect(memberAPage.getByText('该项目已迁移到项目空间')).toBeVisible()
      await memberAPage.getByRole('button', { name: '前往项目空间' }).click()
      await expect(memberAPage).toHaveURL(new RegExp(`/project-spaces/${resolution.spaceId as string}$`))
      await expect(memberAPage.getByRole('heading', { name: legacyProjectName })).toBeVisible()
      const rollbackResponse = await request.post(`${apiBaseUrl}/admin/project-migrations/batches/${batchId}:rollback`, {
        headers: bearer(owner),
        data: { confirmation: 'ROLLBACK' },
      })
      expect(rollbackResponse.ok(), 'stage rollback failed').toBeTruthy()
      expect((await rollbackResponse.json() as { status: string }).status).toBe('rolled_back')
      batchRolledBack = true
      const afterRollback = await getJson<{ status: string }>(request, `${apiBaseUrl}/project-spaces/legacy-resolve/${legacyProject.id}`, memberASession)
      expect(afterRollback.status).toBe('failed')
    } finally {
      await memberAContext?.close()
      await memberBContext?.close()
      await governorContext?.close()
      for (const spaceId of [privateSpaceId, workspaceSpaceId, discoverableSpaceId]) {
        if (spaceId) await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`, { headers: bearer(owner) })
      }
      if (batchId && !batchRolledBack) {
        await request.post(`${apiBaseUrl}/admin/project-migrations/batches/${batchId}:rollback`, { headers: bearer(owner), data: { confirmation: 'ROLLBACK' } })
      }
      await request.post(`${apiBaseUrl}/admin/users/${memberA.id}/offboard`, { headers: bearer(owner), data: { handoverToUserId: ownerProfile.id } })
      await request.post(`${apiBaseUrl}/admin/users/${memberB.id}/offboard`, { headers: bearer(owner), data: { handoverToUserId: ownerProfile.id } })
      await request.post(`${apiBaseUrl}/admin/users/${governor.id}/offboard`, { headers: bearer(owner), data: { handoverToUserId: ownerProfile.id } })
    }
  })
})

async function createSpaceViaUi(page: Page, name: string, spaceKey: string, visibilityLabel: string): Promise<string> {
  await page.getByRole('button', { name: '新建项目空间' }).click()
  const modal = page.locator('.ant-modal:visible')
  await modal.getByLabel('空间名称').fill(name)
  await modal.getByLabel('空间编码').fill(spaceKey)
  await modal.getByLabel('空间说明').fill('S02 M5 stage acceptance fixture')
  await modal.locator('.ant-segmented').getByText(visibilityLabel, { exact: true }).click()
  const createResponsePromise = page.waitForResponse((response) => response.url().endsWith('/api/project-spaces') && response.request().method() === 'POST')
  await modal.getByRole('button', { name: '创建空间' }).click()
  const createResponse = await createResponsePromise
  expect(createResponse.ok(), `space creation failed for ${name}`).toBeTruthy()
  const spaceId = (await createResponse.json() as { id: string }).id
  await expect(page).toHaveURL(new RegExp(`/project-spaces/${spaceId}$`))
  await expect(page.getByRole('heading', { name })).toBeVisible()
  return spaceId
}

function randomKey() {
  return Math.random().toString(36).slice(2, 8).toUpperCase()
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
  expect(response.ok(), `identity fixture creation failed for ${username}`).toBeTruthy()
  const payload = await response.json() as { id: string; username: string; displayName: string }
  return { ...payload, password }
}

async function createLegacyProject(
  request: APIRequestContext,
  session: E2eSession,
  projectKey: string,
  name: string,
  memberIds: string[],
) {
  const response = await request.post(`${apiBaseUrl}/projects`, {
    headers: bearer(session),
    data: { projectKey, name, description: 'S02 M5 stage acceptance legacy fixture', memberIds },
  })
  expect(response.ok(), `legacy project fixture creation failed for ${name}`).toBeTruthy()
  return await response.json() as { id: string; projectKey: string }
}

async function getJson<T>(request: APIRequestContext, url: string, session: E2eSession) {
  const response = await request.get(url, { headers: bearer(session) })
  expect(response.ok(), `GET ${url} failed`).toBeTruthy()
  return await response.json() as T
}
