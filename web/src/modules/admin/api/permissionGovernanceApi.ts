import { apiGet, apiGetText } from '../../../shared/api/httpClient'

export type PermissionInspectionResult = {
  userId: string
  resourceType: string
  resourceId: string
  action: string
  allowed: boolean
  currentLevel: string
  requiredLevel: string
  source: string
  reason: string
  permissionId?: string | null
}

export type PermissionRiskItem = {
  id: string
  ruleCode: string
  severity: 'low' | 'medium' | 'high' | 'critical'
  resourceType?: string | null
  resourceId?: string | null
  subjectType?: string | null
  subjectId?: string | null
  subjectName?: string | null
  permissionLevel?: string | null
  reason: string
}

export type PermissionRiskSummary = {
  total: number
  items: PermissionRiskItem[]
}

export type InspectPermissionParams = {
  userId: string
  resourceType: string
  resourceId: string
  action?: string
}

export type PermissionRiskFilters = {
  knowledgeBaseId?: string
}

export async function inspectPermission(params: InspectPermissionParams): Promise<PermissionInspectionResult> {
  const query = new URLSearchParams({
    userId: params.userId,
    resourceType: params.resourceType,
    resourceId: params.resourceId,
    action: params.action ?? 'view',
  })
  return apiGet<PermissionInspectionResult>(`/admin/permission-governance/inspect?${query}`)
}

export async function listPermissionRisks(filters: PermissionRiskFilters = {}): Promise<PermissionRiskSummary> {
  const params = new URLSearchParams()
  if (filters.knowledgeBaseId) {
    params.set('knowledgeBaseId', filters.knowledgeBaseId)
  }
  const query = params.toString()
  return apiGet<PermissionRiskSummary>(query ? `/admin/permission-governance/risks?${query}` : '/admin/permission-governance/risks')
}

export async function exportPermissionRisks(filters: PermissionRiskFilters = {}): Promise<string> {
  const params = new URLSearchParams()
  if (filters.knowledgeBaseId) {
    params.set('knowledgeBaseId', filters.knowledgeBaseId)
  }
  const query = params.toString()
  return apiGetText(query ? `/admin/permission-governance/risks/export?${query}` : '/admin/permission-governance/risks/export')
}
