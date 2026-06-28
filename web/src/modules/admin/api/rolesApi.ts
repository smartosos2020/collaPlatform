import { apiDelete, apiGet, apiPatch, apiPost, apiPut } from '../../../shared/api/httpClient'

export type PermissionRiskLevel = 'low' | 'medium' | 'high' | 'critical'

export type PermissionCatalogItem = {
  id: string
  code: string
  name: string
  module: string
  description?: string | null
  riskLevel: PermissionRiskLevel
  builtin: boolean
  displayOrder: number
}

export type RoleSummary = {
  id: string
  code: string
  name: string
  scope: string
  description?: string | null
  status: 'active' | 'disabled'
  builtin: boolean
  permissionCodes: string[]
  createdAt: string
  updatedAt: string
}

export type RoleDetail = Omit<RoleSummary, 'permissionCodes'> & {
  permissions: PermissionCatalogItem[]
  assignments: RoleAssignmentSummary[]
}

export type RoleAssignmentSubjectType = 'user' | 'department' | 'user_group'

export type RoleAssignmentSummary = {
  id: string
  roleId: string
  roleCode: string
  roleName: string
  subjectType: RoleAssignmentSubjectType
  subjectId: string
  subjectName?: string | null
  subjectDetail?: string | null
  scopeType: string
  scopeId?: string | null
  effectiveAt: string
  expiresAt?: string | null
  status: 'active' | 'revoked'
  createdAt: string
}

export type RoleRequest = {
  code: string
  name: string
  scope?: string
  description?: string
}

export type UpdateRoleRequest = {
  name: string
  scope?: string
  description?: string
  status?: 'active' | 'disabled'
}

export type ReplaceRolePermissionsRequest = {
  permissionCodes: string[]
  confirmHighRisk?: boolean
}

export type RoleAssignmentRequest = {
  roleId: string
  subjectType: RoleAssignmentSubjectType
  subjectId: string
  scopeType?: string
  scopeId?: string
  effectiveAt?: string
  expiresAt?: string
  confirmHighRisk?: boolean
}

export async function listPermissions(): Promise<PermissionCatalogItem[]> {
  return apiGet<PermissionCatalogItem[]>('/admin/permissions')
}

export async function listRoles(): Promise<RoleSummary[]> {
  return apiGet<RoleSummary[]>('/admin/roles')
}

export async function getRole(roleId: string): Promise<RoleDetail> {
  return apiGet<RoleDetail>(`/admin/roles/${roleId}`)
}

export async function createRole(request: RoleRequest): Promise<RoleSummary> {
  return apiPost<RoleSummary>('/admin/roles', request)
}

export async function updateRole(roleId: string, request: UpdateRoleRequest): Promise<RoleSummary> {
  return apiPatch<RoleSummary>(`/admin/roles/${roleId}`, request)
}

export async function replaceRolePermissions(
  roleId: string,
  request: ReplaceRolePermissionsRequest,
): Promise<RoleDetail> {
  return apiPut<RoleDetail>(`/admin/roles/${roleId}/permissions`, request)
}

export async function listRoleAssignments(roleId?: string): Promise<RoleAssignmentSummary[]> {
  return apiGet<RoleAssignmentSummary[]>(`/admin/role-assignments${roleId ? `?roleId=${roleId}` : ''}`)
}

export async function createRoleAssignment(request: RoleAssignmentRequest): Promise<RoleAssignmentSummary> {
  return apiPost<RoleAssignmentSummary>('/admin/role-assignments', request)
}

export async function revokeRoleAssignment(assignmentId: string): Promise<void> {
  return apiDelete<void>(`/admin/role-assignments/${assignmentId}`)
}
