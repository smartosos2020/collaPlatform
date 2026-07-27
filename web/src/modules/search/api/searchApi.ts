import { apiGet } from '../../../shared/api/httpClient'

export type SearchResult = {
  objectType: 'issue' | 'knowledge_content' | 'base' | 'base_table' | 'base_record' | 'message' | 'work_item'
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
  contentType?: 'space' | 'folder' | 'markdown' | null
  hitSource?: 'title' | 'body_block' | 'comment' | 'tags' | 'directory_path' | string | null
}
export type SearchFacet = {
  field: 'objectType' | string
  value: string
  count: number
}
export type SearchResponse = {
  query: string
  searchScope: 'user_content'
  items: SearchResult[]
  facets: SearchFacet[]
  nextCursor?: string | null
}

export type SearchFilters = {
  knowledgeBaseId?: string
  directoryId?: string
  contentType?: string
  tags?: string[]
  maintainerId?: string
  knowledgeStatus?: string
  updatedFrom?: string
  updatedTo?: string
  spaceIds?: string[]
  objectTypes?: string[]
  objectStatuses?: string[]
  participantRoles?: string[]
  cursor?: string
}

export type SearchSpaceChoice = {
  objectId: string
  title?: string | null
}

export function listSearchSpaceChoices() {
  const params = new URLSearchParams({ types: 'project_space', source: 'all', limit: '50', offset: '0' })
  return apiGet<{ items: SearchSpaceChoice[] }>(`/platform/object-choices?${params}`)
}

export function searchAll(query: string, limit = 20, filters: SearchFilters = {}) {
  const params = new URLSearchParams({ q: query, limit: String(limit) })
  if (filters.knowledgeBaseId) params.set('knowledgeBaseId', filters.knowledgeBaseId)
  if (filters.directoryId) params.set('directoryId', filters.directoryId)
  if (filters.contentType) params.set('contentType', filters.contentType)
  if (filters.maintainerId) params.set('maintainerId', filters.maintainerId)
  if (filters.knowledgeStatus) params.set('knowledgeStatus', filters.knowledgeStatus)
  if (filters.updatedFrom) params.set('updatedFrom', filters.updatedFrom)
  if (filters.updatedTo) params.set('updatedTo', filters.updatedTo)
  filters.tags?.forEach((tag) => params.append('tags', tag))
  filters.spaceIds?.forEach((spaceId) => params.append('spaceIds', spaceId))
  filters.objectTypes?.forEach((objectType) => params.append('objectTypes', objectType))
  filters.objectStatuses?.forEach((status) => params.append('objectStatuses', status))
  filters.participantRoles?.forEach((role) => params.append('participantRoles', role))
  if (filters.cursor) params.set('cursor', filters.cursor)
  return apiGet<SearchResponse>(`/search?${params}`)
}
