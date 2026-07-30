import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type Identity = { id: string; username: string; displayName: string; password: string }
type Item = { id: string; version: number; displayKey: string; title: string }
type Grant = {
  id: string
  status: string
  currentVersion: number
  sourceConfirmed: boolean
  targetConfirmed: boolean
}
type Policy = {
  id: string
  status: string
  version: number
  sourceConfirmedBy?: string
  targetConfirmedBy?: string
}
type Intent = {
  id: string
  status: string
  version: number
  canonicalRelationId?: string
}
type Relation = {
  relationId: string
  sourceSpaceId: string
  sourceWorkItemId: string
  targetSpaceId: string
  targetWorkItemId: string
  status: string
  version: number
}

test.describe('PROJECT-PLATFORM-S18 M2', () => {
  test('dual capability intent creates one canonical edge with minimal disclosure @route-final', async ({
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
    const suffix = `s18m2_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const sourceOwnerIdentity = await createIdentity(request, enterprise, `${suffix}_source`, 'S18 M2 Source Owner')
    const targetOwnerIdentity = await createIdentity(request, enterprise, `${suffix}_target`, 'S18 M2 Target Owner')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S18 M2 Member')
    const guestIdentity = await createIdentity(request, enterprise, `${suffix}_guest`, 'S18 M2 Guest')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S18 M2 Outsider')
    const sourceOwner = await loginByApi(request, sourceOwnerIdentity.username, sourceOwnerIdentity.password)
    const targetOwner = await loginByApi(request, targetOwnerIdentity.username, targetOwnerIdentity.password)
    const member = await loginByApi(request, memberIdentity.username, memberIdentity.password)
    const guest = await loginByApi(request, guestIdentity.username, guestIdentity.password)
    const outsider = await loginByApi(request, outsiderIdentity.username, outsiderIdentity.password)
    let sourceSpaceId: string | undefined
    let targetSpaceId: string | undefined

    try {
      sourceSpaceId = await createSpace(request, sourceOwner, suffix, 'source')
      targetSpaceId = await createSpace(request, targetOwner, suffix, 'target')
      await addMember(request, sourceOwner, sourceSpaceId, memberIdentity.id, 'member')
      await addMember(request, sourceOwner, sourceSpaceId, guestIdentity.id, 'guest')
      const sourceType = await publishedProjectType(request, sourceOwner, sourceSpaceId, `${suffix}-source`)
      const targetType = await publishedProjectType(request, targetOwner, targetSpaceId, `${suffix}-target`)
      const sourceItem = await createItem(
        request, sourceOwner, sourceSpaceId, sourceType.typeId,
        `S18 M2 source secret ${suffix}`, `${suffix}-source-item`,
      )
      const targetItem = await createItem(
        request, targetOwner, targetSpaceId, targetType.typeId,
        `S18 M2 target secret ${suffix}`, `${suffix}-target-item`,
      )
      const concurrentSource = await createItem(
        request, sourceOwner, sourceSpaceId, sourceType.typeId,
        'S18 M2 concurrent source', `${suffix}-concurrent-source`,
      )
      const concurrentTarget = await createItem(
        request, targetOwner, targetSpaceId, targetType.typeId,
        'S18 M2 concurrent target', `${suffix}-concurrent-target`,
      )

      let grant = await createGrant(
        request, sourceOwner, sourceSpaceId, targetSpaceId,
        sourceType, targetType, `${suffix}-grant`,
      )
      grant = await grantLifecycle(
        request, sourceOwner, sourceSpaceId, grant, 'request', undefined, `${suffix}-grant-request`,
      )
      grant = await grantLifecycle(
        request, sourceOwner, sourceSpaceId, grant, 'confirm', 'source', `${suffix}-grant-source`,
      )
      grant = await grantLifecycle(
        request, targetOwner, targetSpaceId, grant, 'confirm', 'target', `${suffix}-grant-target`,
      )
      expect(grant.status).toBe('active')

      let policy = await createPolicy(
        request, sourceOwner, sourceSpaceId, grant.id, 'depends_on',
        'source_to_target', sourceType, targetType, `${suffix}-policy-forward`,
      )
      policy = await policyLifecycle(
        request, sourceOwner, sourceSpaceId, policy, 'request', undefined, `${suffix}-policy-request`,
      )
      policy = await policyLifecycle(
        request, sourceOwner, sourceSpaceId, policy, 'confirm', 'source', `${suffix}-policy-source`,
      )
      policy = await policyLifecycle(
        request, targetOwner, targetSpaceId, policy, 'confirm', 'target', `${suffix}-policy-target`,
      )
      expect(policy.status).toBe('active')

      const reference = await getJson<Record<string, unknown>>(
        request,
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/relation-policies/${policy.id}/endpoints/${targetItem.id}`,
        sourceOwner,
      )
      expect(reference.opaqueReference).toBe(`ref-${targetItem.id.slice(0, 8)}`)
      expect(JSON.stringify(reference)).not.toContain(targetItem.title)
      expect(reference).not.toHaveProperty('title')
      expect(reference).not.toHaveProperty('path')
      expect(reference).not.toHaveProperty('relationCount')

      let intent = await createIntent(
        request, sourceOwner, sourceSpaceId, policy,
        sourceItem, targetItem, `${suffix}-intent`,
      )
      expect(intent.status).toBe('requested')
      intent = await intentLifecycle(
        request, targetOwner, targetSpaceId, intent,
        'accept', undefined, `${suffix}-accept`,
      )
      expect(intent.status).toBe('linked')
      expect(intent.canonicalRelationId).toBeTruthy()
      const relation = await getJson<Relation>(
        request,
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/relation-policies/${policy.id}/relations/${intent.canonicalRelationId}`,
        sourceOwner,
      )
      expect(relation.status).toBe('active')
      expect(relation.sourceWorkItemId).toBe(sourceItem.id)
      expect(relation.targetWorkItemId).toBe(targetItem.id)
      expect(JSON.stringify(relation)).not.toContain(sourceItem.title)
      expect(JSON.stringify(relation)).not.toContain(targetItem.title)

      const duplicate = await rawCreateIntent(
        request, sourceOwner, sourceSpaceId, policy,
        sourceItem, targetItem, `${suffix}-duplicate`,
      )
      expect([409, 422]).toContain(duplicate.status())

      const race = await Promise.all([
        rawCreateIntent(
          request, sourceOwner, sourceSpaceId, policy,
          concurrentSource, concurrentTarget, `${suffix}-race-a`,
        ),
        rawCreateIntent(
          request, sourceOwner, sourceSpaceId, policy,
          concurrentSource, concurrentTarget, `${suffix}-race-b`,
        ),
      ])
      expect(race.filter((response) => response.ok())).toHaveLength(1)

      let reverse = await createPolicy(
        request, sourceOwner, sourceSpaceId, grant.id, 'depends_on',
        'target_to_source', sourceType, targetType, `${suffix}-policy-reverse`,
      )
      reverse = await policyLifecycle(
        request, sourceOwner, sourceSpaceId, reverse, 'request', undefined, `${suffix}-reverse-request`,
      )
      reverse = await policyLifecycle(
        request, sourceOwner, sourceSpaceId, reverse, 'confirm', 'source', `${suffix}-reverse-source`,
      )
      reverse = await policyLifecycle(
        request, targetOwner, targetSpaceId, reverse, 'confirm', 'target', `${suffix}-reverse-target`,
      )
      const cycleIntent = await createIntent(
        request, targetOwner, targetSpaceId, reverse,
        sourceItem, targetItem, `${suffix}-cycle-intent`,
      )
      const cycle = await rawIntentLifecycle(
        request, sourceOwner, sourceSpaceId, cycleIntent,
        'accept', undefined, `${suffix}-cycle-accept`,
      )
      expect(cycle.status()).toBe(422)

      grant = await grantLifecycle(
        request, sourceOwner, sourceSpaceId, grant,
        'pause', undefined, `${suffix}-grant-pause`, 'temporary governance pause',
      )
      const stoppedByGrant = await rawCreateIntent(
        request, sourceOwner, sourceSpaceId, policy,
        sourceItem, concurrentTarget, `${suffix}-paused-intent`,
      )
      expect(stoppedByGrant.status()).toBe(403)
      const retained = await getJson<Relation>(
        request,
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/relation-policies/${policy.id}/relations/${intent.canonicalRelationId}`,
        sourceOwner,
      )
      expect(retained.status).toBe('active')
      grant = await grantLifecycle(
        request, sourceOwner, sourceSpaceId, grant,
        'resume', undefined, `${suffix}-grant-resume`,
      )

      for (const session of [sourceOwner, member, guest]) {
        const visible = await getJson<{ policies: Policy[] }>(
          request,
          `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/relations`,
          session,
        )
        expect(visible.policies.some((value) => value.id === policy.id)).toBeTruthy()
      }
      const forbiddenPolicy = await request.post(
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/relation-policies`,
        {
          headers: { ...bearer(member), 'X-Colla-Request-Id': `${suffix}-member-policy` },
          data: {
            schemaVersion: 1,
            requestId: `${suffix}-member-policy`,
            grantId: grant.id,
            relationKey: 'blocks',
            direction: 'source_to_target',
            sourceTypeId: sourceType.typeId,
            sourceVersionId: sourceType.versionId,
            targetTypeId: targetType.typeId,
            targetVersionId: targetType.versionId,
          },
        },
      )
      expect(forbiddenPolicy.status()).toBe(403)
      for (const session of [outsider, enterprise]) {
        const hidden = await request.get(
          `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/relations`,
          { headers: bearer(session) },
        )
        expect([403, 404]).toContain(hidden.status())
        expect(await hidden.text()).not.toContain(suffix)
      }

      await installSession(page, sourceOwner)
      await page.goto(`/project-spaces/${sourceSpaceId}`)
      await page
        .getByTestId('project-space-overview-secondary-tabs')
        .getByRole('tab', { name: '跨空间关系', exact: true })
        .click()
      const panel = page.getByTestId('cross-space-relations-panel')
      await expect(panel).toBeVisible()
      await expect(panel).toContainText('depends_on')
      await expect(panel).toContainText('已建立')
      await expect(panel).not.toContainText(targetItem.title)
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        await expect(panel).toBeVisible()
        expect(await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        )).toBeLessThanOrEqual(1)
      }
      await page.context().setOffline(true)
      await page.evaluate(() => window.dispatchEvent(new Event('offline')))
      await expect(panel).toContainText('当前离线')
      await expect(panel.getByRole('button', { name: '选择端点建链' }).first()).toBeDisabled()
      await page.context().setOffline(false)
      await page.screenshot({
        path: testInfo.outputPath('s18-cross-space-relations-820.png'),
        fullPage: true,
      })
      const withdrawn = await postJson<Relation>(
        request,
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/relation-policies/${policy.id}/relations/${intent.canonicalRelationId}:withdraw`,
        sourceOwner,
        {
          schemaVersion: 1,
          requestId: `${suffix}-withdraw`,
          expectedVersion: relation.version,
          reason: 'completed cross-team dependency',
        },
        `${suffix}-withdraw`,
      )
      expect(withdrawn.status).toBe('withdrawn')
      expect(withdrawn.version).toBe(1)
    } finally {
      await page.context().setOffline(false).catch(() => undefined)
      for (const entry of [
        { id: sourceSpaceId, session: sourceOwner },
        { id: targetSpaceId, session: targetOwner },
      ]) {
        if (entry.id) {
          await request.post(`${apiBaseUrl}/project-spaces/${entry.id}/settings/archive`, {
            headers: bearer(entry.session),
          }).catch(() => undefined)
        }
      }
      for (const identity of [
        targetOwnerIdentity, memberIdentity, guestIdentity,
        outsiderIdentity, sourceOwnerIdentity,
      ]) {
        await request.post(`${apiBaseUrl}/admin/users/${identity.id}/offboard`, {
          headers: bearer(enterprise),
          data: { handoverToUserId: enterpriseProfile.id },
        }).catch(() => undefined)
      }
    }
  })
})

