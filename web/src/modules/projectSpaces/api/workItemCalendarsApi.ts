import { apiGet, apiPost, apiPut } from '../../../shared/api/httpClient'

import type { QueryDefinition } from './workItemViewsApi'

export type WorkItemCalendarBinding = {
  startField: string
  endField?: string | null
  allDay: boolean
}

export type WorkItemCalendarWindow = {
  startDate: string
  endDate: string
  timezone: string
  mode: 'month' | 'week' | 'day'
}

export type WorkItemCalendarEvent = {
  workItemId: string
  displayKey: string
  title: string
  workItemVersion: number
  startValue?: string | null
  endValue?: string | null
  startInstant?: string | null
  endInstant?: string | null
  displayStartDate?: string | null
  displayEndDate?: string | null
  allDay: boolean
  overlapLane: number
  availableActions: string[]
}

export type WorkItemCalendarResult = {
  schemaVersion: 1
  viewKey: string
  queryHash: string
  binding: WorkItemCalendarBinding
  window: WorkItemCalendarWindow
  days: Array<{ date: string; events: WorkItemCalendarEvent[] }>
  noDateEvents: WorkItemCalendarEvent[]
  visibleEventCount: number
  nextCursor?: string | null
  candidateBoundReached: boolean
}

export type WorkItemCalendarPreference = {
  viewKey: string
  binding: WorkItemCalendarBinding
  timezone: string
  mode: 'month' | 'week' | 'day'
  version: number
  updatedAt: string
}

export type WorkItemCalendarRequest = {
  schemaVersion: 1
  viewKey: string
  binding: WorkItemCalendarBinding
  window: WorkItemCalendarWindow
  query: QueryDefinition
}

export const workItemCalendarKeys = {
  all: ['project-spaces', 'work-item-calendars'] as const,
  preference: (spaceId: string, viewKey: string) =>
    [...workItemCalendarKeys.all, spaceId, viewKey, 'preference'] as const,
  render: (spaceId: string, signature: string) =>
    [...workItemCalendarKeys.all, spaceId, 'render', signature] as const,
}

export function renderWorkItemCalendar(
  spaceId: string,
  request: WorkItemCalendarRequest,
) {
  return apiPost<WorkItemCalendarResult>(
    `/project-spaces/${spaceId}/work-item-calendars:render`,
    request,
  )
}

export function getWorkItemCalendarPreference(spaceId: string, viewKey: string) {
  return apiGet<WorkItemCalendarPreference | null>(
    `/project-spaces/${spaceId}/work-item-calendars/${viewKey}/preference`,
  )
}

export function saveWorkItemCalendarPreference(
  spaceId: string,
  viewKey: string,
  expectedVersion: number,
  binding: WorkItemCalendarBinding,
  timezone: string,
  mode: 'month' | 'week' | 'day',
) {
  const requestId = crypto.randomUUID()
  return apiPut<WorkItemCalendarPreference>(
    `/project-spaces/${spaceId}/work-item-calendars/${viewKey}/preference`,
    { requestId, expectedVersion, binding, timezone, mode },
    { requestId },
  )
}

export function mutateWorkItemCalendarDate(
  spaceId: string,
  viewKey: string,
  event: WorkItemCalendarEvent,
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
    `/project-spaces/${spaceId}/work-item-calendars/${viewKey}/items/${event.workItemId}:date`,
    {
      requestId,
      expectedWorkItemVersion: event.workItemVersion,
      ...values,
    },
    { requestId },
  )
}
