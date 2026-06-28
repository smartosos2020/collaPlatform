import { apiDelete, apiGet, apiPatch, apiPost } from '../../../shared/api/httpClient'

export type DepartmentSummary = {
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
}

export type DepartmentManager = {
  id: string
  departmentId: string
  userId: string
  username: string
  displayName: string
  managerType: 'primary' | 'deputy'
  createdAt: string
}

export type DepartmentTreeNode = {
  department: DepartmentSummary
  managers: DepartmentManager[]
  children: DepartmentTreeNode[]
}

export type DepartmentMember = {
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
}

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

export async function listDepartmentTree(): Promise<DepartmentTreeNode[]> {
  return apiGet<DepartmentTreeNode[]>('/admin/departments/tree')
}

export async function createDepartment(request: DepartmentRequest): Promise<DepartmentSummary> {
  return apiPost<DepartmentSummary>('/admin/departments', request)
}

export async function updateDepartment(departmentId: string, request: DepartmentRequest): Promise<DepartmentSummary> {
  return apiPatch<DepartmentSummary>(`/admin/departments/${departmentId}`, request)
}

export async function moveDepartment(departmentId: string, request: MoveDepartmentRequest): Promise<DepartmentSummary> {
  return apiPost<DepartmentSummary>(`/admin/departments/${departmentId}/move`, request)
}

export async function disableDepartment(departmentId: string): Promise<void> {
  return apiPost<void>(`/admin/departments/${departmentId}/disable`)
}

export async function deleteDepartment(departmentId: string): Promise<void> {
  return apiDelete<void>(`/admin/departments/${departmentId}`)
}

export async function listDepartmentMembers(departmentId: string): Promise<DepartmentMember[]> {
  return apiGet<DepartmentMember[]>(`/admin/departments/${departmentId}/members`)
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
