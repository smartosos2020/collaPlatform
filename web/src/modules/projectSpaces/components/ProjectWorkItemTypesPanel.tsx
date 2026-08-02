import {
  CopyOutlined,
  EditOutlined,
  FileTextOutlined,
  HolderOutlined,
  InboxOutlined,
  InfoCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  StopOutlined,
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
  InputNumber,
  Modal,
  Segmented,
  Skeleton,
  Space,
  Tag,
  Tabs,
  Tooltip,
  Typography,
} from 'antd'
import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent,
  type ReactNode,
} from 'react'

import { ApiRequestError } from '../../../shared/api/httpClient'
import {
  copyWorkItemType,
  createWorkItemType,
  getConfiguredWorkItemType,
  listConfiguredWorkItemTypes,
  reorderWorkItemTypes,
  transitionWorkItemType,
  updateWorkItemType,
  workItemTypeKeys,
  type ConfiguredWorkItemType,
  type WorkItemTypeConfiguration,
  type WorkItemTypeDraft,
  type WorkItemTypeStatus,
} from '../api/workItemTypesApi'
import type { UserProjectSpace } from '../api/projectSpacesApi'
import { workItemConfigurationDraftKeys } from '../api/workItemConfigurationApi'
import type { ProjectSpaceWorkModelTab } from '../projectSpaceRouteContract'
import { errorMessage, formatTime } from '../projectSpaceView'

type FilterStatus = 'all' | WorkItemTypeStatus
type TypeForm = WorkItemTypeDraft
const VERSION_CONFLICT_MESSAGE = '数据已被其他人更新，已刷新为最新版本，请检查当前输入后重新保存。'

