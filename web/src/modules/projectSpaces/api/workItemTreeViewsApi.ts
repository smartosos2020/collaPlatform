import { apiGet, apiPost, apiPut } from '../../../shared/api/httpClient'
import type { QueryDefinition } from './workItemViewsApi'

export type WorkItemTreeNode = {
  id: string
  parentId?: string | null
  displayKey: string
  title: string
  status: string
  version: number
  depth: number
  visibleChildCount: number
  expandable: boolean
  matchKind: 'matched' | 'context'
  availableActions: string[]
}

export type WorkItemTreeResult = {
  schemaVersion: 1
  relationKey: string
  queryHash: string
  parentId?: string | null
  items: WorkItemTreeNode[]
  nextCursor?: string | null
  truncated: boolean
  aggregate: {
    visibleNodeCount: number
    rootCount: number
    matchedCount: number
    maxVisibleDepth: number
    candidateBoundReached: boolean
  }
}

export type WorkItemTreePreference = {
  viewKey: string
  relationKey: string
  expandedNodeIds: string[]
  version: number
  updatedAt: string
}

export const workItemTreeViewKeys = {
  all: ['project-spaces', 'work-item-tree-views'] as const,
  preference: (spaceId: string) => [...workItemTreeViewKeys.all, spaceId, 'preference'] as const,
  roots: (spaceId: string, signature: string) => [
    ...workItemTreeViewKeys.all, spaceId, 'roots', signature,
  ] as const,
}

export function renderWorkItemTree(
  spaceId: string,
  query: QueryDefinition,
  parentId?: string | null,
  cursor?: string | null,
) {
  return apiPost<WorkItemTreeResult>(
    `/project-spaces/${spaceId}/work-item-tree-views:render`,
    {
      schemaVersion: 1,
      relationKey: 'parent_child',
      query,
      parentId: parentId ?? null,
      limit: 50,
      maxDepth: 32,
      cursor: cursor ?? null,
    },
  )
}

export function getWorkItemTreePreference(spaceId: string) {
  return apiGet<WorkItemTreePreference>(
    `/project-spaces/${spaceId}/work-item-tree-views/preferences/default`,
  )
}

export function saveWorkItemTreePreference(
  spaceId: string,
  expectedVersion: number,
  expandedNodeIds: string[],
) {
  const requestId = crypto.randomUUID()
  return apiPut<WorkItemTreePreference>(
    `/project-spaces/${spaceId}/work-item-tree-views/preferences/default`,
    {
      requestId,
      expectedVersion,
      relationKey: 'parent_child',
      expandedNodeIds,
    },
    { requestId },
  )
}
