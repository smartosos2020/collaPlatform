import {
  ArrowLeftOutlined,
  CalendarOutlined,
  CheckSquareOutlined,
  ClockCircleOutlined,
  EditOutlined,
  FileAddOutlined,
  FileOutlined,
  HolderOutlined,
  LinkOutlined,
  NumberOutlined,
  PaperClipOutlined,
  PlusOutlined,
  ReloadOutlined,
  RetweetOutlined,
  SettingOutlined,
  StopOutlined,
  TeamOutlined,
  UnorderedListOutlined,
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
  Tooltip,
  Typography,
} from 'antd'
import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent,
} from 'react'

import { ApiRequestError } from '../../../shared/api/httpClient'
import {
  configureWorkItemField,
  createWorkItemField,
  getConfiguredWorkItemField,
  listConfiguredWorkItemFields,
  listWorkItemFieldTypes,
  reorderWorkItemFields,
  transitionWorkItemField,
  updateWorkItemField,
  workItemFieldKeys,
  type ConfiguredWorkItemField,
  type WorkItemFieldCollection,
  type WorkItemFieldStatus,
  type WorkItemFieldType,
  type WorkItemFieldTypeDescriptor,
} from '../api/workItemFieldsApi'
import {
  getConfiguredWorkItemType,
  listConfiguredWorkItemTypes,
  workItemTypeKeys,
} from '../api/workItemTypesApi'
import type { UserProjectSpace } from '../api/projectSpacesApi'
import { workItemConfigurationDraftKeys } from '../api/workItemConfigurationApi'
import { errorMessage, formatTime } from '../projectSpaceView'
import { WorkItemFieldConfigDrawer } from './WorkItemFieldConfigDrawer'

type FieldFilterStatus = 'all' | WorkItemFieldStatus
type FieldSort = 'configured' | 'name' | 'updated'
type FieldDraft = {
  fieldKey: string
  name: string
  description: string
  fieldType: WorkItemFieldType
}
type FieldEdit = Pick<FieldDraft, 'name' | 'description'>

