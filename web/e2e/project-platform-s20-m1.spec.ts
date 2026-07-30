import { expect, test } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi } from './support/api'
import { addMember, createIdentity, createSpace, getJson } from './support/crossSpace'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Foundation = {
  templates: Array<{
    scenarioKey: string
    name: string
    currentVersion: {
      manifestHash: string
      manifest: {
        components: Array<{
          componentKey: string
          kind: string
          ownerContract: string
          dependencies: string[]
        }>
        prohibitedCapabilities: string[]
      }
    }
  }>
  prohibitedCapabilities: string[]
}

test.describe('PROJECT-PLATFORM-S20 M1', () => {
  test('development scenario catalog is deterministic, permission scoped and responsive', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(480_000)
    requireIsolatedIdentityFixture()
    const enterprise = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(
      request, `${apiBaseUrl}/auth/me`, enterprise,
    )
    const suffix = `s20m1_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterprise, `${suffix}_owner`, 'S20 Dev Owner')
    const adminIdentity = await createIdentity(request, enterprise, `${suffix}_admin`, 'S20 Dev Admin')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S20 Dev Member')
    const guestIdentity = await createIdentity(request, enterprise, `${suffix}_guest`, 'S20 Dev Guest')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S20 Dev Outsider')
    const owner = await loginByApi(request, ownerIdentity.username, ownerIdentity.password)
    const admin = await loginByApi(request, adminIdentity.username, adminIdentity.password)
    const member = await loginByApi(request, memberIdentity.username, memberIdentity.password)
    const guest = await loginByApi(request, guestIdentity.username, guestIdentity.password)
    const outsider = await loginByApi(request, outsiderIdentity.username, outsiderIdentity.password)
    let spaceId: string | undefined
    try {
      spaceId = await createSpace(request, owner, suffix, 'scenario-development')
      await addMember(request, owner, spaceId, adminIdentity.id, 'admin')
      await addMember(request, owner, spaceId, memberIdentity.id, 'member')
      await addMember(request, owner, spaceId, guestIdentity.id, 'guest')

      for (const session of [owner, admin, member, guest]) {
        const foundation = await getJson<Foundation>(
          request, `${apiBaseUrl}/project-spaces/${spaceId}/scenario-templates`, session,
        )
        expect(foundation.templates.map(value => value.scenarioKey)).toEqual(['development'])
        const development = foundation.templates[0]
        expect(development.currentVersion.manifestHash).toMatch(/^[0-9a-f]{64}$/)
        expect(development.currentVersion.manifest.components).toHaveLength(13)
        expect(development.currentVersion.manifest.components.map(value => value.componentKey))
          .toEqual(expect.arrayContaining([
            'type.project',
            'type.requirement',
            'type.task',
            'type.bug',
            'type.version',
            'type.iteration',
            'relation.delivery_hierarchy',
            'view.delivery_board',
            'plan.roadmap',
            'automation.defect_triage',
            'metric.delivery_health',
          ]))
        expect(JSON.stringify(development)).not.toContain(ownerIdentity.displayName)
        expect(foundation.prohibitedCapabilities).toContain('private_table_access')
      }

      const validation = await getJson<{
        valid: boolean
        manifestHash: string
        installationOrder: string[]
        diagnostics: unknown[]
      }>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/scenario-templates/development/validation`,
        owner,
      )
      expect(validation.valid).toBe(true)
      expect(validation.diagnostics).toEqual([])
      expect(validation.installationOrder.indexOf('type.task'))
        .toBeLessThan(validation.installationOrder.indexOf('view.delivery_board'))
      expect(validation.installationOrder.indexOf('type.bug'))
        .toBeLessThan(validation.installationOrder.indexOf('automation.defect_triage'))

      for (const session of [outsider, enterprise]) {
        const hidden = await request.get(
          `${apiBaseUrl}/project-spaces/${spaceId}/scenario-templates`,
          { headers: bearer(session) },
        )
        expect([403, 404]).toContain(hidden.status())
        expect(await hidden.text()).not.toContain('研发项目')
      }

      await installSession(page, owner)
      await page.goto(`/project-spaces/${spaceId}`)
      await page
        .getByTestId('project-space-overview-secondary-tabs')
        .getByRole('tab', { name: '场景模板', exact: true })
        .click()
      const panel = page.getByTestId('scenario-templates-panel')
      await expect(panel).toBeVisible()
      await expect(panel).toContainText('场景模板目录')
      await expect(panel).toContainText('研发项目')
      await expect(panel).toContainText('13 个组件')
      await expect(panel).toContainText('依赖拓扑验证通过')
      await expect(panel.getByRole('button', { name: /研发项目/ })).toBeVisible()
      await expect(panel.getByRole('button', { name: /安装/ })).toHaveCount(0)

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
      await page.screenshot({
        path: testInfo.outputPath('s20-development-template-820.png'),
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
