import {
  ArrowLeftOutlined,
  AppstoreOutlined,
  ApartmentOutlined,
  BarChartOutlined,
  CalendarOutlined,
  CommentOutlined,
  DownloadOutlined,
  FileOutlined,
  HistoryOutlined,
  InboxOutlined,
  PaperClipOutlined,
  PlusOutlined,
  ReloadOutlined,
  SaveOutlined,
  TableOutlined,
  TeamOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntdApp,
  Avatar,
  Button,
  Card,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Progress,
  Segmented,
  Select,
  Skeleton,
  Space,
  Table,
  Tabs,
  Tag,
  Tree,
  type TreeDataNode,
  Typography,
  Upload,
} from 'antd'
import {
  useEffect,
  useMemo,
  useState,
  type CSSProperties,
  type Key,
  type KeyboardEvent,
} from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import { ApiRequestError } from '../../../shared/api/httpClient'
import { listProjectSpaceMembers, type UserProjectSpace } from '../api/projectSpacesApi'
import {
  addWorkItemAttachment,
  addWorkItemComment,
  createWorkItem,
  getWorkItem,
  getWorkItemCreateForm,
  getWorkItemAttachmentDownloadUrl,
  listWorkItemActivities,
  listWorkItemAttachments,
  listWorkItemComments,
  listWorkItemParticipants,
  nudgeWorkItemParticipant,
  putWorkItemParticipant,
  transitionWorkItem,
  updateWorkItem,
  uploadWorkItemFile,
  workItemKeys,
  type WorkItem,
  type WorkItemRuntime,
} from '../api/workItemsApi'
import { listActiveWorkItemTypes, workItemTypeKeys } from '../api/workItemTypesApi'
import {
  bulkWorkItems,
  exportWorkItemView,
  getWorkItemViewPreference,
  renderWorkItemView,
  saveWorkItemViewPreference,
  workItemViewKeys,
  type QueryDefinition,
  type WorkItemColumn,
  type WorkItemViewRow,
} from '../api/workItemViewsApi'
import {
  getWorkItemCalendarPreference,
  mutateWorkItemCalendarDate,
  renderWorkItemCalendar,
  saveWorkItemCalendarPreference,
  workItemCalendarKeys,
  type WorkItemCalendarBinding,
  type WorkItemCalendarEvent,
} from '../api/workItemCalendarsApi'
import {
  getWorkItemGanttPreference,
  mutateWorkItemGanttDate,
  renderWorkItemGantt,
  saveWorkItemGanttPreference,
  workItemGanttKeys,
  type WorkItemGanttBar,
} from '../api/workItemGanttsApi'
import {
  compareWorkItemScheduleBaseline,
  createWorkItemScheduleBaseline,
  deleteWorkItemScheduleBaseline,
  listWorkItemScheduleBaselines,
  renderWorkItemTimeline,
  workItemScheduleKeys,
  type WorkItemScheduleBaselineDiff,
} from '../api/workItemSchedulesApi'
import {
  getWorkItemBoardPreference,
  moveWorkItemBoardCard,
  renderWorkItemBoard,
  saveWorkItemBoardPreference,
  workItemBoardKeys,
  type WorkItemBoardCard,
  type WorkItemBoardColumn,
  type WorkItemBoardResult,
} from '../api/workItemBoardsApi'
import {
  getWorkItemTreePreference,
  renderWorkItemTree,
  saveWorkItemTreePreference,
  workItemTreeViewKeys,
  type WorkItemTreeNode,
} from '../api/workItemTreeViewsApi'
import {
  copySavedView,
  createSavedView,
  deleteSavedView,
  executeSavedView,
  favoriteSavedView,
  listSavedViews,
  revokeSavedView,
  savedViewKeys,
  shareSavedView,
  transferSavedView,
  updateSavedView,
  type WorkItemSavedView,
} from '../api/workItemSavedViewsApi'
import type { ConfiguredWorkItemField } from '../api/workItemFieldsApi'
import type {
  WorkItemFieldAccessProjection,
  WorkItemLayoutNode,
} from '../api/workItemLayoutsApi'
import { WorkItemLayoutRenderer, type WorkItemLayoutValues } from './WorkItemLayoutRenderer'
import { WorkItemNodeWorkflowPanel } from './WorkItemNodeWorkflowPanel'
import { WorkItemRelationsPanel } from './WorkItemRelationsPanel'
import { WorkItemPermissionsPanel } from './WorkItemPermissionsPanel'
import { WorkItemWorkflowPanel } from './WorkItemWorkflowPanel'
import { errorMessage, formatTime } from '../projectSpaceView'
import {
  createProjectPlan,
  getProjectPlan,
  listProjectPlans,
  mutateProjectPlan,
  projectPlanKeys,
  type ProjectPlan,
} from '../api/projectPlansApi'
import { ProjectRegisterPanel } from './ProjectRegisterPanel'
import { ProjectDeliveryPanel } from './ProjectDeliveryPanel'
import { ProjectDetailPanel } from './ProjectDetailPanel'
import { ResourcePlanningPanel } from './ResourcePlanningPanel'
import { ResourceWorklogPanel } from './ResourceWorklogPanel'
import { ResourceCapacityPanel } from './ResourceCapacityPanel'
import { ResourceSchedulePanel } from './ResourceSchedulePanel'
import { AutomationRulesPanel } from './AutomationRulesPanel'
import { AutomationExecutionPanel } from './AutomationExecutionPanel'
import { AutomationConnectorsPanel } from './AutomationConnectorsPanel'
import { AutomationManagementPanel } from './AutomationManagementPanel'

export function ProjectWorkItemsPanel({
  space,
  workItemId,
}: {
  space: UserProjectSpace
  workItemId?: string
}) {
  return workItemId
    ? <WorkItemDetail space={space} workItemId={workItemId} />
    : (
      <>
        <ProjectDetailPanel space={space} />
        <ResourcePlanningPanel space={space} />
        <ResourceWorklogPanel space={space} />
        <ResourceCapacityPanel space={space} />
        <ResourceSchedulePanel space={space} />
        <AutomationRulesPanel space={space} />
        <AutomationExecutionPanel space={space} />
        <AutomationConnectorsPanel space={space} />
        <AutomationManagementPanel space={space} />
        <ProjectPlanPanel space={space} />
        <ProjectRegisterPanel space={space} />
        <ProjectDeliveryPanel space={space} />
        <WorkItemCollection space={space} />
      </>
    )
}

function ProjectPlanPanel({ space }: { space: UserProjectSpace }) {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const today = new Date().toISOString().slice(0, 10)
  const [selectedPlanId, setSelectedPlanId] = useState<string>()
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [startDate, setStartDate] = useState(today)
  const [endDate, setEndDate] = useState(() => shiftDate(today, 30))
  const listQuery = useQuery({
    queryKey: projectPlanKeys.list(space.id),
    queryFn: () => listProjectPlans(space.id),
  })
  const detailQuery = useQuery({
    queryKey: projectPlanKeys.detail(space.id, selectedPlanId ?? 'none'),
    queryFn: () => getProjectPlan(space.id, selectedPlanId!),
    enabled: Boolean(selectedPlanId),
  })
  const refresh = async (plan: ProjectPlan) => {
    setSelectedPlanId(plan.plan.id)
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: projectPlanKeys.list(space.id) }),
      queryClient.invalidateQueries({
        queryKey: projectPlanKeys.detail(space.id, plan.plan.id),
      }),
    ])
  }
  const createMutation = useMutation({
    mutationFn: () => createProjectPlan(space.id, {
      name,
      description,
      startDate,
      endDate,
    }),
    onSuccess: async (plan) => {
      await refresh(plan)
      message.success('项目计划已创建')
    },
    onError: (error) => message.error(errorMessage(error, '项目计划创建失败，请刷新后重试')),
  })
  const mutateMutation = useMutation({
    mutationFn: ({
      operation,
      plan,
    }: {
      operation: 'update' | 'publish' | 'archive' | 'restore'
      plan: ProjectPlan
    }) => mutateProjectPlan(
      space.id,
      plan,
      operation,
      operation === 'update' ? { name, description } : undefined,
    ),
    onSuccess: async (plan, variables) => {
      await refresh(plan)
      message.success({
        update: '项目计划已保存',
        publish: '项目计划已发布',
        archive: '项目计划已归档',
        restore: '项目计划已恢复为草稿',
      }[variables.operation])
    },
    onError: (error) => message.error(errorMessage(error, '项目计划操作失败，请刷新后重试')),
  })
  const current = detailQuery.data
  const writable = space.status === 'active' && space.currentUserRole !== 'guest'
  const validCreate = name.trim()
    && startDate
    && endDate
    && endDate >= startDate

  return (
    <Card
      className="content-card project-plan-panel"
      data-testid="project-plan-panel"
      title={<Space><CalendarOutlined />项目计划与里程碑</Space>}
      extra={<Tag color="blue">S15 · 当前权限实时校准</Tag>}
    >
      <div className="project-plan-toolbar">
        <Select
          aria-label="项目计划"
          allowClear
          showSearch
          value={selectedPlanId}
          loading={listQuery.isLoading}
          placeholder="选择项目计划"
          optionFilterProp="label"
          options={(listQuery.data ?? []).map((plan) => ({
            label: `${plan.name} · ${plan.status}`,
            value: plan.id,
          }))}
          onChange={(planId) => {
            setSelectedPlanId(planId)
            const selected = listQuery.data?.find((plan) => plan.id === planId)
            if (!selected) return
            setName(selected.name)
            setDescription(selected.description)
            setStartDate(selected.startDate)
            setEndDate(selected.endDate)
          }}
        />
        <Input
          aria-label="计划名称"
          value={name}
          maxLength={120}
          placeholder="计划名称"
          onChange={(event) => setName(event.target.value)}
        />
        <Input
          aria-label="计划说明"
          value={description}
          maxLength={1000}
          placeholder="计划说明"
          onChange={(event) => setDescription(event.target.value)}
        />
        <Input
          aria-label="计划开始日期"
          type="date"
          value={startDate}
          disabled={Boolean(current)}
          onChange={(event) => setStartDate(event.target.value)}
        />
        <Input
          aria-label="计划结束日期"
          type="date"
          value={endDate}
          disabled={Boolean(current)}
          onChange={(event) => setEndDate(event.target.value)}
        />
      </div>
      <Space wrap>
        <Button
          type="primary"
          disabled={!writable || !validCreate}
          loading={createMutation.isPending}
          onClick={() => createMutation.mutate()}
        >
          新建计划
        </Button>
        <Button
          disabled={!writable || !current || current.plan.status === 'archived' || !name.trim()}
          loading={mutateMutation.isPending}
          onClick={() => current && mutateMutation.mutate({
            operation: 'update',
            plan: current,
          })}
        >
          保存名称与说明
        </Button>
        {current?.plan.status === 'draft' ? (
          <Button
            disabled={!writable}
            onClick={() => mutateMutation.mutate({ operation: 'publish', plan: current })}
          >
            发布计划
          </Button>
        ) : null}
        {current && current.plan.status !== 'archived' ? (
          <Button
            danger
            disabled={!writable}
            onClick={() => mutateMutation.mutate({ operation: 'archive', plan: current })}
          >
            归档计划
          </Button>
        ) : null}
        {current?.plan.status === 'archived' ? (
          <Button
            disabled={!writable}
            onClick={() => mutateMutation.mutate({ operation: 'restore', plan: current })}
          >
            恢复为草稿
          </Button>
        ) : null}
      </Space>
      {!writable ? (
        <Typography.Paragraph type="secondary">
          当前身份只读；项目计划写入仅对 active owner、admin 和 member 开放。
        </Typography.Paragraph>
      ) : null}
      {detailQuery.isError ? (
        <Alert
          type="error"
          showIcon
          message="项目计划加载失败"
          description={errorMessage(detailQuery.error, '请刷新后重试')}
        />
      ) : null}
      {current ? (
        <div className="project-plan-summary">
          <Space wrap>
            <Tag color={current.plan.status === 'published' ? 'green' : 'default'}>
              {current.plan.status}
            </Tag>
            <Tag>阶段 {current.phases.length}</Tag>
            <Tag>里程碑 {current.progress.visibleMilestones}</Tag>
            <Tag color={current.progress.overdueMilestones > 0 ? 'red' : 'blue'}>
              逾期 {current.progress.overdueMilestones}
            </Tag>
            <Tag>当前可见关联 {current.progress.visibleLinks}</Tag>
            {current.progress.truncated ? <Tag color="orange">隐藏关联已省略</Tag> : null}
          </Space>
          <Progress
            percent={current.progress.completionPercent}
            aria-label="项目计划完成度"
            size="small"
          />
          <List
            size="small"
            loading={detailQuery.isLoading}
            locale={{ emptyText: '暂无里程碑' }}
            dataSource={current.milestones}
            renderItem={(milestone) => (
              <List.Item>
                <Space wrap>
                  <strong>{milestone.name}</strong>
                  <Tag>{milestone.status}</Tag>
                  <Typography.Text type="secondary">
                    {milestone.targetDate}
                  </Typography.Text>
                </Space>
              </List.Item>
            )}
          />
          <Typography.Paragraph type="secondary">
            计划阶段不同于流程节点；里程碑日期不会改写工作项日期。隐藏或收权关联不会进入标题、数量或进度。
          </Typography.Paragraph>
        </div>
      ) : (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="尚未选择项目计划；可填写名称和日期创建草稿。"
        />
      )}
    </Card>
  )
}

