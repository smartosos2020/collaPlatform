import { expect, test } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi } from './support/api'
import { archiveKnowledgeSpaceFixture, createKnowledgeSpaceFixture, uniqueFixtureName } from './support/fixtures'
import { createProjectObjectFixture } from './support/knowledge'

test('@smoke @kb-product-m7 isolated browser selects project and atomically creates a Base entry', async ({ page, request }) => {
  test.setTimeout(90_000)
  const administrator = await loginByApi(request)
  const space = await createKnowledgeSpaceFixture(request, administrator, 'product-m7-object-entry')
  const projectName = uniqueFixtureName('m7-project')
  const project = await createProjectObjectFixture(request, administrator, projectName)
  const baseName = uniqueFixtureName('m7-base')
  const memberUsername = `m7pw${Math.random().toString(36).slice(2, 10)}`
  const memberPassword = 'member123456'
  const memberResponse = await request.post(`${apiBaseUrl}/admin/users`, {
    headers: bearer(administrator),
    data: {
      username: memberUsername,
      password: memberPassword,
      displayName: 'M7 Object Viewer',
      email: `${memberUsername}@example.com`,
      roleCode: 'member',
    },
  })
  expect(memberResponse.ok()).toBeTruthy()
  const member = await memberResponse.json() as { id: string }

  try {
    await installSession(page, administrator)
    const directoryUrl = `/knowledge-bases/${space.id}?itemId=${space.rootItemId}&view=directory`
    await page.goto(directoryUrl)
    await expect(page.getByText(space.name, { exact: true }).first()).toBeVisible()

    await page.locator('.kb-directory-toolbar').getByRole('button', { name: '挂载对象' }).click()
    const objectDialog = page.getByRole('dialog', { name: '新建内容' })
    await expect(objectDialog).toBeVisible()
    await objectDialog.getByLabel('名称').fill('M7 project entry')
    await objectDialog.getByLabel('目标类型').click()
    await page.locator('.ant-select-dropdown:visible .ant-select-item-option', { hasText: '项目' }).click()
    await expect(objectDialog.getByText('全部')).toBeVisible()
    await expect(objectDialog.getByText('最近')).toBeVisible()
    await objectDialog.getByPlaceholder('搜索名称').fill(projectName)
    await expect(objectDialog.getByText(projectName, { exact: true })).toBeVisible()
    await objectDialog.getByText(projectName, { exact: true }).click()
    await expect(objectDialog.getByText(/目标对象 ID|目标路由/)).toHaveCount(0)
    await objectDialog.locator('.ant-btn-primary').click()
    await expect(page).toHaveURL(new RegExp(`/projects/${project.id}`))

    await page.goto(directoryUrl)
    await expect(page.getByText('M7 project entry', { exact: true }).first()).toBeVisible()
    await page.locator('.kb-directory-toolbar').getByRole('button', { name: '多维表格' }).click()
    const baseDialog = page.getByRole('dialog', { name: '新建内容' })
    await baseDialog.getByLabel('名称').fill(baseName)
    await baseDialog.getByLabel('多维表格来源').click()
    await page.locator('.ant-select-dropdown:visible .ant-select-item-option', { hasText: '新建多维表格并挂载' }).click()
    await baseDialog.getByLabel('新多维表格名称').fill(baseName)
    await baseDialog.getByLabel('说明').fill('M7 atomic Base fixture')
    await baseDialog.locator('.ant-btn-primary').click()

    await expect(page.getByText(baseName, { exact: true }).first()).toBeVisible({ timeout: 20_000 })
    await expect(page.getByRole('button', { name: '打开完整表格' })).toBeVisible()
    await expect(page.getByText('目标可访问')).toHaveCount(0)

    const auditResponse = await request.get(`${apiBaseUrl}/admin/audit-logs`, {
      headers: bearer(administrator),
      params: { action: 'knowledge.node.created', targetType: 'knowledge_content', limit: 50 },
    })
    expect(auditResponse.ok()).toBeTruthy()
    const auditEntries = await auditResponse.json() as Array<{ metadata?: { targetObjectType?: string } }>
    expect(auditEntries.some((entry) => entry.metadata?.targetObjectType === 'project')).toBeTruthy()
    expect(auditEntries.some((entry) => entry.metadata?.targetObjectType === 'base')).toBeTruthy()

    const memberSession = await loginByApi(request, memberUsername, memberPassword)
    const memberChoices = await request.get(`${apiBaseUrl}/platform/object-choices?types=project&types=base&query=PW_&limit=50`, {
      headers: bearer(memberSession),
    })
    expect(memberChoices.ok()).toBeTruthy()
    const memberChoicePayload = await memberChoices.text()
    expect(memberChoicePayload).not.toContain(projectName)
    expect(memberChoicePayload).not.toContain(baseName)
  } finally {
    await request.post(`${apiBaseUrl}/admin/users/${member.id}/offboard`, {
      headers: bearer(administrator),
      data: {},
    })
    await archiveKnowledgeSpaceFixture(request, administrator, space)
  }
})
