import { SyncOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Empty,
  Form,
  Input,
  InputNumber,
  List,
  Modal,
  Select,
  Space,
  Tag,
  Typography,
} from 'antd'
import { useEffect, useState } from 'react'

import {
  changeCrossSpaceSyncRule,
  crossSpaceKeys,
  executeCrossSpaceSync,
  getCrossSpaceSync,
  resolveCrossSpaceSyncConflict,
  saveCrossSpaceSyncRule,
  type CrossSpaceSyncRule,
} from '../api/crossSpaceCollaborationApi'
import type { UserProjectSpace } from '../api/projectSpacesApi'
import { errorMessage, formatTime } from '../projectSpaceView'

type RuleForm = {
  name: string
  grantId: string
  policyId: string
  canonicalRelationId: string
  direction: CrossSpaceSyncRule['configuration']['direction']
  sourceField: string
  targetField: string
}

export function CrossSpaceSyncPanel({ space }: { space: UserProjectSpace }) {
  const { message, modal } = AntdApp.useApp()
  const client = useQueryClient()
  const manager = space.currentUserRole === 'owner' || space.currentUserRole === 'admin'
  const [online, setOnline] = useState(() => navigator.onLine)
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm<RuleForm>()
  const query = useQuery({
    queryKey: crossSpaceKeys.sync(space.id),
    queryFn: () => getCrossSpaceSync(space.id),
  })
  const refresh = () => client.invalidateQueries({ queryKey: crossSpaceKeys.sync(space.id) })

  useEffect(() => {
    const calibrate = () => {
      setOnline(navigator.onLine)
      if (navigator.onLine) void query.refetch()
    }
    window.addEventListener('online', calibrate)
    window.addEventListener('offline', calibrate)
    window.addEventListener('focus', calibrate)
    return () => {
      window.removeEventListener('online', calibrate)
      window.removeEventListener('offline', calibrate)
      window.removeEventListener('focus', calibrate)
    }
  }, [query])

  const save = useMutation({
    mutationFn: (value: RuleForm) => saveCrossSpaceSyncRule(space.id, {
      expectedVersion: 0,
      grantId: value.grantId.trim(),
      policyId: value.policyId.trim(),
      canonicalRelationId: value.canonicalRelationId.trim(),
      name: value.name.trim(),
      direction: value.direction,
      trigger: 'manual',
      fieldMappings: [{
        sourceField: value.sourceField.trim(),
        targetField: value.targetField.trim(),
        transform: 'copy',
      }],
      stateMappings: [],
      conflictStrategy: 'manual',
    }),
    onSuccess: async () => {
      setOpen(false)
      form.resetFields()
      await refresh()
      message.success('同步规则草稿已创建')
    },
    onError: (error) => message.error(errorMessage(error, '创建同步规则失败')),
  })
  const lifecycle = useMutation({
    mutationFn: ({
      rule,
      action,
      party,
      reason,
    }: {
      rule: CrossSpaceSyncRule
      action: 'request' | 'confirm' | 'pause' | 'resume' | 'revoke' | 'archive'
      party?: 'source' | 'target'
      reason?: string
    }) => changeCrossSpaceSyncRule(space.id, rule, action, party, reason),
    onSuccess: async () => { await refresh() },
    onError: (error) => message.error(errorMessage(error, '同步规则更新失败')),
  })
  const execute = useMutation({
    mutationFn: ({
      rule,
      direction,
      sourceVersion,
      targetVersion,
    }: {
      rule: CrossSpaceSyncRule
      direction: 'source_to_target' | 'target_to_source'
      sourceVersion: number
      targetVersion: number
    }) => executeCrossSpaceSync(space.id, rule, direction, sourceVersion, targetVersion),
    onSuccess: async (run) => {
      await refresh()
      message.success(run.status === 'conflict' ? '已生成冲突待处理' : '同步运行已完成')
    },
    onError: (error) => message.error(errorMessage(error, '同步执行失败')),
  })
  const resolve = useMutation({
    mutationFn: ({ conflict, resolution, reason }: {
      conflict: NonNullable<typeof query.data>['conflicts'][number]
      resolution: 'skip' | 'compensate' | 'dead_letter'
      reason: string
    }) => resolveCrossSpaceSyncConflict(space.id, conflict, resolution, reason),
    onSuccess: async () => { await refresh() },
    onError: (error) => message.error(errorMessage(error, '冲突处理失败')),
  })

  const run = (rule: CrossSpaceSyncRule) => {
    let sourceVersion = 0
    let targetVersion = 0
    let direction: 'source_to_target' | 'target_to_source' = 'source_to_target'
    modal.confirm({
      title: '执行同步规则',
      content: (
        <Space direction="vertical">
          <Select
            aria-label="同步方向"
            defaultValue={direction}
            options={[
              { value: 'source_to_target', label: '来源 → 目标' },
              { value: 'target_to_source', label: '目标 → 来源' },
            ]}
            onChange={(value) => { direction = value }}
          />
          <InputNumber aria-label="来源版本" min={0} defaultValue={0} onChange={(v) => { sourceVersion = v ?? 0 }} />
          <InputNumber aria-label="目标版本" min={0} defaultValue={0} onChange={(v) => { targetVersion = v ?? 0 }} />
        </Space>
      ),
      onOk: () => execute.mutateAsync({ rule, direction, sourceVersion, targetVersion }),
    })
  }
  const closeConflict = (
    conflict: NonNullable<typeof query.data>['conflicts'][number],
    resolution: 'skip' | 'compensate' | 'dead_letter',
  ) => {
    let reason = ''
    modal.confirm({
      title: '记录冲突治理决定',
      content: <Input.TextArea aria-label="冲突治理理由" onChange={(event) => { reason = event.target.value }} />,
      okButtonProps: { danger: resolution !== 'skip' },
      onOk: () => resolve.mutateAsync({ conflict, resolution, reason }),
    })
  }

  return (
    <Card
      className="content-card cross-space-sync"
      data-testid="cross-space-sync-panel"
      title={<Space><SyncOutlined />跨空间字段与状态同步</Space>}
      extra={manager
        ? <Button disabled={!online} onClick={() => setOpen(true)}>新建同步规则</Button>
        : <Tag>当前成员只读运行事实</Tag>}
    >
      {!online ? (
        <Alert
          showIcon
          type="warning"
          message="当前离线，不能变更或执行同步"
          description="恢复在线或窗口聚焦后，将以服务端规则、运行和冲突事实重新校准。"
        />
      ) : null}
      {query.isError ? <Alert type="error" message="同步治理加载失败" action={<Button onClick={() => query.refetch()}>重试</Button>} /> : null}
      {!query.isLoading && !query.isError && query.data?.rules.length === 0
        ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无同步规则" />
        : null}
      <List
        loading={query.isLoading}
        dataSource={query.data?.rules ?? []}
        renderItem={(rule) => {
          const source = rule.sourceSpaceId === space.id
          const target = rule.targetSpaceId === space.id
          return (
            <List.Item
              actions={[
                manager && rule.status === 'draft' ? <Button key="request" onClick={() => lifecycle.mutate({ rule, action: 'request' })}>请求确认</Button> : null,
                manager && rule.status === 'requested' && source && !rule.sourceConfirmedBy ? <Button key="source" onClick={() => lifecycle.mutate({ rule, action: 'confirm', party: 'source' })}>来源确认</Button> : null,
                manager && rule.status === 'requested' && target && !rule.targetConfirmedBy ? <Button key="target" onClick={() => lifecycle.mutate({ rule, action: 'confirm', party: 'target' })}>目标确认</Button> : null,
                manager && rule.status === 'active' ? <Button key="run" disabled={!online} onClick={() => run(rule)}>执行</Button> : null,
                manager && rule.status === 'active' ? <Button key="pause" onClick={() => lifecycle.mutate({ rule, action: 'pause' })}>暂停</Button> : null,
                manager && rule.status === 'paused' ? <Button key="resume" onClick={() => lifecycle.mutate({ rule, action: 'resume' })}>恢复</Button> : null,
              ].filter(Boolean)}
            >
              <List.Item.Meta
                title={<Space><strong>{rule.name}</strong><Tag>{rule.status}</Tag><Tag>{rule.configuration.direction}</Tag></Space>}
                description={`版本 ${rule.currentVersion} · ${rule.configuration.fieldMappings.length} 个字段映射 · ${formatTime(rule.updatedAt)}`}
              />
            </List.Item>
          )
        }}
      />
      {query.data?.runs.length ? (
        <>
          <Typography.Title level={5}>最近运行</Typography.Title>
          <List
            size="small"
            dataSource={query.data.runs}
            renderItem={(run) => <List.Item><Space><Tag color={run.status === 'succeeded' ? 'green' : run.status === 'conflict' ? 'orange' : undefined}>{run.status}</Tag><span>{run.direction}</span><span>{formatTime(run.createdAt)}</span></Space></List.Item>}
          />
        </>
      ) : null}
      {query.data?.conflicts.filter((item) => item.status === 'open').map((conflict) => (
        <Alert
          key={conflict.id}
          type="warning"
          showIcon
          message={`待处理冲突：${conflict.kind}`}
          action={manager ? <Space><Button onClick={() => closeConflict(conflict, 'skip')}>跳过</Button><Button danger onClick={() => closeConflict(conflict, 'compensate')}>补偿</Button><Button danger onClick={() => closeConflict(conflict, 'dead_letter')}>死信</Button></Space> : undefined}
        />
      ))}
      <Modal title="新建同步规则" open={open} onCancel={() => setOpen(false)} onOk={() => form.submit()} confirmLoading={save.isPending}>
        <Form form={form} layout="vertical" onFinish={(value) => save.mutate(value)} initialValues={{ direction: 'source_to_target', sourceField: 'title', targetField: 'title' }}>
          <Form.Item name="name" label="规则名称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="grantId" label="授权 ID" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="policyId" label="关系策略 ID" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="canonicalRelationId" label="规范关系 ID" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="direction" label="方向" rules={[{ required: true }]}>
            <Select options={[
              { value: 'source_to_target', label: '来源 → 目标' },
              { value: 'target_to_source', label: '目标 → 来源' },
              { value: 'bidirectional', label: '双向' },
            ]} />
          </Form.Item>
          <Space align="start">
            <Form.Item name="sourceField" label="来源字段" rules={[{ required: true }]}><Input /></Form.Item>
            <Form.Item name="targetField" label="目标字段" rules={[{ required: true }]}><Input /></Form.Item>
          </Space>
        </Form>
      </Modal>
    </Card>
  )
}
