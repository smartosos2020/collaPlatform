import { apiDelete, apiGet, apiGetText, apiPatch, apiPost } from '../../../shared/api/httpClient'
import type { DocumentDetail, DocumentSummary, DocumentTemplate, DocumentTreeNode } from '../../docs/api/docsApi'

export type UserKnowledgeSpaceView = {
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
  navigation?: {
    rootDocumentId: string
    homeDocumentId: string
    webPath: string
  }
  collaborationPermission?: {
    level: 'view' | 'comment' | 'edit'
    displayText: string
    canEdit: boolean
  }
  availableActions?: string[]
}

export type KnowledgeBaseSpaceSummary = UserKnowledgeSpaceView

export type UserKnowledgeSpaceDetailView = {
  space: UserKnowledgeSpaceView
  rootDocument: DocumentSummary
  homeDocument: DocumentSummary
}

export type KnowledgeBaseSpaceDetail = UserKnowledgeSpaceDetailView

export type UserKnowledgeDiscoveryView = {
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

export type KnowledgeBaseDiscovery = UserKnowledgeDiscoveryView

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
  return apiGet<UserKnowledgeSpaceView[]>(`/knowledge-bases${params.size ? `?${params}` : ''}`)
}

export function createKnowledgeBase(request: KnowledgeBaseSpaceRequest) {
  return apiPost<UserKnowledgeSpaceDetailView>('/knowledge-bases', request)
}

export function getKnowledgeBase(spaceId: string) {
  return apiGet<UserKnowledgeSpaceDetailView>(`/knowledge-bases/${spaceId}`)
}

export function listKnowledgeBaseItems(spaceId: string, options: { includeArchived?: boolean } = {}) {
  const params = new URLSearchParams()
  if (options.includeArchived) {
    params.set('includeArchived', 'true')
  }
  return apiGet<DocumentSummary[]>(`/knowledge-bases/${spaceId}/items${params.size ? `?${params}` : ''}`)
}

export function listKnowledgeBaseItemTree(spaceId: string, options: { includeArchived?: boolean } = {}) {
  const params = new URLSearchParams()
  if (options.includeArchived) {
    params.set('includeArchived', 'true')
  }
  return apiGet<DocumentTreeNode[]>(`/knowledge-bases/${spaceId}/items/tree${params.size ? `?${params}` : ''}`)
}

export function listKnowledgeBaseTemplates(spaceId: string) {
  return apiGet<DocumentTemplate[]>(`/knowledge-bases/${spaceId}/templates`)
}

export function createKnowledgeBaseTemplate(
  spaceId: string,
  request: {
    title: string
    description?: string
    category?: string
    content?: string
  },
) {
  return apiPost<DocumentTemplate>(`/knowledge-bases/${spaceId}/templates`, request)
}

export function createKnowledgeBaseItem(
  spaceId: string,
  request: {
    parentId?: string | null
    title: string
    docType?: DocumentSummary['docType']
    content?: string
    targetObjectType?: string
    targetObjectId?: string
    targetRoute?: string
    displayMode?: DocumentSummary['displayMode']
    targetTitleStrategy?: DocumentSummary['targetTitleStrategy']
    entryAlias?: string
  },
) {
  return apiPost<DocumentDetail>(`/knowledge-bases/${spaceId}/items`, request)
}

export function createKnowledgeBaseItemFromTemplate(
  spaceId: string,
  request: { templateId: string; parentId?: string | null; title?: string },
) {
  return apiPost<DocumentDetail>(`/knowledge-bases/${spaceId}/items/from-template`, request)
}

export function moveKnowledgeBaseItem(spaceId: string, documentId: string, request: { parentId?: string | null; sortOrder?: number }) {
  return apiPost<DocumentDetail>(`/knowledge-bases/${spaceId}/items/${documentId}/move`, request)
}

export function archiveKnowledgeBaseItem(spaceId: string, documentId: string) {
  return apiPost<DocumentDetail>(`/knowledge-bases/${spaceId}/items/${documentId}/archive`)
}

export function restoreKnowledgeBaseItem(spaceId: string, documentId: string) {
  return apiPost<DocumentDetail>(`/knowledge-bases/${spaceId}/items/${documentId}/restore`)
}

export function getKnowledgeBaseDiscovery(spaceId: string) {
  return apiGet<UserKnowledgeDiscoveryView>(`/knowledge-bases/${spaceId}/discovery`)
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

export function importKnowledgeBaseMarkdownBatch(
  spaceId: string,
  request: {
    parentId?: string | null
    items: Array<{ title: string; content: string; category?: string; tags?: string[] }>
  },
) {
  return apiPost<{ spaceId: string; importedCount: number; documents: DocumentSummary[] }>(
    `/knowledge-bases/${spaceId}/import/markdown-batch`,
    request,
  )
}

export function exportKnowledgeBaseMarkdown(spaceId: string) {
  return apiGetText(`/knowledge-bases/${spaceId}/export/markdown`)
}

export function updateKnowledgeBase(spaceId: string, request: Partial<KnowledgeBaseSpaceRequest>) {
  return apiPatch<UserKnowledgeSpaceDetailView>(`/knowledge-bases/${spaceId}`, request)
}

export function disableKnowledgeBase(spaceId: string) {
  return apiPost<UserKnowledgeSpaceDetailView>(`/knowledge-bases/${spaceId}/disable`)
}

export function restoreKnowledgeBase(spaceId: string) {
  return apiPost<UserKnowledgeSpaceDetailView>(`/knowledge-bases/${spaceId}/restore`)
}

export function archiveKnowledgeBase(spaceId: string) {
  return apiPost<UserKnowledgeSpaceDetailView>(`/knowledge-bases/${spaceId}/archive`)
}

export function deleteKnowledgeBase(spaceId: string) {
  return apiDelete<UserKnowledgeSpaceDetailView>(`/knowledge-bases/${spaceId}`)
}

export function subscribeKnowledgeTarget(spaceId: string, request: { targetType: 'knowledge_base' | 'document'; targetId?: string }) {
  return apiPost<KnowledgeBaseSubscription>(`/knowledge-bases/${spaceId}/subscriptions`, request)
}

export function unsubscribeKnowledgeTarget(spaceId: string, request: { targetType: 'knowledge_base' | 'document'; targetId?: string }) {
  return apiPost<KnowledgeBaseSubscription>(`/knowledge-bases/${spaceId}/subscriptions/remove`, request)
}
