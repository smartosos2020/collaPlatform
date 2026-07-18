import { apiDelete, apiGet, apiGetText, apiPatch, apiPost } from '../../../shared/api/httpClient'
import type { KnowledgeContentDetail, KnowledgeBaseItem, KnowledgeContentTemplate, KnowledgeBaseItemTreeNode } from '../content/api/knowledgeContentApi'

export type UserKnowledgeSpaceView = {
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
  navigation?: {
    rootItemId: string
    homeItemId: string
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
  rootItem: KnowledgeBaseItem
  homeItem: KnowledgeBaseItem
}

export type KnowledgeBaseSpaceDetail = UserKnowledgeSpaceDetailView

export type UserKnowledgeDiscoveryView = {
  spaceId: string
  recentAccessed: KnowledgeBaseItem[]
  favorites: KnowledgeBaseItem[]
  maintainedByMe: KnowledgeBaseItem[]
  dueForReview: KnowledgeBaseItem[]
  popular: KnowledgeBaseItem[]
  recommended: KnowledgeBaseItem[]
  subscribedItems: KnowledgeBaseItem[]
  spaceSubscribed: boolean
}

export type KnowledgeBaseDiscovery = UserKnowledgeDiscoveryView

export type KnowledgeBaseSubscription = {
  targetType: 'knowledge_base' | 'knowledge_content'
  targetId: string
  subscribed: boolean
}

export type KnowledgeBaseHealthMetrics = {
  itemCount: number
  activeItemCount: number
  outdatedItemCount: number
  unmaintainedItemCount: number
  ownerlessItemCount: number
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

export type KnowledgeBaseAccessItemStat = {
  item: KnowledgeBaseItem
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
  popularItems: KnowledgeBaseAccessItemStat[]
  lowAccessItems: KnowledgeBaseAccessItemStat[]
  noResultTerms: KnowledgeBaseSearchTermStat[]
}

export type KnowledgeBaseGovernanceDashboard = {
  spaceId: string
  health: KnowledgeBaseHealthMetrics
  risks: KnowledgeBaseGovernanceRisk[]
  accessStats: KnowledgeBaseAccessStats
}

export type KnowledgeBaseBulkGovernanceRequest = {
  itemIds: string[]
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
  homeItemId?: string
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
  return apiGet<KnowledgeBaseItem[]>(`/knowledge-bases/${spaceId}/items${params.size ? `?${params}` : ''}`)
}

export function listKnowledgeBaseItemTree(spaceId: string, options: { includeArchived?: boolean } = {}) {
  const params = new URLSearchParams()
  if (options.includeArchived) {
    params.set('includeArchived', 'true')
  }
  return apiGet<KnowledgeBaseItemTreeNode[]>(`/knowledge-bases/${spaceId}/items/tree${params.size ? `?${params}` : ''}`)
}

export function listKnowledgeBaseTemplates(spaceId: string) {
  return apiGet<KnowledgeContentTemplate[]>(`/knowledge-bases/${spaceId}/templates`)
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
  return apiPost<KnowledgeContentTemplate>(`/knowledge-bases/${spaceId}/templates`, request)
}

export function createKnowledgeBaseItem(
  spaceId: string,
  request: {
    parentId?: string | null
    title: string
    contentType?: KnowledgeBaseItem['contentType']
    content?: string
    targetObjectType?: string
    targetObjectId?: string
    targetRoute?: string
    displayMode?: KnowledgeBaseItem['displayMode']
    targetTitleStrategy?: KnowledgeBaseItem['targetTitleStrategy']
    entryAlias?: string
  },
) {
  return apiPost<KnowledgeContentDetail>(`/knowledge-bases/${spaceId}/items`, request)
}

export function createKnowledgeBaseBaseEntry(
  spaceId: string,
  request: {
    parentId?: string | null
    existingBaseId?: string
    newBaseName?: string
    newBaseDescription?: string
    displayMode?: KnowledgeBaseItem['displayMode']
    targetTitleStrategy?: KnowledgeBaseItem['targetTitleStrategy']
    entryAlias?: string
  },
) {
  return apiPost<KnowledgeContentDetail>(`/knowledge-bases/${spaceId}/items/base-entry`, request)
}

export function updateKnowledgeObjectEntry(
  spaceId: string,
  itemId: string,
  request: {
    displayMode?: KnowledgeBaseItem['displayMode']
    targetTitleStrategy?: KnowledgeBaseItem['targetTitleStrategy']
    entryAlias?: string
  },
) {
  return apiPatch<KnowledgeContentDetail>(`/knowledge-bases/${spaceId}/items/${itemId}/object-entry`, request)
}

export function createKnowledgeBaseItemFromTemplate(
  spaceId: string,
  request: { templateId: string; parentId?: string | null; title?: string },
) {
  return apiPost<KnowledgeContentDetail>(`/knowledge-bases/${spaceId}/items/from-template`, request)
}

export function moveKnowledgeBaseItem(spaceId: string, itemId: string, request: { parentId?: string | null; sortOrder?: number }) {
  return apiPost<KnowledgeContentDetail>(`/knowledge-bases/${spaceId}/items/${itemId}/move`, request)
}

export function copyKnowledgeBaseItem(spaceId: string, itemId: string, request: { parentId?: string | null; title?: string }) {
  return apiPost<KnowledgeContentDetail>(`/knowledge-bases/${spaceId}/items/${itemId}/copy`, request)
}

export function upgradeKnowledgeBaseTemplate(spaceId: string, templateId: string, content: string) {
  return apiPost<KnowledgeContentTemplate>(`/knowledge-bases/${spaceId}/templates/${templateId}/upgrade`, { content })
}

export function archiveKnowledgeBaseItem(spaceId: string, itemId: string) {
  return apiPost<KnowledgeContentDetail>(`/knowledge-bases/${spaceId}/items/${itemId}/archive`)
}

export function restoreKnowledgeBaseItem(spaceId: string, itemId: string) {
  return apiPost<KnowledgeContentDetail>(`/knowledge-bases/${spaceId}/items/${itemId}/restore`)
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
  return apiPost<{ spaceId: string; importedCount: number; items: KnowledgeBaseItem[] }>(
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
  return apiDelete<void>(`/knowledge-bases/${spaceId}`)
}

export function subscribeKnowledgeTarget(spaceId: string, request: { targetType: 'knowledge_base' | 'knowledge_content'; targetId?: string }) {
  return apiPost<KnowledgeBaseSubscription>(`/knowledge-bases/${spaceId}/subscriptions`, request)
}

export function unsubscribeKnowledgeTarget(spaceId: string, request: { targetType: 'knowledge_base' | 'knowledge_content'; targetId?: string }) {
  return apiPost<KnowledgeBaseSubscription>(`/knowledge-bases/${spaceId}/subscriptions/remove`, request)
}
