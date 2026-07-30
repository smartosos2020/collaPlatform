import type { ProjectSpaceSecondaryTabGroup } from './projectSpaceSecondaryTabs'

export type ProjectSpacePrimaryView =
  | 'overview'
  | 'work-items'
  | 'management'
  | 'members'
  | 'settings'

export type ProjectSpaceExperienceMode = 'simple' | 'advanced'
export type ProjectSpaceStatus = 'active' | 'disabled' | 'archived'
export type ProjectSpaceNavigationCapability =
  | 'view_overview'
  | 'view_work_items'
  | 'view_project_management'
  | 'view_members'
  | 'view_settings'
export type ProjectSpacePersona =
  | 'owner'
  | 'admin'
  | 'custom-role'
  | 'member'
  | 'guest'
  | 'non-member'
  | 'enterprise-admin'

export type ProjectSpaceAdvancedGroup =
  | 'work-model'
  | 'flow-access'
  | 'automation-collaboration'
  | 'metrics-governance'
  | 'scenario-templates'

export type ProjectSpaceAccessSnapshot = Readonly<{
  member: boolean
  currentUserRole?: string | null
  status: ProjectSpaceStatus
  availableActions: readonly string[]
  enterpriseGovernanceOnly?: boolean
}>

export type ProjectSpacePrimaryNavigationItem = Readonly<{
  key: ProjectSpacePrimaryView
  label: string
  order: number
  pathSuffix: string
  taskLayer: 'frequent-execution' | 'project-management' | 'space-governance'
  requiredAction: ProjectSpaceNavigationCapability
  allowedStatuses: readonly ProjectSpaceStatus[]
  deniedBehavior: 'omit'
  readOnlyStatuses: readonly ProjectSpaceStatus[]
  emptyState: string
}>

export const PROJECT_SPACE_PRIMARY_NAVIGATION = [
  {
    key: 'overview',
    label: '概览',
    order: 10,
    pathSuffix: '',
    taskLayer: 'frequent-execution',
    requiredAction: 'view_overview',
    allowedStatuses: ['active', 'disabled', 'archived'],
    deniedBehavior: 'omit',
    readOnlyStatuses: ['disabled', 'archived'],
    emptyState: '显示当前空间摘要和下一步入口，不伪造业务数据。',
  },
  {
    key: 'work-items',
    label: '工作项',
    order: 20,
    pathSuffix: '/work-items',
    taskLayer: 'frequent-execution',
    requiredAction: 'view_work_items',
    allowedStatuses: ['active', 'disabled', 'archived'],
    deniedBehavior: 'omit',
    readOnlyStatuses: ['disabled', 'archived'],
    emptyState: '显示受权范围内的工作项空态和可执行下一步。',
  },
  {
    key: 'management',
    label: '项目管理',
    order: 30,
    pathSuffix: '/management',
    taskLayer: 'project-management',
    requiredAction: 'view_project_management',
    allowedStatuses: ['active', 'disabled', 'archived'],
    deniedBehavior: 'omit',
    readOnlyStatuses: ['disabled', 'archived'],
    emptyState: '按受权模块显示计划、风险、交付、资源或指标空态。',
  },
  {
    key: 'members',
    label: '成员',
    order: 40,
    pathSuffix: '/members',
    taskLayer: 'space-governance',
    requiredAction: 'view_members',
    allowedStatuses: ['active', 'disabled', 'archived'],
    deniedBehavior: 'omit',
    readOnlyStatuses: ['disabled', 'archived'],
    emptyState: '仅显示服务端允许披露的成员和邀请状态。',
  },
  {
    key: 'settings',
    label: '设置',
    order: 50,
    pathSuffix: '/settings',
    taskLayer: 'space-governance',
    requiredAction: 'view_settings',
    allowedStatuses: ['active', 'disabled', 'archived'],
    deniedBehavior: 'omit',
    readOnlyStatuses: ['disabled', 'archived'],
    emptyState: '显示基本设置和当前受权的高级配置入口。',
  },
] as const satisfies readonly ProjectSpacePrimaryNavigationItem[]

export const PROJECT_SPACE_ADVANCED_CONFIGURATION = [
  {
    key: 'work-model',
    label: '工作模型',
    description: '管理任务模板、字段、表单与页面，以及发布配置。',
    concepts: ['work-item-types', 'fields', 'layouts', 'configuration-publication'],
  },
  {
    key: 'flow-access',
    label: '流程与权限',
    description: '管理状态流程、审批与协作流程、关系、角色和数据权限。',
    concepts: ['state-flow', 'node-flow', 'relations', 'roles', 'data-permissions'],
  },
  {
    key: 'automation-collaboration',
    label: '自动化与协同',
    description: '管理自动化规则、运行记录、连接器和跨空间协作。',
    concepts: ['automation', 'connectors', 'cross-space-grants', 'cross-space-sync'],
  },
  {
    key: 'metrics-governance',
    label: '度量治理',
    description: '管理指标定义、看板配置、风险策略和治理报表。',
    concepts: ['metric-definitions', 'dashboards', 'risk-policies', 'governance-reports'],
  },
  {
    key: 'scenario-templates',
    label: '场景模板',
    description: '安装并维护研发、市场、HR 和交付场景模板。',
    concepts: ['development', 'marketing', 'human-resources', 'delivery'],
  },
] as const satisfies readonly Readonly<{
  key: ProjectSpaceAdvancedGroup
  label: string
  description: string
  concepts: readonly string[]
}>[]

