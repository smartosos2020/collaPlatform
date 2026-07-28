import { ControlOutlined, PauseCircleOutlined, PlayCircleOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App as AntdApp, Button, Card, Col, Empty, List, Progress, Row, Select, Space, Statistic, Tag, Typography } from 'antd'
import { useState } from 'react'
import {
  automationRuleKeys, getAutomationManagement, governAutomationQuota,
  saveAutomationManagementPreference, type AutomationQuotaState,
} from '../api/automationRulesApi'
import type { UserProjectSpace } from '../api/projectSpacesApi'
import { errorMessage } from '../projectSpaceView'

export function AutomationManagementPanel({ space }: { space: UserProjectSpace }) {
  const { message } = AntdApp.useApp()
  const client = useQueryClient()
  const [filter, setFilter] = useState('all')
  const query = useQuery({
    queryKey: automationRuleKeys.management(space.id),
    queryFn: () => getAutomationManagement(space.id),
  })
  const refresh = () => client.invalidateQueries({ queryKey: automationRuleKeys.management(space.id) })
  const preference = useMutation({
    mutationFn: (defaultFilter: string) => saveAutomationManagementPreference(space.id, {
      compactMode: false, defaultFilter, expectedVersion: query.data?.preference.version ?? 0,
    }),
    onSuccess: async () => { await refresh(); message.success('管理偏好已保存') },
  })
  const govern = useMutation({
    mutationFn: ({ quota, action }: { quota: AutomationQuotaState; action: 'pause' | 'resume' }) =>
      governAutomationQuota(space.id, quota, action),
    onSuccess: async () => { await refresh(); message.success('限额治理已提交') },
    onError: (error) => message.error(errorMessage(error, '限额治理失败')),
  })
  const configurable = space.status === 'active' && ['owner', 'admin'].includes(space.currentUserRole ?? '')
  const data = query.data
  const visibleRuns = (data?.executions.runs ?? []).filter((run) =>
    filter === 'all' || (filter === 'failed' && run.status === 'failed'))
  return (
    <Card className="content-card automation-management-panel" data-testid="automation-management-panel"
      title={<Space><ControlOutlined />自动化管理与限额</Space>}
      extra={<Select aria-label="管理筛选" value={filter} options={[
        { value: 'all', label: '全部' }, { value: 'failed', label: '失败' },
        { value: 'paused', label: '已暂停' }, { value: 'dead_letter', label: '死信' },
      ]} onChange={(value) => { setFilter(value); preference.mutate(value) }} />}>
      {query.isError && <Alert type="error" showIcon message={errorMessage(query.error, '管理视图加载失败')} />}
      {data && !data.healthy && <Alert type="warning" showIcon message="自动化需要关注" description={data.diagnostics.join(' · ')} />}
      <Row gutter={[16, 16]}>
        <Col xs={12} md={6}><Statistic title="规则" value={data?.rules.rules.length ?? 0} /></Col>
        <Col xs={12} md={6}><Statistic title="运行" value={visibleRuns.length} /></Col>
        <Col xs={12} md={6}><Statistic title="连接器" value={data?.connectors.connectors.length ?? 0} /></Col>
        <Col xs={12} md={6}><Statistic title="死信" value={data?.connectors.deliveries.filter((x) => x.status === 'dead_letter').length ?? 0} /></Col>
      </Row>
      <Typography.Title level={5}>空间 / 规则 / Actor / Action 限额</Typography.Title>
      <List dataSource={data?.quotas ?? []} locale={{ emptyText: <Empty description="首次真实执行后生成限额窗口" /> }}
        renderItem={(quota) => {
          const paused = quota.pausedUntil && new Date(quota.pausedUntil) > new Date()
          return <List.Item actions={[<Button key="govern" disabled={!configurable} loading={govern.isPending}
            icon={paused ? <PlayCircleOutlined /> : <PauseCircleOutlined />}
            onClick={() => govern.mutate({ quota, action: paused ? 'resume' : 'pause' })}>
            {paused ? '恢复' : '暂停'}
          </Button>]}>
            <List.Item.Meta title={<Space><Tag>{quota.quotaType}</Tag><Typography.Text code>{quota.quotaKey}</Typography.Text></Space>}
              description={<Progress percent={Math.min(100, Math.round(quota.usedCount * 100 / quota.limitCount))}
                format={() => `${quota.usedCount}/${quota.limitCount}`} status={paused ? 'exception' : 'normal'} />} />
          </List.Item>
        }} />
      <Typography.Text type="secondary">
        计数在 claim 前后由服务器原子执行；暂停不会删除历史，恢复不会把缓存升级为授权。
      </Typography.Text>
    </Card>
  )
}
