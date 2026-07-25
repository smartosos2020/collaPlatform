import { Alert, DatePicker, Input, InputNumber, Select, Switch, Tag, Typography } from 'antd'
import dayjs from 'dayjs'
import type { ReactNode } from 'react'

import type {
  ConfiguredWorkItemField,
  WorkItemFieldOption,
  WorkItemFieldType,
} from '../api/workItemFieldsApi'
import type {
  WorkItemFieldAccessProjection,
  WorkItemLayoutNode,
  WorkItemLayoutProjectionField,
} from '../api/workItemLayoutsApi'

type RenderableField = Pick<
  ConfiguredWorkItemField,
  'id' | 'fieldKey' | 'name' | 'description' | 'fieldType' | 'config'
> & {
  options?: WorkItemFieldOption[]
}

export type WorkItemLayoutValues = Record<string, unknown>

type ControlContext = {
  field: RenderableField
  id: string
  value: unknown
  disabled: boolean
  onChange: (value: unknown) => void
}

type ControlRenderer = (context: ControlContext) => ReactNode

const controls: Partial<Record<WorkItemFieldType, ControlRenderer>> = {
  text: ({ field, id, value, disabled, onChange }) => {
    const rich = field.config.typeConfig?.presentation === 'rich_text'
    return rich ? (
      <Input.TextArea
        id={id}
        value={stringValue(value)}
        disabled={disabled}
        autoSize={{ minRows: 3, maxRows: 8 }}
        placeholder={`请输入${field.name}`}
        onChange={(event) => onChange(event.target.value)}
      />
    ) : (
      <Input
        id={id}
        value={stringValue(value)}
        disabled={disabled}
        placeholder={`请输入${field.name}`}
        onChange={(event) => onChange(event.target.value)}
      />
    )
  },
  number: ({ field, id, value, disabled, onChange }) => (
    <InputNumber
      id={id}
      value={typeof value === 'number' ? value : null}
      disabled={disabled}
      style={{ width: '100%' }}
      placeholder={`请输入${field.name}`}
      onChange={onChange}
    />
  ),
  boolean: ({ field, id, value, disabled, onChange }) => (
    <Switch
      id={id}
      aria-label={field.name}
      checked={Boolean(value)}
      disabled={disabled}
      onChange={onChange}
    />
  ),
  single_select: ({ field, id, value, disabled, onChange }) => (
    <Select
      id={id}
      aria-label={field.name}
      value={typeof value === 'string' ? value : undefined}
      disabled={disabled}
      allowClear
      options={selectOptions(field)}
      onChange={onChange}
    />
  ),
  multi_select: ({ field, id, value, disabled, onChange }) => (
    <Select
      id={id}
      aria-label={field.name}
      mode="multiple"
      value={Array.isArray(value) ? value : []}
      disabled={disabled}
      options={selectOptions(field)}
      onChange={onChange}
    />
  ),
  date: ({ field, id, value, disabled, onChange }) => (
    <DatePicker
      id={id}
      aria-label={field.name}
      value={dateValue(value)}
      disabled={disabled}
      style={{ width: '100%' }}
      onChange={(next) => onChange(next?.format('YYYY-MM-DD') ?? null)}
    />
  ),
  datetime: ({ field, id, value, disabled, onChange }) => (
    <DatePicker
      id={id}
      aria-label={field.name}
      value={dateValue(value)}
      disabled={disabled}
      showTime
      style={{ width: '100%' }}
      onChange={(next) => onChange(next?.toISOString() ?? null)}
    />
  ),
  url: ({ field, id, value, disabled, onChange }) => (
    <Input
      id={id}
      type="url"
      value={stringValue(value)}
      disabled={disabled}
      placeholder={`请输入${field.name}`}
      onChange={(event) => onChange(event.target.value)}
    />
  ),
  user: ({ field, id, value, disabled, onChange }) => (
    <Select
      id={id}
      aria-label={field.name}
      mode="multiple"
      value={Array.isArray(value) ? value : []}
      disabled={disabled}
      placeholder="选择成员、部门或用户组"
      notFoundContent="身份目录将在工作项运行时连接"
      onChange={onChange}
    />
  ),
  attachment: ({ field, id }) => (
    <Input id={id} aria-label={field.name} disabled placeholder="附件上传将在工作项运行时连接" />
  ),
  work_item_reference: ({ field, id }) => (
    <Select
      id={id}
      aria-label={field.name}
      mode="multiple"
      disabled
      placeholder="工作项引用将在实例能力启用后连接"
    />
  ),
}

