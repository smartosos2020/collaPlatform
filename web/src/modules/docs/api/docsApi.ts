import { apiGet, apiGetText, apiPatch, apiPost } from '../../../shared/api/httpClient'
import type { PlatformObjectSummary } from '../../platform/api/platformObjectsApi'
import type { IssueDetail } from '../../projects/api/projectsApi'

export type DocumentSummary = {
  id: string
  parentId?: string | null
  title: string
  docType: 'markdown' | 'folder' | 'space'
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
  knowledgeBase: boolean
  archived: boolean
}

export type DocumentTreeNode = {
  document: DocumentSummary
  path: string
  depth: number
  childCount: number
  hasChildren: boolean
  children: DocumentTreeNode[]
}

export type DocumentPathItem = {
  id: string
  title: string
  docType: DocumentSummary['docType']
  permissionLevel: DocumentSummary['permissionLevel']
}

export type DocumentRelation = {
  id: string
  documentId: string
  targetType: 'issue' | 'base' | 'base_table' | 'base_record' | 'file'
  targetId: string
  title: string
  webPath?: string | null
  createdAt: string
}

export type DocumentPermission = {
  id: string
  documentId: string
  subjectType: 'user' | 'user_group'
  subjectId: string
  userId?: string | null
  username?: string | null
  displayName?: string | null
  subjectName: string
  subjectDetail?: string | null
  permissionLevel: DocumentSummary['permissionLevel']
  sourceType: 'direct' | 'inherited'
  sourceDocumentId?: string | null
  sourceTitle?: string | null
  createdAt: string
}

export type DocumentShareLink = {
  id: string
  documentId: string
  token: string
  scope: 'workspace'
  permissionLevel: 'view' | 'comment' | 'edit'
  enabled: boolean
  expiresAt?: string | null
  createdBy: string
  createdByName: string
  createdAt: string
  updatedBy?: string | null
  updatedByName?: string | null
  updatedAt?: string | null
}

export type DocumentPermissionRequest = {
  requestId: string
  documentId: string
  requestedPermissionLevel: 'view' | 'comment' | 'edit'
  notifiedCount: number
  status: 'submitted'
}

export type DocumentComment = {
  id: string
  threadId: string
  parentCommentId?: string | null
  documentId: string
  blockId?: string | null
  authorId: string
  authorName: string
  content: string
  anchorType: 'document' | 'block' | 'selection'
  anchorStart?: number | null
  anchorEnd?: number | null
  anchorText?: string | null
  anchorPrefix?: string | null
  anchorSuffix?: string | null
  anchorVersionNo?: number | null
  root: boolean
  resolved: boolean
  resolvedAt?: string | null
  resolvedBy?: string | null
  resolvedByName?: string | null
  reopenedAt?: string | null
  reopenedBy?: string | null
  reopenedByName?: string | null
  createdAt: string
  replies: DocumentComment[]
}

export type DocumentBlock = {
  id: string
  documentId: string
  blockType:
    | 'paragraph'
    | 'heading'
    | 'list'
    | 'task'
    | 'quote'
    | 'code'
    | 'table'
    | 'embed'
    | 'base_view'
    | 'issue_embed'
    | 'message_embed'
    | 'file_embed'
    | 'link'
  content: string
  sortOrder: number
  createdAt: string
  updatedAt: string
  embedSummary?: PlatformObjectSummary | null
  metadata?: Record<string, unknown>
}

export type DocumentBlockDraft = {
  blockType: DocumentBlock['blockType']
  content: string
  sortOrder?: number
}

export type DocumentDetail = {
  document: DocumentSummary
  content: string
  blocks: DocumentBlock[]
  relations: DocumentRelation[]
  permissions: DocumentPermission[]
  shareLinks: DocumentShareLink[]
  comments: DocumentComment[]
}

export type DocumentVersion = {
  id: string
  documentId: string
  versionNo: number
  versionName?: string | null
  versionType: 'auto_snapshot' | 'manual_checkpoint' | 'named' | 'restore' | 'import'
  title: string
  content: string
  summary?: string | null
  sourceVersionNo?: number | null
  blockSnapshot?: string | null
  createdBy: string
  createdByName: string
  createdAt: string
}

export type DocumentDiffLine = {
  type: 'context' | 'added' | 'removed'
  oldLineNo: number
  newLineNo: number
  content: string
  scope?: 'block'
  blockIndex?: number | null
  blockType?: DocumentBlock['blockType'] | null
}

export type DocumentVersionDiff = {
  documentId: string
  fromVersionNo: number
  toVersionNo: number
  lines: DocumentDiffLine[]
}

