import { DownOutlined, RightOutlined } from '@ant-design/icons'
import { Card } from 'antd'
import { useState, type ComponentProps } from 'react'

type CollapsibleWorkItemCardProps = ComponentProps<typeof Card> & {
  collapseLabel: string
  defaultCollapsed?: boolean
}

export function CollapsibleWorkItemCard({
  collapseLabel,
  defaultCollapsed = false,
  className,
  title,
  children,
  ...cardProps
}: CollapsibleWorkItemCardProps) {
  const [collapsed, setCollapsed] = useState(defaultCollapsed)
  const action = collapsed ? '展开' : '收起'

  return (
    <Card
      {...cardProps}
      className={`collapsible-work-item-card${collapsed ? ' is-collapsed' : ''}${className ? ` ${className}` : ''}`}
      title={(
        <button
          type="button"
          className="collapsible-work-item-card-trigger"
          aria-expanded={!collapsed}
          aria-label={`${action}${collapseLabel}`}
          onClick={() => setCollapsed((current) => !current)}
        >
          {collapsed ? <RightOutlined /> : <DownOutlined />}
          <span>{title}</span>
        </button>
      )}
    >
      {children}
    </Card>
  )
}
