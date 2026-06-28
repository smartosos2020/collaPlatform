import { expect, test, type APIRequestContext, type Browser, type BrowserContext, type Page } from '@playwright/test'

const apiBaseUrl = process.env.COLLA_E2E_API_BASE_URL ?? 'http://localhost:8080/api'
const adminUsername = process.env.COLLA_E2E_USERNAME ?? 'admin'
const adminPassword = process.env.COLLA_E2E_PASSWORD ?? ['admin', '123456'].join('')

type LoginTokens = {
  accessToken: string
  refreshToken: string
}

test('M44 docs collaboration: two clients converge and create checkpoint version', async ({ browser, request }) => {
  const suffix = Date.now().toString(36)
  const adminTokens = await login(request, adminUsername, adminPassword, `m44-admin-${suffix}`)
  const authHeaders = { Authorization: `Bearer ${adminTokens.accessToken}` }

  const memberUsername = `m44doc_${suffix}`
  const memberCredential = ['member', '123456'].join('')
  const memberResponse = await request.post(`${apiBaseUrl}/admin/users`, {
    headers: authHeaders,
    data: {
      username: memberUsername,
      password: memberCredential,
      displayName: `M44 Doc ${suffix}`,
      email: `${memberUsername}@colla.local`,
      roleCode: 'member',
    },
  })
  expect(memberResponse.ok()).toBeTruthy()
  const member = await memberResponse.json()

  const baseContent = `M44 collaboration base ${suffix}`
  const docResponse = await request.post(`${apiBaseUrl}/docs`, {
    headers: authHeaders,
    data: {
      title: `M44 Collaboration ${suffix}`,
      content: baseContent,
    },
  })
  expect(docResponse.ok()).toBeTruthy()
  const detail = await docResponse.json()
  const documentId = detail.document.id as string

  const grantResponse = await request.post(`${apiBaseUrl}/docs/${documentId}/permissions`, {
    headers: authHeaders,
    data: {
      userId: member.id,
      permissionLevel: 'edit',
    },
  })
  expect(grantResponse.ok()).toBeTruthy()

  const adminContext = await browserContext(browser, `m44-admin-client-${suffix}`)
  const memberContext = await browserContext(browser, `m44-member-client-${suffix}`)
  const adminPage = await adminContext.newPage()
  const memberPage = await memberContext.newPage()

  try {
    await Promise.all([
      loginInBrowser(adminPage, adminUsername, adminPassword),
      loginInBrowser(memberPage, memberUsername, memberCredential),
    ])
    await Promise.all([
      openDocument(adminPage, documentId, baseContent),
      openDocument(memberPage, documentId, baseContent),
    ])

    const adminLine = `admin line ${suffix}`
    const memberLine = `member line ${suffix}`
    await replaceDocumentContent(adminPage, `${baseContent}\n${adminLine}`)
    await expect.poll(
      async () => {
        const currentResponse = await request.get(`${apiBaseUrl}/docs/${documentId}`, {
          headers: authHeaders,
        })
        expect(currentResponse.ok()).toBeTruthy()
        const current = await currentResponse.json()
        return current.content as string
      },
      { timeout: 15_000 },
    ).toContain(adminLine)
    await memberPage.reload()
    await expect(memberPage.locator('.doc-prosemirror')).toContainText(adminLine)
    await appendDocumentContent(memberPage, memberLine)

    await expect(memberPage.locator('.doc-prosemirror')).toContainText(adminLine)
    await expect(memberPage.locator('.doc-prosemirror')).toContainText(memberLine)
    await expect(memberPage.locator('.doc-prosemirror')).toContainText(adminLine)
    await expect(memberPage.locator('.doc-prosemirror')).toContainText(memberLine)
    await expect(adminPage.getByText('版本冲突').first()).not.toBeVisible()
    await expect(memberPage.getByText('版本冲突').first()).not.toBeVisible()
    await expect.poll(
      async () => {
        const currentResponse = await request.get(`${apiBaseUrl}/docs/${documentId}`, {
          headers: authHeaders,
        })
        expect(currentResponse.ok()).toBeTruthy()
        const current = await currentResponse.json()
        return current.content as string
      },
      { timeout: 15_000 },
    ).toContain(adminLine)
    await expect.poll(
      async () => {
        const currentResponse = await request.get(`${apiBaseUrl}/docs/${documentId}`, {
          headers: authHeaders,
        })
        expect(currentResponse.ok()).toBeTruthy()
        const current = await currentResponse.json()
        return current.content as string
      },
      { timeout: 15_000 },
    ).toContain(memberLine)
    await adminPage.reload()
    await expect(adminPage.locator('.doc-prosemirror')).toContainText(adminLine)
    await expect(adminPage.locator('.doc-prosemirror')).toContainText(memberLine)

    const checkpointButton = adminPage.getByRole('button', { name: '生成版本' })
    await expect(checkpointButton).toBeEnabled({ timeout: 15_000 })
    await checkpointButton.click()
    await expect(adminPage.getByText('已生成版本')).toBeVisible()

    const versionsResponse = await request.get(`${apiBaseUrl}/docs/${documentId}/versions`, {
      headers: authHeaders,
    })
    expect(versionsResponse.ok()).toBeTruthy()
    const versions = await versionsResponse.json()
    expect(versions[0].versionNo).toBe(2)
    expect(versions[0].content).toContain(adminLine)
    expect(versions[0].content).toContain(memberLine)

    await memberPage.reload()
    await expect(memberPage.locator('.doc-prosemirror')).toContainText(adminLine)
    await expect(memberPage.locator('.doc-prosemirror')).toContainText(memberLine)
    await expect(memberPage.getByText(/协同已加入|自动保存/).first()).toBeVisible()
    const healthResponse = await request.get(`${apiBaseUrl}/docs/${documentId}/collaboration/health`, {
      headers: authHeaders,
    })
    expect(healthResponse.ok()).toBeTruthy()
    const health = await healthResponse.json()
    expect(health.activeUsers).toBeGreaterThanOrEqual(1)
    expect(health.dirty).toBeFalsy()
  } finally {
    await adminContext.close()
    await memberContext.close()
  }
})

