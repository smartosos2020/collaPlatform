import { Alert, DatePicker, Input, InputNumber, Select, Switch, Tag, Typography } from 'antd'
import dayjs from 'dayjs'
import type { CSSProperties, ReactNode } from 'react'

import { safeExternalHref } from '../../../shared/url/safeUrl'
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
import { fieldControlLabel } from '../workItemFieldControls'

type RenderableField = Pick<
  ConfiguredWorkItemField,
  'id' | 'fieldKey' | 'name' | 'description' | 'fieldType' | 'config'
> & {
  options?: WorkItemFieldOption[]
}

export type WorkItemLayoutValues = Record<string, unknown>

export type WorkItemSubjectOption = {
  value: string
  label: string
  subjectType: 'member' | 'department' | 'user_group'
}

export type WorkItemLayoutEditorOptions = {
  selectedNodeId?: string
  onSelectNode: (node: WorkItemLayoutNode) => void
  onDragNode?: (node: WorkItemLayoutNode) => void
  onDropNode?: (node: WorkItemLayoutNode) => void
}

type ControlContext = {
  field: RenderableField
  id: string
  value: unknown
  disabled: boolean
  subjectOptions: WorkItemSubjectOption[]
  subjectLoading: boolean
  onChange: (value: unknown) => void
}

type ControlRenderer = (context: ControlContext) => ReactNode

