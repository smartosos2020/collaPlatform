import { apiGet } from '../../../shared/api/httpClient'

export type AdminAuditLogEntryView = {
  id: string
  workspaceId: string
  actorId?: string | null
  actorName?: string | null
  action: string
  targetType: string
  targetId?: string | null
  ipAddress?: string | null
  userAgent?: string | null
  metadata: Record<string, unknown>
  createdAt: string
  actor?: {
    actorId?: string | null
    actorName?: string | null
    ipAddress?: string | null
    userAgent?: string | null
  }
  target?: {
    targetType: string
    targetId?: string | null
  }
  context?: {
    action: string
    metadata: Record<string, unknown>
  }
  riskTag?: 'low' | 'medium' | 'high'
  quickFilters?: string[]
}

export type AuditLogEntry = AdminAuditLogEntryView

export type AuditLogFilters = {
  action?: string
  targetType?: string
  targetId?: string
  actorId?: string
  limit?: number
}

export function listAuditLogs(filters: AuditLogFilters = {}) {
  const params = new URLSearchParams()
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== '') {
      params.set(key, String(value))
    }
  })
  const query = params.toString()
  return apiGet<AdminAuditLogEntryView[]>(query ? `/admin/audit-logs?${query}` : '/admin/audit-logs')
}
