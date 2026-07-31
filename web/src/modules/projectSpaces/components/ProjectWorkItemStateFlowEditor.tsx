import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  DeleteOutlined,
  ForkOutlined,
  PlusOutlined,
  SaveOutlined,
} from '@ant-design/icons'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Collapse,
  Empty,
  Input,
  Select,
  Space,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import { useMemo, useState } from 'react'

import {
  saveWorkItemConfigurationDraft,
  workItemConfigurationDraftKeys,
  type WorkItemConfigurationDraft,
} from '../api/workItemConfigurationApi'
import { errorMessage } from '../projectSpaceView'

type JsonRecord = Record<string, unknown>
type StateDefinition = {
  stateKey: string
  label: string
  description: string
  color: string
  category: 'initial' | 'active' | 'terminal' | 'canceled'
  sortOrder: number
}
type ActionDefinition = {
  actionKey: string
  label: string
  description: string
  kind: 'forward' | 'return' | 'reopen' | 'terminate' | 'restore'
  authorizedRoles: string[]
  requiredFieldKeys: string[]
  fieldPatch: JsonRecord
  sideEffectKeys: string[]
  sortOrder: number
}
type TransitionDefinition = {
  transitionKey: string
  actionKey: string
  fromStateKey: string
  toStateKey: string
  guardKey: string | null
  sortOrder: number
}
type GuardDefinition = {
  guardKey: string
  kind: 'field' | 'participant' | 'space_role' | 'all' | 'any' | 'not'
  operator: string
  fieldKey: string | null
  participantRole: string | null
  spaceRoles: string[]
  value: unknown
  guardKeys: string[]
}
type StateFlow = {
  states: StateDefinition[]
  actions: ActionDefinition[]
  transitions: TransitionDefinition[]
  guards: GuardDefinition[]
}
type LocalStateFlowEdit = {
  flow: StateFlow
  baseServerFlowSignature: string
}

const roleOptions = ['owner', 'admin', 'member', 'guest', 'assignee', 'collaborator', 'watcher']
  .map((value) => ({ label: value, value }))
const categoryOptions = ['initial', 'active', 'terminal', 'canceled']
  .map((value) => ({ label: value, value }))
const actionKindOptions = ['forward', 'return', 'reopen', 'terminate', 'restore']
  .map((value) => ({ label: value, value }))
const guardKindOptions = ['field', 'participant', 'space_role', 'all', 'any', 'not']
  .map((value) => ({ label: value, value }))