export function ProjectWorkItemFieldsPanel({
  space,
  typeId,
  selectedFieldId,
  onBack,
  onSelectField,
  embedded = false,
}: {
  space: UserProjectSpace
  typeId: string
  selectedFieldId?: string
  onBack?: () => void
  onSelectField: (fieldId?: string) => void
  embedded?: boolean
}) {
  const { message, modal } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<FieldFilterStatus>('all')
  const [fieldType, setFieldType] = useState<'all' | WorkItemFieldType>('all')
  const [sort, setSort] = useState<FieldSort>('configured')
  const [search, setSearch] = useState('')
  const [createOpen, setCreateOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [configOpen, setConfigOpen] = useState(false)
  const [conflict, setConflict] = useState<string | null>(null)
  const [draggedFieldId, setDraggedFieldId] = useState<string>()
  const [dragTargetFieldId, setDragTargetFieldId] = useState<string>()
  const [dragStatus, setDragStatus] = useState('')
  const draggedFieldIdRef = useRef<string | undefined>(undefined)
  const reorderBusyRef = useRef(false)
  const [createForm] = Form.useForm<FieldDraft>()
  const [editForm] = Form.useForm<FieldEdit>()
  const selectedCreateType = Form.useWatch('fieldType', createForm)
  const statusFilter = status === 'all' ? undefined : status

  const typeQuery = useQuery({
    queryKey: workItemTypeKeys.detail(space.id, typeId),
    queryFn: () => getConfiguredWorkItemType(space.id, typeId),
    retry: false,
  })
  const allTypesQuery = useQuery({
    queryKey: workItemTypeKeys.configuration(space.id, 'all'),
    queryFn: () => listConfiguredWorkItemTypes(space.id),
    retry: false,
  })
  const catalogQuery = useQuery({
    queryKey: workItemFieldKeys.catalog(space.id),
    queryFn: () => listWorkItemFieldTypes(space.id),
    retry: false,
  })
  const configurationKey = workItemFieldKeys.configuration(space.id, typeId, status)
  const fieldsQuery = useQuery({
    queryKey: configurationKey,
    queryFn: () => listConfiguredWorkItemFields(space.id, typeId, statusFilter),
    retry: false,
  })
  const detailQuery = useQuery({
    queryKey: workItemFieldKeys.detail(space.id, typeId, selectedFieldId ?? 'none'),
    queryFn: () => getConfiguredWorkItemField(space.id, typeId, selectedFieldId as string),
    enabled: Boolean(selectedFieldId),
    retry: false,
  })
  const rawFields = useMemo(() => fieldsQuery.data?.items ?? [], [fieldsQuery.data])
  const fields = useMemo(
    () => projectFields(rawFields, search, fieldType, sort),
    [fieldType, rawFields, search, sort],
  )
  const selectedFromList = rawFields.find((field) => field.id === selectedFieldId)
  const selected = detailQuery.data ?? selectedFromList
  const descriptor = catalogQuery.data?.items.find((item) => item.key === selected?.fieldType)
  const createDescriptor = catalogQuery.data?.items.find((item) => item.key === selectedCreateType)

  useEffect(() => {
    if (!fieldsQuery.isLoading && fields.length > 0 && !selectedFieldId) {
      onSelectField(fields[0].id)
    }
  }, [fields, fieldsQuery.isLoading, onSelectField, selectedFieldId])

  useEffect(() => {
    if (fields.length === 0 && selectedFieldId && !fieldsQuery.isLoading) {
      onSelectField(undefined)
    }
  }, [fields.length, fieldsQuery.isLoading, onSelectField, selectedFieldId])

  const refresh = async (fieldId?: string) => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: [...workItemFieldKeys.all, space.id, typeId] }),
      fieldId
        ? queryClient.invalidateQueries({ queryKey: workItemFieldKeys.detail(space.id, typeId, fieldId) })
        : Promise.resolve(),
      queryClient.invalidateQueries({ queryKey: workItemConfigurationDraftKeys.detail(space.id, typeId) }),
    ])
  }

  const createMutation = useMutation({
    mutationFn: (values: FieldDraft) => {
      const type = catalogQuery.data?.items.find((item) => item.key === values.fieldType)
      if (!type) throw new Error('字段类型目录尚未加载')
      return createWorkItemField(space.id, typeId, {
        fieldKey: values.fieldKey.trim(),
        name: values.name.trim(),
        description: values.description?.trim() || '',
        fieldType: values.fieldType,
        config: type.defaultConfig,
        sortOrder: nextSortOrder(rawFields),
      })
    },
    onSuccess: async (field) => {
      await refresh(field.id)
      setCreateOpen(false)
      createForm.resetFields()
      onSelectField(field.id)
      message.success('字段已创建')
    },
    onError: (error) => showFieldError(message, error, '创建字段失败'),
  })
  const editMutation = useMutation({
    mutationFn: (values: FieldEdit) => updateWorkItemField(space.id, typeId, selected!.id, {
      name: values.name.trim(),
      description: values.description?.trim() || '',
      config: selected!.config,
      aggregateVersion: selected!.aggregateVersion,
    }),
    onSuccess: async (field) => {
      await refresh(field.id)
      setEditOpen(false)
      setConflict(null)
      message.success('字段信息已更新')
    },
    onError: (error) => {
      if (isVersionConflict(error)) {
        setConflict('字段已被其他人更新，系统已刷新最新版本。请核对后重新保存。')
        void refresh(selected?.id)
      }
      showFieldError(message, error, '更新字段失败')
    },
  })
  const configureMutation = useMutation({
    mutationFn: (request: Parameters<typeof configureWorkItemField>[3]) =>
      configureWorkItemField(space.id, typeId, selected!.id, request),
    onSuccess: async (field) => {
      await refresh(field.id)
      setConfigOpen(false)
      message.success('字段配置已保存')
    },
    onError: (error) => {
      if (isVersionConflict(error)) void refresh(selected?.id)
      showFieldError(message, error, '保存字段配置失败')
    },
  })
  const transitionMutation = useMutation({
    mutationFn: (action: 'disable' | 'restore' | 'retire') =>
      transitionWorkItemField(space.id, typeId, selected!.id, action, selected!.aggregateVersion),
    onSuccess: async (field, action) => {
      await refresh(field.id)
      message.success(action === 'restore' ? '字段已恢复' : action === 'disable' ? '字段已停用' : '字段已退役')
    },
    onError: (error) => {
      if (isVersionConflict(error)) void refresh(selected?.id)
      showFieldError(message, error, '字段状态变更失败')
    },
  })
  const reorderMutation = useMutation({
    mutationFn: (entries: ReorderEntry[]) => reorderWorkItemFields(space.id, typeId, entries),
    onMutate: async (entries) => {
      reorderBusyRef.current = true
      await queryClient.cancelQueries({ queryKey: configurationKey })
      const previous = queryClient.getQueryData<WorkItemFieldCollection>(configurationKey)
      if (previous) {
        queryClient.setQueryData<WorkItemFieldCollection>(configurationKey, {
          ...previous,
          items: applyOptimisticOrder(previous.items, entries),
        })
      }
      return { previous }
    },
    onError: (_error, _entries, context) => {
      if (context?.previous) queryClient.setQueryData(configurationKey, context.previous)
      setDragStatus('字段排序保存失败，已恢复原顺序')
      message.error('字段排序保存失败，已恢复原顺序')
    },
    onSuccess: (configuration) => {
      queryClient.setQueryData(configurationKey, {
        ...configuration,
        items: statusFilter
          ? configuration.items.filter((field) => field.status === statusFilter)
          : configuration.items,
      })
      for (const field of configuration.items) {
        queryClient.setQueryData(workItemFieldKeys.detail(space.id, typeId, field.id), field)
      }
      setDragStatus('字段顺序已保存')
      message.success('字段顺序已保存')
    },
    onSettled: async () => {
      reorderBusyRef.current = false
      await refresh(selected?.id)
    },
  })

  const openCreate = () => {
    const first = catalogQuery.data?.items[0]
    createForm.setFieldsValue({
      fieldKey: '',
      name: '',
      description: '',
      fieldType: first?.key ?? 'text',
    })
    setCreateOpen(true)
  }
  const openEdit = () => {
    if (!selected) return
    setConflict(null)
    editForm.setFieldsValue({ name: selected.name, description: selected.description })
    setEditOpen(true)
  }
  const fieldReorderModeEnabled = sort === 'configured'
    && search.trim() === ''
    && fieldType === 'all'

  const commitFieldMove = (sourceId: string, targetId: string, placeAfter: boolean) => {
    if (
      sourceId === targetId
      || !fieldReorderModeEnabled
      || reorderBusyRef.current
      || reorderMutation.isPending
    ) return
    const source = rawFields.find((field) => field.id === sourceId)
    const target = rawFields.find((field) => field.id === targetId)
    if (!source || !target || source.status !== target.status) return
    if (!source.availableActions.includes('reorder') || !target.availableActions.includes('reorder')) return

    const sameStatus = rawFields
      .filter((field) => field.status === source.status)
      .sort((left, right) => left.sortOrder - right.sortOrder || left.fieldKey.localeCompare(right.fieldKey) || left.id.localeCompare(right.id))
    const reordered = sameStatus.filter((field) => field.id !== source.id)
    const targetIndex = reordered.findIndex((field) => field.id === target.id)
    if (targetIndex < 0) return
    reordered.splice(targetIndex + (placeAfter ? 1 : 0), 0, source)

    const currentSortOrders = sameStatus.map((field) => field.sortOrder).sort((left, right) => left - right)
    const sortOrders = new Set(currentSortOrders).size === currentSortOrders.length
      ? currentSortOrders
      : sameStatus.map((_, index) => (index + 1) * 10)
    const entries = reordered
      .map((field, index) => ({
        fieldId: field.id,
        sortOrder: sortOrders[index],
        aggregateVersion: field.aggregateVersion,
      }))
      .filter((entry) => rawFields.find((field) => field.id === entry.fieldId)?.sortOrder !== entry.sortOrder)
    if (entries.length > 0) {
      reorderBusyRef.current = true
      setDragStatus('正在保存字段顺序')
      reorderMutation.mutate(entries)
    }
  }

  const clearFieldDrag = () => {
    draggedFieldIdRef.current = undefined
    setDraggedFieldId(undefined)
    setDragTargetFieldId(undefined)
  }

  const cancelFieldDrag = () => {
    if (draggedFieldIdRef.current) setDragStatus('已取消字段排序')
    clearFieldDrag()
  }

  const resolveFieldPointerTarget = (clientX: number, clientY: number) => {
    if (!fieldReorderModeEnabled) return undefined
    const row = document.elementFromPoint(clientX, clientY)?.closest<HTMLElement>('[data-field-id]')
    const target = rawFields.find((field) => field.id === row?.dataset.fieldId)
    const source = rawFields.find((field) => field.id === draggedFieldIdRef.current)
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

  const reorderFieldWithKeyboard = (
    event: ReactKeyboardEvent<HTMLButtonElement>,
    field: ConfiguredWorkItemField,
  ) => {
    if (event.key !== 'ArrowUp' && event.key !== 'ArrowDown') return
    event.preventDefault()
    if (!fieldReorderModeEnabled) {
      setDragStatus('请先选择配置顺序，并清除搜索和字段类型筛选')
      return
    }
    const sameStatus = rawFields
      .filter((item) => item.status === field.status)
      .sort((left, right) => left.sortOrder - right.sortOrder || left.fieldKey.localeCompare(right.fieldKey) || left.id.localeCompare(right.id))
    const currentIndex = sameStatus.findIndex((item) => item.id === field.id)
    const direction = event.key === 'ArrowUp' ? -1 : 1
    const target = sameStatus[currentIndex + direction]
    if (target) commitFieldMove(field.id, target.id, direction > 0)
  }
  const confirmTransition = (action: 'disable' | 'restore' | 'retire') => {
    const labels = { disable: '停用', restore: '恢复', retire: '退役' }
    modal.confirm({
      title: `确认${labels[action]}“${selected?.name}”？`,
      content: action === 'retire'
        ? '退役不可恢复。字段定义会保留用于历史兼容，但不能继续编辑或配置。'
        : action === 'disable' ? '停用后可恢复，字段不会参与后续布局配置。' : '恢复后字段重新进入可配置状态。',
      okText: `确认${labels[action]}`,
      okButtonProps: action === 'restore' ? {} : { danger: true },
      onOk: () => transitionMutation.mutateAsync(action),
    })
  }

  return (
    <section className="work-item-field-panel" data-testid="work-item-fields-panel">
      <header
        className="content-card work-item-model-section-header work-item-field-header-card"
        data-testid="work-item-model-section-header"
      >
        <div className="work-item-field-header">
          <div className="work-item-field-header-copy">
            {!embedded && onBack ? <Button type="text" icon={<ArrowLeftOutlined />} onClick={onBack}>返回类型</Button> : null}
            <Typography.Title level={4}>字段配置</Typography.Title>
            <Typography.Text className="work-item-field-type-context" type="secondary">
              {typeQuery.data ? `${typeQuery.data.name} · ${typeQuery.data.typeKey}` : '正在读取工作项类型'}
            </Typography.Text>
          </div>
          <div className="work-item-field-filters" role="group" aria-label="字段目录筛选">
            {fieldsQuery.data?.availableActions.includes('create') ? (
              <Button className="work-item-field-create-button" type="primary" icon={<PlusOutlined />} disabled={!catalogQuery.data} onClick={openCreate}>新建字段</Button>
            ) : null}
            <Select
              aria-label="字段类型筛选"
              value={fieldType}
              onChange={setFieldType}
              options={[
                { value: 'all', label: '全部类型' },
                ...(catalogQuery.data?.items.map((item) => ({ value: item.key, label: fieldTypeLabel(item.key) })) ?? []),
              ]}
            />
            <Select
              aria-label="字段排序方式"
              value={sort}
              onChange={setSort}
              options={[
                { value: 'configured', label: '配置顺序' },
                { value: 'name', label: '名称' },
                { value: 'updated', label: '最近更新' },
              ]}
            />
          </div>
        </div>
      </header>

      {fieldsQuery.isError || catalogQuery.isError || typeQuery.isError ? (
        <Alert
          type="error"
          showIcon
          message="字段配置加载失败"
          description={fieldErrorDescription(fieldsQuery.error ?? catalogQuery.error ?? typeQuery.error)}
          action={<Button onClick={() => void Promise.all([fieldsQuery.refetch(), catalogQuery.refetch(), typeQuery.refetch()])}>重试</Button>}
        />
      ) : null}

      <div className="work-item-field-layout">
        <Card className="content-card work-item-field-list-card" aria-label="字段列表">
          <Segmented<FieldFilterStatus>
            className="work-item-field-list-status-filter"
            aria-label="字段状态筛选"
            value={status}
            onChange={setStatus}
            options={[
              { label: '全', value: 'all' },
              { label: '使用中', value: 'active' },
              { label: '已停', value: 'disabled' },
              { label: '已退', value: 'retired' },
            ]}
          />
          <Input.Search
            className="work-item-field-list-search"
            allowClear
            value={search}
            aria-label="搜索字段名称或字段键"
            placeholder="搜索字段名称或字段键"
            onChange={(event) => setSearch(event.target.value)}
          />
          {fieldsQuery.isLoading || catalogQuery.isLoading ? <Skeleton active paragraph={{ rows: 7 }} /> : null}
          {!fieldsQuery.isLoading && fields.length === 0 ? (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={rawFields.length === 0 ? '当前类型还没有字段' : '没有符合筛选条件的字段'}
            >
              {rawFields.length === 0 && fieldsQuery.data?.availableActions.includes('create')
                ? <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>创建第一个字段</Button>
                : null}
            </Empty>
          ) : null}
          <span className="visually-hidden" role="status" aria-live="polite">{dragStatus}</span>
          <div
            className="work-item-field-list"
            role="listbox"
            aria-label="选择字段"
            onPointerCancel={cancelFieldDrag}
            onPointerLeave={cancelFieldDrag}
            onPointerMove={(event) => {
              if (!draggedFieldIdRef.current) return
              setDragTargetFieldId(resolveFieldPointerTarget(event.clientX, event.clientY)?.target.id)
            }}
            onPointerUp={(event) => {
              const wasDragging = Boolean(draggedFieldIdRef.current)
              const resolved = resolveFieldPointerTarget(event.clientX, event.clientY)
              if (resolved) {
                const bounds = resolved.row.getBoundingClientRect()
                commitFieldMove(
                  resolved.source.id,
                  resolved.target.id,
                  event.clientY >= bounds.top + bounds.height / 2,
                )
              } else if (wasDragging) {
                setDragStatus('未调整字段顺序')
              }
              clearFieldDrag()
            }}
          >
            {fields.map((field) => {
              const canReorder = field.availableActions.includes('reorder')
              const reorderDisabled = !fieldReorderModeEnabled
                || reorderMutation.isPending
              return (
                <div
                  className={[
                    'work-item-field-list-item',
                    field.id === selectedFieldId ? 'active' : '',
                    draggedFieldId === field.id ? 'dragging' : '',
                    dragTargetFieldId === field.id ? 'drag-over' : '',
                  ].filter(Boolean).join(' ')}
                  data-field-id={field.id}
                  key={field.id}
                >
                  <button
                    type="button"
                    role="option"
                    aria-selected={field.id === selectedFieldId}
                    className="work-item-field-select"
                    onClick={() => onSelectField(field.id)}
                  >
                    <span className="work-item-field-list-copy">
                      <strong>{field.name}</strong>
                      <small>{field.fieldKey}</small>
                    </span>
                    <span className="work-item-field-list-meta">
                      <FieldStatusDot status={field.status} />
                      <small>{fieldTypeLabel(field.fieldType)}</small>
                    </span>
                  </button>
                  {canReorder ? (
                    <Tooltip title={fieldReorderModeEnabled ? '拖拽调整顺序；聚焦后可使用上下方向键' : '选择配置顺序，并清除搜索和字段类型筛选后可调整顺序'}>
                      <button
                        type="button"
                        className="work-item-field-drag-handle"
                        aria-label={`拖拽排序 ${field.name}`}
                        aria-keyshortcuts="ArrowUp ArrowDown"
                        aria-disabled={reorderDisabled}
                        onPointerDown={(event) => {
                          event.preventDefault()
                          event.currentTarget.focus()
                          if (!fieldReorderModeEnabled) {
                            setDragStatus('请先选择配置顺序，并清除搜索和字段类型筛选')
                            return
                          }
                          if (
                            !event.isPrimary
                            || event.button !== 0
                            || reorderBusyRef.current
                            || reorderMutation.isPending
                          ) return
                          event.currentTarget.setPointerCapture(event.pointerId)
                          draggedFieldIdRef.current = field.id
                          setDraggedFieldId(field.id)
                          setDragStatus(`正在调整${field.name}的顺序`)
                        }}
                        onKeyDown={(event) => reorderFieldWithKeyboard(event, field)}
                      >
                        <HolderOutlined />
                      </button>
                    </Tooltip>
                  ) : null}
                </div>
              )
            })}
          </div>
        </Card>

        <Card className="content-card work-item-field-detail-card">
          {selectedFieldId && detailQuery.isLoading ? <Skeleton active /> : null}
          {detailQuery.isError ? (
            <Empty description="字段不存在或当前账号不可访问">
              <Button onClick={() => onSelectField(undefined)}>返回字段列表</Button>
            </Empty>
          ) : null}
          {!selectedFieldId && !fieldsQuery.isLoading ? <Empty description="请选择一个字段" /> : null}
          {selected ? (
            <div className="work-item-field-detail">
              <div className="work-item-field-detail-header">
                <div className="work-item-field-title-block">
                  <div>
                    <Space wrap size={7}>
                      <Typography.Title level={3}>{selected.name}</Typography.Title>
                      {selected.system ? <Tag color="blue">系统字段</Tag> : <Tag>自定义字段</Tag>}
                    </Space>
                  </div>
                </div>
                <Space wrap>
                  {selected.availableActions.includes('configure') ? <Button type="primary" icon={<SettingOutlined />} onClick={() => setConfigOpen(true)}>配置</Button> : null}
                  {selected.availableActions.includes('edit') ? <Button icon={<EditOutlined />} onClick={openEdit}>编辑</Button> : null}
                  {selected.availableActions.includes('restore') ? <Button className="work-item-field-restore" icon={<ReloadOutlined />} onClick={() => confirmTransition('restore')}>恢复</Button> : null}
                  {selected.availableActions.includes('disable') ? <Button danger icon={<StopOutlined />} onClick={() => confirmTransition('disable')}>停用</Button> : null}
                  {selected.availableActions.includes('retire') ? <Button danger icon={<FileAddOutlined />} onClick={() => confirmTransition('retire')}>退役</Button> : null}
                </Space>
              </div>
              <Typography.Paragraph type={selected.description ? undefined : 'secondary'}>
                {selected.description || '暂无字段说明'}
              </Typography.Paragraph>
              {selected.system ? (
                <Alert showIcon type="info" message="系统字段受保护" description="系统字段可参与排序和生命周期管理，但不能修改定义或类型配置。" />
              ) : null}
              <div className="work-item-field-facts">
                <div><span>字段类型</span><strong>{fieldTypeLabel(selected.fieldType)}</strong></div>
                <div><span>存储语义</span><strong>{descriptor?.storageKind ?? '-'}</strong></div>
                <div><span>排序</span><strong>{selected.sortOrder}</strong></div>
                <div><span>聚合版本</span><strong>{selected.aggregateVersion}</strong></div>
                <div><span>更新时间</span><strong>{formatTime(selected.updatedAt)}</strong></div>
                <div><span>配置哈希</span><strong>{selected.configHash.slice(0, 12)}…</strong></div>
              </div>
              <FieldConfigurationSummary field={selected} descriptor={descriptor} />
            </div>
          ) : null}
        </Card>
      </div>

      <Modal
        title="新建字段"
        open={createOpen}
        okText="创建字段"
        cancelText="取消"
        width={760}
        confirmLoading={createMutation.isPending}
        onCancel={() => setCreateOpen(false)}
        onOk={() => createForm.submit()}
        destroyOnHidden
      >
        <Form<FieldDraft> form={createForm} layout="vertical" onFinish={(values) => createMutation.mutate(values)}>
          <Form.Item name="fieldType" label="字段类型" rules={[{ required: true }]}>
            <FieldTypePicker descriptors={catalogQuery.data?.items ?? []} />
          </Form.Item>
          {createDescriptor ? (
            <Alert
              className="work-item-field-create-capability"
              type="info"
              showIcon
              message={`${fieldTypeLabel(createDescriptor.key)} · ${createDescriptor.storageKind}`}
              description={`筛选 ${yesNo(createDescriptor.filterable)}，排序 ${yesNo(createDescriptor.sortable)}，索引能力 ${createDescriptor.indexCapability}`}
            />
          ) : null}
          <div className="work-item-field-create-grid">
            <Form.Item
              name="fieldKey"
              label="字段键"
              extra="类型内永久唯一，创建后不可修改。"
              rules={[
                { required: true, whitespace: true },
                { pattern: /^[a-z][a-z0-9_]*$/, message: '以小写字母开头，仅支持小写字母、数字和下划线' },
              ]}
            >
              <Input autoFocus maxLength={64} placeholder="例如：campaign_owner" />
            </Form.Item>
            <Form.Item name="name" label="显示名称" rules={[{ required: true, whitespace: true }, { max: 128 }]}>
              <Input maxLength={128} placeholder="例如：活动负责人" />
            </Form.Item>
          </div>
          <Form.Item name="description" label="字段说明" rules={[{ max: 2000 }]}>
            <Input.TextArea rows={3} maxLength={2000} showCount />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="编辑字段"
        open={editOpen}
        okText="保存"
        cancelText="取消"
        confirmLoading={editMutation.isPending}
        onCancel={() => setEditOpen(false)}
        onOk={() => editForm.submit()}
        destroyOnHidden
      >
        <Form<FieldEdit> form={editForm} layout="vertical" onFinish={(values) => editMutation.mutate(values)}>
          {conflict ? <Alert className="work-item-field-conflict" type="error" showIcon message={conflict} /> : null}
          <Form.Item label="字段键"><Input value={selected?.fieldKey} disabled /></Form.Item>
          <Form.Item name="name" label="显示名称" rules={[{ required: true, whitespace: true }, { max: 128 }]}><Input autoFocus /></Form.Item>
          <Form.Item name="description" label="字段说明" rules={[{ max: 2000 }]}><Input.TextArea rows={4} /></Form.Item>
        </Form>
      </Modal>

      <WorkItemFieldConfigDrawer
        open={configOpen}
        field={selected}
        descriptor={descriptor}
        workItemTypes={allTypesQuery.data?.items ?? []}
        saving={configureMutation.isPending}
        onClose={() => setConfigOpen(false)}
        onSave={(request) => configureMutation.mutate(request)}
      />
    </section>
  )
}

