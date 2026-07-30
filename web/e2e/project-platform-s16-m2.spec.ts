import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = { id: string; username: string; displayName: string; password: string }
type TypeSummary = { id: string; typeKey: string }
type Item = { id: string; version: number; title: string }
type Worklog = {
  id: string
  workItemId: string
  userId: string
  workDate: string
  durationMinutes: number
  approvalState: 'draft' | 'submitted' | 'void'
  currentRevision: number
  version: number
  revisions: Array<{ revisionNumber: number; approvalState: string; reason: string }>
}

test.describe('PROJECT-PLATFORM-S16 M2', () => {
  test('worklog immutable revisions exact replay concurrency and responsive recovery @route-final', async ({
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
    const suffix = `s16m2_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterprise, `${suffix}_owner`, 'S16 Worklog Owner')
    const adminIdentity = await createIdentity(request, enterprise, `${suffix}_admin`, 'S16 Worklog Admin')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S16 Worklog Member')
    const guestIdentity = await createIdentity(request, enterprise, `${suffix}_guest`, 'S16 Worklog Guest')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S16 Worklog Outsider')
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
        request, owner, spaceId, typeId, `S16 工时事项 ${suffix}`, `${suffix}-item`,
      )
      const createId = `${suffix}-create`
      const createBody = {
        schemaVersion: 1,
        requestId: createId,
        operation: 'create',
        worklogId: null,
        expectedVersion: 0,
        workItemId: item.id,
        userId: memberIdentity.id,
        workDate: '2026-07-27',
        durationMinutes: 600,
        source: 'proxy',
        reason: '项目负责人代录并保留原因',
      }
      const first = await postJson<Worklog>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/resource-planning/worklogs`,
        owner,
        createBody,
        createId,
      )
      const replay = await postJson<Worklog>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/resource-planning/worklogs`,
        owner,
        createBody,
        createId,
      )
      expect(replay.id).toBe(first.id)
      expect(replay.currentRevision).toBe(1)
      const submitId = `${suffix}-submit`
      const submitted = await postJson<Worklog>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/resource-planning/worklogs`,
        member,
        transitionBody(first, 'submit', submitId, ''),
        submitId,
      )
      expect(submitted.approvalState).toBe('submitted')
      expect(submitted.revisions.map((value) => value.revisionNumber)).toEqual([2, 1])

      for (const session of [owner, spaceAdmin, member, guest]) {
        const visible = await getJson<{ worklogs: Worklog[] }>(
          request,
          `${apiBaseUrl}/project-spaces/${spaceId}/resource-planning/worklogs`,
          session,
        )
        expect(visible.worklogs[0].durationMinutes).toBe(600)
      }
      for (const session of [outsider, enterprise]) {
        const hidden = await request.get(
          `${apiBaseUrl}/project-spaces/${spaceId}/resource-planning/worklogs`,
          { headers: bearer(session) },
        )
        expect([403, 404]).toContain(hidden.status())
        expect(await hidden.text()).not.toContain(item.title)
      }
      const crossSpace = await getJson<{ worklogs: Worklog[] }>(
        request,
        `${apiBaseUrl}/project-spaces/${otherSpaceId}/resource-planning/worklogs`,
        owner,
      )
      expect(crossSpace.worklogs).toEqual([])

      const guestWrite = await request.post(
        `${apiBaseUrl}/project-spaces/${spaceId}/resource-planning/worklogs`,
        {
          headers: { ...bearer(guest), 'X-Colla-Request-Id': `${suffix}-guest` },
          data: transitionBody(submitted, 'withdraw', `${suffix}-guest`, 'guest denied'),
        },
      )
      expect(guestWrite.status()).toBe(403)

      const concurrent = await Promise.all([
        rawMutate(request, member, spaceId, transitionBody(
          submitted, 'withdraw', `${suffix}-withdraw`, '修正日期',
        )),
        rawMutate(request, owner, spaceId, transitionBody(
          submitted, 'void', `${suffix}-void`, '治理作废',
        )),
      ])
      expect(concurrent.map((value) => value.status()).sort()).toEqual([200, 409])

      await installSession(page, owner)
      await page.goto(`/project-spaces/${spaceId}/work-items`)
      await page
        .getByTestId('project-work-items-secondary-tabs')
        .getByRole('tab', { name: '实际工时', exact: true })
        .click()
      await expect(page.getByTestId('resource-worklog-panel')).toBeVisible()
      await expect(page.getByTestId('resource-worklog-panel')).toContainText('600 分钟')
      await expect(page.getByTestId('resource-worklog-panel')).toContainText('修订历史')
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        expect(await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        )).toBeLessThanOrEqual(1)
      }
      await page.context().setOffline(true)
      const draft = `离线工时 ${suffix}`
      await page.getByTestId('resource-worklog-offline-draft').fill(draft)
      await expect(page.getByTestId('resource-worklog-offline-draft')).toHaveValue(draft)
      await page.context().setOffline(false)
      await page.screenshot({
        path: testInfo.outputPath('s16-worklog-820.png'),
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

function transitionBody(
  current: Worklog,
  operation: 'submit' | 'withdraw' | 'void',
  requestId: string,
  reason: string,
) {
  return {
    schemaVersion: 1,
    requestId,
    operation,
    worklogId: current.id,
    expectedVersion: current.version,
    durationMinutes: 0,
    reason,
  }
}

function rawMutate(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  data: ReturnType<typeof transitionBody>,
) {
  return request.post(
    `${apiBaseUrl}/project-spaces/${spaceId}/resource-planning/worklogs`,
    {
      headers: { ...bearer(session), 'X-Colla-Request-Id': data.requestId },
      data,
    },
  )
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

async function createSpace(
  request: APIRequestContext,
  owner: E2eSession,
  suffix: string,
  kind: string,
) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(owner),
    data: {
      spaceKey: `s16-m2-${kind}-${suffix.replaceAll('_', '-')}`,
      name: `S16 工时 ${kind} ${suffix}`,
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s16-m2-member-${userId}` },
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
