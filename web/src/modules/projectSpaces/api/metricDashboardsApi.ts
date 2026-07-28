import { apiGet, apiPost } from '../../../shared/api/httpClient'

export type DashboardSource = {
  schemaVersion: 1
  bindingKey: string
  kind: 'work_item_query' | 'saved_view' | 'cross_space_panorama'
  spaceIds: string[]
  savedViewId?: string
  metricId: string
  metricVersion: number
}

export type DashboardChart = {
  id?: string
  chartKey: string
  name: string
  visualization: 'table' | 'metric_card' | 'line' | 'bar' | 'stacked_bar' | 'distribution'
  bindingKey: string
  metricId: string
  metricVersion: number
  dimensionKeys: string[]
  filters: Record<string, string>
  seriesLimit: number
  pointLimit: number
  drilldown: boolean
  version: number
}

export type DashboardConfig = {
  schemaVersion: 1
  dataSources: DashboardSource[]
  charts: DashboardChart[]
  layout: Array<{
    chartKey: string
    column: number
    row: number
    width: number
    height: number
  }>
  filters: Array<{
    key: string
    dimensionKey: string
    operator: 'eq' | 'in'
    values: string[]
  }>
}

export type MetricDashboard = {
  id: string
  dashboardKey: string
  name: string
  description: string
  status: 'draft' | 'active' | 'disabled' | 'archived'
  sharingScope: 'private' | 'space'
  version: number
  draftConfig: DashboardConfig
  publishedVersion?: {
    id: string
    versionNumber: number
    definitionHash: string
    config: DashboardConfig
    publishedAt: string
  }
  updatedAt: string
}

export type DashboardFoundation = {
  schemaVersion: number
  dashboards: MetricDashboard[]
  visualizations: DashboardChart['visualization'][]
  sourceKinds: DashboardSource['kind'][]
  resultStatuses: string[]
  truncated: boolean
  budgets: Record<string, number>
}

export type DashboardQueryResult = {
  schemaVersion: number
  dashboardId: string
  dashboardVersion: number
  status: string
  stale: boolean
  truncated: boolean
  observedAt: string
  diagnostic: string
  charts: Array<{
    chartKey: string
    name: string
    visualization: DashboardChart['visualization']
    status: string
    unit: string
    visibleSampleCount: number
    stale: boolean
    truncated: boolean
    sourceVersions: string[]
    diagnostic: string
    facets: string[]
    series: Array<{
      key: string
      label: string
      points: Array<{
        key: string
        label: string
        value?: number
        numerator?: number
        denominator?: number
        sampleCount: number
        drilldownKey?: string
      }>
    }>
  }>
}

export const dashboardKeys = {
  foundation: (spaceId: string) => ['project-spaces', spaceId, 'metric-dashboards'] as const,
}

export function getDashboardFoundation(spaceId: string) {
  return apiGet<DashboardFoundation>(`/project-spaces/${spaceId}/metric-dashboards`)
}

export function saveDashboard(
  spaceId: string,
  input: {
    dashboardId?: string
    expectedVersion: number
    dashboardKey: string
    name: string
    description: string
    config: DashboardConfig
  },
) {
  const requestId = crypto.randomUUID()
  return apiPost<MetricDashboard>(
    `/project-spaces/${spaceId}/metric-dashboards`,
    { schemaVersion: 1, requestId, ...input },
    { requestId },
  )
}

export function publishDashboard(spaceId: string, dashboard: MetricDashboard) {
  const requestId = crypto.randomUUID()
  return apiPost<MetricDashboard['publishedVersion']>(
    `/project-spaces/${spaceId}/metric-dashboards/${dashboard.id}/publish`,
    { schemaVersion: 1, requestId, expectedVersion: dashboard.version, action: 'publish' },
    { requestId },
  )
}

export function changeDashboard(
  spaceId: string,
  dashboard: MetricDashboard,
  action: 'share' | 'unshare' | 'disable' | 'revise' | 'archive',
) {
  const requestId = crypto.randomUUID()
  return apiPost<MetricDashboard>(
    `/project-spaces/${spaceId}/metric-dashboards/${dashboard.id}/lifecycle`,
    { schemaVersion: 1, requestId, expectedVersion: dashboard.version, action },
    { requestId },
  )
}

export function queryDashboard(spaceId: string, dashboardId: string, filterValues = {}) {
  return apiPost<DashboardQueryResult>(
    `/project-spaces/${spaceId}/metric-dashboards/${dashboardId}/query`,
    { schemaVersion: 1, anchor: new Date().toISOString(), filterValues },
  )
}
