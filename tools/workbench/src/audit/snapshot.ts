import { mkdirSync, readdirSync, statSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { runSync } from '../lib/process.js'

const excluded = new Set(['node_modules', 'target', 'dist', '.local-reports', '.local-backups', '.local-logs', '.git'])

function inventory(root: string): string[] {
  const result: string[] = []
  const visit = (directory: string): void => {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      if (entry.isDirectory() && excluded.has(entry.name)) continue
      const path = join(directory, entry.name)
      if (entry.isDirectory()) visit(path)
      else result.push(path.slice(root.length + 1).replaceAll('\\', '/'))
    }
  }
  visit(root)
  return result
}

function capture(command: string, args: string[], root: string): string {
  try { return runSync(command, args, { cwd: root }) || '_No output_' }
  catch (error) { return `ERROR: ${error instanceof Error ? error.message : String(error)}` }
}

export function auditSnapshot(root: string, label: string, profile: 'light' | 'full'): string {
  const directory = join(root, '.local-reports')
  mkdirSync(directory, { recursive: true })
  const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, '')
  const report = join(directory, `audit-snapshot-${stamp}.md`)
  const lines = ['# AI Audit Snapshot', '', `- Profile: ${profile}`, `- Label: ${label}`, `- Time: ${new Date().toISOString()}`, `- Root: ${root}`]
  if (profile === 'full') {
    const files = inventory(root)
    lines.push(`- File count excluding generated artifacts: ${files.length}`, '', '## Toolchain', '```text', capture('java', ['-version'], root), capture('mvn', ['-version'], root), `node ${capture('node', ['--version'], root)}`, `pnpm ${capture('pnpm', ['--version'], root)}`, capture('docker', ['--version'], root), '```')
  }
  lines.push('', '## Git Status', '```text', capture('git', ['status', '--short'], root) || 'Working tree clean.', '```')
  if (profile === 'full') lines.push('', '## Docker Compose', '```text', capture('docker', ['compose', 'ps'], root), '```', '', '## Source Inventory', '```text', ...inventory(root), '```')
  writeFileSync(report, lines.join('\n'))
  return report
}
