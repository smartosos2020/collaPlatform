import { expect, test, type APIRequestContext, type APIResponse } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = { id: string; username: string; displayName: string; password: string }
type Plan = {
  plan: { id: string; version: number }
  milestones: Array<{ id: string }>
}
type Register = { entry: { id: string } }
type Delivery = {
  deliverable: {
    id: string
    title: string
    summary: string
    status: string
    ownerUserId: string | null
    dueDate: string | null
    version: number
    planId: string | null
    milestoneId: string | null
    registerEntryIds: string[]
  }
  versions: Array<{
    id: string
    sequence: number
    label: string
    note: string
    materials: unknown[]
  }>
  reviews: Array<{
    id: string
    round: number
    reviewItems: string[]
    requiredSignerIds: string[]
    quorum: number
    status: string
    signoffs: Array<{
      signerId: string
      conclusion: string
      revoked: boolean
    }>
  }>
  acceptances: Array<{ conclusion: string; comment: string }>
  materialsTruncated: boolean
}

test.describe('PROJECT-PLATFORM-S15 M3', () => {
  test('immutable delivery review signoff acceptance closes in a real isolated flow @route-final', async ({
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
    const suffix = `s15m3_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const ownerIdentity = await createIdentity(request, enterprise, `${suffix}_owner`, 'S15 Delivery Owner')
    const adminIdentity = await createIdentity(request, enterprise, `${suffix}_admin`, 'S15 Delivery Admin')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S15 Delivery Member')
    const guestIdentity = await createIdentity(request, enterprise, `${suffix}_guest`, 'S15 Delivery Guest')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S15 Delivery Outsider')
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
      const planBodyValue = planBody(`${suffix}-plan`)
      const plan = await postJson<Plan>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/project-plans`,
        owner,
        planBodyValue,
        planBodyValue.requestId,
      )
      const riskBody = registerBody(`${suffix}-risk`, ownerIdentity.id)
      const risk = await postJson<Register>(
        request,
        `${apiBaseUrl}/project-spaces/${spaceId}/project-register`,
        owner,
        riskBody,
        riskBody.requestId,
      )
      const create = deliveryCreateBody(
        `${suffix}-delivery`,
        `S15 交付包 ${'长名称'.repeat(24)}`,
        ownerIdentity.id,
        plan.plan.id,
        plan.milestones[0].id,
        risk.entry.id,
      )
      const first = await postJson<Delivery>(
        request, deliveryUrl(spaceId), owner, create, create.requestId,
      )
      const replay = await postJson<Delivery>(
        request, deliveryUrl(spaceId), owner, create, create.requestId,
      )
      expect(replay.deliverable.id).toBe(first.deliverable.id)
      expect(replay.deliverable.version).toBe(1)
      expect(first.deliverable.planId).toBe(plan.plan.id)
      expect(first.deliverable.registerEntryIds).toEqual([risk.entry.id])

      for (const session of [owner, spaceAdmin, member, guest]) {
        const rows = await getJson<Array<{ id: string; title: string }>>(
          request, deliveryUrl(spaceId), session,
        )
        expect(rows).toContainEqual(expect.objectContaining({
          id: first.deliverable.id,
          title: first.deliverable.title,
        }))
      }
      for (const session of [outsider, enterprise]) {
        const hidden = await request.get(deliveryUrl(spaceId), {
          headers: bearer(session),
        })
        expect([403, 404]).toContain(hidden.status())
        expect(await hidden.text()).not.toContain(first.deliverable.title)
      }
      const cross = await request.get(
        `${deliveryUrl(otherSpaceId)}/${first.deliverable.id}`,
        { headers: bearer(owner) },
      )
      expect(cross.status()).toBe(404)
      expect(await cross.text()).not.toContain(first.deliverable.title)
      const guestWrite = await request.post(deliveryUrl(spaceId), {
        headers: { ...bearer(guest), 'X-Colla-Request-Id': `${suffix}-guest` },
        data: { ...create, requestId: `${suffix}-guest` },
      })
      expect(guestWrite.status()).toBe(403)

      const concurrent = await Promise.all([
        rawMutate(request, member, spaceId, first, 'update', `${suffix}-update-a`),
        rawMutate(request, member, spaceId, first, 'update', `${suffix}-update-b`),
      ])
      expect(concurrent.map((response) => response.status()).sort()).toEqual([200, 409])
      const winnerResponse = concurrent.find((response) => response.ok())
      if (!winnerResponse) throw new Error('delivery update winner missing')
      let current = await winnerResponse.json() as Delivery

      current = await mutate(
        request, owner, spaceId, current, 'submit_version', `${suffix}-version-1`,
      )
      const versionReplay = await mutate(
        request, owner, spaceId, current, 'submit_version', `${suffix}-version-2`,
      )
      expect(versionReplay.versions).toHaveLength(2)
      current = await mutate(
        request, owner, spaceId, versionReplay, 'withdraw_version', `${suffix}-withdraw`,
      )
      expect(current.deliverable.status).toBe('withdrawn')
      current = await mutate(
        request, owner, spaceId, current, 'submit_version', `${suffix}-version-3`,
      )
      expect(current.versions).toHaveLength(3)
      expect(current.versions.map((version) => version.sequence).sort()).toEqual([1, 2, 3])

      current = await mutate(
        request,
        owner,
        spaceId,
        current,
        'open_review',
        `${suffix}-review`,
        { signerIds: [adminIdentity.id, memberIdentity.id], quorum: 2 },
      )
      expect(current.deliverable.status).toBe('reviewing')
      const signResponses = await Promise.all([
        rawMutate(
          request, spaceAdmin, spaceId, current, 'sign', `${suffix}-sign-admin`,
          { conclusion: 'approve' },
        ),
        rawMutate(
          request, member, spaceId, current, 'sign', `${suffix}-sign-member`,
          { conclusion: 'approve' },
        ),
      ])
      expect(signResponses.map((response) => response.status()).sort()).toEqual([200, 409])
      const signWinner = signResponses.find((response) => response.ok())
      if (!signWinner) throw new Error('signoff winner missing')
      current = await signWinner.json() as Delivery
      const signedIds = new Set(current.reviews[0].signoffs
        .filter((signoff) => !signoff.revoked)
        .map((signoff) => signoff.signerId))
      if (!signedIds.has(adminIdentity.id)) {
        current = await mutate(
          request, spaceAdmin, spaceId, current, 'sign', `${suffix}-sign-admin-retry`,
          { conclusion: 'approve' },
        )
      }
      if (!signedIds.has(memberIdentity.id)) {
        current = await mutate(
          request, member, spaceId, current, 'sign', `${suffix}-sign-member-retry`,
          { conclusion: 'approve' },
        )
      }
      current = await mutate(
        request,
        spaceAdmin,
        spaceId,
        current,
        'revoke_signoff',
        `${suffix}-revoke-admin`,
      )
      current = await mutate(
        request,
        spaceAdmin,
        spaceId,
        current,
        'sign',
        `${suffix}-resign-admin`,
        { conclusion: 'approve' },
      )
      current = await mutate(
        request, owner, spaceId, current, 'close_review', `${suffix}-close`,
      )
      expect(current.deliverable.status).toBe('reviewed')
      expect(current.reviews[0].status).toBe('approved')
      current = await mutate(
        request,
        owner,
        spaceId,
        current,
        'accept',
        `${suffix}-accept`,
        { comment: '范围、材料和会签均满足验收条件' },
      )
      expect(current.deliverable.status).toBe('accepted')
      expect(current.acceptances[0].conclusion).toBe('accepted')
      current = await mutate(
        request, owner, spaceId, current, 'archive', `${suffix}-archive`,
      )
      current = await mutate(
        request, owner, spaceId, current, 'restore', `${suffix}-restore`,
      )
      expect(current.deliverable.status).toBe('draft')
      expect(current.versions).toHaveLength(3)
      expect(current.acceptances).toHaveLength(1)

      await installSession(page, owner)
      await page.goto(`/project-spaces/${spaceId}/work-items`)
      await page
        .getByTestId('project-work-items-secondary-tabs')
        .getByRole('tab', { name: '交付验收', exact: true })
        .click()
      await expect(page.getByTestId('project-delivery-panel')).toBeVisible()
      await page.getByText(current.deliverable.title, { exact: true }).click()
      await expect(page.getByTestId('project-delivery-panel')).toContainText('不可变版本')
      await expect(page.getByTestId('project-delivery-panel')).toContainText('验收结论')
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        expect(await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        )).toBeLessThanOrEqual(1)
      }
      await page.context().setOffline(true)
      const offlineTitle = `离线交付意见 ${suffix}`
      await page.getByLabel('交付物标题').fill(offlineTitle)
      await expect(page.getByLabel('交付物标题')).toHaveValue(offlineTitle)
      await page.context().setOffline(false)
      await page.screenshot({
        path: testInfo.outputPath('s15-delivery-820.png'),
        fullPage: true,
      })

      await installSession(page, guest)
      await page.goto(`/project-spaces/${spaceId}/work-items`)
      await page
        .getByTestId('project-work-items-secondary-tabs')
        .getByRole('tab', { name: '交付验收', exact: true })
        .click()
      await expect(page.getByTestId('project-delivery-panel')).toContainText('当前角色只读')
      await expect(page.getByRole('button', { name: '创建交付物' })).toBeDisabled()
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

function deliveryUrl(spaceId: string) {
  return `${apiBaseUrl}/project-spaces/${spaceId}/deliverables`
}

function deliveryCreateBody(
  requestId: string,
  title: string,
  ownerUserId: string,
  planId: string,
  milestoneId: string,
  registerId: string,
) {
  return {
    schemaVersion: 1,
    requestId,
    title,
    summary: '真实隔离交付物、评审、会签和验收',
    ownerUserId,
    dueDate: '2026-09-30',
    planId,
    milestoneId,
    registerEntryIds: [registerId],
  }
}

function mutationBody(
  current: Delivery,
  operation: string,
  requestId: string,
  options: {
    signerIds?: string[]
    quorum?: number
    conclusion?: string
    comment?: string
  } = {},
) {
  const review = current.reviews[0]
  return {
    schemaVersion: 1,
    requestId,
    expectedVersion: current.deliverable.version,
    operation,
    reason: `e2e ${operation} reason`,
    title: current.deliverable.title,
    summary: current.deliverable.summary,
    ownerUserId: current.deliverable.ownerUserId,
    dueDate: current.deliverable.dueDate,
    versionLabel: `release-${current.versions.length + 1}`,
    versionNote: `immutable ${operation}`,
    materials: operation === 'submit_version' ? [{
      id: crypto.randomUUID(),
      sourceType: 'external',
      sourceId: null,
      externalUri: `https://example.com/${requestId}`,
    }] : [],
    reviewItems: operation === 'open_review' || operation === 'reopen_review'
      ? ['范围符合计划', '物料完整', '验收条件可验证']
      : (review?.reviewItems ?? []),
    requiredSignerIds: options.signerIds ?? review?.requiredSignerIds ?? [],
    quorum: options.quorum ?? review?.quorum ?? 0,
    conclusion: options.conclusion ?? '',
    comment: options.comment ?? `e2e ${operation} comment`,
  }
}

function rawMutate(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  current: Delivery,
  operation: string,
  requestId: string,
  options: { conclusion?: string } = {},
): Promise<APIResponse> {
  return request.post(`${deliveryUrl(spaceId)}/${current.deliverable.id}:mutate`, {
    headers: { ...bearer(session), 'X-Colla-Request-Id': requestId },
    data: mutationBody(current, operation, requestId, options),
  })
}

async function mutate(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  current: Delivery,
  operation: string,
  requestId: string,
  options: {
    signerIds?: string[]
    quorum?: number
    conclusion?: string
    comment?: string
  } = {},
) {
  return postJson<Delivery>(
    request,
    `${deliveryUrl(spaceId)}/${current.deliverable.id}:mutate`,
    session,
    mutationBody(current, operation, requestId, options),
    requestId,
  )
}

function planBody(requestId: string) {
  const phaseId = crypto.randomUUID()
  return {
    schemaVersion: 1,
    requestId,
    name: '交付追踪计划',
    description: 'M3 traceability',
    startDate: '2026-07-01',
    endDate: '2026-09-30',
    phases: [{
      id: phaseId,
      phaseKey: 'delivery',
      name: '交付阶段',
      position: 0,
      startDate: '2026-07-01',
      endDate: '2026-09-30',
      status: 'active',
    }],
    milestones: [{
      id: crypto.randomUUID(),
      phaseId,
      milestoneKey: 'acceptance',
      name: '验收里程碑',
      position: 0,
      targetDate: '2026-09-30',
      status: 'active',
      ownerUserId: null,
    }],
    links: [],
  }
}

function registerBody(requestId: string, ownerUserId: string) {
  return {
    schemaVersion: 1,
    requestId,
    entryType: 'risk',
    title: '交付物完整性风险',
    summary: '跟踪 M3 交付材料完整性',
    ownerUserId,
    dueDate: '2026-09-15',
    probability: 2,
    impact: 4,
    decisionBasis: '',
    changeImpact: '',
    references: [],
    responses: [],
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
      spaceKey: `s15-m3-${kind}-${suffix.replaceAll('_', '-')}`,
      name: `S15 交付 ${kind} ${suffix}`,
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s15-m3-member-${userId}` },
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
