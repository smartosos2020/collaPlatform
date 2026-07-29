import { expect, test } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

test.describe('PROJECT-PLATFORM-S21-M2 legacy product exit', () => {
  test('keeps canonical project spaces usable while old product contracts stay retired', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(180_000)
    requireIsolatedIdentityFixture()
    const admin = await loginByApi(request)
    const headers = bearer(admin)
    const missingId = crypto.randomUUID()
    const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`
    let spaceId: string | undefined

    try {
      const canonical = await request.get(`${apiBaseUrl}/project-spaces`, { headers })
      expect(canonical.ok(), await canonical.text()).toBeTruthy()

      for (const contract of [
        { method: 'get', path: '/projects' },
        { method: 'get', path: `/issues/${missingId}` },
        { method: 'get', path: '/my/issues' },
        { method: 'get', path: `/compat/work-items/legacy/issues/${missingId}` },
      ] as const) {
        const response = await request[contract.method](`${apiBaseUrl}${contract.path}`, { headers })
        expect(response.status(), `${contract.method.toUpperCase()} ${contract.path}`).toBe(404)
      }

      const spaceResponse = await request.post(`${apiBaseUrl}/project-spaces`, {
        headers,
        data: {
          spaceKey: `s21-m2-${suffix}`,
          name: `S21 M2 canonical conversion ${suffix}`,
          visibility: 'private',
        },
      })
      expect(spaceResponse.ok(), await spaceResponse.text()).toBeTruthy()
      spaceId = (await spaceResponse.json() as { id: string }).id
      const typeResponse = await request.post(
        `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`,
        {
          headers: { ...headers, 'X-Colla-Request-Id': `s21-m2-type-${suffix}` },
          data: { typeKey: `task_${suffix.replaceAll('-', '_')}`, name: 'Canonical Task', sortOrder: 10 },
        },
      )
      expect(typeResponse.ok(), await typeResponse.text()).toBeTruthy()
      const typeId = (await typeResponse.json() as { id: string }).id
      const draftUrl = `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${typeId}/draft`
      const draftResponse = await request.get(draftUrl, { headers })
      expect(draftResponse.ok(), await draftResponse.text()).toBeTruthy()
      const draft = await draftResponse.json() as { aggregateVersion: number }
      const validated = await request.post(`${draftUrl}:validate`, {
        headers: { ...headers, 'X-Colla-Request-Id': `s21-m2-validate-${suffix}` },
        data: { expectedAggregateVersion: draft.aggregateVersion },
      })
      expect(validated.ok(), await validated.text()).toBeTruthy()
      const validatedDraft = await validated.json() as { aggregateVersion: number }
      const published = await request.post(`${draftUrl}:publish`, {
        headers: { ...headers, 'X-Colla-Request-Id': `s21-m2-publish-${suffix}` },
        data: {
          expectedDraftAggregateVersion: validatedDraft.aggregateVersion,
          breakingConfirmed: false,
        },
      })
      expect(published.ok(), await published.text()).toBeTruthy()

      const conversationResponse = await request.post(`${apiBaseUrl}/conversations`, {
        headers,
        data: { conversationType: 'group', title: `S21 M2 ${suffix}`, memberIds: [] },
      })
      expect(conversationResponse.ok(), await conversationResponse.text()).toBeTruthy()
      const conversationId = (await conversationResponse.json() as { id: string }).id
      const messageResponse = await request.post(`${apiBaseUrl}/conversations/${conversationId}/messages`, {
        headers,
        data: {
          clientMessageId: crypto.randomUUID(),
          messageType: 'text',
          content: `S21 M2 canonical conversion ${suffix}`,
        },
      })
      expect(messageResponse.ok(), await messageResponse.text()).toBeTruthy()
      const messageId = (await messageResponse.json() as { id: string }).id
      const converted = await request.post(
        `${apiBaseUrl}/conversations/${conversationId}/messages/${messageId}/convert-to-work-item`,
        {
          headers: { ...headers, 'X-Colla-Request-Id': `s21-m2-convert-${suffix}` },
          data: { projectSpaceId: spaceId, workItemTypeId: typeId, title: `Converted ${suffix}` },
        },
      )
      expect(converted.ok(), await converted.text()).toBeTruthy()
      const created = await converted.json() as { id: string; webPath: string }
      expect(created.webPath).toBe(`/project-spaces/${spaceId}/work-items/${created.id}`)
      const retiredConversion = await request.post(
        `${apiBaseUrl}/conversations/${conversationId}/messages/${messageId}/convert-to-issue`,
        { headers, data: {} },
      )
      expect(retiredConversion.status()).toBe(404)

      await installSession(page, admin)
      await page.goto('/projects')
      await expect(page).toHaveURL(/\/project-spaces$/)
      await expect(page.getByText('项目空间', { exact: true }).first()).toBeVisible()

      await page.goto(`/issues/${missingId}`)
      await expect(page.getByText('工作项链接不可用')).toBeVisible()
      await expect(page.getByText('该旧链接不存在、不可访问，或迁移映射尚未就绪。')).toBeVisible()

      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 980 })
        expect(await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        )).toBeLessThanOrEqual(1)
      }
      await page.context().setOffline(true)
      await page.evaluate(() => window.dispatchEvent(new Event('offline')))
      await expect(page.getByText('工作项链接不可用')).toBeVisible()
      await page.screenshot({
        path: testInfo.outputPath('s21-m2-legacy-exit-820.png'),
        fullPage: true,
      })
    } finally {
      if (spaceId) {
        await page.context().setOffline(false).catch(() => undefined)
        await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`, { headers })
      }
    }
  })
})
