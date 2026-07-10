type StatusBadgeProps = {
  status: string
  activeValue?: string
}

export function StatusBadge({ status, activeValue = 'active' }: StatusBadgeProps) {
  const tone = status === activeValue ? 'active' : 'disabled'
  return <span className={`status-badge ${tone}`}>{status}</span>
}
