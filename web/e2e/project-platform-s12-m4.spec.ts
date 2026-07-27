import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = { id: string; username: string; displayName: string; password: string }
type Item = { id: string; version: number; title: string }
type TypeSummary = { id: string; typeKey: string }
type Workflow = {
  workItemVersion: number
  aggregateVersion: number
  tasks: Array<{ id: string; nodeKey: string; dueAt?: string | null }>
}

test.describe('PROJECT-PLATFORM-S12 M4', () => {
  test('activity, reminder, nudge and notification converge for six isolated identities', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(480_000)
    page.setDefaultTimeout(25_000)
    requireIsolatedIdentityFixture()
    const enterpriseAdmin = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(request, `${apiBaseUrl}/auth/me`, enterpriseAdmin)
    const suffix = `s12m4_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_owner`, 'S12 Activity Owner')
    const adminIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_admin`, 'S12 Activity Admin')
    const memberIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_member`, 'S12 Activity Member')
    const guestIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_guest`, 'S12 Activity Guest')
    const outsiderIdentity = await createIdentity(request, enterpriseAdmin, `${suffix}_outsider`, 'S12 Activity Outsider')
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
      const title = `S12 动态提醒催办 ${'长名称'.repeat(24)}`
      let item = await createItem(request, owner, spaceId, projectType.id, title, `${suffix}-item`)
      await putParticipant(request, owner, spaceId, item, memberIdentity.id, 'assignee')
      item = await getJson<Item>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-items/${item.id}`,
        owner,
      )

      const workflow = await getJson<Workflow>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-items/${item.id}/node-workflow`,
        member,
      )
      const task = workflow.tasks.find((candidate) => candidate.nodeKey === 'plan')
      expect(task?.dueAt).toBeTruthy()
      if (!task) throw new Error('plan task missing')
      await postJson(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-items/${item.id}/node-workflow/tasks/${task.id}/actions/claim`,
        member,
        {
          expectedWorkItemVersion: workflow.workItemVersion,
          expectedInstanceVersion: workflow.aggregateVersion,
          fieldPatch: {},
          artifacts: [],
        },
        `${suffix}-claim`,
      )

      const activities = await getJson<{
        items: Array<{ workItemId: string; title: string; sequence: number }>
        unreadCount: number
      }>(request, `${apiBaseUrl}/personal-work/activities`, member)
      expect(activities.items).toContainEqual(expect.objectContaining({ workItemId: item.id, title }))
      expect(activities.unreadCount).toBeGreaterThan(0)
      const latestSequence = Math.max(...activities.items.map((activity) => activity.sequence))
      const readState = await postJson<{ readThroughSequence: number }>(
        request,
        `${apiBaseUrl}/personal-work/activities:read`,
        member,
        { throughSequence: latestSequence },
        `${suffix}-activity-read`,
      )
      expect(readState.readThroughSequence).toBe(latestSequence)

      const reminders = await getJson<{
        items: Array<{ workItemId: string; state: string; dueAt: string }>
      }>(request, `${apiBaseUrl}/personal-work/reminders?timezone=Asia%2FShanghai`, member)
      expect(reminders.items).toContainEqual(expect.objectContaining({
        workItemId: item.id,
        state: 'approaching',
      }))
      await postJson(
        request,
        `${apiBaseUrl}/personal-work/reminders:dispatch`,
        member,
        { timezone: 'Asia/Shanghai', requestId: `${suffix}-reminders` },
        `${suffix}-reminders-http`,
      )

      const nudgeRequest = `${suffix}-nudge`
      const firstNudge = await postJson<{ receiptId: string; replayed: boolean }>(
        request,
        `${apiBaseUrl}/personal-work/spaces/${spaceId}/work-items/${item.id}/nudges`,
        owner,
        { recipientId: memberIdentity.id, requestId: nudgeRequest },
        `${suffix}-nudge-http-1`,
      )
      const replayedNudge = await postJson<{ receiptId: string; replayed: boolean }>(
        request,
        `${apiBaseUrl}/personal-work/spaces/${spaceId}/work-items/${item.id}/nudges`,
        owner,
        { recipientId: memberIdentity.id, requestId: nudgeRequest },
        `${suffix}-nudge-http-2`,
      )
      expect(replayedNudge).toMatchObject({ receiptId: firstNudge.receiptId, replayed: true })
      const rateLimited = await request.post(
        `${apiBaseUrl}/personal-work/spaces/${spaceId}/work-items/${item.id}/nudges`,
        {
          headers: bearer(owner),
          data: { recipientId: memberIdentity.id, requestId: `${suffix}-second-nudge` },
        },
      )
      expect(rateLimited.status()).toBe(429)
      const outsiderNudge = await request.post(
        `${apiBaseUrl}/personal-work/spaces/${spaceId}/work-items/${item.id}/nudges`,
        {
          headers: bearer(outsider),
          data: { recipientId: memberIdentity.id, requestId: `${suffix}-outsider-nudge` },
        },
      )
      expect(outsiderNudge.status()).toBe(404)

      const notifications = await pollNotifications(request, member, item.id)
      expect(notifications.filter((notification) => notification.targetId === item.id))
        .toEqual(expect.arrayContaining([
          expect.objectContaining({ notificationType: 'project_work_item_reminder' }),
          expect.objectContaining({ notificationType: 'project_work_item_nudge' }),
        ]))

      for (const session of [spaceAdmin, guest, outsider, enterpriseAdmin]) {
        const hiddenActivities = await getJson<{ items: Array<{ workItemId: string }> }>(
          request,
          `${apiBaseUrl}/personal-work/activities`,
          session,
        )
        expect(hiddenActivities.items.map((activity) => activity.workItemId)).not.toContain(item.id)
        expect(JSON.stringify(hiddenActivities)).not.toContain(title)
      }

      await installSession(page, member)
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        await page.goto('/notifications')
        await expect(page.getByText('个人动态')).toBeVisible()
        await expect(page.getByText('待办提醒')).toBeVisible()
        await expect(page.getByText(title, { exact: false }).first()).toBeVisible()
        expect(await page.evaluate(() =>
          document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1)
      }
      await page.context().setOffline(true)
      await page.reload().catch(() => undefined)
      await page.context().setOffline(false)
      await page.reload()
      await expect(page.getByText('个人动态')).toBeVisible()
      await page.screenshot({ path: testInfo.outputPath('s12-m4-notifications-820.png'), fullPage: true })

      item = await getJson<Item>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-items/${item.id}`,
        owner,
      )
      await postJson(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-items/${item.id}:archive`,
        owner,
        { expectedVersion: item.version },
        `${suffix}-archive-item`,
      )
      const afterRevocation = await getJson<Array<{ targetId?: string; title: string }>>(
        request,
        `${apiBaseUrl}/notifications?limit=100`,
        member,
      )
      expect(afterRevocation.map((notification) => notification.targetId)).not.toContain(item.id)
      expect(JSON.stringify(afterRevocation)).not.toContain(title)
      const consistency = await postJson<{ rebuilt: boolean; failures: string[] }>(
        request,
        `${apiBaseUrl}/personal-work/consistency`,
        member,
        { dryRun: false, rebuild: true },
        `${suffix}-rebuild`,
      )
      expect(consistency).toMatchObject({ rebuilt: true, failures: [] })
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

