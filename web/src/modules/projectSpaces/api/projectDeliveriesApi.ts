import { apiGet, apiPost } from '../../../shared/api/httpClient'

export type ProjectDeliverableSummary = {
  id: string
  title: string
  summary: string
  status: string
  ownerUserId?: string | null
  dueDate?: string | null
  planId?: string | null
  milestoneId?: string | null
  registerEntryIds: string[]
  currentVersionId?: string | null
  version: number
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
}

export type ProjectDeliverable = {
  deliverable: ProjectDeliverableSummary
  versions: Array<{
    id: string
    sequence: number
    label: string
    note: string
    submittedBy?: string | null
    submittedAt: string
    materials: Array<{
      id: string
      sourceType: string
      sourceId?: string | null
      sourceVersion: number
      externalUri?: string | null
    }>
  }>
  reviews: Array<{
    id: string
    round: number
    deliverableVersionId: string
    reviewItems: string[]
    requiredSignerIds: string[]
    quorum: number
    status: string
    conclusion: string
    signoffs: Array<{
      sequence: number
      signerId: string
      conclusion: string
      comment: string
      revoked: boolean
      occurredAt: string
    }>
    openedAt: string
    closedAt?: string | null
  }>
  acceptances: Array<{
    sequence: number
    conclusion: string
    comment: string
    actorId: string
    reviewId: string
    occurredAt: string
  }>
  materialsTruncated: boolean
}

export const projectDeliveryKeys = {
  all: ['project-spaces', 'deliverables'] as const,
  list: (spaceId: string) => [...projectDeliveryKeys.all, spaceId, 'list'] as const,
  detail: (spaceId: string, id: string) =>
    [...projectDeliveryKeys.all, spaceId, 'detail', id] as const,
}

export function listProjectDeliverables(spaceId: string) {
  return apiGet<ProjectDeliverableSummary[]>(`/project-spaces/${spaceId}/deliverables`)
}

export function getProjectDeliverable(spaceId: string, id: string) {
  return apiGet<ProjectDeliverable>(`/project-spaces/${spaceId}/deliverables/${id}`)
}

export function createProjectDeliverable(
  spaceId: string,
  input: { title: string; summary: string; dueDate?: string },
) {
  const requestId = crypto.randomUUID()
  return apiPost<ProjectDeliverable>(
    `/project-spaces/${spaceId}/deliverables`,
    {
      schemaVersion: 1,
      requestId,
      ...input,
      ownerUserId: null,
      planId: null,
      milestoneId: null,
      registerEntryIds: [],
    },
    { requestId },
  )
}

export function mutateProjectDeliverable(
  spaceId: string,
  current: ProjectDeliverable,
  operation: string,
  input?: {
    signerIds?: string[]
    conclusion?: string
    comment?: string
    versionLabel?: string
  },
) {
  const requestId = crypto.randomUUID()
  const review = current.reviews[0]
  return apiPost<ProjectDeliverable>(
    `/project-spaces/${spaceId}/deliverables/${current.deliverable.id}:mutate`,
    {
      schemaVersion: 1,
      requestId,
      expectedVersion: current.deliverable.version,
      operation,
      reason: `member_${operation}`,
      title: current.deliverable.title,
      summary: current.deliverable.summary,
      ownerUserId: current.deliverable.ownerUserId ?? null,
      dueDate: current.deliverable.dueDate ?? null,
      versionLabel: input?.versionLabel ?? `v${current.versions.length + 1}`,
      versionNote: operation === 'submit_version' ? '通过 Web 提交不可变版本' : '',
      materials: operation === 'submit_version'
        ? [{
            id: crypto.randomUUID(),
            sourceType: 'external',
            sourceId: null,
            externalUri: 'https://example.com/delivery-evidence',
          }]
        : [],
      reviewItems: operation === 'open_review' || operation === 'reopen_review'
        ? ['范围符合计划', '材料完整', '验收条件可验证']
        : (review?.reviewItems ?? []),
      requiredSignerIds: operation === 'open_review' || operation === 'reopen_review'
        ? (input?.signerIds ?? [])
        : (review?.requiredSignerIds ?? []),
      quorum: operation === 'open_review' || operation === 'reopen_review'
        ? Math.max(1, input?.signerIds?.length ?? 0)
        : (review?.quorum ?? 0),
      conclusion: input?.conclusion ?? '',
      comment: input?.comment ?? `Web ${operation}`,
    },
    { requestId },
  )
}
