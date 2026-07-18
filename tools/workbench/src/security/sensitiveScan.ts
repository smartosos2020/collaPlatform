import { readFileSync, readdirSync, statSync, writeFileSync, mkdirSync } from 'node:fs'
import { extname, join, relative, resolve } from 'node:path'

const excludedDirectories = new Set(['node_modules', 'target', 'dist', '.local-logs', '.local-reports', '.local-backups', 'test-results', 'playwright-report', '.git', '.idea'])
const binaryExtensions = new Set(['.png', '.jpg', '.jpeg', '.gif', '.ico', '.jar', '.class', '.zip', '.gz', '.pdf'])
const keyNames = '(?:password|passwd|pwd|secret(?:[_-]?key)?|token|api[_-]?key|access[_-]?key)'

export const sensitivePatterns = [
  { id: 'BEGIN RSA PRIVATE KEY', regex: /BEGIN RSA PRIVATE KEY/g },
  { id: 'BEGIN OPENSSH PRIVATE KEY', regex: /BEGIN OPENSSH PRIVATE KEY/g },
  { id: 'BEGIN EC PRIVATE KEY', regex: /BEGIN EC PRIVATE KEY/g },
  { id: 'AKIA[0-9A-Z]{16}', regex: /AKIA[0-9A-Z]{16}/g },
  { id: 'xox[baprs]-[0-9A-Za-z-]+', regex: /xox[baprs]-[0-9A-Za-z-]+/g },
  { id: 'ghp_[0-9A-Za-z_]{36,}', regex: /ghp_[0-9A-Za-z_]{36,}/g },
  { id: 'sk-[A-Za-z0-9]{20,}', regex: /sk-[A-Za-z0-9]{20,}/g },
  { id: 'jwt', regex: /eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{5,}/g },
]

export interface SensitiveHit { path: string; rule: string; line: number }
interface AllowEntry { pathGlob: string; pattern: string; reason: string }

function globMatches(value: string, glob: string): boolean {
  const expression = glob.split('*').map((part) => part.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('.*')
  return new RegExp(`^${expression}$`, 'i').test(value)
}

function allowlist(path: string): AllowEntry[] {
  try {
    return readFileSync(path, 'utf8').split(/\r?\n/).flatMap((line) => {
      if (!line.trim() || line.trimStart().startsWith('#')) return []
      const [pathGlob, pattern, reason] = line.split('\t', 3)
      if (!pathGlob || !pattern || !reason) throw new Error(`Malformed sensitive scan allowlist line: ${line}`)
      return [{ pathGlob: pathGlob.replaceAll('\\', '/'), pattern, reason }]
    })
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === 'ENOENT') return []
    throw error
  }
}

function filesUnder(root: string): string[] {
  const result: string[] = []
  const visit = (directory: string): void => {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      if (entry.isDirectory() && excludedDirectories.has(entry.name)) continue
      const path = join(directory, entry.name)
      if (entry.isDirectory()) visit(path)
      else if (!binaryExtensions.has(extname(path).toLowerCase()) && statSync(path).size < 2 * 1024 * 1024) result.push(path)
    }
  }
  visit(root)
  return result
}

function isPlaceholder(value: string): boolean {
  const normalized = value.trim().replace(/^['"]|['"]$/g, '')
  return !normalized || /^\$\{[^}]+\}$/.test(normalized) || /^(?:process\.env|import\.meta\.env)\b/.test(normalized) || /^<[^>]+>$/.test(normalized)
}

function lineNumber(content: string, index: number): number {
  return content.slice(0, index).split('\n').length
}

function assignmentHits(content: string, extension: string): Array<{ index: number; rule: string }> {
  const hits: Array<{ index: number; rule: string }> = []
  const patterns = extension === '.json'
    ? [new RegExp(`"${keyNames}"\\s*:\\s*"([^"\\n]+)"`, 'gi')]
    : extension === '.yml' || extension === '.yaml'
      ? [new RegExp(`^\\s*${keyNames}\\s*:\\s*([^#\\r\\n]+)`, 'gim')]
      : extension === '.env' || extension === ''
        ? [new RegExp(`^\\s*${keyNames}\\s*=\\s*([^#\\r\\n]+)`, 'gim')]
        : [
            new RegExp(`\\b${keyNames}\\s*=\\s*['"]([^'"\\r\\n]+)['"]`, 'gi'),
            new RegExp(`["']${keyNames}["']\\s*:\\s*['"]([^'"\\r\\n]+)['"]`, 'gi'),
          ]
  for (const pattern of patterns) {
    for (const match of content.matchAll(pattern)) {
      if (!isPlaceholder(match[1] ?? '')) hits.push({ index: match.index ?? 0, rule: 'credential-assignment' })
    }
  }
  return hits
}

export function scanSensitiveData(root: string, options: { writeReport?: boolean } = {}): { hits: SensitiveHit[]; waived: number; report?: string } {
  const allowlistPath = join(root, 'scripts', 'sensitive-scan-allowlist.tsv')
  const allowed = allowlist(allowlistPath)
  const hits: SensitiveHit[] = []
  let waived = 0
  const scannerPath = resolve(root, 'tools', 'workbench', 'src', 'security', 'sensitiveScan.ts')
  for (const path of filesUnder(root)) {
    if (resolve(path) === scannerPath || resolve(path) === resolve(allowlistPath)) continue
    const content = readFileSync(path, 'utf8')
    const relativePath = relative(root, path).replaceAll('\\', '/')
    const candidates = sensitivePatterns.flatMap(({ id, regex }) => [...content.matchAll(regex)].map((match) => ({ index: match.index ?? 0, rule: id })))
    candidates.push(...assignmentHits(content, extname(path).toLowerCase()))
    for (const candidate of candidates) {
      const waiver = allowed.find((entry) => globMatches(relativePath, entry.pathGlob) && (entry.pattern === '*' || entry.pattern === candidate.rule || (candidate.rule === 'credential-assignment' && entry.pattern.includes('password'))))
      if (waiver) waived += 1
      else hits.push({ path: relativePath, rule: candidate.rule, line: lineNumber(content, candidate.index) })
    }
  }
  let report: string | undefined
  if (options.writeReport !== false) {
    const timestamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z')
    const directory = join(root, '.local-reports')
    mkdirSync(directory, { recursive: true })
    report = join(directory, `sensitive-data-scan-${timestamp}.md`)
    writeFileSync(report, [
      '# Sensitive Data Scan', '', `- Status: ${hits.length ? 'FAIL' : 'PASS'}`, `- Time: ${new Date().toISOString()}`, `- Waived: ${waived}`, '',
      '## Findings', ...(hits.length ? hits.map((hit) => `- ${hit.path}:${hit.line} [${hit.rule}]`) : ['- None']), '',
    ].join('\n'))
  }
  return { hits, waived, report }
}
