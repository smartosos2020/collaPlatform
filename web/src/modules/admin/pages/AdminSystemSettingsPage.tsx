import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  DatabaseOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App as AntdApp, Card, Descriptions, Empty, Space, Statistic, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'

import { listDevices, revokeDevice, type DeviceSummary } from '../../devices/api/devicesApi'
import { listNotificationPreferences, type NotificationPreference } from '../../notifications/api/notificationsApi'
import { getAdminSystemSettings, type AdminSystemSettings } from '../api/systemSettingsApi'

export function AdminSystemSettingsPage() {
  const settingsQuery = useQuery({ queryKey: ['admin', 'system-settings'], queryFn: getAdminSystemSettings })

  return (
    <div className="page-stack admin-system-settings-page" data-testid="admin-system-settings-page">
      {settingsQuery.isError ? <Alert type="error" showIcon message="系统设置暂时无法加载" description="请检查服务连接后重试。" /> : null}
      <Card loading={settingsQuery.isLoading} title={<Space><DatabaseOutlined />企业信息</Space>}>
        {settingsQuery.data ? <WorkspaceDetails settings={settingsQuery.data} /> : <Empty description="暂无企业信息" />}
      </Card>
      <Card loading={settingsQuery.isLoading} title={<Space><LockOutlined />默认安全策略</Space>}>
        {settingsQuery.data ? <PolicyDetails settings={settingsQuery.data} /> : <Empty description="暂无安全策略" />}
      </Card>
      <Card loading={settingsQuery.isLoading} title={<Space><SettingOutlined />运行信息</Space>}>
        {settingsQuery.data ? <RuntimeDetails settings={settingsQuery.data} /> : <Empty description="暂无运行信息" />}
      </Card>
      <Alert
        type="info"
        showIcon
        message="系统设置当前为只读视图"
        description="企业信息、默认策略和运行指标从服务端配置读取；需要变更时请走发布配置流程并保留审计记录。"
      />
    </div>
  )
}

export function AdminSecurityPage() {
  const queryClient = useQueryClient()
  const { message } = AntdApp.useApp()
  const settingsQuery = useQuery({ queryKey: ['admin', 'system-settings'], queryFn: getAdminSystemSettings })
  const devicesQuery = useQuery({ queryKey: ['admin', 'security', 'devices'], queryFn: listDevices })
  const preferencesQuery = useQuery({ queryKey: ['admin', 'security', 'notification-preferences'], queryFn: listNotificationPreferences })
  const revokeMutation = useMutation({
    mutationFn: revokeDevice,
    onSuccess: async () => {
      message.success('登录设备已撤销')
      await queryClient.invalidateQueries({ queryKey: ['admin', 'security', 'devices'] })
    },
  })

  const deviceColumns: ColumnsType<DeviceSummary> = [
    {
      title: '设备',
      dataIndex: 'deviceName',
      render: (_, device) => <Space orientation="vertical" size={0}><Typography.Text strong>{device.deviceName || device.deviceType}</Typography.Text><Typography.Text type="secondary">{device.deviceFingerprint}</Typography.Text></Space>,
    },
    { title: '类型', dataIndex: 'deviceType', render: (value: string) => <Tag>{value}</Tag> },
    { title: '状态', render: (_, device) => device.current ? <Tag color="blue">当前设备</Tag> : device.revokedAt ? <Tag>已撤销</Tag> : <Tag color="green">可用</Tag> },
    { title: '最近活跃', dataIndex: 'lastActiveAt', render: (value?: string | null) => value ? new Date(value).toLocaleString() : '-' },
    { title: '操作', render: (_, device) => !device.current && !device.revokedAt ? <a role="button" tabIndex={0} onClick={() => revokeMutation.mutate(device.id)} onKeyDown={(event) => { if (event.key === 'Enter') revokeMutation.mutate(device.id) }}>撤销</a> : <Typography.Text type="secondary">—</Typography.Text> },
  ]

  return (
    <div className="page-stack admin-security-page" data-testid="admin-security-page">
      {settingsQuery.isError || devicesQuery.isError || preferencesQuery.isError ? <Alert type="error" showIcon message="安全信息加载不完整" description="请检查服务连接后重试。" /> : null}
      <Card loading={settingsQuery.isLoading} title={<Space><SafetyCertificateOutlined />安全策略</Space>}>
        {settingsQuery.data ? <PolicyDetails settings={settingsQuery.data} /> : <Empty description="暂无安全策略" />}
      </Card>
      <Card title={<Space><ClockCircleOutlined />登录设备与会话</Space>} loading={devicesQuery.isLoading}>
        <Typography.Paragraph type="secondary">可查看并撤销当前管理员账号的登录设备；当前设备需要先退出登录后再结束会话。</Typography.Paragraph>
        <Table rowKey="id" columns={deviceColumns} dataSource={devicesQuery.data ?? []} pagination={false} locale={{ emptyText: <Empty description="暂无登录设备" /> }} />
      </Card>
      <Card title={<Space><CheckCircleOutlined />必要通知</Space>} loading={preferencesQuery.isLoading}>
        <Table<NotificationPreference>
          rowKey="sourceType"
          size="small"
          pagination={false}
          dataSource={(preferencesQuery.data ?? []).filter((preference) => preference.required)}
          columns={[{ title: '类别', dataIndex: 'sourceType', render: (value: string) => notificationSourceText[value] ?? value }, { title: '状态', dataIndex: 'enabled', render: (enabled: boolean) => <Tag color={enabled ? 'green' : 'red'}>{enabled ? '始终送达' : '异常'}</Tag> }]}
          locale={{ emptyText: <Empty description="暂无必要通知策略" /> }}
        />
      </Card>
    </div>
  )
}

