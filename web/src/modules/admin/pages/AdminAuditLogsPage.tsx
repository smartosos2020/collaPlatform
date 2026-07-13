import { DownloadOutlined, SearchOutlined } from '@ant-design/icons'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Alert, App as AntdApp, Button, Form, Input, Select, Space, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { objectTypeText } from '../../platform/objectTypeLabels'
import { exportAuditLogs, listAuditLogs, type AuditLogEntry, type AuditLogFilters } from '../api/auditLogsApi'

const targetTypeOptions = [
  { label: '全部对象', value: '' },
  { label: '事项', value: 'issue' },
  { label: '项目', value: 'project' },
  { label: '知识内容', value: 'knowledge_content' },
  { label: '知识库', value: 'knowledge_base' },
  { label: '表格', value: 'base' },
  { label: '审批', value: 'approval' },
  { label: '用户', value: 'user' },
  { label: '消息', value: 'message' },
]

export function AdminAuditLogsPage() {
  const { message } = AntdApp.useApp()
  const [searchParams] = useSearchParams()
  const initialFilters = searchParams.get('permissionOnly') === 'true'
    ? { limit: 100, action: 'resource.permission.granted' }
    : {
        limit: 100,
        action: searchParams.get('action') || undefined,
        targetType: searchParams.get('targetType') || undefined,
        targetId: searchParams.get('targetId') || undefined,
        actorId: searchParams.get('actorId') || undefined,
      }
  const [form] = Form.useForm<AuditLogFilters>()
  const [filters, setFilters] = useState<AuditLogFilters>(initialFilters)
  const auditLogsQuery = useQuery({
    queryKey: ['admin', 'audit-logs', filters],
    queryFn: () => listAuditLogs(filters),
  })
  const exportMutation = useMutation({
    mutationFn: () => exportAuditLogs(filters),
    onSuccess: (csv) => {
      const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
      const link = document.createElement('a')
      link.href = url
      link.download = 'audit-logs.csv'
      link.click()
      URL.revokeObjectURL(url)
      message.success('审计日志已导出')
    },
  })

  const columns: ColumnsType<AuditLogEntry> = [
    {
      title: '时间',
      dataIndex: 'createdAt',
      width: 180,
      render: (value: string) => new Date(value).toLocaleString(),
    },
    {
      title: '操作者',
      dataIndex: 'actorName',
      width: 160,
      render: (_, record) => (
        <Space orientation="vertical" size={0}>
          <Typography.Text>{record.actorName || '系统'}</Typography.Text>
          {record.actorId ? <Typography.Text type="secondary">{record.actorId}</Typography.Text> : null}
        </Space>
      ),
    },
    {
      title: '动作',
      dataIndex: 'action',
      width: 220,
      render: (value: string) => <Tag>{value}</Tag>,
    },
    {
      title: '对象',
      key: 'target',
      width: 260,
      render: (_, record) => (
        <Space orientation="vertical" size={0}>
          <Typography.Text>{objectTypeText[record.targetType] ?? record.targetType}</Typography.Text>
          {record.targetId ? <Typography.Text type="secondary">{record.targetId}</Typography.Text> : null}
        </Space>
      ),
    },
    {
      title: '上下文',
      dataIndex: 'metadata',
      render: (metadata: Record<string, unknown>) => (
        <Typography.Text code>{safeMetadataText(metadata)}</Typography.Text>
      ),
    },
  ]

  return (
    <Space orientation="vertical" size={16} className="page-stack admin-org-page admin-audit-logs-page">
      <Form
        form={form}
        layout="inline"
        className="audit-log-filters"
        initialValues={filters}
        onFinish={(values) => setFilters({ ...values, limit: values.limit ?? 100 })}
      >
        <Form.Item name="action">
          <Input allowClear placeholder="动作，例如 issue.updated" />
        </Form.Item>
        <Form.Item name="targetType">
          <Select className="audit-log-target-select" options={targetTypeOptions} />
        </Form.Item>
        <Form.Item name="targetId">
          <Input allowClear placeholder="对象 ID" />
        </Form.Item>
        <Form.Item name="actorId">
          <Input allowClear placeholder="操作者 ID" />
        </Form.Item>
        <Form.Item name="limit">
          <Input type="number" min={1} max={200} placeholder="数量" />
        </Form.Item>
        <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
          查询
        </Button>
        <Button icon={<DownloadOutlined />} loading={exportMutation.isPending} onClick={() => exportMutation.mutate()}>
          导出当前结果
        </Button>
        <Space className="admin-audit-shortcuts" wrap>
          <Button onClick={() => applyActionFilter('resource.permission.granted')}>权限授予</Button>
          <Button onClick={() => applyActionFilter('resource.permission.revoked')}>权限撤销</Button>
          <Button onClick={() => applyActionFilter('role.permissions.updated')}>角色权限</Button>
          <Button onClick={() => applyKnowledgeFilter('knowledge_base.updated', 'knowledge_base')}>知识库设置</Button>
          <Button onClick={() => applyKnowledgeFilter('resource.permission.granted', 'knowledge_base')}>知识库成员</Button>
          <Button onClick={() => applyKnowledgeFilter('knowledge_base.governance.bulk_updated', 'knowledge_base')}>批量治理</Button>
          <Button onClick={() => applyKnowledgeFilter('resource.permission.inheritance.broken', 'knowledge_content')}>继承变更</Button>
        </Space>
      </Form>

      {auditLogsQuery.isError || exportMutation.isError ? <Alert type="error" showIcon message="审计操作失败" description="请检查筛选条件或管理权限后重试。" /> : null}
      <Alert type="info" showIcon message="敏感字段最小展示" description="页面不展示 IP、User-Agent 或完整元数据；导出文件同样只包含审计定位必需字段。" />

      <Table
        rowKey="id"
        loading={auditLogsQuery.isLoading}
        columns={columns}
        dataSource={auditLogsQuery.data ?? []}
        pagination={{ pageSize: 20 }}
      />
    </Space>
  )

  function applyActionFilter(action: string) {
    const nextFilters = { ...filters, action, limit: filters.limit ?? 100 }
    form.setFieldsValue(nextFilters)
    setFilters(nextFilters)
  }

  function applyKnowledgeFilter(action: string, targetType: string) {
    const nextFilters = { ...filters, action, targetType, limit: filters.limit ?? 100 }
    form.setFieldsValue(nextFilters)
    setFilters(nextFilters)
  }
}

function safeMetadataText(metadata: Record<string, unknown>) {
  const allowed = ['sourceUi', 'apiSurface', 'client', 'requestPath']
  const visible = Object.fromEntries(allowed.filter((key) => metadata?.[key] !== undefined).map((key) => [key, metadata[key]]))
  return Object.keys(visible).length ? JSON.stringify(visible) : '—'
}
