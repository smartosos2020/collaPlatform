import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

import { useAuthStore } from '../../../auth/authStore'
import type { PlatformWebSocketEvent } from '../../../../shared/websocket/websocketEvents'

const WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL ?? 'ws://localhost:8080/ws/events'

export type KnowledgeContentCollaborationStatus =
  | 'idle'
  | 'connecting'
  | 'joined'
  | 'dirty'
  | 'saving'
  | 'synced'
  | 'offline'
  | 'error'

export type KnowledgeContentCursor = {
  from: number
  to: number
  empty?: boolean
}

export type KnowledgeContentCollaborator = {
  userId: string
  username: string
  displayName: string
  clientId: string
  color: string
  cursor?: KnowledgeContentCursor | null
  editing: boolean
  seenAt?: string
}

export type KnowledgeContentCollaborationSnapshot = {
  itemId: string
  title: string
  content: string
  serverClock: number
  stateVector: string
  versionNo?: number
}

type UseKnowledgeContentCollaborationOptions = {
  itemId: string
  title: string
  content: string
  versionNo: number
  canEdit: boolean
  enabled?: boolean
  onRemoteSnapshot: (snapshot: KnowledgeContentCollaborationSnapshot) => void
  onSaved?: (snapshot: { serverClock: number; savedAt?: string }) => void
}

type PendingUpdate = {
  title: string
  content: string
  baseTitle: string
  baseContent: string
  localSeq: number
}

