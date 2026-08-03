import {
  DeleteOutlined,
  PlusOutlined,
  SafetyCertificateOutlined,
  SaveOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery } from '@tanstack/react-query'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Input,
  InputNumber,
  Select,
  Space,
  Tag,
  Typography,
} from 'antd'
import { useMemo, useState, type ReactNode } from 'react'

import {
  saveWorkItemConfigurationDraft,
  type WorkItemConfigurationDraft,
} from '../api/workItemConfigurationApi'
import { listProjectSpaceMembers } from '../api/projectSpacesApi'
import { errorMessage } from '../projectSpaceView'
import { CollapsibleWorkItemCard } from './CollapsibleWorkItemCard'
import {
  isAvailableScopeKind,
  PERMISSION_ACTION_DEFINITIONS,
  PERMISSION_SCOPE_DEFINITIONS,
  PERMISSION_SUBJECT_DEFINITIONS,
  replacePrimarySubject,
  scopeForKind,
  semanticOptionLabel,
  subjectForKind,
  type PermissionDataScope,
  type PermissionSemanticDefinition,
  type PermissionSubjectSelector,
} from './permissionPolicySemantics'

type SpaceRole = {
  roleKey: string
  name: string
  inheritedRoleKeys: string[]
  actionKeys: string[]
  system: boolean
  sortOrder: number
}

type WorkItemRole = {
  roleKey: string
  name: string
  sourceKinds: string[]
  multiple: boolean
  system: boolean
  sortOrder: number
}

type Policy = {
  policyKey: string
  effect: 'allow' | 'deny'
  actionKeys: string[]
  subjectSelectors: PermissionSubjectSelector[]
  dataScope: PermissionDataScope
  fieldKeys: string[]
  nodeKeys: string[]
  relationKeys: string[]
  priority: number
  system: boolean
  sortOrder: number
}

type PermissionModel = {
  schemaVersion: number
  boundTypeKey?: string
  denyOverridesAllow: boolean
  spaceRoleDefinitions: SpaceRole[]
  workItemRoleDefinitions: WorkItemRole[]
  permissionPolicies: Policy[]
  legacyMappings: unknown[]
}

type SnapshotField = {
  fieldKey: string
  name: string
  status?: string
  sortOrder?: number
}

type SnapshotRelation = {
  relationKey: string
  forwardName: string
  reverseName?: string
  sortOrder?: number
}

