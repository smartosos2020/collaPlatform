import assert from 'node:assert/strict'
import test from 'node:test'
import { runCli } from '../src/cli.js'

test('work checkpoint help returns before command execution', async () => {
  const lines: string[] = []
  const originalLog = console.log
  console.log = (...values: unknown[]) => lines.push(values.map(String).join(' '))
  try {
    await runCli(['work', 'checkpoint', '--help'])
  } finally {
    console.log = originalLog
  }
  assert.match(lines.join('\n'), /Usage: pnpm work:checkpoint/)
})

test('root help lists the work-cycle commands', async () => {
  const lines: string[] = []
  const originalLog = console.log
  console.log = (...values: unknown[]) => lines.push(values.map(String).join(' '))
  try {
    await runCli(['--help'])
  } finally {
    console.log = originalLog
  }
  assert.match(lines.join('\n'), /work start \| work checkpoint \| work finish/)
})
