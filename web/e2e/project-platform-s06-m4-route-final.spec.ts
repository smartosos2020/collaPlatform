import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

test.describe('PROJECT-PLATFORM-S06 route final', () => {
  test('configuration versions rollback templates offline and identity boundaries @route-final', async ({
    page,
    request,
  }) => {
    test.setTimeout(300_000)
    page.setDefaultTimeout(20_000)
    requireIsolatedIdentityFixture()
    const owner = await loginByApi(request)
    const ownerProfile = await getJson<{ id: string }>(request, `${apiBaseUrl}/auth/me`, owner)
    const suffix = `s06m4_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const spaceAdmin = await createIdentity(request, owner, `${suffix}_admin`, 'S06 M4 空间管理员', 'member')
    const member = await createIdentity(request, owner, `${suffix}_member`, 'S06 M4 成员', 'member')
    const guest = await createIdentity(request, owner, `${suffix}_guest`, 'S06 M4 访客', 'member')
    const outsider = await createIdentity(request, owner, `${suffix}_outsider`, 'S06 M4 非成员', 'member')
    const governor = await createIdentity(request, owner, `${suffix}_governor`, 'S06 M4 企业管理员', 'admin')
    let spaceId: string | undefined

    try {
      spaceId = await createSpace(request, owner, suffix)
      for (const [identity, roleKey] of [[spaceAdmin, 'admin'], [member, 'member'], [guest, 'guest']] as const) {
        const response = await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/members`, {
          headers: { ...bearer(owner), 'X-Colla-Request-Id': `s06-m4-member-${identity.id}` },
          data: { userId: identity.id, roleKey },
        })
        expect(response.ok()).toBeTruthy()
      }
      const typeId = await createType(request, owner, spaceId, suffix)
      await installSession(page, owner)
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto(`/project-spaces/${spaceId}/types/${typeId}`)

      const panel = page.getByRole('region', { name: '配置草稿状态' })
      await expect(panel).toBeVisible()
      await validateAndPublish(page, panel, typeId)
      await expect(panel).toContainText('当前 v2')

      await editTypeDescription(page, 'S06 M4 compatibility review')
      await expect(panel).toContainText('兼容性 需复核')
      await validateAndPublish(page, panel, typeId)
      await expect(panel).toContainText('当前 v3')

      const version2 = panel.getByRole('listitem').filter({ hasText: 'v2' })
      await version2.getByRole('button', { name: '回滚' }).click()
      await page.getByRole('dialog').getByRole('button', { name: '生成回滚草稿' }).click()
      await expect(panel).toContainText('校验通过')
      await publish(page, panel, typeId)
      await expect(panel).toContainText('当前 v4')

      await editTypeDescription(page, 'S06 M4 template draft')
      const templatePanel = page.getByRole('region', { name: '配置模板' })
      await expect(templatePanel.getByLabel('选择配置模板')).toBeVisible()
      const installResponse = page.waitForResponse((response) =>
        response.url().endsWith(`/configuration/types/${typeId}/template-installation`)
          && response.request().method() === 'POST')
      await templatePanel.getByRole('button', { name: '安装' }).click()
      await page.getByRole('dialog').getByRole('button', { name: '安装到草稿' }).click()
      expect((await installResponse).ok()).toBeTruthy()
      await expect(templatePanel).toContainText('已关联')
      const detachResponse = page.waitForResponse((response) =>
        response.url().endsWith(`/configuration/types/${typeId}/template-installation:detach`)
          && response.request().method() === 'POST')
      await templatePanel.getByRole('button', { name: '解绑' }).click()
      await page.getByRole('dialog').getByRole('button', { name: '解除关联' }).click()
      expect((await detachResponse).ok()).toBeTruthy()
      await expect(templatePanel).toContainText('已解绑')

      const versionsUrl = `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}/versions`
      const adminSession = await loginByApi(request, spaceAdmin.username, spaceAdmin.password)
      expect((await request.get(versionsUrl, { headers: bearer(adminSession) })).status()).toBe(200)
      for (const identity of [member, guest]) {
        const session = await loginByApi(request, identity.username, identity.password)
        expect((await request.get(versionsUrl, { headers: bearer(session) })).status()).toBe(403)
      }
      for (const identity of [outsider, governor]) {
        const session = await loginByApi(request, identity.username, identity.password)
        const response = await request.get(versionsUrl, { headers: bearer(session) })
        expect(response.status()).toBe(404)
        expect(JSON.stringify(await response.json())).not.toContain('configHash')
      }

      const editButton = page.locator('.work-item-type-detail-card').getByRole('button', { name: '编辑' })
      await editButton.focus()
      await editButton.press('Enter')
      await expect(page.getByRole('dialog')).toBeVisible()
      await page.keyboard.press('Escape')
      await expect(page.getByRole('dialog')).toBeHidden()

      await page.context().setOffline(true)
      await expect(page.getByText('当前处于离线状态，已打开页面可继续查看，新的保存操作会失败。')).toBeVisible()
      await page.context().setOffline(false)
      await expect(page.getByText('当前处于离线状态，已打开页面可继续查看，新的保存操作会失败。')).toHaveCount(0)

      await page.setViewportSize({ width: 820, height: 900 })
      await expect(panel).toBeVisible()
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

async function editTypeDescription(page: Page, description: string) {
  const editButton = page.locator('.work-item-type-detail-card').getByRole('button', { name: '编辑' })
  await editButton.scrollIntoViewIfNeeded()
  await editButton.click()
  await page.getByLabel('类型说明').fill(description)
  await page.getByRole('dialog').getByRole('button', { name: /保\s*存/, exact: true }).click()
  await expect(page.getByRole('dialog')).toBeHidden()
}

async function validateAndPublish(
  page: Page,
  panel: import('@playwright/test').Locator,
  typeId: string,
) {
  const validation = page.waitForResponse((response) =>
    response.url().endsWith(`/configuration/types/${typeId}/draft:validate`)
      && response.request().method() === 'POST')
  await panel.getByRole('button', { name: '校验配置' }).click()
  expect((await validation).ok()).toBeTruthy()
  await expect(panel).toContainText('校验通过')
  await publish(page, panel, typeId)
}

async function publish(
  page: Page,
  panel: import('@playwright/test').Locator,
  typeId: string,
) {
  const publication = page.waitForResponse((response) =>
    response.url().endsWith(`/configuration/types/${typeId}/draft:publish`)
      && response.request().method() === 'POST')
  await panel.getByRole('button', { name: '发布版本' }).click()
  await page.getByRole('dialog').getByRole('button', { name: /^(确认并发布|发布版本)$/ }).click()
  expect((await publication).ok()).toBeTruthy()
}

async function createSpace(request: APIRequestContext, session: E2eSession, suffix: string) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(session),
    data: {
      spaceKey: `s06-m4-${suffix.replaceAll('_', '-')}`,
      name: `S06 M4 Route Final ${suffix}`,
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
  const response = await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`, {
    headers: { ...bearer(session), 'X-Colla-Request-Id': `s06-m4-type-${suffix}` },
    data: {
      typeKey: `${suffix}_delivery`,
      name: 'S06 Route Final',
      description: 'S06 M4 real isolated acceptance',
      sortOrder: 10,
    },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json() as { id: string }).id
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
