import {
  AppstoreOutlined,
  DatabaseOutlined,
  DeleteOutlined,
  EditOutlined,
  FileTextOutlined,
  FolderOpenOutlined,
  PlusOutlined,
  SafetyCertificateOutlined,
  SaveOutlined,
  SearchOutlined,
  SettingOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntdApp,
  Button,
  Checkbox,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMemo, useState } from 'react'

import { AdminModuleNav } from '../components/AdminModuleNav'
import { listMembers } from '../api/adminUsersApi'
import { flattenDepartmentTree, listDepartmentTree } from '../api/departmentsApi'
import {
  createRole,
  createRoleAssignment,
  listPermissions,
  listRoleAssignments,
  listRoles,
  replaceRolePermissions,
  revokeRoleAssignment,
  updateRole,
} from '../api/rolesApi'
import type {
  PermissionCatalogItem,
  PermissionRiskLevel,
  RoleAssignmentRequest,
  RoleAssignmentSubjectType,
  RoleAssignmentSummary,
  RoleRequest,
  RoleSummary,
  UpdateRoleRequest,
} from '../api/rolesApi'
import { listUserGroups } from '../api/userGroupsApi'

type RoleFormValues = RoleRequest & Pick<UpdateRoleRequest, 'status'>

export function AdminRolesPage() {
  const queryClient = useQueryClient()
  const { message, modal } = AntdApp.useApp()
  const [selectedRoleId, setSelectedRoleId] = useState<string>()
  const [permissionDraft, setPermissionDraft] = useState<{ roleId?: string; codes: string[] }>({ codes: [] })
  const [confirmHighRisk, setConfirmHighRisk] = useState(false)
  const [roleSearch, setRoleSearch] = useState('')
  const [activePermissionModule, setActivePermissionModule] = useState('all')
  const [roleModalOpen, setRoleModalOpen] = useState(false)
  const [editingRole, setEditingRole] = useState<RoleSummary | null>(null)
  const [assignmentOpen, setAssignmentOpen] = useState(false)
  const [confirmAssignmentHighRisk, setConfirmAssignmentHighRisk] = useState(false)
  const [roleForm] = Form.useForm<RoleFormValues>()
  const [assignmentForm] = Form.useForm<RoleAssignmentRequest>()
  const subjectType = Form.useWatch('subjectType', assignmentForm) ?? 'user'

  const rolesQuery = useQuery({ queryKey: ['admin', 'roles'], queryFn: listRoles })
  const permissionsQuery = useQuery({ queryKey: ['admin', 'permissions'], queryFn: listPermissions })
  const membersQuery = useQuery({ queryKey: ['admin', 'users'], queryFn: () => listMembers() })
  const departmentsQuery = useQuery({ queryKey: ['admin', 'departments', 'tree'], queryFn: listDepartmentTree })
  const groupsQuery = useQuery({
    queryKey: ['admin', 'user-groups', { activeOnly: true }],
    queryFn: () => listUserGroups({ activeOnly: true }),
  })

  const roles = useMemo(() => rolesQuery.data ?? [], [rolesQuery.data])
  const effectiveSelectedRoleId = selectedRoleId ?? roles[0]?.id
  const selectedRole = roles.find((role) => role.id === effectiveSelectedRoleId)
  const permissions = useMemo(() => permissionsQuery.data ?? [], [permissionsQuery.data])
  const permissionByCode = useMemo(
    () => new Map(permissions.map((permission) => [permission.code, permission])),
    [permissions],
  )
  const departments = useMemo(() => flattenDepartmentTree(departmentsQuery.data ?? []), [departmentsQuery.data])
  const permissionCodes =
    permissionDraft.roleId === selectedRole?.id ? permissionDraft.codes : selectedRole?.permissionCodes ?? []
  const assignmentsQuery = useQuery({
    queryKey: ['admin', 'role-assignments', effectiveSelectedRoleId],
    queryFn: () => listRoleAssignments(effectiveSelectedRoleId),
    enabled: Boolean(effectiveSelectedRoleId),
  })

  const groupedPermissions = useMemo(() => {
    return permissions.reduce<Record<string, PermissionCatalogItem[]>>((groups, permission) => {
      groups[permission.module] = [...(groups[permission.module] ?? []), permission]
      return groups
    }, {})
  }, [permissions])
  const permissionModules = useMemo(() => Object.keys(groupedPermissions), [groupedPermissions])
  const visiblePermissionGroups = useMemo(() => {
    if (activePermissionModule === 'all') {
      return Object.entries(groupedPermissions)
    }
    return Object.entries(groupedPermissions).filter(([module]) => module === activePermissionModule)
  }, [activePermissionModule, groupedPermissions])
  const filteredRoles = useMemo(() => {
    const keyword = roleSearch.trim().toLowerCase()
    if (!keyword) {
      return roles
    }
    return roles.filter((role) => role.name.toLowerCase().includes(keyword) || role.code.toLowerCase().includes(keyword))
  }, [roleSearch, roles])

  const selectedContainsHighRisk = permissionCodes.some((code) => {
    const riskLevel = permissionByCode.get(code)?.riskLevel
    return riskLevel === 'high' || riskLevel === 'critical'
  })

  const refreshRoles = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['admin', 'roles'] }),
      queryClient.invalidateQueries({ queryKey: ['admin', 'role-assignments'] }),
    ])
  }

  const saveRoleMutation = useMutation({
    mutationFn: (values: RoleFormValues) =>
      editingRole
        ? updateRole(editingRole.id, {
            name: values.name,
            scope: values.scope,
            description: values.description,
            status: values.status,
          })
        : createRole({
            code: values.code,
            name: values.name,
            scope: values.scope,
            description: values.description,
          }),
    onSuccess: async (role) => {
      message.success(editingRole ? '角色已更新' : '角色已创建')
      setRoleModalOpen(false)
      setEditingRole(null)
      roleForm.resetFields()
      setSelectedRoleId(role.id)
      await refreshRoles()
    },
    onError: (error) => {
      message.error(error instanceof Error ? error.message : '角色保存失败')
    },
  })

  const savePermissionsMutation = useMutation({
    mutationFn: () =>
      replaceRolePermissions(effectiveSelectedRoleId!, {
        permissionCodes,
        confirmHighRisk,
      }),
    onSuccess: async () => {
      message.success('角色权限已保存')
      setConfirmHighRisk(false)
      await refreshRoles()
    },
    onError: (error) => {
      message.error(error instanceof Error ? error.message : '角色权限保存失败')
    },
  })

  const createAssignmentMutation = useMutation({
    mutationFn: createRoleAssignment,
    onSuccess: async () => {
      message.success('角色分配已生效')
      setAssignmentOpen(false)
      assignmentForm.resetFields()
      await refreshRoles()
    },
    onError: (error) => {
      message.error(error instanceof Error ? error.message : '角色分配失败')
    },
  })

  const revokeMutation = useMutation({
    mutationFn: revokeRoleAssignment,
    onSuccess: refreshRoles,
  })

  const assignmentColumns: ColumnsType<RoleAssignmentSummary> = [
    {
      title: '对象',
      dataIndex: 'subjectName',
      render: (_, record) => (
        <Space size={12} className="admin-table-entity">
          <span className="admin-entity-avatar">{entityInitial(record.subjectName ?? record.subjectId)}</span>
          <Space orientation="vertical" size={0}>
            <Typography.Text strong>{record.subjectName ?? record.subjectId}</Typography.Text>
            <Typography.Text type="secondary">
              {subjectTypeLabel(record.subjectType)} · {record.subjectDetail ?? record.subjectId}
            </Typography.Text>
          </Space>
        </Space>
      ),
    },
    {
      title: '范围',
      dataIndex: 'scopeType',
      render: (scopeType) => <span className="admin-soft-badge gray">{scopeType}</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      render: (status) => <span className={`admin-status-pill ${status === 'active' ? 'active' : 'disabled'}`}>{status}</span>,
    },
    {
      title: '操作',
      key: 'actions',
      render: (_, record) =>
        record.status === 'active' ? (
          <Button className="admin-danger-outline" icon={<DeleteOutlined />} size="small" loading={revokeMutation.isPending} onClick={() => confirmRevoke(record)}>
            撤销
          </Button>
        ) : null,
    },
  ]

  const subjectOptions = useMemo(() => {
    if (subjectType === 'department') {
      return departments.map((department) => ({ label: department.label, value: department.id }))
    }
    if (subjectType === 'user_group') {
      return (groupsQuery.data ?? []).map((group) => ({ label: `${group.name} (${group.code})`, value: group.id }))
    }
    return (membersQuery.data ?? []).map((member) => ({
      label: `${member.displayName} (${member.username})`,
      value: member.id,
    }))
  }, [departments, groupsQuery.data, membersQuery.data, subjectType])

  function openCreateRole() {
    setEditingRole(null)
    roleForm.setFieldsValue({ scope: 'workspace', status: 'active' })
    setRoleModalOpen(true)
  }

  function openEditRole(role: RoleSummary) {
    setEditingRole(role)
    roleForm.setFieldsValue({
      code: role.code,
      name: role.name,
      scope: role.scope,
      description: role.description ?? undefined,
      status: role.status,
    })
    setRoleModalOpen(true)
  }

  function openCreateAssignment() {
    if (!effectiveSelectedRoleId) {
      return
    }
    assignmentForm.setFieldsValue({ roleId: effectiveSelectedRoleId, subjectType: 'user', scopeType: 'system' })
    setConfirmAssignmentHighRisk(false)
    setAssignmentOpen(true)
  }

  function savePermissions() {
    if (!effectiveSelectedRoleId) {
      return
    }
    if (selectedContainsHighRisk && !confirmHighRisk) {
      message.warning('包含高风险权限，请先勾选确认后再保存')
      return
    }
    savePermissionsMutation.mutate()
  }

  function submitAssignment(values: RoleAssignmentRequest) {
    if (selectedContainsHighRisk && !confirmAssignmentHighRisk) {
      message.warning('该角色包含高风险权限，请先勾选确认后再分配')
      return
    }
    createAssignmentMutation.mutate({
      ...values,
      confirmHighRisk: confirmAssignmentHighRisk,
    })
  }

  function confirmRevoke(assignment: RoleAssignmentSummary) {
    modal.confirm({
      title: '撤销角色分配',
      content: `确认撤销 ${assignment.subjectName ?? assignment.subjectId} 的 ${assignment.roleName}？`,
      okText: '撤销',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => revokeMutation.mutateAsync(assignment.id),
    })
  }

  return (
    <Space orientation="vertical" size={16} className="page-stack admin-org-page admin-roles-page">
      <Space className="page-toolbar admin-saas-toolbar" wrap>
        <Space size={12}>
          <span className="admin-page-icon">
            <SafetyCertificateOutlined />
          </span>
          <Typography.Title level={2}>角色与权限</Typography.Title>
        </Space>
        <AdminModuleNav />
      </Space>

      <div className="admin-roles-layout">
        <aside className="admin-org-panel admin-role-sidebar">
          <Button block type="primary" icon={<PlusOutlined />} className="admin-sidebar-create admin-sidebar-create-top" onClick={openCreateRole}>
            新增角色
          </Button>
          <Input
            allowClear
            className="admin-role-search"
            prefix={<SearchOutlined />}
            placeholder="搜索角色名称或代码"
            value={roleSearch}
            onChange={(event) => setRoleSearch(event.target.value)}
          />
          <div className="admin-role-card-list">
            {rolesQuery.isLoading ? (
              <div className="admin-user-group-empty">
                <Typography.Text type="secondary">Loading...</Typography.Text>
              </div>
            ) : filteredRoles.length ? (
              filteredRoles.map((role) => (
                <div
                  key={role.id}
                  role="button"
                  tabIndex={0}
                  className={`admin-role-card ${role.id === effectiveSelectedRoleId ? 'active' : ''}`}
                  onClick={() => {
                    setSelectedRoleId(role.id)
                    setConfirmHighRisk(false)
                  }}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault()
                      setSelectedRoleId(role.id)
                      setConfirmHighRisk(false)
                    }
                  }}
                >
                  <span className="admin-role-card-icon">
                    <TeamOutlined />
                  </span>
                  <span className="admin-role-card-copy">
                    <span className="admin-role-card-title">
                      <strong>{role.name}</strong>
                      <small>{role.permissionCodes.length} 权限数</small>
                    </span>
                    <small>{role.code}</small>
                    <span className="admin-role-card-badges">
                      <span className={`admin-status-pill ${role.status === 'active' ? 'active' : 'disabled'}`}>{role.status}</span>
                      {role.builtin ? <span className="admin-soft-badge blue">built-in</span> : null}
                    </span>
                  </span>
                  <Button
                    className="admin-action-button admin-role-edit-button"
                    icon={<EditOutlined />}
                    size="small"
                    onClick={(event) => {
                      event.stopPropagation()
                      openEditRole(role)
                    }}
                  >
                    编辑
                  </Button>
                </div>
              ))
            ) : (
              <div className="admin-user-group-empty">
                <Typography.Text type="secondary">No data</Typography.Text>
              </div>
            )}
          </div>
        </aside>

        <main className="admin-roles-main">
          {selectedRole ? (
            <Space orientation="vertical" size={16} className="page-stack">
              <div className="admin-group-hero-card admin-role-detail-card">
                <div className="admin-group-hero-main">
                  <span className="admin-group-hero-icon">
                    <TeamOutlined />
                  </span>
                  <div className="admin-group-hero-copy">
                    <Space className="admin-group-hero-title-row" size={10} align="center" wrap>
                      <Typography.Title level={3}>{selectedRole.name}</Typography.Title>
                      {selectedRole.builtin ? <span className="admin-soft-badge blue">built-in</span> : null}
                      <Typography.Text type="secondary">{selectedRole.code}</Typography.Text>
                    </Space>
                    <Typography.Paragraph className="admin-role-description" type="secondary">
                      描述： {selectedRole.description || selectedRole.code}
                    </Typography.Paragraph>
                  </div>
                </div>
                <Button
                  type="primary"
                  icon={<SaveOutlined />}
                  loading={savePermissionsMutation.isPending}
                  onClick={savePermissions}
                >
                  保存权限
                </Button>
              </div>

              <div className="admin-detail-card-grid admin-role-card-grid">
                <div className="admin-data-card admin-permission-card">
                  {selectedContainsHighRisk ? (
                    <Alert
                      className="admin-role-risk-alert"
                      showIcon
                      type="warning"
                      message="包含高风险权限"
                      description={
                        <Checkbox checked={confirmHighRisk} onChange={(event) => setConfirmHighRisk(event.target.checked)}>
                          已确认该角色需要高风险权限，保存后将写入审计日志
                        </Checkbox>
                      }
                    />
                  ) : null}

                  <div className="admin-permission-layout">
                    <nav className="admin-permission-category-nav" aria-label="权限分类">
                      <button
                        type="button"
                        className={activePermissionModule === 'all' ? 'active' : ''}
                        onClick={() => setActivePermissionModule('all')}
                      >
                        <AppstoreOutlined />
                        <span>全部权限</span>
                      </button>
                      {permissionModules.map((module) => (
                        <button
                          type="button"
                          key={module}
                          className={activePermissionModule === module ? 'active' : ''}
                          onClick={() => setActivePermissionModule(module)}
                        >
                          {permissionModuleIcon(module)}
                          <span>{module}</span>
                        </button>
                      ))}
                    </nav>

                    <Checkbox.Group
                      className="admin-permission-groups"
                      value={permissionCodes}
                      onChange={(values) => setPermissionDraft({ roleId: selectedRole.id, codes: values.map(String) })}
                    >
                      {visiblePermissionGroups.map(([module, modulePermissions]) => (
                        <section key={module} className="admin-permission-group">
                          <div className="admin-permission-group-title">
                            {permissionModuleIcon(module)}
                            <Typography.Title level={4}>{module}</Typography.Title>
                          </div>
                          <div className="admin-permission-item-grid">
                            {modulePermissions.map((permission) => (
                              <Checkbox key={permission.code} className="admin-permission-item-card" value={permission.code}>
                                <span className="admin-permission-item-copy">
                                  <strong>{permission.name}</strong>
                                  <small>{permission.code}</small>
                                </span>
                                <span className={`admin-risk-badge ${riskClassName(permission.riskLevel)}`}>
                                  {permission.riskLevel}
                                </span>
                              </Checkbox>
                            ))}
                          </div>
                        </section>
                      ))}
                    </Checkbox.Group>
                  </div>
                </div>

                <div className="admin-data-card admin-role-assignment-card">
                  <Space className="admin-org-section-toolbar" wrap>
                    <Space size={10}>
                      <SafetyCertificateOutlined className="admin-section-icon" />
                      <Typography.Title level={4}>角色分配</Typography.Title>
                    </Space>
                    <Button className="admin-action-button" icon={<SafetyCertificateOutlined />} onClick={openCreateAssignment}>
                      分配角色
                    </Button>
                  </Space>
                  <Table
                    rowKey="id"
                    loading={assignmentsQuery.isLoading}
                    columns={assignmentColumns}
                    dataSource={assignmentsQuery.data ?? []}
                    locale={{ emptyText: <AdminTableEmpty /> }}
                    pagination={{ pageSize: 6, placement: ['bottomEnd'] }}
                  />
                </div>
              </div>
            </Space>
          ) : null}
        </main>
      </div>

      <Modal
        title={editingRole ? '编辑角色' : '新增角色'}
        open={roleModalOpen}
        okText={editingRole ? '保存' : '创建'}
        cancelText="取消"
        confirmLoading={saveRoleMutation.isPending}
        onCancel={() => setRoleModalOpen(false)}
        onOk={() => roleForm.submit()}
      >
        <Form form={roleForm} layout="vertical" onFinish={(values) => saveRoleMutation.mutate(values)}>
          <Form.Item label="角色编码" name="code" rules={[{ required: true, message: '请输入角色编码' }]}>
            <Input disabled={Boolean(editingRole)} placeholder="custom_role" />
          </Form.Item>
          <Form.Item label="角色名称" name="name" rules={[{ required: true, message: '请输入角色名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="范围" name="scope">
            <Select options={[{ label: 'workspace', value: 'workspace' }]} />
          </Form.Item>
          <Form.Item label="描述" name="description">
            <Input.TextArea rows={3} />
          </Form.Item>
          {editingRole ? (
            <Form.Item label="状态" name="status">
              <Select
                options={[
                  { label: 'active', value: 'active' },
                  { label: 'disabled', value: 'disabled' },
                ]}
              />
            </Form.Item>
          ) : null}
        </Form>
      </Modal>

      <Modal
        title="分配角色"
        open={assignmentOpen}
        okText="分配"
        cancelText="取消"
        confirmLoading={createAssignmentMutation.isPending}
        onCancel={() => setAssignmentOpen(false)}
        onOk={() => assignmentForm.submit()}
      >
        <Form
          form={assignmentForm}
          layout="vertical"
          onFinish={submitAssignment}
        >
          <Form.Item name="roleId" hidden>
            <Input />
          </Form.Item>
          <Form.Item label="对象类型" name="subjectType" rules={[{ required: true, message: '请选择对象类型' }]}>
            <Select
              options={[
                { label: '成员', value: 'user' },
                { label: '部门', value: 'department' },
                { label: '用户组', value: 'user_group' },
              ]}
              onChange={() => assignmentForm.setFieldValue('subjectId', undefined)}
            />
          </Form.Item>
          <Form.Item label="对象" name="subjectId" rules={[{ required: true, message: '请选择对象' }]}>
            <Select
              showSearch
              loading={membersQuery.isLoading || departmentsQuery.isLoading || groupsQuery.isLoading}
              options={subjectOptions}
              optionFilterProp="label"
            />
          </Form.Item>
          <Form.Item label="范围" name="scopeType">
            <Select options={[{ label: 'system', value: 'system' }]} />
          </Form.Item>
          {selectedContainsHighRisk ? (
            <Alert
              type="warning"
              showIcon
              message="该角色包含高风险权限"
              description={
                <Checkbox
                  checked={confirmAssignmentHighRisk}
                  onChange={(event) => setConfirmAssignmentHighRisk(event.target.checked)}
                >
                  已确认将该高风险角色分配给所选对象，提交后写入审计日志
                </Checkbox>
              }
            />
          ) : null}
        </Form>
      </Modal>
    </Space>
  )
}

function riskClassName(riskLevel: PermissionRiskLevel) {
  if (riskLevel === 'critical' || riskLevel === 'high') return 'high'
  if (riskLevel === 'medium') return 'medium'
  return 'low'
}

function permissionModuleIcon(module: string) {
  if (module === 'admin') return <SettingOutlined />
  if (module === 'identity') return <UserOutlined />
  if (module === 'project') return <FolderOpenOutlined />
  if (module === 'doc') return <FileTextOutlined />
  if (module === 'base') return <DatabaseOutlined />
  return <SafetyCertificateOutlined />
}

function subjectTypeLabel(subjectType: RoleAssignmentSubjectType) {
  if (subjectType === 'department') return '部门'
  if (subjectType === 'user_group') return '用户组'
  return '成员'
}

function entityInitial(value: string) {
  return (value || '?').trim().slice(0, 1).toUpperCase()
}

function AdminTableEmpty() {
  return (
    <div className="admin-table-empty">
      <span className="admin-empty-icon">
        <SafetyCertificateOutlined />
      </span>
      <Typography.Text type="secondary">No data</Typography.Text>
    </div>
  )
}