function WorkItemCollection({ space }: { space: UserProjectSpace }) {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [createOpen, setCreateOpen] = useState(searchParams.get('create') === '1')
  const [selectedTypeOverride, setSelectedTypeOverride] = useState(searchParams.get('typeId') ?? undefined)
  const typesQuery = useQuery({
    queryKey: workItemTypeKeys.active(space.id),
    queryFn: () => listActiveWorkItemTypes(space.id),
  })
  const selectedTypeId = selectedTypeOverride ?? typesQuery.data?.[0]?.id
  const preferenceQuery = useQuery({
    queryKey: workItemViewKeys.preference(space.id),
    queryFn: () => getWorkItemViewPreference(space.id),
  })
  const [modeOverride, setModeOverride] = useState<'table' | 'list'>()
  const [treeMode, setTreeMode] = useState(false)
  const [boardMode, setBoardMode] = useState(false)
  const [calendarMode, setCalendarMode] = useState(false)
  const [ganttMode, setGanttMode] = useState(false)
  const [boardColumnsOverride, setBoardColumnsOverride] = useState<WorkItemBoardColumn[]>()
  const [boardSwimlaneOverride, setBoardSwimlaneOverride] = useState<string | null>()
  const [draggedBoardCard, setDraggedBoardCard] = useState<WorkItemBoardCard>()
  const [draggedCalendarEvent, setDraggedCalendarEvent] = useState<WorkItemCalendarEvent>()
  const [calendarBindingOverride, setCalendarBindingOverride] = useState<WorkItemCalendarBinding>()
  const [calendarTimezoneOverride, setCalendarTimezoneOverride] = useState<string>()
  const [calendarViewModeOverride, setCalendarViewModeOverride] = useState<'month' | 'week' | 'day'>()
  const [calendarAnchorDate, setCalendarAnchorDate] = useState(() =>
    new Date().toISOString().slice(0, 10))
  const [ganttZoomOverride, setGanttZoomOverride] = useState<'day' | 'week' | 'month'>()
  const [ganttExpandedOverride, setGanttExpandedOverride] = useState<string[]>()
  const [baselineName, setBaselineName] = useState('')
  const [selectedBaselineId, setSelectedBaselineId] = useState<string>()
  const [baselineDiff, setBaselineDiff] = useState<WorkItemScheduleBaselineDiff>()
  const [densityOverride, setDensityOverride] = useState<'compact' | 'comfortable'>()
  const [columnKeysOverride, setColumnKeysOverride] = useState<string[]>()
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([])
  const [savedDefinitionOverride, setSavedDefinitionOverride] = useState<QueryDefinition>()
  const [selectedSavedViewId, setSelectedSavedViewId] = useState(
    searchParams.get('savedViewId') ?? undefined,
  )
  const [saveViewOpen, setSaveViewOpen] = useState(false)
  const [savedViewName, setSavedViewName] = useState('')
  const [savedViewDescription, setSavedViewDescription] = useState('')
  const [shareOpen, setShareOpen] = useState(false)
  const [shareUserId, setShareUserId] = useState<string>()
  const [sharePermission, setSharePermission] = useState<'use' | 'manage'>('use')
  const [expandedTreeKeys, setExpandedTreeKeys] = useState<Key[]>()
  const [loadedTreeState, setLoadedTreeState] = useState<{
    queryHash: string
    children: Record<string, WorkItemTreeNode[]>
  }>()
  const mode = modeOverride ?? preferenceQuery.data?.mode ?? 'table'
  const density = densityOverride ?? preferenceQuery.data?.density ?? 'comfortable'
  const availableColumns = useMemo<WorkItemColumn[]>(() => [
    { key: 'displayKey', label: '编号', width: 120, frozen: true, format: 'text' },
    { key: 'title', label: '标题', width: 320, frozen: true, format: 'text' },
    { key: 'status', label: '状态', width: 120, frozen: false, format: 'tag' },
    { key: 'updatedAt', label: '更新于', width: 190, frozen: false, format: 'datetime' },
    { key: 'createdAt', label: '创建于', width: 190, frozen: false, format: 'datetime' },
    { key: 'state', label: '流程状态', width: 150, frozen: false, format: 'tag' },
    { key: 'participantRole', label: '我的角色', width: 150, frozen: false, format: 'tag' },
  ], [])
  const activeColumnKeys = columnKeysOverride
    ?? preferenceQuery.data?.columns.map((column) => column.key)
    ?? availableColumns.slice(0, 4).map((column) => column.key)
  const columns = availableColumns.filter((column) => activeColumnKeys.includes(column.key))
  const defaultDefinition = useMemo<QueryDefinition>(() => ({
    schemaVersion: 1,
    typeId: selectedTypeId,
    filter: null,
    sorts: [{ field: 'updatedAt', direction: 'desc', nulls: 'last' }],
    group: null,
    select: columns.map((column) => column.key),
    limit: 50,
    cursor: null,
  }), [columns, selectedTypeId])
  const definition = savedDefinitionOverride ?? defaultDefinition
  const selectedType = typesQuery.data?.find((type) => type.id === selectedTypeId)
  const boardViewKey = `type-${selectedTypeId ?? 'none'}`
  const defaultBoardColumns = useMemo<WorkItemBoardColumn[]>(() => {
    if (selectedType?.typeKey === 'bug') {
      return [
        { key: 'open', label: '待处理', wipLimit: 8, moveKind: 'state', moveActionKey: 'reopen' },
        { key: 'in_progress', label: '处理中', wipLimit: 5, moveKind: 'state', moveActionKey: 'start_progress' },
        { key: 'resolved', label: '待验证', wipLimit: 4, moveKind: 'state', moveActionKey: 'mark_fixed' },
        { key: 'closed', label: '已关闭', wipLimit: 0, moveKind: 'state', moveActionKey: 'verify_passed' },
        { key: 'canceled', label: '已取消', wipLimit: 0, moveKind: 'state', moveActionKey: 'terminate' },
      ]
    }
    return [
      { key: 'open', label: '待处理', wipLimit: 8, moveKind: 'state', moveActionKey: 'reopen' },
      { key: 'in_progress', label: '处理中', wipLimit: 5, moveKind: 'state', moveActionKey: 'start_progress' },
      { key: 'done', label: '已完成', wipLimit: 0, moveKind: 'state', moveActionKey: 'complete' },
      { key: 'canceled', label: '已取消', wipLimit: 0, moveKind: 'state', moveActionKey: 'terminate' },
    ]
  }, [selectedType?.typeKey])
  const boardPreferenceQuery = useQuery({
    queryKey: workItemBoardKeys.preference(space.id, boardViewKey),
    queryFn: () => getWorkItemBoardPreference(space.id, boardViewKey),
    enabled: Boolean(selectedTypeId) && boardMode,
  })
  const boardColumns = boardColumnsOverride
    ?? boardPreferenceQuery.data?.columns
    ?? defaultBoardColumns
  const boardSwimlaneField = boardSwimlaneOverride !== undefined
    ? boardSwimlaneOverride
    : boardPreferenceQuery.data?.swimlaneField ?? null
  const boardRequest = {
    schemaVersion: 1 as const,
    viewKey: boardViewKey,
    columnField: 'state',
    swimlaneField: boardSwimlaneField,
    columns: boardColumns,
    query: definition,
  }
  const boardSignature = JSON.stringify(boardRequest)
  const boardQuery = useQuery({
    queryKey: workItemBoardKeys.render(space.id, boardSignature),
    queryFn: () => renderWorkItemBoard(space.id, boardRequest),
    enabled: Boolean(selectedTypeId) && boardMode,
  })
  const calendarViewKey = `type-${selectedTypeId ?? 'none'}`
  const calendarPreferenceQuery = useQuery({
    queryKey: workItemCalendarKeys.preference(space.id, calendarViewKey),
    queryFn: () => getWorkItemCalendarPreference(space.id, calendarViewKey),
    enabled: Boolean(selectedTypeId) && calendarMode,
  })
  const calendarBinding = calendarBindingOverride
    ?? calendarPreferenceQuery.data?.binding
    ?? { startField: 'due_date', endField: null, allDay: true }
  const calendarTimezone = calendarTimezoneOverride
    ?? calendarPreferenceQuery.data?.timezone
    ?? Intl.DateTimeFormat().resolvedOptions().timeZone
    ?? 'UTC'
  const calendarViewMode = calendarViewModeOverride
    ?? calendarPreferenceQuery.data?.mode
    ?? 'month'
  const calendarRange = calendarWindow(calendarAnchorDate, calendarViewMode)
  const calendarRequest = {
    schemaVersion: 1 as const,
    viewKey: calendarViewKey,
    binding: calendarBinding,
    window: {
      ...calendarRange,
      timezone: calendarTimezone,
      mode: calendarViewMode,
    },
    query: definition,
  }
  const calendarSignature = JSON.stringify(calendarRequest)
  const calendarQuery = useQuery({
    queryKey: workItemCalendarKeys.render(space.id, calendarSignature),
    queryFn: () => renderWorkItemCalendar(space.id, calendarRequest),
    enabled: Boolean(selectedTypeId) && calendarMode,
  })
  const ganttViewKey = `type-${selectedTypeId ?? 'none'}`
  const ganttPreferenceQuery = useQuery({
    queryKey: workItemGanttKeys.preference(space.id, ganttViewKey),
    queryFn: () => getWorkItemGanttPreference(space.id, ganttViewKey),
    enabled: Boolean(selectedTypeId) && ganttMode,
  })
  const ganttZoom = ganttZoomOverride ?? ganttPreferenceQuery.data?.zoom ?? 'week'
  const ganttExpanded = ganttExpandedOverride
    ?? ganttPreferenceQuery.data?.expandedNodeIds
    ?? []
  const ganttRequest = {
    schemaVersion: 1 as const,
    viewKey: ganttViewKey,
    binding: calendarBinding,
    window: {
      ...calendarRange,
      timezone: calendarTimezone,
      mode: calendarViewMode,
    },
    query: definition,
    hierarchyRelationKey: 'parent_child',
    expandedNodeIds: ganttExpanded,
    criticalPath: true,
  }
  const ganttSignature = JSON.stringify(ganttRequest)
  const ganttQuery = useQuery({
    queryKey: workItemGanttKeys.render(space.id, ganttSignature),
    queryFn: () => renderWorkItemGantt(space.id, ganttRequest),
    enabled: Boolean(selectedTypeId) && ganttMode,
  })
  const baselinesQuery = useQuery({
    queryKey: workItemScheduleKeys.baselines(space.id),
    queryFn: () => listWorkItemScheduleBaselines(space.id),
    enabled: Boolean(selectedTypeId) && ganttMode,
  })
  const timelineQuery = useQuery({
    queryKey: workItemScheduleKeys.timeline(space.id, ganttSignature),
    queryFn: () => renderWorkItemTimeline(space.id, ganttRequest),
    enabled: Boolean(selectedTypeId) && ganttMode,
  })
  const createBaselineMutation = useMutation({
    mutationFn: () => createWorkItemScheduleBaseline(
      space.id,
      baselineName,
      ganttRequest,
    ),
    onSuccess: async (snapshot) => {
      setSelectedBaselineId(snapshot.baseline.id)
      setBaselineName('')
      setBaselineDiff(undefined)
      await queryClient.invalidateQueries({
        queryKey: workItemScheduleKeys.baselines(space.id),
      })
      message.success('排期基线已创建')
    },
    onError: (error) => message.error(errorMessage(error, '基线创建失败，请刷新后重试')),
  })
  const compareBaselineMutation = useMutation({
    mutationFn: (baselineId: string) => compareWorkItemScheduleBaseline(
      space.id,
      baselineId,
      ganttRequest,
    ),
    onSuccess: (result) => {
      setBaselineDiff(result)
      message.success('基线差异已按当前权限重新计算')
    },
    onError: (error) => message.error(errorMessage(error, '基线比较失败，请刷新后重试')),
  })
  const deleteBaselineMutation = useMutation({
    mutationFn: async (baselineId: string) => {
      const baseline = baselinesQuery.data?.find((value) => value.id === baselineId)
      if (!baseline) throw new Error('Baseline is no longer available')
      return deleteWorkItemScheduleBaseline(space.id, baseline)
    },
    onSuccess: async () => {
      setSelectedBaselineId(undefined)
      setBaselineDiff(undefined)
      await queryClient.invalidateQueries({
        queryKey: workItemScheduleKeys.baselines(space.id),
      })
      message.success('排期基线已删除')
    },
    onError: (error) => message.error(errorMessage(error, '基线删除失败，请刷新后重试')),
  })
  const savedViewsQuery = useQuery({
    queryKey: savedViewKeys.list(space.id),
    queryFn: () => listSavedViews(space.id),
  })
  const membersQuery = useQuery({
    queryKey: ['project-spaces', space.id, 'members', 'saved-view-sharing'],
    queryFn: () => listProjectSpaceMembers(space.id),
    enabled: shareOpen,
  })
  const selectedSavedView = savedViewsQuery.data?.find((view) => view.id === selectedSavedViewId)
  const viewSignature = JSON.stringify({ mode, density, columns, definition })
  const itemsQuery = useQuery({
    queryKey: workItemViewKeys.render(space.id, viewSignature),
    queryFn: () => renderWorkItemView(space.id, {
      schemaVersion: 1,
      mode,
      density,
      columns,
      query: definition,
    }),
    enabled: Boolean(selectedTypeId) && !treeMode && !boardMode && !calendarMode && !ganttMode,
  })
  const treePreferenceQuery = useQuery({
    queryKey: workItemTreeViewKeys.preference(space.id),
    queryFn: () => getWorkItemTreePreference(space.id),
    enabled: treeMode && !boardMode && !calendarMode && !ganttMode,
  })
  const treeQuery = useQuery({
    queryKey: workItemTreeViewKeys.roots(space.id, viewSignature),
    queryFn: () => renderWorkItemTree(space.id, definition),
    enabled: Boolean(selectedTypeId) && treeMode && !boardMode && !calendarMode && !ganttMode,
  })
  const effectiveExpandedTreeKeys = expandedTreeKeys
    ?? treePreferenceQuery.data?.expandedNodeIds
    ?? []
  const loadedTreeChildren = useMemo(
    () => loadedTreeState && loadedTreeState.queryHash === treeQuery.data?.queryHash
      ? loadedTreeState.children
      : {},
    [loadedTreeState, treeQuery.data?.queryHash],
  )
  const loadedTreeItems = useMemo(() => [
    ...(treeQuery.data?.items ?? []),
    ...Object.values(loadedTreeChildren).flat(),
  ], [loadedTreeChildren, treeQuery.data?.items])
  const treeData = useMemo(() => {
    const build = (item: WorkItemTreeNode): TreeDataNode => ({
      key: item.id,
      title: (
        <Space wrap size="small">
          <strong>{item.title}</strong>
          <Tag>{item.displayKey}</Tag>
          <Tag>{item.status}</Tag>
          {item.matchKind === 'context' ? <Tag color="default">路径上下文</Tag> : null}
        </Space>
      ),
      isLeaf: !item.expandable,
      children: loadedTreeChildren[item.id]?.map(build),
    })
    return (treeQuery.data?.items ?? []).map(build)
  }, [loadedTreeChildren, treeQuery.data?.items])
  const savePreferenceMutation = useMutation<unknown>({
    mutationFn: () => ganttMode
      ? saveWorkItemGanttPreference(
          space.id,
          ganttViewKey,
          ganttPreferenceQuery.data?.version ?? 0,
          {
            binding: calendarBinding,
            timezone: calendarTimezone,
            zoom: ganttZoom,
            hierarchyRelationKey: 'parent_child',
            expandedNodeIds: ganttExpanded,
          },
        )
      : calendarMode
      ? saveWorkItemCalendarPreference(
          space.id,
          calendarViewKey,
          calendarPreferenceQuery.data?.version ?? 0,
          calendarBinding,
          calendarTimezone,
          calendarViewMode,
        )
      : boardMode
      ? saveWorkItemBoardPreference(
          space.id,
          boardViewKey,
          boardPreferenceQuery.data?.version ?? 0,
          {
            columnField: 'state',
            swimlaneField: boardSwimlaneField,
            columns: boardColumns,
          },
        )
      : treeMode
      ? saveWorkItemTreePreference(
          space.id,
          treePreferenceQuery.data?.version ?? 0,
          effectiveExpandedTreeKeys.map(String),
        )
      : saveWorkItemViewPreference(space.id, {
          requestId: crypto.randomUUID(),
          expectedVersion: preferenceQuery.data?.version ?? 0,
          mode,
          density,
          columns,
        }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ganttMode
          ? workItemGanttKeys.preference(space.id, ganttViewKey)
          : calendarMode
          ? workItemCalendarKeys.preference(space.id, calendarViewKey)
          : boardMode
          ? workItemBoardKeys.preference(space.id, boardViewKey)
          : treeMode
          ? workItemTreeViewKeys.preference(space.id)
          : workItemViewKeys.preference(space.id),
      })
      message.success('视图偏好已保存')
    },
    onError: (error) => message.error(errorMessage(error, '偏好保存失败，请刷新后重试')),
  })
  const bulkMutation = useMutation({
    mutationFn: (action: 'archive' | 'restore') => bulkWorkItems(
      space.id,
      action,
      (itemsQuery.data?.rows ?? [])
        .concat((boardQuery.data?.columns ?? []).flatMap((column) =>
          column.lanes.flatMap((lane) => lane.cards.map((card) => ({
            workItemId: card.workItemId,
            displayKey: card.displayKey,
            title: card.title,
            version: card.workItemVersion,
            cells: [],
            availableActions: card.availableActions,
          }))),
        ))
        .concat(loadedTreeItems.map((item) => ({
          workItemId: item.id,
          displayKey: item.displayKey,
          title: item.title,
          version: item.version,
          cells: [],
          availableActions: item.availableActions,
        })))
        .filter((row) => selectedRowKeys.includes(row.workItemId))
        .map((row) => ({ workItemId: row.workItemId, expectedVersion: row.version })),
    ),
    onSuccess: async (result) => {
      setSelectedRowKeys([])
      await queryClient.invalidateQueries({ queryKey: workItemViewKeys.all })
      message.success(`批量操作完成：${result.succeeded} 成功，${result.failed} 失败`)
    },
    onError: (error) => message.error(errorMessage(error, '批量操作失败')),
  })
  const exportMutation = useMutation({
    mutationFn: () => exportWorkItemView(space.id, definition, columns),
    onSuccess: ({ job, content }) => {
      const url = URL.createObjectURL(new Blob([content], { type: 'text/csv;charset=utf-8' }))
      const link = document.createElement('a')
      link.href = url
      link.download = `work-items-${job.id}.csv`
      link.click()
      URL.revokeObjectURL(url)
      message.success('受权导出已生成')
    },
    onError: (error) => message.error(errorMessage(error, '导出失败')),
  })
  const boardMoveMutation = useMutation({
    mutationFn: async ({
      card,
      column,
      swimlaneKey,
      rank,
      reorder,
    }: {
      card: WorkItemBoardCard
      column: WorkItemBoardResult['columns'][number]
      swimlaneKey: string
      rank: number
      reorder: boolean
    }) => {
      if (!boardPreferenceQuery.data) {
        await saveWorkItemBoardPreference(space.id, boardViewKey, 0, {
          columnField: 'state',
          swimlaneField: boardSwimlaneField,
          columns: boardColumns,
        })
      }
      const action = reorder
        ? undefined
        : card.moveActions.find((candidate) =>
            candidate.kind === column.column.moveKind
            && candidate.actionKey === column.column.moveActionKey)
      if (!reorder && !action) throw new Error('当前身份没有到目标列的可用流程动作')
      return moveWorkItemBoardCard(space.id, boardViewKey, card, {
        columnKey: column.column.key,
        swimlaneKey,
        rank,
        kind: reorder ? 'reorder' : action!.kind,
        actionKey: reorder ? 'reorder' : action!.actionKey,
        action,
      })
    },
    onSuccess: async () => {
      setDraggedBoardCard(undefined)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: workItemBoardKeys.all }),
        queryClient.invalidateQueries({ queryKey: workItemViewKeys.all }),
      ])
      message.success('看板位置已更新')
    },
    onError: (error) => {
      setDraggedBoardCard(undefined)
      message.error(errorMessage(error, '移动失败；工作项可能已变化，请刷新后重试'))
    },
  })
  const moveBoardCard = (
    card: WorkItemBoardCard,
    column: WorkItemBoardResult['columns'][number],
    swimlaneKey: string,
    rank?: number,
    forceReorder = false,
  ) => {
    const reorder = forceReorder || card.columnKey === column.column.key
    if (!reorder && column.column.wipLimit > 0
      && column.visibleCount >= column.column.wipLimit) {
      message.warning(`“${column.column.label}”已达到 WIP 上限`)
      return
    }
    const lane = column.lanes.find((candidate) => candidate.key === swimlaneKey)
      ?? column.lanes[0]
    const nextRank = rank
      ?? ((lane?.cards.at(-1)?.rank ?? 0) + 1024)
    boardMoveMutation.mutate({
      card,
      column,
      swimlaneKey: lane?.key ?? 'unassigned',
      rank: Math.max(0, nextRank),
      reorder,
    })
  }
  const keyboardBoardMove = (
    event: KeyboardEvent,
    card: WorkItemBoardCard,
    columnIndex: number,
    lane: WorkItemBoardResult['columns'][number]['lanes'][number],
  ) => {
    if (!boardQuery.data || boardMoveMutation.isPending) return
    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
      event.preventDefault()
      const targetIndex = columnIndex + (event.key === 'ArrowLeft' ? -1 : 1)
      const target = boardQuery.data.columns[targetIndex]
      if (target) moveBoardCard(card, target, lane.key)
      return
    }
    if (event.key !== 'ArrowUp' && event.key !== 'ArrowDown') return
    event.preventDefault()
    const index = lane.cards.findIndex((candidate) => candidate.workItemId === card.workItemId)
    if (event.key === 'ArrowUp' && index > 0) {
      const before = lane.cards[index - 1]
      const previous = lane.cards[index - 2]
      moveBoardCard(
        card,
        boardQuery.data.columns[columnIndex],
        lane.key,
        previous ? Math.floor((previous.rank + before.rank) / 2) : Math.floor(before.rank / 2),
        true,
      )
    } else if (event.key === 'ArrowDown' && index < lane.cards.length - 1) {
      const after = lane.cards[index + 1]
      const following = lane.cards[index + 2]
      moveBoardCard(
        card,
        boardQuery.data.columns[columnIndex],
        lane.key,
        following ? Math.floor((after.rank + following.rank) / 2) : after.rank + 1024,
        true,
      )
    }
  }
  const calendarDateMutation = useMutation({
    mutationFn: async ({
      event,
      startValue,
      endValue,
      operation,
    }: {
      event: WorkItemCalendarEvent
      startValue?: string | null
      endValue?: string | null
      operation: 'move' | 'resize'
    }) => {
      if (!calendarPreferenceQuery.data) {
        await saveWorkItemCalendarPreference(
          space.id,
          calendarViewKey,
          0,
          calendarBinding,
          calendarTimezone,
          calendarViewMode,
        )
      }
      return mutateWorkItemCalendarDate(space.id, calendarViewKey, event, {
        operation,
        startValue,
        endValue,
        timezone: calendarTimezone,
      })
    },
    onSuccess: async () => {
      setDraggedCalendarEvent(undefined)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: workItemCalendarKeys.all }),
        queryClient.invalidateQueries({ queryKey: workItemViewKeys.all }),
      ])
      message.success('日历日期已更新')
    },
    onError: (error) => {
      setDraggedCalendarEvent(undefined)
      message.error(errorMessage(error, '日期更新失败；工作项可能已变化，请刷新后重试'))
    },
  })
  const moveCalendarEvent = (event: WorkItemCalendarEvent, targetDate: string) => {
    if (!event.allDay || !event.displayStartDate) {
      message.warning('当前 Web 拖放只处理全天日期；时间点请使用日期编辑')
      return
    }
    const duration = Math.max(
      0,
      dateDistance(event.displayStartDate, event.displayEndDate ?? event.displayStartDate),
    )
    calendarDateMutation.mutate({
      event,
      startValue: targetDate,
      endValue: calendarBinding.endField ? shiftDate(targetDate, duration) : null,
      operation: 'move',
    })
  }
  const keyboardCalendarMove = (event: KeyboardEvent, item: WorkItemCalendarEvent) => {
    if (!item.displayStartDate || !['ArrowLeft', 'ArrowRight'].includes(event.key)) return
    event.preventDefault()
    moveCalendarEvent(item, shiftDate(item.displayStartDate, event.key === 'ArrowLeft' ? -1 : 1))
  }
  const ganttDateMutation = useMutation({
    mutationFn: async ({
      bar,
      startValue,
      endValue,
      operation,
    }: {
      bar: WorkItemGanttBar
      startValue?: string | null
      endValue?: string | null
      operation: 'move' | 'resize'
    }) => {
      if (!ganttPreferenceQuery.data) {
        await saveWorkItemGanttPreference(space.id, ganttViewKey, 0, {
          binding: calendarBinding,
          timezone: calendarTimezone,
          zoom: ganttZoom,
          hierarchyRelationKey: 'parent_child',
          expandedNodeIds: ganttExpanded,
        })
      }
      return mutateWorkItemGanttDate(space.id, ganttViewKey, bar, {
        operation,
        startValue,
        endValue,
        timezone: calendarTimezone,
      })
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: workItemGanttKeys.all }),
        queryClient.invalidateQueries({ queryKey: workItemCalendarKeys.all }),
        queryClient.invalidateQueries({ queryKey: workItemViewKeys.all }),
      ])
      message.success('甘特排期已更新')
    },
    onError: (error) => {
      message.error(errorMessage(error, '排期更新失败；请刷新后重试'))
    },
  })
  const shiftGanttBar = (bar: WorkItemGanttBar, days: number) => {
    if (!bar.startDate) return
    const duration = Math.max(0, dateDistance(
      bar.startDate,
      bar.endDate ?? bar.startDate,
    ))
    const startValue = shiftDate(bar.startDate, days)
    ganttDateMutation.mutate({
      bar,
      startValue,
      endValue: calendarBinding.endField ? shiftDate(startValue, duration) : null,
      operation: 'move',
    })
  }
  const currentPresentation = () => ({
    schemaVersion: 1 as const,
    mode: treeMode ? 'tree' as const : mode,
    density,
    columns,
    relationKey: 'parent_child',
    maxDepth: 32,
  })
  const applySavedView = (view: WorkItemSavedView) => {
    setSelectedSavedViewId(view.id)
    setSelectedTypeOverride(view.query.typeId)
    setBoardColumnsOverride(undefined)
    setBoardSwimlaneOverride(undefined)
    setDraggedBoardCard(undefined)
    setSavedDefinitionOverride({ ...view.query, cursor: null })
    setBoardMode(false)
    setCalendarMode(false)
    setGanttMode(false)
    setTreeMode(view.presentation.mode === 'tree')
    if (view.presentation.mode !== 'tree') setModeOverride(view.presentation.mode)
    setDensityOverride(view.presentation.density)
    setColumnKeysOverride(view.presentation.columns.map((column) => column.key))
    setSearchParams({ savedViewId: view.id }, { replace: true })
  }
  const refreshSavedViews = async () => {
    await queryClient.invalidateQueries({ queryKey: savedViewKeys.list(space.id) })
  }
  const createSavedViewMutation = useMutation({
    mutationFn: () => createSavedView(space.id, {
      name: savedViewName,
      description: savedViewDescription,
      scope: 'personal',
      query: { ...definition, cursor: null },
      presentation: currentPresentation(),
    }),
    onSuccess: async (view) => {
      await refreshSavedViews()
      applySavedView(view)
      setSaveViewOpen(false)
      message.success('个人视图已保存')
    },
    onError: (error) => message.error(errorMessage(error, '保存视图失败')),
  })
  const updateSavedViewMutation = useMutation({
    mutationFn: () => {
      if (!selectedSavedView) throw new Error('未选择保存视图')
      return updateSavedView(space.id, selectedSavedView.id, {
        expectedVersion: selectedSavedView.aggregateVersion,
        name: savedViewName || selectedSavedView.name,
        description: savedViewDescription,
        scope: selectedSavedView.scope,
        query: { ...definition, cursor: null },
        presentation: currentPresentation(),
      })
    },
    onSuccess: async (view) => {
      await refreshSavedViews()
      applySavedView(view)
      setSaveViewOpen(false)
      message.success('保存视图已更新')
    },
    onError: (error) => message.error(errorMessage(error, '视图已变化，请刷新后重试')),
  })
  const executeSavedViewMutation = useMutation({
    mutationFn: (viewId: string) => executeSavedView(space.id, viewId),
    onSuccess: ({ view }) => applySavedView(view),
    onError: (error) => {
      setSelectedSavedViewId(undefined)
      message.error(errorMessage(error, '保存视图不可用或需要重新授权'))
    },
  })
  const savedViewCommandMutation = useMutation({
    mutationFn: async (command: 'copy' | 'favorite' | 'delete' | 'share' | 'revoke' | 'transfer') => {
      if (!selectedSavedView) throw new Error('未选择保存视图')
      if (command === 'copy') {
        return copySavedView(space.id, selectedSavedView.id, `${selectedSavedView.name} - 副本`)
      }
      if (command === 'favorite') {
        await favoriteSavedView(selectedSavedView.id)
        return selectedSavedView
      }
      if (command === 'delete') {
        return deleteSavedView(space.id, selectedSavedView.id, selectedSavedView.aggregateVersion)
      }
      if (!shareUserId) throw new Error('请选择空间成员')
      if (command === 'share') {
        return shareSavedView(
          space.id,
          selectedSavedView.id,
          selectedSavedView.aggregateVersion,
          shareUserId,
          sharePermission,
        )
      }
      if (command === 'revoke') {
        return revokeSavedView(
          space.id,
          selectedSavedView.id,
          selectedSavedView.aggregateVersion,
          shareUserId,
        )
      }
      return transferSavedView(
        space.id,
        selectedSavedView.id,
        selectedSavedView.aggregateVersion,
        shareUserId,
      )
    },
    onSuccess: async (view, command) => {
      await refreshSavedViews()
      if (command === 'delete' || command === 'transfer') {
        setSelectedSavedViewId(undefined)
        setSavedDefinitionOverride(undefined)
      } else {
        applySavedView(view)
      }
      setShareOpen(false)
      setShareUserId(undefined)
      message.success({
        copy: '视图副本已创建',
        favorite: '已加入收藏',
        delete: '保存视图已删除',
        share: '分享权限已更新',
        revoke: '分享已撤销',
        transfer: '视图所有权已移交',
      }[command])
    },
    onError: (error) => message.error(errorMessage(error, '保存视图操作失败，请刷新后重试')),
  })

  const openCreate = (typeId?: string) => {
    const nextType = typeId ?? selectedTypeId ?? typesQuery.data?.[0]?.id
    setSelectedTypeOverride(nextType)
    setCreateOpen(true)
    setSearchParams(nextType ? { typeId: nextType, create: '1' } : { create: '1' }, { replace: true })
  }

  return (
    <section className="project-work-items" data-testid="project-work-items">
      <Card
        className="content-card"
        title={<Space><FileOutlined />工作项</Space>}
        extra={space.status === 'active' && space.currentUserRole !== 'guest'
          ? <Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate()}>新建工作项</Button>
          : null}
      >
        <div className="project-work-item-filter">
          <Select
            aria-label="保存视图目录"
            allowClear
            showSearch
            loading={savedViewsQuery.isLoading || executeSavedViewMutation.isPending}
            value={selectedSavedViewId}
            placeholder="个人与共享视图"
            optionFilterProp="label"
            options={savedViewsQuery.data?.map((view) => ({
              label: `${view.name}${view.scope === 'shared' ? ' · 共享' : ' · 个人'}`,
              value: view.id,
            }))}
            onChange={(viewId) => {
              setSelectedSavedViewId(viewId)
              if (viewId) {
                executeSavedViewMutation.mutate(viewId)
              } else {
                setSavedDefinitionOverride(undefined)
                setSearchParams({}, { replace: true })
              }
            }}
          />
          <Select
            aria-label="按工作项类型筛选"
            allowClear
            value={selectedTypeId}
            placeholder="全部类型"
            loading={typesQuery.isLoading}
            options={typesQuery.data?.map((type) => ({ label: type.name, value: type.id }))}
            onChange={(value) => {
              setSelectedTypeOverride(value)
              setBoardColumnsOverride(undefined)
              setBoardSwimlaneOverride(undefined)
              setDraggedBoardCard(undefined)
            }}
          />
          <Segmented
            aria-label="工作项视图模式"
            value={ganttMode
              ? 'gantt'
              : calendarMode
                ? 'calendar'
                : boardMode
                  ? 'board'
                  : treeMode
                    ? 'tree'
                    : mode}
            options={[
              { label: '表格', value: 'table', icon: <TableOutlined /> },
              { label: '紧凑列表', value: 'list', icon: <UnorderedListOutlined /> },
              { label: '层级树', value: 'tree', icon: <ApartmentOutlined /> },
              { label: '看板', value: 'board', icon: <AppstoreOutlined /> },
              { label: '日历', value: 'calendar', icon: <CalendarOutlined /> },
              { label: '甘特', value: 'gantt', icon: <BarChartOutlined /> },
            ]}
            onChange={(value) => {
              setBoardMode(value === 'board')
              setCalendarMode(value === 'calendar')
              setGanttMode(value === 'gantt')
              setTreeMode(value === 'tree')
              if (value === 'table' || value === 'list') setModeOverride(value)
            }}
          />
          {boardMode ? (
            <Select
              aria-label="看板泳道"
              value={boardSwimlaneField ?? 'none'}
              options={[
                { label: '不使用泳道', value: 'none' },
                { label: '按我的参与角色', value: 'participantRole' },
              ]}
              onChange={(value) => setBoardSwimlaneOverride(
                value === 'none' ? null : value,
              )}
            />
          ) : null}
          {calendarMode ? (
            <>
              <Input
                aria-label="日历开始日期字段"
                value={calendarBinding.startField}
                placeholder="开始日期字段"
                onChange={(event) => setCalendarBindingOverride({
                  ...calendarBinding,
                  startField: event.target.value,
                })}
              />
              <Input
                aria-label="日历结束日期字段"
                value={calendarBinding.endField ?? ''}
                placeholder="结束日期字段（可选）"
                onChange={(event) => setCalendarBindingOverride({
                  ...calendarBinding,
                  endField: event.target.value || null,
                })}
              />
              <Select
                aria-label="日历粒度"
                value={calendarViewMode}
                options={[
                  { label: '月', value: 'month' },
                  { label: '周', value: 'week' },
                  { label: '日', value: 'day' },
                ]}
                onChange={setCalendarViewModeOverride}
              />
              <Input
                aria-label="日历时区"
                value={calendarTimezone}
                placeholder="IANA 时区"
                onChange={(event) => setCalendarTimezoneOverride(event.target.value)}
              />
              <Input
                aria-label="日历锚点日期"
                type="date"
                value={calendarAnchorDate}
                onChange={(event) => setCalendarAnchorDate(event.target.value)}
              />
              <Button
                aria-label="上一日历窗口"
                onClick={() => setCalendarAnchorDate(shiftCalendarAnchor(
                  calendarAnchorDate,
                  calendarViewMode,
                  -1,
                ))}
              >
                上一段
              </Button>
              <Button
                aria-label="下一日历窗口"
                onClick={() => setCalendarAnchorDate(shiftCalendarAnchor(
                  calendarAnchorDate,
                  calendarViewMode,
                  1,
                ))}
              >
                下一段
              </Button>
            </>
          ) : null}
          {ganttMode ? (
            <>
              <Input
                aria-label="甘特开始日期字段"
                value={calendarBinding.startField}
                placeholder="开始日期字段"
                onChange={(event) => setCalendarBindingOverride({
                  ...calendarBinding,
                  startField: event.target.value,
                })}
              />
              <Input
                aria-label="甘特结束日期字段"
                value={calendarBinding.endField ?? ''}
                placeholder="结束日期字段（可选）"
                onChange={(event) => setCalendarBindingOverride({
                  ...calendarBinding,
                  endField: event.target.value || null,
                })}
              />
              <Select
                aria-label="甘特缩放"
                value={ganttZoom}
                options={[
                  { label: '日', value: 'day' },
                  { label: '周', value: 'week' },
                  { label: '月', value: 'month' },
                ]}
                onChange={setGanttZoomOverride}
              />
              <Input
                aria-label="甘特时区"
                value={calendarTimezone}
                onChange={(event) => setCalendarTimezoneOverride(event.target.value)}
              />
              <Input
                aria-label="甘特锚点日期"
                type="date"
                value={calendarAnchorDate}
                onChange={(event) => setCalendarAnchorDate(event.target.value)}
              />
            </>
          ) : null}
          <Select
            aria-label="列表密度"
            value={density}
            options={[
              { label: '舒适', value: 'comfortable' },
              { label: '紧凑', value: 'compact' },
            ]}
            onChange={setDensityOverride}
          />
          <Select
            aria-label="显示列"
            mode="multiple"
            value={activeColumnKeys}
            maxTagCount="responsive"
            options={availableColumns.map((column) => ({ label: column.label, value: column.key }))}
            onChange={(value) => setColumnKeysOverride(value.length ? value : ['title'])}
          />
          <Button
            icon={<SaveOutlined />}
            loading={savePreferenceMutation.isPending}
            onClick={() => savePreferenceMutation.mutate()}
          >
            保存偏好
          </Button>
          <Button
            type="primary"
            onClick={() => {
              setSavedViewName(selectedSavedView?.name ?? '')
              setSavedViewDescription(selectedSavedView?.description ?? '')
              setSaveViewOpen(true)
            }}
          >
            {selectedSavedView?.canManage ? '编辑保存视图' : '另存为视图'}
          </Button>
          <Typography.Text type="secondary">列表只展示当前空间内你有权访问的工作项。</Typography.Text>
        </div>
        {selectedSavedView ? (
          <Alert
            className="project-saved-view-access"
            type="info"
            showIcon
            message={<Space wrap>
              <strong>{selectedSavedView.name}</strong>
              <Tag color={selectedSavedView.canManage ? 'blue' : 'default'}>
                {selectedSavedView.canManage ? '可管理' : '仅可使用'}
              </Tag>
              <Typography.Text type="secondary">
                执行时会重新校准工作项与字段权限，分享不会扩大内容访问范围。
              </Typography.Text>
            </Space>}
            action={<Space wrap>
              <Button size="small" onClick={() => savedViewCommandMutation.mutate('favorite')}>收藏</Button>
              <Button size="small" onClick={() => savedViewCommandMutation.mutate('copy')}>复制</Button>
              {selectedSavedView.canManage ? (
                <Button size="small" onClick={() => setShareOpen(true)}>分享与移交</Button>
              ) : null}
              {selectedSavedView.canManage ? (
                <Button
                  size="small"
                  danger
                  onClick={() => Modal.confirm({
                    title: '删除保存视图？',
                    content: '删除后收藏和旧链接不会再显示名称或结果。',
                    okButtonProps: { danger: true },
                    onOk: async () => { await savedViewCommandMutation.mutateAsync('delete') },
                  })}
                >
                  删除
                </Button>
              ) : null}
            </Space>}
          />
        ) : null}
        <div className="project-work-item-actions">
          <Space wrap>
            <Button
              disabled={!selectedRowKeys.length}
              loading={bulkMutation.isPending}
              onClick={() => bulkMutation.mutate('archive')}
            >
              批量归档
            </Button>
            <Button
              disabled={!selectedRowKeys.length}
              loading={bulkMutation.isPending}
              onClick={() => bulkMutation.mutate('restore')}
            >
              批量恢复
            </Button>
            <Button
              icon={<DownloadOutlined />}
              loading={exportMutation.isPending}
              onClick={() => exportMutation.mutate()}
            >
              导出 CSV
            </Button>
            {selectedRowKeys.length ? <Tag color="blue">已选择 {selectedRowKeys.length} 项</Tag> : null}
          </Space>
        </div>
        {(ganttMode
          ? ganttQuery.isLoading
          : calendarMode
            ? calendarQuery.isLoading
          : boardMode
            ? boardQuery.isLoading
          : treeMode
            ? treeQuery.isLoading
            : itemsQuery.isLoading)
          ? <Skeleton active paragraph={{ rows: 5 }} />
          : null}
        {!treeMode && !boardMode && !calendarMode && !ganttMode && itemsQuery.isError ? (
          <Alert
            type="error"
            showIcon
            message="工作项加载失败"
            description={errorMessage(itemsQuery.error, '请稍后重试')}
            action={<Button size="small" onClick={() => itemsQuery.refetch()}>重试</Button>}
          />
        ) : null}
        {!treeMode && !boardMode && !calendarMode && !ganttMode && !itemsQuery.isLoading
          && !itemsQuery.isError && itemsQuery.data?.rows.length === 0 ? (
          <Empty description="当前类型还没有工作项">
            {space.status === 'active' && space.currentUserRole !== 'guest'
              ? <Button type="primary" onClick={() => openCreate()}>创建第一条工作项</Button>
              : null}
          </Empty>
        ) : null}
        {!treeMode && !boardMode && !calendarMode && !ganttMode && itemsQuery.data?.rows.length && mode === 'table' ? (
          <Table
            size={density === 'compact' ? 'small' : 'middle'}
            rowKey="workItemId"
            dataSource={itemsQuery.data.rows}
            pagination={false}
            scroll={{ x: 760 }}
            rowSelection={{
              selectedRowKeys,
              onChange: setSelectedRowKeys,
              getCheckboxProps: (row) => ({ disabled: !row.availableActions.length }),
            }}
            onRow={(item) => ({
              tabIndex: 0,
              onClick: () => navigate(`/project-spaces/${space.id}/work-items/${item.workItemId}`),
              onKeyDown: (event) => {
                if (event.key === 'Enter') {
                  navigate(`/project-spaces/${space.id}/work-items/${item.workItemId}`)
                }
              },
            })}
            columns={columns.map((column) => ({
              title: column.label,
              key: column.key,
              width: column.width,
              fixed: column.frozen ? 'left' as const : undefined,
              render: (_: unknown, row: WorkItemViewRow) => {
                const cell = row.cells.find((candidate) => candidate.columnKey === column.key)
                if (!cell) return <Typography.Text type="secondary">—</Typography.Text>
                if (column.format === 'tag') return <Tag>{cell.displayValue || '—'}</Tag>
                if (column.format === 'datetime') return formatTime(cell.displayValue)
                return column.key === 'title'
                  ? <strong>{cell.displayValue}</strong>
                  : cell.displayValue
              },
            }))}
          />
        ) : null}
        {!treeMode && !boardMode && !calendarMode && !ganttMode && itemsQuery.data?.rows.length && mode === 'list' ? (
          <List
            className={`project-work-item-compact-list is-${density}`}
            dataSource={itemsQuery.data.rows}
            renderItem={(row) => (
              <List.Item
                tabIndex={0}
                onClick={() => navigate(`/project-spaces/${space.id}/work-items/${row.workItemId}`)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    navigate(`/project-spaces/${space.id}/work-items/${row.workItemId}`)
                  }
                }}
              >
                <List.Item.Meta
                  avatar={<Avatar>{row.displayKey.slice(0, 1)}</Avatar>}
                  title={<Space wrap><strong>{row.title}</strong><Tag>{row.displayKey}</Tag></Space>}
                  description={row.cells
                    .filter((cell) => !['title', 'displayKey'].includes(cell.columnKey))
                    .map((cell) => cell.displayValue)
                    .filter(Boolean)
                    .join(' · ')}
                />
              </List.Item>
            )}
          />
        ) : null}
        {ganttMode && ganttQuery.isError ? (
          <Alert
            type="error"
            showIcon
            message="甘特加载失败"
            description={errorMessage(
              ganttQuery.error,
              '请检查日期 capability、层级与依赖；隐藏端点不会降级为占位线。',
            )}
            action={<Button size="small" onClick={() => ganttQuery.refetch()}>重试</Button>}
          />
        ) : null}
        {ganttMode && ganttQuery.data ? (
          <div className="project-work-item-gantt-shell" data-testid="project-work-item-gantt">
            <Space wrap>
              <Tag color="blue">可见排期 {ganttQuery.data.rows.length}</Tag>
              <Tag>依赖线 {ganttQuery.data.dependencies.length}</Tag>
              <Tag color={ganttQuery.data.criticalPathAvailable ? 'red' : 'default'}>
                {ganttQuery.data.criticalPathAvailable
                  ? '关键路径已派生'
                  : `关键路径降级：${ganttQuery.data.criticalPathReason}`}
              </Tag>
              {ganttQuery.data.truncated ? <Tag color="orange">结果按预算截断</Tag> : null}
              <Typography.Text type="secondary">
                排期与依赖均由当前权限重新投影；← → 移动一天。
              </Typography.Text>
            </Space>
            <section className="project-work-item-schedule-history" data-testid="work-item-schedule-history">
              <Typography.Title level={5}>排期基线与最小时间线</Typography.Title>
              <Space wrap>
                <Input
                  aria-label="基线名称"
                  value={baselineName}
                  maxLength={120}
                  placeholder="例如：发布前排期"
                  onChange={(event) => setBaselineName(event.target.value)}
                />
                <Button
                  type="primary"
                  loading={createBaselineMutation.isPending}
                  disabled={!baselineName.trim()}
                  onClick={() => createBaselineMutation.mutate()}
                >
                  创建基线
                </Button>
                <Select
                  aria-label="排期基线"
                  value={selectedBaselineId}
                  placeholder="选择基线"
                  loading={baselinesQuery.isLoading}
                  options={(baselinesQuery.data ?? []).map((baseline) => ({
                    label: baseline.name,
                    value: baseline.id,
                  }))}
                  onChange={(value) => {
                    setSelectedBaselineId(value)
                    setBaselineDiff(undefined)
                  }}
                />
                <Button
                  disabled={!selectedBaselineId}
                  loading={compareBaselineMutation.isPending}
                  onClick={() => selectedBaselineId
                    && compareBaselineMutation.mutate(selectedBaselineId)}
                >
                  比较当前排期
                </Button>
                <Button
                  danger
                  disabled={!selectedBaselineId}
                  loading={deleteBaselineMutation.isPending}
                  onClick={() => selectedBaselineId
                    && deleteBaselineMutation.mutate(selectedBaselineId)}
                >
                  删除基线
                </Button>
              </Space>
              <Typography.Paragraph type="secondary">
                基线只冻结 identity、日期、父级与依赖版本，保留 90 天；标题和权限始终按当前请求重新校准。
              </Typography.Paragraph>
              {baselineDiff ? (
                <Space wrap aria-label="基线差异摘要">
                  <Tag color="blue">事项变化 {baselineDiff.entries.length}</Tag>
                  <Tag>新增依赖 {baselineDiff.addedDependencies}</Tag>
                  <Tag>移除依赖 {baselineDiff.removedDependencies}</Tag>
                  {baselineDiff.truncated ? <Tag color="orange">结果已截断</Tag> : null}
                </Space>
              ) : null}
              {timelineQuery.isError ? (
                <Alert
                  type="warning"
                  showIcon
                  message="时间线加载失败"
                  description={errorMessage(timelineQuery.error, '请稍后重试')}
                />
              ) : (
                <List
                  size="small"
                  aria-label="排期时间线"
                  loading={timelineQuery.isLoading}
                  locale={{ emptyText: '当前受权事项暂无时间线事件' }}
                  dataSource={timelineQuery.data?.events ?? []}
                  renderItem={(event) => (
                    <List.Item>
                      <Space wrap>
                        <Tag>{event.sourceKind}</Tag>
                        <strong>{event.eventType}</strong>
                        <Typography.Text type="secondary">
                          {formatTime(event.occurredAt)}
                        </Typography.Text>
                      </Space>
                    </List.Item>
                  )}
                />
              )}
            </section>
            <div className={`project-work-item-gantt is-${ganttZoom}`}>
              <header className="project-work-item-gantt-header">
                <strong>工作项 / 层级</strong>
                <span>
                  {ganttQuery.data.window.startDate} — {ganttQuery.data.window.endDate}
                </span>
              </header>
              {ganttQuery.data.rows.map((row) => (
                <div
                  className="project-work-item-gantt-row"
                  key={row.workItemId}
                  data-testid={`gantt-row-${row.workItemId}`}
                >
                  <div
                    className="project-work-item-gantt-label"
                    style={{ paddingInlineStart: 10 + row.depth * 18 }}
                  >
                    {row.expandable ? (
                      <Button
                        size="small"
                        type="text"
                        aria-label={`${row.expanded ? '折叠' : '展开'} ${row.bar.title}`}
                        onClick={() => setGanttExpandedOverride(row.expanded
                          ? ganttExpanded.filter((id) => id !== row.workItemId)
                          : [...ganttExpanded, row.workItemId])}
                      >
                        {row.expanded ? '−' : '+'}
                      </Button>
                    ) : <span className="project-work-item-gantt-indent" />}
                    <button
                      type="button"
                      onClick={() => navigate(
                        `/project-spaces/${space.id}/work-items/${row.workItemId}`,
                      )}
                    >
                      <Tag>{row.bar.displayKey}</Tag>
                      <strong title={row.bar.title}>{row.bar.title}</strong>
                    </button>
                  </div>
                  <div className="project-work-item-gantt-track">
                    {row.bar.startDate ? (
                      <div
                        className={`project-work-item-gantt-bar${row.bar.critical ? ' is-critical' : ''}`}
                        style={ganttBarStyle(
                          row.bar.startDate,
                          row.bar.endDate ?? row.bar.startDate,
                          ganttQuery.data.window.startDate,
                          ganttQuery.data.window.endDate,
                        )}
                        tabIndex={0}
                        aria-label={`${row.bar.displayKey} ${row.bar.title} 排期`}
                        onKeyDown={(event) => {
                          if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
                            event.preventDefault()
                            shiftGanttBar(row.bar, event.key === 'ArrowLeft' ? -1 : 1)
                          }
                        }}
                      >
                        <span>{row.bar.startDate} → {row.bar.endDate ?? row.bar.startDate}</span>
                        {calendarBinding.endField ? (
                          <Input
                            aria-label={`调整 ${row.bar.title} 甘特结束日期`}
                            type="date"
                            size="small"
                            value={row.bar.endDate ?? row.bar.startDate}
                            onChange={(event) => ganttDateMutation.mutate({
                              bar: row.bar,
                              startValue: row.bar.startDate,
                              endValue: event.target.value,
                              operation: 'resize',
                            })}
                          />
                        ) : null}
                      </div>
                    ) : (
                      <span className="project-work-item-gantt-unscheduled">未安排日期</span>
                    )}
                  </div>
                </div>
              ))}
            </div>
            <section className="project-work-item-gantt-dependencies">
              <Typography.Title level={5}>受权依赖线</Typography.Title>
              {ganttQuery.data.dependencies.map((line) => (
                <Tag color={line.critical ? 'red' : 'blue'} key={line.relationId}>
                  {line.sourceWorkItemId.slice(0, 8)} ← {line.targetWorkItemId.slice(0, 8)}
                </Tag>
              ))}
              {!ganttQuery.data.dependencies.length
                ? <Typography.Text type="secondary">当前窗口没有双端可见依赖</Typography.Text>
                : null}
            </section>
          </div>
        ) : null}
        {calendarMode && calendarQuery.isError ? (
          <Alert
            type="error"
            showIcon
            message="日历加载失败"
            description={errorMessage(
              calendarQuery.error,
              '请检查已发布日期字段、时区与窗口；日历不会回退读取私有投影。',
            )}
            action={<Button size="small" onClick={() => calendarQuery.refetch()}>重试</Button>}
          />
        ) : null}
        {calendarMode && calendarQuery.data ? (
          <div className="project-work-item-calendar-shell" data-testid="project-work-item-calendar">
            <Space wrap>
              <Tag color="blue">可见日程 {calendarQuery.data.visibleEventCount}</Tag>
              <Tag>{calendarQuery.data.window.startDate} 至 {calendarQuery.data.window.endDate}</Tag>
              <Typography.Text type="secondary">
                全天日程可拖放或用 ← → 移动；日期修改会在服务端重新鉴权。
              </Typography.Text>
            </Space>
            <div
              className={`project-work-item-calendar is-${calendarViewMode}`}
              style={{
                '--calendar-columns': calendarViewMode === 'month'
                  ? 7
                  : calendarQuery.data.days.length,
              } as CSSProperties}
            >
              {calendarQuery.data.days.map((day) => (
                <section
                  className="project-work-item-calendar-day"
                  key={day.date}
                  data-testid={`calendar-day-${day.date}`}
                  onDragOver={(event) => event.preventDefault()}
                  onDrop={(event) => {
                    event.preventDefault()
                    if (draggedCalendarEvent) moveCalendarEvent(draggedCalendarEvent, day.date)
                  }}
                >
                  <header>
                    <strong>{day.date.slice(5)}</strong>
                    <Tag>{day.events.length}</Tag>
                  </header>
                  <div className="project-work-item-calendar-events">
                    {day.events.map((item) => (
                      <article
                        className={`project-work-item-calendar-event lane-${item.overlapLane}`}
                        key={`${day.date}-${item.workItemId}`}
                        tabIndex={0}
                        draggable={item.allDay && !calendarDateMutation.isPending}
                        aria-label={`${item.displayKey} ${item.title}`}
                        onDragStart={() => setDraggedCalendarEvent(item)}
                        onDragEnd={() => setDraggedCalendarEvent(undefined)}
                        onKeyDown={(event) => keyboardCalendarMove(event, item)}
                      >
                        <button
                          type="button"
                          onClick={() => navigate(
                            `/project-spaces/${space.id}/work-items/${item.workItemId}`,
                          )}
                        >
                          <Tag>{item.displayKey}</Tag>
                          <strong title={item.title}>{item.title}</strong>
                        </button>
                        {calendarBinding.endField && item.allDay ? (
                          <Input
                            aria-label={`调整 ${item.title} 结束日期`}
                            type="date"
                            size="small"
                            value={item.displayEndDate ?? item.displayStartDate ?? ''}
                            onChange={(event) => calendarDateMutation.mutate({
                              event: item,
                              startValue: item.displayStartDate,
                              endValue: event.target.value,
                              operation: 'resize',
                            })}
                          />
                        ) : null}
                      </article>
                    ))}
                    {!day.events.length
                      ? <div className="project-work-item-calendar-empty">拖放到此日期</div>
                      : null}
                  </div>
                </section>
              ))}
            </div>
            <section className="project-work-item-calendar-no-date">
              <Typography.Title level={5}>未安排日期</Typography.Title>
              <div>
                {calendarQuery.data.noDateEvents.map((item) => (
                  <article className="project-work-item-calendar-event" key={item.workItemId}>
                    <button
                      type="button"
                      onClick={() => navigate(
                        `/project-spaces/${space.id}/work-items/${item.workItemId}`,
                      )}
                    >
                      <Tag>{item.displayKey}</Tag>
                      <strong title={item.title}>{item.title}</strong>
                    </button>
                    <Input
                      aria-label={`安排 ${item.title} 日期`}
                      type="date"
                      size="small"
                      onChange={(event) => calendarDateMutation.mutate({
                        event: item,
                        startValue: event.target.value,
                        endValue: calendarBinding.endField ? event.target.value : null,
                        operation: 'move',
                      })}
                    />
                  </article>
                ))}
                {!calendarQuery.data.noDateEvents.length
                  ? <Typography.Text type="secondary">没有未安排日期的可见工作项</Typography.Text>
                  : null}
              </div>
            </section>
          </div>
        ) : null}
        {boardMode && !calendarMode && !ganttMode && boardQuery.isError ? (
          <Alert
            type="error"
            showIcon
            message="看板加载失败"
            description={errorMessage(
              boardQuery.error,
              '看板只使用当前查询与权限结果；请检查列配置或刷新后重试。',
            )}
            action={<Button size="small" onClick={() => boardQuery.refetch()}>重试</Button>}
          />
        ) : null}
        {boardMode && !calendarMode && !ganttMode && boardQuery.data ? (
          <div className="project-work-item-board-shell" data-testid="project-work-item-board">
            <Space wrap>
              <Tag color="blue">可见卡片 {
                boardQuery.data.columns.reduce((total, column) => total + column.visibleCount, 0)
              }</Tag>
              <Tag>候选评估 {boardQuery.data.evaluatedCandidates}</Tag>
              {boardQuery.data.candidateBoundReached
                ? <Tag color="orange">候选预算已截断</Tag>
                : null}
              <Typography.Text type="secondary">
                拖放或使用方向键移动；流程动作会在服务端重新鉴权。
              </Typography.Text>
            </Space>
            <div className="project-work-item-board">
              {boardQuery.data.columns.map((column, columnIndex) => (
                <section
                  className={`project-work-item-board-column${column.wipExceeded ? ' is-wip-exceeded' : ''}`}
                  key={column.column.key}
                  data-testid={`board-column-${column.column.key}`}
                >
                  <header>
                    <Space wrap>
                      <strong>{column.column.label}</strong>
                      <Tag color={column.wipExceeded ? 'red' : 'default'}>
                        {column.visibleCount}
                        {column.column.wipLimit > 0 ? ` / ${column.column.wipLimit}` : ''}
                      </Tag>
                    </Space>
                    <Input
                      aria-label={`${column.column.label} WIP 上限`}
                      className="project-work-item-board-wip"
                      type="number"
                      min={0}
                      max={100}
                      value={column.column.wipLimit}
                      onChange={(event) => {
                        const value = Math.max(0, Math.min(100, Number(event.target.value) || 0))
                        setBoardColumnsOverride(boardColumns.map((candidate) =>
                          candidate.key === column.column.key
                            ? { ...candidate, wipLimit: value }
                            : candidate))
                      }}
                    />
                  </header>
                  {column.lanes.map((lane) => (
                    <div
                      className="project-work-item-board-lane"
                      key={lane.key}
                      data-testid={`board-lane-${column.column.key}-${lane.key}`}
                      onDragOver={(event) => event.preventDefault()}
                      onDrop={(event) => {
                        event.preventDefault()
                        if (draggedBoardCard) {
                          moveBoardCard(draggedBoardCard, column, lane.key)
                        }
                      }}
                    >
                      {boardQuery.data.swimlaneField ? (
                        <Typography.Text className="project-work-item-board-lane-label" type="secondary">
                          {lane.label}
                        </Typography.Text>
                      ) : null}
                      {lane.cards.map((card) => (
                        <article
                          className="project-work-item-board-card"
                          key={card.workItemId}
                          tabIndex={0}
                          draggable={!boardMoveMutation.isPending}
                          aria-label={`${card.displayKey} ${card.title}`}
                          onDragStart={() => setDraggedBoardCard(card)}
                          onDragEnd={() => setDraggedBoardCard(undefined)}
                          onClick={() => navigate(
                            `/project-spaces/${space.id}/work-items/${card.workItemId}`,
                          )}
                          onKeyDown={(event) => keyboardBoardMove(
                            event,
                            card,
                            columnIndex,
                            lane,
                          )}
                        >
                          <Space wrap size="small">
                            <Tag>{card.displayKey}</Tag>
                            <Tag color="blue">{card.moveActions.length} 个可用动作</Tag>
                          </Space>
                          <strong title={card.title}>{card.title}</strong>
                          <Typography.Text type="secondary">
                            ↑↓ 排序 · ←→ 跨列
                          </Typography.Text>
                        </article>
                      ))}
                      {!lane.cards.length ? (
                        <div className="project-work-item-board-empty">拖放到此列</div>
                      ) : null}
                    </div>
                  ))}
                </section>
              ))}
            </div>
          </div>
        ) : null}
        {treeMode && !boardMode && !calendarMode && !ganttMode && treeQuery.isError ? (
          <Alert
            type="error"
            showIcon
            message="层级树加载失败"
            description="树不会回退为私表读取或猜测隐藏路径；请重试 REST 校准。"
            action={<Button size="small" onClick={() => treeQuery.refetch()}>重试</Button>}
          />
        ) : null}
        {treeMode && !boardMode && !calendarMode && !ganttMode && treeQuery.data ? (
          <div className="project-work-item-tree" data-testid="project-work-item-tree">
            <Space wrap>
              <Tag color="blue">可见节点 {treeQuery.data.aggregate.visibleNodeCount}</Tag>
              <Tag>匹配 {treeQuery.data.aggregate.matchedCount}</Tag>
              <Tag>最大可见深度 {treeQuery.data.aggregate.maxVisibleDepth}</Tag>
              {treeQuery.data.truncated ? <Tag color="orange">结果已按预算截断</Tag> : null}
            </Space>
            <Tree
              blockNode
              checkable
              selectable
              showLine
              treeData={treeData}
              expandedKeys={effectiveExpandedTreeKeys}
              checkedKeys={selectedRowKeys}
              onExpand={setExpandedTreeKeys}
              onCheck={(keys) => setSelectedRowKeys(Array.isArray(keys) ? keys : keys.checked)}
              onSelect={(keys) => {
                if (keys[0]) navigate(`/project-spaces/${space.id}/work-items/${keys[0]}`)
              }}
              loadData={async (node) => {
                const id = String(node.key)
                if (loadedTreeChildren[id]) return
                const result = await renderWorkItemTree(space.id, definition, id)
                setLoadedTreeState((current) => ({
                  queryHash: treeQuery.data?.queryHash ?? result.queryHash,
                  children: {
                    ...(current?.queryHash === treeQuery.data?.queryHash ? current.children : {}),
                    [id]: result.items,
                  },
                }))
              }}
            />
            {!treeData.length ? <Empty description="当前查询没有可见层级节点" /> : null}
          </div>
        ) : null}
      </Card>
      <Modal
        title={selectedSavedView?.canManage ? '编辑保存视图' : '另存为个人视图'}
        open={saveViewOpen}
        okText={selectedSavedView?.canManage ? '保存新版本' : '创建视图'}
        confirmLoading={createSavedViewMutation.isPending || updateSavedViewMutation.isPending}
        okButtonProps={{ disabled: !savedViewName.trim() }}
        onCancel={() => setSaveViewOpen(false)}
        onOk={() => selectedSavedView?.canManage
          ? updateSavedViewMutation.mutate()
          : createSavedViewMutation.mutate()}
      >
        <Form layout="vertical">
          <Form.Item label="视图名称" required>
            <Input
              autoFocus
              maxLength={120}
              value={savedViewName}
              onChange={(event) => setSavedViewName(event.target.value)}
            />
          </Form.Item>
          <Form.Item label="说明">
            <Input.TextArea
              maxLength={500}
              value={savedViewDescription}
              onChange={(event) => setSavedViewDescription(event.target.value)}
            />
          </Form.Item>
          <Alert
            type="info"
            showIcon
            message="保存查询与展示配置，不复制工作项内容；每次执行都会重新鉴权。"
          />
        </Form>
      </Modal>
      <Modal
        title="分享、撤销与移交"
        open={shareOpen}
        footer={null}
        onCancel={() => setShareOpen(false)}
      >
        <Form layout="vertical">
          <Form.Item label="空间成员" required>
            <Select
              showSearch
              optionFilterProp="label"
              loading={membersQuery.isLoading}
              value={shareUserId}
              options={membersQuery.data
                ?.filter((member) => member.effective && member.userId !== selectedSavedView?.ownerUserId)
                .map((member) => ({
                  label: `${member.displayName} · ${member.roleKey}`,
                  value: member.userId,
                }))}
              onChange={setShareUserId}
            />
          </Form.Item>
          <Form.Item label="权限">
            <Select
              value={sharePermission}
              options={[
                { label: '仅使用（不能修改或再次分享）', value: 'use' },
                { label: '可管理（可编辑、分享与删除）', value: 'manage' },
              ]}
              onChange={setSharePermission}
            />
          </Form.Item>
          <Space wrap>
            <Button
              type="primary"
              disabled={!shareUserId}
              loading={savedViewCommandMutation.isPending}
              onClick={() => savedViewCommandMutation.mutate('share')}
            >
              分享或更新权限
            </Button>
            <Button
              disabled={!shareUserId}
              loading={savedViewCommandMutation.isPending}
              onClick={() => savedViewCommandMutation.mutate('revoke')}
            >
              撤销分享
            </Button>
            {selectedSavedView?.ownerUserId ? (
              <Button
                danger
                disabled={!shareUserId}
                loading={savedViewCommandMutation.isPending}
                onClick={() => Modal.confirm({
                  title: '移交视图所有权？',
                  content: '移交后当前用户不再自动拥有管理权限。',
                  onOk: async () => { await savedViewCommandMutation.mutateAsync('transfer') },
                })}
              >
                移交所有权
              </Button>
            ) : null}
          </Space>
          {selectedSavedView?.shares.filter((share) => share.status === 'active').length ? (
            <List
              header="当前分享"
              dataSource={selectedSavedView.shares.filter((share) => share.status === 'active')}
              renderItem={(share) => (
                <List.Item>
                  <Typography.Text code>{share.subjectUserId}</Typography.Text>
                  <Tag>{share.permission === 'manage' ? '可管理' : '仅使用'}</Tag>
                </List.Item>
              )}
            />
          ) : null}
        </Form>
      </Modal>
      <CreateWorkItemModal
        key={selectedTypeId ?? 'no-type'}
        space={space}
        typeId={selectedTypeId}
        open={createOpen}
        onCancel={() => {
          setCreateOpen(false)
          setSearchParams(selectedTypeId ? { typeId: selectedTypeId } : {}, { replace: true })
        }}
      />
    </section>
  )
}

