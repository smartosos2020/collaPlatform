import { expect, test } from '@playwright/test'

import { installSession, loginByApi } from './support/api'
import { archiveKnowledgeSpaceFixture, createKnowledgeSpaceFixture } from './support/fixtures'
import {
  createKnowledgeItem,
  diffKnowledgeVersions,
  getKnowledgeContent,
  knowledgeContentUrl,
  listKnowledgeVersions,
  restoreKnowledgeVersion,
  saveKnowledgeBlocks,
  saveKnowledgeContent,
} from './support/knowledge'

test('@smoke KB-PRODUCT-M4 autosave, conflict, recovery and version closure', async ({ page, request }) => {
  const session = await loginByApi(request)
  const space = await createKnowledgeSpaceFixture(request, session, 'product-m4-save')

  try {
    const first = await createKnowledgeItem(request, session, space, {
      title: 'M4 save closure entry',
      contentType: 'markdown',
      content: 'M4 initial content',
    })
    const second = await createKnowledgeItem(request, session, space, {
      title: 'M4 second entry',
      contentType: 'markdown',
      content: 'M4 second content',
    })

    await installSession(page, session)
    await page.goto(knowledgeContentUrl(space.id, first.item.id))
    await expect(page.getByLabel('知识内容标题')).toHaveValue('M4 save closure entry')
    await expect(page.getByText('未修改', { exact: true })).toBeVisible()

    const editor = page.locator('.doc-prosemirror[role="textbox"]')
    await expect(editor).toBeVisible()
    const blockSaveRequests: Array<{ mode?: string; postData: string | null }> = []
    page.on('request', (requestEvent) => {
      if (requestEvent.method() === 'PATCH' && requestEvent.url().endsWith(`/knowledge-bases/${space.id}/items/${first.item.id}/blocks`)) {
        blockSaveRequests.push({ mode: requestEvent.postDataJSON()?.saveMode, postData: requestEvent.postData() })
      }
    })

    await editor.click()
    await editor.pressSequentially(' M4 continuous input')
    await expect(editor).toContainText('M4 continuous input')
    await expect.poll(async () => (await getKnowledgeContent(request, session, space.id, first.item.id)).blocks.some((block) => block.content.includes('M4 continuous input')), {
      timeout: 15_000,
      message: 'continuous block input should be persisted by the debounced autosave',
    }).toBeTruthy()
    expect(blockSaveRequests.some((entry) => entry.mode === 'auto')).toBeTruthy()

    const autoBase = await getKnowledgeContent(request, session, space.id, first.item.id)
    const stableBlockId = autoBase.blocks[0]?.id
    const autoOne = await saveKnowledgeBlocks(request, session, space.id, first.item.id, autoBase.item.currentVersionNo, [
      { id: stableBlockId, blockType: 'paragraph', content: 'M4 auto checkpoint candidate A', sortOrder: 0 },
    ], 'auto')
    const versionsAfterFirstAuto = await listKnowledgeVersions(request, session, space.id, first.item.id)
    await saveKnowledgeBlocks(request, session, space.id, first.item.id, autoOne.item.currentVersionNo, [
      { id: stableBlockId, blockType: 'paragraph', content: 'M4 auto checkpoint candidate B', sortOrder: 0 },
    ], 'auto')
    const versionsAfterSecondAuto = await listKnowledgeVersions(request, session, space.id, first.item.id)
    expect(versionsAfterSecondAuto.length).toBe(versionsAfterFirstAuto.length)

    const manualCheckpoint = await saveKnowledgeBlocks(request, session, space.id, first.item.id, autoOne.item.currentVersionNo + 1, [
      { id: stableBlockId, blockType: 'paragraph', content: 'M4 manual checkpoint', sortOrder: 0 },
    ], 'manual')
    const versionsAfterManual = await listKnowledgeVersions(request, session, space.id, first.item.id)
    expect(versionsAfterManual.length).toBeGreaterThan(versionsAfterSecondAuto.length)
    expect(versionsAfterManual.some((version) => version.versionNo === manualCheckpoint.item.currentVersionNo && version.versionType === 'manual_checkpoint')).toBeTruthy()

    const latestManual = await saveKnowledgeBlocks(request, session, space.id, first.item.id, manualCheckpoint.item.currentVersionNo, [
      { id: stableBlockId, blockType: 'paragraph', content: 'M4 latest manual revision', sortOrder: 0 },
    ], 'manual')
    const versionsForDiff = [...await listKnowledgeVersions(request, session, space.id, first.item.id)]
      .sort((left, right) => left.versionNo - right.versionNo)
    const latestVersionIndex = versionsForDiff.findIndex((version) => version.versionNo === latestManual.item.currentVersionNo)
    const previousVersionNo = versionsForDiff[latestVersionIndex - 1]?.versionNo
    expect(previousVersionNo).toBeDefined()
    const diff = await diffKnowledgeVersions(request, session, space.id, first.item.id, previousVersionNo!, latestManual.item.currentVersionNo)
    expect(diff.lines.some((line) => line.type === 'modified' && line.blockId)).toBeTruthy()
    const restored = await restoreKnowledgeVersion(request, session, space.id, first.item.id, manualCheckpoint.item.currentVersionNo)
    expect(restored.item.currentVersionNo).toBeGreaterThan(latestManual.item.currentVersionNo)
    expect(restored.blocks.find((block) => block.id === stableBlockId)?.content).toBe('M4 manual checkpoint')
    const versionsAfterRestore = await listKnowledgeVersions(request, session, space.id, first.item.id)
    expect(versionsAfterRestore.some((version) => version.versionNo === manualCheckpoint.item.currentVersionNo)).toBeTruthy()

    await page.reload()
    await expect(page.getByLabel('知识内容标题')).toHaveValue('M4 save closure entry')
    const conflictBase = await getKnowledgeContent(request, session, space.id, first.item.id)
    await page.getByLabel('知识内容标题').fill('M4 local conflict draft')
    await saveKnowledgeContent(request, session, space.id, first.item.id, {
      baseVersionNo: conflictBase.item.currentVersionNo,
      title: 'M4 remote conflict revision',
      content: conflictBase.content,
    })
    await expect(page.getByText('版本冲突', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '保留本地草稿', exact: true }).click()
    await expect(page.getByRole('dialog').locator('textarea')).toHaveValue(/M4 local conflict draft/)
    await page.keyboard.press('Escape')
    await page.getByRole('button', { name: '刷新远端', exact: true }).click()
    await expect(page.getByLabel('知识内容标题')).toHaveValue('M4 remote conflict revision')

    await page.getByLabel('知识内容标题').fill('M4 navigation recovery draft')
    await page.getByText('M4 second entry', { exact: true }).first().click()
    await expect(page.locator('.ant-modal-confirm-title:visible').filter({ hasText: '存在未保存内容' })).toBeVisible()
    await page.locator('.ant-modal:visible').getByRole('button', { name: '保留并切换', exact: true }).click()
    await expect(page).toHaveURL(new RegExp(`${space.id}/items/${second.item.id}$`))
    await page.getByText('M4 remote conflict revision', { exact: true }).first().click()
    await expect(page.getByText(/发现 .* 保存的本地草稿/)).toBeVisible()
    await page.getByRole('button', { name: '恢复草稿', exact: true }).click()
    await expect(page.getByLabel('知识内容标题')).toHaveValue('M4 navigation recovery draft')
    await expect(page.getByText('保存中', { exact: true })).toHaveCount(0, { timeout: 15_000 })

    await page.context().setOffline(true)
    await page.getByLabel('知识内容标题').fill('M4 offline draft')
    await expect(page.getByText('当前网络不可用', { exact: true })).toBeVisible({ timeout: 15_000 })
    await expect(page.getByText('本地草稿已保留。恢复网络或修复问题后可以重试保存。', { exact: true })).toBeVisible()
    await page.context().setOffline(false)
    await page.getByRole('button', { name: '重试保存', exact: true }).first().click()
    await expect.poll(async () => (await getKnowledgeContent(request, session, space.id, first.item.id)).item.title, {
      timeout: 15_000,
      message: 'offline local draft should be saved after retry',
    }).toBe('M4 offline draft')
  } finally {
    await archiveKnowledgeSpaceFixture(request, session, space)
  }
})
