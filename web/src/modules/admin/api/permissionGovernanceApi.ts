import { apiGet, apiGetText, apiPost } from '../../../shared/api/httpClient'

export type AdminPermissionInspectionView = {
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
  risk?: {
    level: 'low' | 'medium' | 'high' | 'critical'
    weight: number
  }
  impactScope?: {
    resourceType: string
    resourceId: string
    subjectId: string
  }
  suggestedAction?: string
  auditContext?: Record<string, string>
}

export type PermissionInspectionResult = AdminPermissionInspectionView

export type AdminPermissionRiskItemView = {
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
  source?: string
  impactScope?: {
    resourceType?: string | null
    resourceId?: string | null
    subjectId?: string | null
  }
  suggestedAction?: string
  auditContext?: Record<string, string>
}

export type PermissionRiskItem = AdminPermissionRiskItemView

export type AdminPermissionRiskSummaryView = {
  total: number
  items: AdminPermissionRiskItemView[]
  severityBuckets?: Partial<Record<'low' | 'medium' | 'high' | 'critical', number>>
}

export type PermissionRiskSummary = AdminPermissionRiskSummaryView

export type PermissionRiskRemediation = {
  riskId: string
  ruleCode: string
  executable: boolean
  applied: boolean
  action: string
  reason: string
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

export async function inspectPermission(params: InspectPermissionParams): Promise<AdminPermissionInspectionView> {
  const query = new URLSearchParams({
    userId: params.userId,
    resourceType: params.resourceType,
    resourceId: params.resourceId,
    action: params.action ?? 'view',
  })
  return apiGet<AdminPermissionInspectionView>(`/admin/permission-governance/inspect?${query}`)
}

export async function listPermissionRisks(filters: PermissionRiskFilters = {}): Promise<AdminPermissionRiskSummaryView> {
  const params = new URLSearchParams()
  if (filters.knowledgeBaseId) {
    params.set('knowledgeBaseId', filters.knowledgeBaseId)
  }
  const query = params.toString()
  return apiGet<AdminPermissionRiskSummaryView>(
    query ? `/admin/permission-governance/risks?${query}` : '/admin/permission-governance/risks',
  )
}

export async function exportPermissionRisks(filters: PermissionRiskFilters = {}): Promise<string> {
  const params = new URLSearchParams()
  if (filters.knowledgeBaseId) {
    params.set('knowledgeBaseId', filters.knowledgeBaseId)
  }
  const query = params.toString()
  return apiGetText(query ? `/admin/permission-governance/risks/export?${query}` : '/admin/permission-governance/risks/export')
}

export async function remediatePermissionRisk(riskId: string, confirm = false): Promise<PermissionRiskRemediation> {
  return apiPost<PermissionRiskRemediation>(
    `/admin/permission-governance/risks/${encodeURIComponent(riskId)}/remediation?confirm=${confirm}`,
  )
}