test('M42 docs editor: edit, format, save, refresh, and view-only users stay read-only', async ({ browser, request }) => {
  const suffix = Date.now().toString(36)
  const adminTokens = await login(request, adminUsername, adminPassword, `m42-admin-${suffix}`)
  const authHeaders = { Authorization: `Bearer ${adminTokens.accessToken}` }
  const viewerUsername = `m42viewer_${suffix}`
  const viewerCredential = ['member', '123456'].join('')
  const viewer = await createMember(request, authHeaders, viewerUsername, viewerCredential, `M42 Viewer ${suffix}`)

  const baseContent = `M42 base content ${suffix}`
  const docResponse = await request.post(`${apiBaseUrl}/docs`, {
    headers: authHeaders,
    data: {
      title: `M42 Editor ${suffix}`,
      content: baseContent,
    },
  })
  expect(docResponse.ok()).toBeTruthy()
  const detail = await docResponse.json()
  const documentId = detail.document.id as string

  const grantResponse = await request.post(`${apiBaseUrl}/docs/${documentId}/permissions`, {
    headers: authHeaders,
    data: {
      userId: viewer.id,
      permissionLevel: 'view',
    },
  })
  expect(grantResponse.ok()).toBeTruthy()

  const adminContext = await browserContext(browser, `m42-admin-client-${suffix}`)
  const viewerContext = await browserContext(browser, `m42-viewer-client-${suffix}`)
  const adminPage = await adminContext.newPage()
  const viewerPage = await viewerContext.newPage()

  try {
    await loginInBrowser(adminPage, adminUsername, adminPassword)
    await openDocument(adminPage, documentId, baseContent)

    const formattedLine = `M42 formatted heading ${suffix}`
    await replaceDocumentContent(adminPage, formattedLine)
    await adminPage.getByRole('button', { name: '二级标题' }).click()
    await expect.poll(
      async () => {
        const currentResponse = await request.get(`${apiBaseUrl}/docs/${documentId}`, {
          headers: authHeaders,
        })
        expect(currentResponse.ok()).toBeTruthy()
        const current = await currentResponse.json()
        return current.content as string
      },
      { timeout: 15_000 },
    ).toContain(`## ${formattedLine}`)

    const checkpointButton = adminPage.getByRole('button', { name: '生成版本' })
    await expect(checkpointButton).toBeEnabled({ timeout: 15_000 })
    await checkpointButton.click()
    await expect(adminPage.getByText('已生成版本')).toBeVisible()

    await adminPage.reload()
    await expect(adminPage.locator('.doc-prosemirror')).toContainText(formattedLine)
    await expect(adminPage.locator('.doc-prosemirror h2')).toContainText(formattedLine)

    await loginInBrowser(viewerPage, viewerUsername, viewerCredential)
    await openDocument(viewerPage, documentId, formattedLine)
    await expect(viewerPage.getByLabel('文档标题')).toBeDisabled()
    await expect(viewerPage.locator('.doc-prosemirror')).toHaveAttribute('contenteditable', 'false')
    await expect(viewerPage.getByRole('button', { name: '二级标题' })).toBeDisabled()
    await viewerPage.locator('.doc-prosemirror').click()
    await viewerPage.keyboard.insertText(` viewer mutation ${suffix}`)
    await expect(viewerPage.locator('.doc-prosemirror')).not.toContainText(`viewer mutation ${suffix}`)
  } finally {
    await adminContext.close()
    await viewerContext.close()
  }
})

