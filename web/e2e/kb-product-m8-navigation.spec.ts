import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { archiveKnowledgeSpaceFixture, uniqueFixtureName, type KnowledgeSpaceFixture } from './support/fixtures'
import { createKnowledgeItem, createProjectObjectFixture } from './support/knowledge'

test('@smoke @kb-product-m8 content-first navigation stays canonical across node types and identities', async ({ browser, page, request }) => {
  test.setTimeout(120_000)
  const administrator = await loginByApi(request)
  const space = await createWorkspaceKnowledgeSpace(request, administrator)
  const folder = await createKnowledgeItem(request, administrator, space, {
    parentId: space.rootItemId,
    title: uniqueFixtureName('m8-directory'),
    contentType: 'folder',
  })
  const child = await createKnowledgeItem(request, administrator, space, {
    parentId: folder.item.id,
    title: uniqueFixtureName('m8-child'),
    contentType: 'markdown',
    content: '# M8 child content',
  })
  const emptyFolder = await createKnowledgeItem(request, administrator, space, {
    parentId: space.rootItemId,
    title: uniqueFixtureName('m8-empty'),
    contentType: 'folder',
  })
  const archivedItem = await createKnowledgeItem(request, administrator, space, {
    parentId: space.rootItemId,
    title: uniqueFixtureName('m8-archived'),
    contentType: 'markdown',
    content: '# archived',
  })
  const project = await createProjectObjectFixture(request, administrator, uniqueFixtureName('m8-project'))
  const projectEntry = await createObjectEntry(request, administrator, space, {
    title: uniqueFixtureName('m8-project-entry'),
    targetObjectType: 'project',
    targetObjectId: project.id,
  })
  const unavailableTarget = await createKnowledgeItem(request, administrator, space, {
    parentId: space.rootItemId,
    title: uniqueFixtureName('m8-private-target'),
    contentType: 'markdown',
    content: '# unavailable target',
  })
  await createObjectEntry(request, administrator, space, {
    title: uniqueFixtureName('m8-unavailable-entry'),
    targetObjectType: 'knowledge_content',
    targetObjectId: unavailableTarget.item.id,
  })
  const externalEntry = await createExternalEntry(request, administrator, space, uniqueFixtureName('m8-external'))
  const editor = await createMember(request, administrator, 'm8-editor')
  const viewer = await createMember(request, administrator, 'm8-viewer')
  const outsider = await createMember(request, administrator, 'm8-outsider')
  const editorSession = await loginByApi(request, editor.username, editor.password)
  const viewerSession = await loginByApi(request, viewer.username, viewer.password)
  const outsiderSession = await loginByApi(request, outsider.username, outsider.password)
  const editorContext = await browser.newContext()
  const viewerContext = await browser.newContext()
  const outsiderContext = await browser.newContext()
  const editorPage = await editorContext.newPage()
  const viewerPage = await viewerContext.newPage()
  const outsiderPage = await outsiderContext.newPage()

  try {
    await grantResourcePermission(request, administrator, 'knowledge_base', space.id, editor.id, 'edit')
    await grantResourcePermission(request, administrator, 'knowledge_content', space.rootItemId, editor.id, 'edit')
    await grantResourcePermission(request, administrator, 'knowledge_content', space.homeItemId, editor.id, 'edit')
    await grantResourcePermission(request, administrator, 'knowledge_base', space.id, viewer.id, 'view')
    await grantResourcePermission(request, administrator, 'knowledge_content', space.rootItemId, viewer.id, 'view')
    await grantResourcePermission(request, administrator, 'knowledge_content', space.homeItemId, viewer.id, 'view')
    await request.post(`${apiBaseUrl}/platform/objects/knowledge_content/${child.item.id}/access`, { headers: bearer(administrator) })
    await request.post(`${apiBaseUrl}/platform/objects/knowledge_content/${child.item.id}/favorite`, { headers: bearer(administrator) })
    await request.post(`${apiBaseUrl}/knowledge-bases/${space.id}/subscriptions`, {
      headers: bearer(administrator),
      data: { targetType: 'knowledge_content', targetId: child.item.id },
    })
    await request.post(`${apiBaseUrl}/knowledge-bases/${space.id}/items/${archivedItem.item.id}/archive`, { headers: bearer(administrator) })
    await request.post(`${apiBaseUrl}/knowledge-bases/${space.id}/items/${unavailableTarget.item.id}/archive`, { headers: bearer(administrator) })

    await page.setViewportSize({ width: 1366, height: 768 })
    await installSession(page, administrator)
    await page.goto(`/knowledge-bases/${space.id}`)
    await expect(page).toHaveURL(new RegExp(`/knowledge-bases/${space.id}/items/${space.homeItemId}$`))
    await expect(page.locator('.doc-title-input')).toHaveValue('首页')
    await expect(page.getByText('知识元数据')).toBeHidden()
    await expect(page.getByText('内容设置与治理')).toBeVisible()
    await expect(page.getByRole('button', { name: '空间管理' })).toBeVisible()
    await expect(page.getByRole('button', { name: '挂载协作对象' })).toBeVisible()

    await treeNode(page, folder.item.title).click()
    await expect(page).toHaveURL(new RegExp(`/items/${folder.item.id}$`))
    await expect(page.getByRole('heading', { name: folder.item.title, exact: true })).toBeVisible()
    const childCard = page.locator('.doc-directory-entry').filter({ hasText: child.item.title })
    await childCard.focus()
    await childCard.press('Enter')
    await expect(page).toHaveURL(new RegExp(`/items/${child.item.id}$`))
    await expect(page.locator('.doc-title-input')).toHaveValue(child.item.title)

    await page.goBack()
    await expect(page).toHaveURL(new RegExp(`/items/${folder.item.id}$`))
    await expect(treeNode(page, folder.item.title)).toHaveClass(/ant-tree-node-selected/)
    await page.reload()
    await expect(page.getByRole('heading', { name: folder.item.title, exact: true })).toBeVisible()

    await treeNode(page, emptyFolder.item.title).click()
    await expect(page).toHaveURL(new RegExp(`/items/${emptyFolder.item.id}$`))
    await expect(page.getByText('这个目录暂无内容')).toBeVisible()
    await expect(page.getByRole('button', { name: '创建内容页' })).toBeVisible()

    await treeNode(page, projectEntry.item.title).click()
    await expect(page).toHaveURL(new RegExp(`/projects/${project.id}$`))
    await page.goBack()
    await expect(page).toHaveURL(new RegExp(`/items/${emptyFolder.item.id}$`))

    const popupPromise = page.waitForEvent('popup')
    await treeNode(page, externalEntry.item.title).click()
    const popup = await popupPromise
    await expect(popup).toHaveURL(/example\.com\/m8/)
    await popup.close()

    const unavailableUrl = page.url()
    await treeNode(page, '已删除对象入口').click()
    await expect(page.getByText('目标已删除').last()).toBeVisible()
    await expect(page).toHaveURL(unavailableUrl)
    await expect(page.getByText(unavailableTarget.item.title, { exact: true })).toHaveCount(0)

    await page.goto(`/knowledge-bases/${space.id}/items/${child.item.id}`)
    const canonicalChildUrl = page.url()
    for (const mode of ['最近访问', '收藏内容', '关注内容']) {
      await page.locator('.docs-list-controls .ant-select').click()
      await page.locator('.ant-select-dropdown:visible .ant-select-item-option', { hasText: mode }).click()
      await expect(page).toHaveURL(canonicalChildUrl)
      await expect(page.locator('.doc-list-item').filter({ hasText: child.item.title })).toBeVisible()
    }
    await page.locator('.docs-list-controls .ant-select').click()
    await page.locator('.ant-select-dropdown:visible .ant-select-item-option', { hasText: '全部内容' }).click()
    await page.locator('.docs-archive-toggle button').click()
    await page.getByPlaceholder('搜索标题').fill(archivedItem.item.title)
    await expect(page.locator('.doc-list-item').filter({ hasText: archivedItem.item.title })).toContainText('已归档')
    await expect(page).toHaveURL(canonicalChildUrl)

    const desktopOverflow = await page.evaluate(() => ({
      body: document.documentElement.scrollHeight - document.documentElement.clientHeight,
      horizontal: document.documentElement.scrollWidth - document.documentElement.clientWidth,
      mainScrollable: document.querySelector('.docs-main')!.scrollHeight >= document.querySelector('.docs-main')!.clientHeight,
    }))
    expect(desktopOverflow.body).toBeLessThanOrEqual(1)
    expect(desktopOverflow.horizontal).toBeLessThanOrEqual(1)
    expect(desktopOverflow.mainScrollable).toBeTruthy()

    await page.setViewportSize({ width: 900, height: 720 })
    const narrowLayout = await page.evaluate(() => {
      const sidebar = document.querySelector('.docs-sidebar')!.getBoundingClientRect()
      const workspace = document.querySelector('.docs-workspace')!.getBoundingClientRect()
      return {
        sidebarHeight: sidebar.height,
        workspaceWidth: workspace.width,
        horizontal: document.documentElement.scrollWidth - document.documentElement.clientWidth,
      }
    })
    expect(narrowLayout.sidebarHeight).toBeLessThanOrEqual(221)
    expect(narrowLayout.workspaceWidth).toBeLessThanOrEqual(900)
    expect(narrowLayout.horizontal).toBeLessThanOrEqual(1)

    await request.post(`${apiBaseUrl}/knowledge-bases/${space.id}/disable`, { headers: bearer(administrator) })
    await page.goto(`/knowledge-bases/${space.id}/items/${space.homeItemId}`)
    await expect(page.getByText('知识库已停用')).toBeVisible()
    await expect(page.locator('.doc-title-input')).toBeDisabled()
    await request.post(`${apiBaseUrl}/knowledge-bases/${space.id}/restore`, { headers: bearer(administrator) })

    await page.goto(`/knowledge-bases/${space.id}/items/00000000-0000-0000-0000-000000000099`)
    await expect(page.getByText('内容不存在或当前不可见')).toBeVisible()
    await expect(page.getByText(space.name, { exact: true })).toHaveCount(0)

    await installSession(editorPage, editorSession)
    await editorPage.goto(`/knowledge-bases/${space.id}`)
    await expect(editorPage).toHaveURL(new RegExp(`/items/${space.homeItemId}$`))
    await expect(editorPage.locator('.doc-title-input')).toBeEnabled()
    await expect(editorPage.getByRole('button', { name: '空间管理' })).toHaveCount(0)
    await expect(editorPage.getByText('内容设置与治理')).toBeVisible()
    await expect(editorPage.getByRole('button', { name: '挂载协作对象' })).toBeVisible()

    await installSession(viewerPage, viewerSession)
    await viewerPage.goto(`/knowledge-bases/${space.id}`)
    await expect(viewerPage).toHaveURL(new RegExp(`/items/${space.homeItemId}$`))
    await expect(viewerPage.locator('.doc-title-input')).toBeDisabled()
    await expect(viewerPage.getByText('当前为只读模式')).toBeVisible()
    await expect(viewerPage.getByRole('button', { name: '空间管理' })).toHaveCount(0)
    await expect(viewerPage.getByText('内容设置与治理')).toHaveCount(0)
    await expect(viewerPage.getByRole('button', { name: '挂载协作对象' })).toHaveCount(0)

    await installSession(outsiderPage, outsiderSession)
    await outsiderPage.goto(`/knowledge-bases/${space.id}`)
    await expect(outsiderPage.getByText('无法访问这个知识库')).toBeVisible()
    await expect(outsiderPage.getByText(space.name, { exact: true })).toHaveCount(0)
  } finally {
    await editorContext.close()
    await viewerContext.close()
    await outsiderContext.close()
    await offboardMember(request, administrator, editor.id)
    await offboardMember(request, administrator, viewer.id)
    await offboardMember(request, administrator, outsider.id)
    await archiveKnowledgeSpaceFixture(request, administrator, space)
  }
})

