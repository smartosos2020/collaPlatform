import { expect, test } from '@playwright/test'

import { installSession, loginByApi } from './support/api'

test('M5 admin can preview and confirm a permission-risk remediation @route-final', async ({ page, request }) => {
  const session = await loginByApi(request)
  await installSession(page, session)

  let previewCalls = 0
  let confirmCalls = 0
  await page.route('**/api/admin/permission-governance/risks**', async (route) => {
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/remediation')) {
      const confirmed = url.searchParams.get('confirm') === 'true'
      if (confirmed) {
        confirmCalls += 1
      } else {
        previewCalls += 1
      }
      await route.fulfill({
        json: {
          riskId: '11111111-1111-1111-1111-111111111111',
          ruleCode: 'expired_active_permission',
          executable: true,
          applied: confirmed,
          action: 'revoke_permission',
          reason: confirmed ? '风险授权已撤销并写入审计。' : '确认后仅撤销这一条风险授权。',
        },
      })
      return
    }
    await route.fulfill({
      json: {
        total: 1,
        severityBuckets: { medium: 1 },
        items: [{
          id: '11111111-1111-1111-1111-111111111111',
          ruleCode: 'expired_active_permission',
          severity: 'medium',
          resourceType: 'base',
          resourceId: '22222222-2222-2222-2222-222222222222',
          subjectType: 'department',
          subjectId: '33333333-3333-3333-3333-333333333333',
          subjectName: 'M5 验证部门',
          permissionLevel: 'view',
          reason: '授权已过期但记录仍处于 active 状态。',
        }],
      },
    })
  })

  await page.goto('/admin/permission-governance')
  await expect(page.getByText('expired_active_permission')).toBeVisible()
  await page.getByRole('button', { name: /处\s*置/ }).click()
  await expect(page.locator('.ant-modal-confirm-title', { hasText: '确认单项修复权限风险？' })).toBeVisible()
  await page.getByRole('button', { name: /确认\s*修复/ }).click()
  await expect(page.getByText('风险授权已撤销并写入审计。')).toBeVisible()
  expect(previewCalls).toBe(1)
  expect(confirmCalls).toBe(1)
})

test('M5 admin global search follows governance deep link and preserves query @route-final', async ({ page, request }) => {
  const session = await loginByApi(request)
  await installSession(page, session)
  await page.route('**/api/admin/search-governance**', (route) => route.fulfill({
    json: {
      query: '权限风险',
      searchScope: 'admin_governance',
      items: [{
        governanceType: 'permission_risk',
        title: '权限风险处置',
        description: '检索并处置权限风险。',
        adminPath: '/admin/permission-governance?severity=high',
        riskLevel: 'critical',
      }],
    },
  }))

  await page.goto('/admin/overview')
  const search = page.getByPlaceholder('后台治理搜索：成员、部门、角色、审计动作')
  await search.fill('权限风险')
  await search.press('Enter')
  await expect(page).toHaveURL(/\/admin\/permission-governance\?severity=high&q=%E6%9D%83%E9%99%90%E9%A3%8E%E9%99%A9/)
  await expect(page.getByPlaceholder('规则、资源、授权主体')).toHaveValue('权限风险')
})
