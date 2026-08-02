import {
  PROJECT_SPACE_ADVANCED_CONFIGURATION,
  PROJECT_SPACE_SCENARIO_PATHS,
  projectSpacePrimaryPath,
  resolveProjectSpaceRouteContext,
} from './projectSpaceInformationArchitecture'

export const PROJECT_SPACE_ONBOARDING_FLOW_VERSION = 's21-m6-v1' as const

export type ProjectSpaceOnboardingScenarioKey =
  | 'development'
  | 'marketing'
  | 'human-resources'
  | 'delivery'

export type ProjectSpaceOnboardingStartingPoint =
  | { kind: 'unselected'; scenarioKey: null }
  | { kind: 'blank'; scenarioKey: null }
  | { kind: 'scenario'; scenarioKey: ProjectSpaceOnboardingScenarioKey }

export type ProjectSpaceOnboardingTrack = 'manager' | 'member' | 'guest'
export type ProjectSpaceOnboardingStepStatus =
  | 'available'
  | 'verify_on_owner_api'
  | 'blocked'

export type ProjectSpaceOnboardingAcknowledgement = {
  stepKey: string
  acknowledgement: 'seen' | 'skipped'
}

export type ProjectSpaceOnboardingChecklistItem = {
  stepKey: string
  labelKey: string
  helpKey: string
  path: string | null
  dependencies: string[]
  ownerContract: string
  status: ProjectSpaceOnboardingStepStatus
}

export type ProjectSpaceOnboardingView = {
  schemaVersion: 1
  flowVersion: string
  currentFlowVersion: typeof PROJECT_SPACE_ONBOARDING_FLOW_VERSION
  version: number
  updatedAt: string | null
  migrationRequired: boolean
  startingPoint: ProjectSpaceOnboardingStartingPoint
  acknowledgedSteps: ProjectSpaceOnboardingAcknowledgement[]
  dismissed: boolean
  telemetryOptOut: boolean
  selectionEffect: 'experience_only'
  installationRequested: false
  publicationRequested: false
  track: ProjectSpaceOnboardingTrack
  readOnly: boolean
  checklist: ProjectSpaceOnboardingChecklistItem[]
}

export type ProjectSpaceOnboardingCommand =
  | {
      action: 'select_starting_point'
      startingPoint: 'blank'
    }
  | {
      action: 'select_starting_point'
      startingPoint: 'scenario'
      scenarioKey: ProjectSpaceOnboardingScenarioKey
    }
  | {
      action: 'acknowledge_step'
      stepKey: string
      acknowledgement: 'seen' | 'skipped'
    }
  | { action: 'dismiss' }
  | { action: 'resume' }
  | { action: 'upgrade_flow' }
  | { action: 'set_telemetry_opt_out'; telemetryOptOut: boolean }
  | { action: 'reset' }

export type ProjectSpaceOnboardingTelemetryEvent = {
  eventId: string
  flowVersion: string
  stepKey: string
  outcome:
    | 'shown'
    | 'started'
    | 'succeeded'
    | 'skipped'
    | 'blocked'
    | 'failed'
    | 'dismissed'
    | 'reset'
  durationBucket:
    | 'under_5s'
    | '5_to_30s'
    | '30_to_120s'
    | '2_to_10m'
    | 'over_10m'
    | 'unknown'
  errorCode:
    | 'none'
    | 'capability_denied'
    | 'space_read_only'
    | 'offline'
    | 'version_conflict'
    | 'owner_api_failed'
    | 'unknown'
}

export const PROJECT_SPACE_ONBOARDING_STARTING_POINTS = [
  ...PROJECT_SPACE_SCENARIO_PATHS.map((scenario) => ({
    key: scenario.key as ProjectSpaceOnboardingScenarioKey,
    kind: 'scenario' as const,
    label: scenario.key === 'human-resources'
      ? 'HR 模板'
      : `${scenario.label}模板`,
    summary: '选择后仅保存引导路径；安装时只创建或复用 6 个任务模板。',
    effect: '流程、关系、看板、自动化和指标只核验公共 owner 引用，不会由选择动作配置。',
  })),
  {
    key: 'blank',
    kind: 'blank' as const,
    label: '基础空间',
    summary: '不额外安装场景模板，保留平台自动提供的 6 个基础类型。',
    effect: '可按任务模板、字段与页面、流程与权限、校验和发布的顺序自行配置。',
  },
] as const

type OnboardingStepCopy = Readonly<{
  label: string
  help: string
  actionLabel: string
  requiredAction?: string
}>

