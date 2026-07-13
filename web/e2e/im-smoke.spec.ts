import { expect, test } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi } from './support/api'
import { installFailureEvidence } from './support/diagnostics'
import { requireIsolatedIdentityFixture, uniqueFixtureName } from './support/fixtures'

test('@route-final M4 IM browser route: search and convert a message to an issue', async ({ page, request }, testInfo) => {
  requireIsolatedIdentityFixture()
  const flushEvidence = installFailureEvidence(page, testInfo)
  const session = await loginByApi(request)
  const fixtureName = uniqueFixtureName('im-convert')

  try {
    await installSession(page, session)
    await page.goto('/im')
    await expect(page.getByText('消息').first()).toBeVisible()

    const suffix = fixtureName.toLowerCase().replace(/[^a-z0-9]/g, '').slice(-10)
    const memberResponse = await request.post(`${apiBaseUrl}/admin/users`, {
      headers: bearer(session),
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
      headers: bearer(session),
      data: {
        conversationType: 'group',
        title,
        memberIds: [member.id],
      },
    })
    expect(conversationResponse.ok()).toBeTruthy()
    const conversation = await conversationResponse.json()

    const projectResponse = await request.post(`${apiBaseUrl}/projects`, {
      headers: bearer(session),
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
    await expect(page.locator('.im-message-content').filter({ hasText: messageText })).toBeVisible()

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
  } finally {
    await flushEvidence()
  }
})