test('M43 docs editor: block tools insert task, table, Base view, and file card', async ({ browser, request }) => {
  const suffix = Date.now().toString(36)
  const adminTokens = await login(request, adminUsername, adminPassword, `m43-admin-${suffix}`)
  const authHeaders = { Authorization: `Bearer ${adminTokens.accessToken}` }
  const baseResponse = await request.post(`${apiBaseUrl}/bases`, {
    headers: authHeaders,
    data: {
      name: `M43 Base ${suffix}`,
      description: 'M43 slash menu e2e base',
    },
  })
  expect(baseResponse.ok()).toBeTruthy()
  const base = await baseResponse.json()
  const baseId = base.base.id as string
  const tableResponse = await request.post(`${apiBaseUrl}/bases/${baseId}/tables`, {
    headers: authHeaders,
    data: {
      name: `M43 Table ${suffix}`,
    },
  })
  expect(tableResponse.ok()).toBeTruthy()
  const table = await tableResponse.json()
  const tableId = table.table.id as string

  const baseContent = `M43 block baseline ${suffix}`
  const docResponse = await request.post(`${apiBaseUrl}/docs`, {
    headers: authHeaders,
    data: {
      title: `M43 Blocks ${suffix}`,
      content: baseContent,
    },
  })
  expect(docResponse.ok()).toBeTruthy()
  const detail = await docResponse.json()
  const documentId = detail.document.id as string
  const adminContext = await browserContext(browser, `m43-admin-client-${suffix}`)
  const page = await adminContext.newPage()

  try {
    await loginInBrowser(page, adminUsername, adminPassword)
    await openDocument(page, documentId, baseContent)

    const taskText = `M43 task item ${suffix}`
    await replaceDocumentContent(page, taskText)
    await page.getByRole('button', { name: '任务列表' }).click()
    await expect(page.locator('.doc-prosemirror')).toContainText(taskText)

    await page.keyboard.press('End')
    await page.keyboard.press('Enter')
    await openSlashCommand(page, '表格')
    await expect(page.locator('.doc-prosemirror table')).toBeVisible()

    await page.keyboard.press('End')
    await page.keyboard.press('Enter')
    await openSlashCommand(page, 'Base 视图')
    await page.getByPlaceholder('对象 ID').fill(tableId)
    await page.getByRole('button', { name: 'OK' }).click()
    await expect(page.locator('.doc-object-card-node')).toContainText(`M43 Table ${suffix}`)

    await page.keyboard.press('End')
    await page.keyboard.press('Enter')
    await openSlashCommand(page, '文件')
    await page.locator('input[type="file"]').nth(1).setInputFiles({
      name: `m43-${suffix}.txt`,
      mimeType: 'text/plain',
      buffer: Buffer.from(`M43 file card ${suffix}`),
    })
    await expect(page.getByText('文件上传中...')).not.toBeVisible({ timeout: 15_000 })
    await expect(page.locator('.doc-file-card-node')).toContainText(`m43-${suffix}.txt`)

    await expect.poll(
      async () => {
        const currentResponse = await request.get(`${apiBaseUrl}/docs/${documentId}`, {
          headers: authHeaders,
        })
        expect(currentResponse.ok()).toBeTruthy()
        const current = await currentResponse.json()
        return current.content as string
      },
      { timeout: 20_000 },
    ).toContain(taskText)
    const checkpointButton = page.getByRole('button', { name: '生成版本' })
    await expect(checkpointButton).toBeEnabled({ timeout: 15_000 })
    await checkpointButton.click()
    await expect(page.getByText('已生成版本')).toBeVisible()
  } finally {
    await adminContext.close()
  }
})

