export type WorkItemWorkflowMode = 'state' | 'node'

type JsonRecord = Record<string, unknown>

export function getWorkItemWorkflowMode(snapshot: unknown): WorkItemWorkflowMode {
  const value = asObject(snapshot)
  return isObject(value.nodeFlow) ? 'node' : 'state'
}

export function switchWorkItemWorkflowMode(
  snapshot: unknown,
  mode: WorkItemWorkflowMode,
): JsonRecord {
  const next = structuredClone(asObject(snapshot))
  if (mode === 'state') {
    delete next.nodeFlow
    next.snapshotSchemaVersion = Math.max(Number(next.snapshotSchemaVersion ?? 1), 2)
    if (!isObject(next.stateFlow)) next.stateFlow = createDefaultStateFlow()
  } else {
    delete next.stateFlow
    next.snapshotSchemaVersion = Math.max(Number(next.snapshotSchemaVersion ?? 1), 3)
    if (!isObject(next.nodeFlow)) next.nodeFlow = createDefaultNodeFlow()
  }
  return next
}

export function createDefaultStateFlow() {
  const roles = ['admin', 'member', 'owner']
  const action = (actionKey: string, label: string, kind: string, sortOrder: number) => ({
    actionKey,
    label,
    description: '',
    kind,
    authorizedRoles: roles,
    requiredFieldKeys: [],
    fieldPatch: {},
    sideEffectKeys: [],
    sortOrder,
  })
  return {
    states: [
      state('open', '待处理', 'initial', 100),
      state('in_progress', '处理中', 'active', 200),
      state('done', '已完成', 'terminal', 300),
      state('canceled', '已取消', 'canceled', 400),
    ],
    actions: [
      action('start_progress', '开始处理', 'forward', 100),
      action('complete', '完成', 'forward', 200),
      action('reopen', '重新打开', 'reopen', 300),
      action('terminate', '终止', 'terminate', 400),
      action('restore', '恢复', 'restore', 500),
    ],
    transitions: [
      transition('open_start_progress', 'start_progress', 'open', 'in_progress', 100),
      transition('in_progress_complete', 'complete', 'in_progress', 'done', 200),
      transition('done_reopen', 'reopen', 'done', 'open', 300),
      transition('open_terminate', 'terminate', 'open', 'canceled', 400),
      transition('in_progress_terminate', 'terminate', 'in_progress', 'canceled', 500),
      transition('canceled_restore', 'restore', 'canceled', 'open', 600),
    ],
    guards: [],
  }
}

export function createDefaultNodeFlow() {
  return {
    stages: [{ stageKey: 'main', label: '主流程', description: '', sortOrder: 100 }],
    nodes: [
      node('start', '开始', 'start', 'automatic', [], 100),
      node('processing', '处理', 'manual', 'single', ['owner'], 200),
      node('end', '结束', 'end', 'automatic', [], 300),
    ],
    edges: [
      { edgeKey: 'start_to_processing', fromNodeKey: 'start', toNodeKey: 'processing', priority: 100, condition: null },
      { edgeKey: 'processing_to_end', fromNodeKey: 'processing', toNodeKey: 'end', priority: 100, condition: null },
    ],
    branches: [],
    joins: [],
    recoveryCommands: [],
    compensations: [],
  }
}

function state(stateKey: string, label: string, category: string, sortOrder: number) {
  return { stateKey, label, description: '', color: '', category, sortOrder }
}

function transition(
  transitionKey: string,
  actionKey: string,
  fromStateKey: string,
  toStateKey: string,
  sortOrder: number,
) {
  return { transitionKey, actionKey, fromStateKey, toStateKey, guardKey: null, sortOrder }
}

function node(
  nodeKey: string,
  label: string,
  kind: string,
  processingStrategy: string,
  candidateRoles: string[],
  sortOrder: number,
) {
  return {
    nodeKey,
    stageKey: 'main',
    label,
    description: '',
    kind,
    processingStrategy,
    candidateRoles,
    quorumCount: null,
    configuration: {},
    sortOrder,
  }
}

function asObject(value: unknown): JsonRecord {
  return isObject(value) ? value : {}
}

function isObject(value: unknown): value is JsonRecord {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value))
}
