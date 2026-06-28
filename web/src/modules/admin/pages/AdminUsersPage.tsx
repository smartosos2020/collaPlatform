import {
  ApartmentOutlined,
  AuditOutlined,
  KeyOutlined,
  PlusOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  StopOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App as AntdApp, Button, Form, Input, Modal, Select, Space, Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import {
  createMember,
  disableMember,
  enableMember,
  listMembers,
  resetMemberPassword,
} from '../api/adminUsersApi'
import type { CreateMemberRequest, MemberSummary } from '../api/adminUsersApi'
import { flattenDepartmentTree, listDepartmentTree } from '../api/departmentsApi'

const passwordRules = [
  { required: true, message: '请输入密码' },
  {
    pattern: /^(?=.*[A-Za-z])(?=.*\d).{8,}$/,
    message: '至少 8 位，且必须同时包含字母和数字',
  },
]

export function AdminUsersPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const [createOpen, setCreateOpen] = useState(false)
  const [resetUser, setResetUser] = useState<MemberSummary | null>(null)
  const [departmentId, setDepartmentId] = useState<string | undefined>()
  const [createForm] = Form.useForm<CreateMemberRequest>()
  const [resetForm] = Form.useForm<{ newPassword: string }>()

  const departmentsQuery = useQuery({
    queryKey: ['admin', 'departments', 'tree'],
    queryFn: listDepartmentTree,
  })

  const departments = useMemo(() => flattenDepartmentTree(departmentsQuery.data ?? []), [departmentsQuery.data])
  const departmentOptions = departments.map((department) => ({
    label: department.label,
    value: department.id,
  }))

  const membersQuery = useQuery({
    queryKey: ['admin', 'users', { departmentId }],
    queryFn: () => listMembers({ departmentId }),
  })

  const refreshMembers = async () => {
    await queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
  }

  const createMutation = useMutation({
    mutationFn: createMember,
    onSuccess: async () => {
      message.success('成员已创建')
      setCreateOpen(false)
      createForm.resetFields()
      await refreshMembers()
    },
    onError: (error) => {
      message.error(error instanceof Error ? error.message : '成员创建失败')
    },
  })

  const disableMutation = useMutation({
    mutationFn: disableMember,
    onSuccess: refreshMembers,
  })

  const enableMutation = useMutation({
    mutationFn: enableMember,
    onSuccess: refreshMembers,
  })

  const resetMutation = useMutation({
    mutationFn: ({ userId, newPassword }: { userId: string; newPassword: string }) =>
      resetMemberPassword(userId, newPassword),
    onSuccess: async () => {
      message.success('密码已重置')
      setResetUser(null)
      resetForm.resetFields()
      await refreshMembers()
    },
    onError: (error) => {
      message.error(error instanceof Error ? error.message : '密码重置失败')
    },
  })

  const columns: ColumnsType<MemberSummary> = [
    {
      title: '成员',
      dataIndex: 'displayName',
      render: (_, record) => (
        <Space size={12} className="admin-table-entity">
          <span className="admin-entity-avatar">{entityInitial(record.displayName || record.username)}</span>
          <Space orientation="vertical" size={0}>
            <Typography.Text strong>{record.displayName}</Typography.Text>
            <Typography.Text type="secondary">{record.username}</Typography.Text>
          </Space>
        </Space>
      ),
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      render: (value) => value || '-',
    },
    {
      title: '角色',
      dataIndex: 'roles',
      render: (roles: string[]) => (
        <Space size={6} wrap>
          {roles.map((role) => (
            <span key={role} className="admin-soft-badge purple">
              {role}
            </span>
          ))}
        </Space>
      ),
    },
    {
      title: '部门',
      dataIndex: 'departments',
      render: (_, record) => (
        <Space size={6} wrap>
          {record.departments?.length ? (
            record.departments.map((department) => (
              <span
                key={`${department.departmentId}-${department.relationType}`}
                className={`admin-soft-badge ${department.relationType === 'primary' ? 'purple' : 'gray'}`}
              >
                {department.departmentName}
              </span>
            ))
          ) : (
            <span className="admin-soft-badge gray">未分配</span>
          )}
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      render: (status) => <span className={`admin-status-pill ${status === 'active' ? 'active' : 'disabled'}`}>{status}</span>,
    },
    {
      title: '操作',
      key: 'actions',
      render: (_, record) => (
        <Space size={10}>
          <Button className="admin-action-button" size="small" icon={<KeyOutlined />} onClick={() => setResetUser(record)}>
            重置密码
          </Button>
          {record.status === 'active' ? (
            <Button
              className="admin-danger-outline"
              size="small"
              icon={<StopOutlined />}
              loading={disableMutation.isPending}
              onClick={() => disableMutation.mutate(record.id)}
            >
              禁用
            </Button>
          ) : (
            <Button className="admin-action-button" size="small" loading={enableMutation.isPending} onClick={() => enableMutation.mutate(record.id)}>
              启用
            </Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <Space orientation="vertical" size={16} className="page-stack admin-org-page admin-members-page">
      <Space className="page-toolbar admin-saas-toolbar" wrap>
        <Space size={12}>
          <span className="admin-page-icon">
            <TeamOutlined />
          </span>
          <Typography.Title level={2}>成员管理</Typography.Title>
        </Space>
        <Space wrap>
          <Button icon={<ApartmentOutlined />} onClick={() => navigate('/admin/departments')}>组织架构</Button>
          <Button icon={<TeamOutlined />} onClick={() => navigate('/admin/user-groups')}>用户组</Button>
          <Button icon={<SafetyCertificateOutlined />} onClick={() => navigate('/admin/roles')}>角色权限</Button>
          <Button icon={<SafetyCertificateOutlined />} onClick={() => navigate('/admin/permission-governance')}>权限治理</Button>
          <Button icon={<AuditOutlined />} onClick={() => navigate('/admin/audit-logs')}>审计日志</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
            新增成员
          </Button>
        </Space>
      </Space>

      <div className="admin-members-filter-row">
        <Select
          allowClear
          showSearch
          className="admin-user-department-filter"
          suffixIcon={<SearchOutlined />}
          loading={departmentsQuery.isLoading}
          options={departmentOptions}
          optionFilterProp="label"
          placeholder="按部门筛选成员"
          value={departmentId}
          onChange={setDepartmentId}
        />
      </div>

      <div className="admin-data-card admin-members-table-card">
        <Table
          rowKey="id"
          loading={membersQuery.isLoading}
          columns={columns}
          dataSource={membersQuery.data ?? []}
          locale={{ emptyText: <AdminTableEmpty /> }}
          pagination={{ pageSize: 10, placement: ['bottomEnd'] }}
        />
      </div>

      <Modal
        title="新增成员"
        open={createOpen}
        okText="创建"
        cancelText="取消"
        confirmLoading={createMutation.isPending}
        onCancel={() => setCreateOpen(false)}
        onOk={() => createForm.submit()}
      >
        <Form
          form={createForm}
          layout="vertical"
          initialValues={{ roleCode: 'member' }}
          onFinish={(values) => createMutation.mutate(values)}
        >
          <Form.Item label="账号" name="username" rules={[{ required: true, message: '请输入账号' }]}>
            <Input autoComplete="off" />
          </Form.Item>
          <Form.Item label="显示名称" name="displayName" rules={[{ required: true, message: '请输入显示名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="邮箱" name="email" rules={[{ type: 'email', message: '邮箱格式不正确' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="初始密码" name="password" rules={passwordRules}>
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item label="角色" name="roleCode">
            <Input disabled />
          </Form.Item>
          <Form.Item label="主部门" name="primaryDepartmentId">
            <Select
              allowClear
              showSearch
              loading={departmentsQuery.isLoading}
              options={departmentOptions}
              optionFilterProp="label"
              placeholder="可稍后在组织架构中维护"
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`重置密码：${resetUser?.displayName ?? ''}`}
        open={Boolean(resetUser)}
        okText="重置"
        cancelText="取消"
        confirmLoading={resetMutation.isPending}
        onCancel={() => setResetUser(null)}
        onOk={() => resetForm.submit()}
      >
        <Form
          form={resetForm}
          layout="vertical"
          onFinish={(values) => {
            if (resetUser) {
              resetMutation.mutate({ userId: resetUser.id, newPassword: values.newPassword })
            }
          }}
        >
          <Form.Item label="新密码" name="newPassword" rules={passwordRules}>
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}

function entityInitial(value: string) {
  return (value || '?').trim().slice(0, 1).toUpperCase()
}

function AdminTableEmpty() {
  return (
    <div className="admin-table-empty">
      <span className="admin-empty-icon">
        <UserOutlined />
      </span>
      <Typography.Text type="secondary">No data</Typography.Text>
    </div>
  )
}
