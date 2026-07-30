import type { TabsProps } from 'antd'
import type { ReactNode } from 'react'

export type ProjectSpaceSecondaryTabView =
  | 'overview'
  | 'work-items'
  | 'work-item-detail'
  | 'types'
  | 'fields'
  | 'layouts'
  | 'members'
  | 'settings'

export type ProjectSpaceSecondaryTabGroup =
  | 'core'
  | 'planning'
  | 'resources'
  | 'delivery'
  | 'automation'
  | 'collaboration'
  | 'metrics'
  | 'configuration'
  | 'access'
  | 'lifecycle'

export type ProjectSpaceSecondaryTabDefinition = Readonly<{
  key: string
  label: string
  order: number
  group: ProjectSpaceSecondaryTabGroup
  managerOnly: boolean
}>

/**
 * The display order is deliberately owned here instead of by JSX composition.
 * A consuming panel can therefore render one content block at a time without
 * coupling its Ant Design Tabs order to component declaration order.
 */
export const PROJECT_SPACE_SECONDARY_TAB_CONFIG = {
  overview: [
    { key: 'active-types', label: '工作入口', order: 10, group: 'core', managerOnly: false },
    { key: 'activity', label: '空间动态', order: 20, group: 'collaboration', managerOnly: false },
    { key: 'collaboration-boundary', label: '协作边界', order: 30, group: 'access', managerOnly: false },
    { key: 'cross-space-grants', label: '跨空间授权', order: 40, group: 'access', managerOnly: true },
    { key: 'cross-space-relations', label: '跨空间关系', order: 50, group: 'collaboration', managerOnly: false },
    { key: 'cross-space-sync', label: '跨空间同步', order: 60, group: 'collaboration', managerOnly: false },
    { key: 'cross-team-panorama', label: '跨团队全景', order: 70, group: 'collaboration', managerOnly: false },
    { key: 'scenario-templates', label: '场景模板', order: 80, group: 'configuration', managerOnly: false },
    { key: 'metric-dashboards', label: '指标看板', order: 90, group: 'metrics', managerOnly: false },
    { key: 'metric-semantics', label: '指标语义', order: 100, group: 'metrics', managerOnly: false },
    { key: 'metric-risks', label: '指标风险', order: 110, group: 'metrics', managerOnly: false },
    { key: 'metric-governance', label: '指标治理', order: 120, group: 'metrics', managerOnly: false },
  ],
  'work-items': [
    { key: 'work-item-collection', label: '工作项', order: 10, group: 'core', managerOnly: false },
    { key: 'project-detail', label: '项目概况', order: 20, group: 'core', managerOnly: false },
    { key: 'project-plan', label: '项目计划', order: 30, group: 'planning', managerOnly: false },
    { key: 'resource-planning', label: '人员排期', order: 40, group: 'resources', managerOnly: false },
    { key: 'resource-worklog', label: '实际工时', order: 50, group: 'resources', managerOnly: false },
    { key: 'resource-capacity', label: '资源产能', order: 60, group: 'resources', managerOnly: false },
    { key: 'resource-schedule', label: '资源日程', order: 70, group: 'resources', managerOnly: false },
    { key: 'project-register', label: '风险与决策', order: 80, group: 'delivery', managerOnly: false },
    { key: 'project-delivery', label: '交付验收', order: 90, group: 'delivery', managerOnly: false },
    { key: 'automation-rules', label: '自动化规则', order: 100, group: 'automation', managerOnly: false },
    { key: 'automation-execution', label: '运行记录', order: 110, group: 'automation', managerOnly: false },
    { key: 'automation-connectors', label: '连接器', order: 120, group: 'automation', managerOnly: false },
    { key: 'automation-management', label: '自动化治理', order: 130, group: 'automation', managerOnly: false },
  ],
  'work-item-detail': [
    { key: 'details', label: '事项详情', order: 10, group: 'core', managerOnly: false },
    { key: 'workflow', label: '状态流', order: 20, group: 'core', managerOnly: false },
    { key: 'node-workflow', label: '节点流', order: 30, group: 'core', managerOnly: false },
    { key: 'relations', label: '事项关系', order: 40, group: 'collaboration', managerOnly: false },
    { key: 'collaboration', label: '协作记录', order: 50, group: 'collaboration', managerOnly: false },
    { key: 'permissions', label: '权限', order: 60, group: 'access', managerOnly: false },
  ],
  types: [
    { key: 'type-catalog', label: '类型目录', order: 10, group: 'configuration', managerOnly: true },
    { key: 'configuration-draft', label: '配置发布', order: 20, group: 'configuration', managerOnly: true },
  ],
  fields: [
    { key: 'field-catalog', label: '字段目录', order: 10, group: 'configuration', managerOnly: true },
    { key: 'configuration-draft', label: '配置发布', order: 20, group: 'configuration', managerOnly: true },
  ],
  layouts: [
    { key: 'layout-editor', label: '布局设计', order: 10, group: 'configuration', managerOnly: true },
    { key: 'field-access', label: '字段权限', order: 20, group: 'access', managerOnly: true },
    { key: 'access-preview', label: '访问预览', order: 30, group: 'access', managerOnly: true },
    { key: 'configuration-draft', label: '配置发布', order: 40, group: 'configuration', managerOnly: true },
  ],
  members: [
    { key: 'member-list', label: '空间成员', order: 10, group: 'access', managerOnly: true },
    { key: 'invitations', label: '成员邀请', order: 20, group: 'access', managerOnly: true },
  ],
  settings: [
    { key: 'general', label: '基本信息', order: 10, group: 'configuration', managerOnly: true },
    { key: 'lifecycle', label: '空间生命周期', order: 20, group: 'lifecycle', managerOnly: true },
    { key: 'work-model', label: '工作模型', order: 30, group: 'configuration', managerOnly: true },
    { key: 'flow-access', label: '流程与权限', order: 40, group: 'access', managerOnly: true },
    { key: 'automation-collaboration', label: '自动化与协同', order: 50, group: 'automation', managerOnly: true },
    { key: 'metrics-governance', label: '度量治理', order: 60, group: 'metrics', managerOnly: true },
    { key: 'scenario-templates', label: '场景模板', order: 70, group: 'configuration', managerOnly: true },
  ],
} as const satisfies Readonly<
  Record<ProjectSpaceSecondaryTabView, readonly ProjectSpaceSecondaryTabDefinition[]>
