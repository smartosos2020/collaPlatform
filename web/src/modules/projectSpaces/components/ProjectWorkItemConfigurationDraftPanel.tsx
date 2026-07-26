import {
  CheckCircleOutlined,
  CloudUploadOutlined,
  CloseCircleOutlined,
  DeleteOutlined,
  HistoryOutlined,
  ReloadOutlined,
  RollbackOutlined,
  SafetyCertificateOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App as AntdApp, Button, Divider, List, Skeleton, Space, Tag, Tooltip, Typography } from 'antd'

import {
  abandonWorkItemConfigurationDraft,
  getWorkItemConfigurationDraftDiff,
  getWorkItemConfigurationDraft,
  listWorkItemConfigurationVersions,
  prepareWorkItemConfigurationRollback,
  publishWorkItemConfigurationDraft,
  validateWorkItemConfigurationDraft,
  workItemConfigurationDraftKeys,
  workItemConfigurationVersionKeys,
  type ConfigurationVersion,
  type WorkItemConfigurationDraft,
} from '../api/workItemConfigurationApi'
import { workItemTypeKeys } from '../api/workItemTypesApi'
import { errorMessage, formatTime } from '../projectSpaceView'
import { ProjectWorkItemConfigurationTemplatePanel } from './ProjectWorkItemConfigurationTemplatePanel'

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
  const versionsQuery = useQuery({
    queryKey: workItemConfigurationVersionKeys.list(spaceId, typeId),
    queryFn: () => listWorkItemConfigurationVersions(spaceId, typeId),
    retry: false,
    refetchOnWindowFocus: false,
  })
  const draftDiffQuery = useQuery({
    queryKey: workItemConfigurationVersionKeys.draftDiff(
      spaceId,
      typeId,
      draftQuery.data?.configHash ?? 'pending',
    ),
    queryFn: () => getWorkItemConfigurationDraftDiff(spaceId, typeId),
    enabled: Boolean(draftQuery.data && versionsQuery.data?.[0]?.completeSnapshot),
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
  const publishMutation = useMutation({
    mutationFn: (breakingConfirmed: boolean) => publishWorkItemConfigurationDraft(
      spaceId,
      typeId,
      draftQuery.data?.aggregateVersion ?? -1,
      breakingConfirmed,
    ),
    onSuccess: async (result) => {
      message.success(`配置版本 v${result.version.versionNumber} 已发布`)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey }),
        queryClient.invalidateQueries({
          queryKey: workItemConfigurationVersionKeys.list(spaceId, typeId),
        }),
        queryClient.invalidateQueries({
          queryKey: [...workItemTypeKeys.all, spaceId],
        }),
      ])
    },
    onError: (error) => {
      void draftQuery.refetch()
      message.error(errorMessage(error, '配置发布失败'))
    },
  })
  const rollbackMutation = useMutation({
    mutationFn: (version: ConfigurationVersion) => prepareWorkItemConfigurationRollback(
      spaceId,
      typeId,
      version.id,
      draftQuery.data?.aggregateVersion ?? -1,
    ),
    onSuccess: async (result) => {
      message.success(`已从 v${result.sourceVersionNumber} 生成回滚草稿`)
      await queryClient.invalidateQueries({ queryKey })
    },
    onError: (error) => {
      void draftQuery.refetch()
      message.error(errorMessage(error, '准备回滚失败'))
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
  const canPublish = !readOnly && draft.status === 'valid'
  const currentVersion = versionsQuery.data?.find((version) => version.status === 'published')
  const breaking = draftDiffQuery.data?.breaking ?? false

  const confirmPublish = () => {
    modal.confirm({
      title: breaking ? '确认发布破坏性配置变更？' : '发布当前配置？',
      content: breaking
        ? `检测到 ${draftDiffQuery.data?.summary.breaking ?? 0} 项 breaking 变化。发布后会生成不可变新版本，旧版本仅变为 superseded。`
        : '将生成不可变新版本并原子切换当前版本；发布后的历史快照不能编辑或删除。',
      okText: breaking ? '确认并发布' : '发布版本',
      okButtonProps: { danger: breaking },
      cancelText: '取消',
      onOk: () => publishMutation.mutateAsync(breaking),
    })
  }

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
            type="primary"
            icon={<CloudUploadOutlined />}
            disabled={!canPublish}
            loading={publishMutation.isPending}
            onClick={confirmPublish}
          >
            发布版本
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
      <ProjectWorkItemConfigurationTemplatePanel
        spaceId={spaceId}
        typeId={typeId}
        readOnly={readOnly}
        draft={draft}
        currentVersion={currentVersion}
      />
      <Divider className="work-item-version-divider" />
      <div className="work-item-version-heading">
        <Space>
          <HistoryOutlined />
          <Typography.Text strong>版本历史</Typography.Text>
          {currentVersion ? <Tag color="success">当前 v{currentVersion.versionNumber}</Tag> : null}
          {draftDiffQuery.data ? (
            <Tag color={breaking ? 'error' : 'processing'}>
              草稿 diff {draftDiffQuery.data.items.length} 项
            </Tag>
          ) : null}
        </Space>
      </div>
      <List
        className="work-item-version-list"
        loading={versionsQuery.isLoading}
        locale={{ emptyText: '暂无配置版本' }}
        dataSource={versionsQuery.data ?? []}
        renderItem={(version) => (
          <List.Item
            actions={[
              <Button
                key="rollback"
                size="small"
                icon={<RollbackOutlined />}
                disabled={
                  readOnly
                  || !version.completeSnapshot
                  || version.status === 'published'
                  || rollbackMutation.isPending
                }
                onClick={() => modal.confirm({
                  title: `从 v${version.versionNumber} 生成回滚草稿？`,
                  content: '当前草稿将被放弃，历史版本和 current pointer 不会移动；需要重新发布才会生效。',
                  okText: '生成回滚草稿',
                  cancelText: '取消',
                  onOk: () => rollbackMutation.mutateAsync(version),
                })}
              >
                回滚
              </Button>,
            ]}
          >
            <List.Item.Meta
              title={(
                <Space wrap>
                  <Typography.Text>v{version.versionNumber}</Typography.Text>
                  <Tag color={version.status === 'published' ? 'success' : 'default'}>
                    {version.status === 'published' ? '当前' : '历史'}
                  </Tag>
                  {!version.completeSnapshot ? <Tag color="warning">legacy partial</Tag> : null}
                  {version.rollbackSourceVersionId ? <Tag color="purple">回滚发布</Tag> : null}
                </Space>
              )}
              description={`hash ${version.configHash.slice(0, 16)}… · schema v${version.snapshotSchemaVersion} · ${formatTime(version.publishedAt)}`}
            />
          </List.Item>
        )}
      />
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
