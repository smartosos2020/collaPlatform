import { expect, test, type BrowserContext, type Locator, type Page } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, webBaseUrl } from './support/api'
import { archiveKnowledgeSpaceFixture, createKnowledgeSpaceFixture } from './support/fixtures'
import {
  createKnowledgeItem,
  currentUser,
  getKnowledgeContent,
  grantKnowledgePermission,
  knowledgeContentUrl,
  listKnowledgeVersions,
} from './support/knowledge'

test('@smoke @kb-product-m5 two isolated users converge and permission downgrade becomes readonly', async ({ browser, request }) => {
  test.setTimeout(90_000)
  const administrator = await loginByApi(request)
  const administratorUser = await currentUser(request, administrator)
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
  const memberUsername = `m5_collab_${suffix}`
  const memberPassword = 'member123456'
  const memberResponse = await request.post(`${apiBaseUrl}/admin/users`, {
    headers: bearer(administrator),
    data: {
      username: memberUsername,
      password: memberPassword,
      displayName: 'M5 Collaboration Member',
      email: `${memberUsername}@colla.local`,
      roleCode: 'member',
    },
  })
  expect(memberResponse.ok(), 'collaboration member fixture creation failed').toBeTruthy()
  const member = await memberResponse.json() as { id: string }
  const memberSession = await loginByApi(request, memberUsername, memberPassword)
  const space = await createKnowledgeSpaceFixture(request, administrator, 'product-m5-realtime')
  let ownerContext: BrowserContext | undefined
  let memberContext: BrowserContext | undefined

  try {
    const content = await createKnowledgeItem(request, administrator, space, {
      title: 'M5 realtime entry',
      contentType: 'markdown',
      content: 'Shared seed',
    })
    const spacePermission = await request.post(`${apiBaseUrl}/resource-permissions/knowledge_base/${space.id}`, {
      headers: bearer(administrator),
      data: { subjectType: 'user', subjectId: member.id, permissionLevel: 'view', confirmHighRisk: false },
    })
    expect(spacePermission.ok(), 'member knowledge-base permission grant failed').toBeTruthy()
    await grantKnowledgePermission(request, administrator, space.id, content.item.id, {
      subjectType: 'user', subjectId: member.id, permissionLevel: 'edit',
    })
    const versionsBeforeCollaboration = await listKnowledgeVersions(request, administrator, space.id, content.item.id)

    ownerContext = await browser.newContext({ baseURL: webBaseUrl })
    memberContext = await browser.newContext({ baseURL: webBaseUrl })
    const ownerPage = await ownerContext.newPage()
    const memberPage = await memberContext.newPage()
    captureBrowserErrors(ownerPage, 'owner')
    captureBrowserErrors(memberPage, 'member')
    await installSession(ownerPage, administrator)
    await installSession(memberPage, memberSession)
    await Promise.all([
      ownerPage.goto(knowledgeContentUrl(space.id, content.item.id)),
      memberPage.goto(knowledgeContentUrl(space.id, content.item.id)),
    ])
    await expect(ownerPage.getByText('实时已同步')).toBeVisible({ timeout: 20_000 })
    await expect(memberPage.getByText('实时已同步')).toBeVisible({ timeout: 20_000 })

    await memberPage.getByLabel('知识内容标题').fill('M5 shared realtime title')
    await expect(ownerPage.getByLabel('知识内容标题')).toHaveValue('M5 shared realtime title', { timeout: 10_000 })

    const ownerEditor = ownerPage.locator('.doc-prosemirror[role="textbox"]')
    const memberEditor = memberPage.locator('.doc-prosemirror[role="textbox"]')
    await Promise.all([
      appendText(ownerEditor, ' owner-concurrent'),
      appendText(memberEditor, ' member-concurrent'),
    ])
    for (const editor of [ownerEditor, memberEditor]) {
      await expect(editor).toContainText('owner-concurrent', { timeout: 10_000 })
      await expect(editor).toContainText('member-concurrent', { timeout: 10_000 })
    }

    await memberEditor.getByText(/Shared seed/).dblclick({ position: { x: 18, y: 8 } })
    await expect(ownerEditor.locator('.ProseMirror-yjs-selection')).toBeVisible({ timeout: 10_000 })
    await memberEditor.press(process.platform === 'darwin' ? 'Meta+B' : 'Control+B')
    await expect(ownerEditor.locator('strong').first()).toBeVisible({ timeout: 10_000 })

    await ownerEditor.click()
    await ownerEditor.press(process.platform === 'darwin' ? 'Meta+End' : 'Control+End')
    await ownerEditor.press('Enter')
    await ownerEditor.pressSequentially('temporary-move-delete-block')
    await expect(memberEditor).toContainText('temporary-move-delete-block', { timeout: 10_000 })
    await operateBlock(ownerPage, 'temporary-move-delete-block', '上移')
    await expect(memberEditor).toContainText('temporary-move-delete-block', { timeout: 10_000 })
    await operateBlock(ownerPage, 'temporary-move-delete-block', '删除块')
    await expect(memberEditor).not.toContainText('temporary-move-delete-block', { timeout: 10_000 })

    await expect.poll(async () => {
      const stored = await getKnowledgeContent(request, administrator, space.id, content.item.id)
      return {
        title: stored.item.title,
        owner: stored.blocks.some((block) => block.content.includes('owner-concurrent')),
        member: stored.blocks.some((block) => block.content.includes('member-concurrent')),
        deleted: stored.blocks.some((block) => block.content.includes('temporary-move-delete-block')),
      }
    }, { timeout: 20_000 }).toEqual({ title: 'M5 shared realtime title', owner: true, member: true, deleted: false })
    const versionsAfterCollaboration = await listKnowledgeVersions(request, administrator, space.id, content.item.id)
    expect(versionsAfterCollaboration).toHaveLength(versionsBeforeCollaboration.length)

    await grantKnowledgePermission(request, administrator, space.id, content.item.id, {
      subjectType: 'user', subjectId: member.id, permissionLevel: 'view',
    })
    await expect(memberPage.getByLabel('知识内容标题')).toBeDisabled({ timeout: 10_000 })
    await expect(memberEditor).toHaveAttribute('contenteditable', 'false', { timeout: 10_000 })

    const forbidden = await request.post(`${apiBaseUrl}/knowledge-bases/${space.id}/items/${content.item.id}/collaboration/ticket`, {
      headers: bearer(memberSession),
    })
    expect(forbidden.ok()).toBeTruthy()
    expect((await forbidden.json() as { canEdit: boolean }).canEdit).toBeFalsy()
  } finally {
    await ownerContext?.close()
    await memberContext?.close()
    await archiveKnowledgeSpaceFixture(request, administrator, space)
    await request.post(`${apiBaseUrl}/admin/users/${member.id}/offboard`, {
      headers: bearer(administrator),
      data: { handoverToUserId: administratorUser.id },
    })
  }
})

async function appendText(editor: Locator, text: string) {
  await editor.click()
  await editor.press(process.platform === 'darwin' ? 'Meta+End' : 'Control+End')
  await editor.pressSequentially(text)
}

function captureBrowserErrors(page: Page, actor: string) {
  page.on('console', async (message) => {
    if (message.type() !== 'error') return
    const values = await Promise.all(message.args().map(async (argument) => {
      try {
        return await argument.jsonValue()
      } catch {
        return argument.toString()
      }
    }))
    console.error(`[browser:${actor}]`, message.text(), ...values)
  })
}

async function operateBlock(page: Page, text: string, operation: '上移' | '删除块') {
  const block = page.locator('.doc-prosemirror > *').filter({ hasText: text }).first()
  await block.hover()
  const handle = page.getByRole('button', { name: '操作块' })
  await expect(handle).toBeVisible()
  await handle.click()
  await page.getByRole('menuitem', { name: operation }).click()
}
