type ActionSummary = Readonly<{
  availableActions: readonly string[]
}>

type WorkItemTypeSummary = ActionSummary & Readonly<{
  configurationReady: boolean
}>

/**
 * Member content is the intersection of runtime readiness and server actions.
 * Role names, display mode and local cache state deliberately do not participate.
 */
export function visibleProjectSpaceWorkItemTypes<T extends WorkItemTypeSummary>(
  types: readonly T[] | null | undefined,
): T[] {
  return (types ?? []).filter((type) => (
    type.configurationReady && type.availableActions.includes('view')
  ))
}

export function creatableProjectSpaceWorkItemTypes<T extends WorkItemTypeSummary>(
  types: readonly T[] | null | undefined,
): T[] {
  return visibleProjectSpaceWorkItemTypes(types)
    .filter((type) => type.availableActions.includes('create'))
}

export function visibleProjectSpacePersonalWork<T extends ActionSummary>(
  items: readonly T[] | null | undefined,
): T[] {
  return (items ?? []).filter((item) => item.availableActions.includes('view'))
}
