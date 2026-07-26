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
import type { WorkItem } from '../api/workItemsApi'
import {
  correctWorkItemWorkflowState,
  executeWorkItemWorkflowAction,
  getWorkItemWorkflow,
  listWorkItemWorkflowHistory,
  workItemWorkflowKeys,
  type WorkItemWorkflowAction,
} from '../api/workItemWorkflowApi'
import { errorMessage, formatTime } from '../projectSpaceView'

type WorkflowCommandDraft = {
  actionKey: string
  requestId: string
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
    return (
      <Alert
        className="work-item-workflow-capability"
        type={workflow.capability === 'uninitialized' ? 'warning' : 'info'}
        showIcon
        message={workflow.capability === 'uninitialized' ? '状态尚未显式初始化' : '当前绑定版本未配置状态流'}
        description={workflow.capability === 'uninitialized'
          ? '空间管理员需要通过存量状态初始化创建可审计事实；页面不会猜测默认状态。'
          : '此工作项继续按自身绑定版本运行，不会回读最新配置。'}
      />
    )
  }

  const canCorrect = ['owner', 'admin'].includes(space.currentUserRole ?? '')
    && item.status === 'active'
  return (
    <Card
      className="content-card work-item-workflow-panel"
      data-testid="work-item-workflow-panel"
      title={<Space><SafetyCertificateOutlined /><span>状态流</span></Space>}
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
              {workflow.currentStateLabel}
            </Tag>
            <Typography.Text code>{workflow.currentStateKey}</Typography.Text>
            <Typography.Text type="secondary">策略 {workflow.policyVersion}</Typography.Text>
          </Space>
        </div>
        <Space wrap className="work-item-workflow-actions" aria-label="可执行状态动作">
          {workflow.availableActions.map((action) => (
            <Button
              key={action.actionKey}
              type={action.kind === 'forward' ? 'primary' : 'default'}
              danger={action.kind === 'terminate'}
              disabled={!online}
              loading={actionMutation.isPending && commandDraft?.actionKey === action.actionKey}
              onClick={() => execute(action)}
            >
              <span className="work-item-workflow-action-label">{action.label}</span>
              {action.requiredFieldKeys.length > 0 ? (
                <span className="work-item-workflow-requirement">
                  需 {action.requiredFieldKeys.map((key) => fieldLabels.get(key) ?? key).join('、')}
                </span>
              ) : null}
            </Button>
          ))}
          {workflow.availableActions.length === 0 ? (
            <Typography.Text type="secondary">当前没有服务端允许的动作</Typography.Text>
          ) : null}
        </Space>
      </div>
      {commandDraft && actionMutation.isError ? (
        <Alert
          className="work-item-workflow-retry"
          type="warning"
          showIcon
          message="动作请求已保留"
          description="重试将复用同一 request ID，服务端会精确重放或拒绝异载荷。"
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
                    <Typography.Text strong>{entry.actionKey ?? 'initialize'}</Typography.Text>
                    <Tag>{entry.fromStateKey ?? '∅'} → {entry.toStateKey}</Tag>
                    <Typography.Text type="secondary">#{entry.sequenceNumber}</Typography.Text>
                  </Space>
                )}
                description={`${formatTime(entry.occurredAt)} · ${entry.actionKind}`}
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