function FieldTypePicker({
  value,
  onChange,
  descriptors,
}: {
  value?: WorkItemFieldType
  onChange?: (value: WorkItemFieldType) => void
  descriptors: WorkItemFieldTypeDescriptor[]
}) {
  return (
    <div className="work-item-field-type-picker" role="radiogroup" aria-label="字段类型">
      {descriptors.map((descriptor) => (
        <button
          key={descriptor.key}
          type="button"
          role="radio"
          aria-checked={value === descriptor.key}
          className={`work-item-field-type-option${value === descriptor.key ? ' active' : ''}`}
          onClick={() => onChange?.(descriptor.key)}
        >
          <FieldGlyph type={descriptor.key} />
          <span><strong>{fieldTypeLabel(descriptor.key)}</strong><small>{descriptor.storageKind}</small></span>
        </button>
      ))}
    </div>
  )
}

function FieldConfigurationSummary({
  field,
  descriptor,
}: {
  field: ConfiguredWorkItemField
  descriptor?: WorkItemFieldTypeDescriptor
}) {
  return (
    <section className="work-item-field-summary" aria-label="字段配置摘要">
      <div className="work-item-field-summary-heading">
        <div><SettingOutlined /><strong>配置摘要</strong></div>
        <Tag color={field.config.required ? 'purple' : 'default'}>{field.config.required ? '必填' : '可选'}</Tag>
      </div>
      <div className="work-item-field-summary-grid">
        <div><span>默认值</span><strong>{summarizeDefault(field.config.defaultValue)}</strong></div>
        <div><span>规则</span><strong>{field.config.validationRules.length} 条</strong></div>
        <div><span>选项</span><strong>{field.options.length} 项</strong></div>
        <div><span>操作符</span><strong>{descriptor?.operators.length ?? 0} 个</strong></div>
      </div>
      {descriptor ? (
        <Space wrap>
          {descriptor.operators.map((operator) => <Tag key={operator}>{operator}</Tag>)}
        </Space>
      ) : null}
    </section>
  )
}

