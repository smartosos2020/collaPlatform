import {
  expect,
  test,
  type APIRequestContext,
  type Locator,
  type Page,
} from '@playwright/test'

import {
  apiBaseUrl,
  bearer,
  installSession,
  loginByApi,
  type E2eSession,
} from './support/api'
import {
  archiveKnowledgeSpaceFixture,
  createKnowledgeSpaceFixture,
  requireIsolatedIdentityFixture,
  uniqueFixtureName,
} from './support/fixtures'
import { createKnowledgeItem } from './support/knowledge'

type MemberFixture = {
  id: string
  username: string
  password: string
}

type RealtimeSnapshot = {
  state: string
  instance: string
  calibrationCount: number
}

test(
  '@route-final S04 M3 isolated browser reconnects two tabs and REST-calibrates durable notification and IM changes',
  async ({ context, page, request }, testInfo) => {
    test.setTimeout(120_000)
    requireIsolatedIdentityFixture()
    testInfo.annotations.push({
      type: 'fault-boundary',
      description: 'This spec owns browser offline recovery; Gateway process termination is orchestrated externally.',
    })

    const administrator = await loginByApi(request)
    const fixtureName = uniqueFixtureName('s04-m3-client-recovery')
    const suffix = fixtureName.toLowerCase().replace(/[^a-z0-9]/g, '').slice(-16)
    const member = await createMember(request, administrator, suffix)
    const memberSession = await loginByApi(request, member.username, member.password)
    const conversation = await createConversation(request, administrator, member.id, suffix)
    const project = await createProject(request, administrator, member.id, suffix)
    const knowledgeSpace = await createKnowledgeSpaceFixture(request, administrator, 's04-m3-client-recovery')

    try {
      const knowledgeContent = await createKnowledgeItem(request, administrator, knowledgeSpace, {
        title: `S04 M3 Recovery ${suffix}`,
        contentType: 'markdown',
        content: 'Durable permission change created while the recipient browser is offline.',
      })
      await grantResource(
        request,
        administrator,
        'knowledge_base',
        knowledgeSpace.id,
        member.id,
      )
      await markAllNotificationsRead(request, memberSession)

      await installSession(page, memberSession)
      await page.goto('/notifications')
      await expect(page.getByRole('heading', { name: '通知' })).toBeVisible()

      const messengerPage = await context.newPage()
      await installSession(messengerPage, memberSession)
      await messengerPage.goto(`/im?conversationId=${conversation.id}`)
      await expect(messengerPage.getByRole('heading', { name: conversation.title })).toBeVisible()

      const notificationInitial = await readySnapshot(page)
      const messengerInitial = await readySnapshot(messengerPage)
      expect(notificationInitial.instance).not.toBe('')
      expect(messengerInitial.instance).not.toBe('')

      await context.setOffline(true)
      await expectDiagnosticsState(page, 'degraded')
      await expectDiagnosticsState(messengerPage, 'degraded')

      const offlineMessage = `S04 M3 offline message ${suffix}`
      const offlineIssue = `S04 M3 offline issue ${suffix}`
      await sendMessage(request, administrator, conversation.id, offlineMessage)
      await grantResource(
        request,
        administrator,
        'knowledge_content',
        knowledgeContent.item.id,
        member.id,
      )
      await createIssue(request, administrator, project.id, offlineIssue)

      await expect.poll(
        () => notificationExists(request, memberSession, knowledgeContent.item.id),
        {
          timeout: 30_000,
          message: 'permission notification should be durable before browser connectivity returns',
        },
      ).toBeTruthy()
      await expect.poll(
        () => messageExists(request, memberSession, conversation.id, offlineMessage),
        {
          timeout: 30_000,
          message: 'IM message should be durable before browser connectivity returns',
        },
      ).toBeTruthy()
      await expect.poll(
        () => issueExists(request, memberSession, project.id, offlineIssue),
        {
          timeout: 30_000,
          message: 'project change should be durable before browser connectivity returns',
        },
      ).toBeTruthy()

      await context.setOffline(false)

      const notificationRecovered = await readySnapshot(page)
      const messengerRecovered = await readySnapshot(messengerPage)
      expect(notificationRecovered.calibrationCount).toBeGreaterThan(notificationInitial.calibrationCount)
      expect(messengerRecovered.calibrationCount).toBeGreaterThan(messengerInitial.calibrationCount)
      expect(notificationRecovered.instance).not.toBe('')
      expect(messengerRecovered.instance).not.toBe('')

      const recoveredPermissionNotification = page
        .locator('.notification-card-item')
        .filter({ hasText: '你获得了资源访问权限' })
        .filter({ hasText: '未读' })
        .first()
      await expect(recoveredPermissionNotification).toBeVisible({ timeout: 30_000 })
      await expect(
        messengerPage.locator('.im-message-content').filter({ hasText: offlineMessage }),
      ).toBeVisible({ timeout: 30_000 })

      await expect(page).toHaveURL(/\/notifications$/)
      await expect(messengerPage).toHaveURL(
        new RegExp(`/im\\?conversationId=${conversation.id}$`),
      )
    } finally {
      await context.setOffline(false)
      await archiveKnowledgeSpaceFixture(request, administrator, knowledgeSpace)
    }
  },
)

async function readySnapshot(page: Page): Promise<RealtimeSnapshot> {
  const diagnostics = page.getByTestId('realtime-diagnostics')
  await expect(diagnostics).toHaveAttribute('data-state', 'ready', { timeout: 30_000 })
  await expect(diagnostics).toHaveAttribute('data-instance', /.+/, { timeout: 30_000 })
  return readDiagnostics(diagnostics)
}

