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

export async function uploadFileForTarget(
  file: File,
  targetType: string,
  targetId: string,
  onProgress?: (percent: number) => void,
) {
  onProgress?.(0)
  const upload = await createUploadUrl({
    fileName: file.name,
    contentType: file.type || 'application/octet-stream',
    sizeBytes: file.size,
    targetType,
    targetId,
  })
  await uploadToSignedUrl(upload.uploadUrl, upload.headers, file, onProgress)
  onProgress?.(95)
  const metadata = await completeUpload({ fileId: upload.uploadId, targetType, targetId })
  onProgress?.(100)
  return metadata
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
      if (request.status >= 200 && request.status < 300) {
        resolve()
      } else {
        reject(new Error(`File upload failed with status ${request.status}`))
      }
    })
    request.addEventListener('error', () => reject(new Error('File upload failed because the network is unavailable')))
    request.addEventListener('abort', () => reject(new Error('File upload was cancelled')))
    request.send(file)
  })
}
