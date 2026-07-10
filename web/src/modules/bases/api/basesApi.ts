import { apiGet, apiGetText, apiPatch, apiPost } from '../../../shared/api/httpClient'

export type UserBaseView = {
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
  collaboration?: {
    permissionLevel: 'view' | 'edit' | 'manage'
    displayText: string
    canEdit: boolean
  }
  availableActions?: string[]
}

export type BaseSummary = UserBaseView

export type UserBaseMemberView = {
  id: string
  userId: string
  username: string
  displayName: string
  permissionLevel: 'view' | 'edit' | 'manage'
  createdAt: string
  collaboration?: {
    permissionLevel: 'view' | 'edit' | 'manage'
    displayText: string
    canEdit: boolean
  }
}

export type BaseMember = UserBaseMemberView

export type UserBaseTableView = {
  id: string
  baseId: string
  name: string
  primaryFieldId?: string | null
  fieldCount: number
  recordCount: number
  createdAt: string
  updatedAt: string
  availableActions?: string[]
}

export type BaseTableSummary = UserBaseTableView

export type UserBaseDetailView = {
  base: UserBaseView
  tables: UserBaseTableView[]
  members: UserBaseMemberView[]
}

export type BaseDetail = UserBaseDetailView

export type BaseFieldType =
  | 'text'
  | 'number'
  | 'member'
  | 'date'
  | 'attachment'
  | 'single_select'
  | 'multi_select'
  | 'status'
  | 'url'
  | 'object_link'

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
  visibleFieldIds: string[]
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

export type UserBaseRecordPageView = {
  items: BaseRecord[]
  total: number
  limit: number
  offset: number
  collaborationHint?: string
}

export type BaseRecordPage = UserBaseRecordPageView

export type PlatformObjectSummary = {
  objectType: string
  objectId: string
  accessState: 'available' | 'forbidden' | 'deleted' | 'not_found' | 'invalid'
  title?: string | null
  subtitle?: string | null
  status?: string | null
  webPath?: string | null
  deepLink?: string | null
  metadata: Record<string, unknown>
}

export type BaseRecordComment = {
  id: string
  recordId: string
  authorId: string
  authorName: string
  content: string
  createdAt: string
}

export type BaseRecordRelation = {
  id: string
  recordId: string
  targetType: string
  targetId: string
  relationType: string
  target: PlatformObjectSummary
  createdBy: string
  createdByName: string
  createdAt: string
}

export type BaseRecordActivity = {
  id: string
  recordId: string
  actorId: string
  actorName: string
  action: string
  metadata: Record<string, unknown>
  createdAt: string
}

export type BaseRecordDetail = {
  record: BaseRecord
  comments: BaseRecordComment[]
  relations: BaseRecordRelation[]
  activities: BaseRecordActivity[]
}

export type BaseImportResult = {
  created: number
  errors: string[]
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
  return apiGet<UserBaseView[]>('/bases')
}

export function createBase(request: { name: string; description?: string }) {
  return apiPost<UserBaseDetailView>('/bases', request)
}

export function getBase(baseId: string) {
  return apiGet<UserBaseDetailView>(`/bases/${baseId}`)
}

export function updateBase(baseId: string, request: { name?: string; description?: string }) {
  return apiPatch<UserBaseDetailView>(`/bases/${baseId}`, request)
}

export function grantBasePermission(baseId: string, request: { userId: string; permissionLevel: string }) {
  return apiPost<UserBaseDetailView>(`/bases/${baseId}/members`, request)
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
  return apiGet<UserBaseRecordPageView>(`/bases/${baseId}/tables/${tableId}/records?limit=50&offset=0`)
}

export function queryRecords(
  baseId: string,
  tableId: string,
  request: { filters: BaseFilter[]; sorts: BaseSort[]; limit?: number; offset?: number },
) {
  return apiPost<UserBaseRecordPageView>(`/bases/${baseId}/tables/${tableId}/records/query`, request)
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

export function getBaseRecord(recordId: string) {
  return apiGet<BaseRecord>(`/base-records/${recordId}`)
}

export function getBaseRecordDetail(recordId: string) {
  return apiGet<BaseRecordDetail>(`/base-records/${recordId}/detail`)
}

export function addBaseRecordComment(recordId: string, content: string) {
  return apiPost<BaseRecordDetail>(`/base-records/${recordId}/comments`, { content })
}

export function addBaseRecordRelation(recordId: string, request: { targetType: string; targetId: string }) {
  return apiPost<BaseRecordDetail>(`/base-records/${recordId}/relations`, request)
}

export function createView(
  baseId: string,
  tableId: string,
  request: { name: string; filters: BaseFilter[]; sorts: BaseSort[]; visibleFieldIds?: string[] },
) {
  return apiPost<BaseView>(`/bases/${baseId}/tables/${tableId}/views`, request)
}

export function exportBaseCsv(baseId: string, tableId: string) {
  return apiGetText(`/bases/${baseId}/tables/${tableId}/export.csv`)
}

export function importBaseCsv(baseId: string, tableId: string, csv: string) {
  return apiPost<BaseImportResult>(`/bases/${baseId}/tables/${tableId}/import.csv`, { csv })
}
