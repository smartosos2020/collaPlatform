import { apiDelete, apiGet, apiPatch, apiPost, apiPut } from '../../../shared/api/httpClient'

export type PermissionRiskLevel = 'low' | 'medium' | 'high' | 'critical'

export type AdminPermissionCatalogItemView = {
  id: string
  code: string
  name: string
  module: string
  description?: string | null
  riskLevel: PermissionRiskLevel
  builtin: boolean
  displayOrder: number
  category?: {
    module: string
  }
  risk?: {
    level: PermissionRiskLevel
    weight: number
  }
}

export type PermissionCatalogItem = AdminPermissionCatalogItemView

export type AdminRoleView = {
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
  roleClassification?: {
    category: 'system_management' | 'business_collaboration'
    scope: string
    builtin: boolean
  }
  permissionMatrix?: {
    scope: string
    permissionCount: number
    permissionCodes: string[]
  }
  assignmentSummary?: {
    total: number
    subjectTypes: RoleAssignmentSubjectType[]
  }
  governance?: {
    status: 'active' | 'disabled'
    builtin: boolean
    permissionCount: number
  }
  availableActions?: string[]
}

export type RoleSummary = AdminRoleView

export type AdminRoleDetailView = Omit<AdminRoleView, 'permissionCodes' | 'permissionMatrix'> & {
  permissions: AdminPermissionCatalogItemView[]
  assignments: AdminRoleAssignmentView[]
  permissionMatrix?: {
    module: string
    permissions: AdminPermissionCatalogItemView[]
    permissionCount: number
    highRiskCount: number
  }[]
}

export type RoleAssignmentSubjectType = 'user' | 'department' | 'user_group'

export type RoleDetail = AdminRoleDetailView

export type AdminRoleAssignmentView = {
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
  subject?: {
    subjectType: RoleAssignmentSubjectType
    subjectId: string
    subjectName?: string | null
    subjectDetail?: string | null
  }
  scope?: {
    scopeType: string
    scopeId?: string | null
  }
  lifecycle?: {
    status: 'active' | 'revoked'
    effectiveAt: string
    expiresAt?: string | null
  }
  availableActions?: string[]
}

export type RoleAssignmentSummary = AdminRoleAssignmentView

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

export async function listPermissions(): Promise<AdminPermissionCatalogItemView[]> {
  return apiGet<AdminPermissionCatalogItemView[]>('/admin/permissions')
}

export async function listRoles(): Promise<AdminRoleView[]> {
  return apiGet<AdminRoleView[]>('/admin/roles')
}

export async function getRole(roleId: string): Promise<AdminRoleDetailView> {
  return apiGet<AdminRoleDetailView>(`/admin/roles/${roleId}`)
}

export async function createRole(request: RoleRequest): Promise<AdminRoleView> {
  return apiPost<AdminRoleView>('/admin/roles', request)
}

export async function updateRole(roleId: string, request: UpdateRoleRequest): Promise<AdminRoleView> {
  return apiPatch<AdminRoleView>(`/admin/roles/${roleId}`, request)
}

export async function replaceRolePermissions(
  roleId: string,
  request: ReplaceRolePermissionsRequest,
): Promise<AdminRoleDetailView> {
  return apiPut<AdminRoleDetailView>(`/admin/roles/${roleId}/permissions`, request)
}

export async function listRoleAssignments(roleId?: string): Promise<AdminRoleAssignmentView[]> {
  return apiGet<AdminRoleAssignmentView[]>(`/admin/role-assignments${roleId ? `?roleId=${roleId}` : ''}`)
}

export async function createRoleAssignment(request: RoleAssignmentRequest): Promise<AdminRoleAssignmentView> {
  return apiPost<AdminRoleAssignmentView>('/admin/role-assignments', request)
}

export async function revokeRoleAssignment(assignmentId: string): Promise<void> {
  return apiDelete<void>(`/admin/role-assignments/${assignmentId}`)
}
