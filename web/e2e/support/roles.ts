export type RegressionRole = 'administrator' | 'member' | 'editor' | 'viewer' | 'outsider'

export type RoleCredential = {
  role: RegressionRole
  username: string
  password: string
  provisioned: boolean
}

const environmentRoleKeys: Record<Exclude<RegressionRole, 'administrator'>, [string, string]> = {
  member: ['COLLA_E2E_MEMBER_USERNAME', 'COLLA_E2E_MEMBER_PASSWORD'],
  editor: ['COLLA_E2E_EDITOR_USERNAME', 'COLLA_E2E_EDITOR_PASSWORD'],
  viewer: ['COLLA_E2E_VIEWER_USERNAME', 'COLLA_E2E_VIEWER_PASSWORD'],
  outsider: ['COLLA_E2E_OUTSIDER_USERNAME', 'COLLA_E2E_OUTSIDER_PASSWORD'],
}

export function roleCredential(role: RegressionRole): RoleCredential | null {
  if (role === 'administrator') {
    return {
      role,
      username: process.env.COLLA_E2E_ADMIN_USERNAME ?? process.env.COLLA_E2E_USERNAME ?? 'admin',
      password: process.env.COLLA_E2E_ADMIN_PASSWORD ?? process.env.COLLA_E2E_PASSWORD ?? 'admin123456',
      provisioned: true,
    }
  }
  const [usernameKey, passwordKey] = environmentRoleKeys[role]
  const username = process.env[usernameKey]
  const password = process.env[passwordKey]
  if (!username || !password) {
    return null
  }
  return { role, username, password, provisioned: true }
}

export function requireRoleCredential(role: RegressionRole): RoleCredential {
  const credential = roleCredential(role)
  if (!credential) {
    throw new Error(`E2E role ${role} requires configured environment credentials.`)
  }
  return credential
}
