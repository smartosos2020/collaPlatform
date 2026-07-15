import { apiDelete, apiGet, apiGetText, apiPatch, apiPost } from '../../../../shared/api/httpClient'
import type { PlatformObjectSummary } from '../../../platform/api/platformObjectsApi'
import type { IssueDetail } from '../../../projects/api/projectsApi'
import type { JSONContent } from '@tiptap/react'

export type KnowledgeBaseItem = {
  id: string
  parentId?: string | null
  title: string
  contentType: 'markdown' | 'folder' | 'space' | 'object_ref' | 'external_link'
  itemKind: string
  currentVersionNo: number
  permissionLevel: 'view' | 'comment' | 'edit' | 'manage' | 'owner'
  createdBy: string
  createdByName: string
  createdAt: string
  updatedBy: string
  updatedByName: string
  updatedAt: string
  sortOrder: number
  description?: string | null
  coverUrl?: string | null
  defaultPermissionLevel: 'view' | 'comment' | 'edit'
  archived: boolean
  maintainerId?: string | null
  maintainerName?: string | null
  tags?: string[]
  category?: string | null
  knowledgeStatus: 'draft' | 'verified' | 'needs_review' | 'outdated'
  reviewDueAt?: string | null
  verifiedAt?: string | null
  targetObjectType?: string | null
  targetObjectId?: string | null
  targetRoute?: string | null
  displayMode?: string | null
  targetTitleStrategy?: string | null
  entryAlias?: string | null
  targetSummary?: PlatformObjectSummary | null
  collaborationPermission?: { level: string; displayText: string; canEdit: boolean }
  availableActions?: string[]
}

export type KnowledgeBaseItemTreeNode = {
  item: KnowledgeBaseItem
  path: string
  depth: number
  childCount: number
  hasChildren: boolean
  children: KnowledgeBaseItemTreeNode[]
}

export type KnowledgeContentPathItem = { id: string; title: string; contentType: KnowledgeBaseItem['contentType']; permissionLevel: KnowledgeBaseItem['permissionLevel'] }
export type KnowledgeContentRelation = { id: string; itemId: string; targetType: string; targetId: string; title: string; webPath?: string | null; createdAt: string }
export type KnowledgeContentPermission = {
  id: string; itemId: string; subjectType: string; subjectId?: string | null; userId?: string | null; username?: string | null
  displayName?: string | null; subjectName?: string | null; subjectDetail?: string | null; permissionLevel: KnowledgeBaseItem['permissionLevel']
  sourceType: 'direct' | 'inherited'; sourceItemId?: string | null; sourceTitle?: string | null; createdAt: string
}
export type KnowledgeContentShareLink = {
  id: string; itemId: string; token: string; scope: string; permissionLevel: KnowledgeBaseItem['permissionLevel']; enabled: boolean
  expiresAt?: string | null; createdBy: string; createdByName: string; createdAt: string; updatedBy?: string | null; updatedByName?: string | null
  updatedAt?: string | null; knowledgeBaseId?: string | null; knowledgeBaseName?: string | null; knowledgeBaseCode?: string | null
}
export type KnowledgeContentPermissionRequest = { requestId: string; itemId: string; requestedPermissionLevel: string; notifiedCount: number; status: string }
export type KnowledgeContentComment = {
  id: string; threadId: string; parentCommentId?: string | null; itemId: string; blockId?: string | null; authorId: string; authorName: string
  content: string; anchorType: 'document' | 'block' | 'selection'; anchorStart?: number | null; anchorEnd?: number | null; anchorText?: string | null
  anchorPrefix?: string | null; anchorSuffix?: string | null; anchorVersionNo?: number | null; root: boolean; resolved: boolean
  resolvedAt?: string | null; resolvedBy?: string | null; resolvedByName?: string | null; reopenedAt?: string | null; reopenedBy?: string | null
  reopenedByName?: string | null; createdAt: string; replies: KnowledgeContentComment[]
}
export type KnowledgeContentContext = {
  spaceId: string; spaceName: string; spaceCode: string; rootItemId: string; homeItemId: string
  path: KnowledgeContentPathItem[]; pathText: string; webPath: string
}

