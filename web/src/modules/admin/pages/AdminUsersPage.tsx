import {
  CameraOutlined,
  CheckCircleOutlined,
  KeyOutlined,
  PlusOutlined,
  SearchOutlined,
  StopOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App as AntdApp, Button, Form, Input, Modal, Select, Space, Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMemo, useRef, useState } from 'react'

import { completeUpload, createUploadUrl, getFileDownloadUrl } from '../../files/api/filesApi'
import { AdminModuleNav } from '../components/AdminModuleNav'
import {
  createMember,
  disableMember,
  enableMember,
  listMembers,
  resetMemberPassword,
  updateMemberAvatar,
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
  const { message } = AntdApp.useApp()
  const [createOpen, setCreateOpen] = useState(false)
  const [resetUser, setResetUser] = useState<MemberSummary | null>(null)
  const [departmentId, setDepartmentId] = useState<string | undefined>()
  const [filterType, setFilterType] = useState<'department' | 'name'>('department')
  const [memberKeyword, setMemberKeyword] = useState('')
  const [selectedMemberId, setSelectedMemberId] = useState<string>()
  const avatarInputRef = useRef<HTMLInputElement>(null)
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
    queryKey: ['admin', 'users', { departmentId: filterType === 'department' ? departmentId : undefined }],
    queryFn: () => listMembers({ departmentId: filterType === 'department' ? departmentId : undefined }),
  })

  const members = useMemo(() => membersQuery.data ?? [], [membersQuery.data])
  const visibleMembers = useMemo(() => {
    const keyword = memberKeyword.trim().toLowerCase()
    if (filterType !== 'name' || !keyword) {
      return members
    }
    return members.filter((member) =>
      [member.displayName, member.username, member.email ?? ''].some((value) => value.toLowerCase().includes(keyword)),
    )
  }, [filterType, memberKeyword, members])
  const selectedMember = visibleMembers.find((member) => member.id === selectedMemberId) ?? visibleMembers[0]

  const avatarUrlQuery = useQuery({
    queryKey: ['files', selectedMember?.avatarFileId, 'download-url'],
    queryFn: () => getFileDownloadUrl(selectedMember?.avatarFileId as string),
    enabled: Boolean(selectedMember?.avatarFileId),
  })

  const refreshMembers = async () => {
    await queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
  }

  const createMutation = useMutation({
    mutationFn: createMember,
    onSuccess: async (member) => {
      message.success('成员已创建')
      setSelectedMemberId(member.id)
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

  const avatarMutation = useMutation({
    mutationFn: async ({ userId, file }: { userId: string; file: File }) => {
      const upload = await createUploadUrl({
        fileName: file.name,
        contentType: file.type || 'application/octet-stream',
        sizeBytes: file.size,
      })
      await fetch(upload.uploadUrl, {
        method: 'PUT',
        headers: upload.headers,
        body: file,
      })
      const completed = await completeUpload({ fileId: upload.uploadId })
      await updateMemberAvatar(userId, completed.id)
      return completed.id
    },
    onSuccess: async () => {
      message.success('头像已更新')
      await refreshMembers()
    },
    onError: (error) => {
      message.error(error instanceof Error ? error.message : '头像更新失败')
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
              停用
            </Button>
          ) : (
            <Button
              className="admin-success-outline"
              size="small"
              icon={<CheckCircleOutlined />}
              loading={enableMutation.isPending}
              onClick={() => enableMutation.mutate(record.id)}
            >
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
        <AdminModuleNav />
      </Space>

      <div className="admin-members-layout">
        <div className="admin-org-panel admin-member-sidebar">
          <Button block type="primary" icon={<PlusOutlined />} className="admin-sidebar-create admin-sidebar-create-top" onClick={() => setCreateOpen(true)}>
            新增成员
          </Button>

          <div className="admin-member-filter-card">
            <div className="admin-member-search-row">
              <Button
                className="admin-filter-toggle-button"
                onClick={() => {
                  setFilterType(filterType === 'department' ? 'name' : 'department')
                  setSelectedMemberId(undefined)
                }}
              >
                {filterType === 'department' ? '部门' : '名称'}
              </Button>
              {filterType === 'department' ? (
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
                  onChange={(value) => {
                    setDepartmentId(value)
                    setSelectedMemberId(undefined)
                  }}
                />
              ) : (
                <Input
                  allowClear
                  prefix={<SearchOutlined />}
                  placeholder="按成员名称/账号/邮箱筛选"
                  value={memberKeyword}
                  onChange={(event) => {
                    setMemberKeyword(event.target.value)
                    setSelectedMemberId(undefined)
                  }}
                />
              )}
            </div>
          </div>

          <MemberInfoCard
            member={selectedMember}
            avatarUrl={avatarUrlQuery.data?.downloadUrl}
            avatarLoading={avatarMutation.isPending}
            onPickAvatar={() => avatarInputRef.current?.click()}
          />
          <input
            ref={avatarInputRef}
            hidden
            type="file"
            accept="image/*"
            onChange={(event) => {
              const file = event.target.files?.[0]
              event.target.value = ''
              if (!file || !selectedMember) {
                return
              }
              if (!file.type.startsWith('image/')) {
                message.error('请选择图片文件')
                return
              }
              avatarMutation.mutate({ userId: selectedMember.id, file })
            }}
          />
        </div>

        <div className="admin-data-card admin-members-table-card">
          <Table
            rowKey="id"
            loading={membersQuery.isLoading}
            columns={columns}
            dataSource={visibleMembers}
            locale={{ emptyText: <AdminTableEmpty /> }}
            pagination={{ pageSize: 10, placement: ['bottomEnd'] }}
            rowClassName={(record) => (record.id === selectedMember?.id ? 'admin-table-row-selected' : '')}
            onRow={(record) => ({
              onClick: () => setSelectedMemberId(record.id),
            })}
          />
        </div>
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

function MemberInfoCard({
  member,
  avatarUrl,
  avatarLoading,
  onPickAvatar,
}: {
  member?: MemberSummary
  avatarUrl?: string
  avatarLoading: boolean
  onPickAvatar: () => void
}) {
  if (!member) {
    return (
      <div className="admin-member-profile-card empty">
        <span className="admin-empty-icon">
          <UserOutlined />
        </span>
        <Typography.Text type="secondary">No data</Typography.Text>
      </div>
    )
  }

  return (
    <div className="admin-member-profile-card">
      <div className="admin-member-profile-top">
        <div className="admin-member-avatar-wrap">
          {avatarUrl ? (
            <img className="admin-member-avatar-image" src={avatarUrl} alt={member.displayName} />
          ) : (
            <span className="admin-member-avatar-large">{entityInitial(member.displayName || member.username)}</span>
          )}
          <Button
            shape="circle"
            size="small"
            className="admin-member-avatar-edit"
            icon={<CameraOutlined />}
            loading={avatarLoading}
            onClick={onPickAvatar}
          />
        </div>
      </div>

      <div className="admin-member-profile-section">
        <span>名称</span>
        <strong>{member.displayName}</strong>
      </div>
      <div className="admin-member-profile-section">
        <span>编号</span>
        <strong>{member.username}</strong>
      </div>
      <div className="admin-member-profile-section">
        <span>邮箱</span>
        <strong>{member.email || '-'}</strong>
      </div>
      <div className="admin-member-profile-summary-row">
        <div>
          <span>状态</span>
          <span className={`admin-status-pill ${member.status === 'active' ? 'active' : 'disabled'}`}>{member.status}</span>
        </div>
        <div>
          <span>角色</span>
          <Space size={6} wrap>
            {member.roles.length ? member.roles.map((role) => (
              <span key={role} className="admin-soft-badge purple">{role}</span>
            )) : <span className="admin-soft-badge gray">未分配</span>}
          </Space>
        </div>
        <div>
          <span>部门</span>
          <Space size={6} wrap>
            {member.departments.length ? member.departments.map((department) => (
              <span
                key={`${department.departmentId}-${department.relationType}`}
                className={`admin-soft-badge ${department.relationType === 'primary' ? 'purple' : 'gray'}`}
              >
                {department.departmentName}
              </span>
            )) : <span className="admin-soft-badge gray">未分配</span>}
          </Space>
        </div>
      </div>
      <div className="admin-member-profile-grid">
        <div>
          <span>创建时间</span>
          <strong>{formatDateTime(member.createdAt)}</strong>
        </div>
        <div>
          <span>最近登录</span>
          <strong>{member.lastLoginAt ? formatDateTime(member.lastLoginAt) : '-'}</strong>
        </div>
      </div>
    </div>
  )
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
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
