import {
  ApartmentOutlined,
  CloudSyncOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  SaveOutlined,
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
  Tag,
  Typography,
} from 'antd'
import { useEffect, useMemo, useState } from 'react'

import {
  automationRuleKeys,
  changeAutomationRuleLifecycle,
  getAutomationFoundation,
  publishAutomationRule,
  saveAutomationRule,
  type AutomationRule,
} from '../api/automationRulesApi'
import type { UserProjectSpace } from '../api/projectSpacesApi'
import { errorMessage, formatTime } from '../projectSpaceView'

export function AutomationRulesPanel({ space }: { space: UserProjectSpace }) {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const [selectedId, setSelectedId] = useState<string>()
  const [name, setName] = useState('')
  const [eventType, setEventType] = useState('project.work-item.changed')
  const [reference, setReference] = useState('event.aggregateId')
  const [operator, setOperator] = useState('exists')
  const [value, setValue] = useState('')
  const [actionType, setActionType] = useState('send_notification')
  const [offlineDraft, setOfflineDraft] = useState('')
  const [online, setOnline] = useState(() => navigator.onLine)
  const query = useQuery({
    queryKey: automationRuleKeys.detail(space.id),
    queryFn: () => getAutomationFoundation(space.id),
  })
  const current = useMemo(
    () => query.data?.rules.find((rule) => rule.id === selectedId),
    [query.data, selectedId],
  )
  useEffect(() => {
    const markOnline = () => setOnline(true)
    const markOffline = () => setOnline(false)
    window.addEventListener('online', markOnline)
    window.addEventListener('offline', markOffline)
    return () => {
      window.removeEventListener('online', markOnline)
      window.removeEventListener('offline', markOffline)
    }
  }, [])
  const selectRule = (rule: AutomationRule) => {
    setSelectedId(rule.id)
    setName(rule.name)
    setEventType(String(rule.trigger.eventType ?? 'project.work-item.changed'))
    setReference(String(rule.condition.reference ?? 'event.aggregateId'))
    setOperator(String(rule.condition.operator ?? 'exists'))
    setValue(String(rule.condition.value ?? ''))
    setActionType(String(rule.actions[0]?.actionType ?? 'send_notification'))
  }
  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: automationRuleKeys.detail(space.id) })
  const saveMutation = useMutation({
    mutationFn: () => saveAutomationRule(space.id, {
      ruleId: current?.id,
      expectedVersion: current?.version ?? 0,
      name,
      eventType,
      conditionReference: reference,
      conditionOperator: operator,
      conditionValue: value,
      actionType,
    }),
    onSuccess: async (rule) => {
      setSelectedId(rule.id)
      await refresh()
      message.success('自动化规则草稿已保存')
    },
    onError: (error) => message.error(errorMessage(error, '自动化规则保存失败，请校准后重试')),
  })
  const publishMutation = useMutation({
    mutationFn: (rule: AutomationRule) => publishAutomationRule(space.id, rule),
    onSuccess: async () => {
      await refresh()
      message.success('不可变规则版本已发布')
    },
    onError: (error) => message.error(errorMessage(error, '规则发布失败，请校准版本')),
  })
  const lifecycleMutation = useMutation({
    mutationFn: ({
      rule,
      action,
    }: {
      rule: AutomationRule
      action: 'enable' | 'disable' | 'archive'
    }) => changeAutomationRuleLifecycle(space.id, rule, action),
    onSuccess: async () => {
      await refresh()
      message.success('规则状态已更新')
    },
    onError: (error) => message.error(errorMessage(error, '规则状态更新失败')),
  })
  const configurable = space.status === 'active'
    && ['owner', 'admin'].includes(space.currentUserRole ?? '')
  const pending = saveMutation.isPending
    || publishMutation.isPending
    || lifecycleMutation.isPending

  return (
    <Card
      className="content-card automation-rules-panel"
      data-testid="automation-rules-panel"
      title={<Space><ApartmentOutlined />自动化规则</Space>}
      extra={(
        <Button
          icon={<ReloadOutlined />}
          loading={query.isFetching}
          onClick={() => void query.refetch()}
        >
          REST 校准
        </Button>
      )}
    >
      {!online && (
        <Alert
          type="warning"
          showIcon
          message="离线 · 本地输入保留，规则不会伪装成已发布"
        />
      )}
      {query.isError && (
        <Alert type="error" showIcon message={errorMessage(query.error, '自动化规则加载失败')} />
      )}
      {query.data && (
        <div className="automation-rules-layout">
          <section aria-label="自动化规则目录">
            <Typography.Title level={5}>规则目录</Typography.Title>
            {query.data.truncated && <Alert type="warning" message="规则目录已达到 100 条显示上限" />}
            <List
              locale={{ emptyText: <Empty description="暂无自动化规则" /> }}
              dataSource={query.data.rules}
              renderItem={(rule) => (
                <List.Item
                  className={rule.id === selectedId ? 'is-selected' : undefined}
                  onClick={() => selectRule(rule)}
                  actions={[
                    rule.publishedVersion == null ? (
                      <Button
                        key="publish"
                        size="small"
                        disabled={!configurable}
                        loading={publishMutation.isPending}
                        onClick={(event) => {
                          event.stopPropagation()
                          publishMutation.mutate(rule)
                        }}
                      >
                        发布
                      </Button>
                    ) : (
                      <Button
                        key="toggle"
                        size="small"
                        disabled={!configurable}
                        icon={rule.status === 'enabled'
                          ? <PauseCircleOutlined />
                          : <PlayCircleOutlined />}
                        onClick={(event) => {
                          event.stopPropagation()
                          lifecycleMutation.mutate({
                            rule,
                            action: rule.status === 'enabled' ? 'disable' : 'enable',
                          })
                        }}
                      >
                        {rule.status === 'enabled' ? '停用' : '启用'}
                      </Button>
                    ),
                  ]}
                >
                  <List.Item.Meta
                    title={<Space><span>{rule.name}</span><Tag>{rule.status}</Tag></Space>}
                    description={`v${rule.version} · 发布版本 ${rule.publishedVersion ?? '-'} · ${formatTime(rule.updatedAt)}`}
                  />
                </List.Item>
              )}
            />
          </section>
          <section aria-label="自动化规则编辑器">
            <Typography.Title level={5}>声明式 Trigger → Condition → Action</Typography.Title>
            <Space direction="vertical" className="automation-rule-form">
              <Input
                aria-label="规则名称"
                maxLength={160}
                value={name}
                placeholder="例如：事项变化时发送通知"
                onChange={(event) => setName(event.target.value)}
              />
              <Select
                aria-label="触发事件"
                value={eventType}
                options={query.data.events.map((event) => ({
                  value: event.eventType,
                  label: `${event.eventType} v${event.eventVersion}`,
                }))}
                onChange={setEventType}
              />
              <Input
                aria-label="条件字段引用"
                value={reference}
                onChange={(event) => setReference(event.target.value)}
              />
              <Select
                aria-label="条件操作符"
                value={operator}
                options={['exists', 'equals', 'not_equals', 'contains', 'gt', 'gte', 'lt', 'lte']
                  .map((item) => ({ value: item, label: item }))}
                onChange={setOperator}
              />
              <Input
                aria-label="条件值"
                value={value}
                disabled={operator === 'exists'}
                onChange={(event) => setValue(event.target.value)}
              />
              <Select
                aria-label="受控操作"
                value={actionType}
                options={query.data.actions.map((action) => ({
                  value: action.actionType,
                  label: `${action.actionType} · ${action.owner}`,
                }))}
                onChange={setActionType}
              />
              <Input.TextArea
                data-testid="automation-rule-offline-draft"
                value={offlineDraft}
                maxLength={2_000}
                placeholder="本地设计备注（不上传、不作为规则事实）"
                onChange={(event) => setOfflineDraft(event.target.value)}
              />
              <Space wrap>
                <Button
                  type="primary"
                  icon={<SaveOutlined />}
                  disabled={!configurable || !online || name.trim().length < 2}
                  loading={saveMutation.isPending}
                  onClick={() => saveMutation.mutate()}
                >
                  保存规则草稿
                </Button>
                <Button
                  icon={<CloudSyncOutlined />}
                  disabled={!configurable || !current || !online}
                  loading={pending}
                  onClick={() => current && publishMutation.mutate(current)}
                >
                  发布不可变版本
                </Button>
              </Space>
              <Typography.Text type="secondary">
                条件最多 64 节点 / 8 层，操作最多 8 个；不接受脚本、SQL 或任意代码。
              </Typography.Text>
            </Space>
          </section>
        </div>
      )}
    </Card>
  )
}
