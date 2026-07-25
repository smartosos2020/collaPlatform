import assert from 'node:assert/strict'
import { existsSync, mkdtempSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'
import { executeSystemEvidence, sha256File } from '../src/workcycle/systemEvidence.js'

test('executes isolated system evidence and records task-linked checksummed output', async () => {
  const root = mkdtempSync(join(tmpdir(), 'colla-system-evidence-'))
  const evidence = await executeSystemEvidence(root, {
    executable: process.execPath,
    args: ['-e', 'console.log("durable fact asserted")'],
    tasks: ['TEST-M1-T01'],
  })
  const logPath = join(root, evidence.logPath)
  assert.equal(evidence.environment, 'isolated')
  assert.deepEqual(evidence.tasks, ['TEST-M1-T01'])
  assert.equal(existsSync(logPath), true)
  assert.match(readFileSync(logPath, 'utf8'), /durable fact asserted/)
  assert.equal(evidence.sha256, sha256File(logPath))
})

test('rejects a system evidence cwd outside the repository', async () => {
  await assert.rejects(
    executeSystemEvidence(`${tmpdir()}\\contract-root`, {
      executable: process.execPath,
      cwd: '..',
      tasks: ['TEST-M1-T01'],
    }),
    /must remain inside the repository/,
  )
})
