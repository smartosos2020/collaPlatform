import {
  ExperimentOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Empty,
  Input,
  List,
  Select,
  Space,
  Steps,
  Tag,
  Typography,
} from 'antd'
import { useState } from 'react'

import {
  automationRuleKeys,
  executeAutomationRule,
  getAutomationFoundation,
  getAutomationRuns,
} from '../api/automationRulesApi'
import type { UserProjectSpace } from '../api/projectSpacesApi'
import { errorMessage, formatTime } from '../projectSpaceView'

export function AutomationExecutionPanel({ space }: { space: UserProjectSpace }) {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const [ruleId, setRuleId] = useState<string>()
  const [aggregateId, setAggregateId] = useState<string>(() => crypto.randomUUID())
  const rules = useQuery({
    queryKey: automationRuleKeys.detail(space.id),
    queryFn: () => getAutomationFoundation(space.id),
  })
  const runs = useQuery({
    queryKey: automationRuleKeys.runs(space.id),
    queryFn: () => getAutomationRuns(space.id),
  })
  const execute = useMutation({
    mutationFn: (dryRun: boolean) => executeAutomationRule(
      space.id,
      ruleId!,
      dryRun,
      {
        eventType: 'project.work-item.changed',
        aggregateId,
        spaceId: space.id,
      },
    ),
    onSuccess: async (run) => {
      await queryClient.invalidateQueries({ queryKey: automationRuleKeys.runs(space.id) })
      message.success(run.dryRun ? '规则预览已完成，未产生业务副作用' : `规则执行结果：${run.status}`)
    },
    onError: (error) => message.error(errorMessage(error, '规则执行失败，请校准权限和版本')),
  })
  const configurable = space.status === 'active'
    && ['owner', 'admin'].includes(space.currentUserRole ?? '')
  const availableRules = rules.data?.rules.filter((rule) => rule.publishedVersion != null) ?? []

  return (
    <Card
      className="content-card automation-execution-panel"
      data-testid="automation-execution-panel"
      title={<Space><PlayCircleOutlined />自动化执行与步骤</Space>}
      extra={(
        <Button
          icon={<ReloadOutlined />}
          loading={runs.isFetching}
          onClick={() => void runs.refetch()}
        >
          REST 校准
        </Button>
      )}
    >
      {(rules.isError || runs.isError) && (
        <Alert
          type="error"
          showIcon
          message={errorMessage(rules.error ?? runs.error, '自动化执行记录加载失败')}
        />
      )}
      <Space wrap className="automation-execution-toolbar">
        <Select
          aria-label="执行规则"
          placeholder="选择已发布规则"
          value={ruleId}
          options={availableRules.map((rule) => ({
            value: rule.id,
            label: `${rule.name} · ${rule.status} · p${rule.publishedVersion}`,
          }))}
          onChange={setRuleId}
        />
        <Input
          aria-label="事件对象标识"
          value={aggregateId}
          onChange={(event) => setAggregateId(event.target.value)}
        />
        <Button
          icon={<ExperimentOutlined />}
          disabled={!configurable || !ruleId}
          loading={execute.isPending}
          onClick={() => execute.mutate(true)}
        >
          无副作用预览
        </Button>
        <Button
          type="primary"
          icon={<PlayCircleOutlined />}
          disabled={!configurable || !ruleId
            || availableRules.find((rule) => rule.id === ruleId)?.status !== 'enabled'}
          loading={execute.isPending}
          onClick={() => execute.mutate(false)}
        >
          执行受控操作
        </Button>
      </Space>
      <List
        locale={{ emptyText: <Empty description="暂无运行历史" /> }}
        dataSource={runs.data?.runs ?? []}
        renderItem={(run) => (
          <List.Item>
            <List.Item.Meta
              title={(
                <Space wrap>
                  <Tag color={run.status === 'succeeded' ? 'success' : run.status === 'failed' ? 'error' : 'default'}>
                    {run.status}
                  </Tag>
                  <Typography.Text code>{run.id}</Typography.Text>
                  {run.dryRun && <Tag>dry-run</Tag>}
                  <Typography.Text type="secondary">{formatTime(run.startedAt)}</Typography.Text>
                </Space>
              )}
              description={(
                <Steps
                  size="small"
                  current={Math.max(0, run.steps.length - 1)}
                  items={run.steps.map((step) => ({
                    title: step.actionType,
                    status: step.status === 'failed'
                      ? 'error'
                      : step.status === 'succeeded'
                        ? 'finish'
                        : 'wait',
                    description: step.errorCode ?? step.status,
                  }))}
                />
              )}
            />
          </List.Item>
        )}
      />
      <Typography.Text type="secondary">
        每次运行最多 8 步；run、step 与 action receipt 分层幂等，错误仅显示稳定代码。
      </Typography.Text>
    </Card>
  )
}
