import { ApiOutlined, SafetyCertificateOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App as AntdApp, Button, Card, Empty, Form, Input, List, Space, Tag, Typography } from 'antd'
import {
  automationRuleKeys, getAutomationConnectors, saveAutomationConnector, testAutomationConnector,
} from '../api/automationRulesApi'
import type { UserProjectSpace } from '../api/projectSpacesApi'
import { errorMessage, formatTime } from '../projectSpaceView'

export function AutomationConnectorsPanel({ space }: { space: UserProjectSpace }) {
  const [form] = Form.useForm<{ name: string; targetUri: string; credentialReference?: string }>()
  const { message } = AntdApp.useApp()
  const client = useQueryClient()
  const query = useQuery({
    queryKey: automationRuleKeys.connectors(space.id),
    queryFn: () => getAutomationConnectors(space.id),
  })
  const refresh = () => client.invalidateQueries({ queryKey: automationRuleKeys.connectors(space.id) })
  const save = useMutation({
    mutationFn: (value: { name: string; targetUri: string; credentialReference?: string }) =>
      saveAutomationConnector(space.id, { ...value, expectedVersion: 0 }),
    onSuccess: async () => { form.resetFields(); await refresh(); message.success('连接器已保存') },
    onError: (error) => message.error(errorMessage(error, '连接器保存失败')),
  })
  const test = useMutation({
    mutationFn: (id: string) => testAutomationConnector(space.id, id, true),
    onSuccess: async () => { await refresh(); message.success('无网络副作用测试完成') },
    onError: (error) => message.error(errorMessage(error, '连接器测试失败')),
  })
  const configurable = space.status === 'active' && ['owner', 'admin'].includes(space.currentUserRole ?? '')
  return (
    <Card className="content-card automation-connectors-panel" data-testid="automation-connectors-panel"
      title={<Space><ApiOutlined />Webhook 与连接器</Space>}>
      {query.isError && <Alert type="error" showIcon message={errorMessage(query.error, '连接器加载失败')} />}
      <Form form={form} layout="vertical" onFinish={(value) => save.mutate(value)}>
        <Space wrap align="end">
          <Form.Item name="name" label="名称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="targetUri" label="HTTPS 目标" rules={[{ required: true, type: 'url' }]}>
            <Input placeholder="https://hooks.example.com/colla" />
          </Form.Item>
          <Form.Item name="credentialReference" label="凭据引用"><Input placeholder="vault://…" /></Form.Item>
          <Form.Item><Button type="primary" htmlType="submit" disabled={!configurable} loading={save.isPending}>保存连接器</Button></Form.Item>
        </Space>
      </Form>
      <List dataSource={query.data?.connectors ?? []} locale={{ emptyText: <Empty description="暂无连接器" /> }}
        renderItem={(connector) => <List.Item actions={[
          <Button key="test" disabled={!configurable} loading={test.isPending}
            onClick={() => test.mutate(connector.id)}>无副作用测试</Button>,
        ]}><List.Item.Meta title={<Space><Tag color="success">{connector.status}</Tag>{connector.name}</Space>}
          description={<Typography.Text code>{connector.targetUri}</Typography.Text>} /></List.Item>} />
      <Typography.Title level={5}>投递与死信</Typography.Title>
      <List dataSource={query.data?.deliveries ?? []} locale={{ emptyText: <Empty description="暂无投递" /> }}
        renderItem={(delivery) => <List.Item><Space wrap>
          <Tag color={delivery.status === 'succeeded' ? 'success' : delivery.status === 'dead_letter' ? 'error' : 'processing'}>
            {delivery.status}</Tag>
          <Typography.Text code>{delivery.id}</Typography.Text>
          <span>attempt {delivery.attemptCount}</span><span>{formatTime(delivery.createdAt)}</span>
          {delivery.deadLetterReason && <span><SafetyCertificateOutlined /> {delivery.deadLetterReason}</span>}
        </Space></List.Item>} />
      <Typography.Text type="secondary">
        仅 HTTPS；禁止重定向、内网与元数据地址。凭据只保存引用，签名值和 payload 不进入历史或错误。
      </Typography.Text>
    </Card>
  )
}
