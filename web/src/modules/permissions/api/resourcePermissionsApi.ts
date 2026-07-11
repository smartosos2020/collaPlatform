import { apiGet, apiPost } from '../../../shared/api/httpClient'

export type ManagedResourceType = 'knowledge_content' | 'base' | 'project' | 'knowledge_base'
export type ResourcePermissionSubjectType = 'user' | 'department' | 'user_group' | 'role'
export type ResourcePermissionLevel = 'view' | 'comment' | 'edit' | 'manage' | 'owner'

export type ResourcePermissionEntry = {
  id: string
  resourceType: ManagedResourceType
  resourceId: string
  subjectType: ResourcePermissionSubjectType
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
  expandedMemberCount: number
}

export type GrantResourcePermissionRequest = {
  subjectType: ResourcePermissionSubjectType
  subjectId: string
  permissionLevel: ResourcePermissionLevel
  expiresAt?: string
  confirmHighRisk?: boolean
}

export type ResourcePermissionRequest = {
  id: string
  resourceType: ManagedResourceType
  resourceId: string
  requesterId: string
  requesterName: string
  permissionLevel: ResourcePermissionLevel
  reason?: string | null
  status: 'submitted' | 'approved' | 'rejected'
  decidedBy?: string | null
  decidedByName?: string | null
  decidedAt?: string | null
  decisionNote?: string | null
  createdAt: string
  updatedAt: string
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

export async function requestResourcePermission(
  resourceType: ManagedResourceType,
  resourceId: string,
  request: { permissionLevel: ResourcePermissionLevel; reason?: string },
): Promise<ResourcePermissionRequest> {
  return apiPost<ResourcePermissionRequest>(`/resource-permissions/${resourceType}/${resourceId}/requests`, request)
}

export async function listResourcePermissionRequests(
  resourceType: ManagedResourceType,
  resourceId: string,
  status = 'submitted',
): Promise<ResourcePermissionRequest[]> {
  return apiGet<ResourcePermissionRequest[]>(
    `/resource-permissions/${resourceType}/${resourceId}/requests${status ? `?status=${status}` : ''}`,
  )
}

export async function approveResourcePermissionRequest(requestId: string, note?: string): Promise<ResourcePermissionRequest> {
  return apiPost<ResourcePermissionRequest>(`/resource-permissions/requests/${requestId}/approve`, { note })
}

export async function rejectResourcePermissionRequest(requestId: string, note?: string): Promise<ResourcePermissionRequest> {
  return apiPost<ResourcePermissionRequest>(`/resource-permissions/requests/${requestId}/reject`, { note })
}

export async function breakResourcePermissionInheritance(
  resourceType: ManagedResourceType,
  resourceId: string,
  confirmHighRisk = true,
): Promise<void> {
  return apiPost<void>(`/resource-permissions/${resourceType}/${resourceId}/inheritance/break`, { confirmHighRisk })
}

export async function restoreResourcePermissionInheritance(
  resourceType: ManagedResourceType,
  resourceId: string,
): Promise<void> {
  return apiPost<void>(`/resource-permissions/${resourceType}/${resourceId}/inheritance/restore`, {})
}
