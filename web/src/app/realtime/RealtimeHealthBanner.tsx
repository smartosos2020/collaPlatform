import { Alert, Button } from 'antd'
import { useEffect, useRef, useState } from 'react'

import {
  useRealtimeStatus,
  type RealtimeConnectionStatus,
} from '../../shared/realtime'

const DEGRADED_VISIBILITY_DELAY_MS = 1_500
const RECOVERY_VISIBILITY_MS = 2_500

export function RealtimeHealthBanner() {
  const { status, retry } = useRealtimeStatus()
  const [visibleStatus, setVisibleStatus] = useState<RealtimeConnectionStatus | 'recovered' | null>(null)
  const degradationWasVisibleRef = useRef(false)
  const recoveryTimerRef = useRef<number | null>(null)

  useEffect(() => {
    if (recoveryTimerRef.current !== null) {
      window.clearTimeout(recoveryTimerRef.current)
      recoveryTimerRef.current = null
    }
    const delay = status === 'degraded' || status === 'reconnecting'
      ? DEGRADED_VISIBILITY_DELAY_MS
      : status === 'connecting'
        ? DEGRADED_VISIBILITY_DELAY_MS * 2
        : 0
    const timer = window.setTimeout(() => {
      if (status === 'ready') {
        if (degradationWasVisibleRef.current) {
          degradationWasVisibleRef.current = false
          setVisibleStatus('recovered')
          recoveryTimerRef.current = window.setTimeout(() => {
            recoveryTimerRef.current = null
            setVisibleStatus(null)
          }, RECOVERY_VISIBILITY_MS)
        } else {
          setVisibleStatus(null)
        }
      } else if (status === 'degraded' || status === 'reconnecting') {
        degradationWasVisibleRef.current = true
        setVisibleStatus(status)
      } else if (status === 'connecting') {
        setVisibleStatus('connecting')
      } else {
        setVisibleStatus(null)
      }
    }, delay)
    return () => {
      window.clearTimeout(timer)
      if (recoveryTimerRef.current !== null) {
        window.clearTimeout(recoveryTimerRef.current)
        recoveryTimerRef.current = null
      }
    }
  }, [status])

  if (!visibleStatus) {
    return null
  }
  if (visibleStatus === 'recovered') {
    return (
      <Alert
        className="app-realtime-banner"
        data-testid="realtime-health-banner"
        type="success"
        showIcon
        message="实时连接已恢复，页面数据已完成校准。"
      />
    )
  }

  const connecting = visibleStatus === 'connecting'
  return (
    <Alert
      className="app-realtime-banner"
      data-testid="realtime-health-banner"
      type={connecting ? 'info' : 'warning'}
      showIcon
      message={connecting
        ? '正在建立实时连接，当前数据仍以服务端查询结果为准。'
        : '实时更新暂时不可用，页面会在恢复后自动校准。'}
      action={connecting ? null : <Button size="small" onClick={retry}>重试</Button>}
    />
  )
}
