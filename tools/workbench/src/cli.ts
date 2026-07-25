#!/usr/bin/env node
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { optionBoolean, parseCliArgs } from './lib/args.js'
import { commandHelp } from './help.js'
import { repositoryRoot } from './lib/paths.js'
import type { CommandModule } from './commands/types.js'

const commandFamilies: Record<string, () => Promise<CommandModule>> = {
  audit: () => import('./commands/architecture.js'),
  architecture: () => import('./commands/architecture.js'),
  browser: () => import('./commands/browser.js'),
  knowledge: () => import('./commands/knowledge.js'),
  operations: () => import('./commands/operations.js'),
  pilot: () => import('./commands/pilot.js'),
  planning: () => import('./commands/workcycle.js'),
  security: () => import('./commands/security.js'),
  verify: () => import('./commands/workcycle.js'),
  work: () => import('./commands/workcycle.js'),
}

export async function runCli(argv = process.argv.slice(2)): Promise<void> {
  const { positionals, options } = parseCliArgs(argv)
  const command = positionals.join(' ')
  if (optionBoolean(options, 'help')) {
    console.log(commandHelp(command))
    return
  }
  const family = commandFamilies[positionals[0] ?? '']
  if (family) {
    const module = await family()
    await module.runCommand({ command, options, root: repositoryRoot })
    return
  }
  throw new Error(`Unknown workbench command: ${command || '(none)'}`)
}

if (resolve(process.argv[1] ?? '') === fileURLToPath(import.meta.url)) {
  runCli().catch((error: unknown) => {
    console.error(error instanceof Error ? error.message : String(error))
    process.exitCode = 1
  })
}
