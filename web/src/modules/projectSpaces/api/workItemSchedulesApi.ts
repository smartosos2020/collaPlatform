import { apiGet, apiPost } from '../../../shared/api/httpClient'

import type { WorkItemGanttRequest } from './workItemGanttsApi'

export type WorkItemScheduleBaseline = {
  id: string
  name: string
  queryHash: string
  windowStart: string
  windowEnd: string
  version: number
  status: 'active' | 'deleted'
  createdAt: string
  expiresAt: string
}

export type WorkItemScheduleBaselineSnapshot = {
  baseline: WorkItemScheduleBaseline
  entries: Array<{
    workItemId: string
    workItemVersion: number
    startDate?: string | null
    endDate?: string | null
    parentWorkItemId?: string | null
    depth: number
  }>
  dependencies: Array<{
    relationId: string
    relationVersion: number
    sourceWorkItemId: string
    targetWorkItemId: string
  }>
}

export type WorkItemScheduleBaselineDiff = {
  baselineId: string
  entries: Array<{
    workItemId: string
    change: 'added' | 'changed' | 'removed'
    baselineStartDate?: string | null
    currentStartDate?: string | null
    baselineEndDate?: string | null
    currentEndDate?: string | null
    baselineParentWorkItemId?: string | null
    currentParentWorkItemId?: string | null
  }>
  addedDependencies: number
  removedDependencies: number
  truncated: boolean
}

export type WorkItemTimelineResult = {
  events: Array<{
    id: string
    sourceKind: 'activity' | 'audit' | 'workflow' | 'relation'
    sourceId: string
    workItemId?: string | null
    eventType: string
    actorId?: string | null
    occurredAt: string
  }>
  truncated: boolean
}

export const workItemScheduleKeys = {
  all: ['project-spaces', 'work-item-schedules'] as const,
  baselines: (spaceId: string) =>
    [...workItemScheduleKeys.all, spaceId, 'baselines'] as const,
  timeline: (spaceId: string, signature: string) =>
    [...workItemScheduleKeys.all, spaceId, 'timeline', signature] as const,
}

export function listWorkItemScheduleBaselines(spaceId: string) {
  return apiGet<WorkItemScheduleBaseline[]>(
    `/project-spaces/${spaceId}/work-item-schedule-baselines`,
  )
}

export function createWorkItemScheduleBaseline(
  spaceId: string,
  name: string,
  request: WorkItemGanttRequest,
) {
  const requestId = crypto.randomUUID()
  return apiPost<WorkItemScheduleBaselineSnapshot>(
    `/project-spaces/${spaceId}/work-item-schedule-baselines`,
    { schemaVersion: 1, requestId, name, request },
    { requestId },
  )
}

export function compareWorkItemScheduleBaseline(
  spaceId: string,
  baselineId: string,
  request: WorkItemGanttRequest,
) {
  return apiPost<WorkItemScheduleBaselineDiff>(
    `/project-spaces/${spaceId}/work-item-schedule-baselines/${baselineId}:compare`,
    request,
  )
}

export function deleteWorkItemScheduleBaseline(
  spaceId: string,
  baseline: WorkItemScheduleBaseline,
) {
  const requestId = crypto.randomUUID()
  return apiPost<WorkItemScheduleBaseline>(
    `/project-spaces/${spaceId}/work-item-schedule-baselines/${baseline.id}:delete`,
    { requestId, expectedVersion: baseline.version },
    { requestId },
  )
}

export function renderWorkItemTimeline(
  spaceId: string,
  request: WorkItemGanttRequest,
) {
  return apiPost<WorkItemTimelineResult>(
    `/project-spaces/${spaceId}/work-item-timeline:render`,
    { schemaVersion: 1, request, limit: 100 },
  )
}
