import { apiGet, apiPost } from '../../../shared/api/httpClient'

import type { ProjectDeliverableSummary } from './projectDeliveriesApi'
import type { ProjectPlanSummary } from './projectPlansApi'
import type { ProjectRegisterSummary } from './projectRegisterApi'

export type ProjectDetailPreference = {
  schemaVersion: number
  visibleSections: Array<'plan' | 'register' | 'delivery' | 'health'>
  compact: boolean
  version: number
  updatedAt: string
}

export type ProjectHealthSignal = {
  code: string
  severity: 'attention' | 'critical'
  sourceType: string
  sourceId: string
  sourceVersion: number
  rule: string
  explanation: string
  observedAt: string
}

export type ProjectDetail = {
  plans: ProjectPlanSummary[]
  registerEntries: ProjectRegisterSummary[]
  deliverables: ProjectDeliverableSummary[]
  deviations: Array<{
    planId: string
    planVersion: number
    completionPercent: number
    overdueMilestones: number
    visibleMilestones: number
  }>
  blocking: {
    openIssues: number
    highRisks: number
    pendingChanges: number
    pendingAcceptances: number
    rejectedDeliverables: number
  }
  health: {
    status: 'healthy' | 'attention' | 'critical' | 'unknown'
    signals: ProjectHealthSignal[]
    truncated: boolean
    policyVersion: string
    derivedAt: string
  }
  preference: ProjectDetailPreference
}

export const projectDetailKeys = {
  all: ['project-spaces', 'project-detail'] as const,
  detail: (spaceId: string) => [...projectDetailKeys.all, spaceId] as const,
}

export function getProjectDetail(spaceId: string) {
  return apiGet<ProjectDetail>(`/project-spaces/${spaceId}/project-detail`)
}

export function saveProjectDetailPreference(
  spaceId: string,
  preference: ProjectDetailPreference,
  visibleSections: ProjectDetailPreference['visibleSections'],
  compact: boolean,
) {
  const requestId = crypto.randomUUID()
  return apiPost<ProjectDetailPreference>(
    `/project-spaces/${spaceId}/project-detail/preference`,
    {
      schemaVersion: 1,
      requestId,
      expectedVersion: preference.version,
      visibleSections,
      compact,
    },
    { requestId },
  )
}
