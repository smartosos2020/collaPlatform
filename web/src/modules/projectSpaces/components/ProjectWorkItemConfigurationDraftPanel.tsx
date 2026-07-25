import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  DeleteOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App as AntdApp, Button, Skeleton, Space, Tag, Tooltip, Typography } from 'antd'

import {
  abandonWorkItemConfigurationDraft,
  getWorkItemConfigurationDraft,
  validateWorkItemConfigurationDraft,
  workItemConfigurationDraftKeys,
  type WorkItemConfigurationDraft,
} from '../api/workItemConfigurationApi'
import { errorMessage, formatTime } from '../projectSpaceView'

export function ProjectWorkItemConfigurationDraftPanel({
  spaceId,
  typeId,
  readOnly,
}: {
  spaceId: string
  typeId: string
  readOnly: boolean
}) {
  const { message, modal } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const queryKey = workItemConfigurationDraftKeys.detail(spaceId, typeId)
  const draftQuery = useQuery({
    queryKey,
    queryFn: () => getWorkItemConfigurationDraft(spaceId, typeId),
    retry: false,
    refetchOnWindowFocus: false,
  })

  const updateCachedDraft = (draft: WorkItemConfigurationDraft) => {
    queryClient.setQueryData(queryKey, draft)
  }
  const validateMutation = useMutation({
    mutationFn: () => validateWorkItemConfigurationDraft(
      spaceId,
      typeId,
      draftQuery.data?.aggregateVersion ?? -1,
    ),
    onSuccess: (draft) => {
      updateCachedDraft(draft)
      message.success(draft.status === 'valid' ? '配置校验通过' : '配置校验完成，请处理阻断项')
    },
    onError: (error) => {
      void draftQuery.refetch()
      message.error(errorMessage(error, '配置校验失败'))
    },
  })
  const abandonMutation = useMutation({
    mutationFn: () => abandonWorkItemConfigurationDraft(
      spaceId,
      typeId,
      draftQuery.data?.aggregateVersion ?? -1,
    ),
    onSuccess: (draft) => {
      updateCachedDraft(draft)
      message.success('配置草稿已放弃')
    },
    onError: (error) => {
      void draftQuery.refetch()
      message.error(errorMessage(error, '放弃配置草稿失败'))
    },
  })

  if (draftQuery.isLoading) {
    return <section className="work-item-draft-panel"><Skeleton active paragraph={{ rows: 1 }} /></section>
  }
  if (draftQuery.isError) {
    return (
      <Alert
        showIcon
        type="error"
        message="配置草稿加载失败"
        action={<Button size="small" icon={<ReloadOutlined />} onClick={() => draftQuery.refetch()}>重试</Button>}
      />
    )
  }
  const draft = draftQuery.data
  if (!draft) return null

  const errors = draft.diagnostics.filter((item) => item.severity === 'error')
  const warnings = draft.diagnostics.filter((item) => item.severity === 'warning')
  const canValidate = !readOnly && draft.availableActions.includes('validate')
  const canAbandon = !readOnly && draft.availableActions.includes('abandon')

  return (
    <section className={`work-item-draft-panel status-${draft.status}`} aria-label="配置草稿状态">
      <div className="work-item-draft-summary">
        <span className="work-item-draft-icon"><SafetyCertificateOutlined /></span>
        <div className="work-item-draft-copy">
          <Space wrap size={7}>
            <Typography.Text strong>配置草稿</Typography.Text>
            <DraftStatusTag status={draft.status} />
            <Tag>v{draft.aggregateVersion}</Tag>
            {errors.length > 0 ? <Tag color="error" icon={<CloseCircleOutlined />}>{errors.length} 个阻断项</Tag> : null}
            {warnings.length > 0 ? <Tag color="warning" icon={<WarningOutlined />}>{warnings.length} 个提醒</Tag> : null}
          </Space>
          <Typography.Text type="secondary">
            hash {draft.configHash.slice(0, 16)}… · schema v{draft.snapshotSchemaVersion} · 更新于 {formatTime(draft.updatedAt)}
          </Typography.Text>
        </div>
        <Space wrap className="work-item-draft-actions">
          <Button
            type="primary"
            icon={<CheckCircleOutlined />}
            disabled={!canValidate}
            loading={validateMutation.isPending}
            onClick={() => validateMutation.mutate()}
          >
            校验配置
          </Button>
          <Button
            danger
            icon={<DeleteOutlined />}
            disabled={!canAbandon}
            loading={abandonMutation.isPending}
            onClick={() => modal.confirm({
              title: '放弃当前配置草稿？',
              content: '草稿将进入不可变的 abandoned 状态；下一次配置写入会创建新草稿，已发布版本不会被修改。',
              okText: '放弃草稿',
              okButtonProps: { danger: true },
              cancelText: '取消',
              onOk: () => abandonMutation.mutateAsync(),
            })}
          >
            放弃草稿
          </Button>
        </Space>
      </div>
      {draft.diagnostics.length > 0 ? (
        <div className="work-item-draft-diagnostics" aria-label="配置诊断">
          {draft.diagnostics.map((diagnostic) => (
            <Tooltip title={diagnostic.message} key={`${diagnostic.code}:${diagnostic.keyPath}`}>
              <Tag color={diagnostic.severity === 'error' ? 'error' : 'warning'}>
                {diagnostic.keyPath} · {diagnostic.code}
              </Tag>
            </Tooltip>
          ))}
        </div>
      ) : (
        <Typography.Text className="work-item-draft-clean" type="secondary">
          当前快照没有诊断项
        </Typography.Text>
      )}
    </section>
  )
}

function DraftStatusTag({ status }: { status: WorkItemConfigurationDraft['status'] }) {
  const labels: Record<WorkItemConfigurationDraft['status'], string> = {
    editing: '编辑中',
    validating: '校验中',
    valid: '校验通过',
    invalid: '存在阻断',
    abandoned: '已放弃',
  }
  const colors: Record<WorkItemConfigurationDraft['status'], string> = {
    editing: 'processing',
    validating: 'processing',
    valid: 'success',
    invalid: 'error',
    abandoned: 'default',
  }
  return <Tag color={colors[status]}>{labels[status]}</Tag>
}
