import { mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { dirname, extname, posix, relative, resolve } from 'node:path'
import ts from 'typescript'
import { assertWithin } from '../lib/paths.js'
import { runSync } from '../lib/process.js'

const JAVA_MODULE_ROOT = 'server/src/main/java/com/colla/platform/modules/'
const JAVA_SHARED_ROOT = 'server/src/main/java/com/colla/platform/shared/'
const FRONTEND_MODULE_ROOT = 'web/src/modules/'
const MIGRATION_ROOT = 'server/src/main/resources/db/migration/'
const SOURCE_EXTENSIONS = new Set(['.ts', '.tsx', '.js', '.jsx'])

export interface DependencyImport {
  sourceFile: string
  sourceModule: string
  targetModule: string
  targetPackage: string
  targetLayer: string
  kind: string
}

export interface DependencyEdge {
  source: string
  target: string
  count: number
  infrastructureCount: number
}

export interface SqlAccess {
  sourceFile: string
  sourceModule: string
  table: string
  tableOwner: string
  mode: 'read' | 'write' | 'ddl'
  keyword: string
  line: number
  crossOwner: boolean
}

export interface SqlCandidate {
  sourceFile: string
  sourceModule: string
  table: string
  tableOwner: string
  modes: Array<'read' | 'write' | 'ddl'>
  lines: number[]
  occurrenceCount: number
}

export interface ArchitectureInventory {
  schemaVersion: 1
  source: { ref: string; pathFormat: 'repository-relative-posix' }
  semantics: {
    java: string
    frontend: string
    flyway: string
    sql: string
    deduplication: string
    limitations: string[]
  }
  backend: {
    modules: string[]
    moduleFileCounts: Record<string, number>
    javaFiles: number
    crossModuleImportCount: number
    crossModuleFileCount: number
    foreignInfrastructureImportCount: number
    foreignInfrastructureFileCount: number
    directedEdgeCount: number
    crossModuleImports: DependencyImport[]
    edges: DependencyEdge[]
    stronglyConnectedComponents: string[][]
    transactionalForeignInfrastructureFiles: string[]
    sharedToModuleImports: DependencyImport[]
    foreignPrivateImports: DependencyImport[]
  }
  frontend: {
    features: string[]
    sourceFiles: number
    crossFeatureImportCount: number
    crossFeatureFileCount: number
    directedEdgeCount: number
    crossFeatureImports: DependencyImport[]
    edges: DependencyEdge[]
    stronglyConnectedComponents: string[][]
  }
  database: {
    migrations: number
    migrationFiles: string[]
    activeTableCount: number
    activeTables: Array<{
      table: string
      createdIn: string
      lastChangedIn: string
      candidateOwner: string
      ownerStatus: 'candidate' | 'unresolved'
    }>
    ddlEvents: Array<{ migration: string; operation: 'create' | 'rename' | 'drop'; table: string; target?: string; line: number }>
    sqlAccesses: SqlAccess[]
    crossOwnerCandidates: SqlCandidate[]
    crossOwnerCandidateCount: number
    crossOwnerFileCount: number
    accessModeCounts: Record<'read' | 'write' | 'ddl', number>
    dynamicSqlCandidates: Array<{ sourceFile: string; reason: string }>
  }
  runtime: {
    scheduledTasks: Array<{ sourceFile: string; className: string; method: string; schedule: string; line: number }>
    inMemoryState: Array<{ sourceFile: string; pattern: string; line: number }>
    productionServices: Array<{ service: string; image: string; role: string }>
    roles: Array<{ role: string; currentDeployment: string; responsibility: string; factSource: string; evidence: string[] }>
    factSources: Array<{ store: string; role: string; evidence: string[] }>
  }
  comparison?: {
    ref: string
    backend: Record<string, number>
    frontend: Record<string, number>
    database: Record<string, number>
  }
}

interface RepositorySource {
  ref: string
  paths: string[]
  read(path: string): string
  exists(path: string): boolean
}

interface ScanOptions {
  ref?: string
  compareRef?: string
}

export interface InventoryOutput {
  inventory: ArchitectureInventory
  jsonPath: string
  markdownPath: string
}

export interface ArchitectureExpectations {
  schemaVersion: 1
  baselineId: string
  sourceCommit: string
  compareRef: string
  expected: Record<string, unknown>
}

function normalizePath(path: string): string {
  return path.replaceAll('\\', '/').replace(/^\.\//, '')
}

function walk(root: string, directory = root): string[] {
  const result: string[] = []
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (['.git', 'node_modules', 'target', 'dist', '.local-reports', '.local-logs'].includes(entry.name)) continue
    const fullPath = resolve(directory, entry.name)
    if (entry.isDirectory()) result.push(...walk(root, fullPath))
    else if (entry.isFile()) result.push(normalizePath(relative(root, fullPath)))
  }
  return result
}

function repositorySource(root: string, ref?: string): RepositorySource {
  if (!ref) {
    const paths = walk(root).sort()
    const known = new Set(paths)
    return {
      ref: 'WORKTREE',
      paths,
      read: (path) => readFileSync(resolve(root, path), 'utf8'),
      exists: (path) => known.has(normalizePath(path)),
    }
  }
  const output = runSync('git', ['ls-tree', '-r', '--name-only', '-z', ref], { cwd: root, trimOutput: false })
  const paths = output.split('\0').filter(Boolean).map(normalizePath).sort()
  const known = new Set(paths)
  return {
    ref,
    paths,
    read: (path) => runSync('git', ['show', `${ref}:${normalizePath(path)}`], { cwd: root, trimOutput: false }),
    exists: (path) => known.has(normalizePath(path)),
  }
}

function moduleFromPath(path: string, root: string): string | undefined {
  if (!path.startsWith(root)) return undefined
  return path.slice(root.length).split('/')[0] || undefined
}

function layerFromPackage(targetPackage: string, targetModule: string): string {
  const prefix = `com.colla.platform.modules.${targetModule}.`
  return targetPackage.startsWith(prefix) ? targetPackage.slice(prefix.length).split('.')[0] || 'root' : 'root'
}

function lineNumber(content: string, index: number): number {
  return content.slice(0, index).split('\n').length
}

function groupEdges(imports: DependencyImport[]): DependencyEdge[] {
  const grouped = new Map<string, DependencyEdge>()
  for (const item of imports) {
    const key = `${item.sourceModule}\0${item.targetModule}`
    const current = grouped.get(key) ?? { source: item.sourceModule, target: item.targetModule, count: 0, infrastructureCount: 0 }
    current.count += 1
    if (item.targetLayer === 'infrastructure') current.infrastructureCount += 1
    grouped.set(key, current)
  }
  return [...grouped.values()].sort((left, right) => left.source.localeCompare(right.source) || left.target.localeCompare(right.target))
}

function stronglyConnectedComponents(nodes: string[], edges: DependencyEdge[]): string[][] {
  const adjacency = new Map(nodes.map((node) => [node, [] as string[]]))
  for (const edge of edges) adjacency.get(edge.source)?.push(edge.target)
  for (const targets of adjacency.values()) targets.sort()

  let nextIndex = 0
  const indexes = new Map<string, number>()
  const lowLinks = new Map<string, number>()
  const stack: string[] = []
  const onStack = new Set<string>()
  const components: string[][] = []

  const visit = (node: string): void => {
    indexes.set(node, nextIndex)
    lowLinks.set(node, nextIndex)
    nextIndex += 1
    stack.push(node)
    onStack.add(node)
    for (const target of adjacency.get(node) ?? []) {
      if (!indexes.has(target)) {
        visit(target)
        lowLinks.set(node, Math.min(lowLinks.get(node)!, lowLinks.get(target)!))
      } else if (onStack.has(target)) {
        lowLinks.set(node, Math.min(lowLinks.get(node)!, indexes.get(target)!))
      }
    }
    if (lowLinks.get(node) !== indexes.get(node)) return
    const component: string[] = []
    while (stack.length) {
      const member = stack.pop()!
      onStack.delete(member)
      component.push(member)
      if (member === node) break
    }
    if (component.length > 1) components.push(component.sort())
  }

  for (const node of [...nodes].sort()) if (!indexes.has(node)) visit(node)
  return components.sort((left, right) => left.join(',').localeCompare(right.join(',')))
}

function scanJava(source: RepositorySource): ArchitectureInventory['backend'] {
  const javaFiles = source.paths.filter((path) => path.startsWith(JAVA_MODULE_ROOT) && path.endsWith('.java'))
  const modules = [...new Set(javaFiles.map((path) => moduleFromPath(path, JAVA_MODULE_ROOT)!).filter(Boolean))].sort()
  const moduleFileCounts = Object.fromEntries(modules.map((module) => [module, javaFiles.filter((path) => moduleFromPath(path, JAVA_MODULE_ROOT) === module).length]))
  const imports: DependencyImport[] = []
  const infrastructureFiles = new Set<string>()
  const crossFiles = new Set<string>()

  for (const path of javaFiles) {
    const content = source.read(path)
    const sourceModule = moduleFromPath(path, JAVA_MODULE_ROOT)!
    for (const match of content.matchAll(/^\s*import\s+(static\s+)?([A-Za-z_][\w.*]+)\s*;/gm)) {
      const targetPackage = match[2]
      const target = targetPackage.match(/^com\.colla\.platform\.modules\.([^.]+)(?:\.|$)/)
      if (!target || target[1] === sourceModule) continue
      const targetModule = target[1]
      const targetLayer = layerFromPackage(targetPackage, targetModule)
      imports.push({ sourceFile: path, sourceModule, targetModule, targetPackage, targetLayer, kind: match[1] ? 'static' : 'import' })
      crossFiles.add(path)
      if (targetLayer === 'infrastructure') infrastructureFiles.add(path)
    }
  }

  const sharedImports: DependencyImport[] = []
  for (const path of source.paths.filter((item) => item.startsWith(JAVA_SHARED_ROOT) && item.endsWith('.java'))) {
    const content = source.read(path)
    for (const match of content.matchAll(/^\s*import\s+(static\s+)?([A-Za-z_][\w.*]+)\s*;/gm)) {
      const targetPackage = match[2]
      const target = targetPackage.match(/^com\.colla\.platform\.modules\.([^.]+)(?:\.|$)/)
      if (!target) continue
      sharedImports.push({
        sourceFile: path,
        sourceModule: 'shared',
        targetModule: target[1],
        targetPackage,
        targetLayer: layerFromPackage(targetPackage, target[1]),
        kind: match[1] ? 'static' : 'import',
      })
    }
  }

  imports.sort((left, right) => left.sourceFile.localeCompare(right.sourceFile) || left.targetPackage.localeCompare(right.targetPackage))
  sharedImports.sort((left, right) => left.sourceFile.localeCompare(right.sourceFile) || left.targetPackage.localeCompare(right.targetPackage))
  const edges = groupEdges(imports)
  const transactional = javaFiles.filter((path) => {
    if (!path.includes('/application/') || !infrastructureFiles.has(path)) return false
    return /@Transactional\b/.test(source.read(path))
  }).sort()

  return {
    modules,
    moduleFileCounts,
    javaFiles: javaFiles.length,
    crossModuleImportCount: imports.length,
    crossModuleFileCount: crossFiles.size,
    foreignInfrastructureImportCount: imports.filter((item) => item.targetLayer === 'infrastructure').length,
    foreignInfrastructureFileCount: infrastructureFiles.size,
    directedEdgeCount: edges.length,
    crossModuleImports: imports,
    edges,
    stronglyConnectedComponents: stronglyConnectedComponents(modules, edges),
    transactionalForeignInfrastructureFiles: transactional,
    sharedToModuleImports: sharedImports,
    foreignPrivateImports: imports.filter((item) => item.targetLayer !== 'contract'),
  }
}

interface AliasRule {
  prefix: string
  suffix: string
  targets: string[]
}

function aliasRules(source: RepositorySource): AliasRule[] {
  const rules: AliasRule[] = []
  for (const path of source.paths.filter((item) => /^web\/tsconfig(?:\.[^/]+)?\.json$/.test(item))) {
    const parsed = ts.parseConfigFileTextToJson(path, source.read(path))
    const compiler = parsed.config?.compilerOptions
    if (!compiler?.paths) continue
    const base = normalizePath(posix.join(posix.dirname(path), compiler.baseUrl ?? '.'))
    for (const [pattern, rawTargets] of Object.entries(compiler.paths as Record<string, string[]>)) {
      const marker = pattern.indexOf('*')
      rules.push({
        prefix: marker >= 0 ? pattern.slice(0, marker) : pattern,
        suffix: marker >= 0 ? pattern.slice(marker + 1) : '',
        targets: rawTargets.map((target) => normalizePath(posix.join(base, target))),
      })
    }
  }
  return rules.sort((left, right) => right.prefix.length - left.prefix.length)
}

function resolveSourcePath(source: RepositorySource, fromPath: string, specifier: string, aliases: AliasRule[]): string | undefined {
  const roots: string[] = []
  if (specifier.startsWith('.')) roots.push(normalizePath(posix.join(posix.dirname(fromPath), specifier)))
  for (const rule of aliases) {
    if (!specifier.startsWith(rule.prefix) || !specifier.endsWith(rule.suffix)) continue
    const middle = specifier.slice(rule.prefix.length, specifier.length - rule.suffix.length || undefined)
    for (const target of rule.targets) roots.push(normalizePath(target.replace('*', middle)))
  }
  if (specifier.startsWith('@/')) roots.push(`web/src/${specifier.slice(2)}`)

  for (const root of roots) {
    const extension = extname(root)
    const extensionless = extension === '.js' || extension === '.jsx' ? root.slice(0, -extension.length) : root
    const candidates = [
      root,
      `${extensionless}.ts`,
      `${extensionless}.tsx`,
      `${extensionless}.js`,
      `${extensionless}.jsx`,
      `${extensionless}/index.ts`,
      `${extensionless}/index.tsx`,
      `${extensionless}/index.js`,
      `${extensionless}/index.jsx`,
    ]
    const resolved = candidates.find((candidate) => source.exists(candidate))
    if (resolved) return resolved
  }
  return undefined
}

function moduleReferences(path: string, content: string): Array<{ specifier: string; kind: string }> {
  const scriptKind = path.endsWith('.tsx') ? ts.ScriptKind.TSX : path.endsWith('.jsx') ? ts.ScriptKind.JSX : path.endsWith('.js') ? ts.ScriptKind.JS : ts.ScriptKind.TS
  const file = ts.createSourceFile(path, content, ts.ScriptTarget.Latest, true, scriptKind)
  const result: Array<{ specifier: string; kind: string }> = []
  const visit = (node: ts.Node): void => {
    if (ts.isImportDeclaration(node) && ts.isStringLiteralLike(node.moduleSpecifier)) {
      result.push({ specifier: node.moduleSpecifier.text, kind: 'import' })
    } else if (ts.isExportDeclaration(node) && node.moduleSpecifier && ts.isStringLiteralLike(node.moduleSpecifier)) {
      result.push({ specifier: node.moduleSpecifier.text, kind: 're-export' })
    } else if (ts.isCallExpression(node) && node.arguments.length === 1 && ts.isStringLiteralLike(node.arguments[0])) {
      if (node.expression.kind === ts.SyntaxKind.ImportKeyword) result.push({ specifier: node.arguments[0].text, kind: 'dynamic-import' })
      else if (ts.isIdentifier(node.expression) && node.expression.text === 'require') result.push({ specifier: node.arguments[0].text, kind: 'require' })
      else if (
        ts.isElementAccessExpression(node.expression)
        && ts.isStringLiteralLike(node.expression.argumentExpression)
        && node.expression.argumentExpression.text === 'require'
      ) result.push({ specifier: node.arguments[0].text, kind: 'bracket-require' })
    }
    ts.forEachChild(node, visit)
  }
  visit(file)
  return result
}

function scanFrontend(source: RepositorySource): ArchitectureInventory['frontend'] {
  const files = source.paths.filter((path) => path.startsWith(FRONTEND_MODULE_ROOT) && SOURCE_EXTENSIONS.has(extname(path)))
  const features = [...new Set(files.map((path) => moduleFromPath(path, FRONTEND_MODULE_ROOT)!).filter(Boolean))].sort()
  const aliases = aliasRules(source)
  const imports: DependencyImport[] = []
  for (const path of files) {
    const sourceModule = moduleFromPath(path, FRONTEND_MODULE_ROOT)!
    for (const reference of moduleReferences(path, source.read(path))) {
      const targetPath = resolveSourcePath(source, path, reference.specifier, aliases)
      const targetModule = targetPath ? moduleFromPath(targetPath, FRONTEND_MODULE_ROOT) : undefined
      if (!targetPath || !targetModule || targetModule === sourceModule) continue
      imports.push({
        sourceFile: path,
        sourceModule,
        targetModule,
        targetPackage: targetPath,
        targetLayer: targetPath.slice(`${FRONTEND_MODULE_ROOT}${targetModule}/`.length).split('/')[0] || 'root',
        kind: reference.kind,
      })
    }
  }
  imports.sort((left, right) => left.sourceFile.localeCompare(right.sourceFile) || left.targetPackage.localeCompare(right.targetPackage) || left.kind.localeCompare(right.kind))
  const edges = groupEdges(imports)
  return {
    features,
    sourceFiles: files.length,
    crossFeatureImportCount: imports.length,
    crossFeatureFileCount: new Set(imports.map((item) => item.sourceFile)).size,
    directedEdgeCount: edges.length,
    crossFeatureImports: imports,
    edges,
    stronglyConnectedComponents: stronglyConnectedComponents(features, edges),
  }
}

function stripSqlComments(content: string): string {
  return content.replace(/\/\*[\s\S]*?\*\//g, (value) => value.replace(/[^\n]/g, ' ')).replace(/--[^\n]*/g, '')
}

function tableName(raw: string): string {
  return raw.replaceAll('"', '').split('.').at(-1)!.toLowerCase()
}

function candidateOwner(table: string): string {
  const rules: Array<[string, RegExp]> = [
    ['knowledge', /^knowledge_/],
    ['project', /^(project_|projects$|issues$|issue_|iterations$)/],
    ['permission', /^(roles$|permissions$|role_|user_roles$|user_role_|resource_permission|permission_request)/],
    ['identity', /^(workspaces$|users$|user_|sessions$|devices$|departments$|department_|organization_|password_|push_tokens$)/],
    ['platform', /^(platform_|object_type_|object_links$|object_recent_accesses$|object_favorites$|recent_accesses$|favorites$)/],
    ['base', /^(bases$|base_)/],
    ['im', /^(conversations$|conversation_|messages$|message_)/],
    ['approval', /^approval_/],
    ['file', /^(files$|file_)/],
    ['notification', /^(notifications$|notification_)/],
    ['event', /^domain_events$/],
    ['audit', /^audit_logs$/],
    ['search', /^search_index_entries$/],
  ]
  return rules.find(([, pattern]) => pattern.test(table))?.[0] ?? 'unresolved'
}

function migrationVersion(path: string): number {
  const match = posix.basename(path).match(/^V(\d+)__/)
  return match ? Number(match[1]) : Number.MAX_SAFE_INTEGER
}

function scanMigrations(source: RepositorySource): Pick<ArchitectureInventory['database'], 'migrations' | 'migrationFiles' | 'activeTableCount' | 'activeTables' | 'ddlEvents'> {
  const migrationFiles = source.paths
    .filter((path) => path.startsWith(MIGRATION_ROOT) && /^V\d+__.+\.sql$/.test(posix.basename(path)))
    .sort((left, right) => migrationVersion(left) - migrationVersion(right) || left.localeCompare(right))
  const active = new Map<string, { table: string; createdIn: string; lastChangedIn: string }>()
  const ddlEvents: ArchitectureInventory['database']['ddlEvents'] = []

  for (const path of migrationFiles) {
    const content = stripSqlComments(source.read(path))
    const events: Array<{ index: number; operation: 'create' | 'rename' | 'drop'; table: string; target?: string }> = []
    for (const match of content.matchAll(/\bCREATE\s+(?:UNLOGGED\s+)?TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([A-Za-z_"][\w."]*)/gi)) {
      events.push({ index: match.index!, operation: 'create', table: tableName(match[1]) })
    }
    for (const match of content.matchAll(/\bALTER\s+TABLE\s+(?:IF\s+EXISTS\s+)?([A-Za-z_"][\w."]*)\s+RENAME\s+TO\s+([A-Za-z_"][\w."]*)/gi)) {
      events.push({ index: match.index!, operation: 'rename', table: tableName(match[1]), target: tableName(match[2]) })
    }
    for (const match of content.matchAll(/\bDROP\s+TABLE\s+(?:IF\s+EXISTS\s+)?([A-Za-z_"][\w."]*)/gi)) {
      events.push({ index: match.index!, operation: 'drop', table: tableName(match[1]) })
    }
    for (const event of events.sort((left, right) => left.index - right.index)) {
      ddlEvents.push({
        migration: path,
        operation: event.operation,
        table: event.table,
        ...(event.target ? { target: event.target } : {}),
        line: lineNumber(content, event.index),
      })
      if (event.operation === 'create') {
        active.set(event.table, { table: event.table, createdIn: path, lastChangedIn: path })
      } else if (event.operation === 'rename') {
        const previous = active.get(event.table)
        active.delete(event.table)
        active.set(event.target!, { table: event.target!, createdIn: previous?.createdIn ?? path, lastChangedIn: path })
      } else {
        active.delete(event.table)
      }
    }
  }

  const activeTables = [...active.values()].sort((left, right) => left.table.localeCompare(right.table)).map((item) => {
    const owner = candidateOwner(item.table)
    return { ...item, candidateOwner: owner, ownerStatus: owner === 'unresolved' ? 'unresolved' as const : 'candidate' as const }
  })
  return { migrations: migrationFiles.length, migrationFiles, activeTableCount: activeTables.length, activeTables, ddlEvents }
}

function sqlMode(keyword: string): 'read' | 'write' | 'ddl' {
  const normalized = keyword.toLowerCase().replace(/\s+/g, ' ')
  if (normalized === 'from' || normalized === 'join') return 'read'
  if (normalized.startsWith('create') || normalized.startsWith('alter') || normalized.startsWith('drop') || normalized.startsWith('truncate')) return 'ddl'
  return 'write'
}

function maskJavaComments(content: string): string {
  let result = ''
  let index = 0
  let state: 'code' | 'line-comment' | 'block-comment' | 'string' | 'character' | 'text-block' = 'code'
  while (index < content.length) {
    const current = content[index]
    const next = content[index + 1]
    const triple = content.slice(index, index + 3)
    if (state === 'code') {
      if (current === '/' && next === '/') {
        result += '  '
        index += 2
        state = 'line-comment'
      } else if (current === '/' && next === '*') {
        result += '  '
        index += 2
        state = 'block-comment'
      } else if (triple === '"""') {
        result += triple
        index += 3
        state = 'text-block'
      } else {
        result += current
        index += 1
        if (current === '"') state = 'string'
        else if (current === "'") state = 'character'
      }
    } else if (state === 'line-comment') {
      result += current === '\n' ? '\n' : ' '
      index += 1
      if (current === '\n') state = 'code'
    } else if (state === 'block-comment') {
      if (current === '*' && next === '/') {
        result += '  '
        index += 2
        state = 'code'
      } else {
        result += current === '\n' ? '\n' : ' '
        index += 1
      }
    } else if (state === 'text-block') {
      if (triple === '"""') {
        result += triple
        index += 3
        state = 'code'
      } else {
        result += current
        index += 1
      }
    } else {
      result += current
      index += 1
      if (current === '\\' && index < content.length) {
        result += content[index]
        index += 1
      } else if ((state === 'string' && current === '"') || (state === 'character' && current === "'")) {
        state = 'code'
      }
    }
  }
  return result
}

function scanSql(source: RepositorySource, tables: ArchitectureInventory['database']['activeTables']): Pick<ArchitectureInventory['database'], 'sqlAccesses' | 'crossOwnerCandidates' | 'crossOwnerCandidateCount' | 'crossOwnerFileCount' | 'accessModeCounts' | 'dynamicSqlCandidates'> {
  const knownTables = new Map(tables.map((item) => [item.table, item.candidateOwner]))
  const accesses: SqlAccess[] = []
  const dynamicSqlCandidates: Array<{ sourceFile: string; reason: string }> = []
  const javaFiles = source.paths.filter((path) => path.startsWith(JAVA_MODULE_ROOT) && path.endsWith('.java'))
  const accessPattern = /\b(delete\s+from|insert\s+into|update|from|join|truncate(?:\s+table)?|alter\s+table|create\s+table|drop\s+table)\s+([A-Za-z_"][\w."]*)/gi

  for (const path of javaFiles) {
    const content = source.read(path)
    const sqlContent = maskJavaComments(content)
    const sourceModule = moduleFromPath(path, JAVA_MODULE_ROOT)!
    for (const match of sqlContent.matchAll(accessPattern)) {
      const table = tableName(match[2])
      const owner = knownTables.get(table)
      if (!owner) continue
      const mode = sqlMode(match[1])
      accesses.push({
        sourceFile: path,
        sourceModule,
        table,
        tableOwner: owner,
        mode,
        keyword: match[1].toLowerCase().replace(/\s+/g, ' '),
        line: lineNumber(sqlContent, match.index!),
        crossOwner: owner !== 'unresolved' && owner !== sourceModule,
      })
    }
    const hasSqlKeyword = /\b(?:select|insert|update|delete|from|join)\b/i.test(content)
    if (hasSqlKeyword && /(?:StringBuilder|\.append\s*\(|\bString\.format\s*\(|\.formatted\s*\(|"""[\s\S]{0,500}\+|\+\s*[A-Za-z_][\w.]*)/.test(content)) {
      dynamicSqlCandidates.push({ sourceFile: path, reason: 'SQL-bearing source contains concatenation, formatting, or builder construction and requires manual review' })
    }
  }

  accesses.sort((left, right) => left.sourceFile.localeCompare(right.sourceFile) || left.line - right.line || left.table.localeCompare(right.table))
  const crossOwner = accesses.filter((item) => item.crossOwner)
  const groupedCandidates = new Map<string, SqlCandidate>()
  for (const access of crossOwner) {
    const key = `${access.sourceFile}\0${access.table}`
    const candidate = groupedCandidates.get(key) ?? {
      sourceFile: access.sourceFile,
      sourceModule: access.sourceModule,
      table: access.table,
      tableOwner: access.tableOwner,
      modes: [],
      lines: [],
      occurrenceCount: 0,
    }
    if (!candidate.modes.includes(access.mode)) candidate.modes.push(access.mode)
    if (!candidate.lines.includes(access.line)) candidate.lines.push(access.line)
    candidate.occurrenceCount += 1
    groupedCandidates.set(key, candidate)
  }
  const crossOwnerCandidates = [...groupedCandidates.values()].map((candidate) => ({
    ...candidate,
    modes: candidate.modes.sort(),
    lines: candidate.lines.sort((left, right) => left - right),
  })).sort((left, right) => left.sourceFile.localeCompare(right.sourceFile) || left.table.localeCompare(right.table))
  return {
    sqlAccesses: accesses,
    crossOwnerCandidates,
    crossOwnerCandidateCount: crossOwnerCandidates.length,
    crossOwnerFileCount: new Set(crossOwnerCandidates.map((item) => item.sourceFile)).size,
    accessModeCounts: {
      read: accesses.filter((item) => item.mode === 'read').length,
      write: accesses.filter((item) => item.mode === 'write').length,
      ddl: accesses.filter((item) => item.mode === 'ddl').length,
    },
    dynamicSqlCandidates: dynamicSqlCandidates.sort((left, right) => left.sourceFile.localeCompare(right.sourceFile)),
  }
}

function className(content: string, path: string): string {
  return content.match(/\bclass\s+([A-Za-z_]\w*)/)?.[1] ?? posix.basename(path, '.java')
}

function scanRuntime(source: RepositorySource): ArchitectureInventory['runtime'] {
  const scheduledTasks: ArchitectureInventory['runtime']['scheduledTasks'] = []
  const inMemoryState: ArchitectureInventory['runtime']['inMemoryState'] = []
  const javaFiles = source.paths.filter((path) => path.startsWith('server/src/main/java/') && path.endsWith('.java'))
  for (const path of javaFiles) {
    const content = source.read(path)
    for (const match of content.matchAll(/@Scheduled\s*\(([\s\S]*?)\)\s*(?:@[A-Za-z_][\w.]*(?:\([^)]*\))?\s*)*(?:(?:public|protected|private|final|synchronized|static)\s+)*[\w<>,.?@\[\] ]+\s+([A-Za-z_]\w*)\s*\(/g)) {
      scheduledTasks.push({ sourceFile: path, className: className(content, path), method: match[2], schedule: match[1].replace(/\s+/g, ' ').trim(), line: lineNumber(content, match.index!) })
    }
    for (const match of content.matchAll(/new\s+ConcurrentHashMap\s*<[^;]*|ConcurrentHashMap\.newKeySet\s*\([^)]*\)|Collections\.synchronized(?:Map|Set|List)\s*\(/g)) {
      inMemoryState.push({ sourceFile: path, pattern: match[0].replace(/\s+/g, ' ').slice(0, 160), line: lineNumber(content, match.index!) })
    }
  }
  scheduledTasks.sort((left, right) => left.sourceFile.localeCompare(right.sourceFile) || left.line - right.line)
  inMemoryState.sort((left, right) => left.sourceFile.localeCompare(right.sourceFile) || left.line - right.line)

  const composePath = 'deploy/docker-compose.prod.yml'
  const productionServices: ArchitectureInventory['runtime']['productionServices'] = []
  if (source.exists(composePath)) {
    const lines = source.read(composePath).split(/\r?\n/)
    let insideServices = false
    for (let index = 0; index < lines.length; index += 1) {
      const line = lines[index]
      if (line === 'services:') { insideServices = true; continue }
      if (insideServices && /^\S/.test(line) && line.trim()) break
      const service = insideServices ? line.match(/^  ([A-Za-z0-9_-]+):\s*$/)?.[1] : undefined
      if (!service) continue
      let image = ''
      for (let cursor = index + 1; cursor < lines.length && !/^  [A-Za-z0-9_-]+:\s*$/.test(lines[cursor]) && !/^\S/.test(lines[cursor]); cursor += 1) {
        const imageMatch = lines[cursor].match(/^\s{4}image:\s*(.+)$/)
        if (imageMatch) image = imageMatch[1].trim()
      }
      const role = service.startsWith('collaboration-') ? 'collaboration' : service === 'server' ? 'api+worker+event-gateway+legacy-collaboration' : 'infrastructure'
      productionServices.push({ service, image, role })
    }
  }

  return {
    scheduledTasks,
    inMemoryState,
    productionServices,
    roles: [
      {
        role: 'api',
        currentDeployment: 'server',
        responsibility: 'HTTP API plus transaction commands and queries; currently co-hosts worker, general WebSocket, and legacy collaboration schedules',
        factSource: 'PostgreSQL/MinIO with Redis integration',
        evidence: ['deploy/docker-compose.prod.yml', 'server/src/main/java'],
      },
      {
        role: 'worker',
        currentDeployment: 'server',
        responsibility: 'Domain event polling and hard-coded notification/search consumers run as an unconditional scheduled bean',
        factSource: 'PostgreSQL domain_events',
        evidence: ['server/src/main/java/com/colla/platform/modules/event/application/DomainEventWorker.java'],
      },
      {
        role: 'event-gateway',
        currentDeployment: 'server',
        responsibility: 'General /ws/events sessions are process-local and message delivery targets the local registry',
        factSource: 'PostgreSQL/REST facts; process-local sessions',
        evidence: ['server/src/main/java/com/colla/platform/shared/websocket/WebSocketSessionRegistry.java'],
      },
      {
        role: 'legacy-collaboration',
        currentDeployment: 'server',
        responsibility: 'Knowledge rooms, dirty snapshots, presence, autosave, and cleanup remain in Spring memory',
        factSource: 'Process memory with PostgreSQL persistence',
        evidence: ['server/src/main/java/com/colla/platform/modules/knowledge/application/KnowledgeContentCollaborationService.java'],
      },
      {
        role: 'collaboration',
        currentDeployment: 'collaboration-a, collaboration-b',
        responsibility: 'Hocuspocus/Yjs collaboration runs as two independently deployed nodes with Redis fanout and durable backend persistence',
        factSource: 'PostgreSQL durable updates; Redis ephemeral fanout',
        evidence: ['deploy/docker-compose.prod.yml', 'collaboration/src'],
      },
    ],
    factSources: [
      { store: 'PostgreSQL', role: 'Business facts, outbox, audit, search projection, and collaboration durable state', evidence: ['server/src/main/resources/db/migration', 'deploy/docker-compose.prod.yml'] },
      { store: 'Redis', role: 'Ephemeral collaboration fanout and coordination; not the sole business fact', evidence: ['collaboration/src', 'deploy/docker-compose.prod.yml'] },
      { store: 'MinIO', role: 'File object bytes; file metadata and access facts remain in PostgreSQL', evidence: ['server/src/main/java/com/colla/platform/modules/file', 'deploy/docker-compose.prod.yml'] },
      { store: 'Process memory', role: 'General WebSocket sessions and legacy Spring collaboration room/presence state', evidence: ['server/src/main/java/com/colla/platform/shared/websocket', 'server/src/main/java/com/colla/platform/modules/knowledge/application/KnowledgeContentCollaborationService.java'] },
    ],
  }
}

function compare(current: ArchitectureInventory, baseline: ArchitectureInventory): ArchitectureInventory['comparison'] {
  return {
    ref: baseline.source.ref,
    backend: {
      javaFiles: current.backend.javaFiles - baseline.backend.javaFiles,
      crossModuleImports: current.backend.crossModuleImportCount - baseline.backend.crossModuleImportCount,
      crossModuleFiles: current.backend.crossModuleFileCount - baseline.backend.crossModuleFileCount,
      foreignInfrastructureImports: current.backend.foreignInfrastructureImportCount - baseline.backend.foreignInfrastructureImportCount,
      foreignInfrastructureFiles: current.backend.foreignInfrastructureFileCount - baseline.backend.foreignInfrastructureFileCount,
      directedEdges: current.backend.directedEdgeCount - baseline.backend.directedEdgeCount,
      transactionalForeignInfrastructureFiles: current.backend.transactionalForeignInfrastructureFiles.length - baseline.backend.transactionalForeignInfrastructureFiles.length,
    },
    frontend: {
      sourceFiles: current.frontend.sourceFiles - baseline.frontend.sourceFiles,
      crossFeatureImports: current.frontend.crossFeatureImportCount - baseline.frontend.crossFeatureImportCount,
      crossFeatureFiles: current.frontend.crossFeatureFileCount - baseline.frontend.crossFeatureFileCount,
      directedEdges: current.frontend.directedEdgeCount - baseline.frontend.directedEdgeCount,
    },
    database: {
      migrations: current.database.migrations - baseline.database.migrations,
      activeTables: current.database.activeTableCount - baseline.database.activeTableCount,
      crossOwnerCandidates: current.database.crossOwnerCandidateCount - baseline.database.crossOwnerCandidateCount,
      crossOwnerFiles: current.database.crossOwnerFileCount - baseline.database.crossOwnerFileCount,
    },
  }
}

export function scanArchitecture(root: string, options: ScanOptions = {}): ArchitectureInventory {
  const source = repositorySource(root, options.ref)
  const backend = scanJava(source)
  const frontend = scanFrontend(source)
  const migrations = scanMigrations(source)
  const sql = scanSql(source, migrations.activeTables)
  const inventory: ArchitectureInventory = {
    schemaVersion: 1,
    source: { ref: source.ref, pathFormat: 'repository-relative-posix' },
    semantics: {
      java: 'Count one Java file per .java path under the module root and one dependency per source import declaration whose source and target module differ.',
      frontend: 'Parse TS/TSX/JS/JSX with the TypeScript AST; resolve relative, tsconfig paths, @/, extensions, and index files; count one dependency per resolved reference.',
      flyway: 'Apply V<number> migrations in numeric order and interpret CREATE TABLE, ALTER TABLE RENAME TO, and DROP TABLE to derive the effective table set.',
      sql: 'Match active table names after common SQL read/write/DDL keywords in main Java module sources; compare candidate table owner with the source module.',
      deduplication: 'Files, modules, features, and directed edges are unique sets. Import counts retain each source declaration. SQL accesses retain occurrences, while cross-owner candidates are unique by source file plus target table and aggregate modes/lines.',
      limitations: [
        'Candidate owners are prefix heuristics until the S01-M2 owner manifest is approved.',
        'Dynamic SQL, stored functions, ORM-derived access, reflection, and runtime-generated module paths remain explicit manual-review candidates.',
        'Runtime role responsibility combines machine locations with reviewed architecture interpretation and is not a capacity claim.',
      ],
    },
    backend,
    frontend,
    database: { ...migrations, ...sql },
    runtime: scanRuntime(source),
  }
  if (options.compareRef) inventory.comparison = compare(inventory, scanArchitecture(root, { ref: options.compareRef }))
  return inventory
}

function markdownTable(rows: string[][]): string {
  if (!rows.length) return '_None_'
  const width = rows[0].length
  return [
    `| ${rows[0].join(' | ')} |`,
    `| ${Array.from({ length: width }, () => '---').join(' | ')} |`,
    ...rows.slice(1).map((row) => `| ${row.join(' | ')} |`),
  ].join('\n')
}

export function renderArchitectureInventory(inventory: ArchitectureInventory): string {
  const comparisonRows = inventory.comparison
    ? Object.entries(inventory.comparison.backend).map(([metric, value]) => [metric, String(value)])
    : []
  return [
    '# Architecture Inventory',
    '',
    `- schemaVersion: ${inventory.schemaVersion}`,
    `- source: ${inventory.source.ref}`,
    `- path format: ${inventory.source.pathFormat}`,
    '',
    '## Backend',
    '',
    markdownTable([
      ['Metric', 'Value'],
      ['modules', String(inventory.backend.modules.length)],
      ['Java files', String(inventory.backend.javaFiles)],
      ['cross-module imports', String(inventory.backend.crossModuleImportCount)],
      ['cross-module files', String(inventory.backend.crossModuleFileCount)],
      ['foreign infrastructure imports', String(inventory.backend.foreignInfrastructureImportCount)],
      ['foreign infrastructure files', String(inventory.backend.foreignInfrastructureFileCount)],
      ['directed edges', String(inventory.backend.directedEdgeCount)],
      ['transactional foreign-infrastructure files', String(inventory.backend.transactionalForeignInfrastructureFiles.length)],
      ['shared to module imports', String(inventory.backend.sharedToModuleImports.length)],
    ]),
    '',
    '### Backend SCC',
    '',
    inventory.backend.stronglyConnectedComponents.map((component) => `- ${component.join(', ')}`).join('\n') || '_None_',
    '',
    '### Top foreign infrastructure edges',
    '',
    markdownTable([
      ['Source', 'Target', 'Imports'],
      ...inventory.backend.edges.filter((edge) => edge.infrastructureCount).sort((left, right) => right.infrastructureCount - left.infrastructureCount || left.source.localeCompare(right.source)).slice(0, 20).map((edge) => [edge.source, edge.target, String(edge.infrastructureCount)]),
    ]),
    '',
    '## Frontend',
    '',
    markdownTable([
      ['Metric', 'Value'],
      ['features', String(inventory.frontend.features.length)],
      ['source files', String(inventory.frontend.sourceFiles)],
      ['cross-feature imports', String(inventory.frontend.crossFeatureImportCount)],
      ['cross-feature files', String(inventory.frontend.crossFeatureFileCount)],
      ['directed edges', String(inventory.frontend.directedEdgeCount)],
    ]),
    '',
    '### Frontend SCC',
    '',
    inventory.frontend.stronglyConnectedComponents.map((component) => `- ${component.join(', ')}`).join('\n') || '_None_',
    '',
    '## Database',
    '',
    markdownTable([
      ['Metric', 'Value'],
      ['migrations', String(inventory.database.migrations)],
      ['active tables', String(inventory.database.activeTableCount)],
      ['unresolved candidate owners', String(inventory.database.activeTables.filter((table) => table.ownerStatus === 'unresolved').length)],
      ['SQL accesses', String(inventory.database.sqlAccesses.length)],
      ['cross-owner candidates', String(inventory.database.crossOwnerCandidateCount)],
      ['cross-owner files', String(inventory.database.crossOwnerFileCount)],
      ['dynamic SQL candidates', String(inventory.database.dynamicSqlCandidates.length)],
    ]),
    '',
    '## Runtime',
    '',
    markdownTable([
      ['Metric', 'Value'],
      ['scheduled tasks', String(inventory.runtime.scheduledTasks.length)],
      ['in-memory state locations', String(inventory.runtime.inMemoryState.length)],
      ['production services', String(inventory.runtime.productionServices.length)],
    ]),
    '',
    ...(inventory.comparison ? [
      `## Comparison with ${inventory.comparison.ref}`,
      '',
      markdownTable([['Backend metric', 'Delta'], ...comparisonRows]),
      '',
    ] : []),
    '## Counting semantics',
    '',
    `- Java: ${inventory.semantics.java}`,
    `- Frontend: ${inventory.semantics.frontend}`,
    `- Flyway: ${inventory.semantics.flyway}`,
    `- SQL: ${inventory.semantics.sql}`,
    `- Deduplication: ${inventory.semantics.deduplication}`,
    ...inventory.semantics.limitations.map((item) => `- Limitation: ${item}`),
    '',
  ].join('\n')
}

export function writeArchitectureInventory(root: string, inventory: ArchitectureInventory, outputDirectory = '.local-reports', label = 'architecture-inventory'): InventoryOutput {
  const directory = assertWithin(root, resolve(root, outputDirectory), 'architecture inventory output directory')
  mkdirSync(directory, { recursive: true })
  const jsonPath = resolve(directory, `${label}.json`)
  const markdownPath = resolve(directory, `${label}.md`)
  writeFileSync(jsonPath, `${JSON.stringify(inventory, null, 2)}\n`, 'utf8')
  writeFileSync(markdownPath, `${renderArchitectureInventory(inventory)}\n`, 'utf8')
  return { inventory, jsonPath, markdownPath }
}

export function generateArchitectureInventory(root: string, options: ScanOptions & { outputDirectory?: string; label?: string } = {}): InventoryOutput {
  return writeArchitectureInventory(root, scanArchitecture(root, options), options.outputDirectory, options.label)
}

export function architectureMetrics(inventory: ArchitectureInventory): Record<string, unknown> {
  return {
    backend: {
      modules: inventory.backend.modules.length,
      javaFiles: inventory.backend.javaFiles,
      crossModuleImports: inventory.backend.crossModuleImportCount,
      crossModuleFiles: inventory.backend.crossModuleFileCount,
      foreignInfrastructureImports: inventory.backend.foreignInfrastructureImportCount,
      foreignInfrastructureFiles: inventory.backend.foreignInfrastructureFileCount,
      directedEdges: inventory.backend.directedEdgeCount,
      transactionalForeignInfrastructureFiles: inventory.backend.transactionalForeignInfrastructureFiles.length,
      sharedToModuleImports: inventory.backend.sharedToModuleImports.length,
      stronglyConnectedComponents: inventory.backend.stronglyConnectedComponents,
    },
    frontend: {
      features: inventory.frontend.features.length,
      sourceFiles: inventory.frontend.sourceFiles,
      crossFeatureImports: inventory.frontend.crossFeatureImportCount,
      crossFeatureFiles: inventory.frontend.crossFeatureFileCount,
      directedEdges: inventory.frontend.directedEdgeCount,
      stronglyConnectedComponents: inventory.frontend.stronglyConnectedComponents,
    },
    database: {
      migrations: inventory.database.migrations,
      activeTables: inventory.database.activeTableCount,
      unresolvedOwners: inventory.database.activeTables.filter((table) => table.ownerStatus === 'unresolved').length,
      crossOwnerCandidates: inventory.database.crossOwnerCandidateCount,
      crossOwnerFiles: inventory.database.crossOwnerFileCount,
      dynamicSqlCandidates: inventory.database.dynamicSqlCandidates.length,
    },
    runtime: {
      scheduledTasks: inventory.runtime.scheduledTasks.length,
      inMemoryStateLocations: inventory.runtime.inMemoryState.length,
      productionServices: inventory.runtime.productionServices.length,
    },
    comparison: inventory.comparison,
  }
}

function compareExpected(expected: unknown, actual: unknown, path: string, failures: string[]): void {
  if (Array.isArray(expected)) {
    if (!Array.isArray(actual) || JSON.stringify(expected) !== JSON.stringify(actual)) failures.push(`${path}: expected ${JSON.stringify(expected)}, received ${JSON.stringify(actual)}`)
    return
  }
  if (expected && typeof expected === 'object') {
    if (!actual || typeof actual !== 'object' || Array.isArray(actual)) {
      failures.push(`${path}: expected object, received ${JSON.stringify(actual)}`)
      return
    }
    for (const [key, value] of Object.entries(expected as Record<string, unknown>)) {
      compareExpected(value, (actual as Record<string, unknown>)[key], path ? `${path}.${key}` : key, failures)
    }
    return
  }
  if (expected !== actual) failures.push(`${path}: expected ${JSON.stringify(expected)}, received ${JSON.stringify(actual)}`)
}

export function assertArchitectureExpectations(root: string, expectationPath: string, inventory: ArchitectureInventory): ArchitectureExpectations {
  const path = assertWithin(root, resolve(root, expectationPath), 'architecture expectation path')
  const expectations = JSON.parse(readFileSync(path, 'utf8')) as ArchitectureExpectations
  if (expectations.schemaVersion !== inventory.schemaVersion) throw new Error(`Architecture expectation schema mismatch: expected ${expectations.schemaVersion}, inventory ${inventory.schemaVersion}`)
  if (expectations.compareRef && inventory.comparison?.ref !== expectations.compareRef) {
    throw new Error(`Architecture comparison ref mismatch: expected ${expectations.compareRef}, inventory ${inventory.comparison?.ref ?? 'none'}`)
  }
  const failures: string[] = []
  compareExpected(expectations.expected, architectureMetrics(inventory), '', failures)
  if (failures.length) throw new Error(`Architecture baseline mismatch (${failures.length})\n${failures.join('\n')}`)
  return expectations
}
