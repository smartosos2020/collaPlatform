import { apiGet } from '../../../shared/api/httpClient'

export type AdminSystemSettings = {
  workspace: {
    id: string
    name: string
    slug: string
    status: string
    createdAt: string
    updatedAt: string
  }
  securityPolicy: {
    passwordMinLength: number
    passwordRequireLetter: boolean
    passwordRequireDigit: boolean
    accessTokenTtlMinutes: number
    refreshTokenTtlDays: number
    requiredSecurityNotifications: boolean
    requiredSystemNotifications: boolean
  }
  runtime: {
    service: string
    healthEndpoint: string
    memberCount: number
    activeSessionCount: number
    activeDeviceCount: number
  }
}

export function getAdminSystemSettings() {
  return apiGet<AdminSystemSettings>('/admin/system-settings')
}
