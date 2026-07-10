import type { PropsWithChildren } from 'react'

type SoftBadgeProps = PropsWithChildren<{
  tone?: 'purple' | 'blue' | 'gray'
}>

export function SoftBadge({ tone = 'gray', children }: SoftBadgeProps) {
  return <span className={`soft-badge ${tone}`}>{children}</span>
}
