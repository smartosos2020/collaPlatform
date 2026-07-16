import { apiGet, apiPost } from '../../../shared/api/httpClient'

export type ObjectAccessState = 'available' | 'forbidden' | 'disabled' | 'deleted' | 'not_found' | 'invalid'

export type PlatformObjectSummary = {
  objectType: string
  objectId: string
  accessState: ObjectAccessState
  title?: string | null
  subtitle?: string | null
  status?: string | null
  webPath?: string | null
  deepLink?: string | null
  metadata: Record<string, unknown>
}

export type PlatformObjectNavigation = {
  summary: PlatformObjectSummary
  webPath?: string | null
  deepLink?: string | null
  mobileFallbackPath?: string | null
}

export type PlatformObjectChoicePage = {
  items: PlatformObjectSummary[]
  total: number
  limit: number
  offset: number
}

export function listPlatformObjectChoices(options: {
  types: string[]
  query?: string
  source?: 'all' | 'recent'
  limit?: number
  offset?: number
}) {
  const params = new URLSearchParams()
  options.types.forEach((type) => params.append('types', type))
  if (options.query) params.set('query', options.query)
  params.set('source', options.source ?? 'all')
  params.set('limit', String(options.limit ?? 8))
  params.set('offset', String(options.offset ?? 0))
  return apiGet<PlatformObjectChoicePage>(`/platform/object-choices?${params.toString()}`)
}

export type PermissionExplanation = {
  objectType: string
  objectId: string
  action: string
  actionCategory?: 'user_action' | 'object_management' | 'space_management' | 'admin_management' | 'super_admin'
  presentationContext?: 'user' | 'admin'
  allowed: boolean
  accessState: ObjectAccessState
  reason: string
  actionAdvice?: string
  policySourceDetail?: string
  currentLevel: string
  requiredLevel: string
  source: string
}

export type PlatformObjectAction = {
  key: string
  label: string
  href?: string | null
  tone: string
}

export type PlatformObjectCard = {
  summary: PlatformObjectSummary
  presentationContext: 'user' | 'admin'
  actions: PlatformObjectAction[]
  permissionHint: string
}

export type PlatformObjectTypeRule = {
  objectType: string
  displayName: string
  webPathPattern: string
  deepLinkPattern: string
}

export type ParsedInternalLink = {
  resolved: boolean
  source: string
  objectType?: string | null
  objectId?: string | null
  webPath?: string | null
  deepLink?: string | null
  summary?: PlatformObjectSummary | null
}

export function resolveInternalLink(link: string) {
  return apiPost<ParsedInternalLink>('/platform/links/resolve', { link })
}

export function listPlatformObjectTypes() {
  return apiGet<PlatformObjectTypeRule[]>('/platform/object-types')
}

export function getObjectNavigation(objectType: string, objectId: string) {
  return apiGet<PlatformObjectNavigation>(
    `/platform/objects/${encodeURIComponent(objectType)}/${encodeURIComponent(objectId)}/navigation`,
  )
}

export function getPermissionExplanation(objectType: string, objectId: string, action = 'view') {
  const params = new URLSearchParams({ action })
  return apiGet<PermissionExplanation>(
    `/platform/objects/${encodeURIComponent(objectType)}/${encodeURIComponent(objectId)}/permission-explanation?${params}`,
  )
}

export function getObjectCard(objectType: string, objectId: string, context: 'user' | 'admin' = 'user') {
  const params = new URLSearchParams({ context })
  return apiGet<PlatformObjectCard>(
    `/platform/objects/${encodeURIComponent(objectType)}/${encodeURIComponent(objectId)}/card?${params}`,
  )
}

export function markObjectAccessed(objectType: string, objectId: string) {
  return apiPost<PlatformObjectSummary>(
    `/platform/objects/${encodeURIComponent(objectType)}/${encodeURIComponent(objectId)}/access`,
  )
}

export function listRecentObjects(limit = 10) {
  return apiGet<PlatformObjectSummary[]>(`/platform/recent?limit=${limit}`)
}

export function listFavoriteObjects(limit = 20) {
  return apiGet<PlatformObjectSummary[]>(`/platform/favorites?limit=${limit}`)
}

export function addObjectFavorite(objectType: string, objectId: string) {
  return apiPost<PlatformObjectSummary>(
    `/platform/objects/${encodeURIComponent(objectType)}/${encodeURIComponent(objectId)}/favorite`,
  )
}

export function removeObjectFavorite(objectType: string, objectId: string) {
  return apiPost<void>(
    `/platform/objects/${encodeURIComponent(objectType)}/${encodeURIComponent(objectId)}/favorite/remove`,
  )
}
