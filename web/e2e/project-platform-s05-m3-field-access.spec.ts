import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

test.describe('PROJECT-PLATFORM-S05-M3 field access policy', () => {
  test('manager narrows a field and server projections prevent member and enterprise-admin disclosure @smoke', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(240_000)
    requireIsolatedIdentityFixture()
    const suffix = `s05m3_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const enterpriseAdmin = await loginByApi(request)
    const ownerIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_owner`, 'S05 M3 Owner')
    const memberIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_member`, 'S05 M3 Member')
    const owner = await loginByApi(request, ownerIdentity.username, ownerIdentity.password)
    const member = await loginByApi(request, memberIdentity.username, memberIdentity.password)
    let spaceId: string | undefined

    try {
      spaceId = await createSpace(request, owner, suffix)
      await addMember(request, owner, spaceId, memberIdentity.id, 'member')
      const typeId = await createType(request, owner, spaceId, `${suffix}_delivery`)
      const title = await createField(request, owner, spaceId, typeId, 'title', '标题', 10)
      const secret = await createField(request, owner, spaceId, typeId, 'security_note', '安全备注', 20)
      await createLayout(request, owner, spaceId, typeId, title, secret)

      await installSession(page, owner)
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto(`/project-spaces/${spaceId}/types/${typeId}/layouts`)
      await expect(page.getByTestId('work-item-layout-policy-editor')).toBeVisible()
      await page.getByLabel('策略字段').click()
      await page.getByText('安全备注 · security_note', { exact: true }).click()
      await page.getByRole('combobox', { name: '成员访问模式', exact: true }).click()
      await page.getByText('隐藏', { exact: true }).last().click()

      const saveResponse = page.waitForResponse((response) =>
        response.url().endsWith(`/layouts/create/policies`)
          && response.request().method() === 'PUT')
      await page.getByRole('button', { name: '保存策略' }).click()
      await page.getByRole('button', { name: '确认保存' }).click()
      expect((await saveResponse).ok()).toBeTruthy()
      await expect(page.getByText('字段访问策略已保存')).toBeVisible()

      const previewResponse = page.waitForResponse((response) =>
        response.url().endsWith('/layouts/create/preview')
          && response.request().method() === 'POST')
      await page.getByRole('button', { name: '运行预览' }).click()
      expect((await previewResponse).ok()).toBeTruthy()
      await expect(page.getByTestId('work-item-layout-renderer')).toContainText('标题')
      await expect(page.getByTestId('work-item-layout-renderer')).not.toContainText('安全备注')
      await expect(page.getByText('合成预览')).toBeVisible()
      const pageOverflow = await page.evaluate(() =>
        document.documentElement.scrollWidth - document.documentElement.clientWidth)
      expect(pageOverflow).toBeLessThanOrEqual(1)
      await page.screenshot({
        path: testInfo.outputPath('field-access-policy-preview.png'),
        fullPage: true,
      })

      const memberProjection = await request.get(
        `${apiBaseUrl}/project-spaces/${spaceId}/types/${typeId}/layouts/create/projection`,
        { headers: bearer(member) },
      )
      expect(memberProjection.ok()).toBeTruthy()
      const memberBody = await memberProjection.text()
      expect(memberBody).toContain('"title"')
      expect(memberBody).not.toContain('security_note')
      expect(memberBody).not.toContain('role_member_access')

      const enterpriseProjection = await request.get(
        `${apiBaseUrl}/project-spaces/${spaceId}/types/${typeId}/layouts/create/projection`,
        { headers: bearer(enterpriseAdmin) },
      )
      expect(enterpriseProjection.status()).toBe(404)

      const forgedWrite = await request.put(
        `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}/layouts/create/policies`,
        {
          headers: { ...bearer(member), 'X-Colla-Request-Id': `s05-m3-forged-${suffix}` },
          data: { policies: [], aggregateVersion: 1 },
        },
      )
      expect(forgedWrite.status()).toBe(403)
    } finally {
      if (spaceId) {
        await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`, {
          headers: bearer(owner),
        }).catch(() => undefined)
      }
      for (const user of [memberIdentity, ownerIdentity]) {
        await request.post(`${apiBaseUrl}/admin/users/${user.id}/offboard`, {
          headers: bearer(enterpriseAdmin),
          data: { handoverToUserId: (await profile(request, enterpriseAdmin)).id },
        }).catch(() => undefined)
      }
    }
  })
})

type Identity = { id: string; username: string; password: string }
type Field = { id: string; fieldKey: string }

async function profile(request: APIRequestContext, session: E2eSession) {
  const response = await request.get(`${apiBaseUrl}/auth/me`, { headers: bearer(session) })
  expect(response.ok()).toBeTruthy()
  return await response.json() as { id: string }
}

async function createIdentity(
  request: APIRequestContext,
  administrator: E2eSession,
  username: string,
  displayName: string,
): Promise<Identity> {
  const password = 'member123456'
  const response = await request.post(`${apiBaseUrl}/admin/users`, {
    headers: bearer(administrator),
    data: {
      username,
      password,
      displayName,
      email: `${username}@example.com`,
      roleCode: 'member',
    },
  })
  expect(response.ok()).toBeTruthy()
  return { ...(await response.json() as Omit<Identity, 'password'>), password }
}

async function createSpace(request: APIRequestContext, owner: E2eSession, suffix: string) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(owner),
    data: {
      spaceKey: `s05-m3-${suffix.replaceAll('_', '-')}`,
      name: `S05 M3 权限空间 ${suffix}`,
      visibility: 'private',
    },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json() as { id: string }).id
}

async function addMember(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  userId: string,
  roleKey: string,
) {
  const response = await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/members`, {
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s05-m3-member-${userId}` },
    data: { userId, roleKey },
  })
  expect(response.ok()).toBeTruthy()
}

async function createType(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  typeKey: string,
) {
  const response = await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`, {
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s05-m3-type-${typeKey}` },
    data: { typeKey, name: '交付事项', sortOrder: 10 },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json() as { id: string }).id
}

async function createField(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  typeId: string,
  fieldKey: string,
  name: string,
  sortOrder: number,
): Promise<Field> {
  const response = await request.post(
    `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}/fields`,
    {
      headers: { ...bearer(owner), 'X-Colla-Request-Id': `s05-m3-field-${fieldKey}-${typeId}` },
      data: { fieldKey, name, fieldType: 'text', sortOrder, config: {} },
    },
  )
  expect(response.ok()).toBeTruthy()
  return await response.json() as Field
}

async function createLayout(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  typeId: string,
  title: Field,
  secret: Field,
) {
  const sectionId = crypto.randomUUID()
  const response = await request.put(
    `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}/layouts/create`,
    {
      headers: { ...bearer(owner), 'X-Colla-Request-Id': `s05-m3-layout-${typeId}` },
      data: {
        nodes: [
          {
            id: sectionId,
            parentId: null,
            nodeKey: 'main',
            nodeType: 'section',
            fieldId: null,
            fieldKey: null,
            sortOrder: 0,
            config: { title: '新建工作项' },
            visibilityCondition: { schemaVersion: 1 },
          },
          ...[title, secret].map((field, index) => ({
            id: crypto.randomUUID(),
            parentId: sectionId,
            nodeKey: `field_${field.fieldKey}`,
            nodeType: 'field',
            fieldId: field.id,
            fieldKey: field.fieldKey,
            sortOrder: index,
            config: { title: field.fieldKey },
            visibilityCondition: { schemaVersion: 1 },
          })),
        ],
        policies: [],
        aggregateVersion: 0,
      },
    },
  )
  expect(response.ok()).toBeTruthy()
}
