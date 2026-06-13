import { apiGet, apiPatch, apiPost } from '../../../shared/api/httpClient'

export type BaseSummary = {
  id: string
  name: string
  description?: string | null
  status: string
  permissionLevel: 'view' | 'edit' | 'manage'
  tableCount: number
  recordCount: number
  createdBy: string
  createdByName: string
  createdAt: string
  updatedBy: string
  updatedByName: string
  updatedAt: string
}

export type BaseMember = {
  id: string
  userId: string
  username: string
  displayName: string
  permissionLevel: 'view' | 'edit' | 'manage'
  createdAt: string
}

export type BaseTableSummary = {
  id: string
  baseId: string
  name: string
  primaryFieldId?: string | null
  fieldCount: number
  recordCount: number
  createdAt: string
  updatedAt: string
}

export type BaseDetail = {
  base: BaseSummary
  tables: BaseTableSummary[]
  members: BaseMember[]
}

export type BaseFieldType =
  | 'text'
  | 'number'
  | 'member'
  | 'date'
  | 'attachment'
  | 'single_select'
  | 'multi_select'

export type BaseField = {
  id: string
  tableId: string
  fieldKey: string
  name: string
  fieldType: BaseFieldType
  config: Record<string, unknown>
  required: boolean
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export type BaseFilter = {
  fieldId: string
  operator: 'eq' | 'contains' | 'gt' | 'lt'
  value: unknown
}

export type BaseSort = {
  fieldId: string
  direction: 'asc' | 'desc'
}

export type BaseView = {
  id: string
  tableId: string
  name: string
  filters: BaseFilter[]
  sorts: BaseSort[]
  createdAt: string
  updatedAt: string
}

export type BaseTableDetail = {
  table: BaseTableSummary
  fields: BaseField[]
  views: BaseView[]
}

export type BaseRecord = {
  id: string
  tableId: string
  recordNo: number
  primaryText: string
  values: Record<string, unknown>
  createdBy: string
  createdByName: string
  createdAt: string
  updatedBy: string
  updatedByName: string
  updatedAt: string
}

export type BaseRecordPage = {
  items: BaseRecord[]
  total: number
  limit: number
  offset: number
}

export type BaseKanbanView = {
  tableId: string
  groupFieldId: string
  columns: Array<{ key: string; title: string; records: BaseRecord[] }>
}

export type BaseCalendarView = {
  tableId: string
  dateFieldId: string
  buckets: Array<{ date: string; records: BaseRecord[] }>
}

export type CreateFieldRequest = {
  name: string
  fieldType: BaseFieldType
  config?: Record<string, unknown>
  required: boolean
}

export function listBases() {
  return apiGet<BaseSummary[]>('/bases')
}

export function createBase(request: { name: string; description?: string }) {
  return apiPost<BaseDetail>('/bases', request)
}

export function getBase(baseId: string) {
  return apiGet<BaseDetail>(`/bases/${baseId}`)
}

export function updateBase(baseId: string, request: { name?: string; description?: string }) {
  return apiPatch<BaseDetail>(`/bases/${baseId}`, request)
}

export function grantBasePermission(baseId: string, request: { userId: string; permissionLevel: string }) {
  return apiPost<BaseDetail>(`/bases/${baseId}/members`, request)
}

export function createTable(baseId: string, request: { name: string }) {
  return apiPost<BaseTableDetail>(`/bases/${baseId}/tables`, request)
}

export function getTable(baseId: string, tableId: string) {
  return apiGet<BaseTableDetail>(`/bases/${baseId}/tables/${tableId}`)
}

export function createField(baseId: string, tableId: string, request: CreateFieldRequest) {
  return apiPost<BaseTableDetail>(`/bases/${baseId}/tables/${tableId}/fields`, request)
}

export function listRecords(baseId: string, tableId: string) {
  return apiGet<BaseRecordPage>(`/bases/${baseId}/tables/${tableId}/records?limit=50&offset=0`)
}

export function queryRecords(
  baseId: string,
  tableId: string,
  request: { filters: BaseFilter[]; sorts: BaseSort[]; limit?: number; offset?: number },
) {
  return apiPost<BaseRecordPage>(`/bases/${baseId}/tables/${tableId}/records/query`, request)
}

export function getKanbanView(baseId: string, tableId: string, groupFieldId: string) {
  const params = new URLSearchParams({ groupFieldId })
  return apiGet<BaseKanbanView>(`/bases/${baseId}/tables/${tableId}/views/kanban?${params}`)
}

export function getCalendarView(baseId: string, tableId: string, dateFieldId: string) {
  const params = new URLSearchParams({ dateFieldId })
  return apiGet<BaseCalendarView>(`/bases/${baseId}/tables/${tableId}/views/calendar?${params}`)
}

export function createRecord(baseId: string, tableId: string, values: Record<string, unknown>) {
  return apiPost<BaseRecord>(`/bases/${baseId}/tables/${tableId}/records`, { values })
}

export function updateRecord(baseId: string, tableId: string, recordId: string, values: Record<string, unknown>) {
  return apiPatch<BaseRecord>(`/bases/${baseId}/tables/${tableId}/records/${recordId}`, { values })
}

export function createView(baseId: string, tableId: string, request: { name: string; filters: BaseFilter[]; sorts: BaseSort[] }) {
  return apiPost<BaseView>(`/bases/${baseId}/tables/${tableId}/views`, request)
}
