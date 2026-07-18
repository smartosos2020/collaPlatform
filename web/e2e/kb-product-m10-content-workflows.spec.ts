import { expect, test } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, webBaseUrl } from './support/api'
import { archiveKnowledgeSpaceFixture, createKnowledgeSpaceFixture, uniqueFixtureName } from './support/fixtures'
import { createKnowledgeItem, getKnowledgeContent, knowledgeContentUrl, restoreKnowledgeVersion, saveKnowledgeBlocks } from './support/knowledge'

test('@smoke @kb-product-m10 comments, search, relations and transfer workflows remain block-aware', async ({ browser, context, page, request }) => {
  test.setTimeout(300_000)
  const administrator = await loginByApi(request)
  const space = await createKnowledgeSpaceFixture(request, administrator, 'kb-product-m10-content')
  const secondContext = await browser.newContext({ baseURL: webBaseUrl })

  try {
    const source = await createKnowledgeItem(request, administrator, space, {
      title: uniqueFixtureName('m10-anchor-search'),
      contentType: 'markdown',
      content: 'Prefix Anchor target text Suffix',
    })
    const target = await createKnowledgeItem(request, administrator, space, {
      title: uniqueFixtureName('m10-related-target'),
      contentType: 'markdown',
      content: 'Related target body',
    })
    const sourceBlock = source.blocks[0]
    const itemPath = `${apiBaseUrl}/knowledge-bases/${space.id}/items/${source.item.id}`

    const commentResponse = await request.post(`${itemPath}/comments`, {
      headers: bearer(administrator),
      data: {
        blockId: sourceBlock.id,
        anchorType: 'selection',
        anchorStart: 7,
        anchorEnd: 25,
        anchorText: 'Anchor target text',
        anchorPrefix: 'Prefix ',
        anchorSuffix: ' Suffix',
        content: 'M10 browser anchor comment',
      },
    })
    expect(commentResponse.ok()).toBeTruthy()
    const commentDetail = await commentResponse.json() as { comments: Array<{ id: string; anchorState: string }> }
    const commentId = commentDetail.comments[0].id

    await installSession(page, administrator)
    await page.goto(knowledgeContentUrl(space.id, source.item.id))
    await expect(page.getByText('M10 browser anchor comment')).toBeVisible()
    await expect(page.getByText('实时已同步')).toBeVisible()

    const paragraph = page.locator('.doc-prosemirror p').filter({ hasText: 'Prefix Anchor target text Suffix' })
    await paragraph.click()
    await page.keyboard.press('Home')
    await page.keyboard.type('Remote ')
    await expect.poll(async () => {
      const detail = await getKnowledgeContent(request, administrator, space.id, source.item.id) as unknown as {
        comments: Array<{ id: string; anchorStart: number; anchorState: string }>
      }
      return detail.comments.find((comment) => comment.id === commentId)?.anchorStart
    }, { timeout: 25_000 }).toBe(14)

    await page.reload()
    await expect(page.getByText('M10 browser anchor comment')).toBeVisible()
    await expect(page.getByText('锚点已失效')).toHaveCount(0)

    const latest = await getKnowledgeContent(request, administrator, space.id, source.item.id)
    const latestBlock = latest.blocks.find((block) => block.id === sourceBlock.id)
    expect(latestBlock).toBeTruthy()
    const deleted = await request.delete(`${itemPath}/blocks/${sourceBlock.id}`, {
      headers: bearer(administrator),
      params: { baseVersionNo: latest.item.currentVersionNo },
    })
    expect(deleted.ok()).toBeTruthy()
    const deletedDetail = await deleted.json() as { comments: Array<{ id: string; anchorState: string }> }
    expect(deletedDetail.comments.find((comment) => comment.id === commentId)?.anchorState).toBe('detached')
    await page.reload()
    await expect(page.getByText('锚点已失效')).toBeVisible()
    await expect(page.getByText('M10 browser anchor comment')).toBeVisible()

    const restored = await restoreKnowledgeVersion(request, administrator, space.id, source.item.id, latest.item.currentVersionNo)
    expect(restored.comments[0].resolved).toBeFalsy()
    await page.reload()
    await expect(page.getByText('锚点已失效')).toHaveCount(0)

    const addRelation = await request.post(`${itemPath}/relations`, {
      headers: bearer(administrator),
      data: { targetType: 'knowledge_content', targetId: target.item.id },
    })
    expect(addRelation.ok()).toBeTruthy()
    const reverse = await getKnowledgeContent(request, administrator, space.id, target.item.id) as unknown as {
      relations: Array<{ targetId: string }>
    }
    expect(reverse.relations.some((relation) => relation.targetId === source.item.id)).toBeTruthy()
    await page.reload()
    const managementDetails = page.locator('details.doc-management-details')
    await managementDetails.locator('summary').click()
    await expect(managementDetails).toHaveJSProperty('open', true)
    const removeRelationButton = managementDetails.getByRole('button', { name: /^移除关联 / })
    await expect(removeRelationButton).toBeVisible({ timeout: 10_000 })
    await removeRelationButton.click()
    const confirmRemoveButton = page.locator('.ant-popconfirm-buttons .ant-btn-primary')
    await expect(confirmRemoveButton).toBeVisible({ timeout: 10_000 })
    await confirmRemoveButton.click()
    await expect(page.getByText('关联已移除')).toBeVisible({ timeout: 10_000 })
    await expect(removeRelationButton).toHaveCount(0, { timeout: 10_000 })
    await expect.poll(async () => {
      const updatedReverse = await getKnowledgeContent(request, administrator, space.id, target.item.id) as unknown as {
        relations: Array<{ targetId: string }>
      }
      return updatedReverse.relations.some((relation) => relation.targetId === source.item.id)
    }).toBeFalsy()

    const markdown = '# M10 transfer\n| Name | State |\n| --- | --- |\n| Knowledge | Ready |\n```ts\nconst ready = true\n```\n![Diagram](https://example.com/diagram.png)'
    const preview = await request.post(`${itemPath}/import/preview`, {
      headers: bearer(administrator),
      data: { format: 'markdown', source: markdown },
    })
    expect(preview.ok()).toBeTruthy()
    const previewBody = await preview.json() as { convertedFeatures: string[]; safeToApply: boolean }
    expect(previewBody.convertedFeatures).toEqual(expect.arrayContaining(['table', 'code_block', 'image']))
    expect(previewBody.safeToApply).toBeTruthy()
    const oversized = await request.post(`${itemPath}/import/preview`, {
      headers: bearer(administrator),
      data: { format: 'markdown', source: 'x'.repeat(1_000_001) },
    })
    expect([403, 413]).toContain(oversized.status())

    // Close the content page to end the collaboration session before the search flow.
    await page.close()

    const searchSeed = await getKnowledgeContent(request, administrator, space.id, source.item.id)
    const savedBlocks = await saveKnowledgeBlocks(request, administrator, space.id, source.item.id, searchSeed.item.currentVersionNo, [
      { id: searchSeed.blocks[0].id, blockType: 'paragraph', content: '中文搜索定位 M10 unique phrase', sortOrder: 0 },
    ])
    expect(savedBlocks.blocks.some((block) => block.content.includes('unique phrase'))).toBeTruthy()
    const reindex = await request.post(`${process.env.COLLA_E2E_API_BASE_URL ?? 'http://localhost:8080/api'}/admin/search-governance/reindex`, {
      headers: bearer(administrator),
    })
    expect(reindex.ok()).toBeTruthy()
    const searchApiResult = await request.get(`${process.env.COLLA_E2E_API_BASE_URL ?? 'http://localhost:8080/api'}/search?q=unique%20phrase&limit=20`, {
      headers: bearer(administrator),
    })
    expect(searchApiResult.ok()).toBeTruthy()
    const searchApiBody = await searchApiResult.json() as { items: Array<{ objectId: string }> }
    expect(searchApiBody.items.some((item) => item.objectId === source.item.id)).toBeTruthy()
  } finally {
    await Promise.race([secondContext.close(), new Promise((resolve) => setTimeout(resolve, 10_000))])
    await archiveKnowledgeSpaceFixture(request, administrator, space)
    await context.clearCookies()
  }
})