export type KnowledgeContentBlock = {
  id: string; itemId: string; parentId?: string | null
  blockType: 'paragraph' | 'heading' | 'list' | 'bullet_list' | 'ordered_list' | 'task' | 'task_item' | 'quote' | 'code' | 'code_block' | 'table' | 'divider' | 'image' | 'legacy_html' | 'embed' | 'embed_object' | 'base_view' | 'issue_embed' | 'message_embed' | 'file_embed' | 'link_card'
  content: string; sortOrder: number; schemaVersion: number; attrs?: Record<string, unknown> | null; richContent?: Record<string, unknown> | null
  plainText?: string | null; anchorId?: string | null; blockVersion: number; createdBy: string; createdAt: string; updatedBy: string; updatedAt: string
  embedSummary?: PlatformObjectSummary | null; metadata?: Record<string, unknown> | null
}
export type KnowledgeContentBlockDraft = {
  id?: string; parentId?: string | null; blockType: KnowledgeContentBlock['blockType']; content: string; sortOrder?: number
  schemaVersion?: number; attrs?: Record<string, unknown> | null; richContent?: Record<string, unknown> | null; plainText?: string | null
  anchorId?: string | null; deleted?: boolean
}
export type KnowledgeContentDetail = {
  item: KnowledgeBaseItem; content: string; blocks: KnowledgeContentBlock[]; relations: KnowledgeContentRelation[]
  permissions: KnowledgeContentPermission[]; shareLinks: KnowledgeContentShareLink[]; comments: KnowledgeContentComment[]; context: KnowledgeContentContext
}
export type KnowledgeContentVersion = {
  id: string; itemId: string; versionNo: number; versionName?: string | null; versionType: string; title: string; content: string
  summary?: string | null; sourceVersionNo?: number | null; blockSnapshot?: string | null; createdBy: string; createdByName: string; createdAt: string
}
export type KnowledgeContentDiffLine = { type: string; oldLineNo: number; newLineNo: number; content: string; scope?: string | null; blockIndex?: number | null; blockType?: KnowledgeContentBlock['blockType'] | null; blockId?: string | null }
export type KnowledgeContentVersionDiff = { itemId: string; fromVersionNo: number; toVersionNo: number; lines: KnowledgeContentDiffLine[] }
export type KnowledgeContentTemplate = { id: string; title: string; description?: string | null; category: string; content: string; builtIn: boolean; scopeType: string; knowledgeBaseId?: string | null; knowledgeBaseName?: string | null; createdAt: string }
export type KnowledgeContentPerformance = { itemId: string; blockCount: number; embedCount: number; commentCount: number; contentLength: number; lineCount: number; largeContent: boolean; recommendedMode: string }
export type KnowledgeContentMigrationPreview = { itemId: string; currentVersionNo: number; contentBlockCount: number; storedBlockCount: number; contentLength: number; blockProjectionCurrent: boolean; rollbackAvailable: boolean; migrationMode: string }
export type KnowledgeContentSchemaIssue = { code: string; path: string; message: string; severity: 'info' | 'warning' | 'error' | string }
export type KnowledgeContentCanonicalMigrationPreview = {
  itemId: string
  sourceSchemaVersion: number
  targetSchemaVersion: number
  sourceChecksum: string
  targetChecksum: string
  sourceBlockCount: number
  targetBlockCount: number
  changed: boolean
  safeToApply: boolean
  migrationMode: string
  issues: KnowledgeContentSchemaIssue[]
  canonicalDocument: JSONContent
}
export type KnowledgeContentCollaborationHealth = { itemId: string; serverClock: number; activeUsers: number; dirty: boolean; stateVector: string; lastSavedAt?: string | null; updatedAt: string }

const contentPath = (spaceId: string, itemId: string) => `/knowledge-bases/${spaceId}/items/${itemId}`

