import { expect, test, type APIRequestContext, type BrowserContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

test.describe('PROJECT-PLATFORM-S02-M3 project-space UI isolation', () => {
  test('owner, member, and enterprise administrator keep separate project-space boundaries @smoke', async ({ browser, page, request }) => {
    test.setTimeout(120_000)
    requireIsolatedIdentityFixture()
    const suffix = `s02m3_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const owner = await loginByApi(request)
    const ownerProfile = await getJson<{ id: string; displayName: string }>(request, `${apiBaseUrl}/auth/me`, owner)
    const member = await createIdentity(request, owner, `${suffix}_member`, 'S02 M3 协作成员', 'member')
    const governor = await createIdentity(request, owner, `${suffix}_governor`, 'S02 M3 企业管理员', 'admin')
    let memberContext: BrowserContext | undefined
    let governorContext: BrowserContext | undefined
    let spaceId: string | undefined

    try {
      await installSession(page, owner)
      await page.goto('/project-spaces')
      await expect(page.getByTestId('project-spaces-page')).toBeVisible()
      await page.getByRole('button', { name: '新建项目空间' }).click()
      await page.getByLabel('空间名称').fill(`S02 M3 产品空间 ${suffix}`)
      await page.getByLabel('空间编码').fill(`s02-m3-${suffix.replaceAll('_', '-')}`)
      await page.getByLabel('空间说明').fill('用于验证用户协作、空间设置和企业治理边界。')
      const createResponsePromise = page.waitForResponse((response) => response.url().endsWith('/api/project-spaces') && response.request().method() === 'POST')
      await page.getByRole('button', { name: '创建空间' }).click()
      const createResponse = await createResponsePromise
      expect(createResponse.ok()).toBeTruthy()
      spaceId = (await createResponse.json() as { id: string }).id
      await expect(page).toHaveURL(new RegExp(`/project-spaces/${spaceId}$`))
      await expect(page.getByRole('heading', { name: `S02 M3 产品空间 ${suffix}` })).toBeVisible()

      await page.getByRole('button', { name: '空间设置' }).click()
      await page.getByLabel('空间说明').fill('设置已由真实浏览器流程更新。')
      const settingsResponsePromise = page.waitForResponse((response) => response.url().endsWith(`/api/project-spaces/${spaceId}/settings`) && response.request().method() === 'PATCH')
      await page.getByRole('button', { name: '保存设置' }).click()
      expect((await settingsResponsePromise).ok()).toBeTruthy()
      await expect(page.getByText('空间设置已保存')).toBeVisible()

      await page.getByRole('button', { name: '成员', exact: true }).click()
      await expect(page).toHaveURL(new RegExp(`/project-spaces/${spaceId}/members$`))
      await page.getByRole('button', { name: '邀请成员' }).click()
      await page.getByLabel('搜索候选成员').fill(member.username)
      await page.getByRole('combobox', { name: /候选成员/ }).click()
      await page.locator('.ant-select-dropdown:visible').getByText(member.displayName, { exact: false }).click()
      const inviteResponsePromise = page.waitForResponse((response) => response.url().endsWith(`/api/project-spaces/${spaceId}/invitations`) && response.request().method() === 'POST')
      await page.getByRole('button', { name: '发送邀请' }).click()
      expect((await inviteResponsePromise).ok()).toBeTruthy()
      await expect(page.getByText('邀请已发送')).toBeVisible()

      const invitations = await getJson<Array<{ id: string; inviteeUserId: string }>>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/invitations`,
        owner,
      )
      const invitation = invitations.find((item) => item.inviteeUserId === member.id)
      expect(invitation).toBeTruthy()
      const memberSession = await loginByApi(request, member.username, member.password)
      const acceptResponse = await request.post(`${apiBaseUrl}/project-space-invitations/${invitation?.id}/accept`, { headers: bearer(memberSession) })
      expect(acceptResponse.ok()).toBeTruthy()

      memberContext = await browser.newContext()
      const memberPage = await memberContext.newPage()
      await installSession(memberPage, memberSession)
      await memberPage.goto(`/project-spaces/${spaceId}`)
      await expect(memberPage.getByRole('heading', { name: `S02 M3 产品空间 ${suffix}` })).toBeVisible()
      await expect(memberPage.getByText('成员', { exact: true }).first()).toBeVisible()
      await expect(memberPage.getByRole('button', { name: '空间设置' })).toHaveCount(0)
      await expect(memberPage.getByRole('button', { name: '成员', exact: true })).toHaveCount(0)
      await memberPage.goto(`/project-spaces/${spaceId}/settings`)
      await expect(memberPage.getByText('无权访问空间设置')).toBeVisible()

      await installSession(page, owner)
      await page.goto(`/project-spaces/${spaceId}/members`)
      let memberRow = page.getByTestId('project-space-members-card').getByRole('row').filter({ hasText: member.displayName })
      await expect(memberRow).toBeVisible()
      const transferToMemberResponsePromise = page.waitForResponse((response) => response.url().includes(`/api/project-spaces/${spaceId}/members/`) && response.url().endsWith('/transfer-owner') && response.request().method() === 'POST')
      await memberRow.getByRole('button', { name: '转为 Owner' }).click()
      await page.getByRole('button', { name: '确认转移' }).click()
      expect((await transferToMemberResponsePromise).ok()).toBeTruthy()
      await expect(page.getByText('Owner 已转移，你现在是空间管理员')).toBeVisible()

      await memberPage.goto(`/project-spaces/${spaceId}/members`)
      const priorOwnerRow = memberPage.getByTestId('project-space-members-card').getByRole('row').filter({ hasText: ownerProfile.displayName })
      await expect(priorOwnerRow).toBeVisible()
      const transferBackResponsePromise = memberPage.waitForResponse((response) => response.url().includes(`/api/project-spaces/${spaceId}/members/`) && response.url().endsWith('/transfer-owner') && response.request().method() === 'POST')
      await priorOwnerRow.getByRole('button', { name: '转为 Owner' }).click()
      await memberPage.getByRole('button', { name: '确认转移' }).click()
      expect((await transferBackResponsePromise).ok()).toBeTruthy()
      await expect(memberPage.getByText('Owner 已转移，你现在是空间管理员')).toBeVisible()

      await installSession(page, owner)
      await page.goto(`/project-spaces/${spaceId}/members`)
      memberRow = page.getByTestId('project-space-members-card').getByRole('row').filter({ hasText: member.displayName })
      await expect(memberRow).toBeVisible()
      const roleResponsePromise = page.waitForResponse((response) => response.url().includes(`/api/project-spaces/${spaceId}/members/`) && response.url().endsWith('/role') && response.request().method() === 'PATCH')
      await memberRow.getByRole('combobox').click()
      await page.locator('.ant-select-dropdown:visible').getByText('访客', { exact: true }).click()
      expect((await roleResponsePromise).ok()).toBeTruthy()
      await expect(page.getByText('成员角色已更新')).toBeVisible()
      await memberRow.getByRole('button', { name: '移除' }).click()
      const removeResponsePromise = page.waitForResponse((response) => response.url().includes(`/api/project-spaces/${spaceId}/members/`) && response.request().method() === 'DELETE')
      await page.getByRole('button', { name: '确认移除' }).click()
      expect((await removeResponsePromise).ok()).toBeTruthy()
      await expect(memberRow).toHaveCount(0)
      const removedAccess = await request.get(`${apiBaseUrl}/project-spaces/${spaceId}`, { headers: bearer(memberSession) })
      expect(removedAccess.status()).toBe(404)

      await page.goto(`/project-spaces/${spaceId}/settings`)
      await page.getByRole('button', { name: '停用' }).click()
      await page.getByRole('button', { name: '确认停用' }).click()
      await expect(page.getByText('空间已停用，写入和成员变更已关闭。')).toBeVisible()
      await page.getByRole('button', { name: '恢复' }).click()
      await page.getByRole('button', { name: '确认恢复' }).click()
      await expect(page.getByText('空间设置已保存')).toHaveCount(0)
      await expect(page.getByText('空间已停用，写入和成员变更已关闭。')).toHaveCount(0)
      await page.getByRole('button', { name: '归档' }).click()
      await page.getByRole('button', { name: '确认归档' }).click()
      await expect(page.getByText('空间已归档，当前为只读状态。')).toBeVisible()

      const governorSession = await loginByApi(request, governor.username, governor.password)
      governorContext = await browser.newContext()
      const governorPage = await governorContext.newPage()
      await installSession(governorPage, governorSession)
      await governorPage.goto(`/admin/project-spaces/${spaceId}`)
      await expect(governorPage.getByTestId('admin-project-spaces-page')).toBeVisible()
      await expect(governorPage.getByText('企业治理权限不授予协作内容访问')).toBeVisible()
      await expect(governorPage.getByText('后台不提供协作内容打开按钮')).toBeVisible()
      const governorContentResponse = await request.get(`${apiBaseUrl}/project-spaces/${spaceId}`, { headers: bearer(governorSession) })
      expect(governorContentResponse.status()).toBe(404)

      for (const viewport of [{ width: 1366, height: 768 }, { width: 1440, height: 900 }, { width: 390, height: 844 }]) {
        await governorPage.setViewportSize(viewport)
        await governorPage.goto(`/admin/project-spaces/${spaceId}`)
        await expect(governorPage.getByTestId('admin-project-spaces-page')).toBeVisible()
        const overflow = await governorPage.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
        expect(overflow).toBeLessThanOrEqual(1)
      }
    } finally {
      await memberContext?.close()
      await governorContext?.close()
      if (spaceId) {
        await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`, { headers: bearer(owner) })
      }
      await request.post(`${apiBaseUrl}/admin/users/${member.id}/offboard`, { headers: bearer(owner), data: { handoverToUserId: ownerProfile.id } })
      await request.post(`${apiBaseUrl}/admin/users/${governor.id}/offboard`, { headers: bearer(owner), data: { handoverToUserId: ownerProfile.id } })
    }
  })
})

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

async function getJson<T>(request: APIRequestContext, url: string, session: E2eSession) {
  const response = await request.get(url, { headers: bearer(session) })
  expect(response.ok(), `GET ${url} failed`).toBeTruthy()
  return await response.json() as T
}