function CreateWorkItemModal({
  space,
  typeId,
  open,
  onCancel,
}: {
  space: UserProjectSpace
  typeId?: string
  open: boolean
  onCancel: () => void
}) {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const online = useOnlineStatus()
  const [title, setTitle] = useState('')
  const [draftValues, setDraftValues] = useState<{
    runtimeKey: string
    values: WorkItemLayoutValues
  } | null>(null)
  const formQuery = useQuery({
    queryKey: workItemKeys.createForm(space.id, typeId ?? ''),
    queryFn: () => getWorkItemCreateForm(space.id, typeId as string),
    enabled: open && Boolean(typeId),
    retry: false,
  })

  const runtime = formQuery.data?.runtime
  const runtimeKey = runtime ? `${runtime.typeVersionId}:${runtime.configHash}` : ''
  const values = draftValues?.runtimeKey === runtimeKey
    ? draftValues.values
    : runtime
      ? defaultValues(runtime)
      : {}
  const setValues = (next: WorkItemLayoutValues) => setDraftValues({ runtimeKey, values: next })
  const mutation = useMutation({
    mutationFn: () => {
      if (!online || !navigator.onLine) {
        throw new Error('当前处于离线状态，输入已保留，请联网后重试')
      }
      return createWorkItem(space.id, {
        typeId: typeId as string,
        title: title.trim(),
        fieldValues: values,
      })
    },
    onSuccess: async (item) => {
      await queryClient.invalidateQueries({ queryKey: workItemKeys.all })
      message.success('工作项已创建')
      navigate(`/project-spaces/${space.id}/work-items/${item.id}`)
    },
    onError: (error) => message.error(errorMessage(error, '创建失败，输入已保留')),
  })
  return (
    <Modal
      width={880}
      open={open}
      title={`新建${formQuery.data?.typeName ?? '工作项'}`}
      okText="创建"
      cancelText="取消"
      okButtonProps={{ disabled: !online || !title.trim() || !runtime }}
      confirmLoading={mutation.isPending}
      onCancel={onCancel}
      onOk={() => mutation.mutate()}
      destroyOnHidden={false}
    >
      {!typeId ? <Alert type="warning" showIcon message="请先选择工作项类型" /> : null}
      {formQuery.isLoading ? <Skeleton active /> : null}
      {formQuery.isError ? <Alert type="error" showIcon message="创建表单加载失败" description={errorMessage(formQuery.error, '请稍后重试')} /> : null}
      {runtime ? (
        <div className="project-work-item-form">
          <Form layout="vertical">
            <Form.Item label="标题" htmlFor="work-item-create-title" required>
              <Input
                id="work-item-create-title"
                autoFocus
                maxLength={500}
                value={title}
                placeholder="输入工作项标题"
                onChange={(event) => setTitle(event.target.value)}
                onPressEnter={(event) => {
                  if (!event.nativeEvent.isComposing && title.trim()) mutation.mutate()
                }}
              />
            </Form.Item>
          </Form>
          <RuntimeLayout runtime={runtime} values={values} onValuesChange={setValues} />
        </div>
      ) : null}
    </Modal>
  )
}