function FieldGlyph({ type, large = false }: { type: WorkItemFieldType; large?: boolean }) {
  const icons: Record<WorkItemFieldType, React.ReactNode> = {
    text: <FileOutlined />,
    number: <NumberOutlined />,
    boolean: <CheckSquareOutlined />,
    single_select: <UnorderedListOutlined />,
    multi_select: <UnorderedListOutlined />,
    user: <TeamOutlined />,
    date: <CalendarOutlined />,
    datetime: <ClockCircleOutlined />,
    url: <LinkOutlined />,
    attachment: <PaperClipOutlined />,
    work_item_reference: <RetweetOutlined />,
  }
  return <span className={`work-item-field-glyph${large ? ' large' : ''}`} aria-hidden="true">{icons[type]}</span>
}

function FieldStatusDot({ status }: { status: WorkItemFieldStatus }) {
  const label = status === 'active' ? '启用' : status === 'disabled' ? '停用' : '已退役'
  return (
    <Tooltip title={label}>
      <span
        className={`work-item-field-status-dot ${status}`}
        role="img"
        aria-label={label}
      />
    </Tooltip>
  )
}

function projectFields(
  items: ConfiguredWorkItemField[],
  search: string,
  fieldType: 'all' | WorkItemFieldType,
  sort: FieldSort,
) {
  const keyword = search.trim().toLocaleLowerCase()
  const projected = items.filter((field) => {
    if (fieldType !== 'all' && field.fieldType !== fieldType) return false
    return !keyword || `${field.name} ${field.fieldKey}`.toLocaleLowerCase().includes(keyword)
  })
  return [...projected].sort((left, right) => {
    if (sort === 'name') return left.name.localeCompare(right.name) || left.fieldKey.localeCompare(right.fieldKey)
    if (sort === 'updated') return new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime() || left.id.localeCompare(right.id)
    return left.sortOrder - right.sortOrder || left.fieldKey.localeCompare(right.fieldKey) || left.id.localeCompare(right.id)
  })
}

