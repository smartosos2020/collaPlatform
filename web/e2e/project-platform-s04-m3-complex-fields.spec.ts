import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

test.describe('PROJECT-PLATFORM-S04-M3 complex field contracts', () => {
  test('real isolated identities configure and protect complex field semantics @smoke', async ({ request }) => {
    test.setTimeout(180_000)
    requireIsolatedIdentityFixture()
    const suffix = `s04m3_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const owner = await loginByApi(request)
    const ownerProfile = await getJson<{ id: string }>(request, `${apiBaseUrl}/auth/me`, owner)
    const spaceAdmin = await createIdentity(request, owner, `${suffix}_admin`, 'S04 M3 空间管理员', 'member')
    const member = await createIdentity(request, owner, `${suffix}_member`, 'S04 M3 成员', 'member')
    const guest = await createIdentity(request, owner, `${suffix}_guest`, 'S04 M3 访客', 'member')
    const outsider = await createIdentity(request, owner, `${suffix}_outsider`, 'S04 M3 非成员', 'member')
    const governor = await createIdentity(request, owner, `${suffix}_governor`, 'S04 M3 企业管理员', 'admin')
    let spaceId: string | undefined

    try {
      const spaceResponse = await request.post(`${apiBaseUrl}/project-spaces`, {
        headers: bearer(owner),
        data: {
          spaceKey: `s04-m3-${suffix.replaceAll('_', '-')}`,
          name: `S04 M3 复杂字段空间 ${suffix}`,
          visibility: 'private',
        },
      })
      expect(spaceResponse.ok()).toBeTruthy()
      spaceId = (await spaceResponse.json() as { id: string }).id

      for (const [identity, roleKey] of [[spaceAdmin, 'admin'], [member, 'member'], [guest, 'guest']] as const) {
        const response = await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/members`, {
          headers: { ...bearer(owner), 'X-Colla-Request-Id': `s04-m3-member-${identity.id}` },
          data: { userId: identity.id, roleKey },
        })
        expect(response.ok(), `failed to add ${roleKey}`).toBeTruthy()
      }

      const deliveryType = await createType(request, owner, spaceId, `${suffix}_delivery`, 'Delivery')
      const dependencyType = await createType(request, owner, spaceId, `${suffix}_dependency`, 'Dependency')
      const fieldsUrl = `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${deliveryType}/fields`

      const catalog = await getJson<{
        items: Array<{
          key: string
          sortable: boolean
          operators: string[]
          valueSchema: object
          typeConfigSchema: object
          referencePolicy: string
          invalidReferencePolicy: string
        }>
      }>(request, `${apiBaseUrl}/project-spaces/${spaceId}/configuration/field-types`, owner)
      expect(catalog.items.find((item) => item.key === 'url')).toEqual(expect.objectContaining({
        sortable: false,
        operators: ['eq', 'contains', 'is_empty'],
        referencePolicy: 'not_applicable',
      }))
      expect(catalog.items.find((item) => item.key === 'attachment')).toEqual(expect.objectContaining({
        referencePolicy: 'file_module',
        invalidReferencePolicy: 'unavailable_without_snapshot',
      }))
      expect(catalog.items.find((item) => item.key === 'work_item_reference')).toEqual(expect.objectContaining({
        referencePolicy: 'work_item_type_module',
      }))

      const urlField = await createField(request, owner, fieldsUrl, 'source_url', 'Source URL', 'url')
      const configurationUrl = `${fieldsUrl}/${urlField}/configuration`
      const requestId = `s04-m3-url-${suffix}`
      const urlConfiguration = {
        schemaVersion: 1,
        required: false,
        defaultValue: 'HTTPS://Example.COM:443/a/../delivery',
        validationRules: [],
        typeConfig: {
          allowedSchemes: ['https'],
          maxLength: 512,
          allowCredentials: false,
        },
        options: [],
        aggregateVersion: 0,
      }
      const configured = await putJson<{
        aggregateVersion: number
        config: { defaultValue: string; typeConfig: { allowedSchemes: string[] } }
      }>(request, configurationUrl, owner, requestId, urlConfiguration, 200)
      expect(configured.aggregateVersion).toBe(1)
      expect(configured.config.defaultValue).toBe('https://example.com/delivery')
      expect(configured.config.typeConfig.allowedSchemes).toEqual(['https'])

      const replay = await putJson<{ aggregateVersion: number }>(
        request, configurationUrl, owner, requestId, urlConfiguration, 200,
      )
      expect(replay.aggregateVersion).toBe(1)

      const dangerous = await request.put(configurationUrl, {
        headers: { ...bearer(owner), 'X-Colla-Request-Id': `s04-m3-url-danger-${suffix}` },
        data: {
          ...urlConfiguration,
          defaultValue: 'https://operator:secret@example.com/private',
          aggregateVersion: 1,
        },
      })
      expect(dangerous.status()).toBe(400)
      expect((await dangerous.json() as { error: { code: string } }).error.code)
        .toBe('invalid_default_value')

      const referenceField = await createField(
        request, owner, fieldsUrl, 'dependencies', 'Dependencies', 'work_item_reference',
      )
      const referenceUrl = `${fieldsUrl}/${referenceField}/configuration`
      const referenceConfiguration = {
        schemaVersion: 1,
        required: false,
        defaultValue: [],
        validationRules: [],
        typeConfig: {
          targetTypeIds: [dependencyType],
          maxReferences: 5,
          direction: 'outbound',
          relationCapability: 'deferred',
        },
        options: [],
        aggregateVersion: 0,
      }
      const reference = await putJson<{
        config: { defaultValue: string[]; typeConfig: { targetTypeIds: string[] } }
      }>(request, referenceUrl, owner, `s04-m3-reference-${suffix}`, referenceConfiguration, 200)
      expect(reference.config.defaultValue).toEqual([])
      expect(reference.config.typeConfig.targetTypeIds).toEqual([dependencyType])

      const adminSession = await loginByApi(request, spaceAdmin.username, spaceAdmin.password)
      const adminUpdate = await request.put(configurationUrl, {
        headers: { ...bearer(adminSession), 'X-Colla-Request-Id': `s04-m3-admin-${suffix}` },
        data: { ...urlConfiguration, defaultValue: 'https://example.com/admin', aggregateVersion: 1 },
      })
      expect(adminUpdate.status()).toBe(200)

      for (const identity of [member, guest]) {
        const session = await loginByApi(request, identity.username, identity.password)
        const response = await request.put(configurationUrl, {
          headers: { ...bearer(session), 'X-Colla-Request-Id': `s04-m3-denied-${identity.id}` },
          data: { ...urlConfiguration, aggregateVersion: 2 },
        })
        expect(response.status()).toBe(403)
      }
      for (const identity of [outsider, governor]) {
        const session = await loginByApi(request, identity.username, identity.password)
        const response = await request.put(configurationUrl, {
          headers: { ...bearer(session), 'X-Colla-Request-Id': `s04-m3-hidden-${identity.id}` },
          data: { ...urlConfiguration, aggregateVersion: 2 },
        })
        expect(response.status()).toBe(404)
      }
    } finally {
      if (spaceId) {
        await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`, {
          headers: bearer(owner),
        })
      }
      for (const identity of [spaceAdmin, member, guest, outsider, governor]) {
        await request.post(`${apiBaseUrl}/admin/users/${identity.id}/offboard`, {
          headers: bearer(owner),
          data: { handoverToUserId: ownerProfile.id },
        })
      }
    }
  })
})

async function createType(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  typeKey: string,
  name: string,
) {
  const response = await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`, {
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s04-m3-type-${typeKey}` },
    data: { typeKey, name, sortOrder: 10 },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json() as { id: string }).id
}

async function createField(
  request: APIRequestContext,
  owner: E2eSession,
  fieldsUrl: string,
  fieldKey: string,
  name: string,
  fieldType: string,
) {
  const response = await request.post(fieldsUrl, {
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s04-m3-field-${fieldKey}-${Date.now()}` },
    data: { fieldKey, name, fieldType, config: {}, sortOrder: 10 },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json() as { id: string }).id
}

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
