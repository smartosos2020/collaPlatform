import type { QueryClient, QueryKey } from '@tanstack/react-query'

export type RealtimeBusinessSignal = {
  type: string
  objectType?: string | null
  objectId?: string | null
  calibrationPath?: string | null
}

export type ActiveRealtimeResource = {
  objectType: string
  objectId: string
}

export type QueryAction = {
  queryKey: QueryKey
  exact: boolean
}

export const PROJECT_RECONCILIATION_TRIGGERS = [
  'object-changed',
  'access-invalidated',
  'permission-invalidated',
  'identity-invalidated',
] as const

export type ProjectReconciliationTrigger = typeof PROJECT_RECONCILIATION_TRIGGERS[number]

export type ReconciliationDecision = {
  matched: boolean
  trigger: ProjectReconciliationTrigger | null
  remove: QueryAction[]
  invalidate: QueryAction[]
  navigation: { action: 'stay' } | { action: 'exit'; to: string; reason: 'access-invalidated' }
}

export type ReconciliationContext = {
  activeResource?: ActiveRealtimeResource | null
  currentUserId?: string | null
}

export function projectRealtimeReconciliation(
  signal: RealtimeBusinessSignal,
  context: ReconciliationContext = {},
): ReconciliationDecision {
  const objectId = signal.objectId
  const objectType = signal.objectType
  if (!objectId || !objectType) return emptyDecision()

  if (signal.type === `${objectType}.changed`) {
    return changedDecision(objectType, objectId)
  }
  if (signal.type === `${objectType}.invalidated`) {
    return invalidatedDecision(objectType, objectId, context.activeResource)
  }
  if (signal.type === 'permission.invalidated') {
    return permissionDecision(objectType, objectId, context.activeResource)
  }
  if (signal.type === 'identity.invalidated') {
    return identityDecision(objectType, objectId, context)
  }
  return emptyDecision()
}

export async function applyReconciliationDecision(
  queryClient: QueryClient,
  decision: ReconciliationDecision,
) {
  for (const action of decision.remove) {
    queryClient.removeQueries({ queryKey: action.queryKey, exact: action.exact })
  }
  await Promise.all(
    decision.invalidate.map((action) =>
      queryClient.invalidateQueries({ queryKey: action.queryKey, exact: action.exact }),
    ),
  )
  return decision.navigation
}

function changedDecision(objectType: string, objectId: string): ReconciliationDecision {
  const invalidate = changedQueryActions(objectType, objectId)
  if (invalidate.length === 0) return emptyDecision()
  return {
    matched: true,
    trigger: 'object-changed',
    remove: [],
    invalidate,
    navigation: { action: 'stay' },
  }
}

function invalidatedDecision(
  objectType: string,
  objectId: string,
  activeResource?: ActiveRealtimeResource | null,
): ReconciliationDecision {
  const remove = protectedQueryActions(objectType, objectId)
  const invalidate = listQueryActions(objectType)
  if (remove.length === 0 && invalidate.length === 0) return emptyDecision()
  return {
    matched: true,
    trigger: 'access-invalidated',
    remove,
    invalidate,
    navigation: accessNavigation(objectType, objectId, activeResource),
  }
}

function permissionDecision(
  objectType: string,
  objectId: string,
  activeResource?: ActiveRealtimeResource | null,
): ReconciliationDecision {
  const remove = [
    ...protectedQueryActions(objectType, objectId),
    action(['resource-permissions', objectType, objectId], false),
  ]
  const invalidate = [
    ...listQueryActions(objectType),
    ...permissionIdentityActions(objectType),
  ]
  if (remove.length === 1 && invalidate.length === 0) return emptyDecision()
  return {
    matched: true,
    trigger: 'permission-invalidated',
    remove: uniqueActions(remove),
    invalidate: uniqueActions(invalidate),
    navigation: accessNavigation(objectType, objectId, activeResource),
  }
}

