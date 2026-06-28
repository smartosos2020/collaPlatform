import { DownloadOutlined, SearchOutlined } from '@ant-design/icons'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Alert, App as AntdApp, Button, Form, Input, Select, Space, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'

import { listMembers } from '../api/adminUsersApi'
import {
  exportPermissionRisks,
  inspectPermission,
  listPermissionRisks,
  type InspectPermissionParams,
  type PermissionRiskItem,
} from '../api/permissionGovernanceApi'

export function AdminPermissionGovernancePage() {
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<InspectPermissionParams>()
  const membersQuery = useQuery({ queryKey: ['admin', 'users'], queryFn: () => listMembers() })
  const risksQuery = useQuery({ queryKey: ['admin', 'permission-governance', 'risks'], queryFn: listPermissionRisks })

  const inspectMutation = useMutation({
    mutationFn: inspectPermission,
  })

  const exportMutation = useMutation({
    mutationFn: exportPermissionRisks,
    onSuccess: (csv) => {
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = 'permission-risks.csv'
      link.click()
      URL.revokeObjectURL(url)
      message.success('风险列表已导出')
    },
  })

  const columns: ColumnsType<PermissionRiskItem> = [
    {
      title: '规则',
      dataIndex: 'ruleCode',
      render: (value, record) => (
        <Space orientation="vertical" size={0}>
          <Typography.Text strong>{value}</Typography.Text>
          <Typography.Text type="secondary">{record.reason}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '级别',
      dataIndex: 'severity',
      render: (severity) => <Tag color={severityColor(severity)}>{severity}</Tag>,
    },
    {
      title: '资源',
      dataIndex: 'resourceType',
      render: (_, record) => (record.resourceType ? `${record.resourceType}:${record.resourceId}` : '-'),
    },
    {
      title: '主体',
      dataIndex: 'subjectName',
      render: (_, record) => (record.subjectType ? `${record.subjectType}:${record.subjectName ?? record.subjectId}` : '-'),
    },
    {
      title: '权限',
      dataIndex: 'permissionLevel',
      render: (level) => (level ? <Tag>{level}</Tag> : '-'),
    },
  ]

  return (
    <Space orientation="vertical" size={16} className="page-stack">
      <Space className="page-toolbar">
        <Typography.Title level={2}>权限治理</Typography.Title>
        <Space>
          <Button onClick={() => navigate('/admin/roles')}>角色权限</Button>
          <Button onClick={() => navigate('/admin/audit-logs?permissionOnly=true')}>权限审计</Button>
          <Button icon={<DownloadOutlined />} loading={exportMutation.isPending} onClick={() => exportMutation.mutate()}>
            导出风险
          </Button>
        </Space>
      </Space>

      <Form form={form} layout="inline" onFinish={(values) => inspectMutation.mutate(values)}>
        <Form.Item name="userId" rules={[{ required: true, message: '请选择用户' }]}>
          <Select
            showSearch
            className="permission-governance-user"
            loading={membersQuery.isLoading}
            options={(membersQuery.data ?? []).map((member) => ({
              label: `${member.displayName} (${member.username})`,
              value: member.id,
            }))}
            optionFilterProp="label"
            placeholder="用户"
          />
        </Form.Item>
        <Form.Item name="resourceType" initialValue="document" rules={[{ required: true }]}>
          <Select
            className="permission-governance-resource-type"
            options={[
              { label: 'document', value: 'document' },
              { label: 'base', value: 'base' },
              { label: 'project', value: 'project' },
            ]}
          />
        </Form.Item>
        <Form.Item name="resourceId" rules={[{ required: true, message: '请输入资源 ID' }]}>
          <Input className="permission-governance-resource-id" placeholder="资源 ID" />
        </Form.Item>
        <Form.Item name="action" initialValue="view">
          <Select
            className="permission-governance-action"
            options={[
              { label: 'view', value: 'view' },
              { label: 'comment', value: 'comment' },
              { label: 'edit', value: 'edit' },
              { label: 'manage', value: 'manage' },
            ]}
          />
        </Form.Item>
        <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={inspectMutation.isPending}>
          排查
        </Button>
      </Form>

      {inspectMutation.data ? (
        <Alert
          type={inspectMutation.data.allowed ? 'success' : 'warning'}
          showIcon
          message={inspectMutation.data.allowed ? '允许访问' : '不允许访问'}
          description={`${inspectMutation.data.reason} 当前 ${inspectMutation.data.currentLevel}，需要 ${inspectMutation.data.requiredLevel}，来源 ${inspectMutation.data.source}。`}
        />
      ) : null}

      <Table
        rowKey="id"
        loading={risksQuery.isLoading}
        columns={columns}
        dataSource={risksQuery.data?.items ?? []}
        pagination={{ pageSize: 10 }}
      />
    </Space>
  )
}

function severityColor(severity: string) {
  if (severity === 'critical') return 'red'
  if (severity === 'high') return 'orange'
  if (severity === 'medium') return 'gold'
  return 'blue'
}
