import {
  CheckCircleOutlined,
  LinkOutlined,
  PauseCircleOutlined,
  SafetyCertificateOutlined,
  StopOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Descriptions,
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
import { useEffect, useMemo, useState } from 'react'

import {
  changeCrossSpaceLinkIntent,
  changeCrossSpaceRelationPolicy,
  createCrossSpaceLinkIntent,
  createCrossSpaceRelationPolicy,
  crossSpaceKeys,
  getCrossSpaceEndpointReference,
  getCrossSpaceRelations,
  type CrossSpaceLinkIntent,
  type CrossSpaceRelationPolicy,
  type EndpointReference,
} from '../api/crossSpaceCollaborationApi'
import type { UserProjectSpace } from '../api/projectSpacesApi'
import { errorMessage, formatTime } from '../projectSpaceView'

type PolicyForm = {
  grantId: string
  relationKey: string
  direction: CrossSpaceRelationPolicy['direction']
  sourceTypeId: string
  sourceVersionId: string
  targetTypeId: string
  targetVersionId: string
}

type IntentForm = {
  sourceWorkItemId: string
  expectedSourceVersion: number
  targetWorkItemId: string
  expectedTargetVersion: number
}

export function CrossSpaceRelationsPanel({ space }: { space: UserProjectSpace }) {
  const { message, modal } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const [online, setOnline] = useState(() => navigator.onLine)
  const [policyOpen, setPolicyOpen] = useState(false)
  const [intentPolicy, setIntentPolicy] = useState<CrossSpaceRelationPolicy>()
  const [reference, setReference] = useState<EndpointReference>()
  const [referenceInput, setReferenceInput] = useState('')
  const [referencePolicy, setReferencePolicy] = useState<CrossSpaceRelationPolicy>()
  const [policyForm] = Form.useForm<PolicyForm>()
  const [intentForm] = Form.useForm<IntentForm>()
  const manager = space.currentUserRole === 'owner' || space.currentUserRole === 'admin'
  const query = useQuery({
    queryKey: crossSpaceKeys.relations(space.id),
    queryFn: () => getCrossSpaceRelations(space.id),
  })
  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: crossSpaceKeys.relations(space.id) })

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

  const policyById = useMemo(
    () => new Map((query.data?.policies ?? []).map((policy) => [policy.id, policy])),
    [query.data?.policies],
  )
  const savePolicy = useMutation({
    mutationFn: (values: PolicyForm) => createCrossSpaceRelationPolicy(space.id, {
      grantId: values.grantId.trim(),
      relationKey: values.relationKey.trim(),
      direction: values.direction,
      sourceTypeId: values.sourceTypeId.trim(),
      sourceVersionId: values.sourceVersionId.trim(),
      targetTypeId: values.targetTypeId.trim(),
      targetVersionId: values.targetVersionId.trim(),
    }),
    onSuccess: async () => {
      setPolicyOpen(false)
      policyForm.resetFields()
      await refresh()
      message.success('关系策略草稿已创建')
    },
    onError: (error) => message.error(errorMessage(error, '创建关系策略失败')),
  })
  const policyLifecycle = useMutation({
    mutationFn: ({
      policy,
      action,
      party,
      reason,
    }: {
      policy: CrossSpaceRelationPolicy
      action: 'request' | 'confirm' | 'pause' | 'resume' | 'revoke' | 'archive'
      party?: 'source' | 'target'
      reason?: string
    }) => changeCrossSpaceRelationPolicy(space.id, policy, action, party, reason),
    onSuccess: async () => {
      await refresh()
      message.success('关系策略已重新校准')
    },
    onError: (error) => message.error(errorMessage(error, '关系策略更新失败')),
  })
  const saveIntent = useMutation({
    mutationFn: (values: IntentForm) => createCrossSpaceLinkIntent(
      space.id,
      intentPolicy!,
      {
        sourceWorkItemId: values.sourceWorkItemId.trim(),
        expectedSourceVersion: values.expectedSourceVersion,
        targetWorkItemId: values.targetWorkItemId.trim(),
        expectedTargetVersion: values.expectedTargetVersion,
      },
    ),
    onSuccess: async () => {
      setIntentPolicy(undefined)
      intentForm.resetFields()
      await refresh()
      message.success('建链意图已发送，等待目标空间确认')
    },
    onError: (error) => message.error(errorMessage(error, '建链意图发送失败')),
  })
  const intentLifecycle = useMutation({
    mutationFn: ({
      intent,
      action,
      reason,
    }: {
      intent: CrossSpaceLinkIntent
      action: 'accept' | 'reject' | 'cancel'
      reason?: string
    }) => changeCrossSpaceLinkIntent(space.id, intent, action, reason),
    onSuccess: async (_, variables) => {
      await refresh()
      message.success(variables.action === 'accept' ? '跨空间关系已建立' : '建链意图已关闭')
    },
    onError: (error) => message.error(errorMessage(error, '建链意图更新失败')),
  })
  const resolveReference = useMutation({
    mutationFn: () => getCrossSpaceEndpointReference(
      space.id,
      referencePolicy!.id,
      referenceInput.trim(),
    ),
    onSuccess: setReference,
    onError: (error) => {
      setReference(undefined)
      message.error(errorMessage(error, '目标引用不可用'))
    },
  })

  const danger = (
    policy: CrossSpaceRelationPolicy,
    action: 'pause' | 'revoke' | 'archive',
  ) => {
    let reason = ''
    modal.confirm({
      title: action === 'pause' ? '暂停关系策略？' : action === 'revoke' ? '撤销关系策略？' : '归档关系策略？',
      content: (
        <Input.TextArea
          aria-label="关系治理理由"
          placeholder="请输入至少 3 个字符的治理理由"
          onChange={(event) => { reason = event.target.value }}
        />
      ),
      okButtonProps: { danger: true },
      onOk: () => policyLifecycle.mutateAsync({ policy, action, reason }),
    })
  }
  const closeIntent = (intent: CrossSpaceLinkIntent, action: 'reject' | 'cancel') => {
    let reason = ''
    modal.confirm({
      title: action === 'reject' ? '拒绝建链意图？' : '取消建链意图？',
      content: (
        <Input.TextArea
          aria-label="建链关闭理由"
          onChange={(event) => { reason = event.target.value }}
        />
      ),
      onOk: () => intentLifecycle.mutateAsync({ intent, action, reason }),
    })
  }

  return (
    <Card
      className="content-card cross-space-relations"
      data-testid="cross-space-relations-panel"
      title={<Space><LinkOutlined />跨空间关系</Space>}
      extra={manager
        ? <Button disabled={!online} onClick={() => setPolicyOpen(true)}>新建策略</Button>
        : <Tag>当前成员只读治理事实</Tag>}
    >
      {!online ? (
        <Alert
          type="warning"
          showIcon
          message="当前离线，不能确认或建链"
          description="输入会保留；恢复在线、窗口聚焦或 realtime 失效后，以 REST 当前事实重新校准。"
        />
      ) : null}
      {query.isError ? (
        <Alert
          type="error"
          showIcon
          message="跨空间关系加载失败"
          action={<Button onClick={() => query.refetch()}>重试</Button>}
        />
      ) : null}
      {!query.isLoading && !query.isError && query.data?.policies.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无受权关系策略" />
      ) : null}
      <List
        loading={query.isLoading}
        dataSource={query.data?.policies ?? []}
        renderItem={(policy) => {
          const sourceSide = policy.sourceSpaceId === space.id
          const targetSide = policy.targetSpaceId === space.id
          return (
            <List.Item
              key={policy.id}
              actions={[
                manager && policy.status === 'draft'
                  ? <Button key="request" onClick={() => policyLifecycle.mutate({ policy, action: 'request' })}>请求双方确认</Button>
                  : null,
                manager && policy.status === 'requested'
                  && ((sourceSide && !policy.sourceConfirmedBy) || (targetSide && !policy.targetConfirmedBy))
                  ? (
                      <Button
                        key="confirm"
                        icon={<SafetyCertificateOutlined />}
                        onClick={() => policyLifecycle.mutate({
                          policy,
                          action: 'confirm',
                          party: sourceSide ? 'source' : 'target',
                        })}
                      >
                        确认本空间能力
                      </Button>
                    )
                  : null,
                sourceSide && policy.status === 'active'
                  ? <Button key="intent" disabled={!online} onClick={() => setIntentPolicy(policy)}>选择端点建链</Button>
                  : null,
                policy.status === 'active'
                  ? (
                      <Button
                        key="reference"
                        onClick={() => {
                          setReferencePolicy(policy)
                          setReference(undefined)
                        }}
                      >
                        解析最小引用
                      </Button>
                    )
                  : null,
                manager && policy.status === 'active'
                  ? <Button key="pause" icon={<PauseCircleOutlined />} onClick={() => danger(policy, 'pause')}>暂停</Button>
                  : null,
                manager && policy.status === 'paused'
                  ? <Button key="resume" onClick={() => policyLifecycle.mutate({ policy, action: 'resume' })}>恢复</Button>
                  : null,
                manager && !['revoked', 'archived'].includes(policy.status)
                  ? <Button key="revoke" danger icon={<StopOutlined />} onClick={() => danger(policy, 'revoke')}>撤销</Button>
                  : null,
                manager && policy.status === 'revoked'
                  ? <Button key="archive" onClick={() => danger(policy, 'archive')}>归档</Button>
                  : null,
              ].filter(Boolean)}
            >
              <List.Item.Meta
                title={(
                  <Space wrap>
                    <Typography.Text strong>{policy.relationKey}</Typography.Text>
                    <Tag color={policy.status === 'active' ? 'green' : undefined}>
                      {policyStatus(policy.status)}
                    </Tag>
                    <Tag>{policy.direction}</Tag>
                  </Space>
                )}
                description={(
                  <Space direction="vertical" size={2}>
                    <Typography.Text type="secondary">
                      策略 v{policy.version} · grant {policy.grantId} · {formatTime(policy.updatedAt)}
                    </Typography.Text>
                    <Typography.Text type="secondary">
                      双方 capability：源 {policy.sourceConfirmedBy ? '已确认' : '待确认'} / 目标 {policy.targetConfirmedBy ? '已确认' : '待确认'}
                    </Typography.Text>
                  </Space>
                )}
              />
            </List.Item>
          )
        }}
      />
      <Typography.Title level={5}>建链意图与反向引用</Typography.Title>
      <List
        dataSource={query.data?.intents ?? []}
        locale={{ emptyText: '暂无建链意图' }}
        renderItem={(intent) => {
          const policy = policyById.get(intent.policyId)
          const targetSide = intent.targetSpaceId === space.id
          const sourceSide = intent.sourceSpaceId === space.id
          return (
            <List.Item
              key={intent.id}
              actions={[
                targetSide && intent.status === 'requested'
                  ? (
                      <Button
                        key="accept"
                        type="primary"
                        icon={<CheckCircleOutlined />}
                        disabled={!online}
                        onClick={() => intentLifecycle.mutate({ intent, action: 'accept' })}
                      >
                        接受并原子建链
                      </Button>
                    )
                  : null,
                targetSide && intent.status === 'requested'
                  ? <Button key="reject" onClick={() => closeIntent(intent, 'reject')}>拒绝</Button>
                  : null,
                sourceSide && intent.status === 'requested'
                  ? <Button key="cancel" onClick={() => closeIntent(intent, 'cancel')}>取消</Button>
                  : null,
              ].filter(Boolean)}
            >
              <List.Item.Meta
                title={(
                  <Space wrap>
                    <Typography.Text>{policy?.relationKey ?? '受权关系'}</Typography.Text>
                    <Tag>{intentStatus(intent.status)}</Tag>
                  </Space>
                )}
                description={(
                  <Typography.Text type="secondary">
                    {opaque(intent.sourceWorkItemId)} ↔ {opaque(intent.targetWorkItemId)}
                    {' · '}intent v{intent.version}
                    {intent.canonicalRelationId ? ` · edge ${opaque(intent.canonicalRelationId)}` : ''}
                  </Typography.Text>
                )}
              />
            </List.Item>
          )
        }}
      />

      <Modal
        open={policyOpen}
        title="新建跨空间关系策略"
        okText="保存策略草稿"
        confirmLoading={savePolicy.isPending}
        onCancel={() => setPolicyOpen(false)}
        onOk={() => policyForm.submit()}
        destroyOnHidden
      >
        <Alert
          type="info"
          showIcon
          message="策略只引用授权与已发布定义"
          description="双方管理员确认 capability 后才可建链；canonical edge 仍由 S10 公共命令持有。"
        />
        <Form<PolicyForm>
          form={policyForm}
          layout="vertical"
          initialValues={{ direction: 'source_to_target' }}
          onFinish={(values) => savePolicy.mutate(values)}
        >
          <Form.Item name="grantId" label="生效 Grant ID" rules={[{ required: true }]}><Input autoFocus /></Form.Item>
          <Form.Item name="relationKey" label="关系键" rules={[{ required: true }, { pattern: /^[a-z][a-z0-9_]{0,63}$/ }]}><Input /></Form.Item>
          <Form.Item name="direction" label="方向" rules={[{ required: true }]}>
            <Select options={[
              { value: 'source_to_target', label: '源 → 目标' },
              { value: 'target_to_source', label: '目标 → 源' },
              { value: 'bidirectional', label: '双向' },
            ]} />
          </Form.Item>
          <Form.Item name="sourceTypeId" label="源类型 ID" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="sourceVersionId" label="源定义版本 ID" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="targetTypeId" label="目标类型 ID" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="targetVersionId" label="目标定义版本 ID" rules={[{ required: true }]}><Input /></Form.Item>
        </Form>
      </Modal>

      <Modal
        open={Boolean(intentPolicy)}
        title="选择跨空间关系端点"
        okText="发送建链意图"
        confirmLoading={saveIntent.isPending}
        onCancel={() => setIntentPolicy(undefined)}
        onOk={() => intentForm.submit()}
        destroyOnHidden
      >
        <Alert
          type="warning"
          showIcon
          message="目标标题、状态和路径不会在选择器中披露"
          description="请使用受权的稳定 ID 和当前版本；目标空间仍需独立确认 accept-link capability。"
        />
        <Form<IntentForm>
          form={intentForm}
          layout="vertical"
          initialValues={{ expectedSourceVersion: 0, expectedTargetVersion: 0 }}
          onFinish={(values) => saveIntent.mutate(values)}
        >
          <Form.Item name="sourceWorkItemId" label="源工作项 ID" rules={[{ required: true }]}><Input autoFocus /></Form.Item>
          <Form.Item name="expectedSourceVersion" label="源当前版本" rules={[{ required: true }]}><InputNumber min={0} precision={0} /></Form.Item>
          <Form.Item name="targetWorkItemId" label="目标工作项 ID" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="expectedTargetVersion" label="目标当前版本" rules={[{ required: true }]}><InputNumber min={0} precision={0} /></Form.Item>
        </Form>
      </Modal>

      <Modal
        open={Boolean(referencePolicy)}
        title="解析受权最小端点引用"
        footer={null}
        onCancel={() => setReferencePolicy(undefined)}
        destroyOnHidden
      >
        <Space.Compact block>
          <Input
            aria-label="端点工作项 ID"
            value={referenceInput}
            onChange={(event) => setReferenceInput(event.target.value)}
            placeholder="输入对端工作项 ID"
          />
          <Button
            type="primary"
            loading={resolveReference.isPending}
            disabled={!online || !referenceInput.trim()}
            onClick={() => resolveReference.mutate()}
          >
            当前校准
          </Button>
        </Space.Compact>
        {reference ? (
          <Descriptions bordered size="small" column={1} style={{ marginTop: 16 }}>
            <Descriptions.Item label="安全引用">{reference.opaqueReference}</Descriptions.Item>
            <Descriptions.Item label="类型">{reference.typeKey}</Descriptions.Item>
            <Descriptions.Item label="当前版本">{reference.version}</Descriptions.Item>
            <Descriptions.Item label="可建链">{reference.active ? '是' : '否'}</Descriptions.Item>
          </Descriptions>
        ) : null}
      </Modal>
    </Card>
  )
}

function opaque(id: string) {
  return `ref-${id.slice(0, 8)}`
}

function policyStatus(status: CrossSpaceRelationPolicy['status']) {
  return {
    draft: '草稿',
    requested: '待双方确认',
    active: '生效',
    paused: '暂停',
    revoked: '撤销',
    archived: '归档',
  }[status]
}

function intentStatus(status: CrossSpaceLinkIntent['status']) {
  return {
    requested: '待目标确认',
    linked: '已建立',
    rejected: '已拒绝',
    cancelled: '已取消',
  }[status]
}
