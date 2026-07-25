import { optionBoolean, optionString } from '../lib/args.js'
import type { CommandContext } from './types.js'

export async function runCommand({ command, options, root }: CommandContext): Promise<void> {
  if (command === 'security scan') {
    const { scanSensitiveData } = await import('../security/sensitiveScan.js')
    const result = scanSensitiveData(root, { writeReport: !optionBoolean(options, 'skip-report') })
    console.log(`Sensitive data scan: ${result.hits.length ? 'FAIL' : 'PASS'}; findings=${result.hits.length}; waived=${result.waived}`)
    if (result.report) console.log(`Report: ${result.report}`)
    for (const hit of result.hits) console.error(`${hit.path}:${hit.line} [${hit.rule}]`)
    if (result.hits.length) process.exitCode = 1
    return
  }
  if (command === 'security browser-evidence') {
    const { assertRealBrowserEvidence } = await import('../security/browserEvidence.js')
    const browserCommand = optionString(options, 'command')
    if (!browserCommand) throw new Error('--command is required')
    const result = assertRealBrowserEvidence(browserCommand, root)
    console.log(`Real browser evidence verified: references=${result.references.length}; closure=${result.files.length}`)
    return
  }
  if (command === 'security audit') {
    const { runSecurityAudit } = await import('../security/audit.js')
    const result = runSecurityAudit(root, !optionBoolean(options, 'skip-report'))
    result.results.forEach((value) => console.log(`PASS: ${value}`))
    result.failures.forEach((value) => console.error(`FAIL: ${value}`))
    if (result.failures.length) process.exitCode = 1
    return
  }
  throw new Error(`Unknown security command: ${command}`)
}
