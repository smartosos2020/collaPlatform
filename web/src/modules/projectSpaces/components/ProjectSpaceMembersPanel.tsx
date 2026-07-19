import {
  CrownOutlined,
  DeleteOutlined,
  MailOutlined,
  PlusOutlined,
  ReloadOutlined,
  SwapOutlined,
  TeamOutlined,
  UserAddOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  App as AntdApp,
  Avatar,
  Button,
  Card,
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
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { useAuthStore } from '../../auth/authStore'
import { StatusBadge } from '../../../shared/components/StatusBadge'
import { TableEmptyState } from '../../../shared/components/TableEmptyState'
import {
  addProjectSpaceMember,
  changeProjectSpaceMemberRole,
  inviteProjectSpaceMember,
  leaveProjectSpace,
  listProjectSpaceInvitations,
  listProjectSpaceMembers,
  listProjectSpaceRoleCapabilities,
  removeProjectSpaceMember,
  resendProjectSpaceInvitation,
  revokeProjectSpaceInvitation,
  searchProjectSpaceCandidates,
  transferProjectSpaceOwner,
  type ProjectSpaceInvitation,
  type ProjectSpaceMember,
  type ProjectSpaceRole,
  type UserProjectSpace,
} from '../api/projectSpacesApi'
import { errorMessage, formatTime, roleLabel } from '../projectSpaceView'

type AddMemberForm = {
  userId: string
  roleKey: ProjectSpaceRole
  expiresInHours?: number
}

export function ProjectSpaceMembersPanel({ space }: { space: UserProjectSpace }) {
  const { message, modal } = AntdApp.useApp()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const currentUser = useAuthStore((state) => state.currentUser)
  const [memberModalMode, setMemberModalMode] = useState<'add' | 'invite'>()
  const [candidateSearch, setCandidateSearch] = useState('')
  const [form] = Form.useForm<AddMemberForm>()
  const writable = space.status === 'active'
  const isOwner = space.currentUserRole === 'owner'

  const membersQuery = useQuery({
    queryKey: ['project-spaces', space.id, 'members'],
    queryFn: () => listProjectSpaceMembers(space.id),
  })
  const invitationsQuery = useQuery({
    queryKey: ['project-spaces', space.id, 'invitations'],
    queryFn: () => listProjectSpaceInvitations(space.id),
  })
  const capabilitiesQuery = useQuery({
    queryKey: ['project-spaces', space.id, 'role-capabilities'],
    queryFn: () => listProjectSpaceRoleCapabilities(space.id),
  })
  const candidatesQuery = useQuery({
    queryKey: ['project-spaces', space.id, 'member-candidates', candidateSearch],
    queryFn: () => searchProjectSpaceCandidates(space.id, candidateSearch),
    enabled: Boolean(memberModalMode),
  })

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['project-spaces'] }),
      queryClient.invalidateQueries({ queryKey: ['project-spaces', space.id] }),
      queryClient.invalidateQueries({ queryKey: ['project-spaces', space.id, 'members'] }),
      queryClient.invalidateQueries({ queryKey: ['project-spaces', space.id, 'invitations'] }),
      queryClient.invalidateQueries({ queryKey: ['project-spaces', space.id, 'member-candidates'] }),
    ])
  }

  const addMutation = useMutation<ProjectSpaceMember | ProjectSpaceInvitation, Error, AddMemberForm>({
    mutationFn: (values: AddMemberForm) => memberModalMode === 'invite'
      ? inviteProjectSpaceMember(space.id, values)
      : addProjectSpaceMember(space.id, values.userId, values.roleKey),
    onSuccess: async () => {
      await refresh()
      message.success(memberModalMode === 'invite' ? '邀请已发送' : '成员已加入空间')
      closeMemberModal()
    },
    onError: (error) => message.error(errorMessage(error, memberModalMode === 'invite' ? '发送邀请失败' : '加入成员失败')),
  })
  const roleMutation = useMutation({
    mutationFn: ({ memberId, roleKey }: { memberId: string; roleKey: ProjectSpaceRole }) => changeProjectSpaceMemberRole(space.id, memberId, roleKey),
    onSuccess: async () => { await refresh(); message.success('成员角色已更新') },
    onError: (error) => message.error(errorMessage(error, '更新角色失败')),
  })
  const removeMutation = useMutation({
    mutationFn: (memberId: string) => removeProjectSpaceMember(space.id, memberId),
    onSuccess: async () => { await refresh(); message.success('成员已移除') },
    onError: (error) => message.error(errorMessage(error, '移除成员失败')),
  })
  const transferMutation = useMutation({
    mutationFn: (memberId: string) => transferProjectSpaceOwner(space.id, memberId),
    onSuccess: async () => { await refresh(); message.success('Owner 已转移，你现在是空间管理员') },
    onError: (error) => message.error(errorMessage(error, 'Owner 转移失败')),
  })
  const invitationMutation = useMutation({
    mutationFn: ({ action, invitationId }: { action: 'resend' | 'revoke'; invitationId: string }) => action === 'resend'
      ? resendProjectSpaceInvitation(space.id, invitationId)
      : revokeProjectSpaceInvitation(space.id, invitationId),
    onSuccess: async (_, values) => { await refresh(); message.success(values.action === 'resend' ? '邀请已重新发送' : '邀请已撤销') },
    onError: (error) => message.error(errorMessage(error, '邀请操作失败')),
  })
  const leaveMutation = useMutation({
    mutationFn: () => leaveProjectSpace(space.id),
    onSuccess: async () => {
      await refresh()
      message.success('已退出项目空间')
      navigate('/project-spaces', { replace: true })
    },
    onError: (error) => message.error(errorMessage(error, '退出空间失败，请先转移 Owner')),
  })

  const openMemberModal = (mode: 'add' | 'invite') => {
    setMemberModalMode(mode)
    setCandidateSearch('')
    form.setFieldsValue({ roleKey: space.currentUserRole === 'admin' ? 'member' : 'member', expiresInHours: 72 })
  }
  const closeMemberModal = () => {
    setMemberModalMode(undefined)
    form.resetFields()
    setCandidateSearch('')
  }
  const confirmRemove = (member: ProjectSpaceMember) => modal.confirm({
    title: `移除 ${member.displayName || member.username}？`,
    content: '该成员将立即失去空间访问权限，操作会写入审计日志。',
    okText: '确认移除',
    okButtonProps: { danger: true },
    onOk: () => removeMutation.mutateAsync(member.id),
  })
  const confirmTransfer = (member: ProjectSpaceMember) => modal.confirm({
    title: `将 Owner 转移给 ${member.displayName || member.username}？`,
    content: '转移后你将变为空间管理员，新 Owner 将拥有完整空间治理能力。',
    okText: '确认转移',
    onOk: () => transferMutation.mutateAsync(member.id),
  })
  const confirmLeave = () => modal.confirm({
    title: '退出项目空间？',
    content: isOwner ? 'Owner 必须先将所有权转移给其他成员。' : '退出后需要由空间管理员重新添加或邀请。',
    okText: '确认退出',
    okButtonProps: { danger: true },
    onOk: () => leaveMutation.mutateAsync(),
  })

  const roleOptions = availableRoleOptions(space.currentUserRole)
  const memberColumns: ColumnsType<ProjectSpaceMember> = [
    {
      title: '成员', key: 'member', minWidth: 220,
      render: (_, member) => (
        <Space>
          <Avatar className="project-space-member-avatar">{initial(member.displayName || member.username)}</Avatar>
          <span className="project-space-table-person"><strong>{member.displayName || member.username}</strong><small>{member.email || member.username}</small></span>
        </Space>
      ),
    },
    {
      title: '角色', dataIndex: 'roleKey', width: 170,
      render: (roleKey: ProjectSpaceRole, member) => roleKey === 'owner' ? (
        <Tag icon={<CrownOutlined />} color="gold">Owner</Tag>
      ) : (
        <Select
          aria-label={`调整 ${member.displayName || member.username} 的角色`}
          size="small"
          value={roleKey}
          options={roleOptions}
          disabled={!writable || roleMutation.isPending}
          onChange={(nextRole) => roleMutation.mutate({ memberId: member.id, roleKey: nextRole })}
        />
      ),
    },
    { title: '账号状态', dataIndex: 'userStatus', width: 120, render: (status: string) => <StatusBadge status={status} /> },
    { title: '加入时间', dataIndex: 'joinedAt', width: 180, render: formatTime },
    {
      title: '操作', key: 'actions', width: isOwner ? 180 : 100,
      render: (_, member) => (
        <Space size={6}>
          {isOwner && member.roleKey !== 'owner' ? <Button size="small" icon={<SwapOutlined />} disabled={!writable} onClick={() => confirmTransfer(member)}>转为 Owner</Button> : null}
          {member.roleKey !== 'owner' && member.userId !== currentUser?.id ? <Button danger size="small" icon={<DeleteOutlined />} disabled={!writable} onClick={() => confirmRemove(member)}>移除</Button> : null}
        </Space>
      ),
    },
  ]
  const invitationColumns: ColumnsType<ProjectSpaceInvitation> = [
    {
      title: '受邀成员', key: 'invitee', minWidth: 210,
      render: (_, invitation) => <span className="project-space-table-person"><strong>{invitation.inviteeDisplayName}</strong><small>{invitation.inviteeEmail || invitation.inviteeUserId}</small></span>,
    },
    { title: '角色', dataIndex: 'roleKey', width: 120, render: roleLabel },
    { title: '状态', dataIndex: 'status', width: 110, render: invitationStatusTag },
    { title: '有效期至', dataIndex: 'expiresAt', width: 180, render: formatTime },
    {
      title: '操作', key: 'actions', width: 170,
      render: (_, invitation) => invitation.status === 'pending' ? (
        <Space size={6}>
          <Button size="small" icon={<ReloadOutlined />} disabled={!writable} onClick={() => invitationMutation.mutate({ action: 'resend', invitationId: invitation.id })}>重发</Button>
          <Button size="small" danger icon={<DeleteOutlined />} disabled={!writable} onClick={() => invitationMutation.mutate({ action: 'revoke', invitationId: invitation.id })}>撤销</Button>
        </Space>
      ) : <Typography.Text type="secondary">已结束</Typography.Text>,
    },
  ]

  return (
    <div className="project-space-members-stack">
      <Card
        className="content-card"
        data-testid="project-space-members-card"
        title={<Space><TeamOutlined />空间成员 <Typography.Text type="secondary">({membersQuery.data?.length ?? 0})</Typography.Text></Space>}
        extra={<Space wrap><Button icon={<PlusOutlined />} disabled={!writable} onClick={() => openMemberModal('add')}>直接加入</Button><Button type="primary" icon={<UserAddOutlined />} disabled={!writable} onClick={() => openMemberModal('invite')}>邀请成员</Button></Space>}
      >
        <div className="project-space-table-scroll">
          <Table
            rowKey="id"
            columns={memberColumns}
            dataSource={membersQuery.data ?? []}
            loading={membersQuery.isLoading}
            pagination={false}
            locale={{ emptyText: <TableEmptyState description="暂无空间成员" /> }}
            scroll={{ x: 860 }}
          />
        </div>
      </Card>

      <Card
        className="content-card"
        data-testid="project-space-invitations-card"
        title={<Space><MailOutlined />成员邀请</Space>}
        extra={<Button danger type="text" disabled={!writable} onClick={confirmLeave}>退出空间</Button>}
      >
        <div className="project-space-table-scroll">
          <Table
            rowKey="id"
            columns={invitationColumns}
            dataSource={invitationsQuery.data ?? []}
            loading={invitationsQuery.isLoading}
            pagination={{ pageSize: 5, hideOnSinglePage: true }}
            locale={{ emptyText: <TableEmptyState description="暂无成员邀请" /> }}
            scroll={{ x: 760 }}
          />
        </div>
      </Card>

      <Modal
        title={memberModalMode === 'invite' ? '邀请成员' : '直接加入成员'}
        open={Boolean(memberModalMode)}
        okText={memberModalMode === 'invite' ? '发送邀请' : '加入空间'}
        cancelText="取消"
        confirmLoading={addMutation.isPending}
        onCancel={closeMemberModal}
        onOk={() => form.submit()}
        destroyOnHidden
      >
        <Form<AddMemberForm> form={form} layout="vertical" onFinish={(values) => addMutation.mutate(values)}>
          <Input.Search
            allowClear
            aria-label="搜索候选成员"
            placeholder="按名称、账号或邮箱搜索"
            value={candidateSearch}
            onChange={(event) => setCandidateSearch(event.target.value)}
            className="project-space-candidate-search"
          />
          <Form.Item name="userId" label="候选成员" rules={[{ required: true, message: '请选择成员' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              loading={candidatesQuery.isLoading}
              placeholder="选择同企业的启用成员"
              options={(candidatesQuery.data ?? []).map((candidate) => ({
                value: candidate.userId,
                label: `${candidate.displayName || candidate.username} · ${candidate.email || candidate.username}`,
              }))}
              notFoundContent="没有可加入的候选成员"
            />
          </Form.Item>
          <Form.Item name="roleKey" label="空间角色" rules={[{ required: true }]}><Select options={roleOptions} /></Form.Item>
          {memberModalMode === 'invite' ? (
            <Form.Item name="expiresInHours" label="邀请有效期"><Select options={[{ value: 24, label: '24 小时' }, { value: 72, label: '3 天' }, { value: 168, label: '7 天' }]} /></Form.Item>
          ) : null}
        </Form>
        <Typography.Paragraph type="secondary">
          {capabilitiesQuery.data ? `当前角色可配置：${roleOptions.map((option) => option.label).join('、')}` : '正在确认角色能力…'}
        </Typography.Paragraph>
      </Modal>
    </div>
  )
}

function availableRoleOptions(currentRole?: string | null) {
  const roles: ProjectSpaceRole[] = currentRole === 'owner' ? ['admin', 'member', 'guest'] : ['member', 'guest']
  return roles.map((role) => ({ value: role, label: roleLabel(role) }))
}

function invitationStatusTag(status: string) {
  const colors: Record<string, string> = { pending: 'processing', accepted: 'success', rejected: 'default', revoked: 'error', expired: 'warning' }
  const labels: Record<string, string> = { pending: '待接受', accepted: '已接受', rejected: '已拒绝', revoked: '已撤销', expired: '已过期' }
  return <Tag color={colors[status]}>{labels[status] ?? status}</Tag>
}

function initial(value: string) {
  return value.trim().slice(0, 1).toUpperCase() || 'U'
}
