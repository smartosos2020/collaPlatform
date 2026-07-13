import { DatabaseOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Badge, Button, Card, Empty, Space, Statistic, Tag, Typography } from 'antd'
import { Link } from 'react-router-dom'

import { getHealth } from '../../platform/api/platformApi'
import { ObjectSummaryCard } from '../../platform/components/InternalLinkCard'
import { getWorkspaceDashboard } from '../../workspace/api/workspaceApi'
import type { UserWorkspaceDashboardView } from '../../workspace/api/workspaceApi'

export function DashboardPage() {
  const healthQuery = useQuery({
    queryKey: ['health'],
    queryFn: getHealth,
  })
  const dashboardQuery = useQuery({
    queryKey: ['workspace', 'dashboard'],
    queryFn: getWorkspaceDashboard,
  })
  const dashboard = dashboardQuery.data
  const myIssues = dashboard?.myIssues ?? []
  const approvalTodos = dashboard?.approvalTodos ?? []
  const unreadConversations = dashboard?.unreadConversations ?? []
  const latestNotifications = dashboard?.latestNotifications ?? []
  const recentKnowledgeContents = dashboard?.recentKnowledgeContents ?? []
  const recentBases = dashboard?.recentBases ?? []
  const recentObjects = dashboard?.recentObjects ?? []
  const favoriteObjects = dashboard?.favoriteObjects ?? []

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

        <Card title="最近访问" loading={dashboardQuery.isLoading} className="dashboard-section">
          <Space orientation="vertical" size={8} className="dashboard-list">
            {recentObjects.length ? (
              recentObjects.map((summary) => (
                <ObjectSummaryCard summary={summary} key={`recent-${summary.objectType}-${summary.objectId}`} />
              ))
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无最近访问" />
            )}
          </Space>
        </Card>

        <Card title="收藏对象" loading={dashboardQuery.isLoading} className="dashboard-section">
          <Space orientation="vertical" size={8} className="dashboard-list">
            {favoriteObjects.length ? (
              favoriteObjects.map((summary) => (
                <ObjectSummaryCard summary={summary} key={`favorite-${summary.objectType}-${summary.objectId}`} />
              ))
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无收藏对象" />
            )}
          </Space>
        </Card>
      </section>
    </Space>
  )
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
