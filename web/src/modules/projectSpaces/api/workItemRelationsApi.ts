import { apiGet, apiPost } from '../../../shared/api/httpClient'

export type RelationEndpoint = {
  id: string
  typeDefinitionId: string
  typeVersionId: string
  typeKey: string
  displayKey: string
  title: string
  status: string
  version: number
}

export type WorkItemRelation = {
  id: string
  relationKey: string
  kind: 'normal' | 'parent_child' | 'dependency' | 'blocking'
  direction: 'directed' | 'undirected'
  status: 'active' | 'withdrawn'
  version: number
  source: RelationEndpoint
  target: RelationEndpoint
  perspective: 'source' | 'target'
  displayName: string
  reverse: boolean
  availableActions: string[]
  createdAt: string
  updatedAt: string
}

export type RelationSummary = {
  workItemId: string
  groups: Array<{
    relationKey: string
    perspective: string
    displayName: string
    count: number
    truncated: boolean
  }>
  items: WorkItemRelation[]
  truncated: boolean
  calibrationToken: string
}

export type RelationTarget = {
  id: string
  displayKey: string
  title: string
  typeKey: string
  typeName: string
  status: string
  version: number
}

export type RelationTargetPage = {
  relationKey: string
  items: RelationTarget[]
  nextCursor?: string | null
  truncated: boolean
}

export type RelationImpact = {
  focusWorkItemId: string
  relationKey: string
  direction: 'upstream' | 'downstream'
  maxDepth: number
  nodes: RelationTarget[]
  links: Array<{
    relationId: string
    sourceWorkItemId: string
    targetWorkItemId: string
    depth: number
  }>
  truncated: boolean
  truncationReason?: string | null
  calibrationToken: string
}

export type HierarchyNode = RelationTarget & {
  typeDefinitionId: string
  typeVersionId: string
  depth: number
  directRelationId?: string | null
}

export type HierarchyNavigation = {
  focus: HierarchyNode
  breadcrumbs: HierarchyNode[]
  parent?: HierarchyNode | null
  children: HierarchyNode[]
  siblings: HierarchyNode[]
  localTree: HierarchyNode[]
  truncated: boolean
  degradationReason?: string | null
}

export type RelationMigrationUnit = {
  id: string
  sourceRelationId: string
  sourceIssueId: string
  targetType: string
  targetId: string
  classification: string
  sourceWorkItemId?: string | null
  targetWorkItemId?: string | null
  relationId?: string | null
  status: string
  attempt: number
  errorCode?: string | null
}

export type RelationMigrationState = {
  batch: {
    id: string
    spaceId: string
    relationKey: string
    manifestHash: string
    dryRun: boolean
    status: string
    version: number
    totalCount: number
    canonicalCount: number
    preservedCount: number
    completedCount: number
    failedCount: number
  }
  units: RelationMigrationUnit[]
}

export const workItemRelationKeys = {
  all: ['project-spaces', 'work-item-relations'] as const,
  summary: (spaceId: string, workItemId: string) =>
    [...workItemRelationKeys.all, spaceId, workItemId, 'summary'] as const,
  targets: (spaceId: string, workItemId: string, relationKey: string, query: string) =>
    [...workItemRelationKeys.all, spaceId, workItemId, relationKey, 'targets', query] as const,
  hierarchy: (spaceId: string, workItemId: string, relationKey: string) =>
    [...workItemRelationKeys.all, spaceId, workItemId, relationKey, 'hierarchy'] as const,
  impact: (spaceId: string, workItemId: string, relationKey: string, direction: string) =>
    [...workItemRelationKeys.all, spaceId, workItemId, relationKey, direction, 'impact'] as const,
}

export function getRelationSummary(spaceId: string, workItemId: string) {
  return apiGet<RelationSummary>(
    `/project-spaces/${spaceId}/work-item-relation-experience/summary?workItemId=${workItemId}&limit=200`,
  )
}

