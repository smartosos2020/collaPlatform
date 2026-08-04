import {
  ArrowDownOutlined,
  ArrowLeftOutlined,
  ArrowUpOutlined,
  BranchesOutlined,
  CopyOutlined,
  DeleteOutlined,
  EyeOutlined,
  FormOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SaveOutlined,
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
  Select,
  Skeleton,
  Space,
  Switch,
  Tag,
  Typography,
} from 'antd'
import { useMemo, useState, type ReactNode } from 'react'
import { useLocation } from 'react-router-dom'

import { ApiRequestError } from '../../../shared/api/httpClient'
import type { UserProjectSpace } from '../api/projectSpacesApi'
import { workItemConfigurationDraftKeys } from '../api/workItemConfigurationApi'
import {
  createWorkItemField,
  type ConfiguredWorkItemField,
} from '../api/workItemFieldsApi'
import {
  commandWorkItemLayoutNode,
  getWorkItemLayoutWorkbench,
  previewWorkItemLayout,
  saveWorkItemLayoutPolicies,
  saveWorkItemLayout,
  workItemLayoutKeys,
  type WorkItemFieldAccessMode,
  type WorkItemFieldAccessPolicy,
  type WorkItemFieldAccessPolicyDocument,
  type WorkItemFieldAccessRole,
  type WorkItemLayoutKind,
  type WorkItemLayoutNode,
  type WorkItemLayoutNodeCommand,
  type WorkItemLayoutNodeType,
} from '../api/workItemLayoutsApi'
import {
  applyFieldControlPreset,
  availableFieldControlPresets,
  fieldControlLabel,
  fieldControlPreset,
  type WorkItemFieldControlPresetKey,
} from '../workItemFieldControls'
import { ProjectSpaceSecondaryTabs } from './ProjectSpaceSecondaryTabs'
import { WorkItemLayoutRenderer } from './WorkItemLayoutRenderer'

type QuickFieldControlDraft = {
  fieldKey: string
  name: string
}

