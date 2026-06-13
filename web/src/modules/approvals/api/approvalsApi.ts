import { apiGet, apiPost } from '../../../shared/api/httpClient'

export type ApprovalFormField = {
  key: string
  label: string
  type: 'text' | 'textarea' | 'number' | 'select' | 'datetime' | 'file'
  options?: string[]
  required?: boolean
}

export type ApprovalFormSummary = {
  id: string
  formKey: string
  name: string
  description?: string | null
  category: string
  schema: {
    fields?: ApprovalFormField[]
  }
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export type ApprovalInstanceSummary = {
  id: string
  formId: string
  formKey: string
  formName: string
  title: string
  applicantId: string
  applicantName: string
  status: 'pending' | 'approved' | 'rejected' | 'withdrawn'
  currentNodeOrder: number
  submittedAt: string
  completedAt?: string | null
  createdAt: string
  updatedAt: string
}

export type ApprovalTaskSummary = {
  id: string
  instanceId: string
  instanceTitle: string
  formName: string
  applicantName: string
  nodeOrder: number
  assigneeId: string
  assigneeName: string
  status: 'pending' | 'approved' | 'rejected' | 'canceled'
  comment?: string | null
  actedAt?: string | null
  transferredTo?: string | null
  transferredToName?: string | null
  createdAt: string
  updatedAt: string
}

export type ApprovalActionLog = {
  id: string
  instanceId: string
  actorId?: string | null
  actorName?: string | null
  action: string
  fromStatus?: string | null
  toStatus?: string | null
  comment?: string | null
  metadata: Record<string, unknown>
  createdAt: string
}

export type ApprovalInstanceDetail = {
  instance: ApprovalInstanceSummary
  form: ApprovalFormSummary
  payload: Record<string, unknown>
  tasks: ApprovalTaskSummary[]
  actions: ApprovalActionLog[]
}

export type ApprovalStats = {
  pendingTodos: number
  submittedPending: number
  approved: number
  rejected: number
  withdrawn: number
  byForm: Array<{ key: string; label: string; count: number }>
  byStatus: Array<{ key: string; label: string; count: number }>
}

export function listApprovalForms() {
  return apiGet<ApprovalFormSummary[]>('/approvals/forms')
}

export function listApprovalInstances() {
  return apiGet<ApprovalInstanceSummary[]>('/approvals/instances')
}

export function listApprovalTodos() {
  return apiGet<ApprovalTaskSummary[]>('/approvals/todos')
}

export function getApprovalInstance(instanceId: string) {
  return apiGet<ApprovalInstanceDetail>(`/approvals/instances/${instanceId}`)
}

export function getApprovalStats() {
  return apiGet<ApprovalStats>('/approvals/stats')
}

export function startApproval(request: { formId: string; title?: string; payload: Record<string, unknown> }) {
  return apiPost<ApprovalInstanceDetail>('/approvals/instances', request)
}

export function withdrawApproval(instanceId: string, comment?: string) {
  return apiPost<ApprovalInstanceDetail>(`/approvals/instances/${instanceId}/withdraw`, { comment })
}

export function approveApproval(instanceId: string, taskId?: string, comment?: string) {
  return apiPost<ApprovalInstanceDetail>(`/approvals/instances/${instanceId}/approve`, { taskId, comment })
}

export function rejectApproval(instanceId: string, taskId?: string, comment?: string) {
  return apiPost<ApprovalInstanceDetail>(`/approvals/instances/${instanceId}/reject`, { taskId, comment })
}

export function transferApproval(instanceId: string, request: { taskId?: string; assigneeId: string; comment?: string }) {
  return apiPost<ApprovalInstanceDetail>(`/approvals/instances/${instanceId}/transfer`, request)
}
