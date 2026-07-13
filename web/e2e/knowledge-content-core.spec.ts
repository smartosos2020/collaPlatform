import { expect, test } from '@playwright/test'
import { randomUUID } from 'node:crypto'

import { installSession, loginByApi } from './support/api'
import { archiveKnowledgeSpaceFixture, createKnowledgeSpaceFixture } from './support/fixtures'
import {
  addKnowledgeComment,
  createKnowledgeItem,
  createNamedKnowledgeVersion,
  getKnowledgeContent,
  knowledgeContentUrl,
  listKnowledgeVersions,
  replyToKnowledgeComment,
  restoreKnowledgeVersion,
  saveKnowledgeBlocks,
  saveKnowledgeContent,
  setKnowledgeCommentResolution,
} from './support/knowledge'

test('@smoke knowledge content core: tree navigation, autosave, blocks, versions and comments', async ({ page, request }) => {
  const session = await loginByApi(request)
  const space = await createKnowledgeSpaceFixture(request, session, 'content-core')

  try {
    const folder = await createKnowledgeItem(request, session, space, {
      title: 'M3 Content Folder',
      contentType: 'folder',
    })
    const document = await createKnowledgeItem(request, session, space, {
      parentId: folder.item.id,
      title: 'M3 Content Entry',
      contentType: 'markdown',
      content: '# Initial M3 content',
    })
    const linkedDocument = await createKnowledgeItem(request, session, space, {
      parentId: folder.item.id,
      title: 'M3 Linked Knowledge Content',
      contentType: 'markdown',
      content: 'Linked object target',
    })
    const itemId = document.item.id
    const autosavedTitle = 'M3 Autosaved Title'

    await installSession(page, session)
    await page.goto(knowledgeContentUrl(space.id, itemId))
    await expect(page.getByText('知识内容树')).toBeVisible()
    await expect(page.getByText('M3 Content Folder').first()).toBeVisible()
    await expect(page.getByLabel('知识内容标题')).toHaveValue('M3 Content Entry')

    await page.getByLabel('知识内容标题').fill(autosavedTitle)
    await expect.poll(async () => (await getKnowledgeContent(request, session, space.id, itemId)).item.title, {
      timeout: 15_000,
      message: 'collaboration autosave should persist the changed title',
    }).toBe(autosavedTitle)

    await page.reload()
    await expect(page.getByLabel('知识内容标题')).toHaveValue(autosavedTitle)

    const beforeBlocks = await getKnowledgeContent(request, session, space.id, itemId)
    const afterBlocks = await saveKnowledgeBlocks(request, session, space.id, itemId, beforeBlocks.item.currentVersionNo, [
      { blockType: 'heading', content: 'M3 Structured Heading', sortOrder: 0 },
      { blockType: 'paragraph', content: 'M3 structured paragraph survives refresh.', sortOrder: 1 },
      {
        blockType: 'embed_object',
        content: JSON.stringify({ objectType: 'knowledge_content', objectId: linkedDocument.item.id }),
        sortOrder: 2,
      },
      {
        blockType: 'embed_object',
        content: JSON.stringify({ objectType: 'base', objectId: randomUUID() }),
        sortOrder: 3,
      },
    ])
    expect(afterBlocks.blocks.map((block) => block.content)).toEqual(expect.arrayContaining([
      'M3 Structured Heading',
      'M3 structured paragraph survives refresh.',
    ]))
    expect(afterBlocks.blocks.find((block) => block.embedSummary?.objectType === 'knowledge_content')?.embedSummary?.accessState).toBe('available')
    expect(afterBlocks.blocks.find((block) => block.embedSummary?.objectType === 'base')?.embedSummary?.accessState).toBe('not_found')

    await page.reload()
    await expect(page.getByText('M3 Structured Heading').first()).toBeVisible()
    await expect(page.getByText('M3 structured paragraph survives refresh.').first()).toBeVisible()
    await expect(page.getByText('M3 Linked Knowledge Content').first()).toBeVisible()
    await expect(page.getByText('对象不可访问')).toBeVisible()

    const namedVersion = await createNamedKnowledgeVersion(request, session, space.id, itemId, 'M3 stable revision')
    const namedVersionNo = namedVersion.item.currentVersionNo
    const changed = await saveKnowledgeContent(request, session, space.id, itemId, {
      baseVersionNo: namedVersionNo,
      title: 'M3 temporary revision',
      content: 'Temporary version content',
    })
    expect(changed.item.currentVersionNo).toBeGreaterThan(namedVersionNo)
    const restored = await restoreKnowledgeVersion(request, session, space.id, itemId, namedVersionNo)
    expect(restored.item.title).toBe(autosavedTitle)
    expect(restored.item.currentVersionNo).toBeGreaterThan(changed.item.currentVersionNo)
    const versions = await listKnowledgeVersions(request, session, space.id, itemId)
    expect(versions.some((version) => version.versionNo === namedVersionNo && version.versionName === 'M3 stable revision')).toBeTruthy()

    const commented = await addKnowledgeComment(request, session, space.id, itemId, {
      content: '@admin M3 comment trace',
      blockId: restored.blocks[0].id,
    })
    const comment = commented.comments.find((candidate) => candidate.content === '@admin M3 comment trace')
    expect(comment).toBeDefined()
    const replied = await replyToKnowledgeComment(request, session, space.id, itemId, comment!.id, 'M3 comment reply')
    expect(replied.comments.find((candidate) => candidate.id === comment!.id)?.replies.map((reply) => reply.content)).toContain('M3 comment reply')
    const resolved = await setKnowledgeCommentResolution(request, session, space.id, itemId, comment!.id, true)
    expect(resolved.comments.find((candidate) => candidate.id === comment!.id)?.resolved).toBeTruthy()
    const reopened = await setKnowledgeCommentResolution(request, session, space.id, itemId, comment!.id, false)
    expect(reopened.comments.find((candidate) => candidate.id === comment!.id)?.resolved).toBeFalsy()
    const auditResponse = await request.get(`${process.env.COLLA_E2E_API_BASE_URL ?? 'http://127.0.0.1:8080/api'}/admin/audit-logs`, {
      headers: { Authorization: `Bearer ${session.accessToken}` },
      params: { action: 'knowledge.content.comment.added', targetType: 'knowledge_content', targetId: itemId },
    })
    expect(auditResponse.ok()).toBeTruthy()
    const auditEntries = await auditResponse.json() as Array<{ action: string; targetId: string }>
    expect(auditEntries.some((entry) => entry.action === 'knowledge.content.comment.added' && entry.targetId === itemId)).toBeTruthy()
  } finally {
    await archiveKnowledgeSpaceFixture(request, session, space)
  }
})
