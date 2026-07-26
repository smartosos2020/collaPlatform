import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = { id: string; username: string; displayName: string; password: string }
type Item = { id: string; version: number; displayKey: string }
type Presentation = {
  capability: string
  status?: string
  workItemVersion: number
  aggregateVersion: number
  tasks: Array<{ id: string; nodeKey: string; status: string }>
  availableActions: Array<{ actionKey: string }>
}

test.describe('PROJECT-PLATFORM-S09 route final', () => {
  test('node designer, runtime and six identity disclosure use real isolated services @route-final', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(420_000)
    page.setDefaultTimeout(20_000)
    requireIsolatedIdentityFixture()
    const enterpriseAdmin = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(request, `${apiBaseUrl}/auth/me`, enterpriseAdmin)
    const suffix = `s09m5_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_owner`, 'S09 Owner')
    const adminIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_admin`, 'S09 Admin')
    const memberIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_member`, 'S09 Member')
    const guestIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_guest`, 'S09 Guest')
    const outsiderIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_outsider`, 'S09 Outsider')
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
      const configured = await getJson<{ items: Array<{ id: string; typeKey: string; currentVersion: { id: string } }> }>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`,
        owner,
      )
      const type = configured.items.find((candidate) => candidate.typeKey === 'project')
      expect(type, 'project preset must be installed with the space').toBeTruthy()
      if (!type) throw new Error('project preset missing')
      const published = await validateAndPublish(request, owner, spaceId, type.id, suffix)
      const item = await postJson<Item>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-items`,
        owner,
        { typeId: type.id, title: `S09 节点流 ${'长名称'.repeat(28)}`, fieldValues: {} },
        `s09-item-${suffix}`,
      )

      await installSession(page, owner)
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto(`/project-spaces/${spaceId}/types/${type.id}`)
      const designer = page.getByTestId('work-item-node-flow-designer')
      await expect(designer).toBeVisible()
      await expect(designer).toContainText('8 节点')
      await expect(designer).toContainText('delivery_split')
      await designer.getByRole('button', { name: '放大画布' }).click()
      await expect(designer).toContainText('125%')
      await designer.locator('.node-flow-canvas').focus()
      await designer.locator('.node-flow-canvas').press('ArrowRight')
      await expect(designer).toContainText('未保存')

      const workflowUrl = `${apiBaseUrl}/project-spaces/${spaceId}/work-items/${item.id}/node-workflow`
      for (const session of [owner, spaceAdmin, member]) {
        const projection = await getJson<Presentation>(request, workflowUrl, session)
        expect(projection.capability).toBe('available')
        expect(projection.tasks.length).toBeGreaterThan(0)
      }
      const guestProjection = await getJson<Presentation>(request, workflowUrl, guest)
      expect(guestProjection.capability).toBe('available')
      expect(guestProjection.availableActions).toEqual([])
      for (const session of [outsider, enterpriseAdmin]) {
        const hidden = await request.get(workflowUrl, { headers: bearer(session) })
        expect(hidden.status()).toBe(404)
        expect(await hidden.text()).not.toContain('candidateRoles')
      }

      await installSession(page, member)
      await page.goto(`/project-spaces/${spaceId}/work-items/${item.id}`)
      const panel = page.getByTestId('work-item-node-workflow-panel')
      await expect(panel).toBeVisible()
      await expect(panel).toContainText('plan')
      await panel.getByRole('tab', { name: /任务/ }).click()
      await expect(panel).toContainText(/single|制定计划/)
      await panel.getByRole('tab', { name: /历史/ }).click()
      await expect(panel).toContainText(/started|task_created|entered/)
      await page.context().setOffline(true)
      await page.evaluate(() => {
        Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => false })
        window.dispatchEvent(new Event('offline'))
      })
      await expect(panel).toContainText('离线，只读；输入会保留')
      await panel.getByRole('tab', { name: /任务/ }).click()
      for (const action of await panel.locator('.node-task-execution button').all()) {
        await expect(action).toBeDisabled()
      }
      await page.context().setOffline(false)
      await page.evaluate(() => {
        Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => true })
        window.dispatchEvent(new Event('online'))
      })

      await installSession(page, guest)
      await page.goto(`/project-spaces/${spaceId}/work-items/${item.id}`)
      await expect(page.getByTestId('work-item-node-workflow-panel')).toBeVisible()
      await page.getByTestId('work-item-node-workflow-panel').getByRole('tab', { name: /任务/ }).click()
      await expect(page.getByTestId('work-item-node-workflow-panel').locator('.node-task-execution button')).toHaveCount(0)

      await installSession(page, owner)
      for (const width of [1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        await page.goto(`/project-spaces/${spaceId}/work-items/${item.id}`)
        await expect(page.getByTestId('work-item-node-workflow-panel')).toBeVisible()
        expect(await page.evaluate(() =>
          document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1)
      }
      await page.goto(`/project-spaces/${spaceId}/types/${type.id}`)
      await expect(page.getByTestId('work-item-node-flow-designer')).toBeVisible()
      await page.screenshot({ path: testInfo.outputPath('s09-node-flow-820.png'), fullPage: true })
      expect(published.version.id).toBeTruthy()
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
    `s09-validate-${suffix}`,
  )
  return postJson<{ version: { id: string } }>(
    request,
    `${base}/draft:publish`,
    owner,
    { expectedDraftAggregateVersion: draft.aggregateVersion, breakingConfirmed: false },
    `s09-publish-${suffix}`,
  )
}

async function createSpace(request: APIRequestContext, owner: E2eSession, suffix: string) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(owner),
    data: {
      spaceKey: `s09-m5-${suffix.replaceAll('_', '-')}`,
      name: `S09 节点流验收 ${suffix}`,
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s09-member-${userId}` },
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
