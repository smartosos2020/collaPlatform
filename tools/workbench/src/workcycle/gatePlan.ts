import type { QualityMode } from './contracts.js'

export type AffectedArea =
  | 'backend'
  | 'frontend'
  | 'collaboration'
  | 'workbench'
  | 'governance'
  | 'operations'
  | 'workspace'

export type QualityGateId =
  | 'toolchain'
  | 'planning'
  | 'architecture'
  | 'docker'
  | 'workbench-typecheck'
  | 'workbench-tests'
  | 'mockito'
  | 'sensitive-data'
  | 'security-audit'
  | 'flyway'
  | 'knowledge-naming'
  | 'active-platform'
  | 'generated-artifacts'
  | 'implementation-markers'
  | 'documentation-structure'
  | 'work-cycle-documents'
  | 'git-diff'

export interface GatePlanInput {
  mode: QualityMode
  areas?: Iterable<AffectedArea>
  skipDocker?: boolean
  skipAudit?: boolean
}

export interface QualityGatePlan {
  mode: QualityMode
  areas: Set<AffectedArea>
  gates: Set<QualityGateId>
  has(gate: QualityGateId): boolean
}

const allAreas: AffectedArea[] = ['backend', 'frontend', 'collaboration', 'workbench', 'governance', 'operations', 'workspace']
const allAuditGates: QualityGateId[] = [
  'mockito',
  'sensitive-data',
  'security-audit',
  'flyway',
  'knowledge-naming',
  'active-platform',
  'generated-artifacts',
  'implementation-markers',
  'documentation-structure',
]

export function affectedAreas(paths: Iterable<string>): Set<AffectedArea> {
  const areas = new Set<AffectedArea>()
  for (const path of paths) {
    let matched = false
    const add = (area: AffectedArea): void => {
      areas.add(area)
      matched = true
    }
    if (path.startsWith('server/')) add('backend')
    if (path.startsWith('web/')) add('frontend')
    if (path.startsWith('collaboration/')) add('collaboration')
    if (path.startsWith('tools/workbench/')) add('workbench')
    if (path.startsWith('docs/') || path.startsWith('scripts/')) add('governance')
    if (path.startsWith('deploy/')) add('operations')
    if (path === 'package.json' || path === 'pnpm-lock.yaml' || path === 'pnpm-workspace.yaml') add('workspace')
    if (!matched) areas.add('workspace')
  }
  return areas
}

export function createQualityGatePlan(input: GatePlanInput): QualityGatePlan {
  const areas = new Set(input.areas ?? allAreas)
  const gates = new Set<QualityGateId>(['toolchain', 'planning', 'architecture', 'work-cycle-documents'])
  if (!input.skipDocker && (input.mode === 'full' || input.areas === undefined)) gates.add('docker')
  if (['stage', 'full'].includes(input.mode)) gates.add('git-diff')
  if (areas.has('workbench') || areas.has('workspace')) {
    gates.add('workbench-typecheck')
    if (['stage', 'full'].includes(input.mode)) gates.add('workbench-tests')
  }
  if (!input.skipAudit) {
    if (input.mode === 'full' || input.areas === undefined) {
      allAuditGates.forEach((gate) => gates.add(gate))
    } else if (input.mode === 'stage') {
      gates.add('sensitive-data')
      gates.add('generated-artifacts')
      gates.add('implementation-markers')
      if (areas.has('backend')) {
        gates.add('mockito')
        gates.add('security-audit')
        gates.add('knowledge-naming')
      }
      if (areas.has('frontend') || areas.has('collaboration') || areas.has('workbench')) gates.add('security-audit')
      if (areas.has('workbench') || areas.has('governance') || areas.has('workspace')) gates.add('active-platform')
      if (areas.has('governance') || areas.has('workspace')) gates.add('documentation-structure')
    }
  }
  return { mode: input.mode, areas, gates, has: (gate) => gates.has(gate) }
}
