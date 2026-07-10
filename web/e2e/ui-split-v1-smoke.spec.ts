import { expect, test } from '@playwright/test'

const apiBaseUrl = process.env.COLLA_E2E_API_BASE_URL ?? 'http://localhost:8080/api'
const username = process.env.COLLA_E2E_USERNAME ?? 'admin'
const password = process.env.COLLA_E2E_PASSWORD ?? ['admin', '123456'].join('')

const userRoutes = [
  { path: '/', label: '工作台' },
  { path: '/im', label: '消息' },
  { path: '/projects', label: '项目' },
  { path: '/knowledge-bases', label: '知识库' },
  { path: '/bases', label: '表格' },
  { path: '/approvals', label: '审批' },
  { path: '/notifications', label: '通知' },
  { path: '/search?q=colla', label: '搜索' },
] as const

const adminRoutes = [
  { path: '/admin/overview', label: '企业概览' },
  { path: '/admin/departments', label: '组织架构' },
  { path: '/admin/users', label: '成员管理' },
  { path: '/admin/user-groups', label: '用户组' },
  { path: '/admin/roles', label: '角色权限' },
  { path: '/admin/permission-governance', label: '权限治理' },
  { path: '/admin/knowledge-bases', label: '知识库治理' },
  { path: '/admin/audit-logs', label: '审计日志' },
] as const

test('UI-SPLIT v1 smoke: user workspace and admin console stay separated', async ({ page, request }) => {
  const login = await request.post(`${apiBaseUrl}/auth/login`, {
    data: {
      username,
      password,
      deviceType: 'web',
      deviceFingerprint: `ui-split-smoke-${Date.now()}`,
      deviceName: 'UI split smoke',
      appVersion: 'ui-split-v1',
    },
  })
  expect(login.ok()).toBeTruthy()
  const tokens = await login.json()

  await page.addInitScript(
    ([accessToken, refreshToken]) => {
      localStorage.setItem('colla.accessToken', accessToken)
      localStorage.setItem('colla.refreshToken', refreshToken)
    },
    [tokens.accessToken, tokens.refreshToken],
  )

  const failedResponses: string[] = []
  page.on('response', (response) => {
    if (response.url().includes('/api/') && response.status() >= 500) {
      failedResponses.push(`${response.status()} ${response.url()}`)
    }
  })

  for (const route of userRoutes) {
    await page.goto(route.path)
    await expect(page.locator('.user-workspace-shell')).toBeVisible()
    await expect(page.getByText(route.label).first()).toBeVisible()
    await expect(page.locator('.admin-console-shell')).toHaveCount(0)
  }

  await page.locator('.app-user-menu-trigger').click()
  await expect(page.getByText('管理后台')).toBeVisible()

  for (const route of adminRoutes) {
    await page.goto(route.path)
    await expect(page.locator('.admin-console-shell')).toBeVisible()
    await expect(page.getByRole('heading', { name: route.label })).toBeVisible()
    await expect(page.locator('.user-workspace-shell')).toHaveCount(0)
  }

  await expect(page.getByRole('button', { name: '返回工作台' })).toBeVisible()
  expect(failedResponses).toEqual([])
})
