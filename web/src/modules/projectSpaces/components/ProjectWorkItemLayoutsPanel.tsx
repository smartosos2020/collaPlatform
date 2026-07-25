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
  SaveOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Empty,
  Input,
  Modal,
  Segmented,
  Select,
  Skeleton,
  Space,
  Switch,
  Tag,
  Typography,
} from 'antd'
import { useMemo, useState } from 'react'

import { ApiRequestError } from '../../../shared/api/httpClient'
import type { UserProjectSpace } from '../api/projectSpacesApi'
import {
  listConfiguredWorkItemFields,
  listWorkItemFieldTypes,
  workItemFieldKeys,
  type ConfiguredWorkItemField,
} from '../api/workItemFieldsApi'
import {
  commandWorkItemLayoutNode,
  getWorkItemLayout,
  saveWorkItemLayout,
  workItemLayoutKeys,
  type WorkItemLayoutKind,
  type WorkItemLayoutNode,
  type WorkItemLayoutNodeCommand,
  type WorkItemLayoutNodeType,
} from '../api/workItemLayoutsApi'
import { getConfiguredWorkItemType, workItemTypeKeys } from '../api/workItemTypesApi'
import { WorkItemLayoutRenderer } from './WorkItemLayoutRenderer'

export function ProjectWorkItemLayoutsPanel({
  space,
  typeId,
  onBack,
}: {
  space: UserProjectSpace
  typeId: string
  onBack: () => void
}) {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const [kind, setKind] = useState<WorkItemLayoutKind>('create')
  const [selectedId, setSelectedId] = useState<string>()
  const [pendingCommand, setPendingCommand] = useState<WorkItemLayoutNodeCommand>()
  const [draggedId, setDraggedId] = useState<string>()
  const typeQuery = useQuery({
    queryKey: workItemTypeKeys.detail(space.id, typeId),
    queryFn: () => getConfiguredWorkItemType(space.id, typeId),
  })
  const fieldsQuery = useQuery({
    queryKey: workItemFieldKeys.configuration(space.id, typeId, 'active'),
    queryFn: () => listConfiguredWorkItemFields(space.id, typeId, 'active'),
  })
  const catalogQuery = useQuery({
    queryKey: workItemFieldKeys.catalog(space.id),
    queryFn: () => listWorkItemFieldTypes(space.id),
  })
  const layoutQuery = useQuery({
    queryKey: workItemLayoutKeys.detail(space.id, typeId, kind),
    queryFn: () => getWorkItemLayout(space.id, typeId, kind),
    retry: false,
  })
  const layout = layoutQuery.data
  const effectiveSelectedId = layout?.nodes.some((item) => item.id === selectedId)
    ? selectedId
    : layout?.nodes[0]?.id
  const selected = layout?.nodes.find((node) => node.id === effectiveSelectedId)

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: workItemLayoutKeys.detail(space.id, typeId, kind) })
  }

  const saveMutation = useMutation({
    mutationFn: (request: Parameters<typeof saveWorkItemLayout>[3]) =>
      saveWorkItemLayout(space.id, typeId, kind, request),
    onSuccess: async (saved) => {
      queryClient.setQueryData(workItemLayoutKeys.detail(space.id, typeId, kind), saved)
      setSelectedId(saved.nodes[0]?.id)
      message.success(`${kind === 'create' ? '新建页' : '详情页'}布局已创建`)
    },
    onError: (error) => message.error(layoutError(error, '创建布局失败')),
  })

  const commandMutation = useMutation({
    mutationFn: (command: WorkItemLayoutNodeCommand) =>
      commandWorkItemLayoutNode(space.id, typeId, kind, command),
    onSuccess: (saved) => {
      queryClient.setQueryData(workItemLayoutKeys.detail(space.id, typeId, kind), saved)
      setPendingCommand(undefined)
    },
    onError: (error, command) => {
      setPendingCommand(command)
      message.error(layoutError(error, '布局命令执行失败'))
    },
  })

  const runCommand = (command: Omit<WorkItemLayoutNodeCommand, 'aggregateVersion'>) => {
    if (!layout) return
    commandMutation.mutate({ ...command, aggregateVersion: layout.aggregateVersion })
  }

  const initialize = () => {
    const fields = fieldsQuery.data?.items ?? []
    const sectionId = crypto.randomUUID()
    const nodes: WorkItemLayoutNode[] = [
      node(sectionId, null, `${kind}_main`, 'section', 0, { title: kind === 'create' ? '新建工作项' : '工作项详情' }),
      ...fields.map((field, index) => fieldNode(field, sectionId, index)),
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
      { title: nodeLabel(nodeType) },
    )
    runCommand({ operation: 'add', parentId, targetSortOrder: newNode.sortOrder, node: newNode })
    setSelectedId(newNode.id)
  }

  const addField = (field: ConfiguredWorkItemField) => {
    if (!layout || layout.nodes.some((item) => item.fieldId === field.id)) return
    const parent = selected && ['section', 'tab', 'column'].includes(selected.nodeType)
      ? selected
      : layout.nodes.find((item) => item.nodeType === 'section' && item.parentId === null)
    if (!parent) {
      message.warning('请先添加一个区块')
      return
    }
    const newNode = fieldNode(field, parent.id, siblings(layout.nodes, parent.id).length)
    runCommand({ operation: 'add', parentId: parent.id, targetSortOrder: newNode.sortOrder, node: newNode })
    setSelectedId(newNode.id)
  }

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
    const refreshed = await layoutQuery.refetch()
    if (refreshed.data) {
      commandMutation.mutate({ ...pendingCommand, aggregateVersion: refreshed.data.aggregateVersion })
    }
  }

  const loading = typeQuery.isLoading || fieldsQuery.isLoading || catalogQuery.isLoading || layoutQuery.isLoading
  const missing = layoutQuery.error instanceof ApiRequestError && layoutQuery.error.status === 404

  return (
    <section
      className="work-item-layout-page"
      aria-label="页面布局配置"
      data-testid="work-item-layouts-panel"
      tabIndex={0}
      onKeyDown={(event) => {
        if (event.altKey && event.key === 'ArrowUp') { event.preventDefault(); moveSelected(-1) }
        if (event.altKey && event.key === 'ArrowDown') { event.preventDefault(); moveSelected(1) }
        if (event.key === 'Delete' && selected) { event.preventDefault(); removeSelected() }
      }}
    >
      <header className="work-item-layout-header">
        <div>
          <Button type="text" icon={<ArrowLeftOutlined />} onClick={onBack}>返回类型</Button>
          <Typography.Title level={4}>页面布局</Typography.Title>
          <Typography.Text type="secondary">
            {typeQuery.data ? `${typeQuery.data.name} · ${typeQuery.data.typeKey}` : '正在读取工作项类型'}
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
            }}
            options={[{ label: '新建页', value: 'create' }, { label: '详情页', value: 'detail' }]}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void refresh()}>刷新</Button>
        </Space>
      </header>

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
          description={layout.diagnostics.map((diagnostic) => `${diagnostic.nodeKey ?? '布局'}：${diagnostic.code}`).join('；')}
        />
      ) : null}
      {loading ? <Card><Skeleton active /></Card> : null}
      {missing ? (
        <Card className="work-item-layout-empty">
          <Empty description={`尚未配置${kind === 'create' ? '新建页' : '详情页'}布局`}>
            <Button type="primary" icon={<PlusOutlined />} loading={saveMutation.isPending} onClick={initialize}>
              使用当前字段初始化
            </Button>
          </Empty>
        </Card>
      ) : null}
      {layoutQuery.isError && !missing ? (
        <Alert type="error" showIcon message="页面布局加载失败" description={layoutError(layoutQuery.error, '请稍后重试')} />
      ) : null}

      {layout ? (
        <>
          <div className="work-item-layout-editor" data-testid="work-item-layout-editor">
            <Card className="work-item-layout-palette" title={<Space><PlusOutlined />控件</Space>}>
              <div className="work-item-layout-palette-actions">
                {(['section', 'tab', 'column', 'summary'] as const).map((type) => (
                  <Button
                    key={type}
                    aria-label={`添加${nodeLabel(type)}`}
                    onClick={() => addContainer(type)}
                  >
                    {nodeLabel(type)}
                  </Button>
                ))}
              </div>
              <Typography.Text type="secondary">字段</Typography.Text>
              <div className="work-item-layout-field-palette">
                {fieldsQuery.data?.items.map((field) => {
                  const used = layout.nodes.some((item) => item.fieldId === field.id)
                  return (
                    <Button key={field.id} disabled={used} onClick={() => addField(field)}>
                      {field.name}<small>{field.fieldType}</small>
                    </Button>
                  )
                })}
              </div>
            </Card>

            <Card
              className="work-item-layout-canvas"
              title={<Space><BranchesOutlined />布局结构</Space>}
              extra={<Tag>{layout.nodes.length} 个节点</Tag>}
            >
              {layout.nodes.length === 0 ? <Empty description="请从左侧添加区块或标签页" /> : (
                <LayoutTree
                  nodes={layout.nodes}
                  selectedId={effectiveSelectedId}
                  onSelect={setSelectedId}
                  onDrag={setDraggedId}
                  onDrop={dropOn}
                />
              )}
            </Card>

            <Card className="work-item-layout-properties" title={<Space><FormOutlined />属性</Space>}>
              {selected ? (
                <NodeProperties
                  key={`${selected.id}:${layout.aggregateVersion}`}
                  node={selected}
                  fields={fieldsQuery.data?.items ?? []}
                  operators={catalogQuery.data?.items ?? []}
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
          <Card
            className="work-item-layout-renderer-card"
            data-testid="work-item-layout-renderer"
            title={<Space><EyeOutlined />共享渲染预览</Space>}
          >
            <WorkItemLayoutRenderer
              layout={layout}
              fields={fieldsQuery.data?.items ?? []}
              accessProjection={{}}
            />
          </Card>
        </>
      ) : null}
    </section>
  )
}

