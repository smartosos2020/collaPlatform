import { apiGet, apiPatch, apiPost } from '../../../shared/api/httpClient'

export type AdminMemberView = {
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
  profile?: {
    userId: string
    displayName: string
    avatarFileId?: string | null
    email?: string | null
  }
  account?: {
    username: string
    status: 'active' | 'disabled'
    lastLoginAt?: string | null
  }
  organization?: {
    primaryDepartmentId?: string | null
    primaryDepartmentCode?: string | null
    primaryDepartmentName?: string | null
    departments: MemberDepartment[]
  }
  management?: {
    status: 'active' | 'disabled'
    roles: string[]
    createdAt: string
    lastLoginAt?: string | null
  }
  availableActions?: string[]
}

export type MemberSummary = AdminMemberView

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

export async function listMembers(filters: ListMembersFilters = {}): Promise<AdminMemberView[]> {
  const params = new URLSearchParams()
  if (filters.departmentId) {
    params.set('departmentId', filters.departmentId)
  }
  const query = params.toString()
  return apiGet<AdminMemberView[]>(`/admin/users${query ? `?${query}` : ''}`)
}

export async function createMember(request: CreateMemberRequest): Promise<AdminMemberView> {
  return apiPost<AdminMemberView>('/admin/users', request)
}

export async function disableMember(userId: string): Promise<void> {
  return apiPost<void>(`/admin/users/${userId}/disable`)
}

export type OffboardingResult = {
  knowledgeBaseCount: number
  conversationCount: number
}

export async function offboardMember(userId: string, handoverToUserId: string): Promise<OffboardingResult> {
  return apiPost<OffboardingResult>(`/admin/users/${userId}/offboard`, { handoverToUserId })
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
