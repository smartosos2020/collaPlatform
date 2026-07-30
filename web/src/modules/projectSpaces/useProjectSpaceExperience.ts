import { useQuery } from '@tanstack/react-query'
import {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react'

import { useSessionScope } from '../../shared/session/SessionScopeContext'
import {
  getProjectSpaceOnboarding,
  projectSpaceOnboardingKeys,
} from './api/projectSpaceOnboardingApi'
import {
  getProjectSpaceExperienceRollout,
  recordProjectSpaceExperienceTelemetry,
} from './api/projectSpaceExperienceApi'
import {
  canRecordProjectSpaceExperience,
  createProjectSpaceExperienceEvent,
  effectiveProjectSpaceExperienceRollout,
  normalizeProjectSpaceExperienceRollout,
  projectSpaceExperienceFreshness,
  projectSpaceExperienceQueryKey,
  sanitizeProjectSpaceExperienceEvents,
  type ProjectSpaceExperienceEventKind,
  type ProjectSpaceExperienceEventMode,
  type ProjectSpaceExperienceEventOutcome,
  type ProjectSpaceExperienceErrorCode,
  type ProjectSpaceExperienceRollout,
  type ProjectSpaceExperienceRouteKey,
} from './projectSpaceExperience'

export const projectSpaceExperienceKeys = {
  rollout: (workspaceId: string, userId: string, spaceId: string) =>
    projectSpaceExperienceQueryKey(
      workspaceId,
      userId,
      spaceId,
      'experience-rollout',
    ),
}

export function useProjectSpaceExperienceRollout(spaceId?: string) {
  const sessionScope = useSessionScope()
  const [evaluationClock, setEvaluationClock] = useState(() => Date.now())
  const query = useQuery({
    queryKey: projectSpaceExperienceKeys.rollout(
      sessionScope?.workspaceId ?? 'unknown',
      sessionScope?.userId ?? 'unknown',
      spaceId ?? 'unknown',
    ),
    queryFn: () => getProjectSpaceExperienceRollout(spaceId as string),
    enabled: Boolean(spaceId && sessionScope),
    retry: false,
    staleTime: 15_000,
    refetchOnWindowFocus: 'always',
    refetchOnReconnect: 'always',
    refetchInterval: (current) => {
      const rollout = current.state.data
        ? normalizeProjectSpaceExperienceRollout(current.state.data)
        : null
      if (!rollout || rollout.policyVersion === 'unknown') return false
      return Math.max(5_000, Math.min(60_000, rollout.cacheMaxAgeSeconds * 1_000))
    },
  })
  useEffect(() => {
    const normalized = normalizeProjectSpaceExperienceRollout(query.data)
    if (normalized.policyVersion === 'unknown') return
    const ttlMs = normalized.cacheMaxAgeSeconds * 1_000
    const expiresAt = Math.min(
      Date.parse(normalized.evaluatedAt) + ttlMs,
      query.dataUpdatedAt + ttlMs,
    )
    const delay = expiresAt - Date.now()
    const timer = window.setTimeout(
      () => setEvaluationClock(Date.now()),
      Math.max(0, Math.min(delay + 25, 2_147_483_647)),
    )
    return () => window.clearTimeout(timer)
  }, [query.data, query.dataUpdatedAt])
  const rollout = useMemo(
    () => effectiveProjectSpaceExperienceRollout(query.data, {
      now: Math.max(evaluationClock, query.dataUpdatedAt),
      receivedAt: query.dataUpdatedAt,
      requestFailed: query.isError || query.isRefetchError,
    }),
    [
      evaluationClock,
      query.data,
      query.dataUpdatedAt,
      query.isError,
      query.isRefetchError,
    ],
  )
  return { rollout, query }
}

export function useProjectSpaceExperienceTelemetry({
  spaceId,
  member,
  rollout,
  online,
}: {
  spaceId: string
  member: boolean
  rollout: ProjectSpaceExperienceRollout
  online: boolean
}) {
  const sessionScope = useSessionScope()
  const onboardingQuery = useQuery({
    queryKey: projectSpaceOnboardingKeys.detail(
      sessionScope?.workspaceId ?? 'unknown',
      sessionScope?.userId ?? 'unknown',
      spaceId,
    ),
    queryFn: () => getProjectSpaceOnboarding(spaceId),
    enabled: Boolean(sessionScope && member && rollout.telemetry.enabled),
    retry: false,
    staleTime: 30_000,
  })
  const optOut = onboardingQuery.data?.telemetryOptOut ?? true
  const record = useCallback((input: Readonly<{
    eventKind: ProjectSpaceExperienceEventKind
    routeKey: ProjectSpaceExperienceRouteKey
    mode: ProjectSpaceExperienceEventMode
    outcome: ProjectSpaceExperienceEventOutcome
    errorCode?: ProjectSpaceExperienceErrorCode
  }>) => {
    if (!canRecordProjectSpaceExperience({
      online,
      optOut,
      telemetryEnabled: rollout.telemetry.enabled,
    })) {
      return
    }
    const event = createProjectSpaceExperienceEvent({
      ...input,
      durationBucket: 'unknown',
      errorCode: input.errorCode ?? 'none',
      freshness: projectSpaceExperienceFreshness(rollout),
    })
    const events = sanitizeProjectSpaceExperienceEvents(
      [event],
      rollout.telemetry.maxBatchSize,
    )
    if (events.length > 0) {
      void recordProjectSpaceExperienceTelemetry(events).catch(() => undefined)
    }
  }, [online, optOut, rollout])

  return {
    optOut,
    ready: !onboardingQuery.isLoading,
    record,
  }
}