export function WorkItemLayoutRenderer({
  layout,
  fields,
  accessProjection,
  values = {},
  onValuesChange,
  presentation = 'edit',
  disclosure = 'detailed',
}: {
  layout: { layoutKind: string; nodes: WorkItemLayoutNode[] }
  fields: Array<ConfiguredWorkItemField | WorkItemLayoutProjectionField>
  accessProjection: Record<string, WorkItemFieldAccessProjection>
  values?: WorkItemLayoutValues
  onValuesChange?: (values: WorkItemLayoutValues) => void
  presentation?: 'edit' | 'read'
  disclosure?: 'detailed' | 'minimal'
}) {
  const fieldById = new Map(fields.map((field) => [field.id, field as RenderableField]))
  const children = childMap(layout.nodes)
  const roots = children.get(null) ?? []
  const update = (fieldKey: string, value: unknown) => {
    onValuesChange?.({ ...values, [fieldKey]: value })
  }

  if (roots.length === 0) {
    return (
      <Alert
        type="info"
        showIcon
        message={disclosure === 'minimal' ? '当前没有可展示内容' : '布局没有可展示的根节点'}
      />
    )
  }

  return (
    <div
      className="work-item-layout-preview"
      aria-label={`${layout.layoutKind} 布局预览`}
      aria-live="polite"
    >
      {roots.map((node) => (
        <LayoutPreviewNode
          key={node.id}
          node={node}
          children={children}
          fieldById={fieldById}
          accessProjection={accessProjection}
          values={values}
          presentation={presentation}
          disclosure={disclosure}
          onValueChange={update}
        />
      ))}
    </div>
  )
}

function LayoutPreviewNode({
  node,
  children,
  fieldById,
  accessProjection,
  values,
  presentation,
  disclosure,
  onValueChange,
}: {
  node: WorkItemLayoutNode
  children: Map<string | null, WorkItemLayoutNode[]>
  fieldById: Map<string, RenderableField>
  accessProjection: Record<string, WorkItemFieldAccessProjection>
  values: WorkItemLayoutValues
  presentation: 'edit' | 'read'
  disclosure: 'detailed' | 'minimal'
  onValueChange: (fieldKey: string, value: unknown) => void
}) {
  const nested = children.get(node.id) ?? []
  if (node.nodeType === 'field') {
    const field = node.fieldId ? fieldById.get(node.fieldId) : undefined
    if (!field) {
      return (
        <Alert
          type="warning"
          showIcon
          message={disclosure === 'minimal'
            ? '部分内容暂不可用'
            : `字段 ${node.fieldKey ?? node.nodeKey} 不可用，请在布局配置中重新绑定`}
        />
      )
    }
    const projection = accessProjection[field.fieldKey] ?? {
      mode: 'hidden' as const,
      required: false,
      reasonCode: 'missing_server_projection',
    }
    if (projection.mode === 'hidden') return null
    const renderer = controls[field.fieldType]
    if (!renderer) {
      return (
        <Alert
          type="warning"
          showIcon
          message={disclosure === 'minimal'
            ? '部分内容暂不可用'
            : `控件 ${field.fieldType} 尚未映射，请检查字段类型版本`}
        />
      )
    }
    const controlId = `work-item-${layoutSafeId(node.id)}`
    const disabled = presentation === 'read' || projection.mode === 'read'
    return (
      <div className="work-item-layout-preview-field">
        <label htmlFor={controlId}>
          <span>{field.name}</span>
          {projection.required ? <Tag color="purple">必填</Tag> : null}
          {disabled ? <Tag>只读</Tag> : null}
        </label>
        {field.description ? <Typography.Text type="secondary">{field.description}</Typography.Text> : null}
        {presentation === 'read' ? (
          <ReadOnlyValue field={field} value={values[field.fieldKey]} />
        ) : (
          <fieldset disabled={disabled} title={projection.reasonCode}>
            {renderer({
              field,
              id: controlId,
              value: values[field.fieldKey],
              disabled,
              onChange: (value) => onValueChange(field.fieldKey, value),
            })}
          </fieldset>
        )}
      </div>
    )
  }
  if (node.nodeType === 'summary') {
    return (
      <div className="work-item-layout-preview-summary">
        <Typography.Text type="secondary">摘要区域将在工作项实例中展示</Typography.Text>
      </div>
    )
  }
  const title = String(node.config.title ?? node.nodeKey)
  return (
    <section className={`work-item-layout-preview-group is-${node.nodeType}`} aria-labelledby={`group-${node.id}`}>
      <Typography.Title id={`group-${node.id}`} level={5}>{title}</Typography.Title>
      <div className="work-item-layout-preview-children">
        {nested.map((child) => (
          <LayoutPreviewNode
            key={child.id}
            node={child}
            children={children}
            fieldById={fieldById}
            accessProjection={accessProjection}
            values={values}
            presentation={presentation}
            disclosure={disclosure}
            onValueChange={onValueChange}
          />
        ))}
      </div>
    </section>
  )
}

