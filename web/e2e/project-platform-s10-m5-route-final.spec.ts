import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = { id: string; username: string; displayName: string; password: string }
type Item = { id: string; version: number; displayKey: string; title: string }
type TypeSummary = { id: string; typeKey: string }
type Relation = {
  id: string
  version: number
  relationKey: string
  source: { id: string; version: number }
  target: { id: string; version: number }
}

test.describe('PROJECT-PLATFORM-S10 route final', () => {
  test('relation configuration, member graph, migration and six identities close on real isolated services @route-final', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(480_000)
    page.setDefaultTimeout(25_000)
    requireIsolatedIdentityFixture()
    const enterpriseAdmin = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(request, `${apiBaseUrl}/auth/me`, enterpriseAdmin)
    const suffix = `s10m5_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_owner`, 'S10 Owner')
    const adminIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_admin`, 'S10 Admin')
    const memberIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_member`, 'S10 Member')
    const guestIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_guest`, 'S10 Guest')
    const outsiderIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_outsider`, 'S10 Outsider')
    const owner = await loginByApi(request, ownerIdentity.username, ownerIdentity.password)
    const spaceAdmin = await loginByApi(request, adminIdentity.username, adminIdentity.password)
    const member = await loginByApi(request, memberIdentity.username, memberIdentity.password)
    const guest = await loginByApi(request, guestIdentity.username, guestIdentity.password)
    const outsider = await loginByApi(request, outsiderIdentity.username, outsiderIdentity.password)
    let spaceId: string | undefined

    try {
      spaceId = await createSpace(request, owner, suffix)
      await addMember(request, owner, spaceId, adminIdentity.id, 'admin')
      await addMember(request, owner, spaceId, memberIdentity.id, 'member')
      await addMember(request, owner, spaceId, guestIdentity.id, 'guest')
      const types = await getJson<{ items: TypeSummary[] }>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`,
        owner,
      )
      const projectType = types.items.find((candidate) => candidate.typeKey === 'project')
      expect(projectType).toBeTruthy()
      if (!projectType) throw new Error('project preset missing')
      await validateAndPublish(request, owner, spaceId, projectType.id, suffix)

      const parent = await createItem(request, owner, spaceId, projectType.id, `S10 父项 ${'长名称'.repeat(24)}`, `${suffix}-parent`)
      const child = await createItem(request, owner, spaceId, projectType.id, 'S10 子项', `${suffix}-child`)
      const target = await createItem(request, owner, spaceId, projectType.id, 'S10 关系目标', `${suffix}-target`)
      const concurrentTarget = await createItem(request, owner, spaceId, projectType.id, 'S10 并发目标', `${suffix}-concurrent`)

      await createRelation(request, owner, spaceId, 'parent_child', parent, child, `${suffix}-hierarchy`)
      await createRelation(request, owner, spaceId, 'depends_on', child, target, `${suffix}-dependency`)

      const concurrent = await Promise.all([
        rawCreateRelation(request, owner, spaceId, 'relates_to', parent, concurrentTarget, `${suffix}-race-a`),
        rawCreateRelation(request, owner, spaceId, 'relates_to', parent, concurrentTarget, `${suffix}-race-b`),
      ])
      expect(concurrent.filter((response) => response.ok())).toHaveLength(1)
      const loserStatuses = concurrent.filter((response) => !response.ok())
        .map((response) => response.status())
      expect(loserStatuses).toHaveLength(1)
      expect([409, 422]).toContain(loserStatuses[0])

      const cycle = await rawCreateRelation(
        request, owner, spaceId, 'parent_child', child, parent, `${suffix}-cycle`,
      )
      expect(cycle.ok()).toBeFalsy()
      expect([409, 422]).toContain(cycle.status())

      const summaryUrl = `${apiBaseUrl}/project-spaces/${spaceId}/work-item-relation-experience/summary?workItemId=${child.id}&limit=100`
      for (const session of [owner, spaceAdmin, member, guest]) {
        const summary = await getJson<{ items: Relation[] }>(request, summaryUrl, session)
        expect(summary.items.length).toBeGreaterThanOrEqual(2)
      }
      for (const session of [outsider, enterpriseAdmin]) {
        const hidden = await request.get(summaryUrl, { headers: bearer(session) })
        expect(hidden.status()).toBe(404)
        expect(await hidden.text()).not.toContain(child.title)
      }

      await installSession(page, owner)
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto(`/project-spaces/${spaceId}/types/${projectType.id}`)
      await expect(page.getByText('关系定义', { exact: true })).toBeVisible()
      const editor = page.locator('.work-item-relation-definition-editor')
      await expect(editor).toContainText('parent_child')
      await expect(editor).toContainText('depends_on')
      await expect(editor).toContainText('blocks')

      await installSession(page, member)
      await page.goto(`/project-spaces/${spaceId}/work-items/${child.id}`)
      const panel = page.locator('.work-item-relations-panel')
      await expect(panel).toBeVisible()
      await expect(panel).toContainText('S10 父项')
      await expect(panel).toContainText('S10 关系目标')
      await panel.getByRole('tab', { name: '局部层级' }).click()
      await expect(panel).toContainText(parent.displayKey)
      await expect(panel.getByText('替代列表（键盘可导航）')).toBeVisible()
      await panel.getByRole('tab', { name: '影响分析' }).click()
      await expect(panel).toContainText(/上游|下游/)
      await expect(panel).toContainText(target.displayKey)

      await panel.getByRole('tab', { name: '关系' }).click()
      const targetSelect = panel.getByLabel('关系目标')
      await targetSelect.click()
      await targetSelect.fill('并发目标')
      await expect(page.getByText(/S10 并发目标/)).toBeVisible()
      await page.context().setOffline(true)
      await page.evaluate(() => {
        Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => false })
        window.dispatchEvent(new Event('offline'))
      })
      await expect(panel.getByRole('button', { name: '建立关系' })).toBeDisabled()
      await page.context().setOffline(false)
      await page.evaluate(() => {
        Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => true })
        window.dispatchEvent(new Event('online'))
      })

      await installSession(page, guest)
      await page.goto(`/project-spaces/${spaceId}/work-items/${child.id}`)
      await expect(page.locator('.work-item-relations-panel')).toBeVisible()
      await expect(page.locator('.work-item-relations-panel').getByRole('button', { name: '建立关系' })).toHaveCount(0)

      await installSession(page, owner)
      await page.goto(`/project-spaces/${spaceId}/work-items/${child.id}`)
      await page.locator('.work-item-relations-panel').getByRole('tab', { name: 'Legacy 承接' }).click()
      await page.locator('.work-item-relation-migration-tab').getByRole('button', { name: 'Dry-run plan' }).click()
      await expect(page.locator('.work-item-relation-migration-tab')).toContainText(/planned|总计 0/)

      for (const width of [1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        await page.goto(`/project-spaces/${spaceId}/work-items/${child.id}`)
        await expect(page.locator('.work-item-relations-panel')).toBeVisible()
        expect(await page.evaluate(() =>
          document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1)
      }
      await page.screenshot({ path: testInfo.outputPath('s10-relations-820.png'), fullPage: true })
    } finally {
      await page.context().setOffline(false).catch(() => undefined)
      if (spaceId) {
        await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`, {
          headers: bearer(owner),
        }).catch(() => undefined)
      }
      for (const identity of [adminIdentity, memberIdentity, guestIdentity, outsiderIdentity, ownerIdentity]) {
        await request.post(`${apiBaseUrl}/admin/users/${identity.id}/offboard`, {
          headers: bearer(enterpriseAdmin),
          data: { handoverToUserId: enterpriseProfile.id },
        }).catch(() => undefined)
      }
    }
  })
})