async function expectDiagnosticsState(page: Page, state: string) {
  await expect(page.getByTestId('realtime-diagnostics')).toHaveAttribute(
    'data-state',
    state,
    { timeout: 15_000 },
  )
}

async function readDiagnostics(diagnostics: Locator): Promise<RealtimeSnapshot> {
  const [state, instance, calibrationCount] = await Promise.all([
    diagnostics.getAttribute('data-state'),
    diagnostics.getAttribute('data-instance'),
    diagnostics.getAttribute('data-calibration-count'),
  ])
  return {
    state: state ?? '',
    instance: instance ?? '',
    calibrationCount: Number(calibrationCount ?? '0'),
  }
}

async function createMember(
  request: APIRequestContext,
  session: E2eSession,
  suffix: string,
): Promise<MemberFixture> {
  const username = `s04m3_${suffix}`
  const password = 'member123456'
  const response = await request.post(`${apiBaseUrl}/admin/users`, {
    headers: bearer(session),
    data: {
      username,
      password,
      displayName: `S04 M3 Member ${suffix}`,
      email: `${username}@colla.local`,
      roleCode: 'member',
    },
  })
  expect(response.ok(), 'isolated member creation failed').toBeTruthy()
  const payload = await response.json() as { id: string }
  return { id: payload.id, username, password }
}

async function createConversation(
  request: APIRequestContext,
  session: E2eSession,
  memberId: string,
  suffix: string,
) {
  const title = `S04 M3 Recovery Chat ${suffix}`
  const response = await request.post(`${apiBaseUrl}/conversations`, {
    headers: bearer(session),
    data: {
      conversationType: 'group',
      title,
      memberIds: [memberId],
    },
  })
  expect(response.ok(), 'recovery conversation creation failed').toBeTruthy()
  const payload = await response.json() as { id: string }
  return { id: payload.id, title }
}

async function createProject(
  request: APIRequestContext,
  session: E2eSession,
  memberId: string,
  suffix: string,
) {
  const response = await request.post(`${apiBaseUrl}/projects`, {
    headers: bearer(session),
    data: {
      projectKey: `R${suffix.slice(-7)}`.toUpperCase(),
      name: `S04 M3 Recovery Project ${suffix}`,
      description: 'Project signal fixture for browser reconnect calibration.',
      memberIds: [memberId],
    },
  })
  expect(response.ok(), 'recovery project creation failed').toBeTruthy()
  return await response.json() as { id: string }
}

async function createIssue(
  request: APIRequestContext,
  session: E2eSession,
  projectId: string,
  title: string,
) {
  const response = await request.post(`${apiBaseUrl}/projects/${projectId}/issues`, {
    headers: bearer(session),
    data: {
      issueType: 'task',
      title,
      description: 'Created while the browser context is offline.',
      priority: 'medium',
    },
  })
  expect(response.ok(), 'offline project issue creation failed').toBeTruthy()
}

async function sendMessage(
  request: APIRequestContext,
  session: E2eSession,
  conversationId: string,
  content: string,
) {
  const response = await request.post(
    `${apiBaseUrl}/conversations/${conversationId}/messages`,
    {
      headers: bearer(session),
      data: {
        clientMessageId: crypto.randomUUID(),
        messageType: 'text',
        content,
      },
    },
  )
  expect(response.ok(), 'offline IM message creation failed').toBeTruthy()
}

async function grantResource(
  request: APIRequestContext,
  session: E2eSession,
  resourceType: string,
  resourceId: string,
  memberId: string,
) {
  const response = await request.post(
    `${apiBaseUrl}/resource-permissions/${resourceType}/${resourceId}`,
    {
      headers: bearer(session),
      data: {
        subjectType: 'user',
        subjectId: memberId,
        permissionLevel: 'view',
        confirmHighRisk: false,
      },
    },
  )
  expect(response.ok(), `grant ${resourceType} permission failed`).toBeTruthy()
}

async function markAllNotificationsRead(
  request: APIRequestContext,
  session: E2eSession,
) {
  const response = await request.post(`${apiBaseUrl}/notifications/read-all`, {
    headers: bearer(session),
  })
  expect(response.ok(), 'notification baseline reset failed').toBeTruthy()
}

async function notificationExists(
  request: APIRequestContext,
  session: E2eSession,
  targetId: string,
) {
  const response = await request.get(
    `${apiBaseUrl}/notifications?targetType=knowledge_content&limit=100`,
    { headers: bearer(session) },
  )
  if (!response.ok()) return false
  const notifications = await response.json() as Array<{
    notificationType: string
    targetId?: string | null
    readAt?: string | null
  }>
  return notifications.some(
    (item) =>
      item.notificationType === 'resource_permission_granted'
      && item.targetId === targetId
      && !item.readAt,
  )
}

async function messageExists(
  request: APIRequestContext,
  session: E2eSession,
  conversationId: string,
  content: string,
) {
  const response = await request.get(
    `${apiBaseUrl}/conversations/${conversationId}/messages?limit=100`,
    { headers: bearer(session) },
  )
  if (!response.ok()) return false
  const page = await response.json() as { items: Array<{ content: string }> }
  return page.items.some((item) => item.content === content)
}

async function issueExists(
  request: APIRequestContext,
  session: E2eSession,
  projectId: string,
  title: string,
) {
  const response = await request.get(`${apiBaseUrl}/projects/${projectId}/issues`, {
    headers: bearer(session),
  })
  if (!response.ok()) return false
  const issues = await response.json() as Array<{ title: string }>
  return issues.some((item) => item.title === title)
}
