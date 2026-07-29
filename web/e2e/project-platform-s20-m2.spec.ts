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
          ownerContract: string
          dependencies: string[]
        }>
        prohibitedCapabilities: string[]
      }
    }
  }>
}

test.describe('PROJECT-PLATFORM-S20 M2', () => {
  test('marketing catalog is versioned, permission scoped, offline safe and responsive', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(480_000)
    requireIsolatedIdentityFixture()
    const enterprise = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(
      request, `${apiBaseUrl}/auth/me`, enterprise,
    )
    const suffix = `s20m2_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const identities = await Promise.all([
      createIdentity(request, enterprise, `${suffix}_owner`, 'S20 Market Owner'),
      createIdentity(request, enterprise, `${suffix}_admin`, 'S20 Market Admin'),
      createIdentity(request, enterprise, `${suffix}_member`, 'S20 Market Member'),
      createIdentity(request, enterprise, `${suffix}_guest`, 'S20 Market Guest'),
      createIdentity(request, enterprise, `${suffix}_outsider`, 'S20 Market Outsider'),
    ])
    const sessions = await Promise.all(
      identities.map(identity => loginByApi(request, identity.username, identity.password)),
    )
    const [owner, admin, member, guest, outsider] = sessions
    let spaceId: string | undefined
    try {
      spaceId = await createSpace(request, owner, suffix, 'scenario-marketing')
      await addMember(request, owner, spaceId, identities[1].id, 'admin')
      await addMember(request, owner, spaceId, identities[2].id, 'member')
      await addMember(request, owner, spaceId, identities[3].id, 'guest')

      for (const session of [owner, admin, member, guest]) {
        const foundation = await getJson<Foundation>(
          request, `${apiBaseUrl}/project-spaces/${spaceId}/scenario-templates`, session,
        )
        expect(foundation.templates.map(value => value.scenarioKey))
          .toEqual(['development', 'marketing'])
        const marketing = foundation.templates.find(value => value.scenarioKey === 'marketing')!
        expect(marketing.currentVersion.manifestHash).toMatch(/^[0-9a-f]{64}$/)
        expect(marketing.currentVersion.manifest.components).toHaveLength(15)
        expect(marketing.currentVersion.manifest.components.map(value => value.componentKey))
          .toEqual(expect.arrayContaining([
            'type.campaign',
            'type.content',
            'type.asset',
            'type.channel',
            'type.placement',
            'type.review',
            'workflow.content_review',
            'workflow.channel_publish',
            'relation.campaign_content',
            'relation.distribution',
            'view.campaign_calendar',
            'view.campaign_board',
            'automation.review_notify',
            'metric.campaign_review',
            'dashboard.campaign_retrospective',
          ]))
        expect(marketing.currentVersion.manifest.prohibitedCapabilities)
          .toContain('external_channel_credentials')
        expect(JSON.stringify(marketing)).not.toContain(identities[0].displayName)
      }

      const validation = await getJson<{
        valid: boolean
        installationOrder: string[]
        diagnostics: unknown[]
      }>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/scenario-templates/marketing/validation`,
        owner,
      )
      expect(validation.valid).toBe(true)
      expect(validation.diagnostics).toEqual([])
      expect(validation.installationOrder.indexOf('type.content'))
        .toBeLessThan(validation.installationOrder.indexOf('workflow.content_review'))
      expect(validation.installationOrder.indexOf('metric.campaign_review'))
        .toBeLessThan(validation.installationOrder.indexOf('dashboard.campaign_retrospective'))

      for (const session of [outsider, enterprise]) {
        const hidden = await request.get(
          `${apiBaseUrl}/project-spaces/${spaceId}/scenario-templates`,
          { headers: bearer(session) },
        )
        expect([403, 404]).toContain(hidden.status())
        expect(await hidden.text()).not.toContain('市场活动')
      }

      await installSession(page, owner)
      await page.goto(`/project-spaces/${spaceId}`)
      const panel = page.getByTestId('scenario-templates-panel')
      await expect(panel).toBeVisible()
      await panel.getByRole('button', { name: /市场活动/ }).click()
      await expect(panel).toContainText('15 个组件')
      await expect(panel).toContainText('内容评审与素材审批流程')
      await expect(panel).toContainText('市场日历')
      await expect(panel).toContainText('依赖拓扑验证通过')
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
        path: testInfo.outputPath('s20-marketing-template-820.png'),
        fullPage: true,
      })
    } finally {
      await page.context().setOffline(false).catch(() => undefined)
      if (spaceId) {
        await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`, {
          headers: bearer(owner),
        }).catch(() => undefined)
      }
      for (const identity of identities.reverse()) {
        await request.post(`${apiBaseUrl}/admin/users/${identity.id}/offboard`, {
          headers: bearer(enterprise),
          data: { handoverToUserId: enterpriseProfile.id },
        }).catch(() => undefined)
      }
    }
  })
})
