import { expect, test, type APIRequestContext, type APIResponse } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = { id: string; username: string; displayName: string; password: string }
type Entry = {
  entry: {
    id: string
    entryType: 'risk' | 'issue' | 'decision' | 'change'
    title: string
    summary: string
    status: string
    ownerUserId: string | null
    dueDate: string | null
    probability: number | null
    impact: number | null
    score: number
    decisionBasis: string
    changeImpact: string
    supersedesEntryId: string | null
    verification: string
    version: number
  }
  references: Array<{ id: string; sourceType: string; sourceId: string }>
  responses: Array<{
    id: string
    responseType: string
    description: string
    ownerUserId: string | null
    dueDate: string | null
    status: string
  }>
  history: Array<{ operation: string; fromStatus: string; toStatus: string }>
  referencesTruncated: boolean
}
type Plan = {
  plan: {
    id: string
    name: string
    description: string
    startDate: string
    endDate: string
    status: string
    version: number
  }
}

test.describe('PROJECT-PLATFORM-S15 M2', () => {
  test('governance registers close exact authorized lifecycles in a real isolated flow @route-final', async ({
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
    const suffix = `s15m2_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterprise, `${suffix}_owner`, 'S15 Register Owner')
    const adminIdentity = await createIdentity(request, enterprise, `${suffix}_admin`, 'S15 Register Admin')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S15 Register Member')
    const guestIdentity = await createIdentity(request, enterprise, `${suffix}_guest`, 'S15 Register Guest')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S15 Register Outsider')
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

      const riskBody = createBody(
        `${suffix}-risk-create`,
        'risk',
        `交付窗口风险 ${'长名称'.repeat(20)}`,
        ownerIdentity.id,
      )
      const risk = await postJson<Entry>(
        request, registerUrl(spaceId), owner, riskBody, riskBody.requestId,
      )
      const replay = await postJson<Entry>(
        request, registerUrl(spaceId), owner, riskBody, riskBody.requestId,
      )
      expect(replay.entry.id).toBe(risk.entry.id)
      expect(replay.entry.version).toBe(1)
      expect(risk.entry.score).toBe(12)
      expect(risk.responses).toHaveLength(1)

      for (const session of [owner, spaceAdmin, member, guest]) {
        const rows = await getJson<Array<{ id: string; title: string }>>(
          request, registerUrl(spaceId), session,
        )
        expect(rows).toContainEqual(expect.objectContaining({
          id: risk.entry.id,
          title: risk.entry.title,
        }))
      }
      for (const session of [outsider, enterprise]) {
        const hidden = await request.get(registerUrl(spaceId), {
          headers: bearer(session),
        })
        expect([403, 404]).toContain(hidden.status())
        expect(await hidden.text()).not.toContain(risk.entry.title)
      }
      const crossSpace = await request.get(
        `${registerUrl(otherSpaceId)}/${risk.entry.id}`,
        { headers: bearer(owner) },
      )
      expect(crossSpace.status()).toBe(404)
      expect(await crossSpace.text()).not.toContain(risk.entry.title)

      const guestWrite = await request.post(registerUrl(spaceId), {
        headers: { ...bearer(guest), 'X-Colla-Request-Id': `${suffix}-guest` },
        data: { ...riskBody, requestId: `${suffix}-guest` },
      })
      expect(guestWrite.status()).toBe(403)

      const concurrent = await Promise.all([
        rawMutate(request, member, spaceId, risk, 'update', `${suffix}-risk-a`),
        rawMutate(request, member, spaceId, risk, 'update', `${suffix}-risk-b`),
      ])
      expect(concurrent.map((response) => response.status()).sort()).toEqual([200, 409])
      const winnerResponse = concurrent.find((response) => response.ok())
      if (!winnerResponse) throw new Error('register update winner missing')
      let riskCurrent = await winnerResponse.json() as Entry
      for (const operation of ['assess', 'monitor', 'close', 'reopen']) {
        riskCurrent = await mutate(
          request, owner, spaceId, riskCurrent, operation, `${suffix}-risk-${operation}`,
        )
      }
      expect(riskCurrent.entry.status).toBe('monitoring')
      expect(riskCurrent.history.map((row) => row.operation))
        .toEqual(expect.arrayContaining(['create', 'assess', 'monitor', 'close', 'reopen']))

      let issue = await postJson<Entry>(
        request,
        registerUrl(spaceId),
        owner,
        createBody(`${suffix}-issue`, 'issue', '生产阻断问题', ownerIdentity.id),
        `${suffix}-issue`,
      )
      for (const operation of ['escalate', 'resolve', 'verify', 'reopen']) {
        issue = await mutate(
          request, owner, spaceId, issue, operation, `${suffix}-issue-${operation}`,
        )
      }
      expect(issue.entry.status).toBe('open')
      expect(issue.entry.verification).toContain('已验证')

      let decision = await postJson<Entry>(
        request,
        registerUrl(spaceId),
        owner,
        createBody(`${suffix}-decision`, 'decision', '采用渐进式发布', ownerIdentity.id),
        `${suffix}-decision`,
      )
      decision = await mutate(
        request, owner, spaceId, decision, 'adopt', `${suffix}-decision-adopt`,
      )
      decision = await mutate(
        request, owner, spaceId, decision, 'revoke', `${suffix}-decision-revoke`,
      )
      expect(decision.entry.status).toBe('revoked')

      const plan = await postJson<Plan>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/project-plans`,
        owner,
        planBody(`${suffix}-plan`),
        `${suffix}-plan`,
      )
      let change = await postJson<Entry>(
        request,
        registerUrl(spaceId),
        owner,
        createBody(`${suffix}-change`, 'change', '发布计划变更', ownerIdentity.id),
        `${suffix}-change`,
      )
      change = await mutate(
        request, owner, spaceId, change, 'analyze', `${suffix}-change-analyze`,
      )
      change = await mutate(
        request,
        owner,
        spaceId,
        change,
        'approve',
        `${suffix}-change-approve`,
        {
          planId: plan.plan.id,
          expectedPlanVersion: plan.plan.version,
          operation: 'publish',
          requestId: `${suffix}-approved-plan`,
        },
      )
      expect(change.entry.status).toBe('approved')
      const published = await getJson<Plan>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/project-plans/${plan.plan.id}`,
        owner,
      )
      expect(published.plan.status).toBe('published')

      await installSession(page, owner)
      await page.goto(`/project-spaces/${spaceId}/work-items`)
      await page
        .getByTestId('project-work-items-secondary-tabs')
        .getByRole('tab', { name: '风险与决策', exact: true })
        .click()
      await expect(page.getByTestId('project-register-panel')).toBeVisible()
      await page.getByLabel('台账筛选').click()
      await page.getByText('风险', { exact: true }).last().click()
      await page.getByText(riskCurrent.entry.title, { exact: true }).click()
      await expect(page.getByTestId('project-register-panel')).toContainText('不可变历史')
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        await expect(page.getByTestId('project-register-panel')).toBeVisible()
        expect(await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        )).toBeLessThanOrEqual(1)
      }
      await page.context().setOffline(true)
      const offlineTitle = `离线风险草稿 ${suffix}`
      await page.getByLabel('台账标题').fill(offlineTitle)
      await expect(page.getByLabel('台账标题')).toHaveValue(offlineTitle)
      await page.context().setOffline(false)
      await page.screenshot({
        path: testInfo.outputPath('s15-register-820.png'),
        fullPage: true,
      })

      await installSession(page, guest)
      await page.goto(`/project-spaces/${spaceId}/work-items`)
      await page
        .getByTestId('project-work-items-secondary-tabs')
        .getByRole('tab', { name: '风险与决策', exact: true })
        .click()
      await expect(page.getByTestId('project-register-panel')).toContainText('当前角色只读')
      await expect(page.getByRole('button', { name: '创建条目' })).toBeDisabled()
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

function registerUrl(spaceId: string) {
  return `${apiBaseUrl}/project-spaces/${spaceId}/project-register`
}

function createBody(
  requestId: string,
  entryType: 'risk' | 'issue' | 'decision' | 'change',
  title: string,
  ownerUserId: string,
) {
  return {
    schemaVersion: 1,
    requestId,
    entryType,
    title,
    summary: `${title} 的依据和处置摘要`,
    ownerUserId,
    dueDate: '2026-09-30',
    probability: entryType === 'risk' ? 3 : null,
    impact: entryType === 'risk' ? 4 : null,
    decisionBasis: entryType === 'decision' ? '比较替代方案后的不可变依据摘要' : '',
    changeImpact: entryType === 'change' ? '影响计划发布状态并保留来源' : '',
    references: [],
    responses: entryType === 'risk' || entryType === 'issue' ? [{
      id: crypto.randomUUID(),
      responseType: entryType === 'risk' ? 'mitigate' : 'resolve',
      description: '责任人按期执行并验证',
      ownerUserId,
      dueDate: '2026-09-15',
      status: 'active',
    }] : [],
  }
}

function mutationBody(
  current: Entry,
  operation: string,
  requestId: string,
  planAction: unknown = null,
) {
  return {
    schemaVersion: 1,
    requestId,
    expectedVersion: current.entry.version,
    operation,
    reason: `e2e ${operation} reason`,
    title: current.entry.title,
    summary: current.entry.summary,
    ownerUserId: current.entry.ownerUserId,
    dueDate: current.entry.dueDate,
    probability: current.entry.probability,
    impact: current.entry.impact,
    decisionBasis: current.entry.decisionBasis,
    changeImpact: current.entry.changeImpact,
    supersedesEntryId: current.entry.supersedesEntryId,
    verification: operation === 'verify' ? '负责人已验证处置结果' : current.entry.verification,
    references: current.references.map(({ id, sourceType, sourceId }) => ({
      id, sourceType, sourceId,
    })),
    responses: current.responses,
    planAction,
  }
}

function rawMutate(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  current: Entry,
  operation: string,
  requestId: string,
): Promise<APIResponse> {
  return request.post(`${registerUrl(spaceId)}/${current.entry.id}:mutate`, {
    headers: { ...bearer(session), 'X-Colla-Request-Id': requestId },
    data: mutationBody(current, operation, requestId),
  })
}

async function mutate(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  current: Entry,
  operation: string,
  requestId: string,
  planAction: unknown = null,
) {
  return postJson<Entry>(
    request,
    `${registerUrl(spaceId)}/${current.entry.id}:mutate`,
    session,
    mutationBody(current, operation, requestId, planAction),
    requestId,
  )
}

function planBody(requestId: string) {
  const phaseId = crypto.randomUUID()
  return {
    schemaVersion: 1,
    requestId,
    name: '变更控制计划',
    description: '由批准变更调用规范计划命令',
    startDate: '2026-07-01',
    endDate: '2026-09-30',
    phases: [{
      id: phaseId,
      phaseKey: 'delivery',
      name: '交付阶段',
      position: 0,
      startDate: '2026-07-01',
      endDate: '2026-09-30',
      status: 'active',
    }],
    milestones: [{
      id: crypto.randomUUID(),
      phaseId,
      milestoneKey: 'release',
      name: '发布',
      position: 0,
      targetDate: '2026-09-30',
      status: 'active',
      ownerUserId: null,
    }],
    links: [],
  }
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
      spaceKey: `s15-m2-${kind}-${suffix.replaceAll('_', '-')}`,
      name: `S15 台账 ${kind} ${suffix}`,
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s15-m2-member-${userId}` },
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
