import {
  AppstoreOutlined,
  CloudSyncOutlined,
  DashboardOutlined,
  ShareAltOutlined,
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
  Progress,
  Row,
  Select,
  Space,
  Statistic,
  Switch,
  Tag,
  Typography,
} from 'antd'
import { useEffect, useMemo, useState } from 'react'

import { useRealtimeSubscription } from '../../../shared/realtime'
import { useSessionScope } from '../../../shared/session/SessionScopeContext'
import {
  changeDashboard,
  dashboardKeys,
  getDashboardFoundation,
  publishDashboard,
  queryDashboard,
  saveDashboard,
  type DashboardChart,
  type DashboardConfig,
  type DashboardQueryResult,
  type DashboardSource,
  type MetricDashboard,
} from '../api/metricDashboardsApi'
import { getMetricFoundation, metricKeys, type MetricDefinition } from '../api/metricSemanticsApi'
import type { UserProjectSpace } from '../api/projectSpacesApi'
import {
  hasRecoverableLegacyDraft,
  isProjectSpaceDraftRecord,
  markLegacyDraftHandled,
  projectSpaceCacheKey,
  readProjectSpaceDraft,
  recoverLegacyProjectSpaceDraft,
  removeProjectSpaceDraft,
  writeProjectSpaceDraft,
} from '../projectSpaceLocalCache'
import { formatTime } from '../projectSpaceView'

type Editor = {
  dashboardKey: string
  name: string
  description: string
  chartKey: string
  chartName: string
  visualization: DashboardChart['visualization']
  sourceKind: DashboardSource['kind']
  sourceSpaceIds: string
  savedViewId?: string
  metricId?: string
  dimensionKeys: string[]
  drilldown: boolean
}

function defaultEditor(spaceId: string): Editor {
  return {
    dashboardKey: 'delivery.overview',
    name: '交付管理看板',
    description: '只展示当前受权且来源可解释的指标。',
    chartKey: 'work_item.status',
    chartName: '工作项状态分布',
    visualization: 'bar',
    sourceKind: 'work_item_query',
    sourceSpaceIds: spaceId,
    dimensionKeys: ['status'],
    drilldown: true,
  }
}

function isMetricDashboardEditor(value: unknown): value is Editor {
  if (!isProjectSpaceDraftRecord(value)) return false
  return typeof value.dashboardKey === 'string'
    && typeof value.name === 'string'
    && typeof value.description === 'string'
    && typeof value.chartKey === 'string'
    && typeof value.chartName === 'string'
    && typeof value.visualization === 'string'
    && typeof value.sourceKind === 'string'
    && typeof value.sourceSpaceIds === 'string'
    && Array.isArray(value.dimensionKeys)
    && value.dimensionKeys.every((item) => typeof item === 'string')
    && typeof value.drilldown === 'boolean'
}

