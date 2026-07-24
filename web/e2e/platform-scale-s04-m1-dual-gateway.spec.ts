import { expect, test, type APIRequestContext, type BrowserContext, type Page } from '@playwright/test'

import { apiBaseUrl, bearer, loginByApi, webBaseUrl, type E2eSession } from './support/api'
import {
  archiveKnowledgeSpaceFixture,
  createKnowledgeSpaceFixture,
  requireIsolatedIdentityFixture,
  uniqueFixtureName,
} from './support/fixtures'

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
  sequence?: number
  correlationId?: string
  calibrationPath?: string
  instanceId?: string
}

test('@route-final S04 M1 fans one durable signal out through both Gateway nodes exactly once', async ({
  browser,
  request,
}) => {
  test.setTimeout(120_000)
  requireIsolatedIdentityFixture()

  const administrator = await loginByApi(request)
  const suffix = uniqueFixtureName('s04-m1-gateway').toLowerCase().replace(/[^a-z0-9]/g, '').slice(-18)
  const department = await createDepartment(request, administrator, `dept${suffix}`, `S04 M1 Department ${suffix}`)
  const member = await createMember(request, administrator, `member${suffix}`, department.id)
  const memberSession = await loginByApi(request, member.username, member.password)
  const space = await createKnowledgeSpaceFixture(request, administrator, 's04-m1-gateway')
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

    expect(connections.map((connection) => connection.instanceId).sort()).toEqual([
      'event-gateway-a',
      'event-gateway-b',
    ])

    const response = await request.post(
      `${apiBaseUrl}/resource-permissions/knowledge_base/${space.id}`,
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
    expect(response.ok(), 'knowledge-base permission grant should create the durable notification signal').toBeTruthy()

    const frames = await Promise.all(connections.map(async ({ page }) => {
      await expect.poll(
        () => matchingFrames(page, member.id),
        { timeout: 30_000, message: 'each Gateway-local browser session should receive the notification signal' },
      ).toHaveLength(1)
      return matchingFrames(page, member.id)
    }))

    await new Promise((resolve) => setTimeout(resolve, 2_000))
    const settledFrames = await Promise.all(connections.map(({ page }) => matchingFrames(page, member.id)))
    settledFrames.forEach((items) => expect(items).toHaveLength(1))
    expect(frames[0][0].eventId).toBeTruthy()
    expect(frames[1][0].eventId).toBe(frames[0][0].eventId)
    expect(frames[1][0].sequence).toBe(frames[0][0].sequence)
    expect(frames[0][0]).toMatchObject({
      envelopeVersion: 1,
      type: 'notification.created',
      signalVersion: 1,
      audienceType: 'user',
      recipientId: member.id,
      objectType: 'notification',
      calibrationPath: '/api/notifications',
    })
  } finally {
    await Promise.all(contexts.map((context) => context.close()))
    await archiveKnowledgeSpaceFixture(request, administrator, space)
  }
})

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

async function matchingFrames(page: Page, recipientId: string): Promise<RealtimeFrame[]> {
  return page.evaluate((expectedRecipientId) => {
    const state = window as typeof window & { __s04RealtimeFrames?: RealtimeFrame[] }
    return (state.__s04RealtimeFrames ?? []).filter(
      (frame) => frame.type === 'notification.created' && frame.recipientId === expectedRecipientId,
    )
  }, recipientId)
}

async function createDepartment(request: APIRequestContext, session: E2eSession, code: string, name: string) {
  const response = await request.post(`${apiBaseUrl}/admin/departments`, {
    headers: bearer(session),
    data: { code, name, sortOrder: 0 },
  })
  expect(response.ok()).toBeTruthy()
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
      displayName: `S04 M1 Member ${username.slice(-8)}`,
      email: `${username}@colla.local`,
      roleCode: 'member',
      primaryDepartmentId,
    },
  })
  expect(response.ok()).toBeTruthy()
  const payload = await response.json() as { id: string }
  return { ...payload, username, password }
}