async function login(request: APIRequestContext, username: string, password: string, deviceFingerprint: string) {
  const response = await request.post(`${apiBaseUrl}/auth/login`, {
    data: {
      username,
      password,
      deviceType: 'web',
      deviceFingerprint,
      deviceName: 'M44 docs collaboration e2e',
      appVersion: 'm44',
    },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as LoginTokens
}

async function createMember(
  request: APIRequestContext,
  headers: Record<string, string>,
  username: string,
  password: string,
  displayName: string,
) {
  const response = await request.post(`${apiBaseUrl}/admin/users`, {
    headers,
    data: {
      username,
      password,
      displayName,
      email: `${username}@colla.local`,
      roleCode: 'member',
    },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as { id: string }
}

async function browserContext(browser: Browser, clientId: string): Promise<BrowserContext> {
  const context = await browser.newContext()
  await context.addInitScript(
    ({ stableClientId }) => {
      window.localStorage.setItem('colla-doc-client-id', stableClientId)
    },
    {
      stableClientId: clientId,
    },
  )
  return context
}

async function loginInBrowser(page: Page, username: string, password: string) {
  await page.goto('/login')
  await page.getByLabel('账号').fill(username)
  await page.getByLabel('密码').fill(password)
  await page.getByRole('button', { name: /登\s*录/ }).click()
  await expect(page.getByText('消息').first()).toBeVisible()
}

async function openDocument(page: Page, documentId: string, expectedContent: string) {
  await page.goto(`/docs/${documentId}`)
  await expect(page.locator('.doc-prosemirror')).toBeVisible()
  await expect(page.locator('.doc-prosemirror')).toContainText(expectedContent)
  await expect(page.getByText(/协同已加入|自动保存/).first()).toBeVisible()
}

async function replaceDocumentContent(page: Page, content: string) {
  const editor = page.locator('.doc-prosemirror')
  await editor.click()
  await page.keyboard.press('Control+A')
  await page.keyboard.insertText(content)
}

async function appendDocumentContent(page: Page, content: string) {
  const editor = page.locator('.doc-prosemirror')
  await editor.click()
  await page.keyboard.press('End')
  await page.keyboard.press('Enter')
  await page.keyboard.insertText(content)
}

async function openSlashCommand(page: Page, label: string) {
  await page.locator('.doc-editor-toolbar button').first().click()
  const menu = page.locator('.doc-slash-menu')
  await expect(menu).toBeVisible()
  await menu.locator('button').filter({ hasText: label }).click()
}
