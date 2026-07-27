import { apiGet, apiGetText, apiPost, apiPut } from '../../../shared/api/httpClient'

export type QueryFilter = {
  kind: 'and' | 'or' | 'not' | 'predicate'
  field?: string | null
  operator?: string | null
  value?: unknown
  children?: QueryFilter[]
}

export type QueryDefinition = {
  schemaVersion: 1
  typeId?: string
  filter?: QueryFilter | null
  sorts: Array<{ field: string; direction: 'asc' | 'desc'; nulls: 'first' | 'last' }>
  group?: { field: string; aggregates: Array<{ function: string; field?: string; alias: string }> } | null
  select: string[]
  limit: number
  cursor?: string | null
}

export type WorkItemColumn = {
  key: string
  label: string
  width: number
  frozen: boolean
  format: 'text' | 'tag' | 'date' | 'datetime' | 'number' | 'boolean'
}

export type WorkItemViewRow = {
  workItemId: string
  displayKey: string
  title: string
  version: number
  cells: Array<{
    columnKey: string
    value: unknown
    displayValue: string
    disclosure: 'user_safe'
  }>
  availableActions: string[]
}

export type WorkItemViewResult = {
  schemaVersion: 1
  mode: 'table' | 'list'
  density: 'compact' | 'comfortable'
  columns: WorkItemColumn[]
  rows: WorkItemViewRow[]
  nextCursor?: string | null
  queryHash: string
}

export type WorkItemViewPreference = {
  viewKey: string
  mode: 'table' | 'list'
  density: 'compact' | 'comfortable'
  columns: WorkItemColumn[]
  version: number
  updatedAt: string
}

export const workItemViewKeys = {
  all: ['project-spaces', 'work-item-views'] as const,
  preference: (spaceId: string) => [...workItemViewKeys.all, spaceId, 'preference'] as const,
  render: (spaceId: string, signature: string) => [...workItemViewKeys.all, spaceId, 'render', signature] as const,
}

export function renderWorkItemView(
  spaceId: string,
  request: {
    schemaVersion: 1
    mode: 'table' | 'list'
    density: 'compact' | 'comfortable'
    columns: WorkItemColumn[]
    query: QueryDefinition
  },
) {
  return apiPost<WorkItemViewResult>(`/project-spaces/${spaceId}/work-item-views:render`, request)
}

export function getWorkItemViewPreference(spaceId: string) {
  return apiGet<WorkItemViewPreference>(
    `/project-spaces/${spaceId}/work-item-views/preferences/default`,
  )
}

export function saveWorkItemViewPreference(
  spaceId: string,
  request: {
    requestId: string
    expectedVersion: number
    mode: 'table' | 'list'
    density: 'compact' | 'comfortable'
    columns: WorkItemColumn[]
  },
) {
  return apiPut<WorkItemViewPreference>(
    `/project-spaces/${spaceId}/work-item-views/preferences/default`,
    request,
    { requestId: request.requestId },
  )
}

export function bulkWorkItems(
  spaceId: string,
  action: 'archive' | 'restore',
  selections: Array<{ workItemId: string; expectedVersion: number }>,
) {
  const requestId = crypto.randomUUID()
  return apiPost<{
    requestId: string
    succeeded: number
    failed: number
    items: Array<{ workItemId: string; status: string; reasonCode?: string; version?: number }>
  }>(
    `/project-spaces/${spaceId}/work-item-views:bulk`,
    { requestId, action, selections },
    { requestId },
  )
}

export async function exportWorkItemView(
  spaceId: string,
  query: QueryDefinition,
  columns: WorkItemColumn[],
) {
  const requestId = crypto.randomUUID()
  const job = await apiPost<{
    id: string
    status: string
    maxRows: number
    expiresAt: string
    downloadPath: string
  }>(
    `/project-spaces/${spaceId}/work-item-views:export`,
    { requestId, query, columns },
    { requestId },
  )
  const content = await apiGetText(job.downloadPath.replace(/^\/api/, ''))
  return { job, content }
}
