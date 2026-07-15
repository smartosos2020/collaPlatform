import { HocuspocusProvider, type WebSocketStatus } from '@hocuspocus/provider'
import { useEffect, useMemo, useRef, useState } from 'react'
import * as Y from 'yjs'

import type { CurrentUser } from '../../../auth/authStore'
import { createKnowledgeCollaborationTicket, type KnowledgeCollaborationTicket } from '../api/knowledgeContentApi'

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

  useEffect(() => () => document.destroy(), [document])

  useEffect(() => {
    if (!enabled || !spaceId || !itemId || !currentUser) {
      return
    }
    let disposed = false
    let activeProvider: HocuspocusProvider | null = null
    let tokenTimer: number | null = null

    const issueTicket = async () => {
      const current = ticketRef.current
      if (current && new Date(current.expiresAt).getTime() - Date.now() > 60_000 && current.documentName.endsWith(itemId)) {
        return current
      }
      const next = await createKnowledgeCollaborationTicket(spaceId, itemId)
      ticketRef.current = next
      if (!disposed) setCanEdit(next.canEdit)
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
        onAuthenticated: ({ scope }) => { if (!disposed) setCanEdit(scope === 'read-write') },
        onStatus: ({ status: nextStatus }) => { if (!disposed) setStatus(nextStatus) },
        onClose: () => { if (!disposed) setCanEdit(false) },
        onSynced: ({ state }) => { if (!disposed) setSynced(state) },
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
            setCanEdit(message.canView === true && message.canEdit === true)
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
  return { document, provider, status, synced, unsyncedChanges, onlineUsers, canEdit, error, localUser }
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
