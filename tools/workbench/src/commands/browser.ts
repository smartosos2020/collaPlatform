import { optionBoolean, optionNumber, optionString } from '../lib/args.js'
import type { CommandContext } from './types.js'

export async function runCommand({ command, options, root }: CommandContext): Promise<void> {
  const { browserSmoke, isolatedM5Smoke, isolatedProjectPlatformS09Smoke } = await import('../browser/smoke.js')
  if (command === 'browser smoke-im' || command === 'browser smoke-ui-split') {
    await browserSmoke(root, command.endsWith('smoke-im') ? 'e2e/im-smoke.spec.ts' : 'e2e/ui-split-v1-smoke.spec.ts', {
      webBaseUrl: optionString(options, 'web-base-url') || undefined,
      apiBaseUrl: optionString(options, 'api-base-url') || undefined,
      username: optionString(options, 'username') || undefined,
      password: optionString(options, 'password') || undefined,
      headed: optionBoolean(options, 'headed'),
    })
    return
  }
  if (command === 'browser smoke-m5-isolated') {
    await isolatedM5Smoke(root, optionNumber(options, 'database-port', 5432), optionNumber(options, 'api-port', 18080), optionNumber(options, 'web-port', 15173))
    return
  }
  if (command === 'browser smoke-project-platform-s09-isolated') {
    await isolatedProjectPlatformS09Smoke(
      root,
      optionNumber(options, 'database-port', 5432),
      optionNumber(options, 'api-port', 18090),
      optionNumber(options, 'web-port', 15190),
    )
    return
  }
  throw new Error(`Unknown browser command: ${command}`)
}
