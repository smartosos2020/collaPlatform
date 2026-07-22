import { expect, test, type APIRequestContext, type BrowserContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

test.describe('PROJECT-PLATFORM-S02-M4 legacy project deep link', () => {
  test('migrated legacy project deep link routes members to the project space @smoke', async ({ browser, request }) => {
    test.setTimeout(120_000)
    requireIsolatedIdentityFixture()
    const suffix = `s02m4_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const admin = await loginByApi(request)
    const adminProfile = await getJson<{ id: string }>(request, `${apiBaseUrl}/auth/me`, admin)
    const memberA = await createIdentity(request, admin, `${suffix}_member_a`, 'S02 M4 Member A')
    const memberB = await createIdentity(request, admin, `${suffix}_member_b`, 'S02 M4 Member B')
    const migratedProjectName = `S02 M4 Legacy ${suffix}`
    const unmigratedProjectName = `S02 M4 Untouched ${suffix}`
    let memberAContext: BrowserContext | undefined
    let memberBContext: BrowserContext | undefined
    let batchId: string | undefined
    let batchRolledBack = false

    try {
      const migratedProject = await createLegacyProject(request, admin, `S02M4A${randomKey()}`, migratedProjectName, [memberA.id])

      const dryRunResponse = await request.post(`${apiBaseUrl}/admin/project-migrations/spaces:dry-run`, { headers: bearer(admin) })
      expect(dryRunResponse.ok(), 'migration dry-run failed').toBeTruthy()
      expect((await dryRunResponse.json() as { dryRun: boolean }).dryRun).toBe(true)

      const executeResponse = await request.post(`${apiBaseUrl}/admin/project-migrations/spaces:execute`, {
        headers: bearer(admin),
        data: { confirmation: 'EXECUTE' },
      })
      expect(executeResponse.ok(), 'migration execute failed').toBeTruthy()
      const executeBatch = await executeResponse.json() as {
        id: string
        summary: { projects: Array<{ projectId: string; outcome: string; spaceId?: string }> }
      }
      batchId = executeBatch.id
      const projectOutcome = executeBatch.summary.projects.find((item) => item.projectId === migratedProject.id)
      expect(projectOutcome, 'migrated project missing from batch summary').toBeTruthy()
      expect(['CREATED', 'REUSED']).toContain(projectOutcome?.outcome)

      const memberASession = await loginByApi(request, memberA.username, memberA.password)
      const memberBSession = await loginByApi(request, memberB.username, memberB.password)
      const resolution = await getJson<{ status: string; spaceId?: string | null }>(
        request,
        `${apiBaseUrl}/project-spaces/legacy-resolve/${migratedProject.id}`,
        memberASession,
      )
      expect(resolution.status).toBe('mapped')
      expect(resolution.spaceId).toBeTruthy()
      const spaceId = resolution.spaceId as string

      const unmigratedProject = await createLegacyProject(request, admin, `S02M4B${randomKey()}`, unmigratedProjectName, [memberA.id])

      // Scenario 1: the legacy member sees the migration banner on the old deep link and lands on the space.
      memberAContext = await browser.newContext()
      const memberAPage = await memberAContext.newPage()
      await installSession(memberAPage, memberASession)
      await memberAPage.goto(`/projects/${migratedProject.id}`)
      await expect(memberAPage.getByText('该项目已迁移到项目空间')).toBeVisible()
      await memberAPage.getByRole('button', { name: '前往项目空间' }).click()
      await expect(memberAPage).toHaveURL(new RegExp(`/project-spaces/${spaceId}$`))
      await expect(memberAPage.getByRole('heading', { name: migratedProjectName })).toBeVisible()

      // Scenario 2: a non-member opens the same deep link; the private space stays invisible.
      memberBContext = await browser.newContext()
      const memberBPage = await memberBContext.newPage()
      await installSession(memberBPage, memberBSession)
      const outsiderResolvePromise = memberBPage.waitForResponse((response) =>
        response.url().includes(`/api/project-spaces/legacy-resolve/${migratedProject.id}`) && response.request().method() === 'GET',
      )
      await memberBPage.goto(`/projects/${migratedProject.id}`)
      const outsiderResolve = await outsiderResolvePromise
      expect(outsiderResolve.ok()).toBeTruthy()
      expect((await outsiderResolve.json() as { status: string }).status).toBe('unavailable')
      await expect(memberBPage.getByText('创建或选择一个项目')).toBeVisible()
      await expect(memberBPage.getByText('该项目已迁移到项目空间')).toHaveCount(0)
      await expect(memberBPage.getByText(migratedProjectName)).toHaveCount(0)

      // Scenario 3: an unmigrated legacy project never shows the banner.
      const unmigratedResolvePromise = memberAPage.waitForResponse((response) =>
        response.url().includes(`/api/project-spaces/legacy-resolve/${unmigratedProject.id}`) && response.request().method() === 'GET',
      )
      await memberAPage.goto(`/projects/${unmigratedProject.id}`)
      const unmigratedResolve = await unmigratedResolvePromise
      expect(unmigratedResolve.ok()).toBeTruthy()
      expect((await unmigratedResolve.json() as { status: string }).status).toBe('unmigrated')
      await expect(memberAPage.getByRole('heading', { name: unmigratedProjectName })).toBeVisible()
      await expect(memberAPage.getByText('该项目已迁移到项目空间')).toHaveCount(0)

      // Rollback evidence: the batch rolls back and the deep link resolution flips to failed.
      const rollbackResponse = await request.post(`${apiBaseUrl}/admin/project-migrations/batches/${batchId}:rollback`, {
        headers: bearer(admin),
        data: { confirmation: 'ROLLBACK' },
      })
      expect(rollbackResponse.ok(), 'migration rollback failed').toBeTruthy()
      expect((await rollbackResponse.json() as { status: string }).status).toBe('rolled_back')
      batchRolledBack = true
      const afterRollback = await getJson<{ status: string }>(
        request,
        `${apiBaseUrl}/project-spaces/legacy-resolve/${migratedProject.id}`,
        memberASession,
      )
      expect(afterRollback.status).toBe('failed')
    } finally {
      await memberAContext?.close()
      await memberBContext?.close()
      if (batchId && !batchRolledBack) {
        await request.post(`${apiBaseUrl}/admin/project-migrations/batches/${batchId}:rollback`, {
          headers: bearer(admin),
          data: { confirmation: 'ROLLBACK' },
        })
      }
      await request.post(`${apiBaseUrl}/admin/users/${memberA.id}/offboard`, { headers: bearer(admin), data: { handoverToUserId: adminProfile.id } })
      await request.post(`${apiBaseUrl}/admin/users/${memberB.id}/offboard`, { headers: bearer(admin), data: { handoverToUserId: adminProfile.id } })
    }
  })
})

function randomKey() {
  return Math.random().toString(36).slice(2, 8).toUpperCase()
}

async function createIdentity(
  request: APIRequestContext,
  administrator: E2eSession,
  username: string,
  displayName: string,
) {
  const password = 'member123456'
  const response = await request.post(`${apiBaseUrl}/admin/users`, {
    headers: bearer(administrator),
    data: { username, password, displayName, email: `${username}@example.com`, roleCode: 'member' },
  })
  expect(response.ok(), `identity fixture creation failed for ${username}`).toBeTruthy()
  const payload = await response.json() as { id: string; username: string; displayName: string }
  return { ...payload, password }
}

async function createLegacyProject(
  request: APIRequestContext,
  session: E2eSession,
  projectKey: string,
  name: string,
  memberIds: string[],
) {
  const response = await request.post(`${apiBaseUrl}/projects`, {
    headers: bearer(session),
    data: { projectKey, name, description: 'S02 M4 legacy deep link fixture', memberIds },
  })
  expect(response.ok(), `legacy project fixture creation failed for ${name}`).toBeTruthy()
  return await response.json() as { id: string; projectKey: string }
}

async function getJson<T>(request: APIRequestContext, url: string, session: E2eSession) {
  const response = await request.get(url, { headers: bearer(session) })
  expect(response.ok(), `GET ${url} failed`).toBeTruthy()
  return await response.json() as T
}
