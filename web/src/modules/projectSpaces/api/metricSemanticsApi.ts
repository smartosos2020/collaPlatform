import { apiGet, apiPost } from '../../../shared/api/httpClient'

export type MetricExpression = {
  schemaVersion: 1
  aggregation: 'count' | 'sum' | 'average' | 'ratio'
  measureKey?: string
  numeratorMeasureKey?: string
  denominatorMeasureKey?: string
  dimensionKeys: string[]
}

export type MetricWindow = {
  schemaVersion: 1
  kind: 'rolling' | 'fixed'
  amount: number
  unit: 'day' | 'week' | 'month'
  timeZone: string
  calendarKey: 'iso8601'
  comparison: 'none' | 'previous_period'
}

export type MetricDefinition = {
  id: string
  metricKey: string
  name: string
  description: string
  unit: 'count' | 'hours' | 'days' | 'percent' | 'points'
  status: 'draft' | 'active' | 'disabled' | 'archived'
  version: number
  draftExpression: MetricExpression
  draftWindow: MetricWindow
  publishedVersion?: {
    id: string
    versionNumber: number
    definitionHash: string
    expression: MetricExpression
    window: MetricWindow
    publishedAt: string
  }
  updatedAt: string
}

export type MetricFoundation = {
  schemaVersion: number
  measures: Array<{
    key: string
    label: string
    valueType: string
    unit: string
    sourceContract: string
    nullable: boolean
  }>
  dimensions: Array<{
    key: string
    version: number
    label: string
    valueType: string
    sourceContract: string
    cardinalityLimit: number
  }>
  metrics: MetricDefinition[]
  truncated: boolean
  resultStatuses: string[]
  prohibitedCapabilities: string[]
}

export type MetricResult = {
  schemaVersion: number
  metricId: string
  metricVersion?: number
  status: 'ready' | 'unknown' | 'no_sample' | 'suppressed' | 'stale' | 'truncated'
  value?: number
  numerator?: number
  denominator?: number
  unit: string
  window: {
    startInclusive: string
    endExclusive: string
    comparisonStartInclusive?: string
    comparisonEndExclusive?: string
    timeZone: string
    diagnostic: string
  }
  sampleCount: number
  diagnostic: string
  sources: Array<{ sourceContract: string; diagnostic: string }>
}

export const metricKeys = {
  foundation: (spaceId: string) => ['project-spaces', spaceId, 'metrics'] as const,
}

export function getMetricFoundation(spaceId: string) {
  return apiGet<MetricFoundation>(`/project-spaces/${spaceId}/metrics`)
}

export function saveMetric(
  spaceId: string,
  input: {
    metricId?: string
    expectedVersion: number
    metricKey: string
    name: string
    description: string
    unit: MetricDefinition['unit']
    expression: MetricExpression
    window: MetricWindow
  },
) {
  const requestId = crypto.randomUUID()
  return apiPost<MetricDefinition>(
    `/project-spaces/${spaceId}/metrics`,
    { schemaVersion: 1, requestId, ...input },
    { requestId },
  )
}

export function publishMetric(spaceId: string, metric: MetricDefinition) {
  const requestId = crypto.randomUUID()
  return apiPost<MetricDefinition['publishedVersion']>(
    `/project-spaces/${spaceId}/metrics/${metric.id}/publish`,
    { schemaVersion: 1, requestId, expectedVersion: metric.version, action: 'publish' },
    { requestId },
  )
}

export function previewMetric(spaceId: string, metric: MetricDefinition) {
  return apiPost<MetricResult>(
    `/project-spaces/${spaceId}/metrics/${metric.id}/preview`,
    { schemaVersion: 1, anchor: new Date().toISOString(), samples: [] },
  )
}
