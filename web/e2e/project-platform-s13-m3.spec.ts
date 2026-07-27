import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = { id: string; username: string; displayName: string; password: string }
type Item = { id: string; version: number; displayKey: string; title: string }
type TypeSummary = { id: string; typeKey: string }
type Relation = {
  source: { id: string; version: number }
  target: { id: string; version: number }
}
type TreeResult = {
  items: Array<{
    id: string
    parentId?: string | null
    title: string
    visibleChildCount: number
    matchKind: string
  }>
  aggregate: { visibleNodeCount: number; maxVisibleDepth: number }
}

test.describe('PROJECT-PLATFORM-S13 M3', () => {
  test('canonical hierarchy tree stays lazy permission scoped and responsive for six identities', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(480_000)
    page.setDefaultTimeout(25_000)
    requireIsolatedIdentityFixture()
    const enterprise = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(request, `${apiBaseUrl}/auth/me`, enterprise)
    const suffix = `s13m3_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterprise, `${suffix}_owner`, 'S13 Tree Owner')
    const adminIdentity = await createIdentity(request, enterprise, `${suffix}_admin`, 'S13 Tree Admin')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S13 Tree Member')
    const guestIdentity = await createIdentity(request, enterprise, `${suffix}_guest`, 'S13 Tree Guest')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S13 Tree Outsider')
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
      let parent = await createItem(
        request, owner, spaceId, projectType.id,
        `S13 树根 ${'长名称'.repeat(22)}`, `${suffix}-parent`,
      )
      let child = await createItem(
        request, owner, spaceId, projectType.id, 'S13 树子项', `${suffix}-child`,
      )
      let grandchild = await createItem(
        request, owner, spaceId, projectType.id, 'S13 树孙项', `${suffix}-grandchild`,
      )
      const parentEdge = await createRelation(
        request, owner, spaceId, parent, child, `${suffix}-parent-child`,
      )
      parent = { ...parent, version: parentEdge.source.version }
      child = { ...child, version: parentEdge.target.version }
      const childEdge = await createRelation(
        request, owner, spaceId, child, grandchild, `${suffix}-child-grandchild`,
      )
      child = { ...child, version: childEdge.source.version }
      grandchild = { ...grandchild, version: childEdge.target.version }

      const rootRequest = treeRequest(projectType.id, null)
      for (const session of [owner, spaceAdmin, member, guest]) {
        const roots = await postJson<TreeResult>(
          request,
          `${apiBaseUrl}/project-spaces/${spaceId}/work-item-tree-views:render`,
          session,
          rootRequest,
          `${suffix}-tree-${session.username}`,
        )
        expect(roots.items).toContainEqual(expect.objectContaining({
          id: parent.id,
          visibleChildCount: 1,
        }))
        expect(roots.aggregate.visibleNodeCount).toBe(3)
        expect(roots.aggregate.maxVisibleDepth).toBe(2)
      }
      for (const session of [outsider, enterprise]) {
        const hidden = await request.post(
          `${apiBaseUrl}/project-spaces/${spaceId}/work-item-tree-views:render`,
          { headers: bearer(session), data: rootRequest },
        )
        expect([403, 404]).toContain(hidden.status())
        const body = await hidden.text()
        expect(body).not.toContain(parent.title)
        expect(body).not.toContain(child.title)
      }

      const children = await postJson<TreeResult>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-item-tree-views:render`,
        member,
        treeRequest(projectType.id, parent.id),
        `${suffix}-expand-parent`,
      )
      expect(children.items).toContainEqual(expect.objectContaining({
        id: child.id,
        parentId: parent.id,
      }))
      const path = await postJson<{ items: Array<{ id: string }> }>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-item-tree-views/${grandchild.id}:ancestors`,
        member,
        rootRequest,
        `${suffix}-path`,
      )
      expect(path.items.map((item) => item.id)).toEqual([parent.id, child.id, grandchild.id])

      const cycle = await rawCreateRelation(
        request, owner, spaceId, grandchild, parent, `${suffix}-cycle`,
      )
      expect(cycle.ok()).toBeFalsy()
      expect([409, 422]).toContain(cycle.status())

      const preference = await putJson<{ version: number; expandedNodeIds: string[] }>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-item-tree-views/preferences/default`,
        owner,
        {
          requestId: `${suffix}-preference`,
          expectedVersion: 0,
          relationKey: 'parent_child',
          expandedNodeIds: [parent.id],
        },
        `${suffix}-preference-header`,
      )
      expect(preference.version).toBe(1)
      expect(preference.expandedNodeIds).toEqual([parent.id])

      await installSession(page, owner)
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        await page.goto(`/project-spaces/${spaceId}/work-items?typeId=${projectType.id}`)
        await page.getByLabel('工作项视图模式').getByText('层级树').click()
        const tree = page.getByTestId('project-work-item-tree')
        await expect(tree).toContainText(parent.title)
        await expect(tree).toContainText('可见节点 3')
        const rootNode = tree.locator('.ant-tree-treenode').filter({ hasText: parent.title })
        if (!(await rootNode.getByText(child.title).count())) {
          await rootNode.locator('.ant-tree-switcher').click()
        }
        await expect(tree).toContainText(child.title)
        expect(await page.evaluate(() =>
          document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1)
      }
      const tree = page.getByTestId('project-work-item-tree')
      await tree.locator('.ant-tree-checkbox').first().click()
      const selection = page.getByText(/已选择 [1-9]\d* 项/)
      await expect(selection).toBeVisible()
      const selectionText = await selection.textContent()
      await page.getByLabel('工作项视图模式').getByText('表格').click()
      await expect(page.getByText(selectionText ?? '已选择')).toBeVisible()
      await page.context().setOffline(true)
      await page.evaluate(() => window.dispatchEvent(new Event('offline')))
      await page.getByLabel('工作项视图模式').getByText('层级树').click()
      await expect(page.getByTestId('project-work-item-tree')).toContainText(parent.title)
      await page.context().setOffline(false)
      await page.evaluate(() => window.dispatchEvent(new Event('online')))
      await page.screenshot({ path: testInfo.outputPath('s13-m3-tree-820.png'), fullPage: true })
    } finally {
      await page.context().setOffline(false).catch(() => undefined)
      if (spaceId) {
        await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`, {
          headers: bearer(owner),
        }).catch(() => undefined)
      }
      for (const identity of [
        adminIdentity, memberIdentity, guestIdentity, outsiderIdentity, ownerIdentity,
      ]) {
        await request.post(`${apiBaseUrl}/admin/users/${identity.id}/offboard`, {
          headers: bearer(enterprise),
          data: { handoverToUserId: enterpriseProfile.id },
        }).catch(() => undefined)
      }
    }
  })
})

function query(typeId: string) {
  return {
    schemaVersion: 1,
    typeId,
    filter: null,
    sorts: [{ field: 'updatedAt', direction: 'desc', nulls: 'last' }],
    group: null,
    select: ['displayKey', 'title', 'status', 'updatedAt'],
    limit: 100,
    cursor: null,
  }
}

function treeRequest(typeId: string, parentId: string | null) {
  return {
    schemaVersion: 1,
    relationKey: 'parent_child',
    query: query(typeId),
    parentId,
    limit: 50,
    maxDepth: 32,
    cursor: null,
  }
}

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
    request, `${base}/draft:validate`, owner,
    { expectedAggregateVersion: draft.aggregateVersion }, `${suffix}-validate`,
  )
  await postJson(
    request, `${base}/draft:publish`, owner,
    { expectedDraftAggregateVersion: draft.aggregateVersion, breakingConfirmed: false },
    `${suffix}-publish`,
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
  source: Item,
  target: Item,
  requestId: string,
) {
  const response = await rawCreateRelation(request, session, spaceId, source, target, requestId)
  expect(response.ok(), await response.text()).toBeTruthy()
  return await response.json() as Relation
}

function rawCreateRelation(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  source: Item,
  target: Item,
  requestId: string,
) {
  return request.post(`${apiBaseUrl}/project-spaces/${spaceId}/work-item-relations`, {
    headers: { ...bearer(session), 'X-Colla-Request-Id': requestId },
    data: {
      relationKey: 'parent_child',
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
      spaceKey: `s13-m3-${suffix.replaceAll('_', '-')}`,
      name: `S13 树视图 ${suffix}`,
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s13-m3-member-${userId}` },
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

async function putJson<T>(
  request: APIRequestContext,
  url: string,
  session: E2eSession,
  data: unknown,
  requestId: string,
) {
  const response = await request.put(url, {
    headers: { ...bearer(session), 'X-Colla-Request-Id': requestId },
    data,
  })
  expect(response.ok(), `PUT ${url} failed: ${await response.text()}`).toBeTruthy()
  return await response.json() as T
}
