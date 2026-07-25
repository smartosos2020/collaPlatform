import { apiGet, apiPost, apiPut } from '../../../shared/api/httpClient'

import type { JsonObject } from './workItemFieldsApi'

export type WorkItemLayoutKind = 'create' | 'detail'
export type WorkItemLayoutNodeType = 'section' | 'tab' | 'column' | 'field' | 'summary'

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