function LayoutTree({
  nodes,
  selectedId,
  onSelect,
  onDrag,
  onDrop,
}: {
  nodes: WorkItemLayoutNode[]
  selectedId?: string
  onSelect: (id: string) => void
  onDrag: (id: string) => void
  onDrop: (node: WorkItemLayoutNode) => void
}) {
  const children = useMemo(() => {
    const result = new Map<string | null, WorkItemLayoutNode[]>()
    nodes.forEach((item) => {
      const values = result.get(item.parentId) ?? []
      values.push(item)
      result.set(item.parentId, values)
    })
    result.forEach((values) => values.sort((left, right) => left.sortOrder - right.sortOrder))
    return result
  }, [nodes])
  const render = (parentId: string | null, depth = 0): React.ReactNode => (
    children.get(parentId)?.map((item) => (
      <div key={item.id} className="work-item-layout-tree-branch">
        <button
          type="button"
          draggable
          className={`work-item-layout-tree-node${item.id === selectedId ? ' active' : ''}`}
          style={{ paddingLeft: 12 + depth * 18 }}
          onClick={() => onSelect(item.id)}
          onDragStart={() => onDrag(item.id)}
          onDragOver={(event) => event.preventDefault()}
          onDrop={(event) => { event.preventDefault(); onDrop(item) }}
        >
          <span>{nodeLabel(item.nodeType)}</span>
          <strong>{nodeTitle(item)}</strong>
          <small>{item.nodeKey}</small>
        </button>
        {render(item.id, depth + 1)}
      </div>
    )) ?? null
  )
  return <div className="work-item-layout-tree">{render(null)}</div>
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
    onSave({ ...selected, config: { ...selected.config, title: title.trim() || nodeLabel(selected.nodeType) }, visibilityCondition })
  }

  return (
    <div className="work-item-layout-property-form">
      <label>节点类型<Input value={nodeLabel(selected.nodeType)} disabled /></label>
      <label>永久键<Input value={selected.nodeKey} disabled /></label>
      <label>显示名称<Input value={title} maxLength={128} onChange={(event) => setTitle(event.target.value)} /></label>
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
  return {
    ...node(crypto.randomUUID(), parentId, `field_${field.fieldKey}`, 'field', sortOrder, { title: field.name }),
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
  if (type === 'column' || type === 'summary') return ['section', 'tab'].includes(selected.nodeType)
  return false
}

function nodeLabel(type: WorkItemLayoutNodeType) {
  return ({ section: '区块', tab: '标签页', column: '分栏', field: '字段', summary: '摘要' } as const)[type]
}

function nodeTitle(item: WorkItemLayoutNode) {
  return String(item.config.title ?? item.fieldKey ?? item.nodeKey)
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
