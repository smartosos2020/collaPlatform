import { expect, test } from '@playwright/test'

const currentUser = {
  id: '11111111-1111-1111-1111-111111111111',
  workspaceId: '22222222-2222-2222-2222-222222222222',
  username: 'member',
  displayName: '协作成员',
  email: 'member@example.com',
  avatarFileId: null,
  roles: ['member'],
  permissions: [],
}

const preferences = [
  { sourceType: 'im', enabled: true, required: false },
  { sourceType: 'project', enabled: true, required: false },
  { sourceType: 'resource', enabled: true, required: true },
  { sourceType: 'system', enabled: true, required: true },
]

test('M6 member can update profile, tune preferences, and complete keyboard-safe narrow layout @smoke', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('colla.accessToken', 'm6-member-token')
    localStorage.setItem('colla.refreshToken', 'm6-member-refresh')
  })

  let profile = { ...currentUser }
  let currentPreferences = preferences.map((preference) => ({ ...preference }))
  await page.route('**/api/auth/me', async (route) => {
    if (route.request().method() === 'PATCH') {
      const body = route.request().postDataJSON() as { displayName: string; email?: string }
      profile = { ...profile, displayName: body.displayName, email: body.email }
    }
    await route.fulfill({ json: profile })
  })
  await page.route('**/api/notifications/preferences**', async (route) => {
    if (route.request().method() === 'PUT') {
      const sourceType = new URL(route.request().url()).pathname.split('/').at(-1)
      const body = route.request().postDataJSON() as { enabled: boolean }
      currentPreferences = currentPreferences.map((preference) => preference.sourceType === sourceType ? { ...preference, enabled: body.enabled } : preference)
    }
    await route.fulfill({ json: currentPreferences })
  })
  await page.route('**/api/devices', (route) => route.fulfill({ json: [{ id: 'device-1', deviceType: 'web', deviceName: '浏览器', deviceFingerprint: 'm6', createdAt: '2026-07-13T00:00:00Z', activeSessionCount: 1, enabledPushTokenCount: 0, current: true }] }))

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/settings')
  await expect(page.getByRole('heading', { name: '个人设置' })).toBeVisible()
  await expect(page.getByText('账号：member')).toBeVisible()
  const sidebar = page.getByTestId('user-workspace-sidebar')
  const accountTrigger = page.getByTestId('user-account-menu-trigger')
  await expect(sidebar).toHaveAttribute('data-collapsed', 'false')
  await expect(sidebar).toHaveCSS('width', '108px')
  await expect(sidebar.getByText('Colla', { exact: true })).toHaveCount(0)
  await expect(accountTrigger.getByText('协作成员', { exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '收起侧栏' }).click()
  await expect(sidebar).toHaveAttribute('data-collapsed', 'true')
  await expect(sidebar).toHaveCSS('width', '56px')
  await expect(sidebar.getByText('工作台', { exact: true })).toBeHidden()
  await expect(sidebar.locator('.ant-menu-item .anticon')).toHaveCount(8)

  await page.reload()
  await expect(page.getByTestId('user-workspace-sidebar')).toHaveAttribute('data-collapsed', 'true')
  await page.getByRole('button', { name: '展开侧栏' }).click()
  await expect(page.getByTestId('user-workspace-sidebar')).toHaveAttribute('data-collapsed', 'false')
  await expect(page.getByTestId('user-workspace-sidebar')).toHaveCSS('width', '108px')
  await expect(page.getByTestId('user-workspace-sidebar').getByText('工作台', { exact: true })).toBeVisible()
  const desktopOverflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
  expect(desktopOverflow).toBeLessThanOrEqual(1)

  await page.getByLabel('显示名称').fill('协作成员·已更新')
  await page.getByRole('button', { name: '保存资料' }).click()
  await expect(page.getByText('已保存')).toBeVisible()
  await expect(page.getByTestId('user-settings-page').getByText('协作成员·已更新')).toBeVisible()

  const imPreference = page.getByRole('switch', { name: '消息与提及通知' })
  await imPreference.click()
  await expect(imPreference).not.toBeChecked()
  await expect(page.getByText('可按需关闭，设置立即生效').first()).toBeVisible()

  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByRole('heading', { name: '个人设置' })).toBeVisible()
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
  expect(overflow).toBeLessThanOrEqual(1)

  await page.getByRole('button', { name: '保存资料' }).focus()
  await expect(page.getByRole('button', { name: '保存资料' })).toBeFocused()
})
