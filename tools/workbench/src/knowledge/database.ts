import { runSync } from '../lib/process.js'

export interface DatabaseOptions { container: string; database: string; user: string }

export function psql(sql: string, options: DatabaseOptions, tuplesOnly = false): string {
  return runSync('docker', ['exec', '-i', options.container, 'psql', '-U', options.user, '-d', options.database, '-v', 'ON_ERROR_STOP=1', ...(tuplesOnly ? ['-Atc'] : ['-c']), sql], { stdin: '' })
}

export function scalar(sql: string, options: DatabaseOptions): number {
  const value = Number.parseInt(psql(sql, options, true).trim(), 10)
  if (!Number.isFinite(value)) throw new Error(`Expected scalar integer from PostgreSQL, received: ${value}`)
  return value
}
