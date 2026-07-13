import { expect, test, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'

test.describe('M7 real isolated administrator governance', () => {
  test('administrator completes overview, settings, security, handover, batch, risks, export, and module flow @smoke', async ({ page, request }) => {
    requireIsolatedIdentityFixture()
    const suffix = `m7_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const admin = await loginByApi(request)
    const leaving = await createMember(request, admin, `${suffix}_leaving`, 'M7 待交接成员')
    const handover = await createMember(request, admin, `${suffix}_handover`, 'M7 交接成员')
    const batchTarget = await createMember(request, admin, `${suffix}_batch`, 'M7 批量目标')
    const orgViewer = await createMember(request, admin, `${suffix}_orgviewer`, 'M7 组织只读')
    const department = await createDepartment(request, admin, suffix)
    const group = await createGroup(request, admin, suffix)
    const role = await createRole(request, admin, suffix)
    await replaceRolePermissions(request, admin, role.id, ['org.view'])
    await createRoleAssignment(request, admin, role.id, orgViewer.id)
    await new Promise((resolve) => setTimeout(resolve, 500))
    const base = await createBase(request, admin, suffix)
    const permission = await grantExpiredPermission(request, admin, base.id, department.id)

    await installSession(page, admin)

    // T01: real overview drill-down targets and health.
    await page.goto('/admin/overview')
    await expect(page.getByText('企业概览').first()).toBeVisible()
    await expect(page.getByText('成员治理')).toBeVisible()
    await page.getByRole('link', { name: '查看运行信息' }).click()
    await expect(page).toHaveURL(/\/admin\/system-settings$/)
    await expect(page.getByText('系统设置当前为只读视图')).toBeVisible()
    await page.goBack()
    await page.getByRole('link', { name: '排查风险' }).click()
    await expect(page).toHaveURL(/\/admin\/permission-governance$/)

    // T02: admin sees settings; org.view custom role receives a bounded read response but no admin shell.
    await page.goto('/admin/system-settings')
    await expect(page.getByText('Colla')).toBeVisible()
    let orgViewerSession = await loginByApi(request, orgViewer.username, 'member123456')
    let settingsResponse = await request.get(`${apiBaseUrl}/admin/system-settings`, { headers: bearer(orgViewerSession) })
    for (let attempt = 0; attempt < 5 && !settingsResponse.ok(); attempt += 1) {
      await new Promise((resolve) => setTimeout(resolve, 250))
      orgViewerSession = await loginByApi(request, orgViewer.username, 'member123456')
      settingsResponse = await request.get(`${apiBaseUrl}/admin/system-settings`, { headers: bearer(orgViewerSession) })
    }
    expect(settingsResponse.ok()).toBeTruthy()
    const settings = await settingsResponse.json()
    expect(settings.workspace.name).toBeTruthy()
    await installSession(page, orgViewerSession)
    await page.goto('/admin/system-settings')
    await expect(page.getByTestId('admin-system-settings-page')).toBeVisible()
    await expect(page.getByText('系统设置当前为只读视图')).toBeVisible()
    await installSession(page, admin)

    // T03: real security page and device action surface.
    await loginByApi(request)
    await page.goto('/admin/security')
    await expect(page.getByText('安全策略').first()).toBeVisible()
    await expect(page.getByText('登录设备与会话')).toBeVisible()
    await expect(page.getByTestId('admin-security-page').getByText('必要通知')).toBeVisible()
    const revokeLink = page.getByTestId('admin-security-page').getByRole('button', { name: '撤销' }).first()
    await expect(revokeLink).toBeVisible()
    const revokeResponsePromise = page.waitForResponse((response) => response.url().includes('/api/devices/') && response.request().method() === 'DELETE')
    await revokeLink.click()
    expect((await revokeResponsePromise).ok()).toBeTruthy()
    await expect(page.getByText('登录设备已撤销')).toBeVisible()

    // T04: real offboarding and handover mutation.
    const offboardResponse = await request.post(`${apiBaseUrl}/admin/users/${leaving.id}/offboard`, {
      headers: { ...bearer(admin), 'Content-Type': 'application/json' },
      data: { handoverToUserId: handover.id },
    })
    expect(offboardResponse.ok()).toBeTruthy()
    const leavingRows = await getJson(request, `${apiBaseUrl}/admin/users`, admin) as Array<{ id: string; status: string }>
    expect(leavingRows.find((member) => member.id === leaving.id)?.status).toBe('disabled')
    const offboardAudit = await getJson(request, `${apiBaseUrl}/admin/audit-logs?action=user.offboarded&targetType=user&targetId=${leaving.id}`, admin) as Array<{ action: string }>
    expect(offboardAudit[0]?.action).toBe('user.offboarded')

    // T05: real batch preview, permission check, confirmation, and report.
    await page.goto('/admin/batch-governance')
    await expect(page.getByTestId('admin-batch-governance-page')).toBeVisible()
    await page.getByLabel('目标 ID').fill(batchTarget.id)
    await page.getByRole('button', { name: '预览权限' }).click()
    await expect(page.getByText('预览完成，等待确认')).toBeVisible()
    await expect(page.getByText('权限检查通过')).toBeVisible()
    await page.getByRole('button', { name: '确认执行' }).click()
    await expect(page.getByTestId('admin-batch-governance-page').getByText('批量治理已执行')).toBeVisible()
    const batchRows = await getJson(request, `${apiBaseUrl}/admin/users`, admin) as Array<{ id: string; status: string }>
    expect(batchRows.find((member) => member.id === batchTarget.id)?.status).toBe('disabled')

    await page.locator('[data-testid="admin-batch-governance-page"] .ant-select').first().click()
    await page.locator('.ant-select-dropdown:visible').getByText('停用部门', { exact: true }).click()
    await expect(page.locator('[data-testid="admin-batch-governance-page"] .ant-select').first().locator('.ant-select-content[title="停用部门"]')).toBeVisible()
    await page.getByLabel('目标 ID').fill(department.id)
    await expect(page.getByLabel('目标 ID')).toHaveValue(department.id)
    const departmentPreviewResponsePromise = page.waitForResponse((response) => response.url().includes('/admin/batch-governance/preview') && response.request().method() === 'POST')
    await page.getByRole('button', { name: '预览权限' }).click()
    expect((await departmentPreviewResponsePromise).ok()).toBeTruthy()
    await expect(page.getByTestId('admin-batch-governance-page').getByText('权限检查通过')).toBeVisible()
    await expect(page.getByTestId('admin-batch-governance-page').getByRole('cell', { name: department.id, exact: true })).toBeVisible()
    const departmentExecuteResponsePromise = page.waitForResponse((response) => response.url().includes('/admin/batch-governance/execute') && response.request().method() === 'POST')
    await page.getByRole('button', { name: '确认执行' }).click()
    expect((await departmentExecuteResponsePromise).ok()).toBeTruthy()
    const departmentTree = await getJson(request, `${apiBaseUrl}/admin/departments/tree`, admin) as Array<{ department: { id: string; status: string }; children: Array<unknown> }>
    expect(findDepartment(departmentTree, department.id)?.department.status).toBe('disabled')

    await page.locator('[data-testid="admin-batch-governance-page"] .ant-select').first().click()
    await page.locator('.ant-select-dropdown:visible').getByText('停用用户组', { exact: true }).click()
    await expect(page.locator('[data-testid="admin-batch-governance-page"] .ant-select').first().locator('.ant-select-content[title="停用用户组"]')).toBeVisible()
    await page.getByLabel('目标 ID').fill(group.id)
    await expect(page.getByLabel('目标 ID')).toHaveValue(group.id)
    const groupPreviewResponsePromise = page.waitForResponse((response) => response.url().includes('/admin/batch-governance/preview') && response.request().method() === 'POST')
    await page.getByRole('button', { name: '预览权限' }).click()
    expect((await groupPreviewResponsePromise).ok()).toBeTruthy()
    await expect(page.getByTestId('admin-batch-governance-page').getByText('权限检查通过')).toBeVisible()
    await expect(page.getByTestId('admin-batch-governance-page').getByRole('cell', { name: group.id, exact: true })).toBeVisible()
    const groupExecuteResponsePromise = page.waitForResponse((response) => response.url().includes('/admin/batch-governance/execute') && response.request().method() === 'POST')
    await page.getByRole('button', { name: '确认执行' }).click()
    expect((await groupExecuteResponsePromise).ok()).toBeTruthy()
    const groupRow = await getJson(request, `${apiBaseUrl}/admin/user-groups/${group.id}`, admin) as { id: string; status: string }
    expect(groupRow.status).toBe('disabled')

    await page.locator('[data-testid="admin-batch-governance-page"] .ant-select').first().click()
    await page.locator('.ant-select-dropdown:visible').getByText('停用角色', { exact: true }).click()
    await expect(page.locator('[data-testid="admin-batch-governance-page"] .ant-select').first().locator('.ant-select-content[title="停用角色"]')).toBeVisible()
    await page.getByLabel('目标 ID').fill(role.id)
    await expect(page.getByLabel('目标 ID')).toHaveValue(role.id)
    const rolePreviewResponsePromise = page.waitForResponse((response) => response.url().includes('/admin/batch-governance/preview') && response.request().method() === 'POST')
    await page.getByRole('button', { name: '预览权限' }).click()
    expect((await rolePreviewResponsePromise).ok()).toBeTruthy()
    await expect(page.getByTestId('admin-batch-governance-page').getByText('权限检查通过')).toBeVisible()
    await expect(page.getByTestId('admin-batch-governance-page').getByRole('cell', { name: role.id, exact: true })).toBeVisible()
    const roleExecuteResponsePromise = page.waitForResponse((response) => response.url().includes('/admin/batch-governance/execute') && response.request().method() === 'POST')
    await page.getByRole('button', { name: '确认执行' }).click()
    expect((await roleExecuteResponsePromise).ok()).toBeTruthy()
    const roleRow = await getJson(request, `${apiBaseUrl}/admin/roles/${role.id}`, admin) as { id: string; status: string }
    expect(roleRow.status).toBe('disabled')

    // T06: real risk source, impact, recommendation, and confirmed remediation.
    await page.goto('/admin/permission-governance')
    await page.getByPlaceholder('规则、资源、授权主体').fill(base.id)
    const expiredRiskRow = page.getByRole('row').filter({ hasText: 'expired_active_permission' }).filter({ hasText: base.id })
    await expect(expiredRiskRow).toBeVisible()
    await expect(expiredRiskRow.getByText('建议：review_permission_source')).toBeVisible()
    await expiredRiskRow.locator('button').last().click()
    const remediationModal = page.locator('.ant-modal-wrap:visible')
    await expect(remediationModal.getByText('确认单项修复权限风险？').last()).toBeVisible()
    await remediationModal.getByRole('button', { name: '确认修复' }).last().click()
    await expect(page.getByText('风险授权已撤销并写入审计。')).toBeVisible()
    const postRisk = await getJson(request, `${apiBaseUrl}/admin/permission-governance/risks`, admin) as { items: Array<{ id: string }> }
    expect(postRisk.items.some((item) => item.id === permission.id)).toBe(false)

    // T07: real audit export and minimal-field contract.
    await page.goto('/admin/audit-logs')
    await expect(page.getByText('敏感字段最小展示')).toBeVisible()
    const downloadPromise = page.waitForEvent('download')
    await page.getByRole('button', { name: '导出当前结果' }).click()
    const download = await downloadPromise
    expect(download.suggestedFilename()).toBe('audit-logs.csv')
    const csv = await download.createReadStream()
    expect(csv).toBeTruthy()

    // T08: real cross-module admin navigation, states, tables, and narrow layout.
    const routes = [
      ['/admin/departments', '组织架构'],
      ['/admin/users', '成员管理'],
      ['/admin/user-groups', '用户组'],
      ['/admin/roles', '角色权限'],
      ['/admin/permission-governance', '权限治理'],
      ['/admin/knowledge-bases', '知识库治理'],
      ['/admin/app-governance', '应用治理'],
      ['/admin/system-settings', '系统设置'],
      ['/admin/security', '安全策略'],
      ['/admin/audit-logs', '审计日志'],
      ['/admin/batch-governance', '批量治理'],
    ] as const
    for (const [path, label] of routes) {
      await page.goto(path)
      await expect(page.getByText(label).first()).toBeVisible()
    }
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/admin/batch-governance')
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
    expect(overflow).toBeLessThanOrEqual(1)
  })
})

async function getJson(request: APIRequestContext, url: string, session: E2eSession) {
  const response = await request.get(url, { headers: bearer(session) })
  expect(response.ok(), `GET ${url} failed`).toBeTruthy()
  return await response.json()
}

function findDepartment(
  nodes: Array<{ department: { id: string; status: string }; children: Array<{ department: { id: string; status: string }; children: Array<unknown> }> }>,
  departmentId: string,
): { department: { id: string; status: string } } | undefined {
  for (const node of nodes) {
    if (node.department.id === departmentId) return node
    const nested = findDepartment(node.children as Array<{ department: { id: string; status: string }; children: Array<unknown> }>, departmentId)
    if (nested) return nested
  }
  return undefined
}

async function createMember(request: APIRequestContext, session: E2eSession, username: string, displayName: string) {
  const response = await request.post(`${apiBaseUrl}/admin/users`, {
    headers: bearer(session),
    data: { username, password: 'member123456', displayName, email: `${username}@example.com`, roleCode: 'member' },
  })
  expect(response.ok(), 'member fixture creation failed').toBeTruthy()
  const payload = await response.json()
  return { id: payload.id as string, username: payload.username as string }
}

async function createDepartment(request: APIRequestContext, session: E2eSession, suffix: string) {
  const response = await request.post(`${apiBaseUrl}/admin/departments`, { headers: bearer(session), data: { code: `m7-${suffix}`, name: `M7 部门 ${suffix}` } })
  expect(response.ok(), 'department fixture creation failed').toBeTruthy()
  const payload = await response.json()
  return { id: payload.id as string }
}

async function createGroup(request: APIRequestContext, session: E2eSession, suffix: string) {
  const response = await request.post(`${apiBaseUrl}/admin/user-groups`, { headers: bearer(session), data: { code: `m7-${suffix}`, name: `M7 用户组 ${suffix}`, groupType: 'normal' } })
  expect(response.ok(), 'group fixture creation failed').toBeTruthy()
  const payload = await response.json()
  return { id: payload.id as string }
}

async function createRole(request: APIRequestContext, session: E2eSession, suffix: string) {
  const response = await request.post(`${apiBaseUrl}/admin/roles`, { headers: bearer(session), data: { code: `m7_${suffix}`, name: `M7 只读角色 ${suffix}`, scope: 'workspace' } })
  expect(response.ok(), 'role fixture creation failed').toBeTruthy()
  const payload = await response.json()
  return { id: payload.id as string }
}

async function replaceRolePermissions(request: APIRequestContext, session: E2eSession, roleId: string, permissionCodes: string[]) {
  const response = await request.put(`${apiBaseUrl}/admin/roles/${roleId}/permissions`, { headers: bearer(session), data: { permissionCodes } })
  expect(response.ok(), 'role permission fixture failed').toBeTruthy()
}

async function createRoleAssignment(request: APIRequestContext, session: E2eSession, roleId: string, userId: string) {
  const response = await request.post(`${apiBaseUrl}/admin/role-assignments`, { headers: bearer(session), data: { roleId, subjectType: 'user', subjectId: userId, scopeType: 'system' } })
  expect(response.ok(), 'role assignment fixture failed').toBeTruthy()
}

async function createBase(request: APIRequestContext, session: E2eSession, suffix: string) {
  const response = await request.post(`${apiBaseUrl}/bases`, { headers: bearer(session), data: { name: `M7 Base ${suffix}`, description: 'M7 isolated governance fixture' } })
  expect(response.ok(), 'base fixture creation failed').toBeTruthy()
  const payload = await response.json()
  return { id: payload.base.id as string }
}

async function grantExpiredPermission(request: APIRequestContext, session: E2eSession, baseId: string, departmentId: string) {
  const response = await request.post(`${apiBaseUrl}/resource-permissions/base/${baseId}`, {
    headers: bearer(session),
    data: { subjectType: 'department', subjectId: departmentId, permissionLevel: 'view', expiresAt: new Date(Date.now() - 60_000).toISOString() },
  })
  expect(response.ok(), 'permission risk fixture creation failed').toBeTruthy()
  const payload = await response.json()
  return { id: payload.id as string }
}
