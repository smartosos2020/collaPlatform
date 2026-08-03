import {
  BranchesOutlined,
  DeleteOutlined,
  PlusOutlined,
  SaveOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { useMutation } from '@tanstack/react-query'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Col,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Switch,
  Tag,
  Typography,
} from 'antd'
import { useMemo, useState, type ReactNode } from 'react'

import {
  saveWorkItemConfigurationDraft,
  type WorkItemConfigurationDraft,
} from '../api/workItemConfigurationApi'
import { errorMessage } from '../projectSpaceView'
import { CollapsibleWorkItemCard } from './CollapsibleWorkItemCard'

type RelationDefinition = {
  relationKey: string
  kind: 'normal' | 'parent_child' | 'dependency' | 'blocking'
  direction: 'directed' | 'undirected'
  forwardName: string
  reverseName: string
  sourceTypeKeys: string[]
  targetTypeKeys: string[]
  sourceCardinality: 'one' | 'many'
  targetCardinality: 'one' | 'many'
  deletionPolicy: 'restrict' | 'detach' | 'retain_history'
  allowSelf: boolean
  maxDepth: number
  sortOrder: number
}

export function ProjectWorkItemRelationDefinitionsEditor({
  spaceId,
  typeId,
  readOnly,
  draft,
  onDraftSaved,
}: {
  spaceId: string
  typeId: string
  readOnly: boolean
  draft: WorkItemConfigurationDraft
  onDraftSaved: (draft: WorkItemConfigurationDraft) => void
}) {
  const { message } = AntdApp.useApp()
  const snapshot = asObject(draft.snapshot)
  const persisted = useMemo(
    () => definitions(snapshot.relationDefinitions),
    [snapshot.relationDefinitions],
  )
  const [items, setItems] = useState<RelationDefinition[]>(persisted)
  const diagnostics = validate(items, typeKey(snapshot))
  const dirty = JSON.stringify(items) !== JSON.stringify(persisted)
  const mutation = useMutation({
    mutationFn: () => saveWorkItemConfigurationDraft(
      spaceId,
      typeId,
      {
        ...snapshot,
        snapshotSchemaVersion: Math.max(Number(snapshot.snapshotSchemaVersion ?? 1), 4),
        relationDefinitions: items,
      },
      draft.aggregateVersion,
    ),
    onSuccess: (saved) => {
      onDraftSaved(saved)
      message.success('关系定义已保存到配置草稿')
    },
    onError: (error) => message.error(errorMessage(error, '保存关系定义失败，当前输入已保留')),
  })
  const update = (index: number, patch: Partial<RelationDefinition>) => {
    setItems((current) => current.map((item, itemIndex) =>
      itemIndex === index ? normalize({ ...item, ...patch }, itemIndex) : item))
  }
  const add = () => setItems((current) => [
    ...current,
    normalize({
      relationKey: `relation_${current.length + 1}`,
      kind: 'normal',
      direction: 'directed',
      forwardName: '关联',
      reverseName: '被关联',
      sourceTypeKeys: [typeKey(snapshot)],
      targetTypeKeys: [typeKey(snapshot)],
      sourceCardinality: 'many',
      targetCardinality: 'many',
      deletionPolicy: 'detach',
      allowSelf: false,
      maxDepth: 16,
      sortOrder: current.length,
    }, current.length),
  ])
  return (
    <CollapsibleWorkItemCard
      collapseLabel="关系定义"
      className="content-card work-item-relation-definition-editor"
      title={<Space><BranchesOutlined />关系定义</Space>}
      extra={(
        <Space wrap>
          {dirty ? <Tag color="warning">未保存</Tag> : <Tag color="success">已同步</Tag>}
          <Button icon={<PlusOutlined />} disabled={readOnly} onClick={add}>新增关系</Button>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            disabled={readOnly || !dirty || diagnostics.length > 0}
            loading={mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            保存草稿
          </Button>
        </Space>
      )}
    >
      <Typography.Paragraph type="secondary">
        永久 key 决定历史语义；方向、正反向名称、类型矩阵、基数和删除策略随配置版本发布。发布前可通过兼容检查预览影响。
      </Typography.Paragraph>
      {diagnostics.length ? (
        <Alert
          type="error"
          showIcon
          icon={<WarningOutlined />}
          message="关系定义存在即时诊断"
          description={diagnostics.join('；')}
        />
      ) : null}
      <div className="work-item-relation-definition-list">
        {items.map((item, index) => (
          <Card
            size="small"
            key={`${item.relationKey}:${index}`}
            title={<Space><Tag color="purple">{item.kind}</Tag><code>{item.relationKey}</code></Space>}
            extra={(
              <Button
                danger
                type="text"
                aria-label={`删除关系 ${item.relationKey}`}
                icon={<DeleteOutlined />}
                disabled={readOnly}
                onClick={() => setItems((current) => current.filter((_, itemIndex) => itemIndex !== index)
                  .map((candidate, sortOrder) => ({ ...candidate, sortOrder })))}
              />
            )}
          >
            <Row gutter={[12, 12]}>
              <Col xs={24} md={8}>
                <Field label="永久 relation key">
                  <Input
                    aria-label={`关系 ${index + 1} 永久 key`}
                    value={item.relationKey}
                    disabled={readOnly}
                    onChange={(event) => update(index, { relationKey: event.target.value.toLowerCase() })}
                  />
                </Field>
              </Col>
              <Col xs={12} md={4}>
                <Field label="类型">
                  <Select
                    aria-label={`关系 ${index + 1} 类型`}
                    value={item.kind}
                    disabled={readOnly}
                    options={['normal', 'parent_child', 'dependency', 'blocking']
                      .map((value) => ({ value, label: value }))}
                    onChange={(kind) => update(index, { kind })}
                  />
                </Field>
              </Col>
              <Col xs={12} md={4}>
                <Field label="方向">
                  <Select
                    aria-label={`关系 ${index + 1} 方向`}
                    value={item.direction}
                    disabled={readOnly || item.kind !== 'normal'}
                    options={[
                      { value: 'directed', label: '有向' },
                      { value: 'undirected', label: '无向' },
                    ]}
                    onChange={(direction) => update(index, { direction })}
                  />
                </Field>
              </Col>
              <Col xs={12} md={4}>
                <Field label="源端基数">
                  <Select
                    value={item.sourceCardinality}
                    disabled={readOnly}
                    options={[{ value: 'one', label: 'one' }, { value: 'many', label: 'many' }]}
                    onChange={(sourceCardinality) => update(index, { sourceCardinality })}
                  />
                </Field>
              </Col>
              <Col xs={12} md={4}>
                <Field label="目标端基数">
                  <Select
                    value={item.targetCardinality}
                    disabled={readOnly}
                    options={[{ value: 'one', label: 'one' }, { value: 'many', label: 'many' }]}
                    onChange={(targetCardinality) => update(index, { targetCardinality })}
                  />
                </Field>
              </Col>
              <Col xs={12} md={6}>
                <Field label="正向名称">
                  <Input value={item.forwardName} disabled={readOnly} onChange={(event) => update(index, { forwardName: event.target.value })} />
                </Field>
              </Col>
              <Col xs={12} md={6}>
                <Field label="反向名称">
                  <Input value={item.reverseName} disabled={readOnly} onChange={(event) => update(index, { reverseName: event.target.value })} />
                </Field>
              </Col>
              <Col xs={24} md={6}>
                <Field label="源类型 key（逗号分隔）">
                  <Input value={item.sourceTypeKeys.join(',')} disabled={readOnly} onChange={(event) => update(index, { sourceTypeKeys: keys(event.target.value) })} />
                </Field>
              </Col>
              <Col xs={24} md={6}>
                <Field label="目标类型 key（逗号分隔）">
                  <Input value={item.targetTypeKeys.join(',')} disabled={readOnly} onChange={(event) => update(index, { targetTypeKeys: keys(event.target.value) })} />
                </Field>
              </Col>
              <Col xs={12} md={6}>
                <Field label="删除策略">
                  <Select
                    value={item.deletionPolicy}
                    disabled={readOnly}
                    options={['restrict', 'detach', 'retain_history'].map((value) => ({ value, label: value }))}
                    onChange={(deletionPolicy) => update(index, { deletionPolicy })}
                  />
                </Field>
              </Col>
              <Col xs={12} md={6}>
                <Field label="最大图深度">
                  <InputNumber min={1} max={64} value={item.maxDepth} disabled={readOnly} onChange={(maxDepth) => update(index, { maxDepth: maxDepth ?? 16 })} />
                </Field>
              </Col>
              <Col xs={12} md={6}>
                <Field label="允许自关联">
                  <Switch checked={item.allowSelf} disabled={readOnly || item.kind !== 'normal'} onChange={(allowSelf) => update(index, { allowSelf })} />
                </Field>
              </Col>
            </Row>
          </Card>
        ))}
      </div>
    </CollapsibleWorkItemCard>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return <label className="work-item-relation-definition-field"><span>{label}</span>{children}</label>
}

function asObject(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}
}

function typeKey(snapshot: Record<string, unknown>) {
  const definition = asObject(snapshot.typeDefinition)
  return typeof definition.typeKey === 'string' && definition.typeKey ? definition.typeKey : 'task'
}

function definitions(value: unknown): RelationDefinition[] {
  if (!Array.isArray(value)) return []
  return value.map((item, index) => normalize(asObject(item) as Partial<RelationDefinition>, index))
}

function normalize(value: Partial<RelationDefinition>, sortOrder: number): RelationDefinition {
  const structural = ['parent_child', 'dependency', 'blocking'].includes(value.kind ?? '')
  return {
    relationKey: value.relationKey ?? `relation_${sortOrder + 1}`,
    kind: value.kind ?? 'normal',
    direction: structural ? 'directed' : value.direction ?? 'directed',
    forwardName: value.forwardName ?? '关联',
    reverseName: value.reverseName ?? '被关联',
    sourceTypeKeys: value.sourceTypeKeys ?? [],
    targetTypeKeys: value.targetTypeKeys ?? [],
    sourceCardinality: value.sourceCardinality ?? 'many',
    targetCardinality: value.targetCardinality ?? 'many',
    deletionPolicy: value.deletionPolicy ?? 'detach',
    allowSelf: structural ? false : value.allowSelf ?? false,
    maxDepth: value.maxDepth ?? 16,
    sortOrder,
  }
}

function keys(value: string) {
  return [...new Set(value.split(',').map((item) => item.trim().toLowerCase()).filter(Boolean))]
}

function validate(items: RelationDefinition[], ownerTypeKey: string) {
  const diagnostics: string[] = []
  const relationKeys = new Set<string>()
  items.forEach((item, index) => {
    const label = `第 ${index + 1} 条`
    if (!/^[a-z][a-z0-9_]{0,63}$/.test(item.relationKey) || relationKeys.has(item.relationKey)) {
      diagnostics.push(`${label} key 非法或重复`)
    }
    relationKeys.add(item.relationKey)
    if (!item.forwardName.trim() || !item.reverseName.trim()) diagnostics.push(`${label} 正反向名称不能为空`)
    if (!item.sourceTypeKeys.includes(ownerTypeKey)) diagnostics.push(`${label} 源类型必须包含 ${ownerTypeKey}`)
    if (!item.targetTypeKeys.length) diagnostics.push(`${label} 目标类型不能为空`)
    if (item.maxDepth < 1 || item.maxDepth > 64) diagnostics.push(`${label} 最大深度必须为 1-64`)
  })
  return diagnostics
}
