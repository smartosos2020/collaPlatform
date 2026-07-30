import { apiGet, apiPost } from '../../../shared/api/httpClient'
import {
  PROJECT_SPACE_ONBOARDING_FLOW_VERSION,
  type ProjectSpaceOnboardingCommand,
  type ProjectSpaceOnboardingTelemetryEvent,
  type ProjectSpaceOnboardingView,
} from '../projectSpaceOnboarding'
import { projectSpaceExperienceQueryKey } from '../projectSpaceExperience'

export const projectSpaceOnboardingKeys = {
  detail: (workspaceId: string, userId: string, spaceId: string) =>
    projectSpaceExperienceQueryKey(workspaceId, userId, spaceId, 'onboarding'),
}

export function getProjectSpaceOnboarding(spaceId: string) {
  return apiGet<ProjectSpaceOnboardingView>(`/project-spaces/${spaceId}/onboarding`)
}

export function commandProjectSpaceOnboarding(
  spaceId: string,
  expectedVersion: number,
  command: ProjectSpaceOnboardingCommand,
  requestId = createOnboardingEventId(),
) {
  return apiPost<ProjectSpaceOnboardingView>(
    `/project-spaces/${spaceId}/onboarding/commands`,
    {
      requestId,
      schemaVersion: 1,
      flowVersion: PROJECT_SPACE_ONBOARDING_FLOW_VERSION,
      expectedVersion,
      ...command,
    },
    { requestId, retry: false },
  )
}

export async function recordProjectSpaceOnboardingTelemetry(
  spaceId: string,
  events: ProjectSpaceOnboardingTelemetryEvent[],
  telemetryOptOut: boolean,
) {
  if (telemetryOptOut || events.length === 0) return
  await apiPost<void>(
    `/project-spaces/${spaceId}/onboarding/telemetry`,
    { events: events.slice(0, 20) },
    { retry: false },
  )
}

export function createOnboardingEventId() {
  return globalThis.crypto?.randomUUID?.()
    ?? `onboarding-${Date.now()}-${Math.random().toString(36).slice(2)}`
}
