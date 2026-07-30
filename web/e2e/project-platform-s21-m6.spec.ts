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
import { addMember, createIdentity, getJson } from './support/crossSpace'
import { requireIsolatedIdentityFixture } from './support/fixtures'

type ScenarioKey = 'development' | 'marketing' | 'human-resources' | 'delivery'
type PathKey = ScenarioKey | 'blank'

type IdentityFixture = {
  id: string
  username: string
  displayName: string
  password: string
}

type SpaceFixture = {
  id: string
  key: PathKey
  label: string
  name: string
}

type ChecklistItem = {
  stepKey: string
  labelKey: string
  helpKey: string
  path: string | null
  dependencies: string[]
  ownerContract: string
  status: 'available' | 'verify_on_owner_api' | 'blocked'
}

type OnboardingView = {
  schemaVersion: number
  flowVersion: string
  currentFlowVersion: string
  version: number
  updatedAt: string | null
  migrationRequired: boolean
  startingPoint: {
    kind: 'unselected' | 'blank' | 'scenario'
    scenarioKey: ScenarioKey | null
  }
  acknowledgedSteps: Array<{
    stepKey: string
    acknowledgement: 'seen' | 'skipped'
  }>
  dismissed: boolean
  telemetryOptOut: boolean
  selectionEffect: 'experience_only'
  installationRequested: false
  publicationRequested: false
  track: 'manager' | 'member' | 'guest'
  readOnly: boolean
  checklist: ChecklistItem[]
}

type OnboardingCommand =
  | { action: 'select_starting_point'; startingPoint: 'blank' }
  | {
      action: 'select_starting_point'
      startingPoint: 'scenario'
      scenarioKey: ScenarioKey
    }
  | {
      action: 'acknowledge_step'
      stepKey: string
      acknowledgement: 'seen' | 'skipped'
    }
  | { action: 'dismiss' }
  | { action: 'resume' }
  | { action: 'set_telemetry_opt_out'; telemetryOptOut: boolean }
  | { action: 'reset' }

type TypeConfiguration = {
  items: Array<{
    id: string
    typeKey: string
    aggregateVersion: number
    currentVersion: {
      id: string
      number: number
      status: string
      configHash: string
    }
  }>
}

type SpaceView = {
  id: string
  status: 'active' | 'disabled' | 'archived'
}

const FLOW_VERSION = 's21-m6-v1'
const pathDefinitions = [
  { key: 'development', label: '研发模板' },
  { key: 'marketing', label: '市场模板' },
  { key: 'human-resources', label: 'HR模板' },
  { key: 'delivery', label: '交付模板' },
  { key: 'blank', label: '基础空间' },
] as const satisfies ReadonlyArray<{ key: PathKey; label: string }>

