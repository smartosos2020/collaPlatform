import { optionBoolean, optionString } from '../lib/args.js'
import type { CommandContext } from './types.js'

export async function runCommand({ command, options, root }: CommandContext): Promise<void> {
  if (command === 'audit snapshot') {
    const { auditSnapshot } = await import('../audit/snapshot.js')
    const profile = optionString(options, 'profile', 'full') as 'light' | 'full'
    console.log(`Audit snapshot: ${auditSnapshot(root, optionString(options, 'label', 'manual'), profile)}`)
    return
  }
  if (command === 'architecture inventory') {
    const { assertArchitectureExpectations, generateArchitectureInventory } = await import('../architecture/inventory.js')
    const expectationPath = optionString(options, 'expectation-path') || undefined
    const result = generateArchitectureInventory(root, {
      compareRef: optionString(options, 'compare-ref') || undefined,
      outputDirectory: optionString(options, 'output-dir', '.local-reports'),
      label: optionString(options, 'label', 'architecture-inventory'),
    })
    if (expectationPath) {
      const expectations = assertArchitectureExpectations(root, expectationPath, result.inventory)
      console.log(`Architecture baseline matched: ${expectations.baselineId}; source=${expectations.sourceCommit}`)
    }
    console.log(`Architecture inventory: modules=${result.inventory.backend.modules.length}; java=${result.inventory.backend.javaFiles}; backendImports=${result.inventory.backend.crossModuleImportCount}; frontendImports=${result.inventory.frontend.crossFeatureImportCount}; crossOwnerSql=${result.inventory.database.crossOwnerCandidateCount}`)
    console.log(`JSON: ${result.jsonPath}`)
    console.log(`Markdown: ${result.markdownPath}`)
    return
  }
  if (command === 'architecture contracts') {
    const { checkArchitectureContracts } = await import('../architecture/contracts.js')
    const result = checkArchitectureContracts(root, {
      modules: optionString(options, 'modules') || undefined,
      tableOwners: optionString(options, 'table-owners') || undefined,
      exceptions: optionString(options, 'exceptions') || undefined,
      architectureDocument: optionString(options, 'architecture-document') || undefined,
    })
    console.log(`Architecture contracts passed: modules=${result.modules}; activeTables=${result.activeTables}; exceptions=${result.exceptions}; contractFiles=${result.contractFiles}`)
    return
  }
  if (command === 'architecture boundaries') {
    const { checkArchitectureBoundaries, writeBoundaryBaseline } = await import('../architecture/boundaries.js')
    if (optionBoolean(options, 'write-baseline')) {
      const baseline = writeBoundaryBaseline(root, {
        path: optionString(options, 'baseline-path', 'tools/workbench/config/platform-boundary-baseline.json'),
        baselineId: optionString(options, 'baseline-id') || undefined,
        syncExceptions: optionBoolean(options, 'sync-exceptions'),
        exceptionLifecycle: optionBoolean(options, 'sync-exceptions') ? {
          introducedStage: optionString(options, 'introduced-stage'),
          exitStage: optionString(options, 'exit-stage'),
        } : undefined,
      })
      console.log(`Architecture boundary baseline written: backendPrivate=${baseline.backend.foreignPrivateImports.length}; frontendImports=${baseline.frontend.crossFeatureImports.length}; crossOwnerReads=${baseline.database.crossOwnerReads.length}`)
      return
    }
    const result = checkArchitectureBoundaries(root, {
      baselinePath: optionString(options, 'baseline-path') || undefined,
      outputDirectory: optionString(options, 'output-dir') || undefined,
      label: optionString(options, 'label') || undefined,
    })
    console.log(`Architecture boundaries passed: backendPrivate=${result.metrics.backendPrivate}; sharedReverse=${result.metrics.sharedReverse}; frontendImports=${result.metrics.frontendImports}; crossOwnerReads=${result.metrics.crossOwnerReads}`)
    console.log(`Report: ${result.report}`)
    return
  }
  throw new Error(`Unknown architecture command: ${command}`)
}
