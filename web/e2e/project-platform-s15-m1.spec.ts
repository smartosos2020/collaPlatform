import { expect, test, type APIRequestContext, type APIResponse } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = { id: string; username: string; displayName: string; password: string }
type Plan = {
  plan: {
    id: string
    name: string
    description: string
    startDate: string
    endDate: string
    status: 'draft' | 'published' | 'archived'
    version: number
  }
  phases: Array<{
    id: string
    phaseKey: string
    name: string
    position: number
    startDate: string
    endDate: string
    status: string
  }>
  milestones: Array<{
    id: string
    phaseId: string
    milestoneKey: string
    name: string
    position: number
    targetDate: string
    status: string
    ownerUserId: string | null
  }>
  links: unknown[]
  changes: Array<{ operation: string; planVersion: number }>
  progress: {
    visibleMilestones: number
    completedMilestones: number
    visibleLinks: number
    overdueMilestones: number
    completionPercent: number
    truncated: boolean
  }
}

test.describe('PROJECT-PLATFORM-S15 M1', () => {
  test('plan milestones exact lifecycle and responsive recovery close on isolated identities @route-final', async ({
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
    const suffix = `s15m1_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterprise, `${suffix}_owner`, 'S15 Plan Owner')
    const adminIdentity = await createIdentity(request, enterprise, `${suffix}_admin`, 'S15 Plan Admin')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S15 Plan Member')
    const guestIdentity = await createIdentity(request, enterprise, `${suffix}_guest`, 'S15 Plan Guest')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S15 Plan Outsider')
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
      const longName = `S15 交付计划 ${'长名称'.repeat(30)}`
      const createBody = planCreateBody(
        `${suffix}-create`, longName, ownerIdentity.id,
      )
      const first = await postJson<Plan>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/project-plans`,
        owner,
        createBody,
        createBody.requestId,
      )
      const replay = await postJson<Plan>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/project-plans`,
        owner,
        createBody,
        createBody.requestId,
      )
      expect(replay.plan.id).toBe(first.plan.id)
      expect(replay.plan.version).toBe(first.plan.version)
      expect(first.phases).toHaveLength(2)
      expect(first.milestones).toHaveLength(2)
      expect(first.progress.visibleMilestones).toBe(2)

      for (const session of [owner, spaceAdmin, member, guest]) {
        const plans = await getJson<Array<{ id: string; name: string }>>(
          request,
          `${apiBaseUrl}/project-spaces/${spaceId}/project-plans`,
          session,
        )
        expect(plans).toContainEqual(expect.objectContaining({
          id: first.plan.id,
          name: longName,
        }))
      }
      for (const session of [outsider, enterprise]) {
        const hidden = await request.get(
          `${apiBaseUrl}/project-spaces/${spaceId}/project-plans`,
          { headers: bearer(session) },
        )
        expect([403, 404]).toContain(hidden.status())
        expect(await hidden.text()).not.toContain(longName)
      }
      const crossSpace = await request.get(
        `${apiBaseUrl}/project-spaces/${otherSpaceId}/project-plans/${first.plan.id}`,
        { headers: bearer(owner) },
      )
      expect(crossSpace.status()).toBe(404)
      expect(await crossSpace.text()).not.toContain(longName)

      const guestWrite = await request.post(
        `${apiBaseUrl}/project-spaces/${spaceId}/project-plans`,
        {
          headers: { ...bearer(guest), 'X-Colla-Request-Id': `${suffix}-guest-create` },
          data: { ...createBody, requestId: `${suffix}-guest-create` },
        },
      )
      expect(guestWrite.status()).toBe(403)

      const concurrentA = mutateBody(
        first, 'update', `${suffix}-update-a`, '并发计划 A',
      )
      const concurrentB = mutateBody(
        first, 'update', `${suffix}-update-b`, '并发计划 B',
      )
      const concurrent = await Promise.all([
        rawMutate(request, member, spaceId, first.plan.id, concurrentA),
        rawMutate(request, member, spaceId, first.plan.id, concurrentB),
      ])
      expect(concurrent.map((response) => response.status()).sort()).toEqual([200, 409])
      const winnerResponse = concurrent.find((response) => response.ok())
      if (!winnerResponse) throw new Error('plan update winner missing')
      const winner = await winnerResponse.json() as Plan
      expect(['并发计划 A', '并发计划 B']).toContain(winner.plan.name)
      expect(winner.plan.version).toBe(2)

      const published = await postJson<Plan>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/project-plans/${winner.plan.id}:mutate`,
        owner,
        mutateBody(winner, 'publish', `${suffix}-publish`, winner.plan.name),
        `${suffix}-publish`,
      )
      expect(published.plan.status).toBe('published')
      expect(published.changes.map((change) => change.operation))
        .toEqual(expect.arrayContaining(['create', 'update', 'publish']))
      const archived = await postJson<Plan>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/project-plans/${winner.plan.id}:mutate`,
        owner,
        mutateBody(published, 'archive', `${suffix}-archive`, winner.plan.name),
        `${suffix}-archive`,
      )
      expect(archived.plan.status).toBe('archived')
      const restored = await postJson<Plan>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/project-plans/${winner.plan.id}:mutate`,
        owner,
        mutateBody(archived, 'restore', `${suffix}-restore`, winner.plan.name),
        `${suffix}-restore`,
      )
      expect(restored.plan.status).toBe('draft')

      await installSession(page, owner)
      await page.goto(`/project-spaces/${spaceId}/work-items`)
      await page
        .getByTestId('project-work-items-secondary-tabs')
        .getByRole('tab', { name: '项目计划', exact: true })
        .click()
      await expect(page.getByTestId('project-plan-panel')).toBeVisible()
      await page.getByLabel('项目计划').click()
      await page.getByText(
        `${restored.plan.name} · ${restored.plan.status}`,
        { exact: true },
      ).click()
      await expect(page.getByTestId('project-plan-panel')).toContainText(restored.plan.name)
      await expect(page.getByLabel('项目计划完成度')).toBeVisible()
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        await expect(page.getByTestId('project-plan-panel')).toBeVisible()
        const overflow = await page.evaluate(() => ({
          delta: document.documentElement.scrollWidth - document.documentElement.clientWidth,
        }))
        expect(overflow.delta).toBeLessThanOrEqual(1)
      }
      await page.context().setOffline(true)
      const draftDescription = `离线计划说明 ${suffix}`
      await page.getByLabel('计划说明').fill(draftDescription)
      await expect(page.getByLabel('计划说明')).toHaveValue(draftDescription)
      await page.context().setOffline(false)
      await page.screenshot({
        path: testInfo.outputPath('s15-plan-820.png'),
        fullPage: true,
      })

      await installSession(page, guest)
      await page.goto(`/project-spaces/${spaceId}/work-items`)
      await page
        .getByTestId('project-work-items-secondary-tabs')
        .getByRole('tab', { name: '项目计划', exact: true })
        .click()
      await expect(page.getByTestId('project-plan-panel')).toContainText('当前身份只读')
      await expect(page.getByRole('button', { name: '新建计划' })).toBeDisabled()
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

function planCreateBody(requestId: string, name: string, ownerUserId: string) {
  const phaseA = crypto.randomUUID()
  const phaseB = crypto.randomUUID()
  return {
    schemaVersion: 1,
    requestId,
    name,
    description: 'S15 M1 真实隔离项目计划',
    startDate: '2026-07-01',
    endDate: '2026-09-30',
    phases: [
      {
        id: phaseA,
        phaseKey: 'discovery',
        name: '发现阶段',
        position: 0,
        startDate: '2026-07-01',
        endDate: '2026-07-31',
        status: 'completed',
      },
      {
        id: phaseB,
        phaseKey: 'delivery',
        name: '交付阶段',
        position: 1,
        startDate: '2026-08-01',
        endDate: '2026-09-30',
        status: 'active',
      },
    ],
    milestones: [
      {
        id: crypto.randomUUID(),
        phaseId: phaseA,
        milestoneKey: 'scope',
        name: '范围冻结',
        position: 0,
        targetDate: '2026-07-31',
        status: 'completed',
        ownerUserId,
      },
      {
        id: crypto.randomUUID(),
        phaseId: phaseB,
        milestoneKey: 'release',
        name: '正式交付',
        position: 1,
        targetDate: '2026-09-30',
        status: 'active',
        ownerUserId,
      },
    ],
    links: [],
  }
}

function mutateBody(
  current: Plan,
  operation: 'update' | 'publish' | 'archive' | 'restore',
  requestId: string,
  name: string,
) {
  return {
    schemaVersion: 1,
    requestId,
    expectedVersion: current.plan.version,
    operation,
    reason: `e2e_${operation}`,
    name,
    description: current.plan.description,
    startDate: current.plan.startDate,
    endDate: current.plan.endDate,
    phases: current.phases,
    milestones: current.milestones,
    links: [],
  }
}

function rawMutate(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  planId: string,
  data: Record<string, unknown>,
): Promise<APIResponse> {
  return request.post(
    `${apiBaseUrl}/project-spaces/${spaceId}/project-plans/${planId}:mutate`,
    {
      headers: {
        ...bearer(session),
        'X-Colla-Request-Id': String(data.requestId),
      },
      data,
    },
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
      spaceKey: `s15-m1-${kind}-${suffix.replaceAll('_', '-')}`,
      name: `S15 计划 ${kind} ${suffix}`,
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s15-m1-member-${userId}` },
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
