import { apiDelete, apiGet, apiPatch, apiPost } from '../../../shared/api/httpClient'

export type UserGroupSummary = {
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
}

export type UserGroupMember = {
  id: string
  groupId: string
  subjectType: 'user' | 'department'
  subjectId: string
  subjectName: string
  subjectDetail?: string | null
  subjectStatus: 'active' | 'disabled'
  createdAt: string
}

export type ExpandedUserGroupMember = {
  userId: string
  username: string
  displayName: string
  email?: string | null
  status: 'active' | 'disabled'
  sourceType: 'user' | 'department'
  sourceId: string
  sourceName: string
}

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

export async function listUserGroups(options: { activeOnly?: boolean } = {}): Promise<UserGroupSummary[]> {
  const params = new URLSearchParams()
  if (options.activeOnly) {
    params.set('activeOnly', 'true')
  }
  return apiGet<UserGroupSummary[]>(`/admin/user-groups${params.size ? `?${params}` : ''}`)
}

export async function createUserGroup(request: UserGroupRequest): Promise<UserGroupSummary> {
  return apiPost<UserGroupSummary>('/admin/user-groups', request)
}

export async function updateUserGroup(groupId: string, request: UserGroupRequest): Promise<UserGroupSummary> {
  return apiPatch<UserGroupSummary>(`/admin/user-groups/${groupId}`, request)
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

export async function listUserGroupMembers(groupId: string): Promise<UserGroupMember[]> {
  return apiGet<UserGroupMember[]>(`/admin/user-groups/${groupId}/members`)
}

export async function addUserGroupMember(groupId: string, request: AddUserGroupMemberRequest): Promise<UserGroupMember> {
  return apiPost<UserGroupMember>(`/admin/user-groups/${groupId}/members`, request)
}

export async function removeUserGroupMember(groupId: string, memberId: string): Promise<void> {
  return apiDelete<void>(`/admin/user-groups/${groupId}/members/${memberId}`)
}

export async function listExpandedUserGroupMembers(groupId: string): Promise<ExpandedUserGroupMember[]> {
  return apiGet<ExpandedUserGroupMember[]>(`/admin/user-groups/${groupId}/expanded-members`)
}
