import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

test.describe('PROJECT-PLATFORM-S04-M2 field options and validation rules', () => {
  test('real isolated identities configure and protect one field aggregate @smoke', async ({ request }) => {
    test.setTimeout(180_000)
    requireIsolatedIdentityFixture()
    const suffix = `s04m2_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const owner = await loginByApi(request)
    const ownerProfile = await getJson<{ id: string }>(request, `${apiBaseUrl}/auth/me`, owner)
    const spaceAdmin = await createIdentity(request, owner, `${suffix}_admin`, 'S04 M2 空间管理员', 'member')
    const member = await createIdentity(request, owner, `${suffix}_member`, 'S04 M2 成员', 'member')
    const guest = await createIdentity(request, owner, `${suffix}_guest`, 'S04 M2 访客', 'member')
    const outsider = await createIdentity(request, owner, `${suffix}_outsider`, 'S04 M2 非成员', 'member')
    const governor = await createIdentity(request, owner, `${suffix}_governor`, 'S04 M2 企业管理员', 'admin')
    let spaceId: string | undefined

    try {
      const spaceResponse = await request.post(`${apiBaseUrl}/project-spaces`, {
        headers: bearer(owner),
        data: {
          spaceKey: `s04-m2-${suffix.replaceAll('_', '-')}`,
          name: `S04 M2 字段配置空间 ${suffix}`,
          visibility: 'private',
        },
      })
      expect(spaceResponse.ok()).toBeTruthy()
      spaceId = (await spaceResponse.json() as { id: string }).id

      for (const [identity, roleKey] of [[spaceAdmin, 'admin'], [member, 'member'], [guest, 'guest']] as const) {
        const response = await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/members`, {
          headers: { ...bearer(owner), 'X-Colla-Request-Id': `s04-m2-member-${identity.id}` },
          data: { userId: identity.id, roleKey },
        })
        expect(response.ok(), `failed to add ${roleKey}`).toBeTruthy()
      }

      const typeResponse = await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`, {
        headers: { ...bearer(owner), 'X-Colla-Request-Id': `s04-m2-type-${suffix}` },
        data: { typeKey: `${suffix}_delivery`, name: 'Delivery', sortOrder: 10 },
      })
      expect(typeResponse.ok()).toBeTruthy()
      const typeId = (await typeResponse.json() as { id: string }).id
      const fieldsUrl = `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}/fields`

      const catalog = await getJson<{
        items: Array<{ key: string; supportsOptions: boolean; validationRuleKinds: string[] }>
      }>(request, `${apiBaseUrl}/project-spaces/${spaceId}/configuration/field-types`, owner)
      expect(catalog.items.find((item) => item.key === 'single_select')).toEqual(expect.objectContaining({
        supportsOptions: true,
        validationRuleKinds: ['allowed_values'],
      }))

      const create = await request.post(fieldsUrl, {
        headers: { ...bearer(owner), 'X-Colla-Request-Id': `s04-m2-field-${suffix}` },
        data: { fieldKey: 'priority', name: 'Priority', fieldType: 'single_select', config: {}, sortOrder: 10 },
      })
      expect(create.ok()).toBeTruthy()
      const field = await create.json() as { id: string }
      const configurationUrl = `${fieldsUrl}/${field.id}/configuration`
      const requestId = `s04-m2-config-${suffix}`
      const data = {
        schemaVersion: 1,
        required: true,
        defaultValue: 'high',
        validationRules: [{
          ruleKey: 'allowed', kind: 'allowed_values', schemaVersion: 1,
          config: { values: ['low', 'high'] },
        }],
        options: [
          { optionKey: 'high', name: 'High', color: '#EF4444', sortOrder: 10, status: 'active' },
          { optionKey: 'low', name: 'Low', color: '#22C55E', sortOrder: 20, status: 'active' },
        ],
        aggregateVersion: 0,
      }
      const configured = await putJson<{
        aggregateVersion: number
        config: { defaultValue: string }
        options: Array<{ optionKey: string }>
        availableActions: string[]
      }>(request, configurationUrl, owner, requestId, data, 200)
      expect(configured.aggregateVersion).toBe(1)
      expect(configured.config.defaultValue).toBe('high')
      expect(configured.options.map((option) => option.optionKey)).toEqual(['high', 'low'])
      expect(configured.availableActions).toContain('configure')

      const replay = await putJson<{ aggregateVersion: number }>(
        request, configurationUrl, owner, requestId, data, 200,
      )
      expect(replay.aggregateVersion).toBe(1)

      const stale = await request.put(configurationUrl, {
        headers: { ...bearer(owner), 'X-Colla-Request-Id': `s04-m2-stale-${suffix}` },
        data: { ...data, defaultValue: 'low', aggregateVersion: 0 },
      })
      expect(stale.status()).toBe(409)
      expect((await stale.json() as { error: { code: string } }).error.code).toBe('version_conflict')

      const adminSession = await loginByApi(request, spaceAdmin.username, spaceAdmin.password)
      const adminUpdate = await request.put(configurationUrl, {
        headers: { ...bearer(adminSession), 'X-Colla-Request-Id': `s04-m2-admin-${suffix}` },
        data: { ...data, defaultValue: 'low', validationRules: [], aggregateVersion: 1 },
      })
      expect(adminUpdate.status()).toBe(200)

      for (const identity of [member, guest]) {
        const session = await loginByApi(request, identity.username, identity.password)
        const response = await request.put(configurationUrl, {
          headers: { ...bearer(session), 'X-Colla-Request-Id': `s04-m2-denied-${identity.id}` },
          data: { ...data, aggregateVersion: 2 },
        })
        expect(response.status()).toBe(403)
      }
      for (const identity of [outsider, governor]) {
        const session = await loginByApi(request, identity.username, identity.password)
        const response = await request.put(configurationUrl, {
          headers: { ...bearer(session), 'X-Colla-Request-Id': `s04-m2-hidden-${identity.id}` },
          data: { ...data, aggregateVersion: 2 },
        })
        expect(response.status()).toBe(404)
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

async function putJson<T>(
  request: APIRequestContext,
  url: string,
  session: E2eSession,
  requestId: string,
  data: object,
  status: number,
) {
  const response = await request.put(url, {
    headers: { ...bearer(session), 'X-Colla-Request-Id': requestId },
    data,
  })
  expect(response.status()).toBe(status)
  return await response.json() as T
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
