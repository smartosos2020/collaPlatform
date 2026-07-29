import { expect, test } from '@playwright/test'
import { QueryClient } from '@tanstack/react-query'

import {
  notificationListQueryKey,
  notificationUnreadCountQueryKey,
  reconcileActiveNotificationFilters,
} from '../src/modules/notifications/realtime/notificationReconciliation'
import {
  collectMessagesAfterSequence,
  mergeMessagePages,
  messageSignalReconciliationMode,
} from '../src/modules/messenger/realtime/messageReconciliation'
import {
  projectRealtimeReconciliation,
} from '../src/modules/projectSpaces/realtime/projectReconciliation'
import type { MessageSummary } from '../src/modules/messenger/api/messengerApi'
import type { NotificationFilters, UserNotificationView } from '../src/modules/notifications/api/notificationsApi'

test('@smoke @client-contract M3 reconciles a 120-message disconnect window in bounded afterSeq pages', async () => {
  const calls: number[] = []
  const result = await collectMessagesAfterSequence('conversation-1', 0, async (_, afterSeq, limit) => {
    calls.push(afterSeq)
    const start = afterSeq + 1
    const end = Math.min(120, afterSeq + limit)
    return {
      items: start > 120 ? [] : Array.from({ length: end - start + 1 }, (_, index) => message(start + index)),
      nextCursor: null,
    }
  })

  expect(calls).toEqual([0, 100])
  expect(result).toMatchObject({
    pagesFetched: 2,
    nextAfterSeq: 120,
    complete: true,
    stopReason: 'exhausted',
  })
  expect(result.items).toHaveLength(120)
  expect(result.items.map((item) => item.messageSeq)).toEqual(Array.from({ length: 120 }, (_, index) => index + 1))

  const merged = mergeMessagePages({ items: [message(120)], nextCursor: 'older' }, result.items)
  expect(merged.items).toHaveLength(120)
  expect(merged.items[0].messageSeq).toBe(120)
  expect(merged.items[119].messageSeq).toBe(1)
  expect(merged.nextCursor).toBe('older')
})

test('@smoke @client-contract M3 de-duplicates messages and stops a full page that makes no sequence progress', async () => {
  const page = Array.from({ length: 100 }, (_, index) => message(index + 1))
  let calls = 0
  const result = await collectMessagesAfterSequence('conversation-1', 0, async () => {
    calls += 1
    return { items: page, nextCursor: null }
  })

  expect(calls).toBe(2)
  expect(result).toMatchObject({
    pagesFetched: 2,
    nextAfterSeq: 100,
    complete: false,
    stopReason: 'no-progress',
  })
  expect(result.items).toHaveLength(100)
  expect(messageSignalReconciliationMode('message.edited', 'full-refetch')).toBe('full-refetch')
  expect(messageSignalReconciliationMode('message.created', 'full-refetch')).toBe('incremental')
})

test('@smoke @client-contract M3 commits notification list and unread count only after every REST query succeeds', async () => {
  const queryClient = new QueryClient()
  const filters: NotificationFilters = { status: 'unread', limit: 100 }
  const initial = [notification('old')]
  queryClient.setQueryData(notificationListQueryKey(filters), initial)
  queryClient.setQueryData(notificationUnreadCountQueryKey, { count: 1 })

  await expect(
    reconcileActiveNotificationFilters(queryClient, [filters], {
      list: async () => [notification('new')],
      unreadCount: async () => {
        throw new Error('unread unavailable')
      },
    }),
  ).rejects.toThrow('unread unavailable')
  expect(queryClient.getQueryData(notificationListQueryKey(filters))).toEqual(initial)
  expect(queryClient.getQueryData(notificationUnreadCountQueryKey)).toEqual({ count: 1 })

  await reconcileActiveNotificationFilters(queryClient, [filters, { ...filters }], {
    list: async () => [notification('new')],
    unreadCount: async () => ({ count: 7 }),
  })
  expect(queryClient.getQueryData(notificationListQueryKey(filters))).toEqual([notification('new')])
  expect(queryClient.getQueryData(notificationUnreadCountQueryKey)).toEqual({ count: 7 })
})

test('@smoke @client-contract M3 removes and exits only when permission invalidation matches the active object', () => {
  const signal = {
    type: 'permission.invalidated',
    objectType: 'project_space',
    objectId: 'space-a',
    calibrationPath: '/api/should-never-be-fetched',
  }

  const matching = projectRealtimeReconciliation(signal, {
    activeResource: { objectType: 'project_space', objectId: 'space-a' },
  })
  expect(matching.matched).toBeTruthy()
  expect(matching.trigger).toBe('permission-invalidated')
  expect(matching.remove).toContainEqual({ queryKey: ['project-spaces', 'space-a'], exact: false })
  expect(matching.navigation).toEqual({
    action: 'exit',
    to: '/project-spaces',
    reason: 'access-invalidated',
  })

  const unrelated = projectRealtimeReconciliation(signal, {
    activeResource: { objectType: 'project_space', objectId: 'space-b' },
  })
  expect(unrelated.navigation).toEqual({ action: 'stay' })
})

test('@smoke @client-contract M3 routes known signals statically and ignores calibrationPath contents', () => {
  const originalFetch = globalThis.fetch
  let fetchCalls = 0
  globalThis.fetch = (() => {
    fetchCalls += 1
    throw new Error('static mapping must not fetch')
  }) as typeof fetch

  try {
    const decisions = [
      projectRealtimeReconciliation({
        type: 'project_space.changed',
        objectType: 'project_space',
        objectId: 'space-1',
        calibrationPath: '/api/unrelated',
      }),
      projectRealtimeReconciliation({
        type: 'identity.invalidated',
        objectType: 'user',
        objectId: 'user-1',
        calibrationPath: '/api/admin/users',
      }, { currentUserId: 'user-1' }),
    ]

    expect(decisions.every((decision) => decision.matched)).toBeTruthy()
    expect(decisions.map((decision) => decision.trigger)).toEqual([
      'object-changed',
      'identity-invalidated',
    ])
    expect(decisions[0].invalidate).toContainEqual({ queryKey: ['project-spaces', 'space-1'], exact: false })
    expect(decisions[1].invalidate).toContainEqual({ queryKey: ['auth', 'me'], exact: false })
    expect(fetchCalls).toBe(0)
  } finally {
    globalThis.fetch = originalFetch
  }
})

function message(sequence: number): MessageSummary {
  return {
    id: `message-${sequence}`,
    conversationId: 'conversation-1',
    senderId: 'sender-1',
    senderName: 'Sender',
    messageType: 'text',
    content: `Message ${sequence}`,
    clientMessageId: `client-${sequence}`,
    messageSeq: sequence,
    createdAt: new Date(sequence * 1_000).toISOString(),
    editedAt: null,
    revokedAt: null,
    pinnedAt: null,
    pinnedBy: null,
    mentions: [],
    links: [],
    reactions: [],
  }
}

function notification(id: string): UserNotificationView {
  return {
    id,
    notificationType: 'test',
    sourceType: 'test',
    title: id,
    createdAt: '2026-01-01T00:00:00Z',
  }
}