async function validateAndPublish(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  typeId: string,
  suffix: string,
) {
  const base = `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}`
  let draft = await getJson<{ aggregateVersion: number }>(request, `${base}/draft`, owner)
  draft = await postJson(
    request,
    `${base}/draft:validate`,
    owner,
    { expectedAggregateVersion: draft.aggregateVersion },
    `s10-validate-${suffix}`,
  )
  await postJson(
    request,
    `${base}/draft:publish`,
    owner,
    { expectedDraftAggregateVersion: draft.aggregateVersion, breakingConfirmed: false },
    `s10-publish-${suffix}`,
  )
}

async function createItem(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  typeId: string,
  title: string,
  requestId: string,
) {
  return postJson<Item>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/work-items`,
    session,
    { typeId, title, fieldValues: {} },
    requestId,
  )
}

async function createRelation(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  relationKey: string,
  source: Item,
  target: Item,
  requestId: string,
) {
  const response = await rawCreateRelation(request, session, spaceId, relationKey, source, target, requestId)
  expect(response.ok(), await response.text()).toBeTruthy()
  return await response.json() as Relation
}

function rawCreateRelation(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  relationKey: string,
  source: Item,
  target: Item,
  requestId: string,
) {
  return request.post(`${apiBaseUrl}/project-spaces/${spaceId}/work-item-relations`, {
    headers: { ...bearer(session), 'X-Colla-Request-Id': requestId },
    data: {
      relationKey,
      sourceWorkItemId: source.id,
      targetWorkItemId: target.id,
      expectedSourceVersion: source.version,
      expectedTargetVersion: target.version,
    },
  })
}

async function createSpace(request: APIRequestContext, owner: E2eSession, suffix: string) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(owner),
    data: {
      spaceKey: `s10-m5-${suffix.replaceAll('_', '-')}`,
      name: `S10 关系验收 ${suffix}`,
      visibility: 'private',
    },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s10-member-${userId}` },
    data: { userId, roleKey },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
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

async function postJson<T>(
  request: APIRequestContext,
  url: string,
  session: E2eSession,
  data: unknown,
  requestId: string,
) {
  const response = await request.post(url, {
    headers: { ...bearer(session), 'X-Colla-Request-Id': requestId },
    data,
  })
  expect(response.ok(), `POST ${url} failed: ${await response.text()}`).toBeTruthy()
  return await response.json() as T
}
