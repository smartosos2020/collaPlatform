import { expect, test, type APIRequestContext, type BrowserContext, type Page } from '@playwright/test'

import { apiBaseUrl, bearer, loginByApi, webBaseUrl, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture, uniqueFixtureName } from './support/fixtures'

type RealtimeFrame = {
  envelopeVersion?: number
  type?: string
  signalVersion?: number
  eventId?: string
  workspaceId?: string
  audienceType?: string
  recipientId?: string
  objectType?: string
  objectId?: string
  sequenceScope?: string
  sequenceKey?: string
  sequence?: number
  correlationId?: string
  calibrationPath?: string
  payload?: Record<string, unknown>
  instanceId?: string
}

test('@route-final S04 M2 delivers IM, notification, project and security invalidations through both Gateways', async ({
  browser,
  request,
}) => {
  test.setTimeout(180_000)
  requireIsolatedIdentityFixture()

  const administrator = await loginByApi(request)
  const suffix = uniqueFixtureName('s04-m2-signal').toLowerCase().replace(/[^a-z0-9]/g, '').slice(-16)
  const department = await createDepartment(request, administrator, `dept${suffix}`, `S04 M2 Department ${suffix}`)
  const member = await createMember(request, administrator, `member${suffix}`, department.id)
  const outsider = await createMember(request, administrator, `outsider${suffix}`, department.id)
  const memberSession = await loginByApi(request, member.username, member.password)
  const outsiderSession = await loginByApi(request, outsider.username, outsider.password)
  const contexts: BrowserContext[] = []

  try {
    const connections = await Promise.all(Array.from({ length: 2 }, async () => {
      const context = await browser.newContext()
      contexts.push(context)
      const page = await context.newPage()
      await page.goto(webBaseUrl)
      const instanceId = await openRealtimeSocket(page, memberSession.accessToken)
      return { page, instanceId }
    }))

    expect(connections.map(({ instanceId }) => instanceId).sort()).toEqual([
      'event-gateway-a',
      'event-gateway-b',
    ])
    const outsiderContext = await browser.newContext()
    contexts.push(outsiderContext)
    const outsiderPage = await outsiderContext.newPage()
    await outsiderPage.goto(webBaseUrl)
    await openRealtimeSocket(outsiderPage, outsiderSession.accessToken)

    const identityTarget = await createMember(
      request,
      administrator,
      `identity${suffix}`,
      department.id,
    )
    await expectFrames(connections, 'identity.invalidated', (frame) =>
      frame.objectType === 'user' &&
      frame.objectId === identityTarget.id &&
      frame.audienceType === 'workspace' &&
      frame.calibrationPath === '/api/admin/users',
    )

    const conversationResponse = await request.post(`${apiBaseUrl}/conversations`, {
      headers: bearer(administrator),
      data: {
        conversationType: 'group',
        title: `S04 M2 Conversation ${suffix}`,
        memberIds: [member.id],
      },
    })
    expect(conversationResponse.ok(), 'conversation creation should succeed').toBeTruthy()
    const conversation = await conversationResponse.json() as { id: string }

    const messageResponse = await request.post(`${apiBaseUrl}/conversations/${conversation.id}/messages`, {
      headers: bearer(administrator),
      data: {
        messageType: 'text',
        content: `S04 M2 message ${suffix}`,
        clientMessageId: `s04-m2-${suffix}`,
      },
    })
    expect(messageResponse.ok(), 'message creation should succeed').toBeTruthy()
    const message = await messageResponse.json() as { id: string }
    await expectFrames(connections, 'message.created', (frame) =>
      frame.recipientId === member.id &&
      frame.objectType === 'message' &&
      frame.objectId === message.id &&
      frame.audienceType === 'user' &&
      frame.calibrationPath.startsWith(`/api/conversations/${conversation.id}/messages`),
    )
    await expectNoFrame(outsiderPage, 'message.created', message.id)

    const projectResponse = await request.post(`${apiBaseUrl}/projects`, {
      headers: bearer(administrator),
      data: {
        projectKey: `S04${suffix.slice(-6)}`.toUpperCase(),
        name: `S04 M2 Project ${suffix}`,
        description: 'S04 M2 realtime project fixture',
        memberIds: [member.id],
      },
    })
    expect(projectResponse.ok(), 'project creation should succeed').toBeTruthy()
    const project = await projectResponse.json() as { id: string }
    await expectFrames(connections, 'project.changed', (frame) =>
      frame.recipientId === member.id &&
      frame.objectType === 'project' &&
      frame.objectId === project.id &&
      frame.audienceType === 'user' &&
      frame.calibrationPath === `/api/projects/${project.id}`,
    )
    await expectNoFrame(outsiderPage, 'project.changed', project.id)

    const permissionResponse = await request.post(
      `${apiBaseUrl}/resource-permissions/project/${project.id}`,
      {
        headers: bearer(administrator),
        data: {
          subjectType: 'user',
          subjectId: member.id,
          permissionLevel: 'view',
          confirmHighRisk: false,
        },
      },
    )
    expect(permissionResponse.ok(), 'resource permission grant should succeed').toBeTruthy()
    await expectFrames(connections, 'permission.invalidated', (frame) =>
      frame.objectType === 'project' &&
      frame.objectId === project.id &&
      frame.audienceType === 'workspace' &&
      frame.calibrationPath === `/api/resource-permissions/project/${project.id}`,
    )
    await expectFrames(connections, 'notification.created', (frame) =>
      frame.recipientId === member.id &&
      frame.objectType === 'notification' &&
      frame.audienceType === 'user' &&
      frame.calibrationPath === '/api/notifications',
    )
    await expectNoFrame(outsiderPage, 'notification.created', undefined, member.id)

    for (const { page } of connections) {
      const unsafe = await frames(page)
      for (const frame of unsafe.filter(({ envelopeVersion }) => envelopeVersion === 1)) {
        expect(Object.keys(frame.payload ?? {})).not.toEqual(
          expect.arrayContaining(['title', 'body', 'content', 'acl', 'members']),
        )
        expect(frame.eventId).toBeTruthy()
        expect(frame.correlationId).toBeTruthy()
        expect(frame.workspaceId).toBeTruthy()
        expect(frame.sequence).toEqual(expect.any(Number))
        expect(frame.sequenceKey).toBeTruthy()
      }
    }
  } finally {
    await Promise.all(contexts.map((context) => context.close()))
  }
})

