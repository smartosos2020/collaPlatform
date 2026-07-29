import { apiGet, apiPost } from '../../../shared/api/httpClient'

export type ScenarioComponent = {
  componentKey: string
  kind: 'work_item_type' | 'relation' | 'saved_view' | 'board' | 'project_plan' | 'workflow' | 'calendar' | 'automation' | 'notification' | 'risk_policy' | 'metric' | 'dashboard'
  ownerContract: string
  configurationTemplateKey: string
  dependencies: string[]
  required: boolean
  description: string
}

export type ScenarioTemplate = {
  id: string
  scenarioKey: string
  name: string
  description: string
  status: 'active' | 'withdrawn'
  currentVersion: {
    id: string
    versionNumber: number
    schemaVersion: number
    manifestHash: string
    manifest: {
      schemaVersion: number
      scenarioKey: string
      components: ScenarioComponent[]
      capabilities: string[]
      prohibitedCapabilities: string[]
    }
    catalogVersion: string
    publishedAt: string
  }
  updatedAt: string
}

export type ScenarioFoundation = {
  schemaVersion: number
  templates: ScenarioTemplate[]
  truncated: boolean
  supportedComponentKinds: string[]
  prohibitedCapabilities: string[]
}

export type ScenarioValidation = {
  valid: boolean
  manifestHash: string
  installationOrder: string[]
  diagnostics: Array<{ code: string; componentKey: string; message: string }>
}

export type ScenarioInstallResult = {
  runId: string
  installationId?: string
  scenarioKey: string
  operation: 'dry_run' | 'install' | 'retry' | 'upgrade' | 'detach'
  status: 'planned' | 'completed' | 'attention'
  baseManifestHash: string
  upstreamManifestHash: string
  localManifestHash: string
  aggregateVersion: number
  replayed: boolean
  steps: Array<{
    id: string
    componentKey: string
    kind: string
    ownerContract: string
    operation: string
    status: 'planned' | 'completed' | 'skipped'
    targetIdentity: string
    targetVersion: string
    diagnosticCode: string
  }>
  conflicts: Array<{
    keyPath: string
    reason: string
    resolved: boolean
    resolution: string
  }>
  completedAt: string
}

export const scenarioTemplateKeys = {
  foundation: (spaceId: string) => ['project-spaces', spaceId, 'scenario-templates'] as const,
  validation: (spaceId: string, scenarioKey: string) => (
    ['project-spaces', spaceId, 'scenario-templates', scenarioKey, 'validation'] as const
  ),
}

export function getScenarioTemplateFoundation(spaceId: string) {
  return apiGet<ScenarioFoundation>(`/project-spaces/${spaceId}/scenario-templates`)
}

export function validateScenarioTemplate(spaceId: string, scenarioKey: string) {
  return apiGet<ScenarioValidation>(
    `/project-spaces/${spaceId}/scenario-templates/${scenarioKey}/validation`,
  )
}

export function getScenarioInstallation(spaceId: string, scenarioKey: string) {
  return apiGet<ScenarioInstallResult | null>(
    `/project-spaces/${spaceId}/scenario-templates/${scenarioKey}/installation`,
  )
}

export function runScenarioCommand(
  spaceId: string,
  scenarioKey: string,
  operation: 'dry-run' | 'install' | 'retry' | 'upgrade' | 'detach',
  input: {
    requestId: string
    localManifestHash?: string
    conflictResolutions?: Record<string, string>
  },
) {
  return apiPost<ScenarioInstallResult>(
    `/project-spaces/${spaceId}/scenario-templates/${scenarioKey}/${operation}`,
    input,
  )
}
