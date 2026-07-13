import { apiGet } from '../../../shared/api/httpClient'

export type AdminGovernanceSearchResult = {
  governanceType: string
  title: string
  description: string
  adminPath: string
  riskLevel: string
}

export type AdminGovernanceSearchResponse = {
  query: string
  searchScope: 'admin_governance'
  items: AdminGovernanceSearchResult[]
}

export function searchAdminGovernance(query: string, limit = 10) {
  const params = new URLSearchParams({ q: query, limit: String(limit) })
  return apiGet<AdminGovernanceSearchResponse>(`/admin/search-governance?${params}`)
}
