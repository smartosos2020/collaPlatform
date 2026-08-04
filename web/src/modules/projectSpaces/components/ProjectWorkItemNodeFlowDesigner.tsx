import {
  ApartmentOutlined,
  DeleteOutlined,
  MinusOutlined,
  PlusOutlined,
  SaveOutlined,
  UndoOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
} from '@ant-design/icons'
import { useMutation } from '@tanstack/react-query'
import {
  Alert,
  App as AntdApp,
  Button,
  Collapse,
  Empty,
  Input,
  InputNumber,
  Select,
  Space,
  Tag,
  Typography,
} from 'antd'
import { useMemo, useState } from 'react'

import {
  saveWorkItemConfigurationDraft,
  type WorkItemConfigurationDraft,
} from '../api/workItemConfigurationApi'
import { errorMessage } from '../projectSpaceView'
import { CollapsibleWorkItemCard } from './CollapsibleWorkItemCard'

type JsonRecord = Record<string, unknown>
type Stage = { stageKey: string; label: string; description: string; sortOrder: number }
type Node = {
  nodeKey: string
  stageKey: string
  label: string
  description: string
  kind: 'start' | 'manual' | 'automatic' | 'branch' | 'join' | 'end'
  processingStrategy: 'automatic' | 'single' | 'any' | 'all' | 'quorum'
  candidateRoles: string[]
  quorumCount: number | null
  configuration: JsonRecord
  sortOrder: number
}
type Edge = {
  edgeKey: string
  fromNodeKey: string
  toNodeKey: string
  priority: number
  condition: JsonRecord | null
}
type NodeFlow = {
  stages: Stage[]
  nodes: Node[]
  edges: Edge[]
  branches: JsonRecord[]
  joins: JsonRecord[]
  recoveryCommands: JsonRecord[]
  compensations: JsonRecord[]
}

const kinds = ['start', 'manual', 'automatic', 'branch', 'join', 'end']
  .map((value) => ({ value, label: value }))
const strategies = ['automatic', 'single', 'any', 'all', 'quorum']
  .map((value) => ({ value, label: value }))

