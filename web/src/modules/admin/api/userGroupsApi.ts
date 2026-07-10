import { apiDelete, apiGet, apiPatch, apiPost } from '../../../shared/api/httpClient'

export type AdminUserGroupView = {
  id: string
  code: string
  name: string
  description?: string | null
  groupType: 'normal' | 'permission'
  status: 'active' | 'disabled'
  directMemberCount: number
  expandedMemberCount: number
  createdAt: string
  updatedAt: string
  memberExpansion?: {
    directMemberCount: number
    expandedMemberCount: number
  }
  authorizationSubject?: {
    subjectType: 'user_group'
    subjectId: string
    subjectName: string
    subjectDetail?: string | null
  }
  governance?: {
    status: 'active' | 'disabled'
    managedObjectType: string
    managedObjectId: string
  }
  audit?: {
    createdAt: string
    updatedAt: string
  }
  availableActions?: string[]
}

export type UserGroupSummary = AdminUserGroupView

export type AdminUserGroupMemberView = {
  id: string
  groupId: string
  subjectType: 'user' | 'department'
  subjectId: string
  subjectName: string
  subjectDetail?: string | null
  subjectStatus: 'active' | 'disabled'
  createdAt: string
  authorizationSubject?: {
    subjectType: 'user' | 'department'
    subjectId: string
    subjectName: string
    subjectDetail?: string | null
  }
  governance?: {
    status: 'active' | 'disabled'
    managedObjectType: string
    managedObjectId: string
  }
  availableActions?: string[]
}

export type UserGroupMember = AdminUserGroupMemberView

export type AdminExpandedUserGroupMemberView = {
  userId: string
  username: string
  displayName: string
  email?: string | null
  status: 'active' | 'disabled'
  sourceType: 'user' | 'department'
  sourceId: string
  sourceName: string
  profile?: {
    userId: string
    displayName: string
    avatarFileId?: string | null
    email?: string | null
  }
  expansionSource?: {
    sourceType: 'user' | 'department'
    sourceId: string
    sourceName: string
  }
  governance?: {
    status: 'active' | 'disabled'
    managedObjectType: string
    managedObjectId: string
  }
}

export type ExpandedUserGroupMember = AdminExpandedUserGroupMemberView

export type UserGroupRequest = {
  code: string
  name: string
  description?: string
  groupType?: 'normal' | 'permission'
}

export type AddUserGroupMemberRequest = {
  subjectType: 'user' | 'department'
  subjectId: string
}

export async function listUserGroups(options: { activeOnly?: boolean } = {}): Promise<AdminUserGroupView[]> {
  const params = new URLSearchParams()
  if (options.activeOnly) {
    params.set('activeOnly', 'true')
  }
  return apiGet<AdminUserGroupView[]>(`/admin/user-groups${params.size ? `?${params}` : ''}`)
}

export async function createUserGroup(request: UserGroupRequest): Promise<AdminUserGroupView> {
  return apiPost<AdminUserGroupView>('/admin/user-groups', request)
}

export async function updateUserGroup(groupId: string, request: UserGroupRequest): Promise<AdminUserGroupView> {
  return apiPatch<AdminUserGroupView>(`/admin/user-groups/${groupId}`, request)
}

export async function disableUserGroup(groupId: string): Promise<void> {
  return apiPost<void>(`/admin/user-groups/${groupId}/disable`)
}

export async function enableUserGroup(groupId: string): Promise<void> {
  return apiPost<void>(`/admin/user-groups/${groupId}/enable`)
}

export async function deleteUserGroup(groupId: string): Promise<void> {
  return apiDelete<void>(`/admin/user-groups/${groupId}`)
}

export async function listUserGroupMembers(groupId: string): Promise<AdminUserGroupMemberView[]> {
  return apiGet<AdminUserGroupMemberView[]>(`/admin/user-groups/${groupId}/members`)
}

export async function addUserGroupMember(groupId: string, request: AddUserGroupMemberRequest): Promise<AdminUserGroupMemberView> {
  return apiPost<AdminUserGroupMemberView>(`/admin/user-groups/${groupId}/members`, request)
}

export async function removeUserGroupMember(groupId: string, memberId: string): Promise<void> {
  return apiDelete<void>(`/admin/user-groups/${groupId}/members/${memberId}`)
}

export async function listExpandedUserGroupMembers(groupId: string): Promise<AdminExpandedUserGroupMemberView[]> {
  return apiGet<AdminExpandedUserGroupMemberView[]>(`/admin/user-groups/${groupId}/expanded-members`)
}
