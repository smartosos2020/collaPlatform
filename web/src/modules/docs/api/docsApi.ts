import { apiGet, apiPatch, apiPost } from '../../../shared/api/httpClient'
import type { PlatformObjectSummary } from '../../platform/api/platformObjectsApi'

export type DocumentSummary = {
  id: string
  parentId?: string | null
  title: string
  docType: 'markdown' | 'folder' | 'space'
  currentVersionNo: number
  permissionLevel: 'view' | 'edit' | 'manage'
  createdBy: string
  createdByName: string
  createdAt: string
  updatedBy: string
  updatedByName: string
  updatedAt: string
  sortOrder: number
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
  userId: string
  username: string
  displayName: string
  permissionLevel: 'view' | 'edit' | 'manage'
  createdAt: string
}

export type DocumentComment = {
  id: string
  documentId: string
  blockId?: string | null
  authorId: string
  authorName: string
  content: string
  resolved: boolean
  resolvedAt?: string | null
  resolvedBy?: string | null
  resolvedByName?: string | null
  createdAt: string
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
  comments: DocumentComment[]
}

export type DocumentVersion = {
  id: string
  documentId: string
  versionNo: number
  title: string
  content: string
  createdBy: string
  createdByName: string
  createdAt: string
}

export type DocumentDiffLine = {
  type: 'context' | 'added' | 'removed'
  oldLineNo: number
  newLineNo: number
  content: string
}

export type DocumentVersionDiff = {
  documentId: string
  fromVersionNo: number
  toVersionNo: number
  lines: DocumentDiffLine[]
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

export function createDocument(request: {
  parentId?: string | null
  title: string
  docType?: DocumentSummary['docType']
  content?: string
}) {
  return apiPost<DocumentDetail>('/docs', request)
}

export function getDocument(documentId: string) {
  return apiGet<DocumentDetail>(`/docs/${documentId}`)
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

export function diffDocumentVersions(documentId: string, fromVersionNo: number, toVersionNo: number) {
  const params = new URLSearchParams({ fromVersionNo: String(fromVersionNo), toVersionNo: String(toVersionNo) })
  return apiGet<DocumentVersionDiff>(`/docs/${documentId}/versions/diff?${params}`)
}

export function restoreDocumentVersion(documentId: string, versionNo: number) {
  return apiPost<DocumentDetail>(`/docs/${documentId}/versions/${versionNo}/restore`)
}

export function grantDocumentPermission(documentId: string, request: { userId: string; permissionLevel: string }) {
  return apiPost<DocumentDetail>(`/docs/${documentId}/permissions`, request)
}

export function addDocumentRelation(documentId: string, request: { targetType: string; targetId: string }) {
  return apiPost<DocumentDetail>(`/docs/${documentId}/relations`, request)
}

export function addDocumentComment(documentId: string, request: { content: string; blockId?: string }) {
  return apiPost<DocumentDetail>(`/docs/${documentId}/comments`, request)
}

export function resolveDocumentComment(documentId: string, commentId: string) {
  return apiPost<DocumentDetail>(`/docs/${documentId}/comments/${commentId}/resolve`)
}