export function useKnowledgeContentCollaboration({
  itemId,
  title,
  content,
  versionNo,
  canEdit,
  enabled = true,
  onRemoteSnapshot,
  onSaved,
}: UseKnowledgeContentCollaborationOptions) {
  const storeAccessToken = useAuthStore((state) => state.accessToken)
  const accessToken = useMemo(() => storeAccessToken ?? window.localStorage.getItem('colla.accessToken'), [storeAccessToken])
  const clientId = useMemo(() => stableClientId(), [])
  const socketRef = useRef<WebSocket | null>(null)
  const reconnectTimerRef = useRef<number | null>(null)
  const reconnectAttemptRef = useRef(0)
  const joinedRef = useRef(false)
  const localSeqRef = useRef(0)
  const pendingUpdateRef = useRef<PendingUpdate | null>(null)
  const sendTimerRef = useRef<number | null>(null)
  const latestTitleRef = useRef(title)
  const latestContentRef = useRef(content)
  const lastConfirmedTitleRef = useRef(title)
  const lastConfirmedContentRef = useRef(content)
  const serverSnapshotKeyRef = useRef(snapshotKey(title, content))
  const lastSentKeyRef = useRef('')
  const serverClockRef = useRef(versionNo)
  const remoteSnapshotRef = useRef(onRemoteSnapshot)
  const savedRef = useRef(onSaved)
  const resetKeyRef = useRef('')
  const [status, setStatus] = useState<KnowledgeContentCollaborationStatus>('idle')
  const [onlineUsers, setOnlineUsers] = useState<KnowledgeContentCollaborator[]>([])
  const [lastSavedAt, setLastSavedAt] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const sendCommand = useCallback((type: string, payload: Record<string, unknown>) => {
    const socket = socketRef.current
    if (socket?.readyState !== WebSocket.OPEN) {
      return false
    }
    socket.send(JSON.stringify({
      type,
      requestId: requestId(),
      itemId,
      payload: {
        itemId,
        ...payload,
      },
    }))
    return true
  }, [itemId])

  const updateClock = useCallback((payload: Record<string, unknown>) => {
    const nextClock = Number(payload.serverClock ?? 0)
    if (Number.isFinite(nextClock) && nextClock > serverClockRef.current) {
      serverClockRef.current = nextClock
    }
  }, [])

  const updateOnlineUsers = useCallback((payload: Record<string, unknown>) => {
    const users = Array.isArray(payload.onlineUsers) ? payload.onlineUsers : []
    setOnlineUsers(users.map(normalizeCollaborator).filter(Boolean) as KnowledgeContentCollaborator[])
  }, [])

  const sendPendingUpdate = useCallback(() => {
    const pending = pendingUpdateRef.current
    const socket = socketRef.current
    if (!pending || socket?.readyState !== WebSocket.OPEN || !joinedRef.current) {
      return false
    }
    setStatus('saving')
    lastSentKeyRef.current = snapshotKey(pending.title, pending.content)
    sendCommand('knowledge.content.update', {
      clientId,
      localSeq: pending.localSeq,
      baseServerClock: serverClockRef.current,
      encoding: 'snapshot-v1',
      title: pending.title,
      content: pending.content,
    })
    pendingUpdateRef.current = null
    return true
  }, [clientId, sendCommand])

  const queueUpdate = useCallback((nextTitle: string, nextContent: string) => {
    const localSeq = localSeqRef.current + 1
    localSeqRef.current = localSeq
    pendingUpdateRef.current = {
      title: nextTitle,
      content: nextContent,
      baseTitle: lastConfirmedTitleRef.current,
      baseContent: lastConfirmedContentRef.current,
      localSeq,
    }
    if (!sendPendingUpdate()) {
      setStatus('offline')
    }
  }, [sendPendingUpdate])

  const applySnapshot = useCallback((payload: Record<string, unknown>) => {
    updateClock(payload)
    updateOnlineUsers(payload)
    const nextTitle = String(payload.title ?? latestTitleRef.current)
    const nextContent = String(payload.content ?? latestContentRef.current)
    const pending = pendingUpdateRef.current
    const localTitle = pending?.title ?? latestTitleRef.current
    const localContent = pending?.content ?? latestContentRef.current
    const baseTitle = pending?.baseTitle ?? lastConfirmedTitleRef.current
    const baseContent = pending?.baseContent ?? lastConfirmedContentRef.current
    const hasLocalContribution =
      snapshotKey(localTitle, localContent) !== snapshotKey(nextTitle, nextContent) &&
      snapshotKey(localTitle, localContent) !== snapshotKey(baseTitle, baseContent)
    const shouldMerge = Boolean(pending) || hasLocalContribution
    serverSnapshotKeyRef.current = snapshotKey(nextTitle, nextContent)
    lastConfirmedTitleRef.current = nextTitle
    lastConfirmedContentRef.current = nextContent
    const projectedTitle = shouldMerge ? mergeScalarSnapshot(baseTitle, nextTitle, localTitle) : nextTitle
    const projectedContent = shouldMerge ? mergeTextSnapshot(baseContent, nextContent, localContent) : nextContent
    if (shouldMerge && snapshotKey(projectedTitle, projectedContent) !== snapshotKey(nextTitle, nextContent)) {
      const localSeq = localSeqRef.current + 1
      localSeqRef.current = localSeq
      pendingUpdateRef.current = {
        title: projectedTitle,
        content: projectedContent,
        baseTitle: nextTitle,
        baseContent: nextContent,
        localSeq,
      }
      window.setTimeout(() => sendPendingUpdate(), 0)
    } else if (pending) {
      pendingUpdateRef.current = null
    }
    remoteSnapshotRef.current({
      itemId,
      title: projectedTitle,
      content: projectedContent,
      serverClock: serverClockRef.current,
      stateVector: String(payload.stateVector ?? serverClockRef.current),
      versionNo: typeof payload.versionNo === 'number' ? payload.versionNo : undefined,
    })
  }, [itemId, sendPendingUpdate, updateClock, updateOnlineUsers])

  const handleServerMessage = useCallback((data: string) => {
    let event: PlatformWebSocketEvent
    try {
      event = JSON.parse(data) as PlatformWebSocketEvent
    } catch {
      return
    }
    if (event.objectType && event.objectType !== 'knowledge_content') {
      return
    }
    const payload = (event.payload ?? {}) as Record<string, unknown>
    const eventItemId = String(payload.itemId ?? event.objectId ?? '')
    if (eventItemId && eventItemId !== itemId) {
      return
    }

    if (event.type === 'knowledge.content.snapshot') {
      joinedRef.current = true
      applySnapshot(payload)
      setStatus(sendPendingUpdate() ? 'saving' : 'synced')
      return
    }
    if (event.type === 'knowledge.content.update') {
      updateClock(payload)
      updateOnlineUsers(payload)
      const sourceClientId = String(payload.clientId ?? '')
      if (sourceClientId !== clientId) {
        applySnapshot(payload)
      } else {
        const confirmedTitle = String(payload.title ?? latestTitleRef.current)
        const confirmedContent = String(payload.content ?? latestContentRef.current)
        lastConfirmedTitleRef.current = confirmedTitle
        lastConfirmedContentRef.current = confirmedContent
        serverSnapshotKeyRef.current = snapshotKey(confirmedTitle, confirmedContent)
        setStatus('synced')
      }
      return
    }
    if (event.type === 'knowledge.content.awareness.update') {
      updateClock(payload)
      updateOnlineUsers(payload)
      if (joinedRef.current) {
        setStatus((current) => current === 'connecting' ? 'joined' : current)
      }
      return
    }
    if (event.type === 'knowledge.content.saved') {
      updateClock(payload)
      const savedAt = String(payload.savedAt ?? new Date().toISOString())
      setLastSavedAt(savedAt)
      setStatus('synced')
      savedRef.current?.({ serverClock: serverClockRef.current, savedAt })
      return
    }
    if (event.type === 'knowledge.content.error') {
      setStatus('error')
      setError(String(payload.message ?? '知识内容协同连接异常'))
    }
  }, [applySnapshot, clientId, itemId, sendPendingUpdate, updateClock, updateOnlineUsers])

  useEffect(() => {
    remoteSnapshotRef.current = onRemoteSnapshot
  }, [onRemoteSnapshot])

  useEffect(() => {
    latestTitleRef.current = title
    latestContentRef.current = content
  }, [content, title])

  useEffect(() => {
    savedRef.current = onSaved
  }, [onSaved])

  useEffect(() => {
    serverClockRef.current = Math.max(serverClockRef.current, versionNo)
  }, [versionNo])

  useEffect(() => {
    const resetKey = `${itemId}:${versionNo}`
    if (resetKeyRef.current === resetKey) {
      return
    }
    resetKeyRef.current = resetKey
    joinedRef.current = false
    pendingUpdateRef.current = null
    lastConfirmedTitleRef.current = title
    lastConfirmedContentRef.current = content
    serverSnapshotKeyRef.current = snapshotKey(title, content)
    lastSentKeyRef.current = ''
    serverClockRef.current = versionNo
    setOnlineUsers([])
    setLastSavedAt(null)
    setError(null)
  }, [content, itemId, title, versionNo])

  useEffect(() => {
    if (!enabled || !accessToken || !itemId) {
      return undefined
    }
    let closedByEffect = false

    const connect = () => {
      setStatus('connecting')
      setError(null)
      const socket = new WebSocket(`${WS_BASE_URL}?token=${encodeURIComponent(accessToken)}`)
      socketRef.current = socket

      socket.onopen = () => {
        socketRef.current = socket
        reconnectAttemptRef.current = 0
        joinedRef.current = false
        sendCommand('knowledge.content.join', {
          clientId,
          stateVector: String(serverClockRef.current),
        })
        sendCommand('knowledge.content.snapshot.request', {
          clientId,
          stateVector: String(serverClockRef.current),
        })
      }

      socket.onmessage = (message) => {
        handleServerMessage(message.data)
      }

      socket.onclose = () => {
        if (socketRef.current !== socket) {
          return
        }
        socketRef.current = null
        joinedRef.current = false
        if (closedByEffect) {
          setStatus('idle')
          return
        }
        setStatus('offline')
        reconnectAttemptRef.current += 1
        reconnectTimerRef.current = window.setTimeout(connect, reconnectDelay(reconnectAttemptRef.current))
      }

      socket.onerror = () => {
        if (socketRef.current === socket) {
          socket.close()
        }
      }
    }

    connect()

    return () => {
      closedByEffect = true
      if (reconnectTimerRef.current) {
        window.clearTimeout(reconnectTimerRef.current)
      }
      if (sendTimerRef.current) {
        window.clearTimeout(sendTimerRef.current)
      }
      sendCommand('knowledge.content.leave', { clientId })
      socketRef.current?.close()
    }
    // itemId intentionally reconnects the socket so room membership remains single-doc.
  }, [accessToken, clientId, enabled, handleServerMessage, itemId, sendCommand])

  useEffect(() => {
    if (!enabled || !canEdit || !itemId) {
      return
    }
    const key = snapshotKey(title, content)
    if (key === serverSnapshotKeyRef.current || key === lastSentKeyRef.current) {
      return
    }
    if (!joinedRef.current) {
      const localSeq = localSeqRef.current + 1
      localSeqRef.current = localSeq
      pendingUpdateRef.current = {
        title,
        content,
        baseTitle: lastConfirmedTitleRef.current,
        baseContent: lastConfirmedContentRef.current,
        localSeq,
      }
      setStatus(socketRef.current?.readyState === WebSocket.OPEN ? 'connecting' : 'offline')
      return
    }
    setStatus(socketRef.current?.readyState === WebSocket.OPEN ? 'dirty' : 'offline')
    if (sendTimerRef.current) {
      window.clearTimeout(sendTimerRef.current)
    }
    sendTimerRef.current = window.setTimeout(() => {
      queueUpdate(title, content)
    }, 450)
  }, [canEdit, content, enabled, itemId, queueUpdate, title])

  const sendAwareness = useCallback((cursor: KnowledgeContentCursor) => {
    if (!enabled || !itemId || socketRef.current?.readyState !== WebSocket.OPEN) {
      return
    }
    sendCommand('knowledge.content.awareness.update', {
      clientId,
      editing: canEdit,
      cursor,
    })
  }, [canEdit, clientId, enabled, itemId, sendCommand])

  const remoteCursors = useMemo(
    () => onlineUsers.filter((user) => user.clientId !== clientId && user.cursor),
    [clientId, onlineUsers],
  )

  return {
    status,
    clientId,
    onlineUsers,
    remoteCursors,
    lastSavedAt,
    error,
    sendAwareness,
  }

}

