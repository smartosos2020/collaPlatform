import { expect, test } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, webBaseUrl } from './support/api'
import { archiveKnowledgeSpaceFixture, createKnowledgeSpaceFixture, uniqueFixtureName } from './support/fixtures'
import { createKnowledgeItem, getKnowledgeContent, knowledgeContentUrl, restoreKnowledgeVersion, saveKnowledgeBlocks } from './support/knowledge'

test('@smoke @kb-product-m10 comments, search, relations and transfer workflows remain block-aware', async ({ browser, context, page, request }) => {
  test.setTimeout(240_000)
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

    const secondPage = await secondContext.newPage()
    await installSession(secondPage, administrator)
    await secondPage.goto(knowledgeContentUrl(space.id, source.item.id))
    const paragraph = secondPage.locator('.doc-prosemirror p').filter({ hasText: 'Prefix Anchor target text Suffix' })
    await paragraph.click()
    await secondPage.keyboard.press('Home')
    await secondPage.keyboard.type('Remote ')
    await expect.poll(async () => {
      const detail = await getKnowledgeContent(request, administrator, space.id, source.item.id) as unknown as {
        comments: Array<{ id: string; anchorStart: number; anchorState: string }>
      }
      return detail.comments.find((comment) => comment.id === commentId)?.anchorStart
    }, { timeout: 25_000 }).toBe(14)

    await page.reload()
    await expect(page.getByText('M10 browser anchor comment')).toBeVisible()
    await expect(page.getByText('锚点已失效')).toHaveCount(0)
    await secondPage.close()

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

    await page.goto('/search')
    const searchSeed = await getKnowledgeContent(request, administrator, space.id, source.item.id)
    await saveKnowledgeBlocks(request, administrator, space.id, source.item.id, searchSeed.item.currentVersionNo, [
      { id: searchSeed.blocks[0].id, blockType: 'paragraph', content: '中文搜索定位 M10 unique phrase', sortOrder: 0 },
    ])
    await page.goto('/search?q=unique%20phrase')
    const searchResult = page.locator('.search-result-item').filter({ hasText: source.item.title })
    await expect(searchResult).toBeVisible()
    await expect(searchResult.locator('mark').filter({ hasText: 'unique phrase' })).toBeVisible()
    await expect(page.getByLabel('知识内容筛选')).toBeVisible()
    await searchResult.getByRole('button', { name: /打开知识内容/ }).click()
    await expect(page).toHaveURL(new RegExp(`${source.item.id}.*#doc-block-`))
  } finally {
    await secondContext.close()
    await archiveKnowledgeSpaceFixture(request, administrator, space)
    await context.clearCookies()
  }
})
