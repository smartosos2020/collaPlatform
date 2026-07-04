import { useMutation, useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Skeleton, Space, Tag, Typography } from 'antd'
import { LinkOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'

import { resolveNavigationPath } from '../../../shared/client/collaClient'
import { getObjectNavigation, getPermissionExplanation, resolveInternalLink } from '../api/platformObjectsApi'
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
    return <Alert type="warning" message="链接解析失败" showIcon />
  }

  const summary = linkQuery.data.summary
  if (summary.accessState !== 'available') {
    return <UnavailableObjectAlert summary={summary} />
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
  if (summary.accessState !== 'available') {
    return <UnavailableObjectAlert summary={summary} />
  }
  const canOpen = summary.accessState === 'available' && Boolean(summary.webPath || summary.objectId)
  const knowledgePath = metadataText(summary.metadata.knowledgePath)
  const knowledgeBaseName = metadataText(summary.metadata.knowledgeBaseName)
  const displayTitle = summary.objectType === 'document' && knowledgePath ? knowledgePath : summary.title || '未命名对象'
  const displaySubtitle = summary.objectType === 'document' && knowledgePath ? summary.title : summary.subtitle
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
            <Typography.Text strong>{displayTitle}</Typography.Text>
            <Tag>{objectTypeText[summary.objectType] ?? summary.objectType}</Tag>
            {summary.status ? <Tag color="blue">{summary.status}</Tag> : null}
            <Tag color="green">{accessText[summary.accessState] ?? summary.accessState}</Tag>
          </Space>
          {displaySubtitle ? <Typography.Text type="secondary">{displaySubtitle}</Typography.Text> : null}
          <Space wrap size={4} className="internal-link-meta">
            {metadataText(summary.metadata.sourceModule) ? <Tag>来源 {moduleText(metadataText(summary.metadata.sourceModule))}</Tag> : null}
            {knowledgeBaseName ? <Tag color="purple">{knowledgeBaseName}</Tag> : null}
            {metadataText(summary.metadata.updatedAt) ? <Tag>更新 {formatMetaDate(metadataText(summary.metadata.updatedAt))}</Tag> : null}
          </Space>
        </Space>
        <Space size={4}>
          {metadataText(summary.metadata.backReferencePath) ? <Button size="small" href={metadataText(summary.metadata.backReferencePath)}>回看引用</Button> : null}
          {canOpen ? (
            <Button size="small" loading={openMutation.isPending} onClick={handleOpen}>
              {summary.objectType === 'document' ? '打开知识内容' : '打开'}
            </Button>
          ) : null}
        </Space>
      </Space>
    </Card>
  )
}

function UnavailableObjectAlert({ summary }: { summary: PlatformObjectSummary }) {
  const explanationQuery = useQuery({
    queryKey: ['platform', 'permission-explanation', summary.objectType, summary.objectId, 'view'],
    queryFn: () => getPermissionExplanation(summary.objectType, summary.objectId, 'view'),
    enabled: Boolean(summary.objectType && summary.objectId),
  })
  const explanation = explanationQuery.data
  return (
    <Alert
      type="warning"
      showIcon
      message={accessText[summary.accessState] ?? '对象不可访问'}
      description={
        explanation
          ? `${explanation.reason} 来源：${explanation.source}`
          : objectTypeText[summary.objectType] ?? summary.objectType
      }
    />
  )
}

function metadataText(value: unknown) {
  return typeof value === 'string' && value.trim() ? value : ''
}

function moduleText(value: string) {
  return {
    knowledge: '知识库',
    project: '项目',
    base: '表格',
    approval: '审批',
    im: '消息',
  }[value] ?? value
}

function formatMetaDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}
