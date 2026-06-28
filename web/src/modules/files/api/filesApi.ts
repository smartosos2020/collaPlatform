import { apiGet, apiPost } from '../../../shared/api/httpClient'

export type UploadUrlResponse = {
  uploadId: string
  objectKey: string
  uploadUrl: string
  headers: Record<string, string>
  expiresAt: string
}

export type FileMetadata = {
  id: string
  objectKey: string
  originalName: string
  contentType: string
  sizeBytes: number
  status: string
  uploadedBy: string
  createdAt: string
  completedAt?: string | null
}

export type DownloadUrlResponse = {
  downloadUrl: string
  expiresAt: string
}

export function createUploadUrl(request: {
  fileName: string
  contentType?: string
  sizeBytes: number
  targetType?: string
  targetId?: string
}) {
  return apiPost<UploadUrlResponse>('/files/upload-url', request)
}

export function completeUpload(request: { fileId: string; targetType?: string; targetId?: string }) {
  return apiPost<FileMetadata>('/files/complete', request)
}

export function getFileMetadata(fileId: string) {
  return apiGet<FileMetadata>(`/files/${fileId}`)
}

export function getFileDownloadUrl(fileId: string) {
  return apiGet<DownloadUrlResponse>(`/files/${fileId}/download-url`)
}

export async function uploadFileForTarget(file: File, targetType: string, targetId: string) {
  const upload = await createUploadUrl({
    fileName: file.name,
    contentType: file.type || 'application/octet-stream',
    sizeBytes: file.size,
    targetType,
    targetId,
  })
  await fetch(upload.uploadUrl, {
    method: 'PUT',
    headers: upload.headers,
    body: file,
  })
  return completeUpload({ fileId: upload.uploadId, targetType, targetId })
}
