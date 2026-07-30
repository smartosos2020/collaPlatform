import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { addMember, createIdentity, createSpace, getJson } from './support/crossSpace'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type InstallResult = {
  runId: string
  installationId?: string
  scenarioKey: string
  operation: string
  status: string
  localManifestHash: string
  aggregateVersion: number
  replayed: boolean
  steps: Array<{
    componentKey: string
    status: string
    operation: string
    targetIdentity: string
  }>
  conflicts: Array<{
    keyPath: string
    reason: string
    resolved: boolean
    resolution: string
  }>
}

test.describe('PROJECT-PLATFORM-S20 route final', () => {
  test('four scenarios dry-run, install, replay, conflict, upgrade and detach safely', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(480_000)
    requireIsolatedIdentityFixture()
    const enterprise = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(
      request, `${apiBaseUrl}/auth/me`, enterprise,
    )
    const suffix = `s20m5_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const identities = await Promise.all([
      createIdentity(request, enterprise, `${suffix}_owner`, 'S20 Final Owner'),
      createIdentity(request, enterprise, `${suffix}_admin`, 'S20 Final Admin'),
      createIdentity(request, enterprise, `${suffix}_member`, 'S20 Final Member'),
      createIdentity(request, enterprise, `${suffix}_guest`, 'S20 Final Guest'),
      createIdentity(request, enterprise, `${suffix}_outsider`, 'S20 Final Outsider'),
    ])
    const [owner, admin, member, guest, outsider] = await Promise.all(
      identities.map(identity => loginByApi(request, identity.username, identity.password)),
    )
    let spaceId: string | undefined
    try {
      spaceId = await createSpace(request, owner, suffix, 'scenario-route-final')
      await addMember(request, owner, spaceId, identities[1].id, 'admin')
      await addMember(request, owner, spaceId, identities[2].id, 'member')
      await addMember(request, owner, spaceId, identities[3].id, 'guest')
      const base = `${apiBaseUrl}/project-spaces/${spaceId}/scenario-templates`
      const catalog = await getJson<{
        templates: Array<{
          scenarioKey: string
          currentVersion: { manifestHash: string; manifest: { components: unknown[] } }
        }>
      }>(request, base, owner)
      expect(catalog.templates.map(value => value.scenarioKey))
        .toEqual(['delivery', 'development', 'human-resources', 'marketing'])

      const beforeTypes = await getJson<{ items: unknown[] }>(
        request, `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`, owner,
      )
      for (const template of catalog.templates) {
        const dry = await command(
          request, owner, `${base}/${template.scenarioKey}/dry-run`,
          { requestId: `${suffix}-${template.scenarioKey}-dry` },
        )
        expect(dry.operation).toBe('dry_run')
        expect(dry.steps).toHaveLength(template.currentVersion.manifest.components.length)
        expect(dry.steps.every(step => step.status === 'planned')).toBe(true)
      }
      const afterDryTypes = await getJson<{ items: unknown[] }>(
        request, `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`, owner,
      )
      expect(afterDryTypes.items).toHaveLength(beforeTypes.items.length)

      const installs = new Map<string, InstallResult>()
      for (const template of catalog.templates) {
        const requestId = `${suffix}-${template.scenarioKey}-install`
        const installed = await command(
          request, owner, `${base}/${template.scenarioKey}/install`, { requestId },
        )
        expect(installed.status).toBe('completed')
        expect(installed.installationId).toBeTruthy()
        expect(installed.steps.every(step => step.status === 'completed')).toBe(true)
        expect(installed.steps.filter(step => step.operation === 'configure_type'))
          .toHaveLength(6)
        const replay = await command(
          request, owner, `${base}/${template.scenarioKey}/install`, { requestId },
        )
        expect(replay.replayed).toBe(true)
        expect(replay.runId).toBe(installed.runId)
        installs.set(template.scenarioKey, installed)
      }
      const afterInstallTypes = await getJson<{
        items: Array<{ typeKey: string }>
      }>(
        request, `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`, owner,
      )
      expect(afterInstallTypes.items.length).toBeGreaterThanOrEqual(beforeTypes.items.length + 21)
      expect(new Set(afterInstallTypes.items.map(value => value.typeKey)).size)
        .toBe(afterInstallTypes.items.length)

      const divergent = 'a'.repeat(64)
      const blocked = await command(
        request, owner, `${base}/development/upgrade`,
        { requestId: `${suffix}-upgrade-conflict`, localManifestHash: divergent },
      )
      expect(blocked.status).toBe('attention')
      expect(blocked.conflicts).toEqual([
        expect.objectContaining({
          keyPath: 'local_manifest',
          reason: 'LOCAL_MANIFEST_DIVERGED',
          resolved: false,
        }),
      ])
      expect(blocked.steps.every(step => step.status === 'skipped')).toBe(true)
      const resolved = await command(
        request, owner, `${base}/development/upgrade`,
        {
          requestId: `${suffix}-upgrade-local`,
          localManifestHash: divergent,
          conflictResolutions: { local_manifest: 'local' },
        },
      )
      expect(resolved.status).toBe('completed')
      expect(resolved.localManifestHash).toBe(divergent)
      expect(resolved.conflicts[0]).toEqual(expect.objectContaining({
        resolved: true,
        resolution: 'local',
      }))

      const retried = await command(
        request, admin, `${base}/marketing/retry`,
        { requestId: `${suffix}-marketing-retry` },
      )
      expect(retried.status).toBe('completed')
      expect(retried.aggregateVersion).toBeGreaterThan(installs.get('marketing')!.aggregateVersion)
      const detached = await command(
        request, owner, `${base}/delivery/detach`,
        { requestId: `${suffix}-delivery-detach` },
      )
      expect(detached.operation).toBe('detach')
      expect(detached.steps).toHaveLength(1)

      for (const session of [member, guest, outsider, enterprise]) {
        const denied = await request.post(`${base}/human-resources/install`, {
          headers: bearer(session),
          data: { requestId: `${suffix}-denied-${Math.random()}` },
        })
        expect([403, 404]).toContain(denied.status())
        expect(await denied.text()).not.toContain('candidate')
      }

      await installSession(page, owner)
      await page.goto(`/project-spaces/${spaceId}`)
      await page
        .getByTestId('project-space-overview-secondary-tabs')
        .getByRole('tab', { name: '场景模板', exact: true })
        .click()
      const workbench = page.getByTestId('scenario-install-workbench')
      await expect(workbench).toBeVisible({ timeout: 30_000 })
      const preflightButton = workbench.getByRole('button', { name: /预\s*检/ })
      await expect(preflightButton).toBeEnabled()
      await preflightButton.scrollIntoViewIfNeeded()
      await preflightButton.click({ timeout: 15_000 })
      await expect(workbench).toContainText('dry_run · completed')
      await expect(workbench).toContainText('13 个步骤')
      await workbench.getByLabel('本地清单指纹').fill('b'.repeat(64))
      await workbench.getByRole('button', { name: '检查升级' }).click()
      await expect(workbench).toContainText('未决冲突阻止升级')
      await workbench.getByRole('button', { name: '保留本地并升级' }).click()
      await expect(workbench).toContainText('已选择 local')
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 1000 })
        expect(await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        )).toBeLessThanOrEqual(1)
      }
      await page.context().setOffline(true)
      await page.evaluate(() => window.dispatchEvent(new Event('offline')))
      await expect(workbench.getByRole('button', { name: '安装模板' })).toBeDisabled()
      await page.screenshot({
        path: testInfo.outputPath('s20-route-final-820.png'),
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

async function command(
  request: APIRequestContext,
  session: E2eSession,
  url: string,
  data: Record<string, unknown>,
) {
  const response = await request.post(url, { headers: bearer(session), data })
  expect(response.ok(), `POST ${url} failed: ${await response.text()}`).toBeTruthy()
  return await response.json() as InstallResult
}
