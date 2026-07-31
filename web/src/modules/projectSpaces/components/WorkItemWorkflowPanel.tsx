import {
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  HistoryOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  ToolOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Descriptions,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Space,
  Tag,
  Typography,
} from 'antd'
import { useMemo, useState } from 'react'

import { ApiRequestError } from '../../../shared/api/httpClient'
import type { UserProjectSpace } from '../api/projectSpacesApi'
import {
  listWorkItemParticipants,
  workItemKeys,
  type WorkItem,
  type WorkItemParticipant,
} from '../api/workItemsApi'
import {
  correctWorkItemWorkflowState,
  executeWorkItemWorkflowAction,
  getWorkItemWorkflow,
  listWorkItemWorkflowHistory,
  workItemWorkflowKeys,
  type WorkItemWorkflow,
  type WorkItemWorkflowAction,
} from '../api/workItemWorkflowApi'
import { errorMessage, formatTime } from '../projectSpaceView'

type WorkflowCommandDraft = {
  actionKey: string
  requestId: string
}

type NextActionSummary = {
  key: string
  label: string
  source: 'workflow' | 'item'
  primary: boolean
  danger: boolean
}

export function WorkItemActionSummary({
  item,
  onOpenWorkflow,
}: {
  item: WorkItem
  onOpenWorkflow: () => void
}) {
  const workflow = embeddedWorkflow(item)
  const participantsQuery = useQuery({
    queryKey: workItemKeys.participants(item.spaceId, item.id),
    queryFn: () => listWorkItemParticipants(item.spaceId, item.id),
    enabled: item.availableActions.includes('view'),
    retry: (count, error) => !(error instanceof ApiRequestError && [403, 404].includes(error.status)) && count < 2,
  })
  const nextActions = nextActionSummaries(item, workflow)
  const responsible = responsiblePeopleLabel(
    participantsQuery.data?.items,
    participantsQuery.isLoading,
    participantsQuery.isError,
  )
  const deadline = readableDeadline(item) ?? '未设置'

  return (
    <Card
      size="small"
      className="work-item-action-summary"
      data-testid="work-item-action-summary"
      style={{ marginBottom: 16 }}
      title="行动摘要"
    >
      <Descriptions size="small" column={{ xs: 1, sm: 3 }}>
        <Descriptions.Item label="当前状态">
          <Tag color={item.status === 'archived' ? 'default' : stateColor(workflow?.currentStateCategory)}>
            {currentStateLabel(workflow, item.status)}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="负责人">{responsible}</Descriptions.Item>
        <Descriptions.Item label="截止时间">{deadline}</Descriptions.Item>
      </Descriptions>
      <Space direction="vertical" size={6}>
        <Typography.Text strong>下一步动作</Typography.Text>
        {nextActions.length > 0 ? (
          <Space wrap>
            {nextActions.map((action) => (
              action.source === 'workflow' ? (
                <Button
                  key={action.key}
                  size="small"
                  type={action.primary ? 'primary' : 'default'}
                  danger={action.danger}
                  onClick={onOpenWorkflow}
                >
                  {action.label}
                </Button>
              ) : (
                <Tag key={action.key} color={action.danger ? 'red' : 'blue'}>
                  {action.label}
                </Tag>
              )
            ))}
          </Space>
        ) : (
          <Typography.Text type="secondary">
            当前没有需要你处理的动作；如有疑问，请联系负责人确认。
          </Typography.Text>
        )}
      </Space>
    </Card>
  )
}

