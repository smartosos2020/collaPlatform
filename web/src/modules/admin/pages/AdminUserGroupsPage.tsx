import {
  ApartmentOutlined,
  AuditOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  SearchOutlined,
  StopOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App as AntdApp, Button, Form, Input, Modal, Radio, Select, Space, Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { listMembers } from '../api/adminUsersApi'
import { flattenDepartmentTree, listDepartmentTree } from '../api/departmentsApi'
import {
  addUserGroupMember,
  createUserGroup,
  deleteUserGroup,
  disableUserGroup,
  listExpandedUserGroupMembers,
  listUserGroupMembers,
  listUserGroups,
  removeUserGroupMember,
  updateUserGroup,
  type ExpandedUserGroupMember,
  type UserGroupMember,
  type UserGroupRequest,
  type UserGroupSummary,
} from '../api/userGroupsApi'

type GroupModalState = { mode: 'create' } | { mode: 'edit'; group: UserGroupSummary }

type MemberForm = {
  subjectType: 'user' | 'department'
  subjectId: string
}

export function AdminUserGroupsPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { message, modal } = AntdApp.useApp()
  const [selectedGroupId, setSelectedGroupId] = useState<string>()
  const [groupSearch, setGroupSearch] = useState('')
  const [groupModal, setGroupModal] = useState<GroupModalState | null>(null)
  const [memberModalOpen, setMemberModalOpen] = useState(false)
  const [groupForm] = Form.useForm<UserGroupRequest>()
  const [memberForm] = Form.useForm<MemberForm>()

  const groupsQuery = useQuery({ queryKey: ['admin', 'user-groups'], queryFn: () => listUserGroups() })
  const usersQuery = useQuery({ queryKey: ['admin', 'users', { departmentId: undefined }], queryFn: () => listMembers() })
  const departmentsQuery = useQuery({ queryKey: ['admin', 'departments', 'tree'], queryFn: listDepartmentTree })

  const groups = groupsQuery.data ?? []
  const visibleGroups = groups.filter((group) => {
    const keyword = groupSearch.trim().toLowerCase()
    if (!keyword) {
      return true
    }
    return [group.name, group.code, group.description ?? ''].some((value) => value.toLowerCase().includes(keyword))
  })
  const effectiveGroupId = groups.some((group) => group.id === selectedGroupId) ? selectedGroupId : groups[0]?.id
  const selectedGroup = groups.find((group) => group.id === effectiveGroupId)

  const membersQuery = useQuery({
    queryKey: ['admin', 'user-groups', effectiveGroupId, 'members'],
    queryFn: () => listUserGroupMembers(effectiveGroupId as string),
    enabled: Boolean(effectiveGroupId),
  })
  const expandedMembersQuery = useQuery({
    queryKey: ['admin', 'user-groups', effectiveGroupId, 'expanded-members'],
    queryFn: () => listExpandedUserGroupMembers(effectiveGroupId as string),
    enabled: Boolean(effectiveGroupId),
  })

  const departments = useMemo(() => flattenDepartmentTree(departmentsQuery.data ?? []), [departmentsQuery.data])
  const userOptions = (usersQuery.data ?? []).map((member) => ({
    label: `${member.displayName} (${member.username})`,
    value: member.id,
  }))
  const departmentOptions = departments.map((department) => ({ label: department.label, value: department.id }))

  const refreshGroups = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['admin', 'user-groups'] }),
      queryClient.invalidateQueries({ queryKey: ['docs'] }),
    ])
  }

  const groupMutation = useMutation({
    mutationFn: (values: UserGroupRequest) => {
      if (groupModal?.mode === 'edit') {
        return updateUserGroup(groupModal.group.id, values)
      }
      return createUserGroup(values)
    },
    onSuccess: async (group) => {
      message.success(groupModal?.mode === 'edit' ? '用户组已更新' : '用户组已创建')
      setSelectedGroupId(group.id)
      setGroupModal(null)
      groupForm.resetFields()
      await refreshGroups()
    },
  })

  const disableMutation = useMutation({
    mutationFn: disableUserGroup,
    onSuccess: async () => {
      message.success('用户组已停用')
      await refreshGroups()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: deleteUserGroup,
    onSuccess: async () => {
      message.success('用户组已删除')
      await refreshGroups()
    },
  })

  const addMemberMutation = useMutation({
    mutationFn: (values: MemberForm) => addUserGroupMember(effectiveGroupId as string, values),
    onSuccess: async () => {
      message.success('成员主体已加入')
      setMemberModalOpen(false)
      memberForm.resetFields()
      await refreshGroups()
    },
  })

  const removeMemberMutation = useMutation({
    mutationFn: ({ groupId, memberId }: { groupId: string; memberId: string }) => removeUserGroupMember(groupId, memberId),
    onSuccess: async () => {
      message.success('成员主体已移除')
      await refreshGroups()
    },
  })

  const memberColumns: ColumnsType<UserGroupMember> = [
    {
      title: '主体',
      dataIndex: 'subjectName',
      render: (_, record) => (
        <Space size={12}>
          <span className={`admin-entity-avatar ${record.subjectType === 'department' ? 'department' : ''}`}>
            {entityInitial(record.subjectName)}
          </span>
          <Space orientation="vertical" size={0}>
            <Typography.Text strong>{record.subjectName}</Typography.Text>
            <Typography.Text type="secondary">{record.subjectDetail}</Typography.Text>
          </Space>
        </Space>
      ),
    },
    {
      title: '类型',
      dataIndex: 'subjectType',
      width: 110,
      render: (value) => <span className={`admin-soft-badge ${value === 'department' ? 'purple' : 'gray'}`}>{value === 'department' ? '部门' : '成员'}</span>,
    },
    {
      title: '状态',
      dataIndex: 'subjectStatus',
      width: 100,
      render: (value) => <span className={`admin-status-pill ${value === 'active' ? 'active' : 'disabled'}`}>{value}</span>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (_, record) => (
        <Button
          danger
          size="small"
          className="admin-danger-outline"
          icon={<DeleteOutlined />}
          loading={removeMemberMutation.isPending}
          onClick={() => removeMemberMutation.mutate({ groupId: record.groupId, memberId: record.id })}
        >
          移除
        </Button>
      ),
    },
  ]

  const expandedColumns: ColumnsType<ExpandedUserGroupMember> = [
    {
      title: '成员',
      dataIndex: 'displayName',
      render: (_, record) => (
        <Space size={12}>
          <span className="admin-entity-avatar">{entityInitial(record.displayName)}</span>
          <Space orientation="vertical" size={0}>
            <Typography.Text strong>{record.displayName}</Typography.Text>
            <Typography.Text type="secondary">{record.username}</Typography.Text>
          </Space>
        </Space>
      ),
    },
    {
      title: '来源',
      dataIndex: 'sourceName',
      render: (_, record) => (
        <Space>
          <span className={`admin-soft-badge ${record.sourceType === 'department' ? 'purple' : 'gray'}`}>
            {record.sourceType === 'department' ? '部门' : '直加'}
          </span>
          <Typography.Text>{record.sourceName}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value) => <span className={`admin-status-pill ${value === 'active' ? 'active' : 'disabled'}`}>{value}</span>,
    },
  ]

  return (
    <Space orientation="vertical" size={18} className="page-stack admin-org-page admin-user-groups-page">
      <Space className="page-toolbar admin-saas-toolbar" wrap>
        <Space size={12}>
          <span className="admin-page-icon">
            <TeamOutlined />
          </span>
          <Typography.Title level={2}>用户组</Typography.Title>
        </Space>
        <Space wrap>
          <Button icon={<UserOutlined />} onClick={() => navigate('/admin/users')}>
            成员管理
          </Button>
          <Button icon={<ApartmentOutlined />} onClick={() => navigate('/admin/departments')}>
            组织架构
          </Button>
          <Button icon={<AuditOutlined />} onClick={() => navigate('/admin/audit-logs')}>
            审计日志
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreateGroup}>
            新建用户组
          </Button>
        </Space>
      </Space>

      <div className="admin-org-grid admin-user-groups-layout">
        <div className="admin-org-panel admin-user-groups-sidebar">
          <Input
            allowClear
            className="admin-user-group-search"
            placeholder="搜索用户组"
            prefix={<SearchOutlined />}
            value={groupSearch}
            onChange={(event) => setGroupSearch(event.target.value)}
          />
          <div className="admin-user-group-list">
            {visibleGroups.map((group) => (
              <button
                key={group.id}
                type="button"
                className={`admin-user-group-card ${group.id === effectiveGroupId ? 'active' : ''}`}
                onClick={() => setSelectedGroupId(group.id)}
              >
                <span className={`admin-group-card-icon ${group.groupType === 'permission' ? 'permission' : ''}`}>
                  <TeamOutlined />
                </span>
                <span className="admin-group-card-copy">
                  <span className="admin-group-card-title">
                    <strong>{group.name}</strong>
                    <span className={`admin-status-pill ${group.status === 'active' ? 'active' : 'disabled'}`}>{group.status}</span>
                  </span>
                  <small>{group.code}</small>
                </span>
                <span className="admin-group-card-count">{group.expandedMemberCount}</span>
              </button>
            ))}
            {!groupsQuery.isLoading && visibleGroups.length === 0 ? (
              <div className="admin-user-group-empty">
                <Typography.Text type="secondary">没有匹配的用户组</Typography.Text>
              </div>
            ) : null}
          </div>
          <Button block className="admin-sidebar-create" icon={<PlusOutlined />} onClick={openCreateGroup}>
            新建用户组
          </Button>
        </div>

        <div className="admin-user-groups-main">
          {selectedGroup ? (
            <Space orientation="vertical" size={16} className="admin-org-detail">
              <div className="admin-group-hero-card">
                <div className="admin-group-hero-main">
                  <span className="admin-group-hero-icon">
                    <TeamOutlined />
                  </span>
                  <div className="admin-group-hero-copy">
                    <Space size={10} wrap>
                      <Typography.Title level={3}>{selectedGroup.name}</Typography.Title>
                      <span className={`admin-status-pill ${selectedGroup.status === 'active' ? 'active' : 'disabled'}`}>{selectedGroup.status}</span>
                    </Space>
                    <Typography.Text type="secondary">{selectedGroup.code}</Typography.Text>
                    <div className="admin-group-meta-grid">
                      <span className="admin-meta-chip">直接成员 {selectedGroup.directMemberCount}</span>
                      <span className="admin-meta-chip">展开成员 {selectedGroup.expandedMemberCount}</span>
                      <span className="admin-meta-chip">创建时间 {formatDateTime(selectedGroup.createdAt)}</span>
                      {selectedGroup.description ? <span className="admin-meta-chip wide">描述 {selectedGroup.description}</span> : null}
                    </div>
                  </div>
                </div>
                <Space className="admin-group-hero-actions" wrap>
                  <Button icon={<EditOutlined />} className="admin-action-button" onClick={() => openEditGroup(selectedGroup)}>
                    编辑
                  </Button>
                  <Button
                    icon={<StopOutlined />}
                    className="admin-action-button"
                    disabled={selectedGroup.status === 'disabled'}
                    loading={disableMutation.isPending}
                    onClick={() => disableMutation.mutate(selectedGroup.id)}
                  >
                    停用
                  </Button>
                  <Button danger icon={<DeleteOutlined />} className="admin-danger-outline" loading={deleteMutation.isPending} onClick={() => confirmDelete(selectedGroup)}>
                    删除
                  </Button>
                </Space>
              </div>

              <div className="admin-data-card">
                <Space className="admin-org-section-toolbar" wrap>
                  <Typography.Title level={4}>直接成员主体 <span>({selectedGroup.directMemberCount})</span></Typography.Title>
                  <Button
                    icon={<PlusOutlined />}
                    disabled={selectedGroup.status === 'disabled'}
                    className="admin-action-button"
                    onClick={() => {
                      setMemberModalOpen(true)
                      memberForm.setFieldsValue({ subjectType: 'user' })
                    }}
                  >
                    加入主体
                  </Button>
                </Space>
                <Table
                  rowKey="id"
                  size="middle"
                  loading={membersQuery.isLoading}
                  columns={memberColumns}
                  dataSource={membersQuery.data ?? []}
                  pagination={{ pageSize: 8, placement: ['bottomEnd'] }}
                />
              </div>

              <div className="admin-data-card">
                <Typography.Title level={4}>展开成员 <span>({selectedGroup.expandedMemberCount})</span></Typography.Title>
                <Table
                  rowKey={(record) => record.userId}
                  size="middle"
                  loading={expandedMembersQuery.isLoading}
                  columns={expandedColumns}
                  dataSource={expandedMembersQuery.data ?? []}
                  pagination={{ pageSize: 8, placement: ['bottomEnd'] }}
                />
              </div>
            </Space>
          ) : (
            <div className="admin-org-panel admin-org-empty">
              <Typography.Text type="secondary">创建或选择一个用户组</Typography.Text>
            </div>
          )}
        </div>
      </div>

      <Modal
        title={groupModal?.mode === 'edit' ? '编辑用户组' : '新建用户组'}
        open={Boolean(groupModal)}
        okText="保存"
        cancelText="取消"
        confirmLoading={groupMutation.isPending}
        onCancel={() => setGroupModal(null)}
        onOk={() => groupForm.submit()}
      >
        <Form form={groupForm} layout="vertical" onFinish={(values) => groupMutation.mutate(values)}>
          <Form.Item label="编码" name="code" rules={[{ required: true, message: '请输入用户组编码' }]}>
            <Input autoComplete="off" />
          </Form.Item>
          <Form.Item label="名称" name="name" rules={[{ required: true, message: '请输入用户组名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="类型" name="groupType" initialValue="normal">
            <Select
              options={[
                { label: '普通组', value: 'normal' },
                { label: '权限组', value: 'permission' },
              ]}
            />
          </Form.Item>
          <Form.Item label="描述" name="description">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="加入成员主体"
        open={memberModalOpen}
        okText="保存"
        cancelText="取消"
        confirmLoading={addMemberMutation.isPending}
        onCancel={() => setMemberModalOpen(false)}
        onOk={() => memberForm.submit()}
      >
        <Form form={memberForm} layout="vertical" onFinish={(values) => addMemberMutation.mutate(values)}>
          <Form.Item name="subjectType" label="主体类型" initialValue="user" rules={[{ required: true }]}>
            <Radio.Group
              optionType="button"
              buttonStyle="solid"
              options={[
                { label: '成员', value: 'user' },
                { label: '部门', value: 'department' },
              ]}
            />
          </Form.Item>
          <Form.Item shouldUpdate noStyle>
            {({ getFieldValue }) => {
              const subjectType = getFieldValue('subjectType') as MemberForm['subjectType']
              return (
                <Form.Item name="subjectId" label={subjectType === 'department' ? '部门' : '成员'} rules={[{ required: true, message: '请选择主体' }]}>
                  <Select
                    showSearch
                    optionFilterProp="label"
                    loading={subjectType === 'department' ? departmentsQuery.isLoading : usersQuery.isLoading}
                    options={subjectType === 'department' ? departmentOptions : userOptions}
                  />
                </Form.Item>
              )
            }}
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )

  function openCreateGroup() {
    setGroupModal({ mode: 'create' })
    groupForm.setFieldsValue({ code: '', name: '', description: '', groupType: 'normal' })
  }

  function openEditGroup(group: UserGroupSummary) {
    setGroupModal({ mode: 'edit', group })
    groupForm.setFieldsValue({
      code: group.code,
      name: group.name,
      description: group.description ?? '',
      groupType: group.groupType,
    })
  }

  function confirmDelete(group: UserGroupSummary) {
    modal.confirm({
      title: `删除用户组：${group.name}`,
      content: '仅没有直接成员主体的用户组可以删除。删除后不再出现在授权主体选择器中。',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => deleteMutation.mutateAsync(group.id),
    })
  }
}

function entityInitial(value?: string | null) {
  return (value?.trim()?.[0] ?? '?').toUpperCase()
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
