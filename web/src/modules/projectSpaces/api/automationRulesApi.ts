import { apiGet, apiPost } from '../../../shared/api/httpClient'

export type EventCatalogEntry = {
  eventType: string
  eventVersion: number
  allowedFields: string[]
}

export type ActionCatalogEntry = {
  actionType: string
  actionVersion: number
  sideEffecting: boolean
  owner: string
}

export type AutomationRule = {
  id: string
  name: string
  status: 'draft' | 'enabled' | 'disabled' | 'archived'
  trigger: Record<string, unknown>
  condition: Record<string, unknown>
  actions: Array<Record<string, unknown>>
  version: number
  publishedVersion?: number
  updatedBy: string
  updatedAt: string
}

export type AutomationFoundation = {
  schemaVersion: number
  events: EventCatalogEntry[]
  actions: ActionCatalogEntry[]
  rules: AutomationRule[]
  truncated: boolean
}

export type RuleVersion = {
  id: string
  ruleId: string
  versionNumber: number
  definitionHash: string
  definition: Record<string, unknown>
  publishedBy: string
  publishedAt: string
}

export type AutomationStep = {
  id: string
  stepNumber: number
  actionType: string
  status: string
  result: Record<string, unknown>
  errorCode?: string
  startedAt: string
  completedAt?: string
}

export type AutomationRun = {
  id: string
  ruleId: string
  ruleVersion: number
  sourceType: string
  sourceKey: string
  actorId: string
  status: string
  dryRun: boolean
  steps: AutomationStep[]
  errorCode?: string
  fencingToken: number
  attempt: number
  startedAt: string
  completedAt?: string
}

export type ExecutionFoundation = {
  runs: AutomationRun[]
  truncated: boolean
}
export type AutomationConnector = {
  id: string; name: string; targetUri: string; credentialReference?: string
  status: string; signingVersion: number; version: number; updatedAt: string
}
export type ConnectorDelivery = {
  id: string; connectorId: string; status: string; attemptCount: number
  deadLetterReason?: string; createdAt: string
}
export type ConnectorFoundation = {
  schemaVersion: number; connectors: AutomationConnector[]; deliveries: ConnectorDelivery[]
  connectorsTruncated: boolean; deliveriesTruncated: boolean; maxPayloadBytes: number
}
export type AutomationQuotaState = {
  quotaType: string; quotaKey: string; windowStart: string; usedCount: number
  limitCount: number; pausedUntil?: string; version: number
}
export type AutomationManagementFoundation = {
  schemaVersion: number; rules: AutomationFoundation; executions: ExecutionFoundation
  connectors: ConnectorFoundation; quotas: AutomationQuotaState[]
  preference: { compactMode: boolean; defaultFilter: string; version: number }
  healthy: boolean; diagnostics: string[]
}

