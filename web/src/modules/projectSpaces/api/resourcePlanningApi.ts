import { apiGet, apiPost } from '../../../shared/api/httpClient'

export type CalendarException = {
  id: string
  date: string
  availableMinutes: number
  note: string
}

export type WorkCalendar = {
  id?: string
  timezone: string
  workDays: number[]
  dailyMinutes: number
  exceptions: CalendarException[]
  version: number
  updatedBy?: string
  updatedAt: string
}

export type Estimate = {
  id: string
  workItemId: string
  unit: 'hour' | 'day' | 'point'
  amount: number
  sourceWorkItemVersion: number
  version: number
  updatedBy: string
  updatedAt: string
}

export type ScheduleProjection = {
  workItemId: string
  estimateVersion: number
  timeComparable: boolean
  requiredMinutes: number
  projectedStart?: string
  projectedFinish?: string
  truncated: boolean
  explanation: string
}

export type ResourcePlanningFoundation = {
  calendar: WorkCalendar
  estimates: Estimate[]
  schedule: ScheduleProjection[]
}

export const resourcePlanningKeys = {
  all: ['project-spaces', 'resource-planning'] as const,
  detail: (spaceId: string) => [...resourcePlanningKeys.all, spaceId] as const,
}

export function getResourcePlanning(spaceId: string) {
  return apiGet<ResourcePlanningFoundation>(
    `/project-spaces/${spaceId}/resource-planning`,
  )
}

export function saveResourceCalendar(
  spaceId: string,
  calendar: WorkCalendar,
  input: Pick<WorkCalendar, 'timezone' | 'workDays' | 'dailyMinutes' | 'exceptions'>,
) {
  const requestId = crypto.randomUUID()
  return apiPost<WorkCalendar>(
    `/project-spaces/${spaceId}/resource-planning/calendar`,
    {
      schemaVersion: 1,
      requestId,
      expectedVersion: calendar.version,
      ...input,
    },
    { requestId },
  )
}

export function saveResourceEstimate(
  spaceId: string,
  input: {
    workItemId: string
    unit: Estimate['unit']
    amount: number
    expectedVersion: number
  },
) {
  const requestId = crypto.randomUUID()
  return apiPost<Estimate>(
    `/project-spaces/${spaceId}/resource-planning/estimates`,
    { schemaVersion: 1, requestId, ...input },
    { requestId },
  )
}
