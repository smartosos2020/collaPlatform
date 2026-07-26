import { apiGet, apiPost } from '../../../shared/api/httpClient'

export type NodeAvailableAction = {
  actionKey: string
  taskId?: string | null
  nodeKey: string
  reasonCode?: string | null
  expectedWorkItemVersion: number
  expectedInstanceVersion: number
  policyVersion: string
}

export type NodeTaskView = {
  id: string
  tokenId: string
  nodeKey: string
  assignmentStrategy: string
  status: string
  assigneeId?: string | null
  aggregateVersion: number
  createdAt: string
  plannedStartAt?: string | null
  dueAt?: string | null
  timedOutAt?: string | null
}

export type NodeTaskContext = {
  task: NodeTaskView
  form?: { fields?: Array<{ fieldKey: string; mode: string; required?: boolean; sortOrder?: number }> } | null
  values: Record<string, unknown>
  artifactPolicy?: unknown
  artifacts?: Array<Record<string, unknown>>
  candidateCount: number
  availableActions: NodeAvailableAction[]
}

export type NodeWorkflowPresentation = {
  capability: 'available' | 'not_configured' | 'uninitialized'
  policyVersion: string
  instanceId?: string | null
  status?: string | null
  workItemVersion: number
  aggregateVersion: number
  activeTokens: Array<{ id: string; nodeKey: string; stageKey?: string | null; status: string; enteredAt: string }>
  tasks: NodeTaskView[]
  availableActions: NodeAvailableAction[]
}

export type NodeHistoryEntry = {
  id: string
  sequenceNumber: number
  eventKind: string
  nodeKey?: string | null
  taskId?: string | null
  actorId?: string | null
  actorClass: string
  decisionReference?: string | null
  publicPayload: Record<string, unknown>
  occurredAt: string
}

export type NodeCommandResult = {
  workItemId: string
  instanceId: string
  taskId?: string | null
  operation: string
  nodeKey?: string | null
  instanceStatus: string
  workItemVersion: number
  aggregateVersion: number
  replayed: boolean
}

export type NodeBackfillBatch = {
  id: string
  typeDefinitionId: string
  targetTypeVersionId: string
  targetConfigHash: string
  targetEntryNodeKey: string
  requestedCount: number
  completedCount: number
  failedCount: number
  status: string
}

const base = (spaceId: string, workItemId: string) =>
  `/project-spaces/${spaceId}/work-items/${workItemId}/node-workflow`

export const nodeWorkflowKeys = {
  presentation: (spaceId: string, workItemId: string) =>
    ['node-workflow', spaceId, workItemId] as const,
  history: (spaceId: string, workItemId: string) =>
    ['node-workflow-history', spaceId, workItemId] as const,
  task: (spaceId: string, workItemId: string, taskId: string) =>
    ['node-workflow-task', spaceId, workItemId, taskId] as const,
}

export function getNodeWorkflow(spaceId: string, workItemId: string) {
  return apiGet<NodeWorkflowPresentation>(base(spaceId, workItemId))
}

export function getNodeWorkflowHistory(spaceId: string, workItemId: string) {
  return apiGet<{ items: NodeHistoryEntry[]; nextBeforeSequence?: number | null }>(
    `${base(spaceId, workItemId)}/history`,
  ).then((page) => page.items)
}

export function getNodeTask(spaceId: string, workItemId: string, taskId: string) {
  return apiGet<NodeTaskContext>(`${base(spaceId, workItemId)}/tasks/${taskId}`)
}

export function startNodeWorkflow(spaceId: string, workItemId: string, expectedWorkItemVersion: number) {
  return apiPost<NodeCommandResult>(`${base(spaceId, workItemId)}:start`, { expectedWorkItemVersion })
}

export function executeNodeTaskAction(
  spaceId: string,
  workItemId: string,
  taskId: string,
  operation: string,
  input: Record<string, unknown>,
) {
  return apiPost<NodeCommandResult>(
    `${base(spaceId, workItemId)}/tasks/${taskId}/actions/${operation}`,
    input,
    { requestId: crypto.randomUUID() },
  )
}

export function recoverNodeWorkflow(
  spaceId: string,
  workItemId: string,
  commandKey: string,
  input: Record<string, unknown>,
) {
  return apiPost<NodeCommandResult>(
    `${base(spaceId, workItemId)}/recoveries/${commandKey}`,
    input,
    { requestId: crypto.randomUUID() },
  )
}

export function upgradeNodeWorkflow(spaceId: string, workItemId: string, input: Record<string, unknown>) {
  return apiPost<NodeCommandResult>(`${base(spaceId, workItemId)}:upgrade`, input, {
    requestId: crypto.randomUUID(),
  })
}

export function createNodeBackfill(
  spaceId: string,
  input: { typeDefinitionId: string; targetTypeVersionId: string; workItemIds: string[] },
) {
  return apiPost<NodeBackfillBatch>(`/project-spaces/${spaceId}/node-workflow-backfills`, input)
}

export function resumeNodeBackfill(spaceId: string, batchId: string) {
  return apiPost<NodeBackfillBatch>(
    `/project-spaces/${spaceId}/node-workflow-backfills/${batchId}:resume`,
  )
}

export function verifyNodeBackfill(spaceId: string, batchId: string) {
  return apiGet<{
    batchId: string
    status: string
    verifiedCount: number
    failures: Array<{ workItemId: string; code: string; message: string }>
  }>(`/project-spaces/${spaceId}/node-workflow-backfills/${batchId}:verify`)
}
