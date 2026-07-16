import { HocuspocusProvider, WebSocketStatus } from '@hocuspocus/provider'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import * as Y from 'yjs'

import type { CurrentUser } from '../../../auth/authStore'
import { createKnowledgeCollaborationTicket, type KnowledgeCollaborationTicket } from '../api/knowledgeContentApi'

const OFFLINE_MAX_UPDATES = positiveInteger(import.meta.env.VITE_COLLABORATION_OFFLINE_MAX_UPDATES, 500)
const OFFLINE_MAX_BYTES = positiveInteger(import.meta.env.VITE_COLLABORATION_OFFLINE_MAX_BYTES, 1024 * 1024)

export type CollaborationOnlineUser = {
  clientId: number
  id: string
  name: string
  color: string
}

export type KnowledgeContentRealtimeSession = {
  document: Y.Doc
  provider: HocuspocusProvider | null
  status: WebSocketStatus | 'initializing' | 'error'
  synced: boolean
  unsyncedChanges: number
  onlineUsers: CollaborationOnlineUser[]
  canEdit: boolean
  error: string | null
  localUser: { id: string; name: string; color: string } | null
  recovery: {
    state: 'online' | 'offline' | 'overflow'
    queuedUpdates: number
    queuedBytes: number
    maxUpdates: number
    maxBytes: number
  }
  exportOfflineCopy: () => void
}

type Options = {
  spaceId?: string
  itemId?: string | null
  enabled: boolean
  currentUser: CurrentUser | null
}

