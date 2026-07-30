import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = { id: string; username: string; displayName: string; password: string }
type Grant = {
  id: string
  status: string
  currentVersion: number
  sourceConfirmed: boolean
  targetConfirmed: boolean
}

test.describe('PROJECT-PLATFORM-S18 M1', () => {
  test('dual-space grants are versioned explicitly confirmed and fail closed @route-final', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(480_000)
    page.setDefaultTimeout(25_000)
    requireIsolatedIdentityFixture()
    const enterprise = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(
      request, `${apiBaseUrl}/auth/me`, enterprise,
    )
    const suffix = `s18m1_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const sourceOwnerIdentity = await createIdentity(request, enterprise, `${suffix}_source`, 'S18 Source Owner')
    const targetOwnerIdentity = await createIdentity(request, enterprise, `${suffix}_target`, 'S18 Target Owner')
    const adminIdentity = await createIdentity(request, enterprise, `${suffix}_admin`, 'S18 Admin')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S18 Member')
    const guestIdentity = await createIdentity(request, enterprise, `${suffix}_guest`, 'S18 Guest')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S18 Outsider')
    const sourceOwner = await loginByApi(request, sourceOwnerIdentity.username, sourceOwnerIdentity.password)
    const targetOwner = await loginByApi(request, targetOwnerIdentity.username, targetOwnerIdentity.password)
    const spaceAdmin = await loginByApi(request, adminIdentity.username, adminIdentity.password)
    const member = await loginByApi(request, memberIdentity.username, memberIdentity.password)
    const guest = await loginByApi(request, guestIdentity.username, guestIdentity.password)
    const outsider = await loginByApi(request, outsiderIdentity.username, outsiderIdentity.password)
    let sourceSpaceId: string | undefined
    let targetSpaceId: string | undefined

    try {
      sourceSpaceId = await createSpace(request, sourceOwner, suffix, 'source')
      targetSpaceId = await createSpace(request, targetOwner, suffix, 'target')
      await addMember(request, sourceOwner, sourceSpaceId, adminIdentity.id, 'admin')
      await addMember(request, sourceOwner, sourceSpaceId, memberIdentity.id, 'member')
      await addMember(request, sourceOwner, sourceSpaceId, guestIdentity.id, 'guest')
      const sourceType = await publishedProjectType(request, sourceOwner, sourceSpaceId, `${suffix}-source`)
      const targetType = await publishedProjectType(request, targetOwner, targetSpaceId, `${suffix}-target`)
      const requestId = `${suffix}-grant`
      const grantBody = {
        schemaVersion: 1,
        requestId,
        expectedVersion: 0,
        targetSpaceId,
        name: `工程交接授权 ${suffix}`,
        scope: {
          schemaVersion: 1,
          direction: 'bidirectional',
          operations: ['reference', 'relate'],
          typeScopes: [{
            sourceTypeId: sourceType.typeId,
            sourceVersionId: sourceType.versionId,
            targetTypeId: targetType.typeId,
            targetVersionId: targetType.versionId,
          }],
        },
      }
      const created = await postJson<Grant>(
        request,
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/grants`,
        sourceOwner,
        grantBody,
        requestId,
      )
      const replay = await postJson<Grant>(
        request,
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/grants`,
        sourceOwner,
        grantBody,
        requestId,
      )
      expect(replay.id).toBe(created.id)
      expect(created.status).toBe('draft')

      let grant = await lifecycle(
        request, sourceOwner, sourceSpaceId, created, 'request', undefined, `${suffix}-request`,
      )
      grant = await lifecycle(
        request, sourceOwner, sourceSpaceId, grant, 'confirm', 'source', `${suffix}-source-confirm`,
      )
      expect(grant.status).toBe('requested')
      expect(grant.sourceConfirmed).toBeTruthy()
      expect(grant.targetConfirmed).toBeFalsy()
      grant = await lifecycle(
        request, targetOwner, targetSpaceId, grant, 'confirm', 'target', `${suffix}-target-confirm`,
      )
      expect(grant.status).toBe('active')
      expect(grant.targetConfirmed).toBeTruthy()

      for (const session of [sourceOwner, spaceAdmin, member, guest]) {
        const visible = await getJson<{ grants: Array<{ id: string; name: string }> }>(
          request,
          `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/grants`,
          session,
        )
        expect(visible.grants.some((value) => value.id === grant.id)).toBeTruthy()
      }
      for (const session of [outsider, enterprise]) {
        const hidden = await request.get(
          `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/grants`,
          { headers: bearer(session) },
        )
        expect([403, 404]).toContain(hidden.status())
        expect(await hidden.text()).not.toContain(suffix)
      }
      const denied = await request.post(
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/grants/${grant.id}/lifecycle`,
        {
          headers: { ...bearer(member), 'X-Colla-Request-Id': `${suffix}-member-revoke` },
          data: {
            schemaVersion: 1,
            requestId: `${suffix}-member-revoke`,
            expectedVersion: grant.currentVersion,
            action: 'revoke',
            reason: 'member cannot revoke',
          },
        },
      )
      expect(denied.status()).toBe(403)

      await installSession(page, sourceOwner)
      await page.goto(`/project-spaces/${sourceSpaceId}`)
      await page
        .getByTestId('project-space-overview-secondary-tabs')
        .getByRole('tab', { name: '跨空间授权', exact: true })
        .click()
      const panel = page.getByTestId('cross-space-grants-panel')
      await expect(panel).toBeVisible()
      await expect(panel).toContainText(suffix)
      await expect(panel).toContainText('生效')
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        await expect(panel).toBeVisible()
        expect(await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        )).toBeLessThanOrEqual(1)
      }
      await page.context().setOffline(true)
      await page.evaluate(() => window.dispatchEvent(new Event('offline')))
      await expect(panel).toContainText('当前离线')
      await expect(panel.getByRole('button', { name: '新建授权' })).toBeDisabled()
      await page.context().setOffline(false)
      await page.screenshot({
        path: testInfo.outputPath('s18-cross-space-grants-820.png'),
        fullPage: true,
      })
    } finally {
      await page.context().setOffline(false).catch(() => undefined)
      for (const entry of [
        { id: sourceSpaceId, session: sourceOwner },
        { id: targetSpaceId, session: targetOwner },
      ]) {
        if (entry.id) {
          await request.post(`${apiBaseUrl}/project-spaces/${entry.id}/settings/archive`, {
            headers: bearer(entry.session),
          }).catch(() => undefined)
        }
      }
      for (const identity of [
        targetOwnerIdentity, adminIdentity, memberIdentity, guestIdentity,
        outsiderIdentity, sourceOwnerIdentity,
      ]) {
        await request.post(`${apiBaseUrl}/admin/users/${identity.id}/offboard`, {
          headers: bearer(enterprise),
          data: { handoverToUserId: enterpriseProfile.id },
        }).catch(() => undefined)
      }
    }
  })
})

