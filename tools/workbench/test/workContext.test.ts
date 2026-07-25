import assert from 'node:assert/strict'
import test from 'node:test'
import { currentWorkContextSchemaVersion, migrateWorkContext, type WorkContext } from '../src/workcycle/contracts.js'

function legacyContext(): WorkContext {
  return {
    baselineCommit: 'fixture',
    baselineChangedPaths: [],
    baselineFileSignatures: {},
    docMode: 'code-doc-report',
    startedAt: new Date().toISOString(),
    requiredDocs: [],
    workScope: { scopeValid: true, expectedTasks: ['TEST-M1-T01'], milestoneCount: 1, maxMilestonesPerCycle: 1 },
  }
}

test('migrates legacy work contexts to the current schema defaults', () => {
  const context = migrateWorkContext(legacyContext())
  assert.equal(context.schemaVersion, currentWorkContextSchemaVersion)
  assert.deepEqual(context.allowedActiveDocs, [])
  assert.equal(context.allowedReportDir, 'docs/90-reports')
  assert.equal(context.evidencePolicy?.contractVersion, 1)
})

test('rejects work contexts from a future unsupported schema', () => {
  assert.throws(
    () => migrateWorkContext({ ...legacyContext(), schemaVersion: currentWorkContextSchemaVersion + 1 }),
    /Unsupported work context schemaVersion/,
  )
})
