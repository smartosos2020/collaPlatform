import { expect, test, type APIRequestContext, type BrowserContext, type Locator, type Page } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { archiveKnowledgeSpaceFixture, createKnowledgeSpaceFixture, uniqueFixtureName } from './support/fixtures'
import { createKnowledgeItem, getKnowledgeContent, knowledgeContentUrl, saveKnowledgeBlocks } from './support/knowledge'

test('@smoke @kb-product-m9 editor interactions remain contextual, durable and accessible', async ({ browser, context, page, request }) => {
  test.setTimeout(300_000)
  page.setDefaultTimeout(15_000)
  const administrator = await loginByApi(request)
  const space = await createKnowledgeSpaceFixture(request, administrator, 'kb-product-m9-editor')
  let viewerContext: BrowserContext | undefined
  let viewerId: string | undefined

  try {
    const shortItem = await createKnowledgeItem(request, administrator, space, {
      title: uniqueFixtureName('m9-interaction'),
      contentType: 'markdown',
      content: 'M9 initial content',
    })
    const seeded = await getKnowledgeContent(request, administrator, space.id, shortItem.item.id)
    await saveKnowledgeBlocks(request, administrator, space.id, shortItem.item.id, seeded.item.currentVersionNo, [
      { blockType: 'heading', content: 'M9 interaction matrix', sortOrder: 0 },
      { blockType: 'paragraph', content: 'Alpha editable block', sortOrder: 1 },
      { blockType: 'paragraph', content: 'Beta editable block', sortOrder: 2 },
      { blockType: 'table', content: JSON.stringify({ columns: ['Owner', 'State'], rows: [['M9', 'Ready']] }), sortOrder: 3 },
    ])
    const longItem = await createKnowledgeItem(request, administrator, space, {
      title: uniqueFixtureName('m9-long'),
      contentType: 'markdown',
      content: 'Long content seed',
    })
    const longSeed = await getKnowledgeContent(request, administrator, space.id, longItem.item.id)
    await saveKnowledgeBlocks(
      request,
      administrator,
      space.id,
      longItem.item.id,
      longSeed.item.currentVersionNo,
      Array.from({ length: 80 }, (_, index) => ({ blockType: 'paragraph', content: `M9 long block ${index + 1}`, sortOrder: index })),
    )

    await installSession(page, administrator)
    await page.setViewportSize({ width: 1366, height: 820 })
    await page.goto(knowledgeContentUrl(space.id, shortItem.item.id))
    const editor = page.getByRole('textbox', { name: '知识内容正文编辑器' })
    await expect(editor).toBeVisible()
    await expect(page.getByText('实时已同步')).toBeVisible()

    const alpha = paragraphWithText(page, 'Alpha editable block')
    await alpha.click()
    await page.keyboard.press('End')
    await alpha.evaluate((element) => element.dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true, data: '' })))
    await page.keyboard.insertText(' 中文组合输入')
    await alpha.evaluate((element) => element.dispatchEvent(new CompositionEvent('compositionend', { bubbles: true, data: ' 中文组合输入' })))
    await page.keyboard.type(' continuous')
    await expect(editor).toBeFocused()
    await expect(alpha).toContainText('中文组合输入 continuous')
    await expect.poll(async () => {
      const detail = await getKnowledgeContent(request, administrator, space.id, shortItem.item.id)
      return detail.blocks.some((block) => block.content.includes('中文组合输入 continuous'))
    }, { timeout: 20_000 }).toBeTruthy()

    await alpha.click()
    await page.keyboard.press('End')
    await page.keyboard.down('Shift')
    for (let index = 0; index < 10; index += 1) await page.keyboard.press('ArrowLeft')
    await page.keyboard.up('Shift')
    const selectionToolbar = page.locator('.doc-selection-toolbar')
    await expect(selectionToolbar).toBeVisible()
    await expectInside(page.locator('.doc-editor-canvas'), selectionToolbar)
    await expect(selectionToolbar.getByRole('button', { name: '撤销' })).toBeVisible()
    await page.keyboard.press('ArrowRight')
    await expect(selectionToolbar).toBeHidden()

    const beta = paragraphWithText(page, 'Beta editable block')
    await beta.hover()
    const insertButton = page.getByRole('button', { name: '插入块' })
    const operationButton = page.getByRole('button', { name: '操作块' })
    await expect(insertButton).toBeVisible()
    await expect(operationButton).toBeVisible()
    await expectInside(page.locator('.doc-editor-canvas'), insertButton)
    const betaBox = await beta.boundingBox()
    const handleBox = await operationButton.boundingBox()
    expect(betaBox).not.toBeNull()
    expect(handleBox).not.toBeNull()
    expect(Math.abs(handleBox!.y - betaBox!.y)).toBeLessThan(48)
    await operationButton.click()
    const operationMenu = page.locator('.doc-block-operation-dropdown:visible')
    await expect(operationMenu).toBeVisible()
    await expectInside(page.locator('.doc-editor-canvas'), operationMenu)
    await page.getByLabel('知识内容标题').click()
    await expect(operationMenu).toBeHidden()

    await beta.hover()
    await insertButton.click()
    const slashMenu = page.getByRole('dialog', { name: '插入内容块' })
    await expect(slashMenu).toBeVisible()
    await expectInside(page.locator('.doc-editor-canvas'), slashMenu)
    const slashSearch = slashMenu.getByPlaceholder('搜索块、对象或文件')
    await slashSearch.fill('提示')
    await slashSearch.press('Enter')
    await expect(page.locator('.doc-callout')).toHaveCount(1)
    await expect(slashMenu).toBeHidden()

    await beta.hover()
    await insertButton.click()
    await slashSearch.press('ArrowDown')
    await slashSearch.press('Enter')
    const betaHeading = page.locator('.doc-prosemirror h2').filter({ hasText: 'Beta editable block' })
    await expect(betaHeading).toBeVisible()
    await betaHeading.hover()
    await operationButton.click()
    await page.getByRole('menuitem', { name: '转为正文' }).click()
    await expect(paragraphWithText(page, 'Beta editable block')).toBeVisible()
    await paragraphWithText(page, 'Beta editable block').hover()
    await insertButton.click()
    await slashSearch.press('Escape')
    await expect(slashMenu).toBeHidden()
    await expect(editor).toBeFocused()
    await insertButton.click()
    await page.getByLabel('知识内容标题').click()
    await expect(slashMenu).toBeHidden()

    await beta.hover()
    await operationButton.click()
    await page.getByRole('menuitem', { name: '复制块' }).click()
    await expect(page.locator('.doc-prosemirror p').filter({ hasText: 'Beta editable block' })).toHaveCount(2)
    const blockIds = await page.locator('.doc-prosemirror > [data-block-id]').evaluateAll((elements) => elements.map((element) => element.getAttribute('data-block-id')))
    expect(blockIds.every(Boolean)).toBeTruthy()
    expect(new Set(blockIds).size).toBe(blockIds.length)

    const duplicatedBeta = page.locator('.doc-prosemirror p').filter({ hasText: 'Beta editable block' }).last()
    await duplicatedBeta.hover()
    await operationButton.click()
    await page.getByRole('menuitem', { name: '上移' }).click()
    await duplicatedBeta.hover()
    await operationButton.click()
    await page.getByRole('menuitem', { name: '转为引用' }).click()
    await expect(page.locator('.doc-prosemirror blockquote').filter({ hasText: 'Beta editable block' })).toBeVisible()
    await page.locator('.doc-prosemirror blockquote').filter({ hasText: 'Beta editable block' }).hover()
    await operationButton.click()
    await page.getByRole('menuitem', { name: '删除块' }).click()
    await expect(page.locator('.doc-prosemirror blockquote').filter({ hasText: 'Beta editable block' })).toHaveCount(0)
    await editor.press('Control+z')
    await expect(page.locator('.doc-prosemirror blockquote').filter({ hasText: 'Beta editable block' })).toBeVisible()
    await editor.press('Control+y')
    await expect(page.locator('.doc-prosemirror blockquote').filter({ hasText: 'Beta editable block' })).toHaveCount(0)
    await editor.press('Control+z')

    await appendParagraph(editor)
    await dispatchPaste(editor, { text: 'Plain pasted text' })
    await expect(editor).toContainText('Plain pasted text')
    await appendParagraph(editor)
    await dispatchPaste(editor, { text: 'Safe HTML', html: '<p onclick="alert(1)">Safe <strong>HTML</strong><script>window.evil=true</script></p>' })
    await expect(editor.locator('strong').filter({ hasText: 'HTML' })).toBeVisible()
    expect(await editor.locator('script, [onclick]').count()).toBe(0)
    expect(await page.evaluate(() => (window as typeof window & { evil?: boolean }).evil)).toBeUndefined()
    await appendParagraph(editor)
    await dispatchPaste(editor, { text: '- Pasted alpha\n- Pasted beta' })
    await expect(editor.locator('ul').filter({ hasText: 'Pasted alpha' })).toBeVisible()
    await appendParagraph(editor)
    await dispatchPaste(editor, { text: 'A\tB\n1\t2' })
    const pastedTable = editor.locator('table').last()
    await expect(pastedTable).toBeVisible()

    const firstCell = pastedTable.locator('td').first()
    await firstCell.click()
    const tableToolbar = page.locator('.doc-table-editor-toolbar')
    await expect(tableToolbar).toBeVisible()
    await expectInside(page.locator('.doc-editor-canvas'), tableToolbar)
    const initialRows = await pastedTable.locator('tr').count()
    const initialColumns = await pastedTable.locator('tr').first().locator('td,th').count()
    await tableToolbar.getByRole('button', { name: '在下方添加表格行' }).click()
    await tableToolbar.getByRole('button', { name: '在右侧添加表格列' }).click()
    await expect(pastedTable.locator('tr')).toHaveCount(initialRows + 1)
    await expect(pastedTable.locator('tr').first().locator('td,th')).toHaveCount(initialColumns + 1)
    await editor.press('Control+z')
    await editor.press('Control+z')
    await expect(pastedTable.locator('tr')).toHaveCount(initialRows)
    await expect(pastedTable.locator('tr').first().locator('td,th')).toHaveCount(initialColumns)

    await context.setOffline(true)
    await page.locator('.doc-editor-shell > input[type="file"][accept="image/*"]').setInputFiles({
      name: 'm9-image.svg',
      mimeType: 'image/svg+xml',
      buffer: Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="120" height="60"><rect width="120" height="60" fill="#1677ff"/></svg>'),
    })
    await expect(page.getByText(/上传失败：m9-image\.svg/)).toBeVisible()
    await context.setOffline(false)
    await page.getByRole('button', { name: '重试上传' }).click()
    await expect(page.getByText(/上传完成：m9-image\.svg/)).toBeVisible({ timeout: 20_000 })
    const imageCard = page.locator('.doc-file-card-node.image').last()
    const uploadedImage = imageCard.locator('img')
    await expect(uploadedImage).toHaveAttribute('src', /X-Amz-|9000/)
    await expect.poll(() => uploadedImage.evaluate((image: HTMLImageElement) => image.complete ? image.naturalWidth : 0)).toBeGreaterThan(0)
    await imageCard.getByLabel('图片说明').fill('M9 image caption')
    await expect(imageCard.getByLabel('图片说明')).toHaveValue('M9 image caption')
    await imageCard.locator('input[type="file"]').setInputFiles({
      name: 'm9-image-replaced.svg',
      mimeType: 'image/svg+xml',
      buffer: Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="120" height="60"><rect width="120" height="60" fill="#22c55e"/></svg>'),
    })
    await expect(imageCard.getByText('m9-image-replaced.svg')).toBeVisible({ timeout: 20_000 })

    await page.locator('.doc-editor-shell > input[type="file"]:not([accept])').setInputFiles({
      name: 'm9-notes.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('M9 file content'),
    })
    await expect(page.getByText(/上传完成：m9-notes\.txt/)).toBeVisible({ timeout: 20_000 })
    const fileCard = page.locator('.doc-file-card-node:not(.image)').last()
    const uploadedFileId = await fileCard.getAttribute('data-file-id')
    expect(uploadedFileId).toBeTruthy()
    const stableFileCard = page.locator(`.doc-file-card-node[data-file-id="${uploadedFileId}"]`)
    const downloadButton = stableFileCard.locator('button').first()
    await expect(downloadButton).toBeVisible()
    const signedDownload = await request.get(`${apiBaseUrl}/files/${uploadedFileId}/download-url`, { headers: bearer(administrator) })
    expect(signedDownload.ok()).toBeTruthy()
    expect((await signedDownload.json()).downloadUrl).toMatch(/X-Amz-|9000/)
    await page.evaluate(() => {
      const target = window as typeof window & { __m9OpenedUrls?: string[] }
      target.__m9OpenedUrls = []
      window.open = ((url?: string | URL) => {
        target.__m9OpenedUrls?.push(String(url ?? ''))
        return null
      }) as typeof window.open
    })
    await downloadButton.dispatchEvent('click')
    await expect.poll(() => page.evaluate(() => (window as typeof window & { __m9OpenedUrls?: string[] }).__m9OpenedUrls?.[0] ?? '')).toMatch(/X-Amz-|9000/)
    await stableFileCard.getByLabel('文件说明').fill('M9 file caption')

    await alpha.click()
    await page.keyboard.press('Home')
    await page.keyboard.down('Shift')
    for (let index = 0; index < 5; index += 1) await page.keyboard.press('ArrowRight')
    await page.keyboard.up('Shift')
    await Promise.all([
      page.waitForEvent('dialog').then((dialog) => dialog.accept('https://example.com/m9-link')),
      selectionToolbar.getByRole('button', { name: '链接' }).click(),
    ])
    await expect(alpha.locator('a')).toHaveAttribute('href', 'https://example.com/m9-link')

    await appendParagraph(editor)
    await runSlashCommand(editor, slashSearch, '任务')
    await expect(editor.locator('ul[data-type="taskList"]')).toBeVisible()
    await editor.press('Enter')
    await runSlashCommand(editor, slashSearch, '引用')
    await expect(editor.locator('blockquote').last()).toBeVisible()
    await editor.press('Enter')
    await runSlashCommand(editor, slashSearch, '代码')
    await expect(editor.locator('pre')).toBeVisible()
    await editor.press('Control+Enter')
    await runSlashCommand(editor, slashSearch, '分割线')
    await expect(editor.locator('hr')).toBeVisible()

    await expect.poll(async () => {
      const detail = await getKnowledgeContent(request, administrator, space.id, shortItem.item.id)
      return detail.blocks.some((block) => block.blockType === 'callout')
        && detail.blocks.some((block) => block.blockType === 'file_embed')
        && detail.blocks.some((block) => block.blockType === 'table')
    }, { timeout: 30_000 }).toBeTruthy()
    await page.reload()
    await expect(page.locator('.doc-callout')).toBeVisible()
    await expect(page.getByLabel('图片说明')).toHaveValue('M9 image caption')
    await expect(editor).toContainText('Plain pasted text')

    const desktopLayout = await page.evaluate(() => ({
      horizontalOverflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
      canvasOverflow: document.querySelector('.doc-editor-canvas')!.scrollWidth - document.querySelector('.doc-editor-canvas')!.clientWidth,
    }))
    expect(desktopLayout.horizontalOverflow).toBeLessThanOrEqual(1)
    expect(desktopLayout.canvasOverflow).toBeLessThanOrEqual(1)
    await page.setViewportSize({ width: 560, height: 760 })
    await expect(editor).toBeVisible()
    const narrowLayout = await page.evaluate(() => ({
      horizontalOverflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
      editorWidth: document.querySelector('.doc-editor-canvas')!.getBoundingClientRect().width,
    }))
    expect(narrowLayout.horizontalOverflow).toBeLessThanOrEqual(1)
    expect(narrowLayout.editorWidth).toBeLessThanOrEqual(560)

    await page.goto(knowledgeContentUrl(space.id, longItem.item.id))
    await expect(page.getByRole('textbox', { name: '知识内容正文编辑器' })).toContainText('M9 long block 80')

    const viewer = await createMember(request, administrator, 'm9-viewer')
    viewerId = viewer.id
    await grantResourcePermission(request, administrator, 'knowledge_base', space.id, viewer.id, 'view')
    await grantResourcePermission(request, administrator, 'knowledge_content', space.rootItemId, viewer.id, 'view')
    await grantResourcePermission(request, administrator, 'knowledge_content', shortItem.item.id, viewer.id, 'view')
    const viewerSession = await loginByApi(request, viewer.username, viewer.password)
    viewerContext = await browser.newContext()
    const viewerPage = await viewerContext.newPage()
    await installSession(viewerPage, viewerSession)
    await viewerPage.goto(knowledgeContentUrl(space.id, shortItem.item.id))
    await expect(viewerPage.getByText('当前为只读模式')).toBeVisible()
    await expect(viewerPage.getByRole('textbox', { name: '知识内容正文编辑器' })).toHaveAttribute('contenteditable', 'false')
    await expect(viewerPage.getByLabel('图片说明')).toBeDisabled()
    const viewerMediaButtonTexts = () => viewerPage.locator('.doc-file-card-node button').allTextContents()
      .then((texts) => texts.map((text) => text.replace(/\s/g, '')))
    await expect.poll(viewerMediaButtonTexts).toContain('下载')
    expect(await viewerMediaButtonTexts()).not.toContain('替换')
    await expect(viewerPage.getByRole('button', { name: '插入块' })).toHaveCount(0)
  } finally {
    await viewerContext?.close()
    if (viewerId) await request.post(`${apiBaseUrl}/admin/users/${viewerId}/offboard`, { headers: bearer(administrator), data: {} })
    await archiveKnowledgeSpaceFixture(request, administrator, space)
  }
})

