import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import {
  archiveKnowledgeSpaceFixture,
  createKnowledgeSpaceFixture,
  requireIsolatedIdentityFixture,
  uniqueFixtureName,
} from './support/fixtures'
import { createKnowledgeItem, knowledgeContentUrl } from './support/knowledge'

test('@route-final S03 M4 notification projection reaches the recipient inbox', async ({ page, request }) => {
  requireIsolatedIdentityFixture()
  const administrator = await loginByApi(request)
  const suffix = uniqueFixtureName('s03-m4-notification').toLowerCase().replace(/[^a-z0-9]/g, '').slice(-18)
  const department = await createDepartment(request, administrator, `dept${suffix}`, `S03 M4 Department ${suffix}`)
  const member = await createMember(request, administrator, `member${suffix}`, department.id)
  const memberSession = await loginByApi(request, member.username, member.password)
  const space = await createKnowledgeSpaceFixture(request, administrator, 's03-m4-notification')

  try {
    const content = await createKnowledgeItem(request, administrator, space, {
      title: `S03 M4 Notification ${suffix}`,
      contentType: 'markdown',
      content: 'Notification projection browser verification.',
    })
    await grantResource(request, administrator, 'knowledge_base', space.id, 'user', member.id, 'view')
    await grantResource(request, administrator, 'knowledge_content', content.item.id, 'user', member.id, 'view')

    await expect.poll(async () => {
      const response = await request.get(
        `${apiBaseUrl}/notifications?targetType=knowledge_content`,
        { headers: bearer(memberSession) },
      )
      if (!response.ok()) return false
      const notifications = await response.json() as Array<{ notificationType: string; targetId?: string }>
      return notifications.some(
        (item) => item.notificationType === 'resource_permission_granted' && item.targetId === content.item.id,
      )
    }, { timeout: 20_000, message: 'permission grant should reach the member notification inbox' }).toBeTruthy()

    await installSession(page, memberSession)
    await page.goto('/notifications')
    await expect(page.getByText('你获得了资源访问权限').first()).toBeVisible()
    await page.goto(knowledgeContentUrl(space.id, content.item.id))
    await expect(page.getByText(content.item.title).first()).toBeVisible()
    await expect(page.getByText('当前为只读模式')).toBeVisible()
  } finally {
    await archiveKnowledgeSpaceFixture(request, administrator, space)
  }
})

async function createDepartment(request: APIRequestContext, session: E2eSession, code: string, name: string) {
  const response = await request.post(`${apiBaseUrl}/admin/departments`, {
    headers: bearer(session),
    data: { code, name, sortOrder: 0 },
  })
  expect(response.ok()).toBeTruthy()
  return await response.json() as { id: string }
}

async function createMember(
  request: APIRequestContext,
  session: E2eSession,
  username: string,
  primaryDepartmentId: string,
) {
  const password = ['member', '123456'].join('')
  const response = await request.post(`${apiBaseUrl}/admin/users`, {
    headers: bearer(session),
    data: {
      username,
      password,
      displayName: `S03 M4 Member ${username.slice(-8)}`,
      email: `${username}@colla.local`,
      roleCode: 'member',
      primaryDepartmentId,
    },
  })
  expect(response.ok()).toBeTruthy()
  const payload = await response.json() as { id: string }
  return { ...payload, username, password }
}

async function grantResource(
  request: APIRequestContext,
  session: E2eSession,
  resourceType: string,
  resourceId: string,
  subjectType: string,
  subjectId: string,
  permissionLevel: string,
) {
  const response = await request.post(`${apiBaseUrl}/resource-permissions/${resourceType}/${resourceId}`, {
    headers: bearer(session),
    data: { subjectType, subjectId, permissionLevel, confirmHighRisk: false },
  })
  expect(response.ok(), `grant ${permissionLevel} on ${resourceType} failed`).toBeTruthy()
}
