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

type Report = { id: string; name: string; version: number }
type Run = { id: string; status: string; sourceFingerprint: string }
type Foundation = {
  overview: {
    truncated: boolean
    diagnostic: string
    health: Array<{ component: string; status: string; sourceVersion: string }>
  }
  reports: Report[]
  runs: Run[]
  budgets: Record<string, number>
}

test.describe('PROJECT-PLATFORM-S19 M4', () => {
  test('governance report and export remain current, redacted and permission scoped @route-final', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(480_000)
    requireIsolatedIdentityFixture()
    const enterprise = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(
      request, `${apiBaseUrl}/auth/me`, enterprise,
    )
    const suffix = `s19m4_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterprise, `${suffix}_owner`, 'S19 Governance Owner')
    const adminIdentity = await createIdentity(request, enterprise, `${suffix}_admin`, 'S19 Governance Admin')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S19 Governance Member')
    const guestIdentity = await createIdentity(request, enterprise, `${suffix}_guest`, 'S19 Governance Guest')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S19 Governance Outsider')
    const owner = await loginByApi(request, ownerIdentity.username, ownerIdentity.password)
    const admin = await loginByApi(request, adminIdentity.username, adminIdentity.password)
    const member = await loginByApi(request, memberIdentity.username, memberIdentity.password)
    const guest = await loginByApi(request, guestIdentity.username, guestIdentity.password)
    const outsider = await loginByApi(request, outsiderIdentity.username, outsiderIdentity.password)
    let spaceId: string | undefined
    try {
      spaceId = await createSpace(request, owner, suffix, 'governance')
      await addMember(request, owner, spaceId, adminIdentity.id, 'admin')
      await addMember(request, owner, spaceId, memberIdentity.id, 'member')
      await addMember(request, owner, spaceId, guestIdentity.id, 'guest')
      const report = await postJson<Report>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/metric-governance/reports`,
        owner,
        {
          schemaVersion: 1,
          requestId: `${suffix}-save`,
          expectedVersion: 0,
          reportKey: `space.governance.${suffix.replaceAll('_', '-')}`,
          name: `治理审计报表 ${suffix}`,
          description: '只包含当前受权治理元数据。',
          sections: ['metrics', 'dashboards', 'risks', 'configuration', 'audit'],
        },
        `${suffix}-save`,
      )
      const run = await postJson<Run>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/metric-governance/reports/${report.id}/runs`,
        admin,
        {
          schemaVersion: 1,
          requestId: `${suffix}-run`,
          expectedVersion: report.version,
        },
        `${suffix}-run`,
      )
      expect(run.status).toBe('completed')
      expect(run.sourceFingerprint).toMatch(/^[0-9a-f]{64}$/)
      const exported = await postJson<{
        rowCount: number
        contentHash: string
        rows: Array<Record<string, string>>
      }>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/metric-governance/runs/${run.id}/exports`,
        owner,
        { schemaVersion: 1, requestId: `${suffix}-export`, format: 'csv' },
        `${suffix}-export`,
      )
      expect(exported.rowCount).toBe(3)
      expect(exported.contentHash).toMatch(/^[0-9a-f]{64}$/)
      expect(JSON.stringify(exported.rows)).not.toContain(ownerIdentity.displayName)

      for (const session of [owner, admin, member, guest]) {
        const visible = await getJson<Foundation>(
          request, `${apiBaseUrl}/project-spaces/${spaceId}/metric-governance`, session,
        )
        expect(visible.reports.map(value => value.id)).toContain(report.id)
        expect(visible.budgets.exportRows).toBe(500)
      }
      for (const session of [outsider, enterprise]) {
        const hidden = await request.get(
          `${apiBaseUrl}/project-spaces/${spaceId}/metric-governance`,
          { headers: bearer(session) },
        )
        expect([403, 404]).toContain(hidden.status())
        expect(await hidden.text()).not.toContain(report.name)
      }
      const guestExport = await request.post(
        `${apiBaseUrl}/project-spaces/${spaceId}/metric-governance/runs/${run.id}/exports`,
        {
          headers: bearer(guest),
          data: { schemaVersion: 1, requestId: `${suffix}-guest-export`, format: 'csv' },
        },
      )
      expect(guestExport.status()).toBe(403)

      await installSession(page, owner)
      await page.goto(`/project-spaces/${spaceId}`)
      const panel = page.getByTestId('metric-governance-panel')
      await expect(panel).toBeVisible()
      await expect(panel).toContainText('管理驾驶舱、配置健康与审计报表')
      await expect(panel).toContainText(report.name)
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 950 })
        await expect(panel).toBeVisible()
        expect(await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        )).toBeLessThanOrEqual(1)
      }
      await page.context().setOffline(true)
      await page.evaluate(() => window.dispatchEvent(new Event('offline')))
      await expect(panel).toContainText('离线只读')
      await expect(panel.getByRole('button', { name: '运行当前受权报表' })).toBeDisabled()
      await page.screenshot({
        path: testInfo.outputPath('s19-governance-820.png'),
        fullPage: true,
      })
    } finally {
      await page.context().setOffline(false).catch(() => undefined)
      if (spaceId) {
        await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`, {
          headers: bearer(owner),
        }).catch(() => undefined)
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
