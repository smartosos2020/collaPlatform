import { apiGet, apiPost } from '../../../shared/api/httpClient'

export type BatchCapability = { resourceType: string; action: string; label: string }
export type BatchItem = { targetId: string; status: string; message: string }
export type BatchReport = {
  operationId: string
  resourceType: string
  action: string
  targetCount: number
  readyCount: number
  executed: boolean
  items: BatchItem[]
}

export function listBatchCapabilities() {
  return apiGet<BatchCapability[]>('/admin/batch-governance/capabilities')
}

export function previewBatch(command: { resourceType: string; action: string; targetIds: string[] }) {
  return apiPost<BatchReport>('/admin/batch-governance/preview', command)
}

export function executeBatch(command: { resourceType: string; action: string; targetIds: string[] }) {
  return apiPost<BatchReport>('/admin/batch-governance/execute?confirm=true', command)
}
