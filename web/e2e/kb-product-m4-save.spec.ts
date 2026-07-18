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

    await installSession(page, session)
    await page.goto(knowledgeContentUrl(space.id, first.item.id))
    await expect(page.getByLabel('知识内容标题')).toHaveValue('M4 save closure entry')
    await expect(page.getByText('实时已同步')).toBeVisible()

    const editor = page.locator('.doc-prosemirror[role="textbox"]')
    await expect(editor).toBeVisible()

    await editor.click()
    await editor.pressSequentially(' M4 continuous input')
    await expect(editor).toContainText('M4 continuous input')
    await expect.poll(async () => (await getKnowledgeContent(request, session, space.id, first.item.id)).blocks.some((block) => block.content.includes('M4 continuous input')), {
      timeout: 15_000,
      message: 'continuous block input should be persisted by the debounced autosave',
    }).toBeTruthy()

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
    await expect(page.getByText('实时已同步')).toBeVisible()

    // In collaboration mode, Yjs handles concurrent title edits automatically.
    // Verify that a REST save with a stale base version is rejected (409 conflict detection still works).
    const conflictBase = await getKnowledgeContent(request, session, space.id, first.item.id)
    const staleSaveResponse = await request.patch(
      `${process.env.COLLA_E2E_API_BASE_URL ?? 'http://localhost:8080/api'}/knowledge-bases/${space.id}/items/${first.item.id}`,
      {
        headers: { Authorization: `Bearer ${session.accessToken}`, 'Content-Type': 'application/json' },
        data: { baseVersionNo: conflictBase.item.currentVersionNo - 1, title: 'M4 stale write', content: conflictBase.content },
      }
    )
    expect(staleSaveResponse.status()).toBe(409)

    // Offline draft recovery: title changes are queued locally and can be retried.
    await page.context().setOffline(true)
    await page.getByLabel('知识内容标题').fill('M4 offline draft')
    await expect(page.getByText(/当前处于离线状态/)).toBeVisible({ timeout: 15_000 })
    await page.context().setOffline(false)
    await expect.poll(async () => (await getKnowledgeContent(request, session, space.id, first.item.id)).item.title, {
      timeout: 15_000,
      message: 'offline local draft should be saved after reconnect',
    }).toBe('M4 offline draft')
  } finally {
    await archiveKnowledgeSpaceFixture(request, session, space)
  }
})
