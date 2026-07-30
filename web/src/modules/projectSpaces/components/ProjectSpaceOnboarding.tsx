import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  CompassOutlined,
  ExclamationCircleOutlined,
  QuestionCircleOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Collapse,
  Divider,
  Drawer,
  Empty,
  Radio,
  Skeleton,
  Space,
  Switch,
  Tag,
  Typography,
} from 'antd'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import { ApiRequestError } from '../../../shared/api/httpClient'
import { useSessionScope } from '../../../shared/session/SessionScopeContext'
import {
  commandProjectSpaceOnboarding,
  createOnboardingEventId,
  getProjectSpaceOnboarding,
  projectSpaceOnboardingKeys,
  recordProjectSpaceOnboardingTelemetry,
} from '../api/projectSpaceOnboardingApi'
import type { UserProjectSpace } from '../api/projectSpacesApi'
import {
  PROJECT_SPACE_ONBOARDING_STARTING_POINTS,
  canOpenOnboardingStep,
  contextualOnboardingHelp,
  onboardingErrorPresentation,
  onboardingStartingPointValue,
  onboardingStepAcknowledgement,
  resolveOnboardingOwnerPath,
  resolveOnboardingStepCopy,
  startingPointCommand,
  type ProjectSpaceOnboardingCommand,
  type ProjectSpaceOnboardingScenarioKey,
  type ProjectSpaceOnboardingView,
} from '../projectSpaceOnboarding'

type StartingPointValue = ProjectSpaceOnboardingScenarioKey | 'blank'

