import { execFileSync } from 'node:child_process'

import {
  expect,
  test,
  type APIRequestContext,
  type BrowserContext,
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
  instance: string
  readyCount: number
}

test(
  '@route-final S04 M5 isolated browser survives a real Gateway exit and calibrates four durable domains',
  async ({ context, page, request }, testInfo) => {
    test.setTimeout(180_000)
    requireIsolatedIdentityFixture()
    const composeProject = requiredEnvironment('COLLA_COMPOSE_PROJECT')
    testInfo.annotations.push({
      type: 'fault-boundary',
      description: 'This spec places both business tabs on one Gateway, stops that exact instance, and restores it in finally.',
    })

    const administrator = await loginByApi(request)
    const fixtureName = uniqueFixtureName('s04-m3-client-recovery')
    const suffix = fixtureName.toLowerCase().replace(/[^a-z0-9]/g, '').slice(-16)
    const member = await createMember(request, administrator, suffix)
    const memberSession = await loginByApi(request, member.username, member.password)
    const conversation = await createConversation(request, administrator, member.id, suffix)
    const project = await createProject(request, administrator, member.id, suffix)
    const knowledgeSpace = await createKnowledgeSpaceFixture(request, administrator, 's04-m3-client-recovery')
    let stoppedGateway: string | null = null

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

      const notificationRealtime = observeRealtime(page)
      await installSession(page, memberSession)
      await page.goto('/notifications')
      await expect(page.getByRole('heading', { name: '通知' })).toBeVisible()

      const notificationInitial = await readySnapshot(notificationRealtime)
      expect(notificationInitial.instance).not.toBe('')
      const {
        page: messengerPage,
        observer: messengerRealtime,
        snapshot: messengerInitial,
      } = await openMessengerOnInstance(
        context,
        memberSession,
        conversation.id,
        conversation.title,
        notificationInitial.instance,
      )
      expect(messengerInitial.instance).not.toBe('')
      expect(messengerInitial.instance).toBe(notificationInitial.instance)

      const gatewayService = gatewayServiceName(notificationInitial.instance)
      stoppedGateway = containerId(composeProject, gatewayService)
      stopContainer(stoppedGateway)
      await expect.poll(
        () => notificationRealtime.closedCount,
        { timeout: 30_000, message: 'notification tab socket should close with its Gateway' },
      ).toBeGreaterThanOrEqual(1)
      await expect.poll(
        () => messengerRealtime.closedCount,
        { timeout: 30_000, message: 'messenger tab socket should close with its Gateway' },
      ).toBeGreaterThanOrEqual(1)

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
      await expect.poll(
        () => knowledgePermissionExists(
          request,
          memberSession,
          knowledgeSpace.id,
          knowledgeContent.item.id,
        ),
        {
          timeout: 30_000,
          message: 'permission fact should be readable before the stopped Gateway returns',
        },
      ).toBeTruthy()

      const notificationRecovered = await readySnapshot(
        notificationRealtime,
        notificationInitial.readyCount + 1,
      )
      expect(notificationRecovered.instance).not.toBe('')
      expect(notificationRecovered.instance).not.toBe(notificationInitial.instance)
      const messengerRecovered = await readySnapshot(
        messengerRealtime,
        messengerInitial.readyCount + 1,
      )
      expect(messengerRecovered.instance).not.toBe(messengerInitial.instance)
      expect(messengerRecovered.instance).toBe(notificationRecovered.instance)

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
      if (stoppedGateway) {
        ensureContainerRunning(stoppedGateway)
        await waitForContainerHealth(stoppedGateway, 'healthy', 90_000)
      }
      await archiveKnowledgeSpaceFixture(request, administrator, knowledgeSpace)
      await request.post(`${apiBaseUrl}/admin/users/${member.id}/offboard`, {
        headers: bearer(administrator),
        data: {},
      })
    }
  },
)

type RealtimeObserver = {
  readyInstances: string[]
  closedCount: number
}

