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
type Schedule = {
  rows: Array<{ userId: string; conflictCount: number }>
  bars: Array<{ allocationId: string; sourceVersion: number }>
  conflicts: Array<{ signal: string; allocatedMinutes: number; capacityMinutes: number }>
}

test.describe('PROJECT-PLATFORM-S16 M4 route final', () => {
  test('resource schedule preview commit replay permission and responsive recovery @route-final', async ({
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
    const suffix = `s16m4_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterprise, `${suffix}_owner`, 'S16 Schedule Owner')
    const adminIdentity = await createIdentity(request, enterprise, `${suffix}_admin`, 'S16 Schedule Admin')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S16 Schedule Member')
    const guestIdentity = await createIdentity(request, enterprise, `${suffix}_guest`, 'S16 Schedule Guest')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S16 Schedule Outsider')
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
        request, owner, spaceId, typeId, `S16 排期事项 ${suffix}`, `${suffix}-item`,
      )
      const firstBody = allocationCreate(`${suffix}-allocation-a`, item.id, memberIdentity.id, 100)
      const first = await postJson<Allocation>(
        request, allocationUrl(spaceId), owner, firstBody, firstBody.requestId,
      )
      const secondBody = allocationCreate(`${suffix}-allocation-b`, item.id, memberIdentity.id, 50)
      await postJson<Allocation>(
        request, allocationUrl(spaceId), spaceAdmin, secondBody, secondBody.requestId,
      )

      for (const session of [owner, spaceAdmin, member, guest]) {
        const schedule = await getJson<Schedule>(request, scheduleUrl(spaceId), session)
        expect(schedule.rows).toContainEqual(expect.objectContaining({
          userId: memberIdentity.id,
          conflictCount: 1,
        }))
        expect(schedule.bars).toHaveLength(2)
        expect(schedule.conflicts).toContainEqual(expect.objectContaining({
          signal: 'overloaded',
          allocatedMinutes: 720,
          capacityMinutes: 480,
        }))
      }
      for (const session of [outsider, enterprise]) {
        const hidden = await request.get(scheduleUrl(spaceId), { headers: bearer(session) })
        expect([403, 404]).toContain(hidden.status())
        expect(await hidden.text()).not.toContain(item.title)
      }
      const cross = await getJson<Schedule>(request, scheduleUrl(otherSpaceId), owner)
      expect(cross.bars).toEqual([])
      expect(cross.rows).toEqual([])

      const preferenceBody = {
        schemaVersion: 1,
        requestId: `${suffix}-preference`,
        expectedVersion: 0,
        windowStart: '2026-07-21',
        windowEnd: '2026-08-27',
        zoom: 'week',
      }
      const preference = await postJson<{ id: string; version: number }>(
        request, `${scheduleUrl(spaceId)}/preference`, member,
        preferenceBody, preferenceBody.requestId,
      )
      const preferenceReplay = await postJson<{ id: string; version: number }>(
        request, `${scheduleUrl(spaceId)}/preference`, member,
        preferenceBody, preferenceBody.requestId,
      )
      expect(preferenceReplay).toEqual(preference)

      const previewBody = adjustment(first, `${suffix}-preview`, true, 80)
      const preview = await postJson<{ preview: boolean; committed: boolean; version: number }>(
        request, `${scheduleUrl(spaceId)}/adjustments`, owner,
        previewBody, previewBody.requestId,
      )
      expect(preview).toMatchObject({ preview: true, committed: false, version: 1 })
      const commitBody = adjustment(first, `${suffix}-commit`, false, 80)
      const committed = await postJson<{ committed: boolean; version: number }>(
        request, `${scheduleUrl(spaceId)}/adjustments`, owner,
        commitBody, commitBody.requestId,
      )
      const replay = await postJson<{ committed: boolean; version: number }>(
        request, `${scheduleUrl(spaceId)}/adjustments`, owner,
        commitBody, commitBody.requestId,
      )
      expect(committed).toMatchObject({ committed: true, version: 2 })
      expect(replay).toEqual(committed)

      for (const denied of [member, guest]) {
        const deniedBody = adjustment(
          { ...first, version: 2 }, `${suffix}-denied-${crypto.randomUUID()}`, false, 70,
        )
        const response = await request.post(`${scheduleUrl(spaceId)}/adjustments`, {
          headers: { ...bearer(denied), 'X-Colla-Request-Id': deniedBody.requestId },
          data: deniedBody,
        })
        expect(response.status()).toBe(403)
      }

      await installSession(page, owner)
      await page.goto(`/project-spaces/${spaceId}/work-items`)
      await page
        .getByTestId('project-work-items-secondary-tabs')
        .getByRole('tab', { name: '资源日程', exact: true })
        .click()
      await expect(page.getByTestId('resource-schedule-panel')).toBeVisible()
      await expect(page.getByTestId('resource-assignment-bar')).toHaveCount(2)
      await expect(page.getByTestId('resource-conflict-marker')).toHaveCount(1)
      await page.getByTestId('resource-assignment-bar').first().click()
      await page.getByLabel('调整原因').fill(`组合排期校准 ${suffix}`)
      await page.getByTestId('resource-adjustment-preview-button').click()
      await expect(page.getByTestId('resource-adjustment-preview')).toContainText(
        'canonical-allocation:update',
      )
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        expect(await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        )).toBeLessThanOrEqual(1)
      }
      await page.context().setOffline(true)
      await expect(page.getByLabel('调整原因')).toHaveValue(`组合排期校准 ${suffix}`)
      await page.context().setOffline(false)
      await page.screenshot({
        path: testInfo.outputPath('s16-resource-schedule-820.png'),
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

function scheduleUrl(spaceId: string) {
  return `${apiBaseUrl}/project-spaces/${spaceId}/resource-planning/schedule`
}

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
    reason: '明确资源排期',
  }
}

function adjustment(
  current: Allocation,
  requestId: string,
  preview: boolean,
  allocationPercent: number,
) {
  return {
    schemaVersion: 1,
    requestId,
    preview,
    allocationId: current.id,
    expectedVersion: current.version,
    startDate: current.startDate,
    endDate: current.endDate,
    allocationPercent,
    reason: '资源排期调整',
  }
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
      spaceKey: `s16-m4-${kind}-${suffix.replaceAll('_', '-')}`,
      name: `S16 排期 ${kind} ${suffix}`,
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s16-m4-member-${userId}` },
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
