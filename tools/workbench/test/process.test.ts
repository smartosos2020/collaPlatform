import assert from 'node:assert/strict'
import test from 'node:test'
import { run, runSync } from '../src/lib/process.js'

test('runs Node and package-manager launchers without a caller-owned shell command', () => {
  assert.match(runSync('node', ['--version']), /^v\d+/)
  assert.match(runSync('pnpm', ['--version']), /^\d+\.\d+/)
})

test('rejects batch environment expansion characters on Windows', { skip: process.platform !== 'win32' }, () => {
  assert.throws(() => runSync('pnpm', ['--filter', '%PATH%']), /Unsafe Windows batch argument/)
})

test('captures asynchronous command output for evidence logs', async () => {
  assert.equal(await run('node', ['-e', 'process.stdout.write("captured")'], { capture: true }), 'captured')
})