async function openMessengerOnInstance(
  context: BrowserContext,
  session: E2eSession,
  conversationId: string,
  conversationTitle: string,
  expectedInstance: string,
) {
  const ballastPages: Page[] = []
  try {
    for (let attempt = 0; attempt < 4; attempt += 1) {
      const candidate = await context.newPage()
      const observer = observeRealtime(candidate)
      await installSession(candidate, session)
      await candidate.goto(`/im?conversationId=${conversationId}`)
      await expect(candidate.getByRole('heading', { name: conversationTitle })).toBeVisible()
      const snapshot = await readySnapshot(observer)
      if (snapshot.instance === expectedInstance) {
        await Promise.all(ballastPages.map((page) => page.close()))
        return { page: candidate, observer, snapshot }
      }
      ballastPages.push(candidate)
    }
  } catch (error) {
    await Promise.all(ballastPages.map((page) => page.close()))
    throw error
  }
  await Promise.all(ballastPages.map((page) => page.close()))
  throw new Error(`Could not place messenger tab on Gateway ${expectedInstance}`)
}

function observeRealtime(page: Page): RealtimeObserver {
  const observer: RealtimeObserver = { readyInstances: [], closedCount: 0 }
  page.on('websocket', (socket) => {
    if (!socket.url().includes('/ws/events')) {
      return
    }
    socket.on('close', () => {
      observer.closedCount += 1
    })
    socket.on('framereceived', (frame) => {
      if (typeof frame.payload !== 'string') {
        return
      }
      try {
        const payload = JSON.parse(frame.payload) as {
          type?: unknown
          instanceId?: unknown
        }
        if (payload.type === 'connection.ready' && typeof payload.instanceId === 'string') {
          observer.readyInstances.push(payload.instanceId)
        }
      } catch {
        // Malformed frames are asserted by the client contract tests.
      }
    })
  })
  return observer
}

async function readySnapshot(
  observer: RealtimeObserver,
  minimumReadyCount = 1,
): Promise<RealtimeSnapshot> {
  await expect.poll(
    () => observer.readyInstances.length,
    {
      timeout: 30_000,
      message: `expected at least ${minimumReadyCount} connection.ready frames`,
    },
  ).toBeGreaterThanOrEqual(minimumReadyCount)
  const readyCount = observer.readyInstances.length
  return {
    instance: observer.readyInstances[readyCount - 1] ?? '',
    readyCount,
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

async function knowledgePermissionExists(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  itemId: string,
) {
  const response = await request.get(
    `${apiBaseUrl}/knowledge-bases/${spaceId}/items/${itemId}`,
    { headers: bearer(session) },
  )
  return response.ok()
}

function requiredEnvironment(name: string) {
  const value = process.env[name]?.trim()
  if (!value) throw new Error(`${name} is required for the isolated fault-injection spec`)
  return value
}

function gatewayServiceName(instanceId: string) {
  if (instanceId === 'event-gateway-a' || instanceId === 'event-gateway-b') return instanceId
  throw new Error(`Unexpected Gateway instance id: ${instanceId}`)
}

function containerId(project: string, service: string) {
  const result = execFileSync(
    'docker',
    [
      'ps',
      '-aq',
      '--filter',
      `label=com.docker.compose.project=${project}`,
      '--filter',
      `label=com.docker.compose.service=${service}`,
    ],
    { encoding: 'utf8' },
  ).trim()
  if (!result) throw new Error(`Container not found for ${project}/${service}`)
  return result
}

function stopContainer(container: string) {
  execFileSync('docker', ['stop', '--timeout', '10', container], { stdio: 'ignore' })
}

function ensureContainerRunning(container: string) {
  const running = execFileSync(
    'docker',
    ['inspect', '--format', '{{.State.Running}}', container],
    { encoding: 'utf8' },
  ).trim()
  if (running !== 'true') execFileSync('docker', ['start', container], { stdio: 'ignore' })
}

function containerHealth(container: string) {
  return execFileSync(
    'docker',
    ['inspect', '--format', '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}', container],
    { encoding: 'utf8' },
  ).trim()
}

async function waitForContainerHealth(container: string, expected: string, timeoutMs: number) {
  await expect.poll(() => containerHealth(container), { timeout: timeoutMs }).toBe(expected)
}