export function ProjectWorkItemTypesPanel({
  space,
  selectedTypeId,
  autoSelectFirst = true,
  onSelectType,
  onConfigureFields,
  onConfigureLayouts,
  embeddedConfiguration,
  selectedDetailTab,
  onSelectDetailTab,
}: {
  space: UserProjectSpace
  selectedTypeId?: string
  autoSelectFirst?: boolean
  onSelectType: (
    typeId: string,
    options?: Readonly<{ replace?: boolean }>,
  ) => void
  onConfigureFields?: (typeId: string) => void
  onConfigureLayouts?: (typeId: string) => void
  embeddedConfiguration?: Readonly<{
    fields: ReactNode
    layouts: ReactNode
    flowAccess: ReactNode
  }>
  selectedDetailTab?: ProjectSpaceWorkModelTab
  onSelectDetailTab?: (tab: ProjectSpaceWorkModelTab) => void
}) {
  const { message, modal } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<FilterStatus>('all')
  const [localDetailTab, setLocalDetailTab] = useState<ProjectSpaceWorkModelTab>('type-information')
  const detailTab = selectedDetailTab ?? localDetailTab
  const [editorMode, setEditorMode] = useState<'create' | 'edit' | 'copy' | null>(null)
  const [editorConflict, setEditorConflict] = useState<string | null>(null)
  const [draggedTypeId, setDraggedTypeId] = useState<string>()
  const [dragTargetTypeId, setDragTargetTypeId] = useState<string>()
  const [dragStatus, setDragStatus] = useState('')
  const draggedTypeIdRef = useRef<string | undefined>(undefined)
  const [form] = Form.useForm<TypeForm>()
  const statusFilter = status === 'all' ? undefined : status
  const configurationKey = workItemTypeKeys.configuration(space.id, status)
  const configurationQuery = useQuery({
    queryKey: configurationKey,
    queryFn: () => listConfiguredWorkItemTypes(space.id, statusFilter),
    retry: false,
  })
  const items = useMemo(() => configurationQuery.data?.items ?? [], [configurationQuery.data])
  const selectedFromList = items.find((item) => item.id === selectedTypeId)
  const detailQuery = useQuery({
    queryKey: workItemTypeKeys.detail(space.id, selectedTypeId ?? 'none'),
    queryFn: () => getConfiguredWorkItemType(space.id, selectedTypeId as string),
    enabled: Boolean(selectedTypeId),
    retry: false,
  })
  const selected = detailQuery.data ?? selectedFromList

  useEffect(() => {
    if (
      autoSelectFirst
      && !configurationQuery.isLoading
      && items.length > 0
      && !selectedTypeId
    ) {
      onSelectType(items[0].id, { replace: true })
    }
  }, [autoSelectFirst, configurationQuery.isLoading, items, onSelectType, selectedTypeId])

  const refresh = async (typeId?: string) => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: [...workItemTypeKeys.all, space.id] }),
      queryClient.invalidateQueries({ queryKey: workItemTypeKeys.active(space.id) }),
      typeId ? queryClient.invalidateQueries({ queryKey: workItemTypeKeys.detail(space.id, typeId) }) : Promise.resolve(),
      typeId ? queryClient.invalidateQueries({ queryKey: workItemConfigurationDraftKeys.detail(space.id, typeId) }) : Promise.resolve(),
    ])
  }

  const createMutation = useMutation({
    mutationFn: (values: TypeForm) => createWorkItemType(space.id, normalizeDraft(values)),
    onSuccess: async (created) => {
      await refresh(created.id)
      setEditorMode(null)
      form.resetFields()
      onSelectType(created.id)
      message.success('工作项类型已创建')
    },
    onError: (error) => showMutationError(message, error, '创建工作项类型失败'),
  })
  const updateMutation = useMutation({
    mutationFn: (values: TypeForm) => updateWorkItemType(space.id, selected!.id, {
      name: values.name.trim(),
      icon: values.icon?.trim() || '',
      description: values.description?.trim() || '',
      aggregateVersion: selected!.aggregateVersion,
    }),
    onSuccess: async (updated) => {
      await refresh(updated.id)
      setEditorConflict(null)
      setEditorMode(null)
      message.success('工作项类型已更新')
    },
    onError: (error) => {
      if (isVersionConflict(error)) {
        setEditorConflict(VERSION_CONFLICT_MESSAGE)
        void refresh(selected?.id)
      }
      showMutationError(message, error, '更新工作项类型失败')
    },
  })
  const copyMutation = useMutation({
    mutationFn: (values: TypeForm) => copyWorkItemType(space.id, selected!.id, normalizeDraft(values)),
    onSuccess: async (copied) => {
      await refresh(copied.id)
      setEditorMode(null)
      form.resetFields()
      onSelectType(copied.id)
      message.success('工作项类型已复制')
    },
    onError: (error) => showMutationError(message, error, '复制工作项类型失败'),
  })
  const transitionMutation = useMutation({
    mutationFn: (action: 'disable' | 'restore' | 'retire') =>
      transitionWorkItemType(space.id, selected!.id, action, selected!.aggregateVersion),
    onSuccess: async (updated, action) => {
      await refresh(updated.id)
      message.success(action === 'restore' ? '工作项类型已恢复' : action === 'disable' ? '工作项类型已停用' : '工作项类型已退役')
    },
    onError: (error) => {
      if (isVersionConflict(error)) void refresh(selected?.id)
      showMutationError(message, error, '工作项类型状态变更失败')
    },
  })
  const reorderMutation = useMutation({
    mutationFn: (payload: ReorderPayload) => reorderWorkItemTypes(space.id, payload.entries),
    onMutate: async (payload) => {
      await queryClient.cancelQueries({ queryKey: configurationKey })
      const previous = queryClient.getQueryData<WorkItemTypeConfiguration>(configurationKey)
      if (previous) {
        queryClient.setQueryData<WorkItemTypeConfiguration>(configurationKey, {
          ...previous,
          items: applyOptimisticOrder(previous.items, payload.entries),
        })
      }
      return { previous }
    },
    onError: (_error, _payload, context) => {
      if (context?.previous) queryClient.setQueryData(configurationKey, context.previous)
      setDragStatus('排序保存失败，已恢复原顺序')
      message.error('排序保存失败，已恢复原顺序')
    },
    onSuccess: (configuration) => {
      queryClient.setQueryData(configurationKey, {
        ...configuration,
        items: statusFilter
          ? configuration.items.filter((item) => item.status === statusFilter)
          : configuration.items,
      })
      for (const item of configuration.items) {
        queryClient.setQueryData(workItemTypeKeys.detail(space.id, item.id), item)
      }
      setDragStatus('类型顺序已保存')
      message.success('类型顺序已保存')
    },
    onSettled: async () => refresh(selected?.id),
  })

  const openEditor = (mode: 'create' | 'edit' | 'copy') => {
    setEditorConflict(null)
    setEditorMode(mode)
    if (mode === 'create') {
      form.setFieldsValue({ name: '', typeKey: '', icon: '', description: '', sortOrder: nextSortOrder(items) })
      return
    }
    if (!selected) return
    form.setFieldsValue({
      typeKey: mode === 'copy' ? `${selected.typeKey}_copy` : selected.typeKey,
      name: mode === 'copy' ? `${selected.name} 副本` : selected.name,
      icon: selected.icon ?? '',
      description: selected.description ?? '',
      sortOrder: mode === 'copy' ? nextSortOrder(items) : selected.sortOrder,
    })
  }

  const commitTypeMove = (sourceId: string, targetId: string, placeAfter: boolean) => {
    if (sourceId === targetId || reorderMutation.isPending) return
    const source = items.find((item) => item.id === sourceId)
    const target = items.find((item) => item.id === targetId)
    if (!source || !target || source.status !== target.status) return
    if (!source.availableActions.includes('reorder') || !target.availableActions.includes('reorder')) return

    const sameStatus = items.filter((item) => item.status === source.status)
    const reordered = sameStatus.filter((item) => item.id !== source.id)
    const targetIndex = reordered.findIndex((item) => item.id === target.id)
    if (targetIndex < 0) return
    reordered.splice(targetIndex + (placeAfter ? 1 : 0), 0, source)

    const currentSortOrders = sameStatus.map((item) => item.sortOrder).sort((left, right) => left - right)
    const sortOrders = new Set(currentSortOrders).size === currentSortOrders.length
      ? currentSortOrders
      : sameStatus.map((_, index) => (index + 1) * 10)
    const entries = reordered
      .map((item, index) => ({
        typeId: item.id,
        sortOrder: sortOrders[index],
        aggregateVersion: item.aggregateVersion,
      }))
      .filter((entry) => items.find((item) => item.id === entry.typeId)?.sortOrder !== entry.sortOrder)
    if (entries.length > 0) {
      setDragStatus('正在保存类型顺序')
      reorderMutation.mutate({ entries })
    }
  }

  const clearTypeDrag = () => {
    draggedTypeIdRef.current = undefined
    setDraggedTypeId(undefined)
    setDragTargetTypeId(undefined)
  }

  const cancelTypeDrag = () => {
    if (draggedTypeIdRef.current) setDragStatus('已取消排序')
    clearTypeDrag()
  }

  const resolvePointerTarget = (clientX: number, clientY: number) => {
    const row = document.elementFromPoint(clientX, clientY)?.closest<HTMLElement>('[data-type-id]')
    const target = items.find((item) => item.id === row?.dataset.typeId)
    const source = items.find((item) => item.id === draggedTypeIdRef.current)
    if (
      !row
      || !source
      || !target
      || source.id === target.id
      || source.status !== target.status
      || !source.availableActions.includes('reorder')
      || !target.availableActions.includes('reorder')
    ) return undefined
    return { row, source, target }
  }

  const reorderTypeWithKeyboard = (
    event: ReactKeyboardEvent<HTMLElement>,
    type: ConfiguredWorkItemType,
  ) => {
    if (event.key !== 'ArrowUp' && event.key !== 'ArrowDown') return
    event.preventDefault()
    const sameStatus = items.filter((item) => item.status === type.status)
    const currentIndex = sameStatus.findIndex((item) => item.id === type.id)
    const direction = event.key === 'ArrowUp' ? -1 : 1
    const target = sameStatus[currentIndex + direction]
    if (target) commitTypeMove(type.id, target.id, direction > 0)
  }

  const confirmTransition = (action: 'disable' | 'restore' | 'retire') => {
    const labels = { disable: '停用', restore: '恢复', retire: '退役' }
    modal.confirm({
      title: `确认${labels[action]}“${selected?.name}”？`,
      content: action === 'retire'
        ? '退役不可恢复，该类型将不再参与后续配置。'
        : action === 'disable' ? '停用后，成员执行入口将不再展示该类型。' : '恢复后，成员执行入口将重新展示该类型。',
      okText: `确认${labels[action]}`,
      okButtonProps: action === 'restore' ? {} : { danger: true },
      onOk: () => transitionMutation.mutateAsync(action),
    })
  }

  const submitEditor = (values: TypeForm) => {
    if (editorMode === 'create') createMutation.mutate(values)
    if (editorMode === 'edit') updateMutation.mutate(values)
    if (editorMode === 'copy') copyMutation.mutate(values)
  }

  return (
    <section className="work-item-type-panel" data-testid="work-item-types-panel">
      <Card className="content-card work-item-type-toolbar-card">
        <div className="work-item-type-toolbar">
          <div>
            <Typography.Title level={4}>工作项类型</Typography.Title>
            <Typography.Text type="secondary">配置空间内可用的事项分类、顺序和生命周期。</Typography.Text>
          </div>
          <Space wrap className="work-item-type-toolbar-actions" data-testid="work-item-type-list-actions">
            {configurationQuery.data?.availableActions.includes('create') ? (
              <Button type="primary" aria-label="新建类型" icon={<PlusOutlined />} onClick={() => openEditor('create')}>新建类型</Button>
            ) : null}
            <Button
              aria-label="复制"
              icon={<CopyOutlined />}
              disabled={!selected?.availableActions.includes('copy') || copyMutation.isPending}
              onClick={() => openEditor('copy')}
            >
              复制
            </Button>
            <Button
              danger
              aria-label="停用"
              icon={<StopOutlined />}
              disabled={!selected?.availableActions.includes('disable') || transitionMutation.isPending}
              onClick={() => confirmTransition('disable')}
            >
              停用
            </Button>
          </Space>
        </div>
      </Card>

      {configurationQuery.isError ? (
        <Alert type="error" showIcon message="工作项类型加载失败" description={errorMessage(configurationQuery.error, '请稍后重试')} action={<Button onClick={() => configurationQuery.refetch()}>重试</Button>} />
      ) : null}

      <div className="work-item-type-layout">
        <Card className="content-card work-item-type-list-card" aria-label="工作项类型列表">
          <Segmented<FilterStatus>
            className="work-item-type-list-status-filter"
            aria-label="工作项类型状态筛选"
            value={status}
            onChange={setStatus}
            options={[
              { label: '全', value: 'all' },
              { label: '使用中', value: 'active' },
              { label: '已停', value: 'disabled' },
              { label: '已退', value: 'retired' },
            ]}
          />
          {configurationQuery.isLoading ? <Skeleton active paragraph={{ rows: 5 }} /> : null}
          {!configurationQuery.isLoading && items.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前筛选下暂无工作项类型" />
          ) : null}
          <span className="visually-hidden" role="status" aria-live="polite">{dragStatus}</span>
          <div
            className="work-item-type-list"
            role="listbox"
            aria-label="选择工作项类型"
            onPointerCancel={cancelTypeDrag}
            onPointerLeave={cancelTypeDrag}
            onPointerMove={(event) => {
              if (!draggedTypeIdRef.current) return
              setDragTargetTypeId(resolvePointerTarget(event.clientX, event.clientY)?.target.id)
            }}
            onPointerUp={(event) => {
              const wasDragging = Boolean(draggedTypeIdRef.current)
              const resolved = resolvePointerTarget(event.clientX, event.clientY)
              if (resolved) {
                const bounds = resolved.row.getBoundingClientRect()
                commitTypeMove(
                  resolved.source.id,
                  resolved.target.id,
                  event.clientY >= bounds.top + bounds.height / 2,
                )
              } else if (wasDragging) {
                setDragStatus('未调整顺序')
              }
              clearTypeDrag()
            }}
          >
            {items.map((type) => {
              const canReorder = type.availableActions.includes('reorder')
              return (
                <div
                  className={[
                    'work-item-type-list-item',
                    selectedTypeId === type.id ? 'active' : '',
                    draggedTypeId === type.id ? 'dragging' : '',
                    dragTargetTypeId === type.id ? 'drag-over' : '',
                  ].filter(Boolean).join(' ')}
                  data-type-id={type.id}
                  key={type.id}
                >
                  <button
                    type="button"
                    role="option"
                    aria-selected={selectedTypeId === type.id}
                    className="work-item-type-select"
                    onClick={() => onSelectType(type.id)}
                  >
                    <span className="work-item-type-list-copy">
                      <strong>{type.name}</strong>
                      <small>{type.typeKey}</small>
                    </span>
                    <span className="work-item-type-list-state"><WorkItemTypeStatusDot status={type.status} /></span>
                  </button>
                  {canReorder ? (
                    <Tooltip title="拖拽调整顺序；聚焦后可使用上下方向键">
                      <span
                        className="work-item-type-drag-handle"
                        role="button"
                        tabIndex={0}
                        aria-label={`拖拽排序 ${type.name}`}
                        aria-keyshortcuts="ArrowUp ArrowDown"
                        aria-disabled={reorderMutation.isPending}
                        onPointerDown={(event) => {
                          if (reorderMutation.isPending) return
                          event.preventDefault()
                          event.currentTarget.focus()
                          draggedTypeIdRef.current = type.id
                          setDraggedTypeId(type.id)
                          setDragStatus(`正在调整${type.name}的顺序`)
                        }}
                        onKeyDown={(event) => reorderTypeWithKeyboard(event, type)}
                      >
                        <HolderOutlined />
                      </span>
                    </Tooltip>
                  ) : null}
                </div>
              )
            })}
          </div>
        </Card>

        <Card className="content-card work-item-type-detail-card">
          {selectedTypeId && detailQuery.isLoading ? <Skeleton active /> : null}
          {detailQuery.isError ? <Empty description="类型不存在或已不可访问"><Button onClick={() => configurationQuery.refetch()}>返回列表</Button></Empty> : null}
          {!selectedTypeId && !configurationQuery.isLoading ? <Empty description="请选择一个工作项类型" /> : null}
          {selected ? (
            embeddedConfiguration ? (
              <Tabs
                className="work-item-type-detail-tabs"
                activeKey={detailTab}
                onChange={(key) => {
                  const tab = key as ProjectSpaceWorkModelTab
                  if (onSelectDetailTab) onSelectDetailTab(tab)
                  else setLocalDetailTab(tab)
                }}
                items={[
                  {
                    key: 'type-information',
                    label: '类型信息',
                    children: renderTypeInformation(selected, openEditor, confirmTransition),
                  },
                  {
                    key: 'field-configuration',
                    label: '配置字段',
                    children: embeddedConfiguration.fields,
                  },
                  {
                    key: 'page-layout',
                    label: '页面布局',
                    children: embeddedConfiguration.layouts,
                  },
                  {
                    key: 'flow-access',
                    label: '流程与权限',
                    children: embeddedConfiguration.flowAccess,
                  },
                ]}
              />
            ) : (
              renderTypeInformation(
                selected,
                openEditor,
                confirmTransition,
                onConfigureFields,
                onConfigureLayouts,
              )
            )
          ) : null}
        </Card>
      </div>

      <Modal
        title={editorMode === 'create' ? '新建工作项类型' : editorMode === 'copy' ? '复制工作项类型' : '编辑工作项类型'}
        open={Boolean(editorMode)}
        okText={editorMode === 'edit' ? '保存' : editorMode === 'copy' ? '复制' : '创建'}
        cancelText="取消"
        confirmLoading={createMutation.isPending || updateMutation.isPending || copyMutation.isPending}
        onCancel={() => {
          setEditorConflict(null)
          setEditorMode(null)
        }}
        onOk={() => form.submit()}
        forceRender
        destroyOnHidden
      >
        <Form<TypeForm> form={form} layout="vertical" preserve onFinish={submitEditor}>
          {editorConflict ? <Alert type="error" showIcon message={editorConflict} className="work-item-type-conflict-alert" /> : null}
          <Form.Item
            name="typeKey"
            label="类型标识"
            extra={editorMode === 'edit' ? '类型标识创建后不可修改。' : '空间内永久唯一，创建后不可修改。'}
            rules={[{ required: true, whitespace: true }, { pattern: /^[a-z][a-z0-9_]*$/, message: '以小写字母开头，仅支持小写字母、数字和下划线' }]}
          >
            <Input disabled={editorMode === 'edit'} autoFocus={editorMode !== 'edit'} maxLength={64} placeholder="例如：marketing_campaign" />
          </Form.Item>
          <Form.Item name="name" label="显示名称" rules={[{ required: true, whitespace: true }, { max: 128 }]}>
            <Input autoFocus={editorMode === 'edit'} placeholder="例如：市场活动" />
          </Form.Item>
          <Form.Item name="icon" label="图标标识" rules={[{ max: 64 }]}>
            <Input placeholder="可选，例如 campaign" />
          </Form.Item>
          <Form.Item name="description" label="类型说明" rules={[{ max: 2000 }]}>
            <Input.TextArea rows={4} placeholder="说明该类型的使用场景" />
          </Form.Item>
          {editorMode !== 'edit' ? (
            <Form.Item name="sortOrder" label="排序值" rules={[{ required: true }]}>
              <InputNumber min={0} precision={0} className="work-item-type-sort-input" />
            </Form.Item>
          ) : null}
        </Form>
      </Modal>
    </section>
  )
}

