import {
  AppstoreOutlined,
  BarChartOutlined,
  BuildOutlined,
  BugOutlined,
  CarryOutOutlined,
  EyeOutlined,
  FileDoneOutlined,
  FileTextOutlined,
  FlagOutlined,
  FormOutlined,
  LockOutlined,
  InboxOutlined,
  LineChartOutlined,
  PlusOutlined,
  ProjectOutlined,
  PushpinFilled,
  PushpinOutlined,
  ReloadOutlined,
  RightOutlined,
  RobotOutlined,
  RocketOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  SyncOutlined,
  TagsOutlined,
  TeamOutlined,
  UserOutlined,
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
  Select,
  Skeleton,
  Space,
  Tag,
  Timeline,
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
  type ReactNode,
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
  getProjectSpaceSurfacePreview,
  listProjectSpaces,
  saveProjectSpaceExperiencePreference,
  transitionProjectSpace,
  updateProjectSpaceSettings,
  type ProjectSpaceVisibility,
  type UserProjectSpace,
} from '../api/projectSpacesApi'
import { ProjectSpaceSecondaryTabs } from '../components/ProjectSpaceSecondaryTabs'
import {
  listProjectSpacePersonalActivities,
  listProjectSpacePersonalWork,
} from '../api/projectSpacePersonalWorkApi'
import {
  commandProjectSpaceOnboarding,
  getProjectSpaceOnboarding,
} from '../api/projectSpaceOnboardingApi'
import {
  listActiveWorkItemTypes,
  workItemTypeKeys,
} from '../api/workItemTypesApi'
import { errorMessage, formatTime, roleLabel, statusLabel, visibilityLabel } from '../projectSpaceView'
import {
  PROJECT_SPACE_ADVANCED_CONFIGURATION,
  getVisibleProjectSpaceTaskZones,
  projectSpacePrimaryPath,
  resolveProjectSpaceRouteContext,
  type ProjectSpaceExperienceMode,
  type ProjectSpacePrimaryView,
} from '../projectSpaceInformationArchitecture'
import {
  visibleProjectSpacePersonalWork,
  visibleProjectSpaceWorkItemTypes,
} from '../projectSpaceMemberContent'
import {
  readPinnedProjectSpaceIds,
  setProjectSpacePinned,
} from '../projectSpaceLocalCache'
import {
  isProjectSpaceWorkModelTab,
  patchProjectSpaceSearch,
  projectSpaceCrossSurfaceLocation,
  projectSpaceLocationWithContext,
  resolveCanonicalProjectSpaceLocation,
  type ProjectSpaceWorkModelTab,
} from '../projectSpaceRouteContract'
import { canonicalProjectSpaceSurfaceLocation } from '../projectSpaceSurfaceContract'
import {
  projectSpaceExperienceFreshness,
  projectSpaceExperiencePreferenceQueryKey,
  projectSpaceExperienceRouteKey,
  type ProjectSpaceExperienceErrorCode,
  type ProjectSpaceExperienceEventMode,
  type ProjectSpaceExperienceEventOutcome,
} from '../projectSpaceExperience'
import {
  startingPointCommand,
  type ProjectSpaceOnboardingScenarioKey,
} from '../projectSpaceOnboarding'
import {
  useProjectSpaceExperienceRollout,
  useProjectSpaceExperienceTelemetry,
} from '../useProjectSpaceExperience'

