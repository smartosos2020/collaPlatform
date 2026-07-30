import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

test.describe('PROJECT-PLATFORM-S06-M3 configuration templates', () => {
  test('real catalog install detach concurrency and six-identity boundaries @smoke', async ({
    page,
    request,
  }) => {
    test.setTimeout(240_000)
    page.setDefaultTimeout(20_000)
    requireIsolatedIdentityFixture()
    const owner = await loginByApi(request)
    const ownerProfile = await getJson<{ id: string }>(request, `${apiBaseUrl}/auth/me`, owner)
    const suffix = `s06m3_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const spaceAdmin = await createIdentity(request, owner, `${suffix}_admin`, 'S06 M3 空间管理员', 'member')
    const member = await createIdentity(request, owner, `${suffix}_member`, 'S06 M3 成员', 'member')
    const guest = await createIdentity(request, owner, `${suffix}_guest`, 'S06 M3 访客', 'member')
    const outsider = await createIdentity(request, owner, `${suffix}_outsider`, 'S06 M3 非成员', 'member')
    const governor = await createIdentity(request, owner, `${suffix}_governor`, 'S06 M3 企业管理员', 'admin')
    let spaceId: string | undefined

    try {
      spaceId = await createSpace(request, owner, suffix)
      for (const [identity, roleKey] of [[spaceAdmin, 'admin'], [member, 'member'], [guest, 'guest']] as const) {
        const response = await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/members`, {
          headers: { ...bearer(owner), 'X-Colla-Request-Id': `s06-m3-member-${identity.id}` },
          data: { userId: identity.id, roleKey },
        })
        expect(response.ok()).toBeTruthy()
      }
      const typeId = await createType(request, owner, spaceId, suffix)
      await installSession(page, owner)
      await page.setViewportSize({ width: 1366, height: 768 })
      await page.goto(`/project-spaces/${spaceId}/types/${typeId}`)
      await page.getByTestId('project-space-types-secondary-tabs')
        .getByRole('tab', { name: '配置发布', exact: true }).click()

      const draftPanel = page.getByRole('region', { name: '配置草稿状态' })
      await expect(draftPanel).toBeVisible()
      await validateAndPublish(page, draftPanel, typeId)
      const templatePanel = page.getByRole('region', { name: '配置模板' })
      await expect(templatePanel).toBeVisible()
      await expect(templatePanel.getByLabel('选择配置模板')).toBeVisible()

      const installResponse = page.waitForResponse((response) =>
        response.url().endsWith(`/configuration/types/${typeId}/template-installation`)
          && response.request().method() === 'POST')
      await templatePanel.getByRole('button', { name: '安装' }).click()
      await page.getByRole('dialog').getByRole('button', { name: '安装到草稿' }).click()
      expect((await installResponse).ok()).toBeTruthy()
      await expect(templatePanel).toContainText('已关联')
      const installed = await getInstallation(request, owner, spaceId, typeId)
      const installedDraft = await getDraft(request, owner, spaceId, typeId)

      const detachResponse = page.waitForResponse((response) =>
        response.url().endsWith(`/configuration/types/${typeId}/template-installation:detach`)
          && response.request().method() === 'POST')
      await templatePanel.getByRole('button', { name: '解绑' }).click()
      await page.getByRole('dialog').getByRole('button', { name: '解除关联' }).click()
      expect((await detachResponse).ok()).toBeTruthy()
      await expect(templatePanel).toContainText('已解绑')
      expect((await getDraft(request, owner, spaceId, typeId)).configHash).toBe(installedDraft.configHash)

      const catalogUrl = `${apiBaseUrl}/project-spaces/${spaceId}/configuration/templates`
      const templates = await getJson<Array<{
        id: string
        status: string
        currentVersion: { id: string }
      }>>(request, catalogUrl, owner)
      const platformTemplate = templates.find((template) => template.status === 'active')
      expect(platformTemplate).toBeTruthy()
      const currentDraft = await getDraft(request, owner, spaceId, typeId)
      const concurrent = await Promise.all([
        installByApi(request, owner, spaceId, typeId, platformTemplate!, currentDraft.aggregateVersion, `s06-m3-race-a-${suffix}`),
        installByApi(request, owner, spaceId, typeId, platformTemplate!, currentDraft.aggregateVersion, `s06-m3-race-b-${suffix}`),
      ])
      expect(concurrent.map((response) => response.status()).sort()).toEqual([200, 409])
      expect((await getInstallation(request, owner, spaceId, typeId)).status).toBe('attached')

      const adminSession = await loginByApi(request, spaceAdmin.username, spaceAdmin.password)
      expect((await request.get(catalogUrl, { headers: bearer(adminSession) })).status()).toBe(200)
      for (const identity of [member, guest]) {
        const session = await loginByApi(request, identity.username, identity.password)
        expect((await request.get(catalogUrl, { headers: bearer(session) })).status()).toBe(403)
      }
      for (const identity of [outsider, governor]) {
        const session = await loginByApi(request, identity.username, identity.password)
        expect((await request.get(catalogUrl, { headers: bearer(session) })).status()).toBe(404)
      }

      expect(installed.templateId).toBeTruthy()
      expect(await page.evaluate(() =>
        document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1)
    } finally {
      if (spaceId) {
        await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`, {
          headers: bearer(owner),
        }).catch(() => undefined)
      }
      for (const identity of [spaceAdmin, member, guest, outsider, governor]) {
        await request.post(`${apiBaseUrl}/admin/users/${identity.id}/offboard`, {
          headers: bearer(owner),
          data: { handoverToUserId: ownerProfile.id },
        }).catch(() => undefined)
      }
    }
  })
})

async function validateAndPublish(
  page: import('@playwright/test').Page,
  panel: import('@playwright/test').Locator,
  typeId: string,
) {
  const validation = page.waitForResponse((response) =>
    response.url().endsWith(`/configuration/types/${typeId}/draft:validate`)
      && response.request().method() === 'POST')
  await panel.getByRole('button', { name: '校验配置' }).click()
  expect((await validation).ok()).toBeTruthy()
  const publication = page.waitForResponse((response) =>
    response.url().endsWith(`/configuration/types/${typeId}/draft:publish`)
      && response.request().method() === 'POST')
  await panel.getByRole('button', { name: '发布版本' }).click()
  await page.getByRole('dialog').getByRole('button', { name: /^(确认并发布|发布版本)$/ }).click()
  expect((await publication).ok()).toBeTruthy()
  await expect(panel).toContainText('当前 v2')
}

async function createSpace(request: APIRequestContext, session: E2eSession, suffix: string) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(session),
    data: {
      spaceKey: `s06-m3-${suffix.replaceAll('_', '-')}`,
      name: `S06 M3 模板空间 ${suffix}`,
      visibility: 'private',
    },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json() as { id: string }).id
}

async function createType(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  suffix: string,
) {
  const response = await request.post(
    `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`,
    {
      headers: {
        ...bearer(session),
        'X-Colla-Request-Id': `s06-m3-type-${suffix}`,
      },
      data: {
        typeKey: `${suffix}_delivery`,
        name: 'S06 模板配置',
        description: 'S06 M3 real browser acceptance',
        sortOrder: 10,
      },
    },
  )
  expect(response.ok()).toBeTruthy()
  return (await response.json() as { id: string }).id
}

async function installByApi(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  typeId: string,
  template: { id: string; currentVersion: { id: string } },
  expectedDraftAggregateVersion: number,
  requestId: string,
) {
  return request.post(
    `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}/template-installation`,
    {
      headers: { ...bearer(session), 'X-Colla-Request-Id': requestId },
      data: {
        templateId: template.id,
        templateVersionId: template.currentVersion.id,
        expectedDraftAggregateVersion,
      },
    },
  )
}

async function getDraft(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  typeId: string,
) {
  return getJson<{ aggregateVersion: number; configHash: string }>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}/draft`,
    session,
  )
}

async function getInstallation(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  typeId: string,
) {
  return getJson<{ templateId: string; status: string; aggregateVersion: number }>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}/template-installation`,
    session,
  )
}

async function createIdentity(
  request: APIRequestContext,
  administrator: E2eSession,
  username: string,
  displayName: string,
  roleCode: 'member' | 'admin',
) {
  const password = 'member123456'
  const response = await request.post(`${apiBaseUrl}/admin/users`, {
    headers: bearer(administrator),
    data: { username, password, displayName, email: `${username}@example.com`, roleCode },
  })
  expect(response.ok()).toBeTruthy()
  const payload = await response.json() as { id: string; username: string; displayName: string }
  return { ...payload, password }
}

async function getJson<T>(request: APIRequestContext, url: string, session: E2eSession) {
  const response = await request.get(url, { headers: bearer(session) })
  expect(response.ok(), `GET ${url} failed`).toBeTruthy()
  return await response.json() as T
}
