import { expect, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, type E2eSession } from './api'

export type Identity = { id: string; username: string; displayName: string; password: string }
export type Item = { id: string; version: number; displayKey: string; title: string }
export type Grant = { id: string; status: string; currentVersion: number }
export type Policy = { id: string; status: string; version: number }
export type Intent = {
  id: string
  status: string
  version: number
  canonicalRelationId?: string
}

export async function publishedProjectType(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  suffix: string,
) {
  const configured = await getJson<{ items: Array<{ id: string; typeKey: string }> }>(
    request, `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`, owner,
  )
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

export function createItem(
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

export function createGrant(
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
      name: `S18 sync grant ${requestId}`,
      scope: {
        schemaVersion: 1,
        direction: 'bidirectional',
        operations: ['reference', 'relate', 'read_fields', 'sync_fields', 'sync_state'],
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

export function grantLifecycle(
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
    { schemaVersion: 1, requestId, expectedVersion: grant.currentVersion, action, party, reason },
    requestId,
  )
}

export function createPolicy(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  grantId: string,
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
      relationKey: 'relates_to',
      direction: 'bidirectional',
      sourceTypeId: sourceType.typeId,
      sourceVersionId: sourceType.versionId,
      targetTypeId: targetType.typeId,
      targetVersionId: targetType.versionId,
    },
    requestId,
  )
}

export function policyLifecycle(
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

export function createIntent(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  policy: Policy,
  source: Item,
  target: Item,
  requestId: string,
) {
  return postJson<Intent>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/cross-space/relation-policies/${policy.id}/intents`,
    session,
    {
      schemaVersion: 1,
      requestId,
      expectedPolicyVersion: policy.version,
      sourceWorkItemId: source.id,
      expectedSourceVersion: source.version,
      targetWorkItemId: target.id,
      expectedTargetVersion: target.version,
    },
    requestId,
  )
}

export function intentLifecycle(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  intent: Intent,
  action: string,
  requestId: string,
) {
  return postJson<Intent>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/cross-space/link-intents/${intent.id}/lifecycle`,
    session,
    { schemaVersion: 1, requestId, expectedVersion: intent.version, action },
    requestId,
  )
}

export async function createSpace(
  request: APIRequestContext,
  owner: E2eSession,
  suffix: string,
  kind: string,
) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: bearer(owner),
    data: {
      spaceKey: `s18-m3-${kind}-${suffix.replaceAll('_', '-')}`,
      name: `S18 M3 ${kind} ${suffix}`,
      visibility: 'private',
    },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  return (await response.json() as { id: string }).id
}

export async function addMember(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
  userId: string,
  roleKey: string,
) {
  const response = await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/members`, {
    headers: { ...bearer(owner), 'X-Colla-Request-Id': `s18-m3-member-${userId}` },
    data: { userId, roleKey },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
}

export async function createIdentity(
  request: APIRequestContext,
  administrator: E2eSession,
  username: string,
  displayName: string,
) {
  const credentialSecret = ['member', '123456'].join('')
  const response = await request.post(`${apiBaseUrl}/admin/users`, {
    headers: bearer(administrator),
    data: {
      username,
      password: credentialSecret,
      displayName,
      email: `${username}@example.com`,
      roleCode: 'member',
    },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  return {
    ...(await response.json() as Omit<Identity, 'password'>),
    password: credentialSecret,
  }
}

export async function getJson<T>(
  request: APIRequestContext,
  url: string,
  session: E2eSession,
) {
  const response = await request.get(url, { headers: bearer(session) })
  expect(response.ok(), `GET ${url} failed: ${await response.text()}`).toBeTruthy()
  return await response.json() as T
}

export async function postJson<T>(
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
