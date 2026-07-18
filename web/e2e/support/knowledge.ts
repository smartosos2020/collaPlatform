import { expect, type APIRequestContext } from '@playwright/test'

import { apiBaseUrl, bearer, type E2eSession } from './api'
import type { KnowledgeSpaceFixture } from './fixtures'

export type KnowledgeContentDetail = {
  item: {
    id: string
    title: string
    currentVersionNo: number
    contentType: string
  }
  content: string
  blocks: Array<{
    id: string
    blockType: string
    content: string
    sortOrder: number
    embedSummary?: { objectType: string; objectId: string; accessState: string; title?: string | null }
  }>
  comments: Array<{ id: string; content: string; resolved: boolean; replies: Array<{ id: string; content: string }> }>
  permissions: Array<{ subjectType: string; subjectId: string; permissionLevel: string }>
}

export type KnowledgeItemFixture = {
  id: string
  title: string
  currentVersionNo: number
  contentType: string
}

export type BaseObjectFixture = {
  id: string
  name: string
}

export type ProjectObjectFixture = {
  id: string
  name: string
}

const itemPath = (spaceId: string, itemId: string) => `${apiBaseUrl}/knowledge-bases/${spaceId}/items/${itemId}`

export async function createKnowledgeItem(
  request: APIRequestContext,
  session: E2eSession,
  space: KnowledgeSpaceFixture,
  input: { parentId?: string | null; title: string; contentType: 'folder' | 'markdown'; content?: string },
): Promise<KnowledgeContentDetail> {
  const response = await request.post(`${apiBaseUrl}/knowledge-bases/${space.id}/items`, {
    headers: bearer(session),
    data: {
      parentId: input.parentId ?? space.rootItemId,
      title: input.title,
      contentType: input.contentType,
      content: input.content ?? '',
    },
  })
  expect(response.ok(), `knowledge item creation failed for ${input.title}`).toBeTruthy()
  return await response.json() as KnowledgeContentDetail
}

export async function createBaseObjectFixture(
  request: APIRequestContext,
  session: E2eSession,
  name: string,
): Promise<BaseObjectFixture> {
  const response = await request.post(`${apiBaseUrl}/bases`, {
    headers: bearer(session),
    data: { name, description: 'Playwright isolated object-card fixture' },
  })
  expect(response.ok(), `base creation failed for ${name}`).toBeTruthy()
  const payload = await response.json() as { base: BaseObjectFixture }
  return payload.base
}

export async function createProjectObjectFixture(
  request: APIRequestContext,
  session: E2eSession,
  name: string,
): Promise<ProjectObjectFixture> {
  const projectKey = `M3${Math.random().toString(36).slice(2, 10).toUpperCase()}`
  const response = await request.post(`${apiBaseUrl}/projects`, {
    headers: bearer(session),
    data: { projectKey, name, description: 'Playwright isolated object-card fixture', memberIds: [] },
  })
  expect(response.ok(), `project creation failed for ${name}`).toBeTruthy()
  return await response.json() as ProjectObjectFixture
}

export async function getKnowledgeContent(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  itemId: string,
): Promise<KnowledgeContentDetail> {
  const response = await request.get(itemPath(spaceId, itemId), { headers: bearer(session) })
  expect(response.ok(), `knowledge content fetch failed for ${itemId}`).toBeTruthy()
  return await response.json() as KnowledgeContentDetail
}

export async function saveKnowledgeBlocks(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  itemId: string,
  baseVersionNo: number,
  blocks: Array<{ id?: string; blockType: string; content: string; sortOrder: number }>,
  saveMode: 'auto' | 'manual' = 'auto',
): Promise<KnowledgeContentDetail> {
  const response = await request.patch(`${itemPath(spaceId, itemId)}/blocks`, {
    headers: bearer(session),
    data: {
      baseVersionNo,
      saveMode,
      blocks: blocks.map((block) => ({
        ...block,
        schemaVersion: 2,
        attrs: {},
        richContent: {},
        deleted: false,
      })),
    },
  })
  expect(response.ok(), 'structured block save failed').toBeTruthy()
  return await response.json() as KnowledgeContentDetail
}

export async function saveKnowledgeContent(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  itemId: string,
  input: { baseVersionNo: number; title: string; content: string },
): Promise<KnowledgeContentDetail> {
  const response = await request.patch(itemPath(spaceId, itemId), { headers: bearer(session), data: input })
  expect(response.ok(), 'knowledge content save failed').toBeTruthy()
  return await response.json() as KnowledgeContentDetail
}

