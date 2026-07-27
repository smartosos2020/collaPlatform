import { apiGet, apiPatch, apiPost, apiPut } from '../../../shared/api/httpClient'
import type { JsonObject } from './workItemFieldsApi'
import type { WorkItemFieldAccessRole } from './workItemLayoutsApi'

export type WorkItemRuntime = {
  snapshotSchemaVersion: number
  typeVersionId: string
  configHash: string
  layoutKind: 'create' | 'detail'
  snapshot: {
    typeDefinition: JsonObject
    fields: JsonObject[]
    layouts: JsonObject[]
    relationDefinitions?: JsonObject[]
  }
  accessProjection: Record<string, {
    mode: 'hidden' | 'read' | 'write'
    required: boolean
    reasonCode?: string
    matchedRuleKeys?: string[]
  }>
}

export type WorkItemPermissionExplanation = {
  allowed: boolean
  action: string
  reasonCode: string
  disclosureScope: string
  safePolicySources: string[]
  requestAvailable: boolean
  evaluatedAt: string
}

export type WorkItem = {
  id: string
  spaceId: string
  typeDefinitionId: string
  typeVersionId: string
  typeKey: string
  typeName: string
  configHash: string
  itemNumber: number
  displayKey: string
  title: string
  fieldValues: Record<string, unknown>
  runtime: WorkItemRuntime
  status: 'active' | 'archived'
  version: number
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
  archivedAt?: string | null
  availableActions: Array<'view' | 'edit' | 'archive' | 'restore'>
}

export type WorkItemPage = {
  items: WorkItem[]
  nextCursor?: string | null
}

export type WorkItemCreateForm = {
  typeDefinitionId: string
  typeVersionId: string
  typeKey: string
  typeName: string
  runtime: WorkItemRuntime
}

export type WorkItemParticipant = {
  userId: string
  displayName?: string | null
  role: 'owner' | 'assignee' | 'collaborator' | 'watcher'
  createdAt: string
  updatedAt: string
}

export type WorkItemActivity = {
  sequence: number
  type: string
  actorId: string
  actorDisplayName?: string | null
  payload: JsonObject
  occurredAt: string
}

export type WorkItemComment = {
  id: string
  authorId: string
  authorDisplayName?: string | null
  content: string
  version: number
  createdAt: string
  updatedAt?: string | null
}

export type WorkItemAttachment = {
  id: string
  fileId: string
  fileName: string
  contentType: string
  sizeBytes: number
  createdBy: string
  createdByDisplayName?: string | null
  createdAt: string
}

type WorkItemFileUpload = {
  uploadId: string
  uploadUrl: string
  headers: Record<string, string>
}

type WorkItemFileMetadata = {
  id: string
}

type WorkItemFileDownload = {
  downloadUrl: string
  expiresAt: string
}

export const workItemKeys = {
  all: ['project-spaces', 'work-items'] as const,
  list: (spaceId: string, typeId?: string) => [...workItemKeys.all, spaceId, 'list', typeId ?? 'all'] as const,
  detail: (spaceId: string, workItemId: string) => [...workItemKeys.all, spaceId, workItemId] as const,
  createForm: (spaceId: string, typeId: string) => [...workItemKeys.all, spaceId, typeId, 'create-form'] as const,
  participants: (spaceId: string, workItemId: string) => [...workItemKeys.detail(spaceId, workItemId), 'participants'] as const,
  activities: (spaceId: string, workItemId: string) => [...workItemKeys.detail(spaceId, workItemId), 'activities'] as const,
  comments: (spaceId: string, workItemId: string) => [...workItemKeys.detail(spaceId, workItemId), 'comments'] as const,
  attachments: (spaceId: string, workItemId: string) => [...workItemKeys.detail(spaceId, workItemId), 'attachments'] as const,
}

export function listWorkItems(spaceId: string, typeId?: string) {
  const query = typeId ? `?typeId=${encodeURIComponent(typeId)}` : ''
  return apiGet<WorkItemPage>(`/project-spaces/${spaceId}/work-items${query}`)
}

export function getWorkItem(spaceId: string, workItemId: string) {
  return apiGet<WorkItem>(`/project-spaces/${spaceId}/work-items/${workItemId}`)
}

export function getWorkItemPermissionExplanation(
  spaceId: string,
  workItemId: string,
  action: string,
) {
  const query = new URLSearchParams({ action })
  return apiGet<WorkItemPermissionExplanation>(
    `/project-spaces/${spaceId}/work-items/${workItemId}/permission-explanation?${query}`,
  )
}

export function getWorkItemCreateForm(spaceId: string, typeId: string) {
  return apiGet<WorkItemCreateForm>(`/project-spaces/${spaceId}/work-items/types/${typeId}/create-form`)
}

