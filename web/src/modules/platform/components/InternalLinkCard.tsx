import { useMutation, useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Skeleton, Space, Tag, Typography } from 'antd'
import { LinkOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'

import { resolveNavigationPath } from '../../../shared/client/collaClient'
import { getObjectNavigation, resolveInternalLink } from '../api/platformObjectsApi'
import type { PlatformObjectSummary } from '../api/platformObjectsApi'
import { objectTypeText } from '../objectTypeLabels'

type InternalLinkCardProps = {
  link: string
}

const accessText: Record<string, string> = {
  available: '可打开',
  forbidden: '无权限查看',
  deleted: '对象已删除',
  not_found: '对象不存在',
  invalid: '链接无法识别',
}

export function InternalLinkCard({ link }: InternalLinkCardProps) {
  const linkQuery = useQuery({
    queryKey: ['platform', 'link', link],
    queryFn: () => resolveInternalLink(link),
  })

  if (linkQuery.isLoading) {
    return (
      <Card size="small">
        <Skeleton active paragraph={false} />
      </Card>
    )
  }

  if (linkQuery.isError || !linkQuery.data?.summary) {
    return <Alert type="warning" title="链接解析失败" showIcon />
  }

  const summary = linkQuery.data.summary
  if (summary.accessState !== 'available') {
    return <Alert type="warning" title={accessText[summary.accessState] ?? '对象不可访问'} showIcon />
  }

  return <ObjectSummaryCard summary={summary} />
}

export function ObjectSummaryCard({ summary, onOpen }: { summary: PlatformObjectSummary; onOpen?: () => void }) {
  const navigate = useNavigate()
  const openMutation = useMutation({
    mutationFn: () => getObjectNavigation(summary.objectType, summary.objectId),
    onSuccess: (navigation) => {
      const target = resolveNavigationPath(navigation) || resolveNavigationPath(summary)
      if (target) {
        navigate(target)
      }
    },
  })
  const canOpen = summary.accessState === 'available' && Boolean(summary.webPath || summary.objectId)
  const handleOpen = () => {
    if (onOpen) {
      onOpen()
      return
    }
    openMutation.mutate()
  }

  return (
    <Card size="small" className="internal-link-card">
      <Space className="internal-link-content">
        <Space orientation="vertical" size={2}>
          <Space size={8}>
            <LinkOutlined />
            <Typography.Text strong>{summary.title || '未命名对象'}</Typography.Text>
            <Tag>{objectTypeText[summary.objectType] ?? summary.objectType}</Tag>
            {summary.status ? <Tag color="blue">{summary.status}</Tag> : null}
          </Space>
          {summary.subtitle ? <Typography.Text type="secondary">{summary.subtitle}</Typography.Text> : null}
        </Space>
        {canOpen ? (
          <Button size="small" loading={openMutation.isPending} onClick={handleOpen}>
            打开
          </Button>
        ) : null}
      </Space>
    </Card>
  )
}
