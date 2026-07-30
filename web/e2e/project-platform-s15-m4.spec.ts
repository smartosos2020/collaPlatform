import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = { id: string; username: string; displayName: string; password: string }
type Plan = {
  plan: { id: string; version: number }
  milestones: Array<{ id: string }>
}
type Register = { entry: { id: string; title: string } }
type Delivery = { deliverable: { id: string; title: string } }
type Preference = {
  schemaVersion: number
  visibleSections: string[]
  compact: boolean
  version: number
}
type Detail = {
  plans: Array<{ id: string }>
  registerEntries: Array<{ id: string; title: string }>
  deliverables: Array<{ id: string; title: string }>
  deviations: Array<{
    completionPercent: number
    overdueMilestones: number
  }>
  blocking: {
    openIssues: number
    highRisks: number
    pendingChanges: number
    pendingAcceptances: number
    rejectedDeliverables: number
  }
  health: {
    status: string
    signals: Array<{
      code: string
      severity: string
      rule: string
      explanation: string
      sourceVersion: number
    }>
    truncated: boolean
    policyVersion: string
  }
  preference: Preference
}

test.describe('PROJECT-PLATFORM-S15 M4', () => {
  test('project detail derives explainable health with isolated authorization and preferences @route-final', async ({
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
    const suffix = `s15m4_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterprise, `${suffix}_owner`, 'S15 Detail Owner')
    const adminIdentity = await createIdentity(request, enterprise, `${suffix}_admin`, 'S15 Detail Admin')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S15 Detail Member')
    const guestIdentity = await createIdentity(request, enterprise, `${suffix}_guest`, 'S15 Detail Guest')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S15 Detail Outsider')
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

      const planInput = planBody(`${suffix}-plan`)
      const plan = await postJson<Plan>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/project-plans`,
        owner,
        planInput,
        planInput.requestId,
      )
      const riskInput = registerBody(
        `${suffix}-risk`, 'risk', `交付窗口高风险 ${'长名称'.repeat(18)}`,
        ownerIdentity.id, 5, 4,
      )
      const risk = await postJson<Register>(
        request, registerUrl(spaceId), owner, riskInput, riskInput.requestId,
      )
      const issueInput = registerBody(
        `${suffix}-issue`, 'issue', '生产阻断问题',
        ownerIdentity.id, null, null,
      )
      const issue = await postJson<Register>(
        request, registerUrl(spaceId), owner, issueInput, issueInput.requestId,
      )
      const deliveryInput = {
        schemaVersion: 1,
        requestId: `${suffix}-delivery`,
        title: '待验收发布包',
        summary: '用于项目健康详情聚合',
        ownerUserId: ownerIdentity.id,
        dueDate: '2026-08-31',
        planId: plan.plan.id,
        milestoneId: plan.milestones[0].id,
        registerEntryIds: [risk.entry.id, issue.entry.id],
      }
      const delivery = await postJson<Delivery>(
        request, `${apiBaseUrl}/project-spaces/${spaceId}/deliverables`,
        owner, deliveryInput, deliveryInput.requestId,
      )

      for (const session of [owner, spaceAdmin, member, guest]) {
        const detail = await getJson<Detail>(request, detailUrl(spaceId), session)
        expect(detail.health.status).toBe('critical')
        expect(detail.health.truncated).toBe(false)
        expect(detail.health.policyVersion).toBe('project-health-v1')
        expect(detail.blocking.openIssues).toBe(1)
        expect(detail.blocking.highRisks).toBe(1)
        expect(detail.blocking.pendingAcceptances).toBe(1)
        expect(detail.deviations[0].overdueMilestones).toBe(1)
        expect(detail.health.signals.map((value) => value.code))
          .toEqual(expect.arrayContaining([
            'schedule_overdue', 'risk_high', 'issue_open', 'acceptance_pending',
          ]))
        for (const signal of detail.health.signals) {
          expect(signal.rule.length).toBeGreaterThan(5)
          expect(signal.explanation.length).toBeGreaterThan(5)
          expect(signal.sourceVersion).toBeGreaterThan(0)
        }
      }
      for (const session of [outsider, enterprise]) {
        const hidden = await request.get(detailUrl(spaceId), {
          headers: bearer(session),
        })
        expect([403, 404]).toContain(hidden.status())
        const body = await hidden.text()
        expect(body).not.toContain(risk.entry.title)
        expect(body).not.toContain(delivery.deliverable.title)
      }
      const cross = await getJson<Detail>(request, detailUrl(otherSpaceId), owner)
      expect(cross.plans).toHaveLength(0)
      expect(cross.registerEntries).toHaveLength(0)
      expect(cross.deliverables).toHaveLength(0)
      expect(cross.health.status).toBe('healthy')
      expect(JSON.stringify(cross)).not.toContain(risk.entry.title)

      const preferenceInput = {
        schemaVersion: 1,
        requestId: `${suffix}-preference`,
        expectedVersion: 0,
        visibleSections: ['health', 'plan'],
        compact: true,
      }
      const preference = await postJson<Preference>(
        request, `${detailUrl(spaceId)}/preference`, owner,
        preferenceInput, preferenceInput.requestId,
      )
      const replay = await postJson<Preference>(
        request, `${detailUrl(spaceId)}/preference`, owner,
        preferenceInput, preferenceInput.requestId,
      )
      expect(replay).toEqual(preference)
      expect(preference.version).toBe(1)
      const concurrent = await Promise.all([
        rawPreference(request, member, spaceId, `${suffix}-member-a`, false),
        rawPreference(request, member, spaceId, `${suffix}-member-b`, true),
      ])
      expect(concurrent.map((response) => response.status()).sort()).toEqual([200, 409])
      const guestPreference = await rawPreference(
        request, guest, spaceId, `${suffix}-guest`, true,
      )
      expect(guestPreference.status()).toBe(200)

      await installSession(page, owner)
      await page.goto(`/project-spaces/${spaceId}/work-items`)
      await page
        .getByTestId('project-work-items-secondary-tabs')
        .getByRole('tab', { name: '项目概况', exact: true })
        .click()
      const panel = page.getByTestId('project-detail-panel')
      await expect(panel).toBeVisible()
      await expect(page.getByTestId('project-health-status')).toContainText('严重')
      await expect(page.getByTestId('project-health-signals')).toContainText('risk_high')
      await expect(panel).toContainText('Open risk score 20')
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        await expect(panel).toBeVisible()
        expect(await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        )).toBeLessThanOrEqual(1)
      }
      await page.context().setOffline(true)
      const offlineNote = `离线健康分析 ${suffix}`
      await page.getByTestId('project-detail-offline-note').fill(offlineNote)
      await expect(page.getByTestId('project-detail-offline-note')).toHaveValue(offlineNote)
      await expect(panel).toContainText('离线 · 本地输入保留')
      await page.context().setOffline(false)
      await expect(page.getByTestId('project-detail-offline-note')).toHaveValue(offlineNote)
      await page.screenshot({
        path: testInfo.outputPath('s15-project-detail-820.png'),
        fullPage: true,
      })

      await installSession(page, guest)
      await page.goto(`/project-spaces/${spaceId}/work-items`)
      await page
        .getByTestId('project-work-items-secondary-tabs')
        .getByRole('tab', { name: '项目概况', exact: true })
        .click()
      await expect(page.getByTestId('project-detail-panel')).toBeVisible()
      await expect(page.getByTestId('project-health-status')).toContainText('严重')
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

function detailUrl(spaceId: string) {
  return `${apiBaseUrl}/project-spaces/${spaceId}/project-detail`
}

function registerUrl(spaceId: string) {
  return `${apiBaseUrl}/project-spaces/${spaceId}/project-register`
}

function planBody(requestId: string) {
  const phaseId = crypto.randomUUID()
  return {
    schemaVersion: 1,
    requestId,
    name: '项目健康跟踪计划',
    description: 'S15 M4 detail source',
    startDate: '2026-07-01',
    endDate: '2026-08-31',
    phases: [{
      id: phaseId,
      phaseKey: 'delivery',
      name: '交付阶段',
      position: 0,
      startDate: '2026-07-01',
      endDate: '2026-08-31',
      status: 'active',
    }],
    milestones: [{
      id: crypto.randomUUID(),
      phaseId,
      milestoneKey: 'overdue',
      name: '已逾期里程碑',
      position: 0,
      targetDate: '2026-07-15',
      status: 'active',
      ownerUserId: null,
    }],
    links: [],
  }
}

function registerBody(
  requestId: string,
  entryType: 'risk' | 'issue',
  title: string,
  ownerUserId: string,
  probability: number | null,
  impact: number | null,
) {
  return {
    schemaVersion: 1,
    requestId,
    entryType,
    title,
    summary: '项目详情真实隔离健康来源',
    ownerUserId,
    dueDate: '2026-08-15',
    probability,
    impact,
    decisionBasis: '',
    changeImpact: '',
    references: [],
    responses: [],
  }
}

function rawPreference(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  requestId: string,
  compact: boolean,
) {
  return request.post(`${detailUrl(spaceId)}/preference`, {
    headers: { ...bearer(session), 'X-Colla-Request-Id': requestId },
    data: {
      schemaVersion: 1,
      requestId,
      expectedVersion: 0,
      visibleSections: ['plan', 'register', 'delivery', 'health'],
      compact,
    },
  })
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
      spaceKey: `s15-m4-${kind}-${suffix.replaceAll('_', '-')}`,
      name: `S15 详情 ${kind} ${suffix}`,
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s15-m4-member-${userId}` },
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
