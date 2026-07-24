import { readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { assertWithin } from '../lib/paths.js'
import { scanArchitecture, type ArchitectureInventory } from './inventory.js'

export interface ModuleEntry {
  name: string
  owner: string
  rootPackage: string
  publicContract: string
  privatePackages: string[]
  compositionRole: string
  compositionPermission: string
  status: 'active' | 'deprecated' | 'retired'
}

export interface ModuleManifest {
  schemaVersion: 1
  contractVersion: number
  rootPackage: string
  contractPolicy: {
    publicPackage: string
    allowedKinds: string[]
    forbiddenProviderLayers: string[]
    compatibility: string
  }
  modules: ModuleEntry[]
}

export interface TableOwnerManifest {
  schemaVersion: 1
  migrationRange: string
  defaultKind: string
  owners: Record<string, string[]>
  specialTreatment: Record<string, string[]>
  rules: Record<string, string>
}

export interface BoundaryException {
  id: string
  kind: 'java-import' | 'frontend-import' | 'sql'
  sourceModule: string
  targetModule: string
  sourceFile: string
  target: string
  modes: Array<'read' | 'write' | 'ddl' | 'import'>
  reason: string
  owner: string
  introducedStage: string
  exitStage: string
  expiryDecision: 'remove' | 'replace-with-contract' | 'replace-with-projection'
  status: 'candidate' | 'approved' | 'expired'
}

export interface BoundaryExceptionManifest {
  schemaVersion: 1
  approvalPolicy: Record<string, string | string[]>
  exceptions: BoundaryException[]
}

export interface ContractCheckResult {
  modules: number
  activeTables: number
  exceptions: number
  contractFiles: number
}

function readJson<T>(root: string, path: string): T {
  const target = assertWithin(root, resolve(root, path), path)
  return JSON.parse(readFileSync(target, 'utf8')) as T
}

function exactSet(actual: string[], expected: string[], description: string): void {
  const left = [...new Set(actual)].sort()
  const right = [...new Set(expected)].sort()
  if (actual.length !== left.length) throw new Error(`${description} contains duplicate entries`)
  if (JSON.stringify(left) !== JSON.stringify(right)) {
    const missing = right.filter((item) => !left.includes(item))
    const extra = left.filter((item) => !right.includes(item))
    throw new Error(`${description} mismatch; missing=${missing.join(',') || 'none'}; extra=${extra.join(',') || 'none'}`)
  }
}

function validateModules(inventory: ArchitectureInventory, manifest: ModuleManifest): void {
  if (manifest.schemaVersion !== 1 || manifest.contractVersion < 1) throw new Error('Module manifest version is invalid')
  exactSet(manifest.modules.map((module) => module.name), inventory.backend.modules, 'Module manifest')
  for (const module of manifest.modules) {
    const prefix = `${manifest.rootPackage}.${module.name}`
    if (!module.owner || module.rootPackage !== prefix || module.publicContract !== `${prefix}.${manifest.contractPolicy.publicPackage}`) {
      throw new Error(`Module ${module.name} has incomplete owner/package metadata`)
    }
    exactSet(module.privatePackages, manifest.contractPolicy.forbiddenProviderLayers, `Private package list for ${module.name}`)
    if (!module.compositionRole || !module.compositionPermission || module.status !== 'active') {
      throw new Error(`Module ${module.name} has incomplete composition or status metadata`)
    }
  }
}

function validateTables(inventory: ArchitectureInventory, modules: Set<string>, manifest: TableOwnerManifest): void {
  if (manifest.schemaVersion !== 1) throw new Error('Table owner manifest version is invalid')
  const entries = Object.entries(manifest.owners)
  for (const [owner] of entries) if (!modules.has(owner)) throw new Error(`Unknown table owner module: ${owner}`)
  const tables = entries.flatMap(([, values]) => values)
  exactSet(tables, inventory.database.activeTables.map((table) => table.table), 'Table owner manifest')

  const active = new Set(tables)
  for (const [kind, values] of Object.entries(manifest.specialTreatment)) {
    if (!kind || !Array.isArray(values)) throw new Error('Invalid special table treatment')
    for (const table of values) if (!active.has(table) && kind !== 'retired') throw new Error(`Special treatment references unknown active table: ${table}`)
  }
  if ((manifest.specialTreatment.ownerless ?? []).length) throw new Error('Active ownerless tables are forbidden')
  for (const key of ['foreignWrite', 'foreignRead', 'foreignKey', 'schema', 'newTable']) {
    if (!manifest.rules[key]) throw new Error(`Table owner rule is missing: ${key}`)
  }
}

function validateExceptions(modules: Set<string>, manifest: BoundaryExceptionManifest): void {
  if (manifest.schemaVersion !== 1) throw new Error('Boundary exception manifest version is invalid')
  const ids = new Set<string>()
  for (const exception of manifest.exceptions) {
    if (!/^BOUNDARY-[A-Z]+-\d{3}$/.test(exception.id) || ids.has(exception.id)) throw new Error(`Invalid or duplicate boundary exception ID: ${exception.id}`)
    ids.add(exception.id)
    if (!modules.has(exception.sourceModule) || !modules.has(exception.targetModule)) throw new Error(`Boundary exception ${exception.id} references an unknown module`)
    if ([exception.sourceFile, exception.target].some((value) => !value || /[*?[\]{}]/.test(value))) throw new Error(`Boundary exception ${exception.id} must use exact file and target values`)
    if (!exception.modes.length || exception.modes.includes('write')) throw new Error(`Boundary exception ${exception.id} cannot allow foreign writes`)
    if (!exception.reason || !exception.owner || !exception.introducedStage || !exception.exitStage || !exception.expiryDecision) {
      throw new Error(`Boundary exception ${exception.id} is missing approval metadata`)
    }
    if (exception.status !== 'approved') throw new Error(`Boundary exception ${exception.id} is not approved`)
  }
}

function validateContractSources(root: string, manifest: ModuleManifest): number {
  let count = 0
  for (const module of manifest.modules) {
    const prefix = `server/src/main/java/com/colla/platform/modules/${module.name}/contract/`
    const files: string[] = []
    try {
      for (const entry of readdirSync(resolve(root, prefix), { withFileTypes: true })) {
        if (entry.isFile() && entry.name.endsWith('.java')) files.push(`${prefix}${entry.name}`)
      }
    } catch {
      // A module may expose no contract yet; the manifest still reserves the package.
    }
    for (const path of files) {
      count += 1
      const content = readFileSync(resolve(root, path), 'utf8')
      const packageName = content.match(/^\s*package\s+([\w.]+)\s*;/m)?.[1]
      if (packageName !== module.publicContract) throw new Error(`Contract file has an invalid package: ${path}`)
      for (const match of content.matchAll(/^\s*import\s+(?:static\s+)?([\w.*]+)\s*;/gm)) {
        const imported = match[1]
        const provider = imported.match(/^com\.colla\.platform\.modules\.([^.]+)\.([^.]+)/)
        if (provider && manifest.contractPolicy.forbiddenProviderLayers.includes(provider[2])) {
          throw new Error(`Contract file imports a provider private package: ${path} -> ${imported}`)
        }
      }
    }
  }
  return count
}

export function checkArchitectureContracts(
  root: string,
  paths: {
    modules?: string
    tableOwners?: string
    exceptions?: string
    architectureDocument?: string
  } = {},
): ContractCheckResult {
  const inventory = scanArchitecture(root)
  const modules = readJson<ModuleManifest>(root, paths.modules ?? 'tools/workbench/config/platform-modules.json')
  const tables = readJson<TableOwnerManifest>(root, paths.tableOwners ?? 'tools/workbench/config/platform-table-owners.json')
  const exceptions = readJson<BoundaryExceptionManifest>(root, paths.exceptions ?? 'tools/workbench/config/platform-boundary-exceptions.json')
  validateModules(inventory, modules)
  const moduleNames = new Set(modules.modules.map((module) => module.name))
  validateTables(inventory, moduleNames, tables)
  validateExceptions(moduleNames, exceptions)
  const contractFiles = validateContractSources(root, modules)

  const documentPath = assertWithin(root, resolve(root, paths.architectureDocument ?? 'docs/01-architecture/platform-module-contracts.md'), 'module contract architecture document')
  const document = readFileSync(documentPath, 'utf8')
  for (const term of ['模块', '公开合同', 'table owner', '组合查询', '流程协调器', '同步', '异步', '非目标', '最小披露', '事务']) {
    if (!document.includes(term)) throw new Error(`Module contract architecture document is missing required term: ${term}`)
  }
  return {
    modules: modules.modules.length,
    activeTables: Object.values(tables.owners).flat().length,
    exceptions: exceptions.exceptions.length,
    contractFiles,
  }
}
