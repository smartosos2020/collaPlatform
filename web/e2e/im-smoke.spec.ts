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

  const projectResponse = await request.post(`${apiBaseUrl}/projects`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: {
      projectKey: `IMSM${suffix.slice(-6)}`.toUpperCase(),
      name: `IM Smoke Project ${suffix}`,
      description: 'IM smoke conversion target',
      memberIds: [],
    },
  })
  expect(projectResponse.ok()).toBeTruthy()
  const project = await projectResponse.json()

  await page.goto(`/im?conversationId=${conversation.id}`)
  await expect(page.getByRole('heading', { name: title })).toBeVisible()

  const messageText = `IM smoke message ${suffix}`
  const sendResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().includes(`/api/conversations/${conversation.id}/messages`),
  )
  await page.getByPlaceholder(/输入消息/).fill(messageText)
  await page.keyboard.press('Enter')
  const sendResponse = await sendResponsePromise
  expect(sendResponse.ok()).toBeTruthy()
  await expect(page.getByText(messageText)).toBeVisible()

  await page.getByPlaceholder('搜索当前会话消息').fill(messageText)
  await page.keyboard.press('Enter')
  await expect(page.locator('.im-message-search-results').getByText(messageText)).toBeVisible()
  await page.getByPlaceholder('搜索当前会话消息').fill('')

  const targetMessage = page
    .locator('.im-message-body')
    .filter({ hasText: messageText })
  await targetMessage.scrollIntoViewIfNeeded()
  await targetMessage.click({ button: 'right' })
  await page.getByRole('menuitem', { name: /转事项/ }).click()

  const convertModal = page.locator('.ant-modal').filter({ hasText: '从消息创建事项' })
  await expect(convertModal).toBeVisible()
  await convertModal.getByRole('combobox', { name: /项目/ }).click()
  await page.locator('.ant-select-dropdown').getByText(`${project.name} (${project.projectKey})`).click()
  await convertModal.getByRole('button', { name: 'OK' }).click()
  await expect(page.getByText('已从消息创建事项')).toBeVisible()
  await expect(page.locator('.im-message-content').filter({ hasText: /已将消息转为/ })).toBeVisible()
})