function renderTypeInformation(
  selected: ConfiguredWorkItemType,
  openEditor: (mode: 'create' | 'edit' | 'copy') => void,
  confirmTransition: (action: 'disable' | 'restore' | 'retire') => void,
  onConfigureFields?: (typeId: string) => void,
  onConfigureLayouts?: (typeId: string) => void,
) {
  const description = selected.description || '暂无类型说明'
  const protectionDescription = selected.system
    ? `系统预置类型受保护。来源：研发预置目录 ${selected.presetCatalogVersion ?? ''}。可调整顺序、复制或停用，但不能修改类型标识、展示定义或执行退役。`
    : undefined

  return (
    <div className="work-item-type-detail">
      <div
        className="content-card work-item-model-section-header work-item-type-detail-header"
        data-testid="work-item-model-section-header"
      >
        <div className="work-item-type-title-block">
          <Typography.Title level={3}>{selected.name}</Typography.Title>
          {selected.system ? <Tag color="blue">系统类型</Tag> : <Tag>自定义</Tag>}
          <span className="work-item-type-inline-separator" aria-hidden="true">|</span>
          <Typography.Text
            className="work-item-type-description"
            type={selected.description ? undefined : 'secondary'}
            title={description}
          >
            {description}
          </Typography.Text>
          {protectionDescription ? (
            <>
              <span className="work-item-type-inline-separator" aria-hidden="true">|</span>
              <Tooltip title={protectionDescription}>
                <Typography.Text className="work-item-type-protection-note">
                  <InfoCircleOutlined />
                  <span>{protectionDescription}</span>
                </Typography.Text>
              </Tooltip>
            </>
          ) : null}
        </div>
        <Space wrap>
          {onConfigureFields ? <Button onClick={() => onConfigureFields(selected.id)}>配置字段</Button> : null}
          {onConfigureLayouts ? <Button type="primary" onClick={() => onConfigureLayouts(selected.id)}>页面布局</Button> : null}
          {selected.availableActions.includes('edit') ? <Button icon={<EditOutlined />} onClick={() => openEditor('edit')}>编辑</Button> : null}
          {selected.availableActions.includes('restore') ? <Button className="work-item-type-restore" icon={<ReloadOutlined />} onClick={() => confirmTransition('restore')}>恢复</Button> : null}
          {selected.availableActions.includes('retire') ? <Button danger icon={<InboxOutlined />} onClick={() => confirmTransition('retire')}>退役</Button> : null}
        </Space>
      </div>
      <div className="work-item-type-facts">
        <div><span>排序</span><strong>{selected.sortOrder}</strong></div>
        <div><span>聚合版本</span><strong>{selected.aggregateVersion}</strong></div>
        <div><span>当前版本</span><strong>v{selected.currentVersion.number} · {selected.currentVersion.status}</strong></div>
        <div><span>更新时间</span><strong>{formatTime(selected.updatedAt)}</strong></div>
      </div>
      <div className="work-item-type-version-card">
        <div>
          <FileTextOutlined />
          <strong>已发布骨架版本 v{selected.currentVersion.number}</strong>
        </div>
        <Typography.Text type="secondary">配置哈希 {selected.currentVersion.configHash.slice(0, 16)}…</Typography.Text>
        <Typography.Paragraph type="secondary">
          展示属性属于类型定义；已发布版本保持不可变。动态字段、布局与流程将在后续阶段接入。
        </Typography.Paragraph>
      </div>
    </div>
  )
}

