import type { GitBaseline } from '../lib/git.js'

export type ValidationProfile = 'light' | 'stage' | 'route-final'
export type QualityMode = 'quick' | 'stage' | 'full'

export interface QualityGateEvidence {
  reportPath: string
  mode: QualityMode
  status: 'PASS' | 'FAIL'
  stepLogs: string[]
  completedAt: string
}

export interface BrowserEvidence {
  status: 'passed' | 'not_required' | string
  kind?: 'real' | 'mock' | 'not-required' | string
  environment?: 'isolated' | 'shared-readonly' | 'mock' | 'not-required' | string
  command?: string
  logPath?: string
  reason?: string
  completedAt?: string
}

export interface SystemEvidence {
  status: 'passed' | string
  environment: 'isolated' | string
  command: string
  tasks: string[]
  logPath: string
  sha256: string
  completedAt: string
}

export interface WorkScope {
  scopeValid: boolean
  expectedTasks: string[]
  milestoneCount: number
  maxMilestonesPerCycle: number
}

export interface WorkPlanningContext {
  program: string
  programDoc: string
  initiativeIndexDoc: string
  targetArchitectureDoc: string
  programRevision: number
  stage: string
  stageFinalMilestone: string
  isStageFinalMilestone: boolean
}

export interface WorkContext extends GitBaseline {
  schemaVersion?: number
  goal?: string
  status?: string
  taskRange?: string
  milestone?: string
  docMode: string
  startedAt: string
  completedAt?: string
  requiredDocs: string[]
  workScope: WorkScope
  allowedActiveDocs?: string[]
  allowedReportDir?: string
  evidencePolicy?: { contractVersion?: number }
  browserEvidence?: BrowserEvidence
  systemEvidence?: SystemEvidence
  lastQualityGate?: QualityGateEvidence
  auditSnapshots?: string[]
  planning?: WorkPlanningContext
}

export const currentWorkContextSchemaVersion = 3

export function migrateWorkContext(context: WorkContext): WorkContext {
  const sourceVersion = context.schemaVersion ?? 1
  if (!Number.isInteger(sourceVersion) || sourceVersion < 1 || sourceVersion > currentWorkContextSchemaVersion) {
    throw new Error(`Unsupported work context schemaVersion: ${String(context.schemaVersion)}`)
  }
  return {
    ...context,
    schemaVersion: currentWorkContextSchemaVersion,
    allowedActiveDocs: context.allowedActiveDocs ?? [],
    allowedReportDir: context.allowedReportDir ?? 'docs/90-reports',
    evidencePolicy: context.evidencePolicy ?? { contractVersion: 1 },
  }
}
