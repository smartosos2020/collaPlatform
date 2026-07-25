import { apiGet, apiPost, apiPut } from '../../../shared/api/httpClient'

export type ConfigurationDiagnostic = {
  code: string
  severity: 'warning' | 'error'
  keyPath: string
  message: string
}

export type WorkItemConfigurationDraft = {
  id: string
  spaceId: string
  typeDefinitionId: string
  status: 'editing' | 'validating' | 'valid' | 'invalid' | 'abandoned'
  snapshotSchemaVersion: number
  configHash: string
  snapshot: unknown
  diagnostics: ConfigurationDiagnostic[]
  aggregateVersion: number
  sourceLegacyVersionId?: string | null
  updatedBy: string
  updatedAt: string
  availableActions: Array<'save' | 'validate' | 'abandon'>
}

export const workItemConfigurationDraftKeys = {
  all: ['work-item-configuration-drafts'] as const,
  detail: (spaceId: string, typeId: string) =>
    [...workItemConfigurationDraftKeys.all, spaceId, typeId] as const,
}

export function getWorkItemConfigurationDraft(spaceId: string, typeId: string) {
  return apiGet<WorkItemConfigurationDraft>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/draft`,
  )
}

export function saveWorkItemConfigurationDraft(
  spaceId: string,
  typeId: string,
  snapshot: unknown,
  expectedAggregateVersion: number,
) {
  return apiPut<WorkItemConfigurationDraft>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/draft`,
    { snapshot, expectedAggregateVersion },
  )
}

export function validateWorkItemConfigurationDraft(
  spaceId: string,
  typeId: string,
  expectedAggregateVersion: number,
) {
  return apiPost<WorkItemConfigurationDraft>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/draft:validate`,
    { expectedAggregateVersion },
  )
}

export function abandonWorkItemConfigurationDraft(
  spaceId: string,
  typeId: string,
  expectedAggregateVersion: number,
) {
  return apiPost<WorkItemConfigurationDraft>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/draft:abandon`,
    { expectedAggregateVersion },
  )
}
