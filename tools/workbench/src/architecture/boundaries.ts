import { mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { dirname, relative, resolve } from 'node:path'
import ts from 'typescript'
import { gitHead } from '../lib/git.js'
import { assertWithin } from '../lib/paths.js'
import { checkArchitectureContracts, type BoundaryExceptionManifest, type ModuleManifest } from './contracts.js'
import { scanArchitecture, type ArchitectureInventory, type DependencyImport, type SqlCandidate } from './inventory.js'

export interface BoundaryBaseline {
  schemaVersion: 1
  baselineId: string
  sourceCommit: string
  backend: {
    foreignPrivateImports: string[]
    sharedToModuleImports: string[]
    directedEdges: string[]
    stronglyConnectedComponents: string[][]
  }
  frontend: {
    crossFeatureImports: string[]
    sharedToFeatureImports: string[]
    directedEdges: string[]
    stronglyConnectedComponents: string[][]
  }
  database: {
    crossOwnerReads: string[]
    dynamicSqlFiles: string[]
  }
}

export interface BoundaryGateResult {
  baseline: BoundaryBaseline
  report: string
  metrics: {
    backendPrivate: number
    sharedReverse: number
    frontendImports: number
    frontendSharedReverse: number
    crossOwnerReads: number
    foreignWrites: number
  }
}

function posix(path: string): string {
  return path.replaceAll('\\', '/')
}

function walk(directory: string, root: string, result: string[]): void {
  try {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const target = resolve(directory, entry.name)
      if (entry.isDirectory()) walk(target, root, result)
      else if (entry.isFile() && /\.[cm]?[jt]sx?$/.test(entry.name)) result.push(posix(relative(root, target)))
    }
  } catch {
    // Optional source roots are empty in isolated fixtures.
  }
}

function sharedToFeatureImports(root: string): string[] {
  const sharedRoot = resolve(root, 'web/src/shared')
  const paths: string[] = []
  walk(sharedRoot, root, paths)
  const result: string[] = []
  for (const path of paths) {
    const content = readFileSync(resolve(root, path), 'utf8')
    const file = ts.createSourceFile(path, content, ts.ScriptTarget.Latest, true, path.endsWith('x') ? ts.ScriptKind.TSX : ts.ScriptKind.TS)
    const visit = (node: ts.Node): void => {
      let specifier: string | undefined
      let kind = ''
      if (ts.isImportDeclaration(node) && ts.isStringLiteralLike(node.moduleSpecifier)) {
        specifier = node.moduleSpecifier.text
        kind = 'import'
      } else if (ts.isExportDeclaration(node) && node.moduleSpecifier && ts.isStringLiteralLike(node.moduleSpecifier)) {
        specifier = node.moduleSpecifier.text
        kind = 're-export'
      } else if (ts.isCallExpression(node) && node.arguments.length === 1 && ts.isStringLiteralLike(node.arguments[0])) {
        if (node.expression.kind === ts.SyntaxKind.ImportKeyword) {
          specifier = node.arguments[0].text
          kind = 'dynamic-import'
        } else if (ts.isIdentifier(node.expression) && node.expression.text === 'require') {
          specifier = node.arguments[0].text
          kind = 'require'
        } else if (
          ts.isElementAccessExpression(node.expression)
          && ts.isStringLiteralLike(node.expression.argumentExpression)
          && node.expression.argumentExpression.text === 'require'
        ) {
          specifier = node.arguments[0].text
          kind = 'bracket-require'
        }
      }
      if (specifier) {
        const alias = specifier.match(/^@\/modules\/([^/]+)/)
        const relativeTarget = specifier.startsWith('.')
          ? posix(resolve(dirname(resolve(root, path)), specifier))
          : ''
        const marker = relativeTarget.match(/\/web\/src\/modules\/([^/]+)/)
        const feature = alias?.[1] ?? marker?.[1]
        if (feature) result.push(`${path}|${feature}|${specifier}|${kind}`)
      }
      ts.forEachChild(node, visit)
    }
    visit(file)
  }
  return [...new Set(result)].sort()
}

function importKey(item: DependencyImport): string {
  return `${item.sourceFile}|${item.sourceModule}|${item.targetModule}|${item.targetPackage}|${item.kind}`
}

function sqlKey(item: SqlCandidate): string {
  return `${item.sourceFile}|${item.sourceModule}|${item.tableOwner}|${item.table}|read`
}

