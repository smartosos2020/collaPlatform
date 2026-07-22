import { expect, test } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

test.describe('PROJECT-PLATFORM-S03-M4 preset and compatibility UI', () => {
  test('new and existing spaces expose protected presets while legacy projects remain usable @smoke', async ({ page, request }) => {
    test.setTimeout(120_000)
    requireIsolatedIdentityFixture()
    const suffix = `s03m4_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const owner = await loginByApi(request)
    const spaceIds: string[] = []

    try {
      const existingSpace = await createSpace(request, owner, `s03-m4-existing-${suffix}`, `S03 M4 既有空间 ${suffix}`)
      const newSpace = await createSpace(request, owner, `s03-m4-new-${suffix}`, `S03 M4 新空间 ${suffix}`)
      spaceIds.push(existingSpace.id, newSpace.id)
      await installSession(page, owner)

      await page.goto(`/project-spaces/${newSpace.id}/types`)
      await expect(page.getByTestId('work-item-types-panel')).toBeVisible()
      await expect(page.getByRole('option')).toHaveCount(6)
      await expect(page.getByRole('option', { name: /项目.*project/ })).toBeVisible()
      await expect(page.getByRole('option', { name: /需求.*requirement/ })).toBeVisible()
      await expect(page.getByRole('option', { name: /任务.*task/ })).toBeVisible()
      await expect(page.getByRole('option', { name: /缺陷.*bug/ })).toBeVisible()
      await expect(page.getByRole('option', { name: /迭代.*iteration/ })).toBeVisible()
      await expect(page.getByRole('option', { name: /版本.*release/ })).toBeVisible()

      await page.getByRole('option', { name: /项目.*project/ }).click()
      await expect(page.getByText('来源：研发预置目录 development-v1')).toBeVisible()
      await expect(page.getByRole('button', { name: '编辑' })).toHaveCount(0)
      await expect(page.getByRole('button', { name: '退役' })).toHaveCount(0)
      await expect(page.getByRole('button', { name: '复制' })).toBeVisible()

      const initialOrder = await typeKeys(page)
      const reorderResponse = page.waitForResponse((response) =>
        response.url().endsWith('/configuration/types:reorder') && response.request().method() === 'PUT')
      await page.getByRole('button', { name: '下移 项目' }).click()
      expect((await reorderResponse).ok()).toBeTruthy()
      await expect.poll(() => typeKeys(page)).not.toEqual(initialOrder)

      await page.getByRole('button', { name: '停用' }).click()
      const disableResponse = page.waitForResponse((response) => response.url().endsWith(':disable'))
      await page.getByRole('dialog').getByRole('button', { name: '确认停用' }).click()
      expect((await disableResponse).ok()).toBeTruthy()
      await expect(page.getByRole('button', { name: '恢复' })).toBeVisible()
      await page.getByRole('button', { name: '恢复' }).click()
      const restoreResponse = page.waitForResponse((response) => response.url().endsWith(':restore'))
      await page.getByRole('dialog').getByRole('button', { name: '确认恢复' }).click()
      expect((await restoreResponse).ok()).toBeTruthy()

      await page.getByRole('button', { name: '复制' }).click()
      await page.getByLabel('类型标识').fill(`${suffix}_project_copy`)
      await page.getByLabel('显示名称').fill('项目副本')
      await page.getByRole('dialog').getByRole('button', { name: /复\s*制/, exact: true }).click()
      await expect(page.getByRole('heading', { name: '项目副本' })).toBeVisible()
      await expect(page.getByText('自定义')).toBeVisible()

      await page.goto(`/project-spaces/${existingSpace.id}/types`)
      await expect(page.getByRole('option')).toHaveCount(6)
      await expect(page.getByText('系统类型')).toHaveCount(1)

      const summaryResponse = await request.get(`${apiBaseUrl}/project-spaces/${existingSpace.id}/work-item-types`, {
        headers: bearer(owner),
      })
      expect(summaryResponse.ok()).toBeTruthy()
      expect((await summaryResponse.json() as Array<{ typeKey: string }>).map((item) => item.typeKey).sort())
        .toEqual(['bug', 'iteration', 'project', 'release', 'requirement', 'task'])

      await page.goto('/projects')
      await expect(page.getByRole('heading', { name: '项目' })).toBeVisible()
      await expect(page.getByRole('button', { name: '工作项类型' })).toHaveCount(0)
    } finally {
      for (const spaceId of spaceIds) {
        await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`, { headers: bearer(owner) })
      }
    }
  })
})

async function createSpace(
  request: import('@playwright/test').APIRequestContext,
  session: Awaited<ReturnType<typeof loginByApi>>,
  spaceKey: string,
  name: string,
) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(session),
    data: { spaceKey: spaceKey.replaceAll('_', '-'), name, visibility: 'private' },
  })
  expect(response.ok(), `space creation failed: ${name}`).toBeTruthy()
  return await response.json() as { id: string }
}

async function typeKeys(page: import('@playwright/test').Page) {
  return page.getByRole('option').locator('small').allTextContents()
}
