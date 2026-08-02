import {
  CheckCircleOutlined,
  CloudUploadOutlined,
  CloseCircleOutlined,
  DeleteOutlined,
  HistoryOutlined,
  ReloadOutlined,
  RollbackOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App as AntdApp, Button, Divider, List, Skeleton, Space, Tag, Tooltip, Typography } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'

import {
  abandonWorkItemConfigurationDraft,
  getWorkItemConfigurationDraftCompatibility,
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
import { workItemKeys } from '../api/workItemsApi'
import { workItemTypeKeys } from '../api/workItemTypesApi'
import { errorMessage, formatTime } from '../projectSpaceView'
import {
  isWorkItemConfigurationCompatibilityReady,
  requiresWorkItemConfigurationCompatibility,
} from '../workItemConfigurationPublication'
import { ProjectWorkItemConfigurationTemplatePanel } from './ProjectWorkItemConfigurationTemplatePanel'
import { ProjectWorkItemNodeBackfillPanel } from './ProjectWorkItemNodeBackfillPanel'
import { ProjectWorkItemNodeFlowDesigner } from './ProjectWorkItemNodeFlowDesigner'
import { ProjectWorkItemPermissionPolicyEditor } from './ProjectWorkItemPermissionPolicyEditor'
import { ProjectWorkItemRelationDefinitionsEditor } from './ProjectWorkItemRelationDefinitionsEditor'
import { ProjectWorkItemStateFlowEditor } from './ProjectWorkItemStateFlowEditor'
import { ProjectWorkItemStateBackfillPanel } from './ProjectWorkItemStateBackfillPanel'

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
  const location = useLocation()
  const flowAccessRef = useRef<HTMLElement>(null)
  const [dirtyStateFlowDraftId, setDirtyStateFlowDraftId] = useState<string | null>(null)
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
  const currentVersion = versionsQuery.data?.find((version) => version.status === 'published')
  const compatibilityRequired = versionsQuery.isSuccess
    && requiresWorkItemConfigurationCompatibility(currentVersion)
  const draftDiffQuery = useQuery({
    queryKey: workItemConfigurationVersionKeys.draftDiff(
      spaceId,
      typeId,
      draftQuery.data?.configHash ?? 'pending',
    ),
    queryFn: () => getWorkItemConfigurationDraftDiff(spaceId, typeId),
    enabled: Boolean(draftQuery.data && compatibilityRequired),
    retry: false,
    refetchOnWindowFocus: false,
  })
  const compatibilityQuery = useQuery({
    queryKey: workItemConfigurationVersionKeys.draftCompatibility(
      spaceId,
      typeId,
      draftQuery.data?.configHash ?? 'pending',
    ),
    queryFn: () => getWorkItemConfigurationDraftCompatibility(spaceId, typeId),
    enabled: Boolean(draftQuery.data && compatibilityRequired),
    retry: false,
    refetchOnWindowFocus: false,
  })

  useEffect(() => {
    if (location.hash !== '#flow-access' || !draftQuery.data?.id) return
    const frame = window.requestAnimationFrame(() => {
      flowAccessRef.current?.scrollIntoView({ block: 'start' })
      flowAccessRef.current?.focus({ preventScroll: true })
    })
    return () => window.cancelAnimationFrame(frame)
  }, [draftQuery.data?.id, location.hash])

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
        queryClient.removeQueries({
          queryKey: workItemKeys.createForm(spaceId, typeId),
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

  const stateFlowDirty = dirtyStateFlowDraftId === draft.id
  const errors = draft.diagnostics.filter((item) => item.severity === 'error')
  const warnings = draft.diagnostics.filter((item) => item.severity === 'warning')
  const canValidate = !readOnly && !stateFlowDirty && draft.availableActions.includes('validate')
  const canAbandon = !readOnly && !stateFlowDirty && draft.availableActions.includes('abandon')
  const compatibilityImpact = compatibilityQuery.data?.overallImpact
  const publicationBlocked = compatibilityImpact === 'blocked'
  const migrationConfirmationRequired = compatibilityImpact === 'migration_required'
  const compatibilityReady = isWorkItemConfigurationCompatibilityReady(
    currentVersion,
    {
      versionsQuerySucceeded: versionsQuery.isSuccess,
      compatibilityQuerySucceeded: compatibilityQuery.isSuccess,
    },
  )
  const canPublish = !readOnly
    && !stateFlowDirty
    && draft.status === 'valid'
    && compatibilityReady
    && !publicationBlocked
  const breaking = draftDiffQuery.data?.breaking ?? false
  const showNodeFlowEditor = hasNodeFlow(draft.snapshot) && !stateFlowDirty

  const confirmPublish = () => {
    const confirmationRequired = breaking || migrationConfirmationRequired
    modal.confirm({
      title: migrationConfirmationRequired
        ? '确认发布需要实例迁移的配置？'
        : breaking ? '确认发布破坏性配置变更？' : '发布当前配置？',
      content: migrationConfirmationRequired
        ? '新版本只会原子切换类型 current pointer；既有 WorkItem 仍保持旧绑定。实例升级必须另行提供显式状态映射、失败清单和验证，发布不会静默迁移实例。'
        : breaking
          ? `检测到 ${draftDiffQuery.data?.summary.breaking ?? 0} 项 breaking 变化。发布后会生成不可变新版本，旧版本仅变为 superseded。`
        : '将生成不可变新版本并原子切换当前版本；发布后的历史快照不能编辑或删除。',
      okText: confirmationRequired ? '明确确认并发布' : '发布版本',
      okButtonProps: { danger: confirmationRequired },
      cancelText: '取消',
      onOk: () => publishMutation.mutateAsync(confirmationRequired),
    })
  }

  return (
    <section className={`work-item-draft-panel status-${draft.status}`} aria-label="配置草稿状态">
      <div className="work-item-draft-summary">
        <div className="work-item-draft-copy">
          <Space wrap size={7}>
            <Typography.Text strong>配置草稿</Typography.Text>
            <DraftStatusTag status={draft.status} />
            <Tag>v{draft.aggregateVersion}</Tag>
            {errors.length > 0 ? <Tag color="error" icon={<CloseCircleOutlined />}>{errors.length} 个阻断项</Tag> : null}
            {warnings.length > 0 ? <Tag color="warning" icon={<WarningOutlined />}>{warnings.length} 个提醒</Tag> : null}
          </Space>
          <Typography.Text type="secondary">
            schema v{draft.snapshotSchemaVersion} · 更新于 {formatTime(draft.updatedAt)}
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
      {publicationBlocked ? (
        <Alert
          className="work-item-publication-block"
          type="error"
          showIcon
          message="发布已被兼容合同阻断"
          description="前端和服务端都不会提供普通绕过入口；请保留旧绑定或另行完成受控恢复方案。"
        />
      ) : null}
      {stateFlowDirty ? (
        <Alert
          className="work-item-publication-block"
          type="warning"
          showIcon
          message="状态流有未保存修改"
          description="请先保存或放弃状态流的本地修改；在此之前，校验、发布、放弃草稿、模板操作和其他配置编辑均保持禁用，避免发布旧快照。"
        />
      ) : null}
      <section
        id="flow-access"
        ref={flowAccessRef}
        className="work-item-flow-access-section"
        tabIndex={-1}
        aria-labelledby="work-item-flow-access-heading"
      >
        <div className="work-item-flow-access-heading">
          <Typography.Title id="work-item-flow-access-heading" level={4}>
            流程与权限
          </Typography.Title>
          <Typography.Paragraph type="secondary">
            配置数据权限、关系、状态流程及审批与协作流程。变更写入当前任务模板草稿，发布前不会影响成员运行时。
          </Typography.Paragraph>
        </div>
        <ProjectWorkItemPermissionPolicyEditor
          key={`permissions:${draft.id}:${draft.aggregateVersion}`}
          spaceId={spaceId}
          typeId={typeId}
          readOnly={readOnly || stateFlowDirty || draft.status === 'abandoned'}
          draft={draft}
          onDraftSaved={updateCachedDraft}
        />
        <ProjectWorkItemRelationDefinitionsEditor
          key={`relations:${draft.id}:${draft.aggregateVersion}`}
          spaceId={spaceId}
          typeId={typeId}
          readOnly={readOnly || stateFlowDirty || draft.status === 'abandoned'}
          draft={draft}
          onDraftSaved={updateCachedDraft}
        />
        {showNodeFlowEditor ? (
          <>
            <ProjectWorkItemNodeFlowDesigner
              key={`${draft.id}:${draft.aggregateVersion}`}
              spaceId={spaceId}
              typeId={typeId}
              readOnly={readOnly || stateFlowDirty || draft.status === 'abandoned'}
              draft={draft}
              onDraftSaved={updateCachedDraft}
            />
            <ProjectWorkItemNodeBackfillPanel
              spaceId={spaceId}
              typeId={typeId}
              currentVersion={currentVersion}
              readOnly={readOnly}
            />
          </>
        ) : (
          <>
            <ProjectWorkItemStateFlowEditor
              key={draft.id}
              spaceId={spaceId}
              typeId={typeId}
              readOnly={readOnly || draft.status === 'abandoned'}
              draft={draft}
              onDraftSaved={updateCachedDraft}
              onDirtyChange={(dirty) => setDirtyStateFlowDraftId(dirty ? draft.id : null)}
            />
            <ProjectWorkItemStateBackfillPanel
              spaceId={spaceId}
              typeId={typeId}
              currentVersion={currentVersion}
              readOnly={readOnly}
            />
          </>
        )}
      </section>
      <ProjectWorkItemConfigurationTemplatePanel
        spaceId={spaceId}
        typeId={typeId}
        readOnly={readOnly || stateFlowDirty}
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
          {compatibilityQuery.data ? (
            <CompatibilityTag
              impact={compatibilityQuery.data.overallImpact}
              count={compatibilityQuery.data.findings.length}
            />
          ) : null}
        </Space>
      </div>
      {draftDiffQuery.data?.items.length ? (
        <List
          className="work-item-configuration-change-list"
          size="small"
          header={<Typography.Text strong>草稿与当前发布版本差异</Typography.Text>}
          dataSource={draftDiffQuery.data.items}
          renderItem={(item) => (
            <List.Item>
              <Space wrap>
                <Tag>{item.changeType}</Tag>
                <Tag color={item.impact === 'breaking' ? 'error' : 'processing'}>{item.impact}</Tag>
                <Typography.Text code>{item.keyPath}</Typography.Text>
              </Space>
            </List.Item>
          )}
        />
      ) : null}
      {compatibilityQuery.data?.findings.length ? (
        <List
          className="work-item-configuration-change-list"
          size="small"
          header={<Typography.Text strong>兼容与迁移提示</Typography.Text>}
          dataSource={compatibilityQuery.data.findings}
          renderItem={(finding) => (
            <List.Item>
              <List.Item.Meta
                title={(
                  <Space wrap>
                    <CompatibilityTag impact={finding.impact} count={1} />
                    <Typography.Text code>{finding.keyPath}</Typography.Text>
                    <Tag>{finding.reasonCode}</Tag>
                  </Space>
                )}
                description={finding.recommendation}
              />
            </List.Item>
          )}
        />
      ) : null}
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
                  || stateFlowDirty
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

function hasNodeFlow(snapshot: unknown) {
  return Boolean(
    snapshot
    && typeof snapshot === 'object'
    && !Array.isArray(snapshot)
    && (snapshot as Record<string, unknown>).nodeFlow,
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

function CompatibilityTag({
  impact,
  count,
}: {
  impact: 'compatible' | 'review_required' | 'migration_required' | 'blocked'
  count: number
}) {
  const labels = {
    compatible: '兼容',
    review_required: '需复核',
    migration_required: '需迁移',
    blocked: '阻断',
  } as const
  const colors = {
    compatible: 'success',
    review_required: 'warning',
    migration_required: 'orange',
    blocked: 'error',
  } as const
  return <Tag color={colors[impact]}>兼容性 {labels[impact]} · {count} 项</Tag>
}
