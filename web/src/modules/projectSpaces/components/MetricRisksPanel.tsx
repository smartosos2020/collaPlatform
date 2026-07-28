import {
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  SafetyCertificateOutlined,
  SyncOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  Button,
  Card,
  Col,
  Collapse,
  Empty,
  Form,
  Input,
  InputNumber,
  List,
  Row,
  Select,
  Space,
  Tag,
  Typography,
} from 'antd'
import { useEffect, useState } from 'react'

import { useRealtimeSubscription } from '../../../shared/realtime'
import {
  actOnRiskSignal,
  evaluateRisks,
  getRiskFoundation,
  metricRiskKeys,
  publishRiskPolicy,
  saveRiskPolicy,
  type RiskPolicy,
  type RiskSignal,
} from '../api/metricRisksApi'
import type { UserProjectSpace } from '../api/projectSpacesApi'
import { formatTime } from '../projectSpaceView'

type PolicyEditor = {
  policyKey: string
  name: string
  description: string
  signalTypes: string[]
  severity: 'info' | 'warning' | 'critical'
  cooldownHours: number
}

const initialPolicy: PolicyEditor = {
  policyKey: 'delivery.risk',
  name: '交付风险策略',
  description: '只根据当前受权的计划、阻塞、质量和资源公共事实生成预警。',
  signalTypes: ['overdue', 'blocked', 'quality', 'resource'],
  severity: 'warning',
  cooldownHours: 24,
}