const controls: Partial<Record<WorkItemFieldType, ControlRenderer>> = {
  text: ({ field, id, value, disabled, onChange }) => {
    const presentation = String(field.config.typeConfig?.presentation ?? 'single_line')
    const maxLength = Number(field.config.typeConfig?.maxLength ?? 2000)
    if (presentation === 'rich_text' || presentation === 'multiline') return (
      <Input.TextArea
        id={id}
        value={stringValue(value)}
        disabled={disabled}
        maxLength={maxLength}
        autoSize={{ minRows: presentation === 'rich_text' ? 6 : 3, maxRows: presentation === 'rich_text' ? 14 : 8 }}
        placeholder={`请输入${field.name}`}
        onChange={(event) => onChange(event.target.value)}
      />
    )
    return (
      <Input
        id={id}
        type={presentation === 'email' ? 'email' : presentation === 'phone' ? 'tel' : 'text'}
        value={stringValue(value)}
        disabled={disabled}
        maxLength={maxLength}
        placeholder={`请输入${field.name}`}
        onChange={(event) => onChange(event.target.value)}
      />
    )
  },
  number: ({ field, id, value, disabled, onChange }) => {
    const presentation = String(field.config.typeConfig?.presentation ?? 'number')
    const precision = Number(field.config.typeConfig?.precision ?? 0)
    const ratingMax = Number(field.config.typeConfig?.ratingMax ?? 5)
    const durationUnit = durationUnitLabel(String(field.config.typeConfig?.durationUnit ?? 'hours'))
    return (
      <InputNumber
        id={id}
        value={typeof value === 'number' ? value : null}
        disabled={disabled}
        min={presentation === 'rating' ? 1 : undefined}
        max={presentation === 'rating' ? ratingMax : undefined}
        precision={precision}
        prefix={presentation === 'currency' ? currencySymbol(String(field.config.typeConfig?.currencyCode ?? 'CNY')) : undefined}
        suffix={presentation === 'percentage'
          ? '%'
          : presentation === 'duration'
            ? durationUnit
            : presentation === 'rating'
              ? `/ ${ratingMax}`
              : undefined}
        style={{ width: '100%' }}
        placeholder={`请输入${field.name}`}
        onChange={onChange}
      />
    )
  },
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
      showSearch
      optionFilterProp="label"
      style={{ width: '100%' }}
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
      showSearch
      optionFilterProp="label"
      style={{ width: '100%' }}
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
  user: ({ field, id, value, disabled, subjectOptions, subjectLoading, onChange }) => (
    <Select
      id={id}
      aria-label={field.name}
      mode="multiple"
      value={Array.isArray(value) ? value : []}
      disabled={disabled}
      showSearch
      loading={subjectLoading}
      optionFilterProp="label"
      options={userOptions(field, subjectOptions)}
      maxCount={userMaxSelections(field)}
      style={{ width: '100%' }}
      placeholder="选择成员、部门或用户组"
      notFoundContent={subjectLoading ? '正在加载成员目录' : '没有匹配的成员'}
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
      style={{ width: '100%' }}
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
  subjectOptions = [],
  subjectLoading = false,
  editor,
}: {
  layout: { layoutKind: string; nodes: WorkItemLayoutNode[] }
  fields: Array<ConfiguredWorkItemField | WorkItemLayoutProjectionField>
  accessProjection: Record<string, WorkItemFieldAccessProjection>
  values?: WorkItemLayoutValues
  onValuesChange?: (values: WorkItemLayoutValues) => void
  presentation?: 'edit' | 'read'
  disclosure?: 'detailed' | 'minimal'
  subjectOptions?: WorkItemSubjectOption[]
  subjectLoading?: boolean
  editor?: WorkItemLayoutEditorOptions
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
      className={`work-item-layout-preview${editor ? ' work-item-layout-editor-preview' : ''}`}
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
          subjectOptions={subjectOptions}
          subjectLoading={subjectLoading}
          editor={editor}
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
  subjectOptions,
  subjectLoading,
  editor,
  onValueChange,
}: {
  node: WorkItemLayoutNode
  children: Map<string | null, WorkItemLayoutNode[]>
  fieldById: Map<string, RenderableField>
  accessProjection: Record<string, WorkItemFieldAccessProjection>
  values: WorkItemLayoutValues
  presentation: 'edit' | 'read'
  disclosure: 'detailed' | 'minimal'
  subjectOptions: WorkItemSubjectOption[]
  subjectLoading: boolean
  editor?: WorkItemLayoutEditorOptions
  onValueChange: (fieldKey: string, value: unknown) => void
}) {
  const nested = children.get(node.id) ?? []
  if (node.nodeType === 'field') {
    const field = node.fieldId ? fieldById.get(node.fieldId) : undefined
    if (!field) {
      return (
        <EditorNodeFrame node={node} label={`字段 · ${node.fieldKey ?? node.nodeKey}`} editor={editor}>
          <Alert
            type="warning"
            showIcon
            message={disclosure === 'minimal'
              ? '部分内容暂不可用'
              : `字段 ${node.fieldKey ?? node.nodeKey} 不可用，请在布局配置中重新绑定`}
          />
        </EditorNodeFrame>
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
        <EditorNodeFrame node={node} label={`字段 · ${field.name}`} editor={editor}>
          <Alert
            type="warning"
            showIcon
            message={disclosure === 'minimal'
              ? '部分内容暂不可用'
              : `控件 ${field.fieldType} 尚未映射，请检查字段类型版本`}
          />
        </EditorNodeFrame>
      )
    }
    const controlId = `work-item-${layoutSafeId(node.id)}`
    const disabled = presentation === 'read' || projection.mode === 'read'
    const labelPosition = layoutFieldLabelPosition(node.config.labelPosition)
    const controlWidth = layoutFieldControlWidth(node.config.controlWidth)
    const showDescription = Boolean(field.description)
      && (Boolean(editor) || !isGeneratedControlDescription(field))
    return (
      <EditorNodeFrame node={node} label={`字段 · ${field.name}`} editor={editor}>
        <div
          className={`work-item-layout-preview-field is-label-${labelPosition}`}
          style={{ '--work-item-control-width': `${controlWidth}%` } as CSSProperties}
        >
          <div className="work-item-layout-preview-field-copy">
            <label htmlFor={controlId}>
              <span>{field.name}</span>
              {projection.required ? <Tag color="purple">必填</Tag> : null}
              {disabled ? <Tag>只读</Tag> : null}
            </label>
            {showDescription ? <Typography.Text type="secondary">{field.description}</Typography.Text> : null}
          </div>
          <div className="work-item-layout-preview-control">
            {presentation === 'read' ? (
              <ReadOnlyValue field={field} value={values[field.fieldKey]} />
            ) : (
              <fieldset disabled={disabled} title={projection.reasonCode}>
                {renderer({
                  field,
                  id: controlId,
                  value: values[field.fieldKey],
                  disabled,
                  subjectOptions,
                  subjectLoading,
                  onChange: (value) => onValueChange(field.fieldKey, value),
                })}
              </fieldset>
            )}
          </div>
        </div>
      </EditorNodeFrame>
    )
  }
  if (node.nodeType === 'summary') {
    return (
      <EditorNodeFrame node={node} label="摘要" editor={editor}>
        <div className="work-item-layout-preview-summary">
          <Typography.Text type="secondary">摘要区域将在工作项实例中展示</Typography.Text>
        </div>
      </EditorNodeFrame>
    )
  }
  if (node.nodeType === 'relation') {
    return (
      <EditorNodeFrame
        node={node}
        label={`关系控件 · ${String(node.config.title ?? node.nodeKey)}`}
        editor={editor}
      >
        <div className="work-item-layout-preview-summary" data-relation-key={String(node.config.relationKey ?? '')}>
          <Typography.Text strong>{String(node.config.title ?? node.nodeKey)}</Typography.Text>
          <br />
          <Typography.Text type="secondary">
            {String(node.config.mode ?? 'list')} · {String(node.config.relationKey ?? '')}
          </Typography.Text>
        </div>
      </EditorNodeFrame>
    )
  }
  const title = String(node.config.title ?? node.nodeKey)
  const columns = layoutContainerColumns(node.config.columns)
  return (
    <EditorNodeFrame node={node} label={`${previewNodeLabel(node.nodeType)} · ${title}`} editor={editor}>
      <section className={`work-item-layout-preview-group is-${node.nodeType}`} aria-labelledby={`group-${node.id}`}>
        <Typography.Title id={`group-${node.id}`} level={5}>{title}</Typography.Title>
        <div
          className="work-item-layout-preview-children"
          style={node.nodeType === 'section' || node.nodeType === 'tab'
            ? { '--work-item-layout-columns': columns } as CSSProperties
            : undefined}
        >
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
              subjectOptions={subjectOptions}
              subjectLoading={subjectLoading}
              editor={editor}
              onValueChange={onValueChange}
            />
          ))}
        </div>
      </section>
    </EditorNodeFrame>
  )
}