export function ProjectWorkItemStateFlowEditor({
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
  onDirtyChange: (dirty: boolean) => void
}) {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const snapshot = useMemo(() => normalizeObject(draft.snapshot), [draft.snapshot])
  const serverFlow = useMemo(() => normalizeFlow(snapshot.stateFlow), [snapshot.stateFlow])
  const serverFlowSignature = useMemo(() => JSON.stringify(serverFlow), [serverFlow])
  const [localEdit, setLocalEdit] = useState<LocalStateFlowEdit | null>(null)
  const flow = localEdit?.flow ?? serverFlow
  const dirty = localEdit !== null
  const serverFlowChanged = localEdit !== null
    && localEdit.baseServerFlowSignature !== serverFlowSignature
  const fieldKeys = useMemo(() => normalizeArray<JsonRecord>(snapshot.fields)
    .map((field) => String(field.fieldKey ?? ''))
    .filter(Boolean), [snapshot.fields])
  const localDiagnostics = useMemo(() => localValidate(flow), [flow])

  const update = (next: StateFlow) => {
    if (!localEdit) onDirtyChange(true)
    setLocalEdit({
      flow: normalizeSortOrders(next),
      baseServerFlowSignature: localEdit?.baseServerFlowSignature ?? serverFlowSignature,
    })
  }
  const saveMutation = useMutation({
    mutationFn: () => {
      if (!localEdit || serverFlowChanged) {
        throw new Error('State flow local edit is unavailable or conflicts with the server draft')
      }
      const next = structuredClone(snapshot)
      next.stateFlow = localEdit.flow
      return saveWorkItemConfigurationDraft(spaceId, typeId, next, draft.aggregateVersion)
    },
    onSuccess: async (saved) => {
      setLocalEdit(null)
      onDirtyChange(false)
      onDraftSaved(saved)
      await queryClient.invalidateQueries({ queryKey: ['work-item-configuration-versions', spaceId, typeId] })
      message.success('状态流已保存到配置草稿')
    },
    onError: (error) => {
      void queryClient.invalidateQueries({
        queryKey: workItemConfigurationDraftKeys.detail(spaceId, typeId),
      })
      message.error(errorMessage(error, '保存失败，本地状态流输入已保留'))
    },
  })
  const editorReadOnly = readOnly || saveMutation.isPending
  const discardLocalEdit = () => {
    if (saveMutation.isPending) return
    setLocalEdit(null)
    onDirtyChange(false)
  }

  return (
    <Card
      className="work-item-state-flow-editor"
      data-testid="work-item-state-flow-editor"
      title={<Space><ForkOutlined /><span>轻量状态流配置</span>{dirty ? <Tag color="warning">未保存</Tag> : null}</Space>}
      extra={(
        <Space>
          <Button
            disabled={!dirty || saveMutation.isPending}
            onClick={discardLocalEdit}
          >
            放弃本地修改
          </Button>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            disabled={
              readOnly
              || !dirty
              || serverFlowChanged
              || localDiagnostics.some((item) => item.severity === 'error')
            }
            loading={saveMutation.isPending}
            onClick={() => saveMutation.mutate()}
          >
            保存到草稿
          </Button>
        </Space>
      )}
    >
      <Alert
        type="info"
        showIcon
        message="定义随完整配置草稿发布"
        description="永久 key 用于历史、事件和兼容映射；修改展示名不会改变 key。此处不创建节点 token、并行或会签。"
      />
      {serverFlowChanged ? (
        <Alert
          data-testid="work-item-state-flow-conflict"
          type="error"
          showIcon
          message="服务器状态流已变化"
          description="本地输入已保留，但不能覆盖新的服务器状态流。请先复制需要保留的内容，再放弃本地修改并基于最新草稿重新编辑。"
        />
      ) : null}
      <div className="work-item-state-flow-diagnostics" aria-live="polite">
        {[...localDiagnostics, ...draft.diagnostics
          .filter((item) => item.keyPath.startsWith('stateFlow'))]
          .map((diagnostic) => (
            <Tag
              key={`${diagnostic.code}:${diagnostic.keyPath}`}
              color={diagnostic.severity === 'error' ? 'error' : 'warning'}
            >
              {diagnostic.keyPath} · {diagnostic.code}
            </Tag>
          ))}
        {localDiagnostics.length === 0
          && !draft.diagnostics.some((item) => item.keyPath.startsWith('stateFlow'))
          ? <Typography.Text type="secondary">当前编辑图没有本地诊断；保存后仍需执行服务端校验。</Typography.Text>
          : null}
      </div>
      <Tabs
        destroyOnHidden={false}
        items={[
          {
            key: 'states',
            label: `状态 ${flow.states.length}`,
            children: (
              <DefinitionCollection
                title="状态"
                empty="还没有状态。有效状态流必须恰好有一个 initial。"
                items={flow.states}
                readOnly={editorReadOnly}
                add={() => update({
                  ...flow,
                  states: [...flow.states, {
                    stateKey: nextKey('state', flow.states.map((item) => item.stateKey)),
                    label: '新状态',
                    description: '',
                    color: '',
                    category: flow.states.length === 0 ? 'initial' : 'active',
                    sortOrder: (flow.states.length + 1) * 100,
                  }],
                })}
                remove={(index) => update({
                  ...flow,
                  states: flow.states.filter((_, itemIndex) => itemIndex !== index),
                })}
                move={(index, offset) => update({
                  ...flow,
                  states: move(flow.states, index, offset),
                })}
                render={(state, index) => (
                  <div className="state-flow-definition-grid">
                    <Labeled label="永久 key"><Input value={state.stateKey} disabled={editorReadOnly} onChange={(event) => updateAt(flow, 'states', index, { ...state, stateKey: event.target.value }, update)} /></Labeled>
                    <Labeled label="展示名"><Input value={state.label} disabled={editorReadOnly} onChange={(event) => updateAt(flow, 'states', index, { ...state, label: event.target.value }, update)} /></Labeled>
                    <Labeled label="分类"><Select value={state.category} disabled={editorReadOnly} options={categoryOptions} onChange={(category) => updateAt(flow, 'states', index, { ...state, category }, update)} /></Labeled>
                    <Labeled label="颜色"><Input value={state.color} disabled={editorReadOnly} placeholder="#1677ff 或语义色" onChange={(event) => updateAt(flow, 'states', index, { ...state, color: event.target.value }, update)} /></Labeled>
                    <Labeled label="说明" wide><Input value={state.description} disabled={editorReadOnly} onChange={(event) => updateAt(flow, 'states', index, { ...state, description: event.target.value }, update)} /></Labeled>
                  </div>
                )}
              />
            ),
          },
          {
            key: 'actions',
            label: `动作 ${flow.actions.length}`,
            children: (
              <DefinitionCollection
                title="动作"
                empty="还没有动作。动作必须通过转换连接来源和目标状态。"
                items={flow.actions}
                readOnly={editorReadOnly}
                add={() => update({
                  ...flow,
                  actions: [...flow.actions, {
                    actionKey: nextKey('action', flow.actions.map((item) => item.actionKey)),
                    label: '新动作',
                    description: '',
                    kind: 'forward',
                    authorizedRoles: ['owner', 'admin', 'member'],
                    requiredFieldKeys: [],
                    fieldPatch: {},
                    sideEffectKeys: [],
                    sortOrder: (flow.actions.length + 1) * 100,
                  }],
                })}
                remove={(index) => update({
                  ...flow,
                  actions: flow.actions.filter((_, itemIndex) => itemIndex !== index),
                })}
                move={(index, offset) => update({
                  ...flow,
                  actions: move(flow.actions, index, offset),
                })}
                render={(action, index) => (
                  <div className="state-flow-definition-grid">
                    <Labeled label="永久 key"><Input value={action.actionKey} disabled={editorReadOnly} onChange={(event) => updateAt(flow, 'actions', index, { ...action, actionKey: event.target.value }, update)} /></Labeled>
                    <Labeled label="展示名"><Input value={action.label} disabled={editorReadOnly} onChange={(event) => updateAt(flow, 'actions', index, { ...action, label: event.target.value }, update)} /></Labeled>
                    <Labeled label="动作类型"><Select value={action.kind} disabled={editorReadOnly} options={actionKindOptions} onChange={(kind) => updateAt(flow, 'actions', index, { ...action, kind }, update)} /></Labeled>
                    <Labeled label="授权角色" wide><Select mode="multiple" value={action.authorizedRoles} disabled={editorReadOnly} options={roleOptions} onChange={(authorizedRoles) => updateAt(flow, 'actions', index, { ...action, authorizedRoles }, update)} /></Labeled>
                    <Labeled label="必填字段" wide><Select mode="multiple" value={action.requiredFieldKeys} disabled={editorReadOnly} options={fieldKeys.map((value) => ({ label: value, value }))} onChange={(requiredFieldKeys) => updateAt(flow, 'actions', index, { ...action, requiredFieldKeys }, update)} /></Labeled>
                    <Labeled label="说明" wide><Input value={action.description} disabled={editorReadOnly} onChange={(event) => updateAt(flow, 'actions', index, { ...action, description: event.target.value }, update)} /></Labeled>
                  </div>
                )}
              />
            ),
          },
          {
            key: 'transitions',
            label: `转换 ${flow.transitions.length}`,
            children: (
              <DefinitionCollection
                title="连接"
                empty="还没有转换。使用“新增连接”把动作连接到来源和目标状态。"
                items={flow.transitions}
                readOnly={editorReadOnly}
                add={() => update({
                  ...flow,
                  transitions: [...flow.transitions, {
                    transitionKey: nextKey('transition', flow.transitions.map((item) => item.transitionKey)),
                    actionKey: flow.actions[0]?.actionKey ?? '',
                    fromStateKey: flow.states[0]?.stateKey ?? '',
                    toStateKey: flow.states[1]?.stateKey ?? flow.states[0]?.stateKey ?? '',
                    guardKey: null,
                    sortOrder: (flow.transitions.length + 1) * 100,
                  }],
                })}
                remove={(index) => update({
                  ...flow,
                  transitions: flow.transitions.filter((_, itemIndex) => itemIndex !== index),
                })}
                move={(index, offset) => update({
                  ...flow,
                  transitions: move(flow.transitions, index, offset),
                })}
                render={(transition, index) => (
                  <div className="state-flow-definition-grid">
                    <Labeled label="转换 key"><Input value={transition.transitionKey} disabled={editorReadOnly} onChange={(event) => updateAt(flow, 'transitions', index, { ...transition, transitionKey: event.target.value }, update)} /></Labeled>
                    <Labeled label="动作"><Select value={transition.actionKey} disabled={editorReadOnly} options={flow.actions.map((item) => ({ label: `${item.label} (${item.actionKey})`, value: item.actionKey }))} onChange={(actionKey) => updateAt(flow, 'transitions', index, { ...transition, actionKey }, update)} /></Labeled>
                    <Labeled label="来源状态"><Select value={transition.fromStateKey} disabled={editorReadOnly} options={stateOptions(flow)} onChange={(fromStateKey) => updateAt(flow, 'transitions', index, { ...transition, fromStateKey }, update)} /></Labeled>
                    <Labeled label="目标状态"><Select value={transition.toStateKey} disabled={editorReadOnly} options={stateOptions(flow)} onChange={(toStateKey) => updateAt(flow, 'transitions', index, { ...transition, toStateKey }, update)} /></Labeled>
                    <Labeled label="守卫"><Select allowClear value={transition.guardKey ?? undefined} disabled={editorReadOnly} options={flow.guards.map((item) => ({ label: item.guardKey, value: item.guardKey }))} onChange={(guardKey) => updateAt(flow, 'transitions', index, { ...transition, guardKey: guardKey ?? null }, update)} /></Labeled>
                  </div>
                )}
              />
            ),
          },
          {
            key: 'guards',
            label: `守卫 ${flow.guards.length}`,
            children: (
              <DefinitionCollection
                title="声明式守卫"
                empty="没有守卫。无守卫转换仍受服务端动作授权和生命周期规则约束。"
                items={flow.guards}
                readOnly={editorReadOnly}
                add={() => update({
                  ...flow,
                  guards: [...flow.guards, {
                    guardKey: nextKey('guard', flow.guards.map((item) => item.guardKey)),
                    kind: 'field',
                    operator: 'present',
                    fieldKey: fieldKeys[0] ?? null,
                    participantRole: null,
                    spaceRoles: [],
                    value: null,
                    guardKeys: [],
                  }],
                })}
                remove={(index) => update({
                  ...flow,
                  guards: flow.guards.filter((_, itemIndex) => itemIndex !== index),
                })}
                move={() => undefined}
                render={(guard, index) => (
                  <div className="state-flow-definition-grid">
                    <Labeled label="守卫 key"><Input value={guard.guardKey} disabled={editorReadOnly} onChange={(event) => updateAt(flow, 'guards', index, { ...guard, guardKey: event.target.value }, update)} /></Labeled>
                    <Labeled label="类型"><Select value={guard.kind} disabled={editorReadOnly} options={guardKindOptions} onChange={(kind) => updateAt(flow, 'guards', index, { ...guard, kind }, update)} /></Labeled>
                    <Labeled label="操作符"><Input value={guard.operator} disabled={editorReadOnly} placeholder="present / eq / in / has_role" onChange={(event) => updateAt(flow, 'guards', index, { ...guard, operator: event.target.value }, update)} /></Labeled>
                    <Labeled label="字段"><Select allowClear value={guard.fieldKey ?? undefined} disabled={editorReadOnly} options={fieldKeys.map((value) => ({ label: value, value }))} onChange={(fieldKey) => updateAt(flow, 'guards', index, { ...guard, fieldKey: fieldKey ?? null }, update)} /></Labeled>
                    <Labeled label="参与者角色"><Select allowClear value={guard.participantRole ?? undefined} disabled={editorReadOnly} options={roleOptions} onChange={(participantRole) => updateAt(flow, 'guards', index, { ...guard, participantRole: participantRole ?? null }, update)} /></Labeled>
                    <Labeled label="空间角色" wide><Select mode="multiple" value={guard.spaceRoles} disabled={editorReadOnly} options={roleOptions.slice(0, 4)} onChange={(spaceRoles) => updateAt(flow, 'guards', index, { ...guard, spaceRoles }, update)} /></Labeled>
                    <Labeled label="组合守卫" wide><Select mode="multiple" value={guard.guardKeys} disabled={editorReadOnly} options={flow.guards.filter((item) => item.guardKey !== guard.guardKey).map((item) => ({ label: item.guardKey, value: item.guardKey }))} onChange={(guardKeys) => updateAt(flow, 'guards', index, { ...guard, guardKeys }, update)} /></Labeled>
                    <Labeled label="比较值（JSON）" wide><Input value={guard.value == null ? '' : JSON.stringify(guard.value)} disabled={editorReadOnly} onChange={(event) => updateAt(flow, 'guards', index, { ...guard, value: parseJsonValue(event.target.value) }, update)} /></Labeled>
                  </div>
                )}
              />
            ),
          },
          {
            key: 'preview',
            label: '预览',
            children: <StateFlowPreview flow={flow} />,
          },
        ]}
      />
    </Card>
  )
}

