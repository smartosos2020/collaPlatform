import {
  AuditOutlined,
  EyeInvisibleOutlined,
  EyeOutlined,
  InboxOutlined,
  LockOutlined,
  ProjectOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Descriptions,
  Empty,
  Input,
  Select,
  Skeleton,
  Space,
  Statistic,
  Tag,
  Typography,
} from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

import { StatusBadge } from '../../../shared/components/StatusBadge'
import {
  getAdminProjectSpace,
  getProjectSpacePermissionExplanation,
  listAdminProjectSpaces,
  transitionAdminProjectSpace,
  type AdminProjectSpace,
} from '../../projectSpaces/api/projectSpacesApi'
import { errorMessage, formatTime, statusLabel, visibilityLabel } from '../../projectSpaces/projectSpaceView'

export function AdminProjectSpacesPage() {
  const { message, modal } = AntdApp.useApp()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { spaceId } = useParams()
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<string>()
  const [visibility, setVisibility] = useState<string>()

  const spacesQuery = useQuery({
    queryKey: ['admin', 'project-spaces', status, visibility],
    queryFn: () => listAdminProjectSpaces({ status, visibility, includeArchived: true }),
  })
  const spaces = useMemo(() => spacesQuery.data ?? [], [spacesQuery.data])
  const visibleSpaces = useMemo(() => {
    const keyword = search.trim().toLowerCase()
    if (!keyword) return spaces
    return spaces.filter((space) => `${space.name} ${space.spaceKey} ${space.id}`.toLowerCase().includes(keyword))
  }, [search, spaces])

  useEffect(() => {
    if (!spaceId && visibleSpaces.length > 0) {
      navigate(`/admin/project-spaces/${visibleSpaces[0].id}`, { replace: true })
    }
  }, [navigate, spaceId, visibleSpaces])

  const detailQuery = useQuery({
    queryKey: ['admin', 'project-spaces', spaceId, 'detail'],
    queryFn: () => getAdminProjectSpace(spaceId as string),
    enabled: Boolean(spaceId),
  })
  const explanationQuery = useQuery({
    queryKey: ['admin', 'project-spaces', spaceId, 'permission-explanation'],
    queryFn: () => getProjectSpacePermissionExplanation(spaceId as string),
    enabled: Boolean(spaceId),
  })

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['admin', 'project-spaces'] }),
      queryClient.invalidateQueries({ queryKey: ['project-spaces'] }),
    ])
  }
  const transitionMutation = useMutation({
    mutationFn: ({ id, action }: { id: string; action: 'disable' | 'restore' | 'archive' }) => transitionAdminProjectSpace(id, action),
    onSuccess: async (_, values) => {
      await refresh()
      message.success(values.action === 'restore' ? '空间已恢复' : values.action === 'disable' ? '空间已停用' : '空间已归档')
    },
    onError: (error) => message.error(errorMessage(error, '空间治理操作失败')),
  })

  const confirmTransition = (space: AdminProjectSpace, action: 'disable' | 'restore' | 'archive') => {
    const labels = { disable: '停用', restore: '恢复', archive: '归档' }
    modal.confirm({
      title: `确认${labels[action]}“${space.name}”？`,
      content: action === 'restore' ? '恢复后空间成员可继续协作。' : '此操作会影响全部空间成员，并写入企业审计日志。',
      okText: `确认${labels[action]}`,
      okButtonProps: action === 'restore' ? {} : { danger: true },
      onOk: () => transitionMutation.mutateAsync({ id: space.id, action }),
    })
  }

  return (
    <div className="admin-project-spaces-page" data-testid="admin-project-spaces-page">
      <aside className="admin-project-spaces-sidebar admin-org-panel">
        <div className="admin-project-spaces-filter-row">
          <Input
            allowClear
            prefix={<SearchOutlined />}
            value={search}
            aria-label="搜索项目空间"
            placeholder="名称、编码或空间 ID"
            onChange={(event) => setSearch(event.target.value)}
          />
          <Select
            allowClear
            value={status}
            aria-label="按状态筛选空间"
            placeholder="全部状态"
            onChange={setStatus}
            options={[{ value: 'active', label: '启用' }, { value: 'disabled', label: '停用' }, { value: 'archived', label: '已归档' }]}
          />
          <Select
            allowClear
            value={visibility}
            aria-label="按可见性筛选空间"
            placeholder="全部可见性"
            onChange={setVisibility}
            options={[{ value: 'private', label: '仅成员可见' }, { value: 'workspace', label: '企业内可发现' }]}
          />
        </div>
        <div className="admin-project-spaces-list" role="list">
          {spacesQuery.isLoading ? <Skeleton active paragraph={{ rows: 5 }} /> : null}
          {!spacesQuery.isLoading && visibleSpaces.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无符合条件的空间" /> : null}
          {visibleSpaces.map((space) => (
            <button
              key={space.id}
              type="button"
              role="listitem"
              className={`admin-project-space-list-item${space.id === spaceId ? ' active' : ''}`}
              onClick={() => navigate(`/admin/project-spaces/${space.id}`)}
            >
              <span className="admin-project-space-list-icon"><ProjectOutlined /></span>
              <span className="admin-project-space-list-copy"><strong>{space.name}</strong><small>{space.spaceKey}</small></span>
              <span className="admin-project-space-list-meta"><StatusBadge status={space.status} /><small>{space.memberCount} 人</small></span>
            </button>
          ))}
        </div>
      </aside>

      <main className="admin-project-spaces-main">
        {!spaceId && !spacesQuery.isLoading && visibleSpaces.length === 0 ? <Card><Empty description="选择筛选条件或等待空间创建" /></Card> : null}
        {detailQuery.isLoading ? <Card><Skeleton active /></Card> : null}
        {detailQuery.isError ? <Card><Empty description="空间不存在或当前账号无治理权限" /></Card> : null}
        {detailQuery.data ? (
          <AdminProjectSpaceDetail
            space={detailQuery.data}
            explanation={explanationQuery.data}
            loadingExplanation={explanationQuery.isLoading}
            onTransition={(action) => confirmTransition(detailQuery.data, action)}
            onAudit={() => navigate(`/admin/audit-logs?targetType=project_space&targetId=${detailQuery.data.id}`)}
          />
        ) : null}
      </main>
    </div>
  )
}

