import {
  AppstoreOutlined,
  EyeOutlined,
  LockOutlined,
  InboxOutlined,
  PlusOutlined,
  ProjectOutlined,
  ReloadOutlined,
  SettingOutlined,
  TagsOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Empty,
  Form,
  Input,
  Modal,
  Segmented,
  Skeleton,
  Space,
  Tag,
  Typography,
} from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'

import { ApiRequestError } from '../../../shared/api/httpClient'
import { StatusBadge } from '../../../shared/components/StatusBadge'
import {
  createProjectSpace,
  getProjectSpace,
  getProjectSpaceSettings,
  listProjectSpaces,
  transitionProjectSpace,
  updateProjectSpaceSettings,
  type ProjectSpaceVisibility,
  type UserProjectSpace,
} from '../api/projectSpacesApi'
import { ProjectSpaceMembersPanel } from '../components/ProjectSpaceMembersPanel'
import { ProjectWorkItemTypesPanel } from '../components/ProjectWorkItemTypesPanel'
import { listActiveWorkItemTypes, workItemTypeKeys } from '../api/workItemTypesApi'
import { errorMessage, formatTime, roleLabel, statusLabel, visibilityLabel } from '../projectSpaceView'

type CreateSpaceForm = {
  name: string
  spaceKey?: string
  description?: string
  visibility: ProjectSpaceVisibility
}

type SettingsForm = Pick<CreateSpaceForm, 'name' | 'description' | 'visibility'>
type SpaceView = 'overview' | 'members' | 'settings' | 'types'

const recentStorageKey = 'colla.project-spaces.recent'

