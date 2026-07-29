import { expect, test } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

test.describe('PROJECT-PLATFORM-S21-M3 engineering route-final', () => {
  test('keeps canonical work usable and exposes only controlled readiness evidence', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(240_000)
    requireIsolatedIdentityFixture()
    const admin = await loginByApi(request)
    const headers = bearer(admin)
    const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`
    const missingId = crypto.randomUUID()
    let spaceId: string | undefined

    try {
      for (const path of [
        '/projects',
        `/issues/${missingId}`,
        '/my/issues',
        `/compat/work-items/legacy/issues/${missingId}`,
      ]) {
        const response = await request.get(`${apiBaseUrl}${path}`, { headers })
        expect(response.status(), `GET ${path}`).toBe(404)
      }

      const spaceResponse = await request.post(`${apiBaseUrl}/project-spaces`, {
        headers,
        data: {
          spaceKey: `s21-m3-${suffix}`,
          name: `S21 M3 engineering readiness ${suffix}`,
          visibility: 'private',
        },
      })
      expect(spaceResponse.ok(), await spaceResponse.text()).toBeTruthy()
      spaceId = (await spaceResponse.json() as { id: string }).id

      const snapshotResponse = await request.post(
        `${apiBaseUrl}/admin/project-migrations/legacy-audit/snapshots`,
        { headers },
      )
      expect(snapshotResponse.ok(), await snapshotResponse.text()).toBeTruthy()
      const snapshot = await snapshotResponse.json() as {
        inventoryVersion: string
        status: 'ready' | 'blocked'
        surfaces: Array<{ key: string }>
      }
      expect(snapshot.inventoryVersion).toBe('s21-m1-v1')
      expect(snapshot.status).toBe('ready')
      expect(snapshot.surfaces.map(surface => surface.key)).toContain('api.issues')

      await installSession(page, admin)
      await page.goto('/project-spaces')
      await expect(page.getByText('项目空间', { exact: true }).first()).toBeVisible()
      await expect(
        page.getByRole('heading', {
          name: `S21 M3 engineering readiness ${suffix}`,
          exact: true,
        }),
      ).toBeVisible()

      await page.goto('/admin/legacy-exit-audit')
      const evidenceIndex = page.getByTestId('legacy-exit-audit-page')
      await expect(evidenceIndex).toBeVisible()
      await expect(evidenceIndex).toContainText('审计无阻断')
      await expect(evidenceIndex).toContainText('api.issues')
      await expect(evidenceIndex).not.toContainText('password')

      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 980 })
        expect(await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        )).toBeLessThanOrEqual(1)
      }

      await page.context().setOffline(true)
      await page.evaluate(() => window.dispatchEvent(new Event('offline')))
      await expect(evidenceIndex).toContainText('离线状态下不能生成快照或追加删除决定。')
      await expect(evidenceIndex.getByRole('button', { name: '生成审计快照' })).toBeDisabled()
      await page.screenshot({
        path: testInfo.outputPath('s21-m3-engineering-evidence-820.png'),
        fullPage: true,
      })
    } finally {
      if (spaceId) {
        await page.context().setOffline(false).catch(() => undefined)
        await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`, { headers })
      }
    }
  })
})
