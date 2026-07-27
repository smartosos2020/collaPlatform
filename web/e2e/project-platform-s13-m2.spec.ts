import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = { id: string; username: string; displayName: string; password: string }
type Item = { id: string; version: number; title: string }
type TypeSummary = { id: string; typeKey: string }
type ViewResult = {
  rows: Array<{ workItemId: string; title: string; version: number }>
  queryHash: string
}

const columns = [
  { key: 'displayKey', label: '编号', width: 120, frozen: true, format: 'text' },
  { key: 'title', label: '标题', width: 320, frozen: true, format: 'text' },
  { key: 'status', label: '状态', width: 120, frozen: false, format: 'tag' },
  { key: 'updatedAt', label: '更新于', width: 190, frozen: false, format: 'datetime' },
]

test.describe('PROJECT-PLATFORM-S13 M2', () => {
  test('table list bulk export and preferences stay permission scoped for six identities', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(480_000)
    page.setDefaultTimeout(25_000)
    requireIsolatedIdentityFixture()
    const enterprise = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(request, `${apiBaseUrl}/auth/me`, enterprise)
    const suffix = `s13m2_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterprise, `${suffix}_owner`, 'S13 View Owner')
    const adminIdentity = await createIdentity(request, enterprise, `${suffix}_admin`, 'S13 View Admin')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S13 View Member')
    const guestIdentity = await createIdentity(request, enterprise, `${suffix}_guest`, 'S13 View Guest')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S13 View Outsider')
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
      const title = `S13 表格与列表 ${'长名称'.repeat(24)}`
      const item = await createItem(request, owner, spaceId, projectType.id, title, `${suffix}-item`)
      const viewRequest = {
        schemaVersion: 1,
        mode: 'table',
        density: 'comfortable',
        columns,
        query: query(projectType.id),
      }

      for (const session of [owner, spaceAdmin, member, guest]) {
        const view = await postJson<ViewResult>(
          request,
          `${apiBaseUrl}/project-spaces/${spaceId}/work-item-views:render`,
          session,
          viewRequest,
          `${suffix}-render-${session.username}`,
        )
        expect(view.rows).toContainEqual(expect.objectContaining({ workItemId: item.id, title }))
      }
      for (const session of [outsider, enterprise]) {
        const response = await request.post(
          `${apiBaseUrl}/project-spaces/${spaceId}/work-item-views:render`,
          { headers: bearer(session), data: viewRequest },
        )
        expect([403, 404]).toContain(response.status())
        expect(await response.text()).not.toContain(title)
      }

      const preferenceRequest = {
        requestId: `${suffix}-preference`,
        expectedVersion: 0,
        mode: 'list',
        density: 'compact',
        columns,
      }
      const preference = await putJson<{ version: number }>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-item-views/preferences/default`,
        owner,
        preferenceRequest,
        `${suffix}-preference-header`,
      )
      const replay = await putJson<{ version: number }>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-item-views/preferences/default`,
        owner,
        preferenceRequest,
        `${suffix}-preference-replay`,
      )
      expect(replay.version).toBe(preference.version)

      const exportJob = await postJson<{ id: string; downloadPath: string }>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-item-views:export`,
        owner,
        { requestId: `${suffix}-export`, query: query(projectType.id), columns },
        `${suffix}-export-header`,
      )
      const download = await request.get(
        `${apiBaseUrl}/project-spaces/${spaceId}/work-item-views/exports/${exportJob.id}/download`,
        { headers: bearer(owner) },
      )
      expect(download.ok(), await download.text()).toBeTruthy()
      expect(await download.text()).toContain(title)
      const hiddenDownload = await request.get(
        `${apiBaseUrl}/project-spaces/${spaceId}/work-item-views/exports/${exportJob.id}/download`,
        { headers: bearer(outsider) },
      )
      expect([403, 404]).toContain(hiddenDownload.status())
      expect(await hiddenDownload.text()).not.toContain(title)

      const archived = await postJson<{
        succeeded: number
        items: Array<{ version?: number }>
      }>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-item-views:bulk`,
        owner,
        {
          requestId: `${suffix}-bulk-archive`,
          action: 'archive',
          selections: [{ workItemId: item.id, expectedVersion: item.version }],
        },
        `${suffix}-bulk-archive-header`,
      )
      expect(archived.succeeded).toBe(1)
      await postJson(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-item-views:bulk`,
        owner,
        {
          requestId: `${suffix}-bulk-restore`,
          action: 'restore',
          selections: [{ workItemId: item.id, expectedVersion: archived.items[0].version }],
        },
        `${suffix}-bulk-restore-header`,
      )

      await installSession(page, owner)
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        await page.goto(`/project-spaces/${spaceId}/work-items?typeId=${projectType.id}`)
        await expect(page.getByText(title).first()).toBeVisible()
        await expect(page.getByLabel('工作项视图模式')).toBeVisible()
        expect(await page.evaluate(() =>
          document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1)
      }
      await page.getByLabel('工作项视图模式').getByText('紧凑列表').click()
      await expect(page.locator('.project-work-item-compact-list')).toBeVisible()
      await page.screenshot({ path: testInfo.outputPath('s13-m2-list-820.png'), fullPage: true })
    } finally {
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
    select: columns.map((column) => column.key),
    limit: 50,
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
    request,
    `${base}/draft:validate`,
    owner,
    { expectedAggregateVersion: draft.aggregateVersion },
    `${suffix}-validate`,
  )
  await postJson(
    request,
    `${base}/draft:publish`,
    owner,
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

async function createSpace(request: APIRequestContext, owner: E2eSession, suffix: string) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(owner),
    data: {
      spaceKey: `s13-m2-${suffix.replaceAll('_', '-')}`,
      name: `S13 表格列表 ${suffix}`,
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s13-m2-member-${userId}` },
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
