type StatusBadgeProps = {
  status: string
  activeValue?: string
}

export function StatusBadge({ status, activeValue = 'active' }: StatusBadgeProps) {
  const tone = status === activeValue ? 'active' : 'disabled'
  const label = { active: '启用', disabled: '停用', archived: '已归档' }[status] ?? status
  return <span className={`status-badge ${tone}`}>{label}</span>
}
