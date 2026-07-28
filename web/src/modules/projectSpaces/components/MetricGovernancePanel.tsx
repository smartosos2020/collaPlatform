import {
  AuditOutlined,
  DownloadOutlined,
  PlayCircleOutlined,
  SafetyOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  List,
  Row,
  Select,
  Space,
  Statistic,
  Tag,
  Typography,
} from 'antd'
import { useEffect, useState } from 'react'

import { useRealtimeSubscription } from '../../../shared/realtime'
import {
  exportGovernanceRun,
  getGovernanceFoundation,
  governanceKeys,
  runGovernanceReport,
  saveGovernanceReport,
  type AuditReport,
} from '../api/metricGovernanceApi'
import type { UserProjectSpace } from '../api/projectSpacesApi'
import { formatTime } from '../projectSpaceView'

type Editor = {
  reportKey: string
  name: string
  description: string
  sections: string[]
}

const initial: Editor = {
  reportKey: 'space.governance',
  name: '空间治理审计报表',
  description: '只导出当前受权的治理元数据、配置健康与风险覆盖。',
  sections: ['metrics', 'dashboards', 'risks', 'configuration', 'audit'],
}

export function MetricGovernancePanel({ space }: { space: UserProjectSpace }) {
  const client = useQueryClient()
  const [form] = Form.useForm<Editor>()
  const [selected, setSelected] = useState<AuditReport>()
  const [online, setOnline] = useState(() => navigator.onLine)
  const canManage = space.currentUserRole === 'owner' || space.currentUserRole === 'admin'
  const query = useQuery({
    queryKey: governanceKeys.foundation(space.id),
    queryFn: () => getGovernanceFoundation(space.id),
    retry: false,
  })
  const refresh = () => client.invalidateQueries({
    queryKey: governanceKeys.foundation(space.id),
  })
  const save = useMutation({
    mutationFn: (values: Editor) => saveGovernanceReport(space.id, {
      ...values,
      reportId: selected?.id,
      expectedVersion: selected?.version ?? 0,
    }),
    onSuccess: async report => {
      setSelected(report)
      await refresh()
    },
  })
  const run = useMutation({
    mutationFn: () => runGovernanceReport(space.id, selected!),
    onSuccess: refresh,
  })
  const exportRun = useMutation({
    mutationFn: (runId: string) => exportGovernanceRun(space.id, runId, 'csv'),
  })

  useRealtimeSubscription(['project_space.changed'], signal => {
    if (signal.objectType === 'project_space' && signal.objectId === space.id) void refresh()
  })
  useEffect(() => {
    const calibrate = () => {
      setOnline(navigator.onLine)
      if (navigator.onLine) void refresh()
    }
    window.addEventListener('online', calibrate)
    window.addEventListener('offline', calibrate)
    window.addEventListener('focus', calibrate)
    return () => {
      window.removeEventListener('online', calibrate)
      window.removeEventListener('offline', calibrate)
      window.removeEventListener('focus', calibrate)
    }
  }, [space.id])

  const overview = query.data?.overview
  return (
    <Card
      className="content-card metric-governance-panel"
      data-testid="metric-governance-panel"
      title={<Space><SafetyOutlined />管理驾驶舱、配置健康与审计报表</Space>}
      extra={<Tag color={online ? 'green' : 'orange'}>{online ? '当前 REST' : '离线只读'}</Tag>}
    >
      {query.isError ? <Alert type="error" showIcon message="治理驾驶舱加载失败或无权访问" /> : null}
      {overview?.truncated ? (
        <Alert type="warning" showIcon message="至少一个来源已截断；配置健康为 unknown，导出数量不完整" />
      ) : null}
      <Row gutter={[12, 12]}>
        <Col xs={12} sm={8}><Statistic title="已发布指标" value={overview?.publishedMetrics ?? 0} /></Col>
        <Col xs={12} sm={8}><Statistic title="活动看板" value={overview?.activeDashboards ?? 0} /></Col>
        <Col xs={24} sm={8}><Statistic title="待治理风险" value={overview?.openRisks ?? 0} /></Col>
      </Row>
      <List
        grid={{ gutter: 12, xs: 1, sm: 3 }}
        dataSource={overview?.health ?? []}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无当前治理元数据" /> }}
        renderItem={item => (
          <List.Item>
            <Card size="small" title={<Space><span>{item.component}</span><Tag>{item.status}</Tag></Space>}>
              <Typography.Paragraph>{item.explanation}</Typography.Paragraph>
              <Typography.Text type="secondary">{item.sourceVersion} · {item.visibleCount}</Typography.Text>
            </Card>
          </List.Item>
        )}
      />
      {canManage ? (
        <Form<Editor> form={form} layout="vertical" initialValues={initial} onFinish={values => online && save.mutate(values)}>
          <Row gutter={12}>
            <Col xs={24} sm={12}><Form.Item name="name" label="报表名称" rules={[{ required: true }]}><Input aria-label="治理报表名称" /></Form.Item></Col>
            <Col xs={24} sm={12}><Form.Item name="reportKey" label="永久 Key" rules={[{ required: true }]}><Input aria-label="治理报表永久 Key" disabled={Boolean(selected)} /></Form.Item></Col>
            <Col span={24}><Form.Item name="description" label="脱敏与来源说明" rules={[{ required: true }]}><Input.TextArea aria-label="治理报表脱敏与来源说明" /></Form.Item></Col>
            <Col span={24}><Form.Item name="sections" label="报表区块"><Select mode="multiple" options={initial.sections.map(value => ({ value }))} /></Form.Item></Col>
          </Row>
          <Space wrap>
            <Button type="primary" htmlType="submit" disabled={!online} loading={save.isPending}>保存发布定义</Button>
            <Button icon={<PlayCircleOutlined />} disabled={!online || !selected} loading={run.isPending} onClick={() => run.mutate()}>运行当前受权报表</Button>
          </Space>
        </Form>
      ) : <Alert type="info" showIcon message="当前为只读治理视图；只有 owner/admin 可运行或导出报表" />}
      <List
        aria-label="治理报表定义"
        dataSource={query.data?.reports ?? []}
        renderItem={item => (
          <List.Item>
            <Button
              type="text"
              block
              onClick={() => {
                setSelected(item)
                form.setFieldsValue({
                  reportKey: item.reportKey,
                  name: item.name,
                  description: item.description,
                  sections: item.sections,
                })
              }}
            >
              <Space wrap><strong>{item.name}</strong><Tag>{item.status}</Tag><span>v{item.version}</span></Space>
            </Button>
          </List.Item>
        )}
      />
      <List
        header={<Typography.Title level={5}><AuditOutlined /> 审计运行</Typography.Title>}
        dataSource={query.data?.runs ?? []}
        renderItem={item => (
          <List.Item
            actions={canManage ? [(
              <Button
                key="export"
                icon={<DownloadOutlined />}
                disabled={!online}
                onClick={() => exportRun.mutate(item.id)}
              >
                逐行重校准导出
              </Button>
            )] : []}
          >
            <List.Item.Meta
              title={`${item.status} · ${item.sourceFingerprint.slice(0, 16)}`}
              description={`${formatTime(item.completedAt)} · report v${item.reportVersion}`}
            />
          </List.Item>
        )}
      />
      <Typography.Paragraph type="secondary">
        报表与导出只包含当前受权治理元数据；隐藏内容不进入字段、数量、文件名、facet 或错误外形。{overview?.diagnostic}
      </Typography.Paragraph>
    </Card>
  )
}
