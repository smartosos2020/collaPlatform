import { expect, test } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi } from './support/api'
import { addMember, createIdentity, createSpace, getJson } from './support/crossSpace'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Foundation = {
  templates: Array<{
    scenarioKey: string
    currentVersion: {
      manifestHash: string
      manifest: {
        components: Array<{ componentKey: string; dependencies: string[] }>
        prohibitedCapabilities: string[]
      }
    }
  }>
}

test.describe('PROJECT-PLATFORM-S20 M4', () => {
  test('delivery catalog is traceable, permission scoped, offline safe and responsive', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(480_000)
    requireIsolatedIdentityFixture()
    const enterprise = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(
      request, `${apiBaseUrl}/auth/me`, enterprise,
    )
    const suffix = `s20m4_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const identities = await Promise.all([
      createIdentity(request, enterprise, `${suffix}_owner`, 'S20 Delivery Owner'),
      createIdentity(request, enterprise, `${suffix}_admin`, 'S20 Delivery Admin'),
      createIdentity(request, enterprise, `${suffix}_member`, 'S20 Delivery Member'),
      createIdentity(request, enterprise, `${suffix}_guest`, 'S20 Delivery Guest'),
      createIdentity(request, enterprise, `${suffix}_outsider`, 'S20 Delivery Outsider'),
    ])
    const [owner, admin, member, guest, outsider] = await Promise.all(
      identities.map(identity => loginByApi(request, identity.username, identity.password)),
    )
    let spaceId: string | undefined
    try {
      spaceId = await createSpace(request, owner, suffix, 'scenario-delivery')
      await addMember(request, owner, spaceId, identities[1].id, 'admin')
      await addMember(request, owner, spaceId, identities[2].id, 'member')
      await addMember(request, owner, spaceId, identities[3].id, 'guest')

      for (const session of [owner, admin, member, guest]) {
        const foundation = await getJson<Foundation>(
          request, `${apiBaseUrl}/project-spaces/${spaceId}/scenario-templates`, session,
        )
        expect(foundation.templates.map(value => value.scenarioKey))
          .toEqual(['delivery', 'development', 'human-resources', 'marketing'])
        const delivery = foundation.templates.find(value => value.scenarioKey === 'delivery')!
        expect(delivery.currentVersion.manifestHash).toMatch(/^[0-9a-f]{64}$/)
        expect(delivery.currentVersion.manifest.components).toHaveLength(16)
        expect(delivery.currentVersion.manifest.components.map(value => value.componentKey))
          .toEqual(expect.arrayContaining([
            'type.delivery_project',
            'type.delivery_task',
            'type.delivery_risk',
            'type.deliverable',
            'type.delivery_review',
            'type.acceptance',
            'workflow.delivery_stage',
            'workflow.review_remediation',
            'workflow.acceptance_close',
            'relation.delivery_traceability',
            'relation.risk_impact',
            'plan.delivery_timeline',
            'view.delivery_register',
            'automation.delivery_notify',
            'risk.delivery_governance',
            'dashboard.delivery_governance',
          ]))
        expect(delivery.currentVersion.manifest.prohibitedCapabilities)
          .toEqual(expect.arrayContaining([
            'file_content_copy',
            'acceptance_evidence_copy',
            'signature_emulation',
            'customer_content_in_diagnostic',
            'implicit_delivery_success',
          ]))
      }

      const validation = await getJson<{
        valid: boolean
        installationOrder: string[]
        diagnostics: unknown[]
      }>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/scenario-templates/delivery/validation`,
        owner,
      )
      expect(validation.valid).toBe(true)
      expect(validation.diagnostics).toEqual([])
      expect(validation.installationOrder.indexOf('type.deliverable'))
        .toBeLessThan(validation.installationOrder.indexOf('relation.delivery_traceability'))
      expect(validation.installationOrder.indexOf('risk.delivery_governance'))
        .toBeLessThan(validation.installationOrder.indexOf('dashboard.delivery_governance'))

      for (const session of [outsider, enterprise]) {
        const hidden = await request.get(
          `${apiBaseUrl}/project-spaces/${spaceId}/scenario-templates`,
          { headers: bearer(session) },
        )
        expect([403, 404]).toContain(hidden.status())
        expect(await hidden.text()).not.toContain('客户交付')
      }

      await installSession(page, owner)
      await page.goto(`/project-spaces/${spaceId}`)
      const panel = page.getByTestId('scenario-templates-panel')
      await expect(panel).toBeVisible()
      await panel.getByRole('button', { name: /客户交付/ }).click()
      await expect(panel).toContainText('16 个组件')
      await expect(panel).toContainText('交付物评审与整改流程')
      await expect(panel).toContainText('验收、签署引用与关闭流程')
      await expect(panel).toContainText('依赖拓扑验证通过')
      await expect(panel.getByRole('button', { name: /安装/ })).toHaveCount(0)
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 950 })
        expect(await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        )).toBeLessThanOrEqual(1)
      }
      await page.context().setOffline(true)
      await page.evaluate(() => window.dispatchEvent(new Event('offline')))
      await expect(panel).toContainText('离线只读')
      await page.screenshot({
        path: testInfo.outputPath('s20-delivery-template-820.png'),
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
