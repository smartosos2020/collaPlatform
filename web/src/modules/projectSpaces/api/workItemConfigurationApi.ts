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
  sourceVersionId?: string | null
  lineageKind: 'live_edit' | 'legacy_import' | 'rollback'
  updatedBy: string
  updatedAt: string
  availableActions: Array<'save' | 'validate' | 'abandon'>
}

export const workItemConfigurationDraftKeys = {
  all: ['work-item-configuration-drafts'] as const,
  detail: (spaceId: string, typeId: string) =>
    [...workItemConfigurationDraftKeys.all, spaceId, typeId] as const,
}

export type ConfigurationVersion = {
  id: string
  versionNumber: number
  status: 'published' | 'superseded'
  snapshotSchemaVersion: number
  completeSnapshot: boolean
  configHash: string
  snapshot: unknown
  sourceDraftId?: string | null
  rollbackSourceVersionId?: string | null
  publishedBy: string
  publishedAt: string
}

export type ConfigurationDiffItem = {
  keyPath: string
  changeType: 'added' | 'removed' | 'changed'
  impact: 'additive' | 'behavioral' | 'conditional' | 'breaking'
  beforeValue?: unknown
  afterValue?: unknown
}

export type ConfigurationDiff = {
  fromHash: string
  toHash: string
  items: ConfigurationDiffItem[]
  summary: Record<string, number>
  breaking: boolean
}

export type ConfigurationPublicationResult = {
  version: ConfigurationVersion
  diff: ConfigurationDiff
  replayed: boolean
}

export type RollbackPreparation = {
  draftId: string
  draftAggregateVersion: number
  draftStatus: WorkItemConfigurationDraft['status']
  sourceVersionId: string
  sourceVersionNumber: number
  sourceConfigHash: string
}

export const workItemConfigurationVersionKeys = {
  all: ['work-item-configuration-versions'] as const,
  list: (spaceId: string, typeId: string) =>
    [...workItemConfigurationVersionKeys.all, spaceId, typeId] as const,
  draftDiff: (spaceId: string, typeId: string, hash: string) =>
    [...workItemConfigurationVersionKeys.list(spaceId, typeId), 'draft-diff', hash] as const,
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

export function listWorkItemConfigurationVersions(spaceId: string, typeId: string) {
  return apiGet<ConfigurationVersion[]>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/versions`,
  )
}

export function getWorkItemConfigurationDraftDiff(spaceId: string, typeId: string) {
  return apiGet<ConfigurationDiff>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/draft:diff`,
  )
}

export function publishWorkItemConfigurationDraft(
  spaceId: string,
  typeId: string,
  expectedDraftAggregateVersion: number,
  breakingConfirmed: boolean,
) {
  return apiPost<ConfigurationPublicationResult>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/draft:publish`,
    { expectedDraftAggregateVersion, breakingConfirmed },
  )
}

export function prepareWorkItemConfigurationRollback(
  spaceId: string,
  typeId: string,
  versionId: string,
  expectedDraftAggregateVersion: number,
) {
  return apiPost<RollbackPreparation>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/versions/${versionId}:prepare-rollback`,
    { expectedDraftAggregateVersion },
  )
}