export function MetricRisksPanel({ space }: { space: UserProjectSpace }) {
  const client = useQueryClient()
  const [form] = Form.useForm<PolicyEditor>()
  const canManage = space.currentUserRole === 'owner' || space.currentUserRole === 'admin'
  const draftKey = `colla.metric-risk-policy.${space.id}`
  const [selected, setSelected] = useState<RiskPolicy>()
  const [online, setOnline] = useState(() => navigator.onLine)
  const foundation = useQuery({
    queryKey: metricRiskKeys.foundation(space.id),
    queryFn: () => getRiskFoundation(space.id),
    retry: false,
  })
  const refresh = () => client.invalidateQueries({
    queryKey: metricRiskKeys.foundation(space.id),
  })
  const save = useMutation({
    mutationFn: (values: PolicyEditor) => saveRiskPolicy(space.id, {
      ...values,
      policyId: selected?.id,
      expectedVersion: selected?.version ?? 0,
    }),
    onSuccess: async policy => {
      setSelected(policy)
      localStorage.removeItem(draftKey)
      await refresh()
    },
  })
  const publish = useMutation({
    mutationFn: () => publishRiskPolicy(space.id, selected!),
    onSuccess: refresh,
  })
  const evaluate = useMutation({
    mutationFn: () => evaluateRisks(space.id),
    onSuccess: refresh,
  })
  const action = useMutation({
    mutationFn: ({
      signal,
      kind,
    }: {
      signal: RiskSignal
      kind: 'acknowledge' | 'close' | 'suppress' | 'reopen'
    }) => actOnRiskSignal(
      space.id,
      signal,
      kind,
      `${kind} from the governed risk workbench`,
    ),
    onSuccess: refresh,
  })

  useRealtimeSubscription(['project_space.changed'], signal => {
    if (signal.objectType === 'project_space' && signal.objectId === space.id) {
      void refresh()
    }
  })

  useEffect(() => {
    const calibrate = () => {
      setOnline(navigator.onLine)
      if (navigator.onLine) void refresh()
    }
    const storage = (event: StorageEvent) => {
      if (event.key === draftKey && event.newValue) {
        form.setFieldsValue(JSON.parse(event.newValue) as PolicyEditor)
      }
    }
    window.addEventListener('online', calibrate)
    window.addEventListener('offline', calibrate)
    window.addEventListener('focus', calibrate)
    window.addEventListener('storage', storage)
    return () => {
      window.removeEventListener('online', calibrate)
      window.removeEventListener('offline', calibrate)
      window.removeEventListener('focus', calibrate)
      window.removeEventListener('storage', storage)
    }
  }, [draftKey, form, space.id])

  const selectPolicy = (policy: RiskPolicy) => {
    setSelected(policy)
    form.setFieldsValue({
      policyKey: policy.policyKey,
      name: policy.name,
      description: policy.description,
      signalTypes: policy.draftSignalTypes,
      severity: policy.draftSeverity,
      cooldownHours: policy.draftCooldownHours,
    })
  }

  return (
    <Card
      className="content-card metric-risks-panel"
      data-testid="metric-risks-panel"
      title={<Space><SafetyCertificateOutlined />延期、阻塞、质量与资源风险</Space>}
      extra={(
        <Tag icon={<SyncOutlined spin={evaluate.isPending} />} color={online ? 'green' : 'orange'}>
          {online ? 'REST 当前事实' : '离线只读'}
        </Tag>
      )}
    >
      {!online ? (
        <Alert
          type="warning"
          showIcon
          message="离线不伪造风险关闭或评估；恢复后会重新校准当前来源权限"
        />
      ) : null}
      {foundation.isError ? (
        <Alert type="error" showIcon message="风险目录加载失败或无权访问" />
      ) : null}
      {foundation.data?.truncated ? (
        <Alert type="warning" showIcon message="风险目录已截断，数量不代表完整事实" />
      ) : null}
      <Row gutter={[16, 16]}>
        <Col xs={24} md={9}>
          <Typography.Title level={5}>策略与不可变版本</Typography.Title>
          <List
            aria-label="风险策略目录"
            loading={foundation.isLoading}
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无风险策略" /> }}
            dataSource={foundation.data?.policies ?? []}
            renderItem={policy => (
              <List.Item>
                <Button type="text" block onClick={() => selectPolicy(policy)}>
                  <Space wrap>
                    <strong>{policy.name}</strong>
                    <Tag>{policy.status}</Tag>
                    {policy.publishedVersion
                      ? <Tag color="blue">v{policy.publishedVersion.versionNumber}</Tag>
                      : null}
                  </Space>
                </Button>
              </List.Item>
            )}
          />
        </Col>
        <Col xs={24} md={15}>
          {canManage ? (
            <Form<PolicyEditor>
              form={form}
              layout="vertical"
              initialValues={initialPolicy}
              onValuesChange={(_, values) => localStorage.setItem(draftKey, JSON.stringify(values))}
              onFinish={values => online && save.mutate(values)}
            >
              <Row gutter={12}>
                <Col xs={24} sm={12}>
                  <Form.Item name="name" label="策略名称" rules={[{ required: true }, { max: 160 }]}>
                    <Input aria-label="风险策略名称" />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item name="policyKey" label="永久 Key" rules={[{ required: true }, { pattern: /^[a-z][a-z0-9_.-]{1,63}$/ }]}>
                    <Input aria-label="风险策略永久 Key" disabled={Boolean(selected)} />
                  </Form.Item>
                </Col>
                <Col span={24}>
                  <Form.Item name="description" label="来源与解释" rules={[{ required: true }, { max: 2000 }]}>
                    <Input.TextArea aria-label="风险策略来源与解释" rows={2} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item name="signalTypes" label="信号类型" rules={[{ required: true }]}>
                    <Select
                      mode="multiple"
                      options={foundation.data?.signalTypes.map(value => ({ value }))}
                    />
                  </Form.Item>
                </Col>
                <Col xs={12} sm={6}>
                  <Form.Item name="severity" label="严重度">
                    <Select options={foundation.data?.severities.map(value => ({ value }))} />
                  </Form.Item>
                </Col>
                <Col xs={12} sm={6}>
                  <Form.Item name="cooldownHours" label="冷却（小时）">
                    <InputNumber min={1} max={720} />
                  </Form.Item>
                </Col>
              </Row>
              <Space wrap>
                <Button type="primary" htmlType="submit" disabled={!online} loading={save.isPending}>
                  保存策略草稿
                </Button>
                <Button
                  disabled={!online || !selected}
                  loading={publish.isPending}
                  onClick={() => publish.mutate()}
                >
                  发布不可变策略
                </Button>
                <Button
                  data-testid="risk-evaluate-action"
                  disabled={!online}
                  loading={evaluate.isPending}
                  onClick={() => evaluate.mutate()}
                >
                  评估当前受权事实
                </Button>
                <Button onClick={() => {
                  setSelected(undefined)
                  form.setFieldsValue(initialPolicy)
                }}>
                  新建策略
                </Button>
              </Space>
            </Form>
          ) : (
            <Alert type="info" showIcon message="当前为只读风险视图；只有 owner/admin 可发布策略或改变信号状态" />
          )}
        </Col>
      </Row>
      <Typography.Title level={5}>当前风险信号</Typography.Title>
      <List
        aria-label="当前风险信号"
        loading={foundation.isLoading}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前无受权风险信号" /> }}
        dataSource={foundation.data?.signals ?? []}
        renderItem={signal => (
          <List.Item className="metric-risk-signal">
            <Card
              size="small"
              title={(
                <Space wrap>
                  <ExclamationCircleOutlined />
                  <strong>{signal.signalType}</strong>
                  <Tag color={severityColor(signal.severity)}>{signal.severity}</Tag>
                  <Tag>{signal.state}</Tag>
                  <span>策略 v{signal.policyVersion}</span>
                </Space>
              )}
              extra={<span>{formatTime(signal.observedAt)}</span>}
            >
              <Collapse
                ghost
                items={[{
                  key: 'evidence',
                  label: `受限证据 ${signal.evidence.length} · ${signal.evidenceFingerprint.slice(0, 12)}`,
                  children: signal.evidence.map(item => (
                    <Typography.Paragraph key={`${item.sourceType}:${item.sourceIdentity}`}>
                      <strong>{item.sourceType}@{item.sourceVersion}</strong>
                      {' · '}{item.explanation}
                    </Typography.Paragraph>
                  )),
                }]}
              />
              {canManage ? (
                <Space wrap>
                  <Button
                    icon={<CheckCircleOutlined />}
                    disabled={!online || signal.state !== 'open'}
                    onClick={() => action.mutate({ signal, kind: 'acknowledge' })}
                  >
                    确认
                  </Button>
                  <Button
                    disabled={!online || signal.state === 'closed'}
                    onClick={() => action.mutate({ signal, kind: 'close' })}
                  >
                    关闭
                  </Button>
                  <Button
                    disabled={!online || signal.state === 'suppressed'}
                    onClick={() => action.mutate({ signal, kind: 'suppress' })}
                  >
                    抑制
                  </Button>
                  <Button
                    disabled={!online || signal.state === 'open'}
                    onClick={() => action.mutate({ signal, kind: 'reopen' })}
                  >
                    重开
                  </Button>
                </Space>
              ) : null}
            </Card>
          </List.Item>
        )}
      />
      <Typography.Paragraph type="secondary">
        风险信号不改写来源事实、不授权，也不形成个人绩效或隐式利用率评价。{foundation.data?.diagnostic}
      </Typography.Paragraph>
    </Card>
  )
}

function severityColor(severity: RiskSignal['severity']) {
  if (severity === 'critical') return 'red'
  if (severity === 'warning') return 'orange'
  return 'blue'
}