export function createWorkItem(
  spaceId: string,
  request: { typeId: string; title: string; fieldValues: Record<string, unknown> },
) {
  return apiPost<WorkItem>(`/project-spaces/${spaceId}/work-items`, request)
}

export function updateWorkItem(
  spaceId: string,
  workItemId: string,
  request: { title?: string; fieldValues: Record<string, unknown>; expectedVersion: number },
) {
  return apiPatch<WorkItem>(`/project-spaces/${spaceId}/work-items/${workItemId}`, request)
}

export function transitionWorkItem(
  spaceId: string,
  workItemId: string,
  action: 'archive' | 'restore',
  expectedVersion: number,
) {
  return apiPost<WorkItem>(
    `/project-spaces/${spaceId}/work-items/${workItemId}:${action}`,
    { expectedVersion },
  )
}

export function listWorkItemParticipants(spaceId: string, workItemId: string) {
  return apiGet<{ items: WorkItemParticipant[] }>(
    `/project-spaces/${spaceId}/work-items/${workItemId}/participants`,
  )
}

export function putWorkItemParticipant(
  spaceId: string,
  workItemId: string,
  userId: string,
  role: WorkItemParticipant['role'],
  expectedVersion: number,
) {
  return apiPut<{ workItemVersion: number; items: WorkItemParticipant[] }>(
    `/project-spaces/${spaceId}/work-items/${workItemId}/participants/${userId}`,
    { role, expectedVersion },
  )
}

export function listWorkItemActivities(spaceId: string, workItemId: string) {
  return apiGet<{ items: WorkItemActivity[]; nextBeforeSequence?: number | null }>(
    `/project-spaces/${spaceId}/work-items/${workItemId}/activities`,
  )
}

export function listWorkItemComments(spaceId: string, workItemId: string) {
  return apiGet<{ items: WorkItemComment[] }>(
    `/project-spaces/${spaceId}/work-items/${workItemId}/comments`,
  )
}

export function addWorkItemComment(
  spaceId: string,
  workItemId: string,
  content: string,
  expectedVersion: number,
) {
  return apiPost<{ workItemVersion: number; items: WorkItemComment[] }>(
    `/project-spaces/${spaceId}/work-items/${workItemId}/comments`,
    { content, expectedVersion },
  )
}

export function listWorkItemAttachments(spaceId: string, workItemId: string) {
  return apiGet<{ items: WorkItemAttachment[] }>(
    `/project-spaces/${spaceId}/work-items/${workItemId}/attachments`,
  )
}

export function addWorkItemAttachment(
  spaceId: string,
  workItemId: string,
  fileId: string,
  expectedVersion: number,
) {
  return apiPost<{ workItemVersion: number; items: WorkItemAttachment[] }>(
    `/project-spaces/${spaceId}/work-items/${workItemId}/attachments`,
    { fileId, expectedVersion },
  )
}

export async function uploadWorkItemFile(
  file: File,
  workItemId: string,
  onProgress?: (percent: number) => void,
) {
  onProgress?.(0)
  const upload = await apiPost<WorkItemFileUpload>('/files/upload-url', {
    fileName: file.name,
    contentType: file.type || 'application/octet-stream',
    sizeBytes: file.size,
    targetType: 'work_item',
    targetId: workItemId,
  })
  await uploadToSignedUrl(upload.uploadUrl, upload.headers, file, onProgress)
  onProgress?.(95)
  const metadata = await apiPost<WorkItemFileMetadata>('/files/complete', {
    fileId: upload.uploadId,
    targetType: 'work_item',
    targetId: workItemId,
  })
  onProgress?.(100)
  return metadata
}

export function getWorkItemAttachmentDownloadUrl(fileId: string) {
  return apiGet<WorkItemFileDownload>(`/files/${fileId}/download-url`)
}

function uploadToSignedUrl(
  url: string,
  headers: Record<string, string>,
  file: File,
  onProgress?: (percent: number) => void,
) {
  return new Promise<void>((resolve, reject) => {
    const request = new XMLHttpRequest()
    request.open('PUT', url)
    Object.entries(headers).forEach(([name, value]) => request.setRequestHeader(name, value))
    request.upload.addEventListener('progress', (event) => {
      if (event.lengthComputable) {
        onProgress?.(Math.min(90, Math.round((event.loaded / event.total) * 90)))
      }
    })
    request.addEventListener('load', () => {
      if (request.status >= 200 && request.status < 300) resolve()
      else reject(new Error(`File upload failed with status ${request.status}`))
    })
    request.addEventListener('error', () => reject(new Error('File upload failed because the network is unavailable')))
    request.addEventListener('abort', () => reject(new Error('File upload was cancelled')))
    request.send(file)
  })
}

export function resolveLegacyIssue(issueId: string) {
  return apiGet<{ location: string }>(
    `/compat/work-items/legacy/issues/${issueId}/location`,
  )
}

export type RuntimeActorRole = WorkItemFieldAccessRole
