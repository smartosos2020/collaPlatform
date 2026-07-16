import { expect, test, type BrowserContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, webBaseUrl } from './support/api'
import { archiveKnowledgeSpaceFixture, createKnowledgeSpaceFixture } from './support/fixtures'
import { createKnowledgeItem, getKnowledgeContent, knowledgeContentUrl } from './support/knowledge'

test('@smoke KB-PRODUCT-M3 canonical editor keeps focus and saves blocks', async ({ page, request }) => {
  const session = await loginByApi(request)
  const space = await createKnowledgeSpaceFixture(request, session, 'product-m3-editor')
  let viewerContext: BrowserContext | undefined
  let viewerId: string | undefined

  try {
    const first = await createKnowledgeItem(request, session, space, {
      title: 'M3 canonical editor entry',
      contentType: 'markdown',
      content: 'Initial canonical paragraph',
    })
    const second = await createKnowledgeItem(request, session, space, {
      title: 'M3 second entry',
      contentType: 'markdown',
      content: 'Second entry content',
    })
    await installSession(page, session)
    await page.goto(knowledgeContentUrl(space.id, first.item.id))
    await expect(page.getByLabel('知识内容标题')).toHaveValue('M3 canonical editor entry')

    const editor = page.locator('.doc-prosemirror[role="textbox"]')
    await expect(editor).toBeVisible()
    const legacyRootRequests: string[] = []
    page.on('request', (requestEvent) => {
      if (requestEvent.method() !== 'PATCH' || !requestEvent.url().includes(`/knowledge-bases/${space.id}/items/${first.item.id}`)) {
        return
      }
      if (!requestEvent.url().endsWith('/blocks')) {
        legacyRootRequests.push(requestEvent.url())
      }
    })

    await editor.click()
    await editor.pressSequentially(' M3 continuous input')
    await expect(editor).toContainText('M3 continuous input')
    expect(legacyRootRequests).toEqual([])

    await expect.poll(async () => {
      const current = await getKnowledgeContent(request, session, space.id, first.item.id)
      return current.blocks.some((block) => block.content.includes('M3 continuous input'))
    }, { timeout: 15_000 }).toBeTruthy()
    const saved = await getKnowledgeContent(request, session, space.id, first.item.id)
    expect(saved.blocks.some((block) => block.content.includes('M3 continuous input'))).toBeTruthy()
    expect(saved.blocks.every((block) => Boolean(block.id))).toBeTruthy()

    await page.reload()
    await expect(page.locator('.doc-prosemirror[role="textbox"]')).toContainText('M3 continuous input')

    await page.getByText('M3 second entry', { exact: true }).first().click()
    await expect(page.getByLabel('知识内容标题')).toHaveValue('M3 second entry')
    await expect(page.locator('.doc-prosemirror[role="textbox"]')).toContainText('Second entry content')
    await expect(page).toHaveURL(new RegExp(`${space.id}/items/${second.item.id}$`))

    await page.goBack()
    await expect(page.getByLabel('知识内容标题')).toHaveValue('M3 canonical editor entry')
    await expect(page.locator('.doc-prosemirror[role="textbox"]')).toContainText('M3 continuous input')
    await page.goForward()
    await expect(page.getByLabel('知识内容标题')).toHaveValue('M3 second entry')

    const viewerUsername = `m3_viewer_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const viewerPassword = 'member123456'
    const viewerResponse = await request.post(`${apiBaseUrl}/admin/users`, {
      headers: bearer(session),
      data: {
        username: viewerUsername,
        password: viewerPassword,
        displayName: 'M3 Readonly Viewer',
        email: `${viewerUsername}@colla.local`,
        roleCode: 'member',
      },
    })
    expect(viewerResponse.ok(), 'readonly viewer fixture creation failed').toBeTruthy()
    const viewer = await viewerResponse.json() as { id: string }
    viewerId = viewer.id
    const viewerSession = await loginByApi(request, viewerUsername, viewerPassword)
    const spacePermissionResponse = await request.post(`${apiBaseUrl}/resource-permissions/knowledge_base/${space.id}`, {
      headers: bearer(session),
      data: {
        subjectType: 'user',
        subjectId: viewer.id,
        permissionLevel: 'view',
        confirmHighRisk: false,
      },
    })
    expect(spacePermissionResponse.ok(), 'readonly viewer knowledge-base permission grant failed').toBeTruthy()
    const viewerDetail = await getKnowledgeContent(request, viewerSession, space.id, first.item.id)
    expect(viewerDetail.item.permissionLevel).toBe('view')

    viewerContext = await page.context().browser()!.newContext({ baseURL: webBaseUrl })
    const viewerPage = await viewerContext.newPage()
    await installSession(viewerPage, viewerSession)
    await viewerPage.goto(knowledgeContentUrl(space.id, first.item.id))
    await expect(viewerPage.getByText('当前为只读模式')).toBeVisible()
    await expect(viewerPage.getByLabel('知识内容标题')).toBeDisabled()

    const anonymousPage = await page.context().browser()!.newPage()
    await anonymousPage.goto(knowledgeContentUrl(space.id, first.item.id))
    await expect(anonymousPage).toHaveURL(/\/login/)
    await anonymousPage.close()
  } finally {
    if (viewerContext) {
      await viewerContext.close()
    }
    if (viewerId) {
      await request.post(`${apiBaseUrl}/admin/users/${viewerId}/offboard`, { headers: bearer(session) })
    }
    await archiveKnowledgeSpaceFixture(request, session, space)
  }
})