export function WorkItemWorkflowPanel({
  space,
  item,
  fieldPatch,
  online,
  refreshItem,
}: {
  space: UserProjectSpace
  item: WorkItem
  fieldPatch: Record<string, unknown>
  online: boolean
  refreshItem: () => Promise<void>
}) {
  const { message, modal } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const [commandDraft, setCommandDraft] = useState<WorkflowCommandDraft | null>(null)
  const [correctionOpen, setCorrectionOpen] = useState(false)
  const [correctionTarget, setCorrectionTarget] = useState('')
  const [correctionReason, setCorrectionReason] = useState('')
  const workflowQuery = useQuery({
    queryKey: workItemWorkflowKeys.detail(space.id, item.id),
    queryFn: () => getWorkItemWorkflow(space.id, item.id),
    retry: (count, error) => !(error instanceof ApiRequestError && [403, 404].includes(error.status)) && count < 2,
  })
  const historyQuery = useQuery({
    queryKey: workItemWorkflowKeys.history(space.id, item.id),
    queryFn: () => listWorkItemWorkflowHistory(space.id, item.id),
    enabled: workflowQuery.data?.capability === 'available',
  })
  const workflow = workflowQuery.data
  const fieldLabels = useMemo(() => new Map(
    item.runtime.snapshot.fields.map((field) => [
      String(field.fieldKey),
      String(field.name ?? field.label ?? field.fieldKey),
    ]),
  ), [item.runtime.snapshot.fields])

  const refreshWorkflow = async () => {
    await Promise.all([
      refreshItem(),
      queryClient.invalidateQueries({
        queryKey: workItemWorkflowKeys.detail(space.id, item.id),
      }),
      queryClient.invalidateQueries({
        queryKey: workItemWorkflowKeys.history(space.id, item.id),
      }),
    ])
  }
  const actionMutation = useMutation({
    mutationFn: ({ action, requestId }: {
      action: WorkItemWorkflowAction
      requestId: string
    }) => {
      if (!online || !navigator.onLine) {
        throw new Error('当前离线，字段输入和动作请求已保留，请联网后重试')
      }
      if (!workflow?.currentStateKey) throw new Error('状态事实尚未加载')
      return executeWorkItemWorkflowAction(
        space.id,
        item.id,
        action.actionKey,
        {
          fromStateKey: workflow.currentStateKey,
          expectedWorkItemVersion: item.version,
          fieldPatch,
        },
        requestId,
      )
    },
    onSuccess: async (result) => {
      setCommandDraft(null)
      await refreshWorkflow()
      message.success(result.replayed ? '动作已安全重放，状态未重复推进' : '状态动作已执行')
    },
    onError: (error) => {
      if (error instanceof ApiRequestError && error.status === 409) {
        message.error('状态或工作项已被他人更新。输入和动作请求已保留，请刷新后重试。')
      } else if (error instanceof ApiRequestError && error.status === 422) {
        message.error('动作条件或必填字段未满足。输入已保留，请修正后重试。')
      } else {
        message.error(errorMessage(error, '动作执行失败，输入和请求已保留'))
      }
    },
  })
  const correctionMutation = useMutation({
    mutationFn: () => correctWorkItemWorkflowState(space.id, item.id, {
      targetStateKey: correctionTarget.trim(),
      expectedWorkItemVersion: item.version,
      reason: correctionReason.trim(),
      confirmation: 'CORRECT_WORKFLOW_STATE',
    }),
    onSuccess: async () => {
      setCorrectionOpen(false)
      setCorrectionTarget('')
      setCorrectionReason('')
      await refreshWorkflow()
      message.success('受控状态纠错已完成')
    },
    onError: (error) => message.error(errorMessage(error, '纠错失败，原因和目标状态已保留')),
  })

  const execute = (action: WorkItemWorkflowAction) => {
    const current = commandDraft?.actionKey === action.actionKey
      ? commandDraft
      : { actionKey: action.actionKey, requestId: crypto.randomUUID() }
    setCommandDraft(current)
    const run = () => actionMutation.mutate({ action, requestId: current.requestId })
    if (action.kind === 'terminate') {
      modal.confirm({
        title: '确认终止这个工作项的业务流程？',
        content: '终止是显式业务状态动作，不会归档对象；完整历史将被保留。',
        okText: '确认终止',
        okButtonProps: { danger: true },
        cancelText: '取消',
        onOk: run,
      })
      return
    }
    run()
  }

  if (workflowQuery.isLoading) {
    return <Card className="content-card work-item-workflow-panel" loading />
  }
  if (workflowQuery.isError || !workflow) {
    return (
      <Alert
        type="error"
        showIcon
        message="状态流加载失败"
        description="该错误不会清除工作项表单输入。"
        action={<Button icon={<ReloadOutlined />} onClick={() => workflowQuery.refetch()}>重试</Button>}
      />
    )
  }
  if (workflow.capability !== 'available') {
    const notConfigured = workflow.capability === 'not_configured'
    return (
      <Alert
        className="work-item-workflow-capability"
        type="warning"
        showIcon
        message={notConfigured ? '状态流程尚未配置' : '状态流程尚未准备好'}
        description={notConfigured
          ? '当前事项仍可查看；如需推进状态，请联系空间管理员完成流程配置。'
          : '请联系空间管理员完成初始化；准备完成前不会显示可执行动作。'}
      />
    )
  }

  const canCorrect = ['owner', 'admin'].includes(space.currentUserRole ?? '')
    && item.status === 'active'
  return (
    <Card
      className="content-card work-item-workflow-panel"
      data-testid="work-item-workflow-panel"
      title={<Space><SafetyCertificateOutlined /><span>状态流程</span></Space>}
      extra={canCorrect ? (
        <Button icon={<ToolOutlined />} onClick={() => setCorrectionOpen(true)}>
          受控纠错
        </Button>
      ) : null}
    >
      {!online ? (
        <Alert
          type="warning"
          showIcon
          message="当前离线"
          description="字段输入和待重试动作会保留；联网前不会发送命令。"
        />
      ) : null}
      <div className="work-item-workflow-current" aria-live="polite">
        <div>
          <Typography.Text type="secondary">当前状态</Typography.Text>
          <Space wrap>
            <Tag color={stateColor(workflow.currentStateCategory)} className="work-item-workflow-state-tag">
              {currentStateLabel(workflow, item.status)}
            </Tag>
          </Space>
        </div>
        <Space wrap className="work-item-workflow-actions" aria-label="可执行状态动作">
          {workflow.availableActions.map((action) => {
            const requiredLabels = action.requiredFieldKeys
              .map((key) => fieldLabels.get(key))
              .filter((label): label is string => Boolean(label) && !looksTechnicalIdentifier(label!))
            return (
              <Button
                key={action.actionKey}
                data-testid={`work-item-workflow-action-${action.actionKey}`}
                type={action.kind === 'forward' ? 'primary' : 'default'}
                danger={action.kind === 'terminate'}
                disabled={!online || actionMutation.isPending}
                loading={actionMutation.isPending && commandDraft?.actionKey === action.actionKey}
                onClick={() => execute(action)}
              >
                <span className="work-item-workflow-action-label">{workflowActionLabel(action)}</span>
                {action.requiredFieldKeys.length > 0 ? (
                  <span className="work-item-workflow-requirement">
                    {requiredLabels.length > 0
                      ? `需 ${requiredLabels.join('、')}`
                      : '需补充必填信息'}
                  </span>
                ) : null}
              </Button>
            )
          })}
          {workflow.availableActions.length === 0 ? (
            <Typography.Text type="secondary">
              当前无需你推进；如需继续，请联系负责人确认流程状态。
            </Typography.Text>
          ) : null}
        </Space>
      </div>
      {commandDraft && actionMutation.isError ? (
        <Alert
          className="work-item-workflow-retry"
          type="warning"
          showIcon
          message="动作请求已保留"
          description="重试会安全复用本次动作请求，不会重复推进状态。"
        />
      ) : null}
      <div className="work-item-workflow-history">
        <Space><HistoryOutlined /><Typography.Text strong>状态历史</Typography.Text></Space>
        <List
          loading={historyQuery.isLoading}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无状态历史" /> }}
          dataSource={historyQuery.data?.items ?? []}
          renderItem={(entry) => (
            <List.Item>
              <List.Item.Meta
                avatar={entry.actionKey ? <CheckCircleOutlined /> : <ExclamationCircleOutlined />}
                title={(
                  <Space wrap>
                    <Typography.Text strong>
                      {historyActionLabel(entry.actionKey, entry.actionKind, workflow.availableActions)}
                    </Typography.Text>
                    <Tag>状态已更新</Tag>
                  </Space>
                )}
                description={formatTime(entry.occurredAt)}
              />
            </List.Item>
          )}
        />
      </div>
      <Modal
        title="受控状态纠错"
        open={correctionOpen}
        okText="确认纠错"
        cancelText="取消"
        okButtonProps={{
          danger: true,
          disabled: !correctionTarget.trim() || correctionReason.trim().length < 10,
        }}
        confirmLoading={correctionMutation.isPending}
        onCancel={() => setCorrectionOpen(false)}
        onOk={() => correctionMutation.mutate()}
        destroyOnHidden={false}
      >
        <Alert
          type="warning"
          showIcon
          message="危险操作"
          description="纠错会追加不可变历史、审计和事件，不会删除或改写旧历史。"
        />
        <Form layout="vertical" className="work-item-workflow-correction-form">
          <Form.Item label="目标状态永久 key" htmlFor="work-item-workflow-correction-target" required>
            <Input
              id="work-item-workflow-correction-target"
              autoFocus
              value={correctionTarget}
              placeholder="例如 open"
              onChange={(event) => setCorrectionTarget(event.target.value)}
            />
          </Form.Item>
          <Form.Item label="纠错原因（至少 10 个字符）" htmlFor="work-item-workflow-correction-reason" required>
            <Input.TextArea
              id="work-item-workflow-correction-reason"
              value={correctionReason}
              autoSize={{ minRows: 3, maxRows: 8 }}
              maxLength={500}
              onChange={(event) => setCorrectionReason(event.target.value)}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}

function stateColor(category?: string | null) {
  if (category === 'terminal') return 'success'
  if (category === 'canceled') return 'default'
  if (category === 'initial') return 'blue'
  return 'processing'
}

function embeddedWorkflow(item: WorkItem): WorkItemWorkflow | null {
  const runtime = item.runtime as WorkItem['runtime'] & { workflow?: unknown }
  const workflow = runtime.workflow
  if (!workflow || typeof workflow !== 'object') return null
  const candidate = workflow as Partial<WorkItemWorkflow>
  if (!['available', 'not_configured', 'uninitialized'].includes(candidate.capability ?? '')) {
    return null
  }
  return {
    capability: candidate.capability!,
    policyVersion: typeof candidate.policyVersion === 'string' ? candidate.policyVersion : '',
    currentStateKey: typeof candidate.currentStateKey === 'string' ? candidate.currentStateKey : null,
    currentStateLabel: typeof candidate.currentStateLabel === 'string' ? candidate.currentStateLabel : null,
    currentStateCategory: candidate.currentStateCategory ?? null,
    aggregateVersion: typeof candidate.aggregateVersion === 'number' ? candidate.aggregateVersion : 0,
    availableActions: Array.isArray(candidate.availableActions) ? candidate.availableActions : [],
  }
}

function currentStateLabel(
  workflow: WorkItemWorkflow | null | undefined,
  itemStatus: WorkItem['status'],
) {
  if (itemStatus === 'archived') return '已归档'
  if (!workflow || workflow.capability === 'not_configured') return '进行中'
  if (workflow.capability === 'uninitialized') return '待启动'
  const configured = humanReadableLabel(workflow.currentStateLabel)
  if (configured) return configured
  if (workflow.currentStateCategory === 'initial') return '待开始'
  if (workflow.currentStateCategory === 'terminal') return '已完成'
  if (workflow.currentStateCategory === 'canceled') return '已终止'
  return '进行中'
}

function nextActionSummaries(
  item: WorkItem,
  workflow: WorkItemWorkflow | null,
): NextActionSummary[] {
  if (workflow?.capability === 'available' && workflow.availableActions.length > 0) {
    return [...workflow.availableActions]
      .sort((left, right) => left.sortOrder - right.sortOrder)
      .map((action) => ({
        key: `workflow-${action.actionKey}`,
        label: workflowActionLabel(action),
        source: 'workflow' as const,
        primary: action.kind === 'forward',
        danger: action.kind === 'terminate',
      }))
  }
  const labels: Partial<Record<WorkItem['availableActions'][number], string>> = {
    edit: '补充事项信息',
    archive: '可归档事项',
    restore: '可恢复事项',
  }
  return item.availableActions.flatMap((action) => {
    const label = labels[action]
    return label ? [{
      key: `item-${action}`,
      label,
      source: 'item' as const,
      primary: action === 'edit',
      danger: action === 'archive',
    }] : []
  })
}

function workflowActionLabel(action: WorkItemWorkflowAction) {
  const configured = humanReadableLabel(action.label)
  if (configured) return configured
  return ({
    forward: '继续推进',
    return_action: '退回处理',
    reopen: '重新打开',
    terminate: '终止流程',
    restore: '恢复流程',
  } as const)[action.kind]
}

function historyActionLabel(
  actionKey: string | null | undefined,
  actionKind: string,
  availableActions: WorkItemWorkflowAction[],
) {
  if (!actionKey) return '流程已初始化'
  const configured = availableActions.find((action) => action.actionKey === actionKey)
  if (configured) return workflowActionLabel(configured)
  return ({
    forward: '状态已推进',
    return_action: '事项已退回',
    reopen: '事项已重新打开',
    terminate: '流程已终止',
    restore: '流程已恢复',
    initialize: '流程已初始化',
  } as Record<string, string>)[actionKind] ?? '状态已更新'
}

function humanReadableLabel(value?: string | null) {
  const label = value?.trim()
  if (!label) return null
  const normalized = label.toLowerCase().replaceAll(/[\s.-]+/g, '_')
  const localized = ({
    new: '待开始',
    open: '待开始',
    todo: '待开始',
    initial: '待开始',
    active: '进行中',
    doing: '进行中',
    in_progress: '进行中',
    done: '已完成',
    completed: '已完成',
    closed: '已完成',
    terminal: '已完成',
    canceled: '已终止',
    cancelled: '已终止',
    terminated: '已终止',
  } as Record<string, string>)[normalized]
  if (localized) return localized
  return looksTechnicalIdentifier(label) ? null : label
}

function looksTechnicalIdentifier(value: string) {
  const compact = value.trim()
  return looksOpaqueIdentifier(compact)
    || /^[a-z][a-z0-9_.:-]*$/i.test(compact)
}

function looksOpaqueIdentifier(value: string) {
  const compact = value.trim()
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(compact)
    || /^[0-9a-f]{24,}$/i.test(compact)
}

function responsiblePeopleLabel(
  participants: WorkItemParticipant[] | undefined,
  loading: boolean,
  failed: boolean,
) {
  if (loading) return '正在确认…'
  if (failed) return '暂不可用'
  const assignees = (participants ?? []).filter((participant) => participant.role === 'assignee')
  const responsible = assignees.length > 0
    ? assignees
    : (participants ?? []).filter((participant) => participant.role === 'owner')
  const names = [...new Set(responsible.flatMap((participant) => {
    const name = participant.displayName?.trim()
    return name && !looksOpaqueIdentifier(name) ? [name] : []
  }))]
  return names.length > 0 ? names.join('、') : '未指定'
}

function readableDeadline(item: WorkItem) {
  const fieldByKey = new Map(item.runtime.snapshot.fields.flatMap((field) => {
    const key = typeof field.fieldKey === 'string' ? field.fieldKey : ''
    return key ? [[key, field] as const] : []
  }))
  const candidates = Object.entries(item.fieldValues)
    .filter(([key]) => item.runtime.accessProjection[key]?.mode !== 'hidden')
    .map(([key, value]) => {
      const field = fieldByKey.get(key)
      const normalizedKey = key.toLowerCase().replaceAll(/[^a-z0-9]/g, '')
      const name = String(field?.name ?? field?.label ?? '').toLowerCase()
      const exactKey = ['dueat', 'duedate', 'deadline'].includes(normalizedKey)
      const semanticName = /截止|到期|deadline|due date/.test(name)
      return {
        value,
        score: exactKey ? 100 : semanticName ? 80 : 0,
      }
    })
    .filter((candidate) => candidate.score > 0 && typeof candidate.value === 'string')
    .sort((left, right) => right.score - left.score)
  const value = candidates[0]?.value
  if (typeof value !== 'string' || !value.trim()) return null
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return value
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return null
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(parsed)
}
