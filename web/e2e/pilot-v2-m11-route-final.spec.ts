import { expect, test } from '@playwright/test'

import { LoginPage, AdminConsolePage, UserWorkspacePage } from './support/pageObjects'

const adminUsername = process.env.COLLA_E2E_ADMIN_USERNAME ?? 'admin'
const adminPassword = process.env.COLLA_E2E_ADMIN_PASSWORD

const userRoutes = [
  '/',
  '/im',
  '/projects',
  '/knowledge-bases',
  '/bases',
  '/approvals',
  '/notifications',
  '/search?q=colla',
]

const adminRoutes = [
  ['/admin/overview', '企业概览'],
  ['/admin/departments', '组织架构'],
  ['/admin/users', '成员管理'],
  ['/admin/user-groups', '用户组'],
  ['/admin/roles', '角色权限'],
  ['/admin/permission-governance', '权限治理'],
  ['/admin/batch-governance', '批量治理'],
  ['/admin/security', '安全策略'],
  ['/admin/knowledge-bases', '知识库治理'],
  ['/admin/app-governance', '应用治理'],
  ['/admin/system-settings', '系统设置'],
  ['/admin/audit-logs', '审计日志'],
] as const

test.describe.serial('@route-final real UI route closure', () => {
  test('admin can move between the user workspace and every admin route', async ({ page }) => {
    expect(adminPassword, 'COLLA_E2E_ADMIN_PASSWORD is required').toBeTruthy()

    const loginPage = new LoginPage(page)
    const workspace = new UserWorkspacePage(page)
    const adminConsole = new AdminConsolePage(page)

    await loginPage.open()
    await loginPage.signIn(adminUsername, adminPassword!)
    await expect(page).not.toHaveURL(/\/login(?:\?|$)/)
    await workspace.expectVisible()

    for (const route of userRoutes) {
      await page.goto(route)
      await workspace.expectVisible()
    }

    await page.goto('/')
    await workspace.openAccountMenu()
    await expect(page.getByText('管理后台', { exact: true })).toBeVisible()
    await page.getByText('管理后台', { exact: true }).click()
    await expect(page).toHaveURL(/\/admin\/overview$/)

    for (const [route, title] of adminRoutes) {
      await page.goto(route)
      await adminConsole.expectVisible(title)
    }

    await adminConsole.returnToWorkspace()
    await workspace.expectVisible()
  })
})