export const getKnowledgeContent = (spaceId: string, itemId: string) => apiGet<KnowledgeContentDetail>(contentPath(spaceId, itemId))
export const getKnowledgeContentPerformance = (spaceId: string, itemId: string) => apiGet<KnowledgeContentPerformance>(`${contentPath(spaceId, itemId)}/performance`)
export const getKnowledgeContentMigrationPreview = (spaceId: string, itemId: string) => apiGet<KnowledgeContentMigrationPreview>(`${contentPath(spaceId, itemId)}/migration-preview`)
export const getKnowledgeContentCanonicalMigrationPreview = (spaceId: string, itemId: string) => apiGet<KnowledgeContentCanonicalMigrationPreview>(`${contentPath(spaceId, itemId)}/migration/preview`)
export const getKnowledgeContentCollaborationHealth = (spaceId: string, itemId: string) => apiGet<KnowledgeContentCollaborationHealth>(`${contentPath(spaceId, itemId)}/collaboration/health`)

export type KnowledgeCollaborationTicket = {
  url: string
  documentName: string
  ticket: string
  clientId: string
  protocolVersion: 'colla-yjs-v1'
  schemaVersion: number
  permissionLevel: string
  canView: boolean
  canEdit: boolean
  expiresAt: string
}

export const createKnowledgeCollaborationTicket = (spaceId: string, itemId: string) =>
  apiPost<KnowledgeCollaborationTicket>(`${contentPath(spaceId, itemId)}/collaboration/ticket`)