export function ProjectWorkItemLayoutsPanel({
  space,
  typeId,
  onBack,
  configurationDraft,
  embedded = false,
}: {
  space: UserProjectSpace
  typeId: string
  onBack?: () => void
  configurationDraft?: ReactNode
  embedded?: boolean
}) {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const location = useLocation()
  const requestedKind = new URLSearchParams(location.search).get('layoutKind')
  const [kind, setKind] = useState<WorkItemLayoutKind>(
    requestedKind === 'detail' ? 'detail' : 'create',
  )
  const [selectedId, setSelectedId] = useState<string>()
  const [pendingCommand, setPendingCommand] = useState<WorkItemLayoutNodeCommand>()
  const [draggedId, setDraggedId] = useState<string>()
  const [policyState, setPolicyState] = useState<{
    layoutKey: string
    drafts: Record<string, FieldPolicyDraft>
    dirty: boolean
  }>({ layoutKey: '', drafts: {}, dirty: false })
  const [policyFieldId, setPolicyFieldId] = useState<string>()
  const [previewRole, setPreviewRole] = useState<WorkItemFieldAccessRole>('member')
  const [previewSpaceStatus, setPreviewSpaceStatus] = useState<'active' | 'disabled' | 'archived'>('active')
  const [previewTypeStatus, setPreviewTypeStatus] = useState<'active' | 'disabled' | 'retired'>('active')
  const [previewFieldStatus, setPreviewFieldStatus] = useState<'active' | 'disabled' | 'retired'>('active')
  const [previewValues, setPreviewValues] = useState('{}')
  const [quickControlKey, setQuickControlKey] = useState<WorkItemFieldControlPresetKey>()
  const [quickControlForm] = Form.useForm<QuickFieldControlDraft>()
  const workbenchQuery = useQuery({
    queryKey: workItemLayoutKeys.workbench(space.id, typeId),
    queryFn: () => getWorkItemLayoutWorkbench(space.id, typeId),
    retry: false,
    refetchOnWindowFocus: false,
  })
  const fields = useMemo(
    () => workbenchQuery.data?.fields.items ?? [],
    [workbenchQuery.data?.fields.items],
  )
  const fieldTypes = useMemo(
    () => workbenchQuery.data?.fieldTypes.items ?? [],
    [workbenchQuery.data?.fieldTypes.items],
  )
  const activeFields = useMemo(
    () => fields.filter((field) => field.status === 'active'),
    [fields],
  )
  const controlPresets = useMemo(
    () => availableFieldControlPresets(fieldTypes.map((item) => item.key)),
    [fieldTypes],
  )
  const commonControlPresets = useMemo(
    () => controlPresets.filter((item) => item.common),
    [controlPresets],
  )
  const moreControlPresets = useMemo(
    () => controlPresets.filter((item) => !item.common),
    [controlPresets],
  )
  const quickControl = fieldControlPreset(quickControlKey)
  const canCreateField = workbenchQuery.data?.fields.availableActions.includes('create') ?? false
  const editorAccessProjection = useMemo(
    () => Object.fromEntries(fields.map((field) => [field.fieldKey, {
      mode: 'write' as const,
      required: false,
      reasonCode: 'layout_editor',
    }])),
    [fields],
  )
  const selectedLayout = workbenchQuery.data?.layouts[kind]
  const layout = selectedLayout?.configuration
  const runtimeProjection = selectedLayout?.runtimeProjection
  const requestedNodeKey = new URLSearchParams(location.search).get('layoutNodeKey')
  const requestedFieldKey = new URLSearchParams(location.search).get('layoutFieldKey')
  const diagnosticTargetId = requestedKind === kind
    ? layout?.nodes.find((node) => (
      (requestedNodeKey && node.nodeKey === requestedNodeKey)
      || (requestedFieldKey && node.fieldKey === requestedFieldKey)
    ))?.id
    : undefined
  const requestedSelectedId = selectedId ?? diagnosticTargetId
  const effectiveSelectedId = layout?.nodes.some((item) => item.id === requestedSelectedId)
    ? requestedSelectedId
    : layout?.nodes[0]?.id
  const selected = layout?.nodes.find((node) => node.id === effectiveSelectedId)
  const layoutFields = useMemo(() => {
    const used = new Set(layout?.nodes.map((node) => node.fieldId).filter(Boolean))
    return fields.filter((field) => used.has(field.id))
  }, [fields, layout?.nodes])
  const layoutPolicyKey = layout ? `${layout.id}:${layout.aggregateVersion}:${layout.configHash}` : ''
  const baselinePolicyDrafts = useMemo(
    () => policyDraftMap(layoutFields, layout?.policies ?? []),
    [layout?.policies, layoutFields],
  )
  const effectivePolicyDrafts = policyState.layoutKey === layoutPolicyKey
    ? policyState.drafts
    : baselinePolicyDrafts
  const policyDirty = policyState.layoutKey === layoutPolicyKey && policyState.dirty
  const effectivePolicyFieldId = layoutFields.some((field) => field.id === policyFieldId)
    ? policyFieldId
    : layoutFields[0]?.id
  const selectedPolicyField = layoutFields.find((field) => field.id === effectivePolicyFieldId)
  const selectedPolicyDraft = effectivePolicyFieldId
    ? effectivePolicyDrafts[effectivePolicyFieldId]
    : undefined

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: workItemLayoutKeys.workbench(space.id, typeId) }),
      queryClient.invalidateQueries({ queryKey: workItemConfigurationDraftKeys.detail(space.id, typeId) }),
    ])
  }

  const saveMutation = useMutation({
    mutationFn: (request: Parameters<typeof saveWorkItemLayout>[3]) =>
      saveWorkItemLayout(space.id, typeId, kind, request),
    onSuccess: async (saved) => {
      await refresh()
      setSelectedId(saved.nodes[0]?.id)
      message.success(`${kind === 'create' ? '新建页' : '详情页'}布局已创建`)
    },
    onError: (error) => message.error(layoutError(error, '创建布局失败')),
  })

  const commandMutation = useMutation({
    mutationFn: (command: WorkItemLayoutNodeCommand) =>
      commandWorkItemLayoutNode(space.id, typeId, kind, command),
    onSuccess: async () => {
      await refresh()
      setPendingCommand(undefined)
    },
    onError: (error, command) => {
      if (isVersionConflict(error)) {
        setPendingCommand(command)
      }
      message.error(layoutError(error, '布局命令执行失败'))
    },
  })

  const policyMutation = useMutation({
    mutationFn: (policies: Array<Omit<WorkItemFieldAccessPolicy, 'configHash'>>) => {
      if (!layout) throw new Error('布局尚未加载')
      return saveWorkItemLayoutPolicies(space.id, typeId, kind, {
        policies,
        aggregateVersion: layout.aggregateVersion,
      })
    },
    onSuccess: async () => {
      setPolicyState({ layoutKey: '', drafts: {}, dirty: false })
      await refresh()
      message.success('字段访问策略已保存')
    },
    onError: (error) => message.error(layoutError(error, '字段访问策略保存失败')),
  })

  const previewMutation = useMutation({
    mutationFn: (fieldValues: Record<string, unknown>) => previewWorkItemLayout(
      space.id,
      typeId,
      kind,
      {
        role: previewRole,
        spaceStatus: previewSpaceStatus,
        typeStatus: previewTypeStatus,
        fieldValues,
        fieldStatuses: selectedPolicyField
          ? { [selectedPolicyField.fieldKey]: previewFieldStatus }
          : {},
      },
    ),
    onError: (error) => message.error(layoutError(error, '合成预览失败')),
  })

  const runCommand = (command: Omit<WorkItemLayoutNodeCommand, 'aggregateVersion'>) => {
    if (!layout) return
    if (!navigator.onLine) {
      message.error('当前网络不可用，布局操作未保存')
      return
    }
    commandMutation.mutate({ ...command, aggregateVersion: layout.aggregateVersion })
  }

  const initialize = () => {
    const sectionId = crypto.randomUUID()
    const nodes: WorkItemLayoutNode[] = [
      node(sectionId, null, `${kind}_main`, 'section', 0, { title: kind === 'create' ? '新建工作项' : '工作项详情' }),
      ...activeFields.map((field, index) => fieldNode(field, sectionId, index)),
    ]
    saveMutation.mutate({ nodes, policies: [], aggregateVersion: 0 })
  }

  const addContainer = (nodeType: Exclude<WorkItemLayoutNodeType, 'field'>) => {
    if (!layout) return
    const parentId = allowedParent(nodeType, selected) ? selected?.id ?? null : null
    const id = crypto.randomUUID()
    const newNode = node(
      id,
      parentId,
      `${nodeType}_${id.replaceAll('-', '').slice(0, 10)}`,
      nodeType,
      siblings(layout.nodes, parentId).length,
      nodeType === 'relation'
        ? {
          schemaVersion: 1,
          title: nodeLabel(nodeType),
          relationKey: 'related',
          mode: kind === 'create' ? 'picker' : 'list',
          maxItems: 50,
          showReverse: true,
          collapsedByDefault: false,
          listFallback: true,
          keyboardNavigation: true,
        }
        : { title: nodeLabel(nodeType) },
    )
    runCommand({ operation: 'add', parentId, targetSortOrder: newNode.sortOrder, node: newNode })
    setSelectedId(newNode.id)
  }

  const addField = (field: ConfiguredWorkItemField) => {
    if (!layout || layout.nodes.some((item) => item.fieldId === field.id)) return false
    const parent = selected && ['section', 'tab', 'column'].includes(selected.nodeType)
      ? selected
      : layout.nodes.find((item) => item.nodeType === 'section' && item.parentId === null)
    if (!parent) {
      message.warning('请先添加一个区块')
      return false
    }
    const newNode = fieldNode(field, parent.id, siblings(layout.nodes, parent.id).length)
    runCommand({ operation: 'add', parentId: parent.id, targetSortOrder: newNode.sortOrder, node: newNode })
    setSelectedId(newNode.id)
    return true
  }

  const openQuickControl = (controlKey: WorkItemFieldControlPresetKey) => {
    if (!canCreateField) {
      message.warning('当前没有新建字段权限')
      return
    }
    const control = fieldControlPreset(controlKey)
    if (!control) return
    quickControlForm.setFieldsValue({
      fieldKey: nextAvailableFieldKey(control.key, fields),
      name: control.label,
    })
    setQuickControlKey(controlKey)
  }

  const quickControlMutation = useMutation({
    mutationFn: (values: QuickFieldControlDraft) => {
      const control = fieldControlPreset(quickControlKey)
      const descriptor = fieldTypes.find((item) => item.key === control?.fieldType)
      if (!control || !descriptor) throw new Error('字段控件目录尚未加载')
      return createWorkItemField(space.id, typeId, {
        fieldKey: values.fieldKey.trim(),
        name: values.name.trim(),
        description: '',
        fieldType: control.fieldType,
        config: applyFieldControlPreset(descriptor.defaultConfig, control),
        sortOrder: nextFieldSortOrder(fields),
      })
    },
    onSuccess: async (field) => {
      setQuickControlKey(undefined)
      quickControlForm.resetFields()
      await refresh()
      const added = addField(field)
      message.success(added ? '字段控件已创建，正在加入布局' : '字段控件已创建')
    },
    onError: (error) => message.error(layoutError(error, '创建字段控件失败')),
  })

  const moveSelected = (delta: number) => {
    if (!layout || !selected) return
    const group = siblings(layout.nodes, selected.parentId)
    const index = group.findIndex((item) => item.id === selected.id)
    const target = Math.max(0, Math.min(group.length - 1, index + delta))
    if (target === index) return
    runCommand({ operation: 'move', nodeId: selected.id, parentId: selected.parentId, targetSortOrder: target })
  }

  const dropOn = (target: WorkItemLayoutNode) => {
    if (!layout || !draggedId || draggedId === target.id) return
    runCommand({
      operation: 'move',
      nodeId: draggedId,
      parentId: target.parentId,
      targetSortOrder: target.sortOrder,
    })
    setDraggedId(undefined)
  }

  const removeSelected = () => {
    if (!selected) return
    Modal.confirm({
      title: `删除“${nodeTitle(selected)}”？`,
      content: '包含的子节点和字段引用也会一并移除，此操作将作为一个原子命令保存。',
      okText: '删除',
      okButtonProps: { danger: true },
      onOk: () => runCommand({
        operation: 'delete',
        nodeId: selected.id,
        parentId: selected.parentId,
        targetSortOrder: selected.sortOrder,
        confirmReferences: true,
      }),
    })
  }

  const retryPending = async () => {
    if (!pendingCommand) return
    const refreshed = await workbenchQuery.refetch()
    const latest = refreshed.data?.layouts[kind]?.configuration
    if (latest) {
      if (pendingCommand.nodeId
          && !latest.nodes.some((item) => item.id === pendingCommand.nodeId)) {
        message.error('目标节点已被删除，无法重新应用该操作')
        return
      }
      Modal.confirm({
        title: '在最新布局上重新应用操作？',
        content: `已读取 v${latest.aggregateVersion}（${latest.configHash.slice(0, 8)}）。确认后仅重新提交当前“${pendingCommand.operation}”操作。`,
        okText: '重新应用',
        onOk: () => commandMutation.mutate({
          ...pendingCommand,
          aggregateVersion: latest.aggregateVersion,
        }),
      })
    }
  }

  const requestRefresh = () => {
    if (!policyDirty) {
      void refresh()
      return
    }
    Modal.confirm({
      title: '放弃尚未保存的策略修改？',
      content: '刷新将读取服务端最新策略，当前本地策略草稿不会被提交。',
      okText: '放弃并刷新',
      okButtonProps: { danger: true },
      onOk: async () => {
        setPolicyState({ layoutKey: '', drafts: {}, dirty: false })
        await refresh()
      },
    })
  }

  const updatePolicyDraft = (
    fieldId: string,
    update: (draft: FieldPolicyDraft) => FieldPolicyDraft,
  ) => {
    setPolicyState((current) => {
      const drafts = current.layoutKey === layoutPolicyKey
        ? current.drafts
        : baselinePolicyDrafts
      return {
        layoutKey: layoutPolicyKey,
        drafts: { ...drafts, [fieldId]: update(drafts[fieldId]) },
        dirty: true,
      }
    })
    previewMutation.reset()
  }

  const savePolicies = () => {
    if (!layout || !policyDirty) return
    const policies = layoutFields.map((field) => policyRequest(effectivePolicyDrafts[field.id]))
    const submit = () => policyMutation.mutate(policies)
    if (policies.some((policy) => policyIsRestrictive(policy.policy))) {
      Modal.confirm({
        title: '确认收窄字段访问权限',
        content: '只读或隐藏规则会立即影响对应身份，隐藏字段也不会出现在诊断和投影中。',
        okText: '确认保存',
        onOk: submit,
      })
      return
    }
    submit()
  }

  const runPreview = () => {
    const parsed = parsePreviewValues(previewValues)
    if (!parsed.ok) {
      message.error(parsed.message)
      return
    }
    previewMutation.mutate(parsed.value)
  }

  const loading = workbenchQuery.isLoading
  const missing = !loading && !workbenchQuery.isError && selectedLayout == null
  const renderedProjection = previewMutation.data ?? runtimeProjection
  const parsedPreview = parsePreviewValues(previewValues)
  const projectedFieldKeys = new Set(renderedProjection?.fields.map((field) => field.fieldKey) ?? [])
  const hiddenSampleKeys = parsedPreview.ok
    ? Object.keys(parsedPreview.value).filter((key) => !projectedFieldKeys.has(key))
    : []

  return (
    <section
      className={`work-item-layout-page${embedded ? ' embedded' : ''}`}
      aria-label="页面布局配置"
      data-testid="work-item-layouts-panel"
      tabIndex={0}
      onKeyDown={(event) => {
        if (isEditableTarget(event.target)) return
        if (event.altKey && event.key === 'ArrowUp') { event.preventDefault(); moveSelected(-1) }
        if (event.altKey && event.key === 'ArrowDown') { event.preventDefault(); moveSelected(1) }
        if (event.key === 'Delete' && selected) { event.preventDefault(); removeSelected() }
      }}
    >
      <header
        className="content-card work-item-model-section-header work-item-layout-header"
        data-testid="work-item-model-section-header"
      >
        <div>
          {!embedded && onBack ? <Button type="text" icon={<ArrowLeftOutlined />} onClick={onBack}>返回类型</Button> : null}
          <Typography.Title level={4}>页面布局</Typography.Title>
          <Typography.Text type="secondary">
            {workbenchQuery.data ? `${workbenchQuery.data.type.name} · ${workbenchQuery.data.type.typeKey}` : '正在读取工作项类型'}
          </Typography.Text>
        </div>
        <Space wrap>
          {layout ? <Tag>{`v${layout.aggregateVersion} · ${layout.configHash.slice(0, 8)}`}</Tag> : null}
          <Segmented
            value={kind}
            onChange={(value) => {
              setKind(value as WorkItemLayoutKind)
              setSelectedId(undefined)
              setPendingCommand(undefined)
              previewMutation.reset()
            }}
            options={[{ label: '新建页', value: 'create' }, { label: '详情页', value: 'detail' }]}
          />
          <Button icon={<ReloadOutlined />} onClick={requestRefresh}>刷新</Button>
        </Space>
      </header>

      <ProjectSpaceSecondaryTabs
        view="layouts"
        canManage
        testId="project-space-layouts-secondary-tabs"
        ariaLabel="工作项布局内容导航"
        navigationMode={embedded ? 'local' : 'route'}
        panels={{
          'layout-editor': (
            <>
              {pendingCommand ? (
                <Alert
                  showIcon
                  type="warning"
                  message="布局已被其他人更新"
                  description="本地操作意图已保留。刷新最新版本后可重新提交，不会用整页重载覆盖他人修改。"
                  action={<Button onClick={() => void retryPending()}>刷新并重试</Button>}
                />
              ) : null}
              {layout?.diagnostics.length ? (
                <Alert
                  showIcon
                  type="warning"
                  message={`${layout.diagnostics.length} 项布局诊断`}
                  description={(
                    <div className="work-item-layout-diagnostics" aria-live="polite">
                      {layout.diagnostics.map((diagnostic) => (
                        <div key={`${diagnostic.code}:${diagnostic.nodeKey ?? diagnostic.fieldKey ?? 'layout'}`}>
                          <span>
                            <strong>{diagnostic.nodeKey ?? diagnostic.fieldKey ?? '布局'}</strong>
                            {diagnostic.message || diagnostic.code}
                          </span>
                          {diagnostic.nodeKey ? (
                            <Button
                              size="small"
                              onClick={() => setSelectedId(layout.nodes.find((node) =>
                                node.nodeKey === diagnostic.nodeKey
                                || (diagnostic.fieldKey && node.fieldKey === diagnostic.fieldKey))?.id)}
                            >
                              定位节点
                            </Button>
                          ) : diagnostic.fieldKey ? (
                            <Button
                              size="small"
                              onClick={() => setSelectedId(
                                layout.nodes.find((node) => node.fieldKey === diagnostic.fieldKey)?.id,
                              )}
                            >
                              定位字段
                            </Button>
                          ) : null}
                        </div>
                      ))}
                    </div>
                  )}
                />
              ) : null}
              {loading ? <Card><Skeleton active /></Card> : null}
              {missing ? (
                <Card className="work-item-layout-empty">
                  <Empty description={`尚未配置${kind === 'create' ? '新建页' : '详情页'}布局`}>
                    <Button
                      type="primary"
                      icon={<PlusOutlined />}
                      loading={saveMutation.isPending}
                      onClick={initialize}
                    >
                      使用当前字段初始化
                    </Button>
                  </Empty>
                </Card>
              ) : null}
              {workbenchQuery.isError ? (
                <Alert
                  type="error"
                  showIcon
                  message="页面布局加载失败"
                  description={layoutError(workbenchQuery.error, '请稍后重试')}
                  action={<Button onClick={() => void workbenchQuery.refetch()}>重试</Button>}
                />
              ) : null}

              {layout ? (
          <div className="work-item-layout-editor" data-testid="work-item-layout-editor">
            <Card className="work-item-layout-palette" title={<Space><PlusOutlined />控件</Space>}>
              <div className="work-item-layout-palette-actions">
                {(['section', 'tab', 'column', 'relation', 'summary'] as const).map((type) => (
                  <Button
                    key={type}
                    aria-label={`添加${nodeLabel(type)}`}
                    onClick={() => addContainer(type)}
                  >
                    {nodeLabel(type)}
                  </Button>
                ))}
              </div>
              <Typography.Text type="secondary">常用字段控件</Typography.Text>
              <div className="work-item-layout-field-palette">
                {commonControlPresets.map((control) => (
                  <Button
                    key={`control-${control.key}`}
                    disabled={!canCreateField}
                    onClick={() => openQuickControl(control.key)}
                  >
                    {control.label}<small>新建字段</small>
                  </Button>
                ))}
              </div>
              {moreControlPresets.length ? <Typography.Text type="secondary">更多字段控件</Typography.Text> : null}
              {moreControlPresets.length ? (
                <div className="work-item-layout-field-palette">
                  {moreControlPresets.map((control) => (
                    <Button
                      key={`control-${control.key}`}
                      disabled={!canCreateField}
                      onClick={() => openQuickControl(control.key)}
                    >
                      {control.label}<small>新建字段</small>
                    </Button>
                  ))}
                </div>
              ) : null}
              {activeFields.length ? <Typography.Text type="secondary">已配置字段</Typography.Text> : null}
              {activeFields.length ? (
                <div className="work-item-layout-field-palette">
                  {activeFields.map((field) => {
                    const used = layout.nodes.some((item) => item.fieldId === field.id)
                    return (
                      <Button key={field.id} disabled={used} onClick={() => addField(field)}>
                        {field.name}<small>{fieldControlLabel(field.fieldType, field.config.typeConfig)}</small>
                      </Button>
                    )
                  })}
                </div>
              ) : null}
            </Card>

            <Card
              className="work-item-layout-canvas"
              title={<Space><BranchesOutlined />页面效果</Space>}
              extra={<Tag>所见即所得 · {layout.nodes.length} 个控件</Tag>}
            >
              {layout.nodes.length === 0 ? <Empty description="请从左侧添加区块或标签页" /> : (
                <WorkItemLayoutRenderer
                  layout={layout}
                  fields={fields}
                  accessProjection={editorAccessProjection}
                  editor={{
                    selectedNodeId: effectiveSelectedId,
                    onSelectNode: (node) => setSelectedId(node.id),
                    onDragNode: (node) => setDraggedId(node.id),
                    onDropNode: dropOn,
                  }}
                />
              )}
            </Card>

            <Card className="work-item-layout-properties" title={<Space><FormOutlined />属性</Space>}>
              {selected ? (
                <NodeProperties
                  key={`${selected.id}:${layout.aggregateVersion}`}
                  node={selected}
                  fields={fields}
                  operators={fieldTypes}
                  busy={commandMutation.isPending}
                  onSave={(changed) => runCommand({
                    operation: 'update',
                    nodeId: selected.id,
                    parentId: selected.parentId,
                    targetSortOrder: selected.sortOrder,
                    node: changed,
                  })}
                />
              ) : <Empty description="选择一个节点查看属性" />}
              <div className="work-item-layout-node-actions">
                <Button icon={<ArrowUpOutlined />} disabled={!selected} onClick={() => moveSelected(-1)}>上移</Button>
                <Button icon={<ArrowDownOutlined />} disabled={!selected} onClick={() => moveSelected(1)}>下移</Button>
                <Button
                  icon={<CopyOutlined />}
                  disabled={!selected || subtreeContainsField(layout.nodes, selected.id)}
                  onClick={() => selected && runCommand({
                    operation: 'copy',
                    nodeId: selected.id,
                    parentId: selected.parentId,
                    targetSortOrder: selected.sortOrder + 1,
                  })}
                >复制</Button>
                <Button danger icon={<DeleteOutlined />} disabled={!selected} onClick={removeSelected}>删除</Button>
              </div>
            </Card>
          </div>
              ) : null}
            </>
          ),
          'field-access': layout ? (
            <Card
              className="work-item-layout-policy-card"
              data-testid="work-item-layout-policy-editor"
              title={<Space><SafetyCertificateOutlined />字段访问策略</Space>}
              extra={(
                <Button
                  type="primary"
                  icon={<SaveOutlined />}
                  disabled={!policyDirty}
                  loading={policyMutation.isPending}
                  onClick={savePolicies}
                >
                  保存策略
                </Button>
              )}
            >
              {layoutFields.length === 0 ? <Empty description="当前布局没有字段" /> : (
                <div className="work-item-layout-policy-editor">
                  <Select
                    aria-label="策略字段"
                    value={effectivePolicyFieldId}
                    options={layoutFields.map((field) => ({
                      label: `${field.name} · ${field.fieldKey}`,
                      value: field.id,
                    }))}
                    onChange={setPolicyFieldId}
                  />
                  {selectedPolicyField && selectedPolicyDraft ? (
                    <FieldPolicyEditor
                      field={selectedPolicyField}
                      draft={selectedPolicyDraft}
                      onChange={(update) => updatePolicyDraft(selectedPolicyField.id, update)}
                    />
                  ) : null}
                </div>
              )}
            </Card>
          ) : undefined,
          'access-preview': layout ? (
            <Card
              className="work-item-layout-preview-card"
              title={<Space><EyeOutlined />服务端访问投影</Space>}
              extra={renderedProjection ? (
                <Tag color={renderedProjection.synthetic ? 'purple' : 'blue'}>
                  {renderedProjection.synthetic ? '合成预览' : '当前身份'}
                </Tag>
              ) : null}
            >
              <div className="work-item-layout-preview-controls">
                <Select
                  aria-label="预览角色"
                  value={previewRole}
                  options={ACCESS_ROLES.map((role) => ({ label: roleLabel(role), value: role }))}
                  onChange={setPreviewRole}
                />
                <Select
                  aria-label="预览空间状态"
                  value={previewSpaceStatus}
                  options={['active', 'disabled', 'archived'].map((value) => ({ label: `空间 ${value}`, value }))}
                  onChange={setPreviewSpaceStatus}
                />
                <Select
                  aria-label="预览类型状态"
                  value={previewTypeStatus}
                  options={['active', 'disabled', 'retired'].map((value) => ({ label: `类型 ${value}`, value }))}
                  onChange={setPreviewTypeStatus}
                />
                <Select
                  aria-label="预览字段状态"
                  value={previewFieldStatus}
                  disabled={!selectedPolicyField}
                  options={['active', 'disabled', 'retired'].map((value) => ({ label: `选中字段 ${value}`, value }))}
                  onChange={setPreviewFieldStatus}
                />
                <Input.TextArea
                  aria-label="预览字段样本"
                  value={previewValues}
                  autoSize={{ minRows: 1, maxRows: 3 }}
                  placeholder='字段样本 JSON，例如 {"priority":"high"}'
                  onChange={(event) => setPreviewValues(event.target.value)}
                />
                <Button
                  icon={<EyeOutlined />}
                  loading={previewMutation.isPending}
                  onClick={runPreview}
                >
                  运行预览
                </Button>
              </div>
              {policyDirty ? (
                <Alert
                  type="info"
                  showIcon
                  message="合成预览使用已保存策略"
                  description="请先保存当前策略草稿，再验证新的访问结果。"
                />
              ) : null}
              {renderedProjection?.diagnostics.length ? (
                <Alert
                  type="warning"
                  showIcon
                  message={`${renderedProjection.diagnostics.length} 项安全诊断`}
                  description={renderedProjection.diagnostics.map((item) => item.code).join('；')}
                />
              ) : null}
              {hiddenSampleKeys.length > 0 ? (
                <Alert
                  type="warning"
                  showIcon
                  message={`${hiddenSampleKeys.length} 个样本字段当前不可见`}
                  description="预览保留输入值，不会清空、删除或持久化；请确认条件或访问策略后再决定正式提交行为。"
                />
              ) : null}
              <div
                className="work-item-layout-renderer-card"
                data-testid="work-item-layout-renderer"
              >
                {renderedProjection ? (
                  <WorkItemLayoutRenderer
                    layout={renderedProjection}
                    fields={renderedProjection.fields}
                    accessProjection={renderedProjection.accessProjection}
                    values={parsedPreview.ok ? parsedPreview.value : {}}
                  />
                ) : <Empty description="正在读取服务端访问投影" />}
              </div>
            </Card>
          ) : undefined,
          'configuration-draft': configurationDraft,
        }}
      />
      <Modal
        title={`添加${quickControl?.label ?? ''}控件`}
        open={Boolean(quickControl)}
        okText="创建并添加"
        cancelText="取消"
        confirmLoading={quickControlMutation.isPending}
        onCancel={() => setQuickControlKey(undefined)}
        onOk={() => quickControlForm.submit()}
        destroyOnHidden
      >
        <Alert
          type="info"
          showIcon
          message="控件必须绑定字段"
          description="创建后会加入当前选中的区块；页面尚无区块时，字段会先保存到“已配置字段”。"
        />
        <Form<QuickFieldControlDraft>
          form={quickControlForm}
          layout="vertical"
          onFinish={(values) => quickControlMutation.mutate(values)}
        >
          <Form.Item
            name="fieldKey"
            label="字段键"
            rules={[
              { required: true, whitespace: true },
              { pattern: /^[a-z][a-z0-9_]*$/, message: '以小写字母开头，仅支持小写字母、数字和下划线' },
            ]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="name" label="显示名称" rules={[{ required: true, whitespace: true }, { max: 128 }]}>
            <Input maxLength={128} />
          </Form.Item>
        </Form>
      </Modal>
    </section>
  )
}

