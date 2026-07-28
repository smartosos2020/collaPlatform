import { optionBoolean, optionNumber, optionString } from '../lib/args.js'
import type { CommandContext } from './types.js'

export async function runCommand({ command, options, root }: CommandContext): Promise<void> {
  const {
    browserSmoke,
    isolatedM5Smoke,
    isolatedProjectPlatformS09Smoke,
    isolatedProjectPlatformS10Smoke,
    isolatedProjectPlatformS11Smoke,
    isolatedProjectPlatformS13Smoke,
    isolatedProjectPlatformS14Smoke,
    isolatedProjectPlatformS15Smoke,
    isolatedProjectPlatformS16Smoke,
    isolatedProjectPlatformS17Smoke,
    isolatedProjectPlatformS18Smoke,
  } = await import('../browser/smoke.js')
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
  if (command === 'browser smoke-project-platform-s10-isolated') {
    await isolatedProjectPlatformS10Smoke(
      root,
      optionNumber(options, 'database-port', 5432),
      optionNumber(options, 'api-port', 18100),
      optionNumber(options, 'web-port', 15200),
    )
    return
  }
  if (command === 'browser smoke-project-platform-s11-isolated') {
    await isolatedProjectPlatformS11Smoke(
      root,
      optionNumber(options, 'database-port', 5432),
      optionNumber(options, 'api-port', 18110),
      optionNumber(options, 'web-port', 15210),
    )
    return
  }
  if (command === 'browser smoke-project-platform-s13-isolated') {
    const spec = optionString(options, 'spec')
    if (!spec) throw new Error('--spec is required')
    await isolatedProjectPlatformS13Smoke(
      root,
      spec,
      optionNumber(options, 'database-port', 5432),
      optionNumber(options, 'api-port', 18130),
      optionNumber(options, 'web-port', 15230),
    )
    return
  }
  if (command === 'browser smoke-project-platform-s14-isolated') {
    const spec = optionString(options, 'spec')
    if (!spec) throw new Error('--spec is required')
    await isolatedProjectPlatformS14Smoke(
      root,
      spec,
      optionNumber(options, 'database-port', 5432),
      optionNumber(options, 'api-port', 18140),
      optionNumber(options, 'web-port', 15240),
    )
    return
  }
  if (command === 'browser smoke-project-platform-s15-isolated') {
    const spec = optionString(options, 'spec')
    if (!spec) throw new Error('--spec is required')
    await isolatedProjectPlatformS15Smoke(
      root,
      spec,
      optionNumber(options, 'database-port', 5432),
      optionNumber(options, 'api-port', 18150),
      optionNumber(options, 'web-port', 15250),
    )
    return
  }
  if (command === 'browser smoke-project-platform-s16-isolated') {
    const spec = optionString(options, 'spec')
    if (!spec) throw new Error('--spec is required')
    await isolatedProjectPlatformS16Smoke(
      root,
      spec,
      optionNumber(options, 'database-port', 5432),
      optionNumber(options, 'api-port', 18160),
      optionNumber(options, 'web-port', 15260),
    )
    return
  }
  if (command === 'browser smoke-project-platform-s17-isolated') {
    const spec = optionString(options, 'spec')
    if (!spec) throw new Error('--spec is required')
    await isolatedProjectPlatformS17Smoke(
      root,
      spec,
      optionNumber(options, 'database-port', 5432),
      optionNumber(options, 'api-port', 18170),
      optionNumber(options, 'web-port', 15270),
    )
    return
  }
  if (command === 'browser smoke-project-platform-s18-isolated') {
    const spec = optionString(options, 'spec')
    if (!spec) throw new Error('--spec is required')
    await isolatedProjectPlatformS18Smoke(
      root,
      spec,
      optionNumber(options, 'database-port', 5432),
      optionNumber(options, 'api-port', 18180),
      optionNumber(options, 'web-port', 15280),
    )
    return
  }
  throw new Error(`Unknown browser command: ${command}`)
}
