import { apiGet } from '../../../shared/api/httpClient'

export type SearchResult = {
  objectType: 'issue' | 'document' | 'base' | 'base_table' | 'base_record' | 'message'
  objectId: string
  title?: string | null
  excerpt?: string | null
  webPath?: string | null
  deepLink?: string | null
  score: number
  updatedAt: string
  accessState: 'available' | 'forbidden' | 'deleted' | 'not_found' | 'invalid'
  permissionExplanation?: string | null
  knowledgeBaseId?: string | null
  knowledgeBaseName?: string | null
  parentDocumentId?: string | null
  directoryPath?: string | null
  tags?: string[]
  maintainerId?: string | null
  maintainerName?: string | null
  knowledgeStatus?: 'draft' | 'verified' | 'needs_review' | 'outdated' | 'archived' | null
  docType?: 'space' | 'folder' | 'markdown' | null
  hitSource?: 'title' | 'body_block' | 'comment' | 'tags' | 'directory_path' | string | null
}

export type SearchResponse = {
  query: string
  items: SearchResult[]
}

export type SearchFilters = {
  knowledgeBaseId?: string
  directoryId?: string
  docType?: string
  tags?: string[]
  maintainerId?: string
  knowledgeStatus?: string
  updatedFrom?: string
  updatedTo?: string
}

export function searchAll(query: string, limit = 20, filters: SearchFilters = {}) {
  const params = new URLSearchParams({ q: query, limit: String(limit) })
  if (filters.knowledgeBaseId) params.set('knowledgeBaseId', filters.knowledgeBaseId)
  if (filters.directoryId) params.set('directoryId', filters.directoryId)
  if (filters.docType) params.set('docType', filters.docType)
  if (filters.maintainerId) params.set('maintainerId', filters.maintainerId)
  if (filters.knowledgeStatus) params.set('knowledgeStatus', filters.knowledgeStatus)
  if (filters.updatedFrom) params.set('updatedFrom', filters.updatedFrom)
  if (filters.updatedTo) params.set('updatedTo', filters.updatedTo)
  filters.tags?.forEach((tag) => params.append('tags', tag))
  return apiGet<SearchResponse>(`/search?${params}`)
}