function NodeProperties({
  node: selected,
  fields,
  operators,
  busy,
  onSave,
}: {
  node: WorkItemLayoutNode
  fields: ConfiguredWorkItemField[]
  operators: Array<{ key: string; operators: string[] }>
  busy: boolean
  onSave: (node: WorkItemLayoutNode) => void
}) {
  const [title, setTitle] = useState(String(selected.config.title ?? ''))
  const [relationKey, setRelationKey] = useState(String(selected.config.relationKey ?? 'related'))
  const [relationMode, setRelationMode] = useState(String(selected.config.mode ?? 'list'))
  const [relationMaxItems, setRelationMaxItems] = useState(Number(selected.config.maxItems ?? 50))
  const [columns, setColumns] = useState(layoutContainerColumns(selected.config.columns))
  const [labelPosition, setLabelPosition] = useState(layoutFieldLabelPosition(selected.config.labelPosition))
  const [controlWidth, setControlWidth] = useState(layoutFieldControlWidth(selected.config.controlWidth))
  const initialCondition = parseConditionDrafts(selected.visibilityCondition.expression)
  const [conditionJoin, setConditionJoin] = useState<'all' | 'any'>(initialCondition.join)
  const [conditionNegated, setConditionNegated] = useState(initialCondition.negated)
  const [conditionDrafts, setConditionDrafts] = useState<ConditionDraft[]>(initialCondition.drafts)
  const [conditionUnsupported, setConditionUnsupported] = useState(initialCondition.unsupported)
  const conditionErrors = conditionDrafts.flatMap((draft, index) => {
    const error = conditionDraftError(draft, fields)
    return error ? [`条件 ${index + 1}：${error}`] : []
  })

  const save = () => {
    if (conditionUnsupported || conditionErrors.length > 0) return
    const predicates = conditionDrafts.map((draft) => {
      const field = fields.find((item) => item.fieldKey === draft.fieldKey)!
      return {
        kind: 'predicate',
        source: 'field',
        fieldId: field.id,
        fieldKey: field.fieldKey,
        operator: draft.operator,
        ...(draft.operator === 'is_empty'
          ? {}
          : { value: typedConditionValue(field.fieldType, draft.operator, draft.value) }),
      }
    })
    const combined = predicates.length === 0
      ? undefined
      : predicates.length === 1
        ? predicates[0]
        : { kind: conditionJoin, operands: predicates }
    const expression = conditionNegated && combined
      ? { kind: 'not', operand: combined }
      : combined
    const visibilityCondition = expression
      ? { schemaVersion: 1 as const, expression }
      : { schemaVersion: 1 as const }
    const config = selected.nodeType === 'relation'
      ? {
        ...selected.config,
        schemaVersion: 1,
        title: title.trim() || nodeLabel(selected.nodeType),
        relationKey: relationKey.trim().toLowerCase(),
        mode: relationMode,
        maxItems: relationMaxItems,
        showReverse: true,
        collapsedByDefault: Boolean(selected.config.collapsedByDefault),
        listFallback: true,
        keyboardNavigation: true,
      }
      : {
        ...selected.config,
        title: title.trim() || nodeLabel(selected.nodeType),
        ...(selected.nodeType === 'section' || selected.nodeType === 'tab' ? { columns } : {}),
        ...(selected.nodeType === 'field' ? { labelPosition, controlWidth } : {}),
      }
    onSave({ ...selected, config, visibilityCondition })
  }

  return (
    <div className="work-item-layout-property-form">
      <label>节点类型<Input value={nodeLabel(selected.nodeType)} disabled /></label>
      <label>永久键<Input value={selected.nodeKey} disabled /></label>
      <label>显示名称<Input value={title} maxLength={128} onChange={(event) => setTitle(event.target.value)} /></label>
      {selected.nodeType === 'field' ? (
        <>
          <label>
            标签位置
            <Segmented
              block
              value={labelPosition}
              options={[
                { label: '控件上方', value: 'top' },
                { label: '控件左侧', value: 'left' },
              ]}
              onChange={(value) => setLabelPosition(value as 'top' | 'left')}
            />
          </label>
          <label>
            控件宽度
            <Select
              value={controlWidth}
              options={[
                { value: 100, label: '整行 · 100%' },
                { value: 75, label: '四分之三 · 75%' },
                { value: 67, label: '三分之二 · 67%' },
                { value: 50, label: '一半 · 50%' },
                { value: 33, label: '三分之一 · 33%' },
                { value: 25, label: '四分之一 · 25%' },
              ]}
              onChange={setControlWidth}
            />
          </label>
        </>
      ) : null}
      {selected.nodeType === 'section' || selected.nodeType === 'tab' ? (
        <label>
          列数
          <Select
            value={columns}
            options={[1, 2, 3, 4].map((value) => ({ value, label: `${value} 列` }))}
            onChange={setColumns}
          />
        </label>
      ) : null}
      {selected.nodeType === 'relation' ? (
        <>
          <label>
            永久 relation key
            <Input
              value={relationKey}
              status={/^[a-z][a-z0-9_]{0,63}$/.test(relationKey) ? undefined : 'error'}
              onChange={(event) => setRelationKey(event.target.value)}
            />
          </label>
          <label>
            展示模式
            <Select
              value={relationMode}
              options={['picker', 'list', 'hierarchy', 'impact'].map((value) => ({ value, label: value }))}
              onChange={setRelationMode}
            />
          </label>
          <label>
            展示硬限
            <InputNumber min={1} max={200} value={relationMaxItems} onChange={(value) => setRelationMaxItems(value ?? 50)} />
          </label>
          <Alert type="info" showIcon message="关系值由规范 relation API 保存，不进入普通字段 JSON。" />
        </>
      ) : null}
      <div className="work-item-layout-condition-heading">
        <Typography.Text strong>显示条件</Typography.Text>
        <Button
          size="small"
          onClick={() => {
            setConditionUnsupported(false)
            setConditionDrafts((current) => [...current, emptyConditionDraft()])
          }}
        >
          添加条件
        </Button>
      </div>
      {conditionUnsupported ? (
        <Alert
          type="warning"
          showIcon
          message="当前条件包含此编辑器尚未支持的上下文表达式"
          description="原条件会保持不变。清除后可改用字段条件重新配置。"
          action={(
            <Button
              size="small"
              onClick={() => {
                setConditionUnsupported(false)
                setConditionDrafts([])
                setConditionNegated(false)
              }}
            >
              清除条件
            </Button>
          )}
        />
      ) : null}
      {!conditionUnsupported && conditionDrafts.length > 1 ? (
        <Segmented
          block
          value={conditionJoin}
          onChange={(value) => setConditionJoin(value as 'all' | 'any')}
          options={[
            { label: '全部满足', value: 'all' },
            { label: '任一满足', value: 'any' },
          ]}
        />
      ) : null}
      {!conditionUnsupported && conditionDrafts.length > 0 ? (
        <label className="work-item-layout-condition-negate">
          条件结果取反
          <Switch size="small" checked={conditionNegated} onChange={setConditionNegated} />
        </label>
      ) : null}
      {!conditionUnsupported ? conditionDrafts.map((draft, index) => {
        const field = fields.find((item) => item.fieldKey === draft.fieldKey)
        const allowedOperators = operators.find((item) => item.key === field?.fieldType)?.operators ?? []
        return (
          <div className="work-item-layout-condition-row" key={draft.id}>
            <Select
              aria-label={`条件 ${index + 1} 字段`}
              value={draft.fieldKey}
              placeholder={`条件 ${index + 1} 字段`}
              options={fields
                .filter((item) => item.id !== selected.fieldId)
                .map((item) => ({ label: item.name, value: item.fieldKey }))}
              onChange={(value) => setConditionDrafts((current) => current.map((item) =>
                item.id === draft.id ? { ...item, fieldKey: value, operator: undefined, value: '' } : item))}
            />
            <Select
              aria-label={`条件 ${index + 1} 操作符`}
              value={draft.operator}
              placeholder="操作符"
              disabled={!field}
              options={allowedOperators.map((value) => ({ label: value, value }))}
              onChange={(value) => setConditionDrafts((current) => current.map((item) =>
                item.id === draft.id ? { ...item, operator: value, value: '' } : item))}
            />
            {draft.operator && draft.operator !== 'is_empty' ? (
              <Input
                aria-label={`条件 ${index + 1} 比较值`}
                value={draft.value}
                placeholder={draft.operator === 'between' ? '起始值,结束值' : '比较值'}
                onChange={(event) => setConditionDrafts((current) => current.map((item) =>
                  item.id === draft.id ? { ...item, value: event.target.value } : item))}
              />
            ) : null}
            <Button
              danger
              aria-label={`删除条件 ${index + 1}`}
              icon={<DeleteOutlined />}
              onClick={() => setConditionDrafts((current) => current.filter((item) => item.id !== draft.id))}
            />
          </div>
        )
      }) : null}
      {conditionErrors.length > 0 ? (
        <Alert type="error" showIcon message="条件尚未完成" description={conditionErrors.join('；')} />
      ) : null}
      <Button
        type="primary"
        icon={<SaveOutlined />}
        loading={busy}
        disabled={conditionUnsupported || conditionErrors.length > 0}
        onClick={save}
      >
        保存属性
      </Button>
    </div>
  )
}

