import type { ReactNode } from 'react'
import { Typography } from 'antd'

type TableEmptyStateProps = {
  icon?: ReactNode
  description?: string
}

export function TableEmptyState({ icon, description = 'No data' }: TableEmptyStateProps) {
  return (
    <div className="table-empty-state">
      {icon ? <span className="empty-state-icon">{icon}</span> : null}
      <Typography.Text type="secondary">{description}</Typography.Text>
    </div>
  )
}
