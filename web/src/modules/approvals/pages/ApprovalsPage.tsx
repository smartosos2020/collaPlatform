import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  ExportOutlined,
  PlusOutlined,
  ReloadOutlined,
  SwapOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  App as AntdApp,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { useAuthStore } from '../../auth/authStore'
import { listDirectoryMembers } from '../../projects/api/projectsApi'
import {
  approveApproval,
  getApprovalInstance,
  getApprovalStats,
  listApprovalForms,
  listApprovalInstances,
  listApprovalTodos,
  rejectApproval,
  startApproval,
  transferApproval,
  withdrawApproval,
  type ApprovalFormField,
  type ApprovalInstanceSummary,
  type ApprovalTaskSummary,
} from '../api/approvalsApi'

type StartApprovalFormValues = {
  formId: string
  title?: string
  payload?: Record<string, unknown>
}

type ActionFormValues = {
  comment?: string
  assigneeId?: string
}

export function ApprovalsPage() {
  const { approvalId } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { message } = AntdApp.useApp()
  const currentUser = useAuthStore((state) => state.currentUser)
  const [manualSelectedInstanceId, setManualSelectedInstanceId] = useState<string | null>(null)
  const [startOpen, setStartOpen] = useState(false)
  const [selectedFormId, setSelectedFormId] = useState<string | null>(null)
  const [actionMode, setActionMode] = useState<'approve' | 'reject' | 'transfer' | 'withdraw' | null>(null)
  const [startForm] = Form.useForm<StartApprovalFormValues>()
  const [actionForm] = Form.useForm<ActionFormValues>()
  const selectedInstanceId = approvalId ?? manualSelectedInstanceId

  const formsQuery = useQuery({ queryKey: ['approvals', 'forms'], queryFn: listApprovalForms })
  const instancesQuery = useQuery({ queryKey: ['approvals', 'instances'], queryFn: listApprovalInstances })
  const todosQuery = useQuery({ queryKey: ['approvals', 'todos'], queryFn: listApprovalTodos })
  const statsQuery = useQuery({ queryKey: ['approvals', 'stats'], queryFn: getApprovalStats })
  const membersQuery = useQuery({ queryKey: ['members', 'directory'], queryFn: listDirectoryMembers })
  const detailQuery = useQuery({
    queryKey: ['approvals', 'instances', selectedInstanceId],
    queryFn: () => getApprovalInstance(selectedInstanceId || ''),
    enabled: Boolean(selectedInstanceId),
  })

  const selectedForm = useMemo(
    () => formsQuery.data?.find((form) => form.id === selectedFormId) ?? formsQuery.data?.[0],
    [formsQuery.data, selectedFormId],
  )
  const detail = detailQuery.data
  const pendingTask = detail?.tasks.find((task) => task.status === 'pending' && task.assigneeId === currentUser?.id)
  const isApplicant = detail?.instance.applicantId === currentUser?.id
  const memberOptions = (membersQuery.data ?? [])
    .filter((member) => member.status === 'active')
    .map((member) => ({ value: member.id, label: `${member.displayName} @${member.username}` }))

  const refreshApprovals = async (instanceId?: string) => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['approvals', 'instances'] }),
      queryClient.invalidateQueries({ queryKey: ['approvals', 'todos'] }),
      queryClient.invalidateQueries({ queryKey: ['approvals', 'stats'] }),
      queryClient.invalidateQueries({ queryKey: ['workspace', 'dashboard'] }),
      instanceId ? queryClient.invalidateQueries({ queryKey: ['approvals', 'instances', instanceId] }) : Promise.resolve(),
    ])
  }

  const startMutation = useMutation({
    mutationFn: startApproval,
    onSuccess: async (nextDetail) => {
      message.success('审批已提交')
      setStartOpen(false)
      startForm.resetFields()
      navigate(`/approvals/${nextDetail.instance.id}`)
      await refreshApprovals(nextDetail.instance.id)
    },
  })

  const actionMutation = useMutation({
    mutationFn: async (values: ActionFormValues) => {
      if (!detail || !actionMode) {
        throw new Error('approval action is missing context')
      }
      if (actionMode === 'approve') {
        return approveApproval(detail.instance.id, pendingTask?.id, values.comment)
      }
      if (actionMode === 'reject') {
        return rejectApproval(detail.instance.id, pendingTask?.id, values.comment)
      }
      if (actionMode === 'withdraw') {
        return withdrawApproval(detail.instance.id, values.comment)
      }
      if (!values.assigneeId) {
        throw new Error('assignee is required')
      }
      return transferApproval(detail.instance.id, {
        taskId: pendingTask?.id,
        assigneeId: values.assigneeId,
        comment: values.comment,
      })
    },
    onSuccess: async (nextDetail) => {
      message.success('审批已更新')
      setActionMode(null)
      actionForm.resetFields()
      await refreshApprovals(nextDetail.instance.id)
    },
  })

  const instanceColumns: ColumnsType<ApprovalInstanceSummary> = [
    {
      title: '标题',
      dataIndex: 'title',
      render: (_, record) => <Link to={`/approvals/${record.id}`}>{record.title}</Link>,
    },
    { title: '表单', dataIndex: 'formName', width: 140 },
    { title: '申请人', dataIndex: 'applicantName', width: 140 },
    { title: '状态', dataIndex: 'status', width: 110, render: (value) => <ApprovalStatusTag status={value} /> },
    { title: '提交时间', dataIndex: 'submittedAt', width: 180, render: (value) => new Date(value).toLocaleString() },
  ]

  return (
    <Space orientation="vertical" size={16} className="page-stack approvals-page">
      <Space className="page-toolbar" wrap>
        <Typography.Title level={2}>审批</Typography.Title>
        <Space wrap>
          <Button icon={<ReloadOutlined />} onClick={() => void refreshApprovals(selectedInstanceId ?? undefined)} />
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setSelectedFormId(formsQuery.data?.[0]?.id ?? null)
              setStartOpen(true)
            }}
          >
            发起审批
          </Button>
        </Space>
      </Space>

      <section className="approval-metrics">
        <Card size="small">
          <Statistic title="待我审批" value={statsQuery.data?.pendingTodos ?? 0} loading={statsQuery.isLoading} />
        </Card>
        <Card size="small">
          <Statistic title="我提交待审" value={statsQuery.data?.submittedPending ?? 0} loading={statsQuery.isLoading} />
        </Card>
        <Card size="small">
          <Statistic title="已通过" value={statsQuery.data?.approved ?? 0} loading={statsQuery.isLoading} />
        </Card>
        <Card size="small">
          <Statistic title="已拒绝" value={statsQuery.data?.rejected ?? 0} loading={statsQuery.isLoading} />
        </Card>
      </section>

      <section className="approval-grid">
        <Card title="常用表单" loading={formsQuery.isLoading} className="approval-section">
          <Space orientation="vertical" size={8} className="approval-list">
            {(formsQuery.data?.length ?? 0) === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无审批表单" /> : null}
            {formsQuery.data?.map((form) => (
              <button
                className={selectedFormId === form.id ? 'approval-form-item active' : 'approval-form-item'}
                key={form.id}
                onClick={() => {
                  setSelectedFormId(form.id)
                  setStartOpen(true)
                }}
              >
                <span>
                  <Typography.Text strong>{form.name}</Typography.Text>
                  <Typography.Text type="secondary">{form.description}</Typography.Text>
                </span>
                <Tag>{form.category}</Tag>
              </button>
            ))}
          </Space>
        </Card>

        <Card title="待我审批" loading={todosQuery.isLoading} className="approval-section">
          <Space orientation="vertical" size={8} className="approval-list">
            {(todosQuery.data?.length ?? 0) === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无待办" /> : null}
            {todosQuery.data?.map((task) => (
              <ApprovalTodoItem key={task.id} task={task} />
            ))}
          </Space>
        </Card>

        <Card title="审批记录" loading={instancesQuery.isLoading} className="approval-table-card">
          <Table rowKey="id" size="middle" columns={instanceColumns} dataSource={instancesQuery.data ?? []} pagination={{ pageSize: 8 }} />
        </Card>
      </section>

      <ApprovalDetailDrawer
        detail={detail}
          open={Boolean(selectedInstanceId)}
        loading={detailQuery.isLoading}
        canApprove={Boolean(pendingTask)}
        canWithdraw={Boolean(isApplicant && detail?.instance.status === 'pending')}
        onClose={() => {
          setManualSelectedInstanceId(null)
          navigate('/approvals')
        }}
        onAction={setActionMode}
      />

      <Modal
        title="发起审批"
        open={startOpen}
        okText="提交"
        cancelText="取消"
        confirmLoading={startMutation.isPending}
        onCancel={() => setStartOpen(false)}
        onOk={() => startForm.submit()}
      >
        <Form
          form={startForm}
          layout="vertical"
          initialValues={{ formId: selectedForm?.id }}
          onFinish={(values) => {
            const formId = values.formId || selectedForm?.id
            if (!formId) {
              message.warning('请选择审批表单')
              return
            }
            startMutation.mutate({ formId, title: values.title, payload: values.payload ?? {} })
          }}
        >
          <Form.Item label="表单" name="formId" rules={[{ required: true, message: '请选择表单' }]}>
            <Select
              options={(formsQuery.data ?? []).map((form) => ({ value: form.id, label: form.name }))}
              onChange={(value) => setSelectedFormId(value)}
            />
          </Form.Item>
          <Form.Item label="标题" name="title">
            <Input placeholder={selectedForm ? selectedForm.name : '审批标题'} />
          </Form.Item>
          {selectedForm?.schema.fields?.map((field) => (
            <ApprovalFieldInput key={field.key} field={field} />
          ))}
        </Form>
      </Modal>

      <Modal
        title={actionTitle(actionMode)}
        open={Boolean(actionMode)}
        okText="确认"
        cancelText="取消"
        confirmLoading={actionMutation.isPending}
        onCancel={() => setActionMode(null)}
        onOk={() => actionForm.submit()}
      >
        <Form form={actionForm} layout="vertical" onFinish={(values) => actionMutation.mutate(values)}>
          {actionMode === 'transfer' ? (
            <Form.Item label="转交给" name="assigneeId" rules={[{ required: true, message: '请选择成员' }]}>
              <Select showSearch optionFilterProp="label" options={memberOptions} />
            </Form.Item>
          ) : null}
          <Form.Item label="备注" name="comment">
            <Input.TextArea autoSize={{ minRows: 3, maxRows: 6 }} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}

function ApprovalTodoItem({ task }: { task: ApprovalTaskSummary }) {
  return (
    <Link className="approval-todo-item" to={`/approvals/${task.instanceId}`}>
      <span>
        <Typography.Text strong>{task.instanceTitle}</Typography.Text>
        <Typography.Text type="secondary">{task.formName} / {task.applicantName}</Typography.Text>
      </span>
      <Tag color="blue">待审批</Tag>
    </Link>
  )
}

function ApprovalDetailDrawer({
  detail,
  open,
  loading,
  canApprove,
  canWithdraw,
  onClose,
  onAction,
}: {
  detail?: Awaited<ReturnType<typeof getApprovalInstance>>
  open: boolean
  loading: boolean
  canApprove: boolean
  canWithdraw: boolean
  onClose: () => void
  onAction: (mode: 'approve' | 'reject' | 'transfer' | 'withdraw') => void
}) {
  const instance = detail?.instance
  return (
    <Drawer title={instance?.title ?? '审批详情'} open={open} onClose={onClose} size="large" loading={loading}>
      {detail ? (
        <Space orientation="vertical" size={16} className="approval-detail-stack">
          <Space wrap>
            <ApprovalStatusTag status={detail.instance.status} />
            <Tag>{detail.form.name}</Tag>
            <Typography.Text type="secondary">申请人 {detail.instance.applicantName}</Typography.Text>
          </Space>
          <Space wrap>
            {canApprove ? (
              <>
                <Button type="primary" icon={<CheckCircleOutlined />} onClick={() => onAction('approve')}>
                  通过
                </Button>
                <Button danger icon={<CloseCircleOutlined />} onClick={() => onAction('reject')}>
                  拒绝
                </Button>
                <Button icon={<SwapOutlined />} onClick={() => onAction('transfer')}>
                  转交
                </Button>
              </>
            ) : null}
            {canWithdraw ? (
              <Button icon={<ExportOutlined />} onClick={() => onAction('withdraw')}>
                撤回
              </Button>
            ) : null}
          </Space>
          <Card title="申请内容">
            <Descriptions column={1} size="small">
              {detail.form.schema.fields?.map((field) => (
                <Descriptions.Item key={field.key} label={field.label}>
                  {String(detail.payload[field.key] ?? '-')}
                </Descriptions.Item>
              ))}
            </Descriptions>
          </Card>
          <Card title="审批任务">
            <Space orientation="vertical" className="approval-detail-stack">
              {detail.tasks.map((task) => (
                <div className="approval-flow-row" key={task.id}>
                  <span>
                    <Typography.Text strong>{task.assigneeName}</Typography.Text>
                    <Typography.Text type="secondary">第 {task.nodeOrder} 节点</Typography.Text>
                  </span>
                  <Space wrap>
                    <TaskStatusTag status={task.status} />
                    {task.comment ? <Typography.Text type="secondary">{task.comment}</Typography.Text> : null}
                  </Space>
                </div>
              ))}
            </Space>
          </Card>
          <Card title="审批动态">
            <Space orientation="vertical" className="approval-detail-stack">
              {detail.actions.map((action) => (
                <div className="approval-flow-row" key={action.id}>
                  <span>
                    <Typography.Text strong>{action.actorName || '系统'}</Typography.Text>
                    <Typography.Text type="secondary">{new Date(action.createdAt).toLocaleString()}</Typography.Text>
                  </span>
                  <Space wrap>
                    <Tag>{actionText(action.action)}</Tag>
                    {action.comment ? <Typography.Text>{action.comment}</Typography.Text> : null}
                  </Space>
                </div>
              ))}
            </Space>
          </Card>
        </Space>
      ) : null}
    </Drawer>
  )
}

function ApprovalFieldInput({ field }: { field: ApprovalFormField }) {
  const rules = field.required ? [{ required: true, message: `请输入${field.label}` }] : undefined
  const name = ['payload', field.key]
  if (field.type === 'number') {
    return (
      <Form.Item label={field.label} name={name} rules={rules}>
        <InputNumber className="approval-number-input" />
      </Form.Item>
    )
  }
  if (field.type === 'select') {
    return (
      <Form.Item label={field.label} name={name} rules={rules}>
        <Select options={(field.options ?? []).map((option) => ({ value: option, label: option }))} />
      </Form.Item>
    )
  }
  if (field.type === 'textarea') {
    return (
      <Form.Item label={field.label} name={name} rules={rules}>
        <Input.TextArea autoSize={{ minRows: 3, maxRows: 6 }} />
      </Form.Item>
    )
  }
  return (
    <Form.Item label={field.label} name={name} rules={rules}>
      <Input placeholder={field.type === 'datetime' ? 'YYYY-MM-DD HH:mm' : undefined} />
    </Form.Item>
  )
}

function ApprovalStatusTag({ status }: { status: string }) {
  const color = status === 'approved' ? 'green' : status === 'rejected' ? 'red' : status === 'withdrawn' ? 'default' : 'blue'
  return <Tag color={color}>{statusText(status)}</Tag>
}

function TaskStatusTag({ status }: { status: string }) {
  const color = status === 'approved' ? 'green' : status === 'rejected' ? 'red' : status === 'canceled' ? 'default' : 'blue'
  return <Tag color={color}>{taskStatusText(status)}</Tag>
}

function statusText(status: string) {
  return {
    pending: '审批中',
    approved: '已通过',
    rejected: '已拒绝',
    withdrawn: '已撤回',
  }[status] ?? status
}

function taskStatusText(status: string) {
  return {
    pending: '待处理',
    approved: '已通过',
    rejected: '已拒绝',
    canceled: '已取消',
  }[status] ?? status
}

function actionText(action: string) {
  return {
    started: '发起',
    approved: '通过',
    rejected: '拒绝',
    transferred: '转交',
    withdrawn: '撤回',
  }[action] ?? action
}

function actionTitle(mode: 'approve' | 'reject' | 'transfer' | 'withdraw' | null) {
  return {
    approve: '通过审批',
    reject: '拒绝审批',
    transfer: '转交审批',
    withdraw: '撤回审批',
  }[mode ?? 'approve']
}
