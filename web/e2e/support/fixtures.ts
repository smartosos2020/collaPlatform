import { expect, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, type E2eSession } from './api'

export const fixturePrefix = `PW_${new Date().toISOString().replace(/[-:.TZ]/g, '')}`

export type KnowledgeSpaceFixture = {
  id: string
  rootItemId: string
  homeItemId: string
  name: string
  code: string
}

export function uniqueFixtureName(scenario: string) {
  const nonce = Math.random().toString(36).slice(2, 8)
  return `${fixturePrefix}_${scenario}_${nonce}`
}

export async function createKnowledgeSpaceFixture(
  request: APIRequestContext,
  session: E2eSession,
  scenario: string,
): Promise<KnowledgeSpaceFixture> {
  const name = uniqueFixtureName(scenario)
  const response = await request.post(`${apiBaseUrl}/knowledge-bases`, {
    headers: bearer(session),
    data: {
      name,
      code: name.toLowerCase().replace(/[^a-z0-9]+/g, '-').slice(0, 64),
      description: `Playwright isolated fixture for ${scenario}`,
      visibility: 'private',
    },
  })
  expect(response.ok(), `knowledge fixture creation failed for ${scenario}`).toBeTruthy()
  const payload = await response.json()
  return {
    id: payload.space.id,
    rootItemId: payload.space.rootItemId,
    homeItemId: payload.space.homeItemId,
    name,
    code: payload.space.code,
  }
}

export async function archiveKnowledgeSpaceFixture(
  request: APIRequestContext,
  session: E2eSession,
  fixture: KnowledgeSpaceFixture,
) {
  const response = await request.post(`${apiBaseUrl}/knowledge-bases/${fixture.id}/archive`, {
    headers: bearer(session),
  })
  expect(response.ok(), `knowledge fixture archive failed for ${fixture.id}`).toBeTruthy()
}

export async function expectNoActiveFixture(
  request: APIRequestContext,
  session: E2eSession,
  fixture: KnowledgeSpaceFixture,
) {
  const response = await request.get(`${apiBaseUrl}/knowledge-bases`, { headers: bearer(session) })
  expect(response.ok()).toBeTruthy()
  const spaces = await response.json() as Array<{ id: string; name: string }>
  expect(spaces.find((space) => space.id === fixture.id || space.name === fixture.name)).toBeUndefined()
}

export async function listActiveKnowledgeSpaces(
  request: APIRequestContext,
  session: E2eSession,
) {
  const response = await request.get(`${apiBaseUrl}/knowledge-bases`, { headers: bearer(session) })
  expect(response.ok(), 'active knowledge-space listing failed').toBeTruthy()
  return await response.json() as Array<{ id: string; name: string }>
}

export function requireIsolatedIdentityFixture() {
  if (process.env.COLLA_E2E_ISOLATED !== 'true') {
    throw new Error('Dynamic identity fixtures require COLLA_E2E_ISOLATED=true and a disposable test environment.')
  }
}