function treeNode(page: import('@playwright/test').Page, title: string) {
  return page.locator('.docs-sidebar .ant-tree-node-content-wrapper').filter({ hasText: title }).first()
}

async function createWorkspaceKnowledgeSpace(request: APIRequestContext, session: E2eSession): Promise<KnowledgeSpaceFixture> {
  const name = uniqueFixtureName('m8-navigation')
  const response = await request.post(`${apiBaseUrl}/knowledge-bases`, {
    headers: bearer(session),
    data: { name, code: name.toLowerCase().replace(/[^a-z0-9]+/g, '-').slice(0, 64), visibility: 'workspace', defaultPermissionLevel: 'view' },
  })
  expect(response.ok()).toBeTruthy()
  const payload = await response.json()
  return { id: payload.space.id, rootItemId: payload.space.rootItemId, homeItemId: payload.space.homeItemId, name, code: payload.space.code }
}

async function createObjectEntry(
  request: APIRequestContext,
  session: E2eSession,
  space: KnowledgeSpaceFixture,
  input: { title: string; targetObjectType: string; targetObjectId: string },
) {
  const response = await request.post(`${apiBaseUrl}/knowledge-bases/${space.id}/items`, {
    headers: bearer(session),
    data: { parentId: space.rootItemId, contentType: 'object_ref', targetTitleStrategy: 'manual', displayMode: 'link', ...input },
  })
  expect(response.ok()).toBeTruthy()
  return await response.json()
}

