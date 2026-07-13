import { expect, test } from '@playwright/test'

import { loginByApi } from './support/api'
import {
  archiveKnowledgeSpaceFixture,
  createKnowledgeSpaceFixture,
  expectNoActiveFixture,
  listActiveKnowledgeSpaces,
} from './support/fixtures'

test('@smoke knowledge fixture lifecycle leaves the active shared-data baseline unchanged', async ({ request }) => {
  const session = await loginByApi(request)
  const baseline = await listActiveKnowledgeSpaces(request, session)
  const fixture = await createKnowledgeSpaceFixture(request, session, 'lifecycle')

  try {
    const during = await listActiveKnowledgeSpaces(request, session)
    expect(during.some((space) => space.id === fixture.id && space.name === fixture.name)).toBeTruthy()
  } finally {
    await archiveKnowledgeSpaceFixture(request, session, fixture)
    await expectNoActiveFixture(request, session, fixture)
  }

  const after = await listActiveKnowledgeSpaces(request, session)
  expect(after.map((space) => space.id).sort()).toEqual(baseline.map((space) => space.id).sort())
})