const ACCESS_ROLES: WorkItemFieldAccessRole[] = [
  'owner',
  'admin',
  'member',
  'guest',
  'non_member',
  'enterprise_admin',
]

type PolicyEffectDraft = {
  mode: WorkItemFieldAccessMode | 'inherit'
  required: boolean
  ruleKey?: string
}

type FieldPolicyDraft = {
  id: string
  fieldId: string
  fieldKey: string
  policyKey: string
  defaultMode: WorkItemFieldAccessMode
  defaultRequired: boolean
  roles: Record<WorkItemFieldAccessRole, PolicyEffectDraft>
  conditionalRules: WorkItemFieldAccessPolicyDocument['rules']
}

function FieldPolicyEditor({
  field,
  draft,
  onChange,
}: {
  field: ConfiguredWorkItemField
  draft: FieldPolicyDraft
  onChange: (update: (draft: FieldPolicyDraft) => FieldPolicyDraft) => void
}) {
  const setDefaultMode = (mode: WorkItemFieldAccessMode) => onChange((current) => ({
    ...current,
    defaultMode: mode,
    defaultRequired: mode === 'write' ? current.defaultRequired : false,
  }))
  const setRole = (
    role: WorkItemFieldAccessRole,
    update: Partial<PolicyEffectDraft>,
  ) => onChange((current) => {
    const next = { ...current.roles[role], ...update }
    if (next.mode !== 'write') next.required = false
    return { ...current, roles: { ...current.roles, [role]: next } }
  })

  return (
    <div className="work-item-layout-policy-form">
      <div className="work-item-layout-policy-field-heading">
        <div>
          <Typography.Text strong>{field.name}</Typography.Text>
          <Typography.Text type="secondary">{field.fieldKey}</Typography.Text>
        </div>
        <Tag>{field.fieldType}</Tag>
      </div>
      <div className="work-item-layout-policy-default">
        <label>
          默认访问
          <Select
            aria-label="默认访问模式"
            value={draft.defaultMode}
            options={ACCESS_MODES.map((mode) => ({ label: modeLabel(mode), value: mode }))}
            onChange={setDefaultMode}
          />
        </label>
        <label className="work-item-layout-policy-required">
          默认必填
          <Switch
            checked={draft.defaultRequired}
            disabled={draft.defaultMode !== 'write'}
            onChange={(required) => onChange((current) => ({ ...current, defaultRequired: required }))}
          />
        </label>
      </div>
      <div className="work-item-layout-policy-role-list">
        {ACCESS_ROLES.map((role) => {
          const effect = draft.roles[role]
          return (
            <div className="work-item-layout-policy-role" key={role}>
              <span>
                <strong>{roleLabel(role)}</strong>
                <small>{role}</small>
              </span>
              <Select
                aria-label={`${roleLabel(role)}访问模式`}
                value={effect.mode}
                options={[
                  { label: '继承默认', value: 'inherit' },
                  ...ACCESS_MODES.map((mode) => ({ label: modeLabel(mode), value: mode })),
                ]}
                onChange={(mode) => setRole(role, { mode: mode as PolicyEffectDraft['mode'] })}
              />
              <label>
                必填
                <Switch
                  size="small"
                  checked={effect.required}
                  disabled={effect.mode !== 'write'}
                  onChange={(required) => setRole(role, { required })}
                />
              </label>
            </div>
          )
        })}
      </div>
      {draft.conditionalRules.length > 0 ? (
        <Alert
          type="info"
          showIcon
          message={`${draft.conditionalRules.length} 条条件规则将原样保留`}
          description="当前面板只编辑默认效果与角色覆盖，不会删除已有条件规则。"
        />
      ) : null}
      <Typography.Text type="secondary">
        每个角色只有一个无条件覆盖入口，界面会阻止冲突规则；hidden 优先于 read，read 优先于 write。
      </Typography.Text>
    </div>
  )
}