export const getKnowledgeContentPath = (spaceId: string, itemId: string) => apiGet<KnowledgeContentPathItem[]>(`${contentPath(spaceId, itemId)}/path`)
export const updateKnowledgeContentMetadata = (spaceId: string, itemId: string, request: { maintainerId?: string | null; tags?: string[]; category?: string | null; knowledgeStatus?: KnowledgeBaseItem['knowledgeStatus']; reviewDueAt?: string | null; verifiedAt?: string }) => apiPatch<KnowledgeContentDetail>(`${contentPath(spaceId, itemId)}/metadata`, request)
export const listKnowledgeContentBlocks = (spaceId: string, itemId: string) => apiGet<KnowledgeContentBlock[]>(`${contentPath(spaceId, itemId)}/blocks`)
export const saveKnowledgeContentBlocks = (spaceId: string, itemId: string, request: { baseVersionNo: number; title?: string; saveMode?: 'auto' | 'manual'; blocks: KnowledgeContentBlockDraft[] }) => apiPatch<KnowledgeContentDetail>(`${contentPath(spaceId, itemId)}/blocks`, request)
export const insertKnowledgeContentBlock = (spaceId: string, itemId: string, request: { baseVersionNo: number; block: KnowledgeContentBlockDraft; afterSortOrder?: number | null }) => apiPost<KnowledgeContentDetail>(`${contentPath(spaceId, itemId)}/blocks`, request)
export const updateKnowledgeContentBlock = (spaceId: string, itemId: string, blockId: string, request: { baseVersionNo: number; block: Partial<KnowledgeContentBlockDraft> }) => apiPatch<KnowledgeContentDetail>(`${contentPath(spaceId, itemId)}/blocks/${blockId}`, request)
export const reorderKnowledgeContentBlocks = (spaceId: string, itemId: string, request: { baseVersionNo: number; blockIds: string[] }) => apiPost<KnowledgeContentDetail>(`${contentPath(spaceId, itemId)}/blocks/reorder`, request)
export function deleteKnowledgeContentBlock(spaceId: string, itemId: string, blockId: string, baseVersionNo: number) { const params = new URLSearchParams({ baseVersionNo: String(baseVersionNo) }); return apiDelete<KnowledgeContentDetail>(`${contentPath(spaceId, itemId)}/blocks/${blockId}?${params}`) }
export const listKnowledgeContentVersions = (spaceId: string, itemId: string) => apiGet<KnowledgeContentVersion[]>(`${contentPath(spaceId, itemId)}/versions`)
export const createKnowledgeContentCheckpoint = (spaceId: string, itemId: string) => apiPost<KnowledgeContentDetail>(`${contentPath(spaceId, itemId)}/versions/checkpoint`)
export const createNamedKnowledgeContentVersion = (spaceId: string, itemId: string, request: { versionName: string; summary?: string }) => apiPost<KnowledgeContentDetail>(`${contentPath(spaceId, itemId)}/versions/named`, request)
export const importKnowledgeContentMarkdown = (spaceId: string, itemId: string, request: { title?: string; content: string }) => apiPost<KnowledgeContentDetail>(`${contentPath(spaceId, itemId)}/import/markdown`, request)
export const exportKnowledgeContentMarkdown = (spaceId: string, itemId: string) => apiGetText(`${contentPath(spaceId, itemId)}/export/markdown`)
export const exportKnowledgeContentHtml = (spaceId: string, itemId: string) => apiGetText(`${contentPath(spaceId, itemId)}/export/html`)
export function diffKnowledgeContentVersions(spaceId: string, itemId: string, fromVersionNo: number, toVersionNo: number) { const params = new URLSearchParams({ fromVersionNo: String(fromVersionNo), toVersionNo: String(toVersionNo) }); return apiGet<KnowledgeContentVersionDiff>(`${contentPath(spaceId, itemId)}/versions/diff?${params}`) }
export const restoreKnowledgeContentVersion = (spaceId: string, itemId: string, versionNo: number) => apiPost<KnowledgeContentDetail>(`${contentPath(spaceId, itemId)}/versions/${versionNo}/restore`)
export const grantKnowledgeContentPermission = (spaceId: string, itemId: string, request: { userId?: string; subjectType?: string; subjectId?: string; permissionLevel: string }) => apiPost<KnowledgeContentDetail>(`${contentPath(spaceId, itemId)}/permissions`, request)
export const updateKnowledgeContentShareLink = (spaceId: string, itemId: string, request: { scope?: string; permissionLevel: string; enabled?: boolean; expiresAt?: string | null }) => apiPost<KnowledgeContentShareLink>(`${contentPath(spaceId, itemId)}/share-link`, request)
export const setKnowledgeContentShareLinkEnabled = (spaceId: string, itemId: string, enabled: boolean) => apiPost<KnowledgeContentShareLink>(`${contentPath(spaceId, itemId)}/share-link/${enabled ? 'enable' : 'disable'}`)
export const requestKnowledgeContentPermission = (spaceId: string, itemId: string, request: { permissionLevel: string; reason?: string }) => apiPost<KnowledgeContentPermissionRequest>(`${contentPath(spaceId, itemId)}/permission-requests`, request)
export const addKnowledgeContentRelation = (spaceId: string, itemId: string, request: { targetType: string; targetId: string }) => apiPost<KnowledgeContentDetail>(`${contentPath(spaceId, itemId)}/relations`, request)
export const createIssueFromKnowledgeSelection = (spaceId: string, itemId: string, request: { projectId: string; issueType?: string; title?: string; description?: string; priority?: string; assigneeId?: string; dueAt?: string; anchorStart?: number; anchorEnd?: number; anchorText?: string }) => apiPost<IssueDetail>(`${contentPath(spaceId, itemId)}/issues/from-selection`, request)
export type AddKnowledgeContentCommentRequest = { blockId?: string | null; anchorType?: KnowledgeContentComment['anchorType']; anchorStart?: number | null; anchorEnd?: number | null; anchorText?: string | null; anchorPrefix?: string | null; anchorSuffix?: string | null; content: string }
export const addKnowledgeContentComment = (spaceId: string, itemId: string, request: AddKnowledgeContentCommentRequest) => apiPost<KnowledgeContentDetail>(`${contentPath(spaceId, itemId)}/comments`, request)
export const addKnowledgeContentCommentReply = (spaceId: string, itemId: string, commentId: string, request: { content: string }) => apiPost<KnowledgeContentDetail>(`${contentPath(spaceId, itemId)}/comments/${commentId}/replies`, request)
export const resolveKnowledgeContentComment = (spaceId: string, itemId: string, commentId: string) => apiPost<KnowledgeContentDetail>(`${contentPath(spaceId, itemId)}/comments/${commentId}/resolve`)
export const reopenKnowledgeContentComment = (spaceId: string, itemId: string, commentId: string) => apiPost<KnowledgeContentDetail>(`${contentPath(spaceId, itemId)}/comments/${commentId}/reopen`)
