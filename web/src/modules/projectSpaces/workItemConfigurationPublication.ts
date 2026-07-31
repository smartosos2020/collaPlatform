export type WorkItemConfigurationPublicationBaseline = {
  completeSnapshot: boolean
}

export type WorkItemConfigurationPublicationQueries = {
  versionsQuerySucceeded: boolean
  compatibilityQuerySucceeded: boolean
}

export function requiresWorkItemConfigurationCompatibility(
  currentVersion: WorkItemConfigurationPublicationBaseline | undefined,
) {
  return currentVersion?.completeSnapshot === true
}

export function isWorkItemConfigurationCompatibilityReady(
  currentVersion: WorkItemConfigurationPublicationBaseline | undefined,
  queries: WorkItemConfigurationPublicationQueries,
) {
  if (!queries.versionsQuerySucceeded) return false
  return !requiresWorkItemConfigurationCompatibility(currentVersion)
    || queries.compatibilityQuerySucceeded
}
