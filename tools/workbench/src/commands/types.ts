import type { CliOptions } from '../lib/args.js'

export interface CommandContext {
  command: string
  options: CliOptions
  root: string
}

export type CommandRunner = (context: CommandContext) => Promise<void>
export type CommandModule = { runCommand: CommandRunner }
