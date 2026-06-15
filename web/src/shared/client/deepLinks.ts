export type NavigationTarget = {
  webPath?: string | null
  mobileFallbackPath?: string | null
  deepLink?: string | null
}

const deepLinkWebPrefixes: Record<string, (objectId: string) => string> = {
  approval: (objectId) => `/approvals/${objectId}`,
  base: (objectId) => `/bases/${objectId}`,
  document: (objectId) => `/docs/${objectId}`,
  issue: (objectId) => `/issues/${objectId}`,
}

export function resolveNavigationPath(target: NavigationTarget) {
  if (target.webPath) {
    return target.webPath
  }
  if (target.mobileFallbackPath) {
    return target.mobileFallbackPath
  }
  return target.deepLink ? webPathFromDeepLink(target.deepLink) : null
}

export function webPathFromDeepLink(deepLink: string) {
  const match = deepLink.match(/^colla:\/\/([^/]+)\/([^/?#]+)/)
  if (!match) {
    return null
  }
  const [, objectType, objectId] = match
  return deepLinkWebPrefixes[objectType]?.(objectId) ?? null
}