export function ProjectWorkItemPermissionPolicyEditor({
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
  const persisted = useMemo(() => model(snapshot.permissionModel), [snapshot.permissionModel])
  const [value, setValue] = useState<PermissionModel>(persisted)
  const fields = useMemo(() => snapshotFields(snapshot.fields), [snapshot.fields])
  const relations = useMemo(
    () => snapshotRelations(snapshot.relationDefinitions),
    [snapshot.relationDefinitions],
  )
  const needsMemberDirectory = value.permissionPolicies.some((policy) =>
    policy.subjectSelectors.some((selector) => selector.kind === 'user'))
  const membersQuery = useQuery({
    queryKey: ['project-space-permission-subject-members', spaceId],
    queryFn: () => listProjectSpaceMembers(spaceId),
    enabled: needsMemberDirectory,
    staleTime: 30_000,
  })
  const diagnostics = validate(value)
  const dirty = JSON.stringify(value) !== JSON.stringify(persisted)
  const mutation = useMutation({
    mutationFn: () => saveWorkItemConfigurationDraft(
      spaceId,
      typeId,
      {
        ...snapshot,
        snapshotSchemaVersion: 5,
        permissionModel: value,
      },
      draft.aggregateVersion,
    ),
    onSuccess: (saved) => {
      onDraftSaved(saved)
      message.success('权限模型已保存到配置草稿')
    },
    onError: (error) => message.error(errorMessage(error, '保存权限模型失败，当前输入已保留')),
  })
  const updatePolicy = (index: number, patch: Partial<Policy>) => {
    setValue((current) => ({
      ...current,
      permissionPolicies: current.permissionPolicies.map((item, itemIndex) =>
        itemIndex === index ? { ...item, ...patch } : item),
    }))
  }
  const addPolicy = () => setValue((current) => {
    const sortOrder = current.permissionPolicies.length
    return {
      ...current,
      permissionPolicies: [...current.permissionPolicies, {
        policyKey: `custom_policy_${sortOrder + 1}`,
        effect: 'deny',
        actionKeys: ['view'],
        subjectSelectors: [{ kind: 'space_role', key: 'guest', subjectId: null }],
        dataScope: { kind: 'all', values: [] },
        fieldKeys: [],
        nodeKeys: [],
        relationKeys: [],
        priority: 500,
        system: false,
        sortOrder,
      }],
    }
  })
  const memberOptions = (membersQuery.data ?? [])
    .filter((member) => member.effective)
    .map((member) => ({
      value: member.userId,
      label: `${member.displayName || member.username} · @${member.username}`,
    }))
  const fieldOptions = fields.map((field) => ({
    value: field.fieldKey,
    label: `${field.name} · ${field.fieldKey}${field.status && field.status !== 'active' ? `（${fieldStatusName(field.status)}）` : ''}`,
  }))
  const relationOptions = relations.map((relation) => ({
    value: relation.relationKey,
    label: `${relation.forwardName}${relation.reverseName ? ` / ${relation.reverseName}` : ''} · ${relation.relationKey}`,
  }))
  const updateSubjectKind = (index: number, policy: Policy, kind: string) => {
    updatePolicy(index, {
      subjectSelectors: replacePrimarySubject(
        policy.subjectSelectors,
        subjectForKind(
          kind,
          value.spaceRoleDefinitions.map((role) => role.roleKey),
          value.workItemRoleDefinitions.map((role) => role.roleKey),
        ),
      ),
    })
  }
  const updateSubject = (
    index: number,
    policy: Policy,
    selector: PermissionSubjectSelector,
  ) => updatePolicy(index, {
    subjectSelectors: replacePrimarySubject(policy.subjectSelectors, selector),
  })
  const renderSubjectTarget = (policy: Policy, index: number) => {
    const selector = policy.subjectSelectors[0]
    const locked = readOnly || policy.subjectSelectors.length > 1
    if (!selector) {
      return (
        <div className="work-item-permission-static-value is-warning">
          请先选择主体类型
        </div>
      )
    }
    if (selector.kind === 'space_role') {
      return (
        <Select
          aria-label="主体角色"
          value={selector.key ?? undefined}
          disabled={locked}
          showSearch
          optionFilterProp="label"
          placeholder="选择空间角色"
          options={withLegacyOption(
            value.spaceRoleDefinitions
              .toSorted((left, right) => left.sortOrder - right.sortOrder)
              .map((role) => ({ value: role.roleKey, label: spaceRoleLabel(role) })),
            selector.key,
            '既有空间角色',
          )}
          onChange={(key) => updateSubject(index, policy, {
            kind: selector.kind,
            key,
            subjectId: null,
          })}
        />
      )
    }
    if (selector.kind === 'work_item_role') {
      return (
        <Select
          aria-label="事项身份"
          value={selector.key ?? undefined}
          disabled={locked}
          showSearch
          optionFilterProp="label"
          placeholder="选择事项身份"
          options={withLegacyOption(
            value.workItemRoleDefinitions
              .toSorted((left, right) => left.sortOrder - right.sortOrder)
              .map((role) => ({
                value: role.roleKey,
                label: `${role.roleKey === 'creator' ? '工作项创建人' : role.name} · ${role.roleKey}`,
                disabled: role.roleKey !== 'creator' && selector.key !== role.roleKey,
              })),
            selector.key,
            '既有事项身份',
          )}
          onChange={(key) => updateSubject(index, policy, {
            kind: selector.kind,
            key,
            subjectId: null,
          })}
        />
      )
    }
    if (selector.kind === 'user') {
      return (
        <Select
          aria-label="空间成员"
          value={selector.subjectId ?? undefined}
          disabled={locked}
          loading={membersQuery.isFetching}
          status={membersQuery.isError ? 'error' : undefined}
          showSearch
          optionFilterProp="label"
          placeholder={membersQuery.isError ? '成员加载失败，请重试' : '选择空间成员'}
          options={withLegacyOption(memberOptions, selector.subjectId, '既有成员')}
          onChange={(subjectId) => updateSubject(index, policy, {
            kind: selector.kind,
            key: null,
            subjectId,
          })}
        />
      )
    }
    if (selector.kind === 'everyone') {
      return (
        <div className="work-item-permission-static-value">
          所有可进入当前空间的用户
        </div>
      )
    }
    return (
      <div className="work-item-permission-static-value is-compatibility">
        <span>兼容既有配置，当前不可新建</span>
        <code>{selector.key || selector.subjectId || '未设置'}</code>
      </div>
    )
  }

  return (
    <CollapsibleWorkItemCard
      collapseLabel="数据权限策略"
      className="content-card work-item-permission-policy-editor"
      title={<Space><SafetyCertificateOutlined />数据权限策略</Space>}
      extra={(
        <Space wrap>
          {dirty ? <Tag color="warning">未保存</Tag> : <Tag color="success">已同步</Tag>}
          <Button icon={<PlusOutlined />} disabled={readOnly} onClick={addPolicy}>新增策略</Button>
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
        按“允许或拒绝谁，对哪些工作项执行哪些操作”配置。中文名称用于操作，编码仅作为辅助标识；拒绝策略始终优先。
      </Typography.Paragraph>
      <div className="work-item-permission-role-summary" aria-label="权限角色摘要">
        <Tag color="blue">空间角色 {value.spaceRoleDefinitions.length}</Tag>
        <Tag color="purple">事项角色 {value.workItemRoleDefinitions.length}</Tag>
        <Tag color="gold">策略 {value.permissionPolicies.length}</Tag>
        <Tag color="red">拒绝优先</Tag>
      </div>
      <div className="work-item-permission-role-list">
        {value.spaceRoleDefinitions.map((role) => (
          <Tag key={role.roleKey}>{spaceRoleLabel(role)}</Tag>
        ))}
        {value.workItemRoleDefinitions.map((role) => (
          <Tag color="purple" key={role.roleKey}>
            {role.roleKey === 'creator' ? '工作项创建人' : role.name} · {role.roleKey}
          </Tag>
        ))}
      </div>
      {diagnostics.length ? (
        <Alert
          type="error"
          showIcon
          icon={<WarningOutlined />}
          message="权限策略存在即时诊断"
          description={diagnostics.join('；')}
        />
      ) : null}
      <div className="work-item-permission-policy-list">
        {value.permissionPolicies.map((policy, index) => {
          const selector = policy.subjectSelectors[0]
          const subjectLocked = readOnly || policy.subjectSelectors.length > 1
          const showFieldQualifier = policy.fieldKeys.length > 0
            || policy.actionKeys.some((action) => action === 'field_read' || action === 'field_write')
          const showRelationQualifier = policy.relationKeys.length > 0
            || policy.actionKeys.some((action) => action === 'relate' || action === 'accept_link')
          const showQualifierRow = showFieldQualifier || showRelationQualifier || policy.nodeKeys.length > 0
          const unsupportedScope = !isAvailableScopeKind(policy.dataScope.kind)
          return (
            <Card
              className="work-item-permission-policy-card"
              data-policy-index={index + 1}
              size="small"
              key={`${policy.policyKey}:${index}`}
              role="group"
              aria-label={`权限策略 ${policy.policyKey}`}
              title={(
                <Space wrap>
                  <Tag color={policy.effect === 'deny' ? 'red' : 'green'}>
                    {policy.effect === 'deny' ? '拒绝' : '允许'}
                  </Tag>
                  <code>{policy.policyKey}</code>
                  {policy.system ? <Tag>系统预置</Tag> : null}
                </Space>
              )}
              extra={!policy.system ? (
                <Button
                  danger
                  type="text"
                  aria-label={`删除权限策略 ${policy.policyKey}`}
                  icon={<DeleteOutlined />}
                  disabled={readOnly}
                  onClick={() => setValue((current) => ({
                    ...current,
                    permissionPolicies: current.permissionPolicies
                      .filter((_, itemIndex) => itemIndex !== index)
                      .map((item, sortOrder) => ({ ...item, sortOrder })),
                  }))}
                />
              ) : null}
            >
              {policy.subjectSelectors.length > 1 ? (
                <Alert
                  className="work-item-permission-compatibility-alert"
                  type="warning"
                  showIcon
                  message={`此旧策略包含 ${policy.subjectSelectors.length} 个“任一满足”的主体，已锁定主体设置以避免遗漏。`}
                />
              ) : null}
              <div className="work-item-permission-policy-form">
                <div className="work-item-permission-policy-row is-primary">
                  <Field className="is-policy-key" label="策略编码（永久）">
                    <Input
                      aria-label="策略编码"
                      value={policy.policyKey}
                      disabled={readOnly || policy.system}
                      onChange={(event) => updatePolicy(index, {
                        policyKey: semanticKeys(event.target.value)[0] ?? '',
                      })}
                    />
                  </Field>
                  <Field className="is-effect" label="判定结果">
                    <Select
                      aria-label="判定结果"
                      value={policy.effect}
                      disabled={readOnly}
                      options={[{ value: 'allow', label: '允许' }, { value: 'deny', label: '拒绝' }]}
                      onChange={(effect) => updatePolicy(index, { effect })}
                    />
                  </Field>
                  <Field className="is-priority" label="解释排序权重">
                    <InputNumber
                      aria-label="解释排序权重"
                      min={0}
                      max={10_000}
                      value={policy.priority}
                      disabled={readOnly}
                      onChange={(priority) => updatePolicy(index, { priority: priority ?? 0 })}
                    />
                  </Field>
                  <Field className="is-actions" label="允许或拒绝的操作">
                    <Select
                      mode="multiple"
                      aria-label="操作"
                      value={policy.actionKeys}
                      disabled={readOnly}
                      showSearch
                      optionFilterProp="label"
                      maxTagCount="responsive"
                      options={semanticOptions(PERMISSION_ACTION_DEFINITIONS, policy.actionKeys)}
                      onChange={(actionKeys) => updatePolicy(index, { actionKeys })}
                    />
                  </Field>
                </div>
                <div className={`work-item-permission-policy-row is-subject-scope${unsupportedScope ? '' : ' is-scope-all'}`}>
                  <Field className="is-subject-type" label="谁（主体类型）">
                    <Select
                      aria-label="主体类型"
                      value={selector?.kind}
                      disabled={subjectLocked}
                      showSearch
                      optionFilterProp="label"
                      placeholder="选择主体类型"
                      options={semanticOptions(
                        PERMISSION_SUBJECT_DEFINITIONS.map((definition) => ({
                          ...definition,
                          available: definition.available
                            && (definition.value !== 'work_item_role'
                              || value.workItemRoleDefinitions.some((role) => role.roleKey === 'creator')),
                        })),
                        selector?.kind ? [selector.kind] : [],
                      )}
                      onChange={(kind) => updateSubjectKind(index, policy, kind)}
                    />
                  </Field>
                  <Field className="is-subject-key" label={subjectTargetLabel(selector?.kind)}>
                    {renderSubjectTarget(policy, index)}
                  </Field>
                  <Field className="is-scope-type" label="哪些工作项（数据范围）">
                    <Select
                      aria-label="数据范围"
                      value={policy.dataScope.kind}
                      disabled={readOnly}
                      showSearch
                      optionFilterProp="label"
                      options={semanticOptions(PERMISSION_SCOPE_DEFINITIONS, [policy.dataScope.kind])}
                      onChange={(kind) => updatePolicy(index, { dataScope: scopeForKind(kind) })}
                    />
                  </Field>
                  {unsupportedScope ? (
                    <Field className="is-scope-values" label="既有范围参数（兼容只读）">
                      <div className="work-item-permission-static-value is-compatibility">
                        <span>切换到可用范围后将清除这些旧参数</span>
                        <code>{scopeCompatibilityValue(policy.dataScope)}</code>
                      </div>
                    </Field>
                  ) : null}
                </div>
                {showQualifierRow ? (
                  <div className="work-item-permission-policy-row is-qualifiers">
                    {showFieldQualifier ? (
                      <Field className="is-field-keys" label="限定字段（空表示全部字段）">
                        <Select
                          mode="multiple"
                          aria-label="限定字段"
                          value={policy.fieldKeys}
                          disabled={readOnly}
                          showSearch
                          optionFilterProp="label"
                          maxTagCount="responsive"
                          placeholder="全部字段"
                          options={withLegacyOptions(fieldOptions, policy.fieldKeys, '既有字段')}
                          onChange={(fieldKeys) => updatePolicy(index, { fieldKeys })}
                        />
                      </Field>
                    ) : null}
                    {showRelationQualifier ? (
                      <Field className="is-relation-keys" label="限定关系（空表示全部关系）">
                        <Select
                          mode="multiple"
                          aria-label="限定关系"
                          value={policy.relationKeys}
                          disabled={readOnly}
                          showSearch
                          optionFilterProp="label"
                          maxTagCount="responsive"
                          placeholder="全部关系"
                          options={withLegacyOptions(relationOptions, policy.relationKeys, '既有关系')}
                          onChange={(relationKeys) => updatePolicy(index, { relationKeys })}
                        />
                      </Field>
                    ) : null}
                    {policy.nodeKeys.length > 0 ? (
                      <Field className="is-node-keys" label="既有节点限定（兼容）">
                        <Select
                          mode="multiple"
                          aria-label="既有节点限定"
                          value={policy.nodeKeys}
                          disabled={readOnly}
                          maxTagCount="responsive"
                          options={policy.nodeKeys.map((nodeKey) => ({
                            value: nodeKey,
                            label: `既有节点 · ${nodeKey}`,
                          }))}
                          onChange={(nodeKeys) => updatePolicy(index, { nodeKeys })}
                        />
                      </Field>
                    ) : null}
                  </div>
                ) : null}
              </div>
            </Card>
          )
        })}
      </div>
    </CollapsibleWorkItemCard>
  )
}

function Field({
  label,
  children,
  className,
}: {
  label: string
  children: ReactNode
  className?: string
}) {
  return (
    <label className={`work-item-permission-field${className ? ` ${className}` : ''}`}>
      <span>{label}</span>
      {children}
    </label>
  )
}

type PermissionSelectOption = {
  value: string
  label: string
  title?: string
  disabled?: boolean
}

function semanticOptions(
  definitions: PermissionSemanticDefinition[],
  currentValues: string[],
): PermissionSelectOption[] {
  const current = new Set(currentValues)
  const known = new Set(definitions.map((definition) => definition.value))
  return [
    ...definitions.map((definition) => ({
      value: definition.value,
      label: `${semanticOptionLabel(definition)}${definition.available ? '' : '（暂不可新建）'}`,
      title: definition.description,
      disabled: !definition.available && !current.has(definition.value),
    })),
    ...currentValues
      .filter((item) => !known.has(item))
      .map((item) => ({
        value: item,
        label: `既有兼容项 · ${item}`,
        title: '未识别的既有编码；切换后不可重新选择',
      })),
  ]
}

function withLegacyOption(
  options: PermissionSelectOption[],
  currentValue: string | null | undefined,
  fallbackLabel: string,
) {
  if (!currentValue || options.some((option) => option.value === currentValue)) return options
  return [...options, { value: currentValue, label: `${fallbackLabel} · ${currentValue}` }]
}

function withLegacyOptions(
  options: PermissionSelectOption[],
  currentValues: string[],
  fallbackLabel: string,
) {
  const known = new Set(options.map((option) => option.value))
  return [
    ...options,
    ...currentValues
      .filter((currentValue) => !known.has(currentValue))
      .map((currentValue) => ({
        value: currentValue,
        label: `${fallbackLabel} · ${currentValue}`,
      })),
  ]
}

function subjectTargetLabel(kind?: string) {
  if (kind === 'space_role') return '空间角色'
  if (kind === 'work_item_role') return '事项身份'
  if (kind === 'user') return '空间成员'
  if (kind === 'everyone') return '主体范围'
  return '既有主体参数'
}

function spaceRoleLabel(role: SpaceRole) {
  const hierarchyLabels: Record<string, string> = {
    guest: '访客及以上',
    member: '成员及以上',
    admin: '管理员及以上',
    owner: '仅空间所有者',
  }
  return `${hierarchyLabels[role.roleKey] ?? role.name} · ${role.roleKey}`
}

function scopeCompatibilityValue(scope: PermissionDataScope) {
  const values = [
    scope.fieldKey ? `字段 ${scope.fieldKey}` : '',
    scope.operator ? `条件 ${scope.operator}` : '',
    ...(scope.values ?? []),
  ].filter(Boolean)
  return values.length > 0 ? values.join('，') : '无附加参数'
}

function snapshotFields(value: unknown): SnapshotField[] {
  if (!Array.isArray(value)) return []
  return value
    .map((item) => asObject(item))
    .filter((item) => typeof item.fieldKey === 'string')
    .map((item) => ({
      fieldKey: item.fieldKey as string,
      name: typeof item.name === 'string' ? item.name : item.fieldKey as string,
      status: typeof item.status === 'string' ? item.status : undefined,
      sortOrder: typeof item.sortOrder === 'number' ? item.sortOrder : undefined,
    }))
    .toSorted((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0))
}

function snapshotRelations(value: unknown): SnapshotRelation[] {
  if (!Array.isArray(value)) return []
  return value
    .map((item) => asObject(item))
    .filter((item) => typeof item.relationKey === 'string')
    .map((item) => ({
      relationKey: item.relationKey as string,
      forwardName: typeof item.forwardName === 'string'
        ? item.forwardName : item.relationKey as string,
      reverseName: typeof item.reverseName === 'string' ? item.reverseName : undefined,
      sortOrder: typeof item.sortOrder === 'number' ? item.sortOrder : undefined,
    }))
    .toSorted((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0))
}

function fieldStatusName(status: string) {
  if (status === 'disabled') return '已停用'
  if (status === 'retired') return '已退役'
  return status
}

function asObject(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}
}

function model(value: unknown): PermissionModel {
  const source = asObject(value)
  return {
    schemaVersion: 1,
    boundTypeKey: typeof source.boundTypeKey === 'string' ? source.boundTypeKey : undefined,
    denyOverridesAllow: true,
    spaceRoleDefinitions: Array.isArray(source.spaceRoleDefinitions)
      ? source.spaceRoleDefinitions as SpaceRole[] : [],
    workItemRoleDefinitions: Array.isArray(source.workItemRoleDefinitions)
      ? source.workItemRoleDefinitions as WorkItemRole[] : [],
    permissionPolicies: Array.isArray(source.permissionPolicies)
      ? source.permissionPolicies as Policy[] : [],
    legacyMappings: Array.isArray(source.legacyMappings) ? source.legacyMappings : [],
  }
}

function semanticKeys(value: string) {
  return [...new Set(value.split(',')
    .map((item) => item.trim().toLowerCase().replace(/[^a-z0-9_]/g, '_'))
    .filter(Boolean))]
}

function validate(value: PermissionModel) {
  const diagnostics: string[] = []
  const keys = new Set<string>()
  for (const policy of value.permissionPolicies) {
    if (!/^[a-z][a-z0-9_]{0,63}$/.test(policy.policyKey)) {
      diagnostics.push(`策略 ${policy.policyKey || '(空)'} 的永久 key 非法`)
    }
    if (keys.has(policy.policyKey)) diagnostics.push(`策略 key ${policy.policyKey} 重复`)
    keys.add(policy.policyKey)
    if (!policy.actionKeys.length) diagnostics.push(`策略 ${policy.policyKey} 至少需要一个动作`)
    if (!policy.subjectSelectors.length) {
      diagnostics.push(`策略 ${policy.policyKey} 至少需要一个主体`)
      continue
    }
    const selector = policy.subjectSelectors[0]
    if (selector.kind === 'space_role'
      && !value.spaceRoleDefinitions.some((role) => role.roleKey === selector.key)) {
      diagnostics.push(`策略 ${policy.policyKey} 需要选择有效的空间角色`)
    }
    if (selector.kind === 'work_item_role'
      && !value.workItemRoleDefinitions.some((role) => role.roleKey === selector.key)) {
      diagnostics.push(`策略 ${policy.policyKey} 需要选择有效的事项身份`)
    }
    if (selector.kind === 'user' && !selector.subjectId) {
      diagnostics.push(`策略 ${policy.policyKey} 需要选择空间成员`)
    }
    if (selector.kind === 'everyone' && (selector.key || selector.subjectId)) {
      diagnostics.push(`策略 ${policy.policyKey} 的“所有用户”主体不能携带角色或用户编码`)
    }
    if (policy.actionKeys.includes('create')) {
      if (policy.dataScope.kind !== 'all') {
        diagnostics.push(`策略 ${policy.policyKey} 包含“新建工作项”时，数据范围必须为“所有工作项”`)
      }
      if (selector.kind === 'work_item_role' || selector.kind === 'participant_role') {
        diagnostics.push(`策略 ${policy.policyKey} 创建前尚无事项身份，请拆分为独立策略`)
      }
      if (policy.fieldKeys.length || policy.nodeKeys.length || policy.relationKeys.length) {
        diagnostics.push(`策略 ${policy.policyKey} 的“新建工作项”不能使用字段、节点或关系限定`)
      }
    }
  }
  return diagnostics
}
