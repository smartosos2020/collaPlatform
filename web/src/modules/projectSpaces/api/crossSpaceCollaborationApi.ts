import { apiGet, apiPost } from '../../../shared/api/httpClient'

export type CrossSpaceGrantScope = {
  schemaVersion: 1
  direction: 'source_to_target' | 'target_to_source' | 'bidirectional'
  operations: Array<'reference' | 'relate' | 'read_fields' | 'sync_fields' | 'sync_state'>
  typeScopes: Array<{
    sourceTypeId: string
    sourceVersionId: string
    targetTypeId: string
    targetVersionId: string
  }>
  instanceScopes?: string[]
}

export type CrossSpaceGrant = {
  id: string
  sourceSpaceId: string
  targetSpaceId: string
  name: string
  status: 'draft' | 'requested' | 'active' | 'paused' | 'revoked' | 'archived'
  currentVersion: number
  sourceConfirmed: boolean
  targetConfirmed: boolean
  scope: CrossSpaceGrantScope
  scopeHash: string
  updatedBy: string
  updatedAt: string
  revokedAt?: string
  archivedAt?: string
}

export type GrantFoundation = {
  schemaVersion: number
  directions: string[]
  operations: string[]
  grants: CrossSpaceGrant[]
  truncated: boolean
}

export type CrossSpaceRelationPolicy = {
  id: string
  grantId: string
  sourceSpaceId: string
  targetSpaceId: string
  relationKey: string
  direction: CrossSpaceGrantScope['direction']
  sourceTypeId: string
  sourceVersionId: string
  targetTypeId: string
  targetVersionId: string
  status: 'draft' | 'requested' | 'active' | 'paused' | 'revoked' | 'archived'
  version: number
  sourceConfirmedBy?: string
  targetConfirmedBy?: string
  updatedAt: string
}

export type CrossSpaceLinkIntent = {
  id: string
  policyId: string
  policyVersion: number
  sourceSpaceId: string
  sourceWorkItemId: string
  sourceExpectedVersion: number
  targetSpaceId: string
  targetWorkItemId: string
  targetExpectedVersion: number
  status: 'requested' | 'linked' | 'rejected' | 'cancelled'
  version: number
  sourceConfirmedBy: string
  targetConfirmedBy?: string
  canonicalRelationId?: string
  updatedAt: string
}

export type CrossSpaceRelationFoundation = {
  schemaVersion: number
  directions: string[]
  policies: CrossSpaceRelationPolicy[]
  intents: CrossSpaceLinkIntent[]
  policiesTruncated: boolean
  intentsTruncated: boolean
}

export type EndpointReference = {
  spaceId: string
  workItemId: string
  opaqueReference: string
  typeKey: string
  active: boolean
  version: number
}

export type CrossSpaceSyncRule = {
  id: string
  grantId: string
  policyId: string
  canonicalRelationId: string
  sourceSpaceId: string
  targetSpaceId: string
  name: string
  status: 'draft' | 'requested' | 'active' | 'paused' | 'revoked' | 'archived'
  currentVersion: number
  sourceConfirmedBy?: string
  targetConfirmedBy?: string
  configuration: {
    direction: CrossSpaceGrantScope['direction']
    trigger: 'manual' | 'work_item_changed' | 'workflow_state_changed'
    fieldMappings: Array<{ sourceField: string; targetField: string; transform: 'copy' }>
    stateMappings: Array<{ sourceState: string; targetFromState: string; targetAction: string }>
    conflictStrategy: 'manual' | 'source_wins' | 'target_wins'
    configHash: string
  }
  updatedAt: string
}

export type CrossSpaceSyncRun = {
  id: string
  ruleId: string
  direction: 'source_to_target' | 'target_to_source'
  originId: string
  chainDepth: number
  status: 'running' | 'succeeded' | 'conflict' | 'failed' | 'compensated' | 'dead_letter'
  sourceVersion: number
  targetVersion: number
  resultTargetVersion?: number
  failureCode?: string
  createdAt: string
}

export type CrossSpaceSyncConflict = {
  id: string
  runId: string
  kind: string
  status: 'open' | 'resolved' | 'compensated' | 'dead_letter'
  version: number
  resolution?: string
  createdAt: string
}

export type CrossSpaceSyncFoundation = {
  schemaVersion: number
  directions: string[]
  triggers: string[]
  conflictStrategies: string[]
  rules: CrossSpaceSyncRule[]
  runs: CrossSpaceSyncRun[]
  conflicts: CrossSpaceSyncConflict[]
  truncated: boolean
}