export type DocumentTemplate = {
  id: string
  title: string
  description?: string | null
  category: string
  content: string
  builtIn: boolean
  createdAt: string
}

export type DocumentPerformanceProfile = {
  documentId: string
  blockCount: number
  embedCount: number
  commentCount: number
  contentLength: number
  lineCount: number
  largeDocument: boolean
  recommendedMode: 'lazy-preview' | 'full-editor'
}

export type DocumentMigrationPreview = {
  documentId: string
  currentVersionNo: number
  contentBlockCount: number
  storedBlockCount: number
  contentLength: number
  blockProjectionCurrent: boolean
  rollbackAvailable: boolean
  migrationMode: 'content-to-blocks' | 'verify-existing-blocks'
}

export type DocumentCollaborationHealth = {
  documentId: string
  serverClock: number
  activeUsers: number
  dirty: boolean
  stateVector: string
  lastSavedAt?: string | null
  updatedAt?: string | null
}

export type DocumentAcceptanceScenario = {
  key: string
  title: string
  workflow: string
  status: 'ready' | 'trial-ready' | 'blocked'
  evidence: string
}

export type DocumentAcceptanceGate = {
  key: string
  label: string
  status: 'ready' | 'trial-ready' | 'frozen' | 'blocked'
  evidence: string
}

export type DocumentAcceptanceReport = {
  version: string
  status: 'frozen' | 'trial-ready' | 'blocked'
  scenarios: DocumentAcceptanceScenario[]
  gates: DocumentAcceptanceGate[]
  openP0: number
  openP1: number
  frozen: boolean
  frozenCriteria: string
}

export function listDocuments(options: { includeArchived?: boolean } = {}) {
  const params = new URLSearchParams()
  if (options.includeArchived) {
    params.set('includeArchived', 'true')
  }
  return apiGet<DocumentSummary[]>(`/docs${params.size ? `?${params}` : ''}`)
}

export function listDocumentTree(options: { includeArchived?: boolean } = {}) {
  const params = new URLSearchParams()
  if (options.includeArchived) {
    params.set('includeArchived', 'true')
  }
  return apiGet<DocumentTreeNode[]>(`/docs/tree${params.size ? `?${params}` : ''}`)
}

export function listDocumentTemplates() {
  return apiGet<DocumentTemplate[]>('/docs/templates')
}

export function getDocumentAcceptanceReport() {
  return apiGet<DocumentAcceptanceReport>('/docs/acceptance/v1')
}

export function createDocument(request: {
  parentId?: string | null
  title: string
  docType?: DocumentSummary['docType']
  content?: string
  description?: string
  coverUrl?: string
  defaultPermissionLevel?: DocumentSummary['defaultPermissionLevel']
  knowledgeBase?: boolean
}) {
  return apiPost<DocumentDetail>('/docs', request)
}

export function createDocumentFromTemplate(request: { templateId: string; parentId?: string | null; title?: string }) {
  return apiPost<DocumentDetail>('/docs/from-template', request)
}

export function getDocument(documentId: string) {
  return apiGet<DocumentDetail>(`/docs/${documentId}`)
}

export function getDocumentPerformanceProfile(documentId: string) {
  return apiGet<DocumentPerformanceProfile>(`/docs/${documentId}/performance`)
}

export function getDocumentMigrationPreview(documentId: string) {
  return apiGet<DocumentMigrationPreview>(`/docs/${documentId}/migration-preview`)
}

export function getDocumentCollaborationHealth(documentId: string) {
  return apiGet<DocumentCollaborationHealth>(`/docs/${documentId}/collaboration/health`)
}

export function getDocumentPath(documentId: string) {
  return apiGet<DocumentPathItem[]>(`/docs/${documentId}/path`)
}

export function saveDocument(documentId: string, request: { baseVersionNo: number; title: string; content: string }) {
  return apiPatch<DocumentDetail>(`/docs/${documentId}`, request)
}

export function listDocumentBlocks(documentId: string) {
  return apiGet<DocumentBlock[]>(`/docs/${documentId}/blocks`)
}

export function saveDocumentBlocks(
  documentId: string,
  request: { baseVersionNo: number; blocks: DocumentBlockDraft[] },
) {
  return apiPatch<DocumentDetail>(`/docs/${documentId}/blocks`, request)
}

export function moveDocument(documentId: string, request: { parentId?: string | null; sortOrder?: number }) {
  return apiPost<DocumentDetail>(`/docs/${documentId}/move`, request)
}

export function archiveDocument(documentId: string) {
  return apiPost<DocumentDetail>(`/docs/${documentId}/archive`)
}

