import {
  AppstoreOutlined,
  EyeOutlined,
  FileDoneOutlined,
  LockOutlined,
  InboxOutlined,
  LayoutOutlined,
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
import {
  lazy,
  Suspense,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'

import { ApiRequestError } from '../../../shared/api/httpClient'
import { StatusBadge } from '../../../shared/components/StatusBadge'
import { useSessionScope } from '../../../shared/session/SessionScopeContext'
import {
  createProjectSpace,
  getProjectSpace,
  getProjectSpaceExperiencePreference,
  getProjectSpaceSettings,
  listProjectSpaces,
  resetProjectSpaceExperiencePreference,
  saveProjectSpaceExperiencePreference,
  transitionProjectSpace,
  updateProjectSpaceSettings,
  type ProjectSpaceVisibility,
  type UserProjectSpace,
} from '../api/projectSpacesApi'
import { ProjectSpaceSecondaryTabs } from '../components/ProjectSpaceSecondaryTabs'
import { listActiveWorkItemTypes, workItemTypeKeys } from '../api/workItemTypesApi'
import { errorMessage, formatTime, roleLabel, statusLabel, visibilityLabel } from '../projectSpaceView'
import {
  PROJECT_SPACE_ADVANCED_CONFIGURATION,
  getVisibleProjectSpacePrimaryNavigation,
  projectSpacePrimaryPath,
  resolveProjectSpaceRouteContext,
  type ProjectSpaceExperienceMode,
  type ProjectSpacePrimaryView,
} from '../projectSpaceInformationArchitecture'
import {
  readRecentProjectSpaceIds,
  rememberRecentProjectSpace,
} from '../projectSpaceLocalCache'
import { projectSpaceLocationWithContext } from '../projectSpaceRouteContract'
import {
  projectSpaceExperienceFreshness,
  projectSpaceExperiencePreferenceQueryKey,
  projectSpaceExperienceRouteKey,
  type ProjectSpaceExperienceErrorCode,
  type ProjectSpaceExperienceEventMode,
  type ProjectSpaceExperienceEventOutcome,
} from '../projectSpaceExperience'
import {
  useProjectSpaceExperienceRollout,
  useProjectSpaceExperienceTelemetry,
} from '../useProjectSpaceExperience'

type CreateSpaceForm = {
  name: string
  spaceKey?: string
  description?: string
  visibility: ProjectSpaceVisibility
}

type SettingsForm = Pick<CreateSpaceForm, 'name' | 'description' | 'visibility'>
type SpaceView =
  | ProjectSpacePrimaryView
  | 'types'
  | 'fields'
  | 'layouts'
  | 'sample'

const ProjectSpaceMembersPanel = lazy(async () => ({
  default: (await import('../components/ProjectSpaceMembersPanel')).ProjectSpaceMembersPanel,
}))
const ProjectSpaceOnboarding = lazy(async () => ({
  default: (await import('../components/ProjectSpaceOnboarding')).ProjectSpaceOnboarding,
}))
const CrossSpaceGrantsPanel = lazy(async () => ({
  default: (await import('../components/CrossSpaceGrantsPanel')).CrossSpaceGrantsPanel,
}))
const CrossSpaceRelationsPanel = lazy(async () => ({
  default: (await import('../components/CrossSpaceRelationsPanel')).CrossSpaceRelationsPanel,
}))
const CrossSpaceSyncPanel = lazy(async () => ({
  default: (await import('../components/CrossSpaceSyncPanel')).CrossSpaceSyncPanel,
}))
const CrossTeamPanoramaPanel = lazy(async () => ({
  default: (await import('../components/CrossTeamPanoramaPanel')).CrossTeamPanoramaPanel,
}))
const MetricDashboardsPanel = lazy(async () => ({
  default: (await import('../components/MetricDashboardsPanel')).MetricDashboardsPanel,
}))
const MetricGovernancePanel = lazy(async () => ({
  default: (await import('../components/MetricGovernancePanel')).MetricGovernancePanel,
}))
const MetricRisksPanel = lazy(async () => ({
  default: (await import('../components/MetricRisksPanel')).MetricRisksPanel,
}))
const MetricSemanticsPanel = lazy(async () => ({
  default: (await import('../components/MetricSemanticsPanel')).MetricSemanticsPanel,
}))
const ScenarioTemplatesPanel = lazy(async () => ({
  default: (await import('../components/ScenarioTemplatesPanel')).ScenarioTemplatesPanel,
}))
const ProjectWorkItemFieldsPanel = lazy(async () => ({
  default: (await import('../components/ProjectWorkItemFieldsPanel')).ProjectWorkItemFieldsPanel,
}))
const ProjectWorkItemConfigurationDraftPanel = lazy(async () => ({
  default: (await import('../components/ProjectWorkItemConfigurationDraftPanel'))
    .ProjectWorkItemConfigurationDraftPanel,
}))
const ProjectWorkItemLayoutsPanel = lazy(async () => ({
  default: (await import('../components/ProjectWorkItemLayoutsPanel')).ProjectWorkItemLayoutsPanel,
}))
const ProjectWorkItemLayoutSample = lazy(async () => ({
  default: (await import('../components/ProjectWorkItemLayoutSample')).ProjectWorkItemLayoutSample,
}))
const ProjectWorkItemTypesPanel = lazy(async () => ({
  default: (await import('../components/ProjectWorkItemTypesPanel')).ProjectWorkItemTypesPanel,
}))
const ProjectWorkItemsPanel = lazy(async () => ({
  default: (await import('../components/ProjectWorkItemsPanel')).ProjectWorkItemsPanel,
}))

export function ProjectSpacesPage() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const sessionScope = useSessionScope()
  const { spaceId, typeId, fieldId, workItemId } = useParams()
  const [createOpen, setCreateOpen] = useState(false)
  const [search, setSearch] = useState('')
  const [recentIds, setRecentIds] = useState<string[]>([])
  const [createForm] = Form.useForm<CreateSpaceForm>()

  const spacesQuery = useQuery({ queryKey: ['project-spaces'], queryFn: listProjectSpaces })
  const spaceQuery = useQuery({
    queryKey: ['project-spaces', spaceId],
    queryFn: () => getProjectSpace(spaceId as string),
    enabled: Boolean(spaceId),
    retry: (count, error) => !(error instanceof ApiRequestError && [403, 404, 409].includes(error.status)) && count < 2,
  })
  const spaces = useMemo(
    () => spacesQuery.isError ? [] : spacesQuery.data ?? [],
    [spacesQuery.data, spacesQuery.isError],
  )
  const currentSpace = spaceQuery.data
    && !spaceQuery.isError
    && !spaceQuery.isFetching
    ? spaceQuery.data
    : undefined
  const view = resolveProjectSpaceRouteContext(location.pathname).renderView
  const recentScope = sessionScope
  const accessibleSpaceIds = useMemo(
    () => spaces.map((space) => space.id),
    [spaces],
  )
  useEffect(() => {
    let active = true
    queueMicrotask(() => {
      if (!active) return
      setRecentIds(
        !recentScope || !spacesQuery.isSuccess
          ? []
          : readRecentProjectSpaceIds(
              localStorage,
              recentScope,
              accessibleSpaceIds,
            ),
      )
    })
    return () => {
      active = false
    }
  }, [accessibleSpaceIds, recentScope, spacesQuery.isSuccess])

  useEffect(() => {
    if (!spaceId && spaces.length > 0) {
      const defaultPath = defaultProjectSpacePath(spaces[0])
      navigate(
        projectSpaceLocationWithContext(
          defaultPath,
          location.search,
          location.hash,
          ['source'],
        ) ?? defaultPath,
        { replace: true },
      )
    }
  }, [location.hash, location.search, navigate, spaceId, spaces])

  useEffect(() => {
    if (
      !spaceId
      || !currentSpace
      || !recentScope
      || !spacesQuery.isSuccess
      || !accessibleSpaceIds.includes(spaceId)
    ) return
    const remembered = rememberRecentProjectSpace(
      localStorage,
      recentScope,
      spaceId,
      accessibleSpaceIds,
    )
    if (remembered) {
      queueMicrotask(() => {
        setRecentIds(readRecentProjectSpaceIds(
          localStorage,
          recentScope,
          accessibleSpaceIds,
        ))
      })
    }
  }, [
    accessibleSpaceIds,
    currentSpace,
    recentScope,
    spaceId,
    spacesQuery.isSuccess,
  ])

  const filteredSpaces = useMemo(() => {
    const keyword = search.trim().toLowerCase()
    if (!keyword) return spaces
    return spaces.filter((space) => `${space.name} ${space.spaceKey}`.toLowerCase().includes(keyword))
  }, [search, spaces])
  const recentSpaces = useMemo(
    () => sortRecentSpaces(
      filteredSpaces,
      spaceId
        ? [spaceId, ...recentIds.filter((id) => id !== spaceId)]
        : recentIds,
    ),
    [filteredSpaces, recentIds, spaceId],
  )

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

  const openSpace = (id: string) => {
    const target = spaces.find((space) => space.id === id)
    const targetPath = target ? defaultProjectSpacePath(target) : `/project-spaces/${id}`
    navigate(
      projectSpaceLocationWithContext(
        targetPath,
        location.search,
        location.hash,
      ) ?? targetPath,
    )
  }
  const navigateProjectSpacePath = useCallback((
    targetPath: string,
    options: Readonly<{ replace?: boolean }> = {},
  ) => {
    navigate(
      projectSpaceLocationWithContext(
        targetPath,
        location.search,
        location.hash,
      ) ?? targetPath,
      { replace: options.replace },
    )
  }, [location.hash, location.search, navigate])
  const currentSpaceId = currentSpace?.id
  const navigateSpaceView = useCallback((target: SpaceView) => {
    if (!currentSpaceId) return
    const targetPath = ['overview', 'work-items', 'management', 'members', 'settings']
      .includes(target)
      ? projectSpacePrimaryPath(
          currentSpaceId,
          target as ProjectSpacePrimaryView,
        )
      : `/project-spaces/${currentSpaceId}/${target}`
    navigateProjectSpacePath(targetPath)
  }, [currentSpaceId, navigateProjectSpacePath])
  const selectType = useCallback((
    selectedId: string,
    options: Readonly<{ replace?: boolean }> = {},
  ) => {
    if (!currentSpaceId) return
    navigateProjectSpacePath(
      `/project-spaces/${currentSpaceId}/types/${selectedId}`,
      options,
    )
  }, [currentSpaceId, navigateProjectSpacePath])
  const configureFields = useCallback((selectedId: string) => {
    if (!currentSpaceId) return
    navigateProjectSpacePath(
      `/project-spaces/${currentSpaceId}/types/${selectedId}/fields`,
    )
  }, [currentSpaceId, navigateProjectSpacePath])
  const configureLayouts = useCallback((selectedId: string) => {
    if (!currentSpaceId) return
    navigateProjectSpacePath(
      `/project-spaces/${currentSpaceId}/types/${selectedId}/layouts`,
    )
  }, [currentSpaceId, navigateProjectSpacePath])
  const selectField = useCallback((
    selectedTypeId: string,
    selectedId?: string,
  ) => {
    if (!currentSpaceId) return
    navigateProjectSpacePath(
      `/project-spaces/${currentSpaceId}/types/${selectedTypeId}/fields`
      + `${selectedId ? `/${selectedId}` : ''}`,
    )
  }, [currentSpaceId, navigateProjectSpacePath])

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
        {!spaceId && spacesQuery.isError ? (
          <ProjectSpaceLoadError
            error={spacesQuery.error}
            onBack={() => void spacesQuery.refetch()}
          />
        ) : null}
        {!spaceId && !spacesQuery.isLoading && !spacesQuery.isError && spaces.length === 0 ? (
          <Card className="project-space-zero-state">
            <Empty description="还没有可访问的项目空间">
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>创建第一个空间</Button>
            </Empty>
          </Card>
        ) : null}
        {spaceQuery.isError ? <ProjectSpaceLoadError error={spaceQuery.error} onBack={() => navigate('/project-spaces')} /> : null}
        {spaceId && (spaceQuery.isLoading || spaceQuery.isFetching) ? <Card><Skeleton active /></Card> : null}
        {currentSpace ? (
          <ProjectSpaceShell
            key={currentSpace.id}
            space={currentSpace}
            view={view}
            selectedTypeId={typeId}
            selectedFieldId={fieldId}
            selectedWorkItemId={workItemId}
            onNavigate={navigateSpaceView}
            onSelectType={selectType}
            onConfigureFields={configureFields}
            onConfigureLayouts={configureLayouts}
            onSelectField={selectField}
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
  selectedFieldId,
  selectedWorkItemId,
  onNavigate,
  onSelectType,
  onConfigureFields,
  onConfigureLayouts,
  onSelectField,
}: {
  space: UserProjectSpace
  view: SpaceView
  selectedTypeId?: string
  selectedFieldId?: string
  selectedWorkItemId?: string
  onNavigate: (view: SpaceView) => void
  onSelectType: (
    typeId: string,
    options?: Readonly<{ replace?: boolean }>,
  ) => void
  onConfigureFields: (typeId: string) => void
  onConfigureLayouts: (typeId: string) => void
  onSelectField: (typeId: string, fieldId?: string) => void
}) {
  const { message } = AntdApp.useApp()
  const location = useLocation()
  const queryClient = useQueryClient()
  const sessionScope = useSessionScope()
  const [online, setOnline] = useState(() => navigator.onLine)
  const { rollout } = useProjectSpaceExperienceRollout(space.id)
  const enhancedExperience = rollout.enabled && rollout.state === 'enabled'
  const experienceTelemetry = useProjectSpaceExperienceTelemetry({
    spaceId: space.id,
    member: space.member,
    rollout,
    online,
  })
  const routeKey = projectSpaceExperienceRouteKey(location.pathname)
  const lastEntryKey = useRef('')
  const lastRouteErrorKey = useRef('')
  const previousRolloutState = useRef(rollout.state)
  const preferenceQueryKey = projectSpaceExperiencePreferenceQueryKey(
    sessionScope?.workspaceId ?? 'unknown',
    sessionScope?.userId ?? 'unknown',
    space.id,
  )
  useEffect(() => {
    const calibrate = () => setOnline(navigator.onLine)
    window.addEventListener('online', calibrate)
    window.addEventListener('offline', calibrate)
    return () => {
      window.removeEventListener('online', calibrate)
      window.removeEventListener('offline', calibrate)
    }
  }, [])
  const preferenceQuery = useQuery({
    queryKey: preferenceQueryKey,
    queryFn: () => getProjectSpaceExperiencePreference(space.id),
    enabled: Boolean(enhancedExperience && sessionScope),
    retry: false,
  })
  const preference = enhancedExperience
    && online
    && !preferenceQuery.isError
    && !preferenceQuery.isFetching
    ? preferenceQuery.data
    : undefined
  const mode: ProjectSpaceExperienceMode = preference?.mode ?? 'simple'
  const experienceMode: ProjectSpaceExperienceEventMode = enhancedExperience
    ? mode
    : 'baseline'
  const visibleNavigation = getVisibleProjectSpacePrimaryNavigation({
    member: space.member,
    currentUserRole: space.currentUserRole,
    status: space.status,
    availableActions: space.availableActions,
  })
  const activePrimaryView = resolveProjectSpaceRouteContext(location.pathname).primaryView
  const routeAccessDenied = view !== 'overview'
    && !visibleNavigation.some((item) => item.key === activePrimaryView)
  const canManage = space.availableActions.includes('view_settings')
  const readOnly = space.status !== 'active'
  useEffect(() => {
    if (!experienceTelemetry.ready) return
    const entryKey = `${space.id}:${routeKey}:${rollout.policyVersion}:${experienceMode}`
    if (lastEntryKey.current === entryKey) return
    lastEntryKey.current = entryKey
    experienceTelemetry.record({
      eventKind: 'entry',
      routeKey,
      mode: experienceMode,
      outcome: 'shown',
    })
  }, [
    experienceMode,
    experienceTelemetry,
    rollout.policyVersion,
    routeKey,
    space.id,
  ])
  useEffect(() => {
    if (
      rollout.policyVersion === 'unknown'
      || previousRolloutState.current === rollout.state
    ) {
      previousRolloutState.current = rollout.state
      return
    }
    previousRolloutState.current = rollout.state
    experienceTelemetry.record({
      eventKind: 'recovery',
      routeKey,
      mode: experienceMode,
      outcome: rollout.enabled ? 'recovered' : 'blocked',
    })
  }, [experienceMode, experienceTelemetry, rollout, routeKey])
  useEffect(() => {
    if (!routeAccessDenied || !experienceTelemetry.ready) return
    const errorKey = `${space.id}:${routeKey}:${rollout.policyVersion}:${experienceMode}`
    if (lastRouteErrorKey.current === errorKey) return
    lastRouteErrorKey.current = errorKey
    experienceTelemetry.record({
      eventKind: 'route_error',
      routeKey,
      mode: experienceMode,
      outcome: 'blocked',
      errorCode: 'capability_denied',
    })
  }, [
    experienceMode,
    experienceTelemetry,
    rollout.policyVersion,
    routeAccessDenied,
    routeKey,
    space.id,
  ])
  const savePreference = useMutation({
    mutationFn: (nextMode: ProjectSpaceExperienceMode) =>
      saveProjectSpaceExperiencePreference(space.id, nextMode, preference?.version ?? 0),
    onSuccess: (saved) => {
      queryClient.setQueryData(
        preferenceQueryKey,
        saved,
      )
      message.success(saved.mode === 'advanced' ? '已切换到高级模式' : '已切换到简洁模式')
      experienceTelemetry.record({
        eventKind: 'mode',
        routeKey,
        mode: saved.mode,
        outcome: 'changed',
      })
    },
    onError: async (error) => {
      await queryClient.invalidateQueries({
        queryKey: preferenceQueryKey,
      })
      message.error(errorMessage(error, '模式偏好已变化，请重试'))
      experienceTelemetry.record({
        eventKind: 'mode',
        routeKey,
        mode: experienceMode,
        outcome: 'failed',
        errorCode: error instanceof ApiRequestError && error.status === 409
          ? 'version_conflict'
          : 'server_error',
      })
    },
  })
  const resetPreference = useMutation({
    mutationFn: () => resetProjectSpaceExperiencePreference(
      space.id,
      preference?.version ?? 0,
    ),
    onSuccess: (saved) => {
      queryClient.setQueryData(
        preferenceQueryKey,
        saved,
      )
      message.success('已恢复简洁模式')
      experienceTelemetry.record({
        eventKind: 'mode',
        routeKey,
        mode: 'simple',
        outcome: 'changed',
      })
    },
    onError: async (error) => {
      await queryClient.invalidateQueries({
        queryKey: preferenceQueryKey,
      })
      message.error(errorMessage(error, '恢复默认失败，请重试'))
    },
  })
  const recordTaskResult = (
    outcome: ProjectSpaceExperienceEventOutcome,
    errorCode: ProjectSpaceExperienceErrorCode,
  ) => experienceTelemetry.record({
    eventKind: 'task_result',
    routeKey,
    mode: experienceMode,
    outcome,
    errorCode,
  })

  return (
    <div
      className="project-space-shell"
      data-testid="project-space-experience-boundary"
      data-rollout-state={rollout.state}
      data-rollout-policy={rollout.policyVersion}
      data-rollout-freshness={projectSpaceExperienceFreshness(rollout)}
      data-telemetry-sample-basis-points={rollout.telemetry.sampleBasisPoints}
      data-telemetry-max-batch-size={rollout.telemetry.maxBatchSize}
      data-experience-mode={experienceMode}
    >
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
        {enhancedExperience && preference?.availableModes.includes('advanced') ? (
          <Space wrap data-testid="project-space-mode-switch" aria-label="项目空间显示模式">
            <Segmented
              value={mode}
              options={[
                { label: '简洁模式', value: 'simple' },
                { label: '高级模式', value: 'advanced' },
              ]}
              disabled={!online || savePreference.isPending || resetPreference.isPending}
              onChange={(value) => {
                if (online) savePreference.mutate(value as ProjectSpaceExperienceMode)
              }}
            />
            <Button
              type="link"
              disabled={!online || mode === 'simple' || resetPreference.isPending}
              onClick={() => {
                if (online) resetPreference.mutate()
              }}
            >
              恢复默认
            </Button>
          </Space>
        ) : null}
        {enhancedExperience && space.member ? (
          <Suspense fallback={<Button loading disabled>使用引导</Button>}>
            <ProjectSpaceOnboarding
              space={space}
              online={online}
              onExperienceHelp={() => experienceTelemetry.record({
                eventKind: 'help',
                routeKey,
                mode: experienceMode,
                outcome: 'opened',
              })}
            />
          </Suspense>
        ) : null}
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

      <nav
        className="project-space-tabs"
        aria-label="空间导航"
        data-testid="project-space-primary-navigation"
      >
        {visibleNavigation.map((item) => (
          <Button
            key={item.key}
            aria-label={item.label}
            aria-current={activePrimaryView === item.key ? 'page' : undefined}
            type={activePrimaryView === item.key ? 'primary' : 'text'}
            icon={primaryNavigationIcon(item.key)}
            onClick={() => onNavigate(item.key)}
          >
            {item.label}
          </Button>
        ))}
      </nav>

      <Suspense fallback={<ProjectSpacePanelFallback />}>
        {view === 'overview' ? (
          space.member
            ? <ProjectSpaceOverview
                space={space}
                enhancedExperience={enhancedExperience}
                onTaskResult={recordTaskResult}
              />
            : <ProjectSpaceMetadataOverview space={space} />
        ) : null}
        {view === 'work-items' && space.availableActions.includes('view_work_items')
          ? <ProjectWorkItemsPanel space={space} workItemId={selectedWorkItemId} />
          : null}
        {view === 'management' && space.availableActions.includes('view_project_management')
          ? <ProjectSpaceManagementPanel space={space} />
          : null}
        {view === 'types' && canManage ? (
          <ProjectSpaceSecondaryTabs
            view="types"
            canManage
            testId="project-space-types-secondary-tabs"
            ariaLabel="工作项类型内容导航"
            panels={{
              'type-catalog': (
                <ProjectWorkItemTypesPanel
                  space={space}
                  selectedTypeId={selectedTypeId}
                  onSelectType={onSelectType}
                  onConfigureFields={onConfigureFields}
                  onConfigureLayouts={onConfigureLayouts}
                />
              ),
              'configuration-draft': selectedTypeId ? (
                <ProjectWorkItemConfigurationDraftPanel
                  spaceId={space.id}
                  typeId={selectedTypeId}
                  readOnly={readOnly}
                />
              ) : undefined,
            }}
          />
        ) : null}
        {view === 'fields' && canManage && selectedTypeId ? (
          <ProjectSpaceSecondaryTabs
            view="fields"
            canManage
            testId="project-space-fields-secondary-tabs"
            ariaLabel="工作项字段内容导航"
            panels={{
              'field-catalog': (
                <ProjectWorkItemFieldsPanel
                  space={space}
                  typeId={selectedTypeId}
                  selectedFieldId={selectedFieldId}
                  onBack={() => onSelectType(selectedTypeId)}
                  onSelectField={(fieldId) => onSelectField(selectedTypeId, fieldId)}
                />
              ),
              'configuration-draft': (
                <ProjectWorkItemConfigurationDraftPanel
                  spaceId={space.id}
                  typeId={selectedTypeId}
                  readOnly={readOnly}
                />
              ),
            }}
          />
        ) : null}
        {view === 'layouts' && canManage && selectedTypeId ? (
          <ProjectWorkItemLayoutsPanel
            space={space}
            typeId={selectedTypeId}
            onBack={() => onSelectType(selectedTypeId)}
            configurationDraft={(
              <ProjectWorkItemConfigurationDraftPanel
                spaceId={space.id}
                typeId={selectedTypeId}
                readOnly={readOnly}
              />
            )}
          />
        ) : null}
        {view === 'sample' && canManage && selectedTypeId ? (
          <ProjectWorkItemLayoutSample space={space} typeId={selectedTypeId} />
        ) : null}
        {view === 'members' && space.availableActions.includes('view_members')
          ? <ProjectSpaceMembersPanel space={space} />
          : null}
        {view === 'settings' && canManage
          ? <ProjectSpaceSettingsPanel
              space={space}
              mode={mode}
              enhancedExperience={enhancedExperience}
              onTaskResult={recordTaskResult}
            />
          : null}
        {routeAccessDenied
          ? <Alert type="error" showIcon message="无权访问当前空间内容" description="当前身份不能查看此入口；页面不会加载对应数据。" />
          : null}
      </Suspense>
    </div>
  )
}

function primaryNavigationIcon(view: ProjectSpacePrimaryView) {
  if (view === 'overview') return <AppstoreOutlined />
  if (view === 'work-items') return <FileDoneOutlined />
  if (view === 'management') return <ProjectOutlined />
  if (view === 'members') return <TeamOutlined />
  return <SettingOutlined />
}

function ProjectSpacePanelFallback() {
  return (
    <Card className="content-card" data-testid="project-space-panel-loading">
      <Skeleton active paragraph={{ rows: 6 }} />
    </Card>
  )
}

function ProjectSpaceMetadataOverview({ space }: { space: UserProjectSpace }) {
  return (
    <Card className="content-card" data-testid="project-space-metadata-overview" title="空间概览">
      <Typography.Paragraph>
        这是企业内可发现的项目空间。加入空间后才能查看工作项、成员和项目内容。
      </Typography.Paragraph>
      <Space wrap>
        <Tag>{visibilityLabel(space.visibility)}</Tag>
        <Tag>{statusLabel(space.status)}</Tag>
      </Space>
    </Card>
  )
}

function ProjectSpaceManagementPanel({ space }: { space: UserProjectSpace }) {
  return (
    <section data-testid="project-space-management">
      <ProjectWorkItemsPanel space={space} surface="management" />
      <ProjectSpaceSecondaryTabs
        view="overview"
        canManage={space.availableActions.includes('view_settings')}
        testId="project-space-management-metrics"
        ariaLabel="项目指标内容导航"
        queryParameter="metricPanel"
        panels={{
          'metric-dashboards': <MetricDashboardsPanel space={space} />,
          'metric-risks': <MetricRisksPanel space={space} />,
          'metric-governance': <MetricGovernancePanel space={space} />,
        }}
      />
    </section>
  )
}

function ProjectSpaceOverview({
  space,
  enhancedExperience,
  onTaskResult,
}: {
  space: UserProjectSpace
  enhancedExperience: boolean
  onTaskResult: (
    outcome: ProjectSpaceExperienceEventOutcome,
    errorCode: ProjectSpaceExperienceErrorCode,
  ) => void
}) {
  const navigate = useNavigate()
  const canManage = space.availableActions.includes('view_settings')
  const typesQuery = useQuery({
    queryKey: workItemTypeKeys.active(space.id),
    queryFn: () => listActiveWorkItemTypes(space.id),
    retry: false,
  })

  return (
    <ProjectSpaceSecondaryTabs
      view="overview"
      canManage={canManage}
      testId="project-space-overview-secondary-tabs"
      ariaLabel="协作概览内容导航"
      panels={{
        'active-types': (
          <Card className="content-card project-space-active-types" title={<Space><TagsOutlined />可用工作项类型</Space>}>
            {typesQuery.isLoading ? <Skeleton active paragraph={{ rows: 2 }} /> : null}
            {typesQuery.isError ? <Alert type="error" showIcon message="工作项类型加载失败" action={<Button size="small" onClick={() => typesQuery.refetch()}>重试</Button>} /> : null}
            {!typesQuery.isLoading && !typesQuery.isError && typesQuery.data?.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前没有可用的工作项类型" />
            ) : null}
            <div className="project-space-active-type-list" aria-label="可用工作项类型">
              {typesQuery.data?.map((type) => (
                <button
                  type="button"
                  className="project-space-active-type"
                  key={type.id}
                  onClick={() => navigate(`/project-spaces/${space.id}/work-items?typeId=${type.id}&create=1`)}
                >
                  <span className="work-item-type-glyph" aria-hidden="true">{(type.icon?.trim() || type.name.slice(0, 1)).slice(0, 2)}</span>
                  <span><strong>{type.name}</strong><small>{type.typeKey}</small></span>
                  <EyeOutlined aria-hidden="true" />
                </button>
              ))}
            </div>
          </Card>
        ),
        activity: (
          <Card className="content-card" title="空间动态">
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="空间已就绪，事项与流程能力将在后续阶段接入。" />
          </Card>
        ),
        'collaboration-boundary': (
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
        ),
        'cross-space-grants': enhancedExperience && canManage
          ? <CrossSpaceGrantsPanel space={space} />
          : undefined,
        'cross-space-relations': enhancedExperience
          ? <CrossSpaceRelationsPanel space={space} />
          : undefined,
        'cross-space-sync': enhancedExperience
          ? <CrossSpaceSyncPanel space={space} />
          : undefined,
        'cross-team-panorama': enhancedExperience
          ? <CrossTeamPanoramaPanel space={space} />
          : undefined,
        'scenario-templates': enhancedExperience
          ? <ScenarioTemplatesPanel space={space} onTaskResult={onTaskResult} />
          : undefined,
        'metric-semantics': enhancedExperience
          ? <MetricSemanticsPanel space={space} />
          : undefined,
        'metric-dashboards': enhancedExperience
          ? <MetricDashboardsPanel space={space} />
          : undefined,
        'metric-risks': enhancedExperience
          ? <MetricRisksPanel space={space} />
          : undefined,
        'metric-governance': enhancedExperience
          ? <MetricGovernancePanel space={space} />
          : undefined,
      }}
    />
  )
}

function ProjectSpaceSettingsPanel({
  space,
  mode,
  enhancedExperience,
  onTaskResult,
}: {
  space: UserProjectSpace
  mode: ProjectSpaceExperienceMode
  enhancedExperience: boolean
  onTaskResult: (
    outcome: ProjectSpaceExperienceEventOutcome,
    errorCode: ProjectSpaceExperienceErrorCode,
  ) => void
}) {
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
    <ProjectSpaceSecondaryTabs
      view="settings"
      canManage
      testId="project-space-settings-secondary-tabs"
      ariaLabel="空间设置内容导航"
      panels={{
        general: (
          <Card className="content-card" title="基本信息" loading={settingsQuery.isLoading}>
            <Form<SettingsForm> form={form} layout="vertical" onFinish={(values) => updateMutation.mutate({ ...values, name: values.name.trim() })}>
              <Form.Item name="name" label="空间名称" rules={[{ required: true, whitespace: true }, { max: 128 }]}><Input /></Form.Item>
              <Form.Item name="visibility" label="可见性"><Segmented block options={[{ label: '仅成员可见', value: 'private' }, { label: '企业内可发现', value: 'workspace' }]} /></Form.Item>
              <Form.Item name="description" label="空间说明" rules={[{ max: 2000 }]}><Input.TextArea rows={4} /></Form.Item>
              <Button type="primary" htmlType="submit" loading={updateMutation.isPending} disabled={space.status !== 'active'}>保存设置</Button>
            </Form>
          </Card>
        ),
        lifecycle: (
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
        ),
        'work-model': enhancedExperience && mode === 'advanced' ? (
          <Card
            className="content-card"
            data-testid="project-space-advanced-settings"
            title="工作模型"
          >
            <Typography.Paragraph type="secondary">
              {PROJECT_SPACE_ADVANCED_CONFIGURATION.find(
                (group) => group.key === 'work-model',
              )?.description}
            </Typography.Paragraph>
            <Space wrap>
              <Button icon={<TagsOutlined />} onClick={() => navigate(
                `/project-spaces/${space.id}/types`,
              )}>任务模板</Button>
              <Button icon={<LayoutOutlined />} onClick={() => navigate(
                `/project-spaces/${space.id}/types`,
              )}>字段、表单与页面</Button>
            </Space>
          </Card>
        ) : undefined,
        'flow-access': enhancedExperience && mode === 'advanced' ? (
          <Card className="content-card" title="流程与权限">
            <Typography.Paragraph type="secondary">
              {PROJECT_SPACE_ADVANCED_CONFIGURATION.find(
                (group) => group.key === 'flow-access',
              )?.description}
            </Typography.Paragraph>
            <Button onClick={() => navigate(`/project-spaces/${space.id}/types`)}>
              进入任务模板的流程与权限配置
            </Button>
          </Card>
        ) : undefined,
        'automation-collaboration': enhancedExperience && mode === 'advanced' ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Card className="content-card" title="自动化与协同">
              <Typography.Paragraph type="secondary">
                {PROJECT_SPACE_ADVANCED_CONFIGURATION.find(
                  (group) => group.key === 'automation-collaboration',
                )?.description}
              </Typography.Paragraph>
            </Card>
            <CrossSpaceGrantsPanel space={space} />
            <CrossSpaceSyncPanel space={space} />
          </Space>
        ) : undefined,
        'metrics-governance': enhancedExperience && mode === 'advanced' ? (
          <ProjectSpaceSecondaryTabs
            view="overview"
            canManage
            testId="project-space-settings-metrics"
            ariaLabel="度量治理配置导航"
            queryParameter="metricConfig"
            panels={{
              'metric-semantics': <MetricSemanticsPanel space={space} />,
              'metric-dashboards': <MetricDashboardsPanel space={space} />,
              'metric-risks': <MetricRisksPanel space={space} />,
              'metric-governance': <MetricGovernancePanel space={space} />,
            }}
          />
        ) : undefined,
        'scenario-templates': enhancedExperience && mode === 'advanced'
          ? <ScenarioTemplatesPanel space={space} onTaskResult={onTaskResult} />
          : undefined,
      }}
    />
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

function sortRecentSpaces(
  spaces: UserProjectSpace[],
  recentIds: readonly string[],
) {
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

function defaultProjectSpacePath(space: UserProjectSpace) {
  if (
    space.currentUserRole === 'member'
    && space.availableActions.includes('view_work_items')
  ) {
    return `/project-spaces/${space.id}/work-items`
  }
  return `/project-spaces/${space.id}`
}
