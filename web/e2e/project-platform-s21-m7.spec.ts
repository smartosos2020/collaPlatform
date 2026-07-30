import { randomUUID } from 'node:crypto'

import {
  expect,
  test,
  type APIRequestContext,
  type Browser,
  type BrowserContext,
  type Page,
} from '@playwright/test'

import {
  apiBaseUrl,
  bearer,
  installSession,
  loginByApi,
  webBaseUrl,
  type E2eSession,
} from './support/api'
import {
  addMember,
  createIdentity,
  getJson,
  type Identity,
} from './support/crossSpace'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type SpaceView = {
  id: string
  status: 'active' | 'disabled' | 'archived'
  name: string
  currentUserRole?: string | null
  availableActions: string[]
}

type CurrentUser = {
  id: string
  workspaceId: string
}

type ExperiencePreference = {
  schemaVersion: number
  mode: 'simple' | 'advanced'
  version: number
  availableModes: Array<'simple' | 'advanced'>
}

type OnboardingView = {
  schemaVersion: number
  flowVersion: string
  version: number
  startingPoint: {
    kind: 'unselected' | 'blank' | 'scenario'
    scenarioKey: ScenarioKey | null
  }
  acknowledgedSteps: Array<{ stepKey: string; outcome: string }>
  dismissed: boolean
  telemetryOptOut: boolean
  selectionEffect: string
  installationRequested: boolean
  publicationRequested: boolean
}

type RolloutState = 'enabled' | 'baseline' | 'temporarily_disabled' | 'unknown'

type RolloutView = {
  schemaVersion: number
  policyVersion: string
  enabled: boolean
  state: RolloutState
  fallbackContext: string
  evaluatedAt: string
  cacheMaxAgeSeconds: number
  telemetry: {
    schemaVersion: number
    enabled: boolean
    sampleBasisPoints: number
    maxBatchSize: number
  }
}

type ScenarioKey =
  | 'development'
  | 'marketing'
  | 'human-resources'
  | 'delivery'

type ScenarioFoundation = {
  templates: Array<{
    scenarioKey: string
    currentVersion: {
      manifestHash: string
    }
  }>
}

type TypeConfiguration = {
  items: Array<{
    id: string
    typeKey: string
    aggregateVersion: number
    currentVersion?: {
      id?: string
      versionNumber?: number
    } | null
    status?: string
    sortOrder?: number
  }>
}

type BrowserRequest = {
  method: string
  url: string
  postData: string | null
}

const scenarioKeys: readonly ScenarioKey[] = [
  'development',
  'marketing',
  'human-resources',
  'delivery',
]

const navigationLabels = ['概览', '工作项', '项目管理', '成员', '设置'] as const

