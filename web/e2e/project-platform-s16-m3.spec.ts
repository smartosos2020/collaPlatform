import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = { id: string; username: string; displayName: string; password: string }
type TypeSummary = { id: string; typeKey: string }
type Item = { id: string; title: string }
type Allocation = {
  id: string
  workItemId: string
  userId: string
  startDate: string
  endDate: string
  allocationPercent: number
  status: string
  version: number
}

test.describe('PROJECT-PLATFORM-S16 M3', () => {
  test('capacity allocation overload exact replay concurrency and responsive recovery @route-final', async ({
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
    const suffix = `s16m3_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterprise, `${suffix}_owner`, 'S16 Capacity Owner')
    const adminIdentity = await createIdentity(request, enterprise, `${suffix}_admin`, 'S16 Capacity Admin')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S16 Capacity Member')
    const guestIdentity = await createIdentity(request, enterprise, `${suffix}_guest`, 'S16 Capacity Guest')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S16 Capacity Outsider')
    const owner = await loginByApi(request, ownerIdentity.username, ownerIdentity.password)
    const spaceAdmin = await loginByApi(request, adminIdentity.username, adminIdentity.password)
    const member = await loginByApi(request, memberIdentity.username, memberIdentity.password)
    const guest = await loginByApi(request, guestIdentity.username, guestIdentity.password)
    const outsider = await loginByApi(request, outsiderIdentity.username, outsiderIdentity.password)
    let spaceId: string | undefined
    let otherSpaceId: string | undefined

    try {
      spaceId = await createSpace(request, owner, suffix, 'primary')
      otherSpaceId = await createSpace(request, owner, suffix, 'other')
      await addMember(request, owner, spaceId, adminIdentity.id, 'admin')
      await addMember(request, owner, spaceId, memberIdentity.id, 'member')
      await addMember(request, owner, spaceId, guestIdentity.id, 'guest')
      const typeId = await projectType(request, owner, spaceId)
      await validateAndPublish(request, owner, spaceId, typeId, suffix)
      const item = await createItem(
        request, owner, spaceId, typeId, `S16 产能事项 ${suffix}`, `${suffix}-item`,
      )
      const firstBody = allocationCreate(
        `${suffix}-create-a`, item.id, memberIdentity.id, 100,
      )
      const first = await postJson<Allocation>(
        request, allocationUrl(spaceId), owner, firstBody, firstBody.requestId,
      )
      const replay = await postJson<Allocation>(
        request, allocationUrl(spaceId), owner, firstBody, firstBody.requestId,
      )
      expect(replay.id).toBe(first.id)
      expect(replay.version).toBe(1)
      const secondBody = allocationCreate(
        `${suffix}-create-b`, item.id, memberIdentity.id, 50,
      )
      await postJson<Allocation>(
        request, allocationUrl(spaceId), spaceAdmin, secondBody, secondBody.requestId,
      )
      const ruleId = `${suffix}-rule`
      await postJson(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/resource-planning/capacity/rules`,
        owner,
        {
          schemaVersion: 1,
          requestId: ruleId,
          expectedVersion: 0,
          userId: memberIdentity.id,
          dailyMinutes: 480,
          warningPercent: 80,
        },
        ruleId,
      )

      for (const session of [owner, spaceAdmin, member, guest]) {
        const capacity = await getJson<{
          allocations: Allocation[]
          buckets: Array<{ signal: string; allocatedMinutes: number; capacityMinutes: number }>
        }>(
          request,
          `${apiBaseUrl}/project-spaces/${spaceId}/resource-planning/capacity`,
          session,
        )
        expect(capacity.allocations).toHaveLength(2)
        expect(capacity.buckets).toContainEqual(expect.objectContaining({
          signal: 'overloaded',
          allocatedMinutes: 720,
          capacityMinutes: 480,
        }))
      }
      for (const session of [outsider, enterprise]) {
        const hidden = await request.get(
          `${apiBaseUrl}/project-spaces/${spaceId}/resource-planning/capacity`,
          { headers: bearer(session) },
        )
        expect([403, 404]).toContain(hidden.status())
        expect(await hidden.text()).not.toContain(item.title)
      }
      const cross = await getJson<{ allocations: Allocation[] }>(
        request,
        `${apiBaseUrl}/project-spaces/${otherSpaceId}/resource-planning/capacity`,
        owner,
      )
      expect(cross.allocations).toEqual([])

      for (const denied of [member, guest]) {
        const response = await request.post(allocationUrl(spaceId), {
          headers: { ...bearer(denied), 'X-Colla-Request-Id': `${suffix}-denied-${Math.random()}` },
          data: allocationCreate(
            `${suffix}-denied-${crypto.randomUUID()}`, item.id, memberIdentity.id, 20,
          ),
        })
        expect(response.status()).toBe(403)
      }

      const concurrent = await Promise.all([
        rawAllocation(request, owner, spaceId, allocationUpdate(
          first, `${suffix}-update-a`, 80,
        )),
        rawAllocation(request, spaceAdmin, spaceId, allocationUpdate(
          first, `${suffix}-update-b`, 60,
        )),
      ])
      expect(concurrent.map((value) => value.status()).sort()).toEqual([200, 409])

      await installSession(page, owner)
      await page.goto(`/project-spaces/${spaceId}/work-items`)
      await expect(page.getByTestId('resource-capacity-panel')).toBeVisible()
      await expect(page.getByTestId('resource-capacity-panel')).toContainText('overloaded')
      await expect(page.getByTestId('resource-capacity-panel')).toContainText('产能 480')
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        expect(await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        )).toBeLessThanOrEqual(1)
      }
      await page.context().setOffline(true)
      const draft = `离线产能调整 ${suffix}`
      await page.getByTestId('resource-capacity-offline-draft').fill(draft)
      await expect(page.getByTestId('resource-capacity-offline-draft')).toHaveValue(draft)
      await page.context().setOffline(false)
      await page.screenshot({
        path: testInfo.outputPath('s16-capacity-820.png'),
        fullPage: true,
      })
    } finally {
      await page.context().setOffline(false).catch(() => undefined)
      for (const id of [spaceId, otherSpaceId]) {
        if (id) {
          await request.post(`${apiBaseUrl}/project-spaces/${id}/settings/archive`, {
            headers: bearer(owner),
          }).catch(() => undefined)
        }
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

function allocationUrl(spaceId: string) {
  return `${apiBaseUrl}/project-spaces/${spaceId}/resource-planning/capacity/allocations`
}

function allocationCreate(
  requestId: string,
  workItemId: string,
  userId: string,
  allocationPercent: number,
) {
  return {
    schemaVersion: 1,
    requestId,
    operation: 'create',
    allocationId: null,
    expectedVersion: 0,
    workItemId,
    userId,
    startDate: '2026-07-28',
    endDate: '2026-07-28',
    allocationPercent,
    reason: '明确资源计划',
  }
}

function allocationUpdate(
  current: Allocation,
  requestId: string,
  allocationPercent: number,
) {
  return {
    schemaVersion: 1,
    requestId,
    operation: 'update',
    allocationId: current.id,
    expectedVersion: current.version,
    startDate: current.startDate,
    endDate: current.endDate,
    allocationPercent,
    reason: '并发资源调整',
  }
}

function rawAllocation(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  data: ReturnType<typeof allocationUpdate>,
) {
  return request.post(allocationUrl(spaceId), {
    headers: { ...bearer(session), 'X-Colla-Request-Id': data.requestId },
    data,
  })
}

async function projectType(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
) {
  const types = await getJson<{ items: TypeSummary[] }>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`,
    owner,
  )
  const taskType = types.items.find((candidate) => candidate.typeKey === 'task')
  expect(taskType).toBeTruthy()
  return taskType!.id
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

async function createSpace(
  request: APIRequestContext,
  owner: E2eSession,
  suffix: string,
  kind: string,
) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(owner),
    data: {
      spaceKey: `s16-m3-${kind}-${suffix.replaceAll('_', '-')}`,
      name: `S16 产能 ${kind} ${suffix}`,
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s16-m3-member-${userId}` },
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
