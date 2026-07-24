import { execFileSync } from 'node:child_process'

import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

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
} from './support/fixtures'

type MemberFixture = {
  id: string
  username: string
  password: string
}

type RealtimeObserver = {
  readyCount: number
}

test(
  '@route-final S04 M5 Redis outage preserves durable writes and browser recovery',
  async ({ page, request }) => {
    test.setTimeout(180_000)
    requireIsolatedIdentityFixture()
    const composeProject = requiredEnvironment('COLLA_COMPOSE_PROJECT')
    const administrator = await loginByApi(request)
    const suffix = Math.random().toString(36).slice(2, 12)
    const member = await createMember(request, administrator, suffix)
    const memberSession = await loginByApi(request, member.username, member.password)
    const space = await createKnowledgeSpaceFixture(request, administrator, 's04-m5-redis')
    const redisContainer = containerId(composeProject, 'redis')
    const observer = observeRealtime(page)

    try {
      await installSession(page, memberSession)
      await page.goto('/notifications')
      await expect(page.getByRole('heading', { name: '通知' })).toBeVisible()
      await expect.poll(() => observer.readyCount, { timeout: 30_000 }).toBeGreaterThanOrEqual(1)

      stopContainer(redisContainer)
      await expect.poll(
        () => serviceHealth(composeProject, 'event-gateway-a'),
        { timeout: 45_000, message: 'event-gateway-a should expose Redis degradation' },
      ).toBe('unhealthy')
      await expect.poll(
        () => serviceHealth(composeProject, 'event-gateway-b'),
        { timeout: 45_000, message: 'event-gateway-b should expose Redis degradation' },
      ).toBe('unhealthy')

      const grant = await request.post(
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
      expect(grant.ok(), 'durable permission write must not roll back with Redis unavailable').toBeTruthy()
      await expect.poll(
        () => permissionNotificationExists(request, memberSession, space.id),
        {
          timeout: 45_000,
          message: 'permission notification must be durable before Redis returns',
        },
      ).toBeTruthy()

      startContainer(redisContainer)
      await waitForContainerHealth(redisContainer, 'healthy', 60_000)
      await waitForServiceHealth(composeProject, 'event-gateway-a', 'healthy', 90_000)
      await waitForServiceHealth(composeProject, 'event-gateway-b', 'healthy', 90_000)

      await expect(
        page.locator('.notification-card-item')
          .filter({ hasText: '你获得了资源访问权限' })
          .filter({ hasText: '未读' })
          .first(),
      ).toBeVisible({ timeout: 45_000 })
      await expect(page).toHaveURL(/\/notifications$/)
    } finally {
      ensureContainerRunning(redisContainer)
      await waitForContainerHealth(redisContainer, 'healthy', 60_000)
      await archiveKnowledgeSpaceFixture(request, administrator, space)
      await request.post(`${apiBaseUrl}/admin/users/${member.id}/offboard`, {
        headers: bearer(administrator),
        data: {},
      })
    }
  },
)

function observeRealtime(page: Page): RealtimeObserver {
  const observer: RealtimeObserver = { readyCount: 0 }
  page.on('websocket', (socket) => {
    if (!socket.url().includes('/ws/events')) return
    socket.on('framereceived', (frame) => {
      if (typeof frame.payload !== 'string') return
      try {
        const payload = JSON.parse(frame.payload) as { type?: unknown }
        if (payload.type === 'connection.ready') observer.readyCount += 1
      } catch {
        // Protocol parser behavior is covered by the realtime core contract.
      }
    })
  })
  return observer
}

async function createMember(
  request: APIRequestContext,
  session: E2eSession,
  suffix: string,
): Promise<MemberFixture> {
  const username = `s04m5_${suffix}`
  const password = 'member123456'
  const response = await request.post(`${apiBaseUrl}/admin/users`, {
    headers: bearer(session),
    data: {
      username,
      password,
      displayName: `S04 M5 Member ${suffix}`,
      email: `${username}@colla.local`,
      roleCode: 'member',
    },
  })
  expect(response.ok(), 'Redis recovery member creation failed').toBeTruthy()
  const payload = await response.json() as { id: string }
  return { id: payload.id, username, password }
}

async function permissionNotificationExists(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
) {
  const response = await request.get(
    `${apiBaseUrl}/notifications?targetType=knowledge_base&limit=100`,
    { headers: bearer(session) },
  )
  if (!response.ok()) return false
  const items = await response.json() as Array<{ targetId?: string; title?: string }>
  return items.some((item) => item.targetId === spaceId && item.title === '你获得了资源访问权限')
}

function requiredEnvironment(name: string) {
  const value = process.env[name]?.trim()
  if (!value) throw new Error(`${name} is required for the isolated fault-injection spec`)
  return value
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

function startContainer(container: string) {
  execFileSync('docker', ['start', container], { stdio: 'ignore' })
}

function ensureContainerRunning(container: string) {
  const running = execFileSync(
    'docker',
    ['inspect', '--format', '{{.State.Running}}', container],
    { encoding: 'utf8' },
  ).trim()
  if (running !== 'true') startContainer(container)
}

function serviceHealth(project: string, service: string) {
  return containerHealth(containerId(project, service))
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

async function waitForServiceHealth(
  project: string,
  service: string,
  expected: string,
  timeoutMs: number,
) {
  await expect.poll(() => serviceHealth(project, service), { timeout: timeoutMs }).toBe(expected)
}
