import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = { id: string; username: string; displayName: string; password: string }
type Item = { id: string; version: number; title: string; runtime: { snapshot: Record<string, unknown> } }
type TypeSummary = { id: string; typeKey: string }

test.describe('PROJECT-PLATFORM-S11 route final', () => {
  test('permission policy UI, safe explanation and six identities close on real isolated services @route-final', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(480_000)
    page.setDefaultTimeout(25_000)
    requireIsolatedIdentityFixture()
    const enterpriseAdmin = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(request, `${apiBaseUrl}/auth/me`, enterpriseAdmin)
    const suffix = `s11m5_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_owner`, 'S11 Owner')
    const adminIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_admin`, 'S11 Admin')
    const memberIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_member`, 'S11 Member')
    const guestIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_guest`, 'S11 Guest')
    const outsiderIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_outsider`, 'S11 Outsider')
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

      const draft = await getJson<{
        snapshotSchemaVersion: number
        snapshot: { permissionModel?: { permissionPolicies?: unknown[]; spaceRoleDefinitions?: unknown[] } }
      }>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${projectType.id}/draft`,
        owner,
      )
      expect(draft.snapshotSchemaVersion).toBe(5)
      expect(draft.snapshot.permissionModel?.permissionPolicies?.length).toBeGreaterThanOrEqual(5)
      expect(draft.snapshot.permissionModel?.spaceRoleDefinitions?.length).toBe(4)
      await validateAndPublish(request, owner, spaceId, projectType.id, suffix)

      const item = await createItem(
        request,
        owner,
        spaceId,
        projectType.id,
        `S11 权限验收 ${'长名称'.repeat(24)}`,
        `${suffix}-item`,
      )
      for (const session of [owner, spaceAdmin, member, guest]) {
        const visible = await getJson<Item>(
          request,
          `${apiBaseUrl}/project-spaces/${spaceId}/work-items/${item.id}`,
          session,
        )
        expect(visible.runtime.snapshot).not.toHaveProperty('permissionModel')
      }
      for (const session of [outsider, enterpriseAdmin]) {
        const hidden = await request.get(
          `${apiBaseUrl}/project-spaces/${spaceId}/work-items/${item.id}`,
          { headers: bearer(session) },
        )
        expect(hidden.status()).toBe(404)
        expect(await hidden.text()).not.toContain(item.title)
      }

      const memberEdit = await getJson<{ allowed: boolean; safePolicySources: string[] }>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-items/${item.id}/permission-explanation?action=edit`,
        member,
      )
      expect(memberEdit.allowed).toBeTruthy()
      expect(memberEdit.safePolicySources.length).toBeGreaterThan(0)
      const guestEdit = await getJson<{ allowed: boolean; requestAvailable: boolean; safePolicySources: string[] }>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-items/${item.id}/permission-explanation?action=edit`,
        guest,
      )
      expect(guestEdit.allowed).toBeFalsy()
      expect(guestEdit.requestAvailable).toBeTruthy()
      expect(JSON.stringify(guestEdit)).not.toContain('permissionPolicies')

      const adminPreview = await request.post(
        `${apiBaseUrl}/project-spaces/${spaceId}/permission-governance/policy-changes:preview`,
        {
          headers: bearer(spaceAdmin),
          data: {
            expectedVersion: 1,
            currentVersion: 1,
            visibleCandidateCount: 4,
            hiddenCandidateCount: 2,
            grantCount: 1,
            revokeCount: 0,
          },
        },
      )
      expect(adminPreview.ok(), await adminPreview.text()).toBeTruthy()
      expect(await adminPreview.json()).toMatchObject({
        visibleCandidateCount: 4,
        hiddenCandidatesPresent: true,
      })
      const enterprisePreview = await request.post(
        `${apiBaseUrl}/project-spaces/${spaceId}/permission-governance/policy-changes:preview`,
        {
          headers: bearer(enterpriseAdmin),
          data: {
            expectedVersion: 1,
            currentVersion: 1,
            visibleCandidateCount: 4,
            hiddenCandidateCount: 2,
            grantCount: 1,
            revokeCount: 0,
          },
        },
      )
      expect([403, 404]).toContain(enterprisePreview.status())

      await installSession(page, owner)
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto(`/project-spaces/${spaceId}/types/${projectType.id}`)
      const policyEditor = page.locator('.work-item-permission-policy-editor')
      await expect(policyEditor).toBeVisible()
      await expect(policyEditor).toContainText('数据权限策略')
      await expect(policyEditor).toContainText('space_member_baseline')
      await expect(policyEditor).toContainText('deny 优先')

      await installSession(page, member)
      await page.goto(`/project-spaces/${spaceId}/work-items/${item.id}`)
      await page.getByTestId('project-work-item-detail-secondary-tabs')
        .getByRole('tab', { name: '权限', exact: true }).click()
      const permissionPanel = page.locator('.work-item-permissions-panel')
      await expect(permissionPanel).toBeVisible()
      await expect(permissionPanel).toContainText('所有能力均来自服务端决策')
      await expect(permissionPanel).toContainText('可披露来源')
      const actionSelect = permissionPanel.getByLabel('选择权限动作')
      await actionSelect.click()
      await actionSelect.fill('field_read')
      await page.keyboard.press('Enter')
      await expect(permissionPanel).toContainText('field_read')

      await installSession(page, guest)
      await page.goto(`/project-spaces/${spaceId}/work-items/${item.id}`)
      await page.getByTestId('project-work-item-detail-secondary-tabs')
        .getByRole('tab', { name: '权限', exact: true }).click()
      await expect(page.locator('.work-item-permissions-panel')).toBeVisible()
      await expect(page.getByRole('button', { name: '保存' })).toHaveCount(0)

      for (const width of [1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        await page.goto(`/project-spaces/${spaceId}/work-items/${item.id}`)
        await page.getByTestId('project-work-item-detail-secondary-tabs')
          .getByRole('tab', { name: '权限', exact: true }).click()
        await expect(page.locator('.work-item-permissions-panel')).toBeVisible()
        expect(await page.evaluate(() =>
          document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1)
      }
      await page.screenshot({ path: testInfo.outputPath('s11-permissions-820.png'), fullPage: true })
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
    `s11-validate-${suffix}`,
  )
  await postJson(
    request,
    `${base}/draft:publish`,
    owner,
    { expectedDraftAggregateVersion: draft.aggregateVersion, breakingConfirmed: false },
    `s11-publish-${suffix}`,
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
      spaceKey: `s11-m5-${suffix.replaceAll('_', '-')}`,
      name: `S11 权限验收 ${suffix}`,
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s11-member-${userId}` },
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
