import { expect, type APIRequestContext, type Page } from '@playwright/test'

export const apiBaseUrl = process.env.COLLA_E2E_API_BASE_URL ?? 'http://127.0.0.1:8080/api'
export const webBaseUrl = process.env.COLLA_E2E_WEB_BASE_URL ?? 'http://127.0.0.1:5173'

export type E2eSession = {
  accessToken: string
  refreshToken: string
}

export async function loginByApi(
  request: APIRequestContext,
  username = process.env.COLLA_E2E_ADMIN_USERNAME ?? process.env.COLLA_E2E_USERNAME ?? 'admin',
  password = process.env.COLLA_E2E_ADMIN_PASSWORD ?? process.env.COLLA_E2E_PASSWORD ?? 'admin123456',
): Promise<E2eSession> {
  const response = await request.post(`${apiBaseUrl}/auth/login`, {
    data: {
      username,
      password,
      deviceType: 'web',
      deviceFingerprint: `playwright-${username}-${Date.now()}`,
      deviceName: 'Playwright isolated browser suite',
      appVersion: 'pilot-v2',
    },
  })
  expect(response.ok(), `API login failed for ${username}`).toBeTruthy()
  const payload = await response.json() as E2eSession
  return payload
}

export async function installSession(page: Page, session: E2eSession) {
  const persist = ([accessToken, refreshToken]: [string, string]) => {
      localStorage.setItem('colla.accessToken', accessToken)
      localStorage.setItem('colla.refreshToken', refreshToken)
  }
  const tokenPair: [string, string] = [session.accessToken, session.refreshToken]
  await page.addInitScript(persist, tokenPair)
  await page.goto('/login')
  await page.evaluate(persist, tokenPair)
}

export function bearer(session: E2eSession) {
  return { Authorization: `Bearer ${session.accessToken}` }
}