async function publishedProjectType(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  suffix: string,
) {
  const configured = await getJson<{
    items: Array<{ id: string; typeKey: string }>
  }>(request, `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`, owner)
  const type = configured.items.find((candidate) => candidate.typeKey === 'project')
  expect(type, 'project preset must exist').toBeTruthy()
  if (!type) throw new Error('project preset missing')
  const base = `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types/${type.id}`
  let draft = await getJson<{ aggregateVersion: number }>(request, `${base}/draft`, owner)
  draft = await postJson(
    request, `${base}/draft:validate`, owner,
    { expectedAggregateVersion: draft.aggregateVersion }, `${suffix}-validate`,
  )
  const published = await postJson<{ version: { id: string } }>(
    request, `${base}/draft:publish`, owner,
    { expectedDraftAggregateVersion: draft.aggregateVersion, breakingConfirmed: false },
    `${suffix}-publish`,
  )
  return { typeId: type.id, versionId: published.version.id }
}

async function createItem(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  typeId: string,
  title: string,
  requestId: string,
) {
  return postJson<Item>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/work-items`,
    session,
    { typeId, title, fieldValues: {} },
    requestId,
  )
}

async function createGrant(
  request: APIRequestContext,
  session: E2eSession,
  sourceSpaceId: string,
  targetSpaceId: string,
  sourceType: { typeId: string; versionId: string },
  targetType: { typeId: string; versionId: string },
  requestId: string,
) {
  return postJson<Grant>(
    request,
    `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/grants`,
    session,
    {
      schemaVersion: 1,
      requestId,
      expectedVersion: 0,
      targetSpaceId,
      name: `M2 relation grant ${requestId}`,
      scope: {
        schemaVersion: 1,
        direction: 'bidirectional',
        operations: ['reference', 'relate'],
        typeScopes: [{
          sourceTypeId: sourceType.typeId,
          sourceVersionId: sourceType.versionId,
          targetTypeId: targetType.typeId,
          targetVersionId: targetType.versionId,
        }],
      },
    },
    requestId,
  )
}

async function grantLifecycle(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  grant: Grant,
  action: string,
  party: string | undefined,
  requestId: string,
  reason?: string,
) {
  return postJson<Grant>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/cross-space/grants/${grant.id}/lifecycle`,
    session,
    {
      schemaVersion: 1,
      requestId,
      expectedVersion: grant.currentVersion,
      action,
      party,
      reason,
    },
    requestId,
  )
}

