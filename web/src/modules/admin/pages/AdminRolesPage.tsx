import { EditOutlined, PlusOutlined, SafetyCertificateOutlined, SaveOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntdApp,
  Button,
  Checkbox,
  Divider,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'

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
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { message, modal } = AntdApp.useApp()
  const [selectedRoleId, setSelectedRoleId] = useState<string>()
  const [permissionDraft, setPermissionDraft] = useState<{ roleId?: string; codes: string[] }>({ codes: [] })
  const [confirmHighRisk, setConfirmHighRisk] = useState(false)
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

  const roleColumns: ColumnsType<RoleSummary> = [
    {
      title: '角色',
      dataIndex: 'name',
      render: (_, record) => (
        <Space orientation="vertical" size={0}>
          <Typography.Text strong>{record.name}</Typography.Text>
          <Typography.Text type="secondary">{record.code}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '权限数',
      dataIndex: 'permissionCodes',
      render: (codes: string[]) => codes.length,
    },
    {
      title: '状态',
      dataIndex: 'status',
      render: (status, record) => (
        <Space size={4}>
          <Tag color={status === 'active' ? 'green' : 'default'}>{status}</Tag>
          {record.builtin ? <Tag color="blue">built-in</Tag> : null}
        </Space>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      render: (_, record) => (
        <Button icon={<EditOutlined />} size="small" onClick={() => openEditRole(record)}>
          编辑
        </Button>
      ),
    },
  ]

  const assignmentColumns: ColumnsType<RoleAssignmentSummary> = [
    {
      title: '对象',
      dataIndex: 'subjectName',
      render: (_, record) => (
        <Space orientation="vertical" size={0}>
          <Typography.Text>{record.subjectName ?? record.subjectId}</Typography.Text>
          <Typography.Text type="secondary">
            {subjectTypeLabel(record.subjectType)} · {record.subjectDetail ?? record.subjectId}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '范围',
      dataIndex: 'scopeType',
      render: (scopeType) => <Tag>{scopeType}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      render: (status) => <Tag color={status === 'active' ? 'green' : 'default'}>{status}</Tag>,
    },
    {
      title: '操作',
      key: 'actions',
      render: (_, record) =>
        record.status === 'active' ? (
          <Button danger size="small" loading={revokeMutation.isPending} onClick={() => confirmRevoke(record)}>
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
    <Space orientation="vertical" size={16} className="page-stack">
      <Space className="page-toolbar">
        <Typography.Title level={2}>角色与权限</Typography.Title>
        <Space>
          <Button onClick={() => navigate('/admin/users')}>成员</Button>
          <Button onClick={() => navigate('/admin/departments')}>组织架构</Button>
          <Button onClick={() => navigate('/admin/user-groups')}>用户组</Button>
          <Button onClick={() => navigate('/admin/audit-logs')}>审计日志</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreateRole}>
            新增角色
          </Button>
        </Space>
      </Space>

      <Table
        rowKey="id"
        loading={rolesQuery.isLoading}
        columns={roleColumns}
        dataSource={roles}
        pagination={false}
        rowSelection={{
          type: 'radio',
          selectedRowKeys: effectiveSelectedRoleId ? [effectiveSelectedRoleId] : [],
          onChange: ([roleId]) => {
            setSelectedRoleId(String(roleId))
            setConfirmHighRisk(false)
          },
        }}
        onRow={(record) => ({
          onClick: () => {
            setSelectedRoleId(record.id)
            setConfirmHighRisk(false)
          },
        })}
      />

      {selectedRole ? (
        <Space orientation="vertical" size={16} className="page-stack">
          <Space className="page-toolbar">
            <Space orientation="vertical" size={0}>
              <Typography.Title level={3}>{selectedRole.name}</Typography.Title>
              <Typography.Text type="secondary">{selectedRole.description || selectedRole.code}</Typography.Text>
            </Space>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={savePermissionsMutation.isPending}
              onClick={savePermissions}
            >
              保存权限
            </Button>
          </Space>

          {selectedContainsHighRisk ? (
            <Alert
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

          <Space orientation="vertical" size={12} className="page-stack">
            {Object.entries(groupedPermissions).map(([module, modulePermissions]) => (
              <Space key={module} orientation="vertical" size={8} className="page-stack">
                <Typography.Title level={4}>{module}</Typography.Title>
                <Checkbox.Group
                  className="admin-role-permission-group"
                  value={permissionCodes}
                  onChange={(values) => setPermissionDraft({ roleId: selectedRole.id, codes: values.map(String) })}
                >
                  <Space wrap>
                    {modulePermissions.map((permission) => (
                      <Checkbox key={permission.code} value={permission.code}>
                        <Space size={4}>
                          <span>{permission.name}</span>
                          <Tag color={riskColor(permission.riskLevel)}>{permission.riskLevel}</Tag>
                          <Typography.Text type="secondary">{permission.code}</Typography.Text>
                        </Space>
                      </Checkbox>
                    ))}
                  </Space>
                </Checkbox.Group>
              </Space>
            ))}
          </Space>

          <Divider />

          <Space className="page-toolbar">
            <Typography.Title level={3}>角色分配</Typography.Title>
            <Button icon={<SafetyCertificateOutlined />} onClick={openCreateAssignment}>
              分配角色
            </Button>
          </Space>
          <Table
            rowKey="id"
            loading={assignmentsQuery.isLoading}
            columns={assignmentColumns}
            dataSource={assignmentsQuery.data ?? []}
            pagination={{ pageSize: 6 }}
          />
        </Space>
      ) : null}

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

function riskColor(riskLevel: PermissionRiskLevel) {
  if (riskLevel === 'critical') return 'red'
  if (riskLevel === 'high') return 'orange'
  if (riskLevel === 'medium') return 'gold'
  return 'green'
}

function subjectTypeLabel(subjectType: RoleAssignmentSubjectType) {
  if (subjectType === 'department') return '部门'
  if (subjectType === 'user_group') return '用户组'
  return '成员'
}
