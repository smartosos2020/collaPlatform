import { createContext, useContext, useEffect, useRef } from 'react'

import type {
  RealtimeCalibrationRequest,
  RealtimeConnectionStatus,
  RealtimeDiagnosticsSnapshot,
} from './connection'
import type { KnownRealtimeType, RealtimeEnvelope } from './protocol'

export type RealtimeContextValue = {
  accessToken: string
  status: RealtimeConnectionStatus
  retry: () => void
  stop: () => void
  markCalibrated: () => void
  diagnostics: RealtimeDiagnosticsSnapshot | null
  subscribe: (listener: (envelope: RealtimeEnvelope) => void) => () => void
  subscribeCalibration: (listener: (request: RealtimeCalibrationRequest) => void) => () => void
}

export const RealtimeContext = createContext<RealtimeContextValue | null>(null)

export function useRealtimeConnection() {
  const context = useContext(RealtimeContext)
  if (!context) {
    throw new Error('useRealtimeConnection must be used inside RealtimeProvider')
  }
  return context
}

export function useOptionalRealtimeConnection() {
  return useContext(RealtimeContext)
}

export function useRealtimeStatus() {
  const context = useRealtimeConnection()
  return {
    status: context.status,
    retry: context.retry,
    recovered: context.status === 'ready' && context.diagnostics?.lastCalibrationReason === 'reconnected',
    diagnostics: context.diagnostics,
  } as const
}

export function useRealtimeSubscription(
  types: readonly KnownRealtimeType[] | null,
  handler: (envelope: RealtimeEnvelope) => void,
) {
  const context = useOptionalRealtimeConnection()
  const handlerRef = useRef(handler)
  const typeKey = types ? [...types].sort().join('\u0000') : '*'

  useEffect(() => {
    handlerRef.current = handler
  }, [handler])

  useEffect(() => {
    if (!context) return
    const acceptedTypes = types ? new Set<KnownRealtimeType>(types) : null
    return context.subscribe((envelope) => {
      if (!acceptedTypes || acceptedTypes.has(envelope.type)) {
        handlerRef.current(envelope)
      }
    })
  }, [context, typeKey, types])
}

export function useRealtimeCalibration(handler: (request: RealtimeCalibrationRequest) => void) {
  const context = useOptionalRealtimeConnection()
  const handlerRef = useRef(handler)

  useEffect(() => {
    handlerRef.current = handler
  }, [handler])

  useEffect(() => context?.subscribeCalibration((request) => handlerRef.current(request)), [context])
}