test.describe('PROJECT-PLATFORM-S21-M6 progressive project-space onboarding', () => {
  test('@smoke validates real isolated paths, state, permissions and recovery', async ({
    browser,
    page,
    request,
  }, testInfo) => {
    test.setTimeout(480_000)
    requireIsolatedIdentityFixture()

    const enterprise = await loginByApi(request)
    const enterpriseProfile = await getJson<{ id: string }>(
      request,
      `${apiBaseUrl}/auth/me`,
      enterprise,
    )
    const suffix = `s21m6_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const identities: IdentityFixture[] = []
    const spaces: SpaceFixture[] = []
    const extraContexts: BrowserContext[] = []
    let owner: E2eSession | undefined

    try {
      for (const [role, displayName] of [
        ['owner', 'S21 M6 Owner'],
        ['admin', 'S21 M6 Space Admin'],
        ['member', 'S21 M6 Member'],
        ['guest', 'S21 M6 Guest'],
        ['outsider', 'S21 M6 Outsider'],
      ] as const) {
        identities.push(await createIdentity(
          request,
          enterprise,
          `${suffix}_${role}`,
          displayName,
        ))
      }

      const sessions = await Promise.all(
        identities.map(identity => loginByApi(
          request,
          identity.username,
          identity.password,
        )),
      )
      ;[owner] = sessions
      const [, admin, member, guest, outsider] = sessions

      for (const definition of pathDefinitions) {
        const longName = definition.key === 'blank'
          ? `S21 M6 ${'跨团队基础空间长名称'.repeat(7)}`.slice(0, 120)
          : `S21 M6 ${definition.label} ${suffix}`
        const id = await createSpace(
          request,
          owner,
          `s21-m6-${definition.key}-${suffix.replaceAll('_', '-')}`,
          longName,
        )
        spaces.push({ ...definition, id, name: longName })
      }

      const baseSpace = requireSpace(spaces, 'blank')
      await addMember(request, owner, baseSpace.id, identities[1].id, 'admin')
      await addMember(request, owner, baseSpace.id, identities[2].id, 'member')
      await addMember(request, owner, baseSpace.id, identities[3].id, 'guest')

      for (const space of spaces) {
        const defaultView = await getOnboarding(request, owner, space.id)
        expectDefaultView(defaultView)

        const beforeConfiguration = await getJson<TypeConfiguration>(
          request,
          `${apiBaseUrl}/project-spaces/${space.id}/configuration/types`,
          owner,
        )
        const beforeFacts = publishedTypeFacts(beforeConfiguration)
        const beforeInstallation = space.key === 'blank'
          ? null
          : await getInstallation(request, owner, space.id, space.key)

        const selected = await commandOnboarding(
          request,
          owner,
          space.id,
          defaultView.version,
          selectionCommand(space.key),
        )
        expect(selected).toEqual(expect.objectContaining({
          schemaVersion: 1,
          flowVersion: FLOW_VERSION,
          currentFlowVersion: FLOW_VERSION,
          version: defaultView.version + 1,
          selectionEffect: 'experience_only',
          installationRequested: false,
          publicationRequested: false,
          track: 'manager',
          readOnly: false,
        }))
        expectStartingPoint(selected, space.key)

        const afterConfiguration = await getJson<TypeConfiguration>(
          request,
          `${apiBaseUrl}/project-spaces/${space.id}/configuration/types`,
          owner,
        )
        expect(publishedTypeFacts(afterConfiguration)).toEqual(beforeFacts)
        if (space.key !== 'blank') {
          expect(await getInstallation(
            request,
            owner,
            space.id,
            space.key,
          )).toEqual(beforeInstallation)
        }
      }

      for (const space of spaces) {
        expectStartingPoint(
          await getOnboarding(request, owner, space.id),
          space.key,
        )
      }

      const [adminDefault, memberDefault, guestDefault] = await Promise.all([
        getOnboarding(request, admin, baseSpace.id),
        getOnboarding(request, member, baseSpace.id),
        getOnboarding(request, guest, baseSpace.id),
      ])
      expectDefaultView(adminDefault, 'manager')
      expectDefaultView(memberDefault, 'member')
      expectDefaultView(guestDefault, 'guest')
      expect(memberDefault.checklist.map(item => item.stepKey)).toEqual([
        'find_work',
        'create_or_update_work',
        'comment_on_work',
        'attach_file',
        'transition_state',
        'review_notifications',
      ])
      expect(guestDefault.checklist.map(item => item.stepKey)).toEqual(['find_work'])

      for (const session of [outsider, enterprise]) {
        const hidden = await request.get(onboardingUrl(baseSpace.id), {
          headers: bearer(session),
        })
        expect(hidden.status()).toBe(404)
        const hiddenBody = await hidden.text()
        expect(hiddenBody).not.toContain(baseSpace.name)
        expect(hiddenBody).not.toContain('startingPoint')
        expect(hiddenBody).not.toContain('checklist')
      }

      for (const session of [member, guest, outsider, enterprise]) {
        const deniedInstall = await request.post(
          `${apiBaseUrl}/project-spaces/${baseSpace.id}`
          + '/scenario-templates/development/install',
          {
            headers: bearer(session),
            data: { requestId: `${suffix}-denied-${randomUUID()}` },
          },
        )
        expect([403, 404]).toContain(deniedInstall.status())
        expect(await deniedInstall.text()).not.toContain(baseSpace.name)
      }

      await installSession(page, owner)
      for (const space of spaces) {
        await page.goto(`/project-spaces/${space.id}`)
        await expect(page.getByRole('heading', { name: space.name })).toBeVisible()
        await openOnboarding(page)
        const panel = page.getByTestId('project-space-onboarding')
        await expect(page.getByText('管理者引导', { exact: true })).toBeVisible()
        await expect(panel.getByTestId('project-space-onboarding-paths')).toBeVisible()
        await expect(panel.getByRole('radio', { name: space.label })).toBeChecked()
        await expect(panel.getByTestId(
          space.key === 'blank'
            ? 'onboarding-action-configure_work_model'
            : 'onboarding-action-preview_impact',
        )).toBeVisible()
      }

      for (const [session, expectedTrack, expectedChecklist, hasChooser] of [
        [admin, '管理者引导', '空间设置清单', true],
        [member, '成员引导', '开始协作清单', false],
        [guest, '访客只读引导', '访客查看清单', false],
      ] as const) {
        const surface = await openRoleSurface(browser, session, baseSpace.id)
        extraContexts.push(surface.context)
        await openOnboarding(surface.page)
        const panel = surface.page.getByTestId('project-space-onboarding')
        await expect(surface.page.getByText(expectedTrack, { exact: true })).toBeVisible()
        await expect(panel).toContainText(expectedChecklist)
        await expect(panel.getByTestId('project-space-onboarding-paths')).toHaveCount(
          hasChooser ? 1 : 0,
        )
      }

      let baseView = await getOnboarding(request, owner, baseSpace.id)
      const acknowledged = await commandOnboarding(
        request,
        owner,
        baseSpace.id,
        baseView.version,
        {
          action: 'acknowledge_step',
          stepKey: 'choose_starting_point',
          acknowledgement: 'seen',
        },
      )
      expect(acknowledged.acknowledgedSteps).toEqual([
        { stepKey: 'choose_starting_point', acknowledgement: 'seen' },
      ])

      const dismissed = await commandOnboarding(
        request,
        owner,
        baseSpace.id,
        acknowledged.version,
        { action: 'dismiss' },
      )
      expect(dismissed.dismissed).toBe(true)
      await page.goto(`/project-spaces/${baseSpace.id}`)
      await expect(page.getByTestId('project-space-onboarding-open')).toContainText('继续引导')
      await page.getByTestId('project-space-onboarding-open').click()
      await expect(page.getByTestId('project-space-onboarding')).toBeVisible()
      await expect.poll(
        async () => (await getOnboarding(request, owner!, baseSpace.id)).dismissed,
      ).toBe(false)

      baseView = await getOnboarding(request, owner, baseSpace.id)
      const staleVersion = baseView.version
      const [firstCas, secondCas] = await Promise.all([
        rawOnboardingCommand(
          request,
          owner,
          baseSpace.id,
          staleVersion,
          { action: 'dismiss' },
        ),
        rawOnboardingCommand(
          request,
          owner,
          baseSpace.id,
          staleVersion,
          { action: 'dismiss' },
        ),
      ])
      expect([firstCas.status, secondCas.status].sort((left, right) => left - right))
        .toEqual([200, 409])
      for (const result of [firstCas, secondCas]) {
        if (result.status === 409) {
          expect(result.text).not.toContain(baseSpace.name)
        }
      }

      baseView = await getOnboarding(request, owner, baseSpace.id)
      expect(baseView.dismissed).toBe(true)
      baseView = await commandOnboarding(
        request,
        owner,
        baseSpace.id,
        baseView.version,
        { action: 'resume' },
      )
      expect(baseView.dismissed).toBe(false)

      const telemetry = await request.post(
        `${onboardingUrl(baseSpace.id)}/telemetry`,
        {
          headers: bearer(owner),
          data: {
            events: [{
              eventId: randomUUID(),
              flowVersion: FLOW_VERSION,
              stepKey: 'choose_starting_point',
              outcome: 'shown',
              durationBucket: 'under_5s',
              errorCode: 'none',
            }],
          },
        },
      )
      expect(telemetry.status()).toBe(204)

      const optOutRequestId = randomUUID()
      const beforeOptOutVersion = baseView.version
      baseView = await commandOnboarding(
        request,
        owner,
        baseSpace.id,
        beforeOptOutVersion,
        { action: 'set_telemetry_opt_out', telemetryOptOut: true },
        optOutRequestId,
      )
      expect(baseView.telemetryOptOut).toBe(true)
      const replayedOptOut = await commandOnboarding(
        request,
        owner,
        baseSpace.id,
        beforeOptOutVersion,
        { action: 'set_telemetry_opt_out', telemetryOptOut: true },
        optOutRequestId,
      )
      expect(replayedOptOut.version).toBe(baseView.version)

      baseView = await commandOnboarding(
        request,
        owner,
        baseSpace.id,
        baseView.version,
        { action: 'reset' },
      )
      expect(baseView.startingPoint).toEqual({ kind: 'unselected', scenarioKey: null })
      expect(baseView.acknowledgedSteps).toEqual([])
      expect(baseView.dismissed).toBe(false)
      expect(baseView.telemetryOptOut).toBe(true)

      const mutatingBrowserRequests: string[] = []
      page.on('request', current => {
        if (current.method() !== 'GET') mutatingBrowserRequests.push(current.url())
      })
      await page.goto(`/project-spaces/${baseSpace.id}`)
      await expect(page.getByTestId('project-space-onboarding')).toBeVisible()
      await page.getByRole('radio', { name: '基础空间' }).check()
      await page.getByRole('button', { name: '保存起步方式' }).click()
      await expect.poll(
        async () => (await getOnboarding(request, owner!, baseSpace.id)).startingPoint.kind,
      ).toBe('blank')
      expect(mutatingBrowserRequests.filter(url => url.endsWith('/onboarding/commands')))
        .toHaveLength(1)
      expect(mutatingBrowserRequests.some(isBusinessMutation)).toBe(false)

      baseView = await getOnboarding(request, owner, baseSpace.id)
      const mutationsBeforeOffline = mutatingBrowserRequests.length
      await page.context().setOffline(true)
      await page.evaluate(() => window.dispatchEvent(new Event('offline')))
      await expect(page.getByTestId('project-space-onboarding')).toContainText('当前离线')
      await expect(page.getByRole('radio', { name: '研发模板' })).toBeDisabled()
      await expect(page.getByRole('button', { name: '保存起步方式' })).toBeDisabled()
      await page.waitForTimeout(250)
      expect(mutatingBrowserRequests).toHaveLength(mutationsBeforeOffline)
      expect(await getOnboarding(request, owner, baseSpace.id)).toEqual(baseView)

      await page.context().setOffline(false)
      await page.evaluate(() => {
        window.dispatchEvent(new Event('online'))
        window.dispatchEvent(new Event('focus'))
      })
      await expect(page.getByText('当前离线')).toHaveCount(0)

      for (const width of [1440, 1366, 820]) {
        await page.setViewportSize({ width, height: 980 })
        expect(await page.evaluate(
          () => document.documentElement.scrollWidth
            - document.documentElement.clientWidth,
        )).toBeLessThanOrEqual(1)
      }

      await page.keyboard.press('Escape')
      await expect(page.getByTestId('project-space-onboarding')).not.toBeVisible()
      await page.getByTestId('project-space-onboarding-open').focus()
      await page.keyboard.press('Enter')
      await expect(page.getByTestId('project-space-onboarding')).toBeVisible()
      await page.screenshot({
        path: testInfo.outputPath('s21-m6-onboarding-820.png'),
        fullPage: true,
      })

      for (const space of spaces.filter(candidate => candidate.key !== 'blank')) {
        expectStartingPoint(
          await getOnboarding(request, owner, space.id),
          space.key,
        )
      }
    } finally {
      await page.context().setOffline(false).catch(() => undefined)
      await Promise.all(
        extraContexts.map(context => context.close().catch(() => undefined)),
      )
      if (owner) {
        for (const space of spaces) {
          await archiveIfNeeded(request, owner, space.id)
        }
      }
      for (const identity of identities.reverse()) {
        await request.post(`${apiBaseUrl}/admin/users/${identity.id}/offboard`, {
          headers: {
            ...bearer(enterprise),
            'X-Colla-Request-Id': `${suffix}-offboard-${identity.id}`,
          },
          data: { handoverToUserId: enterpriseProfile.id },
        }).catch(() => undefined)
      }
    }
  })
})

function requireSpace(spaces: SpaceFixture[], key: PathKey) {
  const space = spaces.find(candidate => candidate.key === key)
  if (!space) throw new Error(`S21 M6 ${key} fixture is unavailable`)
  return space
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
      description: 'S21 M6 real isolated progressive-onboarding acceptance fixture',
      visibility: 'private',
    },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  return (await response.json() as { id: string }).id
}

function onboardingUrl(spaceId: string) {
  return `${apiBaseUrl}/project-spaces/${spaceId}/onboarding`
}

function getOnboarding(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
) {
  return getJson<OnboardingView>(request, onboardingUrl(spaceId), session)
}

function selectionCommand(key: PathKey): OnboardingCommand {
  return key === 'blank'
    ? { action: 'select_starting_point', startingPoint: 'blank' }
    : {
        action: 'select_starting_point',
        startingPoint: 'scenario',
        scenarioKey: key,
      }
}

async function rawOnboardingCommand(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  expectedVersion: number,
  command: OnboardingCommand,
  requestId = randomUUID(),
) {
  const response = await request.post(`${onboardingUrl(spaceId)}/commands`, {
    headers: {
      ...bearer(session),
      'X-Colla-Request-Id': requestId,
    },
    data: {
      requestId,
      schemaVersion: 1,
      flowVersion: FLOW_VERSION,
      expectedVersion,
      ...command,
    },
  })
  const text = await response.text()
  return {
    status: response.status(),
    text,
    view: response.ok() ? JSON.parse(text) as OnboardingView : undefined,
  }
}

async function commandOnboarding(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  expectedVersion: number,
  command: OnboardingCommand,
  requestId = randomUUID(),
) {
  const result = await rawOnboardingCommand(
    request,
    session,
    spaceId,
    expectedVersion,
    command,
    requestId,
  )
  expect(
    result.status,
    `POST onboarding command failed: ${result.text}`,
  ).toBe(200)
  if (!result.view) throw new Error('Onboarding command returned no view')
  return result.view
}

function expectDefaultView(
  view: OnboardingView,
  track: OnboardingView['track'] = 'manager',
) {
  expect(view).toEqual(expect.objectContaining({
    schemaVersion: 1,
    flowVersion: FLOW_VERSION,
    currentFlowVersion: FLOW_VERSION,
    version: 0,
    updatedAt: null,
    migrationRequired: false,
    startingPoint: { kind: 'unselected', scenarioKey: null },
    acknowledgedSteps: [],
    dismissed: false,
    telemetryOptOut: false,
    selectionEffect: 'experience_only',
    installationRequested: false,
    publicationRequested: false,
    track,
    readOnly: false,
  }))
  expect(view).not.toHaveProperty('role')
  expect(view).not.toHaveProperty('capabilities')
  expect(view).not.toHaveProperty('spaceName')
}

function expectStartingPoint(view: OnboardingView, key: PathKey) {
  expect(view.startingPoint).toEqual(key === 'blank'
    ? { kind: 'blank', scenarioKey: null }
    : { kind: 'scenario', scenarioKey: key })
}

function publishedTypeFacts(configuration: TypeConfiguration) {
  return configuration.items
    .map(item => ({
      id: item.id,
      typeKey: item.typeKey,
      aggregateVersion: item.aggregateVersion,
      currentVersion: item.currentVersion,
    }))
    .sort((left, right) => left.typeKey.localeCompare(right.typeKey))
}

async function getInstallation(
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
  const panel = page.getByTestId('project-space-onboarding')
  if (await panel.isVisible().catch(() => false)) return
  await page.getByTestId('project-space-onboarding-open').click()
  await expect(panel).toBeVisible()
}

async function openRoleSurface(
  browser: Browser,
  session: E2eSession,
  spaceId: string,
) {
  const context = await browser.newContext({ baseURL: webBaseUrl })
  const page = await context.newPage()
  await installSession(page, session)
  await page.goto(`/project-spaces/${spaceId}`)
  await expect(page.getByTestId('project-space-onboarding-open')).toBeVisible()
  return { context, page }
}

function isBusinessMutation(url: string) {
  return [
    '/scenario-templates/',
    '/configuration/',
    '/members',
    '/work-items',
    ':publish',
    '/automation/',
    '/metrics',
  ].some(fragment => url.includes(fragment))
}

async function archiveIfNeeded(
  request: APIRequestContext,
  owner: E2eSession,
  spaceId: string,
) {
  const current = await request.get(`${apiBaseUrl}/project-spaces/${spaceId}`, {
    headers: bearer(owner),
  }).catch(() => undefined)
  if (!current?.ok()) return
  const space = await current.json() as SpaceView
  if (space.status === 'disabled') {
    await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/restore`, {
      headers: bearer(owner),
    }).catch(() => undefined)
  }
  if (space.status !== 'archived') {
    await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/settings/archive`, {
      headers: bearer(owner),
    }).catch(() => undefined)
  }
}
