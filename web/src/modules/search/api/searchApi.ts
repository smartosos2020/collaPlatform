import { apiGet } from '../../../shared/api/httpClient'

export type SearchResult = {
  objectType: 'issue' | 'document' | 'base_record' | 'message'
  objectId: string
  title: string
  excerpt?: string | null
  webPath: string
  deepLink: string
  score: number
  updatedAt: string
  accessState: 'available' | 'forbidden' | 'deleted' | 'not_found' | 'invalid'
}

export type SearchResponse = {
  query: string
  items: SearchResult[]
}

export function searchAll(query: string, limit = 20) {
  const params = new URLSearchParams({ q: query, limit: String(limit) })
  return apiGet<SearchResponse>(`/search?${params}`)
}
