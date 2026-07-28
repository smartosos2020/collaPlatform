import { expect, test } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi } from './support/api'
import {
  addMember,
  createGrant,
  createIdentity,
  createIntent,
  createItem,
  createPolicy,
  createSpace,
  getJson,
  grantLifecycle,
  intentLifecycle,
  policyLifecycle,
  postJson,
  publishedProjectType,
} from './support/crossSpace'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type SyncRule = {
  id: string
  status: string
  currentVersion: number
  sourceConfirmedBy?: string
  targetConfirmedBy?: string
}
type SyncRun = {
  id: string
  status: string
  resultTargetVersion?: number
  failureCode?: string
}
type Foundation = {
  rules: SyncRule[]
  runs: SyncRun[]
  conflicts: Array<{
    id: string
    runId: string
    kind: string
    status: string
    version: number
  }>
}

test.describe('PROJECT-PLATFORM-S18 M3', () => {
  test('bidirectional rule synchronizes by canonical command and governs conflicts @route-final', async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(480_000)
    page.setDefaultTimeout(25_000)
    requireIsolatedIdentityFixture()
    const enterprise = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(
      request, `${apiBaseUrl}/auth/me`, enterprise,
    )
    const suffix = `s18m3_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const sourceIdentity = await createIdentity(request, enterprise, `${suffix}_source`, 'S18 M3 Source Owner')
    const targetIdentity = await createIdentity(request, enterprise, `${suffix}_target`, 'S18 M3 Target Owner')
    const memberIdentity = await createIdentity(request, enterprise, `${suffix}_member`, 'S18 M3 Member')
    const guestIdentity = await createIdentity(request, enterprise, `${suffix}_guest`, 'S18 M3 Guest')
    const outsiderIdentity = await createIdentity(request, enterprise, `${suffix}_outsider`, 'S18 M3 Outsider')
    const sourceOwner = await loginByApi(request, sourceIdentity.username, sourceIdentity.password)
    const targetOwner = await loginByApi(request, targetIdentity.username, targetIdentity.password)
    const member = await loginByApi(request, memberIdentity.username, memberIdentity.password)
    const guest = await loginByApi(request, guestIdentity.username, guestIdentity.password)
    const outsider = await loginByApi(request, outsiderIdentity.username, outsiderIdentity.password)
    let sourceSpaceId: string | undefined
    let targetSpaceId: string | undefined

    try {
      sourceSpaceId = await createSpace(request, sourceOwner, suffix, 'source')
      targetSpaceId = await createSpace(request, targetOwner, suffix, 'target')
      await addMember(request, sourceOwner, sourceSpaceId, memberIdentity.id, 'member')
      await addMember(request, sourceOwner, sourceSpaceId, guestIdentity.id, 'guest')
      const sourceType = await publishedProjectType(request, sourceOwner, sourceSpaceId, `${suffix}-source`)
      const targetType = await publishedProjectType(request, targetOwner, targetSpaceId, `${suffix}-target`)
      const source = await createItem(
        request, sourceOwner, sourceSpaceId, sourceType.typeId,
        `S18 M3 source title ${suffix}`, `${suffix}-source-item`,
      )
      const target = await createItem(
        request, targetOwner, targetSpaceId, targetType.typeId,
        `S18 M3 target title ${suffix}`, `${suffix}-target-item`,
      )

      let grant = await createGrant(
        request, sourceOwner, sourceSpaceId, targetSpaceId,
        sourceType, targetType, `${suffix}-grant`,
      )
      grant = await grantLifecycle(request, sourceOwner, sourceSpaceId, grant, 'request', undefined, `${suffix}-grant-request`)
      grant = await grantLifecycle(request, sourceOwner, sourceSpaceId, grant, 'confirm', 'source', `${suffix}-grant-source`)
      grant = await grantLifecycle(request, targetOwner, targetSpaceId, grant, 'confirm', 'target', `${suffix}-grant-target`)
      expect(grant.status).toBe('active')

      let policy = await createPolicy(
        request, sourceOwner, sourceSpaceId, grant.id,
        sourceType, targetType, `${suffix}-policy`,
      )
      policy = await policyLifecycle(request, sourceOwner, sourceSpaceId, policy, 'request', undefined, `${suffix}-policy-request`)
      policy = await policyLifecycle(request, sourceOwner, sourceSpaceId, policy, 'confirm', 'source', `${suffix}-policy-source`)
      policy = await policyLifecycle(request, targetOwner, targetSpaceId, policy, 'confirm', 'target', `${suffix}-policy-target`)
      let intent = await createIntent(
        request, sourceOwner, sourceSpaceId, policy, source, target, `${suffix}-intent`,
      )
      intent = await intentLifecycle(
        request, targetOwner, targetSpaceId, intent, 'accept', `${suffix}-intent-accept`,
      )
      expect(intent.canonicalRelationId).toBeTruthy()

      let rule = await postJson<SyncRule>(
        request,
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/sync-rules`,
        sourceOwner,
        {
          schemaVersion: 1,
          requestId: `${suffix}-rule`,
          expectedVersion: 0,
          grantId: grant.id,
          policyId: policy.id,
          canonicalRelationId: intent.canonicalRelationId,
          name: `Title mirror ${suffix}`,
          direction: 'bidirectional',
          trigger: 'manual',
          fieldMappings: [{ sourceField: 'title', targetField: 'title', transform: 'copy' }],
          stateMappings: [],
          conflictStrategy: 'manual',
        },
        `${suffix}-rule`,
      )
      rule = await ruleLifecycle(request, sourceOwner, sourceSpaceId, rule, 'request', undefined, `${suffix}-rule-request`)
      rule = await ruleLifecycle(request, sourceOwner, sourceSpaceId, rule, 'confirm', 'source', `${suffix}-rule-source`)
      rule = await ruleLifecycle(request, targetOwner, targetSpaceId, rule, 'confirm', 'target', `${suffix}-rule-target`)
      expect(rule.status).toBe('active')

      const runBody = {
        schemaVersion: 1,
        requestId: `${suffix}-run`,
        expectedRuleVersion: rule.currentVersion,
        direction: 'source_to_target',
        originId: `${suffix}-origin`,
        causationId: `${suffix}-cause`,
        chainDepth: 0,
        expectedSourceVersion: source.version,
        expectedTargetVersion: target.version,
      }
      const run = await postJson<SyncRun>(
        request,
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/sync-rules/${rule.id}/runs`,
        sourceOwner,
        runBody,
        runBody.requestId,
      )
      expect(run.status).toBe('succeeded')
      const replay = await postJson<SyncRun>(
        request,
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/sync-rules/${rule.id}/runs`,
        sourceOwner,
        runBody,
        runBody.requestId,
      )
      expect(replay.id).toBe(run.id)
      const updatedTarget = await getJson<{ title: string; version: number }>(
        request,
        `${apiBaseUrl}/project-spaces/${targetSpaceId}/work-items/${target.id}`,
        targetOwner,
      )
      expect(updatedTarget.title).toBe(source.title)

      const deep = await request.post(
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/sync-rules/${rule.id}/runs`,
        {
          headers: { ...bearer(sourceOwner), 'X-Colla-Request-Id': `${suffix}-deep-run` },
          data: { ...runBody, requestId: `${suffix}-deep-run`, originId: `${suffix}-deep-origin`, chainDepth: 9 },
        },
      )
      expect(deep.status()).toBe(400)

      const conflict = await postJson<SyncRun>(
        request,
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/sync-rules/${rule.id}/runs`,
        sourceOwner,
        {
          ...runBody,
          requestId: `${suffix}-conflict-run`,
          originId: `${suffix}-conflict-origin`,
          expectedTargetVersion: 0,
        },
        `${suffix}-conflict-run`,
      )
      expect(conflict.status).toBe('conflict')
      let foundation = await getJson<Foundation>(
        request, `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/sync`, sourceOwner,
      )
      const openConflict = foundation.conflicts.find((candidate) => candidate.runId === conflict.id)
      expect(openConflict?.kind).toBe('target_version')

      const resolved = await postJson<{ status: string }>(
        request,
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/sync-conflicts/${openConflict!.id}/resolve`,
        sourceOwner,
        {
          schemaVersion: 1,
          requestId: `${suffix}-compensate`,
          expectedVersion: openConflict!.version,
          resolution: 'compensate',
          reason: 'record deterministic manual compensation',
        },
        `${suffix}-compensate`,
      )
      expect(resolved.status).toBe('compensated')

      grant = await grantLifecycle(
        request, sourceOwner, sourceSpaceId, grant, 'pause', undefined,
        `${suffix}-grant-pause`, 'temporary synchronization governance pause',
      )
      const revokedRun = await request.post(
        `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/sync-rules/${rule.id}/runs`,
        {
          headers: { ...bearer(sourceOwner), 'X-Colla-Request-Id': `${suffix}-revoked-run` },
          data: {
            ...runBody,
            requestId: `${suffix}-revoked-run`,
            originId: `${suffix}-revoked-origin`,
            expectedTargetVersion: updatedTarget.version,
          },
        },
      )
      expect(revokedRun.status()).toBe(403)
      grant = await grantLifecycle(request, sourceOwner, sourceSpaceId, grant, 'resume', undefined, `${suffix}-grant-resume`)
      expect(grant.status).toBe('active')

      for (const session of [member, guest]) {
        foundation = await getJson<Foundation>(
          request, `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/sync`, session,
        )
        expect(foundation.rules.some((candidate) => candidate.id === rule.id)).toBeTruthy()
      }
      for (const session of [outsider, enterprise]) {
        const hidden = await request.get(
          `${apiBaseUrl}/project-spaces/${sourceSpaceId}/cross-space/sync`,
          { headers: bearer(session) },
        )
        expect([403, 404]).toContain(hidden.status())
        expect(await hidden.text()).not.toContain(suffix)
      }

      await installSession(page, sourceOwner)
      await page.goto(`/project-spaces/${sourceSpaceId}`)
      const panel = page.getByTestId('cross-space-sync-panel')
      await expect(panel).toBeVisible()
      await expect(panel).toContainText(`Title mirror ${suffix}`)
      await expect(panel).toContainText('compensated')
      await expect(panel).not.toContainText(target.title)
      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 900 })
        await expect(panel).toBeVisible()
        expect(await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        )).toBeLessThanOrEqual(1)
      }
      await page.context().setOffline(true)
      await page.evaluate(() => window.dispatchEvent(new Event('offline')))
      await expect(panel).toContainText('当前离线')
      await expect(panel.getByRole('button', { name: '新建同步规则' })).toBeDisabled()
      await page.context().setOffline(false)
      await page.screenshot({
        path: testInfo.outputPath('s18-cross-space-sync-820.png'),
        fullPage: true,
      })
    } finally {
      await page.context().setOffline(false).catch(() => undefined)
      for (const entry of [
        { id: sourceSpaceId, session: sourceOwner },
        { id: targetSpaceId, session: targetOwner },
      ]) {
        if (entry.id) {
          await request.post(`${apiBaseUrl}/project-spaces/${entry.id}/settings/archive`, {
            headers: bearer(entry.session),
          }).catch(() => undefined)
        }
      }
      for (const identity of [
        targetIdentity, memberIdentity, guestIdentity, outsiderIdentity, sourceIdentity,
      ]) {
        await request.post(`${apiBaseUrl}/admin/users/${identity.id}/offboard`, {
          headers: bearer(enterprise),
          data: { handoverToUserId: enterpriseProfile.id },
        }).catch(() => undefined)
      }
    }
  })
})

function ruleLifecycle(
  request: Parameters<typeof postJson>[0],
  session: Parameters<typeof postJson>[2],
  spaceId: string,
  rule: SyncRule,
  action: string,
  party: string | undefined,
  requestId: string,
) {
  return postJson<SyncRule>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}/cross-space/sync-rules/${rule.id}/lifecycle`,
    session,
    { schemaVersion: 1, requestId, expectedVersion: rule.currentVersion, action, party },
    requestId,
  )
}
