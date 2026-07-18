import { createHash } from 'node:crypto'
import { existsSync, readFileSync } from 'node:fs'
import { relative, resolve } from 'node:path'
import { runSync } from './process.js'

export function gitHead(root: string): string {
  return runSync('git', ['rev-parse', 'HEAD'], { cwd: root })
}

export function gitStatusPaths(root: string): string[] {
  const output = runSync('git', ['status', '--porcelain=v1', '-z', '--untracked-files=all'], { cwd: root, trimOutput: false })
  if (!output) return []
  const entries = output.split('\0').filter(Boolean)
  const paths: string[] = []
  for (let index = 0; index < entries.length; index += 1) {
    const entry = entries[index]
    if (entry.length < 4) throw new Error(`Invalid git status --porcelain -z entry: ${JSON.stringify(entry)}`)
    const status = entry.slice(0, 2)
    paths.push(entry.slice(3))
    if (/[RC]/.test(status)) {
      const source = entries[index + 1]
      if (!source) throw new Error(`Git ${status.trim()} entry is missing its source path`)
      paths.push(source)
      index += 1
    }
  }
  return [...new Set(paths.map((path) => path.replaceAll('\\', '/')))]
}

export function committedPaths(root: string, baselineCommit: string): string[] {
  if (!baselineCommit) return []
  const output = runSync('git', ['diff', '--name-only', '-z', `${baselineCommit}..HEAD`], { cwd: root, trimOutput: false })
  return output ? output.split('\0').filter(Boolean).map((path) => path.replaceAll('\\', '/')) : []
}

export function fileSignature(root: string, path: string): string {
  const fullPath = resolve(root, path)
  if (!existsSync(fullPath)) return 'missing'
  return createHash('sha256').update(readFileSync(fullPath)).digest('hex')
}

export function fileSignatures(root: string, paths: string[]): Record<string, string> {
  return Object.fromEntries([...new Set(paths)].map((path) => [path.replaceAll('\\', '/'), fileSignature(root, path)]))
}

export interface GitBaseline {
  baselineCommit: string
  baselineChangedPaths: string[]
  baselineFileSignatures: Record<string, string>
}

export function changedSinceBaseline(root: string, context: GitBaseline): string[] {
  const candidates = new Set([...gitStatusPaths(root), ...committedPaths(root, context.baselineCommit), ...Object.keys(context.baselineFileSignatures)])
  const baselineDirty = new Set(context.baselineChangedPaths)
  return [...candidates].filter((path) => {
    const baselineSignature = context.baselineFileSignatures[path]
    if (baselineSignature !== undefined) return fileSignature(root, path) !== baselineSignature
    return !baselineDirty.has(path) || committedPaths(root, context.baselineCommit).includes(path)
  }).map((path) => relative(root, resolve(root, path)).replaceAll('\\', '/'))
}
