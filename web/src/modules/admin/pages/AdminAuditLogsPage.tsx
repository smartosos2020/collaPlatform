import { SearchOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Button, Form, Input, Select, Space, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { objectTypeText } from '../../platform/objectTypeLabels'
import { listAuditLogs, type AuditLogEntry, type AuditLogFilters } from '../api/auditLogsApi'

const targetTypeOptions = [
  { label: '全部对象', value: '' },
  { label: '事项', value: 'issue' },
  { label: '项目', value: 'project' },
  { label: '文档', value: 'document' },
  { label: '表格', value: 'base' },
  { label: '审批', value: 'approval' },
  { label: '用户', value: 'user' },
  { label: '消息', value: 'message' },
]

export function AdminAuditLogsPage() {
  const [searchParams] = useSearchParams()
  const initialFilters = searchParams.get('permissionOnly') === 'true'
    ? { limit: 100, action: 'resource.permission.granted' }
    : { limit: 100 }
  const [form] = Form.useForm<AuditLogFilters>()
  const [filters, setFilters] = useState<AuditLogFilters>(initialFilters)
  const auditLogsQuery = useQuery({
    queryKey: ['admin', 'audit-logs', filters],
    queryFn: () => listAuditLogs(filters),
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
        <Typography.Text code>{JSON.stringify(metadata ?? {})}</Typography.Text>
      ),
    },
  ]

  return (
    <Space orientation="vertical" size={16} className="page-stack">
      <Space className="page-toolbar">
        <Typography.Title level={2}>审计日志</Typography.Title>
        <Space>
          <Button onClick={() => applyActionFilter('resource.permission.granted')}>权限授予</Button>
          <Button onClick={() => applyActionFilter('resource.permission.revoked')}>权限撤销</Button>
          <Button onClick={() => applyActionFilter('role.permissions.updated')}>角色权限</Button>
        </Space>
      </Space>

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
      </Form>

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
}
