import { DatabaseOutlined, DragOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Badge, Button, Card, Checkbox, Divider, Empty, Popover, Radio, Space, Statistic, Tag, Typography } from 'antd'
import { useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'

import { PageHeader } from '../../../shared/components/PageHeader'
import { getHealth } from '../../platform/api/platformApi'
import { ObjectSummaryCard } from '../../platform/components/InternalLinkCard'
import { getWorkspaceDashboard, updateWorkspaceDashboardLayout } from '../../workspace/api/workspaceApi'
import type { UserWorkspaceDashboardView } from '../../workspace/api/workspaceApi'
import {
  createDragSession,
  dashboardLayoutModeStorageKey,
  moveCardTo,
  normalizeDashboardLayoutMode,
  sortLayoutCards,
  toggleCardHidden,
} from '../dashboardLayout'
import type { DashboardLayoutMode, DragSession } from '../dashboardLayout'

function readStoredLayoutMode(key: string): DashboardLayoutMode {
  try {
    return normalizeDashboardLayoutMode(window.localStorage.getItem(key))
  } catch {
    return normalizeDashboardLayoutMode(null)
  }
}

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
  const myWorkItems = dashboard?.myWorkItems ?? []
  const approvalTodos = dashboard?.approvalTodos ?? []
  const unreadConversations = dashboard?.unreadConversations ?? []
  const latestNotifications = dashboard?.latestNotifications ?? []
  const recentKnowledgeContents = dashboard?.recentKnowledgeContents ?? []
  const recentBases = dashboard?.recentBases ?? []
  const recentObjects = dashboard?.recentObjects ?? []
  const favoriteObjects = dashboard?.favoriteObjects ?? []
  const draftSummaries = dashboard?.draftSummaries ?? []
  const dashboardLayout = dashboard?.dashboardLayout
  const layoutCards = dashboardLayout?.cards ?? []
  const announcementRef = useRef('')
  const layoutMutation = useMutation({
    mutationFn: (cards: NonNullable<typeof dashboardLayout>['cards']) =>
      updateWorkspaceDashboardLayout(
        crypto.randomUUID(),
        dashboardLayout?.version ?? 0,
        cards,
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['workspace', 'dashboard'] })
      if (announcementRef.current) {
        setDragStatus(announcementRef.current)
        announcementRef.current = ''
      }
    },
    onError: () => {
      announcementRef.current = ''
      setDragStatus('个性化布局保存失败，请重试')
    },
  })
  const orderedLayoutCards = sortLayoutCards(layoutCards)
  const visibleLayoutCards = orderedLayoutCards.filter((card) => !card.hidden)

  const layoutModeKey = dashboardLayoutModeStorageKey()
  // effect-free derived preference: re-reads the matching scope whenever the
  // per-user storage key changes (currentUser resolves asynchronously).
  const storedMode = useMemo(() => readStoredLayoutMode(layoutModeKey), [layoutModeKey])
  const [layoutModeChoice, setLayoutModeChoice] = useState<{ key: string; mode: DashboardLayoutMode } | null>(null)
  const layoutMode = layoutModeChoice?.key === layoutModeKey ? layoutModeChoice.mode : storedMode
  const applyLayoutMode = (raw: unknown) => {
    const mode = normalizeDashboardLayoutMode(raw)
    setLayoutModeChoice({ key: layoutModeKey, mode })
    try {
      window.localStorage.setItem(layoutModeKey, mode)
    } catch {
      // storage unavailable (private mode etc.) — keep in-memory choice
    }
  }
  const bucketByKey = new Map(personalBuckets.map((bucket) => [`personal.${bucket.bucket}`, bucket]))

  const [dragKey, setDragKey] = useState<string | null>(null)
  const [dropTargetKey, setDropTargetKey] = useState<string | null>(null)
  const [dragStatus, setDragStatus] = useState('')
  // synchronous source of truth for the active drag; state above is visual only
  const dragSessionRef = useRef<DragSession | null>(null)
  if (dragSessionRef.current === null) {
    dragSessionRef.current = createDragSession()
  }
  const dragSession = dragSessionRef.current

  const resetDrag = () => {
    dragSession.end()
    setDragKey(null)
    setDropTargetKey(null)
  }

  const renderPersonalizableCard = (cardKey: string) => {
    if (cardKey.startsWith('personal.')) {
      const bucket = bucketByKey.get(cardKey)
      if (!bucket) return null
      return (
        <>
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
        </>
      )
    }
    if (cardKey === 'objects.recent') {
      return recentObjects.length ? (
        recentObjects.map((summary) => (
          <ObjectSummaryCard summary={summary} key={`recent-${summary.objectType}-${summary.objectId}`} />
        ))
      ) : (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无最近访问" />
      )
    }
    if (cardKey === 'objects.favorites') {
      return favoriteObjects.length ? (
        favoriteObjects.map((summary) => (
          <ObjectSummaryCard summary={summary} key={`favorite-${summary.objectType}-${summary.objectId}`} />
        ))
      ) : (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无收藏对象" />
      )
    }
    if (cardKey === 'drafts.own') {
      return draftSummaries.length ? draftSummaries.map((draft) => (
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
      )) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无可恢复草稿" />
    }
    if (cardKey === 'work.recent') {
      return (
        <>
          {myWorkItems.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无事项" /> : null}
          {myWorkItems.map((item) => (
            <div className="dashboard-list-item" key={item.workItemId}>
              <span>
                <Link to={item.deepLink}>{item.displayKey} · {item.title}</Link>
                <Space wrap size={6}>
                  <Tag>{item.spaceName}</Tag>
                  <Tag>{item.typeName}</Tag>
                  <Tag>{item.lifecycle}</Tag>
                </Space>
              </span>
            </div>
          ))}
        </>
      )
    }
    if (cardKey === 'conversations.unread') {
      return (
        <>
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
        </>
      )
    }
    if (cardKey === 'approvals.todo') {
      return (
        <>
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
        </>
      )
    }
    if (cardKey === 'notifications.latest') {
      return (
        <>
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
        </>
      )
    }
    if (cardKey === 'content.recent') {
      return (
        <>
          {recentKnowledgeContents.length + recentBases.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无最近内容" />
          ) : null}
          {recentKnowledgeContents.map((summary) => (
            <ObjectSummaryCard summary={summary} key={`doc-${summary.objectId}`} />
          ))}
          {recentBases.map((base) => <BaseSummaryCard base={base} key={`base-${base.id}`} />)}
        </>
      )
    }
    return null
  }

  const personalizableCardTitle = (cardKey: string) => {
    if (cardKey.startsWith('personal.')) {
      const bucket = bucketByKey.get(cardKey)
      return bucket ? `${personalBucketTitle(bucket.bucket)} · ${bucket.visibleCount}` : personalBucketTitle(cardKey.replace('personal.', '') as 'todo' | 'responsible' | 'participating' | 'watching')
    }
    return orderedLayoutCards.find((card) => card.cardKey === cardKey)?.title ?? cardKey
  }

  const layoutModeOptions: Array<{ value: DashboardLayoutMode; name: string; description: string }> = [
    { value: 'balanced', name: '均衡双列', description: '两列等宽，信息均衡' },
    { value: 'focus', name: '焦点主次', description: '首卡宽幅聚焦，其余双列' },
    { value: 'compact', name: '紧凑三列', description: '三列等宽，密度更高' },
  ]

  const personalizeContent = (
    <div className="dashboard-personalize">
      <div className="dashboard-personalize-section">
        <div className="dashboard-personalize-section-title">卡片布局</div>
        <Radio.Group
          aria-label="卡片布局"
          className="dashboard-layout-mode-group"
          value={layoutMode}
          onChange={(event) => applyLayoutMode(event.target.value)}
        >
          {layoutModeOptions.map((option) => (
            <Radio className="dashboard-layout-mode-option" key={option.value} value={option.value}>
              <span className="dashboard-layout-mode-label">
                <span
                  aria-hidden="true"
                  className={`dashboard-layout-thumb dashboard-layout-thumb-${option.value}`}
                >
                  <i /><i /><i /><i /><i /><i />
                </span>
                <span className="dashboard-layout-mode-text">
                  <span className="dashboard-layout-mode-name">{option.name}</span>
                  <span className="dashboard-layout-mode-desc">{option.description}</span>
                </span>
              </span>
            </Radio>
          ))}
        </Radio.Group>
      </div>
      <Divider className="dashboard-personalize-divider" />
      <div className="dashboard-personalize-section">
        <div className="dashboard-personalize-section-title" id="dashboard-personalize-cards-title">显示卡片</div>
        <div aria-labelledby="dashboard-personalize-cards-title" className="dashboard-personalize-list" role="group">
          {orderedLayoutCards.map((card) => (
            <Checkbox
              key={card.cardKey}
              checked={!card.hidden}
              disabled={layoutMutation.isPending}
              onChange={(event) =>
                layoutMutation.mutate(toggleCardHidden(layoutCards, card.cardKey, !event.target.checked))}
            >
              {card.title}
            </Checkbox>
          ))}
        </div>
      </div>
      {layoutMutation.isError ? <Alert type="warning" showIcon title="布局已在其他标签页更新，请刷新后重试" /> : null}
    </div>
  )

  return (
    <Space orientation="vertical" size={16} className="page-stack workspace-page">
      <PageHeader
        title="工作台"
        actions={
          <Space wrap>
            <Link to="/im">
              <Button type="primary">进入 IM</Button>
            </Link>
            <Link to="/notifications">
              <Button>通知中心</Button>
            </Link>
            <Popover
              content={personalizeContent}
              title="个性化"
              trigger="click"
              placement="bottomRight"
            >
              <Button>个性化</Button>
            </Popover>
          </Space>
        }
      />
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
      {layoutMutation.isError ? <Alert type="warning" showIcon title="个性化布局保存失败，布局可能已在其他标签页更新，请刷新后重试" /> : null}

      <section className="dashboard-metrics">
        <Card size="small">
          <Statistic title="我的事项" value={myWorkItems.length} loading={dashboardQuery.isLoading} />
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

      <section
        className="dashboard-grid"
        data-layout={layoutMode}
        onPointerCancel={resetDrag}
        onPointerLeave={resetDrag}
        onPointerMove={(event) => {
          const activeKey = dragSession.current()
          if (!activeKey) return
          const element = document.elementFromPoint(event.clientX, event.clientY)
          const targetKey = element?.closest('[data-card-key]')?.getAttribute('data-card-key') ?? null
          setDropTargetKey(targetKey && targetKey !== activeKey ? targetKey : null)
        }}
        onPointerUp={(event) => {
          const activeKey = dragSession.current()
          if (!activeKey) return
          const element = document.elementFromPoint(event.clientX, event.clientY)
          const finalTargetKey = element?.closest('[data-card-key]')?.getAttribute('data-card-key') ?? null
          if (finalTargetKey && finalTargetKey !== activeKey && !layoutMutation.isPending) {
            const source = visibleLayoutCards.find((item) => item.cardKey === activeKey)
            const target = visibleLayoutCards.find((item) => item.cardKey === finalTargetKey)
            announcementRef.current = source && target ? `已将 ${source.title} 移到 ${target.title} 的位置` : ''
            layoutMutation.mutate(moveCardTo(layoutCards, activeKey, finalTargetKey))
          }
          resetDrag()
        }}
      >
        <span className="visually-hidden" role="status" aria-live="polite">{dragStatus}</span>
        {visibleLayoutCards.map((card, visibleIndex, visibleCards) => (
            <div
              className={[
                'dashboard-draggable',
                visibleIndex === 0 ? 'focus-span' : '',
                dragKey === card.cardKey ? 'dragging' : '',
                dropTargetKey === card.cardKey && dragKey !== card.cardKey ? 'drop-target' : '',
              ].filter(Boolean).join(' ')}
              data-card-key={card.cardKey}
              key={card.cardKey}
            >
              <Card
                title={
                  <span className="dashboard-card-title">
                    <span
                      aria-disabled={layoutMutation.isPending}
                      aria-label={`拖拽排序 ${card.title}，可使用上下方向键调整顺序`}
                      className={[
                        'dashboard-drag-handle',
                        dragKey === card.cardKey ? 'active' : '',
                      ].filter(Boolean).join(' ')}
                      role="button"
                      tabIndex={layoutMutation.isPending ? -1 : 0}
                      onKeyDown={(event) => {
                        if (layoutMutation.isPending) return
                        if (event.key === 'ArrowUp' || event.key === 'ArrowLeft') {
                          const target = visibleCards[visibleIndex - 1]
                          if (!target) return
                          event.preventDefault()
                          announcementRef.current = `已将 ${card.title} 移到 ${target.title} 之前`
                          layoutMutation.mutate(moveCardTo(layoutCards, card.cardKey, target.cardKey))
                        } else if (event.key === 'ArrowDown' || event.key === 'ArrowRight') {
                          const target = visibleCards[visibleIndex + 1]
                          if (!target) return
                          event.preventDefault()
                          announcementRef.current = `已将 ${card.title} 移到 ${target.title} 之后`
                          layoutMutation.mutate(moveCardTo(layoutCards, card.cardKey, target.cardKey))
                        }
                      }}
                      onPointerDown={(event) => {
                        if (layoutMutation.isPending) return
                        event.preventDefault()
                        event.currentTarget.focus()
                        dragSession.begin(card.cardKey)
                        setDragKey(card.cardKey)
                      }}
                    >
                      <DragOutlined aria-hidden="true" />
                    </span>
                    {personalizableCardTitle(card.cardKey)}
                  </span>
                }
                loading={dashboardQuery.isLoading}
                className="dashboard-section"
              >
                <Space orientation="vertical" size={8} className="dashboard-list">
                  {renderPersonalizableCard(card.cardKey)}
                </Space>
              </Card>
            </div>
          ))}
      </section>
    </Space>
  )
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