async function pollNotifications(request: APIRequestContext, session: E2eSession, itemId: string) {
  const deadline = Date.now() + 45_000
  do {
    const notifications = await getJson<Array<{
      notificationType: string
      targetId?: string
      title: string
    }>>(request, `${apiBaseUrl}/notifications?limit=100`, session)
    if (notifications.filter((notification) => notification.targetId === itemId).length >= 2) {
      return notifications
    }
    await new Promise((resolve) => setTimeout(resolve, 400))
  } while (Date.now() < deadline)
  return getJson(request, `${apiBaseUrl}/notifications?limit=100`, session)
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
    `${suffix}-validate`,
  )
  await postJson(
    request,
    `${base}/draft:publish`,
    owner,
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
      headers: { ...bearer(session), 'X-Colla-Request-Id': `s12-m4-participant-${userId}` },
      data: { role, expectedVersion: item.version },
    },
  )
  expect(response.ok(), await response.text()).toBeTruthy()
}

async function createSpace(request: APIRequestContext, owner: E2eSession, suffix: string) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(owner),
    data: {
      spaceKey: `s12-m4-${suffix.replaceAll('_', '-')}`,
      name: `S12 动态提醒 ${suffix}`,
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s12-m4-member-${userId}` },
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

async function postJson<T = unknown>(
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
