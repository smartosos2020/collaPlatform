import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Badge, Button, Checkbox, Empty, Select, Space, Tag, Typography } from 'antd'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { InternalLinkCard } from '../../platform/components/InternalLinkCard'
import { useWebSocketConnection } from '../../../shared/websocket/useWebSocketConnection'
import type { PlatformWebSocketEvent } from '../../../shared/websocket/websocketEvents'
import {
  getUnreadCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  markNotificationsRead,
} from '../api/notificationsApi'

type StatusFilter = 'all' | 'unread' | 'read'

export function NotificationsPage() {
  const [status, setStatus] = useState<StatusFilter>('all')
  const [source, setSource] = useState('all')
  const [targetType, setTargetType] = useState('all')
  const [selectedIds, setSelectedIds] = useState<string[]>([])
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

  useWebSocketConnection((event: PlatformWebSocketEvent) => {
    if (['notification.created', 'notification.read', 'notification.unread.changed'].includes(event.type)) {
      void refreshNotifications()
    }
  })

  const markReadMutation = useMutation({
    mutationFn: markNotificationRead,
    onSuccess: refreshNotifications,
  })
  const markAllReadMutation = useMutation({
    mutationFn: markAllNotificationsRead,
    onSuccess: refreshNotifications,
  })
  const markSelectedReadMutation = useMutation({
    mutationFn: markNotificationsRead,
    onSuccess: async () => {
      setSelectedIds([])
      await refreshNotifications()
    },
  })

  const notifications = notificationsQuery.data ?? []
  const unreadSelectedIds = selectedIds.filter((id) => notifications.find((item) => item.id === id && !item.readAt))
  const allVisibleSelected = notifications.length > 0 && notifications.every((item) => selectedIds.includes(item.id))
  const toggleSelected = (notificationId: string, checked: boolean) => {
    setSelectedIds((current) =>
      checked ? [...new Set([...current, notificationId])] : current.filter((id) => id !== notificationId),
    )
  }

  return (
    <Space orientation="vertical" size={16} className="page-stack">
      <Space className="page-toolbar">
        <Space>
          <Typography.Title level={2}>通知</Typography.Title>
          <Badge count={unreadCountQuery.data?.count ?? 0} />
        </Space>
        <Space>
          <Button
            disabled={unreadSelectedIds.length === 0}
            loading={markSelectedReadMutation.isPending}
            onClick={() => markSelectedReadMutation.mutate(unreadSelectedIds)}
          >
            批量已读
          </Button>
          <Button loading={markAllReadMutation.isPending} onClick={() => markAllReadMutation.mutate()}>
            全部已读
          </Button>
        </Space>
      </Space>

      <Space wrap className="notification-filters">
        <Checkbox
          checked={allVisibleSelected}
          indeterminate={selectedIds.length > 0 && !allVisibleSelected}
          onChange={(event) => {
            setSelectedIds(event.target.checked ? notifications.map((item) => item.id) : [])
          }}
        >
          选择当前页
        </Checkbox>
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
            { label: '知识内容', value: 'document' },
            { label: 'IM', value: 'mention' },
            { label: '表格', value: 'base' },
            { label: '审批', value: 'approval' },
          ]}
        />
        <Select
          value={targetType}
          onChange={setTargetType}
          options={[
            { label: '全部对象', value: 'all' },
            { label: '需求/Bug', value: 'issue' },
            { label: '知识内容', value: 'document' },
            { label: '多维表格', value: 'base' },
            { label: '表格记录', value: 'base_record' },
            { label: '审批', value: 'approval' },
          ]}
        />
      </Space>

      <Space orientation="vertical" size={10} className="notification-card-list">
        {notificationsQuery.isLoading ? <Typography.Text type="secondary">加载中...</Typography.Text> : null}
        {notifications.length === 0 && !notificationsQuery.isLoading ? <Empty description="暂无通知" /> : null}
        {notifications.map((item) => (
          <div className="notification-card-item" key={item.id}>
            <Checkbox checked={selectedIds.includes(item.id)} onChange={(event) => toggleSelected(item.id, event.target.checked)} />
            <div>
              <Space wrap>
                <Typography.Text strong={!item.readAt}>{item.title}</Typography.Text>
                <Tag>{item.notificationType}</Tag>
                <Tag>{item.sourceType}</Tag>
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
