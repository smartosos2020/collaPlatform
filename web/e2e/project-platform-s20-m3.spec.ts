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

test.describe('PROJECT-PLATFORM-S20 M3', () => {
  test('HR catalog is privacy bounded, permission scoped, offline safe and responsive', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(480_000)
    requireIsolatedIdentityFixture()
    const enterprise = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(
      request, `${apiBaseUrl}/auth/me`, enterprise,
    )
    const suffix = `s20m3_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const identities = await Promise.all([
      createIdentity(request, enterprise, `${suffix}_owner`, 'S20 HR Owner'),
      createIdentity(request, enterprise, `${suffix}_admin`, 'S20 HR Admin'),
      createIdentity(request, enterprise, `${suffix}_member`, 'S20 HR Member'),
      createIdentity(request, enterprise, `${suffix}_guest`, 'S20 HR Guest'),
      createIdentity(request, enterprise, `${suffix}_outsider`, 'S20 HR Outsider'),
    ])
    const [owner, admin, member, guest, outsider] = await Promise.all(
      identities.map(identity => loginByApi(request, identity.username, identity.password)),
    )
    let spaceId: string | undefined
    try {
      spaceId = await createSpace(request, owner, suffix, 'scenario-hr')
      await addMember(request, owner, spaceId, identities[1].id, 'admin')
      await addMember(request, owner, spaceId, identities[2].id, 'member')
      await addMember(request, owner, spaceId, identities[3].id, 'guest')

      for (const session of [owner, admin, member, guest]) {
        const foundation = await getJson<Foundation>(
          request, `${apiBaseUrl}/project-spaces/${spaceId}/scenario-templates`, session,
        )
        expect(foundation.templates.map(value => value.scenarioKey))
          .toEqual(['development', 'human-resources', 'marketing'])
        const hr = foundation.templates.find(value => value.scenarioKey === 'human-resources')!
        expect(hr.currentVersion.manifestHash).toMatch(/^[0-9a-f]{64}$/)
        expect(hr.currentVersion.manifest.components).toHaveLength(16)
        expect(hr.currentVersion.manifest.components.map(value => value.componentKey))
          .toEqual(expect.arrayContaining([
            'type.hiring_plan',
            'type.position',
            'type.candidate',
            'type.interview',
            'type.offer',
            'type.onboarding',
            'workflow.position_approval',
            'workflow.candidate_stage',
            'workflow.interview_offer',
            'relation.hiring_trace',
            'relation.candidate_interview',
            'view.hiring_board',
            'view.interview_calendar',
            'view.recruiter_tasks',
            'automation.interview_notify',
            'metric.hiring_pipeline',
          ]))
        expect(hr.currentVersion.manifest.prohibitedCapabilities)
          .toEqual(expect.arrayContaining([
            'candidate_pii_in_catalog',
            'candidate_evaluation_in_diagnostic',
            'hidden_candidate_count',
            'personal_ranking',
            'interviewer_performance',
          ]))
        expect(JSON.stringify(hr)).not.toContain(identities[2].displayName)
      }

      const validation = await getJson<{
        valid: boolean
        installationOrder: string[]
        diagnostics: unknown[]
      }>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/scenario-templates/human-resources/validation`,
        owner,
      )
      expect(validation.valid).toBe(true)
      expect(validation.diagnostics).toEqual([])
      expect(validation.installationOrder.indexOf('type.candidate'))
        .toBeLessThan(validation.installationOrder.indexOf('workflow.candidate_stage'))

      for (const session of [outsider, enterprise]) {
        const hidden = await request.get(
          `${apiBaseUrl}/project-spaces/${spaceId}/scenario-templates`,
          { headers: bearer(session) },
        )
        expect([403, 404]).toContain(hidden.status())
        expect(await hidden.text()).not.toContain('HR 招聘')
      }

      await installSession(page, owner)
      await page.goto(`/project-spaces/${spaceId}`)
      await page
        .getByTestId('project-space-overview-secondary-tabs')
        .getByRole('tab', { name: '场景模板', exact: true })
        .click()
      const panel = page.getByTestId('scenario-templates-panel')
      await expect(panel).toBeVisible()
      await panel.getByRole('button', { name: /HR 招聘/ }).click()
      await expect(panel).toContainText('16 个组件')
      await expect(panel).toContainText('敏感字段默认受限')
      await expect(panel).toContainText('禁止个人排名')
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
        path: testInfo.outputPath('s20-hr-template-820.png'),
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
