import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = { id: string; username: string; displayName: string; password: string }
type Item = { id: string; version: number; title: string }
type TypeSummary = { id: string; typeKey: string }
type SearchPage = {
  items: Array<{ objectType: string; objectId: string; title: string; accessState: string; webPath: string }>
  facets: Array<{ key: string; value: string; count: number }>
  nextCursor?: string | null
}

test.describe('PROJECT-PLATFORM-S12 M3', () => {
  test('canonical work item search is scoped, filterable, stable and deep-linkable for six identities', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(480_000)
    page.setDefaultTimeout(25_000)
    requireIsolatedIdentityFixture()
    const enterpriseAdmin = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(request, `${apiBaseUrl}/auth/me`, enterpriseAdmin)
    const suffix = `s12m3_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const needle = suffix.replaceAll('_', '')
    const ownerIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_owner`, 'S12 Search Owner')
    const adminIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_admin`, 'S12 Search Admin')
    const memberIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_member`, 'S12 Search Member')
    const guestIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_guest`, 'S12 Search Guest')
    const outsiderIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_outsider`, 'S12 Search Outsider')
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
      const firstTitle = `S12 搜索 ${needle} ${'长名称'.repeat(28)}`
      const first = await createItem(request, owner, spaceId, projectType.id, firstTitle, `${suffix}-first`)
      const second = await createItem(request, owner, spaceId, projectType.id, `S12 搜索 ${needle} 第二项`, `${suffix}-second`)
      await putParticipant(request, owner, spaceId, first, memberIdentity.id, 'watcher')
      await rebuildWorkItems(request, enterpriseAdmin)

      for (const session of [owner, spaceAdmin, member, guest]) {
        const result = await search(request, session, needle, { objectTypes: ['work_item'], spaceIds: [spaceId] })
        expect(result.items.map((item) => item.objectId)).toEqual(expect.arrayContaining([first.id, second.id]))
        expect(result.items.every((item) => item.accessState === 'available')).toBeTruthy()
        expect(result.facets).toContainEqual(expect.objectContaining({ key: 'objectType', value: 'work_item' }))
      }
      for (const session of [outsider, enterpriseAdmin]) {
        const result = await search(request, session, needle, { objectTypes: ['work_item'], spaceIds: [spaceId] })
        expect(result.items).toEqual([])
        expect(JSON.stringify(result)).not.toContain(firstTitle)
      }

      const watcherResult = await search(request, member, needle, {
        objectTypes: ['work_item'],
        objectStatuses: ['active'],
        participantRoles: ['watcher'],
        spaceIds: [spaceId],
      })
      expect(watcherResult.items.map((item) => item.objectId)).toEqual([first.id])
      const guestWatcherResult = await search(request, guest, needle, {
        objectTypes: ['work_item'],
        participantRoles: ['watcher'],
        spaceIds: [spaceId],
      })
      expect(guestWatcherResult.items).toEqual([])

      const firstPage = await search(request, member, needle, {
        objectTypes: ['work_item'],
        spaceIds: [spaceId],
      }, 1)
      expect(firstPage.items).toHaveLength(1)
      expect(firstPage.nextCursor).toBeTruthy()
      const nextPage = await search(request, member, needle, {
        objectTypes: ['work_item'],
        spaceIds: [spaceId],
        cursor: firstPage.nextCursor ?? undefined,
      }, 1)
      expect(nextPage.items).toHaveLength(1)
      expect(nextPage.items[0].objectId).not.toBe(firstPage.items[0].objectId)
      const tamperedCursor = `${firstPage.nextCursor?.slice(0, -1)}x`
      const invalidCursor = await request.get(`${apiBaseUrl}/search`, {
        headers: bearer(member),
        params: { q: needle, objectTypes: 'work_item', cursor: tamperedCursor },
      })
      expect(invalidCursor.status()).toBe(400)

      await installSession(page, member)
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        await page.goto(`/search?q=${needle}&objectTypes=work_item&spaceIds=${spaceId}`)
        await expect(page.getByText(firstTitle, { exact: true })).toBeVisible()
        expect(await page.evaluate(() =>
          document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1)
      }
      await page.locator('.search-result-item').filter({ hasText: firstTitle }).getByRole('button', { name: '打开工作项' }).click()
      await expect(page).toHaveURL(new RegExp(`/project-spaces/${spaceId}/work-items/${first.id}$`))
      await page.screenshot({ path: testInfo.outputPath('s12-m3-search-work-item-820.png'), fullPage: true })

      await archiveItem(request, owner, spaceId, first)
      await rebuildWorkItems(request, enterpriseAdmin)
      const afterArchive = await search(request, member, needle, { objectTypes: ['work_item'], spaceIds: [spaceId] })
      expect(afterArchive.items.map((item) => item.objectId)).not.toContain(first.id)
      expect(JSON.stringify(afterArchive)).not.toContain(firstTitle)
    } finally {
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

async function search(
  request: APIRequestContext,
  session: E2eSession,
  query: string,
  filters: {
    objectTypes?: string[]
    objectStatuses?: string[]
    participantRoles?: string[]
    spaceIds?: string[]
    cursor?: string
  },
  limit = 20,
) {
  const params = new URLSearchParams({ q: query, limit: String(limit) })
  filters.objectTypes?.forEach((value) => params.append('objectTypes', value))
  filters.objectStatuses?.forEach((value) => params.append('objectStatuses', value))
  filters.participantRoles?.forEach((value) => params.append('participantRoles', value))
  filters.spaceIds?.forEach((value) => params.append('spaceIds', value))
  if (filters.cursor) params.set('cursor', filters.cursor)
  return getJson<SearchPage>(request, `${apiBaseUrl}/search?${params}`, session)
}

async function rebuildWorkItems(request: APIRequestContext, administrator: E2eSession) {
  let afterId: string | undefined
  do {
    const response = await request.post(`${apiBaseUrl}/admin/search-governance/reindex/batches`, {
      headers: bearer(administrator),
      data: {
        objectType: 'work_item',
        afterId,
        limit: 250,
        reason: 'PROJECT-PLATFORM-S12-M3 isolated verification',
      },
    })
    expect(response.ok(), await response.text()).toBeTruthy()
    const page = await response.json() as { nextCursor?: string; done: boolean }
    afterId = page.nextCursor
    if (page.done) break
  } while (afterId)
}

async function archiveItem(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  item: Item,
) {
  const response = await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/work-items/${item.id}:archive`, {
    headers: { ...bearer(session), 'X-Colla-Request-Id': `s12-search-archive-${item.id}` },
    data: { expectedVersion: item.version + 1 },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
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
    `s12-m3-validate-${suffix}`,
  )
  await postJson(
    request,
    `${base}/draft:publish`,
    owner,
    { expectedDraftAggregateVersion: draft.aggregateVersion, breakingConfirmed: false },
    `s12-m3-publish-${suffix}`,
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

async function putParticipant(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  item: Item,
  userId: string,
  role: string,
) {
  const response = await request.put(
    `${apiBaseUrl}/project-spaces/${spaceId}/work-items/${item.id}/participants/${userId}`,
    {
      headers: { ...bearer(session), 'X-Colla-Request-Id': `s12-search-participant-${userId}` },
      data: { role, expectedVersion: item.version },
    },
  )
  expect(response.ok(), await response.text()).toBeTruthy()
}

async function createSpace(request: APIRequestContext, owner: E2eSession, suffix: string) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(owner),
    data: {
      spaceKey: `s12-m3-${suffix.replaceAll('_', '-')}`,
      name: `S12 全局搜索 ${suffix}`,
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s12-search-member-${userId}` },
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