function EditorNodeFrame({
  node,
  label,
  editor,
  children,
}: {
  node: WorkItemLayoutNode
  label: string
  editor?: WorkItemLayoutEditorOptions
  children: ReactNode
}) {
  if (!editor) return children
  const selected = editor.selectedNodeId === node.id
  return (
    <div
      className={`work-item-layout-editor-node is-${node.nodeType}${selected ? ' active' : ''}`}
      role="group"
      tabIndex={0}
      draggable
      aria-label={`选择${label}`}
      data-selected={selected || undefined}
      onClick={(event) => {
        event.stopPropagation()
        editor.onSelectNode(node)
      }}
      onKeyDown={(event) => {
        if (event.key !== 'Enter' && event.key !== ' ') return
        event.preventDefault()
        event.stopPropagation()
        editor.onSelectNode(node)
      }}
      onDragStart={(event) => {
        event.stopPropagation()
        editor.onDragNode?.(node)
      }}
      onDragOver={(event) => event.preventDefault()}
      onDrop={(event) => {
        event.preventDefault()
        event.stopPropagation()
        editor.onDropNode?.(node)
      }}
    >
      <span className="work-item-layout-editor-node-label">{label}</span>
      {children}
    </div>
  )
}

function previewNodeLabel(type: WorkItemLayoutNode['nodeType']) {
  return ({ section: '区块', tab: '标签页', column: '分栏', field: '字段', relation: '关系控件', summary: '摘要' } as const)[type]
}

function layoutContainerColumns(value: unknown) {
  const columns = Number(value)
  return Number.isInteger(columns) && columns >= 1 && columns <= 4 ? columns : 2
}

