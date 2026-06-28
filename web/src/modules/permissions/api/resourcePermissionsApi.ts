import { apiGet, apiPost } from '../../../shared/api/httpClient'

export type ManagedResourceType = 'document' | 'base' | 'project'
export type ResourcePermissionSubjectType = 'user' | 'department' | 'user_group'
export type ResourcePermissionLevel = 'view' | 'comment' | 'edit' | 'manage' | 'owner'

export type ResourcePermissionEntry = {
  id: string
  resourceType: ManagedResourceType
  resourceId: string
  subjectType: ResourcePermissionSubjectType | 'role'
  subjectId: string
  subjectName?: string | null
  subjectDetail?: string | null
  permissionLevel: ResourcePermissionLevel
  sourceType: 'direct' | 'inherited' | 'owner' | 'system'
  sourceId?: string | null
  expiresAt?: string | null
  status: 'active' | 'revoked'
  effectiveStatus: 'active' | 'expired' | 'revoked'
  createdAt: string
  updatedAt: string
}

export type GrantResourcePermissionRequest = {
  subjectType: ResourcePermissionSubjectType
  subjectId: string
  permissionLevel: ResourcePermissionLevel
  expiresAt?: string
  confirmHighRisk?: boolean
}

export async function listResourcePermissions(
  resourceType: ManagedResourceType,
  resourceId: string,
): Promise<ResourcePermissionEntry[]> {
  return apiGet<ResourcePermissionEntry[]>(`/resource-permissions/${resourceType}/${resourceId}`)
}

export async function grantResourcePermission(
  resourceType: ManagedResourceType,
  resourceId: string,
  request: GrantResourcePermissionRequest,
): Promise<ResourcePermissionEntry> {
  return apiPost<ResourcePermissionEntry>(`/resource-permissions/${resourceType}/${resourceId}`, request)
}

export async function revokeResourcePermission(permissionId: string, confirmHighRisk = false): Promise<void> {
  return apiPost<void>(`/resource-permissions/${permissionId}/revoke`, { confirmHighRisk })
}
