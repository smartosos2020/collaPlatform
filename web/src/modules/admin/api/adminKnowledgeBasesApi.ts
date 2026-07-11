import { apiDelete, apiGet, apiGetText, apiPatch, apiPost } from '../../../shared/api/httpClient'

export type AdminKnowledgeBaseSpaceView = {
  id: string
  name: string
  code: string
  description?: string | null
  icon?: string | null
  coverUrl?: string | null
  status: 'active' | 'disabled' | 'archived'
  visibility: 'private' | 'workspace'
  rootItemId: string
  homeItemId: string
  ownerId: string
  ownerName: string
  defaultPermissionLevel: 'view' | 'comment' | 'edit'
  createdAt: string
  updatedAt?: string | null
  itemCount: number
  governance: {
    status: string
    visibility: string
    defaultPermissionLevel: string
  }
  auditScope: {
    targetType: 'knowledge_base'
    targetId: string
    actions: string[]
  }
  availableActions: string[]
}

export type AdminKnowledgeBaseContentRef = {
  id: string
  parentId?: string | null
  title: string
  contentType: string
  archived: boolean
  maintainerId?: string | null
  maintainerName?: string | null
  tags: string[]
  knowledgeStatus?: string | null
  reviewDueAt?: string | null
}

export type AdminKnowledgeBaseDetailView = {
  space: AdminKnowledgeBaseSpaceView
  rootItem: AdminKnowledgeBaseContentRef
  homeItem: AdminKnowledgeBaseContentRef
}

export type AdminKnowledgeBaseHealthView = {
  itemCount: number
  activeItemCount: number
  outdatedItemCount: number
  unmaintainedItemCount: number
  ownerlessItemCount: number
  highRiskPermissionCount: number
  blockCoverageGapCount: number
  emptyBlockCount: number
  invalidEmbedBlockCount: number
  blockCoveragePercent: number
}

export type AdminKnowledgeBaseGovernanceRiskView = {
  id: string
  ruleCode: string
  severity: 'low' | 'medium' | 'high' | 'critical'
  resourceType: string
  resourceId: string
  title?: string | null
  subjectType?: string | null
  subjectId?: string | null
  subjectName?: string | null
  permissionLevel?: string | null
  reason: string
  actionPath?: string | null
  risk: {
    severity: string
    weight: number
  }
}

export type AdminKnowledgeBaseAccessItemView = {
  item: AdminKnowledgeBaseContentRef
  visitorCount: number
  accessCount: number
  lastAccessedAt?: string | null
}

export type AdminKnowledgeBaseSearchTermStat = {
  query: string
  count: number
  lastSearchedAt: string
}

export type AdminKnowledgeBaseGovernanceView = {
  space: AdminKnowledgeBaseSpaceView
  health: AdminKnowledgeBaseHealthView
  risks: AdminKnowledgeBaseGovernanceRiskView[]
  accessStats: {
    visitorCount: number
    accessCount: number
    popularItems: AdminKnowledgeBaseAccessItemView[]
    lowAccessItems: AdminKnowledgeBaseAccessItemView[]
    noResultTerms: AdminKnowledgeBaseSearchTermStat[]
  }
  severityBuckets: Record<string, number>
  bulkActions: string[]
}

export type AdminKnowledgeBaseBulkGovernanceRequest = {
  itemIds: string[]
  maintainerId?: string
  tags?: string[]
  replaceTags?: boolean
  archive?: boolean
  requestReview?: boolean
  reviewDueAt?: string
}

export type AdminKnowledgeBaseBulkGovernanceResult = {
  updatedCount: number
  archivedCount: number
  reviewRequestedCount: number
}

export function listAdminKnowledgeBases(options: { includeArchived?: boolean } = { includeArchived: true }) {
  const params = new URLSearchParams()
  if (options.includeArchived) {
    params.set('includeArchived', 'true')
  }
  return apiGet<AdminKnowledgeBaseSpaceView[]>(`/admin/knowledge-bases${params.size ? `?${params}` : ''}`)
}

export function getAdminKnowledgeBase(spaceId: string) {
  return apiGet<AdminKnowledgeBaseDetailView>(`/admin/knowledge-bases/${spaceId}`)
}

export function updateAdminKnowledgeBase(spaceId: string, request: Partial<AdminKnowledgeBaseSpaceView>) {
  return apiPatch<AdminKnowledgeBaseDetailView>(`/admin/knowledge-bases/${spaceId}`, request)
}

export function disableAdminKnowledgeBase(spaceId: string) {
  return apiPost<AdminKnowledgeBaseDetailView>(`/admin/knowledge-bases/${spaceId}/disable`)
}

export function restoreAdminKnowledgeBase(spaceId: string) {
  return apiPost<AdminKnowledgeBaseDetailView>(`/admin/knowledge-bases/${spaceId}/restore`)
}

export function archiveAdminKnowledgeBase(spaceId: string) {
  return apiPost<AdminKnowledgeBaseDetailView>(`/admin/knowledge-bases/${spaceId}/archive`)
}

export function deleteAdminKnowledgeBase(spaceId: string) {
  return apiDelete<AdminKnowledgeBaseDetailView>(`/admin/knowledge-bases/${spaceId}`)
}

export function getAdminKnowledgeBaseGovernance(spaceId: string) {
  return apiGet<AdminKnowledgeBaseGovernanceView>(`/admin/knowledge-bases/${spaceId}/governance`)
}

export function bulkGovernAdminKnowledgeBase(spaceId: string, request: AdminKnowledgeBaseBulkGovernanceRequest) {
  return apiPost<AdminKnowledgeBaseBulkGovernanceResult>(`/admin/knowledge-bases/${spaceId}/governance/bulk`, request)
}

export function exportAdminKnowledgeBaseGovernance(spaceId: string) {
  return apiGetText(`/admin/knowledge-bases/${spaceId}/governance/export`)
}
