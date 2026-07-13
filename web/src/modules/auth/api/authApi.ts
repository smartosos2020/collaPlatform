import { apiGet, apiPatch, apiPost } from '../../../shared/api/httpClient'
import type { CurrentUser } from '../authStore'

export type LoginRequest = {
  username: string
  password: string
}

export type AuthTokens = {
  tokenType: string
  accessToken: string
  refreshToken: string
  accessTokenExpiresAt: string
  refreshTokenExpiresAt: string
  deviceId: string
}

export async function login(request: LoginRequest): Promise<AuthTokens> {
  return apiPost<AuthTokens>('/auth/login', {
    ...request,
    deviceType: 'web',
    deviceFingerprint: getDeviceFingerprint(),
    deviceName: navigator.userAgent.slice(0, 120),
    appVersion: import.meta.env.MODE,
  })
}

export async function getCurrentUser(): Promise<CurrentUser> {
  return apiGet<CurrentUser>('/auth/me')
}

export type UpdateProfileRequest = {
  displayName: string
  email?: string
  avatarFileId?: string | null
}

export function updateProfile(request: UpdateProfileRequest) {
  return apiPatch<CurrentUser>('/auth/me', request)
}

export function changePassword(request: { currentPassword: string; newPassword: string }) {
  return apiPost<void>('/auth/me/password', request)
}

export async function logout(refreshToken: string | null): Promise<void> {
  await apiPost<void>('/auth/logout', { refreshToken })
}

function getDeviceFingerprint(): string {
  const key = 'colla.deviceFingerprint'
  const existing = localStorage.getItem(key)
  if (existing) {
    return existing
  }
  const value = crypto.randomUUID()
  localStorage.setItem(key, value)
  return value
}