export function searchRelationTargets(
  spaceId: string,
  sourceWorkItemId: string,
  relationKey: string,
  query: string,
) {
  const parameters = new URLSearchParams({
    sourceWorkItemId,
    relationKey,
    query,
    limit: '50',
  })
  return apiGet<RelationTargetPage>(
    `/project-spaces/${spaceId}/work-item-relation-experience/targets?${parameters}`,
  )
}

export function createRelation(
  spaceId: string,
  input: {
    relationKey: string
    sourceWorkItemId: string
    targetWorkItemId: string
    expectedSourceVersion: number
    expectedTargetVersion: number
  },
) {
  return apiPost<WorkItemRelation>(`/project-spaces/${spaceId}/work-item-relations`, input)
}

export function withdrawRelation(
  spaceId: string,
  relation: WorkItemRelation,
  reason: string,
) {
  return apiPost<WorkItemRelation>(
    `/project-spaces/${spaceId}/work-item-relations/${relation.id}:withdraw`,
    {
      expectedRelationVersion: relation.version,
      expectedSourceVersion: relation.source.version,
      expectedTargetVersion: relation.target.version,
      reason,
    },
  )
}

export function getRelationImpact(
  spaceId: string,
  workItemId: string,
  relationKey: string,
  direction: 'upstream' | 'downstream',
) {
  const parameters = new URLSearchParams({
    focusWorkItemId: workItemId,
    relationKey,
    direction,
    maxDepth: '8',
    limit: '100',
  })
  return apiGet<RelationImpact>(
    `/project-spaces/${spaceId}/work-item-relation-experience/impact?${parameters}`,
  )
}

export function getHierarchyNavigation(
  spaceId: string,
  workItemId: string,
  relationKey: string,
) {
  const parameters = new URLSearchParams({
    workItemId,
    relationKey,
    maxDepth: '8',
    limit: '100',
  })
  return apiGet<HierarchyNavigation>(
    `/project-spaces/${spaceId}/work-item-hierarchy/navigation?${parameters}`,
  )
}

export function reparentWorkItem(
  spaceId: string,
  input: {
    currentRelationId: string
    newParentWorkItemId: string
    expectedRelationVersion: number
    expectedCurrentParentVersion: number
    expectedNewParentVersion: number
    expectedChildVersion: number
    reason: string
    confirmation: 'REPARENT'
  },
) {
  return apiPost<{ relation: WorkItemRelation }>(
    `/project-spaces/${spaceId}/work-item-hierarchy:reparent`,
    input,
  )
}

export function splitWorkItemChild(
  spaceId: string,
  input: {
    parentWorkItemId: string
    relationKey: string
    childTypeId: string
    childTitle: string
    childFieldValues: Record<string, unknown>
    inheritFieldKeys: string[]
    expectedParentVersion: number
  },
) {
  return apiPost<{ relation: WorkItemRelation }>(
    `/project-spaces/${spaceId}/work-item-hierarchy:split-child`,
    input,
  )
}

export function planRelationMigration(
  spaceId: string,
  input: { relationKey: string; dryRun: boolean; reason: string },
) {
  return apiPost<RelationMigrationState>(
    `/project-spaces/${spaceId}/relation-migrations:plan`,
    input,
  )
}

export function mutateRelationMigration(
  spaceId: string,
  state: RelationMigrationState,
  action: 'execute' | 'resume' | 'verify' | 'rollback',
  reason: string,
) {
  const input = action === 'verify'
    ? { expectedVersion: state.batch.version }
    : {
      expectedVersion: state.batch.version,
      reason,
      confirmation: action === 'rollback' ? 'ROLLBACK_RELATIONS' : 'MIGRATE_RELATIONS',
    }
  return apiPost<RelationMigrationState>(
    `/project-spaces/${spaceId}/relation-migrations/${state.batch.id}:${action}`,
    input,
  )
}
