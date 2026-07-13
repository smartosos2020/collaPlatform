import { expect, test } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, webBaseUrl } from './support/api'
import { archiveKnowledgeSpaceFixture, createKnowledgeSpaceFixture } from './support/fixtures'
import {
  createKnowledgeItem,
  currentUser,
  getKnowledgeContent,
  grantKnowledgePermission,
  knowledgeCollaborationHealth,
  knowledgeContentUrl,
} from './support/knowledge'
import { roleCredential } from './support/roles'

test('@smoke knowledge content never reveals a cross-space item to an otherwise authorized user', async ({ request }) => {
  const session = await loginByApi(request)
  const first = await createKnowledgeSpaceFixture(request, session, 'cross-space-first')
  const second = await createKnowledgeSpaceFixture(request, session, 'cross-space-second')
  const secretTitle = 'M3 Cross Space Secret'

  try {
    const document = await createKnowledgeItem(request, session, first, {
      title: secretTitle,
      contentType: 'markdown',
      content: 'This content must not cross the knowledge-space boundary.',
    })
    const anonymous = await request.get(`${apiBaseUrl}/knowledge-bases/${first.id}/items/${document.item.id}`)
    expect([401, 403]).toContain(anonymous.status())

    const wrongSpace = await request.get(`${apiBaseUrl}/knowledge-bases/${second.id}/items/${document.item.id}`, {
      headers: bearer(session),
    })
    expect([403, 404]).toContain(wrongSpace.status())
    expect(await wrongSpace.text()).not.toContain(secretTitle)
    expect(await wrongSpace.text()).not.toContain(document.item.id)
  } finally {
    await archiveKnowledgeSpaceFixture(request, session, first)
    await archiveKnowledgeSpaceFixture(request, session, second)
  }
})

test('@route-final configured editor sees presence, receives remote content and can trace a mention notification', async ({ browser, page, request }) => {
  const editor = roleCredential('editor')
  test.skip(!editor, 'requires COLLA_E2E_EDITOR_USERNAME and COLLA_E2E_EDITOR_PASSWORD')
  const administrator = await loginByApi(request)
  const editorSession = await loginByApi(request, editor!.username, editor!.password)
  const editorUser = await currentUser(request, editorSession)
  const space = await createKnowledgeSpaceFixture(request, administrator, 'two-user-collaboration')
  const editorContext = await browser.newContext({ baseURL: webBaseUrl })
  const editorPage = await editorContext.newPage()

  try {
    const document = await createKnowledgeItem(request, administrator, space, {
      title: 'M3 Shared Content',
      contentType: 'markdown',
      content: 'Shared collaboration content',
    })
    await grantKnowledgePermission(request, administrator, space.id, document.item.id, {
      subjectType: 'user',
      subjectId: editorUser.id,
      permissionLevel: 'edit',
    })

    await installSession(page, administrator)
    await installSession(editorPage, editorSession)
    await page.goto(knowledgeContentUrl(space.id, document.item.id))
    await editorPage.goto(knowledgeContentUrl(space.id, document.item.id))
    await page.getByRole('button', { name: '切换到兼容编辑器' }).click()
    await editorPage.getByRole('button', { name: '切换到兼容编辑器' }).click()

    await expect.poll(async () => (await knowledgeCollaborationHealth(request, administrator, space.id, document.item.id)).activeUsers, {
      timeout: 15_000,
      message: 'two browser identities should join the same collaboration room',
    }).toBeGreaterThanOrEqual(2)

    await editorPage.getByLabel('知识内容标题').fill('M3 Remote Editor Update')
    await expect(page.getByLabel('知识内容标题')).toHaveValue('M3 Remote Editor Update', { timeout: 15_000 })

    const commentResponse = await request.post(`${apiBaseUrl}/knowledge-bases/${space.id}/items/${document.item.id}/comments`, {
      headers: bearer(administrator),
      data: { content: `@${editorUser.username} M3 mention notification`, anchorType: 'document' },
    })
    expect(commentResponse.ok()).toBeTruthy()
    await expect.poll(async () => {
      const response = await request.get(`${apiBaseUrl}/notifications?targetType=knowledge_content`, { headers: bearer(editorSession) })
      const notifications = await response.json() as Array<{ body?: string; targetId?: string }>
      return notifications.some((notification) => notification.targetId === document.item.id && notification.body?.includes('M3 mention notification'))
    }, { timeout: 15_000 }).toBeTruthy()
  } finally {
    await editorContext.close()
    await archiveKnowledgeSpaceFixture(request, administrator, space)
  }
})

test('@route-final configured sharing subjects receive explicit permission records and readonly users cannot write', async ({ request }) => {
  const viewer = roleCredential('viewer')
  const departmentId = process.env.COLLA_E2E_SHARE_DEPARTMENT_ID
  const groupId = process.env.COLLA_E2E_SHARE_USER_GROUP_ID
  const roleId = process.env.COLLA_E2E_SHARE_ROLE_ID
  test.skip(!viewer || !departmentId || !groupId || !roleId, 'requires viewer credentials and share subject IDs from an isolated route-final environment')
  const administrator = await loginByApi(request)
  const viewerSession = await loginByApi(request, viewer!.username, viewer!.password)
  const viewerUser = await currentUser(request, viewerSession)
  const space = await createKnowledgeSpaceFixture(request, administrator, 'sharing-subjects')

  try {
    const document = await createKnowledgeItem(request, administrator, space, {
      title: 'M3 Sharing Content',
      contentType: 'markdown',
      content: 'Permission target',
    })
    for (const subject of [
      { subjectType: 'user', subjectId: viewerUser.id },
      { subjectType: 'department', subjectId: departmentId! },
      { subjectType: 'user_group', subjectId: groupId! },
      { subjectType: 'role', subjectId: roleId! },
    ]) {
      await grantKnowledgePermission(request, administrator, space.id, document.item.id, {
        ...subject,
        permissionLevel: 'view',
      })
    }
    const detail = await getKnowledgeContent(request, administrator, space.id, document.item.id)
    expect(detail.permissions.filter((permission) => ['user', 'department', 'user_group', 'role'].includes(permission.subjectType)).map((permission) => permission.subjectType)).toEqual(
      expect.arrayContaining(['user', 'department', 'user_group', 'role']),
    )

    const readonlySave = await request.patch(`${apiBaseUrl}/knowledge-bases/${space.id}/items/${document.item.id}`, {
      headers: bearer(viewerSession),
      data: { baseVersionNo: detail.item.currentVersionNo, title: 'Forbidden overwrite', content: 'Forbidden overwrite' },
    })
    expect(readonlySave.status()).toBe(403)
  } finally {
    await archiveKnowledgeSpaceFixture(request, administrator, space)
  }
})
