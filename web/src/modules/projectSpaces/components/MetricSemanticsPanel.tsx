import { BarChartOutlined, CloudSyncOutlined, DiffOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
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
import { useEffect, useMemo, useState } from 'react'

import { useRealtimeSubscription } from '../../../shared/realtime'
import {
  getMetricFoundation,
  metricKeys,
  previewMetric,
  publishMetric,
  saveMetric,
  type MetricDefinition,
  type MetricExpression,
  type MetricResult,
  type MetricWindow,
} from '../api/metricSemanticsApi'
import type { UserProjectSpace } from '../api/projectSpacesApi'
import { formatTime } from '../projectSpaceView'

type Editor = {
  metricKey: string
  name: string
  description: string
  unit: MetricDefinition['unit']
  aggregation: MetricExpression['aggregation']
  measureKey: string
  numeratorMeasureKey?: string
  denominatorMeasureKey?: string
  dimensionKeys: string[]
  windowKind: MetricWindow['kind']
  windowAmount: number
  windowUnit: MetricWindow['unit']
  timeZone: string
  comparison: MetricWindow['comparison']
}

const defaultEditor: Editor = {
  metricKey: 'work_item.total',
  name: '工作项总量',
  description: '当前受权工作项的有界数量。',
  unit: 'count',
  aggregation: 'count',
  measureKey: 'work_item.count',
  dimensionKeys: ['status'],
  windowKind: 'rolling',
  windowAmount: 30,
  windowUnit: 'day',
  timeZone: 'Asia/Shanghai',
  comparison: 'previous_period',
}

export function MetricSemanticsPanel({ space }: { space: UserProjectSpace }) {
  const client = useQueryClient()
  const canManage = space.currentUserRole === 'owner' || space.currentUserRole === 'admin'
  const draftKey = `colla.metric-draft.${space.id}`
  const [form] = Form.useForm<Editor>()
  const [selected, setSelected] = useState<MetricDefinition>()
  const [preview, setPreview] = useState<MetricResult>()
  const [online, setOnline] = useState(() => navigator.onLine)
  const query = useQuery({
    queryKey: metricKeys.foundation(space.id),
    queryFn: () => getMetricFoundation(space.id),
    retry: false,
  })
  const save = useMutation({
    mutationFn: (values: Editor) => saveMetric(space.id, {
      metricId: selected?.id,
      expectedVersion: selected?.version ?? 0,
      metricKey: values.metricKey,
      name: values.name,
      description: values.description,
      unit: values.unit,
      expression: {
        schemaVersion: 1,
        aggregation: values.aggregation,
        measureKey: values.aggregation === 'ratio' ? undefined : values.measureKey,
        numeratorMeasureKey: values.aggregation === 'ratio' ? values.numeratorMeasureKey : undefined,
        denominatorMeasureKey: values.aggregation === 'ratio' ? values.denominatorMeasureKey : undefined,
        dimensionKeys: values.dimensionKeys ?? [],
      },
      window: {
        schemaVersion: 1,
        kind: values.windowKind,
        amount: values.windowAmount,
        unit: values.windowUnit,
        timeZone: values.timeZone,
        calendarKey: 'iso8601',
        comparison: values.comparison,
      },
    }),
    onSuccess: async (metric) => {
      localStorage.removeItem(draftKey)
      setSelected(metric)
      await client.invalidateQueries({ queryKey: metricKeys.foundation(space.id) })
    },
  })
  const publish = useMutation({
    mutationFn: () => publishMetric(space.id, selected!),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: metricKeys.foundation(space.id) })
    },
  })
  const previewMutation = useMutation({
    mutationFn: () => previewMetric(space.id, selected!),
    onSuccess: setPreview,
  })

  useRealtimeSubscription(['project_space.changed'], (signal) => {
    if (signal.objectType === 'project_space' && signal.objectId === space.id) {
      void client.invalidateQueries({
        queryKey: metricKeys.foundation(space.id),
        exact: true,
        refetchType: 'active',
      })
    }
  })

  useEffect(() => {
    const calibrate = () => {
      setOnline(navigator.onLine)
      if (navigator.onLine) void client.invalidateQueries({ queryKey: metricKeys.foundation(space.id) })
    }
    const storage = (event: StorageEvent) => {
      if (event.key === draftKey && event.newValue) form.setFieldsValue(JSON.parse(event.newValue) as Editor)
      calibrate()
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
  }, [client, draftKey, form, space.id])

  const initial = useMemo(() => {
    try {
      return JSON.parse(localStorage.getItem(draftKey) ?? '') as Editor
    } catch {
      return defaultEditor
    }
  }, [draftKey])
  const selectMetric = (metric: MetricDefinition) => {
    setSelected(metric)
    setPreview(undefined)
    form.setFieldsValue({
      metricKey: metric.metricKey,
      name: metric.name,
      description: metric.description,
      unit: metric.unit,
      aggregation: metric.draftExpression.aggregation,
      measureKey: metric.draftExpression.measureKey ?? 'work_item.count',
      numeratorMeasureKey: metric.draftExpression.numeratorMeasureKey,
      denominatorMeasureKey: metric.draftExpression.denominatorMeasureKey,
      dimensionKeys: metric.draftExpression.dimensionKeys,
      windowKind: metric.draftWindow.kind,
      windowAmount: metric.draftWindow.amount,
      windowUnit: metric.draftWindow.unit,
      timeZone: metric.draftWindow.timeZone,
      comparison: metric.draftWindow.comparison,
    })
  }
  const changed = selected?.publishedVersion
    ? JSON.stringify(selected.publishedVersion.expression) !== JSON.stringify(selected.draftExpression)
      || JSON.stringify(selected.publishedVersion.window) !== JSON.stringify(selected.draftWindow)
    : true

  return (
    <Card
      className="content-card metric-semantics-panel"
      data-testid="metric-semantics-panel"
      title={<Space><BarChartOutlined />指标语义、维度与时间窗口</Space>}
      extra={<Tag icon={<CloudSyncOutlined />} color={online ? 'green' : 'orange'}>{online ? 'REST 已校准' : '离线草稿'}</Tag>}
    >
      {!online ? <Alert type="warning" showIcon message="当前离线：只保存本地草稿，不发布或伪造指标结果" /> : null}
      {query.isError ? <Alert type="error" showIcon message="指标目录加载失败或无权访问" /> : null}
      {query.data?.truncated ? <Alert type="warning" showIcon message="指标目录已截断；截断不折算为完整结果" /> : null}
      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}>
          <List
            aria-label="指标目录"
            loading={query.isLoading}
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无指标定义" /> }}
            dataSource={query.data?.metrics ?? []}
            renderItem={(metric) => (
              <List.Item>
                <Button type="text" block className="metric-catalog-item" onClick={() => selectMetric(metric)}>
                  <Space wrap><strong>{metric.name}</strong><Tag>{metric.status}</Tag><span>v{metric.version}</span></Space>
                </Button>
              </List.Item>
            )}
          />
          <Typography.Paragraph type="secondary">
            结果状态：{query.data?.resultStatuses.join(' / ') || 'loading'}。unknown、suppressed、stale、truncated 与 no_sample 均不会显示为 0。
          </Typography.Paragraph>
        </Col>
        <Col xs={24} md={16}>
          {canManage ? (
            <Form<Editor>
              form={form}
              layout="vertical"
              initialValues={initial}
              onValuesChange={(_, values) => localStorage.setItem(draftKey, JSON.stringify(values))}
              onFinish={(values) => online && save.mutate(values)}
            >
              <Row gutter={12}>
                <Col xs={24} sm={12}><Form.Item name="name" label="指标名称" rules={[{ required: true }, { max: 160 }]}><Input /></Form.Item></Col>
                <Col xs={24} sm={12}><Form.Item name="metricKey" label="永久 Key" rules={[{ required: true }, { pattern: /^[a-z][a-z0-9_.-]{1,63}$/ }]}><Input disabled={Boolean(selected)} /></Form.Item></Col>
                <Col span={24}><Form.Item name="description" label="语义说明" rules={[{ required: true }, { max: 2000 }]}><Input.TextArea rows={2} /></Form.Item></Col>
                <Col xs={12} sm={6}><Form.Item name="aggregation" label="聚合"><Select options={['count', 'sum', 'average', 'ratio'].map(value => ({ value }))} /></Form.Item></Col>
                <Col xs={12} sm={6}><Form.Item name="unit" label="单位"><Select options={['count', 'hours', 'days', 'percent', 'points'].map(value => ({ value }))} /></Form.Item></Col>
                <Col xs={24} sm={12}><Form.Item name="measureKey" label="Measure"><Select options={query.data?.measures.map(value => ({ value: value.key, label: `${value.label} · ${value.sourceContract}` }))} /></Form.Item></Col>
                <Col span={24}><Form.Item name="dimensionKeys" label="维度（最多 4 个）"><Select mode="multiple" maxCount={4} options={query.data?.dimensions.map(value => ({ value: value.key, label: `${value.label} v${value.version}` }))} /></Form.Item></Col>
                <Col xs={12} sm={6}><Form.Item name="windowKind" label="窗口"><Select options={['rolling', 'fixed'].map(value => ({ value }))} /></Form.Item></Col>
                <Col xs={12} sm={6}><Form.Item name="windowAmount" label="长度"><InputNumber min={1} max={366} style={{ width: '100%' }} /></Form.Item></Col>
                <Col xs={12} sm={6}><Form.Item name="windowUnit" label="粒度"><Select options={['day', 'week', 'month'].map(value => ({ value }))} /></Form.Item></Col>
                <Col xs={12} sm={6}><Form.Item name="timeZone" label="IANA 时区"><Input /></Form.Item></Col>
                <Col span={24}><Form.Item name="comparison" label="比较区间"><Select options={[{ value: 'none' }, { value: 'previous_period' }]} /></Form.Item></Col>
              </Row>
              <Space wrap>
                <Button type="primary" htmlType="submit" disabled={!online} loading={save.isPending}>保存草稿</Button>
                <Button disabled={!online || !selected || !changed} loading={publish.isPending} onClick={() => publish.mutate()}>发布不可变版本</Button>
                <Button disabled={!online || !selected} loading={previewMutation.isPending} onClick={() => previewMutation.mutate()}>窗口预览</Button>
                <Button onClick={() => { setSelected(undefined); form.setFieldsValue(defaultEditor) }}>新建指标</Button>
              </Space>
            </Form>
          ) : <Alert type="info" showIcon message="当前为只读指标目录；只有空间 owner/admin 可编辑和发布" />}
          {selected?.publishedVersion ? (
            <Descriptions
              className="metric-version-diff"
              title={<Space><DiffOutlined />版本 diff 与来源解释</Space>}
              bordered
              size="small"
              column={{ xs: 1, sm: 2 }}
              items={[
                { key: 'version', label: '已发布', children: `v${selected.publishedVersion.versionNumber} · ${formatTime(selected.publishedVersion.publishedAt)}` },
                { key: 'hash', label: '定义指纹', children: selected.publishedVersion.definitionHash.slice(0, 16) },
                { key: 'changed', label: '草稿差异', children: changed ? '表达式或窗口已变更' : '无差异' },
                { key: 'source', label: '公开来源', children: query.data?.measures.find(value => value.key === selected.draftExpression.measureKey)?.sourceContract ?? 'ratio sources' },
              ]}
            />
          ) : null}
          {preview ? (
            <Alert
              showIcon
              type={preview.status === 'ready' ? 'success' : 'info'}
              message={`预览状态：${preview.status}`}
              description={`${preview.window.startInclusive} → ${preview.window.endExclusive} · ${preview.diagnostic}`}
            />
          ) : null}
        </Col>
      </Row>
    </Card>
  )
}
