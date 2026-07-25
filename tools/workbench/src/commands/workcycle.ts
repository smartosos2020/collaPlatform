import { optionBoolean, optionString, optionStrings } from '../lib/args.js'
import type { BackendStrategy, CollaborationStrategy, FrontendStrategy } from '../workcycle/quality.js'
import type { CommandContext } from './types.js'

export async function runCommand({ command, options, root }: CommandContext): Promise<void> {
  if (command === 'work start' || command === 'work checkpoint' || command === 'work finish') {
    const { runWorkCycle } = await import('../workcycle/cycle.js')
    await runWorkCycle({
      stage: command.split(' ')[1] as 'start' | 'checkpoint' | 'finish',
      goal: optionString(options, 'goal'),
      taskRange: optionString(options, 'task-range'),
      docMode: optionString(options, 'doc-mode', 'code-doc-report') as 'code-doc-report' | 'archive-only',
      validationProfile: (optionString(options, 'validation-profile') || undefined) as 'light' | 'stage' | 'route-final' | undefined,
      backendTestPattern: optionString(options, 'backend-test-pattern') || undefined,
      browserSpecs: optionStrings(options, 'browser-spec'),
      browserGrep: optionString(options, 'browser-grep') || undefined,
      browserEvidenceKind: (optionString(options, 'browser-evidence-kind') || undefined) as 'real' | 'mock' | undefined,
      browserEvidenceEnvironment: (optionString(options, 'browser-evidence-environment') || undefined) as 'isolated' | 'shared-readonly' | 'mock' | undefined,
      browserNotRequiredReason: optionString(options, 'browser-not-required-reason') || undefined,
      systemEvidenceCommand: optionString(options, 'system-evidence-command') || undefined,
      systemEvidenceArgs: optionStrings(options, 'system-evidence-arg'),
      systemEvidenceCwd: optionString(options, 'system-evidence-cwd') || undefined,
      force: optionBoolean(options, 'force'),
    })
    return
  }
  if (command === 'planning check') {
    const { loadActivePlanningContract, planningSummary } = await import('../workcycle/planning.js')
    console.log(planningSummary(loadActivePlanningContract(root)))
    return
  }
  if (command === 'verify') {
    const { runQualityGate } = await import('../workcycle/quality.js')
    const mode = optionString(options, 'mode', 'quick') as 'quick' | 'stage' | 'full'
    await runQualityGate(root, {
      mode,
      backend: optionString(options, 'backend-strategy', mode === 'full' ? 'full' : 'compile') as BackendStrategy,
      backendTestPattern: optionString(options, 'backend-test-pattern') || undefined,
      frontend: optionString(options, 'frontend-strategy', 'full') as FrontendStrategy,
      collaboration: optionString(options, 'collaboration-strategy', mode === 'full' ? 'test' : 'skip') as CollaborationStrategy,
      skipDocker: optionBoolean(options, 'skip-docker'),
      skipAudit: optionBoolean(options, 'skip-audit'),
      compact: optionBoolean(options, 'compact-output'),
    })
    return
  }
  throw new Error(`Unknown core workbench command: ${command}`)
}
