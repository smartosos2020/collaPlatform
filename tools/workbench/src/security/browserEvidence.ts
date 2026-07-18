import { readFileSync, readdirSync, statSync } from 'node:fs'
import { dirname, extname, isAbsolute, join, relative, resolve } from 'node:path'
import ts from 'typescript'
import { exists } from '../lib/paths.js'

const sourceExtensions = ['.ts', '.tsx', '.js', '.jsx', '.mts', '.cts', '.mjs', '.cjs']
const referencePattern = /(?:[A-Za-z0-9_.@~-]+[\\/])*[A-Za-z0-9_.-]+(?:\.spec\.(?:ts|tsx|js|jsx)|\.(?:ps1|ts|js))/gi

interface AliasRule {
  pattern: string
  targets: string[]
  baseUrl: string
}

function configFiles(root: string): string[] {
  const result: string[] = []
  const visit = (directory: string): void => {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      if (entry.name === 'node_modules' || entry.name === '.git' || entry.name === 'dist') continue
      const path = join(directory, entry.name)
      if (entry.isDirectory()) visit(path)
      else if (/^tsconfig(?:\.[^.]+)?\.json$/.test(entry.name)) result.push(path)
    }
  }
  visit(root)
  return result
}

function loadAliases(root: string): AliasRule[] {
  const rules: AliasRule[] = []
  for (const file of configFiles(root)) {
    const parsed = ts.parseConfigFileTextToJson(file, readFileSync(file, 'utf8')).config
    const options = parsed?.compilerOptions ?? {}
    const paths = options.paths ?? {}
    const baseUrl = resolve(dirname(file), options.baseUrl ?? '.')
    for (const [pattern, targets] of Object.entries(paths)) {
      if (Array.isArray(targets)) rules.push({ pattern, targets: targets.map(String), baseUrl })
    }
  }
  return rules
}

function resolveFile(candidate: string): string | undefined {
  const candidates = [candidate, ...sourceExtensions.map((extension) => `${candidate}${extension}`)]
  for (const extension of sourceExtensions) candidates.push(join(candidate, `index${extension}`))
  return candidates.find((path) => exists(path) && statSync(path).isFile())
}

function resolveImport(importer: string, specifier: string, aliases: AliasRule[]): string | undefined {
  if (specifier.startsWith('.')) return resolveFile(resolve(dirname(importer), specifier))
  if (isAbsolute(specifier)) return resolveFile(specifier)
  for (const rule of aliases) {
    const star = rule.pattern.indexOf('*')
    const prefix = star >= 0 ? rule.pattern.slice(0, star) : rule.pattern
    const suffix = star >= 0 ? rule.pattern.slice(star + 1) : ''
    if (!specifier.startsWith(prefix) || !specifier.endsWith(suffix)) continue
    const capture = specifier.slice(prefix.length, specifier.length - suffix.length || undefined)
    for (const target of rule.targets) {
      const resolved = resolveFile(resolve(rule.baseUrl, target.replace('*', capture)))
      if (resolved) return resolved
    }
  }
  return undefined
}

function importSpecifiers(source: ts.SourceFile): string[] {
  const result = new Set<string>()
  const visit = (node: ts.Node): void => {
    if ((ts.isImportDeclaration(node) || ts.isExportDeclaration(node)) && node.moduleSpecifier && ts.isStringLiteral(node.moduleSpecifier)) {
      result.add(node.moduleSpecifier.text)
    }
    if (ts.isCallExpression(node) && node.arguments.length > 0 && ts.isStringLiteral(node.arguments[0])) {
      if (node.expression.kind === ts.SyntaxKind.ImportKeyword || (ts.isIdentifier(node.expression) && node.expression.text === 'require')) {
        result.add(node.arguments[0].text)
      }
    }
    ts.forEachChild(node, visit)
  }
  visit(source)
  return [...result]
}

function memberName(expression: ts.Expression): string | undefined {
  if (ts.isPropertyAccessExpression(expression)) return expression.name.text
  if (ts.isElementAccessExpression(expression) && expression.argumentExpression && ts.isStringLiteral(expression.argumentExpression)) {
    return expression.argumentExpression.text
  }
  return undefined
}