const STEP_COPY: Record<string, OnboardingStepCopy> = {
  choose_starting_point: {
    label: '选择起步方式',
    help: '先选适合团队的场景模板或基础空间。选择仅保存引导偏好，不安装、不发布。',
    actionLabel: '选择起步方式',
    requiredAction: 'view_settings',
  },
  preview_impact: {
    label: '预览模板影响',
    help: '先查看场景计划和影响；预览不会创建任务模板或改变当前配置。',
    actionLabel: '前往场景模板',
    requiredAction: 'view_settings',
  },
  install_scenario: {
    label: '确认安装场景',
    help: '只有在场景模板 owner 页面明确确认才会安装；选择起步方式不会触发安装。',
    actionLabel: '前往场景模板',
    requiredAction: 'view_settings',
  },
  configure_work_model: {
    label: '配置任务模板',
    help: '先定义团队要管理的记录种类；配置草稿不会自动发布。',
    actionLabel: '打开任务模板',
    requiredAction: 'view_settings',
  },
  configure_fields_and_pages: {
    label: '配置字段、表单与页面',
    help: '控制创建和查看工作项时展示的字段与顺序。',
    actionLabel: '打开工作模型',
    requiredAction: 'view_settings',
  },
  configure_workflow: {
    label: '配置工作流程',
    help: '仅在需要状态流转或审批协作时配置，并继续由流程 owner 鉴权。',
    actionLabel: '打开工作流程',
    requiredAction: 'view_settings',
  },
  configure_permissions: {
    label: '校准成员与权限',
    help: '按最小权限确认谁可以查看、编辑和管理当前空间。',
    actionLabel: '打开成员与权限',
    requiredAction: 'view_members',
  },
  publish_configuration: {
    label: '校验并发布配置',
    help: '先校验差异、兼容性和影响，再明确发布；保存草稿不等于生效。',
    actionLabel: '打开发布配置',
    requiredAction: 'view_settings',
  },
  configure_automation: {
    label: '按需配置自动化',
    help: '基础闭环完成后再配置自动化规则、运行限额和连接器。',
    actionLabel: '打开自动化与协同',
    requiredAction: 'view_settings',
  },
  configure_metrics: {
    label: '按需配置指标',
    help: '需要统一口径、看板或风险治理时，再进入指标 owner 页面。',
    actionLabel: '打开度量治理',
    requiredAction: 'view_settings',
  },
  invite_members: {
    label: '邀请或添加成员',
    help: '在成员页添加协作者并设置角色；最终可见范围由当前 capability 决定。',
    actionLabel: '打开成员页',
    requiredAction: 'view_members',
  },
  create_first_work_item: {
    label: '创建第一个工作项',
    help: '使用当前已发布的任务模板创建首个真实工作项。',
    actionLabel: '打开工作项',
    requiredAction: 'view_work_items',
  },
  handoff_first_work_item: {
    label: '把第一个工作项交给团队',
    help: '在工作项 owner 页面添加参与人；引导记录不替代真实交接结果。',
    actionLabel: '检查团队工作',
    requiredAction: 'view_work_items',
  },
  find_work: {
    label: '找到分配给我的工作',
    help: '从工作项列表进入当前有权查看的事项。',
    actionLabel: '查看工作项',
    requiredAction: 'view_work_items',
  },
  create_or_update_work: {
    label: '创建或更新工作项',
    help: '按当前表单和字段权限创建或更新事项；不可写时只显示可查看内容。',
    actionLabel: '打开工作项',
    requiredAction: 'view_work_items',
  },
  comment_on_work: {
    label: '参与工作讨论',
    help: '在具体工作项中评论；评论内容不会写入引导状态或遥测。',
    actionLabel: '打开工作项',
    requiredAction: 'view_work_items',
  },
  attach_file: {
    label: '添加工作附件',
    help: '在具体工作项中上传附件；文件名和内容不会写入引导状态或遥测。',
    actionLabel: '打开工作项',
    requiredAction: 'view_work_items',
  },
  transition_state: {
    label: '推进工作状态',
    help: '只执行当前流程和权限允许的状态动作。',
    actionLabel: '打开工作项',
    requiredAction: 'view_work_items',
  },
  review_notifications: {
    label: '查看工作通知',
    help: '从通知中心确认与自己相关的工作变化，不在引导中复制通知事实。',
    actionLabel: '打开通知中心',
    requiredAction: 'view_work_items',
  },
  'manager.select-starting-point': {
    label: '选择起步方式',
    help: '先选适合团队的场景模板或基础空间。选择仅保存引导偏好，不安装、不发布。',
    actionLabel: '选择起步方式',
    requiredAction: 'view_settings',
  },
  'manager.preview-template': {
    label: '预览并确认场景模板',
    help: '先查看预检计划和影响，再到场景模板 owner 页面明确确认安装。',
    actionLabel: '前往场景模板',
    requiredAction: 'view_settings',
  },
  'manager.configure-work-model': {
    label: '配置任务模板、字段与页面',
    help: '从任务模板开始，再配置字段、表单与页面；草稿不会自动生效。',
    actionLabel: '打开工作模型',
    requiredAction: 'view_settings',
  },
  'manager.configure-flow-access': {
    label: '配置流程与权限',
    help: '仅在需要状态流转、审批协作或更细权限时配置，并继续由 owner API 鉴权。',
    actionLabel: '打开流程与权限',
    requiredAction: 'view_settings',
  },
  'manager.validate-publish': {
    label: '校验并发布配置',
    help: '先校验差异、兼容性和影响，再明确发布；保存草稿不等于生效。',
    actionLabel: '打开发布配置',
    requiredAction: 'view_settings',
  },
  'manager.add-members': {
    label: '邀请或添加成员',
    help: '在成员页添加协作者并设置角色；最终可见范围由当前 capability 决定。',
    actionLabel: '打开成员页',
    requiredAction: 'view_members',
  },
  'manager.create-first-item': {
    label: '创建第一个工作项',
    help: '使用当前已发布的任务模板创建首个真实工作项。',
    actionLabel: '打开工作项',
    requiredAction: 'view_work_items',
  },
  'manager.handoff': {
    label: '把工作交给团队',
    help: '确认成员能找到、更新和流转工作；引导记录不替代真实交接结果。',
    actionLabel: '检查团队工作',
    requiredAction: 'view_work_items',
  },
  'member.find-work': {
    label: '找到分配给我的工作',
    help: '从工作项列表进入当前有权查看的事项。',
    actionLabel: '查看工作项',
    requiredAction: 'view_work_items',
  },
  'member.create-or-update': {
    label: '创建或更新工作项',
    help: '按当前表单和字段权限创建或更新事项；不可写时只显示可查看内容。',
    actionLabel: '打开工作项',
    requiredAction: 'view_work_items',
  },
  'member.comment-attachment': {
    label: '评论并添加附件',
    help: '在具体工作项中协作；评论和附件内容不会写入引导状态或遥测。',
    actionLabel: '打开工作项',
    requiredAction: 'view_work_items',
  },
  'member.transition': {
    label: '推进工作状态',
    help: '只执行当前流程和权限允许的状态动作。',
    actionLabel: '打开工作项',
    requiredAction: 'view_work_items',
  },
  'member.notifications': {
    label: '查看工作通知',
    help: '从通知中心确认与自己相关的工作变化，不在引导中复制通知事实。',
    actionLabel: '打开通知中心',
    requiredAction: 'view_work_items',
  },
  'guest.review': {
    label: '查看受权工作',
    help: '访客只查看当前受权内容；需要编辑时请联系空间 owner 或管理员。',
    actionLabel: '只读查看',
    requiredAction: 'view_work_items',
  },
}

