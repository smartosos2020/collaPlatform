import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

test.describe('PROJECT-PLATFORM-S05-M4 layout workbench and user samples', () => {
  test('six identities receive the correct workbench or minimal sample surface @smoke', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(300_000)
    requireIsolatedIdentityFixture()
    const suffix = `s05m4_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const enterpriseAdmin = await loginByApi(request)
    const enterpriseProfile = await profile(request, enterpriseAdmin)
    const identities = {
      owner: await createIdentity(request, enterpriseAdmin, `${suffix}_owner`, 'S05 M4 Owner'),
      admin: await createIdentity(request, enterpriseAdmin, `${suffix}_admin`, 'S05 M4 Admin'),
      member: await createIdentity(request, enterpriseAdmin, `${suffix}_member`, 'S05 M4 Member'),
      guest: await createIdentity(request, enterpriseAdmin, `${suffix}_guest`, 'S05 M4 Guest'),
      nonMember: await createIdentity(request, enterpriseAdmin, `${suffix}_outside`, 'S05 M4 Outside'),
    }
    const sessions = {
      owner: await loginByApi(request, identities.owner.username, identities.owner.password),
      admin: await loginByApi(request, identities.admin.username, identities.admin.password),
      member: await loginByApi(request, identities.member.username, identities.member.password),
      guest: await loginByApi(request, identities.guest.username, identities.guest.password),
      nonMember: await loginByApi(request, identities.nonMember.username, identities.nonMember.password),
    }
    let spaceId: string | undefined

    try {
      spaceId = await createSpace(request, sessions.owner, suffix)
      await addMember(request, sessions.owner, spaceId, identities.admin.id, 'admin')
      await addMember(request, sessions.owner, spaceId, identities.member.id, 'member')
      await addMember(request, sessions.owner, spaceId, identities.guest.id, 'guest')
      const typeId = await createType(request, sessions.owner, spaceId, `${suffix}_delivery`)
      const title = await createField(request, sessions.owner, spaceId, typeId, 'title', '标题', 'text', 10)
      const priority = await createField(
        request,
        sessions.owner,
        spaceId,
        typeId,
        'priority',
        '优先级',
        'single_select',
        20,
      )
      await configureSelect(request, sessions.owner, spaceId, typeId, priority)
      await createLayout(request, sessions.owner, spaceId, typeId, 'create', title, priority)
      await createLayout(request, sessions.owner, spaceId, typeId, 'detail', title, priority)

      for (const session of [sessions.owner, sessions.admin]) {
        const response = await request.get(
          `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}/layout-workbench`,
          { headers: bearer(session) },
        )
        expect(response.ok()).toBeTruthy()
        const body = await response.json() as {
          fields: { items: unknown[] }
          layouts: Record<string, { configuration: { layoutKind: string } }>
        }
        expect(body.fields.items).toHaveLength(2)
        expect(body.layouts.create.configuration.layoutKind).toBe('create')
        expect(body.layouts.detail.configuration.layoutKind).toBe('detail')
      }

      for (const [role, session] of [
        ['member', sessions.member],
        ['guest', sessions.guest],
      ] as const) {
        const sample = await request.post(
          `${apiBaseUrl}/project-spaces/${spaceId}/types/${typeId}/layouts/create/sample`,
          { headers: bearer(session), data: { fieldValues: { title: role, priority: 'high' } } },
        )
        expect(sample.ok()).toBeTruthy()
        const body = await sample.json() as {
          synthetic: boolean
          context: { role: string }
          availableActions: string[]
        }
        expect(body.synthetic).toBeTruthy()
        expect(body.context.role).toBe(role)
        expect(body.availableActions).toEqual([])
        expect((body as { diagnostics?: unknown[] }).diagnostics).toEqual([])
        expect(JSON.stringify(body)).not.toContain('workspaceId')
        expect(JSON.stringify(body)).not.toContain('createdBy')
      }

      for (const session of [sessions.member, sessions.guest]) {
        const workbench = await request.get(
          `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}/layout-workbench`,
          { headers: bearer(session) },
        )
        expect(workbench.status()).toBe(403)
      }
      for (const session of [sessions.nonMember, enterpriseAdmin]) {
        const sample = await request.post(
          `${apiBaseUrl}/project-spaces/${spaceId}/types/${typeId}/layouts/create/sample`,
          { headers: bearer(session), data: { fieldValues: {} } },
        )
        expect(sample.status()).toBe(404)
        expect(await sample.text()).not.toContain('priority')
      }

      await verifyManagerPage(page, sessions.owner, spaceId, typeId)
      await verifyManagerAccess(page, sessions.admin, spaceId, typeId)
      await verifySamplePage(page, sessions.member, spaceId, typeId, 1440, 900)
      await page.screenshot({
        path: testInfo.outputPath('member-layout-sample-1440.png'),
        fullPage: true,
      })
      await verifySamplePage(page, sessions.guest, spaceId, typeId, 1366, 768)
      await verifySamplePage(page, sessions.member, spaceId, typeId, 820, 900)
      await verifyHiddenPage(page, sessions.nonMember, spaceId, typeId)
      await verifyHiddenPage(page, enterpriseAdmin, spaceId, typeId)
    } finally {
      if (spaceId) {
        await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`, {
          headers: bearer(sessions.owner),
        }).catch(() => undefined)
      }
      for (const identity of Object.values(identities).reverse()) {
        await request.post(`${apiBaseUrl}/admin/users/${identity.id}/offboard`, {
          headers: bearer(enterpriseAdmin),
          data: { handoverToUserId: enterpriseProfile.id },
        }).catch(() => undefined)
      }
    }
  })
})

