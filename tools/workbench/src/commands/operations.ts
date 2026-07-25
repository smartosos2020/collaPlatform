import { optionBoolean, optionNumber, optionString } from '../lib/args.js'
import type { CommandContext } from './types.js'

export async function runCommand({ command, options, root }: CommandContext): Promise<void> {
  if (command === 'operations backup') {
    const { backup } = await import('../operations/backup.js')
    console.log(`Backup completed: ${backup(root, {
      composeFile: optionString(options, 'compose-file') || undefined,
      envFile: optionString(options, 'env-file') || undefined,
      backupDir: optionString(options, 'backup-dir') || undefined,
      backupHelperImage: optionString(options, 'backup-helper-image') || undefined,
      retentionDays: optionNumber(options, 'retention-days', 0),
      skipMinio: optionBoolean(options, 'skip-minio'),
      skipQuiesce: optionBoolean(options, 'skip-quiesce'),
      allowExternalBackupRoot: optionBoolean(options, 'allow-external-backup-root'),
    })}`)
    return
  }
  if (command === 'operations health') {
    const { healthCheck } = await import('../operations/health.js')
    console.log(`Health report: ${await healthCheck(root, {
      composeFile: optionString(options, 'compose-file') || undefined,
      envFile: optionString(options, 'env-file') || undefined,
      baseUrl: optionString(options, 'base-url') || undefined,
      metricsBaseUrl: optionString(options, 'metrics-base-url') || undefined,
      expectedProjectName: optionString(options, 'expected-project-name') || undefined,
      skipCompose: optionBoolean(options, 'skip-compose'),
      requirePrometheus: optionBoolean(options, 'require-prometheus'),
      requireLogCorrelation: optionBoolean(options, 'require-log-correlation'),
    })}`)
    return
  }
  if (command === 'operations restore-drill') {
    const { restoreDrill } = await import('../operations/drill.js')
    console.log(`Restore drill report: ${await restoreDrill(root, {
      backupPath: optionString(options, 'backup-path'),
      composeFile: optionString(options, 'compose-file') || undefined,
      envFile: optionString(options, 'env-file') || undefined,
      baseUrl: optionString(options, 'base-url') || undefined,
      expectedProjectName: optionString(options, 'expected-project-name') || undefined,
      runRestore: optionBoolean(options, 'run-restore'),
      confirmRestore: optionBoolean(options, 'confirm-restore'),
    })}`)
    return
  }
  if (command === 'operations restore') {
    const { restore } = await import('../operations/restore.js')
    console.log(`Restore report: ${await restore(root, {
      backupPath: optionString(options, 'backup-path'),
      composeFile: optionString(options, 'compose-file') || undefined,
      envFile: optionString(options, 'env-file') || undefined,
      backupHelperImage: optionString(options, 'backup-helper-image') || undefined,
      baseUrl: optionString(options, 'base-url') || undefined,
      expectedProjectName: optionString(options, 'expected-project-name'),
      confirmationText: optionString(options, 'confirmation-text'),
      confirmRestore: optionBoolean(options, 'confirm-restore'),
      skipHealthCheck: optionBoolean(options, 'skip-health-check'),
    })}`)
    return
  }
  if (command === 'operations rollback') {
    const { rollback } = await import('../operations/rollback.js')
    console.log(`Rollback report: ${await rollback(root, {
      composeFile: optionString(options, 'compose-file') || undefined,
      envFile: optionString(options, 'env-file') || undefined,
      serverImage: optionString(options, 'server-image'),
      webImage: optionString(options, 'web-image'),
      collaborationImage: optionString(options, 'collaboration-image'),
      expectedSourceCommit: optionString(options, 'expected-source-commit') || undefined,
      backupPath: optionString(options, 'backup-path') || undefined,
      baseUrl: optionString(options, 'base-url') || undefined,
      expectedProjectName: optionString(options, 'expected-project-name'),
      confirmationText: optionString(options, 'confirmation-text'),
      confirmRollback: optionBoolean(options, 'confirm-rollback'),
      restoreData: optionBoolean(options, 'restore-data'),
    })}`)
    return
  }
  if (command === 'operations release-check') {
    const { releaseCheck } = await import('../operations/release.js')
    console.log(`Release report: ${await releaseCheck(root, {
      composeFile: optionString(options, 'compose-file') || undefined,
      envFile: optionString(options, 'env-file') || undefined,
      gateMode: optionString(options, 'gate-mode', 'full') as 'quick' | 'full',
      expectedProjectName: optionString(options, 'expected-project-name') || undefined,
      backupPath: optionString(options, 'backup-path') || undefined,
      backupDir: optionString(options, 'backup-dir') || undefined,
      maxBackupAgeHours: optionNumber(options, 'max-backup-age-hours', 24),
      createBackup: optionBoolean(options, 'create-backup'),
      allowDirty: optionBoolean(options, 'allow-dirty'),
      skipQualityGate: optionBoolean(options, 'skip-quality-gate'),
      skipImageBuild: optionBoolean(options, 'skip-image-build'),
      skipBackupCheck: optionBoolean(options, 'skip-backup-check'),
      allowPartial: optionBoolean(options, 'allow-partial'),
    })}`)
    return
  }
  if (command === 'operations contract-check') {
    const { runSync } = await import('../lib/process.js')
    runSync('pnpm', ['--dir', 'tools/workbench', 'test'], { cwd: root, capture: false })
    return
  }
  throw new Error(`Unknown operations command: ${command}`)
}
