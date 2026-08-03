import {
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'

import { refreshSessionSingleFlight } from '../auth/sessionRefresh'
import {
  RealtimeConnection,
  type RealtimeConnectionStatus,
  type RealtimeDiagnosticsSnapshot,
  type RealtimeOnlineSource,
  type RealtimeScheduler,
  type RealtimeSocket,
} from './connection'
import { RealtimeContext, type RealtimeContextValue } from './RealtimeContext'

export type RealtimeProviderProps = {
  accessToken: string | null
  workspaceId: string | null
  userId: string | null
  children: ReactNode
  url?: string
  socketFactory?: (url: string) => RealtimeSocket
  scheduler?: RealtimeScheduler
  onlineSource?: RealtimeOnlineSource
  random?: () => number
  readyTimeoutMs?: number
}

export function RealtimeProvider({
  accessToken,
  workspaceId,
  userId,
  children,
  url = defaultWebSocketUrl(),
  socketFactory = defaultSocketFactory,
  scheduler = browserScheduler,
  onlineSource = browserOnlineSource,
  random,
  readyTimeoutMs,
}: RealtimeProviderProps) {
  const [reconnectExhaustedCount, setReconnectExhaustedCount] = useState(0)
  const [connection] = useState(() => new RealtimeConnection({
    socketFactory,
    scheduler,
    onlineSource,
    random,
    readyTimeoutMs,
    onReconnectExhausted: () => {
      setReconnectExhaustedCount((current) => current + 1)
    },
  }))
  const [status, setStatus] = useState<RealtimeConnectionStatus>(connection.getStatus())
  const [diagnostics, setDiagnostics] = useState<RealtimeDiagnosticsSnapshot>(connection.getDiagnostics())

  useEffect(() => connection.subscribeStatus(setStatus), [connection])
  useEffect(() => connection.subscribeDiagnostics(setDiagnostics), [connection])

  // 自动重连耗尽（多见于 WS 握手 401，浏览器侧只表现为 1006）时：
  // 先单飞刷新会话；成功会写回 authStore，accessToken 变化触发下方 start 以新身份重连；
  // 失败则保持 degraded，交给用户手动重试或路由守卫接管。
  useEffect(() => {
    if (reconnectExhaustedCount === 0 || !accessToken) {
      return
    }
    let cancelled = false
    void refreshSessionSingleFlight()
      .then(() => {
        if (cancelled) return
        // 成功刷新会更新 accessToken，并由下方 effect 以新凭据重启连接；
        // refresh token 被拒绝时保持 degraded，不得重置失败计数进入新的循环。
      })
      .catch(() => {
        // 网络或刷新服务异常同样保持 degraded，等待用户手动重试。
      })
    return () => {
      cancelled = true
    }
  }, [accessToken, connection, reconnectExhaustedCount])

  useEffect(() => {
    if (!accessToken || !workspaceId || !userId) {
      connection.stop()
      return
    }
    connection.start({ accessToken, workspaceId, userId, url })
    return () => connection.stop()
  }, [accessToken, connection, url, userId, workspaceId])

  const value = useMemo<RealtimeContextValue | null>(() => {
    if (!accessToken) return null
    return {
      accessToken,
      status,
      retry: () => connection.retry(),
      stop: () => connection.stop(),
      markCalibrated: () => connection.markCalibrated(),
      diagnostics: import.meta.env.PROD ? null : diagnostics,
      subscribe: (listener) => connection.subscribe(listener),
      subscribeCalibration: (listener) => connection.subscribeCalibration(listener),
    }
  }, [accessToken, connection, diagnostics, status])

  return (
    <RealtimeContext.Provider value={value}>
      {children}
      {!import.meta.env.PROD && value ? <RealtimeDiagnosticsNode value={value} /> : null}
    </RealtimeContext.Provider>
  )
}

function defaultSocketFactory(url: string): RealtimeSocket {
  const socket = new WebSocket(url)
  const adapter: RealtimeSocket = {
    onopen: null,
    onmessage: null,
    onclose: null,
    onerror: null,
    close: (code, reason) => socket.close(code, reason),
  }
  socket.addEventListener('open', (event) => adapter.onopen?.(event))
  socket.addEventListener('message', (event) => adapter.onmessage?.({ data: event.data }))
  socket.addEventListener('close', (event) => adapter.onclose?.({ code: event.code, reason: event.reason }))
  socket.addEventListener('error', (event) => adapter.onerror?.(event))
  return adapter
}

function defaultWebSocketUrl() {
  const configuredUrl = import.meta.env.VITE_WS_BASE_URL
  if (configuredUrl) return configuredUrl
  if (!import.meta.env.PROD) return 'ws://localhost:8080/ws/events'
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws/events`
}

const browserScheduler: RealtimeScheduler = {
  setTimeout: (callback, delayMs) => window.setTimeout(callback, delayMs),
  clearTimeout: (timer) => window.clearTimeout(timer as number),
  setInterval: (callback, intervalMs) => window.setInterval(callback, intervalMs),
  clearInterval: (timer) => window.clearInterval(timer as number),
}

const browserOnlineSource: RealtimeOnlineSource = {
  isOnline: () => navigator.onLine,
  subscribe: (onOnline, onOffline) => {
    window.addEventListener('online', onOnline)
    window.addEventListener('offline', onOffline)
    return () => {
      window.removeEventListener('online', onOnline)
      window.removeEventListener('offline', onOffline)
    }
  },
}

function RealtimeDiagnosticsNode({ value }: { value: RealtimeContextValue }) {
  const diagnostics = value.diagnostics
  return (
    <output
      hidden
      data-testid="realtime-diagnostics"
      data-state={value.status}
      data-instance={diagnostics?.instanceId ?? ''}
      data-reconnect-attempt={diagnostics?.reconnectAttempt ?? 0}
      data-accepted={diagnostics?.acceptedEvents ?? 0}
      data-duplicate={diagnostics?.duplicateEvents ?? 0}
      data-stale={diagnostics?.staleEvents ?? 0}
      data-gap={diagnostics?.gapEvents ?? 0}
      data-calibration-count={diagnostics?.calibrationRequests ?? 0}
      data-last-calibration={diagnostics?.lastCalibrationReason ?? 'none'}
      data-last-sequence-decision={diagnostics?.lastSequenceDecision ?? 'none'}
    />
  )
}
