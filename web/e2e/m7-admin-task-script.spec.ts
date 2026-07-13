import { expect, test } from '@playwright/test'

const admin = { id: 'admin-1', workspaceId: 'workspace-1', username: 'admin', displayName: '企业管理员', roles: ['admin'], permissions: ['workspace:manage'] }

test('M7 administrator independently completes organization, account, permission, and audit task script @smoke', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('colla.accessToken', 'm7-task-token')
    localStorage.setItem('colla.refreshToken', 'm7-task-refresh')
  })
  let departments: Array<{ department: Record<string, unknown>; managers: unknown[]; children: unknown[] }> = []
  let members: Array<Record<string, unknown>> = [{ ...admin, status: 'active', departments: [], createdAt: '2026-07-13T00:00:00Z', roles: ['admin'] }]
  let auditAction = 'resource.permission.granted'
  await page.route('**/api/auth/me', (route) => route.fulfill({ json: admin }))
  await page.route('**/api/admin/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    if (path.endsWith('/departments/tree')) return route.fulfill({ json: departments })
    if (path.endsWith('/departments') && request.method() === 'POST') {
      const body = request.postDataJSON() as { code: string; name: string }
      const department = { id: 'department-1', parentId: null, code: body.code, name: body.name, path: body.code, depth: 0, sortOrder: 0, status: 'active', memberCount: 0, managerCount: 0, createdAt: '2026-07-13T00:00:00Z', updatedAt: '2026-07-13T00:00:00Z' }
      departments = [{ department, managers: [], children: [] }]
      return route.fulfill({ json: department })
    }
    if (path.includes('/departments/') && path.endsWith('/members')) return route.fulfill({ json: [] })
    if (path.endsWith('/users') && request.method() === 'GET') return route.fulfill({ json: members })
    if (path.endsWith('/users') && request.method() === 'POST') {
      const body = request.postDataJSON() as { username: string; displayName: string }
      const member = { id: 'member-1', username: body.username, displayName: body.displayName, status: 'active', roles: ['member'], departments: [], createdAt: '2026-07-13T00:00:00Z' }
      members = [...members, member]
      return route.fulfill({ json: member })
    }
    if (path.endsWith('/knowledge-bases')) return route.fulfill({ json: [] })
    if (path.endsWith('/permission-governance/risks')) return route.fulfill({ json: { total: 1, items: [{ id: 'risk-1', ruleCode: 'expired_active_permission', severity: 'high', resourceType: 'knowledge_content', resourceId: 'content-1', subjectType: 'user', subjectId: 'member-1', subjectName: '验收成员', permissionLevel: 'edit', reason: '授权已过期', suggestedAction: '撤销授权', impactScope: { resourceType: 'knowledge_content', resourceId: 'content-1', subjectId: 'member-1' } }] } })
    if (path.includes('/permission-governance/risks/risk-1/remediation')) {
      const confirmed = url.searchParams.get('confirm') === 'true'
      return route.fulfill({ json: { riskId: 'risk-1', ruleCode: 'expired_active_permission', executable: true, applied: confirmed, action: 'revoke_permission', reason: confirmed ? '已撤销过期授权' : '将撤销过期授权' } })
    }
    if (path.endsWith('/audit-logs')) {
      auditAction = url.searchParams.get('action') || auditAction
      return route.fulfill({ json: [{ id: 'audit-1', createdAt: '2026-07-13T00:00:00Z', actorName: '企业管理员', actorId: 'admin-1', action: auditAction, targetType: 'knowledge_content', targetId: 'content-1', metadata: { sourceUi: 'admin' } }] })
    }
    return route.fulfill({ json: [] })
  })

  await page.goto('/admin/departments')
  await page.getByRole('button', { name: '新建根部门' }).click()
  await page.getByLabel('部门编码').fill('qa')
  await page.getByLabel('部门名称').fill('验收组织')
  await page.locator('.ant-modal .ant-modal-footer button').last().click()
  await expect(page.getByText('部门已创建')).toBeVisible()

  await page.getByRole('menuitem', { name: '成员管理' }).click()
  await page.getByRole('button', { name: '新增成员' }).click()
  await page.getByLabel('账号').fill('qa-member')
  await page.getByLabel('显示名称').fill('验收成员')
  await page.getByLabel('初始密码').fill('member123456')
  await page.locator('.ant-modal .ant-modal-footer button').last().click()
  await expect(page.getByText('成员已创建')).toBeVisible()

  await page.getByRole('menuitem', { name: '权限治理' }).click()
  await page.locator('.admin-permission-governance-page .ant-table-tbody button').last().click()
  await expect(page.locator('.ant-modal-confirm-title')).toBeVisible()
  await page.locator('.ant-modal-confirm-btns .ant-btn-primary').click()
  await expect(page.getByText('已撤销过期授权')).toBeVisible()

  await page.getByRole('menuitem', { name: '审计日志' }).click()
  await page.getByRole('button', { name: '权限授予' }).click()
  await expect(page.getByText('resource.permission.granted')).toBeVisible()
  await expect(page.getByText('敏感字段最小展示')).toBeVisible()
})