function DefinitionCollection<T>({
  title,
  empty,
  items,
  readOnly,
  add,
  remove,
  move: moveItem,
  render,
}: {
  title: string
  empty: string
  items: T[]
  readOnly: boolean
  add: () => void
  remove: (index: number) => void
  move: (index: number, offset: number) => void
  render: (item: T, index: number) => React.ReactNode
}) {
  return (
    <div className="state-flow-definition-collection">
      <div className="state-flow-definition-heading">
        <Typography.Text type="secondary">拖序使用上下按钮，保存时顺序会规范化为稳定 sortOrder。</Typography.Text>
        <Button icon={<PlusOutlined />} disabled={readOnly} onClick={add}>新增{title}</Button>
      </div>
      {items.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={empty} /> : null}
      <Collapse
        items={items.map((item, index) => ({
          key: String(index),
          label: `${title} ${index + 1}`,
          extra: (
            <Space onClick={(event) => event.stopPropagation()}>
              <Button aria-label={`上移${title} ${index + 1}`} icon={<ArrowUpOutlined />} disabled={readOnly || index === 0} onClick={() => moveItem(index, -1)} />
              <Button aria-label={`下移${title} ${index + 1}`} icon={<ArrowDownOutlined />} disabled={readOnly || index === items.length - 1} onClick={() => moveItem(index, 1)} />
              <Button danger aria-label={`删除${title} ${index + 1}`} icon={<DeleteOutlined />} disabled={readOnly} onClick={() => remove(index)} />
            </Space>
          ),
          children: render(item, index),
        }))}
      />
    </div>
  )
}

