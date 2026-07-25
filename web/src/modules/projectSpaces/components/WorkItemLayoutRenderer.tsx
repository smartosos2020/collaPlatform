import { Alert, DatePicker, Input, InputNumber, Select, Switch, Tag, Typography } from 'antd'
import type { ReactNode } from 'react'

import type {
  ConfiguredWorkItemField,
  WorkItemFieldType,
} from '../api/workItemFieldsApi'
import type {
  WorkItemFieldAccessProjection,
  WorkItemLayout,
  WorkItemLayoutNode,
} from '../api/workItemLayoutsApi'

type ControlRenderer = (field: ConfiguredWorkItemField) => ReactNode

const controls: Partial<Record<WorkItemFieldType, ControlRenderer>> = {
  text: (field) => <Input placeholder={`请输入${field.name}`} />,
  number: (field) => <InputNumber style={{ width: '100%' }} placeholder={`请输入${field.name}`} />,
  boolean: () => <Switch />,
  single_select: (field) => <Select options={field.options.filter((option) => option.status === 'active').map((option) => ({ label: option.name, value: option.optionKey }))} />,
  multi_select: (field) => <Select mode="multiple" options={field.options.filter((option) => option.status === 'active').map((option) => ({ label: option.name, value: option.optionKey }))} />,
  date: () => <DatePicker style={{ width: '100%' }} />,
  datetime: () => <DatePicker showTime style={{ width: '100%' }} />,
  url: (field) => <Input type="url" placeholder={`请输入${field.name}`} />,
  user: () => <Select mode="multiple" disabled placeholder="成员选择将在运行时连接身份目录" />,
  attachment: () => <Input disabled placeholder="附件控件将在运行时连接文件服务" />,
  work_item_reference: () => <Select mode="multiple" disabled placeholder="工作项引用将在运行时连接实例目录" />,
}

export function WorkItemLayoutRenderer({
  layout,
  fields,
  accessProjection,
}: {
  layout: WorkItemLayout
  fields: ConfiguredWorkItemField[]
  accessProjection: Record<string, WorkItemFieldAccessProjection>
}) {
  const fieldById = new Map(fields.map((field) => [field.id, field]))
  const children = childMap(layout.nodes)
  const roots = children.get(null) ?? []

  return (
    <div className="work-item-layout-preview" aria-label={`${layout.layoutKind} 布局预览`}>
      {roots.map((node) => (
        <LayoutPreviewNode
          key={node.id}
          node={node}
          children={children}
          fieldById={fieldById}
          accessProjection={accessProjection}
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
}: {
  node: WorkItemLayoutNode
  children: Map<string | null, WorkItemLayoutNode[]>
  fieldById: Map<string, ConfiguredWorkItemField>
  accessProjection: Record<string, WorkItemFieldAccessProjection>
}) {
  const nested = children.get(node.id) ?? []
  if (node.nodeType === 'field') {
    const field = node.fieldId ? fieldById.get(node.fieldId) : undefined
    if (!field) return <Alert type="warning" showIcon message={`字段 ${node.fieldKey ?? node.nodeKey} 不可用`} />
    const projection = accessProjection[field.fieldKey] ?? { mode: 'write' as const, required: false }
    if (projection.mode === 'hidden') return null
    const renderer = controls[field.fieldType]
    if (!renderer) return <Alert type="warning" showIcon message={`控件 ${field.fieldType} 暂不支持`} />
    return (
      <div className="work-item-layout-preview-field">
        <span>
          {field.name}
          {field.config.required || projection.required ? <Tag color="purple">必填</Tag> : null}
          {projection.mode === 'read' ? <Tag>只读</Tag> : null}
        </span>
        <fieldset disabled={projection.mode === 'read'} title={projection.reasonCode}>
          {renderer(field)}
        </fieldset>
      </div>
    )
  }
  if (node.nodeType === 'summary') {
    return <div className="work-item-layout-preview-summary"><Typography.Text type="secondary">摘要区域</Typography.Text></div>
  }
  const title = String(node.config.title ?? node.nodeKey)
  return (
    <section className={`work-item-layout-preview-group is-${node.nodeType}`}>
      <Typography.Title level={5}>{title}</Typography.Title>
      <div className="work-item-layout-preview-children">
        {nested.map((child) => (
          <LayoutPreviewNode
            key={child.id}
            node={child}
            children={children}
            fieldById={fieldById}
            accessProjection={accessProjection}
          />
        ))}
      </div>
    </section>
  )
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
