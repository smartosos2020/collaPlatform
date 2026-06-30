import { apiDelete, apiGet, apiGetText, apiPatch, apiPost } from '../../../shared/api/httpClient'
import type { DocumentSummary } from '../../docs/api/docsApi'

export type KnowledgeBaseSpaceSummary = {
  id: string
  name: string
  code: string
  description?: string | null
  icon?: string | null
  coverUrl?: string | null
  status: 'active' | 'disabled' | 'archived'
  visibility: 'private' | 'workspace'
  rootDocumentId: string
  homeDocumentId: string
  ownerId: string
  ownerName: string
  defaultPermissionLevel: 'view' | 'comment' | 'edit'
  createdAt: string
  updatedAt?: string | null
  documentCount: number
}

export type KnowledgeBaseSpaceDetail = {
  space: KnowledgeBaseSpaceSummary
  rootDocument: DocumentSummary
  homeDocument: DocumentSummary
}

export type KnowledgeBaseDiscovery = {
  spaceId: string
  recentAccessed: DocumentSummary[]
  favorites: DocumentSummary[]
  maintainedByMe: DocumentSummary[]
  dueForReview: DocumentSummary[]
  popular: DocumentSummary[]
  recommended: DocumentSummary[]
  subscribedDocuments: DocumentSummary[]
  spaceSubscribed: boolean
}

export type KnowledgeBaseSubscription = {
  targetType: 'knowledge_base' | 'document'
  targetId: string
  subscribed: boolean
}

export type KnowledgeBaseHealthMetrics = {
  documentCount: number
  activeDocumentCount: number
  outdatedDocumentCount: number
  unmaintainedDocumentCount: number
  ownerlessDocumentCount: number
  highRiskPermissionCount: number
}

export type KnowledgeBaseGovernanceRisk = {
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
}

export type KnowledgeBaseAccessDocumentStat = {
  document: DocumentSummary
  visitorCount: number
  accessCount: number
  lastAccessedAt?: string | null
}

export type KnowledgeBaseSearchTermStat = {
  query: string
  count: number
  lastSearchedAt: string
}

export type KnowledgeBaseAccessStats = {
  visitorCount: number
  accessCount: number
  popularDocuments: KnowledgeBaseAccessDocumentStat[]
  lowAccessDocuments: KnowledgeBaseAccessDocumentStat[]
  noResultTerms: KnowledgeBaseSearchTermStat[]
}

export type KnowledgeBaseGovernanceDashboard = {
  spaceId: string
  health: KnowledgeBaseHealthMetrics
  risks: KnowledgeBaseGovernanceRisk[]
  accessStats: KnowledgeBaseAccessStats
}

export type KnowledgeBaseBulkGovernanceRequest = {
  documentIds: string[]
  maintainerId?: string
  tags?: string[]
  replaceTags?: boolean
  archive?: boolean
  requestReview?: boolean
  reviewDueAt?: string
}

export type KnowledgeBaseBulkGovernanceResult = {
  updatedCount: number
  archivedCount: number
  reviewRequestedCount: number
}

export type KnowledgeBaseSpaceRequest = {
  name: string
  code?: string
  description?: string
  icon?: string
  coverUrl?: string
  visibility?: KnowledgeBaseSpaceSummary['visibility']
  defaultPermissionLevel?: KnowledgeBaseSpaceSummary['defaultPermissionLevel']
  homeDocumentId?: string
}

export function listKnowledgeBases(options: { includeArchived?: boolean } = {}) {
  const params = new URLSearchParams()
  if (options.includeArchived) {
    params.set('includeArchived', 'true')
  }
  return apiGet<KnowledgeBaseSpaceSummary[]>(`/knowledge-bases${params.size ? `?${params}` : ''}`)
}

export function createKnowledgeBase(request: KnowledgeBaseSpaceRequest) {
  return apiPost<KnowledgeBaseSpaceDetail>('/knowledge-bases', request)
}

export function getKnowledgeBase(spaceId: string) {
  return apiGet<KnowledgeBaseSpaceDetail>(`/knowledge-bases/${spaceId}`)
}

export function getKnowledgeBaseDiscovery(spaceId: string) {
  return apiGet<KnowledgeBaseDiscovery>(`/knowledge-bases/${spaceId}/discovery`)
}

export function getKnowledgeBaseGovernance(spaceId: string) {
  return apiGet<KnowledgeBaseGovernanceDashboard>(`/knowledge-bases/${spaceId}/governance`)
}

export function bulkGovernKnowledgeBase(spaceId: string, request: KnowledgeBaseBulkGovernanceRequest) {
  return apiPost<KnowledgeBaseBulkGovernanceResult>(`/knowledge-bases/${spaceId}/governance/bulk`, request)
}

export function exportKnowledgeBaseGovernance(spaceId: string) {
  return apiGetText(`/knowledge-bases/${spaceId}/governance/export`)
}

export function updateKnowledgeBase(spaceId: string, request: Partial<KnowledgeBaseSpaceRequest>) {
  return apiPatch<KnowledgeBaseSpaceDetail>(`/knowledge-bases/${spaceId}`, request)
}

export function disableKnowledgeBase(spaceId: string) {
  return apiPost<KnowledgeBaseSpaceDetail>(`/knowledge-bases/${spaceId}/disable`)
}

export function restoreKnowledgeBase(spaceId: string) {
  return apiPost<KnowledgeBaseSpaceDetail>(`/knowledge-bases/${spaceId}/restore`)
}

export function archiveKnowledgeBase(spaceId: string) {
  return apiPost<KnowledgeBaseSpaceDetail>(`/knowledge-bases/${spaceId}/archive`)
}

export function deleteKnowledgeBase(spaceId: string) {
  return apiDelete<KnowledgeBaseSpaceDetail>(`/knowledge-bases/${spaceId}`)
}

export function subscribeKnowledgeTarget(spaceId: string, request: { targetType: 'knowledge_base' | 'document'; targetId?: string }) {
  return apiPost<KnowledgeBaseSubscription>(`/knowledge-bases/${spaceId}/subscriptions`, request)
}

export function unsubscribeKnowledgeTarget(spaceId: string, request: { targetType: 'knowledge_base' | 'document'; targetId?: string }) {
  return apiPost<KnowledgeBaseSubscription>(`/knowledge-bases/${spaceId}/subscriptions/remove`, request)
}