type CreateSpaceForm = {
  name: string
  spaceKey?: string
  description?: string
  visibility: ProjectSpaceVisibility
  startingMode: 'blank' | 'scenario' | 'clone'
  scenarioKey?: ProjectSpaceOnboardingScenarioKey
  referenceSpaceId?: string
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
const AutomationRulesPanel = lazy(async () => ({
  default: (await import('../components/AutomationRulesPanel')).AutomationRulesPanel,
}))
const AutomationExecutionPanel = lazy(async () => ({
  default: (await import('../components/AutomationExecutionPanel')).AutomationExecutionPanel,
}))
const AutomationConnectorsPanel = lazy(async () => ({
  default: (await import('../components/AutomationConnectorsPanel')).AutomationConnectorsPanel,
}))
const AutomationManagementPanel = lazy(async () => ({
  default: (await import('../components/AutomationManagementPanel')).AutomationManagementPanel,
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
  const [pinnedIds, setPinnedIds] = useState<string[]>([])
  const [createForm] = Form.useForm<CreateSpaceForm>()
  const startingMode = Form.useWatch('startingMode', createForm) ?? 'blank'
  const selectedScenario = Form.useWatch('scenarioKey', createForm)
  const referenceSpaceId = Form.useWatch('referenceSpaceId', createForm)

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
  // Keep rendering the last loaded space during background refetches so the
  // shell (and all panel local state) is not unmounted on every invalidate.
  const currentSpace = spaceQuery.data && !spaceQuery.isError
    ? spaceQuery.data
    : undefined
  const canonicalSurfaceLocation = currentSpace
    ? canonicalProjectSpaceSurfaceLocation({
        spaceId: currentSpace.id,
        pathname: location.pathname,
        search: location.search,
        hash: location.hash,
      })
    : null
  const view = resolveProjectSpaceRouteContext(location.pathname).renderView
  const spaceListScope = sessionScope
  const accessibleSpaceIds = useMemo(
    () => spaces.map((space) => space.id),
    [spaces],
  )
  useEffect(() => {
    let active = true
    queueMicrotask(() => {
      if (!active) return
      setPinnedIds(
        !spaceListScope || !spacesQuery.isSuccess
          ? []
          : readPinnedProjectSpaceIds(
              localStorage,
              spaceListScope,
              accessibleSpaceIds,
            ),
      )
    })
    return () => {
      active = false
    }
  }, [accessibleSpaceIds, spaceListScope, spacesQuery.isSuccess])

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
    if (canonicalSurfaceLocation) {
      navigate(canonicalSurfaceLocation, { replace: true })
    }
  }, [canonicalSurfaceLocation, navigate])

  const filteredSpaces = useMemo(() => {
    const keyword = search.trim().toLowerCase()
    if (!keyword) return spaces
    return spaces.filter((space) => `${space.name} ${space.spaceKey}`.toLowerCase().includes(keyword))
  }, [search, spaces])
  const pinnedSpaces = useMemo(() => {
    const spacesById = new Map(filteredSpaces.map((space) => [space.id, space]))
    return pinnedIds
      .map((id) => spacesById.get(id))
      .filter((space): space is UserProjectSpace => Boolean(space))
  }, [filteredSpaces, pinnedIds])
  const unpinnedSpaces = useMemo(
    () => {
      const pinned = new Set(pinnedIds)
      return filteredSpaces.filter((space) => !pinned.has(space.id))
    },
    [filteredSpaces, pinnedIds],
  )

  const createMutation = useMutation({
    mutationFn: (values: CreateSpaceForm) => createProjectSpace({
      name: values.name,
      spaceKey: values.spaceKey,
      description: values.description,
      visibility: values.visibility,
    }),
    onSuccess: async (space, values) => {
      await queryClient.invalidateQueries({ queryKey: ['project-spaces'] })
      try {
        const onboarding = await getProjectSpaceOnboarding(space.id)
        const startingPoint = values.startingMode === 'scenario' && values.scenarioKey
          ? values.scenarioKey
          : 'blank'
        await commandProjectSpaceOnboarding(
          space.id,
          onboarding.version,
          startingPointCommand(startingPoint),
        )
      } catch {
        message.warning('空间已创建，但起步引导暂未保存；可在空间内重新选择。')
      }
      if (values.startingMode === 'clone' && values.referenceSpaceId && spaceListScope) {
        const reference = spaces.find((candidate) => candidate.id === values.referenceSpaceId)
        rememberProjectSpaceCloneReference(
          sessionStorage,
          `${spaceListScope.workspaceId}:${spaceListScope.userId}`,
          space.id,
          reference?.name ?? '参考空间',
        )
      }
      setCreateOpen(false)
      createForm.resetFields()
      message.success('项目空间已创建')
      const panel = values.startingMode === 'scenario'
        ? 'scenario-templates'
        : values.startingMode === 'clone'
          ? 'work-model'
          : 'management-home'
      navigate(`/project-spaces/${space.id}/settings?panel=${panel}&source=create`)
    },
    onError: (error) => message.error(errorMessage(error, '创建项目空间失败')),
  })

  const openSpace = (id: string) => {
    const target = spaces.find((space) => space.id === id)
    const targetPath = target ? defaultProjectSpacePath(target) : `/project-spaces/${id}`
    navigate(
      projectSpaceCrossSurfaceLocation(
        targetPath,
        location.search,
        location.hash,
      ) ?? targetPath,
    )
  }
  const togglePinnedSpace = (id: string) => {
    if (!spaceListScope) {
      message.warning('当前会话尚未就绪，暂时无法保存置顶状态')
      return
    }
    const nextPinned = !pinnedIds.includes(id)
    const saved = setProjectSpacePinned(
      localStorage,
      spaceListScope,
      id,
      nextPinned,
      accessibleSpaceIds,
    )
    if (!saved) {
      message.error('置顶状态保存失败')
      return
    }
    setPinnedIds(readPinnedProjectSpaceIds(
      localStorage,
      spaceListScope,
      accessibleSpaceIds,
    ))
  }
  const renderSpaceListItem = (space: UserProjectSpace, pinned: boolean) => (
    <div
      role="listitem"
      className={`project-space-list-item${space.id === spaceId ? ' active' : ''}`}
      key={space.id}
    >
      <button
        type="button"
        className="project-space-list-select"
        aria-current={space.id === spaceId ? 'page' : undefined}
        onClick={() => openSpace(space.id)}
      >
        <span className="project-space-list-copy">
          <strong>{space.name}</strong>
          <small>{space.spaceKey}</small>
        </span>
        <span className="project-space-list-meta">
          <span
            className={`project-space-status-dot ${space.status}`}
            role="img"
            aria-label={statusLabel(space.status)}
            title={statusLabel(space.status)}
          />
          <small>{space.memberCount} 人</small>
        </span>
      </button>
      <Button
        type="text"
        size="small"
        className={`project-space-pin-button${pinned ? ' active' : ''}`}
        icon={pinned ? <PushpinFilled /> : <PushpinOutlined />}
        aria-label={`${pinned ? '取消置顶' : '置顶'} ${space.name}`}
        title={pinned ? '取消置顶' : '置顶'}
        onClick={() => togglePinnedSpace(space.id)}
      />
    </div>
  )
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
    navigate(
      projectSpaceCrossSurfaceLocation(
        targetPath,
        location.search,
        location.hash,
      ) ?? targetPath,
    )
  }, [currentSpaceId, location.hash, location.search, navigate])
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
          <div className="project-space-sidebar-title">
            <Typography.Title level={4}>项目空间</Typography.Title>
            <Typography.Text type="secondary">
              <strong className="project-space-count-value">{spaces.length}</strong> 个空间
            </Typography.Text>
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
        <div className="project-space-list">
          {spacesQuery.isLoading ? <Skeleton active paragraph={{ rows: 4 }} /> : null}
          {!spacesQuery.isLoading && filteredSpaces.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无项目空间" /> : null}
          {!spacesQuery.isLoading && filteredSpaces.length > 0 ? (
            <>
              <section className="project-space-list-section" aria-labelledby="pinned-spaces-label">
                <div className="project-space-list-label" id="pinned-spaces-label">置顶</div>
                <div className="project-space-list-group" role="list">
                  {pinnedSpaces.length > 0
                    ? pinnedSpaces.map((space) => renderSpaceListItem(space, true))
                    : <div className="project-space-list-empty">暂无置顶空间</div>}
                </div>
              </section>
              <section className="project-space-list-section" aria-labelledby="all-spaces-label">
                <div className="project-space-list-label" id="all-spaces-label">全部空间</div>
                <div className="project-space-list-group" role="list">
                  {unpinnedSpaces.map((space) => renderSpaceListItem(space, false))}
                </div>
              </section>
            </>
          ) : null}
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
        {currentSpace && !canonicalSurfaceLocation ? (
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
        onCancel={() => {
          setCreateOpen(false)
          createForm.resetFields()
        }}
        onOk={() => createForm.submit()}
        destroyOnHidden
      >
        <Form<CreateSpaceForm>
          form={createForm}
          layout="vertical"
          initialValues={{ visibility: 'private', startingMode: 'blank' }}
          onFinish={(values) => createMutation.mutate({ ...values, name: values.name.trim(), spaceKey: values.spaceKey?.trim() || undefined })}
        >
          <Form.Item
            name="startingMode"
            label="起步方式"
            rules={[{ required: true }]}
          >
            <Segmented
              block
              options={[
                { label: '空白空间', value: 'blank' },
                { label: '场景模板', value: 'scenario' },
                { label: '克隆配置', value: 'clone' },
              ]}
            />
          </Form.Item>
          {startingMode === 'scenario' ? (
            <Form.Item
              name="scenarioKey"
              label="场景模板"
              rules={[{ required: true, message: '请选择场景模板' }]}
            >
              <Select
                placeholder="选择适合团队的起步模板"
                options={[
                  { label: '研发协作', value: 'development' },
                  { label: '市场项目', value: 'marketing' },
                  { label: 'HR 事务', value: 'human-resources' },
                  { label: '交付管理', value: 'delivery' },
                ]}
              />
            </Form.Item>
          ) : null}
          {startingMode === 'clone' ? (
            <Form.Item
              name="referenceSpaceId"
              label="参考空间"
              rules={[{ required: true, message: '请选择参考空间' }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                placeholder="选择一个有权访问的空间"
                options={spaces.map((space) => ({ label: space.name, value: space.id }))}
              />
            </Form.Item>
          ) : null}
          <Card size="small" title="影响预览" className="project-space-create-impact">
            <div className="project-space-fact-list">
              {createStartingModeImpact(
                startingMode,
                selectedScenario,
                spaces.find((space) => space.id === referenceSpaceId)?.name,
              ).map((item) => (
                <div key={item.label}><span>{item.label}</span><strong>{item.value}</strong></div>
              ))}
            </div>
          </Card>
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
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const sessionScope = useSessionScope()
  const [online, setOnline] = useState(() => navigator.onLine)
  const [previewOpen, setPreviewOpen] = useState(false)
  const [previewRole, setPreviewRole] = useState<'member' | 'guest'>('member')
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
  const accessSnapshot = {
    member: space.member,
    currentUserRole: space.currentUserRole,
    status: space.status,
    availableActions: space.availableActions,
  } as const
  const visibleTaskZones = getVisibleProjectSpaceTaskZones(accessSnapshot)
  const visibleNavigation = visibleTaskZones.flatMap((zone) => zone.navigation)
  const activePrimaryView = resolveProjectSpaceRouteContext(location.pathname).primaryView
  const routeAccessDenied = view !== 'overview'
    && !visibleNavigation.some((item) => item.key === activePrimaryView)
  const canManage = space.availableActions.includes('view_settings')
  const canPreview = canManage
    && (space.currentUserRole === 'owner' || space.currentUserRole === 'admin')
  const readOnly = space.status !== 'active'
  const settingsWorkModelEntry = new URLSearchParams(location.search)
    .get('source') === 'settings-work-model'
  const returnToTypeCatalog = useCallback((typeId: string) => {
    if (!settingsWorkModelEntry) {
      onSelectType(typeId)
      return
    }
    const target = projectSpaceLocationWithContext(
      `/project-spaces/${space.id}/settings`,
      patchProjectSpaceSearch(location.search, {
        panel: 'work-model',
        typeId,
      }),
    )
    if (target) navigate(target)
  }, [location.search, navigate, onSelectType, settingsWorkModelEntry, space.id])
  const previewQuery = useQuery({
    queryKey: ['project-spaces', space.id, 'surface-preview', previewRole],
    queryFn: () => getProjectSpaceSurfacePreview(space.id, previewRole),
    enabled: previewOpen && canPreview,
    retry: false,
  })
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
  const previewTaskZones = previewQuery.data
    ? getVisibleProjectSpaceTaskZones({
        member: true,
        currentUserRole: previewQuery.data.targetRole,
        status: previewQuery.data.readOnly ? 'disabled' : 'active',
        availableActions: previewQuery.data.availableActions,
      })
    : []
  const closePreview = () => {
    setPreviewOpen(false)
    queryClient.removeQueries({
      queryKey: ['project-spaces', space.id, 'surface-preview'],
    })
  }

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
          <div className="project-space-hero-copy">
            <Space wrap size={8}>
              <Typography.Title level={2}>{space.name}</Typography.Title>
              <Tag color="purple">{roleLabel(space.currentUserRole)}</Tag>
            </Space>
            <Typography.Paragraph ellipsis={{ rows: 1, tooltip: space.description || '暂无空间说明' }}>
              {space.description || '暂无空间说明'}
            </Typography.Paragraph>
          </div>
        </div>
        <Space wrap className="project-space-hero-stats">
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
              disabled={!online || savePreference.isPending}
              onChange={(value) => {
                if (online) savePreference.mutate(value as ProjectSpaceExperienceMode)
              }}
            />
          </Space>
        ) : null}
        {canPreview ? (
          <Button
            icon={<EyeOutlined />}
            data-testid="project-space-member-preview-open"
            onClick={() => setPreviewOpen(true)}
          >
            成员视图预览
          </Button>
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
        {visibleTaskZones.map((zone) => (
          <div
            className="project-space-task-zone"
            data-testid={`project-space-task-zone-${zone.key}`}
            key={zone.key}
          >
            <Typography.Text className="project-space-task-zone-label" type="secondary">
              {zone.label}
            </Typography.Text>
            <div className="project-space-task-zone-actions">
              {zone.navigation.map((item) => (
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
            </div>
          </div>
        ))}
      </nav>

      <Modal
        title="成员视图预览"
        open={previewOpen}
        footer={<Button onClick={closePreview}>退出预览</Button>}
        onCancel={closePreview}
        destroyOnHidden
        data-testid="project-space-member-preview"
      >
        <Alert
          type="info"
          showIcon
          message="这是入口展示预览，不会改变你的权限"
          description="预览只返回服务端计算的导航元数据，不加载工作项、成员、配置或指标内容。"
        />
        <Segmented
          block
          value={previewRole}
          options={[
            { label: '普通成员', value: 'member' },
            { label: '访客', value: 'guest' },
          ]}
          onChange={(value) => setPreviewRole(value as 'member' | 'guest')}
        />
        {previewQuery.isLoading ? <Skeleton active paragraph={{ rows: 3 }} /> : null}
        {previewQuery.isError ? (
          <Alert
            type="error"
            showIcon
            message="预览加载失败"
            description="当前权限或网络状态不允许获取预览；实际访问权限没有变化。"
            action={<Button size="small" onClick={() => previewQuery.refetch()}>重试</Button>}
          />
        ) : null}
        {previewQuery.data ? (
          <div className="project-space-preview-zones" aria-label={`${previewRole === 'member' ? '普通成员' : '访客'}可见入口`}>
            {previewTaskZones.map((zone) => (
              <Card key={zone.key} size="small" title={zone.label}>
                <Space wrap>
                  {zone.navigation.map((item) => <Tag key={item.key}>{item.label}</Tag>)}
                </Space>
              </Card>
            ))}
            <Typography.Paragraph type="secondary">
              {previewQuery.data.explanation}
            </Typography.Paragraph>
          </div>
        ) : null}
      </Modal>

      <Suspense fallback={<ProjectSpacePanelFallback />}>
        {view === 'overview' ? (
          space.member
            ? <ProjectSpaceOverview
                space={space}
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
                  onBack={() => returnToTypeCatalog(selectedTypeId)}
                  onSelectField={(fieldId) => onSelectField(selectedTypeId, fieldId)}
                />
              ),
              'configuration-draft': settingsWorkModelEntry ? undefined : (
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
            onBack={() => returnToTypeCatalog(selectedTypeId)}
            configurationDraft={settingsWorkModelEntry ? undefined : (
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
  const location = useLocation()
  const navigate = useNavigate()
  const requestedPanel = new URLSearchParams(location.search).get('panel')
  const supplementalPanels = new Set([
    'cross-space-relations',
    'cross-space-sync',
    'cross-team-panorama',
    'metric-dashboards',
    'metric-risks',
  ])
  const openPanel = (panel: string) => {
    const target = projectSpaceLocationWithContext(
      location.pathname,
      patchProjectSpaceSearch(location.search, { panel }),
      location.hash,
    )
    if (target) navigate(target, { replace: true })
  }

  if (requestedPanel && supplementalPanels.has(requestedPanel)) {
    return (
      <section data-testid="project-space-management">
        <Button type="link" onClick={() => openPanel('project-detail')}>
          返回项目管理
        </Button>
        <ProjectSpaceSecondaryTabs
          view="management"
          testId="project-space-management-results"
          ariaLabel="项目协作与结果导航"
          panels={{
            'cross-space-relations': <CrossSpaceRelationsPanel space={space} />,
            'cross-space-sync': <CrossSpaceSyncPanel space={space} />,
            'cross-team-panorama': <CrossTeamPanoramaPanel space={space} />,
            'metric-dashboards': <MetricDashboardsPanel space={space} />,
            'metric-risks': <MetricRisksPanel space={space} />,
          }}
        />
      </section>
    )
  }

  return (
    <section data-testid="project-space-management">
      <ProjectWorkItemsPanel space={space} surface="management" />
      <Card className="content-card project-space-management-links" title="协作与结果">
        <Typography.Paragraph type="secondary">
          管理事实只在项目管理中维护；成员工作区只展示与你相关的摘要。
        </Typography.Paragraph>
        <Space wrap>
          <Button onClick={() => openPanel('cross-space-relations')}>跨空间关系</Button>
          <Button onClick={() => openPanel('cross-space-sync')}>跨空间同步</Button>
          <Button onClick={() => openPanel('cross-team-panorama')}>跨团队全景</Button>
          <Button onClick={() => openPanel('metric-dashboards')}>结果指标</Button>
          <Button onClick={() => openPanel('metric-risks')}>指标风险</Button>
        </Space>
      </Card>
    </section>
  )
}

function ProjectSpaceOverview({
  space,
}: {
  space: UserProjectSpace
}) {
  const navigate = useNavigate()
  const typesQuery = useQuery({
    queryKey: workItemTypeKeys.active(space.id),
    queryFn: () => listActiveWorkItemTypes(space.id),
    retry: false,
  })
  const personalWorkQuery = useQuery({
    queryKey: ['project-spaces', space.id, 'personal-work'],
    queryFn: () => listProjectSpacePersonalWork(space.id),
    retry: false,
  })
  const activitiesQuery = useQuery({
    queryKey: ['project-spaces', space.id, 'personal-activities'],
    queryFn: () => listProjectSpacePersonalActivities(space.id),
    retry: false,
  })
  const readyTypes = visibleProjectSpaceWorkItemTypes(typesQuery.data)
  const openDeepLink = (deepLink: string) => {
    const target = resolveCanonicalProjectSpaceLocation(deepLink, '?source=member-home')
    if (target) navigate(target)
  }

  return (
    <ProjectSpaceSecondaryTabs
      view="overview"
      testId="project-space-overview-secondary-tabs"
      ariaLabel="成员工作区内容导航"
      panels={{
        'member-home': (
          <section
            className="project-space-overview-panel project-space-member-home"
            data-testid="project-space-member-home"
          >
            <header className="project-space-overview-heading">
              <Typography.Title level={4}>我的工作</Typography.Title>
              <Typography.Text type="secondary">
                集中查看待办事项、负责内容与可创建的工作项
              </Typography.Text>
              {personalWorkQuery.data?.truncated ? (
                <Tag color="blue" className="project-space-overview-hero-tag">还有更多事项</Tag>
              ) : null}
            </header>

            {personalWorkQuery.isLoading ? <Skeleton active paragraph={{ rows: 4 }} /> : null}
            {personalWorkQuery.isError ? (
              <Alert
                type="error"
                showIcon
                message="我的工作加载失败"
                description="没有展示未经确认的缓存内容。请检查网络后重试。"
                action={<Button size="small" onClick={() => personalWorkQuery.refetch()}>重试</Button>}
              />
            ) : null}
            {personalWorkQuery.data ? (
              <div className="project-space-work-buckets" data-testid="project-space-work-buckets">
                {personalWorkQuery.data.buckets.map((bucket) => {
                  const visibleItems = visibleProjectSpacePersonalWork(bucket.items)
                  return (
                    <section
                      className={`project-space-work-bucket${visibleItems.length > 0 ? ' has-items' : ' is-empty'}`}
                      data-testid={`project-space-work-bucket-${bucket.bucket}`}
                      key={bucket.bucket}
                    >
                      <div className="project-space-work-bucket-heading">
                        <strong>{personalWorkBucketLabel(bucket.bucket)}</strong>
                        <span
                          className="project-space-work-bucket-count"
                          data-testid={`project-space-work-bucket-${bucket.bucket}-count`}
                        >
                          {bucket.visibleCount}
                        </span>
                      </div>
                      {visibleItems.length === 0 ? (
                        <Empty
                          className="project-space-work-bucket-empty"
                          image={Empty.PRESENTED_IMAGE_SIMPLE}
                          description="当前没有事项"
                        />
                      ) : (
                        <div className="project-space-personal-work-list" role="list">
                          {visibleItems.slice(0, 3).map((item) => (
                            <div key={`${bucket.bucket}:${item.workItemId}`} role="listitem">
                              <button
                                type="button"
                                className="project-space-personal-work-item"
                                onClick={() => openDeepLink(item.deepLink)}
                              >
                                <span>
                                  <strong>{item.title}</strong>
                                  <small>{item.displayKey} · {item.typeName}</small>
                                </span>
                                <Typography.Text className="project-space-personal-work-open">
                                  打开
                                </Typography.Text>
                              </button>
                            </div>
                          ))}
                        </div>
                      )}
                    </section>
                  )
                })}
              </div>
            ) : null}

            <Card
              className="content-card project-space-active-types"
              data-testid="project-space-active-types"
              title={(
                <span className="project-space-overview-card-title">
                  <BuildOutlined />
                  可用工作项
                </span>
              )}
            >
              {typesQuery.isLoading ? <Skeleton active paragraph={{ rows: 2 }} /> : null}
              {typesQuery.isError ? (
                <Alert
                  type="error"
                  showIcon
                  message="可用工作项加载失败"
                  description="请稍后重试；未就绪配置不会在这里显示。"
                  action={<Button size="small" onClick={() => typesQuery.refetch()}>重试</Button>}
                />
              ) : null}
              {!typesQuery.isLoading && !typesQuery.isError && readyTypes.length === 0 ? (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="当前没有已发布且可用的工作项"
                />
              ) : null}
              <div className="project-space-active-type-list" aria-label="可用工作项" role="list">
                {readyTypes.map((type, index) => {
                  const canCreate = space.status === 'active'
                    && type.availableActions.includes('create')
                  return (
                    <div
                      className="project-space-active-type-item"
                      data-testid={`project-space-work-entry-${index + 1}`}
                      key={type.id}
                      role="listitem"
                    >
                      <button
                        type="button"
                        className={`project-space-active-type${canCreate ? ' is-ready' : ' is-readonly'}`}
                        aria-label={canCreate ? `新建${type.name}` : `查看${type.name}`}
                        onClick={() => navigate(
                          `/project-spaces/${space.id}/work-items?typeId=${type.id}${canCreate ? '&create=1' : ''}`,
                        )}
                      >
                        <span className="project-space-active-type-icon" aria-hidden="true">
                          {overviewWorkItemTypeIcon(type.typeKey)}
                        </span>
                        <span className="project-space-active-type-copy">
                          <strong>{type.name}</strong>
                          <small>{canCreate ? '可创建新事项' : '查看已有事项'}</small>
                        </span>
                        <span className="project-space-active-type-action" aria-hidden="true">
                          {canCreate ? <PlusOutlined /> : <EyeOutlined />}
                        </span>
                      </button>
                    </div>
                  )
                })}
              </div>
            </Card>
          </section>
        ),
        activity: (
          <section
            className="project-space-overview-panel project-space-activity-boundary"
            data-testid="project-space-activity-boundary"
          >
            <header className="project-space-overview-heading">
              <Typography.Title level={4}>动态与边界</Typography.Title>
              <Typography.Text type="secondary">查看空间动态与当前协作边界</Typography.Text>
            </header>

            <div
              className="project-space-activity-boundary-grid"
              data-testid="project-space-activity-boundary-grid"
            >
              <Card
                className="content-card project-space-activity-card"
                data-testid="project-space-activity-card"
                title={(
                  <span className="project-space-overview-card-title">
                    <span className="project-space-overview-card-title-icon"><LineChartOutlined /></span>
                    空间动态
                  </span>
                )}
                extra={activitiesQuery.data ? (
                  <Typography.Text className="project-space-activity-count">
                    {activitiesQuery.data.truncated ? '已显示' : '共'} {activitiesQuery.data.items.length} 条
                  </Typography.Text>
                ) : null}
              >
                {activitiesQuery.isLoading ? <Skeleton active paragraph={{ rows: 8 }} /> : null}
                {activitiesQuery.isError ? (
                  <Alert
                    type="error"
                    showIcon
                    message="空间动态加载失败"
                    action={<Button size="small" onClick={() => activitiesQuery.refetch()}>重试</Button>}
                  />
                ) : null}
                {activitiesQuery.data?.items.length === 0 ? (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有与你相关的空间动态" />
                ) : null}
                {activitiesQuery.data?.items.length ? (
                  <Timeline
                    className="project-space-activity-list"
                    items={activitiesQuery.data.items.map((activity) => ({
                      content: (
                        <button
                          type="button"
                          className="project-space-activity-item"
                          onClick={() => openDeepLink(activity.deepLink)}
                        >
                          <span>
                            <strong>{activity.title}</strong>
                            <small>{activity.displayKey} · {personalActivityLabel(activity.activityType)}</small>
                          </span>
                          <Typography.Text type="secondary">
                            {formatTime(activity.occurredAt)}
                          </Typography.Text>
                        </button>
                      ),
                    }))}
                  />
                ) : null}
              </Card>
              <Card
                className="content-card project-space-boundary-card"
                data-testid="project-space-boundary-card"
                title={(
                  <span className="project-space-overview-card-title">
                    <span className="project-space-overview-card-title-icon"><SafetyCertificateOutlined /></span>
                    协作边界
                  </span>
                )}
              >
                <div className="project-space-boundary-facts">
                  <div>
                    <span className="project-space-boundary-fact-icon"><UserOutlined /></span>
                    <span>当前角色</span>
                    <strong>{roleLabel(space.currentUserRole)}</strong>
                  </div>
                  <div>
                    <span className="project-space-boundary-fact-icon"><EyeOutlined /></span>
                    <span>内容可见性</span>
                    <strong>{visibilityLabel(space.visibility)}</strong>
                  </div>
                  <div>
                    <span className="project-space-boundary-fact-icon"><LockOutlined /></span>
                    <span>空间状态</span>
                    <strong><StatusBadge status={space.status} /></strong>
                  </div>
                </div>
                <Alert
                  className="project-space-boundary-note"
                  type="info"
                  showIcon
                  title="这里是团队成员的日常协作入口。企业治理、全局风险和审计检索只在管理后台提供。"
                />
              </Card>
            </div>
          </section>
        ),
      }}
    />
  )
}

function overviewWorkItemTypeIcon(typeKey: string): ReactNode {
  const normalized = typeKey.toLowerCase()
  if (normalized.includes('requirement')) return <FileTextOutlined />
  if (normalized.includes('task')) return <CarryOutOutlined />
  if (normalized.includes('bug')) return <BugOutlined />
  if (normalized.includes('iteration')) return <SyncOutlined />
  if (normalized.includes('release') || normalized.includes('version')) return <FlagOutlined />
  if (normalized.includes('project')) return <ProjectOutlined />
  return <TagsOutlined />
}

function ProjectSpaceSettingsPanel({
  space,
  mode,
  onTaskResult,
}: {
  space: UserProjectSpace
  mode: ProjectSpaceExperienceMode
  onTaskResult: (
    outcome: ProjectSpaceExperienceEventOutcome,
    errorCode: ProjectSpaceExperienceErrorCode,
  ) => void
}) {
  const { message, modal } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const location = useLocation()
  const sessionScope = useSessionScope()
  const [form] = Form.useForm<SettingsForm>()
  const settingsSearch = new URLSearchParams(location.search)
  const selectedConfigurationTypeId = settingsSearch.get('typeId') ?? undefined
  const requestedWorkModelTab = settingsSearch.get('workModelTab')
  const requestedLayoutKind = settingsSearch.get('layoutKind')
  const selectedWorkModelTab = isProjectSpaceWorkModelTab(requestedWorkModelTab)
    ? requestedWorkModelTab
    : 'type-information'
  const [selectedConfigurationField, setSelectedConfigurationField] = useState<{
    typeId: string
    fieldId?: string
  }>()

  const selectConfigurationType = useCallback((
    typeId: string,
    options: Readonly<{ replace?: boolean }> = {},
  ) => {
    const next = patchProjectSpaceSearch(location.search, { typeId })
    const target = projectSpaceLocationWithContext(
      `/project-spaces/${space.id}/settings`,
      next,
      location.hash,
    )
    if (target) navigate(target, { replace: options.replace })
  }, [location.hash, location.search, navigate, space.id])

  const selectWorkModelTab = useCallback((tab: ProjectSpaceWorkModelTab) => {
    const next = patchProjectSpaceSearch(location.search, {
      panel: 'work-model',
      workModelTab: tab,
    })
    const target = projectSpaceLocationWithContext(
      `/project-spaces/${space.id}/settings`,
      next,
      location.hash,
    )
    if (target) navigate(target, { replace: true })
  }, [location.hash, location.search, navigate, space.id])

  useEffect(() => {
    const panel = new URLSearchParams(location.search).get('panel')
    if (!panel || panel === 'management-home' || !PROJECT_SPACE_SETTINGS_PANEL_LABELS[panel]) {
      return
    }
    if (sessionScope) {
      rememberProjectSpaceSettingsPanel(
        sessionStorage,
        `${sessionScope.workspaceId}:${sessionScope.userId}`,
        space.id,
        panel,
      )
    }
  }, [location.search, sessionScope, space.id])

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
        'management-home': (
          <ProjectSpaceManagementHome
            space={space}
            mode={mode}
            basicInformation={(
              <Card
                className="content-card project-space-management-basic-card"
                title={<span className="project-space-management-card-title">A. 基础信息 <FormOutlined /></span>}
                loading={settingsQuery.isLoading}
                data-testid="project-space-basic-information"
              >
                <Form<SettingsForm>
                  form={form}
                  className="project-space-management-basic-form"
                  labelAlign="left"
                  labelCol={{ flex: '88px' }}
                  wrapperCol={{ flex: 1 }}
                  onFinish={(values) => updateMutation.mutate({ ...values, name: values.name.trim() })}
                >
                  <Form.Item name="name" label="空间名称" rules={[{ required: true, whitespace: true }, { max: 128 }]}><Input aria-label="空间名称" /></Form.Item>
                  <Form.Item name="visibility" label="可见性"><Segmented aria-label="可见性" block options={[{ label: '仅成员可见', value: 'private' }, { label: '企业内可发现', value: 'workspace' }]} /></Form.Item>
                  <Form.Item name="description" label="空间说明" rules={[{ max: 2000 }]}><Input.TextArea aria-label="空间说明" rows={3} /></Form.Item>
                  <Space wrap size={12} className="project-space-management-basic-actions">
                    <Button type="primary" htmlType="submit" loading={updateMutation.isPending} disabled={space.status !== 'active'}>保存设置</Button>
                    {space.availableActions.includes('restore') ? <Button htmlType="button" loading={transitionMutation.isPending} icon={<ReloadOutlined />} className="project-space-restore-button" onClick={() => confirmTransition('restore')}>恢复</Button> : null}
                    {space.availableActions.includes('disable') ? <Button htmlType="button" loading={transitionMutation.isPending} danger icon={<LockOutlined />} onClick={() => confirmTransition('disable')}>停用</Button> : null}
                    {space.availableActions.includes('archive') ? <Button htmlType="button" loading={transitionMutation.isPending} danger icon={<InboxOutlined />} onClick={() => confirmTransition('archive')}>归档</Button> : null}
                  </Space>
                </Form>
              </Card>
            )}
          />
        ),
        'work-model': (
          <Space
            direction="vertical"
            size={16}
            style={{ width: '100%' }}
            data-testid="project-space-advanced-settings"
          >
            <ProjectWorkItemTypesPanel
              space={space}
              selectedTypeId={selectedConfigurationTypeId}
              autoSelectFirst={false}
              onSelectType={selectConfigurationType}
              selectedDetailTab={selectedWorkModelTab}
              onSelectDetailTab={selectWorkModelTab}
              embeddedConfiguration={selectedConfigurationTypeId ? {
                fields: (
                  <ProjectWorkItemFieldsPanel
                    key={`settings-fields:${selectedConfigurationTypeId}`}
                    space={space}
                    typeId={selectedConfigurationTypeId}
                    selectedFieldId={selectedConfigurationField?.typeId === selectedConfigurationTypeId
                      ? selectedConfigurationField.fieldId
                      : undefined}
                    embedded
                    onSelectField={(fieldId) => setSelectedConfigurationField({
                      typeId: selectedConfigurationTypeId,
                      fieldId,
                    })}
                  />
                ),
                layouts: (
                  <ProjectWorkItemLayoutsPanel
                    key={`settings-layouts:${selectedConfigurationTypeId}:${requestedLayoutKind ?? 'default'}`}
                    space={space}
                    typeId={selectedConfigurationTypeId}
                    embedded
                  />
                ),
                flowAccess: (
                  <ProjectWorkItemConfigurationDraftPanel
                    key={`settings-flow-access:${selectedConfigurationTypeId}`}
                    spaceId={space.id}
                    typeId={selectedConfigurationTypeId}
                    readOnly={space.status !== 'active'}
                  />
                ),
              } : undefined}
            />
          </Space>
        ),
        'automation-collaboration': (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Card className="content-card" title="自动化与协同">
              <Typography.Paragraph type="secondary">
                {PROJECT_SPACE_ADVANCED_CONFIGURATION.find(
                  (group) => group.key === 'automation-collaboration',
                )?.description}
              </Typography.Paragraph>
            </Card>
            <ProjectSpaceSecondaryTabs
              view="automation-settings"
              canManage
              testId="project-space-settings-automation"
              ariaLabel="自动化与协同配置导航"
              queryParameter="automationPanel"
              panels={{
                'automation-rules': <AutomationRulesPanel space={space} />,
                'automation-execution': <AutomationExecutionPanel space={space} />,
                'automation-connectors': <AutomationConnectorsPanel space={space} />,
                'cross-space-grants': <CrossSpaceGrantsPanel space={space} />,
                'cross-space-sync': <CrossSpaceSyncPanel space={space} />,
                'automation-management': <AutomationManagementPanel space={space} />,
              }}
            />
          </Space>
        ),
        'metrics-governance': (
          <ProjectSpaceSecondaryTabs
            view="metrics-settings"
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
        ),
        'scenario-templates': (
          <ScenarioTemplatesPanel space={space} onTaskResult={onTaskResult} />
        ),
      }}
    />
  )
}

function ProjectSpaceManagementHome({
  space,
  mode,
  basicInformation,
}: {
  space: UserProjectSpace
  mode: ProjectSpaceExperienceMode
  basicInformation: ReactNode
}) {
  const navigate = useNavigate()
  const sessionScope = useSessionScope()
  const typesQuery = useQuery({
    queryKey: workItemTypeKeys.active(space.id),
    queryFn: () => listActiveWorkItemTypes(space.id),
    retry: false,
  })
  const readyTypes = typesQuery.data?.filter((type) => type.configurationReady) ?? []
  const pendingTypes = typesQuery.data?.filter((type) => !type.configurationReady) ?? []
  const storageScope = sessionScope
    ? `${sessionScope.workspaceId}:${sessionScope.userId}`
    : 'unavailable'
  const recentPanels = sessionScope
    ? readProjectSpaceSettingsPanels(sessionStorage, storageScope, space.id)
    : []
  const cloneReference = sessionScope
    ? readProjectSpaceCloneReference(sessionStorage, storageScope, space.id)
    : null
  const openSettingsPanel = (panel: string) => navigate(
    `/project-spaces/${space.id}/settings?panel=${panel}&source=management-home`,
  )
  const openBasicInformation = () => {
    document.querySelector('[data-testid="project-space-basic-information"]')
      ?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
  const recentEntries = [
    { key: 'general', label: '基本信息' },
    ...recentPanels.map((panel) => ({
      key: panel,
      label: PROJECT_SPACE_SETTINGS_PANEL_LABELS[panel],
    })),
    { key: 'work-model', label: '工作模型' },
    { key: 'scenario-templates', label: '场景模板' },
  ].filter((entry, index, entries) => (
    Boolean(entry.label) && entries.findIndex((candidate) => candidate.key === entry.key) === index
  )).slice(0, 4)

  return (
    <div className="project-space-management-home" data-testid="project-space-management-home">
      <Alert
        type="info"
        showIcon
        message="空间管理集中在这里"
        description={`${mode === 'advanced' ? '高级' : '简洁'}显示模式只调整说明密度，不会增加权限或隐藏已授权的配置入口。`}
      />
      {cloneReference ? (
        <Alert
          type="warning"
          showIcon
          message={`克隆配置预检：参考“${cloneReference}”`}
          description="创建空间时没有复制成员、工作项或配置。请在工作模型中逐项确认后，再明确发布。"
          action={<Button size="small" onClick={() => openSettingsPanel('work-model')}>开始预检</Button>}
        />
      ) : null}
      <div className="project-space-management-dashboard-grid">
        {basicInformation}
        <Card title="B. 配置健康" loading={typesQuery.isLoading}>
          {typesQuery.isError ? (
            <Alert
              type="error"
              showIcon
              message="配置健康暂时不可用"
              action={<Button size="small" onClick={() => typesQuery.refetch()}>重试</Button>}
            />
          ) : (
            <div className="project-space-management-health-list">
              <div><FileDoneOutlined /><span>可用任务模板</span><strong>{readyTypes.length}</strong></div>
              <div><RocketOutlined /><span>待发布配置</span><strong>{pendingTypes.length}</strong></div>
              <div><TeamOutlined /><span>空间成员</span><strong>{space.memberCount}</strong></div>
            </div>
          )}
        </Card>
        <Card title="C. 配置待办">
          {pendingTypes.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有待发布的任务模板" />
          ) : (
            <Space direction="vertical" size={8} style={{ width: '100%' }}>
              {pendingTypes.slice(0, 5).map((type) => (
                <Button
                  block
                  key={type.id}
                  icon={<RocketOutlined />}
                  onClick={() => navigate(
                    `/project-spaces/${space.id}/settings?panel=work-model&workModelTab=flow-access&typeId=${type.id}&source=management-home`,
                  )}
                >
                  发布“{type.name}”的配置
                </Button>
              ))}
            </Space>
          )}
        </Card>
        <Card title="D. 最近入口">
          <div className="project-space-management-recent-grid">
            {recentEntries.map((entry) => (
              <button
                type="button"
                key={entry.key}
                onClick={() => entry.key === 'general'
                  ? openBasicInformation()
                  : openSettingsPanel(entry.key)}
              >
                <span className="project-space-management-recent-icon">
                  {managementPanelIcon(entry.key)}
                </span>
                <strong>{entry.label}</strong>
              </button>
            ))}
          </div>
        </Card>
      </div>
      <Card title="F. 全部管理入口">
        <div className="project-space-management-entry-grid">
          {PROJECT_SPACE_ADVANCED_CONFIGURATION.map((group) => (
            <button
              type="button"
              key={group.key}
              onClick={() => openSettingsPanel(group.key)}
            >
              <span className={`project-space-management-entry-icon is-${group.key}`}>
                {managementPanelIcon(group.key)}
              </span>
              <span className="project-space-management-entry-copy">
                <strong>{group.label}</strong>
                <span>{group.description}</span>
              </span>
              <RightOutlined className="project-space-management-entry-arrow" />
            </button>
          ))}
          <button type="button" onClick={() => navigate(`/project-spaces/${space.id}/members`)}>
            <span className="project-space-management-entry-icon is-members"><TeamOutlined /></span>
            <span className="project-space-management-entry-copy">
              <strong>成员与邀请</strong>
              <span>调整成员角色、邀请状态与空间协作边界。</span>
            </span>
            <RightOutlined className="project-space-management-entry-arrow" />
          </button>
        </div>
      </Card>
    </div>
  )
}

function managementPanelIcon(panel: string): ReactNode {
  if (panel === 'general') return <FormOutlined />
  if (panel === 'work-model') return <AppstoreOutlined />
  if (panel === 'automation-collaboration') return <RobotOutlined />
  if (panel === 'metrics-governance') return <BarChartOutlined />
  if (panel === 'scenario-templates') return <ProjectOutlined />
  return <SettingOutlined />
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

const PROJECT_SPACE_SETTINGS_PANEL_LABELS: Readonly<Record<string, string>> = {
  'work-model': '工作模型',
  'automation-collaboration': '自动化与协同',
  'metrics-governance': '度量治理',
  'scenario-templates': '场景模板',
}

function projectSpaceSettingsRecentKey(scope: string, spaceId: string) {
  return `colla:project-space-settings-recent:${scope}:${spaceId}`
}

function projectSpaceCloneReferenceKey(scope: string, spaceId: string) {
  return `colla:project-space-clone-reference:${scope}:${spaceId}`
}

function rememberProjectSpaceCloneReference(
  storage: Storage,
  scope: string,
  spaceId: string,
  referenceName: string,
) {
  storage.setItem(projectSpaceCloneReferenceKey(scope, spaceId), referenceName.slice(0, 128))
}

function readProjectSpaceCloneReference(storage: Storage, scope: string, spaceId: string) {
  return storage.getItem(projectSpaceCloneReferenceKey(scope, spaceId))
}

function readProjectSpaceSettingsPanels(
  storage: Storage,
  scope: string,
  spaceId: string,
): string[] {
  try {
    const parsed = JSON.parse(
      storage.getItem(projectSpaceSettingsRecentKey(scope, spaceId)) ?? '[]',
    )
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter((panel): panel is string => (
        typeof panel === 'string' && Boolean(PROJECT_SPACE_SETTINGS_PANEL_LABELS[panel])
      ))
      .slice(0, 4)
  } catch {
    return []
  }
}

function rememberProjectSpaceSettingsPanel(
  storage: Storage,
  scope: string,
  spaceId: string,
  panel: string,
) {
  if (!PROJECT_SPACE_SETTINGS_PANEL_LABELS[panel]) return
  const recent = readProjectSpaceSettingsPanels(storage, scope, spaceId)
  storage.setItem(
    projectSpaceSettingsRecentKey(scope, spaceId),
    JSON.stringify([panel, ...recent.filter((candidate) => candidate !== panel)].slice(0, 4)),
  )
}

function createStartingModeImpact(
  mode: CreateSpaceForm['startingMode'],
  scenarioKey?: ProjectSpaceOnboardingScenarioKey,
  referenceName?: string,
) {
  if (mode === 'scenario') {
    const scenarioLabels: Record<ProjectSpaceOnboardingScenarioKey, string> = {
      development: '研发协作',
      marketing: '市场项目',
      'human-resources': 'HR 事务',
      delivery: '交付管理',
    }
    return [
      { label: '起步路径', value: scenarioKey ? scenarioLabels[scenarioKey] : '请选择模板' },
      { label: '创建时', value: '只保存引导选择' },
      { label: '不会发生', value: '不会自动安装或发布配置' },
    ]
  }
  if (mode === 'clone') {
    return [
      { label: '参考来源', value: referenceName ?? '请选择空间' },
      { label: '创建时', value: '建立空白空间并进入预检' },
      { label: '不会复制', value: '成员、工作项、附件和权限' },
    ]
  }
  return [
    { label: '起步路径', value: '基础空间' },
    { label: '创建时', value: '保留平台基础任务模板' },
    { label: '后续动作', value: '按依赖配置并明确发布' },
  ]
}

function defaultProjectSpacePath(space: UserProjectSpace) {
  return `/project-spaces/${space.id}`
}

function personalWorkBucketLabel(bucket: string) {
  if (bucket === 'todo') return '待我处理'
  if (bucket === 'responsible') return '我负责的'
  if (bucket === 'participating') return '我参与的'
  if (bucket === 'watching') return '我关注的'
  return '我的事项'
}

function personalActivityLabel(activityType: string) {
  const labels: Record<string, string> = {
    created: '已创建',
    updated: '已更新',
    transitioned: '状态已变化',
    commented: '有新协作记录',
    participant_added: '新增参与人',
    participant_removed: '参与人已调整',
    archived: '已归档',
  }
  return labels[activityType] ?? '事项有新变化'
}
