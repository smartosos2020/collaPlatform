import { expect, test } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi } from './support/api'
import {
  addMember,
  createGrant,
  createIdentity,
  createSpace,
  getJson,
  grantLifecycle,
  postJson,
  publishedProjectType,
} from './support/crossSpace'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Panorama = {
  preference: { compact: boolean; windowDays: number; version: number }
  slices: Array<{ kind: string; status: string; version: number; source: string }>
  health: {
    status: string
    grants: number
    relations: number
    syncRules: number
    openConflicts: number
    truncated: boolean
  }
}

test.describe('PROJECT-PLATFORM-S18 M4', () => {
  test('authorized panorama aggregates minimal lineage and hides outsiders @route-final', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(480_000)
    requireIsolatedIdentityFixture()
    const enterprise = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(
      request, `${apiBaseUrl}/auth/me`, enterprise,
    )
    const suffix = `s18m4_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const sourceIdentity = await createIdentity(request, enterprise, `${suffix}_source`, 'S18 M4 Source Owner')
    const targetIdentity = await createIdentity(request, enterprise, `${suffix}_target`, 'S18 M4 Target Owner')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S18 M4 Member')
    const guestIdentity = await createIdentity(request, enterprise, `${suffix}_guest`, 'S18 M4 Guest')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S18 M4 Outsider')
    const sourceOwner = await loginByApi(request, sourceIdentity.username, sourceIdentity.password)
    const targetOwner = await loginByApi(request, targetIdentity.username, targetIdentity.password)
    const member = await loginByApi(request, memberIdentity.username, memberIdentity.password)
    const guest = await loginByApi(request, guestIdentity.username, guestIdentity.password)
    const outsider = await loginByApi(request, outsiderIdentity.username, outsiderIdentity.password)
    let sourceSpaceId: string | undefined
    let targetSpaceId: string | undefined
    try {
      sourceSpaceId = await createSpace(request, sourceOwner, suffix, 'source')
      targetSpaceId = await createSpace(request, targetOwner, suffix, 'target')
      await addMember(request, sourceOwner, sourceSpaceId, memberIdentity.id, 'member')
      await addMember(request, sourceOwner, sourceSpaceId, guestIdentity.id, 'guest')
      const sourceType = await publishedProjectType(request, sourceOwner, sourceSpaceId, `${suffix}-source`)
      const targetType = await publishedProjectType(request, targetOwner, targetSpaceId, `${suffix}-target`)
      let grant = await createGrant(
        request, sourceOwner, sourceSpaceId, targetSpaceId,
        sourceType, targetType, `${suffix}-grant`,
      )
      grant = await grantLifecycle(request, sourceOwner, sourceSpaceId, grant, 'request', undefined, `${suffix}-request`)
      grant = await grantLifecycle(request, sourceOwner, sourceSpaceId, grant, 'confirm', 'source', `${suffix}-source-confirm`)
      grant = await grantLifecycle(request, targetOwner, targetSpaceId, grant, 'confirm', 'target', `${suffix}-target-confirm`)
      expect(grant.status).toBe('active')

      for (const session of [sourceOwner, member, guest]) {
        const panorama = await getJson<Panorama>(
          request, `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/panorama`, session,
        )
        expect(panorama.health.grants).toBe(1)
        expect(panorama.slices).toEqual(expect.arrayContaining([
          expect.objectContaining({ kind: 'grant', status: 'active', source: 'cross-space-grant' }),
        ]))
        expect(JSON.stringify(panorama)).not.toContain(sourceType.typeId)
      }
      for (const session of [outsider, enterprise]) {
        const hidden = await request.get(
          `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/panorama`,
          { headers: bearer(session) },
        )
        expect([403, 404]).toContain(hidden.status())
        expect(await hidden.text()).not.toContain(suffix)
      }

      let panorama = await getJson<Panorama>(
        request, `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/panorama`, sourceOwner,
      )
      const preference = await postJson<Panorama['preference']>(
        request,
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/panorama/preference`,
        sourceOwner,
        {
          schemaVersion: 1,
          requestId: `${suffix}-preference`,
          expectedVersion: panorama.preference.version,
          compact: true,
          windowDays: 30,
        },
        `${suffix}-preference`,
      )
      expect(preference.compact).toBeTruthy()
      panorama = await getJson<Panorama>(
        request, `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/panorama`, sourceOwner,
      )
      expect(panorama.preference.compact).toBeTruthy()

      await installSession(page, sourceOwner)
      await page.goto(`/project-spaces/${sourceSpaceId}`)
      await page
        .getByTestId('project-space-overview-secondary-tabs')
        .getByRole('tab', { name: '跨团队全景', exact: true })
        .click()
      const panel = page.getByTestId('cross-team-panorama-panel')
      await expect(panel).toBeVisible()
      await expect(panel).toContainText('跨团队全景与协作审计')
      await expect(panel).toContainText('active')
      await expect(panel).not.toContainText(sourceType.typeId)
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        await expect(panel).toBeVisible()
        expect(await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        )).toBeLessThanOrEqual(1)
      }
      await page.context().setOffline(true)
      await page.evaluate(() => window.dispatchEvent(new Event('offline')))
      await expect(panel).toContainText('当前离线')
      await expect(panel.getByRole('switch', { name: '紧凑视图' })).toBeDisabled()
      await page.context().setOffline(false)
      await page.screenshot({
        path: testInfo.outputPath('s18-cross-team-panorama-820.png'),
        fullPage: true,
      })
    } finally {
      await page.context().setOffline(false).catch(() => undefined)
      for (const entry of [
        { id: sourceSpaceId, session: sourceOwner },
        { id: targetSpaceId, session: targetOwner },
      ]) {
        if (entry.id) {
          await request.post(`${apiBaseUrl}/project-spaces/${entry.id}/settings/archive`, {
            headers: bearer(entry.session),
          }).catch(() => undefined)
        }
      }
      for (const identity of [
        targetIdentity, memberIdentity, guestIdentity, outsiderIdentity, sourceIdentity,
      ]) {
        await request.post(`${apiBaseUrl}/admin/users/${identity.id}/offboard`, {
          headers: bearer(enterprise),
          data: { handoverToUserId: enterpriseProfile.id },
        }).catch(() => undefined)
      }
    }
  })
})
