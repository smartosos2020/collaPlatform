import { expect, test } from '@playwright/test'

import { installSession, loginByApi } from './support/api'
import { installFailureEvidence } from './support/diagnostics'

test('@smoke global search dropdown widths stay stable across selection states', async ({ page, request }, testInfo) => {
  const flushEvidence = installFailureEvidence(page, testInfo)

  try {
    await installSession(page, await loginByApi(request))
    await page.goto('/search')

    const filterBar = page.getByLabel('搜索筛选')
    const dropdowns = filterBar.locator('.ant-select')
    await expect(dropdowns).toHaveCount(7)

    const initialWidths = await dropdownWidths(dropdowns)
    expect(initialWidths.every((width) => width >= 180)).toBeTruthy()

    await page.goto('/search?objectTypes=knowledge_content')
    const selectedObjectTypeDropdown = page.getByLabel('搜索筛选').locator('.ant-select').nth(1)
    await expect(selectedObjectTypeDropdown).toContainText('知识内容')
    expect(await dropdownWidth(selectedObjectTypeDropdown)).toBeCloseTo(initialWidths[1], 0)

    await page.goto('/search')
    const clearedObjectTypeDropdown = page.getByLabel('搜索筛选').locator('.ant-select').nth(1)
    await expect(clearedObjectTypeDropdown).toContainText('全部对象类型')
    expect(await dropdownWidth(clearedObjectTypeDropdown)).toBeCloseTo(initialWidths[1], 0)
  } finally {
    await flushEvidence()
  }
})

test('@smoke global search dropdowns fill the filter row on narrow screens', async ({ page, request }, testInfo) => {
  const flushEvidence = installFailureEvidence(page, testInfo)

  try {
    await page.setViewportSize({ width: 390, height: 844 })
    await installSession(page, await loginByApi(request))
    await page.goto('/search')

    const filterBar = page.getByLabel('搜索筛选')
    const filterBarBox = await filterBar.boundingBox()
    const dropdowns = filterBar.locator('.ant-select')
    await expect(dropdowns).toHaveCount(7)
    expect(filterBarBox).not.toBeNull()

    const widths = await dropdownWidths(dropdowns)
    for (const width of widths) {
      expect(width).toBeCloseTo(filterBarBox!.width, 0)
    }
  } finally {
    await flushEvidence()
  }
})

async function dropdownWidths(dropdowns: import('@playwright/test').Locator) {
  return dropdowns.evaluateAll((elements) => elements.map((element) => element.getBoundingClientRect().width))
}

async function dropdownWidth(dropdown: import('@playwright/test').Locator) {
  return dropdown.evaluate((element) => element.getBoundingClientRect().width)
}
