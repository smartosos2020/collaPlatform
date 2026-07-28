import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = { id: string; username: string; displayName: string; password: string }
type Rule = {
  id: string
  name: string
  status: string
  version: number
  publishedVersion?: number
}

test.describe('PROJECT-PLATFORM-S17 M1', () => {
  test('declarative rules are versioned bounded permission-scoped and responsive @route-final', async ({
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
    const suffix = `s17m1_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterprise, `${suffix}_owner`, 'S17 Owner')
    const adminIdentity = await createIdentity(request, enterprise, `${suffix}_admin`, 'S17 Admin')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S17 Member')
    const guestIdentity = await createIdentity(request, enterprise, `${suffix}_guest`, 'S17 Guest')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S17 Outsider')
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
      const requestId = `${suffix}-create`
      const body = ruleBody(requestId, `事项变化通知 ${suffix}`)
      const first = await postJson<Rule>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/automation/rules`,
        owner,
        body,
        requestId,
      )
      const replay = await postJson<Rule>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/automation/rules`,
        owner,
        body,
        requestId,
      )
      expect(replay.id).toBe(first.id)
      expect(replay.version).toBe(1)

      const publication = await postJson<{ versionNumber: number; definitionHash: string }>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/automation/rules/${first.id}/publish`,
        owner,
        {
          schemaVersion: 1,
          requestId: `${suffix}-publish`,
          expectedVersion: 1,
          action: 'publish',
        },
        `${suffix}-publish`,
      )
      expect(publication.versionNumber).toBe(1)
      expect(publication.definitionHash).toMatch(/^[0-9a-f]{64}$/)

      for (const session of [owner, spaceAdmin, member, guest]) {
        const visible = await getJson<{
          events: Array<{ eventType: string }>
          actions: Array<{ actionType: string }>
          rules: Rule[]
        }>(
          request,
          `${apiBaseUrl}/project-spaces/${spaceId}/automation`,
          session,
        )
        expect(visible.events.map((event) => event.eventType))
          .toContain('project.work-item.changed')
        expect(visible.actions.map((action) => action.actionType))
          .toContain('send_notification')
        expect(visible.rules[0].name).toContain(suffix)
      }
      for (const session of [outsider, enterprise]) {
        const hidden = await request.get(
          `${apiBaseUrl}/project-spaces/${spaceId}/automation`,
          { headers: bearer(session) },
        )
        expect([403, 404]).toContain(hidden.status())
        expect(await hidden.text()).not.toContain(suffix)
      }
      const crossSpace = await getJson<{ rules: Rule[] }>(
        request,
        `${apiBaseUrl}/project-spaces/${otherSpaceId}/automation`,
        owner,
      )
      expect(crossSpace.rules).toHaveLength(0)

      for (const session of [member, guest]) {
        const denied = await request.post(
          `${apiBaseUrl}/project-spaces/${spaceId}/automation/rules`,
          {
            headers: { ...bearer(session), 'X-Colla-Request-Id': `${suffix}-denied` },
            data: ruleBody(`${suffix}-denied`, `隐藏规则 ${suffix}`),
          },
        )
        expect(denied.status()).toBe(403)
      }

      const concurrent = await Promise.all([
        { session: owner, label: 'a' },
        { session: spaceAdmin, label: 'b' },
      ].map(({ session, label }) => request.post(
        `${apiBaseUrl}/project-spaces/${spaceId}/automation/rules`,
        {
          headers: {
            ...bearer(session),
            'X-Colla-Request-Id': `${suffix}-concurrent-${label}`,
          },
          data: {
            ...ruleBody(`${suffix}-concurrent-${label}`, `并发规则 ${label} ${suffix}`),
            ruleId: first.id,
            expectedVersion: 2,
          },
        },
      )))
      expect(concurrent.map((response) => response.status()).sort()).toEqual([200, 409])

      const unsafe = await request.post(
        `${apiBaseUrl}/project-spaces/${spaceId}/automation/rules`,
        {
          headers: {
            ...bearer(owner),
            'X-Colla-Request-Id': `${suffix}-unsafe`,
          },
          data: {
            ...ruleBody(`${suffix}-unsafe`, `不安全规则 ${suffix}`),
            condition: {
              schemaVersion: 1,
              kind: 'compare',
              reference: 'event.aggregateId',
              operator: 'exists',
              script: 'return true',
            },
          },
        },
      )
      expect(unsafe.status()).toBe(400)

      await installSession(page, owner)
      await page.goto(`/project-spaces/${spaceId}/work-items`)
      await expect(page.getByTestId('automation-rules-panel')).toBeVisible()
      await expect(page.getByTestId('automation-rules-panel')).toContainText(suffix)
      await expect(page.getByLabel('触发事件')).toBeVisible()
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        await expect(page.getByTestId('automation-rules-panel')).toBeVisible()
        expect(await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        )).toBeLessThanOrEqual(1)
      }
      await page.context().setOffline(true)
      const draft = `离线自动化草稿 ${suffix}`
      await page.getByTestId('automation-rule-offline-draft').fill(draft)
      await expect(page.getByTestId('automation-rule-offline-draft')).toHaveValue(draft)
      await expect(page.getByTestId('automation-rules-panel')).toContainText('离线 · 本地输入保留')
      await page.context().setOffline(false)
      await page.screenshot({
        path: testInfo.outputPath('s17-automation-rules-820.png'),
        fullPage: true,
      })

      await installSession(page, member)
      await page.goto(`/project-spaces/${spaceId}/work-items`)
      await expect(page.getByRole('button', { name: '保存规则草稿' })).toBeDisabled()
    } finally {
      await page.context().setOffline(false).catch(() => undefined)
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

function ruleBody(requestId: string, name: string) {
  return {
    schemaVersion: 1,
    requestId,
    expectedVersion: 0,
    name,
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
      config: {},
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
      spaceKey: `s17-m1-${kind}-${suffix.replaceAll('_', '-')}`,
      name: `S17 自动化 ${kind} ${suffix}`,
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s17-m1-member-${userId}` },
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
