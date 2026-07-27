import { DatabaseOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Badge, Button, Card, Empty, Space, Statistic, Tag, Typography } from 'antd'
import { Link } from 'react-router-dom'

import { getHealth } from '../../platform/api/platformApi'
import { ObjectSummaryCard } from '../../platform/components/InternalLinkCard'
import { getWorkspaceDashboard, updateWorkspaceDashboardLayout } from '../../workspace/api/workspaceApi'
import type { UserWorkspaceDashboardView } from '../../workspace/api/workspaceApi'

export function DashboardPage() {
  const queryClient = useQueryClient()
  const healthQuery = useQuery({
    queryKey: ['health'],
    queryFn: getHealth,
  })
  const dashboardQuery = useQuery({
    queryKey: ['workspace', 'dashboard'],
    queryFn: getWorkspaceDashboard,
  })
  const dashboard = dashboardQuery.data
  const personalBuckets = dashboard?.personalWork?.buckets ?? []
  const myIssues = dashboard?.myIssues ?? []
  const approvalTodos = dashboard?.approvalTodos ?? []
  const unreadConversations = dashboard?.unreadConversations ?? []
  const latestNotifications = dashboard?.latestNotifications ?? []
  const recentKnowledgeContents = dashboard?.recentKnowledgeContents ?? []
  const recentBases = dashboard?.recentBases ?? []
  const recentObjects = dashboard?.recentObjects ?? []
  const favoriteObjects = dashboard?.favoriteObjects ?? []
  const draftSummaries = dashboard?.draftSummaries ?? []
  const dashboardLayout = dashboard?.dashboardLayout
  const layoutMutation = useMutation({
    mutationFn: (cards: NonNullable<typeof dashboardLayout>['cards']) =>
      updateWorkspaceDashboardLayout(
        crypto.randomUUID(),
        dashboardLayout?.version ?? 0,
        cards,
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['workspace', 'dashboard'] }),
  })
  const visibleCardKeys = new Set(
    (dashboardLayout?.cards ?? []).filter((card) => !card.hidden).map((card) => card.cardKey),
  )
  const visiblePersonalBuckets = personalBuckets
    .filter((bucket) => visibleCardKeys.size === 0 || visibleCardKeys.has(`personal.${bucket.bucket}`))
    .sort((left, right) => cardPosition(dashboardLayout, `personal.${left.bucket}`)
      - cardPosition(dashboardLayout, `personal.${right.bucket}`))

  return (
    <Space orientation="vertical" size={16} className="page-stack">
      <Space className="page-toolbar" wrap>
        <Typography.Title level={2}>工作台</Typography.Title>
        <Space wrap>
          <Link to="/im">
            <Button type="primary">进入 IM</Button>
          </Link>
          <Link to="/notifications">
            <Button>通知中心</Button>
          </Link>
        </Space>
      </Space>
      {healthQuery.data ? (
        <Alert
          type="success"
          showIcon
          title={`后端服务正常：${healthQuery.data.service}`}
          description={healthQuery.data.time}
        />
      ) : null}
      {healthQuery.isError ? <Alert type="error" showIcon title="无法连接后端健康检查接口" /> : null}
      {dashboardQuery.isError ? <Alert type="error" showIcon title="工作台内容暂时无法加载" description="请检查网络连接后重试。" /> : null}

      <section className="dashboard-metrics">
        <Card size="small">
          <Statistic title="我的事项" value={myIssues.length} loading={dashboardQuery.isLoading} />
        </Card>
        <Card size="small">
          <Statistic title="审批待办" value={approvalTodos.length} loading={dashboardQuery.isLoading} />
        </Card>
        <Card size="small">
          <Statistic title="未读消息" value={dashboard?.unreadMessageCount ?? 0} loading={dashboardQuery.isLoading} />
        </Card>
        <Card size="small">
          <Statistic title="未读通知" value={dashboard?.unreadNotificationCount ?? 0} loading={dashboardQuery.isLoading} />
        </Card>
        <Card size="small">
          <Statistic title="收藏对象" value={favoriteObjects.length} loading={dashboardQuery.isLoading} />
        </Card>
      </section>

      <section className="dashboard-grid">
        <Card title="个性化卡片" loading={dashboardQuery.isLoading} className="dashboard-section">
          <Space orientation="vertical" size={8} className="dashboard-list">
            {(dashboardLayout?.cards ?? []).map((card, index, cards) => (
              <Space key={card.cardKey} wrap>
                <Typography.Text>{card.title}</Typography.Text>
                <Button
                  size="small"
                  disabled={layoutMutation.isPending}
                  onClick={() => layoutMutation.mutate(cards.map((item) =>
                    item.cardKey === card.cardKey ? { ...item, hidden: !item.hidden } : item))}
                >
                  {card.hidden ? '显示' : '隐藏'}
                </Button>
                <Button
                  size="small"
                  disabled={index === 0 || layoutMutation.isPending}
                  onClick={() => layoutMutation.mutate(swapCardPosition(cards, index, index - 1))}
                >
                  上移
                </Button>
              </Space>
            ))}
            {layoutMutation.isError ? <Alert type="warning" showIcon title="布局已在其他标签页更新，请刷新后重试" /> : null}
          </Space>
        </Card>

        {visiblePersonalBuckets.map((bucket) => (
          <Card
            title={`${personalBucketTitle(bucket.bucket)} · ${bucket.visibleCount}`}
            loading={dashboardQuery.isLoading}
            className="dashboard-section"
            key={bucket.bucket}
          >
            <Space orientation="vertical" size={8} className="dashboard-list">
              {bucket.items.length === 0 ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={personalBucketEmpty(bucket.bucket)} />
              ) : null}
              {bucket.items.map((item) => (
                <Link
                  className="dashboard-list-item"
                  to={item.deepLink}
                  key={`${bucket.bucket}-${item.workItemId}`}
                  aria-label={`打开 ${item.displayKey} ${item.title}`}
                >
                  <span>
                    <Typography.Text strong>{item.displayKey} · {item.title}</Typography.Text>
                    <Space wrap size={6}>
                      <Tag>{item.spaceName}</Tag>
                      <Tag>{item.typeName}</Tag>
                      {item.reasons
                        .filter((reason) => reason.bucket === bucket.bucket && reason.dueAt)
                        .map((reason) => (
                          <Typography.Text type="secondary" key={`${reason.source}-${reason.sourceVersion}`}>
                            到期 {new Date(reason.dueAt as string).toLocaleString()}
                          </Typography.Text>
                        ))}
                    </Space>
                  </span>
                </Link>
              ))}
            </Space>
          </Card>
        ))}

        <Card title="我的事项" loading={dashboardQuery.isLoading} className="dashboard-section">
          <Space orientation="vertical" size={8} className="dashboard-list">
            {myIssues.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无事项" /> : null}
            {myIssues.map((issue) => (
              <div className="dashboard-list-item" key={issue.id}>
                <span>
                  <Link to={`/issues/${issue.id}`}>{issue.issueKey} · {issue.title}</Link>
                  <Space wrap size={6}>
                    <Tag>{issue.issueType}</Tag>
                    <Tag color={priorityColor(issue.priority)}>{issue.priority}</Tag>
                    <Tag color={statusColor(issue.status)}>{issue.status}</Tag>
                    {issue.dueAt ? <Typography.Text type="secondary">到期 {new Date(issue.dueAt).toLocaleDateString()}</Typography.Text> : null}
                  </Space>
                </span>
              </div>
            ))}
          </Space>
        </Card>

        <Card title="未读会话" loading={dashboardQuery.isLoading} className="dashboard-section">
          <Space orientation="vertical" size={8} className="dashboard-list">
            {unreadConversations.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无未读会话" /> : null}
            {unreadConversations.map((conversation) => (
              <div className="dashboard-list-item" key={conversation.id}>
                <span>
                  <Link to={`/im?conversationId=${conversation.id}`}>{conversation.title}</Link>
                  <Space wrap>
                    <Badge count={conversation.unreadCount} />
                    <Typography.Text type="secondary">{conversation.lastMessage?.content || '暂无消息'}</Typography.Text>
                  </Space>
                </span>
              </div>
            ))}
          </Space>
        </Card>

        <Card title="审批待办" loading={dashboardQuery.isLoading} className="dashboard-section">
          <Space orientation="vertical" size={8} className="dashboard-list">
            {approvalTodos.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无审批待办" /> : null}
            {approvalTodos.map((task) => (
              <Link className="dashboard-list-item" to={`/approvals/${task.instanceId}`} key={task.id}>
                <span>
                  <Typography.Text strong>{task.instanceTitle}</Typography.Text>
                  <Space wrap size={6}>
                    <Tag>{task.formName}</Tag>
                    <Typography.Text type="secondary">{task.applicantName}</Typography.Text>
                  </Space>
                </span>
              </Link>
            ))}
          </Space>
        </Card>

        <Card title="最新通知" loading={dashboardQuery.isLoading} className="dashboard-section">
          <Space orientation="vertical" size={8} className="dashboard-list">
            {latestNotifications.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无通知" /> : null}
            {latestNotifications.map((notification) => (
              <div className="dashboard-list-item" key={notification.id}>
                <span>
                  <Space wrap>
                    <Typography.Text strong={!notification.readAt}>{notification.title}</Typography.Text>
                    <Tag>{notification.notificationType}</Tag>
                    {notification.readAt ? <Tag>已读</Tag> : <Tag color="blue">未读</Tag>}
                  </Space>
                  <Space orientation="vertical" size={4}>
                    {notification.body ? <Typography.Text type="secondary">{notification.body}</Typography.Text> : null}
                    {notification.webPath ? <Link to={notification.webPath}>打开关联对象</Link> : null}
                  </Space>
                </span>
              </div>
            ))}
          </Space>
        </Card>

        <Card title="最近知识内容和表格" loading={dashboardQuery.isLoading} className="dashboard-section">
          <Space orientation="vertical" size={10} className="dashboard-list">
            {recentKnowledgeContents.length + recentBases.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无最近内容" />
            ) : null}
            {recentKnowledgeContents.map((summary) => (
              <ObjectSummaryCard summary={summary} key={`doc-${summary.objectId}`} />
            ))}
            {recentBases.map((base) => <BaseSummaryCard base={base} key={`base-${base.id}`} />)}
          </Space>
        </Card>

        {visibleCardKeys.size === 0 || visibleCardKeys.has('objects.recent') ? <Card title="最近访问" loading={dashboardQuery.isLoading} className="dashboard-section">
          <Space orientation="vertical" size={8} className="dashboard-list">
            {recentObjects.length ? (
              recentObjects.map((summary) => (
                <ObjectSummaryCard summary={summary} key={`recent-${summary.objectType}-${summary.objectId}`} />
              ))
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无最近访问" />
            )}
          </Space>
        </Card> : null}

        {visibleCardKeys.size === 0 || visibleCardKeys.has('objects.favorites') ? <Card title="收藏对象" loading={dashboardQuery.isLoading} className="dashboard-section">
          <Space orientation="vertical" size={8} className="dashboard-list">
            {favoriteObjects.length ? (
              favoriteObjects.map((summary) => (
                <ObjectSummaryCard summary={summary} key={`favorite-${summary.objectType}-${summary.objectId}`} />
              ))
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无收藏对象" />
            )}
          </Space>
        </Card> : null}

        {visibleCardKeys.size === 0 || visibleCardKeys.has('drafts.own') ? (
          <Card title="我的草稿" loading={dashboardQuery.isLoading} className="dashboard-section">
            <Space orientation="vertical" size={8} className="dashboard-list">
              {draftSummaries.length ? draftSummaries.map((draft) => (
                <Link
                  className="dashboard-list-item"
                  to={draft.recoveryPath}
                  key={draft.draftId}
                  aria-label={`恢复草稿 ${draft.typeName}`}
                >
                  <span>
                    <Typography.Text strong>{draft.spaceName} · {draft.typeName}</Typography.Text>
                    <Space wrap>
                      <Tag>{draft.status}</Tag>
                      <Typography.Text type="secondary">
                        更新于 {new Date(draft.updatedAt).toLocaleString()}
                      </Typography.Text>
                    </Space>
                  </span>
                </Link>
              )) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无可恢复草稿" />}
            </Space>
          </Card>
        ) : null}
      </section>
    </Space>
  )
}

function cardPosition(
  layout: UserWorkspaceDashboardView['dashboardLayout'] | undefined,
  key: string,
) {
  return layout?.cards.find((card) => card.cardKey === key)?.position ?? Number.MAX_SAFE_INTEGER
}

function swapCardPosition(
  cards: UserWorkspaceDashboardView['dashboardLayout']['cards'],
  leftIndex: number,
  rightIndex: number,
) {
  return cards.map((card, index) => {
    if (index === leftIndex) return { ...card, position: cards[rightIndex].position }
    if (index === rightIndex) return { ...card, position: cards[leftIndex].position }
    return card
  }).sort((left, right) => left.position - right.position)
}

function personalBucketTitle(bucket: 'todo' | 'responsible' | 'participating' | 'watching') {
  return {
    todo: '我的待办',
    responsible: '我负责的',
    participating: '我参与的',
    watching: '我关注的',
  }[bucket]
}

function personalBucketEmpty(bucket: 'todo' | 'responsible' | 'participating' | 'watching') {
  return {
    todo: '暂无待办',
    responsible: '暂无负责事项',
    participating: '暂无参与事项',
    watching: '暂无关注事项',
  }[bucket]
}

function BaseSummaryCard({ base }: { base: UserWorkspaceDashboardView['recentBases'][number] }) {
  return (
    <Link className="dashboard-list-item dashboard-object-link" to={`/bases/${base.id}`} aria-label={`打开表格空间 ${base.name}`}>
      <Space>
        <DatabaseOutlined aria-hidden="true" />
        <span>{base.name || '未命名表格空间'}</span>
      </Space>
      <Tag>表格空间</Tag>
    </Link>
  )
}

function priorityColor(priority: string) {
  if (priority === 'urgent') {
    return 'red'
  }
  if (priority === 'high') {
    return 'orange'
  }
  if (priority === 'medium') {
    return 'blue'
  }
  return 'default'
}

function statusColor(status: string) {
  if (status === 'resolved' || status === 'closed') {
    return 'green'
  }
  if (status === 'in_progress') {
    return 'blue'
  }
  return 'default'
}