function WorkItemDetail({ space, workItemId }: { space: UserProjectSpace; workItemId: string }) {
  const { message, modal } = AntdApp.useApp()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const online = useOnlineStatus()
  const [draft, setDraft] = useState<{
    version: number
    title: string
    values: WorkItemLayoutValues
  } | null>(null)
  const itemQuery = useQuery({
    queryKey: workItemKeys.detail(space.id, workItemId),
    queryFn: () => getWorkItem(space.id, workItemId),
    retry: (count, error) => !(error instanceof ApiRequestError && [403, 404].includes(error.status)) && count < 2,
  })
  const item = itemQuery.data

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: workItemKeys.detail(space.id, workItemId) }),
      queryClient.invalidateQueries({ queryKey: workItemKeys.list(space.id) }),
      queryClient.invalidateQueries({ queryKey: workItemKeys.activities(space.id, workItemId) }),
    ])
  }
  const saveMutation = useMutation({
    mutationFn: () => {
      if (!item) throw new Error('工作项尚未加载')
      if (!online || !navigator.onLine) {
        throw new Error('当前处于离线状态，输入已保留，请联网后重试')
      }
      return updateWorkItem(space.id, workItemId, {
        title: title.trim(),
        fieldValues: values,
        expectedVersion: item.version,
      })
    },
    onSuccess: async () => {
      await refresh()
      message.success('工作项已保存')
    },
    onError: (error) => {
      const conflict = error instanceof ApiRequestError
        && error.code?.toLowerCase() === 'work_item_version_conflict'
      message.error(conflict ? '工作项已被他人修改。当前输入已保留，请刷新对比后重试。' : errorMessage(error, '保存失败，输入已保留'))
    },
  })
  const transitionMutation = useMutation({
    mutationFn: (action: 'archive' | 'restore') => transitionWorkItem(space.id, workItemId, action, item?.version ?? 0),
    onSuccess: async (_, action) => {
      await refresh()
      message.success(action === 'archive' ? '工作项已归档' : '工作项已恢复')
    },
    onError: (error) => message.error(errorMessage(error, '状态变更失败')),
  })

  if (itemQuery.isLoading) return <Card><Skeleton active /></Card>
  if (itemQuery.isError || !item) {
    return (
      <Card>
        <Empty description="工作项不存在或你无权访问">
          <Button onClick={() => navigate(`/project-spaces/${space.id}/work-items`)}>返回工作项列表</Button>
        </Empty>
      </Card>
    )
  }
  const activeDraft = draft?.version === item.version ? draft : null
  const title = activeDraft?.title ?? item.title
  const values = activeDraft?.values ?? item.fieldValues
  const setTitle = (next: string) => setDraft({
    version: item.version,
    title: next,
    values,
  })
  const setValues = (next: WorkItemLayoutValues) => setDraft({
    version: item.version,
    title,
    values: next,
  })
  const writable = item.availableActions.includes('edit')
  return (
    <section className="project-work-item-detail" data-testid="project-work-item-detail">
      <Card
        className="content-card project-work-item-detail-card"
        title={(
          <Space wrap>
            <Button type="text" aria-label="返回工作项列表" icon={<ArrowLeftOutlined />} onClick={() => navigate(`/project-spaces/${space.id}/work-items`)} />
            <Tag color="purple">{item.typeName}</Tag>
            <Typography.Text type="secondary">{item.displayKey}</Typography.Text>
            <Tag color={item.status === 'active' ? 'green' : 'default'}>{item.status}</Tag>
          </Space>
        )}
        extra={(
          <Space wrap>
            {writable ? <Button type="primary" icon={<SaveOutlined />} disabled={!online} loading={saveMutation.isPending} onClick={() => saveMutation.mutate()}>保存</Button> : null}
            {item.availableActions.includes('archive') ? (
              <Button
                danger
                icon={<InboxOutlined />}
                onClick={() => modal.confirm({
                  title: '归档工作项？',
                  content: '归档后工作项只读，可稍后恢复。',
                  okText: '归档',
                  okButtonProps: { danger: true },
                  onOk: () => transitionMutation.mutateAsync('archive'),
                })}
              >
                归档
              </Button>
            ) : null}
            {item.availableActions.includes('restore') ? <Button icon={<ReloadOutlined />} onClick={() => transitionMutation.mutate('restore')}>恢复</Button> : null}
          </Space>
        )}
      >
        <Form layout="vertical">
          <Form.Item label="标题" htmlFor="work-item-detail-title" required>
            <Input
              id="work-item-detail-title"
              maxLength={500}
              disabled={!writable}
              value={title}
              onChange={(event) => setTitle(event.target.value)}
            />
          </Form.Item>
        </Form>
        <RuntimeLayout
          runtime={item.runtime}
          values={values}
          presentation={writable ? 'edit' : 'read'}
          onValuesChange={setValues}
        />
      </Card>
      <WorkItemPermissionsPanel space={space} item={item} />
      <WorkItemRelationsPanel
        space={space}
        item={item}
        online={online}
        refreshItem={refresh}
      />
      <WorkItemNodeWorkflowPanel
        spaceId={space.id}
        item={item}
        online={online}
        refreshItem={refresh}
      />
      <WorkItemWorkflowPanel
        space={space}
        item={item}
        fieldPatch={values}
        online={online}
        refreshItem={refresh}
      />
      <WorkItemCollaboration space={space} item={item} refreshItem={refresh} />
    </section>
  )
}

