import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useRef, type ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import { AUTH_STORAGE_KEYS, useAuthStore } from '../../modules/auth/authStore'
import {
  listMessages,
  type MessagePage,
} from '../../modules/messenger/api/messengerApi'
import {
  reconcileConversationMessages,
  type MessageReconciliationTrigger,
} from '../../modules/messenger/realtime/messageReconciliation'
import {
  getUnreadCount,
  listNotifications,
  type NotificationFilters,
} from '../../modules/notifications/api/notificationsApi'
import {
  reconcileActiveNotificationFilters,
  type NotificationReconciliationTrigger,
} from '../../modules/notifications/realtime/notificationReconciliation'
import {
  getIssue,
  getProject,
  type UserIssueDetailView,
  type UserProjectDetailView,
} from '../../modules/projects/api/projectsApi'
import {
  applyReconciliationDecision,
  projectRealtimeReconciliation,
  type ActiveRealtimeResource,
} from '../../modules/projects/realtime/projectReconciliation'
import { ApiRequestError } from '../../shared/api/httpClient'
import {
  RealtimeProvider,
  useRealtimeCalibration,
  useRealtimeConnection,
  useRealtimeSubscription,
  type KnownRealtimeType,
  type RealtimeCalibrationReason,
  type RealtimeEnvelope,
} from '../../shared/realtime'

const APPLICATION_SIGNAL_TYPES: readonly KnownRealtimeType[] = [
  'notification.created',
  'notification.read',
  'notification.unread.changed',
  'message.created',
  'message.edited',
  'message.revoked',
  'message.pinned',
  'message.unpinned',
  'message.reaction.toggled',
  'conversation.updated',
  'conversation.read',
  'unread.changed',
  'project.changed',
  'project.invalidated',
  'issue.changed',
  'issue.invalidated',
  'project_space.changed',
  'project_space.invalidated',
  'permission.invalidated',
  'identity.invalidated',
]

const PROTECTED_QUERY_PREFIXES = [
  ['projects'],
  ['issues'],
  ['project-spaces'],
  ['bases'],
  ['knowledge-bases'],
  ['knowledge-content'],
  ['im'],
  ['notifications'],
] as const

export function AppRealtimeBoundary({ children }: { children: ReactNode }) {
  const accessToken = useAuthStore((state) => state.accessToken)
  const currentUser = useAuthStore((state) => state.currentUser)
  const contextVersion = useAuthStore((state) => state.contextVersion)
  const deviceFingerprint = localStorage.getItem(AUTH_STORAGE_KEYS.deviceFingerprint) ?? ''
  const contextKey = [
    contextVersion,
    currentUser?.workspaceId ?? '',
    currentUser?.id ?? '',
    deviceFingerprint,
  ].join(':')

  return (
    <RealtimeProvider
      key={contextKey}
      accessToken={accessToken}
      workspaceId={currentUser?.workspaceId ?? null}
      userId={currentUser?.id ?? null}
    >
      <ApplicationRealtimeRouter />
      {children}
    </RealtimeProvider>
  )
}

