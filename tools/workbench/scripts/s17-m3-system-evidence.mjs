import { spawnSync } from 'node:child_process'
const result = spawnSync('mvn.cmd', [
  '-q', '-f', 'server/pom.xml',
  '-Dtest=AutomationScheduleFoundationIntegrationTests', 'test',
], { cwd: process.cwd(), encoding: 'utf8', shell: true })
process.stdout.write(result.stdout ?? '')
process.stderr.write(result.stderr ?? '')
if (result.error) throw result.error
process.exitCode = result.status ?? 1
