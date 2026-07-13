import { expect, test } from '@playwright/test'

import { apiBaseUrl, bearer, installSession, loginByApi } from './support/api'
import { installFailureEvidence } from './support/diagnostics'
import {
  archiveKnowledgeSpaceFixture,
  createKnowledgeSpaceFixture,
  requireIsolatedIdentityFixture,
  uniqueFixtureName,
} from './support/fixtures'
import { createKnowledgeItem } from './support/knowledge'

test('@route-final M4 cross-module search: knowledge, issue, Base and message resolve to user routes', async ({ page, request }, testInfo) => {
  requireIsolatedIdentityFixture()
  const flushEvidence = installFailureEvidence(page, testInfo)
  const session = await loginByApi(request)
  const fixtureName = uniqueFixtureName('cross-module-search')
  const searchToken = fixtureName.replaceAll('_', '-').toLowerCase()
  const projectKey = `M4${searchToken.replaceAll('-', '').slice(-8)}`.toUpperCase()
  const space = await createKnowledgeSpaceFixture(request, session, 'cross-module-search')

  try {
    const projectResponse = await request.post(`${apiBaseUrl}/projects`, {
      headers: bearer(session),
      data: {
        projectKey,
        name: `Project ${searchToken}`,
        description: `Cross-module regression fixture ${searchToken}`,
        memberIds: [],
      },
    })
    expect(projectResponse.ok()).toBeTruthy()
    const project = await projectResponse.json() as { id: string; conversationId: string }

    const issueResponse = await request.post(`${apiBaseUrl}/projects/${project.id}/issues`, {
      headers: bearer(session),
      data: {
        issueType: 'bug',
        title: `Issue ${searchToken}`,
        description: `Cross-module search fixture ${searchToken}`,
        priority: 'high',
      },
    })
    expect(issueResponse.ok()).toBeTruthy()
    const issue = await issueResponse.json() as { issue: { id: string; title: string } }

    const baseResponse = await request.post(`${apiBaseUrl}/bases`, {
      headers: bearer(session),
      data: { name: `Base ${searchToken}`, description: `Cross-module search fixture ${searchToken}` },
    })
    expect(baseResponse.ok()).toBeTruthy()
    const basePayload = await baseResponse.json() as { base: { id: string } }

    const tableResponse = await request.post(`${apiBaseUrl}/bases/${basePayload.base.id}/tables`, {
      headers: bearer(session),
      data: { name: `Table ${searchToken}` },
    })
    expect(tableResponse.ok()).toBeTruthy()
    const tablePayload = await tableResponse.json() as { table: { id: string } }

    const fieldResponse = await request.post(`${apiBaseUrl}/bases/${basePayload.base.id}/tables/${tablePayload.table.id}/fields`, {
      headers: bearer(session),
      data: { name: 'Title', fieldType: 'text', config: {}, required: true },
    })
    expect(fieldResponse.ok()).toBeTruthy()
    const fieldsPayload = await fieldResponse.json() as { fields: Array<{ id: string }> }
    const titleField = fieldsPayload.fields.at(-1)
    expect(titleField).toBeDefined()

    const recordResponse = await request.post(`${apiBaseUrl}/bases/${basePayload.base.id}/tables/${tablePayload.table.id}/records`, {
      headers: bearer(session),
      data: { values: { [titleField!.id]: `Record ${searchToken}` } },
    })
    expect(recordResponse.ok()).toBeTruthy()

    await createKnowledgeItem(request, session, space, {
      title: `Knowledge ${searchToken}`,
      contentType: 'markdown',
      content: `Cross-module search fixture ${searchToken}`,
    })

    const messageResponse = await request.post(`${apiBaseUrl}/conversations/${project.conversationId}/messages`, {
      headers: bearer(session),
      data: {
        clientMessageId: crypto.randomUUID(),
        messageType: 'text',
        content: `Message ${searchToken} /issues/${issue.issue.id}`,
      },
    })
    expect(messageResponse.ok()).toBeTruthy()

    const reindexResponse = await request.post(`${apiBaseUrl}/admin/search-governance/reindex`, { headers: bearer(session) })
    expect(reindexResponse.ok()).toBeTruthy()

    await expect.poll(async () => {
      const searchResponse = await request.get(`${apiBaseUrl}/search`, {
        headers: bearer(session),
        params: { q: searchToken, limit: '20' },
      })
      if (!searchResponse.ok()) return []
      const payload = await searchResponse.json() as { items: Array<{ objectType: string }> }
      return [...new Set(payload.items.map((item) => item.objectType))].sort()
    }).toEqual(['base', 'base_record', 'base_table', 'issue', 'knowledge_content', 'message'])

    await installSession(page, session)
    await page.goto(`/search?q=${encodeURIComponent(searchToken)}`)
    await expect(page.getByRole('heading', { name: '事项', exact: true })).toBeVisible()
    await expect(page.getByRole('heading', { name: '知识内容', exact: true })).toBeVisible()
    await expect(page.getByRole('heading', { name: '表格空间', exact: true })).toBeVisible()
    await expect(page.getByRole('heading', { name: '消息', exact: true })).toBeVisible()
    const issueResult = page.locator('.search-result-item').filter({ hasText: issue.issue.title })
    const knowledgeResult = page.locator('.search-result-item').filter({ hasText: `Knowledge ${searchToken}` })
    const baseResult = page.locator('.search-result-item').filter({ hasText: `Base ${searchToken}` })
    const messageResult = page.locator('.search-result-item').filter({ hasText: `Message ${searchToken}` })
    await expect(issueResult).toBeVisible()
    await expect(knowledgeResult).toBeVisible()
    await expect(baseResult).toBeVisible()
    await expect(messageResult).toBeVisible()

    await issueResult.getByRole('button', { name: '打开', exact: true }).click()
    await expect(page).toHaveURL(new RegExp(`/issues/${issue.issue.id}$`))
    await expect(page.getByText('基础信息', { exact: true })).toBeVisible()
  } finally {
    await archiveKnowledgeSpaceFixture(request, session, space)
    await flushEvidence()
  }
})