test.describe('PROJECT-PLATFORM-S21-M7 migration and engineering readiness', () => {
  test('@smoke validates real isolated compatibility, rollout and recovery boundaries', async ({
    browser,
    page,
    request,
  }, testInfo) => {
    test.setTimeout(420_000)
    requireIsolatedIdentityFixture()

    const enterprise = await loginByApi(request)
    const enterpriseProfile = await getJson<CurrentUser>(
      request,
      `${apiBaseUrl}/auth/me`,
      enterprise,
    )
    const suffix = `s21m7_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const identities: Identity[] = []
    const extraContexts: BrowserContext[] = []
    let owner: E2eSession | undefined
    let primarySpaceId: string | undefined
    let secondarySpaceId: string | undefined
    const cleanupFailures: string[] = []

    try {
      const ownerIdentity = await provisionIdentity(
        request, enterprise, suffix, 'owner', 'S21 M7 Owner',
      )
      const adminIdentity = await provisionIdentity(
        request, enterprise, suffix, 'admin', 'S21 M7 Space Admin',
      )
      const memberIdentity = await provisionIdentity(
        request, enterprise, suffix, 'member', 'S21 M7 Member',
      )
      const guestIdentity = await provisionIdentity(
        request, enterprise, suffix, 'guest', 'S21 M7 Guest',
      )
      const outsiderIdentity = await provisionIdentity(
        request, enterprise, suffix, 'outsider', 'S21 M7 Outsider',
      )
      identities.push(
        ownerIdentity,
        adminIdentity,
        memberIdentity,
        guestIdentity,
        outsiderIdentity,
      )

      const sessions = await Promise.all([
        loginByApi(request, ownerIdentity.username, ownerIdentity.password),
        loginByApi(request, adminIdentity.username, adminIdentity.password),
        loginByApi(request, memberIdentity.username, memberIdentity.password),
        loginByApi(request, guestIdentity.username, guestIdentity.password),
        loginByApi(request, outsiderIdentity.username, outsiderIdentity.password),
      ])
      const [ownerSession, admin, member, guest, outsider] = sessions
      owner = ownerSession
      const ownerProfile = await getJson<CurrentUser>(
        request,
        `${apiBaseUrl}/auth/me`,
        owner,
      )

      const longSpaceName = `S21 M7 ${'迁移恢复与安全工程复验'.repeat(6)}`.slice(0, 120)
      primarySpaceId = await createSpace(
        request,
        owner,
        `s21-m7-primary-${suffix.replaceAll('_', '-')}`,
        longSpaceName,
      )
      secondarySpaceId = await createSpace(
        request,
        owner,
        `s21-m7-secondary-${suffix.replaceAll('_', '-')}`,
        `S21 M7 cache isolation ${suffix}`,
      )
      await addMember(request, owner, primarySpaceId, adminIdentity.id, 'admin')
      await addMember(request, owner, primarySpaceId, memberIdentity.id, 'member')
      await addMember(request, owner, primarySpaceId, guestIdentity.id, 'guest')

      const [ownerSpace, adminSpace, memberSpace, guestSpace] = await Promise.all([
        getSpace(request, owner, primarySpaceId),
        getSpace(request, admin, primarySpaceId),
        getSpace(request, member, primarySpaceId),
        getSpace(request, guest, primarySpaceId),
      ])
      expect(ownerSpace.availableActions).toEqual(expect.arrayContaining([
        'view_overview',
        'view_work_items',
        'view_project_management',
        'view_members',
        'view_settings',
      ]))
      expect(adminSpace.availableActions).toEqual(expect.arrayContaining([
        'view_overview',
        'view_work_items',
        'view_project_management',
        'view_members',
        'view_settings',
      ]))
      expect(memberSpace.availableActions).toEqual(expect.arrayContaining([
        'view_overview',
        'view_work_items',
        'view_project_management',
      ]))
      expect(memberSpace.availableActions).not.toContain('view_settings')
      expect(guestSpace.availableActions).toEqual(expect.arrayContaining([
        'view_overview',
        'view_work_items',
      ]))
      expect(guestSpace.availableActions).not.toContain('view_project_management')

      const rolloutViews = await Promise.all(
        [owner, admin, member, guest].map(
          session => getRollout(request, session, primarySpaceId as string),
        ),
      )
      for (const rollout of rolloutViews) {
        expectRolloutContract(rollout)
      }
      const expectedRolloutState = process.env.COLLA_E2E_EXPECTED_ROLLOUT_STATE
      if (expectedRolloutState) {
        expect(rolloutViews[0].state).toBe(expectedRolloutState)
      }

      for (const hiddenSession of [outsider, enterprise]) {
        const hiddenSpace = await request.get(
          `${apiBaseUrl}/project-spaces/${primarySpaceId}`,
          { headers: bearer(hiddenSession) },
        )
        expect(hiddenSpace.status()).toBe(404)
        expect(await hiddenSpace.text()).not.toContain(longSpaceName)

        const hiddenRollout = await request.get(
          rolloutUrl(primarySpaceId),
          { headers: bearer(hiddenSession) },
        )
        expect(hiddenRollout.status()).toBe(404)
        const body = await hiddenRollout.text()
        expect(body).not.toContain(longSpaceName)
        expect(body).not.toContain('policyVersion')
      }

      for (const readOnlySession of [member, guest]) {
        const preference = await getPreference(
          request,
          readOnlySession,
          primarySpaceId,
        )
        expect(preference.availableModes).toEqual(['simple'])
        const forbiddenMode = await request.put(preferenceUrl(primarySpaceId), {
          headers: {
            ...bearer(readOnlySession),
            'X-Colla-Request-Id': randomUUID(),
          },
          data: {
            schemaVersion: 1,
            mode: 'advanced',
            expectedVersion: preference.version,
          },
        })
        expect([403, 404]).toContain(forbiddenMode.status())
        expect(await forbiddenMode.text()).not.toContain(longSpaceName)

        const forbiddenDryRun = await request.post(
          `${apiBaseUrl}/project-spaces/${primarySpaceId}`
          + '/scenario-templates/development/dry-run',
          {
            headers: {
              ...bearer(readOnlySession),
              'X-Colla-Request-Id': randomUUID(),
            },
            data: {
              requestId: randomUUID(),
              localManifestHash: null,
              conflictResolutions: {},
            },
          },
        )
        expect([403, 404]).toContain(forbiddenDryRun.status())
        expect(await forbiddenDryRun.text()).not.toContain(longSpaceName)
      }

      const foundation = await getJson<ScenarioFoundation>(
        request,
        `${apiBaseUrl}/project-spaces/${primarySpaceId}/scenario-templates`,
        owner,
      )
      expect(foundation.templates.map(template => template.scenarioKey))
        .toEqual(expect.arrayContaining(scenarioKeys))
      for (const scenarioKey of scenarioKeys) {
        const validation = await getJson<{ valid: boolean; manifestHash: string }>(
          request,
          `${apiBaseUrl}/project-spaces/${primarySpaceId}`
          + `/scenario-templates/${scenarioKey}/validation`,
          owner,
        )
        expect(validation.valid).toBe(true)
        expect(validation.manifestHash).toBeTruthy()
      }

      const businessBefore = await captureBusinessSnapshot(
        request,
        owner,
        primarySpaceId,
      )

      const telemetryEvent = {
        eventId: randomUUID(),
        eventKind: 'entry',
        routeKey: 'overview',
        mode: rolloutViews[0].enabled ? 'simple' : 'baseline',
        outcome: 'shown',
        durationBucket: 'under_5s',
        errorCode: 'none',
        freshness: 'fresh',
      }
      const telemetry = await request.post(
        `${apiBaseUrl}/project-space-experience/telemetry`,
        {
          headers: bearer(owner),
          data: { schemaVersion: 1, events: [telemetryEvent] },
        },
      )
      expect(telemetry.status()).toBe(204)

      const oversizedTelemetry = await request.post(
        `${apiBaseUrl}/project-space-experience/telemetry`,
        {
          headers: bearer(owner),
          data: {
            schemaVersion: 1,
            events: Array.from({ length: 21 }, () => ({
              ...telemetryEvent,
              eventId: randomUUID(),
            })),
          },
        },
      )
      expect(oversizedTelemetry.status()).toBe(400)

      const invalidTelemetry = await request.post(
        `${apiBaseUrl}/project-space-experience/telemetry`,
        {
          headers: bearer(owner),
          data: {
            schemaVersion: 1,
            events: [{ ...telemetryEvent, eventId: randomUUID(), eventKind: 'content' }],
          },
        },
      )
      expect(invalidTelemetry.status()).toBe(400)
      const anonymousTelemetry = await request.post(
        `${apiBaseUrl}/project-space-experience/telemetry`,
        { data: { schemaVersion: 1, events: [telemetryEvent] } },
      )
      expect([401, 403]).toContain(anonymousTelemetry.status())

      await installSession(page, owner)
      const ownerRecentKey = scopedCacheKey(
        ownerProfile.workspaceId,
        ownerProfile.id,
        undefined,
        'recent',
      )
      const protectedDrafts = protectedDraftFixture(primarySpaceId, suffix)
      await page.evaluate(
        ({ recentKey, primaryId, secondaryId, drafts }) => {
          localStorage.setItem(
            'colla.project-spaces.recent',
            JSON.stringify([secondaryId, primaryId]),
          )
          localStorage.setItem(recentKey, '{corrupt')
          for (const [key, value] of Object.entries(drafts)) {
            localStorage.setItem(key, value)
          }
        },
        {
          recentKey: ownerRecentKey,
          primaryId: primarySpaceId,
          secondaryId: secondarySpaceId,
          drafts: protectedDrafts,
        },
      )

      const legacyProjectId = randomUUID()
      let legacyResolverCalls = 0
      page.on('request', current => {
        if (current.url().includes(`/legacy-resolve/${legacyProjectId}`)) {
          legacyResolverCalls += 1
        }
      })
      const legacyResolveResponse = page.waitForResponse(current =>
        current.url().includes(`/api/project-spaces/legacy-resolve/${legacyProjectId}`)
        && current.request().method() === 'GET')
      await page.goto(
        `/projects/${legacyProjectId}`
        + '?source=m7-compat&panel=project-plan'
        + '&returnUrl=https%3A%2F%2Fevil.example#overview',
      )
      const legacyResolve = await legacyResolveResponse
      expect(legacyResolve.ok()).toBe(true)
      const legacyResolution = await legacyResolve.json() as {
        status: string
        spaceId?: string | null
      }
      expect(legacyResolution.status).toBe('unmigrated')
      expect(legacyResolution.spaceId ?? null).toBeNull()
      expect(legacyResolution).not.toHaveProperty('spaceName')
      expect(legacyResolution).not.toHaveProperty('members')
      await expect(page.getByText('项目链接不可用')).toBeVisible()
      await expect(page.getByText(longSpaceName)).toHaveCount(0)
      await page.getByRole('button', { name: '返回项目空间' }).click()
      await expect(page.getByTestId('project-spaces-page')).toBeVisible()
      await expect.poll(() => new URL(page.url()).pathname).toContain('/project-spaces')
      const recoveredLocation = new URL(page.url())
      expect(recoveredLocation.searchParams.get('source')).toBe('m7-compat')
      expect(recoveredLocation.searchParams.has('panel')).toBe(false)
      expect(recoveredLocation.searchParams.has('returnUrl')).toBe(false)
      expect(recoveredLocation.hash).toBe('#overview')
      await page.waitForTimeout(200)
      expect(legacyResolverCalls).toBe(1)

      const browserRequests: BrowserRequest[] = []
      page.on('request', current => browserRequests.push({
        method: current.method(),
        url: current.url(),
        postData: current.postData(),
      }))
      const rolloutResponse = page.waitForResponse(current =>
        current.url().endsWith(`/api/project-spaces/${primarySpaceId}/experience-rollout`)
        && current.request().method() === 'GET')
      await page.goto(`/project-spaces/${primarySpaceId}?source=m7-real#overview`)
      const browserRollout = await rolloutResponse.then(
        async current => await current.json() as RolloutView,
      )
      expectRolloutContract(browserRollout)
      await expect(page.getByRole('heading', { name: longSpaceName })).toBeVisible()
      await expectPrimaryNavigation(page, navigationLabels)
      const experienceBoundary = page.getByTestId('project-space-experience-boundary')
      await expect(experienceBoundary).toHaveAttribute(
        'data-rollout-state',
        browserRollout.state,
      )
      await expect(experienceBoundary).toHaveAttribute(
        'data-rollout-policy',
        browserRollout.policyVersion,
      )
      await expect(experienceBoundary).toHaveAttribute(
        'data-experience-mode',
        browserRollout.enabled ? 'simple' : 'baseline',
      )
      await expect(page.getByTestId('project-space-mode-switch')).toHaveCount(
        browserRollout.enabled ? 1 : 0,
      )

      const ownerStorage = await page.evaluate(
        ({ recentKey, draftKeys }) => ({
          recent: localStorage.getItem(recentKey),
          legacyRecent: localStorage.getItem('colla.project-spaces.recent'),
          drafts: Object.fromEntries(
            draftKeys.map(key => [key, localStorage.getItem(key)]),
          ),
        }),
        {
          recentKey: ownerRecentKey,
          draftKeys: Object.keys(protectedDrafts),
        },
      )
      expect(ownerStorage.legacyRecent).not.toBeNull()
      expect(ownerStorage.drafts).toEqual(protectedDrafts)
      const recentEnvelope = JSON.parse(ownerStorage.recent as string) as {
        schemaVersion: number
        expiresAt: number
        value: string[]
      }
      expect(recentEnvelope.schemaVersion).toBe(2)
      expect(recentEnvelope.expiresAt).toBeGreaterThan(Date.now())
      expect(recentEnvelope.value).toEqual(
        expect.arrayContaining([primarySpaceId, secondarySpaceId]),
      )

      if (browserRollout.enabled) {
        await openOnboarding(page)
        for (const label of ['研发模板', '市场模板', 'HR 模板', '交付模板', '基础空间']) {
          await expect(page.getByRole('radio', { name: label })).toBeVisible()
        }
        await page.keyboard.press('Escape')
        await expect(page.getByTestId('project-space-onboarding')).not.toBeVisible()
      } else {
        await expect(page.getByTestId('project-space-onboarding-open')).toHaveCount(0)
      }

      const ownerOptionalRequests = browserRequests
        .filter(current => current.method === 'GET')
        .map(current => current.url)
        .filter(isHiddenOptionalRequest)
      expect(ownerOptionalRequests).toEqual([])

      const memberProfile = await getJson<CurrentUser>(
        request,
        `${apiBaseUrl}/auth/me`,
        member,
      )
      const memberRecentKey = scopedCacheKey(
        memberProfile.workspaceId,
        memberProfile.id,
        undefined,
        'recent',
      )
      const memberSurface = await openRoleSurface(
        browser,
        member,
        primarySpaceId,
        {
          [ownerRecentKey]: ownerStorage.recent as string,
          'colla.project-spaces.recent': JSON.stringify([
            secondarySpaceId,
            primarySpaceId,
          ]),
        },
      )
      extraContexts.push(memberSurface.context)
      await expectPrimaryNavigation(
        memberSurface.page,
        ['概览', '工作项', '项目管理'],
      )
      await expect(memberSurface.page.getByTestId('project-space-mode-switch')).toHaveCount(0)
      expect(memberSurface.requests.some(current =>
        isHiddenOptionalRequest(current.url))).toBe(false)
      const memberRequestsBeforeDeniedSample = memberSurface.requests.length
      await memberSurface.page.goto(
        `/project-spaces/${primarySpaceId}/types/denied-type/sample?source=m7-member#overview`,
      )
      await expect(
        memberSurface.page.getByText('无权访问当前空间内容'),
      ).toBeVisible()
      expect(
        memberSurface.requests
          .slice(memberRequestsBeforeDeniedSample)
          .filter(current =>
            current.url.includes('/layouts/')
            && current.url.includes('/sample')),
      ).toEqual([])
      const memberStorage = await memberSurface.page.evaluate(
        ({ memberKey, ownerKey }) => ({
          member: localStorage.getItem(memberKey),
          owner: localStorage.getItem(ownerKey),
        }),
        { memberKey: memberRecentKey, ownerKey: ownerRecentKey },
      )
      expect(memberStorage.owner).toBe(ownerStorage.recent)
      const memberEnvelope = JSON.parse(memberStorage.member as string) as {
        schemaVersion: number
        value: string[]
      }
      expect(memberEnvelope.schemaVersion).toBe(2)
      expect(memberEnvelope.value).toEqual([primarySpaceId])

      const adminSurface = await openRoleSurface(browser, admin, primarySpaceId)
      extraContexts.push(adminSurface.context)
      await expectPrimaryNavigation(adminSurface.page, navigationLabels)

      const guestSurface = await openRoleSurface(browser, guest, primarySpaceId)
      extraContexts.push(guestSurface.context)
      await expectPrimaryNavigation(guestSurface.page, ['概览', '工作项'])
      await expect(guestSurface.page.getByTestId('project-space-mode-switch')).toHaveCount(0)
      expect(guestSurface.requests.some(current =>
        isHiddenOptionalRequest(current.url))).toBe(false)

      for (const hiddenSession of [outsider, enterprise]) {
        const hiddenSurface = await openHiddenSurface(
          browser,
          hiddenSession,
          primarySpaceId,
        )
        extraContexts.push(hiddenSurface.context)
        await expect(hiddenSurface.page.getByText('空间不存在或你无权访问')).toBeVisible()
        await expect(hiddenSurface.page.getByText(longSpaceName)).toHaveCount(0)
        await expect(
          hiddenSurface.page.getByTestId('project-space-primary-navigation'),
        ).toHaveCount(0)
      }

      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 980 })
        expect(await page.evaluate(
          () => document.documentElement.scrollWidth
            - document.documentElement.clientWidth,
        )).toBeLessThanOrEqual(1)
      }
      const primaryNavigation = page.getByTestId('project-space-primary-navigation')
      await primaryNavigation.getByRole('button', { name: '概览' }).focus()
      await page.keyboard.press('Tab')
      await expect(
        primaryNavigation.getByRole('button', { name: '工作项' }),
      ).toBeFocused()

      const writesBeforeOffline = browserRequests.filter(
        current => current.method !== 'GET',
      ).length
      await page.context().setOffline(true)
      await page.evaluate(() => window.dispatchEvent(new Event('offline')))
      if (browserRollout.enabled) {
        await expect(experienceBoundary).toHaveAttribute(
          'data-experience-mode',
          'simple',
        )
        const offlineModeSwitch = page.getByTestId('project-space-mode-switch')
        if (await offlineModeSwitch.count() > 0) {
          await expect(
            offlineModeSwitch.locator('.ant-segmented'),
          ).toHaveClass(/ant-segmented-disabled/)
        }
        await page.getByTestId('project-space-onboarding-open').click()
        await expect(page.getByTestId('project-space-onboarding')).toBeVisible()
        await page.keyboard.press('Escape')
      }
      await page.waitForTimeout(250)
      expect(browserRequests.filter(current => current.method !== 'GET'))
        .toHaveLength(writesBeforeOffline)
      await page.context().setOffline(false)
      await page.evaluate(() => {
        window.dispatchEvent(new Event('online'))
        window.dispatchEvent(new Event('focus'))
      })
      if (browserRollout.enabled) {
        await expect(
          page.getByTestId('project-space-mode-switch').locator('.ant-segmented'),
        ).not.toHaveClass(/ant-segmented-disabled/)
      }

      const telemetryPayloads = browserRequests
        .filter(current =>
          current.method === 'POST'
          && (
            current.url.endsWith('/api/project-space-experience/telemetry')
            || current.url.endsWith('/onboarding/telemetry')
          ))
        .map(current => current.postData ?? '')
      if (browserRollout.enabled) {
        expect(telemetryPayloads.length).toBeGreaterThan(0)
      } else {
        expect(telemetryPayloads).toEqual([])
      }
      for (const payload of telemetryPayloads) {
        expect(payload).not.toMatch(
          /(?:workspaceId|spaceId|userId|title|body|content|fieldValue|fileName|candidate|employee|customer)/i,
        )
      }
      const unexpectedWrites = browserRequests.filter(current =>
        current.method !== 'GET'
        && !current.url.endsWith('/api/project-space-experience/telemetry')
        && !current.url.endsWith('/onboarding/telemetry'))
      expect(unexpectedWrites).toEqual([])

      const businessAfter = await captureBusinessSnapshot(
        request,
        owner,
        primarySpaceId,
      )
      expect(businessAfter).toEqual(businessBefore)
      const ownerSpaceAfter = await getSpace(request, owner, primarySpaceId)
      expect(ownerSpaceAfter.availableActions).toEqual(ownerSpace.availableActions)

      const protectedAfter = await page.evaluate(
        keys => Object.fromEntries(keys.map(key => [key, localStorage.getItem(key)])),
        Object.keys(protectedDrafts),
      )
      expect(protectedAfter).toEqual(protectedDrafts)

      await page.setViewportSize({ width: 820, height: 980 })
      await page.screenshot({
        path: testInfo.outputPath('s21-m7-migration-readiness-820.png'),
        fullPage: true,
      })
    } finally {
      await page.context().setOffline(false).catch(error => {
        cleanupFailures.push(`restore browser network: ${String(error)}`)
      })
      await Promise.all(
        extraContexts.map(context => context.close().catch(error => {
          cleanupFailures.push(`close browser context: ${String(error)}`)
        })),
      )
      if (owner && primarySpaceId) {
        await archiveIfNeeded(request, owner, primarySpaceId).catch(error => {
          cleanupFailures.push(String(error))
        })
      }
      if (owner && secondarySpaceId) {
        await archiveIfNeeded(request, owner, secondarySpaceId).catch(error => {
          cleanupFailures.push(String(error))
        })
      }
      for (const identity of identities.reverse()) {
        await offboardIdentity(
          request,
          enterprise,
          enterpriseProfile.id,
          identity,
          suffix,
        ).catch(error => {
          cleanupFailures.push(String(error))
        })
      }
    }
    expect(
      cleanupFailures,
      `S21 M7 cleanup failed:\n${cleanupFailures.join('\n')}`,
    ).toEqual([])
  })
})