function fieldTypeLabel(type: WorkItemFieldType) {
  const labels: Record<WorkItemFieldType, string> = {
    text: '文本',
    number: '数字',
    boolean: '布尔',
    single_select: '单选',
    multi_select: '多选',
    user: '人员',
    date: '日期',
    datetime: '日期时间',
    url: '链接',
    attachment: '附件',
    work_item_reference: '工作项引用',
  }
  return labels[type]
}

type ReorderEntry = { fieldId: string; sortOrder: number; aggregateVersion: number }

function applyOptimisticOrder(items: ConfiguredWorkItemField[], entries: ReorderEntry[]) {
  const sortOrders = new Map(entries.map((entry) => [entry.fieldId, entry.sortOrder]))
  return items
    .map((field) => sortOrders.has(field.id) ? { ...field, sortOrder: sortOrders.get(field.id) as number } : field)
    .sort((left, right) => left.sortOrder - right.sortOrder || left.fieldKey.localeCompare(right.fieldKey))
}

function nextSortOrder(items: ConfiguredWorkItemField[]) {
  return items.reduce((maximum, field) => Math.max(maximum, field.sortOrder), 0) + 10
}

function isVersionConflict(error: unknown) {
  return error instanceof ApiRequestError
    && error.status === 409
    && (error.code === 'FIELD_VERSION_CONFLICT' || /version|版本|changed by another request/i.test(error.message))
}

