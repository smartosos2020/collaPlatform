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
}

export type SearchResponse = {
  query: string
  items: SearchResult[]
}

export function searchAll(query: string, limit = 20) {
  const params = new URLSearchParams({ q: query, limit: String(limit) })
  return apiGet<SearchResponse>(`/search?${params}`)
}