function WorkItemCollaboration({
  space,
  item,
  refreshItem,
}: {
  space: UserProjectSpace
  item: WorkItem
  refreshItem: () => Promise<void>
}) {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const [comment, setComment] = useState('')
  const [uploadProgress, setUploadProgress] = useState<number | null>(null)
  const participantsQuery = useQuery({
    queryKey: workItemKeys.participants(space.id, item.id),
    queryFn: () => listWorkItemParticipants(space.id, item.id),
  })
  const activitiesQuery = useQuery({
    queryKey: workItemKeys.activities(space.id, item.id),
    queryFn: () => listWorkItemActivities(space.id, item.id),
  })
  const commentsQuery = useQuery({
    queryKey: workItemKeys.comments(space.id, item.id),
    queryFn: () => listWorkItemComments(space.id, item.id),
  })
  const attachmentsQuery = useQuery({
    queryKey: workItemKeys.attachments(space.id, item.id),
    queryFn: () => listWorkItemAttachments(space.id, item.id),
  })
  const membersQuery = useQuery({
    queryKey: ['project-spaces', space.id, 'members'],
    queryFn: () => listProjectSpaceMembers(space.id),
    enabled: item.availableActions.includes('edit'),
  })
  const invalidate = async () => {
    await refreshItem()
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: workItemKeys.participants(space.id, item.id) }),
      queryClient.invalidateQueries({ queryKey: workItemKeys.comments(space.id, item.id) }),
      queryClient.invalidateQueries({ queryKey: workItemKeys.attachments(space.id, item.id) }),
    ])
  }
  const commentMutation = useMutation({
    mutationFn: () => addWorkItemComment(space.id, item.id, comment, item.version),
    onSuccess: async () => {
      setComment('')
      await invalidate()
      message.success('评论已发布')
    },
    onError: (error) => message.error(errorMessage(error, '评论失败，内容已保留')),
  })
  const participantMutation = useMutation({
    mutationFn: (userId: string) => putWorkItemParticipant(space.id, item.id, userId, 'collaborator', item.version),
    onSuccess: async () => {
      await invalidate()
      message.success('参与者已添加')
    },
    onError: (error) => message.error(errorMessage(error, '添加参与者失败')),
  })
  const nudgeMutation = useMutation({
    mutationFn: (userId: string) => nudgeWorkItemParticipant(space.id, item.id, userId),
    onSuccess: () => message.success('催办已发送'),
    onError: (error) => message.error(errorMessage(error, '催办不可用或仍在冷却期')),
  })
  const attachmentMutation = useMutation({
    mutationFn: async (file: File) => {
      setUploadProgress(0)
      const metadata = await uploadWorkItemFile(file, item.id, setUploadProgress)
      return addWorkItemAttachment(space.id, item.id, metadata.id, item.version)
    },
    onSuccess: async () => {
      setUploadProgress(null)
      await invalidate()
      message.success('附件已上传')
    },
    onError: (error) => {
      setUploadProgress(null)
      message.error(errorMessage(error, '附件上传失败'))
    },
  })

  return (
    <Card className="content-card project-work-item-collaboration">
      <Tabs
        items={[
          {
            key: 'comments',
            label: <Space><CommentOutlined />评论</Space>,
            children: (
              <div className="project-work-item-tab">
                {item.availableActions.includes('edit') ? (
                  <div className="project-work-item-comment-compose">
                    <Input.TextArea
                      value={comment}
                      autoSize={{ minRows: 2, maxRows: 8 }}
                      maxLength={20_000}
                      placeholder="输入评论，Ctrl/⌘ + Enter 发布"
                      onChange={(event) => setComment(event.target.value)}
                      onKeyDown={(event) => {
                        if ((event.ctrlKey || event.metaKey) && event.key === 'Enter' && comment.trim()) {
                          commentMutation.mutate()
                        }
                      }}
                    />
                    <Button type="primary" disabled={!comment.trim()} loading={commentMutation.isPending} onClick={() => commentMutation.mutate()}>发布</Button>
                  </div>
                ) : null}
                <List
                  locale={{ emptyText: '暂无评论' }}
                  loading={commentsQuery.isLoading}
                  dataSource={commentsQuery.data?.items ?? []}
                  renderItem={(value) => (
                    <List.Item>
                      <List.Item.Meta
                        avatar={<Avatar>{(value.authorDisplayName ?? '?').slice(0, 1)}</Avatar>}
                        title={<Space><span>{value.authorDisplayName ?? '成员'}</span><Typography.Text type="secondary">{formatTime(value.createdAt)}</Typography.Text></Space>}
                        description={<Typography.Paragraph className="project-work-item-long-content">{value.content}</Typography.Paragraph>}
                      />
                    </List.Item>
                  )}
                />
              </div>
            ),
          },
          {
            key: 'participants',
            label: <Space><TeamOutlined />参与者</Space>,
            children: (
              <div className="project-work-item-tab">
                {item.availableActions.includes('edit') ? (
                  <Select
                    showSearch
                    aria-label="添加参与者"
                    placeholder="选择空间成员"
                    loading={membersQuery.isLoading || participantMutation.isPending}
                    optionFilterProp="label"
                    options={membersQuery.data
                      ?.filter((member) => member.effective && !participantsQuery.data?.items.some((item) => item.userId === member.userId))
                      .map((member) => ({ label: member.displayName, value: member.userId }))}
                    onChange={(userId) => participantMutation.mutate(userId)}
                  />
                ) : null}
                <List
                  locale={{ emptyText: '暂无参与者' }}
                  loading={participantsQuery.isLoading}
                  dataSource={participantsQuery.data?.items ?? []}
                  renderItem={(value) => (
                    <List.Item
                      extra={(
                        <Space>
                          <Tag>{value.role}</Tag>
                          {['owner', 'assignee'].includes(value.role) ? (
                            <Button
                              size="small"
                              loading={nudgeMutation.isPending}
                              onClick={() => nudgeMutation.mutate(value.userId)}
                            >
                              催办
                            </Button>
                          ) : null}
                        </Space>
                      )}
                    >
                      <List.Item.Meta
                        avatar={<Avatar>{(value.displayName ?? '?').slice(0, 1)}</Avatar>}
                        title={value.displayName ?? value.userId}
                      />
                    </List.Item>
                  )}
                />
              </div>
            ),
          },
          {
            key: 'attachments',
            label: <Space><PaperClipOutlined />附件</Space>,
            children: (
              <div className="project-work-item-tab">
                {item.availableActions.includes('edit') ? (
                  <Upload
                    showUploadList={false}
                    beforeUpload={(file) => {
                      attachmentMutation.mutate(file)
                      return false
                    }}
                  >
                    <Button icon={<PaperClipOutlined />} loading={attachmentMutation.isPending}>上传附件</Button>
                  </Upload>
                ) : null}
                {uploadProgress != null ? <Progress percent={uploadProgress} size="small" /> : null}
                <List
                  locale={{ emptyText: '暂无附件' }}
                  loading={attachmentsQuery.isLoading}
                  dataSource={attachmentsQuery.data?.items ?? []}
                  renderItem={(value) => (
                    <List.Item
                      actions={[
                        <Button
                          key="download"
                          type="link"
                          onClick={() => void getWorkItemAttachmentDownloadUrl(value.fileId).then((result) => window.open(result.downloadUrl, '_blank', 'noopener,noreferrer'))}
                        >
                          下载
                        </Button>,
                      ]}
                    >
                      <List.Item.Meta
                        avatar={<Avatar icon={<FileOutlined />} />}
                        title={value.fileName}
                        description={`${formatBytes(value.sizeBytes)} · ${value.createdByDisplayName ?? '成员'} · ${formatTime(value.createdAt)}`}
                      />
                    </List.Item>
                  )}
                />
              </div>
            ),
          },
          {
            key: 'activity',
            label: <Space><HistoryOutlined />活动</Space>,
            children: (
              <List
                locale={{ emptyText: '暂无活动' }}
                loading={activitiesQuery.isLoading}
                dataSource={activitiesQuery.data?.items ?? []}
                renderItem={(value) => (
                  <List.Item>
                    <List.Item.Meta
                      title={<Space><Tag>{value.type}</Tag><span>{value.actorDisplayName ?? '系统'}</span></Space>}
                      description={formatTime(value.occurredAt)}
                    />
                  </List.Item>
                )}
              />
            ),
          },
        ]}
      />
    </Card>
  )
}

