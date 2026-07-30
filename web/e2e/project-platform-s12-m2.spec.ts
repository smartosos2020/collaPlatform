import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = { id: string; username: string; displayName: string; password: string }
type Layout = {
  version: number
  cards: Array<{ cardKey: string; title: string; position: number; hidden: boolean; configurable: boolean }>
}
type Dashboard = {
  recentObjects: Array<{ objectId: string; title: string }>
  favoriteObjects: Array<{ objectId: string; title: string }>
  draftSummaries: Array<{ draftId: string; typeName: string; recoveryPath: string }>
  dashboardLayout: Layout
}

test.describe('PROJECT-PLATFORM-S12 M2', () => {
  test('recent favorites drafts and card layout remain owner scoped and replay safe', async ({ page, request }, testInfo) => {
    test.setTimeout(480_000)
    page.setDefaultTimeout(25_000)
    requireIsolatedIdentityFixture()
    const enterprise = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(request, `${apiBaseUrl}/auth/me`, enterprise)
    const suffix = `s12m2_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterprise, `${suffix}_owner`, 'S12 M2 Owner')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S12 M2 Member')
    const owner = await loginByApi(request, ownerIdentity.username, ownerIdentity.password)
    const member = await loginByApi(request, memberIdentity.username, memberIdentity.password)
    let spaceId: string | undefined
    try {
      spaceId = await createSpace(request, owner, suffix)
      await addMember(request, owner, spaceId, memberIdentity.id)
      const types = await getJson<{ items: Array<{ id: string; typeKey: string }> }>(
        request, `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`, owner,
      )
      const type = types.items.find((candidate) => candidate.typeKey === 'project')
      expect(type).toBeTruthy()
      if (!type) throw new Error('project type missing')

      const ownerDrafts = await getJson<Dashboard>(request, `${apiBaseUrl}/workspace/dashboard`, owner)
      const memberDrafts = await getJson<Dashboard>(request, `${apiBaseUrl}/workspace/dashboard`, member)
      expect(ownerDrafts.draftSummaries.some((draft) => draft.recoveryPath.includes(type.id))).toBeTruthy()
      expect(memberDrafts.draftSummaries).toHaveLength(0)

      await validateAndPublish(request, owner, spaceId, type.id, suffix)
      const title = `S12 M2 收藏与最近 ${'长名称'.repeat(20)}`
      const item = await postJson<{ id: string }>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-items`,
        owner,
        { typeId: type.id, title, fieldValues: {} },
        `${suffix}-item`,
      )
      await postJson(
        request,
        `${apiBaseUrl}/platform/objects/work_item/${item.id}/access`,
        owner,
        undefined,
        `${suffix}-access`,
      )
      const favoritePath = `${apiBaseUrl}/platform/personalization/favorites/work_item/${item.id}`
      await postJson(request, favoritePath, owner, { requestId: `${suffix}-favorite`, favorite: true }, `${suffix}-fav-header`)
      await postJson(request, favoritePath, owner, { requestId: `${suffix}-favorite`, favorite: true }, `${suffix}-fav-replay`)

      const ownerDashboard = await getJson<Dashboard>(request, `${apiBaseUrl}/workspace/dashboard`, owner)
      const memberDashboard = await getJson<Dashboard>(request, `${apiBaseUrl}/workspace/dashboard`, member)
      expect(ownerDashboard.favoriteObjects.filter((object) => object.objectId === item.id)).toHaveLength(1)
      expect(ownerDashboard.recentObjects.some((object) => object.objectId === item.id)).toBeTruthy()
      expect(JSON.stringify(memberDashboard)).not.toContain(title)

      const cards = ownerDashboard.dashboardLayout.cards.map((card) =>
        card.cardKey === 'objects.recent' ? { ...card, hidden: true } : card)
      const layout = await postJson<Layout>(
        request,
        `${apiBaseUrl}/platform/personalization/dashboard`,
        owner,
        { requestId: `${suffix}-layout`, expectedVersion: ownerDashboard.dashboardLayout.version, cards },
        `${suffix}-layout-header`,
      )
      const replay = await postJson<Layout>(
        request,
        `${apiBaseUrl}/platform/personalization/dashboard`,
        owner,
        { requestId: `${suffix}-layout`, expectedVersion: ownerDashboard.dashboardLayout.version, cards },
        `${suffix}-layout-replay`,
      )
      expect(replay.version).toBe(layout.version)
      const conflict = await request.post(`${apiBaseUrl}/platform/personalization/dashboard`, {
        headers: bearer(owner),
        data: { requestId: `${suffix}-stale`, expectedVersion: 0, cards },
      })
      expect(conflict.status()).toBe(409)

      await installSession(page, owner)
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        await page.goto('/')
        await expect(page.getByText('个性化卡片')).toHaveCount(0)
        await expect(page.getByRole('button', { name: '个性化', exact: true })).toBeVisible()
        await expect(page.getByText(title).first()).toBeVisible()
        await expect(page.locator('.ant-card-head-title', { hasText: /^最近访问$/ })).toHaveCount(0)
        expect(await page.evaluate(() =>
          document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1)
      }
      await page.screenshot({ path: testInfo.outputPath('s12-m2-dashboard-820.png'), fullPage: true })
    } finally {
      if (spaceId) {
        await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`, {
          headers: bearer(owner),
        }).catch(() => undefined)
      }
      for (const identity of [memberIdentity, ownerIdentity]) {
        await request.post(`${apiBaseUrl}/admin/users/${identity.id}/offboard`, {
          headers: bearer(enterprise),
          data: { handoverToUserId: enterpriseProfile.id },
        }).catch(() => undefined)
      }
    }
  })

  test('personalize popup toggles cards and drag reorder persists across reload', async ({ page, request }) => {
    test.setTimeout(240_000)
    page.setDefaultTimeout(25_000)
    requireIsolatedIdentityFixture()
    const admin = await loginByApi(request)
    const before = await getJson<Dashboard>(request, `${apiBaseUrl}/workspace/dashboard`, admin)
    const originalCards = before.dashboardLayout.cards
    const suffix = `s12m2ui_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    // known baseline: all 12 cards visible, positions 0..11
    const baselineCards = [...originalCards]
      .sort((left, right) => left.position - right.position || left.cardKey.localeCompare(right.cardKey))
      .map((card, index) => ({ cardKey: card.cardKey, position: index, hidden: false }))
    await postJson(
      request,
      `${apiBaseUrl}/platform/personalization/dashboard`,
      admin,
      { requestId: `${suffix}-baseline`, expectedVersion: before.dashboardLayout.version, cards: baselineCards },
      `${suffix}-baseline-header`,
    )
    try {
      await installSession(page, admin)
      await page.goto('/')
      const personalizeButton = page.getByRole('button', { name: '个性化', exact: true })
      await expect(personalizeButton).toBeVisible()
      await expect(page.locator('.dashboard-draggable')).toHaveCount(12)

      // open popup: two sections split by a divider, 3 layout radios, 12 checkboxes
      await personalizeButton.click()
      const popup = page.locator('.ant-popover:visible')
      await expect(popup).toBeVisible()
      await expect(popup.locator('.ant-divider')).toHaveCount(1)
      await expect(popup.getByText('卡片布局')).toBeVisible()
      await expect(popup.getByText('显示卡片')).toBeVisible()
      await expect(popup.getByRole('radio')).toHaveCount(3)
      await expect(popup.getByRole('radio', { name: /均衡双列/ })).toBeChecked()
      await expect(popup.locator('.dashboard-personalize-list .ant-checkbox-wrapper')).toHaveCount(12)
      // the five cards promoted from static sections are toggleable too
      for (const label of ['最近事项', '未读会话', '审批待办', '最新通知', '最近知识内容和表格']) {
        await expect(popup.locator('.ant-checkbox-wrapper', { hasText: label })).toHaveCount(1)
      }

      // compact three-column mode applies immediately and persists across reload
      await popup.getByRole('radio', { name: /紧凑三列/ }).click()
      await expect(page.locator('.dashboard-grid')).toHaveAttribute('data-layout', 'compact')
      await page.getByRole('heading', { name: '工作台' }).click()
      await expect(popup).toBeHidden()
      await page.reload()
      await expect(page.locator('.dashboard-grid')).toHaveAttribute('data-layout', 'compact')

      // focus mode spans only the first visible card; then restore balanced
      await personalizeButton.click()
      await popup.getByRole('radio', { name: /焦点主次/ }).click()
      await expect(page.locator('.dashboard-grid')).toHaveAttribute('data-layout', 'focus')
      await popup.getByRole('radio', { name: /均衡双列/ }).click()
      await expect(page.locator('.dashboard-grid')).toHaveAttribute('data-layout', 'balanced')
      await page.getByRole('heading', { name: '工作台' }).click()
      await expect(popup).toBeHidden()

      // uncheck the first card; persists across reload
      await personalizeButton.click()
      await expect(popup.locator('.ant-checkbox-wrapper')).toHaveCount(12)
      await popup.locator('.ant-checkbox-wrapper').first().click()
      await expect(popup.locator('.ant-checkbox-wrapper').first().locator('input')).not.toBeChecked()
      await expect(page.locator('.dashboard-draggable')).toHaveCount(11)
      await page.getByRole('heading', { name: '工作台' }).click()
      await page.reload()
      await expect(page.locator('.dashboard-draggable')).toHaveCount(11)
      await personalizeButton.click()
      await expect(popup.locator('.ant-checkbox-wrapper')).toHaveCount(12)
      await expect(popup.locator('.ant-checkbox-wrapper').first().locator('input')).not.toBeChecked()

      // hide and restore a newly added (previously static) card
      const unreadToggle = popup.locator('.ant-checkbox-wrapper', { hasText: '未读会话' })
      await unreadToggle.click()
      await expect(unreadToggle.locator('input')).not.toBeChecked()
      await expect(page.locator('.dashboard-draggable')).toHaveCount(10)
      await expect(page.locator('.dashboard-draggable', { hasText: '未读会话' })).toHaveCount(0)
      await unreadToggle.click()
      await expect(unreadToggle.locator('input')).toBeChecked()
      await expect(page.locator('.dashboard-draggable')).toHaveCount(11)
      await page.getByRole('heading', { name: '工作台' }).click()

      // drag the first card onto the second; order persists across reload
      const keysBefore = await page.locator('.dashboard-draggable').evaluateAll(
        (elements) => elements.map((element) => element.getAttribute('data-card-key')),
      )
      const first = page.locator('.dashboard-draggable').first()
      const second = page.locator('.dashboard-draggable').nth(1)
      const handleBox = await first.locator('.dashboard-drag-handle').boundingBox()
      const targetBox = await second.boundingBox()
      expect(handleBox, 'drag handle box').toBeTruthy()
      expect(targetBox, 'drop target box').toBeTruthy()
      if (!handleBox || !targetBox) throw new Error('drag geometry missing')
      await page.mouse.move(handleBox.x + 4, handleBox.y + 4)
      await page.mouse.down()
      await page.mouse.move(targetBox.x + targetBox.width / 2, targetBox.y + targetBox.height / 2, { steps: 12 })
      await page.mouse.up()
      await expect
        .poll(async () => page.locator('.dashboard-draggable').evaluateAll(
          (elements) => elements.map((element) => element.getAttribute('data-card-key')),
        ))
        .toEqual([keysBefore[1], keysBefore[0], ...keysBefore.slice(2)])
      await page.reload()
      await expect
        .poll(async () => page.locator('.dashboard-draggable').evaluateAll(
          (elements) => elements.map((element) => element.getAttribute('data-card-key')),
        ))
        .toEqual([keysBefore[1], keysBefore[0], ...keysBefore.slice(2)])
    } finally {
      await page.evaluate(() => {
        Object.keys(window.localStorage)
          .filter((key) => key.startsWith('colla.dashboard.layout-mode'))
          .forEach((key) => window.localStorage.removeItem(key))
      }).catch(() => undefined)
      const current = await getJson<Dashboard>(request, `${apiBaseUrl}/workspace/dashboard`, admin)
      await postJson(
        request,
        `${apiBaseUrl}/platform/personalization/dashboard`,
        admin,
        {
          requestId: `${suffix}-restore`,
          expectedVersion: current.dashboardLayout.version,
          cards: originalCards.map(({ cardKey, position, hidden }) => ({ cardKey, position, hidden })),
        },
        `${suffix}-restore-header`,
      )
    }
  })
})