type ConditionDraft = {
  id: string
  fieldKey?: string
  operator?: string
  value: string
}

function emptyConditionDraft(): ConditionDraft {
  return { id: crypto.randomUUID(), value: '' }
}

function parseConditionDrafts(expression: unknown): {
  join: 'all' | 'any'
  negated: boolean
  drafts: ConditionDraft[]
  unsupported: boolean
} {
  if (!expression || typeof expression !== 'object') {
    return { join: 'all', negated: false, drafts: [], unsupported: false }
  }
  let current = expression as Record<string, unknown>
  let negated = false
  if (current.kind === 'not' && current.operand && typeof current.operand === 'object') {
    negated = true
    current = current.operand as Record<string, unknown>
  }
  const join = current.kind === 'any' ? 'any' : 'all'
  const operands = current.kind === 'all' || current.kind === 'any'
    ? Array.isArray(current.operands) ? current.operands : []
    : [current]
  const predicates = operands.filter((item): item is Record<string, unknown> =>
    Boolean(item && typeof item === 'object'))
  const unsupported = predicates.some((item) =>
    item.kind !== 'predicate' || item.source !== 'field' || typeof item.fieldKey !== 'string')
  if (unsupported) return { join, negated, drafts: [], unsupported: true }
  return {
    join,
    negated,
    unsupported: false,
    drafts: predicates.map((item) => ({
      id: crypto.randomUUID(),
      fieldKey: item.fieldKey as string,
      operator: typeof item.operator === 'string' ? item.operator : undefined,
      value: Array.isArray(item.value) ? item.value.join(',') : item.value == null ? '' : String(item.value),
    })),
  }
}

