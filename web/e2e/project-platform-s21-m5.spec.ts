import {
  expect,
  test,
  type APIRequestContext,
  type Browser,
  type BrowserContext,
  type Page,
} from '@playwright/test'

import {
  apiBaseUrl,
  bearer,
  installSession,
  loginByApi,
  webBaseUrl,
  type E2eSession,
} from './support/api'
import { addMember, createIdentity, getJson } from './support/crossSpace'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type SpaceView = {
  id: string
  status: 'active' | 'disabled' | 'archived'
  currentUserRole?: string | null
  availableActions: string[]
}

type ExperiencePreference = {
  schemaVersion: number
  mode: 'simple' | 'advanced'
  version: number
  updatedAt?: string | null
  availableModes: Array<'simple' | 'advanced'>
}

type IdentityFixture = {
  id: string
  username: string
  displayName: string
  password: string
}

const navigationLabels = ['概览', '工作项', '项目管理', '成员', '设置'] as const

test.describe('PROJECT-PLATFORM-S21-M5 simple and role-aware project space', () => {
  test('@smoke validates five-entry navigation, mode preference and compatibility recovery', async ({
    browser,
    page,
    request,
  }, testInfo) => {
    test.setTimeout(360_000)
    requireIsolatedIdentityFixture()

    const enterprise = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(
      request,
      `${apiBaseUrl}/auth/me`,
      enterprise,
    )
    const suffix = `s21m5_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const identities: IdentityFixture[] = []
    const extraContexts: BrowserContext[] = []
    let primarySpaceId: string | undefined
    let secondarySpaceId: string | undefined

    try {
      const ownerIdentity = await provisionIdentity(
        request, enterprise, suffix, 'owner', 'S21 M5 Owner',
      )
      const adminIdentity = await provisionIdentity(
        request, enterprise, suffix, 'admin', 'S21 M5 Space Admin',
      )
      const memberIdentity = await provisionIdentity(
        request, enterprise, suffix, 'member', 'S21 M5 Member',
      )
      const guestIdentity = await provisionIdentity(
        request, enterprise, suffix, 'guest', 'S21 M5 Guest',
      )
      const outsiderIdentity = await provisionIdentity(
        request, enterprise, suffix, 'outsider', 'S21 M5 Outsider',
      )
      identities.push(
        ownerIdentity,
        adminIdentity,
        memberIdentity,
        guestIdentity,
        outsiderIdentity,
      )

      const [owner, admin, member, guest, outsider] = await Promise.all([
        loginByApi(request, ownerIdentity.username, ownerIdentity.password),
        loginByApi(request, adminIdentity.username, adminIdentity.password),
        loginByApi(request, memberIdentity.username, memberIdentity.password),
        loginByApi(request, guestIdentity.username, guestIdentity.password),
        loginByApi(request, outsiderIdentity.username, outsiderIdentity.password),
      ])

      const longSpaceName = `S21 M5 ${'跨团队项目空间长名称'.repeat(7)}`.slice(0, 120)
      primarySpaceId = await createSpace(
        request,
        owner,
        `s21-m5-primary-${suffix.replaceAll('_', '-')}`,
        longSpaceName,
      )
      secondarySpaceId = await createSpace(
        request,
        owner,
        `s21-m5-secondary-${suffix.replaceAll('_', '-')}`,
        `S21 M5 preference isolation ${suffix}`,
      )
      await addMember(request, owner, primarySpaceId, adminIdentity.id, 'admin')
      await addMember(request, owner, primarySpaceId, memberIdentity.id, 'member')
      await addMember(request, owner, primarySpaceId, guestIdentity.id, 'guest')

      const [ownerSpace, adminSpace, memberSpace, guestSpace] = await Promise.all([
        getSpace(request, owner, primarySpaceId),
        getSpace(request, admin, primarySpaceId),
        getSpace(request, member, primarySpaceId),
        getSpace(request, guest, primarySpaceId),
      ])
      expect(ownerSpace.availableActions).toEqual(expect.arrayContaining([
        'view_overview',
        'view_work_items',
        'view_project_management',
        'view_members',
        'view_settings',
      ]))
      expect(adminSpace.availableActions).toEqual(expect.arrayContaining([
        'view_overview',
        'view_work_items',
        'view_project_management',
        'view_members',
        'view_settings',
      ]))
      expect(memberSpace.availableActions).toEqual(expect.arrayContaining([
        'view_overview',
        'view_work_items',
        'view_project_management',
      ]))
      expect(memberSpace.availableActions).not.toEqual(expect.arrayContaining([
        'view_members',
        'view_settings',
      ]))
      expect(guestSpace.availableActions).toEqual(expect.arrayContaining([
        'view_overview',
        'view_work_items',
      ]))
      expect(guestSpace.availableActions).not.toContain('view_project_management')

      for (const session of [outsider, enterprise]) {
        const hidden = await request.get(
          `${apiBaseUrl}/project-spaces/${primarySpaceId}`,
          { headers: bearer(session) },
        )
        expect(hidden.status()).toBe(404)
        expect(await hidden.text()).not.toContain(longSpaceName)
        const hiddenPreference = await request.get(
          preferenceUrl(primarySpaceId),
          { headers: bearer(session) },
        )
        expect(hiddenPreference.status()).toBe(404)
        expect(await hiddenPreference.text()).not.toContain(longSpaceName)
      }

      const ownerDefault = await getPreference(request, owner, primarySpaceId)
      expect(ownerDefault).toEqual(expect.objectContaining({
        schemaVersion: 1,
        mode: 'simple',
        version: 0,
        availableModes: ['simple', 'advanced'],
      }))
      expect(ownerDefault).not.toHaveProperty('role')
      expect(ownerDefault).not.toHaveProperty('capabilities')

      for (const session of [member, guest]) {
        const current = await getPreference(request, session, primarySpaceId)
        expect(current).toEqual(expect.objectContaining({
          mode: 'simple',
          availableModes: ['simple'],
        }))
        const forbidden = await request.put(preferenceUrl(primarySpaceId), {
          headers: {
            ...bearer(session),
            'X-Colla-Request-Id': `${suffix}-forbidden-mode-${Math.random()}`,
          },
          data: { schemaVersion: 1, mode: 'advanced', expectedVersion: current.version },
        })
        expect([403, 404]).toContain(forbidden.status())
        expect(await forbidden.text()).not.toContain(longSpaceName)
      }

      const advanced = await putPreference(
        request,
        owner,
        primarySpaceId,
        'advanced',
        ownerDefault.version,
        `${suffix}-advanced`,
      )
      expect(advanced).toEqual(expect.objectContaining({
        schemaVersion: 1,
        mode: 'advanced',
        version: 1,
      }))
      const secondaryDefault = await getPreference(request, owner, secondarySpaceId)
      expect(secondaryDefault).toEqual(expect.objectContaining({
        mode: 'simple',
        version: 0,
      }))
      const adminDefault = await getPreference(request, admin, primarySpaceId)
      expect(adminDefault).toEqual(expect.objectContaining({
        mode: 'simple',
        version: 0,
      }))

      const winner = await putPreference(
        request,
        owner,
        primarySpaceId,
        'simple',
        advanced.version,
        `${suffix}-winner`,
      )
      const stale = await request.put(preferenceUrl(primarySpaceId), {
        headers: {
          ...bearer(owner),
          'X-Colla-Request-Id': `${suffix}-stale`,
        },
        data: {
          schemaVersion: 1,
          mode: 'advanced',
          expectedVersion: advanced.version,
        },
      })
      expect(stale.status()).toBe(409)
      expect(await stale.text()).not.toContain(longSpaceName)

      const reset = await resetPreference(
        request,
        owner,
        primarySpaceId,
        winner.version,
        `${suffix}-reset`,
      )
      expect(reset).toEqual(expect.objectContaining({
        schemaVersion: 1,
        mode: 'simple',
        version: 0,
        availableModes: ['simple', 'advanced'],
      }))

      await installSession(page, owner)
      await page.goto(`/project-spaces/${primarySpaceId}`)
      await expect(page.getByRole('heading', { name: longSpaceName })).toBeVisible()
      await expectPrimaryNavigation(page, navigationLabels)
      await expect(page.getByTestId('project-space-mode-switch')).toBeVisible()
      await expectCurrentPrimaryView(page, '概览')

      const configuredTypes = await getJson<{
        items: Array<{ id: string; typeKey: string }>
      }>(
        request,
        `${apiBaseUrl}/project-spaces/${primarySpaceId}/configuration/types`,
        owner,
      )
      const type = configuredTypes.items.find(candidate => candidate.typeKey === 'task')
        ?? configuredTypes.items[0]
      expect(type, 'project space preset type is required').toBeTruthy()
      if (!type) throw new Error('project space preset type is unavailable')

      await page.goto(
        `/project-spaces/${primarySpaceId}/work-items`
        + `?typeId=${type.id}&savedViewId=m5-contract&panel=work-item-collection&source=m5`,
      )
      await expectCurrentPrimaryView(page, '工作项')
      await selectExperienceMode(page, '高级')
      await expect.poll(
        async () => (await getPreference(request, owner, primarySpaceId)).mode,
      ).toBe('advanced')
      expect(new URL(page.url()).searchParams.get('typeId')).toBe(type.id)
      expect(new URL(page.url()).searchParams.get('savedViewId')).toBe('m5-contract')
      expect(new URL(page.url()).searchParams.get('panel')).toBe('work-item-collection')
      expect(new URL(page.url()).searchParams.get('source')).toBe('m5')

      await page.goto(
        `/project-spaces/${primarySpaceId}/types/${type.id}`
        + '?panel=configuration-draft&source=m5-compat',
      )
      await expectCurrentPrimaryView(page, '设置')
      const legacyTypeTabs = page.getByTestId('project-space-types-secondary-tabs')
      await expect(legacyTypeTabs).toBeVisible()
      await expect(legacyTypeTabs.getByRole('tab', { name: '配置发布' })).toBeVisible()
      expect(new URL(page.url()).searchParams.get('panel')).toBe('configuration-draft')
      expect(new URL(page.url()).searchParams.get('source')).toBe('m5-compat')

      await page.goto(`/project-spaces/${primarySpaceId}/management?panel=project-plan`)
      await expectCurrentPrimaryView(page, '项目管理')
      const management = page.getByTestId('project-space-management')
      await expect(management).toBeVisible()
      for (const label of ['计划', '风险', '交付', '资源', '指标']) {
        await expect(management.getByText(new RegExp(label)).first()).toBeVisible()
      }

      await page.goto(`/project-spaces/${primarySpaceId}/settings?panel=work-model`)
      await expectCurrentPrimaryView(page, '设置')
      const settingsTabs = page.getByTestId('project-space-settings-secondary-tabs')
      for (const label of ['工作模型', '自动化与协同', '度量治理', '场景模板']) {
        await expect(settingsTabs.getByRole('tab', { name: label })).toBeVisible()
      }
      await expect(settingsTabs.getByRole('tab', { name: '流程与权限', exact: true }))
        .toHaveCount(0)
      await expect(page.getByTestId('project-space-advanced-settings')).toBeVisible()

      const memberSurface = await openSurface(browser, member, primarySpaceId)
      extraContexts.push(memberSurface.context)
      await expectPrimaryNavigation(memberSurface.page, ['概览', '工作项', '项目管理'])
      await expect(memberSurface.page.getByTestId('project-space-mode-switch')).toHaveCount(0)
      expect(memberSurface.requests.some(isRestrictedShellRequest)).toBe(false)

      const guestSurface = await openSurface(browser, guest, primarySpaceId)
      extraContexts.push(guestSurface.context)
      await expectPrimaryNavigation(guestSurface.page, ['概览', '工作项'])
      await expect(guestSurface.page.getByTestId('project-space-mode-switch')).toHaveCount(0)
      expect(guestSurface.requests.some(isRestrictedShellRequest)).toBe(false)

      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 980 })
        expect(await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        )).toBeLessThanOrEqual(1)
      }
      const primaryNavigation = page.getByTestId('project-space-primary-navigation')
      await primaryNavigation.getByRole('button', { name: '概览' }).focus()
      await page.keyboard.press('Tab')
      await expect(primaryNavigation.getByRole('button', { name: '工作项' })).toBeFocused()

      await page.context().setOffline(true)
      await page.evaluate(() => window.dispatchEvent(new Event('offline')))
      await expect(
        page.getByTestId('project-space-mode-switch').locator('.ant-segmented'),
      ).toHaveClass(/ant-segmented-disabled/)
      await expect.poll(
        async () => (await getPreference(request, owner, primarySpaceId)).mode,
      ).toBe('advanced')
      await page.context().setOffline(false)
      await page.evaluate(() => {
        window.dispatchEvent(new Event('online'))
        window.dispatchEvent(new Event('focus'))
      })
      await expect(
        page.getByTestId('project-space-mode-switch').locator('.ant-segmented'),
      ).not.toHaveClass(/ant-segmented-disabled/)
      await expect.poll(
        async () => (await getPreference(request, owner, primarySpaceId)).mode,
      ).toBe('advanced')

      await transitionSpace(request, owner, primarySpaceId, 'disable')
      await page.goto(`/project-spaces/${primarySpaceId}`)
      await expect(page.getByRole('alert').filter({ hasText: '空间已停用' })).toBeVisible()
      await expectCurrentPrimaryView(page, '概览')
      await transitionSpace(request, owner, primarySpaceId, 'restore')

      await transitionSpace(request, owner, secondarySpaceId, 'archive')
      await page.goto(`/project-spaces/${secondarySpaceId}`)
      await expect(page.getByRole('alert').filter({ hasText: '空间已归档' })).toBeVisible()
      await expectCurrentPrimaryView(page, '概览')

      await page.setViewportSize({ width: 820, height: 980 })
      await page.screenshot({
        path: testInfo.outputPath('s21-m5-simple-shell-820.png'),
        fullPage: true,
      })
    } finally {
      await page.context().setOffline(false).catch(() => undefined)
      await Promise.all(extraContexts.map(context => context.close().catch(() => undefined)))
      if (primarySpaceId) {
        await archiveIfNeeded(request, enterprise, identities[0], primarySpaceId)
      }
      if (secondarySpaceId) {
        await archiveIfNeeded(request, enterprise, identities[0], secondarySpaceId)
      }
      for (const identity of identities.reverse()) {
        await request.post(`${apiBaseUrl}/admin/users/${identity.id}/offboard`, {
          headers: {
            ...bearer(enterprise),
            'X-Colla-Request-Id': `${suffix}-offboard-${identity.id}`,
          },
          data: { handoverToUserId: enterpriseProfile.id },
        }).catch(() => undefined)
      }
    }
  })
})

async function provisionIdentity(
  request: APIRequestContext,
  enterprise: E2eSession,
  suffix: string,
  role: string,
  displayName: string,
) {
  return createIdentity(
    request,
    enterprise,
    `${suffix}_${role}`,
    displayName,
  )
}

async function createSpace(
  request: APIRequestContext,
  owner: E2eSession,
  spaceKey: string,
  name: string,
) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: {
      ...bearer(owner),
      'X-Colla-Request-Id': `create-${spaceKey}`,
    },
    data: {
      spaceKey,
      name,
      description: 'S21 M5 isolated simple-shell acceptance fixture',
      visibility: 'private',
    },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  return (await response.json() as { id: string }).id
}

function preferenceUrl(spaceId: string) {
  return `${apiBaseUrl}/project-spaces/${spaceId}/experience-preference`
}

function getPreference(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
) {
  return getJson<ExperiencePreference>(request, preferenceUrl(spaceId), session)
}

async function putPreference(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  mode: ExperiencePreference['mode'],
  expectedVersion: number,
  requestId: string,
) {
  const response = await request.put(preferenceUrl(spaceId), {
    headers: { ...bearer(session), 'X-Colla-Request-Id': requestId },
    data: { schemaVersion: 1, mode, expectedVersion },
  })
  expect(response.ok(), `PUT experience preference failed: ${await response.text()}`).toBeTruthy()
  return await response.json() as ExperiencePreference
}

async function resetPreference(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  expectedVersion: number,
  requestId: string,
) {
  const response = await request.delete(
    `${preferenceUrl(spaceId)}?expectedVersion=${expectedVersion}`,
    { headers: { ...bearer(session), 'X-Colla-Request-Id': requestId } },
  )
  expect(response.ok(), `DELETE experience preference failed: ${await response.text()}`).toBeTruthy()
  return await response.json() as ExperiencePreference
}

function getSpace(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
) {
  return getJson<SpaceView>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}`,
    session,
  )
}

async function transitionSpace(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  action: 'disable' | 'restore' | 'archive',
) {
  const response = await request.post(
    `${apiBaseUrl}/project-spaces/${spaceId}/settings/${action}`,
    {
      headers: {
        ...bearer(session),
        'X-Colla-Request-Id': `s21-m5-${spaceId}-${action}-${Date.now()}`,
      },
    },
  )
  expect(response.ok(), `${action} project space failed: ${await response.text()}`).toBeTruthy()
}

async function openSurface(
  browser: Browser,
  session: E2eSession,
  spaceId: string,
) {
  const context = await browser.newContext({ baseURL: webBaseUrl })
  const page = await context.newPage()
  const requests: string[] = []
  await installSession(page, session)
  page.on('request', current => requests.push(current.url()))
  await page.goto(`/project-spaces/${spaceId}`)
  await expect(page.getByTestId('project-space-primary-navigation')).toBeVisible()
  return { context, page, requests }
}

async function expectPrimaryNavigation(
  page: Page,
  expectedLabels: readonly string[],
) {
  const navigation = page.getByTestId('project-space-primary-navigation')
  await expect(navigation).toBeVisible()
  for (const label of navigationLabels) {
    await expect(navigation.getByText(label, { exact: true })).toHaveCount(
      expectedLabels.includes(label) ? 1 : 0,
    )
  }
}

function expectCurrentPrimaryView(page: Page, label: string) {
  return expect(
    page
      .getByTestId('project-space-primary-navigation')
      .locator('[aria-current="page"]'),
  ).toContainText(label)
}

async function selectExperienceMode(page: Page, label: '简洁' | '高级') {
  const switcher = page.getByTestId('project-space-mode-switch')
  await expect(switcher).toBeVisible()
  await switcher.getByText(new RegExp(label)).first().click()
}

function isRestrictedShellRequest(url: string) {
  return [
    '/members',
    '/settings',
    '/configuration/',
    '/scenario-templates',
    '/automation/',
    '/metrics',
  ].some(fragment => url.includes(fragment))
}

async function archiveIfNeeded(
  request: APIRequestContext,
  enterprise: E2eSession,
  ownerIdentity: IdentityFixture | undefined,
  spaceId: string,
) {
  if (!ownerIdentity) return
  const owner = await loginByApi(request, ownerIdentity.username, ownerIdentity.password)
    .catch(() => undefined)
  if (!owner) return
  const current = await request.get(`${apiBaseUrl}/project-spaces/${spaceId}`, {
    headers: bearer(owner),
  }).catch(() => undefined)
  if (!current?.ok()) return
  const space = await current.json() as SpaceView
  if (space.status === 'disabled') {
    await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/restore`, {
      headers: bearer(owner),
    }).catch(() => undefined)
  }
  if (space.status !== 'archived') {
    await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`, {
      headers: bearer(owner),
    }).catch(() => undefined)
  }
  void enterprise
}
