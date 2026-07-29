import { apiGet, apiGetText, apiPost } from '../../../shared/api/httpClient'

export type LegacySurface = {
  key: string
  layer: string
  owner: string
  accessMode: string
  userVisible: boolean
  removalStage: string
  evidence: string
}

export type LegacyAuditFinding = {
  id: string
  key: string
  category: string
  severity: 'info' | 'warning' | 'blocking'
  status: string
  affectedCount: number
  safeDetail: Record<string, unknown>
  recordedAt: string
}

export type RemovalDecision = {
  id: string
  snapshotId: string
  surfaceKey: string
  decision: 'remove' | 'retain_history' | 'blocked'
  reason: string
  requestId: string
  decidedBy: string
  decidedAt: string
  replayed: boolean
}

export type LegacyAuditSnapshot = {
  id: string
  workspaceId: string
  inventoryVersion: string
  status: 'ready' | 'blocked'
  sourceFingerprint: string
  totals: Record<string, number>
  surfaces: LegacySurface[]
  findings: LegacyAuditFinding[]
  decisions: RemovalDecision[]
  generatedBy: string
  generatedAt: string
}

const base = '/admin/project-migrations/legacy-audit'

export function listLegacyAuditSnapshots() {
  return apiGet<LegacyAuditSnapshot[]>(`${base}/snapshots`)
}

export function createLegacyAuditSnapshot() {
  return apiPost<LegacyAuditSnapshot>(`${base}/snapshots`)
}

export function decideLegacySurface(
  snapshotId: string,
  input: {
    surfaceKey: string
    decision: RemovalDecision['decision']
    reason: string
    requestId: string
  },
) {
  return apiPost<RemovalDecision>(`${base}/snapshots/${snapshotId}/decisions`, input, {
    requestId: input.requestId,
  })
}

export function exportLegacyAuditSnapshot(snapshotId: string) {
  return apiGetText(`${base}/snapshots/${snapshotId}/export`)
}
