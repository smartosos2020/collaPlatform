import { expect, test } from '@playwright/test'
import { randomUUID } from 'node:crypto'

import { installSession, loginByApi } from './support/api'
import { archiveKnowledgeSpaceFixture, createKnowledgeSpaceFixture, uniqueFixtureName } from './support/fixtures'
import {
  createBaseObjectFixture,
  createKnowledgeItem,
  createProjectObjectFixture,
  getKnowledgeContent,
  knowledgeContentUrl,
  saveKnowledgeBlocks,
} from './support/knowledge'

test('@route-final isolated object cards render Base, project and knowledge content with a safe unavailable fallback', async ({ page, request }) => {
  test.skip(process.env.COLLA_E2E_ISOLATED !== 'true', 'creates Base and project fixtures, so it requires an isolated disposable environment')
  const session = await loginByApi(request)
  const base = await createBaseObjectFixture(request, session, uniqueFixtureName('object-card-base'))
  const project = await createProjectObjectFixture(request, session, uniqueFixtureName('object-card-project'))
  const space = await createKnowledgeSpaceFixture(request, session, 'object-cards')

  try {
    const target = await createKnowledgeItem(request, session, space, {
      title: 'M3 Object Card Knowledge',
      contentType: 'markdown',
      content: 'Object card target',
    })
    const host = await createKnowledgeItem(request, session, space, {
      title: 'M3 Object Card Host',
      contentType: 'markdown',
      content: 'Object card host',
    })
    const before = await getKnowledgeContent(request, session, space.id, host.item.id)
    const saved = await saveKnowledgeBlocks(request, session, space.id, host.item.id, before.item.currentVersionNo, [
      { blockType: 'heading', content: 'M3 Object Cards', sortOrder: 0 },
      { blockType: 'embed_object', content: JSON.stringify({ objectType: 'base', objectId: base.id }), sortOrder: 1 },
      { blockType: 'embed_object', content: JSON.stringify({ objectType: 'project', objectId: project.id }), sortOrder: 2 },
      { blockType: 'embed_object', content: JSON.stringify({ objectType: 'knowledge_content', objectId: target.item.id }), sortOrder: 3 },
      { blockType: 'embed_object', content: JSON.stringify({ objectType: 'base', objectId: randomUUID() }), sortOrder: 4 },
    ])
    expect(saved.blocks.filter((block) => ['base', 'project', 'knowledge_content'].includes(block.embedSummary?.objectType ?? '')).every(
      (block) => block.embedSummary?.accessState === 'available',
    )).toBeTruthy()
    expect(saved.blocks.find((block) => block.embedSummary?.accessState === 'not_found')).toBeDefined()

    await installSession(page, session)
    await page.goto(knowledgeContentUrl(space.id, host.item.id))
    await expect(page.getByText(base.name).first()).toBeVisible()
    await expect(page.getByText(project.name).first()).toBeVisible()
    await expect(page.getByText('M3 Object Card Knowledge').first()).toBeVisible()
    await expect(page.getByText('对象不可访问')).toBeVisible()
  } finally {
    await archiveKnowledgeSpaceFixture(request, session, space)
  }
})
