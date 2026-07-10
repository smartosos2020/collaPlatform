import { apiGet, apiPost } from '../../../shared/api/httpClient'

export type UserNotificationView = {
  id: string
  notificationType: string
  sourceType: string
  notificationScope?: 'user_collaboration' | 'admin_governance'
  title: string
  body?: string | null
  targetType?: string | null
  targetId?: string | null
  webPath?: string | null
  readAt?: string | null
  createdAt: string
  reminder?: {
    unread: boolean
    webPath?: string | null
  }
  availableActions?: string[]
}

export type NotificationItem = UserNotificationView

export type UnreadCount = {
  count: number
}

export type NotificationBatchResult = {
  changed: number
}

export type NotificationFilters = {
  unreadOnly?: boolean
  status?: 'unread' | 'read'
  source?: string
  targetType?: string
  limit?: number
}

export function listNotifications(filters: NotificationFilters = {}) {
  const params = new URLSearchParams()
  if (filters.unreadOnly !== undefined) {
    params.set('unreadOnly', String(filters.unreadOnly))
  }
  if (filters.status) {
    params.set('status', filters.status)
  }
  if (filters.source) {
    params.set('source', filters.source)
  }
  if (filters.targetType) {
    params.set('targetType', filters.targetType)
  }
  if (filters.limit) {
    params.set('limit', String(filters.limit))
  }
  const query = params.toString()
  return apiGet<UserNotificationView[]>(query ? `/notifications?${query}` : '/notifications')
}

export function getUnreadCount() {
  return apiGet<UnreadCount>('/notifications/unread-count')
}

export function markNotificationRead(notificationId: string) {
  return apiPost<void>(`/notifications/${notificationId}/read`)
}

export function markNotificationsRead(notificationIds: string[]) {
  return apiPost<NotificationBatchResult>('/notifications/read-batch', { notificationIds })
}

export function markAllNotificationsRead() {
  return apiPost<void>('/notifications/read-all')
}
