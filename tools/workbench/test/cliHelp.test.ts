import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
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

test('CLI keeps non-core command domains behind dynamic imports', () => {
  const root = join(dirname(fileURLToPath(import.meta.url)), '..')
  const source = readFileSync(join(root, 'src/cli.ts'), 'utf8')
  for (const family of ['architecture', 'knowledge', 'operations', 'pilot', 'browser', 'security', 'workcycle']) {
    assert.match(source, new RegExp(`import\\(['"]\\./commands/${family}`), family)
  }
  assert.doesNotMatch(source, /^import .+['"]\.\/(?:architecture|knowledge|operations|pilot|browser|security|workcycle)\//m)
})
