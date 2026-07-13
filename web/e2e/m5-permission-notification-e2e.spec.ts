import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession, webBaseUrl } from './support/api'
import { archiveKnowledgeSpaceFixture, createKnowledgeSpaceFixture, requireIsolatedIdentityFixture, uniqueFixtureName } from './support/fixtures'
import { createKnowledgeItem, knowledgeContentUrl } from './support/knowledge'

test('M5 member receives an explainable grant and admin diagnoses then remediates its expired risk @route-final', async ({ browser, page, request }) => {
  requireIsolatedIdentityFixture()
  const administrator = await loginByApi(request)
  const suffix = uniqueFixtureName('m5-permission').toLowerCase().replace(/[^a-z0-9]/g, '').slice(-18)
  const department = await createDepartment(request, administrator, `dept${suffix}`, `M5 Department ${suffix}`)
  const member = await createMember(request, administrator, `member${suffix}`, department.id)
  const memberSession = await loginByApi(request, member.username, member.password)
  const space = await createKnowledgeSpaceFixture(request, administrator, 'm5-permission-notification')
  const memberContext = await browser.newContext({ baseURL: webBaseUrl })
  const memberPage = await memberContext.newPage()

  try {
    const content = await createKnowledgeItem(request, administrator, space, {
      title: `M5 Explainable Permission ${suffix}`,
      contentType: 'markdown',
      content: 'Member-visible, read-only permission verification.',
    })
    await grantResource(request, administrator, 'knowledge_base', space.id, 'user', member.id, 'view')
    await grantResource(request, administrator, 'knowledge_content', content.item.id, 'user', member.id, 'view')
    await grantResource(
      request, administrator, 'knowledge_content', content.item.id, 'department', department.id, 'view',
      new Date(Date.now() - 60_000).toISOString(),
    )

    await expect.poll(async () => {
      const response = await request.get(`${apiBaseUrl}/notifications?targetType=knowledge_content`, { headers: bearer(memberSession) })
      if (!response.ok()) return false
      const notifications = await response.json() as Array<{ notificationType: string; targetId?: string }>
      return notifications.some((item) => item.notificationType === 'resource_permission_granted' && item.targetId === content.item.id)
    }, { timeout: 20_000, message: 'permission grant should reach the member notification inbox' }).toBeTruthy()

    await installSession(memberPage, memberSession)
    await memberPage.goto('/notifications')
    await expect(memberPage.getByText('你获得了资源访问权限').first()).toBeVisible()
    await memberPage.goto(knowledgeContentUrl(space.id, content.item.id))
    await expect(memberPage.getByText(content.item.title).first()).toBeVisible()
    await expect(memberPage.getByText('当前为只读模式')).toBeVisible()
    const forbiddenWrite = await request.patch(`${apiBaseUrl}/knowledge-bases/${space.id}/items/${content.item.id}`, {
      headers: bearer(memberSession),
      data: { baseVersionNo: content.item.currentVersionNo, title: 'Forbidden overwrite', content: 'Forbidden overwrite' },
    })
    expect(forbiddenWrite.status()).toBe(403)

    await installSession(page, administrator)
    await page.goto('/admin/permission-governance')
    await page.locator('.permission-governance-user').click()
    await page.getByText(`${member.displayName} (${member.username})`, { exact: true }).click()
    await page.locator('.permission-governance-resource-id').fill(content.item.id)
    await page.locator('.permission-governance-action').click()
    await page.getByText('edit', { exact: true }).last().click()
    await page.getByRole('button', { name: /排查/ }).click()
    await expect(page.getByText('不允许访问')).toBeVisible()
    await expect(page.getByText(/当前 view，需要 edit/)).toBeVisible()

    const riskRow = page.getByRole('row').filter({ hasText: 'expired_active_permission' }).filter({ hasText: content.item.id })
    await expect(riskRow).toBeVisible()
    await riskRow.getByRole('button', { name: /处\s*置/ }).click()
    await expect(page.locator('.ant-modal-confirm-title', { hasText: '确认单项修复权限风险？' })).toBeVisible()
    await page.getByRole('button', { name: /确认\s*修复/ }).click()
    await expect(page.getByText('风险授权已撤销并写入审计。')).toBeVisible()

    const audit = await request.get(
      `${apiBaseUrl}/admin/audit-logs?action=permission.risk.remediated&targetType=knowledge_content&targetId=${content.item.id}`,
      { headers: bearer(administrator) },
    )
    expect(audit.ok()).toBeTruthy()
    const auditItems = await audit.json() as Array<{ action: string }>
    expect(auditItems.some((item) => item.action === 'permission.risk.remediated')).toBeTruthy()
  } finally {
    await memberContext.close()
    await archiveKnowledgeSpaceFixture(request, administrator, space)
  }
})

async function createDepartment(request: APIRequestContext, session: E2eSession, code: string, name: string) {
  const response = await request.post(`${apiBaseUrl}/admin/departments`, {
    headers: bearer(session), data: { code, name, sortOrder: 0 },
  })
  expect(response.ok()).toBeTruthy()
  return await response.json() as { id: string }
}

async function createMember(request: APIRequestContext, session: E2eSession, username: string, primaryDepartmentId: string) {
  const password = ['member', '123456'].join('')
  const displayName = `M5 Member ${username.slice(-8)}`
  const response = await request.post(`${apiBaseUrl}/admin/users`, {
    headers: bearer(session),
    data: { username, password, displayName, email: `${username}@colla.local`, roleCode: 'member', primaryDepartmentId },
  })
  expect(response.ok()).toBeTruthy()
  const payload = await response.json() as { id: string }
  return { ...payload, username, password, displayName }
}

async function grantResource(
  request: APIRequestContext,
  session: E2eSession,
  resourceType: string,
  resourceId: string,
  subjectType: string,
  subjectId: string,
  permissionLevel: string,
  expiresAt?: string,
) {
  const response = await request.post(`${apiBaseUrl}/resource-permissions/${resourceType}/${resourceId}`, {
    headers: bearer(session),
    data: { subjectType, subjectId, permissionLevel, expiresAt, confirmHighRisk: false },
  })
  expect(response.ok(), `grant ${permissionLevel} on ${resourceType} failed`).toBeTruthy()
}
