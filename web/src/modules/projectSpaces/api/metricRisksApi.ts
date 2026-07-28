import { apiGet, apiPost } from '../../../shared/api/httpClient'

export type RiskPolicy = {
  id: string
  policyKey: string
  name: string
  description: string
  status: 'draft' | 'active' | 'disabled' | 'archived'
  draftSignalTypes: string[]
  draftSeverity: 'info' | 'warning' | 'critical'
  draftCooldownHours: number
  version: number
  updatedAt: string
  publishedVersion?: {
    id: string
    versionNumber: number
    definitionHash: string
    signalTypes: string[]
    severity: string
    cooldownHours: number
    publishedAt: string
  }
}

export type RiskEvidence = {
  sourceType: string
  sourceIdentity: string
  sourceVersion: number
  observedAt: string
  explanation: string
  available: boolean
}

export type RiskSignal = {
  id: string
  policyId: string
  policyVersion: number
  signalType: string
  severity: 'info' | 'warning' | 'critical'
  state: 'open' | 'acknowledged' | 'suppressed' | 'closed' | 'invalidated'
  evidenceFingerprint: string
  evidence: RiskEvidence[]
  version: number
  resolutionReason: string
  observedAt: string
  updatedAt: string
}

export type RiskFoundation = {
  schemaVersion: number
  policies: RiskPolicy[]
  signals: RiskSignal[]
  signalTypes: string[]
  severities: string[]
  states: string[]
  truncated: boolean
  budgets: Record<string, number>
  diagnostic: string
}

export const metricRiskKeys = {
  foundation: (spaceId: string) => ['project-spaces', spaceId, 'metric-risks'] as const,
}

export function getRiskFoundation(spaceId: string) {
  return apiGet<RiskFoundation>(`/project-spaces/${spaceId}/metric-risks`)
}

export function saveRiskPolicy(
  spaceId: string,
  input: {
    policyId?: string
    expectedVersion: number
    policyKey: string
    name: string
    description: string
    signalTypes: string[]
    severity: string
    cooldownHours: number
  },
) {
  const requestId = crypto.randomUUID()
  return apiPost<RiskPolicy>(
    `/project-spaces/${spaceId}/metric-risks/policies`,
    { schemaVersion: 1, requestId, ...input },
    { requestId },
  )
}

export function publishRiskPolicy(spaceId: string, policy: RiskPolicy) {
  const requestId = crypto.randomUUID()
  return apiPost<RiskPolicy['publishedVersion']>(
    `/project-spaces/${spaceId}/metric-risks/policies/${policy.id}/publish`,
    { schemaVersion: 1, requestId, expectedVersion: policy.version, action: 'publish' },
    { requestId },
  )
}

export function evaluateRisks(spaceId: string) {
  const requestId = crypto.randomUUID()
  return apiPost<RiskSignal[]>(
    `/project-spaces/${spaceId}/metric-risks/evaluate`,
    { schemaVersion: 1, requestId, anchor: new Date().toISOString() },
    { requestId },
  )
}

export function actOnRiskSignal(
  spaceId: string,
  signal: RiskSignal,
  action: 'acknowledge' | 'close' | 'suppress' | 'reopen' | 'invalidate',
  reason: string,
) {
  const requestId = crypto.randomUUID()
  return apiPost<RiskSignal>(
    `/project-spaces/${spaceId}/metric-risks/signals/${signal.id}/actions`,
    {
      schemaVersion: 1,
      requestId,
      expectedVersion: signal.version,
      action,
      reason,
    },
    { requestId },
  )
}
