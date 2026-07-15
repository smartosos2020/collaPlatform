import { useEffect, useRef, useState } from 'react'

import { useAuthStore } from '../../modules/auth/authStore'
import type { PlatformWebSocketEvent } from './websocketEvents'

const WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL ?? defaultWebSocketUrl()

function defaultWebSocketUrl() {
  if (!import.meta.env.PROD) return 'ws://localhost:8080/ws/events'
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws/events`
}

export type WebSocketStatus = 'idle' | 'connecting' | 'connected' | 'disconnected'

export function useWebSocketConnection(onEvent: (event: PlatformWebSocketEvent) => void) {
  const accessToken = useAuthStore((state) => state.accessToken)
  const [status, setStatus] = useState<WebSocketStatus>('idle')
  const eventHandlerRef = useRef(onEvent)
  const reconnectAttemptRef = useRef(0)
  const reconnectTimerRef = useRef<number | null>(null)
  const seenEventIdsRef = useRef<string[]>([])
  const socketRef = useRef<WebSocket | null>(null)

  useEffect(() => {
    eventHandlerRef.current = onEvent
  }, [onEvent])

  useEffect(() => {
    if (!accessToken) {
      return undefined
    }

    let closedByEffect = false

    const connect = () => {
      setStatus('connecting')
      const socket = new WebSocket(`${WS_BASE_URL}?token=${encodeURIComponent(accessToken)}`)
      socketRef.current = socket

      socket.onopen = () => {
        reconnectAttemptRef.current = 0
        setStatus('connected')
      }
      socket.onmessage = (message) => {
        try {
          const event = JSON.parse(message.data) as PlatformWebSocketEvent
          if (event.eventId && hasSeenEvent(event.eventId, seenEventIdsRef.current)) {
            return
          }
          if (event.eventId) {
            rememberEvent(event.eventId, seenEventIdsRef.current)
          }
          eventHandlerRef.current(event)
        } catch {
          // Ignore malformed server frames; REST sync remains the source of truth.
        }
      }
      socket.onclose = () => {
        socketRef.current = null
        if (closedByEffect) {
          setStatus('idle')
          return
        }
        setStatus('disconnected')
        reconnectAttemptRef.current += 1
        reconnectTimerRef.current = window.setTimeout(connect, reconnectDelay(reconnectAttemptRef.current))
      }
      socket.onerror = () => {
        socket.close()
      }
    }

    connect()

    return () => {
      closedByEffect = true
      if (reconnectTimerRef.current) {
        window.clearTimeout(reconnectTimerRef.current)
      }
      socketRef.current?.close()
    }
  }, [accessToken])

  return status
}

function reconnectDelay(attempt: number) {
  const base = Math.min(15000, 1000 * 2 ** Math.max(0, attempt - 1))
  return base + Math.floor(Math.random() * 300)
}

function hasSeenEvent(eventId: string, seenEventIds: string[]) {
  return seenEventIds.includes(eventId)
}

function rememberEvent(eventId: string, seenEventIds: string[]) {
  seenEventIds.push(eventId)
  if (seenEventIds.length > 200) {
    seenEventIds.splice(0, seenEventIds.length - 200)
  }
}