const STEP_ALIASES: ReadonlyArray<Readonly<{
  fragments: readonly string[]
  copyKey: keyof typeof STEP_COPY
}>> = [
  { fragments: ['select', 'starting'], copyKey: 'manager.select-starting-point' },
  { fragments: ['select', 'path'], copyKey: 'manager.select-starting-point' },
  { fragments: ['preview'], copyKey: 'manager.preview-template' },
  { fragments: ['install'], copyKey: 'manager.preview-template' },
  { fragments: ['member', 'add'], copyKey: 'manager.add-members' },
  { fragments: ['invite'], copyKey: 'manager.add-members' },
  { fragments: ['work', 'model'], copyKey: 'manager.configure-work-model' },
  { fragments: ['field'], copyKey: 'manager.configure-work-model' },
  { fragments: ['form'], copyKey: 'manager.configure-work-model' },
  { fragments: ['flow'], copyKey: 'manager.configure-flow-access' },
  { fragments: ['permission'], copyKey: 'manager.configure-flow-access' },
  { fragments: ['publish'], copyKey: 'manager.validate-publish' },
  { fragments: ['validat'], copyKey: 'manager.validate-publish' },
  { fragments: ['first', 'item'], copyKey: 'manager.create-first-item' },
  { fragments: ['handoff'], copyKey: 'manager.handoff' },
  { fragments: ['find'], copyKey: 'member.find-work' },
  { fragments: ['create'], copyKey: 'member.create-or-update' },
  { fragments: ['update'], copyKey: 'member.create-or-update' },
  { fragments: ['comment'], copyKey: 'member.comment-attachment' },
  { fragments: ['attachment'], copyKey: 'member.comment-attachment' },
  { fragments: ['status'], copyKey: 'member.transition' },
  { fragments: ['transition'], copyKey: 'member.transition' },
  { fragments: ['notification'], copyKey: 'member.notifications' },
  { fragments: ['guest'], copyKey: 'guest.review' },
]

