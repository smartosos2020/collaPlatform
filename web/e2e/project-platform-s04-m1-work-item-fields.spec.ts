import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

test.describe('PROJECT-PLATFORM-S04-M1 work-item field foundation', () => {
  test('isolated identities exercise field catalog, lifecycle, idempotency, and space RBAC @smoke', async ({ request }) => {
    test.setTimeout(180_000)
    requireIsolatedIdentityFixture()
    const suffix = `s04m1_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const owner = await loginByApi(request)
    const ownerProfile = await getJson<{ id: string }>(request, `${apiBaseUrl}/auth/me`, owner)
    const spaceAdmin = await createIdentity(request, owner, `${suffix}_admin`, 'S04 M1 空间管理员', 'member')
    const member = await createIdentity(request, owner, `${suffix}_member`, 'S04 M1 协作成员', 'member')
    const guest = await createIdentity(request, owner, `${suffix}_guest`, 'S04 M1 访客', 'member')
    const outsider = await createIdentity(request, owner, `${suffix}_outsider`, 'S04 M1 非成员', 'member')
    const governor = await createIdentity(request, owner, `${suffix}_governor`, 'S04 M1 企业管理员', 'admin')
    let spaceId: string | undefined

    try {
      const spaceResponse = await request.post(`${apiBaseUrl}/project-spaces`, {
        headers: bearer(owner),
        data: {
          spaceKey: `s04-m1-${suffix.replaceAll('_', '-')}`,
          name: `S04 M1 字段空间 ${suffix}`,
          visibility: 'private',
        },
      })
      expect(spaceResponse.ok()).toBeTruthy()
      spaceId = (await spaceResponse.json() as { id: string }).id

      for (const [identity, roleKey] of [[spaceAdmin, 'admin'], [member, 'member'], [guest, 'guest']] as const) {
        const response = await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/members`, {
          headers: { ...bearer(owner), 'X-Colla-Request-Id': `s04-m1-member-${identity.id}` },
          data: { userId: identity.id, roleKey },
        })
        expect(response.ok(), `failed to add ${roleKey}`).toBeTruthy()
      }

      const typeResponse = await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`, {
        headers: { ...bearer(owner), 'X-Colla-Request-Id': `s04-m1-type-${suffix}` },
        data: { typeKey: `${suffix}_delivery`, name: 'Delivery', sortOrder: 10 },
      })
      expect(typeResponse.ok()).toBeTruthy()
      const typeId = (await typeResponse.json() as { id: string }).id
      const fieldsUrl = `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}/fields`

      const catalog = await getJson<{ items: Array<{ key: string; operators: string[] }> }>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/configuration/field-types`,
        owner,
      )
      expect(catalog.items.map((item) => item.key)).toEqual([
        'text', 'number', 'boolean', 'single_select', 'multi_select', 'user',
        'date', 'datetime', 'url', 'attachment', 'work_item_reference',
      ])

      const createRequestId = `s04-m1-field-title-${suffix}`
      const title = await createField(request, owner, fieldsUrl, createRequestId, {
        fieldKey: 'title', name: 'Title', fieldType: 'text', config: {}, sortOrder: 20,
      })
      const replay = await createField(request, owner, fieldsUrl, createRequestId, {
        fieldKey: 'title', name: 'Title', fieldType: 'text', config: {}, sortOrder: 20,
      })
      expect(replay.id).toBe(title.id)
      const points = await createField(request, owner, fieldsUrl, `s04-m1-field-points-${suffix}`, {
        fieldKey: 'points', name: 'Points', fieldType: 'number', config: {}, sortOrder: 10,
      })

      const update = await request.patch(`${fieldsUrl}/${title.id}`, {
        headers: { ...bearer(owner), 'X-Colla-Request-Id': `s04-m1-update-${suffix}` },
        data: { name: 'Summary', description: 'Canonical title', config: {}, aggregateVersion: 0 },
      })
      expect(update.ok()).toBeTruthy()

      const reorder = await request.put(`${fieldsUrl}:reorder`, {
        headers: { ...bearer(owner), 'X-Colla-Request-Id': `s04-m1-reorder-${suffix}` },
        data: {
          items: [
            { fieldId: title.id, sortOrder: 10, aggregateVersion: 1 },
            { fieldId: points.id, sortOrder: 20, aggregateVersion: 0 },
          ],
        },
      })
      expect(reorder.ok()).toBeTruthy()

      let version = 2
      for (const [action, expected] of [['disable', 'disabled'], ['restore', 'active']] as const) {
        const response = await request.post(`${fieldsUrl}/${title.id}:${action}`, {
          headers: { ...bearer(owner), 'X-Colla-Request-Id': `s04-m1-${action}-${suffix}` },
          data: { aggregateVersion: version },
        })
        expect(response.ok()).toBeTruthy()
        expect((await response.json() as { status: string }).status).toBe(expected)
        version += 1
      }
      const retire = await request.post(`${fieldsUrl}/${points.id}:retire`, {
        headers: { ...bearer(owner), 'X-Colla-Request-Id': `s04-m1-retire-${suffix}` },
        data: { aggregateVersion: 1 },
      })
      expect(retire.ok()).toBeTruthy()

      const configured = await getJson<{ items: Array<{ fieldKey: string; status: string }> }>(request, fieldsUrl, owner)
      expect(configured.items).toEqual(expect.arrayContaining([
        expect.objectContaining({ fieldKey: 'title', status: 'active' }),
        expect.objectContaining({ fieldKey: 'points', status: 'retired' }),
      ]))

      const adminSession = await loginByApi(request, spaceAdmin.username, spaceAdmin.password)
      expect((await request.get(fieldsUrl, { headers: bearer(adminSession) })).status()).toBe(200)
      for (const identity of [member, guest]) {
        const session = await loginByApi(request, identity.username, identity.password)
        expect((await request.get(fieldsUrl, { headers: bearer(session) })).status()).toBe(403)
      }
      for (const identity of [outsider, governor]) {
        const session = await loginByApi(request, identity.username, identity.password)
        expect((await request.get(fieldsUrl, { headers: bearer(session) })).status()).toBe(404)
      }
    } finally {
      if (spaceId) await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`, { headers: bearer(owner) })
      for (const identity of [spaceAdmin, member, guest, outsider, governor]) {
        await request.post(`${apiBaseUrl}/admin/users/${identity.id}/offboard`, {
          headers: bearer(owner),
          data: { handoverToUserId: ownerProfile.id },
        })
      }
    }
  })
})

async function createField(
  request: APIRequestContext,
  session: E2eSession,
  url: string,
  requestId: string,
  data: { fieldKey: string; name: string; fieldType: string; config: object; sortOrder: number },
) {
  const response = await request.post(url, {
    headers: { ...bearer(session), 'X-Colla-Request-Id': requestId },
    data,
  })
  expect(response.ok()).toBeTruthy()
  return await response.json() as { id: string }
}

async function createIdentity(
  request: APIRequestContext,
  administrator: E2eSession,
  username: string,
  displayName: string,
  roleCode: 'member' | 'admin',
) {
  const password = 'member123456'
  const response = await request.post(`${apiBaseUrl}/admin/users`, {
    headers: bearer(administrator),
    data: { username, password, displayName, email: `${username}@example.com`, roleCode },
  })
  expect(response.ok(), `identity fixture creation failed for ${username}`).toBeTruthy()
  const payload = await response.json() as { id: string; username: string; displayName: string }
  return { ...payload, password }
}

async function getJson<T>(request: APIRequestContext, url: string, session: E2eSession) {
  const response = await request.get(url, { headers: bearer(session) })
  expect(response.ok(), `GET ${url} failed`).toBeTruthy()
  return await response.json() as T
}