export function ProjectSpaceOnboarding({
  space,
  online,
  onExperienceHelp,
}: {
  space: UserProjectSpace
  online: boolean
  onExperienceHelp?: () => void
}) {
  const { message, modal } = AntdApp.useApp()
  const location = useLocation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const sessionScope = useSessionScope()
  const [openOverride, setOpenOverride] = useState<boolean | null>(null)
  const [selectedPointDraft, setSelectedPointDraft] = useState<StartingPointValue>()
  const automaticShownTelemetrySent = useRef(false)
  const eligible = space.member && space.availableActions.some(
    (action) => action === 'view_overview' || action === 'view_work_items',
  )
  const queryKey = projectSpaceOnboardingKeys.detail(
    sessionScope?.workspaceId ?? 'unknown',
    sessionScope?.userId ?? 'unknown',
    space.id,
  )
  const onboardingQuery = useQuery({
    queryKey,
    queryFn: () => getProjectSpaceOnboarding(space.id),
    enabled: Boolean(eligible && online && sessionScope),
    retry: (count, error) => !(error instanceof ApiRequestError && [400, 403, 404, 409].includes(error.status)) && count < 1,
  })
  const onboarding = onboardingQuery.data
  const effectiveReadOnly = space.status !== 'active' || Boolean(onboarding?.readOnly)
  const experienceCommandDisabled = !online || Boolean(onboarding?.migrationRequired)
  const savedPoint = onboarding ? onboardingStartingPointValue(onboarding.startingPoint) : undefined
  const selectedPoint = selectedPointDraft ?? savedPoint
  const open = openOverride ?? Boolean(
    onboarding
    && !onboarding.dismissed
    && onboarding.startingPoint.kind === 'unselected',
  )
  const contextHelp = useMemo(
    () => contextualOnboardingHelp(location.pathname),
    [location.pathname],
  )

  useEffect(() => {
    if (
      !automaticShownTelemetrySent.current
      && online
      && onboarding
      && open
      && !onboarding.dismissed
      && onboarding.startingPoint.kind === 'unselected'
    ) {
      automaticShownTelemetrySent.current = true
      const stepKey = onboarding.checklist[0]?.stepKey
      if (stepKey) {
        emitOnboardingTelemetry(online, space.id, onboarding, stepKey, 'shown')
      }
    }
  }, [onboarding, online, open, space.id])

  const commandMutation = useMutation({
    mutationFn: (command: ProjectSpaceOnboardingCommand) => commandProjectSpaceOnboarding(
      space.id,
      onboarding?.version ?? 0,
      command,
    ),
    onSuccess: (saved) => {
      queryClient.setQueryData(queryKey, saved)
    },
    onError: async (error) => {
      if (error instanceof ApiRequestError && error.status === 409) {
        await queryClient.invalidateQueries({ queryKey })
      }
      const presentation = onboardingErrorPresentation(error)
      message.error(`${presentation.title}：${presentation.description}`)
    },
  })

  if (!eligible) return null

  const openGuide = () => {
    onExperienceHelp?.()
    if (onboarding?.dismissed && online && !onboarding.migrationRequired) {
      commandMutation.mutate(
        { action: 'resume' },
        {
          onSuccess: (saved) => {
            setOpenOverride(true)
            const stepKey = saved.checklist[0]?.stepKey
            if (stepKey) {
              emitOnboardingTelemetry(online, space.id, saved, stepKey, 'shown')
            }
          },
        },
      )
      return
    }
    setOpenOverride(true)
    const stepKey = onboarding?.checklist[0]?.stepKey
    if (onboarding && stepKey) {
      emitOnboardingTelemetry(online, space.id, onboarding, stepKey, 'shown')
    }
  }

  const saveStartingPoint = () => {
    if (!selectedPoint || effectiveReadOnly || !online || onboarding?.migrationRequired) return
    commandMutation.mutate(startingPointCommand(selectedPoint), {
      onSuccess: (saved) => {
        setOpenOverride(true)
        setSelectedPointDraft(undefined)
        message.success('起步方式已保存；尚未安装模板，也未发布配置')
        emitOnboardingTelemetry(
          online,
          space.id,
          saved,
          'choose_starting_point',
          'succeeded',
        )
      },
    })
  }

  const dismiss = () => {
    if (!online || onboarding?.migrationRequired) return
    commandMutation.mutate(
      { action: 'dismiss' },
      {
        onSuccess: (saved) => {
          setOpenOverride(false)
          message.success('已暂停主动提示，可随时从“继续引导”恢复')
          const stepKey = saved.checklist[0]?.stepKey
          if (stepKey) {
            emitOnboardingTelemetry(online, space.id, saved, stepKey, 'dismissed')
          }
        },
      },
    )
  }

  const reset = () => {
    if (!online || effectiveReadOnly || onboarding?.migrationRequired) return
    modal.confirm({
      title: '重置当前用户的引导？',
      content: '只会清理你在此空间的引导选择和提示记录，不会删除成员、工作项、模板或已发布配置。',
      okText: '确认重置',
      cancelText: '取消',
      onOk: () => commandMutation.mutateAsync(
        { action: 'reset' },
        {
          onSuccess: () => {
            setSelectedPointDraft(undefined)
            message.success('引导已重置，业务数据没有变化')
          },
        },
      ),
    })
  }

  const launcherLabel = onboarding
    ? onboarding.dismissed || onboarding.startingPoint.kind !== 'unselected'
      ? '继续引导'
      : '开始使用'
    : '使用引导'

  return (
    <>
      <Space wrap>
        <Button
          icon={<CompassOutlined />}
          data-testid="project-space-onboarding-open"
          onClick={openGuide}
          loading={commandMutation.isPending && onboarding?.dismissed}
        >
          {launcherLabel}
        </Button>
        <Button
          type="text"
          icon={<QuestionCircleOutlined />}
          aria-label={`了解${contextHelp.title}`}
          onClick={() => {
            onExperienceHelp?.()
            setOpenOverride(true)
          }}
        >
          此页怎么用
        </Button>
      </Space>

      <Drawer
        open={open}
        width="min(580px, 100vw)"
        title={(
          <Space>
            <CompassOutlined />
            <span>项目空间使用引导</span>
          </Space>
        )}
        extra={onboarding?.track ? <Tag color={trackColor(onboarding.track)}>{trackLabel(onboarding.track)}</Tag> : null}
        onClose={() => setOpenOverride(false)}
        footer={onboarding ? (
          <Space wrap style={{ width: '100%', justifyContent: 'space-between' }}>
            <Button
              data-testid="onboarding-dismiss"
              disabled={experienceCommandDisabled || commandMutation.isPending}
              onClick={dismiss}
            >
              稍后提醒
            </Button>
            <Button
              danger
              type="text"
              data-testid="onboarding-reset"
              disabled={experienceCommandDisabled || effectiveReadOnly || commandMutation.isPending}
              onClick={reset}
            >
              重置我的引导
            </Button>
          </Space>
        ) : null}
      >
        <div data-testid="project-space-onboarding">
          <div data-testid="project-onboarding">
            {!online ? (
              <Alert
                showIcon
                type="warning"
                message="当前离线"
                description="可查看已经缓存的说明，但不能保存选择、更新提示记录或发起业务操作。恢复网络后会重新校准服务端状态。"
                style={{ marginBottom: 16 }}
              />
            ) : null}

            {onboardingQuery.isLoading && !onboarding ? <Skeleton active paragraph={{ rows: 8 }} /> : null}
            {onboardingQuery.isError && !onboarding ? (
              <OnboardingLoadError error={onboardingQuery.error} onRetry={() => onboardingQuery.refetch()} />
            ) : null}
            {online && !onboardingQuery.isLoading && !onboardingQuery.isError && !onboarding ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无可用引导" />
            ) : null}

            {onboarding ? (
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                {onboarding.migrationRequired ? (
                  <Alert
                    showIcon
                    type="info"
                    message="引导内容已有新版本"
                    description="升级只迁移当前用户的引导状态，不会改变业务配置。"
                    action={(
                      <Button
                        size="small"
                        disabled={!online || commandMutation.isPending}
                        onClick={() => commandMutation.mutate({ action: 'upgrade_flow' })}
                      >
                        升级引导
                      </Button>
                    )}
                  />
                ) : null}

                {effectiveReadOnly ? (
                  <Alert
                    showIcon
                    type="warning"
                    message="当前空间为只读状态"
                    description="引导仅解释现状；涉及安装、配置、成员和工作项写入的入口已关闭。请联系空间 owner，或使用当前受权的恢复入口。"
                  />
                ) : null}

                <Collapse
                  size="small"
                  items={[{
                    key: contextHelp.title,
                    label: `此页怎么用：${contextHelp.title}`,
                    children: (
                      <Space direction="vertical" size={8}>
                        <Typography.Text><strong>是什么：</strong>{contextHelp.what}</Typography.Text>
                        <Typography.Text><strong>何时需要：</strong>{contextHelp.when}</Typography.Text>
                        <Typography.Text><strong>下一步：</strong>{contextHelp.next}</Typography.Text>
                      </Space>
                    ),
                  }]}
                />

                {onboarding.track === 'manager' ? (
                  <StartingPointChooser
                    value={selectedPoint}
                    savedValue={onboardingStartingPointValue(onboarding.startingPoint)}
                    disabled={experienceCommandDisabled || effectiveReadOnly || commandMutation.isPending}
                    onChange={setSelectedPointDraft}
                    onSave={saveStartingPoint}
                  />
                ) : null}

                <Divider style={{ margin: 0 }} />

                <section data-testid="onboarding-checklist" aria-label={checklistTitle(onboarding.track)}>
                  <Space direction="vertical" size={12} style={{ width: '100%' }}>
                    <div>
                      <Typography.Title level={4} style={{ marginBottom: 4 }}>{checklistTitle(onboarding.track)}</Typography.Title>
                      <Typography.Text type="secondary">
                        “已看过/已跳过”只记录引导提示，不代表安装、发布、交接或工作已经完成。
                      </Typography.Text>
                    </div>
                    {onboarding.checklist.length === 0 ? (
                      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前身份没有可用步骤" />
                    ) : onboarding.checklist.map((item) => {
                      const copy = resolveOnboardingStepCopy(item)
                      const acknowledgement = onboardingStepAcknowledgement(onboarding, item.stepKey)
                      const path = item.stepKey === 'choose_starting_point'
                        ? null
                        : resolveOnboardingOwnerPath(item, space.id)
                      const canOpen = canOpenOnboardingStep(
                        { ...item, path },
                        space.availableActions,
                        online,
                        effectiveReadOnly,
                      )
                      return (
                        <Card
                          key={item.stepKey}
                          size="small"
                          title={(
                            <Space wrap>
                              {stepIcon(item.status, acknowledgement)}
                              <span>{copy.label}</span>
                            </Space>
                          )}
                          extra={<StepStatusTag status={item.status} acknowledgement={acknowledgement} />}
                        >
                          <Space direction="vertical" size={10} style={{ width: '100%' }}>
                            <Typography.Text type="secondary">{copy.help}</Typography.Text>
                            {item.dependencies.length > 0 ? (
                              <Typography.Text type="secondary">
                                需要先处理 {item.dependencies.length} 个前置引导提示。
                              </Typography.Text>
                            ) : null}
                            <details>
                              <summary>技术诊断</summary>
                              <Typography.Text type="secondary">
                                公共 owner：<Typography.Text code>{item.ownerContract}</Typography.Text>
                                {' · '}
                                服务端状态：<Typography.Text code>{item.status}</Typography.Text>
                              </Typography.Text>
                            </details>
                            <Space wrap>
                              {item.stepKey === 'choose_starting_point' ? (
                                <Button
                                  type="primary"
                                  size="small"
                                  data-testid={`onboarding-action-${item.stepKey}`}
                                  disabled={effectiveReadOnly}
                                  onClick={() => document.querySelector(
                                    '[data-testid="onboarding-starting-point"]',
                                  )?.scrollIntoView({ behavior: 'smooth', block: 'start' })}
                                >
                                  回到起步方式
                                </Button>
                              ) : null}
                              {path ? (
                                <Button
                                  type="primary"
                                  size="small"
                                  data-testid={`onboarding-action-${item.stepKey}`}
                                  disabled={!canOpen}
                                  onClick={() => {
                                    setOpenOverride(false)
                                    navigate(path)
                                  }}
                                >
                                  {onboarding.track === 'guest' ? '只读查看' : copy.actionLabel}
                                </Button>
                              ) : null}
                              {!acknowledgement ? (
                                <>
                                  <Button
                                    size="small"
                                  type="link"
                                    disabled={experienceCommandDisabled || commandMutation.isPending}
                                    onClick={() => commandMutation.mutate({
                                      action: 'acknowledge_step',
                                      stepKey: item.stepKey,
                                      acknowledgement: 'seen',
                                    })}
                                  >
                                    标记提示已读
                                  </Button>
                                  <Button
                                    size="small"
                                  type="link"
                                    disabled={experienceCommandDisabled || commandMutation.isPending}
                                    onClick={() => commandMutation.mutate({
                                      action: 'acknowledge_step',
                                      stepKey: item.stepKey,
                                      acknowledgement: 'skipped',
                                    })}
                                  >
                                    不再提示此步
                                  </Button>
                                </>
                              ) : null}
                            </Space>
                          </Space>
                        </Card>
                      )
                    })}
                  </Space>
                </section>

                <Divider style={{ margin: 0 }} />
                <Space wrap style={{ width: '100%', justifyContent: 'space-between' }}>
                  <Space>
                    <SafetyCertificateOutlined />
                    <Typography.Text>分享匿名引导结果</Typography.Text>
                  </Space>
                  <Switch
                    checked={!onboarding.telemetryOptOut}
                    disabled={experienceCommandDisabled || commandMutation.isPending}
                    aria-label="分享匿名引导结果"
                    onChange={(checked) => commandMutation.mutate({
                      action: 'set_telemetry_opt_out',
                      telemetryOptOut: !checked,
                    })}
                  />
                </Space>
                <Typography.Text type="secondary">
                  仅包含稳定步骤、结果、耗时区间和安全错误码；不包含标题、字段值、评论、附件名或个人评分。
                </Typography.Text>
              </Space>
            ) : null}
          </div>
        </div>
      </Drawer>
    </>
  )
}

