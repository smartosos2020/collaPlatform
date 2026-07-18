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
  return [...new Set(output.split('\0').filter(Boolean).flatMap((entry) => {
    const value = entry.slice(3)
    return value.includes(' -> ') ? [value.split(' -> ').at(-1)!] : [value]
  }).map((path) => path.replaceAll('\\', '/')))]
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
