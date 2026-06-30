import {
  ApartmentOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  StopOutlined,
  SwapOutlined,
  UserAddOutlined,
  UserSwitchOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App as AntdApp, Button, Form, Input, InputNumber, Modal, Select, Space, Table, Tree, Typography } from 'antd'
import type { DataNode } from 'antd/es/tree'
import type { ColumnsType } from 'antd/es/table'
import { useMemo, useState } from 'react'

import { listMembers } from '../api/adminUsersApi'
import { AdminModuleNav } from '../components/AdminModuleNav'
import {
  addDepartmentManager,
  addDepartmentMember,
  createDepartment,
  deleteDepartment,
  disableDepartment,
  enableDepartment,
  flattenDepartmentTree,
  listDepartmentMembers,
  listDepartmentTree,
  moveDepartment,
  removeDepartmentManager,
  removeDepartmentMember,
  updateDepartment,
  type DepartmentManager,
  type DepartmentMember,
  type DepartmentRequest,
  type DepartmentSummary,
  type DepartmentTreeNode,
  type MoveDepartmentRequest,
} from '../api/departmentsApi'

type DepartmentModalState =
  | { mode: 'create'; parentId?: string | null }
  | { mode: 'edit'; department: DepartmentSummary }

type AssignmentModalState = 'member' | 'manager' | null

