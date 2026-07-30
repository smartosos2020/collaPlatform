import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'

test.describe('PROJECT-PLATFORM-S06-M1 configuration draft', () => {
  test('manager validates, refreshes and abandons the single configuration draft @smoke', async ({
    page,
    request,
  }) => {
    test.setTimeout(180_000)
    page.setDefaultTimeout(15_000)
    const session = await loginByApi(request)
    const suffix = `s06m1_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
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
      await expect(panel).toContainText('编辑中')
      await expect(panel).toContainText('hash ')
      await expect(panel).toContainText('missing_layout_kind')
      await expect(panel).toContainText('1 个提醒')

      const validateResponse = page.waitForResponse((response) =>
        response.url().endsWith(`/configuration/types/${typeId}/draft:validate`)
          && response.request().method() === 'POST')
      await panel.getByRole('button', { name: '校验配置' }).click()
      expect((await validateResponse).ok()).toBeTruthy()
      await expect(panel).toContainText('校验通过')

      const beforeEdit = await getDraft(request, session, spaceId, typeId)
      await page.getByTestId('project-space-types-secondary-tabs')
        .getByRole('tab', { name: '类型目录', exact: true }).click()
      const editButton = page.locator('.work-item-type-detail-card').getByRole('button', { name: '编辑' })
      await editButton.scrollIntoViewIfNeeded()
      await editButton.click()
      await page.getByLabel('类型说明').fill('S06 M1 自动刷新草稿')
      const editResponse = page.waitForResponse((response) =>
        response.url().endsWith(`/configuration/types/${typeId}`)
          && response.request().method() === 'PATCH')
      await page.getByRole('dialog').getByRole('button', { name: /保\s*存/, exact: true }).click()
      expect((await editResponse).ok()).toBeTruthy()
      await page.getByTestId('project-space-types-secondary-tabs')
        .getByRole('tab', { name: '配置发布', exact: true }).click()
      await expect(panel).toContainText('编辑中')
      await expect.poll(async () => (await getDraft(request, session, spaceId as string, typeId)).aggregateVersion)
        .toBeGreaterThan(beforeEdit.aggregateVersion)

      const abandonResponse = page.waitForResponse((response) =>
        response.url().endsWith(`/configuration/types/${typeId}/draft:abandon`)
          && response.request().method() === 'POST')
      await panel.getByRole('button', { name: '放弃草稿' }).click()
      await page.getByRole('dialog').getByRole('button', { name: '放弃草稿' }).click()
      expect((await abandonResponse).ok()).toBeTruthy()
      await expect(panel).toContainText('已放弃')
      await expect(panel.getByRole('button', { name: '校验配置' })).toBeDisabled()

      await page.getByTestId('project-space-types-secondary-tabs')
        .getByRole('tab', { name: '类型目录', exact: true }).click()
      await editButton.scrollIntoViewIfNeeded()
      await editButton.click()
      await page.getByLabel('类型说明').fill('S06 M1 新草稿')
      await page.getByRole('dialog').getByRole('button', { name: /保\s*存/, exact: true }).click()
      await page.getByTestId('project-space-types-secondary-tabs')
        .getByRole('tab', { name: '配置发布', exact: true }).click()
      await expect(panel).toContainText('编辑中')
      const nextDraft = await getDraft(request, session, spaceId, typeId)
      expect(nextDraft.id).not.toBe(beforeEdit.id)

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

type DraftResponse = {
  id: string
  aggregateVersion: number
}

async function createSpace(
  request: APIRequestContext,
  session: E2eSession,
  suffix: string,
) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(session),
    data: {
      spaceKey: `s06-m1-${suffix.replaceAll('_', '-')}`,
      name: `S06 M1 草稿空间 ${suffix}`,
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
        'X-Colla-Request-Id': `s06-m1-type-${suffix}`,
      },
      data: {
        typeKey: `${suffix}_delivery`,
        name: 'S06 配置草稿',
        description: 'S06 M1 browser acceptance',
        sortOrder: 10,
      },
    },
  )
  expect(response.ok()).toBeTruthy()
  return (await response.json() as { id: string }).id
}

async function getDraft(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  typeId: string,
) {
  const response = await request.get(
    `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}/draft`,
    { headers: bearer(session) },
  )
  expect(response.ok()).toBeTruthy()
  return await response.json() as DraftResponse
}
