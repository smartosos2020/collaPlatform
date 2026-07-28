import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import {
  addMember,
  createGrant,
  createIdentity,
  createItem,
  createSpace,
  getJson,
  grantLifecycle,
  postJson,
  publishedProjectType,
} from './support/crossSpace'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Metric = {
  id: string
  metricKey: string
  name: string
  version: number
  publishedVersion?: { versionNumber: number }
}

type Dashboard = {
  id: string
  dashboardKey: string
  name: string
  status: string
  sharingScope: string
  version: number
  publishedVersion?: { versionNumber: number; definitionHash: string }
}

type DashboardFoundation = {
  dashboards: Dashboard[]
  visualizations: string[]
  sourceKinds: string[]
  budgets: Record<string, number>
}

type DashboardQuery = {
  status: string
  truncated: boolean
  charts: Array<{
    status: string
    visibleSampleCount: number
    series: unknown[]
    facets: string[]
    sourceVersions: string[]
  }>
}

function metricBody(requestId: string, suffix: string) {
  return {
    schemaVersion: 1,
    requestId,
    expectedVersion: 0,
    metricKey: `dashboard.count.${suffix.replaceAll('_', '-')}`,
    name: `看板计数指标 ${suffix}`,
    description: '只对当前受权来源做有界计数。',
    unit: 'count',
    expression: {
      schemaVersion: 1,
      aggregation: 'count',
      measureKey: 'work_item.count',
      dimensionKeys: ['status', 'space'],
    },
    window: {
      schemaVersion: 1,
      kind: 'rolling',
      amount: 30,
      unit: 'day',
      timeZone: 'Asia/Shanghai',
      calendarKey: 'iso8601',
      comparison: 'previous_period',
    },
  }
}

function dashboardBody(
  requestId: string,
  suffix: string,
  spaceId: string,
  metric: Metric,
  dashboard?: Dashboard,
  sourceKind: 'work_item_query' | 'cross_space_panorama' = 'work_item_query',
  targetSpaceId?: string,
) {
  const dashboardKey = sourceKind === 'work_item_query'
    ? `delivery.dashboard.${suffix.replaceAll('_', '-')}`
    : `cross.dashboard.${suffix.replaceAll('_', '-')}`
  const chartKey = sourceKind === 'work_item_query' ? 'status.chart' : 'grant.chart'
  return {
    schemaVersion: 1,
    requestId,
    dashboardId: dashboard?.id,
    expectedVersion: dashboard?.version ?? 0,
    dashboardKey,
    name: sourceKind === 'work_item_query'
      ? `交付管理看板 ${suffix}`
      : `跨空间管理看板 ${suffix}`,
    description: '配置可共享，数据和授权不可复制。',
    config: {
      schemaVersion: 1,
      dataSources: [{
        schemaVersion: 1,
        bindingKey: 'primary.source',
        kind: sourceKind,
        spaceIds: targetSpaceId ? [spaceId, targetSpaceId] : [spaceId],
        metricId: metric.id,
        metricVersion: metric.publishedVersion!.versionNumber,
      }],
      charts: [{
        chartKey,
        name: sourceKind === 'work_item_query' ? '状态分布' : '授权状态',
        visualization: sourceKind === 'work_item_query' ? 'bar' : 'distribution',
        bindingKey: 'primary.source',
        metricId: metric.id,
        metricVersion: metric.publishedVersion!.versionNumber,
        dimensionKeys: ['status'],
        filters: {},
        seriesLimit: 12,
        pointLimit: 50,
        drilldown: true,
        version: 0,
      }],
      layout: [{ chartKey, column: 0, row: 0, width: 12, height: 5 }],
      filters: [],
    },
  }
}

async function createMetric(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  suffix: string,
) {
  let metric = await postJson<Metric>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/metrics`,
    owner,
    metricBody(`${suffix}-metric-save`, suffix),
    `${suffix}-metric-save`,
  )
  await postJson(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/metrics/${metric.id}/publish`,
    owner,
    {
      schemaVersion: 1,
      requestId: `${suffix}-metric-publish`,
      expectedVersion: metric.version,
      action: 'publish',
    },
    `${suffix}-metric-publish`,
  )
  metric = (await getJson<{ metrics: Metric[] }>(
    request, `${apiBaseUrl}/project-spaces/${spaceId}/metrics`, owner,
  )).metrics.find(value => value.id === metric.id)!
  return metric
}

