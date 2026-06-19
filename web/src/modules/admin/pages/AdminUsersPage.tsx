import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App as AntdApp, Button, Form, Input, Modal, Space, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

import {
  createMember,
  disableMember,
  enableMember,
  listMembers,
  resetMemberPassword,
} from '../api/adminUsersApi'
import type { CreateMemberRequest, MemberSummary } from '../api/adminUsersApi'

export function AdminUsersPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const [createOpen, setCreateOpen] = useState(false)
  const [resetUser, setResetUser] = useState<MemberSummary | null>(null)
  const [createForm] = Form.useForm<CreateMemberRequest>()
  const [resetForm] = Form.useForm<{ newPassword: string }>()

  const membersQuery = useQuery({
    queryKey: ['admin', 'users'],
    queryFn: listMembers,
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
  })

  const columns: ColumnsType<MemberSummary> = [
    {
      title: '成员',
      dataIndex: 'displayName',
      render: (_, record) => (
        <Space orientation="vertical" size={0}>
          <Typography.Text strong>{record.displayName}</Typography.Text>
          <Typography.Text type="secondary">{record.username}</Typography.Text>
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
        <Space size={4}>
          {roles.map((role) => (
            <Tag key={role}>{role}</Tag>
          ))}
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      render: (status) => <Tag color={status === 'active' ? 'green' : 'default'}>{status}</Tag>,
    },
    {
      title: '操作',
      key: 'actions',
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => setResetUser(record)}>
            重置密码
          </Button>
          {record.status === 'active' ? (
            <Button
              size="small"
              danger
              loading={disableMutation.isPending}
              onClick={() => disableMutation.mutate(record.id)}
            >
              禁用
            </Button>
          ) : (
            <Button size="small" loading={enableMutation.isPending} onClick={() => enableMutation.mutate(record.id)}>
              启用
            </Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <Space orientation="vertical" size={16} className="page-stack">
      <Space className="page-toolbar">
        <Typography.Title level={2}>成员管理</Typography.Title>
        <Space>
          <Button onClick={() => navigate('/admin/audit-logs')}>审计日志</Button>
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            新增成员
          </Button>
        </Space>
      </Space>

      <Table
        rowKey="id"
        loading={membersQuery.isLoading}
        columns={columns}
        dataSource={membersQuery.data ?? []}
        pagination={{ pageSize: 10 }}
      />

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
          <Form.Item label="初始密码" name="password" rules={[{ required: true, min: 8, message: '至少 8 位' }]}>
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item label="角色" name="roleCode">
            <Input disabled />
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
          <Form.Item label="新密码" name="newPassword" rules={[{ required: true, min: 8, message: '至少 8 位' }]}>
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