export function ProjectSpacesPage() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const { spaceId, typeId } = useParams()
  const [createOpen, setCreateOpen] = useState(false)
  const [search, setSearch] = useState('')
  const [createForm] = Form.useForm<CreateSpaceForm>()

  const spacesQuery = useQuery({ queryKey: ['project-spaces'], queryFn: listProjectSpaces })
  const spaceQuery = useQuery({
    queryKey: ['project-spaces', spaceId],
    queryFn: () => getProjectSpace(spaceId as string),
    enabled: Boolean(spaceId),
    retry: (count, error) => !(error instanceof ApiRequestError && [403, 404, 409].includes(error.status)) && count < 2,
  })
  const spaces = useMemo(() => spacesQuery.data ?? [], [spacesQuery.data])
  const view = resolveSpaceView(location.pathname)

  useEffect(() => {
    if (!spaceId && spaces.length > 0) {
      navigate(`/project-spaces/${spaces[0].id}`, { replace: true })
    }
  }, [navigate, spaceId, spaces])

  useEffect(() => {
    if (spaceId && spaceQuery.data) rememberRecentSpace(spaceId)
  }, [spaceId, spaceQuery.data])

  const filteredSpaces = useMemo(() => {
    const keyword = search.trim().toLowerCase()
    if (!keyword) return spaces
    return spaces.filter((space) => `${space.name} ${space.spaceKey}`.toLowerCase().includes(keyword))
  }, [search, spaces])
  const recentSpaces = useMemo(() => sortRecentSpaces(filteredSpaces), [filteredSpaces])

  const createMutation = useMutation({
    mutationFn: createProjectSpace,
    onSuccess: async (space) => {
      await queryClient.invalidateQueries({ queryKey: ['project-spaces'] })
      setCreateOpen(false)
      createForm.resetFields()
      message.success('项目空间已创建')
      navigate(`/project-spaces/${space.id}`)
    },
    onError: (error) => message.error(errorMessage(error, '创建项目空间失败')),
  })

  const openSpace = (id: string) => navigate(`/project-spaces/${id}`)

  return (
    <div className="project-space-workspace" data-testid="project-spaces-page">
      <aside className="project-space-sidebar" aria-label="项目空间列表">
        <div className="project-space-sidebar-heading">
          <div>
            <Typography.Title level={4}>项目空间</Typography.Title>
            <Typography.Text type="secondary">{spaces.length} 个可访问空间</Typography.Text>
          </div>
          <Button type="primary" icon={<PlusOutlined />} aria-label="新建项目空间" onClick={() => setCreateOpen(true)} />
        </div>
        <Input.Search
          allowClear
          value={search}
          placeholder="搜索空间名称或编码"
          aria-label="搜索项目空间"
          onChange={(event) => setSearch(event.target.value)}
        />
        <div className="project-space-list-label">最近空间</div>
        <div className="project-space-list" role="list">
          {spacesQuery.isLoading ? <Skeleton active paragraph={{ rows: 4 }} /> : null}
          {!spacesQuery.isLoading && recentSpaces.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无项目空间" /> : null}
          {recentSpaces.map((space) => (
            <button
              type="button"
              role="listitem"
              className={`project-space-list-item${space.id === spaceId ? ' active' : ''}`}
              key={space.id}
              onClick={() => openSpace(space.id)}
            >
              <span className="project-space-list-icon"><ProjectOutlined /></span>
              <span className="project-space-list-copy">
                <strong>{space.name}</strong>
                <small>{space.spaceKey}</small>
              </span>
              <span className="project-space-list-meta">
                <StatusBadge status={space.status} />
                <small>{space.memberCount} 人</small>
              </span>
            </button>
          ))}
        </div>
      </aside>

      <main className="project-space-main">
        {!spaceId && spacesQuery.isLoading ? <Card><Skeleton active /></Card> : null}
        {!spaceId && !spacesQuery.isLoading && spaces.length === 0 ? (
          <Card className="project-space-zero-state">
            <Empty description="还没有可访问的项目空间">
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>创建第一个空间</Button>
            </Empty>
          </Card>
        ) : null}
        {spaceQuery.isError ? <ProjectSpaceLoadError error={spaceQuery.error} onBack={() => navigate('/project-spaces')} /> : null}
        {spaceId && spaceQuery.isLoading ? <Card><Skeleton active /></Card> : null}
        {spaceQuery.data ? (
          <ProjectSpaceShell
            key={spaceQuery.data.id}
            space={spaceQuery.data}
            view={view}
            selectedTypeId={typeId}
            onNavigate={(target) => navigate(`/project-spaces/${spaceQuery.data.id}${target === 'overview' ? '' : `/${target}`}`)}
            onSelectType={(selectedId) => navigate(`/project-spaces/${spaceQuery.data.id}/types/${selectedId}`)}
          />
        ) : null}
      </main>

      <Modal
        title="新建项目空间"
        open={createOpen}
        okText="创建空间"
        cancelText="取消"
        confirmLoading={createMutation.isPending}
        onCancel={() => setCreateOpen(false)}
        onOk={() => createForm.submit()}
        destroyOnHidden
      >
        <Form<CreateSpaceForm>
          form={createForm}
          layout="vertical"
          initialValues={{ visibility: 'private' }}
          onFinish={(values) => createMutation.mutate({ ...values, name: values.name.trim(), spaceKey: values.spaceKey?.trim() || undefined })}
        >
          <Form.Item name="name" label="空间名称" rules={[{ required: true, whitespace: true, message: '请输入空间名称' }, { max: 128 }]}>
            <Input autoFocus placeholder="例如：市场增长项目" />
          </Form.Item>
          <Form.Item
            name="spaceKey"
            label="空间编码"
            extra="可选；留空时由系统生成。"
            rules={[
              { max: 64 },
              { pattern: /^[a-z0-9][a-z0-9-]*$/, message: '仅支持小写字母、数字和连字符' },
            ]}
          >
            <Input placeholder="market-growth" />
          </Form.Item>
          <Form.Item name="visibility" label="可见性" rules={[{ required: true }]}>
            <Segmented block options={[{ label: '仅成员可见', value: 'private' }, { label: '企业内可发现', value: 'workspace' }]} />
          </Form.Item>
          <Form.Item name="description" label="空间说明" rules={[{ max: 2000 }]}>
            <Input.TextArea rows={3} placeholder="说明空间目标和适用团队" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

function ProjectSpaceShell({
  space,
  view,
  selectedTypeId,
  onNavigate,
  onSelectType,
}: {
  space: UserProjectSpace
  view: SpaceView
  selectedTypeId?: string
  onNavigate: (view: SpaceView) => void
  onSelectType: (typeId: string) => void
}) {
  const canManage = space.currentUserRole === 'owner' || space.currentUserRole === 'admin'
  const readOnly = space.status !== 'active'

  return (
    <div className="project-space-shell">
      <Card className="project-space-hero">
        <div className="project-space-hero-main">
          <span className="project-space-hero-icon"><ProjectOutlined /></span>
          <div className="project-space-hero-copy">
            <Space wrap size={8}>
              <Typography.Title level={2}>{space.name}</Typography.Title>
              <StatusBadge status={space.status} />
              <Tag color="purple">{roleLabel(space.currentUserRole)}</Tag>
            </Space>
            <Typography.Text type="secondary">{space.spaceKey}</Typography.Text>
            <Typography.Paragraph ellipsis={{ rows: 2 }}>{space.description || '暂无空间说明'}</Typography.Paragraph>
          </div>
        </div>
        <Space wrap className="project-space-hero-stats">
          <Tag icon={<TeamOutlined />}>{space.memberCount} 位成员</Tag>
          <Tag icon={space.visibility === 'private' ? <LockOutlined /> : <EyeOutlined />}>{visibilityLabel(space.visibility)}</Tag>
          <Tag>更新于 {formatTime(space.updatedAt)}</Tag>
        </Space>
      </Card>

      {readOnly ? (
        <Alert
          showIcon
          type={space.status === 'archived' ? 'info' : 'warning'}
          message={space.status === 'archived' ? '空间已归档，当前为只读状态。' : '空间已停用，写入和成员变更已关闭。'}
          description={canManage ? '可前往空间设置恢复空间。' : '请联系空间 owner 或管理员处理。'}
          action={canManage ? <Button size="small" onClick={() => onNavigate('settings')}>前往设置</Button> : undefined}
        />
      ) : null}

      <nav className="project-space-tabs" aria-label="空间导航">
        <Button aria-label="协作概览" type={view === 'overview' ? 'primary' : 'text'} icon={<AppstoreOutlined />} onClick={() => onNavigate('overview')}>协作概览</Button>
        {canManage ? <Button aria-label="工作项类型" type={view === 'types' ? 'primary' : 'text'} icon={<TagsOutlined />} onClick={() => onNavigate('types')}>工作项类型</Button> : null}
        {canManage ? <Button aria-label="成员" type={view === 'members' ? 'primary' : 'text'} icon={<TeamOutlined />} onClick={() => onNavigate('members')}>成员</Button> : null}
        {canManage ? <Button aria-label="空间设置" type={view === 'settings' ? 'primary' : 'text'} icon={<SettingOutlined />} onClick={() => onNavigate('settings')}>空间设置</Button> : null}
      </nav>

      {view === 'overview' ? <ProjectSpaceOverview space={space} /> : null}
      {view === 'types' && canManage ? <ProjectWorkItemTypesPanel space={space} selectedTypeId={selectedTypeId} onSelectType={onSelectType} /> : null}
      {view === 'members' && canManage ? <ProjectSpaceMembersPanel space={space} /> : null}
      {view === 'settings' && canManage ? <ProjectSpaceSettingsPanel space={space} /> : null}
      {view !== 'overview' && !canManage ? <Alert type="error" showIcon message="无权访问空间设置" description="成员执行视角不展示成员治理和空间配置。" /> : null}
    </div>
  )
}

function ProjectSpaceOverview({ space }: { space: UserProjectSpace }) {
  const typesQuery = useQuery({
    queryKey: workItemTypeKeys.active(space.id),
    queryFn: () => listActiveWorkItemTypes(space.id),
    retry: false,
  })

  return (
    <section className="project-space-overview-grid">
      <Card className="content-card project-space-active-types" title={<Space><TagsOutlined />可用工作项类型</Space>}>
        {typesQuery.isLoading ? <Skeleton active paragraph={{ rows: 2 }} /> : null}
        {typesQuery.isError ? <Alert type="error" showIcon message="工作项类型加载失败" action={<Button size="small" onClick={() => typesQuery.refetch()}>重试</Button>} /> : null}
        {!typesQuery.isLoading && !typesQuery.isError && typesQuery.data?.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前没有可用的工作项类型" />
        ) : null}
        <div className="project-space-active-type-list" aria-label="可用工作项类型">
          {typesQuery.data?.map((type) => (
            <div className="project-space-active-type" key={type.typeKey}>
              <span className="work-item-type-glyph" aria-hidden="true">{(type.icon?.trim() || type.name.slice(0, 1)).slice(0, 2)}</span>
              <span><strong>{type.name}</strong><small>{type.typeKey}</small></span>
            </div>
          ))}
        </div>
      </Card>
      <Card className="content-card" title="空间动态">
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="空间已就绪，事项与流程能力将在后续阶段接入。" />
      </Card>
      <Card className="content-card" title="协作边界">
        <div className="project-space-fact-list">
          <div><span>当前角色</span><strong>{roleLabel(space.currentUserRole)}</strong></div>
          <div><span>内容可见性</span><strong>{visibilityLabel(space.visibility)}</strong></div>
          <div><span>空间状态</span><strong>{statusLabel(space.status)}</strong></div>
        </div>
        <Typography.Paragraph type="secondary">
          这里是团队成员的日常协作入口。企业治理、全局风险和审计检索只在管理后台提供。
        </Typography.Paragraph>
      </Card>
    </section>
  )
}

function ProjectSpaceSettingsPanel({ space }: { space: UserProjectSpace }) {
  const { message, modal } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [form] = Form.useForm<SettingsForm>()

  useEffect(() => {
    form.setFieldsValue({ name: space.name, description: space.description ?? '', visibility: space.visibility })
  }, [form, space])

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['project-spaces'] }),
      queryClient.invalidateQueries({ queryKey: ['project-spaces', space.id] }),
      queryClient.invalidateQueries({ queryKey: ['project-spaces', space.id, 'settings'] }),
    ])
  }
  const settingsQuery = useQuery({ queryKey: ['project-spaces', space.id, 'settings'], queryFn: () => getProjectSpaceSettings(space.id) })
  const updateMutation = useMutation({
    mutationFn: (values: SettingsForm) => updateProjectSpaceSettings(space.id, values),
    onSuccess: async () => { await refresh(); message.success('空间设置已保存') },
    onError: (error) => message.error(errorMessage(error, '保存空间设置失败')),
  })
  const transitionMutation = useMutation({
    mutationFn: (action: 'disable' | 'restore' | 'archive') => transitionProjectSpace(space.id, action),
    onSuccess: async (_, action) => {
      await refresh()
      message.success(action === 'restore' ? '空间已恢复' : action === 'disable' ? '空间已停用' : '空间已归档')
      if (action === 'archive') navigate(`/project-spaces/${space.id}/settings`, { replace: true })
    },
    onError: (error) => message.error(errorMessage(error, '空间状态变更失败')),
  })

  const confirmTransition = (action: 'disable' | 'restore' | 'archive') => {
    const labels = { disable: '停用', restore: '恢复', archive: '归档' }
    modal.confirm({
      title: `确认${labels[action]}空间？`,
      content: action === 'restore' ? '恢复后成员可继续协作。' : '成员的写入和治理操作将立即受限。',
      okText: `确认${labels[action]}`,
      okButtonProps: action === 'restore' ? {} : { danger: true },
      onOk: () => transitionMutation.mutateAsync(action),
    })
  }

  return (
    <section className="project-space-settings-grid">
      <Card className="content-card" title="基本信息" loading={settingsQuery.isLoading}>
        <Form<SettingsForm> form={form} layout="vertical" onFinish={(values) => updateMutation.mutate({ ...values, name: values.name.trim() })}>
          <Form.Item name="name" label="空间名称" rules={[{ required: true, whitespace: true }, { max: 128 }]}><Input /></Form.Item>
          <Form.Item name="visibility" label="可见性"><Segmented block options={[{ label: '仅成员可见', value: 'private' }, { label: '企业内可发现', value: 'workspace' }]} /></Form.Item>
          <Form.Item name="description" label="空间说明" rules={[{ max: 2000 }]}><Input.TextArea rows={4} /></Form.Item>
          <Button type="primary" htmlType="submit" loading={updateMutation.isPending} disabled={space.status !== 'active'}>保存设置</Button>
        </Form>
      </Card>
      <Card className="content-card project-space-danger-card" title="空间生命周期">
        <div className="project-space-lifecycle-row">
          <div><strong>当前状态</strong><p>{statusDescription(space.status)}</p></div>
          <StatusBadge status={space.status} />
        </div>
        <Space wrap>
          {space.availableActions.includes('restore') ? <Button icon={<ReloadOutlined />} className="project-space-restore-button" onClick={() => confirmTransition('restore')}>恢复</Button> : null}
          {space.availableActions.includes('disable') ? <Button danger icon={<LockOutlined />} onClick={() => confirmTransition('disable')}>停用</Button> : null}
          {space.availableActions.includes('archive') ? <Button danger icon={<InboxOutlined />} onClick={() => confirmTransition('archive')}>归档</Button> : null}
        </Space>
      </Card>
    </section>
  )
}

