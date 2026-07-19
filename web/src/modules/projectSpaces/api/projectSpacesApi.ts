import { apiDelete, apiGet, apiPatch, apiPost } from '../../../shared/api/httpClient'

export type ProjectSpaceStatus = 'active' | 'disabled' | 'archived'
export type ProjectSpaceVisibility = 'private' | 'workspace'
export type ProjectSpaceRole = 'owner' | 'admin' | 'member' | 'guest'

export type UserProjectSpace = {
  id: string
  spaceKey: string
  name: string
  description?: string | null
  status: ProjectSpaceStatus
  visibility: ProjectSpaceVisibility
  version: number
  currentUserRole?: ProjectSpaceRole | null
  memberCount: number
  member: boolean
  createdAt: string
  updatedAt: string
  disabledAt?: string | null
  archivedAt?: string | null
  availableActions: string[]
}

export type ProjectSpaceMember = {
  id: string
  spaceId: string
  userId: string
  username: string
  displayName: string
  avatarFileId?: string | null
  email?: string | null
  userStatus: string
  memberStatus: string
  roleKey: ProjectSpaceRole
  effective: boolean
  joinedAt: string
  removedAt?: string | null
  updatedAt: string
}

export type ProjectSpaceCandidate = {
  userId: string
  username: string
  displayName: string
  avatarFileId?: string | null
  email?: string | null
  departments: string[]
}

export type ProjectSpaceInvitation = {
  id: string
  spaceId: string
  inviteeUserId: string
  inviteeDisplayName: string
  inviteeEmail?: string | null
  roleKey: ProjectSpaceRole
  status: string
  expiresAt: string
  invitedBy: string
  invitedAt: string
  respondedAt?: string | null
  revokedBy?: string | null
  revokedAt?: string | null
  version: number
  updatedAt: string
  lastSentAt?: string | null
}

export type ProjectSpaceRoleCapability = {
  roleKey: ProjectSpaceRole
  capabilities: string[]
  canManageOwner: boolean
  canGrantAdmin: boolean
}

export type AdminProjectSpace = {
  id: string
  workspaceId: string
  spaceKey: string
  name: string
  description?: string | null
  status: ProjectSpaceStatus
  visibility: ProjectSpaceVisibility
  version: number
  memberCount: number
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
  disabledAt?: string | null
  archivedAt?: string | null
  contentAccessGranted: boolean
  governancePermission: string
  availableGovernanceActions: string[]
}

export type ProjectSpacePermissionExplanation = {
  spaceId: string
  governancePermission: string
  governanceAllowed: boolean
  contentAccessGranted: boolean
  contentAccessSource: string
  explanation: string
}

export function listProjectSpaces() {
  return apiGet<UserProjectSpace[]>('/project-spaces')
}

export function createProjectSpace(request: {
  spaceKey?: string
  name: string
  description?: string
  visibility: ProjectSpaceVisibility
}) {
  return apiPost<UserProjectSpace>('/project-spaces', request)
}

export function getProjectSpace(spaceId: string) {
  return apiGet<UserProjectSpace>(`/project-spaces/${spaceId}`)
}

export function getProjectSpaceSettings(spaceId: string) {
  return apiGet<UserProjectSpace>(`/project-spaces/${spaceId}/settings`)
}

export function updateProjectSpaceSettings(
  spaceId: string,
  request: { name: string; description?: string; visibility: ProjectSpaceVisibility },
) {
  return apiPatch<UserProjectSpace>(`/project-spaces/${spaceId}/settings`, request)
}

export function transitionProjectSpace(
  spaceId: string,
  action: 'disable' | 'restore' | 'archive',
) {
  return apiPost<UserProjectSpace>(`/project-spaces/${spaceId}/settings/${action}`)
}

export function listProjectSpaceMembers(spaceId: string) {
  return apiGet<ProjectSpaceMember[]>(`/project-spaces/${spaceId}/members`)
}

export function searchProjectSpaceCandidates(spaceId: string, query: string) {
  const params = new URLSearchParams({ query })
  return apiGet<ProjectSpaceCandidate[]>(`/project-spaces/${spaceId}/member-candidates?${params}`)
}

export function addProjectSpaceMember(spaceId: string, userId: string, roleKey: ProjectSpaceRole) {
  return apiPost<ProjectSpaceMember>(`/project-spaces/${spaceId}/members`, { userId, roleKey })
}

export function changeProjectSpaceMemberRole(spaceId: string, memberId: string, roleKey: ProjectSpaceRole) {
  return apiPatch<ProjectSpaceMember>(`/project-spaces/${spaceId}/members/${memberId}/role`, { roleKey })
}

export function removeProjectSpaceMember(spaceId: string, memberId: string) {
  return apiDelete<ProjectSpaceMember>(`/project-spaces/${spaceId}/members/${memberId}`)
}

export function leaveProjectSpace(spaceId: string) {
  return apiPost<ProjectSpaceMember>(`/project-spaces/${spaceId}/members/leave`)
}

export function transferProjectSpaceOwner(spaceId: string, memberId: string) {
  return apiPost<ProjectSpaceMember[]>(`/project-spaces/${spaceId}/members/${memberId}/transfer-owner`)
}

export function listProjectSpaceInvitations(spaceId: string) {
  return apiGet<ProjectSpaceInvitation[]>(`/project-spaces/${spaceId}/invitations`)
}

export function inviteProjectSpaceMember(
  spaceId: string,
  request: { userId: string; roleKey: ProjectSpaceRole; expiresInHours?: number },
) {
  return apiPost<ProjectSpaceInvitation>(`/project-spaces/${spaceId}/invitations`, request)
}

export function resendProjectSpaceInvitation(spaceId: string, invitationId: string) {
  return apiPost<ProjectSpaceInvitation>(`/project-spaces/${spaceId}/invitations/${invitationId}/resend`)
}

export function revokeProjectSpaceInvitation(spaceId: string, invitationId: string) {
  return apiDelete<ProjectSpaceInvitation>(`/project-spaces/${spaceId}/invitations/${invitationId}`)
}

export function listProjectSpaceRoleCapabilities(spaceId: string) {
  return apiGet<ProjectSpaceRoleCapability[]>(`/project-spaces/${spaceId}/role-capabilities`)
}

export function listAdminProjectSpaces(filters: {
  status?: string
  visibility?: string
  includeArchived?: boolean
} = {}) {
  const params = new URLSearchParams({
    includeArchived: String(filters.includeArchived ?? true),
    limit: '100',
    offset: '0',
  })
  if (filters.status) params.set('status', filters.status)
  if (filters.visibility) params.set('visibility', filters.visibility)
  return apiGet<AdminProjectSpace[]>(`/admin/project-spaces?${params}`)
}

export function getAdminProjectSpace(spaceId: string) {
  return apiGet<AdminProjectSpace>(`/admin/project-spaces/${spaceId}`)
}

export function getProjectSpacePermissionExplanation(spaceId: string) {
  return apiGet<ProjectSpacePermissionExplanation>(`/admin/project-spaces/${spaceId}/permission-explanation`)
}

export function transitionAdminProjectSpace(
  spaceId: string,
  action: 'disable' | 'restore' | 'archive',
) {
  return apiPost<AdminProjectSpace>(`/admin/project-spaces/${spaceId}/${action}`)
}