function StartingPointChooser({
  value,
  savedValue,
  disabled,
  onChange,
  onSave,
}: {
  value?: StartingPointValue
  savedValue?: StartingPointValue
  disabled: boolean
  onChange: (value: StartingPointValue) => void
  onSave: () => void
}) {
  return (
    <section data-testid="onboarding-starting-point" aria-label="选择起步方式">
      <div data-testid="project-space-onboarding-paths">
        <Typography.Title level={4} style={{ marginBottom: 4 }}>选择起步方式</Typography.Title>
        <Typography.Paragraph type="secondary">
          保存选择不会安装模板，也不会发布配置。安装和发布必须在各自业务页面再次明确确认。
        </Typography.Paragraph>
        <Radio.Group
          value={value}
          disabled={disabled}
          onChange={(event) => onChange(event.target.value as StartingPointValue)}
          style={{ width: '100%' }}
        >
          <Space direction="vertical" size={8} style={{ width: '100%' }}>
            {PROJECT_SPACE_ONBOARDING_STARTING_POINTS.map((point) => (
              <Card
                key={point.key}
                size="small"
                styles={{ body: { padding: 12 } }}
              >
                <Radio value={point.key}>
                  <Typography.Text strong>{point.label}</Typography.Text>
                </Radio>
                <Typography.Paragraph type="secondary" style={{ margin: '8px 0 0 24px' }}>
                  {point.summary}
                  <br />
                  {point.effect}
                </Typography.Paragraph>
              </Card>
            ))}
          </Space>
        </Radio.Group>
        <Button
          type="primary"
          style={{ marginTop: 12 }}
          disabled={disabled || !value || value === savedValue}
          onClick={onSave}
        >
          保存起步方式
        </Button>
      </div>
    </section>
  )
}

