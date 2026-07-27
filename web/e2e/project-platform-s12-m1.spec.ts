import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = { id: string; username: string; displayName: string; password: string }
type Item = { id: string; version: number; title: string }
type TypeSummary = { id: string; typeKey: string }
type PersonalPage = {
  buckets: Array<{
    bucket: 'todo' | 'responsible' | 'participating' | 'watching'
    visibleCount: number
    items: Array<{ workItemId: string; title: string; deepLink: string }>
  }>
}

test.describe('PROJECT-PLATFORM-S12 M1', () => {
  test('personal work buckets stay user-scoped and render real deep links for six identities', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(480_000)
    page.setDefaultTimeout(25_000)
    requireIsolatedIdentityFixture()
    const enterpriseAdmin = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(request, `${apiBaseUrl}/auth/me`, enterpriseAdmin)
    const suffix = `s12m1_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_owner`, 'S12 Owner')
    const adminIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_admin`, 'S12 Space Admin')
    const memberIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_member`, 'S12 Member')
    const guestIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_guest`, 'S12 Guest')
    const outsiderIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_outsider`, 'S12 Outsider')
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
      const title = `S12 个人工作 ${'长名称'.repeat(24)}`
      const item = await createItem(request, owner, spaceId, projectType.id, title, `${suffix}-item`)
      await putParticipant(request, owner, spaceId, item, memberIdentity.id, 'watcher')

      const ownerPage = await getJson<PersonalPage>(request, `${apiBaseUrl}/personal-work?limit=20`, owner)
      const memberPage = await getJson<PersonalPage>(request, `${apiBaseUrl}/personal-work?limit=20`, member)
      expect(bucket(ownerPage, 'responsible').items).toContainEqual(expect.objectContaining({
        workItemId: item.id,
        title,
      }))
      expect(bucket(memberPage, 'watching').items).toContainEqual(expect.objectContaining({
        workItemId: item.id,
        title,
        deepLink: `/project-spaces/${spaceId}/work-items/${item.id}`,
      }))

      for (const session of [spaceAdmin, guest, outsider, enterpriseAdmin]) {
        const personal = await getJson<PersonalPage>(request, `${apiBaseUrl}/personal-work?limit=20`, session)
        expect(personal.buckets.every((entry) => entry.items.every((candidate) => candidate.workItemId !== item.id)))
          .toBeTruthy()
        expect(JSON.stringify(personal)).not.toContain(title)
      }

      await installSession(page, member)
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto('/')
      await expect(page.getByText('我关注的 · 1')).toBeVisible()
      await expect(page.getByText(title)).toBeVisible()
      await page.getByText(title).click()
      await expect(page).toHaveURL(new RegExp(`/project-spaces/${spaceId}/work-items/${item.id}$`))

      for (const width of [1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        await page.goto('/')
        await expect(page.getByText('我关注的 · 1')).toBeVisible()
        expect(await page.evaluate(() =>
          document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1)
      }
      await page.screenshot({ path: testInfo.outputPath('s12-m1-personal-work-820.png'), fullPage: true })
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

function bucket(page: PersonalPage, key: PersonalPage['buckets'][number]['bucket']) {
  const result = page.buckets.find((candidate) => candidate.bucket === key)
  if (!result) throw new Error(`missing bucket ${key}`)
  return result
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
    `s12-validate-${suffix}`,
  )
  await postJson(
    request,
    `${base}/draft:publish`,
    owner,
    { expectedDraftAggregateVersion: draft.aggregateVersion, breakingConfirmed: false },
    `s12-publish-${suffix}`,
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
      headers: { ...bearer(session), 'X-Colla-Request-Id': `s12-participant-${userId}` },
      data: { role, expectedVersion: item.version },
    },
  )
  expect(response.ok(), await response.text()).toBeTruthy()
}

async function createSpace(request: APIRequestContext, owner: E2eSession, suffix: string) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(owner),
    data: {
      spaceKey: `s12-m1-${suffix.replaceAll('_', '-')}`,
      name: `S12 个人工作 ${suffix}`,
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s12-member-${userId}` },
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