const FALLBACK_STEP_COPY: OnboardingStepCopy = {
  label: '继续下一步',
  help: '请在对应业务页面核验真实结果；引导状态不代表业务操作已完成。',
  actionLabel: '打开业务页面',
}

export function resolveOnboardingStepCopy(item: ProjectSpaceOnboardingChecklistItem): OnboardingStepCopy {
  const labelKeyStep = item.labelKey.split('.').at(-1)
  const direct = STEP_COPY[item.stepKey] ?? STEP_COPY[item.labelKey] ?? (labelKeyStep ? STEP_COPY[labelKeyStep] : undefined)
  if (direct) return direct
  const candidate = `${item.stepKey} ${item.labelKey} ${item.helpKey}`.toLowerCase()
  const alias = STEP_ALIASES.find(({ fragments }) => fragments.every((fragment) => candidate.includes(fragment)))
  return alias ? STEP_COPY[alias.copyKey] : FALLBACK_STEP_COPY
}

export function onboardingStartingPointValue(
  startingPoint: ProjectSpaceOnboardingStartingPoint,
): ProjectSpaceOnboardingScenarioKey | 'blank' | undefined {
  if (startingPoint.kind === 'scenario') return startingPoint.scenarioKey
  if (startingPoint.kind === 'blank') return 'blank'
  return undefined
}

export function startingPointCommand(
  value: ProjectSpaceOnboardingScenarioKey | 'blank',
): Extract<ProjectSpaceOnboardingCommand, { action: 'select_starting_point' }> {
  return value === 'blank'
    ? { action: 'select_starting_point', startingPoint: 'blank' }
    : { action: 'select_starting_point', startingPoint: 'scenario', scenarioKey: value }
}

export function onboardingStepAcknowledgement(
  view: ProjectSpaceOnboardingView,
  stepKey: string,
): ProjectSpaceOnboardingAcknowledgement['acknowledgement'] | undefined {
  return view.acknowledgedSteps.find((item) => item.stepKey === stepKey)?.acknowledgement
}

export function canOpenOnboardingStep(
  item: ProjectSpaceOnboardingChecklistItem,
  availableActions: readonly string[],
  online: boolean,
  readOnly: boolean,
): boolean {
  if (!online || item.status === 'blocked' || !safeOnboardingPath(item.path)) return false
  const copy = resolveOnboardingStepCopy(item)
  if (copy.requiredAction && !availableActions.includes(copy.requiredAction)) return false
  if (readOnly && isOnboardingWriteStep(item)) return false
  return true
}

export function resolveOnboardingOwnerPath(
  item: ProjectSpaceOnboardingChecklistItem,
  spaceId: string,
): string | null {
  const expandedPath = item.path?.replaceAll('{spaceId}', encodeURIComponent(spaceId)) ?? null
  if (safeOnboardingPath(expandedPath, spaceId)) return expandedPath
  const copy = resolveOnboardingStepCopy(item)
  if (copy.requiredAction === 'view_members') return projectSpacePrimaryPath(spaceId, 'members')
  if (copy.requiredAction === 'view_work_items') return projectSpacePrimaryPath(spaceId, 'work-items')
  if (copy.requiredAction === 'view_settings') {
    const candidate = `${item.stepKey} ${item.labelKey} ${copy.label}`.toLowerCase()
    if (
      candidate.includes('flow')
      || candidate.includes('workflow')
      || candidate.includes('流程')
      || candidate.includes('publish')
      || candidate.includes('发布')
      || candidate.includes('validat')
      || candidate.includes('校验')
    ) {
      return `/project-spaces/${spaceId}/settings?panel=work-model&workModelTab=flow-access`
    }
    if (
      candidate.includes('scenario')
      || candidate.includes('场景')
      || item.stepKey === 'choose_starting_point'
      || item.stepKey === 'preview_impact'
      || item.stepKey === 'install_scenario'
    ) {
      return `/project-spaces/${spaceId}/settings?panel=scenario-templates`
    }
    if (candidate.includes('automation') || candidate.includes('自动化')) {
      return `/project-spaces/${spaceId}/settings?panel=automation-collaboration`
    }
    if (candidate.includes('metric') || candidate.includes('指标') || candidate.includes('度量')) {
      return `/project-spaces/${spaceId}/settings?panel=metrics-governance`
    }
    return `/project-spaces/${spaceId}/settings?panel=work-model`
  }
  return null
}

