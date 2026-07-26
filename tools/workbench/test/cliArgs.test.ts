import assert from 'node:assert/strict'
import test from 'node:test'
import { optionStrings, parseCliArgs } from '../src/lib/args.js'

test('CLI preserves additional equals signs in repeated inline option values', () => {
  const { options } = parseCliArgs([
    '--system-evidence-arg=-q',
    '--system-evidence-arg=-Dtest=WorkItemServiceIntegrationTests,ModuleArchitectureTests',
    '--system-evidence-arg=test',
  ])

  assert.deepEqual(optionStrings(options, 'system-evidence-arg'), [
    '-q',
    '-Dtest=WorkItemServiceIntegrationTests,ModuleArchitectureTests',
    'test',
  ])
})
