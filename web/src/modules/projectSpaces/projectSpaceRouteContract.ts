export const PROJECT_SPACE_QUERY_KEYS = [
  'source',
  'panel',
  'metricPanel',
  'metricConfig',
  'automationPanel',
  'typeId',
  'workModelTab',
  'create',
  'savedViewId',
] as const

export const PROJECT_SPACE_WORK_MODEL_TABS = [
  'type-information',
  'field-configuration',
  'page-layout',
  'flow-access',
] as const

export type ProjectSpaceWorkModelTab = (typeof PROJECT_SPACE_WORK_MODEL_TABS)[number]

export type ProjectSpaceQueryKey = (typeof PROJECT_SPACE_QUERY_KEYS)[number]
export type ProjectSpaceQueryPatch = Partial<
  Record<ProjectSpaceQueryKey, string | null | undefined>
>

const PROJECT_SPACE_QUERY_KEY_SET = new Set<string>(PROJECT_SPACE_QUERY_KEYS)
const TOKEN_VALUE = /^[a-zA-Z0-9][a-zA-Z0-9_.:-]{0,127}$/
const SAFE_HASH = /^#[a-zA-Z0-9][a-zA-Z0-9_.:-]{0,127}$/
const SAFE_SEGMENT = '[a-zA-Z0-9][a-zA-Z0-9_-]{0,127}'
const CANONICAL_PROJECT_SPACE_PATH = new RegExp(
  `^/project-spaces/${SAFE_SEGMENT}`
    + `(?:`
    + `/work-items(?:/${SAFE_SEGMENT})?`
    + `|/management`
    + `|/members`
    + `|/settings`
    + `|/types(?:/${SAFE_SEGMENT}(?:/fields(?:/${SAFE_SEGMENT})?|/layouts|/sample)?)?`
    + `)?$`,
)

export function patchProjectSpaceSearch(
  current: URLSearchParams | string,
  patch: ProjectSpaceQueryPatch,
): URLSearchParams {
  const next = sanitizeProjectSpaceSearch(current)
  for (const key of PROJECT_SPACE_QUERY_KEYS) {
    if (!Object.hasOwn(patch, key)) continue
    const value = patch[key]
    if (value === null) {
      next.delete(key)
      continue
    }
    if (value === undefined) continue
    if (validProjectSpaceQueryValue(key, value)) {
      next.set(key, value)
    } else {
      next.delete(key)
    }
  }
  return next
}

export function sanitizeProjectSpaceSearch(
  search: URLSearchParams | string,
  preserveKeys: readonly ProjectSpaceQueryKey[] = PROJECT_SPACE_QUERY_KEYS,
): URLSearchParams {
  const current = typeof search === 'string'
    ? new URLSearchParams(search.startsWith('?') ? search.slice(1) : search)
    : search
  const allowed = new Set<string>(preserveKeys)
  const sanitized = new URLSearchParams()
  for (const key of PROJECT_SPACE_QUERY_KEYS) {
    if (!allowed.has(key)) continue
    const value = current.get(key)
    if (value !== null && validProjectSpaceQueryValue(key, value)) {
      sanitized.set(key, value)
    }
  }
  return sanitized
}

export function projectSpaceLocationWithContext(
  targetPathname: string,
  currentSearch: URLSearchParams | string,
  currentHash = '',
  preserveKeys: readonly ProjectSpaceQueryKey[] = PROJECT_SPACE_QUERY_KEYS,
): string | null {
  if (!isCanonicalProjectSpacePath(targetPathname)) return null
  return formatProjectSpaceLocation(
    targetPathname,
    sanitizeProjectSpaceSearch(currentSearch, preserveKeys),
    sanitizeProjectSpaceHash(currentHash),
  )
}

/**
 * Moving between primary product surfaces must not carry view-local state.
 * `source` is the only query value that remains meaningful across surfaces;
 * the bounded hash stays available for a caller-owned focus target.
 */
export function projectSpaceCrossSurfaceLocation(
  targetPathname: string,
  currentSearch: URLSearchParams | string,
  currentHash = '',
): string | null {
  return projectSpaceLocationWithContext(
    targetPathname,
    currentSearch,
    currentHash,
    ['source'],
  )
}

export function resolveCanonicalProjectSpaceLocation(
  target: string,
  currentSearch: URLSearchParams | string,
  currentHash = '',
): string | null {
  if (!target.startsWith('/') || target.startsWith('//') || target.includes('\\')) return null

  let parsed: URL
  try {
    parsed = new URL(target, 'https://colla.invalid')
  } catch {
    return null
  }
  if (parsed.origin !== 'https://colla.invalid' || !isCanonicalProjectSpacePath(parsed.pathname)) {
    return null
  }

  const merged = sanitizeProjectSpaceSearch(currentSearch)
  const targetQuery = sanitizeProjectSpaceSearch(parsed.search)
  for (const [key, value] of targetQuery) {
    merged.set(key, value)
  }
  const hash = sanitizeProjectSpaceHash(parsed.hash)
    || sanitizeProjectSpaceHash(currentHash)
  return formatProjectSpaceLocation(parsed.pathname, merged, hash)
}

export function legacyProjectSpaceLocation(
  spaceId: string,
  currentSearch: URLSearchParams | string,
  currentHash = '',
): string | null {
  if (!new RegExp(`^${SAFE_SEGMENT}$`).test(spaceId)) return null
  return projectSpaceLocationWithContext(
    `/project-spaces/${spaceId}`,
    currentSearch,
    currentHash,
  )
}

export function projectSpaceListLocation(
  currentSearch: URLSearchParams | string,
  currentHash = '',
): string {
  return formatProjectSpaceLocation(
    '/project-spaces',
    sanitizeProjectSpaceSearch(currentSearch, ['source']),
    sanitizeProjectSpaceHash(currentHash),
  )
}

export function isCanonicalProjectSpacePath(pathname: string): boolean {
  if (
    !pathname.startsWith('/project-spaces/')
    || pathname.includes('\\')
    || pathname.includes('//')
    || pathname.includes('%')
  ) {
    return false
  }
  return CANONICAL_PROJECT_SPACE_PATH.test(pathname)
}

export function sanitizeProjectSpaceHash(hash: string): string {
  if (!hash) return ''
  const normalized = hash.startsWith('#') ? hash : `#${hash}`
  return SAFE_HASH.test(normalized) ? normalized : ''
}

export function isProjectSpaceQueryKey(value: string): value is ProjectSpaceQueryKey {
  return PROJECT_SPACE_QUERY_KEY_SET.has(value)
}

export function isProjectSpaceWorkModelTab(value: string | null): value is ProjectSpaceWorkModelTab {
  return PROJECT_SPACE_WORK_MODEL_TABS.some((tab) => tab === value)
}

function validProjectSpaceQueryValue(key: ProjectSpaceQueryKey, value: string): boolean {
  if (key === 'create') return value === '1'
  if (key === 'workModelTab') return isProjectSpaceWorkModelTab(value)
  return TOKEN_VALUE.test(value)
}

function formatProjectSpaceLocation(
  pathname: string,
  search: URLSearchParams,
  hash: string,
): string {
  const query = search.toString()
  return `${pathname}${query ? `?${query}` : ''}${hash}`
}
