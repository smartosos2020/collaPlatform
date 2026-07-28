import { apiGet, apiPost } from '../../../shared/api/httpClient'

export type Allocation = {
  id: string
  workItemId: string
  userId: string
  startDate: string
  endDate: string
  allocationPercent: number
  status: 'active' | 'ended' | 'archived'
  version: number
  updatedBy: string
  updatedAt: string
}

export type CapacityRule = {
  id: string
  userId: string
  dailyMinutes: number
  warningPercent: number
  version: number
  updatedBy: string
  updatedAt: string
}

export type CapacityFoundation = {
  allocations: Allocation[]
  rules: CapacityRule[]
  buckets: Array<{
    userId: string
    date: string
    capacityMinutes: number
    allocatedMinutes: number
    actualMinutes: number
    signal: 'underloaded' | 'full' | 'overloaded'
    conflict: boolean
    explanation: string
  }>
  truncated: boolean
}

export const resourceCapacityKeys = {
  all: ['project-spaces', 'resource-capacity'] as const,
  detail: (spaceId: string) => [...resourceCapacityKeys.all, spaceId] as const,
}

export function getResourceCapacity(spaceId: string) {
  return apiGet<CapacityFoundation>(
    `/project-spaces/${spaceId}/resource-planning/capacity`,
  )
}

export function mutateAllocation(
  spaceId: string,
  input: {
    operation: 'create' | 'update' | 'end' | 'archive'
    current?: Allocation
    workItemId?: string
    userId?: string
    startDate?: string
    endDate?: string
    allocationPercent?: number
    reason: string
  },
) {
  const requestId = crypto.randomUUID()
  return apiPost<Allocation>(
    `/project-spaces/${spaceId}/resource-planning/capacity/allocations`,
    {
      schemaVersion: 1,
      requestId,
      operation: input.operation,
      allocationId: input.current?.id,
      expectedVersion: input.current?.version ?? 0,
      workItemId: input.workItemId,
      userId: input.userId,
      startDate: input.startDate,
      endDate: input.endDate,
      allocationPercent: input.allocationPercent,
      reason: input.reason,
    },
    { requestId },
  )
}

export function saveCapacityRule(
  spaceId: string,
  input: {
    current?: CapacityRule
    userId: string
    dailyMinutes: number
    warningPercent: number
  },
) {
  const requestId = crypto.randomUUID()
  return apiPost<CapacityRule>(
    `/project-spaces/${spaceId}/resource-planning/capacity/rules`,
    {
      schemaVersion: 1,
      requestId,
      expectedVersion: input.current?.version ?? 0,
      userId: input.userId,
      dailyMinutes: input.dailyMinutes,
      warningPercent: input.warningPercent,
    },
    { requestId },
  )
}
