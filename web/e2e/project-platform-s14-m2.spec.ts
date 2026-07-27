import { expect, test, type APIRequestContext, type APIResponse } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = { id: string; username: string; displayName: string; password: string }
type TypeSummary = { id: string; typeKey: string }
type Item = { id: string; version: number; title: string }
type Member = { id: string; userId: string }
type CalendarEvent = {
  workItemId: string
  title: string
  workItemVersion: number
  startValue?: string | null
  endValue?: string | null
  displayStartDate?: string | null
  displayEndDate?: string | null
}
type Calendar = {
  days: Array<{ date: string; events: CalendarEvent[] }>
  noDateEvents: CalendarEvent[]
  visibleEventCount: number
  candidateBoundReached: boolean
}

test.describe('PROJECT-PLATFORM-S14 M2', () => {
  test('calendar dates, DST, exact replay and recovery close on isolated identities @route-final', async ({
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
    const suffix = `s14m2_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterprise, `${suffix}_owner`, 'S14 Calendar Owner')
    const adminIdentity = await createIdentity(request, enterprise, `${suffix}_admin`, 'S14 Calendar Admin')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S14 Calendar Member')
    const guestIdentity = await createIdentity(request, enterprise, `${suffix}_guest`, 'S14 Calendar Guest')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S14 Calendar Outsider')
    const owner = await loginByApi(request, ownerIdentity.username, ownerIdentity.password)
    const spaceAdmin = await loginByApi(request, adminIdentity.username, adminIdentity.password)
    const member = await loginByApi(request, memberIdentity.username, memberIdentity.password)
    const guest = await loginByApi(request, guestIdentity.username, guestIdentity.password)
    const outsider = await loginByApi(request, outsiderIdentity.username, outsiderIdentity.password)
    let spaceId: string | undefined
    let otherSpaceId: string | undefined

    try {
      spaceId = await createSpace(request, owner, suffix, 'primary')
      await addMember(request, owner, spaceId, adminIdentity.id, 'admin')
      await addMember(request, owner, spaceId, memberIdentity.id, 'member')
      await addMember(request, owner, spaceId, guestIdentity.id, 'guest')
      const typeId = await projectType(request, owner, spaceId)
      await addDateFields(request, owner, spaceId, typeId, suffix)
      await validateAndPublish(request, owner, spaceId, typeId, `${suffix}-primary`)
      const longTitle = `S14 日历 ${'跨时区超长标题'.repeat(20)}`
      const dated = await createItem(
        request, owner, spaceId, typeId, longTitle,
        { start_date: '2026-03-07', end_date: '2026-03-09' },
        `${suffix}-dated`,
      )
      const noDate = await createItem(
        request, owner, spaceId, typeId, 'S14 未安排日程', {}, `${suffix}-no-date`,
      )
      const viewKey = `type-${typeId}`
      const body = calendarRequest(typeId, viewKey)

      for (const session of [owner, spaceAdmin, member, guest]) {
        const calendar = await renderCalendar(
          request, session, spaceId, body, `${suffix}-matrix-${session.accessToken.slice(-6)}`,
        )
        expect(calendar.visibleEventCount).toBe(1)
        expect(calendar.noDateEvents).toHaveLength(1)
        expect(calendar.candidateBoundReached).toBeFalsy()
        expect(event(calendar, dated.id)).toMatchObject({
          startValue: '2026-03-07',
          endValue: '2026-03-09',
          displayStartDate: '2026-03-07',
          displayEndDate: '2026-03-09',
        })
      }
      for (const session of [outsider, enterprise]) {
        const hidden = await request.post(
          `${apiBaseUrl}/project-spaces/${spaceId}/work-item-calendars:render`,
          { headers: bearer(session), data: body },
        )
        expect([403, 404]).toContain(hidden.status())
        expect(await hidden.text()).not.toContain(longTitle)
      }

      otherSpaceId = await createSpace(request, owner, suffix, 'other')
      const otherTypeId = await projectType(request, owner, otherSpaceId)
      await addDateFields(request, owner, otherSpaceId, otherTypeId, `${suffix}-other`)
      await validateAndPublish(request, owner, otherSpaceId, otherTypeId, `${suffix}-other`)
      const other = await createItem(
        request, owner, otherSpaceId, otherTypeId, 'S14 跨空间隐藏日程',
        { start_date: '2026-03-08', end_date: '2026-03-08' }, `${suffix}-other-item`,
      )

      await savePreference(request, owner, spaceId, viewKey, `${suffix}-owner-pref`)
      const initial = await renderCalendar(request, owner, spaceId, body, `${suffix}-initial`)
      const datedEvent = event(initial, dated.id)
      const moveA = mutation(datedEvent, `${suffix}-move-a`, '2026-03-08', '2026-03-10')
      const moveB = mutation(datedEvent, `${suffix}-move-b`, '2026-03-09', '2026-03-11')
      const attempts = await Promise.all([
        rawMutation(request, owner, spaceId, viewKey, dated.id, moveA),
        rawMutation(request, owner, spaceId, viewKey, dated.id, moveB),
      ])
      expect(attempts.filter((response) => response.ok())).toHaveLength(1)
      expect(attempts.find((response) => !response.ok())?.status()).toBe(409)
      const winner = attempts.find((response) => response.ok())
      if (!winner) throw new Error('date mutation winner missing')
      const winnerBody = winner === attempts[0] ? moveA : moveB
      const winnerResult = await winner.json()
      const replay = await rawMutation(request, owner, spaceId, viewKey, dated.id, winnerBody)
      expect(replay.ok(), await replay.text()).toBeTruthy()
      expect(await replay.json()).toMatchObject({ ...winnerResult, replayed: true })

      const crossSpace = await rawMutation(
        request, owner, spaceId, viewKey, other.id,
        { ...mutation(datedEvent, `${suffix}-cross`, '2026-03-10', '2026-03-10'), expectedWorkItemVersion: other.version },
      )
      expect([403, 404]).toContain(crossSpace.status())
      expect(await crossSpace.text()).not.toContain(other.title)

      await savePreference(request, member, spaceId, viewKey, `${suffix}-member-pref`)
      const memberCalendar = await renderCalendar(
        request, member, spaceId, body, `${suffix}-member-before-revoke`,
      )
      const memberEvent = event(memberCalendar, dated.id)
      const members = await getJson<Member[]>(
        request, `${apiBaseUrl}/project-spaces/${spaceId}/members`, owner,
      )
      const memberRecord = members.find((candidate) => candidate.userId === memberIdentity.id)
      if (!memberRecord) throw new Error('member record missing')
      const removed = await request.delete(
        `${apiBaseUrl}/project-spaces/${spaceId}/members/${memberRecord.id}`,
        { headers: bearer(owner) },
      )
      expect(removed.ok(), await removed.text()).toBeTruthy()
      const revoked = await rawMutation(
        request, member, spaceId, viewKey, dated.id,
        mutation(memberEvent, `${suffix}-revoked`, '2026-03-12', '2026-03-14'),
      )
      expect([403, 404]).toContain(revoked.status())

      await installSession(page, owner)
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto(`/project-spaces/${spaceId}/work-items?typeId=${typeId}`)
      await page.getByLabel('工作项视图模式').getByText('日历').click()
      await page.getByLabel('日历锚点日期').fill('2026-03-08')
      await page.getByLabel('日历开始日期字段').fill('start_date')
      await page.getByLabel('日历结束日期字段').fill('end_date')
      await page.getByLabel('日历时区').fill('America/New_York')
      const calendar = page.getByTestId('project-work-item-calendar')
      await expect(calendar).toBeVisible()
      await expect(calendar).toContainText(longTitle)
      await expect(calendar).toContainText(noDate.title)

      await page.context().setOffline(true)
      await page.getByLabel('日历时区').fill('Asia/Shanghai')
      await page.getByRole('button', { name: '保存偏好' }).click()
      await page.context().setOffline(false)
      await expect(page.getByLabel('日历时区')).toHaveValue('Asia/Shanghai')
      await page.getByRole('button', { name: '保存偏好' }).click()
      await expect(page.getByText('视图偏好已保存')).toBeVisible()

      const noDateInput = page.getByLabel(`安排 ${noDate.title} 日期`)
      await noDateInput.fill('2026-03-15')
      await expect(page.getByText('日历日期已更新')).toBeVisible()

      for (const width of [1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        await expect(calendar).toBeVisible()
        const overflow = await page.evaluate(() =>
          document.documentElement.scrollWidth - document.documentElement.clientWidth)
        expect(overflow).toBeLessThanOrEqual(1)
      }
      await page.screenshot({ path: testInfo.outputPath('s14-calendar-820.png'), fullPage: true })

      await installSession(page, guest)
      await page.goto(`/project-spaces/${spaceId}/work-items?typeId=${typeId}`)
      await page.getByLabel('工作项视图模式').getByText('日历').click()
      await page.getByLabel('日历锚点日期').fill('2026-03-08')
      await page.getByLabel('日历开始日期字段').fill('start_date')
      await page.getByLabel('日历结束日期字段').fill('end_date')
      await expect(page.getByTestId('project-work-item-calendar')).toBeVisible()
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

function calendarRequest(typeId: string, viewKey: string) {
  return {
    schemaVersion: 1,
    viewKey,
    binding: { startField: 'start_date', endField: 'end_date', allDay: true },
    window: {
      startDate: '2026-03-01',
      endDate: '2026-03-31',
      timezone: 'America/New_York',
      mode: 'month',
    },
    query: {
      schemaVersion: 1,
      typeId,
      filter: null,
      sorts: [{ field: 'updatedAt', direction: 'desc', nulls: 'last' }],
      group: null,
      select: ['id', 'displayKey', 'title', 'status'],
      limit: 100,
      cursor: null,
    },
  }
}

function event(calendar: Calendar, itemId: string) {
  const result = calendar.days.flatMap((day) => day.events)
    .find((candidate) => candidate.workItemId === itemId)
  if (!result) throw new Error(`calendar event ${itemId} missing`)
  return result
}

function mutation(
  item: CalendarEvent,
  requestId: string,
  startValue: string,
  endValue: string,
) {
  return {
    requestId,
    expectedWorkItemVersion: item.workItemVersion,
    operation: 'move',
    startValue,
    endValue,
    timezone: 'America/New_York',
  }
}

async function renderCalendar(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  body: unknown,
  requestId: string,
) {
  return postJson<Calendar>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/work-item-calendars:render`,
    session,
    body,
    requestId,
  )
}

async function savePreference(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  viewKey: string,
  requestId: string,
) {
  const response = await request.put(
    `${apiBaseUrl}/project-spaces/${spaceId}/work-item-calendars/${viewKey}/preference`,
    {
      headers: { ...bearer(session), 'X-Colla-Request-Id': requestId },
      data: {
        requestId,
        expectedVersion: 0,
        binding: { startField: 'start_date', endField: 'end_date', allDay: true },
        timezone: 'America/New_York',
        mode: 'month',
      },
    },
  )
  expect(response.ok(), await response.text()).toBeTruthy()
}

function rawMutation(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  viewKey: string,
  itemId: string,
  data: Record<string, unknown>,
): Promise<APIResponse> {
  return request.post(
    `${apiBaseUrl}/project-spaces/${spaceId}/work-item-calendars/${viewKey}/items/${itemId}:date`,
    {
      headers: { ...bearer(session), 'X-Colla-Request-Id': String(data.requestId) },
      data,
    },
  )
}

async function addDateFields(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  typeId: string,
  suffix: string,
) {
  const url = `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}/fields`
  for (const [fieldKey, name, sortOrder] of [
    ['start_date', 'Start date', 80],
    ['end_date', 'End date', 90],
  ] as const) {
    const response = await request.post(url, {
      headers: { ...bearer(owner), 'X-Colla-Request-Id': `${suffix}-field-${fieldKey}` },
      data: { fieldKey, name, fieldType: 'date', config: {}, sortOrder },
    })
    expect(response.ok(), await response.text()).toBeTruthy()
  }
}

async function projectType(request: APIRequestContext, owner: E2eSession, spaceId: string) {
  const types = await getJson<{ items: TypeSummary[] }>(
    request, `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`, owner,
  )
  const taskType = types.items.find((candidate) => candidate.typeKey === 'task')
  if (!taskType) throw new Error('task preset missing')
  return taskType.id
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
  fieldValues: Record<string, string>,
  requestId: string,
) {
  return postJson<Item>(
    request, `${apiBaseUrl}/project-spaces/${spaceId}/work-items`, session,
    { typeId, title, fieldValues }, requestId,
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
      spaceKey: `s14-m2-${kind}-${suffix.replaceAll('_', '-')}`,
      name: `S14 Calendar ${kind} ${suffix}`,
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s14-m2-member-${userId}` },
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
  expect(response.ok(), `identity fixture creation failed for ${username}`).toBeTruthy()
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