function stableClientId() {
  const key = 'colla-doc-client-id'
  const existing = window.localStorage.getItem(key)
  if (existing) {
    return existing
  }
  const value = window.crypto?.randomUUID?.() ?? `client-${Date.now()}-${Math.random().toString(16).slice(2)}`
  window.localStorage.setItem(key, value)
  return value
}

function snapshotKey(title: string, content: string) {
  return `${title}\u0000${content}`
}

function mergeScalarSnapshot(base: string, remote: string, local: string) {
  if (local === remote || local === base) {
    return remote
  }
  if (remote === base) {
    return local
  }
  return local
}

function mergeTextSnapshot(base: string, remote: string, local: string) {
  if (local === remote || local === base) {
    return remote
  }
  if (remote === base || local.includes(remote)) {
    return local
  }
  if (remote.includes(local)) {
    return remote
  }
  if (remote.startsWith(base) && local.startsWith(base)) {
    return appendUnique(remote, local.slice(base.length))
  }

  const mergedLines = new Set(splitNonEmptyLines(base))
  const lines: string[] = []
  for (const line of [...splitNonEmptyLines(remote), ...splitNonEmptyLines(local)]) {
    if (mergedLines.has(line)) {
      continue
    }
    mergedLines.add(line)
    lines.push(line)
  }
  return [base, ...lines].filter((line) => line.length > 0).join('\n')
}

