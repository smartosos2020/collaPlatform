import assert from 'node:assert/strict'
import test from 'node:test'
import { run, runSync, spawnManaged } from '../src/lib/process.js'

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

test('starts package-manager background processes through the cross-platform launcher', async () => {
  const child = spawnManaged('pnpm', ['--version'], { stdio: ['ignore', 'pipe', 'pipe'] })
  let output = ''
  child.stdout?.on('data', (chunk) => { output += String(chunk) })
  child.stderr?.on('data', (chunk) => { output += String(chunk) })
  const code = await new Promise<number | null>((resolve, reject) => { child.on('error', reject); child.on('close', resolve) })
  assert.equal(code, 0)
  assert.match(output.trim(), /^\d+\.\d+/)
})
