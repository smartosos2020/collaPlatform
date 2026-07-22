import { apiGet, apiPatch, apiPost, apiPut } from '../../../shared/api/httpClient'

export type WorkItemTypeStatus = 'active' | 'disabled' | 'retired'
export type WorkItemTypeAction = 'create' | 'edit' | 'copy' | 'reorder' | 'disable' | 'restore' | 'retire'

export type WorkItemTypeVersion = {
  id: string
  number: number
  status: string
  configHash: string
  config: unknown
}

export type ConfiguredWorkItemType = {
  id: string
  typeKey: string
  name: string
  icon?: string | null
  description?: string | null
  sortOrder: number
  status: WorkItemTypeStatus
  system: boolean
  source: 'development_preset' | 'workspace_custom'
  presetCatalogVersion?: string | null
  aggregateVersion: number
  currentVersion: WorkItemTypeVersion
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
  availableActions: WorkItemTypeAction[]
}

export type WorkItemTypeConfiguration = {
  spaceId: string
  spaceStatus: string
  availableActions: WorkItemTypeAction[]
  items: ConfiguredWorkItemType[]
}

export type ActiveWorkItemTypeSummary = {
  typeKey: string
  name: string
  icon?: string | null
  sortOrder: number
}

export type WorkItemTypeDraft = {
  typeKey: string
  name: string
  icon?: string
  description?: string
  sortOrder?: number
}

export const workItemTypeKeys = {
  all: ['project-spaces', 'work-item-types'] as const,
  configuration: (spaceId: string, status = 'all') =>
    [...workItemTypeKeys.all, spaceId, 'configuration', status] as const,
  detail: (spaceId: string, typeId: string) =>
    [...workItemTypeKeys.all, spaceId, 'detail', typeId] as const,
  active: (spaceId: string) => [...workItemTypeKeys.all, spaceId, 'active'] as const,
}

export function listConfiguredWorkItemTypes(spaceId: string, status?: WorkItemTypeStatus) {
  const query = status ? `?${new URLSearchParams({ status })}` : ''
  return apiGet<WorkItemTypeConfiguration>(`/project-spaces/${spaceId}/configuration/types${query}`)
}

export function getConfiguredWorkItemType(spaceId: string, typeId: string) {
  return apiGet<ConfiguredWorkItemType>(`/project-spaces/${spaceId}/configuration/types/${typeId}`)
}

export function listActiveWorkItemTypes(spaceId: string) {
  return apiGet<ActiveWorkItemTypeSummary[]>(`/project-spaces/${spaceId}/work-item-types`)
}

export function createWorkItemType(spaceId: string, draft: WorkItemTypeDraft) {
  return apiPost<ConfiguredWorkItemType>(`/project-spaces/${spaceId}/configuration/types`, draft)
}

export function updateWorkItemType(
  spaceId: string,
  typeId: string,
  draft: Pick<WorkItemTypeDraft, 'name' | 'icon' | 'description'> & { aggregateVersion: number },
) {
  return apiPatch<ConfiguredWorkItemType>(`/project-spaces/${spaceId}/configuration/types/${typeId}`, draft)
}

export function copyWorkItemType(spaceId: string, typeId: string, draft: WorkItemTypeDraft) {
  return apiPost<ConfiguredWorkItemType>(`/project-spaces/${spaceId}/configuration/types/${typeId}:copy`, draft)
}

export function reorderWorkItemTypes(
  spaceId: string,
  items: Array<{ typeId: string; sortOrder: number; aggregateVersion: number }>,
) {
  return apiPut<WorkItemTypeConfiguration>(`/project-spaces/${spaceId}/configuration/types:reorder`, { items })
}

export function transitionWorkItemType(
  spaceId: string,
  typeId: string,
  action: 'disable' | 'restore' | 'retire',
  aggregateVersion: number,
) {
  return apiPost<ConfiguredWorkItemType>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}:${action}`,
    { aggregateVersion },
  )
}