function paragraphWithText(page: Page, text: string) {
  return page.locator('.doc-prosemirror p').filter({ hasText: text }).first()
}

async function appendParagraph(editor: Locator) {
  await editor.click()
  await editor.press('Control+End')
  await editor.press('Enter')
}

async function dispatchPaste(editor: Locator, data: { text: string; html?: string }) {
  await editor.evaluate((element, payload) => {
    const transfer = new DataTransfer()
    transfer.setData('text/plain', payload.text)
    if (payload.html) transfer.setData('text/html', payload.html)
    element.dispatchEvent(new ClipboardEvent('paste', { bubbles: true, cancelable: true, clipboardData: transfer }))
  }, data)
}

async function runSlashCommand(editor: Locator, search: Locator, command: string) {
  await editor.press('/')
  await search.fill(command)
  await search.press('Enter')
}

async function expectInside(container: Locator, target: Locator) {
  const containerBox = await container.boundingBox()
  const targetBox = await target.boundingBox()
  expect(containerBox).not.toBeNull()
  expect(targetBox).not.toBeNull()
  expect(targetBox!.x).toBeGreaterThanOrEqual(containerBox!.x - 1)
  expect(targetBox!.x + targetBox!.width).toBeLessThanOrEqual(containerBox!.x + containerBox!.width + 1)
  expect(targetBox!.y).toBeGreaterThanOrEqual(containerBox!.y - 1)
  expect(targetBox!.y + targetBox!.height).toBeLessThanOrEqual(containerBox!.y + containerBox!.height + 1)
}

async function createMember(request: APIRequestContext, session: E2eSession, prefix: string) {
  const username = `${prefix.replace(/[^a-z0-9]/g, '')}${Math.random().toString(36).slice(2, 9)}`
  const password = 'member123456'
  const response = await request.post(`${apiBaseUrl}/admin/users`, {
    headers: bearer(session),
    data: { username, password, displayName: prefix, email: `${username}@example.com`, roleCode: 'member' },
  })
  expect(response.ok()).toBeTruthy()
  const payload = await response.json()
  return { id: payload.id as string, username, password }
}

async function grantResourcePermission(
  request: APIRequestContext,
  session: E2eSession,
  resourceType: 'knowledge_base' | 'knowledge_content',
  resourceId: string,
  userId: string,
  permissionLevel: 'view' | 'edit',
) {
  const response = await request.post(`${apiBaseUrl}/resource-permissions/${resourceType}/${resourceId}`, {
    headers: bearer(session),
    data: { subjectType: 'user', subjectId: userId, permissionLevel, confirmHighRisk: true },
  })
  expect(response.ok()).toBeTruthy()
}
