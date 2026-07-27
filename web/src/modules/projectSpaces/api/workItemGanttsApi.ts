import { apiGet, apiPost, apiPut } from '../../../shared/api/httpClient'

import type {
  WorkItemCalendarBinding,
  WorkItemCalendarWindow,
} from './workItemCalendarsApi'
import type { QueryDefinition } from './workItemViewsApi'

export type WorkItemGanttBar = {
  workItemId: string
  displayKey: string
  title: string
  workItemVersion: number
  startDate?: string | null
  endDate?: string | null
  allDay: boolean
  critical: boolean
  totalFloatDays: number
  availableActions: string[]
}

export type WorkItemGanttRow = {
  workItemId: string
  parentWorkItemId?: string | null
  depth: number
  expandable: boolean
  expanded: boolean
  bar: WorkItemGanttBar
}

export type WorkItemGanttDependency = {
  relationId: string
  relationKey: string
  sourceWorkItemId: string
  targetWorkItemId: string
  relationVersion: number
  critical: boolean
}

export type WorkItemGanttResult = {
  schemaVersion: 1
  viewKey: string
  queryHash: string
  binding: WorkItemCalendarBinding
  window: WorkItemCalendarWindow
  rows: WorkItemGanttRow[]
  dependencies: WorkItemGanttDependency[]
  criticalPathAvailable: boolean
  criticalPathReason: string
  truncated: boolean
}

export type WorkItemGanttPreference = {
  viewKey: string
  binding: WorkItemCalendarBinding
  timezone: string
  zoom: 'day' | 'week' | 'month'
  hierarchyRelationKey: string
  expandedNodeIds: string[]
  version: number
  updatedAt: string
}

export type WorkItemGanttRequest = {
  schemaVersion: 1
  viewKey: string
  binding: WorkItemCalendarBinding
  window: WorkItemCalendarWindow
  query: QueryDefinition
  hierarchyRelationKey: string
  expandedNodeIds: string[]
  criticalPath: boolean
}

export const workItemGanttKeys = {
  all: ['project-spaces', 'work-item-gantts'] as const,
  preference: (spaceId: string, viewKey: string) =>
    [...workItemGanttKeys.all, spaceId, viewKey, 'preference'] as const,
  render: (spaceId: string, signature: string) =>
    [...workItemGanttKeys.all, spaceId, 'render', signature] as const,
}

export function renderWorkItemGantt(spaceId: string, request: WorkItemGanttRequest) {
  return apiPost<WorkItemGanttResult>(
    `/project-spaces/${spaceId}/work-item-gantts:render`,
    request,
  )
}

export function getWorkItemGanttPreference(spaceId: string, viewKey: string) {
  return apiGet<WorkItemGanttPreference | null>(
    `/project-spaces/${spaceId}/work-item-gantts/${viewKey}/preference`,
  )
}

export function saveWorkItemGanttPreference(
  spaceId: string,
  viewKey: string,
  expectedVersion: number,
  values: Omit<WorkItemGanttPreference, 'viewKey' | 'version' | 'updatedAt'>,
) {
  const requestId = crypto.randomUUID()
  return apiPut<WorkItemGanttPreference>(
    `/project-spaces/${spaceId}/work-item-gantts/${viewKey}/preference`,
    { requestId, expectedVersion, ...values },
    { requestId },
  )
}

export function mutateWorkItemGanttDate(
  spaceId: string,
  viewKey: string,
  bar: WorkItemGanttBar,
  values: {
    operation: 'move' | 'resize'
    startValue?: string | null
    endValue?: string | null
    timezone: string
  },
) {
  const requestId = crypto.randomUUID()
  return apiPost<{
    workItemId: string
    viewKey: string
    startValue?: string | null
    endValue?: string | null
    workItemVersion: number
    replayed: boolean
  }>(
    `/project-spaces/${spaceId}/work-item-gantts/${viewKey}/items/${bar.workItemId}:date`,
    {
      requestId,
      expectedWorkItemVersion: bar.workItemVersion,
      ...values,
    },
    { requestId },
  )
}
