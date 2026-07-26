import { apiGet, apiPost } from '../../../shared/api/httpClient'

export type WorkItemWorkflowAction = {
  actionKey: string
  label: string
  kind: 'forward' | 'return_action' | 'reopen' | 'terminate' | 'restore'
  requiredFieldKeys: string[]
  sortOrder: number
  policyVersion: string
}

export type WorkItemWorkflow = {
  capability: 'available' | 'not_configured' | 'uninitialized'
  policyVersion: string
  currentStateKey?: string | null
  currentStateLabel?: string | null
  currentStateCategory?: 'initial' | 'active' | 'terminal' | 'canceled' | null
  aggregateVersion: number
  availableActions: WorkItemWorkflowAction[]
}

export type WorkItemWorkflowHistoryEntry = {
  id: string
  sequenceNumber: number
  fromStateKey?: string | null
  toStateKey: string
  actionKey?: string | null
  actionKind: string
  actorId: string
  decisionReference: string
  correlationId: string
  occurredAt: string
}

export type WorkItemWorkflowCommandResult = {
  workItemId: string
  actionKey: string
  fromStateKey: string
  toStateKey: string
  workItemVersion: number
  aggregateVersion: number
  replayed: boolean
}

export type WorkItemStateBackfillBatch = {
  id: string
  spaceId: string
  typeDefinitionId: string
  targetTypeVersionId: string
  targetConfigHash: string
  targetStateKey: string
  status: string
  requestedCount: number
  completedCount: number
  failedCount: number
  manifestHash: string
  createdAt: string
  completedAt?: string | null
}

export type WorkItemStateBackfillVerification = {
  batchId: string
  status: string
  verifiedCount: number
  failures: Array<{
    workItemId: string
    errorCode: string
    errorMessage: string
  }>
}

export const workItemWorkflowKeys = {
  all: ['project-spaces', 'work-item-workflows'] as const,
  detail: (spaceId: string, workItemId: string) =>
    [...workItemWorkflowKeys.all, spaceId, workItemId] as const,
  history: (spaceId: string, workItemId: string) =>
    [...workItemWorkflowKeys.detail(spaceId, workItemId), 'history'] as const,
}

export function getWorkItemWorkflow(spaceId: string, workItemId: string) {
  return apiGet<WorkItemWorkflow>(
    `/project-spaces/${spaceId}/work-items/${workItemId}/workflow`,
  )
}

export function listWorkItemWorkflowHistory(spaceId: string, workItemId: string) {
  return apiGet<{ items: WorkItemWorkflowHistoryEntry[]; nextBeforeSequence?: number | null }>(
    `/project-spaces/${spaceId}/work-items/${workItemId}/workflow/history`,
  )
}

export function executeWorkItemWorkflowAction(
  spaceId: string,
  workItemId: string,
  actionKey: string,
  request: {
    fromStateKey: string
    expectedWorkItemVersion: number
    fieldPatch: Record<string, unknown>
  },
  requestId: string,
) {
  return apiPost<WorkItemWorkflowCommandResult>(
    `/project-spaces/${spaceId}/work-items/${workItemId}/workflow/actions/${encodeURIComponent(actionKey)}`,
    request,
    { requestId, retry: false },
  )
}

export function correctWorkItemWorkflowState(
  spaceId: string,
  workItemId: string,
  request: {
    targetStateKey: string
    expectedWorkItemVersion: number
    reason: string
    confirmation: 'CORRECT_WORKFLOW_STATE'
  },
) {
  return apiPost<WorkItemWorkflowCommandResult>(
    `/project-spaces/${spaceId}/work-items/${workItemId}/workflow/corrections`,
    request,
    { retry: false },
  )
}

export function createWorkItemStateBackfill(
  spaceId: string,
  request: {
    typeDefinitionId: string
    targetTypeVersionId: string
    targetStateKey: string
    workItemIds: string[]
    reason: string
    confirmation: 'INITIALIZE_EXISTING_WORKFLOW_STATES'
  },
) {
  return apiPost<WorkItemStateBackfillBatch>(
    `/project-spaces/${spaceId}/workflow-backfills`,
    request,
    { retry: false },
  )
}

export function resumeWorkItemStateBackfill(spaceId: string, batchId: string) {
  return apiPost<WorkItemStateBackfillBatch>(
    `/project-spaces/${spaceId}/workflow-backfills/${batchId}:resume`,
    { confirmation: 'RESUME_WORKFLOW_STATE_BACKFILL' },
    { retry: false },
  )
}

export function verifyWorkItemStateBackfill(spaceId: string, batchId: string) {
  return apiGet<WorkItemStateBackfillVerification>(
    `/project-spaces/${spaceId}/workflow-backfills/${batchId}:verify`,
  )
}
