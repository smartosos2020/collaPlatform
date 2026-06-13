import { apiGet, apiPatch, apiPost } from '../../../shared/api/httpClient'

export type DocumentSummary = {
  id: string
  parentId?: string | null
  title: string
  docType: string
  currentVersionNo: number
  permissionLevel: 'view' | 'edit' | 'manage'
  createdBy: string
  createdByName: string
  createdAt: string
  updatedBy: string
  updatedByName: string
  updatedAt: string
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
  authorId: string
  authorName: string
  content: string
  createdAt: string
}

export type DocumentDetail = {
  document: DocumentSummary
  content: string
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

export function listDocuments() {
  return apiGet<DocumentSummary[]>('/docs')
}

export function createDocument(request: { parentId?: string | null; title: string; content?: string }) {
  return apiPost<DocumentDetail>('/docs', request)
}

export function getDocument(documentId: string) {
  return apiGet<DocumentDetail>(`/docs/${documentId}`)
}

export function saveDocument(documentId: string, request: { baseVersionNo: number; title: string; content: string }) {
  return apiPatch<DocumentDetail>(`/docs/${documentId}`, request)
}

export function moveDocument(documentId: string, parentId?: string | null) {
  return apiPost<DocumentDetail>(`/docs/${documentId}/move`, { parentId })
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

export function addDocumentComment(documentId: string, content: string) {
  return apiPost<DocumentDetail>(`/docs/${documentId}/comments`, { content })
}
