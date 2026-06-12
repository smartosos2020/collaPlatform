import { Card, Typography } from 'antd'

type PagePlaceholderProps = {
  title: string
  description: string
}

export function PagePlaceholder({ title, description }: PagePlaceholderProps) {
  return (
    <Card className="page-card">
      <Typography.Title level={3}>{title}</Typography.Title>
      <Typography.Paragraph type="secondary">{description}</Typography.Paragraph>
    </Card>
  )
}