function WorkspaceDetails({ settings }: { settings: AdminSystemSettings }) {
  return <Descriptions column={{ xs: 1, sm: 2 }} size="small"><Descriptions.Item label="企业名称">{settings.workspace.name}</Descriptions.Item><Descriptions.Item label="标识">{settings.workspace.slug}</Descriptions.Item><Descriptions.Item label="状态"><Tag color="green">{settings.workspace.status}</Tag></Descriptions.Item><Descriptions.Item label="创建时间">{new Date(settings.workspace.createdAt).toLocaleString()}</Descriptions.Item></Descriptions>
}

function PolicyDetails({ settings }: { settings: AdminSystemSettings }) {
  const policy = settings.securityPolicy
  return <Descriptions column={{ xs: 1, sm: 2 }} size="small"><Descriptions.Item label="密码长度">至少 {policy.passwordMinLength} 位</Descriptions.Item><Descriptions.Item label="密码复杂度">{policy.passwordRequireLetter ? '需要字母' : '无需字母'} · {policy.passwordRequireDigit ? '需要数字' : '无需数字'}</Descriptions.Item><Descriptions.Item label="访问令牌">{policy.accessTokenTtlMinutes} 分钟</Descriptions.Item><Descriptions.Item label="刷新令牌">{policy.refreshTokenTtlDays} 天</Descriptions.Item><Descriptions.Item label="权限与安全通知"><Tag color="green">必要送达</Tag></Descriptions.Item><Descriptions.Item label="系统通知"><Tag color="green">必要送达</Tag></Descriptions.Item></Descriptions>
}

function RuntimeDetails({ settings }: { settings: AdminSystemSettings }) {
  return <Space wrap className="admin-system-runtime-metrics"><Statistic title="成员" value={settings.runtime.memberCount} /><Statistic title="活跃会话" value={settings.runtime.activeSessionCount} /><Statistic title="活跃设备" value={settings.runtime.activeDeviceCount} /><Typography.Text type="secondary">服务：{settings.runtime.service} · {settings.runtime.healthEndpoint}</Typography.Text></Space>
}

const notificationSourceText: Record<string, string> = { resource: '权限与安全', system: '系统通知' }
