import { SafetyCertificateOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App as AntdApp, Button, Checkbox, Form, Modal, Select, Space, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMemo, useState } from 'react'

import { listMembers } from '../../admin/api/adminUsersApi'
import { flattenDepartmentTree, listDepartmentTree } from '../../admin/api/departmentsApi'
import { listUserGroups } from '../../admin/api/userGroupsApi'
import {
  grantResourcePermission,
  listResourcePermissions,
  revokeResourcePermission,
} from '../api/resourcePermissionsApi'
import type {
  GrantResourcePermissionRequest,
  ManagedResourceType,
  ResourcePermissionEntry,
  ResourcePermissionLevel,
} from '../api/resourcePermissionsApi'

type Props = {
  open: boolean
  resourceType: ManagedResourceType
  resourceId?: string
  resourceName?: string
  onClose: () => void
}

export function ResourcePermissionsModal({ open, resourceType, resourceId, resourceName, onClose }: Props) {
  const queryClient = useQueryClient()
  const { message, modal } = AntdApp.useApp()
  const [form] = Form.useForm<GrantResourcePermissionRequest>()
  const subjectType = Form.useWatch('subjectType', form) ?? 'user'
  const permissionLevel = Form.useWatch('permissionLevel', form) ?? 'view'
  const [confirmHighRisk, setConfirmHighRisk] = useState(false)

  const permissionsQuery = useQuery({
    queryKey: ['resource-permissions', resourceType, resourceId],
    queryFn: () => listResourcePermissions(resourceType, resourceId!),
    enabled: open && Boolean(resourceId),
  })
  const membersQuery = useQuery({ queryKey: ['admin', 'users'], queryFn: () => listMembers(), enabled: open })
  const departmentsQuery = useQuery({
    queryKey: ['admin', 'departments', 'tree'],
    queryFn: listDepartmentTree,
    enabled: open,
  })
  const groupsQuery = useQuery({
    queryKey: ['admin', 'user-groups', { activeOnly: true }],
    queryFn: () => listUserGroups({ activeOnly: true }),
    enabled: open,
  })
  const departments = useMemo(() => flattenDepartmentTree(departmentsQuery.data ?? []), [departmentsQuery.data])
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

  const highRisk = isHighRisk(permissionLevel)

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['resource-permissions', resourceType, resourceId] }),
      queryClient.invalidateQueries({ queryKey: ['docs'] }),
      queryClient.invalidateQueries({ queryKey: ['bases'] }),
      queryClient.invalidateQueries({ queryKey: ['projects'] }),
    ])
  }

  const grantMutation = useMutation({
    mutationFn: (values: GrantResourcePermissionRequest) =>
      grantResourcePermission(resourceType, resourceId!, {
        ...values,
        confirmHighRisk,
      }),
    onSuccess: async () => {
      message.success('权限已更新')
      form.resetFields()
      setConfirmHighRisk(false)
      await refresh()
    },
  })

  const revokeMutation = useMutation({
    mutationFn: ({ permissionId, confirm }: { permissionId: string; confirm: boolean }) =>
      revokeResourcePermission(permissionId, confirm),
    onSuccess: async () => {
      message.success('权限已撤销')
      await refresh()
    },
  })

  const columns: ColumnsType<ResourcePermissionEntry> = [
    {
      title: '授权主体',
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
      title: '权限',
      dataIndex: 'permissionLevel',
      render: (level: ResourcePermissionLevel) => <Tag color={isHighRisk(level) ? 'orange' : 'blue'}>{level}</Tag>,
    },
    {
      title: '来源',
      dataIndex: 'sourceType',
      render: (sourceType, record) => (
        <Space size={4}>
          <Tag color={sourceType === 'direct' ? 'green' : 'default'}>{sourceType}</Tag>
          {record.sourceId ? <Typography.Text type="secondary">{record.sourceId}</Typography.Text> : null}
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'effectiveStatus',
      render: (status) => <Tag color={status === 'active' ? 'green' : 'default'}>{status}</Tag>,
    },
    {
      title: '操作',
      key: 'actions',
      render: (_, record) => {
        if (record.sourceType !== 'direct') {
          return <Typography.Text type="secondary">继承来源不可在此撤销</Typography.Text>
        }
        if (record.effectiveStatus !== 'active') {
          return null
        }
        return (
          <Button danger size="small" loading={revokeMutation.isPending} onClick={() => confirmRevoke(record)}>
            撤销
          </Button>
        )
      },
    },
  ]

  function confirmRevoke(record: ResourcePermissionEntry) {
    const highRiskRevoke = isHighRisk(record.permissionLevel)
    modal.confirm({
      title: '撤销资源权限',
      content: highRiskRevoke ? '该授权为高风险权限，确认撤销后会写入审计日志。' : '确认撤销该直接授权？',
      okText: '撤销',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => revokeMutation.mutateAsync({ permissionId: record.id, confirm: highRiskRevoke }),
    })
  }

  return (
    <Modal
      width={920}
      title={`资源权限${resourceName ? `：${resourceName}` : ''}`}
      open={open}
      onCancel={onClose}
      footer={null}
    >
      <Space orientation="vertical" size={16} className="page-stack">
        <Form
          form={form}
          layout="inline"
          initialValues={{ subjectType: 'user', permissionLevel: 'view' }}
          onFinish={(values) => grantMutation.mutate(values)}
        >
          <Form.Item name="subjectType" rules={[{ required: true }]}>
            <Select
              className="resource-permission-subject-type"
              options={[
                { label: '成员', value: 'user' },
                { label: '部门', value: 'department' },
                { label: '用户组', value: 'user_group' },
              ]}
              onChange={() => form.setFieldValue('subjectId', undefined)}
            />
          </Form.Item>
          <Form.Item name="subjectId" rules={[{ required: true, message: '请选择授权主体' }]}>
            <Select
              showSearch
              className="resource-permission-subject"
              loading={membersQuery.isLoading || departmentsQuery.isLoading || groupsQuery.isLoading}
              options={subjectOptions}
              optionFilterProp="label"
              placeholder="选择主体"
            />
          </Form.Item>
          <Form.Item name="permissionLevel" rules={[{ required: true }]}>
            <Select
              className="resource-permission-level"
              options={[
                { label: 'view', value: 'view' },
                { label: 'comment', value: 'comment' },
                { label: 'edit', value: 'edit' },
                { label: 'manage', value: 'manage' },
                { label: 'owner', value: 'owner' },
              ]}
            />
          </Form.Item>
          <Button type="primary" icon={<SafetyCertificateOutlined />} htmlType="submit" loading={grantMutation.isPending}>
            保存授权
          </Button>
        </Form>

        {highRisk ? (
          <Alert
            type="warning"
            showIcon
            message="高风险权限"
            description={
              <Checkbox checked={confirmHighRisk} onChange={(event) => setConfirmHighRisk(event.target.checked)}>
                已确认授予 manage/owner 权限，保存后写入审计日志
              </Checkbox>
            }
          />
        ) : null}

        <Table
          rowKey="id"
          loading={permissionsQuery.isLoading}
          columns={columns}
          dataSource={permissionsQuery.data ?? []}
          pagination={{ pageSize: 6 }}
        />
      </Space>
    </Modal>
  )
}

function isHighRisk(level: string) {
  return level === 'manage' || level === 'owner'
}

function subjectTypeLabel(subjectType: string) {
  if (subjectType === 'department') return '部门'
  if (subjectType === 'user_group') return '用户组'
  if (subjectType === 'role') return '角色'
  return '成员'
}
