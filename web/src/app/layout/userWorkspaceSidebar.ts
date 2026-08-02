export const USER_WORKSPACE_SIDEBAR_VALUES = ['expanded', 'collapsed'] as const
export type UserWorkspaceSidebarState = (typeof USER_WORKSPACE_SIDEBAR_VALUES)[number]

export function normalizeUserWorkspaceSidebarState(raw: unknown): UserWorkspaceSidebarState {
  return raw === 'collapsed' ? 'collapsed' : 'expanded'
}

export function userWorkspaceSidebarStorageKey(userId?: string | null): string {
  const scope = userId && userId.trim() ? userId.trim() : 'anonymous'
  return `colla.user-workspace.sidebar.${scope}`
}
