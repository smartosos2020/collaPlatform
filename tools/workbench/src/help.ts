const workCommandHelp: Record<string, string> = {
  'work start': [
    'Usage: pnpm work:start --goal <text> --task-range <MILESTONE-T01 to MILESTONE-TNN> [options]',
    '',
    'Starts one milestone-scoped AI work cycle and records its immutable baseline.',
    'Options: --doc-mode, --validation-profile, --force',
  ].join('\n'),
  'work checkpoint': [
    'Usage: pnpm work:checkpoint [options]',
    '',
    'Runs an intermediate validation checkpoint for the active work cycle.',
    'Options: --validation-profile, --backend-test-pattern, --browser-spec',
  ].join('\n'),
  'work finish': [
    'Usage: pnpm work:finish [options]',
    '',
    'Runs the closing quality gate and completes the active work cycle.',
    'Provide either --browser-spec with evidence metadata or --browser-not-required-reason.',
    'For system-real-isolated tasks also provide --system-evidence-command and repeat --system-evidence-arg as needed.',
  ].join('\n'),
}

export function commandHelp(command: string): string {
  const specific = workCommandHelp[command]
  if (specific) return specific
  return [
    'Usage: pnpm workbench <command> [options]',
    '',
    'Core commands:',
    '  work start | work checkpoint | work finish',
    '  planning check',
    '  verify',
    '  architecture inventory | architecture contracts | architecture boundaries',
    '  security scan | security audit | security browser-evidence',
    '',
    'Run any command with --help for command-specific usage where available.',
  ].join('\n')
}
