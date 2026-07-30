import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = { id: string; username: string; displayName: string; password: string }
type Calendar = {
  id: string
  timezone: string
  workDays: number[]
  dailyMinutes: number
  exceptions: Array<{
    id: string
    date: string
    availableMinutes: number
    note: string
  }>
  version: number
}

test.describe('PROJECT-PLATFORM-S16 M1', () => {
  test('calendar exact replay visibility concurrency and responsive offline recovery @route-final', async ({
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
    const suffix = `s16m1_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterprise, `${suffix}_owner`, 'S16 Owner')
    const adminIdentity = await createIdentity(request, enterprise, `${suffix}_admin`, 'S16 Admin')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S16 Member')
    const guestIdentity = await createIdentity(request, enterprise, `${suffix}_guest`, 'S16 Guest')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S16 Outsider')
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
      const requestId = `${suffix}-calendar-create`
      const body = {
        schemaVersion: 1,
        requestId,
        expectedVersion: 0,
        timezone: 'Asia/Shanghai',
        workDays: [1, 2, 3, 4, 5],
        dailyMinutes: 480,
        exceptions: [{
          id: crypto.randomUUID(),
          date: '2026-10-01',
          availableMinutes: 0,
          note: `国庆例外 ${suffix}`,
        }],
      }
      const first = await postJson<Calendar>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/resource-planning/calendar`,
        owner,
        body,
        requestId,
      )
      const replay = await postJson<Calendar>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/resource-planning/calendar`,
        owner,
        body,
        requestId,
      )
      expect(replay.id).toBe(first.id)
      expect(replay.version).toBe(1)

      for (const session of [owner, spaceAdmin, member, guest]) {
        const visible = await getJson<{ calendar: Calendar }>(
          request,
          `${apiBaseUrl}/project-spaces/${spaceId}/resource-planning`,
          session,
        )
        expect(visible.calendar.timezone).toBe('Asia/Shanghai')
        expect(visible.calendar.exceptions[0].note).toContain(suffix)
      }
      for (const session of [outsider, enterprise]) {
        const hidden = await request.get(
          `${apiBaseUrl}/project-spaces/${spaceId}/resource-planning`,
          { headers: bearer(session) },
        )
        expect([403, 404]).toContain(hidden.status())
        expect(await hidden.text()).not.toContain(suffix)
      }
      const crossSpace = await getJson<{ calendar: Calendar }>(
        request,
        `${apiBaseUrl}/project-spaces/${otherSpaceId}/resource-planning`,
        owner,
      )
      expect(crossSpace.calendar.version).toBe(0)
      expect(JSON.stringify(crossSpace)).not.toContain(suffix)

      const guestWrite = await request.post(
        `${apiBaseUrl}/project-spaces/${spaceId}/resource-planning/calendar`,
        {
          headers: { ...bearer(guest), 'X-Colla-Request-Id': `${suffix}-guest` },
          data: { ...body, requestId: `${suffix}-guest`, expectedVersion: 1 },
        },
      )
      expect(guestWrite.status()).toBe(403)

      const concurrent = await Promise.all(['a', 'b'].map((label) =>
        request.post(
          `${apiBaseUrl}/project-spaces/${spaceId}/resource-planning/calendar`,
          {
            headers: {
              ...bearer(member),
              'X-Colla-Request-Id': `${suffix}-concurrent-${label}`,
            },
            data: {
              ...body,
              requestId: `${suffix}-concurrent-${label}`,
              expectedVersion: 1,
              dailyMinutes: label === 'a' ? 420 : 450,
            },
          },
        )))
      expect(concurrent.map((response) => response.status()).sort()).toEqual([200, 409])

      await installSession(page, owner)
      await page.goto(`/project-spaces/${spaceId}/work-items`)
      await page
        .getByTestId('project-work-items-secondary-tabs')
        .getByRole('tab', { name: '人员排期', exact: true })
        .click()
      await expect(page.getByTestId('resource-planning-panel')).toBeVisible()
      await expect(page.getByLabel('IANA 时区')).toHaveValue('Asia/Shanghai')
      await expect(page.getByTestId('resource-planning-panel')).toContainText('国庆例外')
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        await expect(page.getByTestId('resource-planning-panel')).toBeVisible()
        expect(await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        )).toBeLessThanOrEqual(1)
      }
      await page.context().setOffline(true)
      const draft = `离线资源草稿 ${suffix}`
      await page.getByTestId('resource-planning-offline-draft').fill(draft)
      await expect(page.getByTestId('resource-planning-offline-draft')).toHaveValue(draft)
      await expect(page.getByTestId('resource-planning-panel')).toContainText('离线 · 本地输入保留')
      await page.context().setOffline(false)
      await page.screenshot({
        path: testInfo.outputPath('s16-resource-planning-820.png'),
        fullPage: true,
      })

      await installSession(page, guest)
      await page.goto(`/project-spaces/${spaceId}/work-items`)
      await page
        .getByTestId('project-work-items-secondary-tabs')
        .getByRole('tab', { name: '人员排期', exact: true })
        .click()
      await expect(page.getByRole('button', { name: '保存工作日历' })).toBeDisabled()
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

async function createSpace(
  request: APIRequestContext,
  owner: E2eSession,
  suffix: string,
  kind: string,
) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(owner),
    data: {
      spaceKey: `s16-m1-${kind}-${suffix.replaceAll('_', '-')}`,
      name: `S16 资源规划 ${kind} ${suffix}`,
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s16-m1-member-${userId}` },
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