export const automationRuleKeys = {
  all: ['project-spaces', 'automation'] as const,
  detail: (spaceId: string) => [...automationRuleKeys.all, spaceId] as const,
  runs: (spaceId: string) => [...automationRuleKeys.all, spaceId, 'runs'] as const,
  connectors: (spaceId: string) => [...automationRuleKeys.all, spaceId, 'connectors'] as const,
  management: (spaceId: string) => [...automationRuleKeys.all, spaceId, 'management'] as const,
}
export function getAutomationManagement(spaceId: string) {
  return apiGet<AutomationManagementFoundation>(`/project-spaces/${spaceId}/automation/management`)
}
export function saveAutomationManagementPreference(
  spaceId: string, input: { compactMode: boolean; defaultFilter: string; expectedVersion: number },
) {
  const requestId = crypto.randomUUID()
  return apiPost(`/project-spaces/${spaceId}/automation/management/preference`,
    { schemaVersion: 1, requestId, ...input }, { requestId })
}
export function governAutomationQuota(
  spaceId: string, quota: AutomationQuotaState, action: 'pause' | 'resume',
) {
  const requestId = crypto.randomUUID()
  return apiPost<AutomationQuotaState>(`/project-spaces/${spaceId}/automation/management/quota`, {
    schemaVersion: 1, requestId, quotaType: quota.quotaType, quotaKey: quota.quotaKey,
    action, pausedUntil: action === 'pause' ? new Date(Date.now() + 60 * 60 * 1000).toISOString() : undefined,
    reason: `${action} automation quota through management console`, expectedVersion: quota.version,
  }, { requestId })
}
export function getAutomationConnectors(spaceId: string) {
  return apiGet<ConnectorFoundation>(`/project-spaces/${spaceId}/automation/connectors`)
}
export function saveAutomationConnector(
  spaceId: string,
  input: { connectorId?: string; expectedVersion: number; name: string; targetUri: string; credentialReference?: string },
) {
  const requestId = crypto.randomUUID()
  return apiPost<AutomationConnector>(`/project-spaces/${spaceId}/automation/connectors`, {
    schemaVersion: 1, requestId, ...input,
  }, { requestId })
}
export function testAutomationConnector(spaceId: string, connectorId: string, dryRun = true) {
  const requestId = crypto.randomUUID()
  return apiPost<ConnectorDelivery>(
    `/project-spaces/${spaceId}/automation/connectors/${connectorId}/test`,
    { schemaVersion: 1, requestId, payload: '{"schemaVersion":1,"kind":"connector-test"}', dryRun },
    { requestId },
  )
}

export function getAutomationRuns(spaceId: string) {
  return apiGet<ExecutionFoundation>(`/project-spaces/${spaceId}/automation/runs`)
}

export function executeAutomationRule(
  spaceId: string,
  ruleId: string,
  dryRun: boolean,
  event: Record<string, unknown>,
) {
  const requestId = crypto.randomUUID()
  return apiPost<AutomationRun>(
    `/project-spaces/${spaceId}/automation/rules/${ruleId}/execute`,
    { schemaVersion: 1, requestId, dryRun, event },
    { requestId },
  )
}

export function getAutomationFoundation(spaceId: string) {
  return apiGet<AutomationFoundation>(`/project-spaces/${spaceId}/automation`)
}

export function saveAutomationRule(
  spaceId: string,
  input: {
    ruleId?: string
    expectedVersion: number
    name: string
    eventType: string
    conditionReference: string
    conditionOperator: string
    conditionValue: string
    actionType: string
  },
) {
  const requestId = crypto.randomUUID()
  return apiPost<AutomationRule>(
    `/project-spaces/${spaceId}/automation/rules`,
    {
      schemaVersion: 1,
      requestId,
      expectedVersion: input.expectedVersion,
      ruleId: input.ruleId,
      name: input.name,
      trigger: {
        schemaVersion: 1,
        type: 'event',
        eventType: input.eventType,
        eventVersion: 1,
      },
      condition: {
        schemaVersion: 1,
        kind: 'compare',
        reference: input.conditionReference,
        operator: input.conditionOperator,
        value: input.conditionValue,
      },
      actions: [{
        schemaVersion: 1,
        actionType: input.actionType,
        config: {},
      }],
    },
    { requestId },
  )
}

export function publishAutomationRule(spaceId: string, rule: AutomationRule) {
  const requestId = crypto.randomUUID()
  return apiPost<RuleVersion>(
    `/project-spaces/${spaceId}/automation/rules/${rule.id}/publish`,
    {
      schemaVersion: 1,
      requestId,
      expectedVersion: rule.version,
      action: 'publish',
    },
    { requestId },
  )
}

export function changeAutomationRuleLifecycle(
  spaceId: string,
  rule: AutomationRule,
  action: 'enable' | 'disable' | 'archive',
) {
  const requestId = crypto.randomUUID()
  return apiPost<AutomationRule>(
    `/project-spaces/${spaceId}/automation/rules/${rule.id}/lifecycle`,
    {
      schemaVersion: 1,
      requestId,
      expectedVersion: rule.version,
      action,
    },
    { requestId },
  )
}
