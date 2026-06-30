import { apiGet, apiPatch, apiPost } from '../../../shared/api/httpClient'

export type MemberSummary = {
  id: string
  username: string
  displayName: string
  avatarFileId?: string | null
  email?: string
  status: 'active' | 'disabled'
  lastLoginAt?: string
  createdAt: string
  roles: string[]
  departments: MemberDepartment[]
}

export type MemberDepartment = {
  departmentId: string
  departmentCode: string
  departmentName: string
  relationType: 'primary' | 'member'
}

export type CreateMemberRequest = {
  username: string
  password: string
  displayName: string
  email?: string
  roleCode?: string
  primaryDepartmentId?: string
}

export type ListMembersFilters = {
  departmentId?: string
}

export async function listMembers(filters: ListMembersFilters = {}): Promise<MemberSummary[]> {
  const params = new URLSearchParams()
  if (filters.departmentId) {
    params.set('departmentId', filters.departmentId)
  }
  const query = params.toString()
  return apiGet<MemberSummary[]>(`/admin/users${query ? `?${query}` : ''}`)
}

export async function createMember(request: CreateMemberRequest): Promise<MemberSummary> {
  return apiPost<MemberSummary>('/admin/users', request)
}

export async function disableMember(userId: string): Promise<void> {
  return apiPost<void>(`/admin/users/${userId}/disable`)
}

export async function enableMember(userId: string): Promise<void> {
  return apiPost<void>(`/admin/users/${userId}/enable`)
}

export async function resetMemberPassword(userId: string, newPassword: string): Promise<void> {
  return apiPatch<void>(`/admin/users/${userId}/password`, { newPassword })
}

export async function updateMemberAvatar(userId: string, avatarFileId?: string | null): Promise<void> {
  return apiPatch<void>(`/admin/users/${userId}/avatar`, { avatarFileId })
}
