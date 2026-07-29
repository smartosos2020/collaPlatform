import { expect, test } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

test.describe('PROJECT-PLATFORM-S21-M1 legacy exit audit', () => {
  test('creates immutable audit evidence and records a removal decision', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(180_000)
    requireIsolatedIdentityFixture()
    const admin = await loginByApi(request)
    const created = await request.post(
      `${apiBaseUrl}/admin/project-migrations/legacy-audit/snapshots`,
      { headers: bearer(admin) },
    )
    expect(created.ok(), await created.text()).toBeTruthy()
    const snapshot = await created.json() as {
      id: string
      inventoryVersion: string
      surfaces: Array<{ key: string }>
    }
    expect(snapshot.inventoryVersion).toBe('s21-m1-v1')
    expect(snapshot.surfaces).toHaveLength(10)

    await installSession(page, admin)
    await page.goto('/admin/legacy-exit-audit')
    const workbench = page.getByTestId('legacy-exit-audit-page')
    await expect(workbench).toBeVisible()
    await expect(workbench).toContainText('Legacy surface 与删除决定')
    await expect(workbench).toContainText('api.issues')
    const firstRow = workbench.getByRole('row').filter({ hasText: 'api.issues' })
    await firstRow.getByRole('button', { name: 'M2 删除' }).click()
    await expect(firstRow.getByText('remove')).toBeVisible()

    const exported = await request.get(
      `${apiBaseUrl}/admin/project-migrations/legacy-audit/snapshots/${snapshot.id}/export`,
      { headers: bearer(admin) },
    )
    expect(exported.ok()).toBeTruthy()
    expect(exported.headers()['content-disposition']).toContain('legacy-audit-')
    expect(await exported.text()).not.toContain('password')

    for (const width of [1440, 1366, 820]) {
      await page.setViewportSize({ width, height: 980 })
      expect(await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      )).toBeLessThanOrEqual(1)
    }
    await page.context().setOffline(true)
    await page.evaluate(() => window.dispatchEvent(new Event('offline')))
    await expect(workbench.getByRole('button', { name: '生成审计快照' })).toBeDisabled()
    await page.screenshot({
      path: testInfo.outputPath('s21-m1-legacy-audit-820.png'),
      fullPage: true,
    })
  })
})
