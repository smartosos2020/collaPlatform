import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Badge, Button, Empty, Select, Space, Tag, Typography } from 'antd'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { InternalLinkCard } from '../../platform/components/InternalLinkCard'
import {
  getUnreadCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from '../api/notificationsApi'

type StatusFilter = 'all' | 'unread' | 'read'

export function NotificationsPage() {
  const [status, setStatus] = useState<StatusFilter>('all')
  const [source, setSource] = useState('all')
  const [targetType, setTargetType] = useState('all')
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const notificationFilters = {
    status: status === 'all' ? undefined : status,
    source: source === 'all' ? undefined : source,
    targetType: targetType === 'all' ? undefined : targetType,
    limit: 100,
  }

  const notificationsQuery = useQuery({
    queryKey: ['notifications', notificationFilters],
    queryFn: () => listNotifications(notificationFilters),
  })
  const unreadCountQuery = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: getUnreadCount,
  })

  const refreshNotifications = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['notifications'] }),
      queryClient.invalidateQueries({ queryKey: ['notifications', 'unread-count'] }),
    ])
  }

  const markReadMutation = useMutation({
    mutationFn: markNotificationRead,
    onSuccess: refreshNotifications,
  })
  const markAllReadMutation = useMutation({
    mutationFn: markAllNotificationsRead,
    onSuccess: refreshNotifications,
  })

  const notifications = notificationsQuery.data ?? []

  return (
    <Space orientation="vertical" size={16} className="page-stack">
      <Space className="page-toolbar">
        <Space>
          <Typography.Title level={2}>通知</Typography.Title>
          <Badge count={unreadCountQuery.data?.count ?? 0} />
        </Space>
        <Space>
          <Button loading={markAllReadMutation.isPending} onClick={() => markAllReadMutation.mutate()}>
            全部已读
          </Button>
        </Space>
      </Space>

      <Space wrap className="notification-filters">
        <Select
          value={status}
          onChange={setStatus}
          options={[
            { label: '全部状态', value: 'all' },
            { label: '未读', value: 'unread' },
            { label: '已读', value: 'read' },
          ]}
        />
        <Select
          value={source}
          onChange={setSource}
          options={[
            { label: '全部来源', value: 'all' },
            { label: '项目', value: 'issue' },
            { label: '文档', value: 'document' },
            { label: 'IM', value: 'mention' },
            { label: '表格', value: 'base' },
          ]}
        />
        <Select
          value={targetType}
          onChange={setTargetType}
          options={[
            { label: '全部对象', value: 'all' },
            { label: '需求/Bug', value: 'issue' },
            { label: '文档', value: 'document' },
            { label: '多维表格', value: 'base' },
            { label: '表格记录', value: 'base_record' },
          ]}
        />
      </Space>

      <Space orientation="vertical" size={10} className="notification-card-list">
        {notificationsQuery.isLoading ? <Typography.Text type="secondary">加载中...</Typography.Text> : null}
        {notifications.length === 0 && !notificationsQuery.isLoading ? <Empty description="暂无通知" /> : null}
        {notifications.map((item) => (
          <div className="notification-card-item" key={item.id}>
            <div>
              <Space wrap>
                <Typography.Text strong={!item.readAt}>{item.title}</Typography.Text>
                <Tag>{item.notificationType}</Tag>
                {item.readAt ? <Tag>已读</Tag> : <Tag color="blue">未读</Tag>}
              </Space>
              <Space orientation="vertical" size={4} className="notification-card-body">
                {item.body ? <Typography.Text type="secondary">{item.body}</Typography.Text> : null}
                {item.webPath ? <InternalLinkCard link={item.webPath} /> : null}
                {!item.webPath && item.targetType && item.targetId ? (
                  <Typography.Text type="secondary">
                    {item.targetType} / {item.targetId}
                  </Typography.Text>
                ) : null}
              </Space>
            </div>
            <Space>
              {item.webPath ? (
                <Button type="link" onClick={() => navigate(item.webPath || '/')}>
                  打开
                </Button>
              ) : null}
              {item.readAt ? null : (
                <Button type="link" loading={markReadMutation.isPending} onClick={() => markReadMutation.mutate(item.id)}>
                  标为已读
                </Button>
              )}
            </Space>
          </div>
        ))}
      </Space>
    </Space>
  )
}