function conditionDraftError(draft: ConditionDraft, fields: ConfiguredWorkItemField[]) {
  const field = fields.find((item) => item.fieldKey === draft.fieldKey)
  if (!field) return '请选择有效字段'
  if (!draft.operator) return '请选择操作符'
  if (draft.operator === 'is_empty') return undefined
  if (!draft.value.trim()) return '请输入比较值'
  if (field.fieldType === 'number' && draft.operator !== 'between' && !Number.isFinite(Number(draft.value))) {
    return '请输入有效数字'
  }
  if (draft.operator === 'between' && draft.value.split(',').filter((item) => item.trim()).length !== 2) {
    return 'between 需要两个以逗号分隔的值'
  }
  return undefined
}

function node(
  id: string,
  parentId: string | null,
  nodeKey: string,
  nodeType: WorkItemLayoutNodeType,
  sortOrder: number,
  config: Record<string, unknown>,
): WorkItemLayoutNode {
  return {
    id, parentId, nodeKey, nodeType, fieldId: null, fieldKey: null, sortOrder,
    config, visibilityCondition: { schemaVersion: 1 },
  }
}

function fieldNode(field: ConfiguredWorkItemField, parentId: string, sortOrder: number): WorkItemLayoutNode {
  const id = crypto.randomUUID()
  const suffix = id.replaceAll('-', '').slice(0, 10)
  const baseKey = `field_${field.fieldKey}`.slice(0, 64 - suffix.length - 1)
  return {
    ...node(id, parentId, `${baseKey}_${suffix}`, 'field', sortOrder, {
      title: field.name,
      labelPosition: 'top',
      controlWidth: 100,
    }),
    fieldId: field.id,
    fieldKey: field.fieldKey,
  }
}

