import { apiGet, apiPost } from '../../../shared/api/httpClient'
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
