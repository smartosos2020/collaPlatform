import {
  ApartmentOutlined,
  AppstoreOutlined,
  CalendarOutlined,
  CommentOutlined,
  EditOutlined,
  FilterOutlined,
  LinkOutlined,
  PlusOutlined,
  SaveOutlined,
  ShareAltOutlined,
  TableOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  App as AntdApp,
  Button,
  Checkbox,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Segmented,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { listDirectoryMembers } from '../../projects/api/projectsApi'
import {
  createBase,
  createField,
  createRecord,
  createTable,
  createView,
  getBase,
  getBaseRecord,
  getCalendarView,
  getKanbanView,
  getTable,
  grantBasePermission,
  listBases,
  queryRecords,
  updateRecord,
  type BaseField,
  type BaseFieldType,
  type BaseFilter,
  type BaseRecord,
  type BaseSort,
  type BaseView,
} from '../api/basesApi'

type CreateBaseForm = {
  name: string
  description?: string
}

type CreateTableForm = {
  name: string
}

type CreateFieldForm = {
  name: string
  fieldType: BaseFieldType
  required?: boolean
  optionsText?: string
}

type RecordFormValues = Record<string, unknown>

type FilterSortState = {
  filters: BaseFilter[]
  sorts: BaseSort[]
}

type FilterFormValues = {
  filterFieldId?: string
  operator?: BaseFilter['operator']
  filterValue?: string
  sortFieldId?: string
  direction?: BaseSort['direction']
}

type PermissionForm = {
  userId: string
  permissionLevel: 'view' | 'edit' | 'manage'
}

type BaseViewMode = 'grid' | 'kanban' | 'calendar'

const fieldTypes: Array<{ label: string; value: BaseFieldType }> = [
  { label: '文本', value: 'text' },
  { label: '数字', value: 'number' },
  { label: '成员', value: 'member' },
  { label: '日期', value: 'date' },
  { label: '附件', value: 'attachment' },
  { label: '单选', value: 'single_select' },
  { label: '多选', value: 'multi_select' },
]

const emptyFields: BaseField[] = []

export function BasesPage() {
  const { baseId, tableId, recordId } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { message } = AntdApp.useApp()
  const [selectedBaseId, setSelectedBaseId] = useState<string | null>(null)
  const [selectedTableId, setSelectedTableId] = useState<string | null>(null)
  const [createBaseOpen, setCreateBaseOpen] = useState(false)
  const [createTableOpen, setCreateTableOpen] = useState(false)
  const [createFieldOpen, setCreateFieldOpen] = useState(false)
  const [recordModalOpen, setRecordModalOpen] = useState(false)
  const [editingRecord, setEditingRecord] = useState<BaseRecord | null>(null)
  const [permissionOpen, setPermissionOpen] = useState(false)
  const [saveViewOpen, setSaveViewOpen] = useState(false)
  const [filterState, setFilterState] = useState<FilterSortState>({ filters: [], sorts: [] })
  const [viewMode, setViewMode] = useState<BaseViewMode>('grid')
  const [kanbanFieldId, setKanbanFieldId] = useState<string | null>(null)
  const [calendarFieldId, setCalendarFieldId] = useState<string | null>(null)
  const [createBaseForm] = Form.useForm<CreateBaseForm>()
  const [createTableForm] = Form.useForm<CreateTableForm>()
  const [createFieldForm] = Form.useForm<CreateFieldForm>()
  const [recordForm] = Form.useForm<RecordFormValues>()
  const [filterForm] = Form.useForm<FilterFormValues>()
  const [permissionForm] = Form.useForm<PermissionForm>()
  const [viewForm] = Form.useForm<{ name: string }>()

  const basesQuery = useQuery({ queryKey: ['bases'], queryFn: listBases })
  const activeBaseId = baseId ?? selectedBaseId ?? basesQuery.data?.[0]?.id ?? null
  const baseQuery = useQuery({
    queryKey: ['bases', activeBaseId],
    queryFn: () => getBase(activeBaseId || ''),
    enabled: Boolean(activeBaseId),
  })
  const activeTableId = tableId ?? selectedTableId ?? baseQuery.data?.tables[0]?.id ?? null
  const tableQuery = useQuery({
    queryKey: ['bases', activeBaseId, 'tables', activeTableId],
    queryFn: () => getTable(activeBaseId || '', activeTableId || ''),
    enabled: Boolean(activeBaseId && activeTableId),
  })
  const recordsQuery = useQuery({
    queryKey: ['bases', activeBaseId, 'tables', activeTableId, 'records', filterState],
    queryFn: () =>
      queryRecords(activeBaseId || '', activeTableId || '', {
        filters: filterState.filters,
        sorts: filterState.sorts,
        limit: 50,
        offset: 0,
      }),
    enabled: Boolean(activeBaseId && activeTableId),
  })
  const recordDetailQuery = useQuery({
    queryKey: ['base-records', recordId],
    queryFn: () => getBaseRecord(recordId || ''),
    enabled: Boolean(recordId),
  })
  const membersQuery = useQuery({ queryKey: ['members', 'directory'], queryFn: listDirectoryMembers })

  const activeBase = baseQuery.data?.base
  const tableDetail = tableQuery.data
  const fields = tableDetail?.fields ?? emptyFields
  const kanbanFieldOptions = useMemo(
    () =>
      fields
        .filter((field) => ['single_select', 'member', 'text'].includes(field.fieldType))
        .map((field) => ({ label: field.name, value: field.id })),
    [fields],
  )
  const dateFieldOptions = useMemo(
    () =>
      fields
        .filter((field) => field.fieldType === 'date')
        .map((field) => ({ label: field.name, value: field.id })),
    [fields],
  )
  const activeKanbanFieldId = kanbanFieldId ?? kanbanFieldOptions[0]?.value ?? null
  const activeCalendarFieldId = calendarFieldId ?? dateFieldOptions[0]?.value ?? null
  const canEdit = hasPermission(activeBase?.permissionLevel, 'edit')
  const canManage = hasPermission(activeBase?.permissionLevel, 'manage')
  const kanbanQuery = useQuery({
    queryKey: ['bases', activeBaseId, 'tables', activeTableId, 'kanban', activeKanbanFieldId],
    queryFn: () => getKanbanView(activeBaseId || '', activeTableId || '', activeKanbanFieldId || ''),
    enabled: Boolean(activeBaseId && activeTableId && viewMode === 'kanban' && activeKanbanFieldId),
  })
  const calendarQuery = useQuery({
    queryKey: ['bases', activeBaseId, 'tables', activeTableId, 'calendar', activeCalendarFieldId],
    queryFn: () => getCalendarView(activeBaseId || '', activeTableId || '', activeCalendarFieldId || ''),
    enabled: Boolean(activeBaseId && activeTableId && viewMode === 'calendar' && activeCalendarFieldId),
  })

  useEffect(() => {
    const firstBase = basesQuery.data?.[0]
    if (!baseId && !selectedBaseId && firstBase) {
      navigate(`/bases/${firstBase.id}`, { replace: true })
    }
  }, [baseId, basesQuery.data, navigate, selectedBaseId])

  useEffect(() => {
    const firstTable = baseQuery.data?.tables[0]
    if (activeBaseId && !tableId && !selectedTableId && firstTable) {
      navigate(`/bases/${activeBaseId}/tables/${firstTable.id}`, { replace: true })
    }
  }, [activeBaseId, baseQuery.data?.tables, navigate, selectedTableId, tableId])

  const refreshBase = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['bases'] }),
      activeBaseId ? queryClient.invalidateQueries({ queryKey: ['bases', activeBaseId] }) : Promise.resolve(),
      activeBaseId && activeTableId
        ? queryClient.invalidateQueries({ queryKey: ['bases', activeBaseId, 'tables', activeTableId] })
        : Promise.resolve(),
      activeBaseId && activeTableId
        ? queryClient.invalidateQueries({ queryKey: ['bases', activeBaseId, 'tables', activeTableId, 'records'] })
        : Promise.resolve(),
    ])
  }

  const createBaseMutation = useMutation({
    mutationFn: createBase,
    onSuccess: async (detail) => {
      setCreateBaseOpen(false)
      createBaseForm.resetFields()
      setSelectedBaseId(detail.base.id)
      navigate(`/bases/${detail.base.id}`)
      await refreshBase()
    },
  })

  const createTableMutation = useMutation({
    mutationFn: (values: CreateTableForm) => createTable(activeBaseId || '', values),
    onSuccess: async (detail) => {
      setCreateTableOpen(false)
      createTableForm.resetFields()
      setSelectedTableId(detail.table.id)
      navigate(`/bases/${detail.table.baseId}/tables/${detail.table.id}`)
      await refreshBase()
    },
  })

  const createFieldMutation = useMutation({
    mutationFn: (values: CreateFieldForm) =>
      createField(activeBaseId || '', activeTableId || '', {
        name: values.name,
        fieldType: values.fieldType,
        required: Boolean(values.required),
        config: selectConfig(values),
      }),
    onSuccess: async () => {
      setCreateFieldOpen(false)
      createFieldForm.resetFields()
      await refreshBase()
    },
  })

  const saveRecordMutation = useMutation({
    mutationFn: (values: RecordFormValues) => {
      const normalized = normalizeRecordValues(fields, values)
      if (editingRecord) {
        return updateRecord(activeBaseId || '', activeTableId || '', editingRecord.id, normalized)
      }
      return createRecord(activeBaseId || '', activeTableId || '', normalized)
    },
    onSuccess: async (record) => {
      setRecordModalOpen(false)
      setEditingRecord(null)
      recordForm.resetFields()
      if (activeBaseId && activeTableId) {
        navigate(`/bases/${activeBaseId}/tables/${activeTableId}/records/${record.id}`)
      }
      await refreshBase()
    },
  })

  const permissionMutation = useMutation({
    mutationFn: (values: PermissionForm) => grantBasePermission(activeBaseId || '', values),
    onSuccess: async () => {
      setPermissionOpen(false)
      permissionForm.resetFields()
      await refreshBase()
    },
  })

  const saveViewMutation = useMutation({
    mutationFn: (values: { name: string }) => createView(activeBaseId || '', activeTableId || '', { name: values.name, ...filterState }),
    onSuccess: async () => {
      setSaveViewOpen(false)
      viewForm.resetFields()
      await refreshBase()
    },
  })

  const rows = recordsQuery.data?.items ?? []
  const selectedRecord = rows.find((record) => record.id === recordId) ?? recordDetailQuery.data ?? null
  const memberNameById = useMemo(() => {
    const entries = (membersQuery.data ?? []).map((member) => [member.id, member.displayName] as const)
    return new Map(entries)
  }, [membersQuery.data])
  const tableColumns = useMemo(() => {
    const dynamicColumns = fields.map((field) => ({
      title: (
        <Space size={6}>
          <span>{field.name}</span>
          {field.required ? <Tag color="red">必填</Tag> : null}
        </Space>
      ),
      dataIndex: field.id,
      key: field.id,
      width: 180,
      render: (_value: unknown, record: BaseRecord) => (
        <CellValue field={field} record={record} memberNameById={memberNameById} />
      ),
    }))
    return [
      {
        title: '#',
        dataIndex: 'recordNo',
        key: 'recordNo',
        width: 74,
        fixed: 'left' as const,
        render: (value: number, record: BaseRecord) => (
          <Link to={`/bases/${activeBaseId}/tables/${activeTableId}/records/${record.id}`}>#{value}</Link>
        ),
      },
      ...dynamicColumns,
      {
        title: '更新',
        dataIndex: 'updatedAt',
        key: 'updatedAt',
        width: 180,
        render: (value: string) => new Date(value).toLocaleString(),
      },
    ]
  }, [activeBaseId, activeTableId, fields, memberNameById])

  const openRecordModal = useCallback((record: BaseRecord | null) => {
    setEditingRecord(record)
    recordForm.resetFields()
    if (record) {
      recordForm.setFieldsValue(recordValuesForForm(fields, record))
    }
    setRecordModalOpen(true)
  }, [fields, recordForm])

  const applyFilterSort = (values: FilterFormValues) => {
    const filters =
      values.filterFieldId && values.filterValue
        ? [{ fieldId: values.filterFieldId, operator: values.operator ?? 'eq', value: values.filterValue }]
        : []
    const sorts = values.sortFieldId ? [{ fieldId: values.sortFieldId, direction: values.direction ?? 'asc' }] : []
    setFilterState({ filters, sorts })
  }

  const applyView = (view: BaseView) => {
    setFilterState({ filters: view.filters, sorts: view.sorts })
    filterForm.setFieldsValue({
      filterFieldId: view.filters[0]?.fieldId,
      operator: view.filters[0]?.operator,
      filterValue: valueToFormText(view.filters[0]?.value),
      sortFieldId: view.sorts[0]?.fieldId,
      direction: view.sorts[0]?.direction,
    })
  }

  return (
    <div className="bases-workspace">
      <aside className="bases-sidebar">
        <div className="section-heading">
          <div>
            <Typography.Title level={4}>表格</Typography.Title>
            <Typography.Text type="secondary">{basesQuery.data?.length ?? 0} 个空间</Typography.Text>
          </div>
          <Tooltip title="新建表格空间">
            <Button icon={<PlusOutlined />} type="primary" onClick={() => setCreateBaseOpen(true)} />
          </Tooltip>
        </div>

        <Space orientation="vertical" size={8} className="bases-list">
          {(basesQuery.data ?? []).map((base) => (
            <button
              className={`base-list-item${base.id === activeBaseId ? ' active' : ''}`}
              key={base.id}
              type="button"
              onClick={() => {
                setSelectedBaseId(base.id)
                setSelectedTableId(null)
                navigate(`/bases/${base.id}`)
              }}
            >
              <ApartmentOutlined />
              <span>
                <strong>{base.name}</strong>
                <small>{base.tableCount} 表 · {base.recordCount} 记录</small>
              </span>
            </button>
          ))}
        </Space>
      </aside>

      <main className="bases-main">
        {activeBase ? (
          <>
            <header className="base-header">
              <div>
                <Typography.Title level={3}>{activeBase.name}</Typography.Title>
                <Space wrap>
                  <Tag>{permissionText(activeBase.permissionLevel)}</Tag>
                  <Typography.Text type="secondary">{activeBase.description || '暂无描述'}</Typography.Text>
                </Space>
              </div>
              <Space wrap>
                <Tooltip title="授权">
                  <Button icon={<ShareAltOutlined />} disabled={!canManage} onClick={() => setPermissionOpen(true)} />
                </Tooltip>
                <Button icon={<PlusOutlined />} disabled={!canEdit} onClick={() => setCreateTableOpen(true)}>
                  数据表
                </Button>
              </Space>
            </header>

            <section className="base-table-tabs">
              {(baseQuery.data?.tables ?? []).map((table) => (
                <button
                  className={`base-table-tab${table.id === activeTableId ? ' active' : ''}`}
                  key={table.id}
                  type="button"
                  onClick={() => {
                    setSelectedTableId(table.id)
                    navigate(`/bases/${activeBaseId}/tables/${table.id}`)
                  }}
                >
                  <TableOutlined />
                  <span>{table.name}</span>
                  <Tag>{table.recordCount}</Tag>
                </button>
              ))}
            </section>

            {tableDetail ? (
              <section className="base-grid-shell">
                <div className="base-grid-toolbar">
                  <Space>
                    <Typography.Title level={4}>{tableDetail.table.name}</Typography.Title>
                    <Typography.Text type="secondary">{recordsQuery.data?.total ?? 0} 条</Typography.Text>
                  </Space>
                  <Space wrap>
                    <Segmented
                      value={viewMode}
                      options={[
                        { label: '表格', value: 'grid', icon: <TableOutlined /> },
                        { label: '看板', value: 'kanban', icon: <AppstoreOutlined /> },
                        { label: '日历', value: 'calendar', icon: <CalendarOutlined /> },
                      ]}
                      onChange={(value) => setViewMode(value as BaseViewMode)}
                    />
                    <Button icon={<PlusOutlined />} disabled={!canEdit} onClick={() => openRecordModal(null)}>
                      记录
                    </Button>
                    <Button icon={<PlusOutlined />} disabled={!canEdit} onClick={() => setCreateFieldOpen(true)}>
                      字段
                    </Button>
                  </Space>
                </div>

                {viewMode === 'grid' ? (
                  <Table
                    className="base-record-table"
                    rowKey="id"
                    size="middle"
                    columns={tableColumns}
                    dataSource={rows}
                    loading={recordsQuery.isLoading}
                    pagination={false}
                    rowClassName={(record) => (record.id === recordId ? 'base-record-row-active' : '')}
                    scroll={{ x: Math.max(720, fields.length * 180 + 260), y: 520 }}
                    onRow={(record) => ({
                      onDoubleClick: () => openRecordModal(record),
                    })}
                  />
                ) : null}

                {viewMode === 'kanban' ? (
                  <div className="base-alt-view">
                    <Select
                      className="base-view-field-select"
                      placeholder="选择分组字段"
                      options={kanbanFieldOptions}
                      value={activeKanbanFieldId}
                      onChange={setKanbanFieldId}
                    />
                    <div className="base-kanban">
                      {(kanbanQuery.data?.columns ?? []).map((column) => (
                        <section className="base-kanban-column" key={column.key}>
                          <Space className="base-kanban-title">
                            <Typography.Text strong>{column.title}</Typography.Text>
                            <Tag>{column.records.length}</Tag>
                          </Space>
                          {column.records.map((record) => (
                            <button className="base-view-record" key={record.id} type="button" onClick={() => openRecordModal(record)}>
                              <strong>{record.primaryText}</strong>
                              <small>#{record.recordNo}</small>
                            </button>
                          ))}
                        </section>
                      ))}
                      {!activeKanbanFieldId ? <Empty description="选择字段后查看看板" /> : null}
                    </div>
                  </div>
                ) : null}

                {viewMode === 'calendar' ? (
                  <div className="base-alt-view">
                    <Select
                      className="base-view-field-select"
                      placeholder="选择日期字段"
                      options={dateFieldOptions}
                      value={activeCalendarFieldId}
                      onChange={setCalendarFieldId}
                    />
                    <div className="base-calendar">
                      {(calendarQuery.data?.buckets ?? []).map((bucket) => (
                        <section className="base-calendar-day" key={bucket.date}>
                          <Typography.Text strong>{bucket.date}</Typography.Text>
                          {bucket.records.map((record) => (
                            <button className="base-view-record" key={record.id} type="button" onClick={() => openRecordModal(record)}>
                              <strong>{record.primaryText}</strong>
                              <small>#{record.recordNo}</small>
                            </button>
                          ))}
                        </section>
                      ))}
                      {!activeCalendarFieldId ? <Empty description="选择日期字段后查看日历" /> : null}
                    </div>
                  </div>
                ) : null}
              </section>
            ) : (
              <div className="base-empty-state">
                <Empty description="暂无数据表">
                  <Button type="primary" icon={<PlusOutlined />} disabled={!canEdit} onClick={() => setCreateTableOpen(true)}>
                    新建数据表
                  </Button>
                </Empty>
              </div>
            )}
          </>
        ) : (
          <div className="base-empty-state">
            <Empty description="暂无表格空间">
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateBaseOpen(true)}>
                新建表格空间
              </Button>
            </Empty>
          </div>
        )}
      </main>

      <aside className="bases-panel">
        <Typography.Title level={4}>字段与视图</Typography.Title>
        <Space orientation="vertical" size={12} className="bases-panel-stack">
          {selectedRecord ? (
            <BaseRecordDetail
              record={selectedRecord}
              fields={fields}
              baseId={activeBaseId}
              tableId={activeTableId}
              memberNameById={memberNameById}
              canEdit={canEdit}
              onEdit={() => openRecordModal(selectedRecord)}
            />
          ) : null}

          <div className="base-side-section">
            <Space className="base-side-section-title">
              <Typography.Text strong>字段</Typography.Text>
              <Button size="small" icon={<PlusOutlined />} disabled={!canEdit || !activeTableId} onClick={() => setCreateFieldOpen(true)} />
            </Space>
            <Space wrap>
              {fields.map((field) => (
                <Tag key={field.id}>{field.name} · {fieldTypeText(field.fieldType)}</Tag>
              ))}
              {fields.length === 0 ? <Typography.Text type="secondary">暂无字段</Typography.Text> : null}
            </Space>
          </div>

          <div className="base-side-section">
            <Space className="base-side-section-title">
              <Typography.Text strong>筛选排序</Typography.Text>
              <Tooltip title="保存视图">
                <Button size="small" icon={<SaveOutlined />} disabled={!canEdit || !activeTableId} onClick={() => setSaveViewOpen(true)} />
              </Tooltip>
            </Space>
            <Form
              form={filterForm}
              layout="vertical"
              initialValues={{ operator: 'contains', direction: 'asc' }}
              onFinish={applyFilterSort}
            >
              <Form.Item name="filterFieldId" label="筛选字段">
                <Select
                  allowClear
                  options={fields.map((field) => ({ label: field.name, value: field.id }))}
                />
              </Form.Item>
              <Form.Item name="operator" label="条件">
                <Select
                  options={[
                    { label: '等于', value: 'eq' },
                    { label: '包含', value: 'contains' },
                    { label: '大于', value: 'gt' },
                    { label: '小于', value: 'lt' },
                  ]}
                />
              </Form.Item>
              <Form.Item name="filterValue" label="值">
                <Input />
              </Form.Item>
              <Form.Item name="sortFieldId" label="排序字段">
                <Select
                  allowClear
                  options={fields.map((field) => ({ label: field.name, value: field.id }))}
                />
              </Form.Item>
              <Form.Item name="direction" label="方向">
                <Select
                  options={[
                    { label: '升序', value: 'asc' },
                    { label: '降序', value: 'desc' },
                  ]}
                />
              </Form.Item>
              <Space>
                <Button type="primary" icon={<FilterOutlined />} htmlType="submit" disabled={!activeTableId}>
                  应用
                </Button>
                <Button
                  onClick={() => {
                    setFilterState({ filters: [], sorts: [] })
                    filterForm.resetFields()
                  }}
                >
                  清空
                </Button>
              </Space>
            </Form>
          </div>

          <div className="base-side-section">
            <Typography.Text strong>已保存视图</Typography.Text>
            <Space wrap className="base-view-tags">
              {(tableDetail?.views ?? []).map((view) => (
                <Button key={view.id} size="small" onClick={() => applyView(view)}>
                  {view.name}
                </Button>
              ))}
              {(tableDetail?.views ?? []).length === 0 ? <Typography.Text type="secondary">暂无视图</Typography.Text> : null}
            </Space>
          </div>

          <div className="base-side-section">
            <Typography.Text strong>成员权限</Typography.Text>
            <Space wrap className="base-view-tags">
              {(baseQuery.data?.members ?? []).map((member) => (
                <Tag key={member.id}>{member.displayName}: {permissionText(member.permissionLevel)}</Tag>
              ))}
            </Space>
          </div>
        </Space>
      </aside>

      <Modal
        title="新建表格空间"
        open={createBaseOpen}
        onCancel={() => setCreateBaseOpen(false)}
        onOk={() => createBaseForm.submit()}
        confirmLoading={createBaseMutation.isPending}
      >
        <Form form={createBaseForm} layout="vertical" onFinish={(values) => createBaseMutation.mutate(values)}>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="新建数据表"
        open={createTableOpen}
        onCancel={() => setCreateTableOpen(false)}
        onOk={() => createTableForm.submit()}
        confirmLoading={createTableMutation.isPending}
      >
        <Form form={createTableForm} layout="vertical" onFinish={(values) => createTableMutation.mutate(values)}>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="新建字段"
        open={createFieldOpen}
        onCancel={() => setCreateFieldOpen(false)}
        onOk={() => createFieldForm.submit()}
        confirmLoading={createFieldMutation.isPending}
      >
        <Form
          form={createFieldForm}
          layout="vertical"
          initialValues={{ fieldType: 'text', required: false }}
          onFinish={(values) => createFieldMutation.mutate(values)}
        >
          <Form.Item name="name" label="字段名" rules={[{ required: true, message: '请输入字段名' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="fieldType" label="类型" rules={[{ required: true }]}>
            <Select options={fieldTypes} />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, next) => prev.fieldType !== next.fieldType}>
            {({ getFieldValue }) =>
              ['single_select', 'multi_select'].includes(getFieldValue('fieldType')) ? (
                <Form.Item
                  name="optionsText"
                  label="选项"
                  rules={[{ required: true, message: '请输入选项' }]}
                  extra="用英文逗号分隔，例如：未开始,进行中,完成"
                >
                  <Input />
                </Form.Item>
              ) : null
            }
          </Form.Item>
          <Form.Item name="required" valuePropName="checked">
            <Checkbox>必填</Checkbox>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={editingRecord ? `编辑 #${editingRecord.recordNo}` : '新建记录'}
        open={recordModalOpen}
        onCancel={() => {
          setRecordModalOpen(false)
          setEditingRecord(null)
          recordForm.resetFields()
        }}
        onOk={() => recordForm.submit()}
        confirmLoading={saveRecordMutation.isPending}
        width={720}
      >
        <Form form={recordForm} layout="vertical" onFinish={(values) => saveRecordMutation.mutate(values)}>
          {fields.length === 0 ? <Empty description="请先创建字段" /> : null}
          {fields.map((field) => (
            <RecordFieldInput key={field.id} field={field} members={membersQuery.data ?? []} />
          ))}
        </Form>
      </Modal>

      <Modal
        title="授权表格空间"
        open={permissionOpen}
        onCancel={() => setPermissionOpen(false)}
        onOk={() => permissionForm.submit()}
        confirmLoading={permissionMutation.isPending}
      >
        <Form form={permissionForm} layout="vertical" onFinish={(values) => permissionMutation.mutate(values)}>
          <Form.Item name="userId" label="成员" rules={[{ required: true, message: '请选择成员' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={(membersQuery.data ?? []).map((member) => ({ label: member.displayName, value: member.id }))}
            />
          </Form.Item>
          <Form.Item name="permissionLevel" label="权限" initialValue="view" rules={[{ required: true }]}>
            <Select
              options={[
                { label: '查看', value: 'view' },
                { label: '编辑', value: 'edit' },
                { label: '管理', value: 'manage' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="保存视图"
        open={saveViewOpen}
        onCancel={() => setSaveViewOpen(false)}
        onOk={() => viewForm.submit()}
        confirmLoading={saveViewMutation.isPending}
      >
        <Form
          form={viewForm}
          layout="vertical"
          onFinish={(values) => {
            if (filterState.filters.length === 0 && filterState.sorts.length === 0) {
              message.warning('请先应用筛选或排序')
              return
            }
            saveViewMutation.mutate(values)
          }}
        >
          <Form.Item name="name" label="视图名" rules={[{ required: true, message: '请输入视图名' }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

function BaseRecordDetail({
  record,
  fields,
  baseId,
  tableId,
  memberNameById,
  canEdit,
  onEdit,
}: {
  record: BaseRecord
  fields: BaseField[]
  baseId: string | null
  tableId: string | null
  memberNameById: Map<string, string>
  canEdit: boolean
  onEdit: () => void
}) {
  const recordPath = baseId && tableId ? `/bases/${baseId}/tables/${tableId}/records/${record.id}` : ''
  return (
    <div className="base-side-section base-record-detail">
      <Space className="base-side-section-title">
        <Typography.Text strong>记录详情 #{record.recordNo}</Typography.Text>
        <Space>
          {recordPath ? (
            <Tooltip title="打开链接">
              <Button size="small" icon={<LinkOutlined />} href={recordPath} />
            </Tooltip>
          ) : null}
          <Tooltip title="编辑记录">
            <Button size="small" icon={<EditOutlined />} disabled={!canEdit} onClick={onEdit} />
          </Tooltip>
        </Space>
      </Space>
      <Typography.Text type="secondary">{record.primaryText || '未命名记录'}</Typography.Text>
      <div className="base-record-detail-grid">
        {fields.map((field) => (
          <div className="base-record-detail-row" key={field.id}>
            <span>{field.name}</span>
            <CellValue field={field} record={record} memberNameById={memberNameById} />
          </div>
        ))}
      </div>
      <Space wrap>
        <Tag>创建：{record.createdByName}</Tag>
        <Tag>更新：{record.updatedByName}</Tag>
      </Space>
      <Typography.Text type="secondary">{new Date(record.updatedAt).toLocaleString()}</Typography.Text>
      <div className="base-record-comments-placeholder">
        <CommentOutlined />
        <Typography.Text type="secondary">记录评论预留</Typography.Text>
      </div>
    </div>
  )
}

function RecordFieldInput({ field, members }: { field: BaseField; members: Array<{ id: string; displayName: string }> }) {
  const rules = field.required ? [{ required: true, message: `请输入${field.name}` }] : []
  if (field.fieldType === 'number') {
    return (
      <Form.Item name={field.id} label={field.name} rules={rules}>
        <InputNumber className="base-form-control" />
      </Form.Item>
    )
  }
  if (field.fieldType === 'member') {
    return (
      <Form.Item name={field.id} label={field.name} rules={rules}>
        <Select
          showSearch
          optionFilterProp="label"
          options={members.map((member) => ({ label: member.displayName, value: member.id }))}
        />
      </Form.Item>
    )
  }
  if (field.fieldType === 'single_select') {
    return (
      <Form.Item name={field.id} label={field.name} rules={rules}>
        <Select options={selectOptions(field)} />
      </Form.Item>
    )
  }
  if (field.fieldType === 'multi_select') {
    return (
      <Form.Item name={field.id} label={field.name} rules={rules}>
        <Select mode="multiple" options={selectOptions(field)} />
      </Form.Item>
    )
  }
  if (field.fieldType === 'date') {
    return (
      <Form.Item name={field.id} label={field.name} rules={rules}>
        <Input type="date" />
      </Form.Item>
    )
  }
  if (field.fieldType === 'attachment') {
    return (
      <Form.Item name={field.id} label={field.name} rules={rules} extra="当前轻量版输入已上传文件 ID">
        <Input placeholder="fileId" />
      </Form.Item>
    )
  }
  return (
    <Form.Item name={field.id} label={field.name} rules={rules}>
      <Input />
    </Form.Item>
  )
}

function CellValue({
  field,
  record,
  memberNameById,
}: {
  field: BaseField
  record: BaseRecord
  memberNameById: Map<string, string>
}) {
  const value = record.values[field.id] ?? record.values[field.name]
  if (value === undefined || value === null || value === '') {
    return <Typography.Text type="secondary">-</Typography.Text>
  }
  if (field.fieldType === 'multi_select' && Array.isArray(value)) {
    return (
      <Space wrap size={4}>
        {value.map((item) => <Tag key={String(item)}>{String(item)}</Tag>)}
      </Space>
    )
  }
  if (field.fieldType === 'member') {
    return <span>{memberNameById.get(String(value)) ?? String(value)}</span>
  }
  if (field.fieldType === 'attachment') {
    return <Tag>{String(value).slice(0, 8)}</Tag>
  }
  return <span>{String(value)}</span>
}

function selectConfig(values: CreateFieldForm) {
  if (!['single_select', 'multi_select'].includes(values.fieldType)) {
    return {}
  }
  return {
    options: (values.optionsText ?? '')
      .split(',')
      .map((option) => option.trim())
      .filter(Boolean),
  }
}

function normalizeRecordValues(fields: BaseField[], values: RecordFormValues) {
  const normalized: Record<string, unknown> = {}
  for (const field of fields) {
    const value = values[field.id]
    if (value === undefined || value === null || value === '') {
      continue
    }
    normalized[field.id] = value
  }
  return normalized
}

function recordValuesForForm(fields: BaseField[], record: BaseRecord) {
  const values: RecordFormValues = {}
  for (const field of fields) {
    values[field.id] = record.values[field.id] ?? record.values[field.name]
  }
  return values
}

function selectOptions(field: BaseField) {
  const raw = field.config.options
  if (!Array.isArray(raw)) {
    return []
  }
  return raw.map((option) => ({ label: String(option), value: String(option) }))
}

function valueToFormText(value: unknown) {
  if (value === undefined || value === null) {
    return undefined
  }
  if (Array.isArray(value)) {
    return value.join(',')
  }
  return String(value)
}

function fieldTypeText(fieldType: string) {
  return fieldTypes.find((item) => item.value === fieldType)?.label ?? fieldType
}

function hasPermission(current: string | undefined, required: 'view' | 'edit' | 'manage') {
  const rank = { view: 1, edit: 2, manage: 3 }
  return current ? rank[current as keyof typeof rank] >= rank[required] : false
}

function permissionText(permission: string) {
  return { view: '可查看', edit: '可编辑', manage: '可管理' }[permission] ?? permission
}
