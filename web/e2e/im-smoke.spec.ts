import { expect, test } from '@playwright/test'

const apiBaseUrl = process.env.COLLA_E2E_API_BASE_URL ?? 'http://localhost:8080/api'
const username = process.env.COLLA_E2E_USERNAME ?? 'admin'
const loginCredential = process.env.COLLA_E2E_PASSWORD ?? ['admin', '123456'].join('')

test('IM browser smoke: login, open conversation, send message, open context menu', async ({ page, request }) => {
  await page.goto('/login')
  await page.getByLabel('账号').fill(username)
  await page.getByLabel('密码').fill(loginCredential)
  await page.getByRole('button', { name: /登\s*录/ }).click()
  await expect(page.getByText('消息').first()).toBeVisible()

  const accessToken = await page.evaluate(() => localStorage.getItem('colla.accessToken'))
  expect(accessToken).toBeTruthy()

  const suffix = Date.now().toString(36)
  const memberResponse = await request.post(`${apiBaseUrl}/admin/users`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: {
      username: `smoke_${suffix}`,
      password: 'member123456',
      displayName: `Smoke ${suffix}`,
      email: `smoke_${suffix}@colla.local`,
      roleCode: 'member',
    },
  })
  expect(memberResponse.ok()).toBeTruthy()
  const member = await memberResponse.json()

  const title = `IM Smoke ${suffix}`
  const conversationResponse = await request.post(`${apiBaseUrl}/conversations`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: {
      conversationType: 'group',
      title,
      memberIds: [member.id],
    },
  })
  expect(conversationResponse.ok()).toBeTruthy()
  const conversation = await conversationResponse.json()

  await page.goto(`/im?conversationId=${conversation.id}`)
  await expect(page.getByRole('heading', { name: title })).toBeVisible()

  const messageText = `IM smoke message ${suffix}`
  await page.getByPlaceholder(/输入消息/).fill(messageText)
  await page.keyboard.press('Enter')
  await expect(page.getByText(messageText)).toBeVisible()

  await page
    .locator('.im-message-body')
    .filter({ hasText: messageText })
    .dispatchEvent('contextmenu', { bubbles: true, cancelable: true, button: 2 })
  await expect(page.getByRole('menuitem', { name: /转事项/ })).toBeVisible()
})