>

export type ProjectSpaceSecondaryTabFor<View extends ProjectSpaceSecondaryTabView> =
  (typeof PROJECT_SPACE_SECONDARY_TAB_CONFIG)[View][number]

export type ProjectSpaceSecondaryTabKey<View extends ProjectSpaceSecondaryTabView> =
  ProjectSpaceSecondaryTabFor<View>['key']

export type ProjectSpaceSecondaryTabOptions<View extends ProjectSpaceSecondaryTabView> = Readonly<{
  canManage?: boolean
  includeKeys?: readonly ProjectSpaceSecondaryTabKey<View>[]
}>

type OrderedTab = Readonly<{
  key: string
  order: number
}>

export function sortProjectSpaceSecondaryTabs<T extends OrderedTab>(tabs: readonly T[]): T[] {
  return tabs
    .map((tab, sourceIndex) => ({ tab, sourceIndex }))
    .sort((left, right) => left.tab.order - right.tab.order || left.sourceIndex - right.sourceIndex)
    .map(({ tab }) => tab)
}

export function getProjectSpaceSecondaryTabs<View extends ProjectSpaceSecondaryTabView>(
  view: View,
  options: ProjectSpaceSecondaryTabOptions<View> = {},
): ProjectSpaceSecondaryTabFor<View>[] {
  const included = options.includeKeys ? new Set<string>(options.includeKeys) : null
  const visible = PROJECT_SPACE_SECONDARY_TAB_CONFIG[view].filter((tab) => (
    (!tab.managerOnly || options.canManage === true)
    && (!included || included.has(tab.key))
  ))

  return sortProjectSpaceSecondaryTabs(visible)
}

export function createProjectSpaceSecondaryTabItems<View extends ProjectSpaceSecondaryTabView>(
  view: View,
  renderContent: (tab: ProjectSpaceSecondaryTabFor<View>) => ReactNode,
  options: ProjectSpaceSecondaryTabOptions<View> = {},
): NonNullable<TabsProps['items']> {
  return getProjectSpaceSecondaryTabs(view, options).map((tab) => ({
    key: tab.key,
    label: tab.label,
    children: renderContent(tab),
  }))
}
