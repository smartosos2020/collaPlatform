import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = { id: string; username: string; displayName: string; password: string }
type Rule = { id: string; status: string; version: number; publishedVersion?: number }
type Run = {
  id: string
  status: string
  dryRun: boolean
  steps: Array<{ actionType: string; status: string }>
}

test.describe('PROJECT-PLATFORM-S17 M2', () => {
  test('controlled action execution is permission scoped idempotent observable and responsive', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(480_000)
    page.setDefaultTimeout(25_000)
    requireIsolatedIdentityFixture()
    const enterprise = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(
      request, `${apiBaseUrl}/auth/me`, enterprise,
    )
    const suffix = `s17m2_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterprise, `${suffix}_owner`, 'S17 M2 Owner')
    const adminIdentity = await createIdentity(request, enterprise, `${suffix}_admin`, 'S17 M2 Admin')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S17 M2 Member')
    const guestIdentity = await createIdentity(request, enterprise, `${suffix}_guest`, 'S17 M2 Guest')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S17 M2 Outsider')
    const owner = await loginByApi(request, ownerIdentity.username, ownerIdentity.password)
    const spaceAdmin = await loginByApi(request, adminIdentity.username, adminIdentity.password)
    const member = await loginByApi(request, memberIdentity.username, memberIdentity.password)
    const guest = await loginByApi(request, guestIdentity.username, guestIdentity.password)
    const outsider = await loginByApi(request, outsiderIdentity.username, outsiderIdentity.password)
    let spaceId: string | undefined
    let otherSpaceId: string | undefined

    try {
      spaceId = await createSpace(request, owner, suffix, 'primary')
      otherSpaceId = await createSpace(request, owner, suffix, 'other')
      await addMember(request, owner, spaceId, adminIdentity.id, 'admin')
      await addMember(request, owner, spaceId, memberIdentity.id, 'member')
      await addMember(request, owner, spaceId, guestIdentity.id, 'guest')
      const createId = `${suffix}-create`
      const rule = await postJson<Rule>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/automation/rules`,
        owner,
        ruleBody(createId, memberIdentity.id, suffix),
        createId,
      )
      await postJson(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/automation/rules/${rule.id}/publish`,
        owner,
        {
          schemaVersion: 1,
          requestId: `${suffix}-publish`,
          expectedVersion: 1,
          action: 'publish',
        },
        `${suffix}-publish`,
      )
      const enabled = await postJson<Rule>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/automation/rules/${rule.id}/lifecycle`,
        owner,
        {
          schemaVersion: 1,
          requestId: `${suffix}-enable`,
          expectedVersion: 2,
          action: 'enable',
        },
        `${suffix}-enable`,
      )
      expect(enabled.status).toBe('enabled')

      const executeId = `${suffix}-execute`
      const command = {
        schemaVersion: 1,
        requestId: executeId,
        dryRun: false,
        event: {
          eventType: 'project.work-item.changed',
          aggregateId: crypto.randomUUID(),
          spaceId,
        },
      }
      const first = await postJson<Run>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/automation/rules/${rule.id}/execute`,
        owner,
        command,
        executeId,
      )
      const replay = await postJson<Run>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/automation/rules/${rule.id}/execute`,
        owner,
        command,
        executeId,
      )
      expect(first.status).toBe('succeeded')
      expect(first.steps).toEqual(expect.arrayContaining([
        expect.objectContaining({ actionType: 'send_notification', status: 'succeeded' }),
      ]))
      expect(replay.id).toBe(first.id)

      const preview = await postJson<Run>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/automation/rules/${rule.id}/execute`,
        spaceAdmin,
        { ...command, requestId: `${suffix}-preview`, dryRun: true },
        `${suffix}-preview`,
      )
      expect(preview.dryRun).toBeTruthy()
      expect(preview.steps[0]?.status).toBe('skipped')

      for (const session of [owner, spaceAdmin, member, guest]) {
        const history = await getJson<{ runs: Run[] }>(
          request, `${apiBaseUrl}/project-spaces/${spaceId}/automation/runs`, session,
        )
        expect(history.runs.some((run) => run.id === first.id)).toBeTruthy()
      }
      for (const session of [member, guest]) {
        const denied = await request.post(
          `${apiBaseUrl}/project-spaces/${spaceId}/automation/rules/${rule.id}/execute`,
          {
            headers: { ...bearer(session), 'X-Colla-Request-Id': `${suffix}-denied` },
            data: { ...command, requestId: `${suffix}-denied` },
          },
        )
        expect(denied.status()).toBe(403)
      }
      for (const session of [outsider, enterprise]) {
        const hidden = await request.get(
          `${apiBaseUrl}/project-spaces/${spaceId}/automation/runs`,
          { headers: bearer(session) },
        )
        expect([403, 404]).toContain(hidden.status())
        expect(await hidden.text()).not.toContain(first.id)
      }
      const crossSpace = await getJson<{ runs: Run[] }>(
        request, `${apiBaseUrl}/project-spaces/${otherSpaceId}/automation/runs`, owner,
      )
      expect(crossSpace.runs).toHaveLength(0)

      await expect.poll(async () => {
        const response = await request.get(`${apiBaseUrl}/notifications`, {
          headers: bearer(member),
        })
        if (!response.ok()) return false
        const notifications = await response.json() as Array<{ title: string }>
        return notifications.some((item) => item.title.includes(suffix))
      }, { timeout: 30_000 }).toBeTruthy()

      await installSession(page, owner)
      await page.goto(`/project-spaces/${spaceId}/work-items`)
      await page
        .getByTestId('project-work-items-secondary-tabs')
        .getByRole('tab', { name: '运行记录', exact: true })
        .click()
      await expect(page.getByTestId('automation-execution-panel')).toBeVisible()
      await expect(page.getByTestId('automation-execution-panel')).toContainText(first.id)
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        await expect(page.getByTestId('automation-execution-panel')).toBeVisible()
        expect(await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        )).toBeLessThanOrEqual(1)
      }
      await page.screenshot({
        path: testInfo.outputPath('s17-m2-execution-820.png'),
        fullPage: true,
      })
      await installSession(page, member)
      await page.goto(`/project-spaces/${spaceId}/work-items`)
      await page
        .getByTestId('project-work-items-secondary-tabs')
        .getByRole('tab', { name: '运行记录', exact: true })
        .click()
      await expect(page.getByRole('button', { name: '无副作用预览' })).toBeDisabled()
      await expect(page.getByRole('button', { name: '执行受控操作' })).toBeDisabled()
    } finally {
      for (const id of [spaceId, otherSpaceId]) {
        if (id) {
          await request.post(`${apiBaseUrl}/project-spaces/${id}/settings/archive`, {
            headers: bearer(owner),
          }).catch(() => undefined)
        }
      }
      for (const identity of [
        adminIdentity, memberIdentity, guestIdentity, outsiderIdentity, ownerIdentity,
      ]) {
        await request.post(`${apiBaseUrl}/admin/users/${identity.id}/offboard`, {
          headers: bearer(enterprise),
          data: { handoverToUserId: enterpriseProfile.id },
        }).catch(() => undefined)
      }
    }
  })
})

function ruleBody(requestId: string, recipientId: string, suffix: string) {
  return {
    schemaVersion: 1,
    requestId,
    expectedVersion: 0,
    name: `执行通知 ${suffix}`,
    trigger: {
      schemaVersion: 1,
      type: 'event',
      eventType: 'project.work-item.changed',
      eventVersion: 1,
    },
    condition: {
      schemaVersion: 1,
      kind: 'compare',
      reference: 'event.aggregateId',
      operator: 'exists',
    },
    actions: [{
      schemaVersion: 1,
      actionType: 'send_notification',
      config: {
        recipientId,
        title: `自动化执行 ${suffix}`,
        body: 'M2 controlled notification',
      },
    }],
  }
}

async function createSpace(
  request: APIRequestContext,
  owner: E2eSession,
  suffix: string,
  kind: string,
) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(owner),
    data: {
      spaceKey: `s17-m2-${kind}-${suffix.replaceAll('_', '-')}`,
      name: `S17 M2 ${kind} ${suffix}`,
      visibility: 'private',
    },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  return (await response.json() as { id: string }).id
}

async function addMember(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  userId: string,
  roleKey: string,
) {
  const response = await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/members`, {
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s17-m2-member-${userId}` },
    data: { userId, roleKey },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
}

async function createIdentity(
  request: APIRequestContext,
  administrator: E2eSession,
  username: string,
  displayName: string,
) {
  const password = 'member123456'
  const response = await request.post(`${apiBaseUrl}/admin/users`, {
    headers: bearer(administrator),
    data: {
      username,
      password,
      displayName,
      email: `${username}@example.com`,
      roleCode: 'member',
    },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  return { ...(await response.json() as Omit<Identity, 'password'>), password }
}

async function getJson<T>(
  request: APIRequestContext,
  url: string,
  session: E2eSession,
) {
  const response = await request.get(url, { headers: bearer(session) })
  expect(response.ok(), `GET ${url} failed: ${await response.text()}`).toBeTruthy()
  return await response.json() as T
}

async function postJson<T>(
  request: APIRequestContext,
  url: string,
  session: E2eSession,
  data: unknown,
  requestId: string,
) {
  const response = await request.post(url, {
    headers: { ...bearer(session), 'X-Colla-Request-Id': requestId },
    data,
  })
  expect(response.ok(), `POST ${url} failed: ${await response.text()}`).toBeTruthy()
  return await response.json() as T
}
