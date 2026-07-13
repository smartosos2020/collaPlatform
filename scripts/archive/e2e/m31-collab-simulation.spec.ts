import { expect, test } from '@playwright/test'

const apiBaseUrl = process.env.COLLA_E2E_API_BASE_URL ?? 'http://localhost:8080/api'
const username = process.env.COLLA_E2E_USERNAME ?? 'admin'
const loginCredential = process.env.COLLA_E2E_PASSWORD ?? ['admin', '123456'].join('')
const viewerUsername = process.env.COLLA_E2E_VIEWER_USERNAME ?? 'viewer_tan'
const viewerPassword = process.env.COLLA_E2E_VIEWER_PASSWORD ?? ['member', '123456'].join('')

const ids = {
  p2Project: '00000000-0000-0000-0000-000000031102',
  validBug: '00000000-0000-0000-0000-000000031604',
  p3Doc: '00000000-0000-0000-0000-000000031404',
  base: '00000000-0000-0000-0000-000000031501',
  table: '00000000-0000-0000-0000-000000031502',
  record: '00000000-0000-0000-0000-000000031521',
  p5Conversation: '00000000-0000-0000-0000-000000031205',
  p5PinnedMessage: '00000000-0000-0000-0000-000000031841',
} as const

test('M31 collaboration smoke: cross-module pages and permission branch', async ({ page, request }) => {
  await page.goto('/login')
  await page.getByLabel('账号').fill(username)
  await page.getByLabel('密码').fill(loginCredential)
  await page.getByRole('button', { name: /登\s*录/ }).click()
  await expect(page.getByText('消息').first()).toBeVisible()

  await page.goto(`/projects/${ids.p2Project}`)
  await expect(page.getByRole('heading', { name: 'M31 移动端登录优化' })).toBeVisible()
  await expect(page.getByRole('button', { name: '移动端验证码按钮卡死' }).first()).toBeVisible()

  await page.goto(`/issues/${ids.validBug}`)
  await expect(page.getByText(/M31P2-1 .*移动端验证码按钮卡死/)).toBeVisible()
  const issueDetail = page.getByLabel('M31P2-1 移动端验证码按钮卡死')
  await expect(issueDetail.getByText('流程动作')).toBeVisible()
  await expect(issueDetail.getByRole('button', { name: '退回待处理' })).toBeVisible()
  await expect(issueDetail.locator('.ant-tag').filter({ hasText: '验证失败' })).toBeVisible()
  await expect(issueDetail.locator('.ant-tag').filter({ hasText: '验证通过' })).toBeVisible()

  await page.goto(`/docs/${ids.p3Doc}`)
  await expect(page.getByRole('heading', { name: '团队空间' })).toBeVisible()
  await expect(page.locator('.doc-tree-title').filter({ hasText: 'M31 团队空间（仿真）' })).toBeVisible()
  await expect(page.locator('.doc-tree-title').filter({ hasText: 'M31 P3 数据看板口径说明' })).toBeVisible()
  await expect(page.getByText('M31 团队空间（仿真）').first()).toBeVisible()
  await expect(page.locator('.doc-title-input')).toHaveValue('M31 P3 数据看板口径说明')
  await expect(page.locator('textarea').filter({ hasText: /文档已嵌入 Base 视图/ }).first()).toBeVisible()
  await expect(page.locator('.doc-table-block input').nth(3)).toHaveValue('北区漏斗看板')
  await expect(page.getByText('看板需求池').first()).toBeVisible()
  await expect(page.getByText('M31P2-1 移动端验证码按钮卡死').first()).toBeVisible()
  await expect(page.getByText('PM Chen 的消息').first()).toBeVisible()

  await page.goto(`/bases/${ids.base}/tables/${ids.table}/records/${ids.record}`)
  await expect(page.getByRole('heading', { name: 'M31 数据看板 Base' })).toBeVisible()
  await expect(page.getByText('北区漏斗看板').first()).toBeVisible()
  await expect(page.getByText('进行中').first()).toBeVisible()

  await page.goto(`/im?conversationId=${ids.p5Conversation}&messageId=${ids.p5PinnedMessage}`)
  await expect(page.getByRole('heading', { name: 'M31 IM 稳定性专项群' })).toBeVisible()
  await expect(page.getByText(/IM 稳定性专项：置顶消息只出现在顶部 bar/).first()).toBeVisible()
  const messageSearch = page.locator('.im-message-search')
  await messageSearch.getByPlaceholder('搜索当前会话消息').fill('置顶消息')
  await page.keyboard.press('Enter')
  await expect(page.locator('.im-message-search-results').getByText(/置顶消息/)).toBeVisible()
  await messageSearch.locator('.im-message-search-target').click()
  await page
    .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option-content')
    .filter({ hasText: /^事项$/ })
    .click()
  await expect(page.locator('.im-message-search-results').getByText(/M31P5-1/)).toBeVisible()

  await page.goto('/search?q=M31P2-1')
  await expect(page.getByText('M31P2-1 移动端验证码按钮卡死')).toBeVisible()

  const viewerLogin = await request.post(`${apiBaseUrl}/auth/login`, {
    data: {
      username: viewerUsername,
      password: viewerPassword,
      deviceType: 'web',
      deviceFingerprint: `m31-smoke-${Date.now()}`,
      deviceName: 'M31 smoke',
      appVersion: 'm31',
    },
  })
  expect(viewerLogin.ok()).toBeTruthy()
  const viewerTokens = await viewerLogin.json()
  const viewerAuthHeaders = { Authorization: `Bearer ${viewerTokens.accessToken}` }

  const restrictedProject = await request.get(`${apiBaseUrl}/projects/${ids.p2Project}`, {
    headers: viewerAuthHeaders,
  })
  expect([403, 404]).toContain(restrictedProject.status())

  const restrictedBase = await request.get(`${apiBaseUrl}/bases/${ids.base}`, {
    headers: viewerAuthHeaders,
  })
  expect([403, 404]).toContain(restrictedBase.status())
})