async function createExternalEntry(request: APIRequestContext, session: E2eSession, space: KnowledgeSpaceFixture, title: string) {
  const response = await request.post(`${apiBaseUrl}/knowledge-bases/${space.id}/items`, {
    headers: bearer(session),
    data: { parentId: space.rootItemId, title, contentType: 'external_link', targetRoute: 'https://example.com/m8', displayMode: 'link' },
  })
  expect(response.ok()).toBeTruthy()
  return await response.json()
}

async function createMember(request: APIRequestContext, session: E2eSession, prefix: string) {
  const username = `${prefix.replace(/[^a-z0-9]/g, '')}${Math.random().toString(36).slice(2, 9)}`
  const password = 'member123456'
  const response = await request.post(`${apiBaseUrl}/admin/users`, {
    headers: bearer(session),
    data: { username, password, displayName: prefix, email: `${username}@example.com`, roleCode: 'member' },
  })
  expect(response.ok()).toBeTruthy()
  const payload = await response.json()
  return { id: payload.id as string, username, password }
}

async function grantResourcePermission(
  request: APIRequestContext,
  session: E2eSession,
  resourceType: 'knowledge_base' | 'knowledge_content',
  resourceId: string,
  userId: string,
  permissionLevel: 'view' | 'edit',
) {
  const response = await request.post(`${apiBaseUrl}/resource-permissions/${resourceType}/${resourceId}`, {
    headers: bearer(session),
    data: { subjectType: 'user', subjectId: userId, permissionLevel, confirmHighRisk: true },
  })
  expect(response.ok()).toBeTruthy()
}

async function offboardMember(request: APIRequestContext, session: E2eSession, userId: string) {
  await request.post(`${apiBaseUrl}/admin/users/${userId}/offboard`, { headers: bearer(session), data: {} })
}
