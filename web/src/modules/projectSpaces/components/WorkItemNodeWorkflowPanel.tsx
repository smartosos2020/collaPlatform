import {
  BranchesOutlined,
  CheckOutlined,
  HistoryOutlined,
  ReloadOutlined,
  SendOutlined,
  SwapOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Descriptions,
  Empty,
  Input,
  List,
  Modal,
  Select,
  Space,
  Spin,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import { useMemo, useState } from 'react'

import { ApiRequestError } from '../../../shared/api/httpClient'
import {
  executeNodeTaskAction,
  getNodeTask,
  getNodeWorkflow,
  getNodeWorkflowHistory,
  nodeWorkflowKeys,
  recoverNodeWorkflow,
  startNodeWorkflow,
  upgradeNodeWorkflow,
  type NodeTaskView,
} from '../api/workItemNodeWorkflowApi'
import type { WorkItem } from '../api/workItemsApi'
import { errorMessage, formatTime } from '../projectSpaceView'

export function WorkItemNodeWorkflowPanel({
  spaceId,
  item,
  online,
  refreshItem,
}: {
  spaceId: string
  item: WorkItem
  online: boolean
  refreshItem: () => Promise<void>
}) {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const [selectedTaskId, setSelectedTaskId] = useState('')
  const [fieldPatch, setFieldPatch] = useState<Record<string, unknown>>({})
  const [decision, setDecision] = useState('')
  const [targetAssigneeId, setTargetAssigneeId] = useState('')
  const [artifactManifest, setArtifactManifest] = useState('[]')
  const [recoveryOpen, setRecoveryOpen] = useState(false)
  const [upgradeOpen, setUpgradeOpen] = useState(false)
  const [recovery, setRecovery] = useState({ commandKey: '', reason: '', confirmation: '' })
  const [upgrade, setUpgrade] = useState({ targetTypeVersionId: '', nodeMap: '{}', reason: '', confirmation: '' })

  const presentationQuery = useQuery({
    queryKey: nodeWorkflowKeys.presentation(spaceId, item.id),
    queryFn: () => getNodeWorkflow(spaceId, item.id),
    retry: false,
    refetchOnWindowFocus: false,
  })
  const presentation = presentationQuery.data
  const effectiveTaskId = selectedTaskId || presentation?.tasks[0]?.id || ''
  const historyQuery = useQuery({
    queryKey: nodeWorkflowKeys.history(spaceId, item.id),
    queryFn: () => getNodeWorkflowHistory(spaceId, item.id),
    enabled: presentation?.capability === 'available',
    retry: false,
  })
  const taskQuery = useQuery({
    queryKey: nodeWorkflowKeys.task(spaceId, item.id, effectiveTaskId),
    queryFn: () => getNodeTask(spaceId, item.id, effectiveTaskId),
    enabled: Boolean(effectiveTaskId),
    retry: false,
  })

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: nodeWorkflowKeys.presentation(spaceId, item.id) }),
      queryClient.invalidateQueries({ queryKey: nodeWorkflowKeys.history(spaceId, item.id) }),
      effectiveTaskId
        ? queryClient.invalidateQueries({ queryKey: nodeWorkflowKeys.task(spaceId, item.id, effectiveTaskId) })
        : Promise.resolve(),
      refreshItem(),
    ])
  }
  const commandMutation = useMutation({
    mutationFn: async ({ operation, task }: { operation: string; task: NodeTaskView }) => {
      if (!presentation) throw new Error('节点流尚未加载')
      return executeNodeTaskAction(spaceId, item.id, task.id, operation, {
        expectedWorkItemVersion: presentation.workItemVersion,
        expectedInstanceVersion: presentation.aggregateVersion,
        decision: decision || undefined,
        targetAssigneeId: targetAssigneeId || undefined,
        fieldPatch: { ...(taskQuery.data?.values ?? {}), ...fieldPatch },
        artifacts: JSON.parse(artifactManifest) as Array<Record<string, unknown>>,
      })
    },
    onSuccess: async (result) => {
      message.success(result.replayed ? '命令已幂等重放' : '节点任务已更新')
      await refresh()
    },
    onError: async (error) => {
      if (error instanceof ApiRequestError && [409, 422].includes(error.status)) await refresh()
      message.error(errorMessage(error, '节点任务操作失败，输入内容已保留'))
    },
  })
  const startMutation = useMutation({
    mutationFn: () => startNodeWorkflow(spaceId, item.id, presentation?.workItemVersion ?? item.version),
    onSuccess: refresh,
    onError: (error) => message.error(errorMessage(error, '启动节点流失败')),
  })
  const recoveryMutation = useMutation({
    mutationFn: () => recoverNodeWorkflow(spaceId, item.id, recovery.commandKey, {
      expectedWorkItemVersion: presentation?.workItemVersion,
      expectedInstanceVersion: presentation?.aggregateVersion,
      reason: recovery.reason,
      confirmation: recovery.confirmation,
    }),
    onSuccess: async () => {
      setRecoveryOpen(false)
      message.success('恢复命令已执行')
      await refresh()
    },
    onError: async (error) => {
      if (error instanceof ApiRequestError && [409, 422].includes(error.status)) await refresh()
      message.error(errorMessage(error, '恢复失败，确认文本已保留'))
    },
  })
  const upgradeMutation = useMutation({
    mutationFn: () => upgradeNodeWorkflow(spaceId, item.id, {
      expectedWorkItemVersion: presentation?.workItemVersion,
      expectedInstanceVersion: presentation?.aggregateVersion,
      targetTypeVersionId: upgrade.targetTypeVersionId,
      nodeMap: JSON.parse(upgrade.nodeMap) as Record<string, unknown>,
      reason: upgrade.reason,
      confirmation: upgrade.confirmation,
    }),
    onSuccess: async () => {
      setUpgradeOpen(false)
      message.success('节点流实例升级完成')
      await refresh()
    },
    onError: async (error) => {
      if (error instanceof ApiRequestError && [409, 422].includes(error.status)) await refresh()
      message.error(errorMessage(error, '实例升级失败，映射内容已保留'))
    },
  })

  if (presentationQuery.isLoading) return <Card><Spin size="small" /> 正在加载节点流…</Card>
  if (presentationQuery.error instanceof ApiRequestError && presentationQuery.error.status === 404) return null
  if (presentationQuery.isError) {
    return <Alert type="error" showIcon message="节点流加载失败" action={<Button icon={<ReloadOutlined />} onClick={() => presentationQuery.refetch()}>重试</Button>} />
  }
  if (!presentation || presentation.capability === 'not_configured') return null
  if (presentation.capability === 'uninitialized') {
    return (
      <Card data-testid="work-item-node-workflow-panel" title={<Space><BranchesOutlined />节点流</Space>}>
        <Empty
          description="节点流已配置，实例尚未启动"
          image={Empty.PRESENTED_IMAGE_SIMPLE}
        >
          <Button type="primary" disabled={!online} loading={startMutation.isPending} onClick={() => startMutation.mutate()}>
            启动节点流
          </Button>
        </Empty>
      </Card>
    )
  }

  const tasksByNode = groupBy(presentation.tasks, (task) => task.nodeKey)
  return (
    <Card
      className="node-workflow-panel"
      data-testid="work-item-node-workflow-panel"
      title={<Space wrap><BranchesOutlined />节点流执行面<Tag color="processing">{presentation.status}</Tag><Tag>策略 v{presentation.policyVersion}</Tag></Space>}
      extra={(
        <Space wrap>
          {!online ? <Tag color="warning">离线，只读；输入会保留</Tag> : null}
          <Button onClick={() => setRecoveryOpen(true)}>恢复</Button>
          <Button icon={<SwapOutlined />} onClick={() => setUpgradeOpen(true)}>升级实例</Button>
          <Button icon={<ReloadOutlined />} onClick={refresh}>刷新</Button>
        </Space>
      )}
    >
      <Tabs
        items={[
          {
            key: 'graph',
            label: '实例图',
            children: (
              <div className="node-runtime-graph">
                {presentation.activeTokens.map((token) => (
                  <section key={token.id} className="node-runtime-column">
                    <Space wrap><Tag color="blue">{token.nodeKey}</Tag><Tag>{token.status}</Tag></Space>
                    <Typography.Text type="secondary">进入 {formatTime(token.enteredAt)}</Typography.Text>
                    {(tasksByNode[token.nodeKey] ?? []).map((task) => (
                      <button
                        type="button"
                        className={`node-runtime-task${task.id === selectedTaskId ? ' selected' : ''}`}
                        key={task.id}
                        onClick={() => {
                          setSelectedTaskId(task.id)
                          setFieldPatch({})
                        }}
                      >
                        <strong>{task.assignmentStrategy}</strong>
                        <span>{task.status}</span>
                        {task.dueAt ? <small>截止 {formatTime(task.dueAt)}</small> : null}
                      </button>
                    ))}
                  </section>
                ))}
              </div>
            ),
          },
          {
            key: 'task',
            label: `任务${presentation.tasks.length ? ` (${presentation.tasks.length})` : ''}`,
            children: effectiveTaskId ? (
              <TaskExecution
                loading={taskQuery.isLoading}
                task={taskQuery.data?.task}
                fields={taskQuery.data?.form?.fields ?? []}
                actions={taskQuery.data?.availableActions.map((action) => action.actionKey) ?? []}
                candidateCount={taskQuery.data?.candidateCount ?? 0}
                artifactPolicy={taskQuery.data?.artifactPolicy ?? []}
                artifacts={taskQuery.data?.artifacts ?? []}
                fieldPatch={{ ...(taskQuery.data?.values ?? {}), ...fieldPatch }}
                decision={decision}
                targetAssigneeId={targetAssigneeId}
                artifactManifest={artifactManifest}
                disabled={!online || commandMutation.isPending}
                onFieldPatch={setFieldPatch}
                onDecision={setDecision}
                onTargetAssignee={setTargetAssigneeId}
                onArtifactManifest={setArtifactManifest}
                onExecute={(operation) => {
                  if (taskQuery.data?.task) commandMutation.mutate({ operation, task: taskQuery.data.task })
                }}
              />
            ) : <Empty description="当前没有可执行任务" />,
          },
          {
            key: 'history',
            label: <Space><HistoryOutlined />历史</Space>,
            children: (
              <List
                loading={historyQuery.isLoading}
                dataSource={historyQuery.data ?? []}
                locale={{ emptyText: '暂无节点流历史' }}
                renderItem={(entry) => (
                  <List.Item>
                    <List.Item.Meta
                      title={<Space wrap><Tag>#{entry.sequenceNumber}</Tag><Typography.Text>{entry.eventKind}</Typography.Text>{entry.nodeKey ? <Tag color="blue">{entry.nodeKey}</Tag> : null}</Space>}
                      description={`${entry.actorClass} · ${formatTime(entry.occurredAt)}`}
                    />
                  </List.Item>
                )}
              />
            ),
          },
        ]}
      />
      <Modal
        title="受控恢复"
        open={recoveryOpen}
        okText="执行恢复"
        okButtonProps={{ danger: true, disabled: !online || !recovery.commandKey || !recovery.reason || !recovery.confirmation }}
        confirmLoading={recoveryMutation.isPending}
        onCancel={() => setRecoveryOpen(false)}
        onOk={() => recoveryMutation.mutate()}
      >
        <Space direction="vertical" className="node-command-form">
          <Select
            aria-label="恢复命令"
            value={recovery.commandKey || undefined}
            placeholder="选择或输入命令 key"
            showSearch
            options={['return_to_plan', 'jump_to_acceptance', 'terminate_delivery', 'correct_to_plan'].map((value) => ({ value, label: value }))}
            onChange={(commandKey) => setRecovery((value) => ({ ...value, commandKey }))}
          />
          <Input.TextArea aria-label="恢复原因" value={recovery.reason} onChange={(event) => setRecovery((value) => ({ ...value, reason: event.target.value }))} placeholder="必填恢复原因" />
          <Input aria-label="精确确认文本" value={recovery.confirmation} onChange={(event) => setRecovery((value) => ({ ...value, confirmation: event.target.value }))} placeholder="输入服务端要求的精确确认文本" />
        </Space>
      </Modal>
      <Modal
        title="显式升级节点流实例"
        open={upgradeOpen}
        okText="执行升级"
        okButtonProps={{ danger: true, disabled: !online || !upgrade.targetTypeVersionId || !upgrade.reason || !upgrade.confirmation }}
        confirmLoading={upgradeMutation.isPending}
        onCancel={() => setUpgradeOpen(false)}
        onOk={() => upgradeMutation.mutate()}
      >
        <Space direction="vertical" className="node-command-form">
          <Input value={upgrade.targetTypeVersionId} onChange={(event) => setUpgrade((value) => ({ ...value, targetTypeVersionId: event.target.value }))} placeholder="目标类型版本 UUID" />
          <Input.TextArea rows={5} value={upgrade.nodeMap} onChange={(event) => setUpgrade((value) => ({ ...value, nodeMap: event.target.value }))} placeholder="显式 one-to-one / split / merge 映射 JSON" />
          <Input.TextArea value={upgrade.reason} onChange={(event) => setUpgrade((value) => ({ ...value, reason: event.target.value }))} placeholder="升级原因" />
          <Input value={upgrade.confirmation} onChange={(event) => setUpgrade((value) => ({ ...value, confirmation: event.target.value }))} placeholder="精确确认文本" />
        </Space>
      </Modal>
    </Card>
  )
}