async function verifyManagerPage(
  page: Page,
  session: E2eSession,
  spaceId: string,
  typeId: string,
) {
  await installSession(page, session)
  await page.setViewportSize({ width: 1366, height: 768 })
  await page.goto(`/project-spaces/${spaceId}/types/${typeId}/layouts`)
  await expect(page.getByTestId('work-item-layouts-panel')).toBeVisible()
  await expect(page.getByTestId('work-item-layout-renderer')).toBeVisible()
  await expect(page.getByText('当前身份')).toBeVisible()

  const values = page.getByLabel('预览字段样本')
  await values.fill('{"title":"preview","priority":"high"}')
  await values.press('Home')
  await values.press('Delete')
  await expect(page.getByRole('dialog', { name: /删除/ })).toHaveCount(0)
  await values.fill('{"title":"preview","priority":"high"}')
  const previewRole = page.getByLabel('预览角色')
  await previewRole.click()
  await previewRole.press('ArrowDown')
  await previewRole.press('Enter')
  await page.getByRole('button', { name: '运行预览' }).click()
  await expect(page.getByText('合成预览')).toBeVisible()
  await expect(page.locator('.work-item-layout-preview-field').filter({ hasText: '标题' })).toBeVisible()
  await previewRole.focus()
  const focusPath = new Set<string>()
  let bodyTransitions = 0
  for (let index = 0; index < 12; index++) {
    await page.keyboard.press('Tab')
    const focused = await page.evaluate(() => {
      const element = document.activeElement as HTMLElement | null
      return element
        ? `${element.tagName}:${element.getAttribute('aria-label') ?? element.textContent?.trim() ?? ''}`
        : ''
    })
    if (focused.startsWith('BODY:')) {
      bodyTransitions += 1
    }
    focusPath.add(focused)
  }
  expect(focusPath.size).toBeGreaterThan(4)
  expect(bodyTransitions).toBeLessThanOrEqual(1)
  expect([...focusPath].some((focused) => focused.includes('运行预览'))).toBeTruthy()
  expect(await page.getByRole('button', { name: '添加区块' }).evaluate((element) =>
    !(element as HTMLButtonElement).disabled && (element as HTMLElement).tabIndex >= 0)).toBeTruthy()
  await expect(page.locator('[aria-live="polite"]')).not.toHaveCount(0)
  expect(await minimumLabelContrast(page)).toBeGreaterThanOrEqual(4.5)
  expect(await page.evaluate(() =>
    document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1)

  await page.context().setOffline(true)
  const offlineAlert = page.getByText('当前处于离线状态，已打开页面可继续查看，新的保存操作会失败。')
  await expect(offlineAlert).toBeVisible()
  await page.getByRole('button', { name: '添加区块' }).click()
  await expect(page.getByText('当前网络不可用，布局操作未保存')).toBeVisible()
  await page.context().setOffline(false)
  await expect(offlineAlert).toHaveCount(0)
  const recoveredCommand = page.waitForResponse((response) =>
    response.url().includes('/nodes:command') && response.request().method() === 'POST')
  await page.getByRole('button', { name: '添加区块' }).click()
  expect((await recoveredCommand).ok()).toBeTruthy()
  await page.getByRole('button', { name: /刷\s*新/ }).click()
  await expect(page.getByTestId('work-item-layout-renderer')).toBeVisible()

  await page.setViewportSize({ width: 820, height: 900 })
  await expect(page.getByTestId('work-item-layouts-panel')).toBeVisible()
  expect(await page.evaluate(() =>
    document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1)
  expect(await page.locator('.work-item-layout-editor').evaluate((element) =>
    element.scrollWidth >= element.clientWidth)).toBeTruthy()
}

async function verifyManagerAccess(
  page: Page,
  session: E2eSession,
  spaceId: string,
  typeId: string,
) {
  await installSession(page, session)
  await page.setViewportSize({ width: 1366, height: 768 })
  await page.goto(`/project-spaces/${spaceId}/types/${typeId}/layouts`)
  await expect(page.getByTestId('work-item-layouts-panel')).toBeVisible()
  await expect(page.getByTestId('work-item-layout-policy-editor')).toBeVisible()
}

async function verifyHiddenPage(
  page: Page,
  session: E2eSession,
  spaceId: string,
  typeId: string,
) {
  await installSession(page, session)
  await page.goto(`/project-spaces/${spaceId}/types/${typeId}/sample`)
  await expect(page.getByText('空间不存在或你无权访问')).toBeVisible()
  await expect(page.getByTestId('work-item-layout-sample')).toHaveCount(0)
}

async function minimumLabelContrast(page: Page) {
  return page.locator('.work-item-layout-preview-field label').evaluateAll((labels) => {
    const parse = (value: string) => {
      const channels = value.match(/[\d.]+/g)?.slice(0, 3).map(Number) ?? [0, 0, 0]
      return channels.map((channel) => {
        const normalized = channel / 255
        return normalized <= 0.03928
          ? normalized / 12.92
          : ((normalized + 0.055) / 1.055) ** 2.4
      })
    }
    const luminance = (channels: number[]) =>
      0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
    return Math.min(...labels.map((label) => {
      const style = getComputedStyle(label)
      const foreground = luminance(parse(style.color))
      let backgroundElement: Element | null = label
      let background = [1, 1, 1]
      while (backgroundElement) {
        const color = getComputedStyle(backgroundElement).backgroundColor
        if (color !== 'rgba(0, 0, 0, 0)' && color !== 'transparent') {
          background = parse(color)
          break
        }
        backgroundElement = backgroundElement.parentElement
      }
      const backgroundLuminance = luminance(background)
      return (Math.max(foreground, backgroundLuminance) + 0.05)
        / (Math.min(foreground, backgroundLuminance) + 0.05)
    }))
  })
}

async function verifySamplePage(
  page: Page,
  session: E2eSession,
  spaceId: string,
  typeId: string,
  width: number,
  height: number,
) {
  await installSession(page, session)
  await page.setViewportSize({ width, height })
  await page.goto(`/project-spaces/${spaceId}/types/${typeId}/sample`)
  await expect(page.getByTestId('work-item-layout-sample')).toBeVisible()
  await expect(page.getByText('这是当前身份的非持久化布局样例')).toBeVisible()
  await expect(page.getByRole('button', { name: '重新计算样例' })).toBeVisible()
  await expect(page.getByRole('button', { name: /保存|创建工作项/ })).toHaveCount(0)
  await expect(page.locator('label').filter({ hasText: '标题' })).toBeVisible()
  expect(await page.evaluate(() =>
    document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1)
}

type Identity = { id: string; username: string; password: string }
type Field = { id: string; fieldKey: string; aggregateVersion: number }

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
      spaceKey: `s05-m4-${suffix.replaceAll('_', '-')}`,
      name: `S05 M4 布局样例 ${suffix}`,
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s05-m4-member-${userId}` },
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s05-m4-type-${typeKey}` },
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
  fieldType: string,
  sortOrder: number,
): Promise<Field> {
  const response = await request.post(
    `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}/fields`,
    {
      headers: { ...bearer(owner), 'X-Colla-Request-Id': `s05-m4-field-${fieldKey}-${typeId}` },
      data: { fieldKey, name, fieldType, sortOrder, config: {} },
    },
  )
  expect(response.ok()).toBeTruthy()
  return await response.json() as Field
}

async function configureSelect(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  typeId: string,
  field: Field,
) {
  const response = await request.put(
    `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}/fields/${field.id}/configuration`,
    {
      headers: { ...bearer(owner), 'X-Colla-Request-Id': `s05-m4-options-${field.id}` },
      data: {
        schemaVersion: 1,
        required: false,
        defaultValue: null,
        validationRules: [],
        typeConfig: {},
        options: [
          { optionKey: 'high', name: '高', color: '#EF4444', sortOrder: 10, status: 'active' },
          { optionKey: 'low', name: '低', color: '#22C55E', sortOrder: 20, status: 'active' },
        ],
        aggregateVersion: field.aggregateVersion,
      },
    },
  )
  expect(response.ok()).toBeTruthy()
}

async function createLayout(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  typeId: string,
  kind: 'create' | 'detail',
  title: Field,
  priority: Field,
) {
  const sectionId = crypto.randomUUID()
  const response = await request.put(
    `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}/layouts/${kind}`,
    {
      headers: { ...bearer(owner), 'X-Colla-Request-Id': `s05-m4-layout-${kind}-${typeId}` },
      data: {
        nodes: [
          {
            id: sectionId,
            parentId: null,
            nodeKey: `${kind}_main`,
            nodeType: 'section',
            fieldId: null,
            fieldKey: null,
            sortOrder: 0,
            config: { title: kind === 'create' ? '新建样例' : '详情样例' },
            visibilityCondition: { schemaVersion: 1 },
          },
          ...[title, priority].map((field, index) => ({
            id: crypto.randomUUID(),
            parentId: sectionId,
            nodeKey: `${kind}_${field.fieldKey}`,
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