test.describe('PROJECT-PLATFORM-S19 M2', () => {
  test('dashboard remains permission-scoped across publish, share, grant revoke and recovery', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(480_000)
    requireIsolatedIdentityFixture()
    const enterprise = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(
      request, `${apiBaseUrl}/auth/me`, enterprise,
    )
    const suffix = `s19m2_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterprise, `${suffix}_owner`, 'S19 Dashboard Owner')
    const adminIdentity = await createIdentity(request, enterprise, `${suffix}_admin`, 'S19 Dashboard Admin')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S19 Dashboard Member')
    const guestIdentity = await createIdentity(request, enterprise, `${suffix}_guest`, 'S19 Dashboard Guest')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S19 Dashboard Outsider')
    const owner = await loginByApi(request, ownerIdentity.username, ownerIdentity.password)
    const admin = await loginByApi(request, adminIdentity.username, adminIdentity.password)
    const member = await loginByApi(request, memberIdentity.username, memberIdentity.password)
    const guest = await loginByApi(request, guestIdentity.username, guestIdentity.password)
    const outsider = await loginByApi(request, outsiderIdentity.username, outsiderIdentity.password)
    let sourceSpaceId: string | undefined
    let targetSpaceId: string | undefined
    try {
      sourceSpaceId = await createSpace(request, owner, suffix, 'dashboard-source')
      targetSpaceId = await createSpace(request, admin, suffix, 'dashboard-target')
      await addMember(request, owner, sourceSpaceId, adminIdentity.id, 'admin')
      await addMember(request, owner, sourceSpaceId, memberIdentity.id, 'member')
      await addMember(request, owner, sourceSpaceId, guestIdentity.id, 'guest')

      const sourceType = await publishedProjectType(request, owner, sourceSpaceId, `${suffix}-source`)
      const targetType = await publishedProjectType(request, admin, targetSpaceId, `${suffix}-target`)
      for (let index = 0; index < 4; index += 1) {
        await createItem(
          request, owner, sourceSpaceId, sourceType.typeId,
          `受权看板样本 ${index} ${suffix}`, `${suffix}-item-${index}`,
        )
      }
      const metric = await createMetric(request, owner, sourceSpaceId, suffix)

      const saveRequestId = `${suffix}-dashboard-save`
      let dashboard = await postJson<Dashboard>(
        request,
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metric-dashboards`,
        owner,
        dashboardBody(saveRequestId, suffix, sourceSpaceId, metric),
        saveRequestId,
      )
      const replay = await postJson<Dashboard>(
        request,
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metric-dashboards`,
        owner,
        dashboardBody(saveRequestId, suffix, sourceSpaceId, metric),
        saveRequestId,
      )
      expect(replay).toEqual(dashboard)
      const conflictingReplay = await request.post(
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metric-dashboards`,
        {
          headers: { ...bearer(owner), 'X-Colla-Request-Id': saveRequestId },
          data: {
            ...dashboardBody(saveRequestId, suffix, sourceSpaceId, metric),
            name: '不同输入',
          },
        },
      )
      expect(conflictingReplay.status()).toBe(409)

      await postJson(
        request,
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metric-dashboards/${dashboard.id}/publish`,
        owner,
        {
          schemaVersion: 1,
          requestId: `${suffix}-dashboard-publish`,
          expectedVersion: dashboard.version,
          action: 'publish',
        },
        `${suffix}-dashboard-publish`,
      )
      dashboard = (await getJson<DashboardFoundation>(
        request, `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metric-dashboards`, owner,
      )).dashboards.find(value => value.id === dashboard.id)!
      expect(dashboard.publishedVersion?.versionNumber).toBe(1)

      const query = await postJson<DashboardQuery>(
        request,
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metric-dashboards/${dashboard.id}/query`,
        owner,
        { schemaVersion: 1, anchor: new Date().toISOString(), filterValues: {} },
        `${suffix}-query`,
      )
      expect(query.status).toBe('ready')
      expect(query.charts[0].visibleSampleCount).toBe(4)
      expect(query.charts[0].series.length).toBeGreaterThan(0)

      for (const session of [owner, admin, member, guest]) {
        const foundation = await getJson<DashboardFoundation>(
          request, `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metric-dashboards`, session,
        )
        expect(foundation.dashboards.map(value => value.id)).toContain(dashboard.id)
        expect(foundation.visualizations).toEqual(expect.arrayContaining([
          'table', 'metric_card', 'line', 'bar', 'stacked_bar', 'distribution',
        ]))
        expect(foundation.sourceKinds).toContain('cross_space_panorama')
        expect(foundation.budgets.charts).toBe(24)
      }
      for (const session of [member, guest]) {
        const forbidden = await request.post(
          `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metric-dashboards`,
          {
            headers: bearer(session),
            data: dashboardBody(`${suffix}-forbidden`, suffix, sourceSpaceId, metric),
          },
        )
        expect(forbidden.status()).toBe(403)
        expect(await forbidden.text()).not.toContain('受权看板样本')
      }
      for (const session of [outsider, enterprise]) {
        const hidden = await request.get(
          `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metric-dashboards`,
          { headers: bearer(session) },
        )
        expect([403, 404]).toContain(hidden.status())
        expect(await hidden.text()).not.toContain(dashboard.name)
      }

      const update = (name: string, requestId: string) => request.post(
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metric-dashboards`,
        {
          headers: { ...bearer(owner), 'X-Colla-Request-Id': requestId },
          data: {
            ...dashboardBody(requestId, suffix, sourceSpaceId, metric, dashboard),
            name,
          },
        },
      )
      const concurrent = await Promise.all([
        update(`并发看板 A ${suffix}`, `${suffix}-concurrent-a`),
        update(`并发看板 B ${suffix}`, `${suffix}-concurrent-b`),
      ])
      expect(concurrent.map(value => value.status()).sort()).toEqual([200, 409])
      dashboard = (await getJson<DashboardFoundation>(
        request, `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metric-dashboards`, owner,
      )).dashboards.find(value => value.id === dashboard.id)!
      dashboard = await postJson<Dashboard>(
        request,
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metric-dashboards/${dashboard.id}/lifecycle`,
        owner,
        {
          schemaVersion: 1,
          requestId: `${suffix}-share`,
          expectedVersion: dashboard.version,
          action: 'share',
        },
        `${suffix}-share`,
      )
      expect(dashboard.sharingScope).toBe('space')
      const outsiderAfterShare = await request.get(
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metric-dashboards`,
        { headers: bearer(outsider) },
      )
      expect([403, 404]).toContain(outsiderAfterShare.status())

      let grant = await createGrant(
        request, owner, sourceSpaceId, targetSpaceId,
        sourceType, targetType, `${suffix}-grant`,
      )
      grant = await grantLifecycle(
        request, owner, sourceSpaceId, grant, 'request', undefined, `${suffix}-grant-request`,
      )
      grant = await grantLifecycle(
        request, owner, sourceSpaceId, grant, 'confirm', 'source', `${suffix}-grant-source`,
      )
      grant = await grantLifecycle(
        request, admin, targetSpaceId, grant, 'confirm', 'target', `${suffix}-grant-target`,
      )
      expect(grant.status).toBe('active')
      let crossDashboard = await postJson<Dashboard>(
        request,
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metric-dashboards`,
        owner,
        dashboardBody(
          `${suffix}-cross-save`, suffix, sourceSpaceId, metric,
          undefined, 'cross_space_panorama', targetSpaceId,
        ),
        `${suffix}-cross-save`,
      )
      await postJson(
        request,
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metric-dashboards/${crossDashboard.id}/publish`,
        owner,
        {
          schemaVersion: 1,
          requestId: `${suffix}-cross-publish`,
          expectedVersion: crossDashboard.version,
          action: 'publish',
        },
        `${suffix}-cross-publish`,
      )
      crossDashboard = (await getJson<DashboardFoundation>(
        request, `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metric-dashboards`, owner,
      )).dashboards.find(value => value.id === crossDashboard.id)!
      const activeCross = await postJson<DashboardQuery>(
        request,
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metric-dashboards/${crossDashboard.id}/query`,
        owner,
        { schemaVersion: 1, anchor: new Date().toISOString(), filterValues: {} },
        `${suffix}-cross-query`,
      )
      expect(activeCross.charts[0].status).toBe('suppressed')
      expect(activeCross.charts[0].series).toEqual([])
      expect(activeCross.charts[0].facets).toEqual([])
      expect(activeCross.charts[0].visibleSampleCount).toBe(0)
      expect(activeCross.charts[0].sourceVersions).toEqual([])

      grant = await grantLifecycle(
        request, owner, sourceSpaceId, grant, 'revoke', undefined, `${suffix}-grant-revoke`,
        'S19-M2 permission recalibration',
      )
      expect(grant.status).toBe('revoked')
      const revokedCross = await postJson<DashboardQuery>(
        request,
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/metric-dashboards/${crossDashboard.id}/query`,
        owner,
        { schemaVersion: 1, anchor: new Date().toISOString(), filterValues: {} },
        `${suffix}-revoked-query`,
      )
      expect(revokedCross.charts[0].status).toBe('no_sample')
      expect(revokedCross.charts[0].series).toEqual([])

      await installSession(page, owner)
      await page.goto(`/project-spaces/${sourceSpaceId}`)
      const panel = page.getByTestId('metric-dashboards-panel')
      await expect(panel).toBeVisible()
      await expect(panel).toContainText('图表、看板与跨空间数据源')
      await panel.getByRole('button', { name: new RegExp(dashboard.name) }).click()
      await panel.getByRole('button', { name: 'REST 重新计算' }).click()
      await expect(panel).toContainText('看板结果：ready')
      await expect(panel).toContainText('freshness：current')
      const secondPage = await page.context().newPage()
      await secondPage.goto(`/project-spaces/${sourceSpaceId}`)
      const secondPanel = secondPage.getByTestId('metric-dashboards-panel')
      await expect(secondPanel).toBeVisible()
      await secondPanel.getByLabel('看板名称').fill(`多标签看板草稿 ${suffix}`)
      await expect(panel.getByLabel('看板名称')).toHaveValue(`多标签看板草稿 ${suffix}`)
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
      await expect(panel.getByTestId('dashboard-share-action')).toBeDisabled()
      await page.screenshot({
        path: testInfo.outputPath('s19-metric-dashboard-820.png'),
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
