import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'
import { repositoryRoot } from '../src/lib/paths.js'
import { loadActivePlanningContract } from '../src/workcycle/planning.js'
import { dockerDependencyArgs, recordQualityGateEvidence } from '../src/workcycle/quality.js'
import { assertDocumentationStructure, assertFrontendRouteLazyLoading, assertGeneratedArtifacts, assertMockitoJavaAgent, implementationMarkers } from '../src/workcycle/staticChecks.js'

test('route contract requires lazyRoute dynamic imports and rejects static page imports', () => {
  const root = mkdtempSync(join(tmpdir(), 'colla-route-contract-'))
  const directory = join(root, 'web/src/app'); mkdirSync(directory, { recursive: true })
  const router = join(directory, 'router.tsx')
  writeFileSync(router, `const Page = lazyRoute(() => import('../modules/demo/pages/DemoPage'), 'DemoPage')`)
  assert.doesNotThrow(() => assertFrontendRouteLazyLoading(root))
  writeFileSync(router, `import { DemoPage } from '../modules/demo/pages/DemoPage'\nexport { DemoPage }`)
  assert.throws(() => assertFrontendRouteLazyLoading(root), /static import/)
})

test('Mockito, generated artifact and active documentation contracts pass for the repository', () => {
  assert.doesNotThrow(() => assertMockitoJavaAgent(repositoryRoot))
  assert.doesNotThrow(() => assertGeneratedArtifacts(repositoryRoot))
  assert.doesNotThrow(() => assertDocumentationStructure(repositoryRoot, loadActivePlanningContract(repositoryRoot)))
})

test('quality evidence is persisted into an active cycle context', () => {
  const root = mkdtempSync(join(tmpdir(), 'colla-quality-evidence-'))
  const directory = join(root, '.local-reports'); mkdirSync(directory, { recursive: true })
  const contextPath = join(directory, 'work-cycle-current.json')
  writeFileSync(contextPath, JSON.stringify({ status: 'in-progress', requiredDocs: [], workScope: { scopeValid: true, expectedTasks: [], milestoneCount: 1, maxMilestonesPerCycle: 1 } }))
  const evidence = { reportPath: 'quality.md', mode: 'stage' as const, status: 'PASS' as const, stepLogs: ['step.log'], completedAt: new Date().toISOString() }
  recordQualityGateEvidence(root, evidence)
  assert.deepEqual(JSON.parse(readFileSync(contextPath, 'utf8')).lastQualityGate, evidence)
})

test('implementation marker inventory includes untracked source files', () => {
  const root = mkdtempSync(join(tmpdir(), 'colla-marker-inventory-'))
  mkdirSync(join(root, 'src'), { recursive: true })
  writeFileSync(join(root, 'src/new-file.ts'), `// ${'TO' + 'DO'} close the contract\nexport const value = 1`)
  assert.deepEqual(implementationMarkers(root), [`src/new-file.ts:1:// ${'TO' + 'DO'} close the contract`])
})

test('Docker dependency gate waits for service health', () => {
  assert.deepEqual(dockerDependencyArgs, ['compose', 'up', '-d', '--wait', '--wait-timeout', '120', 'postgres', 'redis', 'minio'])
})
