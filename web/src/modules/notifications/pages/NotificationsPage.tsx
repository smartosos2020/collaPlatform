import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Badge, Button, Card, Checkbox, Empty, List, Select, Space, Switch, Tag, Typography } from 'antd'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { InternalLinkCard } from '../../platform/components/InternalLinkCard'
import {
  getUnreadCount,
  getPersonalReminderPreference,
  listPersonalActivities,
  listPersonalReminders,
  listNotifications,
  markPersonalActivitiesRead,
  markAllNotificationsRead,
  markNotificationRead,
  markNotificationsRead,
  dispatchPersonalReminders,
  updatePersonalReminderPreference,
} from '../api/notificationsApi'
import { reconcileActiveNotificationFilters } from '../realtime/notificationReconciliation'

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
  const activitiesQuery = useQuery({
    queryKey: ['personal-work', 'activities'],
    queryFn: () => listPersonalActivities(),
  })
  const remindersQuery = useQuery({
    queryKey: ['personal-work', 'reminders'],
    queryFn: () => listPersonalReminders(),
  })
  const reminderPreferenceQuery = useQuery({
    queryKey: ['personal-work', 'reminder-preference'],
    queryFn: getPersonalReminderPreference,
  })

  const refreshNotifications = async () => {
    await reconcileActiveNotificationFilters(
      queryClient,
      [notificationFilters],
      { list: listNotifications, unreadCount: getUnreadCount },
    )
  }

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
  const markActivitiesReadMutation = useMutation({
    mutationFn: markPersonalActivitiesRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['personal-work', 'activities'] }),
  })
  const dispatchRemindersMutation = useMutation({
    mutationFn: () => dispatchPersonalReminders(),
    onSuccess: async () => {
      await Promise.all([
        refreshNotifications(),
        queryClient.invalidateQueries({ queryKey: ['personal-work', 'reminders'] }),
      ])
    },
  })
  const reminderPreferenceMutation = useMutation({
    mutationFn: (enabled: boolean) => updatePersonalReminderPreference({
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
      approachingMinutes: reminderPreferenceQuery.data?.approachingMinutes ?? 1440,
      enabled,
    }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['personal-work', 'reminder-preference'] }),
        queryClient.invalidateQueries({ queryKey: ['personal-work', 'reminders'] }),
      ])
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

      {notificationsQuery.isError ? <Alert type="error" showIcon message="通知暂时无法加载" description="请检查网络连接后重试。" /> : null}

      <Card
        title={<Space>个人动态<Badge count={activitiesQuery.data?.unreadCount ?? 0} /></Space>}
        extra={
          <Button
            disabled={!activitiesQuery.data?.items.length}
            loading={markActivitiesReadMutation.isPending}
            onClick={() => {
              const latest = activitiesQuery.data?.items[0]?.sequence
              if (latest != null) markActivitiesReadMutation.mutate(latest)
            }}
          >
            动态全部已读
          </Button>
        }
      >
        <List
          loading={activitiesQuery.isLoading}
          locale={{ emptyText: '暂无可见动态' }}
          dataSource={activitiesQuery.data?.items ?? []}
          renderItem={(activity) => (
            <List.Item actions={[<Button key="open" type="link" onClick={() => navigate(activity.deepLink)}>打开</Button>]}>
              <List.Item.Meta
                title={<Space><Typography.Text strong={activity.sequence > (activitiesQuery.data?.readThroughSequence ?? 0)}>{activity.displayKey}</Typography.Text><Tag>{activity.activityType}</Tag></Space>}
                description={<Typography.Text type="secondary">{activity.title} · {new Date(activity.occurredAt).toLocaleString()}</Typography.Text>}
              />
            </List.Item>
          )}
        />
      </Card>

      <Card
        title="待办提醒"
        extra={
          <Space>
            <Typography.Text type="secondary">提醒</Typography.Text>
            <Switch
              checked={reminderPreferenceQuery.data?.enabled ?? true}
              loading={reminderPreferenceMutation.isPending}
              onChange={(enabled) => reminderPreferenceMutation.mutate(enabled)}
            />
            <Button loading={dispatchRemindersMutation.isPending} onClick={() => dispatchRemindersMutation.mutate()}>
              刷新并路由通知
            </Button>
          </Space>
        }
      >
        <List
          loading={remindersQuery.isLoading}
          locale={{ emptyText: remindersQuery.data?.enabled === false ? '提醒已关闭' : '暂无临期或超期待办' }}
          dataSource={remindersQuery.data?.items ?? []}
          renderItem={(reminder) => (
            <List.Item actions={[<Button key="open" type="link" onClick={() => navigate(reminder.deepLink)}>打开</Button>]}>
              <List.Item.Meta
                title={<Space><Typography.Text>{reminder.displayKey}</Typography.Text><Tag color={reminder.state === 'overdue' ? 'red' : reminder.state === 'due' ? 'orange' : 'blue'}>{reminder.state}</Tag></Space>}
                description={`${reminder.title} · ${new Date(reminder.dueAt).toLocaleString()}`}
              />
            </List.Item>
          )}
        />
      </Card>

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
            { label: '知识内容', value: 'knowledge_content' },
            { label: 'IM', value: 'mention' },
            { label: '表格', value: 'base' },
            { label: '审批', value: 'approval' },
            { label: '工作项', value: 'project' },
          ]}
        />
        <Select
          value={targetType}
          onChange={setTargetType}
          options={[
            { label: '全部对象', value: 'all' },
            { label: '需求/Bug', value: 'issue' },
            { label: '知识内容', value: 'knowledge_content' },
            { label: '多维表格', value: 'base' },
            { label: '表格记录', value: 'base_record' },
            { label: '审批', value: 'approval' },
            { label: '工作项', value: 'work_item' },
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