function ownerName(expression: ts.Expression): string {
  if (ts.isPropertyAccessExpression(expression) || ts.isElementAccessExpression(expression)) return expression.expression.getText()
  return ''
}

function astViolations(path: string, sanctionedInstaller: string): string[] {
  const source = ts.createSourceFile(path, readFileSync(path, 'utf8'), ts.ScriptTarget.Latest, true)
  const violations: string[] = []
  const visit = (node: ts.Node): void => {
    if (ts.isCallExpression(node)) {
      const name = memberName(node.expression)
      const owner = ownerName(node.expression)
      if ((name === 'route' && /(?:^|\.)(?:page|context)$/.test(owner)) ||
          ((name === 'fulfill' || name === 'abort') && /(?:^|\.)route$/.test(owner))) {
        violations.push(`${owner}[${name}]`)
      }
      if (name === 'addInitScript' && /(?:^|\.)page$/.test(owner) && resolve(path) !== resolve(sanctionedInstaller)) {
        violations.push(`${owner}[addInitScript] outside sanctioned installer`)
      }
    }
    ts.forEachChild(node, visit)
  }
  visit(source)
  return violations
}

function textViolations(path: string): string[] {
  const content = readFileSync(path, 'utf8')
  const markers = [
    /\b(?:page|context)\s*(?:\.\s*route|\[\s*['"]route['"]\s*\])\s*\(/,
    /\broute\s*(?:\.\s*(?:fulfill|abort)|\[\s*['"](?:fulfill|abort)['"]\s*\])\s*\(/,
  ]
  return markers.filter((marker) => marker.test(content)).map(String)
}

export interface BrowserEvidenceResult {
  references: string[]
  files: string[]
}

export function assertRealBrowserEvidence(command: string, root: string): BrowserEvidenceResult {
  const references = [...new Set(command.match(referencePattern) ?? [])]
  if (references.length === 0) throw new Error('Real browser evidence must name a concrete spec or launcher script; --grep alone is insufficient.')
  const bases = [root, join(root, 'web'), join(root, 'web', 'e2e')]
  const topLevel = new Set<string>()
  for (const reference of references) {
    for (const base of bases) {
      const path = resolveFile(resolve(base, reference))
      if (path) topLevel.add(path)
    }
  }
  if (topLevel.size === 0) throw new Error('Real browser evidence does not reference an existing file.')

  const aliases = loadAliases(root)
  const queue = [...topLevel]
  const closure = new Set<string>()
  while (queue.length > 0) {
    const current = queue.shift()!
    if (closure.has(current)) continue
    closure.add(current)
    if (!sourceExtensions.includes(extname(current))) {
      const nested = readFileSync(current, 'utf8').match(referencePattern) ?? []
      for (const reference of nested) {
        for (const base of bases) {
          const path = resolveFile(resolve(base, reference))
          if (path && !closure.has(path)) queue.push(path)
        }
      }
      continue
    }
    const source = ts.createSourceFile(current, readFileSync(current, 'utf8'), ts.ScriptTarget.Latest, true)
    for (const specifier of importSpecifiers(source)) {
      const imported = resolveImport(current, specifier, aliases)
      if (imported && imported.startsWith(resolve(root)) && !closure.has(imported)) queue.push(imported)
    }
  }

  const sanctionedInstaller = join(root, 'web', 'e2e', 'support', 'api.ts')
  const violations: string[] = []
  for (const path of closure) {
    const hits = sourceExtensions.includes(extname(path)) ? astViolations(path, sanctionedInstaller) : textViolations(path)
    for (const hit of hits) violations.push(`${relative(root, path).replaceAll('\\', '/')} uses ${hit}`)
  }
  if (violations.length > 0) throw new Error(`Browser evidence is declared real but mocks browser/API state: ${violations.join('; ')}`)
  return { references: [...topLevel], files: [...closure] }
}
