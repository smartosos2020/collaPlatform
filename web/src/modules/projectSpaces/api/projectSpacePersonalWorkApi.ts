import { apiGet } from '../../../shared/api/httpClient'

export type ProjectSpacePersonalWorkBucket =
  | 'todo'
  | 'responsible'
  | 'participating'
  | 'watching'

export type ProjectSpacePersonalWorkItem = {
  workItemId: string
  spaceId: string
  spaceName: string
  typeKey: string
  typeName: string
  displayKey: string
  title: string
  lifecycle: string
  version: number
  updatedAt: string
  reasons: Array<{
    bucket: ProjectSpacePersonalWorkBucket
    source: 'node_task' | 'participant'
    sourceState: string
    sourceVersion: number
    dueAt?: string | null
  }>
  capabilities: string[]
  availableActions: string[]
  deepLink: string
}

export type ProjectSpacePersonalWorkPage = {
  buckets: Array<{
    bucket: ProjectSpacePersonalWorkBucket
    visibleCount: number
    items: ProjectSpacePersonalWorkItem[]
  }>
  nextCursor?: string | null
  truncated: boolean
  generatedAt: string
}

export type ProjectSpacePersonalActivity = {
  sequence: number
  workItemId: string
  spaceId: string
  displayKey: string
  title: string
  activityType: string
  sourceVersion: number
  occurredAt: string
  deepLink: string
}

export type ProjectSpacePersonalActivityPage = {
  items: ProjectSpacePersonalActivity[]
  nextBeforeSequence?: number | null
  readThroughSequence: number
  unreadCount: number
  truncated: boolean
  generatedAt: string
}

export function listProjectSpacePersonalWork(spaceId: string, limit = 12) {
  const params = new URLSearchParams({
    spaceId,
    limit: String(limit),
  })
  return apiGet<ProjectSpacePersonalWorkPage>(`/personal-work?${params}`)
}

export function listProjectSpacePersonalActivities(spaceId: string, limit = 12) {
  const params = new URLSearchParams({
    spaceId,
    limit: String(limit),
  })
  return apiGet<ProjectSpacePersonalActivityPage>(`/personal-work/activities?${params}`)
}
