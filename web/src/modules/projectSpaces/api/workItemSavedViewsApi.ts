import { apiGet, apiPatch, apiPost } from '../../../shared/api/httpClient'

import type { QueryDefinition, WorkItemColumn, WorkItemViewResult } from './workItemViewsApi'
import type { WorkItemTreeResult } from './workItemTreeViewsApi'

export type SavedViewPresentation = {
  schemaVersion: 1
  mode: 'table' | 'list' | 'tree'
  density: 'compact' | 'comfortable'
  columns: WorkItemColumn[]
  relationKey: string
  maxDepth: number
}

export type SavedViewShare = {
  subjectUserId: string
  permission: 'use' | 'manage'
  status: 'active' | 'revoked'
  version: number
  sharedAt: string
  revokedAt?: string | null
}

export type WorkItemSavedView = {
  id: string
  spaceId: string
  ownerUserId: string
  scope: 'personal' | 'shared'
  name: string
  description: string
  status: 'active' | 'deleted'
  aggregateVersion: number
  versionNumber: number
  configHash: string
  query: QueryDefinition
  presentation: SavedViewPresentation
  shares: SavedViewShare[]
  canUse: boolean
  canManage: boolean
  createdAt: string
  updatedAt: string
}

export type SavedViewExecution = {
  view: WorkItemSavedView
  result: WorkItemViewResult | WorkItemTreeResult
}

export const savedViewKeys = {
  all: ['project-spaces', 'saved-views'] as const,
  list: (spaceId: string) => [...savedViewKeys.all, spaceId] as const,
}

export function listSavedViews(spaceId: string) {
  return apiGet<WorkItemSavedView[]>(`/project-spaces/${spaceId}/saved-views`)
}

export function createSavedView(
  spaceId: string,
  input: {
    name: string
    description: string
    scope: 'personal' | 'shared'
    query: QueryDefinition
    presentation: SavedViewPresentation
  },
) {
  const requestId = crypto.randomUUID()
  return apiPost<WorkItemSavedView>(
    `/project-spaces/${spaceId}/saved-views`,
    { requestId, ...input },
    { requestId },
  )
}

export function updateSavedView(
  spaceId: string,
  viewId: string,
  input: Omit<Parameters<typeof createSavedView>[1], 'scope'> & {
    expectedVersion: number
    scope: 'personal' | 'shared'
  },
) {
  const requestId = crypto.randomUUID()
  return apiPatch<WorkItemSavedView>(
    `/project-spaces/${spaceId}/saved-views/${viewId}`,
    { requestId, ...input },
    { requestId },
  )
}

function command(
  spaceId: string,
  viewId: string,
  action: 'copy' | 'share' | 'revoke' | 'transfer' | 'delete',
  input: Record<string, unknown>,
) {
  const requestId = crypto.randomUUID()
  return apiPost<WorkItemSavedView>(
    `/project-spaces/${spaceId}/saved-views/${viewId}:${action}`,
    { requestId, ...input },
    { requestId },
  )
}

export function copySavedView(spaceId: string, viewId: string, name: string) {
  return command(spaceId, viewId, 'copy', { name })
}

export function shareSavedView(
  spaceId: string,
  viewId: string,
  expectedVersion: number,
  subjectUserId: string,
  permission: 'use' | 'manage',
) {
  return command(spaceId, viewId, 'share', { expectedVersion, subjectUserId, permission })
}

export function revokeSavedView(
  spaceId: string,
  viewId: string,
  expectedVersion: number,
  subjectUserId: string,
) {
  return command(spaceId, viewId, 'revoke', { expectedVersion, subjectUserId })
}

export function transferSavedView(
  spaceId: string,
  viewId: string,
  expectedVersion: number,
  newOwnerUserId: string,
) {
  return command(spaceId, viewId, 'transfer', { expectedVersion, newOwnerUserId })
}

export function deleteSavedView(spaceId: string, viewId: string, expectedVersion: number) {
  return command(spaceId, viewId, 'delete', { expectedVersion })
}

export function executeSavedView(spaceId: string, viewId: string) {
  return apiPost<SavedViewExecution>(
    `/project-spaces/${spaceId}/saved-views/${viewId}:execute`,
  )
}

export function favoriteSavedView(viewId: string) {
  const requestId = crypto.randomUUID()
  return apiPost<unknown>(
    `/platform/personalization/favorites/saved_view/${viewId}`,
    { requestId, favorite: true },
    { requestId },
  )
}