function StateFlowPreview({ flow }: { flow: StateFlow }) {
  const outgoing = new Map<string, TransitionDefinition[]>()
  flow.transitions.forEach((transition) => {
    outgoing.set(transition.fromStateKey, [...(outgoing.get(transition.fromStateKey) ?? []), transition])
  })
  return (
    <div className="state-flow-preview" aria-label="状态流预览">
      {flow.states.map((state) => (
        <div className={`state-flow-preview-state category-${state.category}`} key={state.stateKey}>
          <div>
            <Tag color={state.category === 'terminal' ? 'success' : state.category === 'canceled' ? 'default' : 'processing'}>
              {state.category}
            </Tag>
            <Typography.Text strong>{state.label || state.stateKey}</Typography.Text>
            <Typography.Text code>{state.stateKey}</Typography.Text>
          </div>
          <div className="state-flow-preview-transitions">
            {(outgoing.get(state.stateKey) ?? []).map((transition) => (
              <Tag key={transition.transitionKey}>
                {transition.actionKey} → {transition.toStateKey}{transition.guardKey ? ` · ${transition.guardKey}` : ''}
              </Tag>
            ))}
            {(outgoing.get(state.stateKey) ?? []).length === 0
              ? <Typography.Text type="secondary">无出向转换</Typography.Text>
              : null}
          </div>
        </div>
      ))}
    </div>
  )
}

