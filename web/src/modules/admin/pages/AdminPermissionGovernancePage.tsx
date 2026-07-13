import { DownloadOutlined, SearchOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App as AntdApp, Button, Form, Input, Select, Space, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import { listAdminKnowledgeBases } from '../api/adminKnowledgeBasesApi'
import { listMembers } from '../api/adminUsersApi'
import {
  exportPermissionRisks,
  inspectPermission,
  listPermissionRisks,
  remediatePermissionRisk,
  type InspectPermissionParams,
  type PermissionRiskItem,
} from '../api/permissionGovernanceApi'

export function AdminPermissionGovernancePage() {
  const { message, modal } = AntdApp.useApp()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [searchParams] = useSearchParams()
  const [form] = Form.useForm<InspectPermissionParams>()
  const [knowledgeBaseId, setKnowledgeBaseId] = useState<string | undefined>(() => searchParams.get('knowledgeBaseId') || undefined)
  const [severity, setSeverity] = useState<string | undefined>(() => searchParams.get('severity') || undefined)
  const [riskQuery, setRiskQuery] = useState(() => searchParams.get('q') || '')
  const membersQuery = useQuery({ queryKey: ['admin', 'users'], queryFn: () => listMembers() })
  const spacesQuery = useQuery({ queryKey: ['admin', 'knowledge-bases', 'permission-governance'], queryFn: () => listAdminKnowledgeBases({ includeArchived: true }) })
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
  const remediationMutation = useMutation({
    mutationFn: ({ riskId, confirm }: { riskId: string; confirm: boolean }) => remediatePermissionRisk(riskId, confirm),
    onSuccess: async (result, variables) => {
      if (!variables.confirm) {
        if (!result.executable) {
          message.warning(result.reason)
          return
        }
        modal.confirm({
          title: '确认单项修复权限风险？',
          content: `${result.reason} 风险规则：${result.ruleCode}`,
          okText: '确认修复',
          cancelText: '取消',
          onOk: () => remediationMutation.mutateAsync({ riskId: result.riskId, confirm: true }),
        })
        return
      }
      message.success(result.reason)
      await queryClient.invalidateQueries({ queryKey: ['admin', 'permission-governance', 'risks'] })
    },
  })
  const filteredRisks = useMemo(() => {
    const query = riskQuery.trim().toLowerCase()
    return (risksQuery.data?.items ?? []).filter((risk) =>
      (!severity || risk.severity === severity)
      && (!query || [risk.ruleCode, risk.reason, risk.resourceType, risk.resourceId, risk.subjectName, risk.subjectId]
        .some((value) => value?.toLowerCase().includes(query))),
    )
  }, [riskQuery, risksQuery.data?.items, severity])

  const columns: ColumnsType<PermissionRiskItem> = [
    {
      title: '规则',
      dataIndex: 'ruleCode',
      render: (value, record) => (
        <Space orientation="vertical" size={0}>
          <Typography.Text strong>{value}</Typography.Text>
          <Typography.Text type="secondary">{record.reason}</Typography.Text>
          {record.suggestedAction ? <Typography.Text type="secondary">建议：{record.suggestedAction}</Typography.Text> : null}
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
      title: '影响范围',
      key: 'impactScope',
      render: (_, record) => record.impactScope ? `${record.impactScope.resourceType}:${record.impactScope.resourceId}` : '-',
    },
    {
      title: '权限',
      dataIndex: 'permissionLevel',
      render: (level) => (level ? <Tag>{level}</Tag> : '-'),
    },
    {
      title: '快捷操作',
      key: 'actions',
      render: (_, record) => (
        <Space>
          {record.resourceType && record.resourceId ? (
            <Button size="small" onClick={() => navigate(`/admin/audit-logs?targetType=${record.resourceType}&targetId=${record.resourceId}`)}>
              审计
            </Button>
          ) : null}
          {record.subjectType === 'user' && record.subjectId ? (
            <Button size="small" onClick={() => navigate(`/admin/users?userId=${record.subjectId}`)}>成员</Button>
          ) : null}
          {record.subjectType === 'user' && record.subjectId && record.resourceType && record.resourceId ? (
            <Button
              size="small"
              onClick={() => {
                form.setFieldsValue({
                  userId: record.subjectId ?? undefined,
                  resourceType: record.resourceType ?? undefined,
                  resourceId: record.resourceId ?? undefined,
                  action: 'view',
                })
                inspectMutation.mutate({
                  userId: record.subjectId ?? '',
                  resourceType: record.resourceType ?? '',
                  resourceId: record.resourceId ?? '',
                  action: 'view',
                })
              }}
            >
              授权
            </Button>
          ) : null}
          <Button
            size="small"
            danger={record.severity === 'high' || record.severity === 'critical'}
            loading={remediationMutation.isPending && remediationMutation.variables?.riskId === record.id}
            onClick={() => remediationMutation.mutate({ riskId: record.id, confirm: false })}
          >
            处置
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <Space orientation="vertical" size={16} className="page-stack admin-org-page admin-permission-governance-page">
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
        <Form.Item name="resourceType" initialValue="knowledge_content" rules={[{ required: true }]}>
          <Select
            className="permission-governance-resource-type"
            options={[
              { label: '知识内容', value: 'knowledge_content' },
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
        <Select
          allowClear
          placeholder="风险级别"
          value={severity}
          onChange={setSeverity}
          options={['critical', 'high', 'medium', 'low'].map((value) => ({ label: value, value }))}
        />
        <Input.Search
          allowClear
          placeholder="规则、资源、授权主体"
          value={riskQuery}
          onChange={(event) => setRiskQuery(event.target.value)}
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
      {risksQuery.isError || inspectMutation.isError || exportMutation.isError || remediationMutation.isError ? <Alert type="error" showIcon message="权限风险操作失败" description="请检查资源权限后重试；未确认的动作不会写入。" /> : null}

      <Table
        rowKey="id"
        loading={risksQuery.isLoading}
        columns={columns}
        dataSource={filteredRisks}
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
