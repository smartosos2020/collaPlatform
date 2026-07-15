import { expect, test, type Page } from '@playwright/test'

import { apiBaseUrl, bearer, loginByApi } from './support/api'
import { LoginPage, UserWorkspacePage } from './support/pageObjects'

const adminUsername = process.env.COLLA_E2E_ADMIN_USERNAME ?? 'admin'
const adminPassword = process.env.COLLA_E2E_ADMIN_PASSWORD
const memberUsername = process.env.COLLA_E2E_MEMBER_USERNAME ?? 'pilot-member-01'
const memberPassword = process.env.COLLA_E2E_MEMBER_PASSWORD
const syntheticUsernames = (process.env.COLLA_E2E_SYNTHETIC_USERNAMES ?? 'pilot-owner,pilot-admin,pilot-member-01,pilot-member-02,pilot-member-03')
  .split(',')
  .map((username) => username.trim())
  .filter(Boolean)

async function loginThroughUi(page: Page, username: string, password: string) {
  const loginPage = new LoginPage(page)
  await loginPage.open()
  await loginPage.signIn(username, password)
  await expect(page).not.toHaveURL(/\/login(?:\?|$)/)
}

test.describe.serial('@pilot-m9 initialized pilot baseline is visible through the real UI', () => {
  test('administrator can review initialized identity resources', async ({ page, request }) => {
    expect(adminPassword, 'COLLA_E2E_ADMIN_PASSWORD is required').toBeTruthy()
    const session = await loginByApi(request, adminUsername, adminPassword!)

    const departmentsResponse = await request.get(`${apiBaseUrl}/admin/departments/tree`, { headers: bearer(session) })
    const usersResponse = await request.get(`${apiBaseUrl}/admin/users`, { headers: bearer(session) })
    const groupsResponse = await request.get(`${apiBaseUrl}/admin/user-groups`, { headers: bearer(session) })
    expect(departmentsResponse.ok()).toBeTruthy()
    expect(usersResponse.ok()).toBeTruthy()
    expect(groupsResponse.ok()).toBeTruthy()
    expect(JSON.stringify(await departmentsResponse.json())).toContain('pilot-root')
    expect(JSON.stringify(await usersResponse.json())).toContain('pilot-member-01')
    expect(JSON.stringify(await groupsResponse.json())).toContain('pilot-v2-participants')

    await loginThroughUi(page, adminUsername, adminPassword!)
    await page.goto('/admin/departments')
    await expect(page.getByRole('heading', { name: '组织架构' })).toBeVisible()
    await expect(page.getByText('PILOT-V2 Team', { exact: true }).first()).toBeVisible()

    await page.goto('/admin/users')
    await expect(page.getByRole('heading', { name: '成员管理' })).toBeVisible()
    await expect(page.getByText('pilot-member-01', { exact: true }).first()).toBeVisible()

    await page.goto('/admin/user-groups')
    await expect(page.getByRole('heading', { name: '用户组' })).toBeVisible()
    await expect(page.getByText('PILOT-V2 Participants', { exact: true }).first()).toBeVisible()
  })

  test('pilot member can open initialized collaboration resources', async ({ page, request }) => {
    expect(memberPassword, 'COLLA_E2E_MEMBER_PASSWORD is required').toBeTruthy()
    const session = await loginByApi(request, memberUsername, memberPassword!)

    const projectsResponse = await request.get(`${apiBaseUrl}/projects`, { headers: bearer(session) })
    const knowledgeResponse = await request.get(`${apiBaseUrl}/knowledge-bases`, { headers: bearer(session) })
    const basesResponse = await request.get(`${apiBaseUrl}/bases`, { headers: bearer(session) })
    const approvalsResponse = await request.get(`${apiBaseUrl}/approvals/forms`, { headers: bearer(session) })
    expect(projectsResponse.ok()).toBeTruthy()
    expect(knowledgeResponse.ok()).toBeTruthy()
    expect(basesResponse.ok()).toBeTruthy()
    expect(approvalsResponse.ok()).toBeTruthy()
    expect(JSON.stringify(await projectsResponse.json())).toContain('PILOT-V2 Collaboration Trial')
    expect(JSON.stringify(await knowledgeResponse.json())).toContain('PILOT-V2 Handbook')
    expect(JSON.stringify(await basesResponse.json())).toContain('PILOT-V2 Feedback Register')
    expect(JSON.stringify(await approvalsResponse.json())).toContain('leave')

    await loginThroughUi(page, memberUsername, memberPassword!)
    await page.goto('/projects')
    await expect(page.getByText('PILOT-V2 Collaboration Trial', { exact: true }).first()).toBeVisible()

    await page.goto('/knowledge-bases')
    await expect(page.getByText('PILOT-V2 Handbook', { exact: true }).first()).toBeVisible()

    await page.goto('/bases')
    await expect(page.getByText('PILOT-V2 Feedback Register', { exact: true }).first()).toBeVisible()

    await page.goto('/approvals')
    await expect(page.getByRole('heading', { name: '审批' })).toBeVisible()
    await expect(page.getByText('请假申请', { exact: true }).first()).toBeVisible()
  })

  test('all five synthetic personas authenticate through the real login page', async ({ browser, request }) => {
    expect(memberPassword, 'COLLA_E2E_MEMBER_PASSWORD is required').toBeTruthy()
    expect(syntheticUsernames).toHaveLength(5)

    for (const username of syntheticUsernames) {
      await loginByApi(request, username, memberPassword!)
      const context = await browser.newContext()
      const page = await context.newPage()
      await loginThroughUi(page, username, memberPassword!)
      await new UserWorkspacePage(page).expectVisible()
      await context.close()
    }
  })
})