function siblings(nodes: WorkItemLayoutNode[], parentId: string | null) {
  return nodes.filter((item) => item.parentId === parentId).sort((left, right) => left.sortOrder - right.sortOrder)
}

function subtreeContainsField(nodes: WorkItemLayoutNode[], nodeId: string): boolean {
  const candidate = nodes.find((item) => item.id === nodeId)
  if (!candidate) return false
  if (candidate.nodeType === 'field') return true
  return nodes
    .filter((item) => item.parentId === nodeId)
    .some((item) => subtreeContainsField(nodes, item.id))
}

function allowedParent(type: WorkItemLayoutNodeType, selected?: WorkItemLayoutNode) {
  if (!selected) return false
  if (type === 'section') return ['section', 'tab'].includes(selected.nodeType)
  if (type === 'column') return ['section', 'tab'].includes(selected.nodeType)
  if (type === 'relation' || type === 'summary') return ['section', 'tab', 'column'].includes(selected.nodeType)
  return false
}

function nodeLabel(type: WorkItemLayoutNodeType) {
  return ({ section: '区块', tab: '标签页', column: '分栏', field: '字段', relation: '关系控件', summary: '摘要' } as const)[type]
}

function nodeTitle(item: WorkItemLayoutNode) {
  return String(item.config.title ?? item.fieldKey ?? item.nodeKey)
}

function layoutContainerColumns(value: unknown) {
  const columns = Number(value)
  return Number.isInteger(columns) && columns >= 1 && columns <= 4 ? columns : 2
}

function layoutFieldLabelPosition(value: unknown): 'top' | 'left' {
  return value === 'left' ? 'left' : 'top'
}

function layoutFieldControlWidth(value: unknown) {
  const width = Number(value)
  return [25, 33, 50, 67, 75, 100].includes(width) ? width : 100
}

