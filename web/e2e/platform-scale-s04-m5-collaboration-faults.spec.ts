import { execFileSync } from 'node:child_process'

import { expect, test, type BrowserContext, type Locator } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, webBaseUrl } from './support/api'
import {
  archiveKnowledgeSpaceFixture,
  createKnowledgeSpaceFixture,
  requireIsolatedIdentityFixture,
} from './support/fixtures'
import {
  createKnowledgeItem,
  currentUser,
  grantKnowledgePermission,
  knowledgeContentUrl,
} from './support/knowledge'

test(
  '@route-final S04 M5 collaboration survives node, Redis and PostgreSQL faults with durable reload',
  async ({ browser, request }) => {
    test.setTimeout(360_000)
    requireIsolatedIdentityFixture()
    const project = requiredEnvironment('COLLA_COMPOSE_PROJECT')
    const administrator = await loginByApi(request)
    const administratorUser = await currentUser(request, administrator)
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const username = `s04m5collab_${suffix}`
    const password = 'member123456'
    const memberResponse = await request.post(`${apiBaseUrl}/admin/users`, {
      headers: bearer(administrator),
      data: {
        username,
        password,
        displayName: `S04 M5 Collaboration ${suffix}`,
        email: `${username}@colla.local`,
        roleCode: 'member',
      },
    })
    expect(memberResponse.ok(), 'collaboration fault member creation failed').toBeTruthy()
    const member = await memberResponse.json() as { id: string }
    const memberSession = await loginByApi(request, username, password)
    const space = await createKnowledgeSpaceFixture(request, administrator, 's04-m5-collaboration-faults')
    const services = {
      postgres: containerId(project, 'postgres'),
      redis: containerId(project, 'redis'),
      collaborationA: containerId(project, 'collaboration-a'),
      collaborationB: containerId(project, 'collaboration-b'),
    }
    let ownerContext: BrowserContext | undefined
    let memberContext: BrowserContext | undefined

    try {
      const content = await createKnowledgeItem(request, administrator, space, {
        title: `S04 M5 durable collaboration ${suffix}`,
        contentType: 'markdown',
        content: 'Durable fault seed',
      })
      const spacePermission = await request.post(
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
      expect(spacePermission.ok()).toBeTruthy()
      await grantKnowledgePermission(request, administrator, space.id, content.item.id, {
        subjectType: 'user',
        subjectId: member.id,
        permissionLevel: 'edit',
      })

      const acceptedBaselineA = acceptedConnections(services.collaborationA)
      const acceptedBaselineB = acceptedConnections(services.collaborationB)
      ownerContext = await browser.newContext({ baseURL: webBaseUrl })
      memberContext = await browser.newContext({ baseURL: webBaseUrl })
      const ownerPage = await ownerContext.newPage()
      const memberPage = await memberContext.newPage()
      await installSession(ownerPage, administrator)
      await installSession(memberPage, memberSession)
      await Promise.all([
        ownerPage.goto(knowledgeContentUrl(space.id, content.item.id)),
        memberPage.goto(knowledgeContentUrl(space.id, content.item.id)),
      ])
      await expect(ownerPage.getByText('实时已同步')).toBeVisible({ timeout: 30_000 })
      await expect(memberPage.getByText('实时已同步')).toBeVisible({ timeout: 30_000 })
      await expect.poll(
        () => acceptedConnections(services.collaborationA),
        { timeout: 30_000, message: 'collaboration-a must own a real browser connection' },
      ).toBeGreaterThan(acceptedBaselineA)
      await expect.poll(
        () => acceptedConnections(services.collaborationB),
        { timeout: 30_000, message: 'collaboration-b must own a real browser connection' },
      ).toBeGreaterThan(acceptedBaselineB)

      const acceptedBeforeFailoverB = acceptedConnections(services.collaborationB)
      stopContainer(services.collaborationA)
      await expect.poll(
        () => acceptedConnections(services.collaborationB),
        { timeout: 45_000, message: 'the client on collaboration-a must reconnect to collaboration-b with a fresh ticket' },
      ).toBeGreaterThan(acceptedBeforeFailoverB)
      await expect(ownerPage.getByText('实时已同步')).toBeVisible({ timeout: 45_000 })
      await expect(memberPage.getByText('实时已同步')).toBeVisible({ timeout: 45_000 })
      const nodeExitText = ` node-exit-${suffix}`
      await appendText(ownerPage.locator('.doc-prosemirror[role="textbox"]'), nodeExitText)
      await expect(memberPage.locator('.doc-prosemirror[role="textbox"]'))
        .toContainText(nodeExitText.trim(), { timeout: 30_000 })
      startContainer(services.collaborationA)
      await waitForHealth(services.collaborationA, 'healthy', 90_000)

      stopContainer(services.redis)
      await waitForHealth(services.collaborationA, 'unhealthy', 60_000)
      await waitForHealth(services.collaborationB, 'unhealthy', 60_000)
      const redisText = ` redis-outage-${suffix}`
      await appendText(ownerPage.locator('.doc-prosemirror[role="textbox"]'), redisText)
      startContainer(services.redis)
      await waitForHealth(services.redis, 'healthy', 60_000)
      await waitForHealth(services.collaborationA, 'healthy', 90_000)
      await waitForHealth(services.collaborationB, 'healthy', 90_000)
      await expect(memberPage.locator('.doc-prosemirror[role="textbox"]'))
        .toContainText(redisText.trim(), { timeout: 45_000 })

      stopContainer(services.postgres)
      await waitForHealth(services.collaborationA, 'unhealthy', 60_000)
      await waitForHealth(services.collaborationB, 'unhealthy', 60_000)
      const postgresText = ` postgres-outage-${suffix}`
      await appendText(ownerPage.locator('.doc-prosemirror[role="textbox"]'), postgresText)
      await expect(memberPage.locator('.doc-prosemirror[role="textbox"]'))
        .toContainText(postgresText.trim(), { timeout: 30_000 })
      await expect.poll(
        () => durableQueued(services.collaborationA) + durableQueued(services.collaborationB),
        {
          timeout: 45_000,
          message: 'the PostgreSQL outage update must enter a bounded durable queue before recovery starts',
        },
      ).toBeGreaterThanOrEqual(1)

      startContainer(services.postgres)
      await waitForHealth(services.postgres, 'healthy', 90_000)
      await waitForService(project, 'api-a', 'healthy', 120_000)
      await waitForService(project, 'api-b', 'healthy', 120_000)
      await waitForHealth(services.collaborationA, 'healthy', 120_000)
      await waitForHealth(services.collaborationB, 'healthy', 120_000)
      await expect.poll(
        () => durableRecovered(services.collaborationA) + durableRecovered(services.collaborationB),
        {
          timeout: 90_000,
          message: 'a collaboration node must replay its bounded durable queue after PostgreSQL recovery',
        },
      ).toBeGreaterThanOrEqual(1)

      await ownerContext.close()
      await memberContext.close()
      ownerContext = undefined
      memberContext = undefined
      restartContainer(services.collaborationA)
      restartContainer(services.collaborationB)
      await waitForHealth(services.collaborationA, 'healthy', 120_000)
      await waitForHealth(services.collaborationB, 'healthy', 120_000)

      ownerContext = await browser.newContext({ baseURL: webBaseUrl })
      const reloadPage = await ownerContext.newPage()
      await installSession(reloadPage, administrator)
      await reloadPage.goto(knowledgeContentUrl(space.id, content.item.id))
      await expect(reloadPage.getByText('实时已同步')).toBeVisible({ timeout: 30_000 })
      const reloadedEditor = reloadPage.locator('.doc-prosemirror[role="textbox"]')
      await expect(reloadedEditor).toContainText(nodeExitText.trim(), { timeout: 30_000 })
      await expect(reloadedEditor).toContainText(redisText.trim(), { timeout: 30_000 })
      await expect(reloadedEditor).toContainText(postgresText.trim(), { timeout: 30_000 })
    } finally {
      await ownerContext?.close()
      await memberContext?.close()
      for (const container of Object.values(services)) ensureContainerRunning(container)
      await waitForHealth(services.postgres, 'healthy', 90_000)
      await waitForHealth(services.redis, 'healthy', 60_000)
      await waitForService(project, 'api-a', 'healthy', 120_000)
      await waitForService(project, 'api-b', 'healthy', 120_000)
      await waitForHealth(services.collaborationA, 'healthy', 120_000)
      await waitForHealth(services.collaborationB, 'healthy', 120_000)
      await archiveKnowledgeSpaceFixture(request, administrator, space)
      await request.post(`${apiBaseUrl}/admin/users/${member.id}/offboard`, {
        headers: bearer(administrator),
        data: { handoverToUserId: administratorUser.id },
      })
    }
  },
)

async function appendText(editor: Locator, text: string) {
  await expect(editor).toHaveAttribute('contenteditable', 'true')
  await editor.click()
  await editor.press(process.platform === 'darwin' ? 'Meta+End' : 'Control+End')
  await editor.pressSequentially(text)
  await expect(editor).toContainText(text.trim())
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

function restartContainer(container: string) {
  execFileSync('docker', ['restart', '--timeout', '10', container], { stdio: 'ignore' })
}

function ensureContainerRunning(container: string) {
  const running = execFileSync(
    'docker',
    ['inspect', '--format', '{{.State.Running}}', container],
    { encoding: 'utf8' },
  ).trim()
  if (running !== 'true') startContainer(container)
}

function containerHealth(container: string) {
  return execFileSync(
    'docker',
    ['inspect', '--format', '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}', container],
    { encoding: 'utf8' },
  ).trim()
}

function acceptedConnections(container: string) {
  return Number(collaborationMetrics(container).acceptedConnections ?? 0)
}

function durableRecovered(container: string) {
  const metrics = collaborationMetrics(container)
  return Number((metrics.durableQueue as { recoveredUpdates?: number } | undefined)?.recoveredUpdates ?? 0)
}

function durableQueued(container: string) {
  const metrics = collaborationMetrics(container)
  return Number((metrics.durableQueue as { updates?: number } | undefined)?.updates ?? 0)
}

function collaborationMetrics(container: string) {
  const script = [
    "fetch('http://127.0.0.1:1234/metrics',{headers:{'x-colla-collaboration-secret':process.env.COLLA_COLLABORATION_INTERNAL_SECRET}})",
    '.then(r=>r.json()).then(v=>process.stdout.write(JSON.stringify(v)))',
  ].join('')
  const payload = execFileSync('docker', ['exec', container, 'node', '-e', script], { encoding: 'utf8' })
  return JSON.parse(payload) as Record<string, unknown>
}

async function waitForHealth(container: string, expected: string, timeoutMs: number) {
  await expect.poll(() => containerHealth(container), { timeout: timeoutMs }).toBe(expected)
}

async function waitForService(project: string, service: string, expected: string, timeoutMs: number) {
  await waitForHealth(containerId(project, service), expected, timeoutMs)
}
