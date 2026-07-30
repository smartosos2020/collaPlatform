import { expect, test, type APIRequestContext, type BrowserContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

test.describe('PROJECT-PLATFORM-S05-M2 layout editor', () => {
  test('isolated manager configures independent create/detail graphs while members stay outside settings @smoke', async ({
    browser,
    page,
    request,
  }) => {
    test.setTimeout(240_000)
    page.setDefaultTimeout(15_000)
    requireIsolatedIdentityFixture()
    const suffix = `s05m2_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const owner = await loginByApi(request)
    const ownerProfile = await getJson<{ id: string }>(request, `${apiBaseUrl}/auth/me`, owner)
    const member = await createIdentity(request, owner, `${suffix}_member`, 'S05 M2 成员')
    const contexts: BrowserContext[] = []
    let spaceId: string | undefined

    try {
      spaceId = await createSpace(request, owner, suffix)
      await addMember(request, owner, spaceId, member.id)
      const typeId = await createType(request, owner, spaceId, `${suffix}_delivery`)
      await createField(request, owner, spaceId, typeId, {
        fieldKey: 'title',
        name: '标题',
        fieldType: 'text',
        sortOrder: 10,
      })
      await createField(request, owner, spaceId, typeId, {
        fieldKey: 'priority',
        name: '优先级',
        fieldType: 'single_select',
        sortOrder: 20,
      })

      const createUrl = `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}/layouts/create`
      const detailUrl = `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}/layouts/detail`
      await installSession(page, owner)
      await page.setViewportSize({ width: 1366, height: 768 })
      await page.goto(`/project-spaces/${spaceId}/types/${typeId}/layouts`)
      await expect(page.getByTestId('work-item-layouts-panel')).toBeVisible()
      await expect(page.getByText('尚未配置新建页布局')).toBeVisible()

      const initializeCreate = page.waitForResponse((response) =>
        response.url().endsWith(`/configuration/types/${typeId}/layouts/create`)
          && response.request().method() === 'PUT')
      await page.getByRole('button', { name: '使用当前字段初始化' }).click()
      expect((await initializeCreate).ok()).toBeTruthy()
      await expect(page.getByTestId('work-item-layout-editor')).toBeVisible()
      await page.getByTestId('project-space-layouts-secondary-tabs')
        .getByRole('tab', { name: '访问预览', exact: true }).click()
      await expect(page.getByTestId('work-item-layout-renderer')).toContainText('标题')
      await expect(page.getByTestId('work-item-layout-renderer')).toContainText('优先级')
      await page.getByTestId('project-space-layouts-secondary-tabs')
        .getByRole('tab', { name: '布局设计', exact: true }).click()

      const addSection = page.waitForResponse((response) =>
        response.url().endsWith('/layouts/create/nodes:command')
          && response.request().method() === 'POST')
      await page.getByRole('button', { name: '添加区块' }).click()
      expect((await addSection).ok()).toBeTruthy()

      await page.getByRole('button', { name: '添加条件' }).click()
      await page.getByLabel('条件 1 字段').click()
      await page.getByText('优先级', { exact: true }).last().click()
      await page.getByLabel('条件 1 操作符').click()
      await page.getByText('eq', { exact: true }).last().click()
      await page.getByLabel('条件 1 比较值').fill('high')
      const saveCondition = page.waitForResponse((response) =>
        response.url().endsWith('/layouts/create/nodes:command')
          && response.request().method() === 'POST')
      await page.getByRole('button', { name: '保存属性' }).click()
      expect((await saveCondition).ok()).toBeTruthy()

      const createLayout = await getJson<LayoutResponse>(request, createUrl, owner)
      expect(createLayout.nodes.some((node) => node.visibilityCondition.expression != null)).toBeTruthy()
      expect(createLayout.nodes.filter((node) => node.nodeType === 'field')).toHaveLength(2)

      await page.getByText('详情页', { exact: true }).click()
      await expect(page.getByText('尚未配置详情页布局')).toBeVisible()
      const initializeDetail = page.waitForResponse((response) =>
        response.url().endsWith(`/configuration/types/${typeId}/layouts/detail`)
          && response.request().method() === 'PUT')
      await page.getByRole('button', { name: '使用当前字段初始化' }).click()
      expect((await initializeDetail).ok()).toBeTruthy()
      const detailLayout = await getJson<LayoutResponse>(request, detailUrl, owner)
      expect(detailLayout.layoutKind).toBe('detail')
      expect(detailLayout.id).not.toBe(createLayout.id)
      expect((await getJson<LayoutResponse>(request, createUrl, owner)).configHash).toBe(createLayout.configHash)

      const pageOverflow = await page.evaluate(() =>
        document.documentElement.scrollWidth - document.documentElement.clientWidth)
      expect(pageOverflow).toBeLessThanOrEqual(1)
      const editorBox = await page.getByTestId('work-item-layout-editor').boundingBox()
      expect(editorBox?.x ?? -1).toBeGreaterThanOrEqual(0)
      expect((editorBox?.x ?? 0) + (editorBox?.width ?? 0)).toBeLessThanOrEqual(1367)

      const memberSession = await loginByApi(request, member.username, member.password)
      expect((await request.get(createUrl, { headers: bearer(memberSession) })).status()).toBe(403)
      const memberContext = await browser.newContext({ viewport: { width: 1366, height: 768 } })
      contexts.push(memberContext)
      const memberPage = await memberContext.newPage()
      await installSession(memberPage, memberSession)
      await memberPage.goto(`/project-spaces/${spaceId}/types/${typeId}/layouts`)
      await expect(memberPage.getByText('无权访问空间设置')).toBeVisible()
      await expect(memberPage.getByRole('button', { name: '页面布局' })).toHaveCount(0)
      await expect(memberPage.getByTestId('work-item-layouts-panel')).toHaveCount(0)
    } finally {
      for (const context of contexts) await context.close()
      if (spaceId) {
        await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`, {
          headers: bearer(owner),
        }).catch(() => undefined)
      }
      await request.post(`${apiBaseUrl}/admin/users/${member.id}/offboard`, {
        headers: bearer(owner),
        data: { handoverToUserId: ownerProfile.id },
      }).catch(() => undefined)
    }
  })
})

type LayoutResponse = {
  id: string
  layoutKind: 'create' | 'detail'
  configHash: string
  nodes: Array<{
    nodeType: string
    visibilityCondition: { expression?: object }
  }>
}

async function createSpace(request: APIRequestContext, owner: E2eSession, suffix: string) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(owner),
    data: {
      spaceKey: `s05-m2-${suffix.replaceAll('_', '-')}`,
      name: `S05 M2 布局空间 ${suffix}`,
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
) {
  const response = await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/members`, {
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s05-m2-member-${userId}` },
    data: { userId, roleKey: 'member' },
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s05-m2-type-${typeKey}` },
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
  field: { fieldKey: string; name: string; fieldType: string; sortOrder: number },
) {
  const response = await request.post(
    `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}/fields`,
    {
      headers: { ...bearer(owner), 'X-Colla-Request-Id': `s05-m2-field-${field.fieldKey}-${typeId}` },
      data: { ...field, config: {} },
    },
  )
  expect(response.ok()).toBeTruthy()
}

async function createIdentity(
  request: APIRequestContext,
  administrator: E2eSession,
  username: string,
  displayName: string,
) {
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
  return { ...(await response.json() as { id: string; username: string }), password }
}

async function getJson<T>(request: APIRequestContext, url: string, session: E2eSession) {
  const response = await request.get(url, { headers: bearer(session) })
  expect(response.ok(), `GET ${url} failed`).toBeTruthy()
  return await response.json() as T
}