export async function listKnowledgeVersions(request: APIRequestContext, session: E2eSession, spaceId: string, itemId: string) {
  const response = await request.get(`${itemPath(spaceId, itemId)}/versions`, { headers: bearer(session) })
  expect(response.ok(), 'knowledge version list failed').toBeTruthy()
  return await response.json() as Array<{ versionNo: number; versionName?: string | null; versionType?: string; title: string }>
}

export async function diffKnowledgeVersions(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  itemId: string,
  fromVersionNo: number,
  toVersionNo: number,
) {
  const response = await request.get(`${itemPath(spaceId, itemId)}/versions/diff`, {
    headers: bearer(session),
    params: { fromVersionNo, toVersionNo },
  })
  expect(response.ok(), `knowledge version diff ${fromVersionNo}->${toVersionNo} failed`).toBeTruthy()
  return await response.json() as { fromVersionNo: number; toVersionNo: number; lines: Array<{ type: string; content: string; blockId?: string | null }> }
}

export async function createNamedKnowledgeVersion(request: APIRequestContext, session: E2eSession, spaceId: string, itemId: string, versionName: string) {
  const response = await request.post(`${itemPath(spaceId, itemId)}/versions/named`, {
    headers: bearer(session),
    data: { versionName, summary: 'Playwright route fixture' },
  })
  expect(response.ok(), 'named knowledge version creation failed').toBeTruthy()
  return await response.json() as KnowledgeContentDetail
}

export async function restoreKnowledgeVersion(request: APIRequestContext, session: E2eSession, spaceId: string, itemId: string, versionNo: number) {
  const response = await request.post(`${itemPath(spaceId, itemId)}/versions/${versionNo}/restore`, { headers: bearer(session) })
  if (!response.ok()) {
    const body = await response.text().catch(() => '<unreadable>')
    throw new Error(`knowledge version ${versionNo} restore failed: HTTP ${response.status()} ${body.slice(0, 200)}`)
  }
  return await response.json() as KnowledgeContentDetail
}

export async function addKnowledgeComment(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  itemId: string,
  input: { content: string; blockId?: string },
) {
  const response = await request.post(`${itemPath(spaceId, itemId)}/comments`, {
    headers: bearer(session),
    data: { ...input, anchorType: input.blockId ? 'block' : 'document' },
  })
  expect(response.ok(), 'knowledge comment creation failed').toBeTruthy()
  return await response.json() as KnowledgeContentDetail
}

export async function replyToKnowledgeComment(request: APIRequestContext, session: E2eSession, spaceId: string, itemId: string, commentId: string, content: string) {
  const response = await request.post(`${itemPath(spaceId, itemId)}/comments/${commentId}/replies`, { headers: bearer(session), data: { content } })
  expect(response.ok(), 'knowledge comment reply failed').toBeTruthy()
  return await response.json() as KnowledgeContentDetail
}

export async function setKnowledgeCommentResolution(request: APIRequestContext, session: E2eSession, spaceId: string, itemId: string, commentId: string, resolved: boolean) {
  const response = await request.post(`${itemPath(spaceId, itemId)}/comments/${commentId}/${resolved ? 'resolve' : 'reopen'}`, { headers: bearer(session) })
  expect(response.ok(), 'knowledge comment resolution update failed').toBeTruthy()
  return await response.json() as KnowledgeContentDetail
}

export async function currentUser(request: APIRequestContext, session: E2eSession) {
  const response = await request.get(`${apiBaseUrl}/auth/me`, { headers: bearer(session) })
  expect(response.ok(), 'current user lookup failed').toBeTruthy()
  return await response.json() as { id: string; username: string }
}

export async function grantKnowledgePermission(
  request: APIRequestContext,
  session: E2eSession,
  spaceId: string,
  itemId: string,
  input: { subjectType: string; subjectId: string; permissionLevel: 'view' | 'comment' | 'edit' | 'manage' | 'owner' },
) {
  const response = await request.post(`${itemPath(spaceId, itemId)}/permissions`, { headers: bearer(session), data: input })
  expect(response.ok(), `knowledge permission grant failed for ${input.subjectType}`).toBeTruthy()
  return await response.json() as KnowledgeContentDetail
}

export async function knowledgeCollaborationHealth(request: APIRequestContext, session: E2eSession, spaceId: string, itemId: string) {
  const response = await request.get(`${itemPath(spaceId, itemId)}/collaboration/health`, { headers: bearer(session) })
  expect(response.ok(), 'knowledge collaboration health lookup failed').toBeTruthy()
  return await response.json() as { activeUsers: number; dirty: boolean; serverClock: number }
}

export function knowledgeContentUrl(spaceId: string, itemId: string) {
  return `/knowledge-bases/${spaceId}/items/${itemId}`
}