function WorkItemTypeStatusDot({ status }: { status: WorkItemTypeStatus }) {
  const label = status === 'active' ? '启用' : status === 'disabled' ? '停用' : '已退役'
  return (
    <Tooltip title={label}>
      <span
        className={`work-item-type-status-dot ${status}`}
        role="img"
        aria-label={label}
      />
    </Tooltip>
  )
}

function normalizeDraft(values: TypeForm): WorkItemTypeDraft {
  return {
    typeKey: values.typeKey.trim(),
    name: values.name.trim(),
    icon: values.icon?.trim() || '',
    description: values.description?.trim() || '',
    sortOrder: values.sortOrder ?? 0,
  }
}

function nextSortOrder(items: ConfiguredWorkItemType[]) {
  return items.reduce((maximum, item) => Math.max(maximum, item.sortOrder), 0) + 10
}

type ReorderPayload = {
  entries: Array<{ typeId: string; sortOrder: number; aggregateVersion: number }>
}

function applyOptimisticOrder(
  items: ConfiguredWorkItemType[],
  entries: ReorderPayload['entries'],
) {
  const orderById = new Map(entries.map((entry) => [entry.typeId, entry.sortOrder]))
  return items
    .map((item) => orderById.has(item.id) ? { ...item, sortOrder: orderById.get(item.id) as number } : item)
    .sort((left, right) => left.sortOrder - right.sortOrder || left.name.localeCompare(right.name))
}

function isVersionConflict(error: unknown) {
  return error instanceof ApiRequestError
    && error.status === 409
    && (error.code === 'version_conflict' || /version|版本|changed by another request/i.test(error.message))
}

function showMutationError(
  message: ReturnType<typeof AntdApp.useApp>['message'],
  error: unknown,
  fallback: string,
) {
  if (isVersionConflict(error)) {
    message.error('数据已被其他人更新，已刷新为最新版本，请重新操作。')
    return
  }
  message.error(errorMessage(error, fallback))
}
