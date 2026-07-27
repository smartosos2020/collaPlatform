import { apiGet, apiPost } from '../../../shared/api/httpClient'

export type ProjectRegisterType = 'risk' | 'issue' | 'decision' | 'change'

export type ProjectRegisterSummary = {
  id: string
  entryType: ProjectRegisterType
  title: string
  summary: string
  status: string
  ownerUserId?: string | null
  dueDate?: string | null
  probability?: number | null
  impact?: number | null
  score: number
  decisionBasis: string
  changeImpact: string
  supersedesEntryId?: string | null
  verification: string
  version: number
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
}

export type ProjectRegisterEntry = {
  entry: ProjectRegisterSummary
  references: Array<{
    id: string
    sourceType: 'work_item' | 'plan'
    sourceId: string
    sourceVersion: number
  }>
  responses: Array<{
    id: string
    responseType: string
    description: string
    ownerUserId?: string | null
    dueDate?: string | null
    status: 'planned' | 'active' | 'completed' | 'cancelled'
  }>
  history: Array<{
    sequence: number
    operation: string
    fromStatus: string
    toStatus: string
    reason: string
    actorId: string
    entryVersion: number
    occurredAt: string
  }>
  referencesTruncated: boolean
}

export const projectRegisterKeys = {
  all: ['project-spaces', 'project-register'] as const,
  list: (spaceId: string, type: string) =>
    [...projectRegisterKeys.all, spaceId, 'list', type] as const,
  detail: (spaceId: string, entryId: string) =>
    [...projectRegisterKeys.all, spaceId, 'detail', entryId] as const,
}

export function listProjectRegister(spaceId: string, type: string) {
  const query = type === 'all' ? '' : `?type=${encodeURIComponent(type)}`
  return apiGet<ProjectRegisterSummary[]>(
    `/project-spaces/${spaceId}/project-register${query}`,
  )
}

export function getProjectRegisterEntry(spaceId: string, entryId: string) {
  return apiGet<ProjectRegisterEntry>(
    `/project-spaces/${spaceId}/project-register/${entryId}`,
  )
}

export function createProjectRegisterEntry(
  spaceId: string,
  input: {
    entryType: ProjectRegisterType
    title: string
    summary: string
    dueDate?: string
  },
) {
  const requestId = crypto.randomUUID()
  return apiPost<ProjectRegisterEntry>(
    `/project-spaces/${spaceId}/project-register`,
    {
      schemaVersion: 1,
      requestId,
      ...input,
      ownerUserId: null,
      probability: input.entryType === 'risk' ? 3 : null,
      impact: input.entryType === 'risk' ? 3 : null,
      decisionBasis: input.entryType === 'decision' ? input.summary || '待评审依据' : '',
      changeImpact: input.entryType === 'change' ? input.summary || '待分析影响' : '',
      references: [],
      responses: input.entryType === 'risk' || input.entryType === 'issue'
        ? [{
            id: crypto.randomUUID(),
            responseType: input.entryType === 'risk' ? 'mitigate' : 'resolve',
            description: '明确责任、到期和验证条件',
            ownerUserId: null,
            dueDate: input.dueDate || null,
            status: 'planned',
          }]
        : [],
    },
    { requestId },
  )
}

export function mutateProjectRegisterEntry(
  spaceId: string,
  current: ProjectRegisterEntry,
  operation: string,
  reason: string,
) {
  const requestId = crypto.randomUUID()
  return apiPost<ProjectRegisterEntry>(
    `/project-spaces/${spaceId}/project-register/${current.entry.id}:mutate`,
    {
      schemaVersion: 1,
      requestId,
      expectedVersion: current.entry.version,
      operation,
      reason,
      title: current.entry.title,
      summary: current.entry.summary,
      ownerUserId: current.entry.ownerUserId ?? null,
      dueDate: current.entry.dueDate ?? null,
      probability: current.entry.probability ?? null,
      impact: current.entry.impact ?? null,
      decisionBasis: current.entry.decisionBasis,
      changeImpact: current.entry.changeImpact,
      supersedesEntryId: current.entry.supersedesEntryId ?? null,
      verification: operation === 'verify' ? '负责人已验证处置结果' : current.entry.verification,
      references: current.references.map(({ id, sourceType, sourceId }) => ({
        id,
        sourceType,
        sourceId,
      })),
      responses: current.responses,
      planAction: null,
    },
    { requestId },
  )
}
