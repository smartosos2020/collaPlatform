import { expect, test, type APIRequestContext } from '@playwright/test'
import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = { id: string; username: string; displayName: string; password: string }
type Connector = { id: string; name: string; status: string; version: number }

test.describe('PROJECT-PLATFORM-S17 M4', () => {
  test('connectors are scoped SSRF-safe dry-run observable and responsive', async ({ page, request }, testInfo) => {
    test.setTimeout(480_000)
    requireIsolatedIdentityFixture()
    const enterprise = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(request, `${apiBaseUrl}/auth/me`, enterprise)
    const suffix = `s17m4_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterprise, `${suffix}_owner`, 'S17 M4 Owner')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S17 M4 Member')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S17 M4 Outsider')
    const owner = await loginByApi(request, ownerIdentity.username, ownerIdentity.password)
    const member = await loginByApi(request, memberIdentity.username, memberIdentity.password)
    const outsider = await loginByApi(request, outsiderIdentity.username, outsiderIdentity.password)
    let spaceId: string | undefined
    let otherSpaceId: string | undefined
    try {
      spaceId = await createSpace(request, owner, suffix, 'primary')
      otherSpaceId = await createSpace(request, owner, suffix, 'other')
      await addMember(request, owner, spaceId, memberIdentity.id, 'member')
      const requestId = `${suffix}-connector`
      const connector = await postJson<Connector>(
        request, `${apiBaseUrl}/project-spaces/${spaceId}/automation/connectors`, owner,
        { schemaVersion: 1, requestId, expectedVersion: 0, name: `Webhook ${suffix}`,
          targetUri: 'https://example.com/colla-hook', credentialReference: `vault://colla/${suffix}` },
        requestId,
      )
      expect(connector.status).toBe('active')
      const dryRun = await postJson<{ status: string; attemptCount: number }>(
        request, `${apiBaseUrl}/project-spaces/${spaceId}/automation/connectors/${connector.id}/test`, owner,
        { schemaVersion: 1, requestId: `${suffix}-dry`, payload: '{"kind":"test"}', dryRun: true },
        `${suffix}-dry`,
      )
      expect(dryRun.status).toBe('succeeded')
      expect(dryRun.attemptCount).toBe(1)
      const replay = await postJson<{ id: string }>(
        request, `${apiBaseUrl}/project-spaces/${spaceId}/automation/connectors/${connector.id}/test`, owner,
        { schemaVersion: 1, requestId: `${suffix}-dry`, payload: '{"kind":"test"}', dryRun: true },
        `${suffix}-dry`,
      )
      expect(replay.id).toBe((dryRun as { id?: string }).id)

      for (const targetUri of ['http://example.com/hook', 'https://127.0.0.1/hook', 'https://169.254.169.254/latest']) {
        const response = await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/automation/connectors`, {
          headers: { ...bearer(owner), 'X-Colla-Request-Id': crypto.randomUUID() },
          data: { schemaVersion: 1, requestId: crypto.randomUUID(), expectedVersion: 0,
            name: 'Blocked target', targetUri, credentialReference: 'vault://blocked' },
        })
        expect(response.status()).toBe(400)
        expect(await response.text()).not.toContain('vault://blocked')
      }
      const memberView = await getJson<{ connectors: Connector[] }>(
        request, `${apiBaseUrl}/project-spaces/${spaceId}/automation/connectors`, member,
      )
      expect(memberView.connectors[0].name).toContain(suffix)
      const denied = await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/automation/connectors`, {
        headers: bearer(member),
        data: { schemaVersion: 1, requestId: `${suffix}-denied`, expectedVersion: 0,
          name: `hidden ${suffix}`, targetUri: 'https://example.com', credentialReference: 'vault://hidden' },
      })
      expect(denied.status()).toBe(403)
      for (const session of [outsider, enterprise]) {
        const hidden = await request.get(`${apiBaseUrl}/project-spaces/${spaceId}/automation/connectors`,
          { headers: bearer(session) })
        expect([403, 404]).toContain(hidden.status())
        expect(await hidden.text()).not.toContain(suffix)
      }
      const cross = await getJson<{ connectors: Connector[] }>(
        request, `${apiBaseUrl}/project-spaces/${otherSpaceId}/automation/connectors`, owner,
      )
      expect(cross.connectors).toHaveLength(0)

      await installSession(page, owner)
      await page.goto(`/project-spaces/${spaceId}/work-items`)
      await expect(page.getByTestId('automation-connectors-panel')).toBeVisible()
      await expect(page.getByTestId('automation-connectors-panel')).toContainText(suffix)
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth))
          .toBeLessThanOrEqual(1)
      }
      await page.screenshot({ path: testInfo.outputPath('s17-m4-connectors-820.png'), fullPage: true })
      await installSession(page, member)
      await page.goto(`/project-spaces/${spaceId}/work-items`)
      await expect(page.getByRole('button', { name: '保存连接器' })).toBeDisabled()
    } finally {
      for (const id of [spaceId, otherSpaceId]) if (id) {
        await request.post(`${apiBaseUrl}/project-spaces/${id}/settings/archive`, { headers: bearer(owner) }).catch(() => undefined)
      }
      for (const identity of [memberIdentity, outsiderIdentity, ownerIdentity]) {
        await request.post(`${apiBaseUrl}/admin/users/${identity.id}/offboard`, {
          headers: bearer(enterprise), data: { handoverToUserId: enterpriseProfile.id },
        }).catch(() => undefined)
      }
    }
  })
})

async function createSpace(request: APIRequestContext, owner: E2eSession, suffix: string, kind: string) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(owner), data: { spaceKey: `s17-m4-${kind}-${suffix.replaceAll('_', '-')}`,
      name: `S17 M4 ${kind} ${suffix}`, visibility: 'private' },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  return (await response.json() as { id: string }).id
}
async function addMember(request: APIRequestContext, owner: E2eSession, spaceId: string, userId: string, roleKey: string) {
  const response = await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/members`, {
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s17-m4-member-${userId}` }, data: { userId, roleKey },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
}
async function createIdentity(request: APIRequestContext, admin: E2eSession, username: string, displayName: string) {
  const password = 'member123456'
  const response = await request.post(`${apiBaseUrl}/admin/users`, {
    headers: bearer(admin), data: { username, password, displayName, email: `${username}@example.com`, roleCode: 'member' },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  return { ...(await response.json() as Omit<Identity, 'password'>), password }
}
async function getJson<T>(request: APIRequestContext, url: string, session: E2eSession) {
  const response = await request.get(url, { headers: bearer(session) })
  expect(response.ok(), `GET ${url}: ${await response.text()}`).toBeTruthy()
  return await response.json() as T
}
async function postJson<T>(request: APIRequestContext, url: string, session: E2eSession, data: unknown, requestId: string) {
  const response = await request.post(url, { headers: { ...bearer(session), 'X-Colla-Request-Id': requestId }, data })
  expect(response.ok(), `POST ${url}: ${await response.text()}`).toBeTruthy()
  return await response.json() as T
}