export type CrossTeamPanorama = {
  schemaVersion: number
  preference: { compact: boolean; windowDays: number; version: number }
  slices: Array<{
    kind: 'grant' | 'relation' | 'sync' | 'conflict'
    identity: string
    sourceSpaceId: string
    targetSpaceId: string
    status: string
    version: number
    source: string
    observedAt: string
  }>
  audit: Array<{
    kind: string
    identity: string
    status: string
    version: number
    source: string
    occurredAt: string
  }>
  health: {
    status: 'healthy' | 'attention' | 'unknown'
    grants: number
    relations: number
    syncRules: number
    openConflicts: number
    truncated: boolean
    diagnostic: string
  }
  observedAt: string
}

export const crossSpaceKeys = {
  grants: (spaceId: string) => ['project-spaces', spaceId, 'cross-space', 'grants'] as const,
  relations: (spaceId: string) => ['project-spaces', spaceId, 'cross-space', 'relations'] as const,
  sync: (spaceId: string) => ['project-spaces', spaceId, 'cross-space', 'sync'] as const,
  panorama: (spaceId: string) => ['project-spaces', spaceId, 'cross-space', 'panorama'] as const,
}

export function getCrossTeamPanorama(spaceId: string) {
  return apiGet<CrossTeamPanorama>(
    `/project-spaces/${spaceId}/cross-space/panorama`,
  )
}

export function saveCrossTeamPanoramaPreference(
  spaceId: string,
  preference: CrossTeamPanorama['preference'],
  compact: boolean,
) {
  const requestId = crypto.randomUUID()
  return apiPost<CrossTeamPanorama['preference']>(
    `/project-spaces/${spaceId}/cross-space/panorama/preference`,
    {
      schemaVersion: 1,
      requestId,
      expectedVersion: preference.version,
      compact,
      windowDays: preference.windowDays,
    },
    { requestId },
  )
}

export function getCrossSpaceSync(spaceId: string) {
  return apiGet<CrossSpaceSyncFoundation>(`/project-spaces/${spaceId}/cross-space/sync`)
}

export function saveCrossSpaceSyncRule(
  spaceId: string,
  input: {
    ruleId?: string
    expectedVersion: number
    grantId: string
    policyId: string
    canonicalRelationId: string
    name: string
    direction: CrossSpaceGrantScope['direction']
    trigger: 'manual' | 'work_item_changed' | 'workflow_state_changed'
    fieldMappings: CrossSpaceSyncRule['configuration']['fieldMappings']
    stateMappings: CrossSpaceSyncRule['configuration']['stateMappings']
    conflictStrategy: 'manual' | 'source_wins' | 'target_wins'
  },
) {
  const requestId = crypto.randomUUID()
  return apiPost<CrossSpaceSyncRule>(
    `/project-spaces/${spaceId}/cross-space/sync-rules`,
    { schemaVersion: 1, requestId, ...input },
    { requestId },
  )
}

export function changeCrossSpaceSyncRule(
  spaceId: string,
  rule: CrossSpaceSyncRule,
  action: 'request' | 'confirm' | 'pause' | 'resume' | 'revoke' | 'archive',
  party?: 'source' | 'target',
  reason?: string,
) {
  const requestId = crypto.randomUUID()
  return apiPost<CrossSpaceSyncRule>(
    `/project-spaces/${spaceId}/cross-space/sync-rules/${rule.id}/lifecycle`,
    { schemaVersion: 1, requestId, expectedVersion: rule.currentVersion, action, party, reason },
    { requestId },
  )
}

export function executeCrossSpaceSync(
  spaceId: string,
  rule: CrossSpaceSyncRule,
  direction: 'source_to_target' | 'target_to_source',
  sourceVersion: number,
  targetVersion: number,
) {
  const requestId = crypto.randomUUID()
  return apiPost<CrossSpaceSyncRun>(
    `/project-spaces/${spaceId}/cross-space/sync-rules/${rule.id}/runs`,
    {
      schemaVersion: 1,
      requestId,
      expectedRuleVersion: rule.currentVersion,
      direction,
      originId: crypto.randomUUID(),
      causationId: crypto.randomUUID(),
      chainDepth: 0,
      expectedSourceVersion: sourceVersion,
      expectedTargetVersion: targetVersion,
    },
    { requestId },
  )
}

