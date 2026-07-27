import { apiGet, apiPost } from '../../../shared/api/httpClient'

export type ProjectPlanSummary = {
  id: string
  name: string
  description: string
  startDate: string
  endDate: string
  status: 'draft' | 'published' | 'archived'
  version: number
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
  archivedAt?: string | null
}

export type ProjectPlanPhase = {
  id: string
  phaseKey: string
  name: string
  position: number
  startDate: string
  endDate: string
  status: 'planned' | 'active' | 'completed'
}

export type ProjectPlanMilestone = {
  id: string
  phaseId: string
  milestoneKey: string
  name: string
  position: number
  targetDate: string
  status: 'planned' | 'active' | 'completed'
  ownerUserId?: string | null
}

export type ProjectPlanLink = {
  id: string
  milestoneId: string
  workItemId: string
  sourceWorkItemVersion: number
}

export type ProjectPlan = {
  plan: ProjectPlanSummary
  phases: ProjectPlanPhase[]
  milestones: ProjectPlanMilestone[]
  links: ProjectPlanLink[]
  changes: Array<{
    sequence: number
    operation: 'create' | 'update' | 'publish' | 'archive' | 'restore'
    reason: string
    actorId: string
    planVersion: number
    occurredAt: string
  }>
  progress: {
    visibleMilestones: number
    completedMilestones: number
    visibleLinks: number
    overdueMilestones: number
    completionPercent: number
    truncated: boolean
  }
}

export const projectPlanKeys = {
  all: ['project-spaces', 'project-plans'] as const,
  list: (spaceId: string) => [...projectPlanKeys.all, spaceId, 'list'] as const,
  detail: (spaceId: string, planId: string) =>
    [...projectPlanKeys.all, spaceId, 'detail', planId] as const,
}

export function listProjectPlans(spaceId: string) {
  return apiGet<ProjectPlanSummary[]>(`/project-spaces/${spaceId}/project-plans`)
}

export function getProjectPlan(spaceId: string, planId: string) {
  return apiGet<ProjectPlan>(`/project-spaces/${spaceId}/project-plans/${planId}`)
}

export function createProjectPlan(
  spaceId: string,
  input: { name: string; description: string; startDate: string; endDate: string },
) {
  const requestId = crypto.randomUUID()
  const phaseId = crypto.randomUUID()
  const milestoneId = crypto.randomUUID()
  return apiPost<ProjectPlan>(
    `/project-spaces/${spaceId}/project-plans`,
    {
      schemaVersion: 1,
      requestId,
      ...input,
      phases: [{
        id: phaseId,
        phaseKey: 'delivery',
        name: '交付阶段',
        position: 0,
        startDate: input.startDate,
        endDate: input.endDate,
        status: 'active',
      }],
      milestones: [{
        id: milestoneId,
        phaseId,
        milestoneKey: 'release',
        name: '计划交付',
        position: 0,
        targetDate: input.endDate,
        status: 'planned',
        ownerUserId: null,
      }],
      links: [],
    },
    { requestId },
  )
}

export function mutateProjectPlan(
  spaceId: string,
  current: ProjectPlan,
  operation: 'update' | 'publish' | 'archive' | 'restore',
  input?: { name: string; description: string },
) {
  const requestId = crypto.randomUUID()
  return apiPost<ProjectPlan>(
    `/project-spaces/${spaceId}/project-plans/${current.plan.id}:mutate`,
    {
      schemaVersion: 1,
      requestId,
      expectedVersion: current.plan.version,
      operation,
      reason: operation === 'update' ? 'member_edit' : `member_${operation}`,
      name: input?.name ?? current.plan.name,
      description: input?.description ?? current.plan.description,
      startDate: current.plan.startDate,
      endDate: current.plan.endDate,
      phases: current.phases,
      milestones: current.milestones,
      links: current.links.map(({ id, milestoneId, workItemId }) => ({
        id,
        milestoneId,
        workItemId,
      })),
    },
    { requestId },
  )
}
