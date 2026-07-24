import { notifyManager, type QueryClient, type QueryKey } from '@tanstack/react-query'

import type {
  NotificationFilters,
  UnreadCount,
  UserNotificationView,
} from '../api/notificationsApi'

export type NotificationReconciliationApi = {
  list: (filters: NotificationFilters) => Promise<UserNotificationView[]>
  unreadCount: () => Promise<UnreadCount>
}

export const NOTIFICATION_RECONCILIATION_TRIGGERS = [
  'initial-connect',
  'reconnect',
  'sequence-gap',
  'window-focus',
  'signal',
] as const

export type NotificationReconciliationTrigger = typeof NOTIFICATION_RECONCILIATION_TRIGGERS[number]

export type NotificationReconciliationResult = {
  trigger: NotificationReconciliationTrigger
  lists: Array<{ filters: NotificationFilters; items: UserNotificationView[] }>
  unreadCount: UnreadCount
}

export function notificationListQueryKey(filters: NotificationFilters): QueryKey {
  return ['notifications', filters]
}

export const notificationUnreadCountQueryKey: QueryKey = ['notifications', 'unread-count']

export async function reconcileActiveNotificationFilters(
  queryClient: QueryClient,
  activeFilters: readonly NotificationFilters[],
  api: NotificationReconciliationApi,
  trigger: NotificationReconciliationTrigger = 'signal',
): Promise<NotificationReconciliationResult> {
  const filters = uniqueFilters(activeFilters)
  const [unreadCount, ...lists] = await Promise.all([
    api.unreadCount(),
    ...filters.map((filter) => api.list(filter)),
  ])

  const snapshots = filters.map((filter, index) => ({ filters: filter, items: lists[index] }))
  notifyManager.batch(() => {
    for (const snapshot of snapshots) {
      queryClient.setQueryData(notificationListQueryKey(snapshot.filters), snapshot.items)
    }
    queryClient.setQueryData(notificationUnreadCountQueryKey, unreadCount)
  })

  return { trigger, lists: snapshots, unreadCount }
}

function uniqueFilters(filters: readonly NotificationFilters[]) {
  const seen = new Set<string>()
  return filters.filter((filter) => {
    const key = JSON.stringify({
      limit: filter.limit ?? null,
      source: filter.source ?? null,
      status: filter.status ?? null,
      targetType: filter.targetType ?? null,
      unreadOnly: filter.unreadOnly ?? null,
    })
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}