function baselineFrom(root: string, inventory: ArchitectureInventory, sourceCommit = gitHead(root)): BoundaryBaseline {
  return {
    schemaVersion: 1,
    baselineId: 'PLATFORM-SCALE-S01-M3',
    sourceCommit,
    backend: {
      foreignPrivateImports: inventory.backend.foreignPrivateImports.map(importKey).sort(),
      sharedToModuleImports: inventory.backend.sharedToModuleImports.map(importKey).sort(),
      directedEdges: inventory.backend.edges.map((edge) => `${edge.source}|${edge.target}`).sort(),
      stronglyConnectedComponents: inventory.backend.stronglyConnectedComponents,
    },
    frontend: {
      crossFeatureImports: inventory.frontend.crossFeatureImports.map(importKey).sort(),
      sharedToFeatureImports: sharedToFeatureImports(root),
      directedEdges: inventory.frontend.edges.map((edge) => `${edge.source}|${edge.target}`).sort(),
      stronglyConnectedComponents: inventory.frontend.stronglyConnectedComponents,
    },
    database: {
      crossOwnerReads: inventory.database.crossOwnerCandidates
        .filter((item) => item.modes.includes('read'))
        .map(sqlKey)
        .sort(),
      dynamicSqlFiles: inventory.database.dynamicSqlCandidates.map((item) => item.sourceFile).sort(),
    },
  }
}

function compareExact(label: string, actual: string[], expected: string[], failures: string[]): void {
  const additions = actual.filter((item) => !expected.includes(item))
  const stale = expected.filter((item) => !actual.includes(item))
  if (additions.length) failures.push(`${label} additions:\n${additions.join('\n')}`)
  if (stale.length) failures.push(`${label} baseline must ratchet after removals:\n${stale.join('\n')}`)
}

function readJson<T>(root: string, path: string): T {
  return JSON.parse(readFileSync(assertWithin(root, resolve(root, path), path), 'utf8')) as T
}

function exceptionKey(item: BoundaryExceptionManifest['exceptions'][number]): string {
  return `${item.sourceFile}|${item.sourceModule}|${item.targetModule}|${item.target}|read`
}

function validateSqlExceptions(root: string, baseline: BoundaryBaseline, failures: string[]): void {
  const manifest = readJson<BoundaryExceptionManifest>(root, 'tools/workbench/config/platform-boundary-exceptions.json')
  const sqlExceptions = manifest.exceptions.filter((item) => item.kind === 'sql').map(exceptionKey).sort()
  compareExact('SQL read exception', sqlExceptions, baseline.database.crossOwnerReads, failures)
}

function render(result: BoundaryGateResult, failures: string[]): string {
  const lines = [
    '# Architecture Boundary Gate',
    '',
    `- schemaVersion: ${result.baseline.schemaVersion}`,
    `- baseline: ${result.baseline.baselineId}`,
    `- sourceCommit: ${result.baseline.sourceCommit}`,
    `- status: ${failures.length ? 'FAIL' : 'PASS'}`,
    '',
    '| Metric | Value |',
    '| --- | ---: |',
    `| backend private imports | ${result.metrics.backendPrivate} |`,
    `| shared reverse imports | ${result.metrics.sharedReverse} |`,
    `| frontend cross-feature imports | ${result.metrics.frontendImports} |`,
    `| frontend shared reverse imports | ${result.metrics.frontendSharedReverse} |`,
    `| cross-owner reads | ${result.metrics.crossOwnerReads} |`,
    `| foreign writes | ${result.metrics.foreignWrites} |`,
  ]
  if (failures.length) lines.push('', '## Failures', '', ...failures.map((failure) => `- ${failure.replaceAll('\n', '\n  ')}`))
  return `${lines.join('\n')}\n`
}

export function synchronizeSqlReadExceptions(root: string, inventory: ArchitectureInventory): number {
  const path = assertWithin(root, resolve(root, 'tools/workbench/config/platform-boundary-exceptions.json'), 'boundary exception manifest')
  const current = JSON.parse(readFileSync(path, 'utf8')) as BoundaryExceptionManifest
  const modules = readJson<ModuleManifest>(root, 'tools/workbench/config/platform-modules.json')
  const owners = new Map(modules.modules.map((module) => [module.name, module.owner]))
  const reads = inventory.database.crossOwnerCandidates.filter((item) => item.modes.includes('read'))
    .sort((left, right) => sqlKey(left).localeCompare(sqlKey(right)))
  current.exceptions = reads.map((item, index) => ({
    id: `BOUNDARY-READ-${String(index + 1).padStart(3, '0')}`,
    kind: 'sql',
    sourceModule: item.sourceModule,
    targetModule: item.tableOwner,
    sourceFile: item.sourceFile,
    target: item.table,
    modes: ['read'],
    reason: `${item.sourceModule} currently reads ${item.tableOwner} facts until an approved query contract or owned projection replaces this exact access.`,
    owner: owners.get(item.sourceModule) ?? item.sourceModule,
    introducedStage: 'PLATFORM-SCALE-S01',
    exitStage: 'PLATFORM-SCALE-S05',
    expiryDecision: item.sourceModule === 'search' ? 'replace-with-projection' : 'replace-with-contract',
    status: 'approved',
  }))
  writeFileSync(path, `${JSON.stringify(current, null, 2)}\n`)
  return current.exceptions.length
}

