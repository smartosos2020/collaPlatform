import { useEffect, useRef } from 'react'

import { useOptionalRealtimeConnection } from '../realtime/RealtimeContext'
import type { PlatformWebSocketEvent } from './websocketEvents'

export type WebSocketStatus = 'idle' | 'connecting' | 'connected' | 'disconnected'

export function useWebSocketConnection(
  accessToken: string | null,
  onEvent: (event: PlatformWebSocketEvent) => void,
) {
  const realtime = useOptionalRealtimeConnection()
  const eventHandlerRef = useRef(onEvent)

  useEffect(() => {
    eventHandlerRef.current = onEvent
  }, [onEvent])

  useEffect(() => {
    if (!realtime || !accessToken || realtime.accessToken !== accessToken) return
    return realtime.subscribe((event) => eventHandlerRef.current(event))
  }, [accessToken, realtime])

  if (!accessToken) return 'idle'
  if (!realtime || realtime.accessToken !== accessToken) return 'disconnected'
  return legacyStatus(realtime.status)
}

function legacyStatus(status: NonNullable<ReturnType<typeof useOptionalRealtimeConnection>>['status']): WebSocketStatus {
  if (status === 'stopped') return 'idle'
  if (status === 'connecting') return 'connecting'
  if (status === 'ready') return 'connected'
  return 'disconnected'
}
