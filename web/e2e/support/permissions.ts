import { expect, type APIResponse, type Page } from '@playwright/test'

export async function expectApiDenied(response: APIResponse, expected: 401 | 403 | 404) {
  expect(response.status()).toBe(expected)
}

export async function expectLoginRequired(page: Page) {
  await expect(page).toHaveURL(/\/login/)
  await expect(page.getByLabel('账号')).toBeVisible()
}

export async function expireBrowserSession(page: Page) {
  await page.evaluate(() => {
    localStorage.removeItem('colla.accessToken')
    localStorage.removeItem('colla.refreshToken')
  })
  await page.reload()
  await expectLoginRequired(page)
}
