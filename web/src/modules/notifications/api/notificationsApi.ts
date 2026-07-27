import { apiGet, apiPost, apiPut } from '../../../shared/api/httpClient'

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

export type NotificationPreference = {
  sourceType: string
  enabled: boolean
  required: boolean
}

export type NotificationFilters = {
  unreadOnly?: boolean
  status?: 'unread' | 'read'
  source?: string
  targetType?: string
  limit?: number
}

export type PersonalActivity = {
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

export type PersonalActivityPage = {
  items: PersonalActivity[]
  nextBeforeSequence?: number | null
  readThroughSequence: number
  unreadCount: number
  truncated: boolean
  generatedAt: string
}

export type PersonalReminder = {
  workItemId: string
  spaceId: string
  displayKey: string
  title: string
  dueAt: string
  state: 'approaching' | 'due' | 'overdue'
  deepLink: string
}

export type PersonalReminderView = {
  items: PersonalReminder[]
  timezone: string
  evaluatedAt: string
  enabled: boolean
}

export type PersonalReminderPreference = {
  timezone: string
  approachingMinutes: number
  enabled: boolean
  updatedAt: string
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

export function listNotificationPreferences() {
  return apiGet<NotificationPreference[]>('/notifications/preferences')
}

export function updateNotificationPreference(sourceType: string, enabled: boolean) {
  return apiPut<NotificationPreference[]>(`/notifications/preferences/${sourceType}`, { enabled })
}

export function listPersonalActivities(limit = 30) {
  return apiGet<PersonalActivityPage>(`/personal-work/activities?limit=${limit}`)
}

export function markPersonalActivitiesRead(throughSequence: number) {
  return apiPost<{ readThroughSequence: number; updatedAt: string }>('/personal-work/activities:read', {
    throughSequence,
  })
}

export function listPersonalReminders(timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC') {
  return apiGet<PersonalReminderView>(`/personal-work/reminders?timezone=${encodeURIComponent(timezone)}`)
}

export function dispatchPersonalReminders(timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC') {
  return apiPost<{ considered: number; emitted: number; dispatchedAt: string }>('/personal-work/reminders:dispatch', {
    timezone,
    requestId: crypto.randomUUID(),
  })
}

export function getPersonalReminderPreference() {
  return apiGet<PersonalReminderPreference>('/personal-work/reminder-preference')
}

export function updatePersonalReminderPreference(preference: {
  timezone: string
  approachingMinutes: number
  enabled: boolean
}) {
  return apiPut<PersonalReminderPreference>('/personal-work/reminder-preference', preference)
}
