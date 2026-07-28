import { apiGet, apiPost } from '../../../shared/api/httpClient'

export type GovernanceHealth = {
  component: string
  status: 'healthy' | 'attention' | 'unknown'
  visibleCount: number
  sourceVersion: string
  truncated: boolean
  explanation: string
}

export type GovernanceOverview = {
  schemaVersion: number
  health: GovernanceHealth[]
  openRisks: number
  publishedMetrics: number
  activeDashboards: number
  truncated: boolean
  observedAt: string
  diagnostic: string
}

export type AuditReport = {
  id: string
  reportKey: string
  name: string
  description: string
  sections: string[]
  status: string
  version: number
  updatedAt: string
}

export type ReportRun = {
  id: string
  reportId: string
  reportVersion: number
  status: string
  result: GovernanceOverview
  sourceFingerprint: string
  startedAt: string
  completedAt: string
}

export type GovernanceFoundation = {
  overview: GovernanceOverview
  reports: AuditReport[]
  runs: ReportRun[]
  budgets: Record<string, number>
}

export const governanceKeys = {
  foundation: (spaceId: string) => ['project-spaces', spaceId, 'metric-governance'] as const,
}

export function getGovernanceFoundation(spaceId: string) {
  return apiGet<GovernanceFoundation>(`/project-spaces/${spaceId}/metric-governance`)
}

export function saveGovernanceReport(
  spaceId: string,
  input: {
    reportId?: string
    expectedVersion: number
    reportKey: string
    name: string
    description: string
    sections: string[]
  },
) {
  const requestId = crypto.randomUUID()
  return apiPost<AuditReport>(
    `/project-spaces/${spaceId}/metric-governance/reports`,
    { schemaVersion: 1, requestId, ...input },
    { requestId },
  )
}

export function runGovernanceReport(spaceId: string, report: AuditReport) {
  const requestId = crypto.randomUUID()
  return apiPost<ReportRun>(
    `/project-spaces/${spaceId}/metric-governance/reports/${report.id}/runs`,
    { schemaVersion: 1, requestId, expectedVersion: report.version },
    { requestId },
  )
}

export function exportGovernanceRun(spaceId: string, runId: string, format: 'csv' | 'json') {
  const requestId = crypto.randomUUID()
  return apiPost<{
    id: string
    rowCount: number
    truncated: boolean
    contentHash: string
  }>(
    `/project-spaces/${spaceId}/metric-governance/runs/${runId}/exports`,
    { schemaVersion: 1, requestId, format },
    { requestId },
  )
}
