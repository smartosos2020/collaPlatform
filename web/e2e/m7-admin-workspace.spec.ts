import { expect, test } from '@playwright/test'

const admin = {
  id: '11111111-1111-1111-1111-111111111111',
  workspaceId: '22222222-2222-2222-2222-222222222222',
  username: 'admin',
  displayName: '企业管理员',
  roles: ['admin'],
  permissions: ['workspace:manage'],
}

const settings = {
  workspace: { id: admin.workspaceId, name: 'Colla 示例企业', slug: 'colla-demo', status: 'active', createdAt: '2026-07-13T00:00:00Z', updatedAt: '2026-07-13T00:00:00Z' },
  securityPolicy: { passwordMinLength: 8, passwordRequireLetter: true, passwordRequireDigit: true, accessTokenTtlMinutes: 60, refreshTokenTtlDays: 14, requiredSecurityNotifications: true, requiredSystemNotifications: true },
  runtime: { service: 'colla-platform', healthEndpoint: '健康检查由 /api/health 提供', memberCount: 12, activeSessionCount: 3, activeDeviceCount: 5 },
}

test('M7 administrator can drill down overview, read settings, and manage security devices @smoke', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('colla.accessToken', 'm7-admin-token')
    localStorage.setItem('colla.refreshToken', 'm7-admin-refresh')
  })
  await page.route('**/api/auth/me', (route) => route.fulfill({ json: admin }))
  await page.route('**/api/admin/system-settings', (route) => route.fulfill({ json: settings }))
  await page.route('**/api/health', (route) => route.fulfill({ json: { status: 'ok', service: 'colla-platform', time: '2026-07-13T00:00:00Z' } }))
  await page.route('**/api/devices/**', (route) => route.fulfill({ status: 204 }))
  await page.route('**/api/devices', (route) => route.fulfill({ json: [
    { id: 'device-current', deviceType: 'web', deviceName: '当前浏览器', deviceFingerprint: 'current', createdAt: '2026-07-13T00:00:00Z', activeSessionCount: 1, enabledPushTokenCount: 0, current: true },
    { id: 'device-old', deviceType: 'desktop', deviceName: '旧设备', deviceFingerprint: 'old', createdAt: '2026-07-12T00:00:00Z', activeSessionCount: 1, enabledPushTokenCount: 0, current: false },
  ] }))
  await page.route('**/api/notifications/preferences', (route) => route.fulfill({ json: [
    { sourceType: 'resource', enabled: true, required: true },
    { sourceType: 'system', enabled: true, required: true },
  ] }))
  await page.route('**/api/admin/users**', (route) => route.fulfill({ json: [{ ...admin, status: 'active', departments: [] }, { id: 'member-2', username: 'disabled', displayName: '异常成员', status: 'disabled', roles: ['member'], departments: [] }] }))
  await page.route('**/api/admin/departments/tree', (route) => route.fulfill({ json: [] }))
  await page.route('**/api/admin/user-groups', (route) => route.fulfill({ json: [] }))
  await page.route('**/api/admin/roles', (route) => route.fulfill({ json: [] }))
  await page.route('**/api/admin/permission-governance/risks', (route) => route.fulfill({ json: { items: [{ id: 'risk-1', severity: 'high', ruleCode: 'broad_permission', reason: '范围过宽' }], total: 1 } }))
  await page.route('**/api/admin/audit-logs**', (route) => route.fulfill({ json: [{ id: 'audit-1', action: 'user.disabled', actorName: '企业管理员', targetType: 'user', createdAt: '2026-07-13T00:00:00Z' }] }))

  await page.goto('/admin/overview')
  await expect(page.getByRole('heading', { name: '企业概览' })).toBeVisible()
  await expect(page.getByText('停用成员 1')).toBeVisible()
  await page.getByRole('link', { name: '查看运行信息' }).click()
  await expect(page.getByTestId('admin-system-settings-page')).toBeVisible()
  await expect(page.getByText('Colla 示例企业')).toBeVisible()
  await expect(page.getByText('系统设置当前为只读视图')).toBeVisible()

  await page.getByRole('menuitem', { name: '安全策略' }).click()
  await expect(page.getByTestId('admin-security-page')).toBeVisible()
  await expect(page.getByText('旧设备')).toBeVisible()
  await page.getByRole('button', { name: '撤销' }).click()
})