function Labeled({ label, wide, children }: { label: string; wide?: boolean; children: React.ReactNode }) {
  return (
    <label className={wide ? 'state-flow-field state-flow-field-wide' : 'state-flow-field'}>
      <span>{label}</span>
      {children}
    </label>
  )
}

function normalizeFlow(value: unknown): StateFlow {
  const source = normalizeObject(value)
  return normalizeSortOrders({
    states: normalizeArray<StateDefinition>(source.states),
    actions: normalizeArray<ActionDefinition>(source.actions).map((action) => ({
      ...action,
      kind: String(action.kind) === 'return_action' ? 'return' : action.kind,
      authorizedRoles: normalizeArray<string>(action.authorizedRoles),
      requiredFieldKeys: normalizeArray<string>(action.requiredFieldKeys),
      fieldPatch: normalizeObject(action.fieldPatch),
      sideEffectKeys: normalizeArray<string>(action.sideEffectKeys),
    })),
    transitions: normalizeArray<TransitionDefinition>(source.transitions),
    guards: normalizeArray<GuardDefinition>(source.guards).map((guard) => ({
      ...guard,
      spaceRoles: normalizeArray<string>(guard.spaceRoles),
      guardKeys: normalizeArray<string>(guard.guardKeys),
    })),
  })
}

