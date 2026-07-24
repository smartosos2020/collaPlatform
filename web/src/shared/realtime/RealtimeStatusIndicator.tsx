import type { RealtimeConnectionStatus } from './connection'
import { useRealtimeStatus } from './RealtimeContext'

export function RealtimeStatusIndicator({ className }: { className?: string }) {
  const { status, recovered, diagnostics } = useRealtimeStatus()
  return (
    <span
      className={className}
      data-testid="realtime-connection-status"
      data-state={status}
      data-recovered={recovered ? 'true' : 'false'}
      data-gap-count={diagnostics?.gapEvents ?? 0}
      data-last-calibration={diagnostics?.lastCalibrationReason ?? 'none'}
      role="status"
      aria-live="polite"
    >
      {statusLabel(status, recovered)}
    </span>
  )
}

function statusLabel(status: RealtimeConnectionStatus, recovered: boolean) {
  if (recovered) return '实时连接已恢复'
  if (status === 'ready') return '实时连接正常'
  if (status === 'connecting') return '正在连接'
  if (status === 'reconnecting') return '正在恢复连接'
  if (status === 'degraded') return '实时连接降级'
  return '实时连接已停止'
}
