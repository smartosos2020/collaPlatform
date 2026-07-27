import {
  ArrowLeftOutlined,
  ApartmentOutlined,
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
import { useEffect, useMemo, useState, type Key } from 'react'
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

export function ProjectWorkItemsPanel({
  space,
  workItemId,
}: {
  space: UserProjectSpace
  workItemId?: string
}) {
  return workItemId
    ? <WorkItemDetail space={space} workItemId={workItemId} />
    : <WorkItemCollection space={space} />
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
    enabled: Boolean(selectedTypeId) && !treeMode,
  })
  const treePreferenceQuery = useQuery({
    queryKey: workItemTreeViewKeys.preference(space.id),
    queryFn: () => getWorkItemTreePreference(space.id),
    enabled: treeMode,
  })
  const treeQuery = useQuery({
    queryKey: workItemTreeViewKeys.roots(space.id, viewSignature),
    queryFn: () => renderWorkItemTree(space.id, definition),
    enabled: Boolean(selectedTypeId) && treeMode,
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
    mutationFn: () => treeMode
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
        queryKey: treeMode
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
    setSavedDefinitionOverride({ ...view.query, cursor: null })
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
            onChange={setSelectedTypeOverride}
          />
          <Segmented
            aria-label="工作项视图模式"
            value={treeMode ? 'tree' : mode}
            options={[
              { label: '表格', value: 'table', icon: <TableOutlined /> },
              { label: '紧凑列表', value: 'list', icon: <UnorderedListOutlined /> },
              { label: '层级树', value: 'tree', icon: <ApartmentOutlined /> },
            ]}
            onChange={(value) => {
              setTreeMode(value === 'tree')
              if (value !== 'tree') setModeOverride(value as 'table' | 'list')
            }}
          />
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
        {(treeMode ? treeQuery.isLoading : itemsQuery.isLoading)
          ? <Skeleton active paragraph={{ rows: 5 }} />
          : null}
        {!treeMode && itemsQuery.isError ? (
          <Alert
            type="error"
            showIcon
            message="工作项加载失败"
            description={errorMessage(itemsQuery.error, '请稍后重试')}
            action={<Button size="small" onClick={() => itemsQuery.refetch()}>重试</Button>}
          />
        ) : null}
        {!treeMode && !itemsQuery.isLoading && !itemsQuery.isError && itemsQuery.data?.rows.length === 0 ? (
          <Empty description="当前类型还没有工作项">
            {space.status === 'active' && space.currentUserRole !== 'guest'
              ? <Button type="primary" onClick={() => openCreate()}>创建第一条工作项</Button>
              : null}
          </Empty>
        ) : null}
        {!treeMode && itemsQuery.data?.rows.length && mode === 'table' ? (
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
        {!treeMode && itemsQuery.data?.rows.length && mode === 'list' ? (
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
        {treeMode && treeQuery.isError ? (
          <Alert
            type="error"
            showIcon
            message="层级树加载失败"
            description="树不会回退为私表读取或猜测隐藏路径；请重试 REST 校准。"
            action={<Button size="small" onClick={() => treeQuery.refetch()}>重试</Button>}
          />
        ) : null}
        {treeMode && treeQuery.data ? (
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
