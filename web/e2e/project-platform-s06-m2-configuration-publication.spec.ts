import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'

test.describe('PROJECT-PLATFORM-S06-M2 immutable publication', () => {
  test('manager publishes, compares and rolls back through a new higher version @smoke', async ({
    page,
    request,
  }) => {
    test.setTimeout(180_000)
    page.setDefaultTimeout(15_000)
    const session = await loginByApi(request)
    const suffix = `s06m2_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    let spaceId: string | undefined

    try {
      spaceId = await createSpace(request, session, suffix)
      const typeId = await createType(request, session, spaceId, suffix)
      await installSession(page, session)
      await page.setViewportSize({ width: 1366, height: 768 })
      await page.goto(`/project-spaces/${spaceId}/types/${typeId}`)
      await page.getByTestId('project-space-types-secondary-tabs')
        .getByRole('tab', { name: '配置发布', exact: true }).click()

      const panel = page.getByRole('region', { name: '配置草稿状态' })
      await expect(panel).toBeVisible()
      await validateAndPublish(page, panel, typeId)
      await expect(panel).toContainText('当前 v2')
      await expect(panel).toContainText('legacy partial')

      await page.getByTestId('project-space-types-secondary-tabs')
        .getByRole('tab', { name: '类型目录', exact: true }).click()
      const editButton = page.locator('.work-item-type-detail-card').getByRole('button', { name: '编辑' })
      await editButton.scrollIntoViewIfNeeded()
      await editButton.click()
      await page.getByLabel('类型说明').fill('S06 M2 第二个不可变版本')
      await page.getByRole('dialog').getByRole('button', { name: /保\s*存/, exact: true }).click()
      await page.getByTestId('project-space-types-secondary-tabs')
        .getByRole('tab', { name: '配置发布', exact: true }).click()
      await expect(panel).toContainText('编辑中')
      await validateAndPublish(page, panel, typeId)
      await expect(panel).toContainText('当前 v3')

      const version2 = panel.getByRole('listitem').filter({ hasText: 'v2' })
      await expect(version2).toContainText('历史')
      const prepareResponse = page.waitForResponse((response) =>
        response.url().includes(`/configuration/types/${typeId}/versions/`)
          && response.url().endsWith(':prepare-rollback')
          && response.request().method() === 'POST')
      await version2.getByRole('button', { name: '回滚' }).click()
      await page.getByRole('dialog').getByRole('button', { name: '生成回滚草稿' }).click()
      expect((await prepareResponse).ok()).toBeTruthy()
      await expect(panel).toContainText('校验通过')

      await publish(page, panel, typeId)
      await expect(panel).toContainText('当前 v4')
      await expect(panel.getByRole('listitem').filter({ hasText: 'v4' })).toContainText('回滚发布')

      const versions = await request.get(
        `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}/versions`,
        { headers: bearer(session) },
      )
      expect(versions.ok()).toBeTruthy()
      const items = await versions.json() as Array<{
        versionNumber: number
        status: string
        completeSnapshot: boolean
        rollbackSourceVersionId?: string
      }>
      expect(items.map((item) => item.versionNumber)).toEqual([4, 3, 2, 1])
      expect(items[0].status).toBe('published')
      expect(items[0].rollbackSourceVersionId).toBeTruthy()
      expect(items[3].completeSnapshot).toBe(false)

      expect(await page.evaluate(() =>
        document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1)
    } finally {
      if (spaceId) {
        await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`, {
          headers: bearer(session),
        }).catch(() => undefined)
      }
    }
  })
})

async function validateAndPublish(
  page: import('@playwright/test').Page,
  panel: import('@playwright/test').Locator,
  typeId: string,
) {
  const validateResponse = page.waitForResponse((response) =>
    response.url().endsWith(`/configuration/types/${typeId}/draft:validate`)
      && response.request().method() === 'POST')
  await panel.getByRole('button', { name: '校验配置' }).click()
  expect((await validateResponse).ok()).toBeTruthy()
  await expect(panel).toContainText('校验通过')
  await publish(page, panel, typeId)
}

async function publish(
  page: import('@playwright/test').Page,
  panel: import('@playwright/test').Locator,
  typeId: string,
) {
  const response = page.waitForResponse((value) =>
    value.url().endsWith(`/configuration/types/${typeId}/draft:publish`)
      && value.request().method() === 'POST')
  await panel.getByRole('button', { name: '发布版本' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('button', { name: /^(确认并发布|发布版本)$/ }).click()
  expect((await response).ok()).toBeTruthy()
  await expect(dialog).toBeHidden()
}

async function createSpace(
  request: APIRequestContext,
  session: E2eSession,
  suffix: string,
) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(session),
    data: {
      spaceKey: `s06-m2-${suffix.replaceAll('_', '-')}`,
      name: `S06 M2 发布空间 ${suffix}`,
      visibility: 'private',
    },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json() as { id: string }).id
}

async function createType(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  suffix: string,
) {
  const response = await request.post(
    `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`,
    {
      headers: {
        ...bearer(session),
        'X-Colla-Request-Id': `s06-m2-type-${suffix}`,
      },
      data: {
        typeKey: `${suffix}_delivery`,
        name: 'S06 不可变发布',
        description: 'S06 M2 browser acceptance',
        sortOrder: 10,
      },
    },
  )
  expect(response.ok()).toBeTruthy()
  return (await response.json() as { id: string }).id
}
