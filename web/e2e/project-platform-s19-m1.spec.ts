import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { addMember, createIdentity, createSpace, getJson, postJson } from './support/crossSpace'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Metric = {
  id: string
  metricKey: string
  name: string
  status: string
  version: number
  publishedVersion?: { versionNumber: number; definitionHash: string }
}

type Foundation = {
  measures: Array<{ key: string; sourceContract: string }>
  dimensions: Array<{ key: string; version: number; sourceContract: string }>
  metrics: Metric[]
  resultStatuses: string[]
  prohibitedCapabilities: string[]
}

function metricBody(requestId: string, key: string, name: string) {
  return {
    schemaVersion: 1,
    requestId,
    expectedVersion: 0,
    metricKey: key,
    name,
    description: '只计算当前受权、未截断的工作项样本。',
    unit: 'count',
    expression: {
      schemaVersion: 1,
      aggregation: 'count',
      measureKey: 'work_item.count',
      dimensionKeys: ['status'],
    },
    window: {
      schemaVersion: 1,
      kind: 'fixed',
      amount: 1,
      unit: 'day',
      timeZone: 'America/New_York',
      calendarKey: 'iso8601',
      comparison: 'previous_period',
    },
  }
}

async function createMetric(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  requestId: string,
  key: string,
  name: string,
) {
  return postJson<Metric>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/metrics`,
    session,
    metricBody(requestId, key, name),
    requestId,
  )
}

test.describe('PROJECT-PLATFORM-S19 M1', () => {
  test('versioned metric semantics preserve permission, DST, replay and incomplete states', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(480_000)
    requireIsolatedIdentityFixture()
    const enterprise = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(
      request, `${apiBaseUrl}/auth/me`, enterprise,
    )
    const suffix = `s19m1_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterprise, `${suffix}_owner`, 'S19 Metric Owner')
    const adminIdentity = await createIdentity(request, enterprise, `${suffix}_admin`, 'S19 Metric Admin')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S19 Metric Member')
    const guestIdentity = await createIdentity(request, enterprise, `${suffix}_guest`, 'S19 Metric Guest')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S19 Metric Outsider')
    const owner = await loginByApi(request, ownerIdentity.username, ownerIdentity.password)
    const admin = await loginByApi(request, adminIdentity.username, adminIdentity.password)
    const member = await loginByApi(request, memberIdentity.username, memberIdentity.password)
    const guest = await loginByApi(request, guestIdentity.username, guestIdentity.password)
    const outsider = await loginByApi(request, outsiderIdentity.username, outsiderIdentity.password)
    let sourceSpaceId: string | undefined
    let targetSpaceId: string | undefined
    try {
      sourceSpaceId = await createSpace(request, owner, suffix, 'metric-source')
      targetSpaceId = await createSpace(request, admin, suffix, 'metric-target')
      await addMember(request, owner, sourceSpaceId, adminIdentity.id, 'admin')
      await addMember(request, owner, sourceSpaceId, memberIdentity.id, 'member')
      await addMember(request, owner, sourceSpaceId, guestIdentity.id, 'guest')

      const requestId = `${suffix}-save`
      let metric = await createMetric(
        request, owner, sourceSpaceId, requestId,
        `authorized.${suffix.replaceAll('_', '-')}`, `受权指标 ${suffix}`,
      )
      const replay = await postJson<Metric>(
        request,
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metrics`,
        owner,
        metricBody(requestId, metric.metricKey, metric.name),
        requestId,
      )
      expect(replay).toEqual(metric)

      const conflictingReplay = await request.post(
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metrics`,
        {
          headers: { ...bearer(owner), 'X-Colla-Request-Id': requestId },
          data: { ...metricBody(requestId, metric.metricKey, '不同输入'), description: '冲突' },
        },
      )
      expect(conflictingReplay.status()).toBe(409)

      metric = await postJson<Metric>(
        request,
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metrics/${metric.id}/publish`,
        owner,
        { schemaVersion: 1, requestId: `${suffix}-publish`, expectedVersion: metric.version, action: 'publish' },
        `${suffix}-publish`,
      ).then(async () => {
        const foundation = await getJson<Foundation>(
          request, `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metrics`, owner,
        )
        return foundation.metrics.find(value => value.id === metric.id)!
      })
      expect(metric.publishedVersion?.versionNumber).toBe(1)

      const update = (name: string, updateRequestId: string) => request.post(
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metrics`,
        {
          headers: { ...bearer(owner), 'X-Colla-Request-Id': updateRequestId },
          data: {
            ...metricBody(updateRequestId, metric.metricKey, name),
            metricId: metric.id,
            expectedVersion: metric.version,
          },
        },
      )
      const concurrent = await Promise.all([
        update(`并发语义 A ${suffix}`, `${suffix}-concurrent-a`),
        update(`并发语义 B ${suffix}`, `${suffix}-concurrent-b`),
      ])
      expect(concurrent.map(value => value.status()).sort()).toEqual([200, 409])
      metric = (await getJson<Foundation>(
        request, `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metrics`, owner,
      )).metrics.find(value => value.id === metric.id)!
      expect(metric.version).toBe(3)

      const samples = [
        '2026-03-08T06:00:00Z',
        '2026-03-08T12:00:00Z',
        '2026-03-09T03:00:00Z',
        '2026-03-07T23:00:00Z',
      ].map((occurredAt, index) => ({
        occurredAt,
        value: index + 1,
        dimensions: { status: 'active' },
        sourceIdentity: `item-${index}`,
        sourceVersion: 1,
        authorized: true,
        suppressed: false,
        stale: false,
        truncated: false,
      }))
      const preview = await postJson<{
        status: string
        value?: number
        sampleCount: number
        window: { startInclusive: string; endExclusive: string; timeZone: string }
      }>(
        request,
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metrics/${metric.id}/preview`,
        owner,
        { schemaVersion: 1, anchor: '2026-03-08T16:00:00Z', samples },
        `${suffix}-preview`,
      )
      expect(preview.status).toBe('ready')
      expect(preview.value).toBe(3)
      expect(preview.sampleCount).toBe(3)
      expect(
        Date.parse(preview.window.endExclusive) - Date.parse(preview.window.startInclusive),
      ).toBe(23 * 60 * 60 * 1000)
      expect(preview.window.timeZone).toBe('America/New_York')

      await createMetric(
        request, admin, targetSpaceId, `${suffix}-target-save`,
        `target.secret.${suffix.replaceAll('_', '-')}`, `目标空间私有指标 ${suffix}`,
      )
      for (const session of [owner, admin, member, guest]) {
        const foundation = await getJson<Foundation>(
          request, `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metrics`, session,
        )
        expect(foundation.metrics.map(value => value.id)).toContain(metric.id)
        expect(JSON.stringify(foundation)).not.toContain('目标空间私有指标')
        expect(foundation.resultStatuses).toEqual(expect.arrayContaining([
          'unknown', 'no_sample', 'suppressed', 'stale', 'truncated',
        ]))
        expect(foundation.prohibitedCapabilities).toContain('personal_ranking')
      }
      for (const session of [member, guest]) {
        const forbidden = await request.post(
          `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metrics`,
          {
            headers: bearer(session),
            data: metricBody(`${suffix}-forbidden-${session.accessToken.slice(-4)}`, `forbidden.${suffix}`, '越权指标'),
          },
        )
        expect(forbidden.status()).toBe(403)
        expect(await forbidden.text()).not.toContain('目标空间私有指标')
      }
      for (const session of [outsider, enterprise]) {
        const hidden = await request.get(
          `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metrics`,
          { headers: bearer(session) },
        )
        expect([403, 404]).toContain(hidden.status())
        expect(await hidden.text()).not.toContain(metric.name)
      }

      await installSession(page, owner)
      await page.goto(`/project-spaces/${sourceSpaceId}`)
      const panel = page.getByTestId('metric-semantics-panel')
      await expect(panel).toBeVisible()
      await expect(panel).toContainText('指标语义、维度与时间窗口')
      await panel.getByRole('button', { name: new RegExp(metric.name) }).click()
      await expect(panel).toContainText('版本 diff 与来源解释')
      await expect(panel).toContainText('WorkItemQueryService.execute')
      const realtimeRequestId = `${suffix}-realtime`
      const realtimeUpdate = await request.post(
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metrics`,
        {
          headers: { ...bearer(owner), 'X-Colla-Request-Id': realtimeRequestId },
          data: {
            ...metricBody(realtimeRequestId, metric.metricKey, `实时校准指标 ${suffix}`),
            metricId: metric.id,
            expectedVersion: metric.version,
          },
        },
      )
      expect(realtimeUpdate.status(), await realtimeUpdate.text()).toBe(200)
      metric = await realtimeUpdate.json() as Metric
      await page.evaluate(() => window.dispatchEvent(new Event('focus')))
      await expect(
        panel.getByRole('button', { name: new RegExp(`实时校准指标 ${suffix}`) }),
      ).toBeVisible({ timeout: 30_000 })

      const secondPage = await page.context().newPage()
      await secondPage.goto(`/project-spaces/${sourceSpaceId}`)
      const secondPanel = secondPage.getByTestId('metric-semantics-panel')
      await expect(secondPanel).toBeVisible()
      await secondPanel.getByLabel('指标名称').fill(`多标签草稿 ${suffix}`)
      await expect(panel.getByLabel('指标名称')).toHaveValue(`多标签草稿 ${suffix}`)
      await secondPage.close()
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 950 })
        await expect(panel).toBeVisible()
        expect(await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        )).toBeLessThanOrEqual(1)
      }
      await page.context().setOffline(true)
      await page.evaluate(() => window.dispatchEvent(new Event('offline')))
      await expect(panel).toContainText('当前离线')
      await expect(panel.getByRole('button', { name: '发布不可变版本' })).toBeDisabled()
      await page.screenshot({
        path: testInfo.outputPath('s19-metric-semantics-820.png'),
        fullPage: true,
      })
    } finally {
      await page.context().setOffline(false).catch(() => undefined)
      for (const entry of [
        { id: sourceSpaceId, session: owner },
        { id: targetSpaceId, session: admin },
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
