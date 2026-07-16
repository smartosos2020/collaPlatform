import { expect, test } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi } from './support/api'
import { archiveKnowledgeSpaceFixture, createKnowledgeSpaceFixture, uniqueFixtureName } from './support/fixtures'
import { createKnowledgeItem, knowledgeContentUrl, saveKnowledgeBlocks } from './support/knowledge'

type PerformanceContract = {
  blockCount: number
  snapshotBytes: number
  budgetTier: 100 | 500 | 1000
  initialRenderBlocks: number
  loadBudgetMs: number
  inputBudgetMs: number
  saveBudgetMs: number
  searchBudgetMs: number
  collaborationBudgetMs: number
}

test('@smoke @kb-product-m11 large content budgets, diagnostics and accessibility remain bounded', async ({ context, page, request }) => {
  test.setTimeout(240_000)
  const administrator = await loginByApi(request)
  const space = await createKnowledgeSpaceFixture(request, administrator, 'kb-product-m11-budgets')

  try {
    const cases: Array<{ blockCount: 100 | 500 | 1000; itemId: string }> = []
    for (const blockCount of [100, 500, 1000] as const) {
      const item = await createKnowledgeItem(request, administrator, space, {
        title: uniqueFixtureName(`m11-${blockCount}-blocks`),
        contentType: 'markdown',
        content: 'Initial block',
      })
      const saved = await saveKnowledgeBlocks(
        request,
        administrator,
        space.id,
        item.item.id,
        item.item.currentVersionNo,
        Array.from({ length: blockCount }, (_, index) => ({
          blockType: 'paragraph',
          content: `M11 ${blockCount} block ${index + 1}`,
          sortOrder: index,
        })),
        'manual',
      )
      expect(saved.blocks).toHaveLength(blockCount)
      cases.push({ blockCount, itemId: item.item.id })

      const startedAt = Date.now()
      const performanceResponse = await request.get(
        `${apiBaseUrl}/knowledge-bases/${space.id}/items/${item.item.id}/performance`,
        { headers: bearer(administrator) },
      )
      const elapsedMs = Date.now() - startedAt
      expect(performanceResponse.ok()).toBeTruthy()
      const performance = await performanceResponse.json() as PerformanceContract
      expect(performance.blockCount).toBe(blockCount)
      expect(performance.budgetTier).toBe(blockCount)
      expect(performance.snapshotBytes).toBeGreaterThan(0)
      expect(performance.initialRenderBlocks).toBe(Math.min(blockCount, 160))
      expect(elapsedMs).toBeLessThan(performance.loadBudgetMs)
      expect(performance.inputBudgetMs).toBeGreaterThan(0)
      expect(performance.saveBudgetMs).toBeGreaterThan(0)
      expect(performance.searchBudgetMs).toBeGreaterThan(0)
      expect(performance.collaborationBudgetMs).toBeGreaterThan(0)
    }

    const largest = cases.find((entry) => entry.blockCount === 1000)
    expect(largest).toBeTruthy()
    const diagnosticsResponse = await request.get(
      `${apiBaseUrl}/knowledge-bases/${space.id}/items/${largest!.itemId}/diagnostics`,
      { headers: bearer(administrator) },
    )
    expect(diagnosticsResponse.ok()).toBeTruthy()
    const diagnostics = await diagnosticsResponse.json() as Record<string, unknown>
    expect(diagnostics.redacted).toBe(true)
    expect(diagnostics.blockCount).toBe(1000)
    expect(diagnostics.snapshotBytes).toEqual(expect.any(Number))
    expect(diagnostics).not.toHaveProperty('title')
    expect(diagnostics).not.toHaveProperty('content')
    expect(diagnostics).not.toHaveProperty('blocks')

    await installSession(page, administrator)
    const navigationStartedAt = Date.now()
    await page.goto(knowledgeContentUrl(space.id, largest!.itemId))
    await expect(page.getByText('大内容预览模式')).toBeVisible()
    expect(Date.now() - navigationStartedAt).toBeLessThan(4000)

    await expect(page.getByRole('main')).toHaveCount(1)
    await expect(page.getByRole('region', { name: '知识内容正文' })).toBeVisible()
    await expect(page.getByRole('textbox', { name: '知识内容标题' })).toBeVisible()
    await expect(page.getByRole('textbox', { name: '知识内容正文编辑器' })).toBeVisible()
    await expect(page.getByRole('button', { name: '内容分享与权限' })).toBeVisible()
    await expect(page.getByRole('button', { name: '关联对象' })).toBeVisible()

    const editorBlocks = page.locator('.doc-prosemirror > *')
    await expect(editorBlocks).toHaveCount(160)
    await page.getByRole('button', { name: '继续加载 160 块' }).click()
    await expect(editorBlocks).toHaveCount(320)

    const title = page.getByRole('textbox', { name: '知识内容标题' })
    await expect(title).toBeDisabled()
    const keyboardStart = page.getByRole('button', { name: '继续加载 160 块' })
    await keyboardStart.focus()
    await expect(keyboardStart).toBeFocused()
    await page.keyboard.press('Tab')
    await expect(keyboardStart).not.toBeFocused()
    const focusVisible = await page.evaluate(() => document.activeElement?.matches(':focus-visible') ?? false)
    expect(focusVisible).toBeTruthy()

    const accessibilityStructure = await page.evaluate(() => ({
      duplicateIds: Array.from(document.querySelectorAll('[id]'))
        .map((element) => element.id)
        .filter((id, index, ids) => ids.indexOf(id) !== index),
      horizontalOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
      headings: Array.from(document.querySelectorAll('h1')).map((heading) => heading.textContent?.trim()),
    }))
    expect(accessibilityStructure.duplicateIds).toEqual([])
    expect(accessibilityStructure.horizontalOverflow).toBeFalsy()
    expect(accessibilityStructure.headings).toHaveLength(1)
  } finally {
    await archiveKnowledgeSpaceFixture(request, administrator, space)
    await context.clearCookies()
  }
})
