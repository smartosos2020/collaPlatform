import { optionBoolean, optionString } from '../lib/args.js'
import type { CommandContext } from './types.js'

export async function runCommand({ command, options, root }: CommandContext): Promise<void> {
  if (command === 'knowledge naming-guard') {
    const { namingGuard } = await import('../knowledge/checks.js')
    const findings = namingGuard(root)
    findings.forEach((value) => console.error(value))
    if (findings.length) process.exitCode = 1
    else console.log('Knowledge naming guard passed.')
    return
  }
  if (command === 'knowledge consistency-check') {
    const { consistencyCheck } = await import('../knowledge/checks.js')
    const result = consistencyCheck(root, {
      container: optionString(options, 'container', 'colla-postgres'),
      database: optionString(options, 'database', 'colla_platform'),
      user: optionString(options, 'database-user', 'colla'),
    }, optionString(options, 'output-dir', '.local-reports'))
    console.log(`Knowledge consistency: ${result.failures ? 'FAIL' : 'PASS'}; report=${result.report}`)
    if (result.failures) process.exitCode = 2
    return
  }
  if (command === 'knowledge inspect-object-references') {
    const { inspectObjectReferences } = await import('../knowledge/checks.js')
    inspectObjectReferences({
      container: optionString(options, 'container', 'colla-postgres'),
      database: optionString(options, 'database', 'colla_platform'),
      user: optionString(options, 'user', 'colla'),
    }).forEach((value) => console.log(value))
    return
  }
  if (command === 'knowledge repair-reference') {
    const { repairKnowledgeReference } = await import('../knowledge/repair.js')
    const result = repairKnowledgeReference(root, {
      referenceId: optionString(options, 'reference-id'),
      action: optionString(options, 'action', 'preview') as 'preview' | 'repair',
      container: optionString(options, 'container', 'colla-postgres'),
      database: optionString(options, 'database', 'colla_platform'),
      user: optionString(options, 'database-user', 'colla'),
      backupPath: optionString(options, 'backup-path') || undefined,
      createBackup: optionBoolean(options, 'create-backup'),
      confirm: optionBoolean(options, 'confirm'),
      outputDir: optionString(options, 'output-dir', '.local-reports'),
    })
    console.log(`Knowledge reference repair: ${result.report}`)
    return
  }
  throw new Error(`Unknown knowledge command: ${command}`)
}
