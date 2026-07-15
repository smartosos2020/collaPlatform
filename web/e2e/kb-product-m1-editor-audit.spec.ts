import { expect, test, type Locator } from '@playwright/test'
import { randomUUID } from 'node:crypto'

import { installSession, loginByApi } from './support/api'
import { archiveKnowledgeSpaceFixture, createKnowledgeSpaceFixture } from './support/fixtures'
import {
  createKnowledgeItem,
  getKnowledgeContent,
  knowledgeContentUrl,
  saveKnowledgeBlocks,
} from './support/knowledge'

test('@smoke @kb-product-m1 editor interaction baseline is observable in a real browser', async ({ page, request }, testInfo) => {
  const session = await loginByApi(request)
  const space = await createKnowledgeSpaceFixture(request, session, 'kb-product-m1-editor-audit')

  try {
    const document = await createKnowledgeItem(request, session, space, {
      title: 'KB Product M1 Editor Audit',
      contentType: 'markdown',
      content: 'Initial audit content',
    })
    const before = await getKnowledgeContent(request, session, space.id, document.item.id)
    await saveKnowledgeBlocks(request, session, space.id, document.item.id, before.item.currentVersionNo, [
      { blockType: 'heading', content: 'M1 editor interaction audit', sortOrder: 0 },
      { blockType: 'paragraph', content: 'Continuous input marker', sortOrder: 1 },
      {
        blockType: 'table',
        content: JSON.stringify({ columns: ['Owner', 'Status'], rows: [['Alice', 'Ready']] }),
        sortOrder: 2,
      },
      {
        blockType: 'embed_object',
        content: JSON.stringify({ objectType: 'base', objectId: randomUUID() }),
        sortOrder: 3,
      },
    ])

    await installSession(page, session)
    await page.goto(knowledgeContentUrl(space.id, document.item.id))

    const editor = page.getByRole('textbox', { name: '知识内容正文编辑器' })
    await expect(editor).toBeVisible()
    await expect(page.getByText('块编辑器', { exact: true })).toBeVisible()
    const unavailableObject = page.getByText(/对象不可(用|访问)/)
    await expect(unavailableObject).toBeVisible()

    const paragraph = page.locator('.doc-prosemirror p').filter({ hasText: 'Continuous input marker' }).first()
    await paragraph.click()
    await page.keyboard.press('End')
    for (const character of ' ABC') {
      await page.keyboard.type(character)
      await expect(editor).toBeFocused()
    }
    const observedContinuousInput = await paragraph.innerText()
    expect(observedContinuousInput).toContain('Continuous input marker')
    await testInfo.attach('continuous-input-observation', {
      body: Buffer.from(JSON.stringify({ expectedSuffix: ' ABC', observedContinuousInput }, null, 2)),
      contentType: 'application/json',
    })

    await paragraph.hover()
    const canvas = page.locator('.doc-editor-canvas')
    const insertButton = page.getByRole('button', { name: '插入块' })
    const operationButton = page.getByRole('button', { name: '操作块' })
    await expect(insertButton).toBeVisible()
    await expect(operationButton).toBeVisible()
    await expectControlInsideCanvas(canvas, insertButton)
    await expectControlInsideCanvas(canvas, operationButton)

    await insertButton.click()
    const slashMenu = page.locator('.doc-slash-menu')
    await expect(slashMenu).toBeVisible()
    await expectControlInsideCanvas(canvas, slashMenu)
    await page.getByLabel('知识内容标题').click()
    await expect(slashMenu).toBeHidden()

    const table = page.locator('.doc-prosemirror table').first()
    await table.locator('td').first().click()
    const tableToolbar = page.locator('.doc-table-editor-toolbar')
    await expect(tableToolbar).toBeVisible()
    const tableBox = await table.boundingBox()
    const toolbarBox = await tableToolbar.boundingBox()
    expect(tableBox).not.toBeNull()
    expect(toolbarBox).not.toBeNull()
    expect(Math.abs(toolbarBox!.y + toolbarBox!.height - tableBox!.y)).toBeLessThan(96)

    await testInfo.attach('object-card-after-edit-observation', {
      body: Buffer.from(JSON.stringify({ unavailableObjectCount: await unavailableObject.count() }, null, 2)),
      contentType: 'application/json',
    })
    await testInfo.attach('kb-product-m1-editor-audit', {
      body: await page.screenshot({ fullPage: true }),
      contentType: 'image/png',
    })
  } finally {
    await archiveKnowledgeSpaceFixture(request, session, space)
  }
})

async function expectControlInsideCanvas(canvas: Locator, control: Locator) {
  const canvasBox = await canvas.boundingBox()
  const controlBox = await control.boundingBox()
  expect(canvasBox).not.toBeNull()
  expect(controlBox).not.toBeNull()
  expect(controlBox!.x).toBeGreaterThanOrEqual(canvasBox!.x)
  expect(controlBox!.x + controlBox!.width).toBeLessThanOrEqual(canvasBox!.x + canvasBox!.width + 1)
}
