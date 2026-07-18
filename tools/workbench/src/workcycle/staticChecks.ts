import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'
import ts from 'typescript'
import { runSync } from '../lib/process.js'
import type { PlanningContract } from './planning.js'

const generatedPaths = ['web/dist', 'server/target', 'node_modules', '.local-logs', '.local-reports', '.local-backups', 'web/test-results', 'web/playwright-report']

function documentStatus(path: string, content: string): string {
  const match = /^---\r?\n([\s\S]*?)\r?\n---(?:\r?\n|$)/.exec(content.replace(/^\uFEFF/, ''))
  if (!match) throw new Error(`Active document must start with YAML front matter: ${path}`)
  const status = match[1].match(/^status:\s*['"]?([^'"\r\n]+)['"]?\s*$/m)?.[1]?.trim().toLowerCase()
  if (!status) throw new Error(`Active document front matter requires status: ${path}`)
  return status
}

export function assertFrontendRouteLazyLoading(root: string): void {
  const path = join(root, 'web/src/app/router.tsx')
  const source = ts.createSourceFile(path, readFileSync(path, 'utf8'), ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
  let lazyDynamicImport = false
  const visit = (node: ts.Node): void => {
    if (ts.isImportDeclaration(node) && ts.isStringLiteral(node.moduleSpecifier) && /(?:^|\/)modules\/.*\/pages\//.test(node.moduleSpecifier.text.replaceAll('\\', '/'))) {
      throw new Error(`Route pages must use lazyRoute dynamic imports, found static import: ${node.moduleSpecifier.text}`)
    }
    if (ts.isCallExpression(node) && ts.isIdentifier(node.expression) && node.expression.text === 'lazyRoute') {
      const findImport = (candidate: ts.Node): boolean => (ts.isCallExpression(candidate) && candidate.expression.kind === ts.SyntaxKind.ImportKeyword) || candidate.getChildren(source).some(findImport)
      if (node.arguments.some(findImport)) lazyDynamicImport = true
    }
    ts.forEachChild(node, visit)
  }
  visit(source)
  if (!lazyDynamicImport) throw new Error('Router must use lazyRoute with dynamic imports for page components')
}

export function assertMockitoJavaAgent(root: string): void {
  const pom = readFileSync(join(root, 'server/pom.xml'), 'utf8')
  if (!/maven-surefire-plugin/.test(pom) || !/-javaagent:\$\{settings\.localRepository\}\/org\/mockito\/mockito-core/.test(pom)) {
    throw new Error('Server test runtime must configure Mockito as a javaagent in maven-surefire-plugin')
  }
}

export function assertGeneratedArtifacts(root: string): void {
  const tracked = runSync('git', ['ls-files', '-z', '--', ...generatedPaths], { cwd: root, trimOutput: false }).split('\0').filter(Boolean)
  if (tracked.length) throw new Error(`Generated artifacts are tracked: ${tracked.join(', ')}`)
  for (const path of generatedPaths) {
    const probe = `${path}/.workbench-ignore-probe`
    try { runSync('git', ['check-ignore', '-q', '--no-index', '--', probe], { cwd: root }) }
    catch { throw new Error(`Generated/local path is not ignored: ${path}`) }
  }
}

export function assertDocumentationStructure(root: string, planning: PlanningContract): void {
  const docsRoot = join(root, 'docs')
  const invalidRootDocs = readdirSync(docsRoot, { withFileTypes: true }).filter((entry) => entry.isFile() && entry.name.endsWith('.md') && entry.name !== 'README.md')
  if (invalidRootDocs.length) throw new Error(`Only docs/README.md is allowed in docs root: ${invalidRootDocs.map((entry) => entry.name).join(', ')}`)
  const expected = new Map<string, string[]>([
    ['docs/README.md', ['active']],
    ['docs/00-product/current-product-scope.md', ['active']],
    ['docs/01-architecture/current-architecture.md', ['active']],
    ['docs/01-architecture/technology-selection.md', ['active']],
    ['docs/01-architecture/platform-object-model.md', ['active']],
    [planning.roadmapPath, ['active', 'completed']],
    ['docs/03-engineering/ai-engineering-governance.md', ['active']],
    [planning.initiativeIndexDoc, ['active']],
    [planning.programDoc, ['active']],
    [planning.targetArchitectureDoc, ['active', 'target']],
  ])
  for (const [path, statuses] of expected) {
    const absolutePath = join(root, path)
    if (!existsSync(absolutePath)) throw new Error(`Active document is missing: ${path}`)
    const status = documentStatus(path, readFileSync(absolutePath, 'utf8'))
    if (!statuses.includes(status)) throw new Error(`${path} status must be ${statuses.join(' or ')}, found ${status}`)
  }
}

export function implementationMarkers(root: string): string[] {
  const markers: string[] = []
  const excludedDirectories = new Set(['.git', 'node_modules', 'target', 'dist', '.local-logs', '.local-reports', '.local-backups', 'test-results', 'playwright-report'])
  const extensions = new Set(['.ts', '.tsx', '.js', '.jsx', '.java', '.xml', '.yml', '.yaml', '.json', '.sql', '.md', '.css', '.scss'])
  const excludedFiles = new Set(['docs/03-engineering/ai-engineering-governance.md', 'tools/workbench/src/workcycle/quality.ts', 'tools/workbench/src/workcycle/staticChecks.ts'])
  const visit = (directory: string): void => {
    if (markers.length >= 50) return
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const absolutePath = join(directory, entry.name)
      const relativePath = absolutePath.slice(root.length + 1).replaceAll('\\', '/')
      if (entry.isDirectory()) {
        if (excludedDirectories.has(entry.name) || relativePath === 'scripts/archive' || relativePath.startsWith('scripts/archive/') || relativePath === 'docs/90-reports' || relativePath === 'docs/99-archive') continue
        visit(absolutePath)
      } else if (extensions.has(entry.name.slice(entry.name.lastIndexOf('.')).toLowerCase()) && !excludedFiles.has(relativePath)) {
        readFileSync(absolutePath, 'utf8').split(/\r?\n/).forEach((line, index) => {
          if (markers.length < 50 && /\b(?:TODO|FIXME|HACK|XXX)\b/.test(line)) markers.push(`${relativePath}:${index + 1}:${line.trim()}`)
        })
      }
    }
  }
  visit(root)
  return markers
}
