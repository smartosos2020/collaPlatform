export type PermissionSubjectSelector = {
  kind: string
  key?: string | null
  subjectId?: string | null
}

export type PermissionDataScope = {
  kind: string
  fieldKey?: string | null
  operator?: string | null
  values?: string[]
}

export type PermissionSemanticDefinition = {
  value: string
  label: string
  description: string
  available: boolean
}

export const PERMISSION_ACTION_DEFINITIONS: PermissionSemanticDefinition[] = [
  { value: 'create', label: '新建工作项', description: '创建当前类型的工作项', available: true },
  { value: 'view', label: '查看工作项', description: '查看工作项及进入详情', available: true },
  { value: 'edit', label: '编辑工作项', description: '修改标题和基础内容', available: true },
  { value: 'archive', label: '归档工作项', description: '归档仍在使用的工作项', available: true },
  { value: 'restore', label: '恢复工作项', description: '恢复已归档的工作项', available: true },
  { value: 'comment', label: '发表评论', description: '新增工作项评论', available: true },
  { value: 'attach', label: '管理附件', description: '上传或处理工作项附件', available: true },
  { value: 'participant_manage', label: '管理参与人', description: '增删负责人、协作者和关注者', available: true },
  { value: 'transition', label: '执行流程流转', description: '执行状态转换或流程任务', available: true },
  { value: 'workflow_manage', label: '管理流程实例', description: '恢复、纠正或升级流程实例', available: true },
  { value: 'relate', label: '发起工作项关系', description: '当前工作项作为来源建立关系', available: true },
  { value: 'accept_link', label: '接受工作项关系', description: '当前工作项作为目标接受关系', available: true },
  { value: 'field_read', label: '读取字段', description: '决定是否可以读取指定字段', available: true },
  { value: 'field_write', label: '修改字段', description: '决定是否可以修改指定字段', available: true },
  { value: 'delete', label: '永久删除工作项', description: '治理能力尚未完整接入', available: false },
  { value: 'relation_manage', label: '管理关系定义', description: '治理能力尚未完整接入', available: false },
  { value: 'role_assign', label: '分配事项角色', description: '治理能力尚未完整接入', available: false },
  { value: 'policy_manage', label: '管理权限策略', description: '治理能力尚未完整接入', available: false },
  { value: 'permission_explain', label: '查看权限解释', description: '治理能力尚未完整接入', available: false },
  { value: 'permission_request', label: '发起权限申请', description: '治理能力尚未完整接入', available: false },
  { value: 'governance_inspect', label: '查看治理信息', description: '治理能力尚未完整接入', available: false },
  { value: 'migration_manage', label: '管理实例迁移', description: '治理能力尚未完整接入', available: false },
]

export const PERMISSION_SUBJECT_DEFINITIONS: PermissionSemanticDefinition[] = [
  { value: 'space_role', label: '空间角色', description: '按当前项目空间中的角色判断', available: true },
  { value: 'work_item_role', label: '工作项创建人', description: '当前仅开放创建人身份', available: true },
  { value: 'user', label: '指定空间成员', description: '从当前空间成员中选择具体用户', available: true },
  { value: 'everyone', label: '所有可进入空间的用户', description: '包含所有可进入当前空间的用户', available: true },
  { value: 'enterprise_role', label: '企业角色', description: '企业角色目录尚未接入此页面', available: false },
  { value: 'participant_role', label: '参与者角色', description: '各运行入口的参与者上下文尚未统一', available: false },
  { value: 'department', label: '指定部门', description: '运行时尚未支持部门主体', available: false },
  { value: 'user_group', label: '指定用户组', description: '运行时尚未支持用户组主体', available: false },
]

export const PERMISSION_SCOPE_DEFINITIONS: PermissionSemanticDefinition[] = [
  { value: 'all', label: '所有工作项', description: '不再附加工作项范围限制', available: true },
  { value: 'created_by_subject', label: '仅该主体创建的工作项', description: '只匹配由上述主体创建的工作项', available: true },
  { value: 'explicit_set', label: '指定工作项', description: '需要完整的工作项选择器后开放', available: false },
  { value: 'participating', label: '主体参与的工作项', description: '各运行入口的参与者上下文尚未统一', available: false },
  { value: 'work_item_role', label: '担任指定事项角色', description: '校验与运行时协议尚未统一', available: false },
  { value: 'field_match', label: '字段满足条件', description: '匹配运算符协议尚未统一', available: false },
]

export function semanticOptionLabel(definition: PermissionSemanticDefinition) {
  return `${definition.label} · ${definition.value}`
}

export function replacePrimarySubject(
  selectors: PermissionSubjectSelector[],
  selector: PermissionSubjectSelector,
) {
  return selectors.length > 0 ? [selector, ...selectors.slice(1)] : [selector]
}

export function subjectForKind(
  kind: string,
  spaceRoleKeys: string[],
  workItemRoleKeys: string[],
): PermissionSubjectSelector {
  if (kind === 'space_role') {
    return {
      kind,
      key: spaceRoleKeys.includes('guest') ? 'guest' : spaceRoleKeys[0] ?? null,
      subjectId: null,
    }
  }
  if (kind === 'work_item_role') {
    return {
      kind,
      key: workItemRoleKeys.includes('creator') ? 'creator' : null,
      subjectId: null,
    }
  }
  if (kind === 'user') return { kind, key: null, subjectId: null }
  if (kind === 'everyone') return { kind, key: null, subjectId: null }
  return { kind, key: null, subjectId: null }
}

export function scopeForKind(kind: string): PermissionDataScope {
  return {
    kind,
    fieldKey: null,
    operator: null,
    values: [],
  }
}

export function isAvailableSubjectKind(kind: string) {
  return PERMISSION_SUBJECT_DEFINITIONS.some((item) => item.value === kind && item.available)
}

export function isAvailableScopeKind(kind: string) {
  return PERMISSION_SCOPE_DEFINITIONS.some((item) => item.value === kind && item.available)
}
