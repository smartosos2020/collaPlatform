import { apiGet, apiPost, apiPut } from '../../../shared/api/httpClient'

import type {
  JsonObject,
  WorkItemFieldCollection,
  WorkItemFieldConfig,
  WorkItemFieldOption,
  WorkItemFieldType,
  WorkItemFieldTypeCatalog,
} from './workItemFieldsApi'
import type { ConfiguredWorkItemType } from './workItemTypesApi'

export type WorkItemLayoutKind = 'create' | 'detail'
export type WorkItemLayoutNodeType = 'section' | 'tab' | 'column' | 'field' | 'relation' | 'summary'

export type WorkItemLayoutCondition = {
  schemaVersion: 1
  expression?: JsonObject
}

export type WorkItemLayoutNode = {
  id: string
  parentId: string | null
  nodeKey: string
  nodeType: WorkItemLayoutNodeType
  fieldId: string | null
  fieldKey: string | null
  sortOrder: number
  config: JsonObject
  visibilityCondition: WorkItemLayoutCondition
}

export type WorkItemFieldAccessPolicy = {
  id: string
  fieldId: string
  fieldKey: string
  policyKey: string
  policy: JsonObject
  configHash: string
}

export type WorkItemFieldAccessProjection = {
  mode: 'hidden' | 'read' | 'write'
  required: boolean
  reasonCode?: string
  matchedRuleKeys?: string[]
  explanation?: Array<{
    source: string
    mode: 'hidden' | 'read' | 'write'
    reasonCode: string
  }>
}

export type WorkItemFieldAccessMode = WorkItemFieldAccessProjection['mode']
export type WorkItemFieldAccessRole =
  | 'owner'
  | 'admin'
  | 'member'
  | 'guest'
  | 'non_member'
  | 'enterprise_admin'

export type WorkItemFieldAccessPolicyRule = {
  ruleKey: string
  roles: WorkItemFieldAccessRole[]
  mode: WorkItemFieldAccessMode
  required: boolean
  when?: JsonObject
}

export type WorkItemFieldAccessPolicyDocument = {
  schemaVersion: 1
  default: {
    mode: WorkItemFieldAccessMode
    required: boolean
  }
  rules: WorkItemFieldAccessPolicyRule[]
}

export type WorkItemLayoutDiagnostic = {
  code: string
  nodeKey?: string | null
  fieldKey?: string | null
  message: string
}

export type WorkItemLayout = {
  id: string
  spaceId: string
  typeDefinitionId: string
  layoutKind: WorkItemLayoutKind
  configHash: string
  status: string
  aggregateVersion: number
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
  nodes: WorkItemLayoutNode[]
  policies: WorkItemFieldAccessPolicy[]
  diagnostics: WorkItemLayoutDiagnostic[]
  availableActions: string[]
}

export type SaveWorkItemLayoutRequest = {
  nodes: WorkItemLayoutNode[]
  policies: Array<Omit<WorkItemFieldAccessPolicy, 'configHash'>>
  aggregateVersion: number
}

export type SaveWorkItemLayoutPoliciesRequest = {
  policies: Array<Omit<WorkItemFieldAccessPolicy, 'configHash'>>
  aggregateVersion: number
}

export type WorkItemLayoutProjectionField = {
  id: string
  fieldKey: string
  name: string
  description: string
  fieldType: WorkItemFieldType
  config: WorkItemFieldConfig
  status: string
  system: boolean
  options: Array<WorkItemFieldOption>
}

export type WorkItemLayoutProjection = {
  id: string
  spaceId: string
  typeDefinitionId: string
  layoutKind: WorkItemLayoutKind
  configHash: string
  aggregateVersion: number
  synthetic: boolean
  context: {
    role: WorkItemFieldAccessRole
    spaceStatus: 'active' | 'disabled' | 'archived'
    typeStatus: 'active' | 'disabled' | 'retired'
    mode: 'runtime' | 'synthetic'
  }
  nodes: WorkItemLayoutNode[]
  fields: WorkItemLayoutProjectionField[]
  accessProjection: Record<string, WorkItemFieldAccessProjection>
  diagnostics: WorkItemLayoutDiagnostic[]
  availableActions: string[]
}

