import { expect, test } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi } from './support/api'
import {
  addMember,
  createIdentity,
  createSpace,
  getJson,
  postJson,
} from './support/crossSpace'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Policy = {
  id: string
  name: string
  status: string
  version: number
  publishedVersion?: { versionNumber: number; definitionHash: string }
}

type Signal = {
  id: string
  signalType: string
  state: string
  version: number
  evidenceFingerprint: string
  evidence: Array<{ sourceType: string; sourceIdentity: string; explanation: string }>
}

type Foundation = {
  policies: Policy[]
  signals: Signal[]
  truncated: boolean
  budgets: Record<string, number>
}

test.describe('PROJECT-PLATFORM-S19 M3', () => {
  test('risk policy, evidence, closure and permission recalibration stay exact', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(480_000)
    requireIsolatedIdentityFixture()
    const enterprise = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(
      request, `${apiBaseUrl}/auth/me`, enterprise,
    )
    const suffix = `s19m3_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterprise, `${suffix}_owner`, 'S19 Risk Owner')
    const adminIdentity = await createIdentity(request, enterprise, `${suffix}_admin`, 'S19 Risk Admin')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S19 Risk Member')
    const guestIdentity = await createIdentity(request, enterprise, `${suffix}_guest`, 'S19 Risk Guest')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S19 Risk Outsider')
    const owner = await loginByApi(request, ownerIdentity.username, ownerIdentity.password)
    const admin = await loginByApi(request, adminIdentity.username, adminIdentity.password)
    const member = await loginByApi(request, memberIdentity.username, memberIdentity.password)
    const guest = await loginByApi(request, guestIdentity.username, guestIdentity.password)
    const outsider = await loginByApi(request, outsiderIdentity.username, outsiderIdentity.password)
    let spaceId: string | undefined
    let otherSpaceId: string | undefined
    try {
      spaceId = await createSpace(request, owner, suffix, 'risk-source')
      otherSpaceId = await createSpace(request, admin, suffix, 'risk-other')
      await addMember(request, owner, spaceId, adminIdentity.id, 'admin')
      await addMember(request, owner, spaceId, memberIdentity.id, 'member')
      await addMember(request, owner, spaceId, guestIdentity.id, 'guest')

      const phaseId = crypto.randomUUID()
      await postJson(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/project-plans`,
        owner,
        {
          schemaVersion: 1,
          requestId: `${suffix}-plan`,
          name: `延期计划 ${suffix}`,
          description: 'Risk evidence source',
          startDate: '2026-06-01',
          endDate: '2026-07-20',
          phases: [{
            id: phaseId,
            phaseKey: 'delivery',
            name: '交付',
            position: 0,
            startDate: '2026-06-01',
            endDate: '2026-07-20',
            status: 'active',
          }],
          milestones: [{
            id: crypto.randomUUID(),
            phaseId,
            milestoneKey: 'release',
            name: '延期发布',
            position: 0,
            targetDate: '2026-07-01',
            status: 'active',
            ownerUserId: ownerIdentity.id,
          }],
          links: [],
        },
        `${suffix}-plan`,
      )

      const saveRequestId = `${suffix}-policy-save`
      const policyBody = {
        schemaVersion: 1,
        requestId: saveRequestId,
        expectedVersion: 0,
        policyKey: `delivery.risk.${suffix.replaceAll('_', '-')}`,
        name: `交付风险策略 ${suffix}`,
        description: '只消费当前受权的公共事实，不评价个人绩效。',
        signalTypes: ['overdue', 'blocked', 'quality', 'resource'],
        severity: 'warning',
        cooldownHours: 24,
      }
      let policy = await postJson<Policy>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/metric-risks/policies`,
        owner,
        policyBody,
        saveRequestId,
      )
      const replay = await postJson<Policy>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/metric-risks/policies`,
        owner,
        policyBody,
        saveRequestId,
      )
      expect(replay).toEqual(policy)
      await postJson(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/metric-risks/policies/${policy.id}/publish`,
        owner,
        {
          schemaVersion: 1,
          requestId: `${suffix}-publish`,
          expectedVersion: policy.version,
          action: 'publish',
        },
        `${suffix}-publish`,
      )
      policy = (await getJson<Foundation>(
        request, `${apiBaseUrl}/project-spaces/${spaceId}/metric-risks`, owner,
      )).policies.find(value => value.id === policy.id)!
      expect(policy.publishedVersion?.versionNumber).toBe(1)

      const signals = await postJson<Signal[]>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/metric-risks/evaluate`,
        owner,
        {
          schemaVersion: 1,
          requestId: `${suffix}-evaluate`,
          anchor: new Date().toISOString(),
        },
        `${suffix}-evaluate`,
      )
      const overdue = signals.find(value => value.signalType === 'overdue')
      expect(overdue).toBeDefined()
      expect(overdue!.evidence).toEqual(expect.arrayContaining([
        expect.objectContaining({ sourceType: 'ProjectPlanService' }),
      ]))
      expect(overdue!.evidenceFingerprint).toMatch(/^[0-9a-f]{64}$/)

      for (const session of [owner, admin, member, guest]) {
        const visible = await getJson<Foundation>(
          request, `${apiBaseUrl}/project-spaces/${spaceId}/metric-risks`, session,
        )
        expect(visible.policies.map(value => value.id)).toContain(policy.id)
        expect(visible.budgets.chainDepth).toBe(8)
      }
      for (const session of [outsider, enterprise]) {
        const hidden = await request.get(
          `${apiBaseUrl}/project-spaces/${spaceId}/metric-risks`,
          { headers: bearer(session) },
        )
        expect([403, 404]).toContain(hidden.status())
        expect(await hidden.text()).not.toContain(policy.name)
      }
      const guestWrite = await request.post(
        `${apiBaseUrl}/project-spaces/${spaceId}/metric-risks/evaluate`,
        {
          headers: bearer(guest),
          data: {
            schemaVersion: 1,
            requestId: `${suffix}-guest-evaluate`,
            anchor: new Date().toISOString(),
          },
        },
      )
      expect(guestWrite.status()).toBe(403)

      const ackRequestId = `${suffix}-ack`
      const acknowledged = await postJson<Signal>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/metric-risks/signals/${overdue!.id}/actions`,
        admin,
        {
          schemaVersion: 1,
          requestId: ackRequestId,
          expectedVersion: overdue!.version,
          action: 'acknowledge',
          reason: '已确认当前延期证据',
        },
        ackRequestId,
      )
      expect(acknowledged.state).toBe('acknowledged')
      const closeRequestId = `${suffix}-close`
      const closed = await postJson<Signal>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/metric-risks/signals/${overdue!.id}/actions`,
        owner,
        {
          schemaVersion: 1,
          requestId: closeRequestId,
          expectedVersion: acknowledged.version,
          action: 'close',
          reason: '已建立受控缓解计划',
        },
        closeRequestId,
      )
      expect(closed.state).toBe('closed')
      const staleClose = await request.post(
        `${apiBaseUrl}/project-spaces/${spaceId}/metric-risks/signals/${overdue!.id}/actions`,
        {
          headers: bearer(owner),
          data: {
            schemaVersion: 1,
            requestId: `${suffix}-stale-close`,
            expectedVersion: acknowledged.version,
            action: 'close',
            reason: 'stale',
          },
        },
      )
      expect(staleClose.status()).toBe(409)

      const crossSpace = await request.get(
        `${apiBaseUrl}/project-spaces/${otherSpaceId}/metric-risks`,
        { headers: bearer(owner) },
      )
      expect([403, 404]).toContain(crossSpace.status())
      expect(await crossSpace.text()).not.toContain(policy.name)

      await installSession(page, owner)
      await page.goto(`/project-spaces/${spaceId}`)
      await page
        .getByTestId('project-space-overview-secondary-tabs')
        .getByRole('tab', { name: '指标风险', exact: true })
        .click()
      const panel = page.getByTestId('metric-risks-panel')
      await expect(panel).toBeVisible()
      await expect(panel).toContainText('延期、阻塞、质量与资源风险')
      await expect(panel).toContainText(policy.name)
      await expect(panel).toContainText('overdue')
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 950 })
        await expect(panel).toBeVisible()
        expect(await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        )).toBeLessThanOrEqual(1)
      }
      await page.context().setOffline(true)
      await page.evaluate(() => window.dispatchEvent(new Event('offline')))
      await expect(panel).toContainText('离线不伪造风险关闭')
      await expect(panel.getByTestId('risk-evaluate-action')).toBeDisabled()
      await page.screenshot({
        path: testInfo.outputPath('s19-risk-workbench-820.png'),
        fullPage: true,
      })
    } finally {
      await page.context().setOffline(false).catch(() => undefined)
      for (const entry of [
        { id: spaceId, session: owner },
        { id: otherSpaceId, session: admin },
      ]) {
        if (entry.id) {
          await request.post(`${apiBaseUrl}/project-spaces/${entry.id}/settings/archive`, {
            headers: bearer(entry.session),
          }).catch(() => undefined)
        }
      }
      for (const identity of [
        outsiderIdentity, guestIdentity, memberIdentity, adminIdentity, ownerIdentity,
      ]) {
        await request.post(`${apiBaseUrl}/admin/users/${identity.id}/offboard`, {
          headers: bearer(enterprise),
          data: { handoverToUserId: enterpriseProfile.id },
        }).catch(() => undefined)
      }
    }
  })
})