function OnboardingLoadError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  const presentation = onboardingErrorPresentation(error)
  return (
    <Alert
      showIcon
      type="error"
      message={presentation.title}
      description={presentation.description}
      action={<Button size="small" onClick={onRetry}>重新加载</Button>}
    />
  )
}

function StepStatusTag({
  status,
  acknowledgement,
}: {
  status: 'available' | 'verify_on_owner_api' | 'blocked'
  acknowledgement?: 'seen' | 'skipped'
}) {
  if (acknowledgement === 'seen') return <Tag color="blue">提示已读</Tag>
  if (acknowledgement === 'skipped') return <Tag>已跳过提示</Tag>
  if (status === 'verify_on_owner_api') return <Tag color="gold">到业务页核验</Tag>
  if (status === 'blocked') return <Tag color="default">当前不可用</Tag>
  return <Tag color="green">可继续</Tag>
}

function stepIcon(
  status: 'available' | 'verify_on_owner_api' | 'blocked',
  acknowledgement?: 'seen' | 'skipped',
) {
  if (acknowledgement) return <CheckCircleOutlined style={{ color: '#1677ff' }} />
  if (status === 'blocked') return <ExclamationCircleOutlined style={{ color: '#8c8c8c' }} />
  if (status === 'verify_on_owner_api') return <ClockCircleOutlined style={{ color: '#d48806' }} />
  return <CheckCircleOutlined style={{ color: '#389e0d' }} />
}

function trackLabel(track: 'manager' | 'member' | 'guest') {
  if (track === 'manager') return '管理者引导'
  if (track === 'member') return '成员引导'
  return '访客只读引导'
}

function trackColor(track: 'manager' | 'member' | 'guest') {
  if (track === 'manager') return 'purple'
  if (track === 'member') return 'blue'
  return 'default'
}

function checklistTitle(track: 'manager' | 'member' | 'guest') {
  if (track === 'manager') return '空间设置清单'
  if (track === 'member') return '开始协作清单'
  return '访客查看清单'
}

function emitOnboardingTelemetry(
  online: boolean,
  spaceId: string,
  view: ProjectSpaceOnboardingView,
  stepKey: string,
  outcome: 'shown' | 'succeeded' | 'dismissed',
) {
  if (!online) return
  void recordProjectSpaceOnboardingTelemetry(
    spaceId,
    [{
      eventId: createOnboardingEventId(),
      flowVersion: view.currentFlowVersion,
      stepKey,
      outcome,
      durationBucket: 'unknown',
      errorCode: 'none',
    }],
    view.telemetryOptOut,
  ).catch(() => undefined)
}