export function MetricDashboardsPanel({ space }: { space: UserProjectSpace }) {
  const client = useQueryClient()
  const sessionScope = useSessionScope()
  const canManage = space.availableActions.includes('view_settings')
  const legacyDraftKey = `colla.metric-dashboard-draft.${space.id}`
  const legacyLayoutKey = `colla.metric-dashboard-layout.${space.id}`
  const draftScope = useMemo(
    () => sessionScope
      ? {
          workspaceId: sessionScope.workspaceId,
          userId: sessionScope.userId,
          spaceId: space.id,
        }
      : null,
    [sessionScope, space.id],
  )
  const draftKey = draftScope
    ? projectSpaceCacheKey(draftScope, 'metric-dashboard-draft')
    : null
  const [form] = Form.useForm<Editor>()
  const [selected, setSelected] = useState<MetricDashboard>()
  const [result, setResult] = useState<DashboardQueryResult>()
  const [online, setOnline] = useState(() => navigator.onLine)
  const [handledLegacyDraftKey, setHandledLegacyDraftKey] = useState<string | null>(null)
  const foundation = useQuery({
    queryKey: dashboardKeys.foundation(space.id),
    queryFn: () => getDashboardFoundation(space.id),
    retry: false,
  })
  const metrics = useQuery({
    queryKey: metricKeys.foundation(space.id),
    queryFn: () => getMetricFoundation(space.id),
    retry: false,
  })
  const save = useMutation({
    mutationFn: (values: Editor) => {
      const metric = metrics.data?.metrics.find(value => value.id === values.metricId)
      if (!metric?.publishedVersion) throw new Error('请选择已发布指标')
      return saveDashboard(space.id, {
        dashboardId: selected?.id,
        expectedVersion: selected?.version ?? 0,
        dashboardKey: values.dashboardKey,
        name: values.name,
        description: values.description,
        config: config(space.id, values, metric, selected),
      })
    },
    onSuccess: async (dashboard) => {
      if (draftScope) {
        removeProjectSpaceDraft(localStorage, draftScope, 'metric-dashboard-draft')
        markLegacyDraftHandled(localStorage, draftScope, 'metric-dashboard-draft')
      }
      setSelected(dashboard)
      setResult(undefined)
      await client.invalidateQueries({ queryKey: dashboardKeys.foundation(space.id) })
    },
  })
  const publish = useMutation({
    mutationFn: () => publishDashboard(space.id, selected!),
    onSuccess: async () => {
      await refresh()
    },
  })
  const share = useMutation({
    mutationFn: (action: 'share' | 'unshare') => changeDashboard(space.id, selected!, action),
    onSuccess: async (dashboard) => {
      setSelected(dashboard)
      await client.invalidateQueries({ queryKey: dashboardKeys.foundation(space.id) })
    },
  })
  const execute = useMutation({
    mutationFn: () => queryDashboard(space.id, selected!.id),
    onSuccess: setResult,
  })

  const refresh = async () => {
    const current = await client.fetchQuery({
      queryKey: dashboardKeys.foundation(space.id),
      queryFn: () => getDashboardFoundation(space.id),
    })
    const updated = current.dashboards.find(value => value.id === selected?.id)
    if (updated) setSelected(updated)
  }

  useRealtimeSubscription(['project_space.changed'], (signal) => {
    if (signal.objectType === 'project_space' && signal.objectId === space.id) {
      setResult(undefined)
      void client.invalidateQueries({
        queryKey: dashboardKeys.foundation(space.id),
        exact: true,
        refetchType: 'active',
      })
    }
  })

  useEffect(() => {
    const calibrate = () => {
      setOnline(navigator.onLine)
      setResult(undefined)
      if (navigator.onLine) {
        void client.invalidateQueries({ queryKey: dashboardKeys.foundation(space.id) })
      }
    }
    const storage = (event: StorageEvent) => {
      if (event.key === draftKey && draftScope) {
        const draft = readProjectSpaceDraft(
          localStorage,
          draftScope,
          'metric-dashboard-draft',
          isMetricDashboardEditor,
        )
        if (draft) form.setFieldsValue(draft)
      }
      if (event.key === legacyLayoutKey) setResult(undefined)
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
  }, [client, draftKey, draftScope, form, legacyLayoutKey, space.id])

  const initial = useMemo(() => {
    if (!draftScope) return defaultEditor(space.id)
    return readProjectSpaceDraft(
      localStorage,
      draftScope,
      'metric-dashboard-draft',
      isMetricDashboardEditor,
    ) ?? defaultEditor(space.id)
  }, [draftScope, space.id])
  const legacyDraftAvailable = Boolean(
    draftScope
    && handledLegacyDraftKey !== draftKey
    && hasRecoverableLegacyDraft(
      localStorage,
      draftScope,
      'metric-dashboard-draft',
      legacyDraftKey,
      isMetricDashboardEditor,
    ),
  )
  const recoverLegacyDraft = () => {
    if (!draftScope) return
    const recovered = recoverLegacyProjectSpaceDraft(
      localStorage,
      draftScope,
      'metric-dashboard-draft',
      legacyDraftKey,
      isMetricDashboardEditor,
    )
    if (recovered) form.setFieldsValue(recovered)
    setHandledLegacyDraftKey(draftKey)
  }

  const selectDashboard = (dashboard: MetricDashboard) => {
    setSelected(dashboard)
    setResult(undefined)
    const chart = dashboard.draftConfig.charts[0]
    const source = dashboard.draftConfig.dataSources[0]
    form.setFieldsValue({
      dashboardKey: dashboard.dashboardKey,
      name: dashboard.name,
      description: dashboard.description,
      chartKey: chart?.chartKey,
      chartName: chart?.name,
      visualization: chart?.visualization,
      sourceKind: source?.kind,
      sourceSpaceIds: source?.spaceIds.join(','),
      savedViewId: source?.savedViewId,
      metricId: source?.metricId,
      dimensionKeys: chart?.dimensionKeys,
      drilldown: chart?.drilldown,
    })
  }

  return (
    <Card
      className="content-card metric-dashboards-panel"
      data-testid="metric-dashboards-panel"
      title={<Space><DashboardOutlined />图表、看板与跨空间数据源</Space>}
      extra={(
        <Tag icon={<CloudSyncOutlined />} color={online ? 'green' : 'orange'}>
          {online ? 'REST 已校准' : '离线布局'}
        </Tag>
      )}
    >
      {!online ? (
        <Alert
          type="warning"
          showIcon
          message="当前离线：仅保留本地设计和布局，不伪造保存、分享或查询成功"
        />
      ) : null}
      {canManage && legacyDraftAvailable ? (
        <Alert
          type="info"
          showIcon
          message="检测到旧版本机草稿"
          description="旧草稿未自动跨账号载入；确认属于当前账号后可恢复。"
          action={<Button onClick={recoverLegacyDraft}>恢复旧草稿</Button>}
        />
      ) : null}
      {foundation.isError ? (
        <Alert type="error" showIcon message="看板目录加载失败或无权访问" />
      ) : null}
      {foundation.data?.truncated ? (
        <Alert type="warning" showIcon message="看板目录已截断；当前目录不代表完整事实" />
      ) : null}
      <Row gutter={[16, 16]}>
        <Col xs={24} md={7}>
          <List
            aria-label="管理看板目录"
            loading={foundation.isLoading}
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无管理看板" /> }}
            dataSource={foundation.data?.dashboards ?? []}
            renderItem={dashboard => (
              <List.Item>
                <Button
                  type="text"
                  block
                  className="dashboard-catalog-item"
                  onClick={() => selectDashboard(dashboard)}
                >
                  <Space wrap>
                    <strong>{dashboard.name}</strong>
                    <Tag>{dashboard.status}</Tag>
                    <Tag color={dashboard.sharingScope === 'space' ? 'blue' : 'default'}>
                      {dashboard.sharingScope === 'space' ? '空间共享' : '私有配置'}
                    </Tag>
                  </Space>
                </Button>
              </List.Item>
            )}
          />
          <Typography.Paragraph type="secondary">
            分享只引用配置；指标结果、缓存和跨空间 grant 不会被复制。
          </Typography.Paragraph>
        </Col>
        <Col xs={24} md={17}>
          {canManage ? (
            <Form<Editor>
              form={form}
              layout="vertical"
              initialValues={initial}
              onValuesChange={(_, values) => {
                if (draftScope) {
                  writeProjectSpaceDraft(
                    localStorage,
                    draftScope,
                    'metric-dashboard-draft',
                    values,
                  )
                }
              }}
              onFinish={values => online && save.mutate(values)}
            >
              <Row gutter={12}>
                <Col xs={24} sm={12}>
                  <Form.Item name="name" label="看板名称" rules={[{ required: true }, { max: 160 }]}>
                    <Input aria-label="看板名称" />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item name="dashboardKey" label="永久 Key" rules={[{ required: true }, { pattern: /^[a-z][a-z0-9_.-]{1,63}$/ }]}>
                    <Input aria-label="看板永久 Key" disabled={Boolean(selected)} />
                  </Form.Item>
                </Col>
                <Col span={24}>
                  <Form.Item name="description" label="治理说明" rules={[{ required: true }, { max: 2000 }]}>
                    <Input.TextArea aria-label="看板治理说明" rows={2} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item name="chartName" label="图表名称" rules={[{ required: true }]}>
                    <Input aria-label="图表名称" />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item name="chartKey" label="图表 Key" rules={[{ required: true }, { pattern: /^[a-z][a-z0-9_.-]{1,63}$/ }]}>
                    <Input aria-label="图表 Key" />
                  </Form.Item>
                </Col>
                <Col xs={12} sm={8}>
                  <Form.Item name="visualization" label="可视化">
                    <Select options={foundation.data?.visualizations.map(value => ({ value }))} />
                  </Form.Item>
                </Col>
                <Col xs={12} sm={8}>
                  <Form.Item name="sourceKind" label="数据源">
                    <Select options={foundation.data?.sourceKinds.map(value => ({ value }))} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={8}>
                  <Form.Item name="metricId" label="不可变指标版本" rules={[{ required: true }]}>
                    <Select
                      options={metrics.data?.metrics
                        .filter(value => value.publishedVersion)
                        .map(value => ({
                          value: value.id,
                          label: `${value.name} · v${value.publishedVersion?.versionNumber}`,
                        }))}
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item name="sourceSpaceIds" label="来源空间 ID（逗号分隔）" rules={[{ required: true }]}>
                    <Input aria-label="来源空间 ID（逗号分隔）" />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item name="savedViewId" label="保存视图 ID（仅 saved_view）">
                    <Input aria-label="保存视图 ID（仅 saved_view）" />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={18}>
                  <Form.Item name="dimensionKeys" label="Series / Point 维度">
                    <Select
                      mode="multiple"
                      maxCount={2}
                      options={metrics.data?.dimensions.map(value => ({
                        value: value.key,
                        label: `${value.label} · v${value.version}`,
                      }))}
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={6}>
                  <Form.Item name="drilldown" label="安全钻取" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Col>
              </Row>
              <Space wrap>
                <Button type="primary" htmlType="submit" disabled={!online} loading={save.isPending}>
                  保存草稿
                </Button>
                <Button disabled={!online || !selected} loading={publish.isPending} onClick={() => publish.mutate()}>
                  发布不可变版本
                </Button>
                <Button
                  icon={<ShareAltOutlined />}
                  data-testid="dashboard-share-action"
                  disabled={!online || !selected?.publishedVersion}
                  loading={share.isPending}
                  onClick={() => share.mutate(selected?.sharingScope === 'space' ? 'unshare' : 'share')}
                >
                  {selected?.sharingScope === 'space' ? '撤销共享' : '空间共享'}
                </Button>
                <Button
                  icon={<AppstoreOutlined />}
                  disabled={!online || !selected?.publishedVersion}
                  loading={execute.isPending}
                  onClick={() => execute.mutate()}
                >
                  REST 重新计算
                </Button>
                <Button onClick={() => {
                  setSelected(undefined)
                  setResult(undefined)
                  form.setFieldsValue(defaultEditor(space.id))
                }}>
                  新建看板
                </Button>
              </Space>
            </Form>
          ) : (
            <Alert
              type="info"
              showIcon
              message="当前为只读管理看板；只有空间 owner/admin 可编辑、发布和分享"
            />
          )}
          {selected?.publishedVersion ? (
            <Typography.Paragraph className="dashboard-version-explain" type="secondary">
              发布版本 v{selected.publishedVersion.versionNumber} ·
              指纹 {selected.publishedVersion.definitionHash.slice(0, 16)} ·
              {formatTime(selected.publishedVersion.publishedAt)}
            </Typography.Paragraph>
          ) : null}
          {result ? <DashboardResultView result={result} /> : null}
        </Col>
      </Row>
    </Card>
  )
}

function DashboardResultView({ result }: { result: DashboardQueryResult }) {
  return (
    <section className="metric-dashboard-results" aria-label="当前受权看板结果">
      <Alert
        showIcon
        type={result.status === 'ready' ? 'success' : 'warning'}
        message={`看板结果：${result.status}`}
        description={`${formatTime(result.observedAt)} · ${result.diagnostic}`}
      />
      <div className="metric-dashboard-grid">
        {result.charts.map(chart => (
          <Card
            key={chart.chartKey}
            size="small"
            title={<Space wrap><span>{chart.name}</span><Tag>{chart.visualization}</Tag></Space>}
            extra={<Tag color={chart.status === 'ready' ? 'green' : 'orange'}>{chart.status}</Tag>}
          >
            {chart.status !== 'ready' ? (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="证据不完整：series、facet、数量和来源版本已隐藏"
              />
            ) : chart.visualization === 'metric_card' ? (
              <Statistic
                value={chart.series[0]?.points[0]?.value}
                suffix={chart.unit}
                title={`${chart.visibleSampleCount} 个当前受权样本`}
              />
            ) : (
              <div className="metric-chart-series">
                {chart.series.flatMap(series => series.points.map(point => (
                  <div className="metric-chart-row" key={`${series.key}:${point.key}`}>
                    <Typography.Text ellipsis title={`${series.label} / ${point.label}`}>
                      {series.label} / {point.label}
                    </Typography.Text>
                    <Progress
                      percent={Math.min(100, Math.max(0, Number(point.value ?? 0)))}
                      format={() => point.value ?? 'unknown'}
                    />
                    {point.drilldownKey ? <Tag>可安全钻取</Tag> : null}
                  </div>
                )))}
              </div>
            )}
            <Typography.Paragraph type="secondary">
              freshness：{chart.stale ? 'stale' : 'current'} ·
              truncation：{chart.truncated ? 'truncated' : 'complete'} ·
              {chart.diagnostic}
            </Typography.Paragraph>
          </Card>
        ))}
      </div>
    </section>
  )
}

function config(
  spaceId: string,
  values: Editor,
  metric: MetricDefinition,
  dashboard?: MetricDashboard,
): DashboardConfig {
  const version = metric.publishedVersion!.versionNumber
  const bindingKey = 'primary.source'
  const chart: DashboardChart = {
    id: dashboard?.draftConfig.charts[0]?.id,
    chartKey: values.chartKey,
    name: values.chartName,
    visualization: values.visualization,
    bindingKey,
    metricId: metric.id,
    metricVersion: version,
    dimensionKeys: values.dimensionKeys ?? [],
    filters: {},
    seriesLimit: 12,
    pointLimit: 50,
    drilldown: values.drilldown,
    version: dashboard?.draftConfig.charts[0]?.version ?? 0,
  }
  const spaceIds = values.sourceSpaceIds
    .split(',')
    .map(value => value.trim())
    .filter(Boolean)
  if (!spaceIds.includes(spaceId)) spaceIds.unshift(spaceId)
  return {
    schemaVersion: 1,
    dataSources: [{
      schemaVersion: 1,
      bindingKey,
      kind: values.sourceKind,
      spaceIds,
      savedViewId: values.sourceKind === 'saved_view' ? values.savedViewId : undefined,
      metricId: metric.id,
      metricVersion: version,
    }],
    charts: [chart],
    layout: [{ chartKey: chart.chartKey, column: 0, row: 0, width: 12, height: 5 }],
    filters: [],
  }
}
