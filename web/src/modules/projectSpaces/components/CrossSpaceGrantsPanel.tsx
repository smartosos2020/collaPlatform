import {
  CheckCircleOutlined,
  LinkOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  SafetyCertificateOutlined,
  StopOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Select,
  Space,
  Tag,
  Typography,
} from 'antd'
import { useEffect, useState } from 'react'

import {
  changeCrossSpaceGrant,
  crossSpaceKeys,
  getCrossSpaceGrants,
  saveCrossSpaceGrant,
  type CrossSpaceGrant,
  type CrossSpaceGrantScope,
} from '../api/crossSpaceCollaborationApi'
import type { UserProjectSpace } from '../api/projectSpacesApi'
import { errorMessage, formatTime } from '../projectSpaceView'

type GrantForm = {
  name: string
  targetSpaceId: string
  direction: CrossSpaceGrantScope['direction']
  operations: CrossSpaceGrantScope['operations']
  sourceTypeId: string
  sourceVersionId: string
  targetTypeId: string
  targetVersionId: string
}

export function CrossSpaceGrantsPanel({ space }: { space: UserProjectSpace }) {
  const { message, modal } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [online, setOnline] = useState(() => navigator.onLine)
  const [form] = Form.useForm<GrantForm>()
  const query = useQuery({
    queryKey: crossSpaceKeys.grants(space.id),
    queryFn: () => getCrossSpaceGrants(space.id),
  })
  useEffect(() => {
    const update = () => {
      setOnline(navigator.onLine)
      if (navigator.onLine) void query.refetch()
    }
    window.addEventListener('online', update)
    window.addEventListener('offline', update)
    window.addEventListener('focus', update)
    return () => {
      window.removeEventListener('online', update)
      window.removeEventListener('offline', update)
      window.removeEventListener('focus', update)
    }
  }, [query])
  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: crossSpaceKeys.grants(space.id) })
  const saveMutation = useMutation({
    mutationFn: (values: GrantForm) => saveCrossSpaceGrant(space.id, {
      expectedVersion: 0,
      targetSpaceId: values.targetSpaceId.trim(),
      name: values.name.trim(),
      scope: {
        schemaVersion: 1,
        direction: values.direction,
        operations: values.operations,
        typeScopes: [{
          sourceTypeId: values.sourceTypeId.trim(),
          sourceVersionId: values.sourceVersionId.trim(),
          targetTypeId: values.targetTypeId.trim(),
          targetVersionId: values.targetVersionId.trim(),
        }],
      },
    }),
    onSuccess: async () => {
      setOpen(false)
      form.resetFields()
      await refresh()
      message.success('跨空间授权草稿已创建')
    },
    onError: (error) => message.error(errorMessage(error, '创建授权失败')),
  })
  const lifecycleMutation = useMutation({
    mutationFn: ({
      grant,
      action,
      party,
      reason,
    }: {
      grant: CrossSpaceGrant
      action: 'request' | 'confirm' | 'pause' | 'resume' | 'revoke' | 'archive'
      party?: 'source' | 'target'
      reason?: string
    }) => changeCrossSpaceGrant(space.id, grant, action, party, reason),
    onSuccess: async () => {
      await refresh()
      message.success('授权状态已更新')
    },
    onError: (error) => message.error(errorMessage(error, '授权状态更新失败')),
  })
  const confirmDanger = (grant: CrossSpaceGrant, action: 'pause' | 'revoke' | 'archive') => {
    let reason = ''
    modal.confirm({
      title: action === 'pause' ? '暂停跨空间授权？' : action === 'revoke' ? '撤销跨空间授权？' : '归档授权历史？',
      content: (
        <Input.TextArea
          aria-label="治理理由"
          placeholder="请输入至少 3 个字符的治理理由"
          onChange={(event) => { reason = event.target.value }}
        />
      ),
      okButtonProps: { danger: true },
      onOk: () => lifecycleMutation.mutateAsync({ grant, action, reason }),
    })
  }

  return (
    <Card
      className="content-card cross-space-grants"
      data-testid="cross-space-grants-panel"
      title={<Space><SafetyCertificateOutlined />跨空间授权</Space>}
      extra={<Button icon={<LinkOutlined />} onClick={() => setOpen(true)} disabled={!online}>新建授权</Button>}
    >
      {!online ? <Alert type="warning" showIcon message="当前离线" description="草稿输入会保留；恢复在线后重新校准授权事实。" /> : null}
      {query.isError ? <Alert type="error" showIcon message="授权加载失败" action={<Button onClick={() => query.refetch()}>重试</Button>} /> : null}
      {!query.isLoading && !query.isError && query.data?.grants.length === 0
        ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无跨空间授权" />
        : null}
      <List
        loading={query.isLoading}
        dataSource={query.data?.grants ?? []}
        renderItem={(grant) => (
          <List.Item
            key={grant.id}
            actions={[
              grant.status === 'draft'
                ? <Button key="request" onClick={() => lifecycleMutation.mutate({ grant, action: 'request' })}>发起请求</Button>
                : null,
              grant.status === 'requested' && !grant.sourceConfirmed
                ? <Button key="source" icon={<CheckCircleOutlined />} onClick={() => lifecycleMutation.mutate({ grant, action: 'confirm', party: 'source' })}>源空间确认</Button>
                : null,
              grant.status === 'requested' && !grant.targetConfirmed
                ? <Button key="target" icon={<CheckCircleOutlined />} onClick={() => lifecycleMutation.mutate({ grant, action: 'confirm', party: 'target' })}>目标空间确认</Button>
                : null,
              grant.status === 'active'
                ? <Button key="pause" icon={<PauseCircleOutlined />} onClick={() => confirmDanger(grant, 'pause')}>暂停</Button>
                : null,
              grant.status === 'paused'
                ? <Button key="resume" icon={<PlayCircleOutlined />} onClick={() => lifecycleMutation.mutate({ grant, action: 'resume' })}>恢复</Button>
                : null,
              !['revoked', 'archived'].includes(grant.status)
                ? <Button key="revoke" danger icon={<StopOutlined />} onClick={() => confirmDanger(grant, 'revoke')}>撤销</Button>
                : null,
              grant.status === 'revoked'
                ? <Button key="archive" onClick={() => confirmDanger(grant, 'archive')}>归档</Button>
                : null,
            ].filter(Boolean)}
          >
            <List.Item.Meta
              title={<Space wrap><Typography.Text strong>{grant.name}</Typography.Text><Tag>{statusLabel(grant.status)}</Tag></Space>}
              description={(
                <Space direction="vertical" size={2}>
                  <Typography.Text type="secondary">目标空间 {grant.targetSpaceId}</Typography.Text>
                  <Typography.Text type="secondary">
                    {grant.scope.direction} · {grant.scope.operations.join(' / ')} · v{grant.currentVersion} · {formatTime(grant.updatedAt)}
                  </Typography.Text>
                  <Typography.Text type="secondary">
                    双方确认：源 {grant.sourceConfirmed ? '已确认' : '待确认'} / 目标 {grant.targetConfirmed ? '已确认' : '待确认'}
                  </Typography.Text>
                </Space>
              )}
            />
          </List.Item>
        )}
      />
      <Modal
        open={open}
        title="新建跨空间类型授权"
        okText="保存草稿"
        confirmLoading={saveMutation.isPending}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        destroyOnHidden
      >
        <Alert
          type="info"
          showIcon
          message="授权不会创建成员或复制权限"
          description="只有源、目标空间管理员分别确认后才会生效；类型必须引用已发布配置版本。"
        />
        <Form<GrantForm>
          form={form}
          layout="vertical"
          initialValues={{ direction: 'bidirectional', operations: ['reference', 'relate'] }}
          onFinish={(values) => saveMutation.mutate(values)}
        >
          <Form.Item name="name" label="授权名称" rules={[{ required: true, whitespace: true }, { max: 160 }]}><Input autoFocus /></Form.Item>
          <Form.Item name="targetSpaceId" label="目标空间 ID" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="direction" label="方向" rules={[{ required: true }]}>
            <Select options={[
              { value: 'source_to_target', label: '源 → 目标' },
              { value: 'target_to_source', label: '目标 → 源' },
              { value: 'bidirectional', label: '双向' },
            ]} />
          </Form.Item>
          <Form.Item name="operations" label="最小操作范围" rules={[{ required: true }]}>
            <Select mode="multiple" options={[
              { value: 'reference', label: '引用' },
              { value: 'relate', label: '建立关系' },
              { value: 'read_fields', label: '读取字段' },
              { value: 'sync_fields', label: '同步字段' },
              { value: 'sync_state', label: '同步状态' },
            ]} />
          </Form.Item>
          <Typography.Title level={5}>已发布类型版本</Typography.Title>
          <Form.Item name="sourceTypeId" label="源类型 ID" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="sourceVersionId" label="源版本 ID" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="targetTypeId" label="目标类型 ID" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="targetVersionId" label="目标版本 ID" rules={[{ required: true }]}><Input /></Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}

function statusLabel(status: CrossSpaceGrant['status']) {
  return {
    draft: '草稿',
    requested: '待双方确认',
    active: '生效',
    paused: '已暂停',
    revoked: '已撤销',
    archived: '已归档',
  }[status]
}