export type WorkItemLayoutWorkbench = {
  type: ConfiguredWorkItemType
  fields: WorkItemFieldCollection
  fieldTypes: WorkItemFieldTypeCatalog
  layouts: Record<WorkItemLayoutKind, {
    configuration: WorkItemLayout
    runtimeProjection: WorkItemLayoutProjection
  } | null>
}

export type WorkItemLayoutSyntheticPreviewRequest = {
  role: WorkItemFieldAccessRole
  spaceStatus: 'active' | 'disabled' | 'archived'
  typeStatus: 'active' | 'disabled' | 'retired'
  fieldValues: JsonObject
  fieldStatuses: Record<string, 'active' | 'disabled' | 'retired'>
}

export type WorkItemLayoutNodeCommand = {
  operation: 'add' | 'copy' | 'move' | 'reorder' | 'update' | 'delete'
  nodeId?: string
  parentId?: string | null
  targetSortOrder: number
  node?: WorkItemLayoutNode
  confirmReferences?: boolean
  aggregateVersion: number
}

export const workItemLayoutKeys = {
  all: ['workspace', 'project-spaces', 'work-item-layouts'] as const,
  detail: (spaceId: string, typeId: string, layoutKind: WorkItemLayoutKind) =>
    [...workItemLayoutKeys.all, spaceId, typeId, layoutKind] as const,
  workbench: (spaceId: string, typeId: string) =>
    [...workItemLayoutKeys.all, spaceId, typeId, 'workbench'] as const,
}

export function getWorkItemLayoutWorkbench(spaceId: string, typeId: string) {
  return apiGet<WorkItemLayoutWorkbench>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/layout-workbench`,
  )
}

export function getWorkItemLayout(spaceId: string, typeId: string, layoutKind: WorkItemLayoutKind) {
  return apiGet<WorkItemLayout>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/layouts/${layoutKind}`,
  )
}

export function saveWorkItemLayout(
  spaceId: string,
  typeId: string,
  layoutKind: WorkItemLayoutKind,
  request: SaveWorkItemLayoutRequest,
) {
  return apiPut<WorkItemLayout>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/layouts/${layoutKind}`,
    request,
  )
}

export function saveWorkItemLayoutPolicies(
  spaceId: string,
  typeId: string,
  layoutKind: WorkItemLayoutKind,
  request: SaveWorkItemLayoutPoliciesRequest,
) {
  return apiPut<WorkItemLayout>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/layouts/${layoutKind}/policies`,
    request,
  )
}

export function previewWorkItemLayout(
  spaceId: string,
  typeId: string,
  layoutKind: WorkItemLayoutKind,
  request: WorkItemLayoutSyntheticPreviewRequest,
) {
  return apiPost<WorkItemLayoutProjection>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/layouts/${layoutKind}/preview`,
    request,
  )
}

export function getWorkItemLayoutProjection(
  spaceId: string,
  typeId: string,
  layoutKind: WorkItemLayoutKind,
) {
  return apiGet<WorkItemLayoutProjection>(
    `/project-spaces/${spaceId}/types/${typeId}/layouts/${layoutKind}/projection`,
  )
}

export function sampleWorkItemLayout(
  spaceId: string,
  typeId: string,
  layoutKind: WorkItemLayoutKind,
  fieldValues: JsonObject,
) {
  return apiPost<WorkItemLayoutProjection>(
    `/project-spaces/${spaceId}/types/${typeId}/layouts/${layoutKind}/sample`,
    { fieldValues },
  )
}

export function commandWorkItemLayoutNode(
  spaceId: string,
  typeId: string,
  layoutKind: WorkItemLayoutKind,
  command: WorkItemLayoutNodeCommand,
) {
  return apiPost<WorkItemLayout>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/layouts/${layoutKind}/nodes:command`,
    command,
  )
}