export function contextualOnboardingHelp(pathname: string): Readonly<{
  title: string
  what: string
  when: string
  next: string
}> {
  const context = resolveProjectSpaceRouteContext(pathname)
  if (context.renderView === 'types' || context.renderView === 'fields' || context.renderView === 'layouts') {
    const workModel = PROJECT_SPACE_ADVANCED_CONFIGURATION.find((item) => item.key === 'work-model')
    return {
      title: '工作模型',
      what: workModel?.description ?? '管理任务模板、字段、表单与页面，以及发布配置。',
      when: '团队需要新增记录种类、调整表单或准备发布配置时使用。',
      next: '先改草稿，再做校验、影响分析和兼容性检查，最后明确发布。',
    }
  }
  if (context.primaryView === 'work-items') {
    return {
      title: '工作项',
      what: '团队查找、创建、更新和推进真实工作的日常入口。',
      when: '需要处理任务、评论、附件或状态流转时使用。',
      next: '先打开具体工作项，再按当前表单、流程和权限执行可用动作。',
    }
  }
  if (context.primaryView === 'management') {
    return {
      title: '项目管理',
      what: '集中查看计划、交付、资源、风险和指标等项目管理内容。',
      when: '需要跨工作项协调进度或判断项目风险时使用。',
      next: '选择当前要管理的内容块；没有权限的模块不会显示。',
    }
  }
  if (context.primaryView === 'members') {
    return {
      title: '成员',
      what: '管理空间成员、邀请和角色的治理入口。',
      when: '团队需要加入协作者、调整角色或完成工作交接时使用。',
      next: '按最小权限添加成员；最终能力以服务端当前授权为准。',
    }
  }
  if (context.primaryView === 'settings') {
    return {
      title: '空间管理',
      what: '集中管理成员以外的空间信息、工作模型、流程权限、自动化、度量、模板和生命周期。',
      when: '需要检查配置健康、处理配置待办或改变空间治理时使用。',
      next: '从管理首页选择对应入口；保存草稿、安装模板和发布配置是三个独立动作。',
    }
  }
  return {
    title: '成员工作区',
    what: '集中展示与你相关的待办、参与、关注、可用工作项和空间动态。',
    when: '进入空间或需要快速判断下一步时使用。',
    next: '先处理我的工作；管理者需要配置时进入空间管理。',
  }
}

export function onboardingErrorPresentation(error: unknown): Readonly<{
  title: string
  description: string
}> {
  const status = typeof error === 'object' && error !== null && 'status' in error
    ? Number((error as { status?: unknown }).status)
    : 0
  if (status === 403 || status === 404) {
    return {
      title: '无法加载此引导',
      description: '空间不存在或当前账号无权访问。请返回可访问空间，或联系空间 owner。',
    }
  }
  if (status === 409) {
    return {
      title: '引导状态已更新',
      description: '另一个页面刚刚更新了引导，请重新加载后继续。',
    }
  }
  if (status === 400) {
    return {
      title: '当前操作无法保存',
      description: '引导版本或步骤已经变化，请重新加载后再试。',
    }
  }
  return {
    title: '引导暂时不可用',
    description: '没有覆盖服务端状态。请检查网络后重试。',
  }
}

function safeOnboardingPath(path: string | null, spaceId?: string): path is string {
  if (!path?.startsWith('/')) return false
  if (path.startsWith('/notifications')) return true
  if (!path.startsWith('/project-spaces/')) return false
  if (!spaceId) return true
  return path === `/project-spaces/${spaceId}`
    || path.startsWith(`/project-spaces/${spaceId}/`)
    || path.startsWith(`/project-spaces/${spaceId}?`)
}

function isOnboardingWriteStep(item: ProjectSpaceOnboardingChecklistItem): boolean {
  const candidate = `${item.stepKey} ${item.labelKey} ${item.ownerContract}`.toLowerCase()
  return [
    'install',
    'create',
    'update',
    'comment',
    'attachment',
    'transition',
    'publish',
    'member',
    'invite',
    'configure',
    'handoff',
  ].some((fragment) => candidate.includes(fragment))
}