function typedConditionValue(fieldType: string, operator: string | undefined, value: string): unknown {
  if (operator === 'between') {
    return value.split(',').map((item) => typedScalar(fieldType, item.trim()))
  }
  if (['in', 'contains_any', 'contains_all'].includes(operator ?? '')) {
    return value.split(',').map((item) => typedScalar(fieldType, item.trim())).filter((item) => item !== '')
  }
  if (fieldType === 'number') return Number(value)
  if (fieldType === 'boolean') return value === 'true'
  if (['multi_select', 'user', 'attachment', 'work_item_reference'].includes(fieldType)) {
    return value.split(',').map((item) => item.trim()).filter(Boolean)
  }
  return value
}

function typedScalar(fieldType: string, value: string): unknown {
  if (fieldType === 'number') return Number(value)
  if (fieldType === 'boolean') return value === 'true'
  return value
}

const ACCESS_MODES: WorkItemFieldAccessMode[] = ['write', 'read', 'hidden']

function emptyRoleEffects(): Record<WorkItemFieldAccessRole, PolicyEffectDraft> {
  return Object.fromEntries(ACCESS_ROLES.map((role) => [
    role,
    { mode: 'inherit', required: false },
  ])) as Record<WorkItemFieldAccessRole, PolicyEffectDraft>
}

function policyDraftMap(
  fields: ConfiguredWorkItemField[],
  policies: WorkItemFieldAccessPolicy[],
): Record<string, FieldPolicyDraft> {
  const byField = new Map(policies.map((policy) => [policy.fieldId, policy]))
  return Object.fromEntries(fields.map((field) => {
    const stored = byField.get(field.id)
    const document = policyDocument(stored?.policy)
    const roles = emptyRoleEffects()
    const conditionalRules: WorkItemFieldAccessPolicyDocument['rules'] = []
    document.rules.forEach((rule) => {
      if (rule.when) {
        conditionalRules.push(rule)
        return
      }
      rule.roles.forEach((role) => {
        roles[role] = {
          mode: rule.mode,
          required: rule.required,
          ruleKey: rule.ruleKey,
        }
      })
    })
    return [field.id, {
      id: stored?.id ?? crypto.randomUUID(),
      fieldId: field.id,
      fieldKey: field.fieldKey,
      policyKey: stored?.policyKey ?? `${field.fieldKey}_access`,
      defaultMode: document.default.mode,
      defaultRequired: document.default.required,
      roles,
      conditionalRules,
    }]
  }))
}

function policyDocument(value: unknown): WorkItemFieldAccessPolicyDocument {
  const candidate = value && typeof value === 'object'
    ? value as Partial<WorkItemFieldAccessPolicyDocument>
    : {}
  const defaultMode = isAccessMode(candidate.default?.mode) ? candidate.default.mode : 'write'
  const rules = Array.isArray(candidate.rules)
    ? candidate.rules.filter(isPolicyRule)
    : []
  return {
    schemaVersion: 1,
    default: {
      mode: defaultMode,
      required: defaultMode === 'write' && candidate.default?.required === true,
    },
    rules,
  }
}

function isPolicyRule(value: unknown): value is WorkItemFieldAccessPolicyDocument['rules'][number] {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<WorkItemFieldAccessPolicyDocument['rules'][number]>
  return typeof candidate.ruleKey === 'string'
    && Array.isArray(candidate.roles)
    && candidate.roles.every((role) => ACCESS_ROLES.includes(role))
    && isAccessMode(candidate.mode)
    && typeof candidate.required === 'boolean'
}

function isAccessMode(value: unknown): value is WorkItemFieldAccessMode {
  return ACCESS_MODES.includes(value as WorkItemFieldAccessMode)
}

function policyRequest(
  draft: FieldPolicyDraft,
): Omit<WorkItemFieldAccessPolicy, 'configHash'> {
  const roleRules = ACCESS_ROLES.flatMap((role) => {
    const effect = draft.roles[role]
    if (effect.mode === 'inherit') return []
    return [{
      ruleKey: effect.ruleKey ?? `role_${role}_access`,
      roles: [role],
      mode: effect.mode,
      required: effect.mode === 'write' && effect.required,
    }]
  })
  return {
    id: draft.id,
    fieldId: draft.fieldId,
    fieldKey: draft.fieldKey,
    policyKey: draft.policyKey,
    policy: {
      schemaVersion: 1,
      default: {
        mode: draft.defaultMode,
        required: draft.defaultMode === 'write' && draft.defaultRequired,
      },
      rules: [...draft.conditionalRules, ...roleRules],
    },
  }
}

function policyIsRestrictive(value: unknown) {
  const document = policyDocument(value)
  return document.default.mode !== 'write'
    || document.rules.some((rule) => rule.mode !== 'write' || rule.required)
}

function parsePreviewValues(value: string):
  | { ok: true; value: Record<string, unknown> }
  | { ok: false; message: string } {
  try {
    const parsed = JSON.parse(value || '{}') as unknown
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
      return { ok: false, message: '字段样本必须是 JSON 对象' }
    }
    return { ok: true, value: parsed as Record<string, unknown> }
  } catch {
    return { ok: false, message: '字段样本不是有效 JSON' }
  }
}

function roleLabel(role: WorkItemFieldAccessRole) {
  return ({
    owner: '空间所有者',
    admin: '空间管理员',
    member: '成员',
    guest: '访客',
    non_member: '非成员',
    enterprise_admin: '企业管理员',
  } as const)[role]
}

function nextAvailableFieldKey(
  base: WorkItemFieldControlPresetKey,
  fields: ConfiguredWorkItemField[],
) {
  const used = new Set(fields.map((field) => field.fieldKey))
  if (!used.has(base)) return base
  let index = 2
  while (used.has(`${base}_${index}`)) index += 1
  return `${base}_${index}`
}

function nextFieldSortOrder(fields: ConfiguredWorkItemField[]) {
  return fields.reduce((maximum, field) => Math.max(maximum, field.sortOrder), 0) + 10
}

function isEditableTarget(target: EventTarget | null) {
  if (!(target instanceof HTMLElement)) return false
  return target.isContentEditable
    || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)
    || Boolean(target.closest('[contenteditable="true"], .ant-select, .ant-picker'))
}

function isVersionConflict(error: unknown) {
  return error instanceof ApiRequestError
    && ['version_conflict', 'layout_version_conflict'].includes(error.code ?? '')
}

function modeLabel(mode: WorkItemFieldAccessMode) {
  return ({ write: '可写', read: '只读', hidden: '隐藏' } as const)[mode]
}

function layoutError(error: unknown, fallback: string) {
  if (error instanceof ApiRequestError) {
    const messages: Record<string, string> = {
      version_conflict: '布局已被其他人更新，请刷新后重试。',
      layout_delete_confirmation_required: '删除包含引用的节点前需要明确确认。',
      invalid_layout_condition_operator: '条件操作符与字段类型不兼容。',
      layout_condition_hidden_dependency: '条件引用的字段必须同时出现在当前布局。',
      layout_condition_cycle: '显示条件之间形成了循环依赖。',
      not_found_or_hidden: '布局不存在或当前身份不可见。',
    }
    return error.code && messages[error.code] ? messages[error.code] : error.message
  }
  return error instanceof Error ? error.message : fallback
}