function ApplicationRealtimeRouter() {
  const queryClient = useQueryClient()
  const location = useLocation()
  const navigate = useNavigate()
  const currentUser = useAuthStore((state) => state.currentUser)
  const realtime = useRealtimeConnection()
  const calibrationRunRef = useRef<Promise<boolean> | null>(null)
  const lastFocusCalibrationAtRef = useRef(0)

  const activeResource = useMemo(
    () => activeResourceFromPath(location.pathname),
    [location.pathname],
  )

  const reconcileNotifications = useCallback(
    async (trigger: NotificationReconciliationTrigger) => {
      const filters = activeNotificationFilters(queryClient)
      await reconcileActiveNotificationFilters(
        queryClient,
        filters,
        { list: listNotifications, unreadCount: getUnreadCount },
        trigger,
      )
    },
    [queryClient],
  )

  const reconcileIm = useCallback(
    async (
      trigger: MessageReconciliationTrigger,
      signal?: RealtimeEnvelope,
    ) => {
      await queryClient.invalidateQueries({ queryKey: ['im', 'conversations'] })
      const targetConversationId = signalConversationId(signal)
      const queries = queryClient.getQueryCache().findAll({ queryKey: ['im', 'messages'] })
      const uniqueQueries = new Map<string, (typeof queries)[number]>()
      for (const query of queries) {
        const queryKey = query.queryKey
        const conversationId = typeof queryKey[2] === 'string' ? queryKey[2] : null
        if (!conversationId || (targetConversationId && targetConversationId !== conversationId)) {
          continue
        }
        uniqueQueries.set(JSON.stringify(queryKey), query)
      }

      await Promise.all([...uniqueQueries.values()].map(async (query) => {
        const queryKey = query.queryKey
        const conversationId = queryKey[2] as string
        const isContextQuery = typeof queryKey[3] === 'string' && queryKey[3].length > 0
        if (isContextQuery || query.getObserversCount() === 0) {
          await queryClient.invalidateQueries({ queryKey, exact: true, refetchType: 'active' })
          return
        }
        const current = queryClient.getQueryData<MessagePage>(queryKey)
        const afterSeq = Math.max(0, ...(current?.items ?? []).map((item) => item.messageSeq))
        await reconcileConversationMessages({
          queryClient,
          queryKey,
          conversationId,
          afterSeq,
          signalType: signal?.type ?? 'message.created',
          trigger,
          mutationReconciliation: signal?.type === 'message.created' ? 'incremental' : 'full-refetch',
          fetchPage: (id, sequence, limit) => listMessages(id, null, sequence, limit),
        })
        await queryClient.invalidateQueries({
          queryKey: ['im', 'conversation', conversationId],
          exact: true,
        })
      }))
    },
    [queryClient],
  )

  const reconcileProjectSignal = useCallback(
    async (signal: RealtimeEnvelope) => {
      const activeIssueProjectId = activeResource?.objectType === 'issue'
        ? queryClient.getQueryData<UserIssueDetailView>(['issues', activeResource.objectId])?.issue.projectId
        : undefined
      if (signal.type === 'identity.invalidated' &&
        signal.objectType === 'user' &&
        signal.objectId === currentUser?.id) {
        for (const queryKey of PROTECTED_QUERY_PREFIXES) {
          queryClient.removeQueries({ queryKey })
        }
      }

      const decision = projectRealtimeReconciliation(signal, {
        currentUserId: currentUser?.id,
        activeResource: signal.type === 'permission.invalidated' ? null : activeResource,
      })
      const navigation = await applyReconciliationDecision(queryClient, decision)
      if (navigation.action === 'exit') {
        navigate(navigation.to, { replace: true })
        return
      }

      if (signal.type === 'permission.invalidated') {
        await verifyActiveProjectAccess(
          signal,
          activeResource,
          activeIssueProjectId,
          queryClient,
          navigate,
        )
      }
    },
    [activeResource, currentUser?.id, navigate, queryClient],
  )

  const reconcileAll = useCallback(
    (reason: RealtimeCalibrationReason | 'window-focus') => {
      if (calibrationRunRef.current) {
        return calibrationRunRef.current
      }
      const notificationTrigger = notificationTriggerFor(reason)
      const messageTrigger = messageTriggerFor(reason)
      const run = Promise.allSettled([
        reconcileNotifications(notificationTrigger),
        reconcileIm(messageTrigger),
        queryClient.invalidateQueries({ queryKey: ['projects'] }),
        queryClient.invalidateQueries({ queryKey: ['project-spaces'] }),
        queryClient.invalidateQueries({ queryKey: ['resource-permissions'] }),
      ]).then((results) => results.every((result) => result.status === 'fulfilled'))
      calibrationRunRef.current = run.finally(() => {
        calibrationRunRef.current = null
      })
      return calibrationRunRef.current
    },
    [queryClient, reconcileIm, reconcileNotifications],
  )

  useRealtimeSubscription(APPLICATION_SIGNAL_TYPES, (signal) => {
    if (signal.type.startsWith('notification.')) {
      void ignoreReconciliationFailure(reconcileNotifications('signal'))
      return
    }
    if (isImSignal(signal.type)) {
      void ignoreReconciliationFailure(reconcileIm('signal', signal))
      return
    }
    void ignoreReconciliationFailure(reconcileProjectSignal(signal))
  })

  useRealtimeCalibration((request) => {
    void reconcileAll(request.reason).then((complete) => {
      if (complete) {
        realtime.markCalibrated()
      }
    })
  })

  useEffect(() => {
    const calibrateOnFocus = () => {
      if (document.visibilityState !== 'visible') {
        return
      }
      const now = Date.now()
      if (now - lastFocusCalibrationAtRef.current < 30_000) {
        return
      }
      lastFocusCalibrationAtRef.current = now
      void reconcileAll('window-focus').then((complete) => {
        if (complete) {
          realtime.markCalibrated()
        }
      })
    }
    window.addEventListener('focus', calibrateOnFocus)
    document.addEventListener('visibilitychange', calibrateOnFocus)
    return () => {
      window.removeEventListener('focus', calibrateOnFocus)
      document.removeEventListener('visibilitychange', calibrateOnFocus)
    }
  }, [realtime, reconcileAll])

  return null
}