export function resolveCrossSpaceSyncConflict(
  spaceId: string,
  conflict: CrossSpaceSyncConflict,
  resolution: 'skip' | 'compensate' | 'dead_letter',
  reason: string,
) {
  const requestId = crypto.randomUUID()
  return apiPost<CrossSpaceSyncConflict>(
    `/project-spaces/${spaceId}/cross-space/sync-conflicts/${conflict.id}/resolve`,
    {
      schemaVersion: 1,
      requestId,
      expectedVersion: conflict.version,
      resolution,
      reason,
    },
    { requestId },
  )
}

export function getCrossSpaceGrants(spaceId: string) {
  return apiGet<GrantFoundation>(`/project-spaces/${spaceId}/cross-space/grants`)
}

export function saveCrossSpaceGrant(
  spaceId: string,
  input: {
    grantId?: string
    expectedVersion: number
    targetSpaceId: string
    name: string
    scope: CrossSpaceGrantScope
  },
) {
  const requestId = crypto.randomUUID()
  return apiPost<CrossSpaceGrant>(
    `/project-spaces/${spaceId}/cross-space/grants`,
    { schemaVersion: 1, requestId, ...input },
    { requestId },
  )
}

export function changeCrossSpaceGrant(
  spaceId: string,
  grant: CrossSpaceGrant,
  action: 'request' | 'confirm' | 'pause' | 'resume' | 'revoke' | 'archive',
  party?: 'source' | 'target',
  reason?: string,
) {
  const requestId = crypto.randomUUID()
  return apiPost<CrossSpaceGrant>(
    `/project-spaces/${spaceId}/cross-space/grants/${grant.id}/lifecycle`,
    {
      schemaVersion: 1,
      requestId,
      expectedVersion: grant.currentVersion,
      action,
      party,
      reason,
    },
    { requestId },
  )
}

export function getCrossSpaceRelations(spaceId: string) {
  return apiGet<CrossSpaceRelationFoundation>(
    `/project-spaces/${spaceId}/cross-space/relations`,
  )
}

export function createCrossSpaceRelationPolicy(
  spaceId: string,
  input: Omit<Parameters<typeof bodyPolicy>[0], 'requestId'>,
) {
  const requestId = crypto.randomUUID()
  return apiPost<CrossSpaceRelationPolicy>(
    `/project-spaces/${spaceId}/cross-space/relation-policies`,
    bodyPolicy({ ...input, requestId }),
    { requestId },
  )
}

function bodyPolicy(input: {
  requestId: string
  grantId: string
  relationKey: string
  direction: CrossSpaceGrantScope['direction']
  sourceTypeId: string
  sourceVersionId: string
  targetTypeId: string
  targetVersionId: string
}) {
  return { schemaVersion: 1, ...input }
}

export function changeCrossSpaceRelationPolicy(
  spaceId: string,
  policy: CrossSpaceRelationPolicy,
  action: 'request' | 'confirm' | 'pause' | 'resume' | 'revoke' | 'archive',
  party?: 'source' | 'target',
  reason?: string,
) {
  const requestId = crypto.randomUUID()
  return apiPost<CrossSpaceRelationPolicy>(
    `/project-spaces/${spaceId}/cross-space/relation-policies/${policy.id}/lifecycle`,
    {
      schemaVersion: 1,
      requestId,
      expectedVersion: policy.version,
      action,
      party,
      reason,
    },
    { requestId },
  )
}

export function createCrossSpaceLinkIntent(
  spaceId: string,
  policy: CrossSpaceRelationPolicy,
  input: {
    sourceWorkItemId: string
    expectedSourceVersion: number
    targetWorkItemId: string
    expectedTargetVersion: number
  },
) {
  const requestId = crypto.randomUUID()
  return apiPost<CrossSpaceLinkIntent>(
    `/project-spaces/${spaceId}/cross-space/relation-policies/${policy.id}/intents`,
    {
      schemaVersion: 1,
      requestId,
      expectedPolicyVersion: policy.version,
      ...input,
    },
    { requestId },
  )
}

export function changeCrossSpaceLinkIntent(
  spaceId: string,
  intent: CrossSpaceLinkIntent,
  action: 'accept' | 'reject' | 'cancel',
  reason?: string,
) {
  const requestId = crypto.randomUUID()
  return apiPost<CrossSpaceLinkIntent>(
    `/project-spaces/${spaceId}/cross-space/link-intents/${intent.id}/lifecycle`,
    { schemaVersion: 1, requestId, expectedVersion: intent.version, action, reason },
    { requestId },
  )
}

export function getCrossSpaceEndpointReference(
  spaceId: string,
  policyId: string,
  workItemId: string,
) {
  return apiGet<EndpointReference>(
    `/project-spaces/${spaceId}/cross-space/relation-policies/${policyId}/endpoints/${workItemId}`,
  )
}