function AdminProjectSpaceDetail({
  space,
  explanation,
  loadingExplanation,
  onTransition,
  onAudit,
}: {
  space: AdminProjectSpace
  explanation?: { contentAccessGranted: boolean; contentAccessSource: string; explanation: string; governancePermission: string }
  loadingExplanation: boolean
  onTransition: (action: 'disable' | 'restore' | 'archive') => void
  onAudit: () => void
}) {
  return (
    <Space orientation="vertical" size={16} className="page-stack">
      <Card className="admin-project-space-hero">
        <div className="admin-project-space-hero-copy">
          <span className="admin-project-space-hero-icon"><ProjectOutlined /></span>
          <div>
            <Space wrap><Typography.Title level={2}>{space.name}</Typography.Title><StatusBadge status={space.status} /><Tag>{visibilityLabel(space.visibility)}</Tag></Space>
            <Typography.Text type="secondary">{space.spaceKey} · {space.id}</Typography.Text>
            <Typography.Paragraph>{space.description || '暂无空间说明'}</Typography.Paragraph>
          </div>
        </div>
        <Space wrap>
          <Button icon={<AuditOutlined />} onClick={onAudit}>审计日志</Button>
          {space.availableGovernanceActions.includes('restore') ? <Button className="project-space-restore-button" icon={<ReloadOutlined />} onClick={() => onTransition('restore')}>恢复</Button> : null}
          {space.availableGovernanceActions.includes('disable') ? <Button danger icon={<LockOutlined />} onClick={() => onTransition('disable')}>停用</Button> : null}
          {space.availableGovernanceActions.includes('archive') ? <Button danger icon={<InboxOutlined />} onClick={() => onTransition('archive')}>归档</Button> : null}
        </Space>
      </Card>

      <section className="admin-project-space-metrics">
        <Card><Statistic title="成员" value={space.memberCount} prefix={<TeamOutlined />} /></Card>
        <Card><Statistic title="状态" value={statusLabel(space.status)} /></Card>
        <Card><Statistic title="版本" value={space.version} /></Card>
      </section>

      <section className="admin-project-space-detail-grid">
        <Card className="content-card" title="治理边界">
          <Skeleton active loading={loadingExplanation} paragraph={{ rows: 3 }}>
            <Alert
              showIcon
              type={explanation?.contentAccessGranted ? 'info' : 'warning'}
              icon={explanation?.contentAccessGranted ? <EyeOutlined /> : <EyeInvisibleOutlined />}
              message={explanation?.contentAccessGranted ? '治理账号同时拥有空间成员身份' : '企业治理权限不授予协作内容访问'}
              description={explanation?.explanation || '正在读取权限解释。'}
            />
            <Descriptions size="small" column={1} className="admin-project-space-boundary-facts">
              <Descriptions.Item label="治理权限"><Tag icon={<SafetyCertificateOutlined />}>{explanation?.governancePermission || space.governancePermission}</Tag></Descriptions.Item>
              <Descriptions.Item label="内容权限来源">{explanation?.contentAccessSource || 'none'}</Descriptions.Item>
              <Descriptions.Item label="内容入口">后台不提供协作内容打开按钮</Descriptions.Item>
            </Descriptions>
          </Skeleton>
        </Card>
        <Card className="content-card" title="空间信息">
          <Descriptions size="small" column={1}>
            <Descriptions.Item label="Workspace">{space.workspaceId}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{formatTime(space.createdAt)}</Descriptions.Item>
            <Descriptions.Item label="更新时间">{formatTime(space.updatedAt)}</Descriptions.Item>
            <Descriptions.Item label="停用时间">{formatTime(space.disabledAt)}</Descriptions.Item>
            <Descriptions.Item label="归档时间">{formatTime(space.archivedAt)}</Descriptions.Item>
          </Descriptions>
        </Card>
      </section>
    </Space>
  )
}
