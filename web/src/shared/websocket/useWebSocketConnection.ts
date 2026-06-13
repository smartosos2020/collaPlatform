import { useEffect, useRef, useState } from 'react'

import { useAuthStore } from '../../modules/auth/authStore'
import type { PlatformWebSocketEvent } from './websocketEvents'

const WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL ?? 'ws://localhost:8080/ws/events'

export type WebSocketStatus = 'idle' | 'connecting' | 'connected' | 'disconnected'

export function useWebSocketConnection(onEvent: (event: PlatformWebSocketEvent) => void) {
  const accessToken = useAuthStore((state) => state.accessToken)
  const [status, setStatus] = useState<WebSocketStatus>('idle')
  const eventHandlerRef = useRef(onEvent)
  const reconnectTimerRef = useRef<number | null>(null)
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

      socket.onopen = () => setStatus('connected')
      socket.onmessage = (message) => {
        try {
          eventHandlerRef.current(JSON.parse(message.data) as PlatformWebSocketEvent)
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
        reconnectTimerRef.current = window.setTimeout(connect, 1500)
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
