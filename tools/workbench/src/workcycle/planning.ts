import { existsSync, readFileSync } from 'node:fs'
import { join, posix } from 'node:path'

export interface PlanningContract {
  roadmapPath: string
  roadmapStatus: 'active' | 'completed'
  route: string
  program: string
  programDoc: string
  targetArchitectureDoc: string
  programRevision: number
  stage: string
  stageFinalMilestone: string
  programStageStatus: string
  taskRows: Map<string, string>
  milestones: Set<string>
}

function frontMatter(content: string, label: string): Map<string, string> {
  const match = /^---\r?\n([\s\S]*?)\r?\n---(?:\r?\n|$)/.exec(content)
  if (!match) throw new Error(`${label} must start with YAML front matter`)
  const values = new Map<string, string>()
  for (const line of match[1].split(/\r?\n/)) {
    if (!line.trim() || line.trimStart().startsWith('#')) continue
    const separator = line.indexOf(':')
    if (separator <= 0) throw new Error(`${label} has invalid front matter: ${line}`)
    values.set(line.slice(0, separator).trim(), line.slice(separator + 1).trim().replace(/^['"]|['"]$/g, ''))
  }
  return values
}

function required(values: Map<string, string>, key: string, label: string): string {
  const value = values.get(key)?.trim()
  if (!value) throw new Error(`${label} front matter requires '${key}'`)
  return value
}

function markdownCells(line: string): string[] {
  if (!line.trim().startsWith('|') || !line.trim().endsWith('|')) return []
  return line.trim().slice(1, -1).split(/(?<!\\)\|/).map((cell) => cell.replaceAll('\\|', '|').trim())
}

function normalizedDocumentPath(value: string): string {
  return posix.normalize(value.replaceAll('\\', '/'))
}

function stageStatusRows(content: string): Array<{ stage: string; status: string }> {
  const lines = content.split(/\r?\n/)
  const rows: Array<{ stage: string; status: string }> = []
  let statusIndex = -1
  for (const line of lines) {
    const cells = markdownCells(line)
    if (!cells.length) continue
    if (cells[0].toLowerCase() === 'stage') {
      statusIndex = cells.findIndex((cell) => ['status', '状态'].includes(cell.toLowerCase()))
      continue
    }
    if (statusIndex < 0 || cells.length <= statusIndex || !/^[A-Z][A-Z0-9-]*-S\d{2}$/.test(cells[0])) continue
    rows.push({ stage: cells[0], status: cells[statusIndex] })
  }
  return rows
}

export function loadActivePlanningContract(root: string): PlanningContract {
  const roadmapPath = 'docs/02-roadmap/current-roadmap.md'
  const roadmapAbsolutePath = join(root, roadmapPath)
  if (!existsSync(roadmapAbsolutePath)) throw new Error(`Active roadmap is missing: ${roadmapPath}`)
  const roadmap = readFileSync(roadmapAbsolutePath, 'utf8').replace(/^\uFEFF/, '')
  const roadmapMeta = frontMatter(roadmap, roadmapPath)
  const roadmapStatus = required(roadmapMeta, 'status', roadmapPath).toLowerCase()
  if (!['active', 'completed'].includes(roadmapStatus)) throw new Error(`Active roadmap status must be active or completed, found '${roadmapStatus}'`)

  const route = required(roadmapMeta, 'route', roadmapPath).toUpperCase()
  const program = required(roadmapMeta, 'program', roadmapPath).toUpperCase()
  const stage = required(roadmapMeta, 'stage', roadmapPath).toUpperCase()
  const stageFinalMilestone = required(roadmapMeta, 'stage_final_milestone', roadmapPath).toUpperCase()
  const programDoc = normalizedDocumentPath(required(roadmapMeta, 'program_doc', roadmapPath))
  const programRevision = Number(required(roadmapMeta, 'program_revision', roadmapPath))
  if (!Number.isInteger(programRevision) || programRevision < 1) throw new Error(`${roadmapPath} program_revision must be a positive integer`)
  if (route !== stage) throw new Error(`${roadmapPath} route must equal stage`)
  if (!new RegExp(`^${program.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}-S\\d{2}$`).test(stage)) throw new Error(`${stage} must use ${program}-SXX naming`)
  if (!new RegExp(`^${stage.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}-M\\d+$`).test(stageFinalMilestone)) throw new Error(`${stageFinalMilestone} must be a milestone inside ${stage}`)
  if (!programDoc.startsWith('docs/00-product/initiatives/') || !programDoc.endsWith('.md') || programDoc.includes('..')) throw new Error(`program_doc must be an initiative document under docs/00-product/initiatives: ${programDoc}`)

  const programAbsolutePath = join(root, programDoc)
  if (!existsSync(programAbsolutePath)) throw new Error(`Program document is missing: ${programDoc}`)
  const programContent = readFileSync(programAbsolutePath, 'utf8').replace(/^\uFEFF/, '')
  const programMeta = frontMatter(programContent, programDoc)
  if (required(programMeta, 'program', programDoc).toUpperCase() !== program) throw new Error(`${programDoc} program does not match ${program}`)
  const programDocumentRevision = Number(required(programMeta, 'revision', programDoc))
  if (programDocumentRevision !== programRevision) throw new Error(`Program revision mismatch: roadmap=${programRevision}, program=${programDocumentRevision}`)
  const targetArchitectureDoc = normalizedDocumentPath(required(programMeta, 'target_architecture_doc', programDoc))
  if (!targetArchitectureDoc.startsWith('docs/01-architecture/') || !targetArchitectureDoc.endsWith('.md') || targetArchitectureDoc.includes('..')) throw new Error(`target_architecture_doc must be under docs/01-architecture: ${targetArchitectureDoc}`)
  const targetArchitecturePath = join(root, targetArchitectureDoc)
  if (!existsSync(targetArchitecturePath)) throw new Error(`Target architecture document is missing: ${targetArchitectureDoc}`)
  const targetArchitectureMeta = frontMatter(readFileSync(targetArchitecturePath, 'utf8').replace(/^\uFEFF/, ''), targetArchitectureDoc)
  if (required(targetArchitectureMeta, 'program', targetArchitectureDoc).toUpperCase() !== program) throw new Error(`${targetArchitectureDoc} program does not match ${program}`)

  const stages = stageStatusRows(programContent)
  const stageMatches = stages.filter((entry) => entry.stage === stage)
  if (stageMatches.length !== 1) throw new Error(`${programDoc} must contain exactly one Stage index row for ${stage}; found ${stageMatches.length}`)
  const activeStages = stages.filter((entry) => entry.status.toLowerCase() === 'active')
  const programStageStatus = stageMatches[0].status
  if (roadmapStatus === 'active') {
    if (programStageStatus.toLowerCase() !== 'active') throw new Error(`${stage} must be Active in ${programDoc}`)
    if (activeStages.length !== 1 || activeStages[0].stage !== stage) throw new Error(`${programDoc} must contain exactly one Active Stage matching ${stage}`)
    const currentStage = required(programMeta, 'current_stage', programDoc).toUpperCase()
    if (currentStage !== stage) throw new Error(`${programDoc} current_stage must match ${stage}`)
  } else {
    if (!['done', 'completed'].includes(programStageStatus.toLowerCase())) throw new Error(`${stage} must be Done or Completed when the current roadmap is completed`)
    if (activeStages.length) throw new Error(`${programDoc} cannot activate another Stage while the completed roadmap still occupies current-roadmap.md`)
    if (required(programMeta, 'current_stage', programDoc).toLowerCase() !== 'none') throw new Error(`${programDoc} current_stage must be none while a completed roadmap awaits archival`)
  }

  const taskRows = new Map<string, string>()
  const milestones = new Set<string>()
  for (const line of roadmap.split(/\r?\n/)) {
    const cells = markdownCells(line)
    if (cells.length < 4) continue
    const taskMatch = /^([A-Z][A-Z0-9-]*-S\d{2}-M\d+)-T(\d{2})$/.exec(cells[0])
    if (!taskMatch) continue
    if (taskRows.has(cells[0])) throw new Error(`${roadmapPath} contains duplicate task ${cells[0]}`)
    if (!cells[0].startsWith(`${stage}-`)) throw new Error(`${roadmapPath} contains a task outside ${stage}: ${cells[0]}`)
    taskRows.set(cells[0], cells.at(-1) ?? '')
    milestones.add(taskMatch[1])
  }
  if (!taskRows.size) throw new Error(`${roadmapPath} must contain tasks for ${stage}`)
  if (!milestones.has(stageFinalMilestone)) throw new Error(`${roadmapPath} does not contain the final milestone ${stageFinalMilestone}`)

  return {
    roadmapPath,
    roadmapStatus: roadmapStatus as PlanningContract['roadmapStatus'],
    route,
    program,
    programDoc,
    targetArchitectureDoc,
    programRevision,
    stage,
    stageFinalMilestone,
    programStageStatus,
    taskRows,
    milestones,
  }
}

export function assertTaskScopeInPlanning(contract: PlanningContract, milestone: string, tasks: string[]): void {
  if (!tasks.length) throw new Error('Task range must resolve to at least one task')
  if (!contract.milestones.has(milestone)) throw new Error(`${milestone} is not declared in ${contract.roadmapPath}`)
  if (!milestone.startsWith(`${contract.stage}-`)) throw new Error(`${milestone} is outside the active Stage ${contract.stage}`)
  for (const task of tasks) {
    const status = contract.taskRows.get(task)
    if (status === undefined) throw new Error(`${task} is not declared in ${contract.roadmapPath}`)
    if (['done', 'completed'].includes(status.toLowerCase())) throw new Error(`${task} is already ${status}; reopen it before starting another work cycle`)
  }
}

export function planningSummary(contract: PlanningContract): string {
  return `Planning contract passed: program=${contract.program}; revision=${contract.programRevision}; stage=${contract.stage}; milestones=${contract.milestones.size}; tasks=${contract.taskRows.size}; final=${contract.stageFinalMilestone}`
}
