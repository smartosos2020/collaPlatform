import { Typography } from 'antd'
import type { ReactNode } from 'react'

type PageHeaderProps = {
  title: string
  meta?: ReactNode
  description?: ReactNode
  actions?: ReactNode
  className?: string
}

/**
 * Reusable page title area. Keeps the existing `.page-toolbar` layout
 * semantics (full-width row, title left, actions right) while rendering a
 * semantic <header> element. The heading renders only `title` so its
 * accessible name stays exact; `meta` sits beside it on a separate flex row.
 * Purely presentational — no business behavior.
 */
export function PageHeader({ title, meta, description, actions, className }: PageHeaderProps) {
  const classes = ['page-header', 'page-toolbar', className].filter(Boolean).join(' ')
  return (
    <header className={classes}>
      <div className="page-header-main">
        <div className="page-header-title-row">
          <Typography.Title level={2} className="page-header-title">
            {title}
          </Typography.Title>
          {meta}
        </div>
        {description ? (
          <Typography.Text type="secondary" className="page-header-description">
            {description}
          </Typography.Text>
        ) : null}
      </div>
      {actions ? <div className="page-header-actions">{actions}</div> : null}
    </header>
  )
}