async function provisionIdentity(
  request: APIRequestContext,
  enterprise: E2eSession,
  suffix: string,
  role: string,
  displayName: string,
) {
  return createIdentity(
    request,
    enterprise,
    `${suffix}_${role}`,
    displayName,
  )
}

async function createSpace(
  request: APIRequestContext,
  owner: E2eSession,
  spaceKey: string,
  name: string,
) {
  const response = await request.post(`${apiBaseUrl}/project-spaces`, {
    headers: {
      ...bearer(owner),
      'X-Colla-Request-Id': `create-${spaceKey}`,
    },
    data: {
      spaceKey,
      name,
      description: 'S21 M7 real isolated migration and readiness fixture',
      visibility: 'private',
    },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  return (await response.json() as { id: string }).id
}

function getSpace(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
) {
  return getJson<SpaceView>(
    request,
    `${apiBaseUrl}/project-spaces/${spaceId}`,
    session,
  )
}

function preferenceUrl(spaceId: string) {
  return `${apiBaseUrl}/project-spaces/${spaceId}/experience-preference`
}

function getPreference(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
) {
  return getJson<ExperiencePreference>(
    request,
    preferenceUrl(spaceId),
    session,
  )
}

function rolloutUrl(spaceId: string) {
  return `${apiBaseUrl}/project-spaces/${spaceId}/experience-rollout`
}

function getRollout(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
) {
  return getJson<RolloutView>(request, rolloutUrl(spaceId), session)
}

function expectRolloutContract(rollout: RolloutView) {
  expect(rollout.schemaVersion).toBe(1)
  expect(rollout.policyVersion).toBeTruthy()
  expect([
    'enabled',
    'baseline',
    'temporarily_disabled',
    'unknown',
  ]).toContain(rollout.state)
  expect(rollout.enabled).toBe(rollout.state === 'enabled')
  expect(rollout.fallbackContext).toBe('canonical_project_space')
  expect(Number.isFinite(Date.parse(rollout.evaluatedAt))).toBe(true)
  expect(rollout.cacheMaxAgeSeconds).toBeGreaterThanOrEqual(0)
  expect(rollout.cacheMaxAgeSeconds).toBeLessThanOrEqual(3_600)
  expect(rollout.telemetry.schemaVersion).toBe(1)
  expect(rollout.telemetry.sampleBasisPoints).toBeGreaterThanOrEqual(0)
  expect(rollout.telemetry.sampleBasisPoints).toBeLessThanOrEqual(10_000)
  expect(rollout.telemetry.maxBatchSize).toBeGreaterThanOrEqual(1)
  expect(rollout.telemetry.maxBatchSize).toBeLessThanOrEqual(20)
  expect(rollout).not.toHaveProperty('role')
  expect(rollout).not.toHaveProperty('capabilities')
  expect(rollout).not.toHaveProperty('spaceName')
  expect(rollout).not.toHaveProperty('userId')
}

async function captureBusinessSnapshot(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
) {
  const [configuration, onboarding, preference] = await Promise.all([
    getJson<TypeConfiguration>(
      request,
      `${apiBaseUrl}/project-spaces/${spaceId}/configuration/types`,
      session,
    ),
    getJson<OnboardingView>(
      request,
      `${apiBaseUrl}/project-spaces/${spaceId}/onboarding`,
      session,
    ),
    getPreference(request, session, spaceId),
  ])
  const installations = Object.fromEntries(await Promise.all(
    scenarioKeys.map(async scenarioKey => [
      scenarioKey,
      await getScenarioInstallation(request, session, spaceId, scenarioKey),
    ]),
  ))
  return {
    configuration: configuration.items
      .map(item => ({
        id: item.id,
        typeKey: item.typeKey,
        aggregateVersion: item.aggregateVersion,
        currentVersion: item.currentVersion ?? null,
        status: item.status ?? null,
        sortOrder: item.sortOrder ?? null,
      }))
      .sort((left, right) => left.typeKey.localeCompare(right.typeKey)),
    onboarding: {
      schemaVersion: onboarding.schemaVersion,
      flowVersion: onboarding.flowVersion,
      version: onboarding.version,
      startingPoint: onboarding.startingPoint,
      acknowledgedSteps: onboarding.acknowledgedSteps,
      dismissed: onboarding.dismissed,
      telemetryOptOut: onboarding.telemetryOptOut,
      selectionEffect: onboarding.selectionEffect,
      installationRequested: onboarding.installationRequested,
      publicationRequested: onboarding.publicationRequested,
    },
    preference,
    installations,
  }
}

async function getScenarioInstallation(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  scenarioKey: ScenarioKey,
) {
  const response = await request.get(
    `${apiBaseUrl}/project-spaces/${spaceId}`
    + `/scenario-templates/${scenarioKey}/installation`,
    { headers: bearer(session) },
  )
  const body = await response.text()
  expect(
    response.ok(),
    `GET scenario installation failed: ${body}`,
  ).toBeTruthy()
  return body ? JSON.parse(body) as unknown : null
}

async function openOnboarding(page: Page) {
  const onboarding = page.getByTestId('project-space-onboarding')
  const openedAutomatically = await onboarding
    .waitFor({ state: 'visible', timeout: 5_000 })
    .then(() => true)
    .catch(() => false)
  if (openedAutomatically) return
  await page.getByTestId('project-space-onboarding-open').click({
    timeout: 5_000,
  })
  await expect(onboarding).toBeVisible()
}

async function openRoleSurface(
  browser: Browser,
  session: E2eSession,
  spaceId: string,
  storageSeed: Record<string, string> = {},
) {
  const context = await browser.newContext({ baseURL: webBaseUrl })
  const page = await context.newPage()
  await installSession(page, session)
  if (Object.keys(storageSeed).length > 0) {
    await page.evaluate(seed => {
      for (const [key, value] of Object.entries(seed)) {
        localStorage.setItem(key, value)
      }
    }, storageSeed)
  }
  const requests: BrowserRequest[] = []
  page.on('request', current => requests.push({
    method: current.method(),
    url: current.url(),
    postData: current.postData(),
  }))
  await page.goto(`/project-spaces/${spaceId}`)
  await expect(page.getByTestId('project-space-primary-navigation')).toBeVisible()
  return { context, page, requests }
}

async function openHiddenSurface(
  browser: Browser,
  session: E2eSession,
  spaceId: string,
) {
  const context = await browser.newContext({ baseURL: webBaseUrl })
  const page = await context.newPage()
  await installSession(page, session)
  await page.goto(`/project-spaces/${spaceId}`)
  return { context, page }
}

async function expectPrimaryNavigation(
  page: Page,
  expectedLabels: readonly string[],
) {
  const navigation = page.getByTestId('project-space-primary-navigation')
  await expect(navigation).toBeVisible()
  for (const label of navigationLabels) {
    await expect(navigation.getByText(label, { exact: true })).toHaveCount(
      expectedLabels.includes(label) ? 1 : 0,
    )
  }
}

function scopedCacheKey(
  workspaceId: string,
  userId: string,
  spaceId: string | undefined,
  kind: string,
) {
  const space = spaceId ? `.${encodeURIComponent(spaceId)}` : ''
  return `colla.project-space-ui.v2.${encodeURIComponent(workspaceId)}`
    + `.${encodeURIComponent(userId)}${space}.${kind}`
}

function protectedDraftFixture(spaceId: string, suffix: string) {
  return {
    [`colla.metric-dashboard-draft.${spaceId}`]: JSON.stringify({
      name: `dashboard-${suffix}`,
    }),
    [`colla.metric-draft.${spaceId}`]: JSON.stringify({
      name: `semantic-${suffix}`,
    }),
    [`colla.metric-risk-policy.${spaceId}`]: JSON.stringify({
      name: `risk-${suffix}`,
    }),
  }
}

function isHiddenOptionalRequest(url: string) {
  return [
    '/members',
    '/settings',
    '/scenario-templates',
    '/automation/',
    '/cross-space/',
    '/metrics/',
  ].some(fragment => url.includes(fragment))
}

async function archiveIfNeeded(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
) {
  const current = await request.get(`${apiBaseUrl}/project-spaces/${spaceId}`, {
    headers: bearer(owner),
  })
  if (current.status() === 404) return
  if (!current.ok()) {
    throw new Error(`cleanup GET space ${spaceId} failed: HTTP ${current.status()}`)
  }
  const space = await current.json() as SpaceView
  if (space.status === 'disabled') {
    const restore = await request.post(
      `${apiBaseUrl}/project-spaces/${spaceId}/settings/restore`,
      { headers: bearer(owner) },
    )
    if (!restore.ok()) {
      throw new Error(`cleanup restore space ${spaceId} failed: HTTP ${restore.status()}`)
    }
  }
  if (space.status !== 'archived') {
    const archive = await request.post(
      `${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`,
      { headers: bearer(owner) },
    )
    if (!archive.ok()) {
      throw new Error(`cleanup archive space ${spaceId} failed: HTTP ${archive.status()}`)
    }
  }
}

async function offboardIdentity(
  request: APIRequestContext,
  enterprise: E2eSession,
  handoverToUserId: string,
  identity: Identity,
  suffix: string,
) {
  const response = await request.post(`${apiBaseUrl}/admin/users/${identity.id}/offboard`, {
    headers: {
      ...bearer(enterprise),
      'X-Colla-Request-Id': `${suffix}-offboard-${identity.id}`,
    },
    data: { handoverToUserId },
  })
  if (!response.ok()) {
    throw new Error(`cleanup offboard identity ${identity.id} failed: HTTP ${response.status()}`)
  }
}