function normalizeSortOrders(flow: StateFlow): StateFlow {
  return {
    ...flow,
    states: flow.states.map((item, index) => ({ ...item, sortOrder: (index + 1) * 100 })),
    actions: flow.actions.map((item, index) => ({ ...item, sortOrder: (index + 1) * 100 })),
    transitions: flow.transitions.map((item, index) => ({ ...item, sortOrder: (index + 1) * 100 })),
  }
}

function normalizeObject(value: unknown): JsonRecord {
  return value != null && typeof value === 'object' && !Array.isArray(value)
    ? structuredClone(value as JsonRecord)
    : {}
}

function normalizeArray<T>(value: unknown): T[] {
  return Array.isArray(value) ? structuredClone(value) as T[] : []
}

function stateOptions(flow: StateFlow) {
  return flow.states.map((state) => ({
    label: `${state.label || state.stateKey} (${state.stateKey})`,
    value: state.stateKey,
  }))
}

function move<T>(items: T[], index: number, offset: number) {
  const target = index + offset
  if (target < 0 || target >= items.length) return items
  const next = [...items]
  ;[next[index], next[target]] = [next[target], next[index]]
  return next
}

function updateAt<K extends keyof StateFlow>(
  flow: StateFlow,
  key: K,
  index: number,
  value: StateFlow[K][number],
  update: (flow: StateFlow) => void,
) {
  const items = [...flow[key]] as StateFlow[K]
  items[index] = value
  update({ ...flow, [key]: items })
}

function nextKey(prefix: string, existing: string[]) {
  let index = existing.length + 1
  while (existing.includes(`${prefix}_${index}`)) index += 1
  return `${prefix}_${index}`
}

function parseJsonValue(value: string) {
  if (!value.trim()) return null
  try {
    return JSON.parse(value) as unknown
  } catch {
    return value
  }
}

function localValidate(flow: StateFlow) {
  const diagnostics: Array<{
    code: string
    severity: 'warning' | 'error'
    keyPath: string
    message: string
  }> = []
  const keyPattern = /^[a-z][a-z0-9_]{0,63}$/
  const check = (values: string[], path: string) => {
    values.forEach((value, index) => {
      if (!keyPattern.test(value)) diagnostics.push({
        code: 'invalid_semantic_key',
        severity: 'error',
        keyPath: `${path}[${index}]`,
        message: '永久 key 必须使用小写字母、数字和下划线',
      })
    })
    if (new Set(values).size !== values.length) diagnostics.push({
      code: 'duplicate_semantic_key',
      severity: 'error',
      keyPath: path,
      message: '永久 key 不能重复',
    })
  }
  check(flow.states.map((item) => item.stateKey), 'stateFlow.states')
  check(flow.actions.map((item) => item.actionKey), 'stateFlow.actions')
  check(flow.transitions.map((item) => item.transitionKey), 'stateFlow.transitions')
  check(flow.guards.map((item) => item.guardKey), 'stateFlow.guards')
  if (flow.states.filter((item) => item.category === 'initial').length !== 1) diagnostics.push({
    code: 'initial_state_count',
    severity: 'error',
    keyPath: 'stateFlow.states',
    message: '必须恰好有一个 initial 状态',
  })
  if (!flow.states.some((item) => item.category === 'terminal')) diagnostics.push({
    code: 'terminal_state_missing',
    severity: 'warning',
    keyPath: 'stateFlow.states',
    message: '建议至少配置一个 terminal 状态',
  })
  return diagnostics
}