export function ProjectWorkItemNodeFlowDesigner({
  spaceId,
  typeId,
  readOnly,
  draft,
  onDraftSaved,
  onDirtyChange,
}: {
  spaceId: string
  typeId: string
  readOnly: boolean
  draft: WorkItemConfigurationDraft
  onDraftSaved: (draft: WorkItemConfigurationDraft) => void
  onDirtyChange?: (dirty: boolean) => void
}) {
  const { message } = AntdApp.useApp()
  const snapshot = useMemo(() => asObject(draft.snapshot), [draft.snapshot])
  const [flow, setFlow] = useState<NodeFlow>(() => normalizeFlow(snapshot.nodeFlow))
  const [selected, setSelected] = useState(flow.nodes[0]?.nodeKey ?? '')
  const [zoom, setZoom] = useState(100)
  const [dirty, setDirty] = useState(false)
  const selectedNode = flow.nodes.find((node) => node.nodeKey === selected)
  const diagnostics = validateFlow(flow)

  const mutate = (update: (current: NodeFlow) => NodeFlow) => {
    setFlow((current) => update(current))
    setDirty(true)
    onDirtyChange?.(true)
  }
  const saveMutation = useMutation({
    mutationFn: () => saveWorkItemConfigurationDraft(
      spaceId,
      typeId,
      {
        ...snapshot,
        snapshotSchemaVersion: Math.max(Number(snapshot.snapshotSchemaVersion ?? 1), 3),
        nodeFlow: flow,
        stateFlow: undefined,
      },
      draft.aggregateVersion,
    ),
    onSuccess: (saved) => {
      setDirty(false)
      onDirtyChange?.(false)
      onDraftSaved(saved)
      message.success('节点流草稿已保存')
    },
    onError: (error) => message.error(errorMessage(error, '节点流草稿保存失败')),
  })
  const discardChanges = () => {
    const restored = normalizeFlow(snapshot.nodeFlow)
    setFlow(restored)
    setSelected(restored.nodes[0]?.nodeKey ?? '')
    setDirty(false)
    onDirtyChange?.(false)
  }

  const addStage = () => {
    const index = flow.stages.length + 1
    mutate((current) => ({
      ...current,
      stages: [...current.stages, {
        stageKey: uniqueKey(`stage_${index}`, current.stages.map((item) => item.stageKey)),
        label: `阶段 ${index}`,
        description: '',
        sortOrder: index * 10,
      }],
    }))
  }
  const addNode = () => {
    if (!flow.stages.length) return
    const index = flow.nodes.length + 1
    const nodeKey = uniqueKey(`node_${index}`, flow.nodes.map((item) => item.nodeKey))
    mutate((current) => ({
      ...current,
      nodes: [...current.nodes, {
        nodeKey,
        stageKey: current.stages[0].stageKey,
        label: `节点 ${index}`,
        description: '',
        kind: 'manual',
        processingStrategy: 'single',
        candidateRoles: ['owner'],
        quorumCount: null,
        configuration: {},
        sortOrder: index * 10,
      }],
    }))
    setSelected(nodeKey)
  }
  const updateNode = (patch: Partial<Node>) => mutate((current) => ({
    ...current,
    nodes: current.nodes.map((node) => node.nodeKey === selected ? { ...node, ...patch } : node),
  }))
  const removeNode = () => {
    mutate((current) => ({
      ...current,
      nodes: current.nodes.filter((node) => node.nodeKey !== selected),
      edges: current.edges.filter((edge) => edge.fromNodeKey !== selected && edge.toNodeKey !== selected),
      branches: current.branches.filter((branch) => branch.nodeKey !== selected),
      joins: current.joins.filter((join) => join.nodeKey !== selected),
    }))
    setSelected('')
  }
  const addEdge = () => {
    if (flow.nodes.length < 2) return
    const from = selectedNode?.nodeKey ?? flow.nodes[0].nodeKey
    const to = flow.nodes.find((node) => node.nodeKey !== from)?.nodeKey ?? from
    const index = flow.edges.length + 1
    mutate((current) => ({
      ...current,
      edges: [...current.edges, {
        edgeKey: uniqueKey(`edge_${index}`, current.edges.map((item) => item.edgeKey)),
        fromNodeKey: from,
        toNodeKey: to,
        priority: index,
        condition: null,
      }],
    }))
  }

  return (
    <CollapsibleWorkItemCard
      collapseLabel="节点流设计器"
      className="node-flow-designer"
      data-testid="work-item-node-flow-designer"
      title={(
        <Space>
          <ApartmentOutlined />
          <span>节点流设计器</span>
        </Space>
      )}
      extra={(
        <Space wrap size={[6, 6]}>
          <Tag color="blue">{flow.stages.length} 阶段</Tag>
          <Tag color="cyan">{flow.nodes.length} 节点</Tag>
          <Tag>{flow.edges.length} 连线</Tag>
          {dirty ? <Tag color="warning">未保存</Tag> : <Tag color="success">已同步</Tag>}
          <Button aria-label="缩小画布" icon={<ZoomOutOutlined />} onClick={() => setZoom(Math.max(75, zoom - 25))} />
          <Typography.Text>{zoom}%</Typography.Text>
          <Button aria-label="放大画布" icon={<ZoomInOutlined />} onClick={() => setZoom(Math.min(125, zoom + 25))} />
          <Button
            icon={<UndoOutlined />}
            disabled={readOnly || !dirty || saveMutation.isPending}
            onClick={discardChanges}
          >
            放弃修改
          </Button>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            disabled={readOnly || !dirty || diagnostics.length > 0}
            loading={saveMutation.isPending}
            onClick={() => saveMutation.mutate()}
          >
            保存节点流
          </Button>
        </Space>
      )}
    >
      {diagnostics.length ? (
        <Alert
          showIcon
          type="error"
          message="节点流存在阻断项"
          description={diagnostics.join('；')}
        />
      ) : null}
      <Space wrap className="node-flow-toolbar">
        <Button icon={<PlusOutlined />} disabled={readOnly} onClick={addStage}>阶段</Button>
        <Button icon={<PlusOutlined />} disabled={readOnly || !flow.stages.length} onClick={addNode}>节点</Button>
        <Button icon={<PlusOutlined />} disabled={readOnly || flow.nodes.length < 2} onClick={addEdge}>连线</Button>
      </Space>
      {flow.stages.length ? (
        <div
          className="node-flow-canvas"
          role="application"
          aria-label="节点流画布，可用方向键移动选中节点"
          style={{ transform: `scale(${zoom / 100})`, transformOrigin: 'top left' }}
          onKeyDown={(event) => {
            if (!selectedNode || !['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) return
            event.preventDefault()
            const delta = ['ArrowLeft', 'ArrowUp'].includes(event.key) ? -10 : 10
            updateNode({ sortOrder: Math.max(0, selectedNode.sortOrder + delta) })
          }}
          tabIndex={0}
        >
          {flow.stages.slice().sort(byOrder).map((stage) => (
            <section className="node-flow-stage" key={stage.stageKey} aria-label={stage.label}>
              <Input
                aria-label={`${stage.stageKey} 阶段名称`}
                value={stage.label}
                disabled={readOnly}
                onChange={(event) => mutate((current) => ({
                  ...current,
                  stages: current.stages.map((item) =>
                    item.stageKey === stage.stageKey ? { ...item, label: event.target.value } : item),
                }))}
              />
              <Typography.Text code>{stage.stageKey}</Typography.Text>
              {flow.nodes.filter((node) => node.stageKey === stage.stageKey).sort(byOrder).map((node) => (
                <button
                  type="button"
                  className={`node-flow-node${node.nodeKey === selected ? ' selected' : ''}`}
                  key={node.nodeKey}
                  onClick={() => setSelected(node.nodeKey)}
                  aria-pressed={node.nodeKey === selected}
                >
                  <Tag color={node.kind === 'end' ? 'success' : node.kind === 'branch' ? 'purple' : 'blue'}>
                    {node.kind}
                  </Tag>
                  <strong>{node.label}</strong>
                  <small>{node.nodeKey}</small>
                </button>
              ))}
            </section>
          ))}
        </div>
      ) : <Empty description="先创建阶段，再添加节点" />}
      <div className="node-flow-edge-list" aria-label="连线列表">
        {flow.edges.map((edge) => (
          <Space key={edge.edgeKey} wrap>
            <Input
              value={edge.edgeKey}
              disabled={readOnly}
              onChange={(event) => mutate((current) => ({
                ...current,
                edges: current.edges.map((item) => item.edgeKey === edge.edgeKey
                  ? { ...item, edgeKey: event.target.value } : item),
              }))}
            />
            <Select
              value={edge.fromNodeKey}
              options={flow.nodes.map((node) => ({ value: node.nodeKey, label: node.label }))}
              disabled={readOnly}
              onChange={(value) => mutate((current) => ({ ...current, edges: current.edges.map((item) =>
                item.edgeKey === edge.edgeKey ? { ...item, fromNodeKey: value } : item) }))}
            />
            <Typography.Text>→</Typography.Text>
            <Select
              value={edge.toNodeKey}
              options={flow.nodes.map((node) => ({ value: node.nodeKey, label: node.label }))}
              disabled={readOnly}
              onChange={(value) => mutate((current) => ({ ...current, edges: current.edges.map((item) =>
                item.edgeKey === edge.edgeKey ? { ...item, toNodeKey: value } : item) }))}
            />
            <Button
              danger
              aria-label={`删除连线 ${edge.edgeKey}`}
              icon={<MinusOutlined />}
              disabled={readOnly}
              onClick={() => mutate((current) => ({
                ...current,
                edges: current.edges.filter((item) => item.edgeKey !== edge.edgeKey),
              }))}
            />
          </Space>
        ))}
      </div>
      {selectedNode ? (
        <Collapse
          defaultActiveKey={['node', 'policy']}
          items={[
            {
              key: 'node',
              label: '节点与路由',
              children: (
                <Space wrap align="start">
                  <Input value={selectedNode.label} disabled={readOnly} addonBefore="名称" onChange={(e) => updateNode({ label: e.target.value })} />
                  <Select value={selectedNode.stageKey} disabled={readOnly} options={flow.stages.map((stage) => ({ value: stage.stageKey, label: stage.label }))} onChange={(stageKey) => updateNode({ stageKey })} />
                  <Select value={selectedNode.kind} disabled={readOnly} options={kinds} onChange={(kind) => updateNode({ kind })} />
                  <Select value={selectedNode.processingStrategy} disabled={readOnly} options={strategies} onChange={(processingStrategy) => updateNode({ processingStrategy })} />
                  <Select mode="tags" value={selectedNode.candidateRoles} disabled={readOnly} tokenSeparators={[',']} onChange={(candidateRoles) => updateNode({ candidateRoles })} placeholder="候选角色" />
                  <InputNumber min={1} value={selectedNode.quorumCount ?? undefined} disabled={readOnly || selectedNode.processingStrategy !== 'quorum'} onChange={(value) => updateNode({ quorumCount: value })} addonBefore="法定人数" />
                  <Button danger icon={<DeleteOutlined />} disabled={readOnly} onClick={removeNode}>删除节点</Button>
                </Space>
              ),
            },
            {
              key: 'policy',
              label: '表单、指派、产物、时限',
              children: (
                <NodeConfigurationEditor
                  key={`${selectedNode.nodeKey}:${JSON.stringify(selectedNode.configuration)}`}
                  configuration={selectedNode.configuration}
                  readOnly={readOnly}
                  onApply={(configuration) => updateNode({ configuration })}
                />
              ),
            },
            {
              key: 'recovery',
              label: '分支、汇聚、恢复与补偿',
              children: (
                <Input.TextArea
                  rows={10}
                  aria-label="高级节点流策略 JSON"
                  disabled={readOnly}
                  value={JSON.stringify({
                    branches: flow.branches,
                    joins: flow.joins,
                    recoveryCommands: flow.recoveryCommands,
                    compensations: flow.compensations,
                  }, null, 2)}
                  onChange={(event) => {
                    try {
                      const value = JSON.parse(event.target.value) as Partial<NodeFlow>
                      mutate((current) => ({
                        ...current,
                        branches: value.branches ?? [],
                        joins: value.joins ?? [],
                        recoveryCommands: value.recoveryCommands ?? [],
                        compensations: value.compensations ?? [],
                      }))
                    } catch {
                      // Keep last valid JSON; server performs authoritative validation.
                    }
                  }}
                />
              ),
            },
          ]}
        />
      ) : null}
    </CollapsibleWorkItemCard>
  )
}

function NodeConfigurationEditor({
  configuration,
  readOnly,
  onApply,
}: {
  configuration: JsonRecord
  readOnly: boolean
  onApply: (configuration: JsonRecord) => void
}) {
  const [draft, setDraft] = useState(() => JSON.stringify(configuration, null, 2))
  const [error, setError] = useState(false)

  const apply = () => {
    try {
      const parsed = JSON.parse(draft) as unknown
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
        throw new Error('configuration must be an object')
      }
      setError(false)
      onApply(parsed as JsonRecord)
    } catch {
      setError(true)
    }
  }

  return (
    <div>
      <Input.TextArea
        rows={9}
        aria-label="节点配置 JSON"
        disabled={readOnly}
        value={draft}
        status={error ? 'error' : undefined}
        onChange={(event) => {
          setDraft(event.target.value)
          if (error) setError(false)
        }}
        onBlur={apply}
      />
      {error ? <Typography.Text type="danger">JSON 格式不正确，配置必须是 JSON 对象</Typography.Text> : null}
    </div>
  )
}

function normalizeFlow(input: unknown): NodeFlow {
  const value = asObject(input)
  return {
    stages: Array.isArray(value.stages) ? value.stages as Stage[] : [],
    nodes: Array.isArray(value.nodes) ? value.nodes as Node[] : [],
    edges: Array.isArray(value.edges) ? value.edges as Edge[] : [],
    branches: Array.isArray(value.branches) ? value.branches as JsonRecord[] : [],
    joins: Array.isArray(value.joins) ? value.joins as JsonRecord[] : [],
    recoveryCommands: Array.isArray(value.recoveryCommands) ? value.recoveryCommands as JsonRecord[] : [],
    compensations: Array.isArray(value.compensations) ? value.compensations as JsonRecord[] : [],
  }
}

function validateFlow(flow: NodeFlow) {
  const errors: string[] = []
  const unique = (values: string[]) => new Set(values).size === values.length
  if (!unique(flow.stages.map((item) => item.stageKey))) errors.push('阶段 key 重复')
  if (!unique(flow.nodes.map((item) => item.nodeKey))) errors.push('节点 key 重复')
  if (!unique(flow.edges.map((item) => item.edgeKey))) errors.push('连线 key 重复')
  const stages = new Set(flow.stages.map((item) => item.stageKey))
  const nodes = new Set(flow.nodes.map((item) => item.nodeKey))
  if (flow.nodes.some((item) => !stages.has(item.stageKey))) errors.push('节点引用了不存在的阶段')
  if (flow.edges.some((item) => !nodes.has(item.fromNodeKey) || !nodes.has(item.toNodeKey))) {
    errors.push('连线引用了不存在的节点')
  }
  return errors
}

function asObject(input: unknown): JsonRecord {
  return input && typeof input === 'object' && !Array.isArray(input) ? input as JsonRecord : {}
}

function uniqueKey(base: string, existing: string[]) {
  let value = base
  let suffix = 2
  while (existing.includes(value)) value = `${base}_${suffix++}`
  return value
}

function byOrder<T extends { sortOrder: number }>(left: T, right: T) {
  return left.sortOrder - right.sortOrder
}