export const PROJECT_SPACE_TERMINOLOGY = [
  { canonical: '概览', legacy: ['协作概览'], explanation: '团队进入空间后的任务入口和当前摘要。' },
  { canonical: '任务模板', legacy: ['工作项类型'], explanation: '定义团队需要管理的记录种类；高级说明保留技术名“工作项类型”。' },
  { canonical: '表单与页面', legacy: ['页面布局', '布局设计'], explanation: '控制创建和查看记录时展示的字段与顺序。' },
  { canonical: '发布配置', legacy: ['配置发布'], explanation: '校验草稿并使一个不可变版本生效。' },
  { canonical: '审批与协作流程', legacy: ['节点流'], explanation: '适合审批、会签、交付物和多人协作的流程。' },
  { canonical: '启用、停用与归档', legacy: ['空间生命周期'], explanation: '管理空间可写、只读和历史保留状态。' },
  { canonical: '指标定义', legacy: ['指标语义'], explanation: '定义指标口径、维度和时间窗口。' },
  { canonical: '自动化运行与限额', legacy: ['自动化治理'], explanation: '查看自动化执行、失败、重试和使用上限。' },
] as const

export const PROJECT_SPACE_PERSONA_TASK_MATRIX = {
  owner: {
    primaryViews: ['overview', 'work-items', 'management', 'members', 'settings'],
    modeSwitch: true,
    defaultView: 'overview',
    explanation: '管理空间并参与日常协作；高级配置仍受当前服务端 capability 校准。',
  },
  admin: {
    primaryViews: ['overview', 'work-items', 'management', 'members', 'settings'],
    modeSwitch: true,
    defaultView: 'overview',
    explanation: '管理空间并参与日常协作；不能执行 owner-only 动作。',
  },
  'custom-role': {
    primaryViews: ['overview'],
    modeSwitch: false,
    defaultView: 'overview',
    explanation: '展示身份只提供默认落点；实际入口完全由服务端 capability 决定。',
  },
  member: {
    primaryViews: ['overview', 'work-items', 'management'],
    modeSwitch: false,
    defaultView: 'work-items',
    explanation: '完成日常工作和当前受权的项目管理任务。',
  },
  guest: {
    primaryViews: ['overview', 'work-items'],
    modeSwitch: false,
    defaultView: 'overview',
    explanation: '只读查看当前受权内容，不显示配置或治理入口。',
  },
  'non-member': {
    primaryViews: ['overview'],
    modeSwitch: false,
    defaultView: 'overview',
    explanation: '仅可查看企业内可发现空间的最小元数据，不加载成员内容。',
  },
  'enterprise-admin': {
    primaryViews: [],
    modeSwitch: false,
    defaultView: 'overview',
    explanation: '非空间成员留在企业治理 Shell，不获得空间内容旁路。',
  },
} as const satisfies Record<ProjectSpacePersona, Readonly<{
  primaryViews: readonly ProjectSpacePrimaryView[]
  modeSwitch: boolean
  defaultView: ProjectSpacePrimaryView
  explanation: string
}>>

export const PROJECT_SPACE_CONTENT_LAYER_BY_SECONDARY_GROUP = {
  core: 'frequent-execution',
  planning: 'project-management',
  resources: 'project-management',
  delivery: 'project-management',
  automation: 'advanced-configuration',
  collaboration: 'frequent-execution',
  metrics: 'project-management',
  configuration: 'advanced-configuration',
  access: 'advanced-configuration',
  lifecycle: 'advanced-configuration',
} as const satisfies Record<
  ProjectSpaceSecondaryTabGroup,
  'frequent-execution' | 'project-management' | 'advanced-configuration'
>

export const PROJECT_SPACE_VIEWPORT_CONTRACT = [
  { width: 1440, navigation: 'single-row', sidebar: 'expanded' },
  { width: 1366, navigation: 'single-row', sidebar: 'expanded' },
  { width: 820, navigation: 'scrollable', sidebar: 'collapsible' },
] as const