async function expectFrames(
  connections: Array<{ page: Page }>,
  type: string,
  predicate: (frame: RealtimeFrame) => boolean,
) {
  const accepted = await Promise.all(connections.map(async ({ page }) => {
    await expect.poll(
      async () => (await frames(page)).filter((frame) => frame.type === type && predicate(frame)),
      { timeout: 30_000, message: `${type} should reach each Gateway-local session` },
    ).toHaveLength(1)
    return (await frames(page)).filter((frame) => frame.type === type && predicate(frame))
  }))
  expect(accepted[0][0].eventId).toBe(accepted[1][0].eventId)
  expect(accepted[0][0].sequence).toBe(accepted[1][0].sequence)
}

async function expectNoFrame(page: Page, type: string, objectId?: string, recipientId?: string) {
  await new Promise((resolve) => setTimeout(resolve, 1_500))
  const unexpected = (await frames(page)).filter(
    (frame) => frame.type === type
      && (!objectId || frame.objectId === objectId)
      && (!recipientId || frame.recipientId === recipientId),
  )
  expect(unexpected, `non-target user must not receive ${type}`).toHaveLength(0)
}

async function openRealtimeSocket(page: Page, accessToken: string) {
  const url = new URL(process.env.COLLA_E2E_WS_BASE_URL ?? webBaseUrl)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  url.pathname = '/ws/events'
  url.search = new URLSearchParams({ token: accessToken }).toString()

  return page.evaluate((socketUrl) => new Promise<string>((resolve, reject) => {
    const state = window as typeof window & {
      __s04RealtimeFrames?: RealtimeFrame[]
      __s04RealtimeSocket?: WebSocket
    }
    state.__s04RealtimeFrames = []
    const socket = new WebSocket(socketUrl)
    state.__s04RealtimeSocket = socket
    const timer = window.setTimeout(() => reject(new Error('connection.ready timed out')), 15_000)
    socket.addEventListener('message', (event) => {
      const frame = JSON.parse(String(event.data)) as RealtimeFrame
      state.__s04RealtimeFrames?.push(frame)
      if (frame.type === 'connection.ready') {
        window.clearTimeout(timer)
        resolve(frame.instanceId ?? '')
      }
    })
    socket.addEventListener('error', () => {
      window.clearTimeout(timer)
      reject(new Error('WebSocket handshake failed'))
    }, { once: true })
  }), url.toString())
}

async function frames(page: Page): Promise<RealtimeFrame[]> {
  return page.evaluate(() => {
    const state = window as typeof window & { __s04RealtimeFrames?: RealtimeFrame[] }
    return state.__s04RealtimeFrames ?? []
  })
}

async function createDepartment(request: APIRequestContext, session: E2eSession, code: string, name: string) {
  const response = await request.post(`${apiBaseUrl}/admin/departments`, {
    headers: bearer(session),
    data: { code, name, sortOrder: 0 },
  })
  expect(response.ok(), 'department creation should succeed').toBeTruthy()
  return await response.json() as { id: string }
}

async function createMember(
  request: APIRequestContext,
  session: E2eSession,
  username: string,
  primaryDepartmentId: string,
) {
  const password = ['member', '123456'].join('')
  const response = await request.post(`${apiBaseUrl}/admin/users`, {
    headers: bearer(session),
    data: {
      username,
      password,
      displayName: `S04 M2 Member ${username.slice(-8)}`,
      email: `${username}@colla.local`,
      roleCode: 'member',
      primaryDepartmentId,
    },
  })
  expect(response.ok(), 'member creation should succeed').toBeTruthy()
  const payload = await response.json() as { id: string }
  return { ...payload, username, password }
}
