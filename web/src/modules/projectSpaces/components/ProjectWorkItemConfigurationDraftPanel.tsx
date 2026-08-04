import {
  ApartmentOutlined,
  CheckCircleOutlined,
  CloudUploadOutlined,
  CloseCircleOutlined,
  DeleteOutlined,
  HistoryOutlined,
  QuestionCircleOutlined,
  ReloadOutlined,
  RollbackOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App as AntdApp, Button, Divider, List, Segmented, Skeleton, Space, Tag, Tooltip, Typography } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import {
  abandonWorkItemConfigurationDraft,
  getWorkItemConfigurationDraftCompatibility,
  getWorkItemConfigurationDraftDiff,
  getWorkItemConfigurationDraft,
  listWorkItemConfigurationVersions,
  prepareWorkItemConfigurationRollback,
  publishWorkItemConfigurationDraft,
  saveWorkItemConfigurationDraft,
  validateWorkItemConfigurationDraft,
  workItemConfigurationDraftKeys,
  workItemConfigurationVersionKeys,
  type ConfigurationDiagnostic,
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
import {
  getWorkItemWorkflowMode,
  switchWorkItemWorkflowMode,
  type WorkItemWorkflowMode,
} from '../workItemWorkflowMode'
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
  const navigate = useNavigate()
  const flowAccessRef = useRef<HTMLElement>(null)
  const [dirtyWorkflowDraftId, setDirtyWorkflowDraftId] = useState<string | null>(null)
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
  const workflowModeMutation = useMutation({
    mutationFn: (mode: WorkItemWorkflowMode) => {
      const currentDraft = draftQuery.data
      if (!currentDraft) throw new Error('配置草稿尚未加载')
      return saveWorkItemConfigurationDraft(
        spaceId,
        typeId,
        switchWorkItemWorkflowMode(currentDraft.snapshot, mode),
        currentDraft.aggregateVersion,
      )
    },
    onSuccess: (saved, mode) => {
      updateCachedDraft(saved)
      setDirtyWorkflowDraftId(null)
      message.success(mode === 'state' ? '已切换为轻量状态流' : '已切换为审批协作节点流')
    },
    onError: (error) => {
      void draftQuery.refetch()
      message.error(errorMessage(error, '切换流程模式失败'))
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

  const workflowDirty = dirtyWorkflowDraftId === draft.id
  const workflowMode = getWorkItemWorkflowMode(draft.snapshot)
  const errors = draft.diagnostics.filter((item) => item.severity === 'error')
  const warnings = draft.diagnostics.filter((item) => item.severity === 'warning')
  const canValidate = !readOnly && !workflowDirty && draft.availableActions.includes('validate')
  const canAbandon = !readOnly && !workflowDirty && draft.availableActions.includes('abandon')
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
    && !workflowDirty
    && draft.status === 'valid'
    && compatibilityReady
    && !publicationBlocked
  const breaking = draftDiffQuery.data?.breaking ?? false

  const confirmWorkflowModeChange = (mode: WorkItemWorkflowMode) => {
    if (mode === workflowMode || workflowModeMutation.isPending) return
    modal.confirm({
      title: mode === 'state' ? '切换为轻量状态流？' : '切换为审批协作节点流？',
      content: (
        <div className="work-item-workflow-mode-confirmation">
          <p>两种流程不能同时生效。切换会在当前草稿中移除原流程定义，并生成一套可编辑的默认流程。</p>
          <p>已发布版本与正在运行的工作项不会立即改变；只有重新校验并发布后，新版本才会生效。</p>
        </div>
      ),
      okText: '确认切换',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => workflowModeMutation.mutateAsync(mode),
    })
  }

  const openDiagnostic = (diagnostic: ConfigurationDiagnostic) => {
    const presentation = configurationDiagnosticPresentation(diagnostic, draft.snapshot)
    const searchParams = new URLSearchParams(location.search)
    searchParams.set('panel', 'work-model')
    searchParams.set('workModelTab', presentation.tab)
    if (presentation.layoutKind) searchParams.set('layoutKind', presentation.layoutKind)
    else searchParams.delete('layoutKind')
    if (presentation.layoutNodeKey) searchParams.set('layoutNodeKey', presentation.layoutNodeKey)
    else searchParams.delete('layoutNodeKey')
    if (presentation.layoutFieldKey) searchParams.set('layoutFieldKey', presentation.layoutFieldKey)
    else searchParams.delete('layoutFieldKey')
    navigate({
      pathname: location.pathname,
      search: `?${searchParams.toString()}`,
      hash: '',
    }, { replace: true })
  }

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
      <header
        className="content-card work-item-model-section-header work-item-flow-access-heading"
        data-testid="work-item-model-section-header"
      >
        <div className="work-item-flow-access-heading-main">
          <Typography.Title id="work-item-flow-access-heading" level={4}>流程与权限</Typography.Title>
          <Tooltip title="配置数据权限、关系、状态流程及审批与协作流程。变更写入当前任务模板草稿，发布前不会影响成员运行时。">
            <Button
              type="text"
              size="small"
              className="work-item-flow-access-help"
              aria-label="查看流程与权限说明"
              icon={<QuestionCircleOutlined />}
            />
          </Tooltip>
          <Space wrap size={7} className="work-item-draft-meta">
            <DraftStatusTag status={draft.status} />
            <Tag>v{draft.aggregateVersion}</Tag>
            {errors.length > 0 ? <Tag color="error" icon={<CloseCircleOutlined />}>{errors.length} 个阻断项</Tag> : null}
            {warnings.length > 0 ? <Tag color="warning" icon={<WarningOutlined />}>{warnings.length} 个提醒</Tag> : null}
          </Space>
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
      </header>
      {draft.diagnostics.length > 0 ? (
        <section className="content-card work-item-draft-diagnostics" aria-label="配置诊断">
          {draft.diagnostics.map((diagnostic) => {
            const presentation = configurationDiagnosticPresentation(diagnostic, draft.snapshot)
            return (
              <Tooltip
                title={`${presentation.description} 点击后将打开“${presentation.location}”。`}
                key={`${diagnostic.code}:${diagnostic.keyPath}`}
              >
                <Button
                  size="small"
                  danger={diagnostic.severity === 'error'}
                  className="work-item-diagnostic-link"
                  data-diagnostic-code={diagnostic.code}
                  icon={diagnostic.severity === 'error' ? <CloseCircleOutlined /> : <WarningOutlined />}
                  onClick={() => openDiagnostic(diagnostic)}
                >
                  {presentation.label}
                  <span className="work-item-diagnostic-action">定位处理</span>
                </Button>
              </Tooltip>
            )
          })}
        </section>
      ) : null}
      {publicationBlocked ? (
        <Alert
          className="work-item-publication-block"
          type="error"
          showIcon
          message="发布已被兼容合同阻断"
          description="前端和服务端都不会提供普通绕过入口；请保留旧绑定或另行完成受控恢复方案。"
        />
      ) : null}
      {workflowDirty ? (
        <Alert
          className="work-item-publication-block"
          type="warning"
          showIcon
          message="流程配置有未保存修改"
          description="请先保存或放弃当前流程的本地修改；在此之前，模式切换、校验、发布、放弃草稿、模板操作和其他配置编辑均保持禁用，避免发布旧快照。"
        />
      ) : null}
      <section
        id="flow-access"
        ref={flowAccessRef}
        className="work-item-flow-access-section"
        tabIndex={-1}
        aria-labelledby="work-item-flow-access-heading"
      >
        <div className="work-item-workflow-mode-selector" aria-label="流程模式">
          <div className="work-item-workflow-mode-copy">
            <Space size={6}>
              <ApartmentOutlined />
              <Typography.Text strong>流程模式</Typography.Text>
              <Tag color={workflowMode === 'state' ? 'blue' : 'purple'}>
                {workflowMode === 'state' ? '状态驱动' : '节点驱动'}
              </Tag>
            </Space>
            <Typography.Text type="secondary">
              轻量状态流适合常规状态流转；审批协作节点流适合多人处理、分支与会签。两者只能启用一种。
            </Typography.Text>
          </div>
          <Segmented<WorkItemWorkflowMode>
            value={workflowMode}
            options={[
              { label: '轻量状态流', value: 'state' },
              { label: '审批协作节点流', value: 'node' },
            ]}
            disabled={readOnly || workflowDirty || draft.status === 'abandoned' || workflowModeMutation.isPending}
            onChange={confirmWorkflowModeChange}
          />
        </div>
        <ProjectWorkItemConfigurationTemplatePanel
          spaceId={spaceId}
          typeId={typeId}
          readOnly={readOnly || workflowDirty}
          draft={draft}
          currentVersion={currentVersion}
        />
        <ProjectWorkItemPermissionPolicyEditor
          key={`permissions:${draft.id}:${draft.aggregateVersion}`}
          spaceId={spaceId}
          typeId={typeId}
          readOnly={readOnly || workflowDirty || draft.status === 'abandoned'}
          draft={draft}
          onDraftSaved={updateCachedDraft}
        />
        <ProjectWorkItemRelationDefinitionsEditor
          key={`relations:${draft.id}:${draft.aggregateVersion}`}
          spaceId={spaceId}
          typeId={typeId}
          readOnly={readOnly || workflowDirty || draft.status === 'abandoned'}
          draft={draft}
          onDraftSaved={updateCachedDraft}
        />
        {workflowMode === 'node' ? (
          <>
            <ProjectWorkItemNodeFlowDesigner
              key={`${draft.id}:${draft.aggregateVersion}`}
              spaceId={spaceId}
              typeId={typeId}
              readOnly={readOnly || draft.status === 'abandoned'}
              draft={draft}
              onDraftSaved={updateCachedDraft}
              onDirtyChange={(dirty) => setDirtyWorkflowDraftId(dirty ? draft.id : null)}
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
              onDirtyChange={(dirty) => setDirtyWorkflowDraftId(dirty ? draft.id : null)}
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
                  || workflowDirty
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

type DiagnosticTarget = {
  tab: 'type-information' | 'field-configuration' | 'page-layout' | 'flow-access'
  location: string
  layoutKind?: 'create' | 'detail'
  layoutNodeKey?: string
  layoutFieldKey?: string
}

type DiagnosticPresentation = DiagnosticTarget & {
  label: string
  description: string
}

function configurationDiagnosticPresentation(
  diagnostic: ConfigurationDiagnostic,
  snapshot: unknown,
): DiagnosticPresentation {
  const target = diagnosticTarget(diagnostic.keyPath)
  if (diagnostic.code === 'missing_layout_kind') {
    const missingKinds = missingLayoutKinds(snapshot)
    const missingLabel = missingKinds.length === 2
      ? '新建页和详情页'
      : missingKinds[0] === 'create' ? '新建页' : '详情页'
    return {
      ...target,
      label: `页面布局未完整：缺少${missingLabel}`,
      description: `请补充${missingLabel}布局，确保创建和查看工作项时都有可用页面。`,
      layoutKind: missingKinds[0] ?? 'detail',
    }
  }

  if (diagnostic.code === 'inactive_layout_field' || diagnostic.code === 'unknown_layout_field') {
    const context = layoutDiagnosticContext(snapshot, diagnostic.keyPath)
    const fieldLabel = context.fieldName && context.layoutFieldKey
      ? `${context.fieldName}（${context.layoutFieldKey}）`
      : context.layoutFieldKey
    return {
      ...target,
      ...context,
      label: diagnostic.code === 'inactive_layout_field'
        ? '布局引用了已停用字段'
        : '布局引用了不存在的字段',
      description: fieldLabel
        ? `布局引用了${diagnostic.code === 'inactive_layout_field' ? '已停用' : '不存在的'}字段“${fieldLabel}”，请在页面布局中移除或替换。`
        : `${diagnostic.code === 'inactive_layout_field' ? '布局引用了已停用字段' : '布局引用了不存在的字段'}，请在页面布局中移除或替换。`,
    }
  }

  const labels: Record<string, string> = {
    missing_type_definition: '工作项类型信息不完整',
    invalid_fields: '字段配置格式有误',
    field_budget_exceeded: '配置字段数量超过上限',
    duplicate_or_missing_field_key: '字段编码缺失或重复',
    invalid_field_config: '字段参数配置有误',
    invalid_field_options: '字段选项配置有误',
    option_budget_exceeded: '字段选项数量超过上限',
    invalid_layouts: '页面布局配置格式有误',
    duplicate_or_invalid_layout_kind: '页面布局类型重复或无效',
    invalid_layout_nodes: '页面布局节点格式有误',
    layout_node_budget_exceeded: '页面布局节点数量超过上限',
    duplicate_or_missing_node_key: '布局节点编码缺失或重复',
    unknown_layout_field: '布局引用了不存在的字段',
    inactive_layout_field: '布局引用了已停用字段',
    missing_layout_parent: '布局节点缺少上级容器',
    layout_cycle: '页面布局存在循环嵌套',
    layout_depth_exceeded: '页面布局嵌套层级过深',
    invalid_access_policies: '字段权限策略格式有误',
    access_policy_budget_exceeded: '字段权限策略数量超过上限',
    duplicate_or_missing_policy_key: '权限策略编码缺失或重复',
    inactive_policy_field: '权限策略引用了已停用字段',
    unknown_condition_field: '显示条件引用了不存在的字段',
    cross_workspace_reference: '配置不能引用其他工作区',
    cross_space_reference: '配置不能引用其他项目空间',
  }
  const label = labels[diagnostic.code] ?? `${target.location}存在需要处理的配置`
  return {
    ...target,
    label,
    description: `${label}。请前往对应配置位置检查并修正。`,
  }
}

type LayoutDiagnosticContext = Pick<DiagnosticTarget, 'layoutKind' | 'layoutNodeKey' | 'layoutFieldKey'> & {
  fieldName?: string
}

function layoutDiagnosticContext(snapshot: unknown, keyPath: string): LayoutDiagnosticContext {
  const match = /^\$\.layouts\[(\d+)\]\.nodes\[(\d+)\]/.exec(keyPath)
  if (!match || !snapshot || typeof snapshot !== 'object' || Array.isArray(snapshot)) return {}

  const root = snapshot as Record<string, unknown>
  const layouts = root.layouts
  if (!Array.isArray(layouts)) return {}
  const layout = layouts[Number(match[1])]
  if (!layout || typeof layout !== 'object' || Array.isArray(layout)) return {}
  const layoutRecord = layout as Record<string, unknown>
  const nodes = layoutRecord.nodes
  if (!Array.isArray(nodes)) return {}
  const node = nodes[Number(match[2])]
  if (!node || typeof node !== 'object' || Array.isArray(node)) return {}
  const nodeRecord = node as Record<string, unknown>
  const fieldKey = typeof nodeRecord.fieldKey === 'string' ? nodeRecord.fieldKey : undefined
  const nodeKey = typeof nodeRecord.nodeKey === 'string' ? nodeRecord.nodeKey : undefined
  const layoutKind = layoutRecord.layoutKind === 'create' || layoutRecord.layoutKind === 'detail'
    ? layoutRecord.layoutKind
    : undefined
  const fields = root.fields
  const field = Array.isArray(fields)
    ? fields.find((item) => item && typeof item === 'object' && !Array.isArray(item)
      && (item as Record<string, unknown>).fieldKey === fieldKey)
    : undefined
  const fieldName = field && typeof field === 'object' && !Array.isArray(field)
    && typeof (field as Record<string, unknown>).name === 'string'
    ? (field as Record<string, unknown>).name as string
    : undefined
  return { layoutKind, layoutNodeKey: nodeKey, layoutFieldKey: fieldKey, fieldName }
}

function diagnosticTarget(keyPath: string): DiagnosticTarget {
  if (keyPath.startsWith('$.typeDefinition')) {
    return { tab: 'type-information', location: '类型信息' }
  }
  if (keyPath.startsWith('$.fields')) {
    return { tab: 'field-configuration', location: '配置字段' }
  }
  if (keyPath.startsWith('$.layouts')) {
    return { tab: 'page-layout', location: '页面布局' }
  }
  return { tab: 'flow-access', location: '流程与权限' }
}

function missingLayoutKinds(snapshot: unknown): Array<'create' | 'detail'> {
  if (!snapshot || typeof snapshot !== 'object' || Array.isArray(snapshot)) return ['create', 'detail']
  const layouts = (snapshot as Record<string, unknown>).layouts
  if (!Array.isArray(layouts)) return ['create', 'detail']
  const configured = new Set(layouts.map((layout) => {
    if (!layout || typeof layout !== 'object' || Array.isArray(layout)) return undefined
    return (layout as Record<string, unknown>).layoutKind
  }))
  return (['create', 'detail'] as const).filter((kind) => !configured.has(kind))
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
