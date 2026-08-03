import { safeHref, safeInternalPath } from '../url/safeUrl.ts'

export type NavigationTarget = {
  webPath?: string | null
  mobileFallbackPath?: string | null
  deepLink?: string | null
}

const deepLinkWebPrefixes: Record<string, (objectId: string) => string> = {
  approval: (objectId) => `/approvals/${objectId}`,
  base: (objectId) => `/bases/${objectId}`,
  knowledge_content: () => '/knowledge-bases',
  work_item: () => '/project-spaces',
  'work-item': () => '/project-spaces',
  project_space: (objectId) => `/project-spaces/${objectId}`,
  'project-space': (objectId) => `/project-spaces/${objectId}`,
}

export function resolveNavigationPath(target: NavigationTarget) {
  if (target.webPath) {
    return safeInternalPath(target.webPath)
  }
  if (target.mobileFallbackPath) {
    return safeInternalPath(target.mobileFallbackPath)
  }
  return target.deepLink ? webPathFromDeepLink(target.deepLink) : null
}

/**
 * 校验来自服务端的跳转地址：站内路径或白名单外链（http/https/mailto/tel）
 * 原样返回，其余（javascript:/data: 伪协议、协议相对地址等）返回 null。
 * 调用方拿到 null 时不得再把它放进 href / navigate。
 */
export function normalizeKnowledgeContentPath(path: string): string | null {
  return safeHref(path)
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
