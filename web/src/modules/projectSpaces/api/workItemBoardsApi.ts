import { apiGet, apiPost, apiPut } from '../../../shared/api/httpClient'

import type { QueryDefinition } from './workItemViewsApi'

export type WorkItemBoardColumn = {
  key: string
  label: string
  wipLimit: number
  moveKind: '' | 'state' | 'node' | 'reorder'
  moveActionKey: string
}

export type WorkItemBoardAction = {
  kind: 'state' | 'node'
  actionKey: string
  label: string
  fromStateKey?: string | null
  taskId?: string | null
  expectedInstanceVersion: number
}

export type WorkItemBoardCard = {
  workItemId: string
  displayKey: string
  title: string
  status: string
  workItemVersion: number
  columnKey: string
  swimlaneKey: string
  rank: number
  orderVersion: number
  availableActions: string[]
  moveActions: WorkItemBoardAction[]
}

export type WorkItemBoardResult = {
  schemaVersion: 1
  viewKey: string
  queryHash: string
  columnField: string
  swimlaneField?: string | null
  columns: Array<{
    column: WorkItemBoardColumn
    visibleCount: number
    wipExceeded: boolean
    lanes: Array<{ key: string; label: string; cards: WorkItemBoardCard[] }>
  }>
  nextCursor?: string | null
  evaluatedCandidates: number
  candidateBoundReached: boolean
}

export type WorkItemBoardPreference = {
  viewKey: string
  columnField: string
  swimlaneField?: string | null
  columns: WorkItemBoardColumn[]
  version: number
  updatedAt: string
}

export type WorkItemBoardRequest = {
  schemaVersion: 1
  viewKey: string
  columnField: string
  swimlaneField?: string | null
  columns: WorkItemBoardColumn[]
  query: QueryDefinition
}

export const workItemBoardKeys = {
  all: ['project-spaces', 'work-item-boards'] as const,
  preference: (spaceId: string, viewKey: string) =>
    [...workItemBoardKeys.all, spaceId, viewKey, 'preference'] as const,
  render: (spaceId: string, signature: string) =>
    [...workItemBoardKeys.all, spaceId, 'render', signature] as const,
}

export function renderWorkItemBoard(spaceId: string, request: WorkItemBoardRequest) {
  return apiPost<WorkItemBoardResult>(
    `/project-spaces/${spaceId}/work-item-boards:render`,
    request,
  )
}

export function getWorkItemBoardPreference(spaceId: string, viewKey: string) {
  return apiGet<WorkItemBoardPreference | null>(
    `/project-spaces/${spaceId}/work-item-boards/${viewKey}/preference`,
  )
}

export function saveWorkItemBoardPreference(
  spaceId: string,
  viewKey: string,
  expectedVersion: number,
  request: Omit<WorkItemBoardRequest, 'schemaVersion' | 'viewKey' | 'query'>,
) {
  const requestId = crypto.randomUUID()
  return apiPut<WorkItemBoardPreference>(
    `/project-spaces/${spaceId}/work-item-boards/${viewKey}/preference`,
    { requestId, expectedVersion, ...request },
    { requestId },
  )
}

export function moveWorkItemBoardCard(
  spaceId: string,
  viewKey: string,
  card: WorkItemBoardCard,
  target: {
    columnKey: string
    swimlaneKey: string
    rank: number
    kind: 'state' | 'node' | 'reorder'
    actionKey: string
    action?: WorkItemBoardAction
  },
) {
  const requestId = crypto.randomUUID()
  return apiPost<{
    workItemId: string
    viewKey: string
    targetColumnKey: string
    targetSwimlaneKey: string
    rank: number
    workItemVersion: number
    workflowVersion: number
    orderVersion: number
    replayed: boolean
  }>(
    `/project-spaces/${spaceId}/work-item-boards/${viewKey}/items/${card.workItemId}:move`,
    {
      requestId,
      expectedWorkItemVersion: card.workItemVersion,
      expectedOrderVersion: card.orderVersion,
      targetColumnKey: target.columnKey,
      targetSwimlaneKey: target.swimlaneKey,
      rank: target.rank,
      kind: target.kind,
      actionKey: target.actionKey,
      fromStateKey: target.action?.fromStateKey ?? null,
      taskId: target.action?.taskId ?? null,
      nodeOperation: target.kind === 'node' ? target.actionKey : null,
      expectedInstanceVersion: target.action?.expectedInstanceVersion ?? 0,
      decision: null,
      fieldPatch: null,
    },
    { requestId },
  )
}