function TaskExecution({
  loading,
  task,
  fields,
  actions,
  candidateCount,
  artifactPolicy,
  artifacts,
  fieldPatch,
  decision,
  targetAssigneeId,
  artifactManifest,
  disabled,
  onFieldPatch,
  onDecision,
  onTargetAssignee,
  onArtifactManifest,
  onExecute,
}: {
  loading: boolean
  task?: NodeTaskView
  fields: Array<{ fieldKey: string; mode: string; required?: boolean }>
  actions: string[]
  candidateCount: number
  artifactPolicy: unknown
  artifacts: Array<Record<string, unknown>>
  fieldPatch: Record<string, unknown>
  decision: string
  targetAssigneeId: string
  artifactManifest: string
  disabled: boolean
  onFieldPatch: (value: Record<string, unknown>) => void
  onDecision: (value: string) => void
  onTargetAssignee: (value: string) => void
  onArtifactManifest: (value: string) => void
  onExecute: (operation: string) => void
}) {
  const visibleFields = useMemo(() => fields.filter((field) => field.mode !== 'hidden'), [fields])
  if (loading) return <Spin />
  if (!task) return <Empty description="任务上下文不可用" />
  return (
    <Space direction="vertical" className="node-task-execution">
      <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 4 }}>
        <Descriptions.Item label="节点">{task.nodeKey}</Descriptions.Item>
        <Descriptions.Item label="状态">{task.status}</Descriptions.Item>
        <Descriptions.Item label="受理人">{task.assigneeId ?? '候选池'}</Descriptions.Item>
        <Descriptions.Item label="候选人数">{candidateCount}</Descriptions.Item>
        <Descriptions.Item label="截止">{task.dueAt ? formatTime(task.dueAt) : '无'}</Descriptions.Item>
      </Descriptions>
      {visibleFields.map((field) => (
        <label key={field.fieldKey}>
          <Typography.Text>{field.fieldKey}{field.required ? ' *' : ''}</Typography.Text>
          <Input
            value={String(fieldPatch[field.fieldKey] ?? '')}
            readOnly={field.mode === 'read'}
            onChange={(event) => onFieldPatch({ ...fieldPatch, [field.fieldKey]: event.target.value })}
          />
        </label>
      ))}
      <Input.TextArea value={decision} onChange={(event) => onDecision(event.target.value)} placeholder="表决、提交或退回决定" />
      <Input value={targetAssigneeId} onChange={(event) => onTargetAssignee(event.target.value)} placeholder="转交目标用户 UUID（仅 transfer）" />
      {policyCount(artifactPolicy) > 0 ? (
        <Alert
          type="info"
          showIcon
          message={`交付物策略 ${policyCount(artifactPolicy)} 项 · 已提交 ${artifacts.length} 项`}
          description="先在附件区完成上传，再按服务端公开策略提交 fileId/objectId；临时上传不会自动成为节点交付物。"
        />
      ) : null}
      <Input.TextArea
        rows={3}
        value={artifactManifest}
        onChange={(event) => onArtifactManifest(event.target.value)}
        aria-label="节点交付物 manifest JSON"
        placeholder='[{"artifactKey":"delivery","kind":"file","fileId":"UUID"}]'
      />
      <Space wrap>
        {actions.map((action) => (
          <Button
            key={action}
            type={['complete', 'submit', 'vote'].includes(action) ? 'primary' : 'default'}
            danger={['withdraw', 'reject'].includes(action)}
            disabled={disabled}
            icon={action === 'transfer' ? <SendOutlined /> : <CheckOutlined />}
            onClick={() => onExecute(action)}
          >
            {action}
          </Button>
        ))}
      </Space>
    </Space>
  )
}

function groupBy<T>(items: T[], key: (item: T) => string) {
  return items.reduce<Record<string, T[]>>((result, item) => {
    const value = key(item)
    result[value] = [...(result[value] ?? []), item]
    return result
  }, {})
}

function policyCount(policy: unknown) {
  if (Array.isArray(policy)) return policy.length
  if (policy && typeof policy === 'object') {
    const artifacts = (policy as Record<string, unknown>).artifacts
    return Array.isArray(artifacts) ? artifacts.length : Object.keys(policy).length
  }
  return 0
}
