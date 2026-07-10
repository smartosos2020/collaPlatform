import { apiDelete, apiGet, apiPatch, apiPost } from '../../../shared/api/httpClient'

export type AdminDepartmentView = {
  id: string
  parentId?: string | null
  code: string
  name: string
  path: string
  depth: number
  sortOrder: number
  status: 'active' | 'disabled'
  memberCount: number
  managerCount: number
  createdAt: string
  updatedAt: string
  hierarchy?: {
    parentId?: string | null
    path: string
    depth: number
    sortOrder: number
  }
  membership?: {
    memberCount: number
    managerCount: number
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

export type DepartmentSummary = AdminDepartmentView

export type AdminDepartmentManagerView = {
  id: string
  departmentId: string
  userId: string
  username: string
  displayName: string
  managerType: 'primary' | 'deputy'
  createdAt: string
  profile?: {
    userId: string
    displayName: string
    avatarFileId?: string | null
    email?: string | null
  }
  availableActions?: string[]
}

export type DepartmentManager = AdminDepartmentManagerView

export type AdminDepartmentTreeNode = {
  department: AdminDepartmentView
  managers: AdminDepartmentManagerView[]
  children: AdminDepartmentTreeNode[]
}

export type DepartmentTreeNode = AdminDepartmentTreeNode

export type AdminDepartmentMemberView = {
  id: string
  departmentId: string
  userId: string
  username: string
  displayName: string
  email?: string
  relationType: 'primary' | 'member'
  status: 'active' | 'disabled'
  startedAt: string
  endedAt?: string | null
  profile?: {
    userId: string
    displayName: string
    avatarFileId?: string | null
    email?: string | null
  }
  governance?: {
    status: 'active' | 'disabled'
    managedObjectType: string
    managedObjectId: string
  }
  availableActions?: string[]
}

export type DepartmentMember = AdminDepartmentMemberView

export type DepartmentRequest = {
  parentId?: string | null
  code: string
  name: string
  sortOrder?: number
}

export type MoveDepartmentRequest = {
  parentId?: string | null
  sortOrder?: number
}

export async function listDepartmentTree(): Promise<AdminDepartmentTreeNode[]> {
  return apiGet<AdminDepartmentTreeNode[]>('/admin/departments/tree')
}

export async function createDepartment(request: DepartmentRequest): Promise<AdminDepartmentView> {
  return apiPost<AdminDepartmentView>('/admin/departments', request)
}

export async function updateDepartment(departmentId: string, request: DepartmentRequest): Promise<AdminDepartmentView> {
  return apiPatch<AdminDepartmentView>(`/admin/departments/${departmentId}`, request)
}

export async function moveDepartment(departmentId: string, request: MoveDepartmentRequest): Promise<AdminDepartmentView> {
  return apiPost<AdminDepartmentView>(`/admin/departments/${departmentId}/move`, request)
}

export async function disableDepartment(departmentId: string): Promise<void> {
  return apiPost<void>(`/admin/departments/${departmentId}/disable`)
}

export async function enableDepartment(departmentId: string): Promise<void> {
  return apiPost<void>(`/admin/departments/${departmentId}/enable`)
}

export async function deleteDepartment(departmentId: string): Promise<void> {
  return apiDelete<void>(`/admin/departments/${departmentId}`)
}

export async function listDepartmentMembers(departmentId: string): Promise<AdminDepartmentMemberView[]> {
  return apiGet<AdminDepartmentMemberView[]>(`/admin/departments/${departmentId}/members`)
}

export async function addDepartmentMember(
  departmentId: string,
  request: { userId: string; relationType?: 'primary' | 'member' },
): Promise<void> {
  return apiPost<void>(`/admin/departments/${departmentId}/members`, request)
}

export async function removeDepartmentMember(departmentId: string, userId: string): Promise<void> {
  return apiDelete<void>(`/admin/departments/${departmentId}/members/${userId}`)
}

export async function addDepartmentManager(
  departmentId: string,
  request: { userId: string; managerType?: 'primary' | 'deputy' },
): Promise<void> {
  return apiPost<void>(`/admin/departments/${departmentId}/managers`, request)
}

export async function removeDepartmentManager(
  departmentId: string,
  userId: string,
  managerType: 'primary' | 'deputy' = 'primary',
): Promise<void> {
  return apiDelete<void>(`/admin/departments/${departmentId}/managers/${userId}?managerType=${managerType}`)
}

export type FlatDepartment = DepartmentSummary & {
  label: string
}

export function flattenDepartmentTree(nodes: DepartmentTreeNode[]): FlatDepartment[] {
  return nodes.flatMap((node) => {
    const label = `${'　'.repeat(node.department.depth)}${node.department.name}`
    return [{ ...node.department, label }, ...flattenDepartmentTree(node.children)]
  })
}