function appendUnique(content: string, suffix: string) {
  if (!suffix || content.includes(suffix.trim())) {
    return content
  }
  if (content.endsWith('\n') || suffix.startsWith('\n')) {
    return content + suffix
  }
  return `${content}\n${suffix}`
}

function splitNonEmptyLines(content: string) {
  return content
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
}

function requestId() {
  return window.crypto?.randomUUID?.() ?? `request-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function reconnectDelay(attempt: number) {
  const base = Math.min(15000, 1000 * 2 ** Math.max(0, attempt - 1))
  return base + Math.floor(Math.random() * 300)
}

function normalizeCollaborator(value: unknown): KnowledgeContentCollaborator | null {
  if (!value || typeof value !== 'object') {
    return null
  }
  const item = value as Record<string, unknown>
  return {
    userId: String(item.userId ?? ''),
    username: String(item.username ?? ''),
    displayName: String(item.displayName ?? item.username ?? '协作者'),
    clientId: String(item.clientId ?? ''),
    color: String(item.color ?? '#1677ff'),
    cursor: normalizeCursor(item.cursor),
    editing: Boolean(item.editing),
    seenAt: item.seenAt ? String(item.seenAt) : undefined,
  }
}

function normalizeCursor(value: unknown): KnowledgeContentCursor | null {
  if (!value || typeof value !== 'object') {
    return null
  }
  const cursor = value as Record<string, unknown>
  const from = Number(cursor.from ?? 0)
  const to = Number(cursor.to ?? from)
  if (!Number.isFinite(from) || from <= 0) {
    return null
  }
  return {
    from,
    to: Number.isFinite(to) && to > 0 ? to : from,
    empty: Boolean(cursor.empty ?? from === to),
  }
}

