import { expect, test, type BrowserContext } from '@playwright/test'

import { installSession, loginByApi, webBaseUrl } from './support/api'
import { archiveKnowledgeSpaceFixture, createKnowledgeSpaceFixture } from './support/fixtures'
import { createKnowledgeItem, getKnowledgeContent, knowledgeContentUrl } from './support/knowledge'

test('@smoke @kb-product-m6 isolated browser keeps bounded offline edits and converges after reconnect', async ({ browser, request }) => {
  test.setTimeout(90_000)
  const administrator = await loginByApi(request)
  const space = await createKnowledgeSpaceFixture(request, administrator, 'product-m6-recovery')
  let offlineContext: BrowserContext | undefined
  let observerContext: BrowserContext | undefined

  try {
    const content = await createKnowledgeItem(request, administrator, space, {
      title: 'M6 recovery entry',
      contentType: 'markdown',
      content: 'Recovery seed',
    })
    offlineContext = await browser.newContext({ baseURL: webBaseUrl })
    observerContext = await browser.newContext({ baseURL: webBaseUrl })
    const offlinePage = await offlineContext.newPage()
    const observerPage = await observerContext.newPage()
    await installSession(offlinePage, administrator)
    await installSession(observerPage, administrator)
    await Promise.all([
      offlinePage.goto(knowledgeContentUrl(space.id, content.item.id)),
      observerPage.goto(knowledgeContentUrl(space.id, content.item.id)),
    ])
    await expect(offlinePage.getByText('实时已同步')).toBeVisible({ timeout: 20_000 })
    await expect(observerPage.getByText('实时已同步')).toBeVisible({ timeout: 20_000 })

    await offlineContext.setOffline(true)
    await expect(offlinePage.getByText('当前网络不可用').last()).toBeVisible({ timeout: 15_000 })
    const title = offlinePage.getByLabel('知识内容标题')
    await expect(title).toBeEnabled()
    await title.fill('M6 offline recovery title')
    const editor = offlinePage.locator('.doc-prosemirror[role="textbox"]')
    await editor.click()
    await editor.press(process.platform === 'darwin' ? 'Meta+End' : 'Control+End')
    await editor.pressSequentially(' offline-recovery-body')
    await expect(offlinePage.getByText(/本地修改正在等待重连/)).toBeVisible()
    await expect(offlinePage.getByRole('button', { name: '导出恢复副本' })).toBeVisible()

    await offlineContext.setOffline(false)
    await expect(offlinePage.getByText('实时已同步')).toBeVisible({ timeout: 20_000 })
    await expect(observerPage.getByLabel('知识内容标题')).toHaveValue('M6 offline recovery title', { timeout: 20_000 })
    await expect(observerPage.locator('.doc-prosemirror[role="textbox"]')).toContainText('offline-recovery-body', { timeout: 20_000 })
    await expect.poll(async () => {
      const stored = await getKnowledgeContent(request, administrator, space.id, content.item.id)
      return {
        title: stored.item.title,
        body: stored.blocks.some((block) => block.content.includes('offline-recovery-body')),
      }
    }, { timeout: 20_000 }).toEqual({ title: 'M6 offline recovery title', body: true })
  } finally {
    await offlineContext?.close()
    await observerContext?.close()
    await archiveKnowledgeSpaceFixture(request, administrator, space)
  }
})