function ProjectSpaceLoadError({ error, onBack }: { error: Error; onBack: () => void }) {
  const status = error instanceof ApiRequestError ? error.status : 0
  return (
    <Card>
      <Empty
        description={status === 404 ? '空间不存在或你无权访问' : status === 409 ? '空间当前状态不可访问' : '项目空间加载失败'}
      >
        <Button onClick={onBack}>返回空间列表</Button>
      </Empty>
    </Card>
  )
}

function resolveSpaceView(pathname: string): SpaceView {
  if (pathname.endsWith('/members')) return 'members'
  if (pathname.endsWith('/settings')) return 'settings'
  if (/\/types(?:\/[^/]+)?$/.test(pathname)) return 'types'
  return 'overview'
}

function rememberRecentSpace(spaceId: string) {
  try {
    const ids = JSON.parse(localStorage.getItem(recentStorageKey) ?? '[]') as string[]
    localStorage.setItem(recentStorageKey, JSON.stringify([spaceId, ...ids.filter((id) => id !== spaceId)].slice(0, 8)))
  } catch {
    localStorage.setItem(recentStorageKey, JSON.stringify([spaceId]))
  }
}

function sortRecentSpaces(spaces: UserProjectSpace[]) {
  const recentIds = (() => {
    try { return JSON.parse(localStorage.getItem(recentStorageKey) ?? '[]') as string[] } catch { return [] }
  })()
  const recentOrder = new Map(recentIds.map((id, index) => [id, index]))
  return [...spaces].sort((left, right) => {
    const leftIndex = recentOrder.get(left.id) ?? Number.MAX_SAFE_INTEGER
    const rightIndex = recentOrder.get(right.id) ?? Number.MAX_SAFE_INTEGER
    if (leftIndex !== rightIndex) return leftIndex - rightIndex
    return new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime()
  })
}

function statusDescription(status: string) {
  if (status === 'disabled') return '空间保留数据，但成员不能继续写入或调整成员。'
  if (status === 'archived') return '空间作为历史记录只读保留，可由 owner 或管理员恢复。'
  return '空间正常运行，成员可以按角色参与协作。'
}
