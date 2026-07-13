import { expect, test } from '@playwright/test'

import { installSession, loginByApi } from './support/api'
import { installFailureEvidence } from './support/diagnostics'
import { AdminConsolePage, UserWorkspacePage } from './support/pageObjects'

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

test('@smoke @route-final M4 UI-SPLIT v1 smoke: user workspace and admin console stay separated', async ({ page, request }, testInfo) => {
  const flushEvidence = installFailureEvidence(page, testInfo)
  try {
    await installSession(page, await loginByApi(request))
    const userWorkspace = new UserWorkspacePage(page)
    const adminConsole = new AdminConsolePage(page)

    for (const route of userRoutes) {
      await page.goto(route.path)
      await userWorkspace.expectVisible()
      await expect(page.getByText(route.label).first()).toBeVisible()
    }

    await userWorkspace.openAccountMenu()
    await expect(page.getByText('管理后台')).toBeVisible()

    for (const route of adminRoutes) {
      await page.goto(route.path)
      await adminConsole.expectVisible(route.label)
    }

    await adminConsole.returnToWorkspace()
    await expect(page).toHaveURL(/\/$/)
    await userWorkspace.expectVisible()
  } finally {
    await flushEvidence()
  }
})