export function writeBoundaryBaseline(
  root: string,
  path = 'tools/workbench/config/platform-boundary-baseline.json',
  syncExceptions = false,
): BoundaryBaseline {
  const inventory = scanArchitecture(root)
  if (syncExceptions) synchronizeSqlReadExceptions(root, inventory)
  const baseline = baselineFrom(root, inventory)
  const target = assertWithin(root, resolve(root, path), 'boundary baseline')
  mkdirSync(dirname(target), { recursive: true })
  writeFileSync(target, `${JSON.stringify(baseline, null, 2)}\n`)
  return baseline
}

export function checkArchitectureBoundaries(
  root: string,
  options: { baselinePath?: string; outputDirectory?: string; label?: string } = {},
): BoundaryGateResult {
  checkArchitectureContracts(root)
  const inventory = scanArchitecture(root)
  const baseline = readJson<BoundaryBaseline>(root, options.baselinePath ?? 'tools/workbench/config/platform-boundary-baseline.json')
  if (baseline.schemaVersion !== 1) throw new Error('Boundary baseline schema version is invalid')
  const actual = baselineFrom(root, inventory, baseline.sourceCommit)
  const failures: string[] = []
  compareExact('Backend private import', actual.backend.foreignPrivateImports, baseline.backend.foreignPrivateImports, failures)
  compareExact('Shared reverse import', actual.backend.sharedToModuleImports, baseline.backend.sharedToModuleImports, failures)
  if (actual.backend.sharedToModuleImports.length) failures.push('Shared Java code must not depend on business modules')
  compareExact('Backend edge', actual.backend.directedEdges, baseline.backend.directedEdges, failures)
  if (JSON.stringify(actual.backend.stronglyConnectedComponents) !== JSON.stringify(baseline.backend.stronglyConnectedComponents)) failures.push('Backend SCC differs from the ratcheted baseline')
  compareExact('Frontend import', actual.frontend.crossFeatureImports, baseline.frontend.crossFeatureImports, failures)
  compareExact('Frontend shared reverse import', actual.frontend.sharedToFeatureImports, baseline.frontend.sharedToFeatureImports, failures)
  if (actual.frontend.sharedToFeatureImports.length) failures.push('Frontend shared code must not depend on feature modules')
  compareExact('Frontend edge', actual.frontend.directedEdges, baseline.frontend.directedEdges, failures)
  if (JSON.stringify(actual.frontend.stronglyConnectedComponents) !== JSON.stringify(baseline.frontend.stronglyConnectedComponents)) failures.push('Frontend SCC differs from the ratcheted baseline')
  compareExact('Cross-owner read', actual.database.crossOwnerReads, baseline.database.crossOwnerReads, failures)
  const writes = inventory.database.crossOwnerCandidates.filter((item) => item.modes.includes('write'))
  if (writes.length) failures.push(`Foreign writes are forbidden:\n${writes.map((item) => `${item.sourceFile}|${item.sourceModule}|${item.tableOwner}|${item.table}`).join('\n')}`)
  validateSqlExceptions(root, baseline, failures)

  const result: BoundaryGateResult = {
    baseline,
    report: '',
    metrics: {
      backendPrivate: actual.backend.foreignPrivateImports.length,
      sharedReverse: actual.backend.sharedToModuleImports.length,
      frontendImports: actual.frontend.crossFeatureImports.length,
      frontendSharedReverse: actual.frontend.sharedToFeatureImports.length,
      crossOwnerReads: actual.database.crossOwnerReads.length,
      foreignWrites: writes.length,
    },
  }
  const outputDirectory = assertWithin(root, resolve(root, options.outputDirectory ?? '.local-reports'), 'boundary output directory')
  mkdirSync(outputDirectory, { recursive: true })
  result.report = resolve(outputDirectory, `${options.label ?? 'architecture-boundaries'}.md`)
  writeFileSync(result.report, render(result, failures))
  if (failures.length) throw new Error(`Architecture boundary gate failed (${failures.length}); report=${result.report}\n${failures.join('\n')}`)
  return result
}
