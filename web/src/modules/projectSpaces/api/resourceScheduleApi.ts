import { apiGet, apiPost } from '../../../shared/api/httpClient'

export type SchedulePreference = {
  id?: string
  windowStart: string
  windowEnd: string
  zoom: 'day' | 'week' | 'month'
  version: number
  updatedAt?: string
}

export type ResourceSchedule = {
  windowStart: string
  windowEnd: string
  zoom: 'day' | 'week' | 'month'
  rows: Array<{
    userId: string
    capacityMinutes: number
    allocatedMinutes: number
    actualMinutes: number
    conflictCount: number
  }>
  bars: Array<{
    allocationId: string
    workItemId: string
    userId: string
    startDate: string
    endDate: string
    allocationPercent: number
    sourceVersion: number
  }>
  conflicts: Array<{
    userId: string
    date: string
    signal: string
    capacityMinutes: number
    allocatedMinutes: number
    explanation: string
  }>
  preference: SchedulePreference
  truncated: boolean
}

export const resourceScheduleKeys = {
  all: ['project-spaces', 'resource-schedule'] as const,
  detail: (spaceId: string) => [...resourceScheduleKeys.all, spaceId] as const,
}

export function getResourceSchedule(spaceId: string) {
  return apiGet<ResourceSchedule>(
    `/project-spaces/${spaceId}/resource-planning/schedule`,
  )
}

export function saveResourceSchedulePreference(
  spaceId: string,
  input: {
    current: SchedulePreference
    windowStart: string
    windowEnd: string
    zoom: 'day' | 'week' | 'month'
  },
) {
  const requestId = crypto.randomUUID()
  return apiPost<SchedulePreference>(
    `/project-spaces/${spaceId}/resource-planning/schedule/preference`,
    {
      schemaVersion: 1,
      requestId,
      expectedVersion: input.current.version,
      windowStart: input.windowStart,
      windowEnd: input.windowEnd,
      zoom: input.zoom,
    },
    { requestId },
  )
}

export function adjustResourceAllocation(
  spaceId: string,
  input: {
    requestId: string
    preview: boolean
    allocationId: string
    expectedVersion: number
    startDate: string
    endDate: string
    allocationPercent: number
    reason: string
  },
) {
  return apiPost<{
    preview: boolean
    committed: boolean
    allocationId: string
    startDate: string
    endDate: string
    allocationPercent: number
    version: number
    provenance: string
  }>(
    `/project-spaces/${spaceId}/resource-planning/schedule/adjustments`,
    { schemaVersion: 1, ...input },
    { requestId: input.requestId },
  )
}