async function createPolicy(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  grantId: string,
  relationKey: string,
  direction: string,
  sourceType: { typeId: string; versionId: string },
  targetType: { typeId: string; versionId: string },
  requestId: string,
) {
  return postJson<Policy>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/cross-space/relation-policies`,
    session,
    {
      schemaVersion: 1,
      requestId,
      grantId,
      relationKey,
      direction,
      sourceTypeId: sourceType.typeId,
      sourceVersionId: sourceType.versionId,
      targetTypeId: targetType.typeId,
      targetVersionId: targetType.versionId,
    },
    requestId,
  )
}

async function policyLifecycle(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  policy: Policy,
  action: string,
  party: string | undefined,
  requestId: string,
) {
  return postJson<Policy>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/cross-space/relation-policies/${policy.id}/lifecycle`,
    session,
    { schemaVersion: 1, requestId, expectedVersion: policy.version, action, party },
    requestId,
  )
}

async function createIntent(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  policy: Policy,
  source: Item,
  target: Item,
  requestId: string,
) {
  const response = await rawCreateIntent(
    request, session, spaceId, policy, source, target, requestId,
  )
  expect(response.ok(), await response.text()).toBeTruthy()
  return await response.json() as Intent
}

function rawCreateIntent(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  policy: Policy,
  source: Item,
  target: Item,
  requestId: string,
) {
  return request.post(
    `${apiBaseUrl}/project-spaces/${spaceId}/cross-space/relation-policies/${policy.id}/intents`,
    {
      headers: { ...bearer(session), 'X-Colla-Request-Id': requestId },
      data: {
        schemaVersion: 1,
        requestId,
        expectedPolicyVersion: policy.version,
        sourceWorkItemId: source.id,
        expectedSourceVersion: source.version,
        targetWorkItemId: target.id,
        expectedTargetVersion: target.version,
      },
    },
  )
}

async function intentLifecycle(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  intent: Intent,
  action: string,
  reason: string | undefined,
  requestId: string,
) {
  const response = await rawIntentLifecycle(
    request, session, spaceId, intent, action, reason, requestId,
  )
  expect(response.ok(), await response.text()).toBeTruthy()
  return await response.json() as Intent
}

function rawIntentLifecycle(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  intent: Intent,
  action: string,
  reason: string | undefined,
  requestId: string,
) {
  return request.post(
    `${apiBaseUrl}/project-spaces/${spaceId}/cross-space/link-intents/${intent.id}/lifecycle`,
    {
      headers: { ...bearer(session), 'X-Colla-Request-Id': requestId },
      data: { schemaVersion: 1, requestId, expectedVersion: intent.version, action, reason },
    },
  )
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
      spaceKey: `s18-m2-${kind}-${suffix.replaceAll('_', '-')}`,
      name: `S18 M2 ${kind} ${suffix}`,
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
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s18-m2-member-${userId}` },
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
