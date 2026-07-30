import { apiGet, apiPost } from '../../../shared/api/httpClient'
import type {
  ProjectSpaceExperienceRollout,
  ProjectSpaceExperienceTelemetryEvent,
} from '../projectSpaceExperience'

export function getProjectSpaceExperienceRollout(spaceId: string) {
  return apiGet<ProjectSpaceExperienceRollout>(
    `/project-spaces/${spaceId}/experience-rollout`,
  )
}

export function recordProjectSpaceExperienceTelemetry(
  events: readonly ProjectSpaceExperienceTelemetryEvent[],
) {
  return apiPost<void>('/project-space-experience/telemetry', {
    schemaVersion: 1,
    events,
  })
}
