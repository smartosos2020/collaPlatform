import { expect, test, type APIRequestContext, type BrowserContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

test.describe('PROJECT-PLATFORM-S03-M3 work-item type UI', () => {
  test('isolated roles use server-projected type configuration and execution summaries @smoke', async ({ browser, page, request }) => {
    test.setTimeout(180_000)
    requireIsolatedIdentityFixture()
    const suffix = `s03m3_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const owner = await loginByApi(request)
    const ownerProfile = await getJson<{ id: string }>(request, `${apiBaseUrl}/auth/me`, owner)
    const spaceAdmin = await createIdentity(request, owner, `${suffix}_admin`, 'S03 M3 空间管理员', 'member')
    const member = await createIdentity(request, owner, `${suffix}_member`, 'S03 M3 协作成员', 'member')
    const guest = await createIdentity(request, owner, `${suffix}_guest`, 'S03 M3 访客', 'member')
    const outsider = await createIdentity(request, owner, `${suffix}_outsider`, 'S03 M3 普通非成员', 'member')
    const governor = await createIdentity(request, owner, `${suffix}_governor`, 'S03 M3 企业管理员', 'admin')
    const contexts: BrowserContext[] = []
    let spaceId: string | undefined
    const spaceName = `S03 M3 类型空间 ${suffix}`

    try {
      await installSession(page, owner)
      await page.goto('/project-spaces')
      await page.getByRole('button', { name: '新建项目空间' }).click()
      await page.getByLabel('空间名称').fill(spaceName)
      await page.getByLabel('空间编码').fill(`s03-m3-${suffix.replaceAll('_', '-')}`)
      const createSpaceResponse = page.waitForResponse((response) => response.url().endsWith('/api/project-spaces') && response.request().method() === 'POST')
      await page.getByRole('button', { name: '创建空间' }).click()
      const spaceResponse = await createSpaceResponse
      expect(spaceResponse.ok()).toBeTruthy()
      expect(spaceResponse.request().headers()['x-colla-request-id']).toBeTruthy()
      spaceId = (await spaceResponse.json() as { id: string }).id

      for (const [identity, roleKey] of [[spaceAdmin, 'admin'], [member, 'member'], [guest, 'guest']] as const) {
        const response = await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/members`, {
          headers: { ...bearer(owner), 'X-Colla-Request-Id': `s03-m3-member-${identity.id}` },
          data: { userId: identity.id, roleKey },
        })
        expect(response.ok(), `failed to add ${roleKey}`).toBeTruthy()
      }

      await page.goto(`/project-spaces/${spaceId}/types`)
      await expect(page.getByTestId('work-item-types-panel')).toBeVisible()
      await createType(page, '需求', `${suffix}_requirement`, '产品与业务需求', 10)
      await expect(page).toHaveURL(new RegExp(`/project-spaces/${spaceId}/types/[0-9a-f-]+$`))
      await createType(page, '缺陷', `${suffix}_bug`, '研发缺陷', 20)

      await page.getByRole('option', { name: new RegExp(`${suffix}_requirement`) }).click()
      await page.getByRole('button', { name: '编辑' }).click()
      await expect(page.getByLabel('类型标识')).toBeDisabled()
      await page.getByLabel('显示名称').fill('业务需求')
      const editResponsePromise = page.waitForResponse((response) => response.url().includes('/configuration/types/') && response.request().method() === 'PATCH')
      await page.getByRole('dialog').getByRole('button', { name: /保\s*存/, exact: true }).click()
      const editResponse = await editResponsePromise
      expect(editResponse.ok()).toBeTruthy()
      expect(editResponse.request().headers()['x-colla-request-id']).toBeTruthy()
      await expect(page.getByRole('heading', { name: '业务需求' })).toBeVisible()

      await page.getByRole('button', { name: '复制' }).click()
      await page.getByLabel('类型标识').fill(`${suffix}_requirement_copy`)
      await page.getByLabel('显示名称').fill('需求副本')
      await page.getByRole('dialog').getByRole('button', { name: /复\s*制/, exact: true }).click()
      await expect(page.getByRole('heading', { name: '需求副本' })).toBeVisible()

      await page.getByRole('option', { name: new RegExp(`${suffix}_requirement\\b`) }).click()
      await page.getByRole('button', { name: '复制' }).click()
      await page.getByLabel('类型标识').fill(`${suffix}_bug`)
      await page.getByLabel('显示名称').fill('冲突副本输入保留')
      const copyConflictPromise = page.waitForResponse((response) => response.url().endsWith(':copy') && response.request().method() === 'POST')
      await page.getByRole('dialog').getByRole('button', { name: /复\s*制/, exact: true }).click()
      expect((await copyConflictPromise).status()).toBe(409)
      await expect(page.getByLabel('显示名称')).toHaveValue('冲突副本输入保留')
      await page.getByRole('dialog').getByRole('button', { name: /取\s*消/, exact: true }).click()

      const originalOrder = await visibleTypeKeys(page)
      const reorderResponsePromise = page.waitForResponse((response) => response.url().endsWith('/configuration/types:reorder') && response.request().method() === 'PUT')
      await page.getByRole('button', { name: '下移 业务需求' }).click()
      expect((await reorderResponsePromise).ok()).toBeTruthy()
      await expect.poll(() => visibleTypeKeys(page)).not.toEqual(originalOrder)

      const configuration = await getJson<TypeConfiguration>(request, `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`, owner)
      const externallyTouched = configuration.items.filter((item) => item.typeKey === `${suffix}_bug` || item.typeKey === `${suffix}_requirement`)
      const externalReorder = await request.put(`${apiBaseUrl}/project-spaces/${spaceId}/configuration/types:reorder`, {
        headers: { ...bearer(owner), 'X-Colla-Request-Id': `s03-m3-reorder-conflict-${suffix}` },
        data: { items: externallyTouched.map((item) => ({ typeId: item.id, sortOrder: item.sortOrder, aggregateVersion: item.aggregateVersion })) },
      })
      expect(externalReorder.ok()).toBeTruthy()
      const rollbackOrder = await visibleTypeKeys(page)
      const failedReorderPromise = page.waitForResponse((response) => response.url().endsWith('/configuration/types:reorder') && response.request().method() === 'PUT')
      await page.getByRole('button', { name: '上移 业务需求' }).click()
      expect((await failedReorderPromise).status()).toBe(409)
      await expect(page.getByText('排序保存失败，已恢复原顺序')).toBeVisible()
      await expect.poll(() => visibleTypeKeys(page)).toEqual(rollbackOrder)

      await page.getByRole('button', { name: '停用' }).click()
      const disableResponsePromise = waitForTypeTransition(page, ':disable')
      await page.getByRole('dialog').getByRole('button', { name: '确认停用' }).click()
      expect((await disableResponsePromise).ok()).toBeTruthy()
      await expect(page.getByText('工作项类型已停用')).toBeVisible()
      await expect(page.getByRole('button', { name: '恢复' })).toBeVisible()
      await page.getByRole('button', { name: '恢复' }).click()
      const restoreResponsePromise = waitForTypeTransition(page, ':restore')
      await page.getByRole('dialog').getByRole('button', { name: '确认恢复' }).click()
      expect((await restoreResponsePromise).ok()).toBeTruthy()
      await expect(page.getByText('工作项类型已恢复')).toBeVisible()

      const selected = await getJson<{ aggregateVersion: number }>(request, `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeIdFromUrl(page.url())}`, owner)
      const externalUpdate = await request.patch(`${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeIdFromUrl(page.url())}`, {
        headers: { ...bearer(owner), 'X-Colla-Request-Id': `s03-m3-external-${suffix}` },
        data: { name: '外部并发更新', icon: '', description: '并发版本', aggregateVersion: selected.aggregateVersion },
      })
      expect(externalUpdate.ok()).toBeTruthy()
      await page.getByRole('button', { name: '编辑' }).click()
      await page.getByLabel('显示名称').fill('过期页面更新')
      await page.getByRole('dialog').getByRole('button', { name: /保\s*存/, exact: true }).click()
      await expect(page.getByText('数据已被其他人更新，已刷新为最新版本，请检查当前输入后重新保存。')).toBeVisible()
      await page.getByRole('dialog').getByRole('button', { name: /取\s*消/, exact: true }).click()
      await expect(page.getByRole('heading', { name: '外部并发更新' })).toBeVisible()

      await page.getByRole('option', { name: new RegExp(`${suffix}_requirement_copy`) }).click()
      await page.getByRole('button', { name: '退役' }).click()
      const retireResponsePromise = waitForTypeTransition(page, ':retire')
      await page.getByRole('dialog').getByRole('button', { name: '确认退役' }).click()
      expect((await retireResponsePromise).ok()).toBeTruthy()
      await expect(page.getByText('工作项类型已退役')).toBeVisible()
      await expect(page.getByRole('button', { name: '恢复' })).toHaveCount(0)

      const adminSession = await loginByApi(request, spaceAdmin.username, spaceAdmin.password)
      const adminPage = await newIdentityPage(browser, contexts, adminSession)
      await adminPage.goto(`/project-spaces/${spaceId}/types`)
      await expect(adminPage.getByTestId('work-item-types-panel')).toBeVisible()
      await expect(adminPage.getByRole('button', { name: '新建类型' })).toBeVisible()

      for (const identity of [member, guest]) {
        const session = await loginByApi(request, identity.username, identity.password)
        const executionPage = await newIdentityPage(browser, contexts, session)
        await executionPage.goto(`/project-spaces/${spaceId}`)
        await expect(executionPage.getByLabel('可用工作项类型')).toContainText(`${suffix}_bug`)
        await expect(executionPage.getByLabel('可用工作项类型')).not.toContainText(`${suffix}_requirement_copy`)
        await expect(executionPage.getByRole('button', { name: '工作项类型' })).toHaveCount(0)
        await executionPage.goto(`/project-spaces/${spaceId}/types`)
        await expect(executionPage.getByText('无权访问空间设置')).toBeVisible()
        await expect(executionPage.getByTestId('work-item-types-panel')).toHaveCount(0)
      }

      const outsiderSession = await loginByApi(request, outsider.username, outsider.password)
      const outsiderConfig = await request.get(`${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`, { headers: bearer(outsiderSession) })
      expect(outsiderConfig.status()).toBe(404)
      const outsiderSummary = await request.get(`${apiBaseUrl}/project-spaces/${spaceId}/work-item-types`, { headers: bearer(outsiderSession) })
      expect(outsiderSummary.status()).toBe(404)
      const outsiderPage = await newIdentityPage(browser, contexts, outsiderSession)
      await outsiderPage.goto('/project-spaces')
      await expect(outsiderPage.getByText(spaceName)).toHaveCount(0)
      await outsiderPage.goto(`/project-spaces/${spaceId}`)
      await expect(outsiderPage.getByText('空间不存在或你无权访问')).toBeVisible()

      const governorSession = await loginByApi(request, governor.username, governor.password)
      const governorConfig = await request.get(`${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`, { headers: bearer(governorSession) })
      expect(governorConfig.status()).toBe(404)
      const governorSummary = await request.get(`${apiBaseUrl}/project-spaces/${spaceId}/work-item-types`, { headers: bearer(governorSession) })
      expect(governorSummary.status()).toBe(404)
      const governorPage = await newIdentityPage(browser, contexts, governorSession)
      await governorPage.goto(`/project-spaces/${spaceId}`)
      await expect(governorPage.getByText('空间不存在或你无权访问')).toBeVisible()

      for (const viewport of [{ width: 1366, height: 768 }, { width: 1440, height: 900 }, { width: 390, height: 844 }]) {
        await page.setViewportSize(viewport)
        await page.goto(`/project-spaces/${spaceId}/types`)
        await expect(page.getByTestId('work-item-types-panel')).toBeVisible()
        const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
        expect(overflow).toBeLessThanOrEqual(1)
      }
    } finally {
      for (const context of contexts) await context.close()
      if (spaceId) await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`, { headers: bearer(owner) })
      for (const identity of [spaceAdmin, member, guest, outsider, governor]) {
        await request.post(`${apiBaseUrl}/admin/users/${identity.id}/offboard`, {
          headers: bearer(owner),
          data: { handoverToUserId: ownerProfile.id },
        })
      }
    }
  })
})

async function createType(
  page: import('@playwright/test').Page,
  name: string,
  typeKey: string,
  description: string,
  sortOrder: number,
) {
  await page.getByRole('button', { name: '新建类型' }).click()
  await page.getByLabel('类型标识').fill(typeKey)
  await page.getByLabel('显示名称').fill(name)
  await page.getByLabel('类型说明').fill(description)
  await page.getByLabel('排序值').fill(String(sortOrder))
  const responsePromise = page.waitForResponse((response) => response.url().endsWith('/configuration/types') && response.request().method() === 'POST')
  await page.getByRole('dialog').getByRole('button', { name: /创\s*建/, exact: true }).click()
  const response = await responsePromise
  expect(response.ok()).toBeTruthy()
  expect(response.request().headers()['x-colla-request-id']).toBeTruthy()
  await expect(page.getByText('工作项类型已创建').last()).toBeVisible()
}

async function visibleTypeKeys(page: import('@playwright/test').Page) {
  return page.getByRole('option').locator('small').allTextContents()
}

function typeIdFromUrl(url: string) {
  const match = url.match(/\/types\/([0-9a-f-]+)$/)
  if (!match) throw new Error(`type id missing from ${url}`)
  return match[1]
}

function waitForTypeTransition(page: import('@playwright/test').Page, suffix: ':disable' | ':restore' | ':retire') {
  return page.waitForResponse((response) => response.url().endsWith(suffix) && response.request().method() === 'POST')
}

type TypeConfiguration = {
  items: Array<{ id: string; typeKey: string; sortOrder: number; aggregateVersion: number }>
}

async function newIdentityPage(browser: import('@playwright/test').Browser, contexts: BrowserContext[], session: E2eSession) {
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
