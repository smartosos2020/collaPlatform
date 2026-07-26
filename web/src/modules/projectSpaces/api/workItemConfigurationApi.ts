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

export type ConfigurationTemplateVersionSummary = {
  id: string
  versionNumber: number
  snapshotSchemaVersion: number
  configHash: string
  sourceCatalogVersion?: string | null
}

export type ConfigurationTemplate = {
  id: string
  scope: 'platform' | 'workspace'
  templateKey: string
  name: string
  description: string
  status: 'active' | 'withdrawn'
  currentVersion: ConfigurationTemplateVersionSummary
  sourceKind: 'platform_catalog' | 'workspace_snapshot'
  availableActions: Array<'install' | 'create_version' | 'withdraw'>
}

export type ConfigurationTemplateInstallation = {
  id: string
  templateId: string
  installedVersionId: string
  upstreamVersionId: string
  status: 'attached' | 'detached'
  lastLineageSummary: Record<string, unknown>
  aggregateVersion: number
  updatedAt: string
  availableActions: Array<'preview_upgrade' | 'apply_upgrade' | 'detach'>
}

export type TemplateMergeConflict = {
  keyPath: string
  baseValue: unknown
  upstreamValue: unknown
  localValue: unknown
  reason: 'delete_or_modify' | 'concurrent_change'
}

export type TemplateUpgradePreview = {
  installationId: string
  templateId: string
  baseVersionId: string
  upstreamVersionId: string
  baseHash: string
  upstreamHash: string
  localHash: string
  mergedHash: string
  mergedSnapshot: unknown
  conflicts: TemplateMergeConflict[]
  summary: Record<string, number>
  upgradeAvailable: boolean
}

export type TemplateCommandResult = {
  installation: ConfigurationTemplateInstallation
  draft: {
    id: string
    aggregateVersion: number
    status: WorkItemConfigurationDraft['status']
    configHash: string
  }
  mergeSummary: Record<string, number>
  replayed: boolean
}

export const workItemConfigurationVersionKeys = {
  all: ['work-item-configuration-versions'] as const,
  list: (spaceId: string, typeId: string) =>
    [...workItemConfigurationVersionKeys.all, spaceId, typeId] as const,
  draftDiff: (spaceId: string, typeId: string, hash: string) =>
    [...workItemConfigurationVersionKeys.list(spaceId, typeId), 'draft-diff', hash] as const,
}

export const workItemConfigurationTemplateKeys = {
  all: ['work-item-configuration-templates'] as const,
  catalog: (spaceId: string) =>
    [...workItemConfigurationTemplateKeys.all, 'catalog', spaceId] as const,
  installation: (spaceId: string, typeId: string) =>
    [...workItemConfigurationTemplateKeys.all, 'installation', spaceId, typeId] as const,
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

export function listWorkItemConfigurationTemplates(spaceId: string) {
  return apiGet<ConfigurationTemplate[]>(
    `/project-spaces/${spaceId}/configuration/templates`,
  )
}

export function getWorkItemConfigurationTemplateInstallation(
  spaceId: string,
  typeId: string,
) {
  return apiGet<ConfigurationTemplateInstallation | null>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/template-installation`,
  )
}

export function createWorkItemConfigurationTemplate(
  spaceId: string,
  typeId: string,
  versionId: string,
  input: { templateKey: string; name: string; description: string },
) {
  return apiPost<ConfigurationTemplate>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/versions/${versionId}:create-template`,
    input,
  )
}

export function installWorkItemConfigurationTemplate(
  spaceId: string,
  typeId: string,
  templateId: string,
  templateVersionId: string,
  expectedDraftAggregateVersion: number,
) {
  return apiPost<TemplateCommandResult>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/template-installation`,
    { templateId, templateVersionId, expectedDraftAggregateVersion },
  )
}

export function previewWorkItemConfigurationTemplateUpgrade(
  spaceId: string,
  typeId: string,
  targetTemplateVersionId?: string,
  resolutions: Record<string, 'local' | 'upstream'> = {},
) {
  return apiPost<TemplateUpgradePreview>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/template-installation:preview-upgrade`,
    { targetTemplateVersionId, resolutions },
  )
}

export function applyWorkItemConfigurationTemplateUpgrade(
  spaceId: string,
  typeId: string,
  targetTemplateVersionId: string,
  expectedDraftAggregateVersion: number,
  expectedInstallationAggregateVersion: number,
  resolutions: Record<string, 'local' | 'upstream'>,
) {
  return apiPost<TemplateCommandResult>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/template-installation:apply-upgrade`,
    {
      targetTemplateVersionId,
      expectedDraftAggregateVersion,
      expectedInstallationAggregateVersion,
      resolutions,
    },
  )
}

export function detachWorkItemConfigurationTemplate(
  spaceId: string,
  typeId: string,
  expectedInstallationAggregateVersion: number,
) {
  return apiPost<TemplateCommandResult>(
    `/project-spaces/${spaceId}/configuration/types/${typeId}/template-installation:detach`,
    { expectedInstallationAggregateVersion },
  )
}