function layoutFieldLabelPosition(value: unknown) {
  return value === 'left' ? 'left' : 'top'
}

function layoutFieldControlWidth(value: unknown) {
  const width = Number(value)
  return [25, 33, 50, 67, 75, 100].includes(width) ? width : 100
}

function isGeneratedControlDescription(field: RenderableField) {
  return field.description.trim() === `${fieldControlLabel(field.fieldType, field.config.typeConfig)}控件`
}

function ReadOnlyValue({ field, value }: { field: RenderableField; value: unknown }) {
  if (value == null || value === '' || (Array.isArray(value) && value.length === 0)) {
    return <Typography.Text type="secondary">未填写</Typography.Text>
  }
  if (field.fieldType === 'boolean') {
    return <Tag color={value ? 'green' : 'default'}>{value ? '是' : '否'}</Tag>
  }
  if (field.fieldType === 'number' && typeof value === 'number') {
    return <Typography.Text>{formatNumberValue(field, value)}</Typography.Text>
  }
  if (field.fieldType === 'single_select' && typeof value === 'string') {
    const option = field.options?.find((item) => item.optionKey === value)
    return <Tag color={option?.color}>{option?.name ?? value}</Tag>
  }
  if (field.fieldType === 'url' && typeof value === 'string') {
    const href = safeExternalHref(value)
    if (!href) {
      return <Typography.Text>{value}</Typography.Text>
    }
    return <Typography.Link href={href} target="_blank" rel="noreferrer">{value}</Typography.Link>
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

function userOptions(field: RenderableField, options: WorkItemSubjectOption[]) {
  const typeConfig = field.config.typeConfig ?? {}
  const allowedSubjectTypes = Array.isArray(typeConfig.allowedSubjectTypes)
    ? typeConfig.allowedSubjectTypes.map(String)
    : ['member']
  const selectionScope = Array.isArray(typeConfig.selectionScope)
    ? typeConfig.selectionScope.filter(isSubjectScopeEntry)
    : []
  return options
    .filter((option) => allowedSubjectTypes.includes(option.subjectType))
    .filter((option) => selectionScope.length === 0 || selectionScope.some((scope) => (
      scope.subjectType === option.subjectType && scope.subjectId === option.value
    )))
}

function userMaxSelections(field: RenderableField) {
  const maxSelections = Number(field.config.typeConfig?.maxSelections)
  return Number.isInteger(maxSelections) && maxSelections > 0 ? maxSelections : undefined
}

function isSubjectScopeEntry(value: unknown): value is { subjectType: string; subjectId: string } {
  if (!value || typeof value !== 'object') return false
  const entry = value as Record<string, unknown>
  return typeof entry.subjectType === 'string' && typeof entry.subjectId === 'string'
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

function formatNumberValue(field: RenderableField, value: number) {
  const presentation = String(field.config.typeConfig?.presentation ?? 'number')
  const precision = Number(field.config.typeConfig?.precision ?? 0)
  if (presentation === 'currency') {
    const currency = String(field.config.typeConfig?.currencyCode ?? 'CNY')
    return new Intl.NumberFormat('zh-CN', { style: 'currency', currency, maximumFractionDigits: precision }).format(value)
  }
  if (presentation === 'percentage') return `${value.toFixed(precision)}%`
  if (presentation === 'duration') return `${value.toFixed(precision)} ${durationUnitLabel(String(field.config.typeConfig?.durationUnit ?? 'hours'))}`
  if (presentation === 'rating') return `${value} / ${Number(field.config.typeConfig?.ratingMax ?? 5)}`
  return value.toFixed(precision)
}

function currencySymbol(code: string) {
  return ({ CNY: '¥', USD: '$', EUR: '€', GBP: '£', JPY: '¥' } as Record<string, string>)[code] ?? code
}

function durationUnitLabel(unit: string) {
  return ({ minutes: '分钟', hours: '小时', days: '天' } as Record<string, string>)[unit] ?? unit
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