export function restoreDocument(documentId: string) {
  return apiPost<DocumentDetail>(`/docs/${documentId}/restore`)
}

export function listDocumentVersions(documentId: string) {
  return apiGet<DocumentVersion[]>(`/docs/${documentId}/versions`)
}

export function createDocumentCheckpoint(documentId: string) {
  return apiPost<DocumentDetail>(`/docs/${documentId}/versions/checkpoint`)
}

export function createNamedDocumentVersion(documentId: string, request: { versionName: string; summary?: string }) {
  return apiPost<DocumentDetail>(`/docs/${documentId}/versions/named`, request)
}

export function importDocumentMarkdown(documentId: string, request: { title?: string; content: string }) {
  return apiPost<DocumentDetail>(`/docs/${documentId}/import/markdown`, request)
}

export function exportDocumentMarkdown(documentId: string) {
  return apiGetText(`/docs/${documentId}/export/markdown`)
}

export function exportDocumentHtml(documentId: string) {
  return apiGetText(`/docs/${documentId}/export/html`)
}

export function diffDocumentVersions(documentId: string, fromVersionNo: number, toVersionNo: number) {
  const params = new URLSearchParams({ fromVersionNo: String(fromVersionNo), toVersionNo: String(toVersionNo) })
  return apiGet<DocumentVersionDiff>(`/docs/${documentId}/versions/diff?${params}`)
}

export function restoreDocumentVersion(documentId: string, versionNo: number) {
  return apiPost<DocumentDetail>(`/docs/${documentId}/versions/${versionNo}/restore`)
}

export function grantDocumentPermission(
  documentId: string,
  request: { userId?: string; subjectType?: 'user' | 'user_group'; subjectId?: string; permissionLevel: string },
) {
  return apiPost<DocumentDetail>(`/docs/${documentId}/permissions`, request)
}

export function updateDocumentShareLink(
  documentId: string,
  request: { scope?: 'workspace'; permissionLevel: 'view' | 'comment' | 'edit'; enabled?: boolean; expiresAt?: string | null },
) {
  return apiPost<DocumentShareLink>(`/docs/${documentId}/share-link`, request)
}

export function setDocumentShareLinkEnabled(documentId: string, enabled: boolean) {
  return apiPost<DocumentShareLink>(`/docs/${documentId}/share-link/${enabled ? 'enable' : 'disable'}`)
}

export function updateDocumentKnowledgeBase(
  documentId: string,
  request: {
    description?: string
    coverUrl?: string
    defaultPermissionLevel?: DocumentSummary['defaultPermissionLevel']
    knowledgeBase?: boolean
  },
) {
  return apiPost<DocumentDetail>(`/docs/${documentId}/knowledge-base`, request)
}

export function requestDocumentPermission(
  documentId: string,
  request: { permissionLevel: 'view' | 'comment' | 'edit'; reason?: string },
) {
  return apiPost<DocumentPermissionRequest>(`/docs/${documentId}/permission-requests`, request)
}

export function addDocumentRelation(documentId: string, request: { targetType: string; targetId: string }) {
  return apiPost<DocumentDetail>(`/docs/${documentId}/relations`, request)
}

export function createIssueFromDocumentSelection(
  documentId: string,
  request: {
    projectId: string
    issueType?: 'requirement' | 'task' | 'bug'
    title?: string
    description?: string
    priority?: string
    assigneeId?: string
    dueAt?: string
    anchorStart?: number
    anchorEnd?: number
    anchorText?: string
  },
) {
  return apiPost<IssueDetail>(`/docs/${documentId}/issues/from-selection`, request)
}

export type AddDocumentCommentRequest = {
  content: string
  blockId?: string
  anchorType?: DocumentComment['anchorType']
  anchorStart?: number
  anchorEnd?: number
  anchorText?: string
  anchorPrefix?: string
  anchorSuffix?: string
}

export function addDocumentComment(documentId: string, request: AddDocumentCommentRequest) {
  return apiPost<DocumentDetail>(`/docs/${documentId}/comments`, request)
}

export function addDocumentCommentReply(documentId: string, commentId: string, request: { content: string }) {
  return apiPost<DocumentDetail>(`/docs/${documentId}/comments/${commentId}/replies`, request)
}

export function resolveDocumentComment(documentId: string, commentId: string) {
  return apiPost<DocumentDetail>(`/docs/${documentId}/comments/${commentId}/resolve`)
}

export function reopenDocumentComment(documentId: string, commentId: string) {
  return apiPost<DocumentDetail>(`/docs/${documentId}/comments/${commentId}/reopen`)
}
