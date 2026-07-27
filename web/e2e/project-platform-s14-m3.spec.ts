import { expect, test, type APIRequestContext, type APIResponse } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = { id: string; username: string; displayName: string; password: string }
type Item = { id: string; version: number; title: string }
type GanttBar = {
  workItemId: string
  title: string
  workItemVersion: number
  startDate?: string | null
  endDate?: string | null
  critical: boolean
}
type Gantt = {
  rows: Array<{ workItemId: string; depth: number; bar: GanttBar }>
  dependencies: Array<{
    relationId: string
    sourceWorkItemId: string
    targetWorkItemId: string
    critical: boolean
  }>
  criticalPathAvailable: boolean
  criticalPathReason: string
  truncated: boolean
}

test.describe('PROJECT-PLATFORM-S14 M3', () => {
  test('gantt hierarchy, dependency and schedule recovery close on isolated identities @route-final', async ({
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
    const suffix = `s14m3_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterprise, `${suffix}_owner`, 'S14 Gantt Owner')
    const adminIdentity = await createIdentity(request, enterprise, `${suffix}_admin`, 'S14 Gantt Admin')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S14 Gantt Member')
    const guestIdentity = await createIdentity(request, enterprise, `${suffix}_guest`, 'S14 Gantt Guest')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S14 Gantt Outsider')
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
      const typeId = await projectType(request, owner, spaceId)
      await addDateFields(request, owner, spaceId, typeId, suffix)
      await validateAndPublish(request, owner, spaceId, typeId, suffix)
      const predecessor = await createItem(
        request,
        owner,
        spaceId,
        typeId,
        `S14 甘特前置 ${'超长排期'.repeat(20)}`,
        { start_date: '2026-07-01', end_date: '2026-07-03' },
        `${suffix}-predecessor`,
      )
      const successor = await createItem(
        request,
        owner,
        spaceId,
        typeId,
        'S14 甘特后继',
        { start_date: '2026-07-04', end_date: '2026-07-07' },
        `${suffix}-successor`,
      )
      const relation = await postJson<{ id: string }>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-item-relations`,
        owner,
        {
          relationKey: 'depends_on',
          sourceWorkItemId: successor.id,
          targetWorkItemId: predecessor.id,
          expectedSourceVersion: successor.version,
          expectedTargetVersion: predecessor.version,
        },
        `${suffix}-dependency`,
      )
      const viewKey = `type-${typeId}`
      await savePreference(request, owner, spaceId, viewKey, `${suffix}-owner-pref`)
      const body = ganttRequest(typeId, viewKey)

      for (const session of [owner, spaceAdmin, member, guest]) {
        const gantt = await renderGantt(
          request, session, spaceId, body, `${suffix}-matrix-${session.accessToken.slice(-6)}`,
        )
        expect(gantt.rows).toHaveLength(2)
        expect(gantt.dependencies).toEqual([
          expect.objectContaining({
            relationId: relation.id,
            sourceWorkItemId: successor.id,
            targetWorkItemId: predecessor.id,
            critical: true,
          }),
        ])
        expect(gantt.criticalPathAvailable).toBeTruthy()
        expect(gantt.criticalPathReason).toBe('ok')
        expect(gantt.truncated).toBeFalsy()
      }
      for (const session of [outsider, enterprise]) {
        const hidden = await request.post(
          `${apiBaseUrl}/project-spaces/${spaceId}/work-item-gantts:render`,
          { headers: bearer(session), data: body },
        )
        expect([403, 404]).toContain(hidden.status())
        expect(await hidden.text()).not.toContain(predecessor.title)
      }

      const baselineRequestId = `${suffix}-baseline-create`
      const baselineCommand = {
        schemaVersion: 1,
        requestId: baselineRequestId,
        name: `S14 发布基线 ${'长名称'.repeat(16)}`,
        request: body,
      }
      const baseline = await postJson<{
        baseline: { id: string; version: number; name: string }
        entries: Array<{ workItemId: string }>
        dependencies: Array<{ relationId: string }>
      }>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-item-schedule-baselines`,
        owner,
        baselineCommand,
        baselineRequestId,
      )
      expect(baseline.entries).toHaveLength(2)
      expect(baseline.dependencies).toEqual([
        expect.objectContaining({ relationId: relation.id }),
      ])
      const baselineReplay = await postJson<typeof baseline>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-item-schedule-baselines`,
        owner,
        baselineCommand,
        baselineRequestId,
      )
      expect(baselineReplay).toEqual(baseline)
      const memberBaselines = await getJson<unknown[]>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-item-schedule-baselines`,
        member,
      )
      expect(memberBaselines).toEqual([])
      const initialDiff = await postJson<{
        entries: unknown[]
        addedDependencies: number
        removedDependencies: number
      }>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-item-schedule-baselines/${baseline.baseline.id}:compare`,
        owner,
        body,
      )
      expect(initialDiff).toMatchObject({
        entries: [],
        addedDependencies: 0,
        removedDependencies: 0,
      })
      const timeline = await postJson<{
        events: Array<{ sourceKind: string; eventType: string }>
        truncated: boolean
      }>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-item-timeline:render`,
        owner,
        { schemaVersion: 1, request: body, limit: 100 },
      )
      expect(timeline.events.length).toBeGreaterThan(0)
      expect(timeline.events.map((event) => event.sourceKind)).toContain('relation')
      expect(timeline.truncated).toBeFalsy()
      const hiddenTimeline = await request.post(
        `${apiBaseUrl}/project-spaces/${spaceId}/work-item-timeline:render`,
        {
          headers: bearer(outsider),
          data: { schemaVersion: 1, request: body, limit: 100 },
        },
      )
      expect([403, 404]).toContain(hiddenTimeline.status())
      expect(await hiddenTimeline.text()).not.toContain(predecessor.title)

      await savePreference(request, member, spaceId, viewKey, `${suffix}-member-pref`)
      const memberGantt = await renderGantt(
        request, member, spaceId, body, `${suffix}-member-before-race`,
      )
      const memberBar = memberGantt.rows.find((row) => row.workItemId === successor.id)?.bar
      if (!memberBar) throw new Error('member schedule bar missing')
      const moveA = mutation(memberBar, `${suffix}-move-a`, '2026-07-05', '2026-07-08')
      const moveB = mutation(memberBar, `${suffix}-move-b`, '2026-07-06', '2026-07-09')
      const attempts = await Promise.all([
        rawMutation(request, member, spaceId, viewKey, successor.id, moveA),
        rawMutation(request, member, spaceId, viewKey, successor.id, moveB),
      ])
      expect(attempts.filter((response) => response.ok())).toHaveLength(1)
      expect(attempts.find((response) => !response.ok())?.status()).toBe(409)
      const winner = attempts.find((response) => response.ok())
      if (!winner) throw new Error('gantt move winner missing')
      const winnerBody = winner === attempts[0] ? moveA : moveB
      const winnerResult = await winner.json()
      const replay = await rawMutation(
        request, member, spaceId, viewKey, successor.id, winnerBody,
      )
      expect(replay.ok(), await replay.text()).toBeTruthy()
      expect(await replay.json()).toMatchObject({ ...winnerResult, replayed: true })
      const changedDiff = await postJson<{ entries: Array<{ workItemId: string }> }>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/work-item-schedule-baselines/${baseline.baseline.id}:compare`,
        owner,
        body,
      )
      expect(changedDiff.entries).toEqual([
        expect.objectContaining({ workItemId: successor.id }),
      ])

      const members = await getJson<Array<{ id: string; userId: string }>>(
        request, `${apiBaseUrl}/project-spaces/${spaceId}/members`, owner,
      )
      const memberRecord = members.find((candidate) => candidate.userId === memberIdentity.id)
      if (!memberRecord) throw new Error('member record missing')
      const removed = await request.delete(
        `${apiBaseUrl}/project-spaces/${spaceId}/members/${memberRecord.id}`,
        { headers: bearer(owner) },
      )
      expect(removed.ok(), await removed.text()).toBeTruthy()
      const revoked = await request.post(
        `${apiBaseUrl}/project-spaces/${spaceId}/work-item-gantts:render`,
        { headers: bearer(member), data: body },
      )
      expect([403, 404]).toContain(revoked.status())

      await installSession(page, owner)
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto(`/project-spaces/${spaceId}/work-items?typeId=${typeId}`)
      await page.getByLabel('工作项视图模式').getByText('甘特').click()
      await page.getByLabel('甘特锚点日期').fill('2026-07-01')
      await page.getByLabel('甘特开始日期字段').fill('start_date')
      await page.getByLabel('甘特结束日期字段').fill('end_date')
      await page.getByLabel('甘特时区').fill('Asia/Shanghai')
      const gantt = page.getByTestId('project-work-item-gantt')
      await expect(gantt).toBeVisible()
      await expect(gantt).toContainText(predecessor.title)
      await expect(gantt).toContainText('关键路径已派生')
      await expect(gantt).toContainText('依赖线 1')
      const scheduleHistory = page.getByTestId('work-item-schedule-history')
      await expect(scheduleHistory).toBeVisible()
      await expect(page.getByLabel('排期时间线')).toContainText('relation')

      await page.context().setOffline(true)
      await page.getByLabel('基线名称').fill('S14 UI 离线恢复基线')
      await page.getByRole('button', { name: '创建基线' }).click()
      await expect(page.getByLabel('基线名称')).toHaveValue('S14 UI 离线恢复基线')
      await page.context().setOffline(false)
      await expect(page.getByText('排期基线已创建')).toBeVisible({ timeout: 45_000 })
      await page.getByRole('button', { name: '比较当前排期' }).click()
      await expect(page.getByText('基线差异已按当前权限重新计算')).toBeVisible()
      await expect(page.getByLabel('基线差异摘要')).toBeVisible()
      await page.getByRole('button', { name: '删除基线' }).click()
      await expect(page.getByText('排期基线已删除')).toBeVisible()

      await page.context().setOffline(true)
      await page.getByLabel('甘特缩放').click()
      await page.getByText('月', { exact: true }).last().click()
      await page.getByRole('button', { name: '保存偏好' }).click()
      await page.context().setOffline(false)
      await expect(page.locator('.ant-select').filter({
        has: page.getByLabel('甘特缩放'),
      })).toContainText('月')
      await page.getByRole('button', { name: '保存偏好' }).click()
      await expect(page.getByText('视图偏好已保存')).toBeVisible()

      const successorBar = gantt.getByLabel(/TASK-\d+ S14 甘特后继 排期/)
      await successorBar.focus()
      await successorBar.press('ArrowRight')
      await expect(page.getByText('甘特排期已更新')).toBeVisible()

      for (const width of [1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        await expect(gantt).toBeVisible()
        const overflow = await page.evaluate(() =>
          document.documentElement.scrollWidth - document.documentElement.clientWidth)
        expect(overflow).toBeLessThanOrEqual(1)
      }
      await page.screenshot({ path: testInfo.outputPath('s14-gantt-820.png'), fullPage: true })
    } finally {
      await page.context().setOffline(false).catch(() => undefined)
      if (spaceId) {
        await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`, {
          headers: bearer(owner),
        }).catch(() => undefined)
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

function ganttRequest(typeId: string, viewKey: string) {
  return {
    schemaVersion: 1,
    viewKey,
    binding: { startField: 'start_date', endField: 'end_date', allDay: true },
    window: {
      startDate: '2026-07-01',
      endDate: '2026-07-31',
      timezone: 'Asia/Shanghai',
      mode: 'month',
    },
    query: {
      schemaVersion: 1,
      typeId,
      filter: null,
      sorts: [{ field: 'updatedAt', direction: 'asc', nulls: 'last' }],
      group: null,
      select: ['id', 'displayKey', 'title', 'status'],
      limit: 100,
      cursor: null,
    },
    hierarchyRelationKey: 'parent_child',
    expandedNodeIds: [],
    criticalPath: true,
  }
}

function mutation(
  bar: GanttBar,
  requestId: string,
  startValue: string,
  endValue: string,
) {
  return {
    requestId,
    expectedWorkItemVersion: bar.workItemVersion,
    operation: 'move',
    startValue,
    endValue,
    timezone: 'Asia/Shanghai',
  }
}

async function renderGantt(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  body: unknown,
  requestId: string,
) {
  return postJson<Gantt>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/work-item-gantts:render`,
    session,
    body,
    requestId,
  )
}

async function rawMutation(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  viewKey: string,
  itemId: string,
  data: Record<string, unknown>,
): Promise<APIResponse> {
  return request.post(
    `${apiBaseUrl}/project-spaces/${spaceId}/work-item-gantts/${viewKey}/items/${itemId}:date`,
    {
      headers: { ...bearer(session), 'X-Colla-Request-Id': String(data.requestId) },
      data,
    },
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
    `${apiBaseUrl}/project-spaces/${spaceId}/work-item-gantts/${viewKey}/preference`,
    {
      headers: { ...bearer(session), 'X-Colla-Request-Id': requestId },
      data: {
        requestId,
        expectedVersion: 0,
        binding: { startField: 'start_date', endField: 'end_date', allDay: true },
        timezone: 'Asia/Shanghai',
        zoom: 'week',
        hierarchyRelationKey: 'parent_child',
        expandedNodeIds: [],
      },
    },
  )
  expect(response.ok(), await response.text()).toBeTruthy()
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
  const types = await getJson<{ items: Array<{ id: string; typeKey: string }> }>(
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

async function createSpace(request: APIRequestContext, owner: E2eSession, suffix: string) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(owner),
    data: {
      spaceKey: `s14-m3-${suffix.replaceAll('_', '-')}`,
      name: `S14 Gantt ${suffix}`,
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s14-m3-member-${userId}` },
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