export function useKnowledgeContentRealtimeCollaboration({ spaceId, itemId, enabled, currentUser }: Options): KnowledgeContentRealtimeSession {
  const document = useMemo(() => new Y.Doc({ guid: itemId ?? undefined }), [itemId])
  const ticketRef = useRef<KnowledgeCollaborationTicket | null>(null)
  const [provider, setProvider] = useState<HocuspocusProvider | null>(null)
  const [status, setStatus] = useState<KnowledgeContentRealtimeSession['status']>('initializing')
  const [synced, setSynced] = useState(false)
  const [unsyncedChanges, setUnsyncedChanges] = useState(0)
  const [onlineUsers, setOnlineUsers] = useState<CollaborationOnlineUser[]>([])
  const [canEdit, setCanEdit] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [recoveryState, setRecoveryState] = useState<'online' | 'offline' | 'overflow'>('online')
  const [queuedUpdates, setQueuedUpdates] = useState(0)
  const [queuedBytes, setQueuedBytes] = useState(0)
  const statusRef = useRef<KnowledgeContentRealtimeSession['status']>('initializing')
  const authorizedCanEditRef = useRef(false)
  const queueRef = useRef({ updates: 0, bytes: 0 })

  useEffect(() => () => document.destroy(), [document])

  useEffect(() => {
    if (!enabled || !spaceId || !itemId || !currentUser) {
      return
    }
    let disposed = false
    let activeProvider: HocuspocusProvider | null = null
    let tokenTimer: number | null = null

    const resetQueue = () => {
      queueRef.current = { updates: 0, bytes: 0 }
      setQueuedUpdates(0)
      setQueuedBytes(0)
      setRecoveryState('online')
    }

    const handleDocumentUpdate = (update: Uint8Array, origin: unknown) => {
      if (origin === activeProvider || statusRef.current !== 'disconnected' || !authorizedCanEditRef.current) return
      const next = { updates: queueRef.current.updates + 1, bytes: queueRef.current.bytes + update.byteLength }
      queueRef.current = next
      setQueuedUpdates(next.updates)
      setQueuedBytes(next.bytes)
      if (next.updates > OFFLINE_MAX_UPDATES || next.bytes > OFFLINE_MAX_BYTES) {
        setRecoveryState('overflow')
        setCanEdit(false)
        setError('离线修改已达到本地恢复上限，请导出副本后重新连接')
      } else {
        setRecoveryState('offline')
      }
    }
    const handleOffline = () => {
      statusRef.current = WebSocketStatus.Disconnected
      setStatus(WebSocketStatus.Disconnected)
      setRecoveryState('offline')
      setCanEdit(authorizedCanEditRef.current)
      activeProvider?.disconnect()
    }
    const handleOnline = () => {
      setError(null)
      activeProvider?.connect()
    }
    document.on('update', handleDocumentUpdate)
    window.addEventListener('offline', handleOffline)
    window.addEventListener('online', handleOnline)

    const issueTicket = async () => {
      const current = ticketRef.current
      if (current && new Date(current.expiresAt).getTime() - Date.now() > 60_000 && current.documentName.endsWith(itemId)) {
        return current
      }
      const next = await createKnowledgeCollaborationTicket(spaceId, itemId)
      ticketRef.current = next
      if (!disposed) {
        authorizedCanEditRef.current = next.canEdit
        setCanEdit(next.canEdit)
      }
      return next
    }

    void issueTicket().then((initialTicket) => {
      if (disposed) return
      activeProvider = new HocuspocusProvider({
        url: collaborationWebSocketUrl(import.meta.env.VITE_COLLABORATION_WS_URL ?? initialTicket.url),
        name: initialTicket.documentName,
        document,
        token: async () => (await issueTicket()).ticket,
        forceSyncInterval: 10_000,
        flushDelay: 120,
        onAuthenticated: ({ scope }) => {
          if (disposed) return
          authorizedCanEditRef.current = scope === 'read-write'
          setCanEdit(authorizedCanEditRef.current)
          setError(null)
        },
        onStatus: ({ status: nextStatus }) => {
          if (disposed) return
          statusRef.current = nextStatus
          setStatus(nextStatus)
          if (nextStatus === 'disconnected') setRecoveryState('offline')
        },
        onClose: ({ event }) => {
          if (disposed) return
          statusRef.current = WebSocketStatus.Disconnected
          setStatus(WebSocketStatus.Disconnected)
          if (event.code === 4401 || event.code === 4403) {
            authorizedCanEditRef.current = false
            setCanEdit(false)
            setError(event.reason || '实时协作权限已失效')
          } else {
            setCanEdit(authorizedCanEditRef.current)
            setRecoveryState('offline')
          }
        },
        onSynced: ({ state }) => {
          if (disposed) return
          setSynced(state)
          if (state) {
            resetQueue()
            setCanEdit(authorizedCanEditRef.current)
            setError(null)
          }
        },
        onUnsyncedChanges: ({ number }) => { if (!disposed) setUnsyncedChanges(number) },
        onAuthenticationFailed: ({ reason }) => {
          if (!disposed) {
            setCanEdit(false)
            setStatus('error')
            setError(reason || '实时协作身份已失效')
          }
        },
        onStateless: ({ payload }) => {
          if (disposed) return
          try {
            const message = JSON.parse(payload) as { type?: string; protocolVersion?: string; canView?: boolean; canEdit?: boolean }
            if (message.type !== 'permission' || message.protocolVersion !== 'colla-yjs-v1') return
            authorizedCanEditRef.current = message.canView === true && message.canEdit === true
            setCanEdit(authorizedCanEditRef.current)
            if (message.canView !== true) {
              setStatus('error')
              setError('知识内容访问权限已失效')
            }
          } catch {
            // Ignore stateless messages owned by future protocol extensions.
          }
        },
        onAwarenessChange: ({ states }) => {
          if (disposed) return
          const nextUsers = states.flatMap((state) => {
            const user = state.user as { id?: string; name?: string; color?: string } | undefined
            return user?.id ? [{
              clientId: state.clientId,
              id: user.id,
              name: user.name || user.id,
              color: user.color || '#5b5bd6',
            }] : []
          }).sort((left, right) => left.clientId - right.clientId)
          setOnlineUsers((current) => onlineUsersEqual(current, nextUsers) ? current : nextUsers)
        },
      })
      setProvider(activeProvider)
      tokenTimer = window.setInterval(() => {
        void activeProvider?.sendToken().catch(() => {
          if (!disposed) setError('实时协作权限刷新失败，已保留本地修改')
        })
      }, 2_000)
    }).catch((reason: unknown) => {
      if (!disposed) {
        setStatus('error')
        setError(reason instanceof Error ? reason.message : '实时协作连接失败')
      }
    })

    return () => {
      disposed = true
      if (tokenTimer != null) window.clearInterval(tokenTimer)
      document.off('update', handleDocumentUpdate)
      window.removeEventListener('offline', handleOffline)
      window.removeEventListener('online', handleOnline)
      activeProvider?.destroy()
      setProvider(null)
      setOnlineUsers([])
    }
  }, [currentUser, document, enabled, itemId, spaceId])

  const localUser = useMemo(() => currentUser ? {
    id: currentUser.id,
    name: currentUser.displayName || currentUser.username,
    color: colorFor(currentUser.id),
  } : null, [currentUser])
  const exportOfflineCopy = useCallback(() => {
    const payload = Y.encodeStateAsUpdate(document)
    const copy = new Uint8Array(payload.byteLength)
    copy.set(payload)
    const blob = new Blob([copy.buffer], { type: 'application/octet-stream' })
    const url = URL.createObjectURL(blob)
    const anchor = window.document.createElement('a')
    anchor.href = url
    anchor.download = `knowledge-${itemId ?? 'content'}-${Date.now()}.yjs`
    anchor.click()
    URL.revokeObjectURL(url)
  }, [document, itemId])
  return {
    document, provider, status, synced, unsyncedChanges, onlineUsers, canEdit, error, localUser,
    recovery: {
      state: recoveryState, queuedUpdates, queuedBytes,
      maxUpdates: OFFLINE_MAX_UPDATES, maxBytes: OFFLINE_MAX_BYTES,
    },
    exportOfflineCopy,
  }
}

function onlineUsersEqual(left: CollaborationOnlineUser[], right: CollaborationOnlineUser[]) {
  return left.length === right.length && left.every((user, index) => {
    const candidate = right[index]
    return candidate?.clientId === user.clientId
      && candidate.id === user.id
      && candidate.name === user.name
      && candidate.color === user.color
  })
}

function colorFor(value: string) {
  const colors = ['#5b5bd6', '#16a085', '#d97706', '#dc2626', '#2563eb', '#9333ea']
  let hash = 0
  for (let index = 0; index < value.length; index += 1) hash = ((hash << 5) - hash + value.charCodeAt(index)) | 0
  return colors[Math.abs(hash) % colors.length]
}

function collaborationWebSocketUrl(value: string) {
  if (!value.startsWith('/')) return value
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}${value}`
}

function positiveInteger(value: string | undefined, fallback: number) {
  const parsed = Number.parseInt(value ?? '', 10)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}
