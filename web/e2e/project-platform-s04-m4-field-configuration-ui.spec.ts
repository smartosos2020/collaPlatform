import { expect, test, type APIRequestContext, type BrowserContext, type Page } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

test.describe('PROJECT-PLATFORM-S04-M4 field configuration UI', () => {
  test('six isolated identities close the field configuration workflow @smoke', async ({ browser, page, request }) => {
    test.setTimeout(240_000)
    page.setDefaultTimeout(15_000)
    requireIsolatedIdentityFixture()
    const suffix = `s04m4_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const owner = await loginByApi(request)
    const ownerProfile = await getJson<{ id: string }>(request, `${apiBaseUrl}/auth/me`, owner)
    const spaceAdmin = await createIdentity(request, owner, `${suffix}_admin`, 'S04 M4 空间管理员', 'member')
    const member = await createIdentity(request, owner, `${suffix}_member`, 'S04 M4 成员', 'member')
    const guest = await createIdentity(request, owner, `${suffix}_guest`, 'S04 M4 访客', 'member')
    const outsider = await createIdentity(request, owner, `${suffix}_outsider`, 'S04 M4 非成员', 'member')
    const governor = await createIdentity(request, owner, `${suffix}_governor`, 'S04 M4 企业管理员', 'admin')
    const contexts: BrowserContext[] = []
    let spaceId: string | undefined

    try {
      const spaceResponse = await request.post(`${apiBaseUrl}/project-spaces`, {
        headers: bearer(owner),
        data: {
          spaceKey: `s04-m4-${suffix.replaceAll('_', '-')}`,
          name: `S04 M4 字段配置空间 ${suffix}`,
          visibility: 'private',
        },
      })
      expect(spaceResponse.ok()).toBeTruthy()
      spaceId = (await spaceResponse.json() as { id: string }).id

      for (const [identity, roleKey] of [[spaceAdmin, 'admin'], [member, 'member'], [guest, 'guest']] as const) {
        const response = await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/members`, {
          headers: { ...bearer(owner), 'X-Colla-Request-Id': `s04-m4-member-${identity.id}` },
          data: { userId: identity.id, roleKey },
        })
        expect(response.ok(), `failed to add ${roleKey}`).toBeTruthy()
      }

      const deliveryTypeId = await createType(request, owner, spaceId, `${suffix}_delivery`, '交付事项')
      const emptyTypeId = await createType(request, owner, spaceId, `${suffix}_empty`, '空字段类型')
      const fieldsUrl = `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${deliveryTypeId}/fields`

      await installSession(page, owner)
      await page.goto(`/project-spaces/${spaceId}/types/${deliveryTypeId}/fields`)
      await expect(page.getByTestId('work-item-fields-panel')).toBeVisible()
      await expect(page.getByText('当前类型还没有字段')).toBeVisible()

      const priorityFieldId = await createFieldInUi(page, {
        typeLabel: '单选',
        fieldKey: 'priority',
        name: '优先级',
        description: '项目优先级选项',
      })
      await expect(page).toHaveURL(new RegExp(`/types/${deliveryTypeId}/fields/${priorityFieldId}$`))

      await page.getByRole('button', { name: /配置$/ }).click()
      const drawer = page.getByRole('dialog', { name: /配置字段/ })
      await drawer.getByRole('button', { name: '添加选项' }).click()
      await drawer.getByLabel('选项 1 键').fill('high')
      await drawer.getByLabel('选项 1 名称').fill('高')
      await drawer.getByRole('button', { name: '添加选项' }).click()
      await drawer.getByLabel('选项 2 键').fill('normal')
      await drawer.getByLabel('选项 2 名称').fill('普通')
      const configurePriority = page.waitForResponse((response) =>
        response.url().endsWith(`/fields/${priorityFieldId}/configuration`)
          && response.request().method() === 'PUT')
      await drawer.getByRole('button', { name: '保存配置' }).click()
      expect((await configurePriority).ok()).toBeTruthy()
      await expect(page.getByText('字段配置已保存')).toBeVisible()
      await expect(page.getByLabel('字段配置摘要')).toContainText('2 项')

      await page.getByRole('button', { name: /新建字段$/ }).click()
      const createDialog = page.getByRole('dialog', { name: '新建字段' })
      await createDialog.getByRole('radio', { name: /文本/ }).click()
      await createDialog.getByLabel('字段键', { exact: true }).fill('summary')
      await createDialog.getByLabel('显示名称').fill('摘要')
      await createDialog.getByLabel('字段说明').fill('用于验证文本默认值和长度规则')
      const createSummary = page.waitForResponse((response) =>
        response.url().endsWith(`/types/${deliveryTypeId}/fields`)
          && response.request().method() === 'POST')
      await createDialog.getByRole('button', { name: '创建字段' }).click()
      const createSummaryResponse = await createSummary
      expect(createSummaryResponse.ok()).toBeTruthy()
      const summaryFieldId = (await createSummaryResponse.json() as { id: string }).id
      await expect(page).toHaveURL(new RegExp(`/types/${deliveryTypeId}/fields/${summaryFieldId}$`))

      await page.getByRole('button', { name: /配置$/ }).click()
      const summaryDrawer = page.getByRole('dialog', { name: /配置字段/ })
      await summaryDrawer.getByLabel('默认值').fill('项目摘要')
      await summaryDrawer.getByRole('button', { name: '添加规则' }).click()
      await summaryDrawer.getByLabel('最小长度').fill('2')
      await summaryDrawer.getByLabel('最大长度').fill('80')
      const configureSummary = page.waitForResponse((response) =>
        response.url().endsWith(`/fields/${summaryFieldId}/configuration`)
          && response.request().method() === 'PUT')
      await summaryDrawer.getByRole('button', { name: '保存配置' }).click()
      expect((await configureSummary).ok()).toBeTruthy()
      await expect(page.getByLabel('字段配置摘要')).toContainText('1 条')

      await page.reload()
      await expect(page.getByRole('heading', { name: '摘要' })).toBeVisible()
      await page.getByLabel('搜索字段名称或字段键').fill('priority')
      await expect(page.getByRole('option', { name: /priority/ })).toBeVisible()
      await expect(page.getByRole('option', { name: /summary/ })).toHaveCount(0)
      await page.getByLabel('搜索字段名称或字段键').clear()

      await page.getByRole('option', { name: /summary/ }).click()
      const originalKeys = await visibleFieldKeys(page)
      const reorderResponse = page.waitForResponse((response) =>
        response.url().endsWith(`/types/${deliveryTypeId}/fields:reorder`)
          && response.request().method() === 'PUT')
      await page.getByRole('button', { name: '上移 摘要' }).click()
      expect((await reorderResponse).ok()).toBeTruthy()
      await expect.poll(() => visibleFieldKeys(page)).not.toEqual(originalKeys)

      await page.getByRole('button', { name: '停用' }).click()
      const disableResponse = page.waitForResponse((response) =>
        response.url().endsWith(`/fields/${summaryFieldId}:disable`)
          && response.request().method() === 'POST')
      await page.getByRole('dialog').getByRole('button', { name: '确认停用' }).click()
      expect((await disableResponse).ok()).toBeTruthy()
      await expect(page.getByRole('button', { name: '恢复' })).toBeVisible()
      await page.getByRole('button', { name: '恢复' }).click()
      const restoreResponse = page.waitForResponse((response) =>
        response.url().endsWith(`/fields/${summaryFieldId}:restore`)
          && response.request().method() === 'POST')
      await page.getByRole('dialog').getByRole('button', { name: '确认恢复' }).click()
      expect((await restoreResponse).ok()).toBeTruthy()

      const ownerConfiguration = await getJson<FieldCollection>(request, fieldsUrl, owner)
      expect(ownerConfiguration.availableActions).toContain('create')
      expect(ownerConfiguration.items).toEqual(expect.arrayContaining([
        expect.objectContaining({ id: priorityFieldId, fieldKey: 'priority', options: expect.arrayContaining([
          expect.objectContaining({ optionKey: 'high' }),
          expect.objectContaining({ optionKey: 'normal' }),
        ]) }),
        expect.objectContaining({
          id: summaryFieldId,
          fieldKey: 'summary',
          config: expect.objectContaining({
            defaultValue: '项目摘要',
            validationRules: [expect.objectContaining({
              kind: 'length',
              config: { min: 2, max: 80 },
            })],
          }),
        }),
      ]))

      await page.goto(`/project-spaces/${spaceId}/types/${emptyTypeId}/fields`)
      await expect(page.getByText('当前类型还没有字段')).toBeVisible()
      await page.goto(`/project-spaces/${spaceId}/types/${deliveryTypeId}/fields/00000000-0000-0000-0000-000000000099`)
      await expect(page.getByText('字段不存在或当前账号不可访问')).toBeVisible()

      const adminSession = await loginByApi(request, spaceAdmin.username, spaceAdmin.password)
      expect((await request.get(fieldsUrl, { headers: bearer(adminSession) })).status()).toBe(200)
      const adminPage = await newIdentityPage(browser, contexts, adminSession)
      await adminPage.goto(`/project-spaces/${spaceId}/types/${deliveryTypeId}/fields/${priorityFieldId}`)
      await expect(adminPage.getByTestId('work-item-fields-panel')).toBeVisible()
      await expect(adminPage.getByRole('button', { name: /配置$/ })).toBeVisible()

      for (const identity of [member, guest]) {
        const session = await loginByApi(request, identity.username, identity.password)
        expect((await request.get(fieldsUrl, { headers: bearer(session) })).status()).toBe(403)
        const identityPage = await newIdentityPage(browser, contexts, session)
        await identityPage.goto(`/project-spaces/${spaceId}/types/${deliveryTypeId}/fields/${priorityFieldId}`)
        await expect(identityPage.getByText('无权访问空间设置')).toBeVisible()
        await expect(identityPage.getByTestId('work-item-fields-panel')).toHaveCount(0)
      }

      for (const identity of [outsider, governor]) {
        const session = await loginByApi(request, identity.username, identity.password)
        expect((await request.get(fieldsUrl, { headers: bearer(session) })).status()).toBe(404)
        const identityPage = await newIdentityPage(browser, contexts, session)
        await identityPage.goto(`/project-spaces/${spaceId}`)
        await expect(identityPage.getByText('空间不存在或你无权访问')).toBeVisible()
      }

      for (const viewport of [
        { width: 1366, height: 768 },
        { width: 1440, height: 900 },
        { width: 390, height: 844 },
      ]) {
        await page.setViewportSize(viewport)
        await page.goto(`/project-spaces/${spaceId}/types/${deliveryTypeId}/fields/${priorityFieldId}`)
        await expect(page.getByTestId('work-item-fields-panel')).toBeVisible()
        const overflow = await page.evaluate(() =>
          document.documentElement.scrollWidth - document.documentElement.clientWidth)
        expect(overflow).toBeLessThanOrEqual(1)
      }
    } finally {
      for (const context of contexts) await context.close()
      if (spaceId) {
        await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`, {
          headers: bearer(owner),
        })
      }
      for (const identity of [spaceAdmin, member, guest, outsider, governor]) {
        await request.post(`${apiBaseUrl}/admin/users/${identity.id}/offboard`, {
          headers: bearer(owner),
          data: { handoverToUserId: ownerProfile.id },
        })
      }
    }
  })
})

type FieldCollection = {
  availableActions: string[]
  items: Array<{
    id: string
    fieldKey: string
    options: Array<{ optionKey: string }>
    config: {
      defaultValue: unknown
      validationRules: Array<{ kind: string; config: object }>
    }
  }>
}

async function createFieldInUi(
  page: Page,
  field: { typeLabel: string; fieldKey: string; name: string; description: string },
) {
  await page.getByRole('button', { name: /新建字段$/ }).click()
  const dialog = page.getByRole('dialog', { name: '新建字段' })
  await dialog.getByRole('radio', { name: new RegExp(field.typeLabel) }).click()
  await dialog.getByLabel('字段键', { exact: true }).fill(field.fieldKey)
  await dialog.getByLabel('显示名称').fill(field.name)
  await dialog.getByLabel('字段说明').fill(field.description)
  const response = page.waitForResponse((candidate) =>
    candidate.url().includes('/configuration/types/')
      && candidate.url().endsWith('/fields')
      && candidate.request().method() === 'POST')
  await dialog.getByRole('button', { name: '创建字段' }).click()
  const createResponse = await response
  expect(createResponse.ok()).toBeTruthy()
  return (await createResponse.json() as { id: string }).id
}

async function createType(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  typeKey: string,
  name: string,
) {
  const response = await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`, {
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s04-m4-type-${typeKey}` },
    data: { typeKey, name, sortOrder: 10 },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json() as { id: string }).id
}

async function visibleFieldKeys(page: Page) {
  return page.getByRole('option').locator('small').allTextContents()
}

async function newIdentityPage(
  browser: import('@playwright/test').Browser,
  contexts: BrowserContext[],
  session: E2eSession,
) {
  const context = await browser.newContext()
  contexts.push(context)
  const page = await context.newPage()
  await installSession(page, session)
  return page
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
  expect(response.ok(), `identity fixture creation failed for ${username}`).toBeTruthy()
  const payload = await response.json() as { id: string; username: string; displayName: string }
  return { ...payload, password }
}

async function getJson<T>(request: APIRequestContext, url: string, session: E2eSession) {
  const response = await request.get(url, { headers: bearer(session) })
  expect(response.ok(), `GET ${url} failed`).toBeTruthy()
  return await response.json() as T
}
