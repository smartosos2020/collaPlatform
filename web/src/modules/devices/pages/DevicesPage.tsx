import { DesktopOutlined, MobileOutlined, TabletOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App as AntdApp, Button, Card, Empty, Space, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'

import {
  listDevices,
  probePush,
  registerPushToken,
  revokeDevice,
  type DeviceSummary,
} from '../api/devicesApi'

export function DevicesPage() {
  const queryClient = useQueryClient()
  const { message } = AntdApp.useApp()
  const devicesQuery = useQuery({ queryKey: ['devices'], queryFn: listDevices })
  const refreshDevices = async () => {
    await queryClient.invalidateQueries({ queryKey: ['devices'] })
  }
  const revokeMutation = useMutation({
    mutationFn: revokeDevice,
    onSuccess: async () => {
      message.success('设备已撤销')
      await refreshDevices()
    },
  })
  const pushMutation = useMutation({
    mutationFn: (deviceId: string) => registerPushToken(deviceId, `fake-${crypto.randomUUID()}`),
    onSuccess: async () => {
      message.success('假 Push Token 已登记')
      await refreshDevices()
    },
  })
  const probeMutation = useMutation({
    mutationFn: probePush,
    onSuccess: (result) => {
      message.info(result.deliverable ? `假通道可投递：${result.enabledTokenCount} 个 token` : '当前设备没有可投递 token')
    },
  })

  const columns: ColumnsType<DeviceSummary> = [
    {
      title: '设备',
      dataIndex: 'deviceName',
      render: (_, device) => (
        <Space>
          {deviceIcon(device.deviceType)}
          <Space orientation="vertical" size={0}>
            <Typography.Text strong>{device.deviceName || device.deviceType}</Typography.Text>
            <Typography.Text type="secondary">{device.deviceFingerprint}</Typography.Text>
          </Space>
        </Space>
      ),
    },
    {
      title: '类型',
      dataIndex: 'deviceType',
      width: 110,
      render: (value: string) => <Tag>{value}</Tag>,
    },
    {
      title: '状态',
      width: 150,
      render: (_, device) => (
        <Space wrap>
          {device.current ? <Tag color="blue">当前设备</Tag> : null}
          {device.revokedAt ? <Tag>已撤销</Tag> : <Tag color="green">可用</Tag>}
        </Space>
      ),
    },
    {
      title: '活跃会话',
      dataIndex: 'activeSessionCount',
      width: 110,
    },
    {
      title: 'Push',
      dataIndex: 'enabledPushTokenCount',
      width: 90,
    },
    {
      title: '最近活跃',
      dataIndex: 'lastActiveAt',
      width: 190,
      render: (value?: string | null) => value ? new Date(value).toLocaleString() : '-',
    },
    {
      title: '操作',
      width: 260,
      render: (_, device) => (
        <Space wrap>
          {device.current && !device.revokedAt ? (
            <>
              <Button size="small" loading={pushMutation.isPending} onClick={() => pushMutation.mutate(device.id)}>
                登记假 Push
              </Button>
              <Button size="small" loading={probeMutation.isPending} onClick={() => probeMutation.mutate(device.id)}>
                验证通道
              </Button>
            </>
          ) : null}
          {!device.current && !device.revokedAt ? (
            <Button danger size="small" loading={revokeMutation.isPending} onClick={() => revokeMutation.mutate(device.id)}>
              撤销
            </Button>
          ) : null}
        </Space>
      ),
    },
  ]

  return (
    <Space orientation="vertical" size={16} className="page-stack">
      <Space className="page-toolbar" wrap>
        <div>
          <Typography.Title level={2}>设备管理</Typography.Title>
          <Typography.Text type="secondary">查看多端登录设备、撤销设备、验证假 Push 通道。</Typography.Text>
        </div>
      </Space>

      <Card>
        <Table
          rowKey="id"
          loading={devicesQuery.isLoading}
          columns={columns}
          dataSource={devicesQuery.data ?? []}
          locale={{ emptyText: <Empty description="暂无设备" /> }}
          pagination={false}
          scroll={{ x: 980 }}
        />
      </Card>
    </Space>
  )
}

function deviceIcon(deviceType: DeviceSummary['deviceType']) {
  if (deviceType === 'desktop') {
    return <DesktopOutlined />
  }
  if (deviceType === 'ios' || deviceType === 'android') {
    return <MobileOutlined />
  }
  return <TabletOutlined />
}