function ReadOnlyValue({ field, value }: { field: RenderableField; value: unknown }) {
  if (value == null || value === '' || (Array.isArray(value) && value.length === 0)) {
    return <Typography.Text type="secondary">未填写</Typography.Text>
  }
  if (field.fieldType === 'boolean') {
    return <Tag color={value ? 'green' : 'default'}>{value ? '是' : '否'}</Tag>
  }
  if (field.fieldType === 'single_select' && typeof value === 'string') {
    const option = field.options?.find((item) => item.optionKey === value)
    return <Tag color={option?.color}>{option?.name ?? value}</Tag>
  }
  if (field.fieldType === 'url' && typeof value === 'string') {
    return <Typography.Link href={value} target="_blank" rel="noreferrer">{value}</Typography.Link>
  }
  if (['date', 'datetime'].includes(field.fieldType) && typeof value === 'string') {
    const parsed = dayjs(value)
    return (
      <Typography.Text>
        {parsed.isValid()
          ? parsed.format(field.fieldType === 'date' ? 'YYYY-MM-DD' : 'YYYY-MM-DD HH:mm')
          : value}
      </Typography.Text>
    )
  }
  if (Array.isArray(value)) {
    return (
      <div className="work-item-layout-read-values">
        {value.map((item, index) => {
          const option = field.options?.find((candidate) => candidate.optionKey === item)
          const label = option?.name ?? displayItem(item)
          return <Tag color={option?.color} key={`${label}-${index}`}>{label}</Tag>
        })}
      </div>
    )
  }
  if (typeof value === 'object') {
    return <Typography.Text>{displayItem(value)}</Typography.Text>
  }
  return <Typography.Text>{String(value)}</Typography.Text>
}

function selectOptions(field: RenderableField) {
  return (field.options ?? [])
    .filter((option) => option.status === 'active')
    .map((option) => ({ label: option.name, value: option.optionKey }))
}

function stringValue(value: unknown) {
  return typeof value === 'string' ? value : ''
}

function dateValue(value: unknown) {
  if (typeof value !== 'string') return null
  const parsed = dayjs(value)
  return parsed.isValid() ? parsed : null
}

function displayItem(value: unknown) {
  if (value && typeof value === 'object') {
    const item = value as Record<string, unknown>
    const label = item.name ?? item.title ?? item.displayName ?? item.fileName ?? item.id
    return typeof label === 'string' ? label : '对象'
  }
  return String(value)
}

function layoutSafeId(value: string) {
  return value.replace(/[^a-zA-Z0-9_-]/g, '')
}

function childMap(nodes: WorkItemLayoutNode[]) {
  const result = new Map<string | null, WorkItemLayoutNode[]>()
  nodes.forEach((node) => {
    const values = result.get(node.parentId) ?? []
    values.push(node)
    result.set(node.parentId, values)
  })
  result.forEach((values) => values.sort((left, right) => left.sortOrder - right.sortOrder))
  return result
}
