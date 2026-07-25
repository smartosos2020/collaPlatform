import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { isAbsolute, join, relative, resolve } from 'node:path'
import { run } from '../lib/process.js'
import type { SystemEvidence } from './contracts.js'

export interface SystemEvidenceRequest {
  executable: string
  args?: string[]
  cwd?: string
  tasks: string[]
}

function evidencePath(root: string, value: string): string {
  const resolved = resolve(root, value || '.')
  const relation = relative(root, resolved)
  if (relation === '..' || relation.startsWith(`..\\`) || isAbsolute(relation)) {
    throw new Error(`System evidence cwd must remain inside the repository: ${value}`)
  }
  return resolved
}

function displayCommand(executable: string, args: string[]): string {
  const quote = (value: string): string => /[\s"]/g.test(value) ? JSON.stringify(value) : value
  return [executable, ...args].map(quote).join(' ')
}

export function sha256File(path: string): string {
  if (!existsSync(path)) throw new Error(`Evidence log does not exist: ${path}`)
  return createHash('sha256').update(readFileSync(path)).digest('hex')
}

export async function executeSystemEvidence(root: string, request: SystemEvidenceRequest): Promise<SystemEvidence> {
  if (!request.executable.trim()) throw new Error('System evidence requires an executable')
  if (!request.tasks.length) throw new Error('System evidence requires at least one linked task')
  const args = request.args ?? []
  const output = await run(request.executable, args, {
    cwd: evidencePath(root, request.cwd ?? '.'),
    capture: true,
    trimOutput: false,
  })
  const directory = join(root, '.local-reports')
  mkdirSync(directory, { recursive: true })
  const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, '')
  const logPath = join(directory, `work-cycle-system-${stamp}.log`)
  writeFileSync(logPath, output || '_Command completed without output._\n')
  return {
    status: 'passed',
    environment: 'isolated',
    command: displayCommand(request.executable, args),
    tasks: [...new Set(request.tasks)].sort(),
    logPath: relative(root, logPath).replaceAll('\\', '/'),
    sha256: sha256File(logPath),
    completedAt: new Date().toISOString(),
  }
}