function activeNotificationFilters(queryClient: ReturnType<typeof useQueryClient>) {
  const filters: NotificationFilters[] = []
  for (const query of queryClient.getQueryCache().findAll({ queryKey: ['notifications'] })) {
    const candidate = query.queryKey[1]
    if (candidate && typeof candidate === 'object' && !Array.isArray(candidate)) {
      filters.push(candidate as NotificationFilters)
    }
  }
  return filters
}

function signalConversationId(signal?: RealtimeEnvelope) {
  if (!signal) return null
  const payloadConversationId = signal.payload.conversationId
  if (typeof payloadConversationId === 'string') return payloadConversationId
  return signal.objectType === 'conversation' ? signal.objectId : null
}

function isImSignal(type: KnownRealtimeType) {
  return type.startsWith('message.') ||
    type.startsWith('conversation.') ||
    type === 'unread.changed'
}

function activeResourceFromPath(pathname: string): ActiveRealtimeResource | null {
  const segments = pathname.split('/').filter(Boolean)
  if (segments[0] === 'projects' && segments[1]) {
    return { objectType: 'project', objectId: segments[1] }
  }
  if (segments[0] === 'issues' && segments[1]) {
    return { objectType: 'issue', objectId: segments[1] }
  }
  if (segments[0] === 'project-spaces' && segments[1]) {
    return { objectType: 'project_space', objectId: segments[1] }
  }
  if (segments[0] === 'bases' && segments[1]) {
    return { objectType: 'base', objectId: segments[1] }
  }
  if (segments[0] === 'knowledge-bases' && segments[1] && segments[2] === 'items' && segments[3]) {
    return { objectType: 'knowledge_content', objectId: segments[3] }
  }
  if (segments[0] === 'knowledge-bases' && segments[1]) {
    return { objectType: 'knowledge_base', objectId: segments[1] }
  }
  return null
}

async function verifyActiveProjectAccess(
  signal: RealtimeEnvelope,
  activeResource: ActiveRealtimeResource | null,
  activeIssueProjectId: string | undefined,
  queryClient: ReturnType<typeof useQueryClient>,
  navigate: ReturnType<typeof useNavigate>,
) {
  if (!activeResource) return
  try {
    if (signal.objectType === 'project') {
      const matchesActiveProject = activeResource.objectType === 'project' &&
        activeResource.objectId === signal.objectId
      const matchesActiveIssueProject = activeIssueProjectId === signal.objectId
      if (!matchesActiveProject && !matchesActiveIssueProject) return
      const project = await getProject(signal.objectId)
      queryClient.setQueryData<UserProjectDetailView>(['projects', signal.objectId], project)
      return
    }
    if (signal.objectType === 'issue' &&
      activeResource.objectType === 'issue' &&
      activeResource.objectId === signal.objectId) {
      const issue = await getIssue(signal.objectId)
      queryClient.setQueryData<UserIssueDetailView>(['issues', signal.objectId], issue)
    }
  } catch (error) {
    if (error instanceof ApiRequestError && [401, 403, 404].includes(error.status)) {
      navigate('/projects', { replace: true })
    }
  }
}

async function ignoreReconciliationFailure(reconciliation: Promise<unknown>) {
  try {
    await reconciliation
  } catch {
    // The durable REST state remains authoritative and the next focus/reconnect retries calibration.
  }
}

function notificationTriggerFor(
  reason: RealtimeCalibrationReason | 'window-focus',
): NotificationReconciliationTrigger {
  if (reason === 'initial-ready') return 'initial-connect'
  if (reason === 'reconnected') return 'reconnect'
  if (reason === 'gap') return 'sequence-gap'
  if (reason === 'window-focus') return 'window-focus'
  return 'reconnect'
}

function messageTriggerFor(
  reason: RealtimeCalibrationReason | 'window-focus',
): MessageReconciliationTrigger {
  if (reason === 'initial-ready') return 'initial-connect'
  if (reason === 'reconnected') return 'reconnect'
  if (reason === 'gap') return 'sequence-gap'
  if (reason === 'window-focus') return 'window-focus'
  return 'reconnect'
}
