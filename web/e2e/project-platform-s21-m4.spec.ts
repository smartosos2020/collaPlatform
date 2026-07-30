import { expect, test } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi } from './support/api'
import { createIdentity, createSpace } from './support/crossSpace'
import { requireIsolatedIdentityFixture } from './support/fixtures'

test.describe('PROJECT-PLATFORM-S21-M4 information architecture contract', () => {
  test('walks the four-scenario navigation baseline without weakening deep links or disclosure', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(180_000)
    requireIsolatedIdentityFixture()
    const enterprise = await loginByApi(request)
    const suffix = `s21m4_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const outsiderIdentity = await createIdentity(
      request,
      enterprise,
      `${suffix}_outsider`,
      'S21 M4 Outsider',
    )
    const outsider = await loginByApi(request, outsiderIdentity.username, outsiderIdentity.password)
    const spaceId = await createSpace(request, enterprise, suffix, 'information-architecture')
    const headers = bearer(enterprise)

    try {
      const outsiderDetail = await request.get(`${apiBaseUrl}/project-spaces/${spaceId}`, {
        headers: bearer(outsider),
      })
      expect(outsiderDetail.status()).toBe(404)
      expect(await outsiderDetail.text()).not.toContain(suffix)

      await installSession(page, enterprise)
      await page.goto(`/project-spaces/${spaceId}?panel=scenario-templates&source=contract`)
      await expect(page.getByRole('heading', { name: new RegExp(suffix), exact: false })).toBeVisible()
      const overviewTabs = page.getByTestId('project-space-overview-secondary-tabs')
      await expect(overviewTabs.getByRole('tab', { name: '场景模板', exact: true })).toHaveAttribute(
        'aria-selected',
        'true',
      )
      expect(page.url()).toContain('source=contract')

      await page.goto(`/project-spaces/${spaceId}/work-items?panel=project-plan&savedViewId=contract`)
      const workTabs = page.getByTestId('project-work-items-secondary-tabs')
      await expect(workTabs.getByRole('tab', { name: '项目计划', exact: true })).toHaveAttribute(
        'aria-selected',
        'true',
      )
      expect(page.url()).toContain('savedViewId=contract')

      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 980 })
        expect(await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        )).toBeLessThanOrEqual(1)
      }
      await workTabs.getByRole('tab').first().focus()
      await page.keyboard.press('ArrowRight')
      await expect(workTabs.getByRole('tab').nth(1)).toBeFocused()
      await page.screenshot({
        path: testInfo.outputPath('s21-m4-information-architecture-820.png'),
        fullPage: true,
      })
    } finally {
      await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`, { headers })
    }
  })
})
