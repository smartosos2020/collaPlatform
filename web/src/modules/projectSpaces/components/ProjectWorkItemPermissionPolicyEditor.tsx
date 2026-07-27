import {
  DeleteOutlined,
  PlusOutlined,
  SafetyCertificateOutlined,
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
  Tag,
  Typography,
} from 'antd'
import { useMemo, useState, type ReactNode } from 'react'

import {
  saveWorkItemConfigurationDraft,
  type WorkItemConfigurationDraft,
} from '../api/workItemConfigurationApi'
import { errorMessage } from '../projectSpaceView'

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
  subjectSelectors: Array<{ kind: string; key?: string | null; subjectId?: string | null }>
  dataScope: { kind: string; fieldKey?: string | null; operator?: string | null; values?: string[] }
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

const ACTIONS = [
  'create', 'view', 'edit', 'archive', 'restore', 'delete', 'comment', 'attach',
  'participant_manage', 'transition', 'workflow_manage', 'relate', 'accept_link',
  'relation_manage', 'field_read', 'field_write', 'role_assign', 'policy_manage',
  'permission_explain', 'permission_request', 'governance_inspect', 'migration_manage',
]
const SUBJECT_KINDS = [
  'enterprise_role', 'space_role', 'work_item_role', 'participant_role',
  'user', 'department', 'user_group', 'everyone',
]
const SCOPE_KINDS = [
  'all', 'created_by_subject', 'participating', 'work_item_role', 'field_match', 'explicit_set',
]

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

  return (
    <Card
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
        策略随不可变配置版本发布。拒绝优先；字段、节点、关系限定和 data scope 只能收紧对象访问，不能反向授权。
      </Typography.Paragraph>
      <div className="work-item-permission-role-summary" aria-label="权限角色摘要">
        <Tag color="blue">空间角色 {value.spaceRoleDefinitions.length}</Tag>
        <Tag color="purple">事项角色 {value.workItemRoleDefinitions.length}</Tag>
        <Tag color="gold">策略 {value.permissionPolicies.length}</Tag>
        <Tag color="red">deny 优先</Tag>
      </div>
      <div className="work-item-permission-role-list">
        {value.spaceRoleDefinitions.map((role) => (
          <Tag key={role.roleKey}>{role.name} · {role.roleKey}</Tag>
        ))}
        {value.workItemRoleDefinitions.map((role) => (
          <Tag color="purple" key={role.roleKey}>{role.name} · {role.roleKey}</Tag>
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
        {value.permissionPolicies.map((policy, index) => (
          <Card
            size="small"
            key={`${policy.policyKey}:${index}`}
            title={(
              <Space wrap>
                <Tag color={policy.effect === 'deny' ? 'red' : 'green'}>{policy.effect}</Tag>
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
            <Row gutter={[12, 12]}>
              <Col xs={24} md={8}>
                <Field label="永久 policy key">
                  <Input
                    aria-label={`策略 ${index + 1} 永久 key`}
                    value={policy.policyKey}
                    disabled={readOnly || policy.system}
                    onChange={(event) => updatePolicy(index, {
                      policyKey: semanticKeys(event.target.value)[0] ?? '',
                    })}
                  />
                </Field>
              </Col>
              <Col xs={12} md={4}>
                <Field label="效果">
                  <Select
                    aria-label={`策略 ${index + 1} 效果`}
                    value={policy.effect}
                    disabled={readOnly}
                    options={[{ value: 'allow', label: '允许' }, { value: 'deny', label: '拒绝' }]}
                    onChange={(effect) => updatePolicy(index, { effect })}
                  />
                </Field>
              </Col>
              <Col xs={12} md={4}>
                <Field label="优先级">
                  <InputNumber
                    min={0}
                    max={10_000}
                    value={policy.priority}
                    disabled={readOnly}
                    onChange={(priority) => updatePolicy(index, { priority: priority ?? 0 })}
                  />
                </Field>
              </Col>
              <Col xs={24} md={8}>
                <Field label="动作">
                  <Select
                    mode="multiple"
                    aria-label={`策略 ${index + 1} 动作`}
                    value={policy.actionKeys}
                    disabled={readOnly}
                    options={ACTIONS.map((action) => ({ value: action, label: action }))}
                    onChange={(actionKeys) => updatePolicy(index, { actionKeys })}
                  />
                </Field>
              </Col>
              <Col xs={12} md={6}>
                <Field label="Subject 类型">
                  <Select
                    value={policy.subjectSelectors[0]?.kind}
                    disabled={readOnly}
                    options={SUBJECT_KINDS.map((kind) => ({ value: kind, label: kind }))}
                    onChange={(kind) => updatePolicy(index, {
                      subjectSelectors: [{ ...policy.subjectSelectors[0], kind }],
                    })}
                  />
                </Field>
              </Col>
              <Col xs={12} md={6}>
                <Field label="Subject key">
                  <Input
                    value={policy.subjectSelectors[0]?.key ?? ''}
                    disabled={readOnly || policy.subjectSelectors[0]?.kind === 'everyone'}
                    onChange={(event) => updatePolicy(index, {
                      subjectSelectors: [{
                        ...policy.subjectSelectors[0],
                        key: event.target.value.trim().toLowerCase(),
                      }],
                    })}
                  />
                </Field>
              </Col>
              <Col xs={12} md={6}>
                <Field label="Data scope">
                  <Select
                    aria-label={`策略 ${index + 1} 数据范围`}
                    value={policy.dataScope.kind}
                    disabled={readOnly}
                    options={SCOPE_KINDS.map((kind) => ({ value: kind, label: kind }))}
                    onChange={(kind) => updatePolicy(index, {
                      dataScope: { ...policy.dataScope, kind },
                    })}
                  />
                </Field>
              </Col>
              <Col xs={12} md={6}>
                <Field label="Scope values（逗号分隔）">
                  <Input
                    value={(policy.dataScope.values ?? []).join(',')}
                    disabled={readOnly || policy.dataScope.kind === 'all'}
                    onChange={(event) => updatePolicy(index, {
                      dataScope: { ...policy.dataScope, values: semanticKeys(event.target.value) },
                    })}
                  />
                </Field>
              </Col>
              <Col xs={24} md={8}>
                <Field label="字段限定 key">
                  <Input
                    value={policy.fieldKeys.join(',')}
                    disabled={readOnly}
                    onChange={(event) => updatePolicy(index, { fieldKeys: semanticKeys(event.target.value) })}
                  />
                </Field>
              </Col>
              <Col xs={24} md={8}>
                <Field label="节点限定 key">
                  <Input
                    value={policy.nodeKeys.join(',')}
                    disabled={readOnly}
                    onChange={(event) => updatePolicy(index, { nodeKeys: semanticKeys(event.target.value) })}
                  />
                </Field>
              </Col>
              <Col xs={24} md={8}>
                <Field label="关系限定 key">
                  <Input
                    value={policy.relationKeys.join(',')}
                    disabled={readOnly}
                    onChange={(event) => updatePolicy(index, { relationKeys: semanticKeys(event.target.value) })}
                  />
                </Field>
              </Col>
            </Row>
          </Card>
        ))}
      </div>
    </Card>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return <label className="work-item-permission-field"><span>{label}</span>{children}</label>
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
    if (!policy.subjectSelectors.length) diagnostics.push(`策略 ${policy.policyKey} 至少需要一个 subject`)
  }
  return diagnostics
}
