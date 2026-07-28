import { apiGet, apiPost } from '../../../shared/api/httpClient'

export type WorklogRevision = {
  id: string
  revisionNumber: number
  workDate: string
  durationMinutes: number
  source: 'manual' | 'import' | 'proxy'
  approvalState: 'draft' | 'submitted' | 'void'
  reason: string
  actorId: string
  createdAt: string
}

export type Worklog = {
  id: string
  workItemId: string
  userId: string
  workDate: string
  durationMinutes: number
  source: 'manual' | 'import' | 'proxy'
  approvalState: 'draft' | 'submitted' | 'void'
  currentRevision: number
  version: number
  updatedBy: string
  updatedAt: string
  revisions: WorklogRevision[]
}

export type WorklogFoundation = {
  worklogs: Worklog[]
  variance: Array<{
    workItemId: string
    estimateUnit: string
    estimatedMinutes: number
    actualMinutes: number
    comparable: boolean
    varianceMinutes: number
    explanation: string
  }>
  truncated: boolean
}

export const resourceWorklogKeys = {
  all: ['project-spaces', 'resource-worklogs'] as const,
  detail: (spaceId: string) => [...resourceWorklogKeys.all, spaceId] as const,
}

export function getResourceWorklogs(spaceId: string) {
  return apiGet<WorklogFoundation>(
    `/project-spaces/${spaceId}/resource-planning/worklogs`,
  )
}

export function mutateResourceWorklog(
  spaceId: string,
  input: {
    operation: 'create' | 'update' | 'submit' | 'withdraw' | 'void'
    current?: Worklog
    workItemId?: string
    userId?: string
    workDate?: string
    durationMinutes?: number
    source?: Worklog['source']
    reason: string
  },
) {
  const requestId = crypto.randomUUID()
  return apiPost<Worklog>(
    `/project-spaces/${spaceId}/resource-planning/worklogs`,
    {
      schemaVersion: 1,
      requestId,
      operation: input.operation,
      worklogId: input.current?.id,
      expectedVersion: input.current?.version ?? 0,
      workItemId: input.workItemId,
      userId: input.userId,
      workDate: input.workDate,
      durationMinutes: input.durationMinutes ?? 0,
      source: input.source,
      reason: input.reason,
    },
    { requestId },
  )
}
