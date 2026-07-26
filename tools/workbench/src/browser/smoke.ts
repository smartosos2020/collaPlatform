import { mkdirSync, openSync } from 'node:fs'
import { join } from 'node:path'
import { type ChildProcess } from 'node:child_process'
import { randomUUID } from 'node:crypto'
import { run, runSync, spawnManaged } from '../lib/process.js'

export interface SmokeOptions { webBaseUrl?: string; apiBaseUrl?: string; username?: string; password?: string; headed?: boolean }
export async function browserSmoke(root: string, spec: string, options: SmokeOptions): Promise<void> {
  const password = options.password || process.env.COLLA_E2E_PASSWORD || ['admin', '123456'].join('')
  await run('pnpm', ['exec', 'playwright', 'test', spec, '--config=e2e/playwright.config.ts', ...(options.headed ? ['--headed'] : [])], { cwd: join(root, 'web'), env: { COLLA_E2E_WEB_BASE_URL: options.webBaseUrl ?? 'http://127.0.0.1:5173', COLLA_E2E_API_BASE_URL: options.apiBaseUrl ?? 'http://localhost:8080/api', COLLA_E2E_USERNAME: options.username ?? 'admin', COLLA_E2E_PASSWORD: password } })
}

function background(command: string, args: string[], cwd: string, env: NodeJS.ProcessEnv, output: string, error: string): ChildProcess {
  return spawnManaged(command, args, {
    cwd,
    env,
    detached: process.platform !== 'win32',
    stdio: ['ignore', openSync(output, 'w'), openSync(error, 'w')],
  })
}
async function stopTree(child?: ChildProcess): Promise<void> {
  if (!child?.pid || child.exitCode != null) return
  if (process.platform === 'win32') runSync('taskkill', ['/PID', String(child.pid), '/T', '/F'], { allowFailure: true })
  else { try { process.kill(-child.pid, 'SIGTERM') } catch { child.kill('SIGTERM') } }
}
async function waitReady(url: string, timeoutMs = 90000): Promise<void> {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) { try { if ((await fetch(url, { signal: AbortSignal.timeout(2000) })).ok) return } catch { /* retry */ } await new Promise((resolve) => setTimeout(resolve, 2000)) }
  throw new Error(`Timed out waiting for ${url}`)
}

export async function isolatedM5Smoke(root: string, databasePort = 5432, apiPort = 18080, webPort = 15173): Promise<void> {
  return isolatedRouteSmoke(root, 'm5-permission-notification-e2e.spec.ts', 'colla_m5_e2e', databasePort, apiPort, webPort)
}

export async function isolatedProjectPlatformS09Smoke(
  root: string,
  databasePort = 5432,
  apiPort = 18090,
  webPort = 15190,
): Promise<void> {
  return isolatedRouteSmoke(
    root,
    'project-platform-s09-m5-route-final.spec.ts',
    'colla_s09_m5_e2e',
    databasePort,
    apiPort,
    webPort,
  )
}

async function isolatedRouteSmoke(
  root: string,
  spec: string,
  databasePrefix: string,
  databasePort: number,
  apiPort: number,
  webPort: number,
): Promise<void> {
  const database = `${databasePrefix}_${randomUUID().replaceAll('-', '').slice(0, 8)}`; const logs = join(root, '.local-logs'); mkdirSync(logs, { recursive: true })
  let server: ChildProcess | undefined; let web: ChildProcess | undefined
  try {
    runSync('mvn', ['-q', '-f', 'server/pom.xml', '-DskipTests', 'package'], { cwd: root }); runSync('docker', ['exec', 'colla-postgres', 'createdb', '-U', 'colla', database])
    server = background('java', ['-jar', 'server/target/colla-platform-server-0.1.0-SNAPSHOT.jar'], root, { COLLA_DATASOURCE_URL: `jdbc:postgresql://127.0.0.1:${databasePort}/${database}`, COLLA_DATASOURCE_USERNAME: 'colla', COLLA_DATASOURCE_PASSWORD: ['colla', 'dev', 'password'].join('_'), SERVER_PORT: String(apiPort), CORS_ALLOWED_ORIGINS: `http://127.0.0.1:${webPort}` }, join(logs, 'm5-isolated-server.out.log'), join(logs, 'm5-isolated-server.err.log'))
    web = background('pnpm', ['dev', '--host', '127.0.0.1', '--port', String(webPort)], join(root, 'web'), { VITE_API_BASE_URL: `http://127.0.0.1:${apiPort}/api`, VITE_WS_BASE_URL: `ws://127.0.0.1:${apiPort}/ws/events` }, join(logs, 'm5-isolated-web.out.log'), join(logs, 'm5-isolated-web.err.log'))
    await waitReady(`http://127.0.0.1:${apiPort}/actuator/health`); await waitReady(`http://127.0.0.1:${webPort}`)
    await run('pnpm', ['exec', 'playwright', 'test', '--config', 'e2e/playwright.config.ts', spec], { cwd: join(root, 'web'), env: { COLLA_E2E_SUITE: 'route-final', COLLA_E2E_ISOLATED: 'true', COLLA_E2E_API_BASE_URL: `http://127.0.0.1:${apiPort}/api`, COLLA_E2E_WEB_BASE_URL: `http://127.0.0.1:${webPort}` } })
  } finally { await stopTree(web); await stopTree(server); runSync('docker', ['exec', 'colla-postgres', 'dropdb', '--if-exists', '--force', '-U', 'colla', database], { allowFailure: true }) }
}