async function publishedProjectType(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  suffix: string,
) {
  const configured = await getJson<{
    items: Array<{ id: string; typeKey: string; currentVersion?: { id: string } }>
  }>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`,
    owner,
  )
  const type = configured.items.find((candidate) => candidate.typeKey === 'project')
  expect(type, 'project preset must exist').toBeTruthy()
  if (!type) throw new Error('project preset missing')
  const base = `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${type.id}`
  let draft = await getJson<{ aggregateVersion: number }>(request, `${base}/draft`, owner)
  draft = await postJson(
    request,
    `${base}/draft:validate`,
    owner,
    { expectedAggregateVersion: draft.aggregateVersion },
    `${suffix}-validate`,
  )
  const published = await postJson<{ version: { id: string } }>(
    request,
    `${base}/draft:publish`,
    owner,
    { expectedDraftAggregateVersion: draft.aggregateVersion, breakingConfirmed: false },
    `${suffix}-publish`,
  )
  return { typeId: type.id, versionId: published.version.id }
}

async function lifecycle(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  grant: Grant,
  action: string,
  party: string | undefined,
  requestId: string,
) {
  return postJson<Grant>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/cross-space/grants/${grant.id}/lifecycle`,
    session,
    {
      schemaVersion: 1,
      requestId,
      expectedVersion: grant.currentVersion,
      action,
      party,
    },
    requestId,
  )
}

async function createSpace(
  request: APIRequestContext,
  owner: E2eSession,
  suffix: string,
  kind: string,
) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(owner),
    data: {
      spaceKey: `s18-m1-${kind}-${suffix.replaceAll('_', '-')}`,
      name: `S18 跨空间 ${kind} ${suffix}`,
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s18-m1-member-${userId}` },
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
    data: {
      username,
      password,
      displayName,
      email: `${username}@example.com`,
      roleCode: 'member',
    },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  return { ...(await response.json() as Omit<Identity, 'password'>), password }
}

async function getJson<T>(
  request: APIRequestContext,
  url: string,
  session: E2eSession,
) {
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