function RuntimeLayout({
  runtime,
  values,
  onValuesChange,
  presentation = 'edit',
}: {
  runtime: WorkItemRuntime
  values: WorkItemLayoutValues
  onValuesChange: (values: WorkItemLayoutValues) => void
  presentation?: 'edit' | 'read'
}) {
  const projection = useMemo(() => normalizeRuntime(runtime), [runtime])
  return (
    <WorkItemLayoutRenderer
      layout={projection.layout}
      fields={projection.fields}
      accessProjection={runtime.accessProjection as Record<string, WorkItemFieldAccessProjection>}
      values={values}
      presentation={presentation}
      onValuesChange={onValuesChange}
    />
  )
}

function normalizeRuntime(runtime: WorkItemRuntime) {
  const fields = runtime.snapshot.fields as unknown as ConfiguredWorkItemField[]
  const fieldByKey = new Map(fields.map((field) => [field.fieldKey, field]))
  const source = runtime.snapshot.layouts.find((layout) => layout.layoutKind === runtime.layoutKind)
  const rawNodes = Array.isArray(source?.nodes) ? source.nodes as Array<Record<string, unknown>> : []
  const idByKey = new Map(rawNodes.map((node) => [String(node.nodeKey), String(node.id)]))
  const nodes: WorkItemLayoutNode[] = rawNodes.map((node) => {
    const fieldKey = node.fieldKey == null ? null : String(node.fieldKey)
    const parentKey = node.parentKey == null ? null : String(node.parentKey)
    return {
      id: String(node.id),
      parentId: parentKey ? idByKey.get(parentKey) ?? null : null,
      nodeKey: String(node.nodeKey),
      nodeType: String(node.nodeType) as WorkItemLayoutNode['nodeType'],
      fieldId: fieldKey ? fieldByKey.get(fieldKey)?.id ?? null : null,
      fieldKey,
      sortOrder: Number(node.sortOrder ?? 0),
      config: (node.config ?? {}) as Record<string, unknown>,
      visibilityCondition: (node.visibilityCondition ?? { schemaVersion: 1 }) as WorkItemLayoutNode['visibilityCondition'],
    }
  })
  if (nodes.length === 0) {
    const rootId = `runtime-${runtime.layoutKind}-root`
    nodes.push({
      id: rootId,
      parentId: null,
      nodeKey: 'main',
      nodeType: 'section',
      fieldId: null,
      fieldKey: null,
      sortOrder: 0,
      config: { title: '详细信息' },
      visibilityCondition: { schemaVersion: 1 },
    })
    fields.forEach((field, index) => nodes.push({
      id: `runtime-${field.id}`,
      parentId: rootId,
      nodeKey: `field-${field.fieldKey}`,
      nodeType: 'field',
      fieldId: field.id,
      fieldKey: field.fieldKey,
      sortOrder: index,
      config: {},
      visibilityCondition: { schemaVersion: 1 },
    }))
  }
  return {
    fields,
    layout: { layoutKind: runtime.layoutKind, nodes },
  }
}