export const PROJECT_SPACE_SCENARIO_PATHS = [
  {
    key: 'development',
    label: '研发',
    primarySequence: ['overview', 'work-items', 'management'],
    advancedSequence: ['work-model', 'flow-access'],
  },
  {
    key: 'marketing',
    label: '市场',
    primarySequence: ['overview', 'work-items', 'management'],
    advancedSequence: ['scenario-templates', 'automation-collaboration'],
  },
  {
    key: 'human-resources',
    label: 'HR',
    primarySequence: ['overview', 'work-items', 'management'],
    advancedSequence: ['work-model', 'flow-access'],
  },
  {
    key: 'delivery',
    label: '交付',
    primarySequence: ['overview', 'work-items', 'management'],
    advancedSequence: ['scenario-templates', 'metrics-governance'],
  },
] as const satisfies readonly Readonly<{
  key: string
  label: string
  primarySequence: readonly ProjectSpacePrimaryView[]
  advancedSequence: readonly ProjectSpaceAdvancedGroup[]
}>[]

export type ProjectSpaceRouteContext = Readonly<{
  primaryView: ProjectSpacePrimaryView
  renderView: ProjectSpacePrimaryView | 'types' | 'fields' | 'layouts' | 'sample'
  advancedGroup?: ProjectSpaceAdvancedGroup
  compatibilityRoute: boolean
  preserveQuery: boolean
}>

const COMPATIBILITY_ROUTE_RULES = [
  { pattern: /\/types\/[^/]+\/fields(?:\/[^/]+)?$/, advancedGroup: 'work-model', renderView: 'fields' },
  { pattern: /\/types\/[^/]+\/layouts$/, advancedGroup: 'work-model', renderView: 'layouts' },
  { pattern: /\/types\/[^/]+\/sample$/, advancedGroup: 'work-model', renderView: 'sample' },
  { pattern: /\/types(?:\/[^/]+)?$/, advancedGroup: 'work-model', renderView: 'types' },
] as const satisfies readonly Readonly<{
  pattern: RegExp
  advancedGroup: ProjectSpaceAdvancedGroup
  renderView: 'types' | 'fields' | 'layouts' | 'sample'
}>[]

export function resolveProjectSpaceRouteContext(pathname: string): ProjectSpaceRouteContext {
  const compatibilityRule = COMPATIBILITY_ROUTE_RULES.find((rule) => rule.pattern.test(pathname))
  if (compatibilityRule) {
    return {
      primaryView: 'settings',
      renderView: compatibilityRule.renderView,
      advancedGroup: compatibilityRule.advancedGroup,
      compatibilityRoute: true,
      preserveQuery: true,
    }
  }
  if (/\/work-items(?:\/[^/]+)?$/.test(pathname)) {
    return {
      primaryView: 'work-items',
      renderView: 'work-items',
      compatibilityRoute: false,
      preserveQuery: true,
    }
  }
  if (pathname.endsWith('/management')) {
    return {
      primaryView: 'management',
      renderView: 'management',
      compatibilityRoute: false,
      preserveQuery: true,
    }
  }
  if (pathname.endsWith('/members')) {
    return {
      primaryView: 'members',
      renderView: 'members',
      compatibilityRoute: false,
      preserveQuery: true,
    }
  }
  if (pathname.endsWith('/settings')) {
    return {
      primaryView: 'settings',
      renderView: 'settings',
      compatibilityRoute: false,
      preserveQuery: true,
    }
  }
  return {
    primaryView: 'overview',
    renderView: 'overview',
    compatibilityRoute: false,
    preserveQuery: true,
  }
}

export function projectSpacePersona(access: ProjectSpaceAccessSnapshot): ProjectSpacePersona {
  if (access.enterpriseGovernanceOnly) return 'enterprise-admin'
  if (!access.member) return 'non-member'
  if (access.currentUserRole === 'owner') return 'owner'
  if (access.currentUserRole === 'admin') return 'admin'
  if (access.currentUserRole === 'guest') return 'guest'
  if (access.currentUserRole && access.currentUserRole !== 'member') return 'custom-role'
  return 'member'
}

export function getVisibleProjectSpacePrimaryNavigation(
  access: ProjectSpaceAccessSnapshot,
): ProjectSpacePrimaryNavigationItem[] {
  if (access.enterpriseGovernanceOnly) return []
  const actions = new Set(access.availableActions)
  const navigation: readonly ProjectSpacePrimaryNavigationItem[] = PROJECT_SPACE_PRIMARY_NAVIGATION
  return navigation.filter((item) => (
    actions.has(item.requiredAction)
    && item.allowedStatuses.includes(access.status)
  ))
}

export function projectSpacePrimaryPath(spaceId: string, view: ProjectSpacePrimaryView): string {
  const item = PROJECT_SPACE_PRIMARY_NAVIGATION.find((candidate) => candidate.key === view)
  if (!item) return `/project-spaces/${spaceId}`
  return `/project-spaces/${spaceId}${item.pathSuffix}`
}