function identityDecision(
  objectType: string,
  objectId: string,
  context: ReconciliationContext,
): ReconciliationDecision {
  const invalidate: QueryAction[] = []
  if (objectType === 'user') {
    invalidate.push(action(['members', 'directory'], false), action(['admin', 'users'], false))
    if (objectId === context.currentUserId) invalidate.push(action(['auth', 'me'], true))
  } else if (objectType === 'department') {
    invalidate.push(action(['members', 'directory'], false), action(['admin', 'departments'], false))
  } else if (objectType === 'user_group') {
    invalidate.push(action(['admin', 'user-groups'], false))
  } else if (objectType === 'role') {
    invalidate.push(action(['admin', 'roles'], false), action(['auth', 'me'], true))
  } else {
    return emptyDecision()
  }
  return {
    matched: true,
    trigger: 'identity-invalidated',
    remove: [],
    invalidate: uniqueActions(invalidate),
    navigation: { action: 'stay' },
  }
}

function changedQueryActions(objectType: string, objectId: string): QueryAction[] {
  if (objectType === 'project') {
    return [
      action(['projects'], true),
      action(['projects', objectId], true),
      action(['projects', objectId, 'stats'], false),
      action(['projects', objectId, 'issues'], false),
    ]
  }
  if (objectType === 'issue') {
    return [action(['issues', objectId], false), action(['projects'], false)]
  }
  if (objectType === 'project_space') {
    return [action(['project-spaces'], true), action(['project-spaces', objectId], false)]
  }
  return []
}

function protectedQueryActions(objectType: string, objectId: string): QueryAction[] {
  if (objectType === 'project') {
    return [action(['projects', objectId], false), action(['issues'], false)]
  }
  if (objectType === 'issue') return [action(['issues', objectId], false)]
  if (objectType === 'project_space') return [action(['project-spaces', objectId], false)]
  if (objectType === 'base') return [action(['bases', objectId], false)]
  if (objectType === 'knowledge_base') {
    return [action(['knowledge-bases', objectId], false), action(['knowledge-content'], false)]
  }
  if (objectType === 'knowledge_content') return [action(['knowledge-content'], false)]
  return []
}

function listQueryActions(objectType: string): QueryAction[] {
  if (objectType === 'project' || objectType === 'issue') return [action(['projects'], true)]
  if (objectType === 'project_space') return [action(['project-spaces'], true)]
  if (objectType === 'base') return [action(['bases'], true)]
  if (objectType === 'knowledge_base' || objectType === 'knowledge_content') {
    return [action(['knowledge-bases'], false)]
  }
  return []
}

function permissionIdentityActions(objectType: string): QueryAction[] {
  if (objectType === 'role' || objectType === 'user' || objectType === 'user_group' || objectType === 'department') {
    return [action(['auth', 'me'], true), action(['members', 'directory'], false)]
  }
  return []
}

function accessNavigation(
  objectType: string,
  objectId: string,
  activeResource?: ActiveRealtimeResource | null,
): ReconciliationDecision['navigation'] {
  if (activeResource?.objectType !== objectType || activeResource.objectId !== objectId) {
    return { action: 'stay' }
  }
  const target = safeExitTarget(objectType)
  return target
    ? { action: 'exit', to: target, reason: 'access-invalidated' }
    : { action: 'stay' }
}

function safeExitTarget(objectType: string) {
  if (objectType === 'project' || objectType === 'issue') return '/projects'
  if (objectType === 'project_space') return '/project-spaces'
  if (objectType === 'base') return '/bases'
  if (objectType === 'knowledge_base' || objectType === 'knowledge_content') return '/knowledge-bases'
  return null
}

function action(queryKey: QueryKey, exact: boolean): QueryAction {
  return { queryKey, exact }
}

function uniqueActions(actions: QueryAction[]) {
  const seen = new Set<string>()
  return actions.filter((item) => {
    const key = `${JSON.stringify(item.queryKey)}:${item.exact}`
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

function emptyDecision(): ReconciliationDecision {
  return {
    matched: false,
    trigger: null,
    remove: [],
    invalidate: [],
    navigation: { action: 'stay' },
  }
}
