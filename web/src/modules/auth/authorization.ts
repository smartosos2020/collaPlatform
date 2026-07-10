import type { CurrentUser } from './authStore'

const adminPermissionCodes = new Set([
  'admin.access',
  'user.manage',
  'org.view',
  'org.manage',
  'usergroup.view',
  'usergroup.manage',
  'role.view',
  'role.manage',
  'permission.inspect',
])

export function canAccessAdmin(currentUser: CurrentUser | null | undefined) {
  if (!currentUser) {
    return false
  }
  return currentUser.roles.includes('admin') || currentUser.permissions.some((permission) => adminPermissionCodes.has(permission))
}
