import { DownloadOutlined, SafetyCertificateOutlined, SearchOutlined } from '@ant-design/icons'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Alert, App as AntdApp, Button, Form, Input, Select, Space, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useState } from 'react'

import { listMembers } from '../api/adminUsersApi'
import { AdminModuleNav } from '../components/AdminModuleNav'
import { listKnowledgeBases } from '../../knowledgeBases/api/knowledgeBasesApi'
import {
  exportPermissionRisks,
  inspectPermission,
  listPermissionRisks,
  type InspectPermissionParams,
  type PermissionRiskItem,
} from '../api/permissionGovernanceApi'

export function AdminPermissionGovernancePage() {
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<InspectPermissionParams>()
  const [knowledgeBaseId, setKnowledgeBaseId] = useState<string | undefined>()
  const membersQuery = useQuery({ queryKey: ['admin', 'users'], queryFn: () => listMembers() })
  const spacesQuery = useQuery({ queryKey: ['knowledge-bases', 'permission-governance'], queryFn: () => listKnowledgeBases({ includeArchived: true }) })
  const risksQuery = useQuery({
    queryKey: ['admin', 'permission-governance', 'risks', knowledgeBaseId],
    queryFn: () => listPermissionRisks({ knowledgeBaseId }),
  })

  const inspectMutation = useMutation({
    mutationFn: inspectPermission,
  })

  const exportMutation = useMutation({
    mutationFn: () => exportPermissionRisks({ knowledgeBaseId }),
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
    <Space orientation="vertical" size={16} className="page-stack admin-org-page admin-permission-governance-page">
      <Space className="page-toolbar admin-saas-toolbar" wrap>
        <Space size={12}>
          <span className="admin-page-icon">
            <SafetyCertificateOutlined />
          </span>
          <Typography.Title level={2}>权限治理</Typography.Title>
        </Space>
        <AdminModuleNav />
      </Space>

      <Form
        form={form}
        layout="inline"
        className="permission-governance-inspect-form"
        onFinish={(values) => inspectMutation.mutate(values)}
      >
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
              { label: 'knowledge_base', value: 'knowledge_base' },
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
        <Button icon={<DownloadOutlined />} loading={exportMutation.isPending} onClick={() => exportMutation.mutate()}>
          导出风险
        </Button>
        <Select
          allowClear
          showSearch
          className="permission-governance-kb"
          loading={spacesQuery.isLoading}
          placeholder="按知识库筛选风险"
          value={knowledgeBaseId}
          onChange={setKnowledgeBaseId}
          optionFilterProp="label"
          options={(spacesQuery.data ?? []).map((space) => ({ value: space.id, label: space.name }))}
        />
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