export function AdminDepartmentsPage() {
  const queryClient = useQueryClient()
  const { message, modal } = AntdApp.useApp()
  const [selectedDepartmentId, setSelectedDepartmentId] = useState<string>()
  const [departmentModal, setDepartmentModal] = useState<DepartmentModalState | null>(null)
  const [moveTarget, setMoveTarget] = useState<DepartmentSummary | null>(null)
  const [assignmentModal, setAssignmentModal] = useState<AssignmentModalState>(null)
  const [departmentForm] = Form.useForm<DepartmentRequest>()
  const [moveForm] = Form.useForm<MoveDepartmentRequest>()
  const [assignmentForm] = Form.useForm<{ userId: string; relationType?: 'primary' | 'member'; managerType?: 'primary' | 'deputy' }>()

  const treeQuery = useQuery({
    queryKey: ['admin', 'departments', 'tree'],
    queryFn: listDepartmentTree,
  })

  const allMembersQuery = useQuery({
    queryKey: ['admin', 'users', { departmentId: undefined }],
    queryFn: () => listMembers(),
  })

  const departments = useMemo(() => flattenDepartmentTree(treeQuery.data ?? []), [treeQuery.data])
  const effectiveSelectedDepartmentId = departments.some((department) => department.id === selectedDepartmentId)
    ? selectedDepartmentId
    : departments[0]?.id
  const selectedDepartment = departments.find((department) => department.id === effectiveSelectedDepartmentId)
  const selectedNode = useMemo(
    () => (effectiveSelectedDepartmentId ? findDepartmentNode(treeQuery.data ?? [], effectiveSelectedDepartmentId) : undefined),
    [effectiveSelectedDepartmentId, treeQuery.data],
  )

  const membersQuery = useQuery({
    queryKey: ['admin', 'departments', effectiveSelectedDepartmentId, 'members'],
    queryFn: () => listDepartmentMembers(effectiveSelectedDepartmentId as string),
    enabled: Boolean(effectiveSelectedDepartmentId),
  })

  const departmentOptions = departments.map((department) => ({
    label: department.label,
    value: department.id,
    disabled: moveTarget ? department.id === moveTarget.id || department.path.startsWith(`${moveTarget.path}/`) : false,
  }))

  const userOptions = (allMembersQuery.data ?? []).map((member) => ({
    label: `${member.displayName} (${member.username})`,
    value: member.id,
  }))

  const refreshOrganization = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['admin', 'departments'] }),
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] }),
    ])
  }

  const departmentMutation = useMutation({
    mutationFn: (values: DepartmentRequest) => {
      if (departmentModal?.mode === 'edit') {
        return updateDepartment(departmentModal.department.id, values)
      }
      return createDepartment(values)
    },
    onSuccess: async (department) => {
      message.success(departmentModal?.mode === 'edit' ? '部门已更新' : '部门已创建')
      setSelectedDepartmentId(department.id)
      setDepartmentModal(null)
      departmentForm.resetFields()
      await refreshOrganization()
    },
  })

  const moveMutation = useMutation({
    mutationFn: (values: MoveDepartmentRequest) => moveDepartment(moveTarget?.id as string, values),
    onSuccess: async (department) => {
      message.success('部门已移动')
      setSelectedDepartmentId(department.id)
      setMoveTarget(null)
      moveForm.resetFields()
      await refreshOrganization()
    },
  })

  const disableMutation = useMutation({
    mutationFn: disableDepartment,
    onSuccess: async () => {
      message.success('部门已停用')
      await refreshOrganization()
    },
  })

  const enableMutation = useMutation({
    mutationFn: enableDepartment,
    onSuccess: async () => {
      message.success('部门已启用')
      await refreshOrganization()
    },
    onError: (error) => {
      message.error(error instanceof Error ? error.message : '部门启用失败')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: deleteDepartment,
    onSuccess: async () => {
      message.success('部门已删除')
      await refreshOrganization()
    },
  })

  const assignmentMutation = useMutation({
    mutationFn: (values: { userId: string; relationType?: 'primary' | 'member'; managerType?: 'primary' | 'deputy' }) => {
      if (!effectiveSelectedDepartmentId) {
        return Promise.resolve()
      }
      if (assignmentModal === 'manager') {
        return addDepartmentManager(effectiveSelectedDepartmentId, {
          userId: values.userId,
          managerType: values.managerType ?? 'primary',
        })
      }
      return addDepartmentMember(effectiveSelectedDepartmentId, {
        userId: values.userId,
        relationType: values.relationType ?? 'member',
      })
    },
    onSuccess: async () => {
      message.success(assignmentModal === 'manager' ? '负责人已添加' : '成员已加入')
      setAssignmentModal(null)
      assignmentForm.resetFields()
      await refreshOrganization()
    },
  })

  const removeMemberMutation = useMutation({
    mutationFn: ({ departmentId, userId }: { departmentId: string; userId: string }) =>
      removeDepartmentMember(departmentId, userId),
    onSuccess: async () => {
      message.success('成员已移除')
      await refreshOrganization()
    },
  })

  const removeManagerMutation = useMutation({
    mutationFn: ({ departmentId, userId, managerType }: { departmentId: string; userId: string; managerType: 'primary' | 'deputy' }) =>
      removeDepartmentManager(departmentId, userId, managerType),
    onSuccess: async () => {
      message.success('负责人已移除')
      await refreshOrganization()
    },
  })

  const treeData = useMemo(() => buildTreeData(treeQuery.data ?? []), [treeQuery.data])
  const tableEmpty = <AdminTableEmpty />

  const memberColumns: ColumnsType<DepartmentMember> = [
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
      title: '关系',
      dataIndex: 'relationType',
      width: 100,
      render: (value) => (
        <span className={`admin-soft-badge ${value === 'primary' ? 'purple' : 'gray'}`}>
          {value === 'primary' ? '主部门' : '成员'}
        </span>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status) => <span className={`admin-status-pill ${status === 'active' ? 'active' : 'disabled'}`}>{status}</span>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (_, record) => (
        <Button
          className="admin-danger-outline"
          size="small"
          icon={<DeleteOutlined />}
          loading={removeMemberMutation.isPending}
          onClick={() => removeMemberMutation.mutate({ departmentId: record.departmentId, userId: record.userId })}
        >
          移除
        </Button>
      ),
    },
  ]

  const managerColumns: ColumnsType<DepartmentManager> = [
    {
      title: '负责人',
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
      key: 'email',
      render: () => '-',
    },
    {
      title: '类型',
      dataIndex: 'managerType',
      width: 100,
      render: (value) => (
        <span className={`admin-soft-badge ${value === 'primary' ? 'purple' : 'gray'}`}>
          {value === 'primary' ? '主管' : '副主管'}
        </span>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (_, record) => (
        <Button
          className="admin-danger-outline"
          size="small"
          icon={<DeleteOutlined />}
          loading={removeManagerMutation.isPending}
          onClick={() =>
            removeManagerMutation.mutate({
              departmentId: record.departmentId,
              userId: record.userId,
              managerType: record.managerType,
            })
          }
        >
          移除
        </Button>
      ),
    },
  ]

  return (
    <Space orientation="vertical" size={16} className="page-stack admin-org-page admin-departments-page">
      <Space className="page-toolbar admin-saas-toolbar" wrap>
        <Space size={12}>
          <span className="admin-page-icon">
            <ApartmentOutlined />
          </span>
          <Typography.Title level={2}>组织架构</Typography.Title>
        </Space>
        <AdminModuleNav />
      </Space>

      <div className="admin-org-grid admin-departments-layout">
        <div className="admin-org-panel admin-org-tree-panel admin-department-tree-card">
          <div className="admin-sidebar-action-row">
            <Button className="admin-sidebar-create" icon={<PlusOutlined />} onClick={() => openCreateDepartment(null)}>
              新建根部门
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              disabled={!effectiveSelectedDepartmentId}
              onClick={() => {
                if (effectiveSelectedDepartmentId) {
                  openCreateDepartment(effectiveSelectedDepartmentId)
                }
              }}
            >
              新建子部门
            </Button>
          </div>
          <Tree
            blockNode
            virtual={false}
            showLine={{ showLeafIcon: false }}
            treeData={treeData}
            className="admin-department-tree"
            selectedKeys={effectiveSelectedDepartmentId ? [effectiveSelectedDepartmentId] : []}
            defaultExpandAll
            onSelect={(keys) => setSelectedDepartmentId(keys[0]?.toString())}
          />
        </div>

        <div className="admin-departments-main">
          {selectedDepartment ? (
            <Space orientation="vertical" size={16} className="admin-org-detail">
              <div className="admin-group-hero-card admin-department-hero-card">
                <div className="admin-group-hero-main">
                  <span className="admin-group-hero-icon">
                    <ApartmentOutlined />
                  </span>
                  <div className="admin-group-hero-copy">
                    <Space className="admin-group-hero-title-row" size={12} align="center" wrap>
                      <Typography.Title level={3}>{selectedDepartment.name}</Typography.Title>
                      <span className={`admin-status-pill ${selectedDepartment.status === 'active' ? 'active' : 'disabled'}`}>
                        {selectedDepartment.status}
                      </span>
                      <Typography.Text type="secondary">{selectedDepartment.code}</Typography.Text>
                    </Space>
                    <div className="admin-group-meta-grid">
                      <span className="admin-meta-chip">层级 {selectedDepartment.depth + 1}</span>
                      <span className="admin-meta-chip">成员 {selectedDepartment.memberCount}</span>
                      <span className="admin-meta-chip">负责人 {selectedDepartment.managerCount}</span>
                      <span className="admin-meta-chip">排序 {selectedDepartment.sortOrder}</span>
                    </div>
                  </div>
                </div>
                <Space className="admin-group-hero-actions" wrap>
                  <Button className="admin-action-button" icon={<EditOutlined />} onClick={() => openEditDepartment(selectedDepartment)}>
                    编辑
                  </Button>
                  <Button className="admin-action-button" icon={<SwapOutlined />} onClick={() => openMoveDepartment(selectedDepartment)}>
                    移动
                  </Button>
                  {selectedDepartment.status === 'active' ? (
                    <Button
                      className="admin-danger-outline"
                      icon={<StopOutlined />}
                      loading={disableMutation.isPending}
                      onClick={() => disableMutation.mutate(selectedDepartment.id)}
                    >
                      停用
                    </Button>
                  ) : (
                    <Button
                      className="admin-success-outline"
                      icon={<CheckCircleOutlined />}
                      loading={enableMutation.isPending}
                      onClick={() => enableMutation.mutate(selectedDepartment.id)}
                    >
                      启用
                    </Button>
                  )}
                  <Button
                    className="admin-danger-outline"
                    icon={<DeleteOutlined />}
                    loading={deleteMutation.isPending}
                    onClick={() => confirmDelete(selectedDepartment)}
                  >
                    删除
                  </Button>
                </Space>
              </div>

              <div className="admin-detail-card-grid admin-department-card-grid">
                <div className="admin-data-card">
                  <Space className="admin-org-section-toolbar" wrap>
                    <Typography.Title level={4}>部门成员</Typography.Title>
                    <Button className="admin-action-button" icon={<UserAddOutlined />} onClick={() => openAssignment('member')}>
                      加入成员
                    </Button>
                  </Space>
                  <Table
                    rowKey="id"
                    size="middle"
                    loading={membersQuery.isLoading}
                    columns={memberColumns}
                    dataSource={membersQuery.data ?? []}
                    locale={{ emptyText: tableEmpty }}
                    pagination={{ pageSize: 8, placement: ['bottomEnd'] }}
                  />
                </div>

                <div className="admin-data-card">
                  <Space className="admin-org-section-toolbar" wrap>
                    <Typography.Title level={4}>部门负责人</Typography.Title>
                    <Button className="admin-action-button" icon={<UserSwitchOutlined />} onClick={() => openAssignment('manager')}>
                      添加负责人
                    </Button>
                  </Space>
                  <Table
                    rowKey="id"
                    size="middle"
                    columns={managerColumns}
                    dataSource={selectedNode?.managers ?? []}
                    locale={{ emptyText: tableEmpty }}
                    pagination={false}
                  />
                </div>
              </div>
            </Space>
          ) : (
            <div className="admin-org-empty">
              <Typography.Text type="secondary">创建或选择一个部门</Typography.Text>
            </div>
          )}
        </div>
      </div>

      <Modal
        title={departmentModal?.mode === 'edit' ? '编辑部门' : '新建部门'}
        open={Boolean(departmentModal)}
        okText="保存"
        cancelText="取消"
        confirmLoading={departmentMutation.isPending}
        onCancel={() => setDepartmentModal(null)}
        onOk={() => departmentForm.submit()}
      >
        <Form form={departmentForm} layout="vertical" onFinish={(values) => departmentMutation.mutate(values)}>
          <Form.Item label="上级部门" name="parentId">
            <Select allowClear disabled={departmentModal?.mode === 'edit'} options={departmentOptions} placeholder="根部门" />
          </Form.Item>
          <Form.Item label="部门编码" name="code" rules={[{ required: true, message: '请输入部门编码' }]}>
            <Input autoComplete="off" />
          </Form.Item>
          <Form.Item label="部门名称" name="name" rules={[{ required: true, message: '请输入部门名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="排序" name="sortOrder">
            <InputNumber min={0} className="admin-org-number-input" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`移动部门：${moveTarget?.name ?? ''}`}
        open={Boolean(moveTarget)}
        okText="移动"
        cancelText="取消"
        confirmLoading={moveMutation.isPending}
        onCancel={() => setMoveTarget(null)}
        onOk={() => moveForm.submit()}
      >
        <Form form={moveForm} layout="vertical" onFinish={(values) => moveMutation.mutate(values)}>
          <Form.Item label="新的上级部门" name="parentId">
            <Select allowClear options={departmentOptions} placeholder="移动为根部门" />
          </Form.Item>
          <Form.Item label="排序" name="sortOrder">
            <InputNumber min={0} className="admin-org-number-input" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={assignmentModal === 'manager' ? '添加负责人' : '加入成员'}
        open={Boolean(assignmentModal)}
        okText="保存"
        cancelText="取消"
        confirmLoading={assignmentMutation.isPending}
        onCancel={() => setAssignmentModal(null)}
        onOk={() => assignmentForm.submit()}
      >
        <Form form={assignmentForm} layout="vertical" onFinish={(values) => assignmentMutation.mutate(values)}>
          <Form.Item label="成员" name="userId" rules={[{ required: true, message: '请选择成员' }]}>
            <Select showSearch loading={allMembersQuery.isLoading} options={userOptions} optionFilterProp="label" />
          </Form.Item>
          {assignmentModal === 'manager' ? (
            <Form.Item label="负责人类型" name="managerType" initialValue="primary">
              <Select
                options={[
                  { label: '主管', value: 'primary' },
                  { label: '副主管', value: 'deputy' },
                ]}
              />
            </Form.Item>
          ) : (
            <Form.Item label="部门关系" name="relationType" initialValue="member">
              <Select
                options={[
                  { label: '主部门', value: 'primary' },
                  { label: '成员', value: 'member' },
                ]}
              />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </Space>
  )

  function openCreateDepartment(parentId: string | null) {
    setDepartmentModal({ mode: 'create', parentId })
    departmentForm.setFieldsValue({ parentId, code: '', name: '', sortOrder: 0 })
  }

  function openEditDepartment(department: DepartmentSummary) {
    setDepartmentModal({ mode: 'edit', department })
    departmentForm.setFieldsValue({
      parentId: department.parentId ?? null,
      code: department.code,
      name: department.name,
      sortOrder: department.sortOrder,
    })
  }

  function openMoveDepartment(department: DepartmentSummary) {
    setMoveTarget(department)
    moveForm.setFieldsValue({ parentId: department.parentId ?? null, sortOrder: department.sortOrder })
  }

  function openAssignment(type: AssignmentModalState) {
    setAssignmentModal(type)
    assignmentForm.setFieldsValue({
      relationType: type === 'member' ? 'member' : undefined,
      managerType: type === 'manager' ? 'primary' : undefined,
    })
  }

  function confirmDelete(department: DepartmentSummary) {
    modal.confirm({
      title: `删除部门：${department.name}`,
      content: '仅空的叶子部门可以删除。删除后部门不再出现在组织树中。',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => deleteMutation.mutateAsync(department.id),
    })
  }
}

function buildTreeData(nodes: DepartmentTreeNode[]): DataNode[] {
  return nodes.map((node) => ({
    key: node.department.id,
    title: (
      <span className="admin-department-tree-node">
        <ApartmentOutlined className="admin-department-node-icon" />
        <span className="admin-department-node-name">{node.department.name}</span>
        <span className="admin-department-count">{node.department.memberCount}</span>
      </span>
    ),
    children: buildTreeData(node.children),
  }))
}

function entityInitial(value: string) {
  return (value || '?').trim().slice(0, 1).toUpperCase()
}

function AdminTableEmpty() {
  return (
    <div className="admin-table-empty">
      <span className="admin-empty-icon">
        <ApartmentOutlined />
      </span>
      <Typography.Text type="secondary">No data</Typography.Text>
    </div>
  )
}

function findDepartmentNode(nodes: DepartmentTreeNode[], departmentId: string): DepartmentTreeNode | undefined {
  for (const node of nodes) {
    if (node.department.id === departmentId) {
      return node
    }
    const match = findDepartmentNode(node.children, departmentId)
    if (match) {
      return match
    }
  }
  return undefined
}
