import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

test.describe('M6 real isolated member workspace', () => {
  test('ordinary member completes settings, preferences, objects, states, accessibility, and workspace task flow @smoke', async ({ page, request }) => {
    requireIsolatedIdentityFixture()
    const suffix = `m6_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const admin = await loginByApi(request)
    const username = `${suffix}_member`
    const initialPassword = ['member', '123456'].join('')
    const changedPassword = ['member', '654321'].join('')
    const createMemberResponse = await request.post(`${apiBaseUrl}/admin/users`, {
      headers: bearer(admin),
      data: { username, password: initialPassword, displayName: 'M6 真实成员', email: `${username}@example.com`, roleCode: 'member' },
    })
    expect(createMemberResponse.ok(), 'isolated member creation failed').toBeTruthy()
    await createMemberResponse.json()
    let memberSession = await loginByApi(request, username, initialPassword)

    const knowledge = await createKnowledgeBase(request, memberSession, suffix)
    const project = await createProject(request, memberSession, suffix)
    const base = await createBase(request, memberSession, suffix)
    const conversation = await createConversation(request, memberSession, suffix)

    await installSession(page, memberSession)

    // T01: real profile/avatar/password/security closure.
    await page.goto('/settings')
    await expect(page.getByRole('heading', { name: '个人设置' })).toBeVisible()
    await page.getByLabel('显示名称').fill('M6 真实成员·已更新')
    await page.getByRole('button', { name: '保存资料' }).click()
    await expect(page.getByText('已保存')).toBeVisible()
    await page.locator('input[type="file"]').setInputFiles({
      name: 'm6-avatar.png',
      mimeType: 'image/png',
      buffer: Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    })
    await expect.poll(async () => (await getMe(request, memberSession)).avatarFileId).not.toBeNull()
    await page.getByLabel('当前密码').fill(initialPassword)
    await page.getByLabel('新密码').first().fill(changedPassword)
    await page.getByLabel('确认新密码').fill(changedPassword)
    await page.getByRole('button', { name: '更新密码' }).click()
    await expect(page.getByText('密码已更新')).toBeVisible()
    memberSession = await loginByApi(request, username, changedPassword)
    await installSession(page, memberSession)
    await page.goto('/settings')
    await expect(page.getByText('M6 真实成员·已更新')).toBeVisible()
    await expect(page.getByText('登录设备', { exact: true }).first()).toBeVisible()

    // T02: server-backed notification preference plus browser-backed work preference.
    const messageSwitch = page.getByRole('switch', { name: '消息与提及通知' })
    await expect(messageSwitch).toBeEnabled()
    await messageSwitch.click()
    await page.reload()
    await expect(page.getByRole('switch', { name: '消息与提及通知' })).not.toBeChecked()
    const persistedPreferences = await getJson(request, `${apiBaseUrl}/notifications/preferences`, memberSession)
    expect(persistedPreferences.find((item: { sourceType: string }) => item.sourceType === 'im').enabled).toBe(false)
    await expect(page.getByRole('switch', { name: '权限与安全通知' })).toBeDisabled()
    const compactSwitch = page.getByRole('switch', { name: '紧凑卡片' })
    await compactSwitch.click()
    await page.reload()
    await expect(page.getByRole('switch', { name: '紧凑卡片' })).toBeChecked()

    // T03: real object creation and cross-module navigation through object cards/search.
    await page.goto('/knowledge-bases')
    await expect(page.getByText(knowledge.name)).toBeVisible()
    await page.locator('.kb-card').filter({ hasText: knowledge.name }).getByRole('button').first().click()
    await expect(page).toHaveURL(new RegExp(`/knowledge-bases/${knowledge.id}`))
    await page.goto('/projects')
    await expect(page.getByText(project.name).first()).toBeVisible()
    await page.goto('/bases')
    await expect(page.getByText(base.name).first()).toBeVisible()
    await page.goto(`/search?q=${encodeURIComponent(knowledge.name)}`)
    await expect(page.getByText(knowledge.name).first()).toBeVisible()

    // T04: real empty/denied/offline states have explanatory copy, without API interception.
    await page.goto('/notifications')
    await expect(page.getByText('暂无通知')).toBeVisible()
    await page.goto('/error/403')
    await expect(page.getByText('无权限访问')).toBeVisible()
    await page.goto('/settings')
    await page.context().setOffline(true)
    await expect(page.getByText('当前处于离线状态，已打开页面可继续查看，新的保存操作会失败。')).toBeVisible()
    await page.context().setOffline(false)

    // T05: ordinary shell has no admin navigation or internal governance copy.
    await page.goto('/')
    await expect(page.getByPlaceholder('搜索事项、知识内容、表格、消息')).toBeVisible()
    await expect(page.getByPlaceholder('后台治理搜索：成员、部门、角色、审计动作')).toHaveCount(0)
    const workspaceText = await page.locator('body').innerText()
    expect(workspaceText).not.toContain('PILOT-V2-')
    expect(workspaceText).not.toContain('迁移脚本')

    // T06: real pages at desktop and narrow viewports have no page-level overflow.
    for (const viewport of [{ width: 1366, height: 900 }, { width: 1440, height: 900 }, { width: 390, height: 844 }]) {
      await page.setViewportSize(viewport)
      await page.goto('/settings')
      await expect(page.getByRole('heading', { name: '个人设置' })).toBeVisible()
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
      expect(overflow, `horizontal overflow at ${viewport.width}`).toBeLessThanOrEqual(1)
    }

    // T07: real keyboard interaction and readable control names.
    await page.getByLabel('显示名称').focus()
    await page.keyboard.press('Tab')
    await page.keyboard.press('Tab')
    await page.getByRole('button', { name: '保存资料' }).focus()
    await page.keyboard.press('Enter')
    await expect(page.getByText('已保存')).toBeVisible()
    await page.getByRole('switch', { name: '对象链接在新标签页打开' }).focus()
    await page.keyboard.press('Space')
    await expect(page.getByRole('switch', { name: '对象链接在新标签页打开' })).toBeChecked()

    // T08: real ordinary-member task script across IM, projects, knowledge, Base, approval, notifications and search.
    await page.goto('/im')
    await expect(page.getByText(conversation.title).first()).toBeVisible()
    await page.goto(`/projects/${project.id}`)
    await expect(page.getByText(project.name).first()).toBeVisible()
    await page.goto(`/knowledge-bases/${knowledge.id}`)
    await expect(page.getByText(knowledge.name).first()).toBeVisible()
    await page.goto(`/bases/${base.id}`)
    await expect(page.getByText(base.name).first()).toBeVisible()
    await page.goto('/approvals')
    await expect(page.getByRole('heading', { name: '审批' })).toBeVisible()
    await page.goto('/notifications')
    await expect(page.getByRole('heading', { name: '通知' })).toBeVisible()
    await page.goto(`/search?q=${encodeURIComponent(knowledge.name)}`)
    await expect(page.getByText(knowledge.name).first()).toBeVisible()
  })
})

async function getMe(request: APIRequestContext, session: E2eSession) {
  return await getJson(request, `${apiBaseUrl}/auth/me`, session) as { avatarFileId?: string | null; displayName: string }
}

async function getJson(request: APIRequestContext, url: string, session: E2eSession) {
  const response = await request.get(url, { headers: bearer(session) })
  expect(response.ok(), `GET ${url} failed`).toBeTruthy()
  return await response.json()
}

async function createKnowledgeBase(request: APIRequestContext, session: E2eSession, suffix: string) {
  const response = await request.post(`${apiBaseUrl}/knowledge-bases`, {
    headers: bearer(session),
    data: { name: `M6 Knowledge ${suffix}`, code: `m6-${suffix}`.slice(0, 64), description: 'M6 isolated real fixture', visibility: 'private' },
  })
  expect(response.ok(), 'knowledge base fixture creation failed').toBeTruthy()
  const payload = await response.json()
  return { id: payload.space.id, name: payload.space.name }
}

async function createProject(request: APIRequestContext, session: E2eSession, suffix: string) {
  const response = await request.post(`${apiBaseUrl}/projects`, {
    headers: bearer(session),
    data: { projectKey: `M6${suffix.slice(-5).toUpperCase()}`, name: `M6 Project ${suffix}`, description: 'M6 isolated real fixture', memberIds: [] },
  })
  expect(response.ok(), 'project fixture creation failed').toBeTruthy()
  const payload = await response.json()
  return { id: payload.id, name: payload.name }
}

async function createBase(request: APIRequestContext, session: E2eSession, suffix: string) {
  const response = await request.post(`${apiBaseUrl}/bases`, {
    headers: bearer(session),
    data: { name: `M6 Base ${suffix}`, description: 'M6 isolated real fixture' },
  })
  expect(response.ok(), 'base fixture creation failed').toBeTruthy()
  const payload = await response.json()
  return { id: payload.base.id, name: payload.base.name }
}

async function createConversation(request: APIRequestContext, session: E2eSession, suffix: string) {
  const response = await request.post(`${apiBaseUrl}/conversations`, {
    headers: bearer(session),
    data: { conversationType: 'group', title: `M6 Conversation ${suffix}`, memberIds: [] },
  })
  expect(response.ok(), 'conversation fixture creation failed').toBeTruthy()
  const payload = await response.json()
  return { id: payload.id, title: payload.title }
}