function defaultValues(runtime: WorkItemRuntime) {
  return Object.fromEntries(runtime.snapshot.fields.flatMap((field) => {
    const config = field.config as Record<string, unknown> | undefined
    return config?.defaultValue == null ? [] : [[String(field.fieldKey), config.defaultValue]]
  }))
}

function formatBytes(value: number) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

function shiftDate(value: string, days: number) {
  const date = new Date(`${value}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}

function dateDistance(start: string, end: string) {
  return Math.round(
    (Date.parse(`${end}T00:00:00Z`) - Date.parse(`${start}T00:00:00Z`))
      / (24 * 60 * 60 * 1000),
  )
}

function calendarWindow(anchor: string, mode: 'month' | 'week' | 'day') {
  const date = new Date(`${anchor}T00:00:00Z`)
  if (mode === 'day') return { startDate: anchor, endDate: anchor }
  if (mode === 'week') {
    const mondayOffset = (date.getUTCDay() + 6) % 7
    const startDate = shiftDate(anchor, -mondayOffset)
    return { startDate, endDate: shiftDate(startDate, 6) }
  }
  const year = date.getUTCFullYear()
  const month = date.getUTCMonth()
  const startDate = new Date(Date.UTC(year, month, 1)).toISOString().slice(0, 10)
  const endDate = new Date(Date.UTC(year, month + 1, 0)).toISOString().slice(0, 10)
  return { startDate, endDate }
}

function shiftCalendarAnchor(
  anchor: string,
  mode: 'month' | 'week' | 'day',
  direction: -1 | 1,
) {
  if (mode === 'day') return shiftDate(anchor, direction)
  if (mode === 'week') return shiftDate(anchor, direction * 7)
  const date = new Date(`${anchor}T00:00:00Z`)
  date.setUTCMonth(date.getUTCMonth() + direction)
  return date.toISOString().slice(0, 10)
}

function ganttBarStyle(
  start: string,
  end: string,
  windowStart: string,
  windowEnd: string,
) {
  const total = Math.max(1, dateDistance(windowStart, windowEnd) + 1)
  const offset = Math.max(0, dateDistance(windowStart, start))
  const duration = Math.max(1, dateDistance(start, end) + 1)
  return {
    '--gantt-left': `${Math.min(100, offset / total * 100)}%`,
    '--gantt-width': `${Math.max(2, Math.min(100 - offset / total * 100, duration / total * 100))}%`,
  } as CSSProperties
}

function useOnlineStatus() {
  const [online, setOnline] = useState(() => navigator.onLine)
  useEffect(() => {
    const markOnline = () => setOnline(true)
    const markOffline = () => setOnline(false)
    window.addEventListener('online', markOnline)
    window.addEventListener('offline', markOffline)
    return () => {
      window.removeEventListener('online', markOnline)
      window.removeEventListener('offline', markOffline)
    }
  }, [])
  return online
}