function showFieldError(
  message: ReturnType<typeof AntdApp.useApp>['message'],
  error: unknown,
  fallback: string,
) {
  if (isVersionConflict(error)) {
    message.error('字段已被其他人更新，已刷新最新版本，请重新操作。')
    return
  }
  message.error(fieldErrorDescription(error, fallback))
}

function fieldErrorDescription(error: unknown, fallback = '请稍后重试') {
  if (error instanceof ApiRequestError) {
    const messages: Record<string, string> = {
      FIELD_KEY_CONFLICT: '字段键已存在，请使用新的永久键。',
      INVALID_FIELD_CONFIG: '字段配置不符合类型约束，请检查默认值和规则。',
      INVALID_FIELD_OPTION: '选项配置无效，请检查键、状态和排序。',
      INVALID_FIELD_RULE: '校验规则冲突或超出允许范围。',
      FIELD_VERSION_CONFLICT: '字段已被其他人更新，请刷新后重试。',
      SYSTEM_FIELD_PROTECTED: '系统字段受保护，不能执行此操作。',
      RETIRED_FIELD: '已退役字段不能继续编辑。',
      SPACE_UNAVAILABLE: '空间当前为只读状态，不能修改字段。',
      NOT_FOUND_OR_HIDDEN: '字段、类型或空间不存在，或当前账号无权访问。',
      FORBIDDEN: '当前角色无权配置字段。',
    }
    if (error.code && messages[error.code]) return messages[error.code]
  }
  return errorMessage(error, fallback)
}

function summarizeDefault(value: unknown) {
  if (value === null || value === undefined || value === '') return '未设置'
  if (Array.isArray(value)) return value.length === 0 ? '未设置' : `${value.length} 项`
  if (typeof value === 'boolean') return value ? '是' : '否'
  return String(value)
}

function yesNo(value: boolean) {
  return value ? '支持' : '不支持'
}
