import { apiGet, apiPatch, apiPost, apiPut } from '../../../shared/api/httpClient'

export type WorkItemFieldType =
  | 'text'
  | 'number'
  | 'boolean'
  | 'single_select'
  | 'multi_select'
  | 'user'
  | 'date'
  | 'datetime'
  | 'url'
  | 'attachment'
  | 'work_item_reference'

export type WorkItemFieldStatus = 'active' | 'disabled' | 'retired'
export type WorkItemFieldAction = 'create' | 'edit' | 'configure' | 'reorder' | 'disable' | 'restore' | 'retire'
export type JsonObject = Record<string, unknown>

export type WorkItemFieldValidationRule = {
  ruleKey: string
  kind: string
  schemaVersion: number
  config: JsonObject
}

export type WorkItemFieldConfig = {
  schemaVersion: number
  required: boolean
  defaultValue: unknown
  validationRules: WorkItemFieldValidationRule[]
  typeConfig: JsonObject
}

export type WorkItemFieldOption = {
  id?: string
  optionKey: string
  name: string
  color: string
  sortOrder: number
  status: 'active' | 'disabled'
  createdBy?: string
  createdAt?: string
  updatedBy?: string
  updatedAt?: string
}

export type WorkItemFieldTypeDescriptor = {
  key: WorkItemFieldType
  storageKind: string
  configSchemaVersion: number
  operators: string[]
  filterable: boolean
  sortable: boolean
  indexCapability: string
  supportsOptions: boolean
  validationRuleKinds: string[]
  valueSchema: JsonObject
  typeConfigSchema: JsonObject
  referencePolicy: string
  invalidReferencePolicy: string
  configSchema: JsonObject
  defaultConfig: WorkItemFieldConfig
}

export type WorkItemFieldTypeCatalog = {
  items: WorkItemFieldTypeDescriptor[]
}

export type ConfiguredWorkItemField = {
  id: string
  spaceId: string
  typeDefinitionId: string
  fieldKey: string
  name: string
  description: string
  fieldType: WorkItemFieldType
  config: WorkItemFieldConfig
  configHash: string
  sortOrder: number
  status: WorkItemFieldStatus
  system: boolean
  aggregateVersion: number
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
  options: WorkItemFieldOption[]
  availableActions: WorkItemFieldAction[]
}

export type WorkItemFieldCollection = {
  spaceId: string
  typeDefinitionId: string
  spaceStatus: string
  availableActions: WorkItemFieldAction[]
  items: ConfiguredWorkItemField[]
}

export type CreateWorkItemFieldRequest = {
  fieldKey: string
  name: string
  description: string
  fieldType: WorkItemFieldType
  config: WorkItemFieldConfig
  sortOrder: number
}

export type ConfigureWorkItemFieldRequest = WorkItemFieldConfig & {
  options: Array<Pick<WorkItemFieldOption, 'optionKey' | 'name' | 'color' | 'sortOrder' | 'status'>>
  aggregateVersion: number
}

export const workItemFieldKeys = {
  all: ['project-spaces', 'work-item-fields'] as const,
  catalog: (spaceId: string) => [...workItemFieldKeys.all, spaceId, 'catalog'] as const,
  configuration: (spaceId: string, typeId: string, status = 'all') =>
    [...workItemFieldKeys.all, spaceId, typeId, 'configuration', status] as const,
  detail: (spaceId: string, typeId: string, fieldId: string) =>
    [...workItemFieldKeys.all, spaceId, typeId, 'detail', fieldId] as const,
}

export function listWorkItemFieldTypes(spaceId: string) {
  return apiGet<WorkItemFieldTypeCatalog>(`/project-spaces/${spaceId}/configuration/field-types`)
}

export function listConfiguredWorkItemFields(
  spaceId: string,
  typeId: string,
  status?: WorkItemFieldStatus,
) {
  const query = status ? `?${new URLSearchParams({ status })}` : ''
  return apiGet<WorkItemFieldCollection>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/fields${query}`,
  )
}

export function getConfiguredWorkItemField(spaceId: string, typeId: string, fieldId: string) {
  return apiGet<ConfiguredWorkItemField>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/fields/${fieldId}`,
  )
}

export function createWorkItemField(spaceId: string, typeId: string, request: CreateWorkItemFieldRequest) {
  return apiPost<ConfiguredWorkItemField>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/fields`,
    request,
  )
}

export function updateWorkItemField(
  spaceId: string,
  typeId: string,
  fieldId: string,
  request: Pick<ConfiguredWorkItemField, 'name' | 'description' | 'config' | 'aggregateVersion'>,
) {
  return apiPatch<ConfiguredWorkItemField>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/fields/${fieldId}`,
    request,
  )
}

export function configureWorkItemField(
  spaceId: string,
  typeId: string,
  fieldId: string,
  request: ConfigureWorkItemFieldRequest,
) {
  return apiPut<ConfiguredWorkItemField>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/fields/${fieldId}/configuration`,
    request,
  )
}

export function reorderWorkItemFields(
  spaceId: string,
  typeId: string,
  items: Array<{ fieldId: string; sortOrder: number; aggregateVersion: number }>,
) {
  return apiPut<WorkItemFieldCollection>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/fields:reorder`,
    { items },
  )
}

export function transitionWorkItemField(
  spaceId: string,
  typeId: string,
  fieldId: string,
  action: 'disable' | 'restore' | 'retire',
  aggregateVersion: number,
) {
  return apiPost<ConfiguredWorkItemField>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/fields/${fieldId}:${action}`,
    { aggregateVersion },
  )
}