async function validateAndPublish(
  request: APIRequestContext, session: E2eSession, spaceId: string, typeId: string, suffix: string,
) {
  const base = `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}`
  let draft = await getJson<{ aggregateVersion: number }>(request, `${base}/draft`, session)
  draft = await postJson(request, `${base}/draft:validate`, session,
    { expectedAggregateVersion: draft.aggregateVersion }, `${suffix}-validate`)
  await postJson(request, `${base}/draft:publish`, session,
    { expectedDraftAggregateVersion: draft.aggregateVersion, breakingConfirmed: false }, `${suffix}-publish`)
}

async function createSpace(request: APIRequestContext, session: E2eSession, suffix: string) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(session),
    data: { spaceKey: `s12-m2-${suffix.replaceAll('_', '-')}`, name: `S12 M2 ${suffix}`, visibility: 'private' },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  return (await response.json() as { id: string }).id
}

async function addMember(request: APIRequestContext, session: E2eSession, spaceId: string, userId: string) {
  const response = await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/members`, {
    headers: { ...bearer(session), 'X-Colla-Request-Id': `s12-m2-member-${userId}` },
    data: { userId, roleKey: 'member' },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
}

async function createIdentity(
  request: APIRequestContext, administrator: E2eSession, username: string, displayName: string,
) {
  const password = 'member123456'
  const response = await request.post(`${apiBaseUrl}/admin/users`, {
    headers: bearer(administrator),
    data: { username, password, displayName, email: `${username}@example.com`, roleCode: 'member' },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  return { ...(await response.json() as Omit<Identity, 'password'>), password }
}

async function getJson<T>(request: APIRequestContext, url: string, session: E2eSession) {
  const response = await request.get(url, { headers: bearer(session) })
  expect(response.ok(), `GET ${url} failed: ${await response.text()}`).toBeTruthy()
  return await response.json() as T
}

async function postJson<T = unknown>(
  request: APIRequestContext, url: string, session: E2eSession, data: unknown, requestId: string,
) {
  const response = await request.post(url, {
    headers: { ...bearer(session), 'X-Colla-Request-Id': requestId },
    data,
  })
  expect(response.ok(), `POST ${url} failed: ${await response.text()}`).toBeTruthy()
  return await response.json() as T
}
