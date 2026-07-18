import { accessSync, constants } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

export const packageRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
export const repositoryRoot = resolve(packageRoot, '../..')

export function repoPath(...parts: string[]): string {
  return join(repositoryRoot, ...parts)
}

export function assertWithin(parent: string, candidate: string, description = 'path'): string {
  const resolvedParent = resolve(parent)
  const resolvedCandidate = resolve(candidate)
  const relation = relative(resolvedParent, resolvedCandidate)
  if (relation.startsWith('..') || resolve(resolvedParent, relation) !== resolvedCandidate) {
    throw new Error(`${description} escapes ${resolvedParent}: ${resolvedCandidate}`)
  }
  return resolvedCandidate
}

export function exists(path: string): boolean {
  try {
    accessSync(path, constants.F_OK)
    return true
  } catch {
    return false
  }
}
