import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

const ignoredDirectories = new Set(['.git', 'node_modules', 'target', 'dist', 'coverage', '.local-reports', '.local-backups', '.local-pilot'])

function walk(root: string, directory = root): string[] {
  const files: string[] = []
  for (const entry of readdirSync(directory)) {
    const path = join(directory, entry)
    if (statSync(path).isDirectory()) {
      if (!ignoredDirectories.has(entry) && entry.toLowerCase() !== 'archive') files.push(...walk(root, path))
    } else files.push(relative(root, path).replaceAll('\\', '/'))
  }
  return files
}

export function activePlatformViolations(root: string): string[] {
  const files = walk(root)
  const violations = files.filter((path) => path.toLowerCase().endsWith('.ps1')).map((path) => `active PowerShell file: ${path}`)
  const entryFiles = files.filter((path) =>
    path === 'package.json' || path === 'pnpm-workspace.yaml' || path.startsWith('.github/workflows/') ||
    path === 'README.md' || path === 'deploy/README.md' || /^docs\/(?:01-architecture|03-engineering|05-runbooks)\/.+\.md$/.test(path),
  )
  for (const path of entryFiles) {
    if (!existsSync(join(root, path))) continue
    const content = readFileSync(join(root, path), 'utf8')
    const lines = content.split(/\r?\n/)
    lines.forEach((line, index) => {
      if (/\b(?:powershell|pwsh)\b|\.ps1\b/i.test(line)) violations.push(`Windows-only active reference: ${path}:${index + 1}`)
    })
  }
  return violations
}
