import { apiGet, apiPatch, apiPost } from '../../../shared/api/httpClient'

export type MemberSummary = {
  id: string
  username: string
  displayName: string
  email?: string
  status: 'active' | 'disabled'
  lastLoginAt?: string
  createdAt: string
  roles: string[]
}

export type CreateMemberRequest = {
  username: string
  password: string
  displayName: string
  email?: string
  roleCode?: string
}

export async function listMembers(): Promise<MemberSummary[]> {
  return apiGet<MemberSummary[]>('/admin/users')
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
