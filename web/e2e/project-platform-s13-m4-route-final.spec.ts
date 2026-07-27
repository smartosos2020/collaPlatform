import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = { id: string; username: string; displayName: string; password: string }
type TypeSummary = { id: string; typeKey: string }
type SavedView = {
  id: string
  name: string
  aggregateVersion: number
  ownerUserId: string
  canManage: boolean
  shares: Array<{ subjectUserId: string; status: string; permission: string }>
}

const columns = [
  { key: 'displayKey', label: '编号', width: 120, frozen: true, format: 'text' },
  { key: 'title', label: '标题', width: 320, frozen: true, format: 'text' },
  { key: 'status', label: '状态', width: 120, frozen: false, format: 'tag' },
  { key: 'updatedAt', label: '更新于', width: 190, frozen: false, format: 'datetime' },
]

test.describe('PROJECT-PLATFORM-S13 M4', () => {
  test('saved views replay, re-authorize, share, revoke, transfer and clean up for six identities @route-final', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(480_000)
    page.setDefaultTimeout(25_000)
    requireIsolatedIdentityFixture()
    const enterprise = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(request, `${apiBaseUrl}/auth/me`, enterprise)
    const suffix = `s13m4_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterprise, `${suffix}_owner`, 'S13 Saved Owner')
    const adminIdentity = await createIdentity(request, enterprise, `${suffix}_admin`, 'S13 Saved Admin')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S13 Saved Member')
    const guestIdentity = await createIdentity(request, enterprise, `${suffix}_guest`, 'S13 Saved Guest')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S13 Saved Outsider')
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
      const title = `S13 保存视图 ${'长名称'.repeat(20)}`
      await postJson(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-items`,
        owner,
        { typeId: projectType.id, title, fieldValues: {} },
        `${suffix}-item`,
      )

      const createBody = {
        requestId: `${suffix}-create`,
        name: `S13 团队视图 ${'长名称'.repeat(12)}`,
        description: '分享只分发查询与展示配置，执行结果仍由当前身份权限决定。',
        scope: 'personal',
        query: query(projectType.id),
        presentation: {
          schemaVersion: 1,
          mode: 'table',
          density: 'comfortable',
          columns,
          relationKey: 'parent_child',
          maxDepth: 32,
        },
      }
      const saved = await postJson<SavedView>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/saved-views`,
        owner,
        createBody,
        `${suffix}-create-header`,
      )
      const replay = await postJson<SavedView>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/saved-views`,
        owner,
        createBody,
        `${suffix}-create-replay-header`,
      )
      expect(replay).toEqual(saved)

      for (const session of [spaceAdmin, member, guest]) {
        const directory = await getJson<SavedView[]>(
          request,
          `${apiBaseUrl}/project-spaces/${spaceId}/saved-views`,
          session,
        )
        expect(directory).not.toContainEqual(expect.objectContaining({ id: saved.id }))
      }
      for (const session of [outsider, enterprise]) {
        const response = await request.get(
          `${apiBaseUrl}/project-spaces/${spaceId}/saved-views/${saved.id}`,
          { headers: bearer(session) },
        )
        expect([403, 404]).toContain(response.status())
        expect(await response.text()).not.toContain(saved.name)
      }

      const edit = (requestId: string, name: string) => request.patch(
        `${apiBaseUrl}/project-spaces/${spaceId}/saved-views/${saved.id}`,
        {
          headers: { ...bearer(owner), 'X-Colla-Request-Id': requestId },
          data: {
            requestId,
            expectedVersion: saved.aggregateVersion,
            name,
            description: createBody.description,
            scope: 'shared',
            query: query(projectType.id),
            presentation: createBody.presentation,
          },
        },
      )
      const edits = await Promise.all([
        edit(`${suffix}-edit-a`, `${saved.name} A`),
        edit(`${suffix}-edit-b`, `${saved.name} B`),
      ])
      expect(edits.filter((response) => response.ok())).toHaveLength(1)
      const rejectedEdit = edits.find((response) => !response.ok())
      expect(rejectedEdit).toBeTruthy()
      expect(
        rejectedEdit?.status(),
        rejectedEdit ? await rejectedEdit.text() : 'second update unexpectedly missing',
      ).toBe(409)
      let current = await edits.find((response) => response.ok())!.json() as SavedView

      current = await savedCommand(
        request, owner, spaceId, current, 'share',
        { subjectUserId: memberIdentity.id, permission: 'use' },
        `${suffix}-share-member`,
      )
      current = await savedCommand(
        request, owner, spaceId, current, 'share',
        { subjectUserId: adminIdentity.id, permission: 'manage' },
        `${suffix}-share-admin`,
      )
      current = await savedCommand(
        request, owner, spaceId, current, 'share',
        { subjectUserId: guestIdentity.id, permission: 'use' },
        `${suffix}-share-guest`,
      )
      for (const session of [owner, spaceAdmin, member, guest]) {
        const execution = await postJson<{ result: { rows: Array<{ title: string }> } }>(
          request,
          `${apiBaseUrl}/project-spaces/${spaceId}/saved-views/${saved.id}:execute`,
          session,
          undefined,
          `${suffix}-execute-${session.username}`,
        )
        expect(execution.result.rows).toContainEqual(expect.objectContaining({ title }))
      }

      await postJson(
        request,
        `${apiBaseUrl}/platform/personalization/favorites/saved_view/${saved.id}`,
        member,
        { requestId: `${suffix}-favorite`, favorite: true },
        `${suffix}-favorite-header`,
      )
      const favorites = await getJson<Array<{ objectId: string; title: string }>>(
        request,
        `${apiBaseUrl}/platform/favorites?limit=20`,
        member,
      )
      expect(favorites).toContainEqual(expect.objectContaining({ objectId: saved.id }))

      current = await savedCommand(
        request, owner, spaceId, current, 'revoke',
        { subjectUserId: memberIdentity.id },
        `${suffix}-revoke-member`,
      )
      const revoked = await request.get(
        `${apiBaseUrl}/project-spaces/${spaceId}/saved-views/${saved.id}`,
        { headers: bearer(member) },
      )
      expect(revoked.status()).toBe(404)
      expect(await revoked.text()).not.toContain(current.name)
      const cleanedFavorites = await getJson<Array<{ objectId: string }>>(
        request,
        `${apiBaseUrl}/platform/favorites?limit=20`,
        member,
      )
      expect(cleanedFavorites).not.toContainEqual(expect.objectContaining({ objectId: saved.id }))

      await installSession(page, owner)
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        await page.goto(`/project-spaces/${spaceId}/work-items?savedViewId=${saved.id}`)
        await expect(page.getByLabel('保存视图目录')).toBeVisible()
        await expect(page.getByText(current.name).first()).toBeVisible()
        await expect(page.getByText(title).first()).toBeVisible()
        expect(await page.evaluate(() =>
          document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1)
      }
      await page.getByRole('button', { name: /复\s*制/ }).click()
      await expect(page.getByText(/副本/).first()).toBeVisible()
      await page.context().setOffline(true)
      await page.evaluate(() => window.dispatchEvent(new Event('offline')))
      await expect(page.getByLabel('保存视图目录')).toBeVisible()
      await page.context().setOffline(false)
      await page.evaluate(() => window.dispatchEvent(new Event('online')))
      await page.screenshot({ path: testInfo.outputPath('s13-m4-saved-view-820.png'), fullPage: true })

      current = await getJson<SavedView>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/saved-views/${saved.id}`,
        owner,
      )
      current = await savedCommand(
        request, owner, spaceId, current, 'transfer',
        { newOwnerUserId: adminIdentity.id },
        `${suffix}-transfer-admin`,
      )
      expect(current.ownerUserId).toBe(adminIdentity.id)
      const formerOwner = await request.get(
        `${apiBaseUrl}/project-spaces/${spaceId}/saved-views/${saved.id}`,
        { headers: bearer(owner) },
      )
      expect(formerOwner.status()).toBe(404)
      const crossSpace = await request.get(
        `${apiBaseUrl}/project-spaces/00000000-0000-0000-0000-000000000001/saved-views/${saved.id}`,
        { headers: bearer(spaceAdmin) },
      )
      expect([403, 404]).toContain(crossSpace.status())
      await savedCommand(
        request, spaceAdmin, spaceId, current, 'delete', {},
        `${suffix}-delete`,
      )
      const deleted = await request.get(
        `${apiBaseUrl}/project-spaces/${spaceId}/saved-views/${saved.id}`,
        { headers: bearer(spaceAdmin) },
      )
      expect(deleted.status()).toBe(404)
      expect(await deleted.text()).not.toContain(current.name)
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
    select: columns.map((column) => column.key),
    limit: 50,
    cursor: null,
  }
}

async function savedCommand(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  view: SavedView,
  action: 'share' | 'revoke' | 'transfer' | 'delete',
  data: Record<string, unknown>,
  requestId: string,
) {
  return postJson<SavedView>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/saved-views/${view.id}:${action}`,
    session,
    { requestId, expectedVersion: view.aggregateVersion, ...data },
    `${requestId}-header`,
  )
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

async function createSpace(request: APIRequestContext, owner: E2eSession, suffix: string) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(owner),
    data: {
      spaceKey: `s13-m4-${suffix.replaceAll('_', '-')}`,
      name: `S13 保存视图 ${suffix}`,
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s13-m4-member-${userId}` },
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
