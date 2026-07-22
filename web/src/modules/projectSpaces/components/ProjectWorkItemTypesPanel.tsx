import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  CopyOutlined,
  EditOutlined,
  FileTextOutlined,
  InboxOutlined,
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
  Tooltip,
  Typography,
} from 'antd'
import { useEffect, useMemo, useState } from 'react'

import { ApiRequestError } from '../../../shared/api/httpClient'
import { StatusBadge } from '../../../shared/components/StatusBadge'
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
import { errorMessage, formatTime } from '../projectSpaceView'

type FilterStatus = 'all' | WorkItemTypeStatus
type TypeForm = WorkItemTypeDraft
const VERSION_CONFLICT_MESSAGE = '数据已被其他人更新，已刷新为最新版本，请检查当前输入后重新保存。'

export function ProjectWorkItemTypesPanel({
  space,
  selectedTypeId,
  onSelectType,
}: {
  space: UserProjectSpace
  selectedTypeId?: string
  onSelectType: (typeId: string) => void
}) {
  const { message, modal } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<FilterStatus>('all')
  const [editorMode, setEditorMode] = useState<'create' | 'edit' | 'copy' | null>(null)
  const [editorConflict, setEditorConflict] = useState<string | null>(null)
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
    if (!configurationQuery.isLoading && items.length > 0 && !selectedTypeId) {
      onSelectType(items[0].id)
    }
  }, [configurationQuery.isLoading, items, onSelectType, selectedTypeId])

  const refresh = async (typeId?: string) => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: [...workItemTypeKeys.all, space.id] }),
      queryClient.invalidateQueries({ queryKey: workItemTypeKeys.active(space.id) }),
      typeId ? queryClient.invalidateQueries({ queryKey: workItemTypeKeys.detail(space.id, typeId) }) : Promise.resolve(),
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
      message.error('排序保存失败，已恢复原顺序')
    },
    onSuccess: (configuration) => {
      queryClient.setQueryData(configurationKey, configuration)
      for (const item of configuration.items) {
        queryClient.setQueryData(workItemTypeKeys.detail(space.id, item.id), item)
      }
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

  const moveType = (type: ConfiguredWorkItemType, direction: -1 | 1) => {
    const sameStatus = items.filter((item) => item.status === type.status)
    const currentIndex = sameStatus.findIndex((item) => item.id === type.id)
    const adjacent = sameStatus[currentIndex + direction]
    if (!adjacent) return
    reorderMutation.mutate({
      entries: [
        { typeId: type.id, sortOrder: adjacent.sortOrder, aggregateVersion: type.aggregateVersion },
        { typeId: adjacent.id, sortOrder: type.sortOrder, aggregateVersion: adjacent.aggregateVersion },
      ],
    })
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
          {configurationQuery.data?.availableActions.includes('create') ? (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => openEditor('create')}>新建类型</Button>
          ) : null}
        </div>
        <Segmented<FilterStatus>
          aria-label="工作项类型状态筛选"
          value={status}
          onChange={setStatus}
          options={[
            { label: '全部', value: 'all' },
            { label: '使用中', value: 'active' },
            { label: '已停用', value: 'disabled' },
            { label: '已退役', value: 'retired' },
          ]}
        />
      </Card>

      {configurationQuery.isError ? (
        <Alert type="error" showIcon message="工作项类型加载失败" description={errorMessage(configurationQuery.error, '请稍后重试')} action={<Button onClick={() => configurationQuery.refetch()}>重试</Button>} />
      ) : null}

      <div className="work-item-type-layout">
        <Card className="content-card work-item-type-list-card" aria-label="工作项类型列表">
          {configurationQuery.isLoading ? <Skeleton active paragraph={{ rows: 5 }} /> : null}
          {!configurationQuery.isLoading && items.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前筛选下暂无工作项类型" />
          ) : null}
          <div className="work-item-type-list" role="listbox" aria-label="选择工作项类型">
            {items.map((type) => {
              const sameStatus = items.filter((item) => item.status === type.status)
              const typeIndex = sameStatus.findIndex((item) => item.id === type.id)
              const canReorder = type.availableActions.includes('reorder') && !reorderMutation.isPending
              return (
                <div className={`work-item-type-list-item${selectedTypeId === type.id ? ' active' : ''}`} key={type.id}>
                  <button
                    type="button"
                    role="option"
                    aria-selected={selectedTypeId === type.id}
                    className="work-item-type-select"
                    onClick={() => onSelectType(type.id)}
                  >
                    <TypeGlyph type={type} />
                    <span className="work-item-type-list-copy">
                      <strong>{type.name}</strong>
                      <small>{type.typeKey}</small>
                    </span>
                    <span className="work-item-type-list-state"><StatusBadge status={type.status} /></span>
                  </button>
                  {canReorder ? (
                    <span className="work-item-type-order-actions">
                      <Tooltip title="上移"><Button aria-label={`上移 ${type.name}`} size="small" type="text" icon={<ArrowUpOutlined />} disabled={typeIndex === 0} onClick={() => moveType(type, -1)} /></Tooltip>
                      <Tooltip title="下移"><Button aria-label={`下移 ${type.name}`} size="small" type="text" icon={<ArrowDownOutlined />} disabled={typeIndex === sameStatus.length - 1} onClick={() => moveType(type, 1)} /></Tooltip>
                    </span>
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
            <div className="work-item-type-detail">
              <div className="work-item-type-detail-header">
                <div className="work-item-type-title-block">
                  <TypeGlyph type={selected} large />
                  <div>
                    <Space wrap size={7}>
                      <Typography.Title level={3}>{selected.name}</Typography.Title>
                      <StatusBadge status={selected.status} />
                      {selected.system ? <Tag color="blue">系统类型</Tag> : <Tag>自定义</Tag>}
                    </Space>
                    <Typography.Text code>{selected.typeKey}</Typography.Text>
                  </div>
                </div>
                <Space wrap>
                  {selected.availableActions.includes('edit') ? <Button icon={<EditOutlined />} onClick={() => openEditor('edit')}>编辑</Button> : null}
                  {selected.availableActions.includes('copy') ? <Button icon={<CopyOutlined />} onClick={() => openEditor('copy')}>复制</Button> : null}
                  {selected.availableActions.includes('restore') ? <Button className="work-item-type-restore" icon={<ReloadOutlined />} onClick={() => confirmTransition('restore')}>恢复</Button> : null}
                  {selected.availableActions.includes('disable') ? <Button danger icon={<StopOutlined />} onClick={() => confirmTransition('disable')}>停用</Button> : null}
                  {selected.availableActions.includes('retire') ? <Button danger icon={<InboxOutlined />} onClick={() => confirmTransition('retire')}>退役</Button> : null}
                </Space>
              </div>
              <Typography.Paragraph className="work-item-type-description" type={selected.description ? undefined : 'secondary'}>
                {selected.description || '暂无类型说明'}
              </Typography.Paragraph>
              {selected.system ? (
                <Alert
                  type="info"
                  showIcon
                  message="系统预置类型受保护"
                  description={`来源：研发预置目录 ${selected.presetCatalogVersion ?? ''}。可调整顺序、复制或停用，但不能修改类型标识、展示定义或执行退役。`}
                />
              ) : null}
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

function TypeGlyph({ type, large = false }: { type: Pick<ConfiguredWorkItemType, 'name' | 'icon'>; large?: boolean }) {
  const label = type.icon?.trim() || type.name.trim().slice(0, 1).toUpperCase() || 'T'
  return <span className={`work-item-type-glyph${large ? ' large' : ''}`} aria-hidden="true">{label.slice(0, 2)}</span>
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
