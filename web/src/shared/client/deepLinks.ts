export type NavigationTarget = {
  webPath?: string | null
  mobileFallbackPath?: string | null
  deepLink?: string | null
}

const deepLinkWebPrefixes: Record<string, (objectId: string) => string> = {
  approval: (objectId) => `/approvals/${objectId}`,
  base: (objectId) => `/bases/${objectId}`,
  knowledge_content: () => '/knowledge-bases',
  issue: (objectId) => `/issues/${objectId}`,
}

export function resolveNavigationPath(target: NavigationTarget) {
  if (target.webPath) {
    return normalizeKnowledgeContentPath(target.webPath)
  }
  if (target.mobileFallbackPath) {
    return target.mobileFallbackPath
  }
  return target.deepLink ? webPathFromDeepLink(target.deepLink) : null
}

export function normalizeKnowledgeContentPath(path: string) {
  return path
}

export function webPathFromDeepLink(deepLink: string) {
  const match = deepLink.match(/^colla:\/\/([^/]+)\/([^/?#]+)/)
  if (!match) {
    return null
  }
  const [, objectType, objectId] = match
  if (objectType === 'knowledge-content' || objectType === 'knowledge_content') {
    const query = deepLink.includes('?') ? new URLSearchParams(deepLink.slice(deepLink.indexOf('?') + 1)) : null
    const spaceId = query?.get('spaceId')
    return spaceId ? `/knowledge-bases/${spaceId}/items/${objectId}` : '/knowledge-bases'
  }
  return deepLinkWebPrefixes[objectType]?.(objectId) ?? null
}
