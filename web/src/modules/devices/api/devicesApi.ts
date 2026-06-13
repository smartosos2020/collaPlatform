import { apiDelete, apiGet, apiPost } from '../../../shared/api/httpClient'

export type DeviceSummary = {
  id: string
  deviceType: 'web' | 'desktop' | 'ios' | 'android'
  deviceName?: string | null
  deviceFingerprint: string
  appVersion?: string | null
  lastActiveAt?: string | null
  createdAt: string
  revokedAt?: string | null
  activeSessionCount: number
  enabledPushTokenCount: number
  current: boolean
}

export type PushTokenSummary = {
  id: string
  deviceId: string
  provider: string
  enabled: boolean
  createdAt: string
  updatedAt: string
  revokedAt?: string | null
}

export type PushProbeResult = {
  deviceId: string
  enabledTokenCount: number
  deliverable: boolean
  checkedAt: string
}

export function listDevices() {
  return apiGet<DeviceSummary[]>('/devices')
}

export function revokeDevice(deviceId: string) {
  return apiDelete<void>(`/devices/${deviceId}`)
}

export function registerPushToken(deviceId: string, token: string, provider = 'fake') {
  return apiPost<PushTokenSummary>(`/devices/${deviceId}/push-token`, { provider, token })
}

export function probePush(deviceId: string) {
  return apiPost<PushProbeResult>(`/devices/${deviceId}/push-probe`)
}
