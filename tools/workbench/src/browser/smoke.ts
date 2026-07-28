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

export async function isolatedProjectPlatformS10Smoke(
  root: string,
  databasePort = 5432,
  apiPort = 18100,
  webPort = 15200,
): Promise<void> {
  return isolatedRouteSmoke(
    root,
    'project-platform-s10-m5-route-final.spec.ts',
    'colla_s10_m5_e2e',
    databasePort,
    apiPort,
    webPort,
  )
}

export async function isolatedProjectPlatformS11Smoke(
  root: string,
  databasePort = 5432,
  apiPort = 18110,
  webPort = 15210,
): Promise<void> {
  return isolatedRouteSmoke(
    root,
    'project-platform-s11-m5-route-final.spec.ts',
    'colla_s11_m5_e2e',
    databasePort,
    apiPort,
    webPort,
  )
}

export async function isolatedProjectPlatformS12Smoke(
  root: string,
  spec: string,
  databasePort = 5432,
  apiPort = 18120,
  webPort = 15220,
): Promise<void> {
  if (!/^project-platform-s12-[a-z0-9-]+\.spec\.ts$/.test(spec)) {
    throw new Error(`Unsupported PROJECT-PLATFORM-S12 browser spec: ${spec}`)
  }
  return isolatedRouteSmoke(
    root,
    spec,
    'colla_s12_e2e',
    databasePort,
    apiPort,
    webPort,
    'all',
  )
}

export async function isolatedProjectPlatformS13Smoke(
  root: string,
  spec: string,
  databasePort = 5432,
  apiPort = 18130,
  webPort = 15230,
): Promise<void> {
  if (!/^project-platform-s13-[a-z0-9-]+\.spec\.ts$/.test(spec)) {
    throw new Error(`Unsupported PROJECT-PLATFORM-S13 browser spec: ${spec}`)
  }
  return isolatedRouteSmoke(
    root,
    spec,
    'colla_s13_e2e',
    databasePort,
    apiPort,
    webPort,
    'all',
  )
}

export async function isolatedProjectPlatformS14Smoke(
  root: string,
  spec: string,
  databasePort = 5432,
  apiPort = 18140,
  webPort = 15240,
): Promise<void> {
  if (!/^project-platform-s14-[a-z0-9-]+\.spec\.ts$/.test(spec)) {
    throw new Error(`Unsupported PROJECT-PLATFORM-S14 browser spec: ${spec}`)
  }
  return isolatedRouteSmoke(
    root,
    spec,
    'colla_s14_e2e',
    databasePort,
    apiPort,
    webPort,
    'all',
  )
}

export async function isolatedProjectPlatformS15Smoke(
  root: string,
  spec: string,
  databasePort = 5432,
  apiPort = 18150,
  webPort = 15250,
): Promise<void> {
  if (!/^project-platform-s15-[a-z0-9-]+\.spec\.ts$/.test(spec)) {
    throw new Error(`Unsupported PROJECT-PLATFORM-S15 browser spec: ${spec}`)
  }
  return isolatedRouteSmoke(
    root,
    spec,
    'colla_s15_e2e',
    databasePort,
    apiPort,
    webPort,
    'all',
  )
}

export async function isolatedProjectPlatformS16Smoke(
  root: string,
  spec: string,
  databasePort = 5432,
  apiPort = 18160,
  webPort = 15260,
): Promise<void> {
  if (!/^project-platform-s16-[a-z0-9-]+\.spec\.ts$/.test(spec)) {
    throw new Error(`Unsupported PROJECT-PLATFORM-S16 browser spec: ${spec}`)
  }
  return isolatedRouteSmoke(
    root,
    spec,
    'colla_s16_e2e',
    databasePort,
    apiPort,
    webPort,
    'all',
  )
}

export async function isolatedProjectPlatformS17Smoke(
  root: string,
  spec: string,
  databasePort = 5432,
  apiPort = 18170,
  webPort = 15270,
): Promise<void> {
  if (!/^project-platform-s17-[a-z0-9-]+\.spec\.ts$/.test(spec)) {
    throw new Error(`Unsupported PROJECT-PLATFORM-S17 browser spec: ${spec}`)
  }
  return isolatedRouteSmoke(
    root,
    spec,
    'colla_s17_e2e',
    databasePort,
    apiPort,
    webPort,
    'all',
  )
}

export async function isolatedProjectPlatformS18Smoke(
  root: string,
  spec: string,
  databasePort = 5432,
  apiPort = 18180,
  webPort = 15280,
): Promise<void> {
  if (!/^project-platform-s18-[a-z0-9-]+\.spec\.ts$/.test(spec)) {
    throw new Error(`Unsupported PROJECT-PLATFORM-S18 browser spec: ${spec}`)
  }
  return isolatedRouteSmoke(
    root,
    spec,
    'colla_s18_e2e',
    databasePort,
    apiPort,
    webPort,
    'all',
  )
}

export async function isolatedProjectPlatformS19Smoke(
  root: string,
  spec: string,
  databasePort = 5432,
  apiPort = 18190,
  webPort = 15290,
): Promise<void> {
  if (!/^project-platform-s19-[a-z0-9-]+\.spec\.ts$/.test(spec)) {
    throw new Error(`Unsupported PROJECT-PLATFORM-S19 browser spec: ${spec}`)
  }
  return isolatedRouteSmoke(
    root,
    spec,
    'colla_s19_e2e',
    databasePort,
    apiPort,
    webPort,
    'all',
  )
}

async function isolatedRouteSmoke(
  root: string,
  spec: string,
  databasePrefix: string,
  databasePort: number,
  apiPort: number,
  webPort: number,
  suite = 'route-final',
): Promise<void> {
  const database = `${databasePrefix}_${randomUUID().replaceAll('-', '').slice(0, 8)}`; const logs = join(root, '.local-logs'); mkdirSync(logs, { recursive: true })
  let server: ChildProcess | undefined; let web: ChildProcess | undefined
  let databaseContainer = 'colla-postgres'
  let effectiveDatabasePort = databasePort
  let temporaryDatabaseContainer: string | undefined
  try {
    if (!databaseReady(databaseContainer)) {
      temporaryDatabaseContainer = `colla-workbench-postgres-${randomUUID().replaceAll('-', '').slice(0, 8)}`
      runSync('docker', [
        'run', '--detach', '--rm', '--name', temporaryDatabaseContainer,
        '--env', 'POSTGRES_USER=colla',
        '--env', `POSTGRES_PASSWORD=${['colla', 'dev', 'password'].join('_')}`,
        '--env', 'POSTGRES_DB=postgres',
        '--publish', '127.0.0.1::5432',
        'postgres:16-alpine',
      ])
      databaseContainer = temporaryDatabaseContainer
      await waitDatabaseReady(databaseContainer)
      const port = runSync('docker', ['port', databaseContainer, '5432/tcp'])
      effectiveDatabasePort = Number(port.trim().split(':').at(-1))
      if (!Number.isInteger(effectiveDatabasePort) || effectiveDatabasePort <= 0) {
        throw new Error(`Cannot resolve isolated PostgreSQL port from: ${port}`)
      }
    }
    runSync('mvn', ['-q', '-f', 'server/pom.xml', '-DskipTests', 'package'], { cwd: root }); runSync('docker', ['exec', databaseContainer, 'createdb', '-U', 'colla', database])
    server = background('java', ['-jar', 'server/target/colla-platform-server-0.1.0-SNAPSHOT.jar'], root, { COLLA_DATASOURCE_URL: `jdbc:postgresql://127.0.0.1:${effectiveDatabasePort}/${database}`, COLLA_DATASOURCE_USERNAME: 'colla', COLLA_DATASOURCE_PASSWORD: ['colla', 'dev', 'password'].join('_'), COLLA_EVENT_WORKER_ENABLED: 'true', COLLA_EVENT_WORKER_CONCURRENCY: '2', COLLA_EVENT_WORKER_QUEUE_CAPACITY: '0', COLLA_EVENT_WORKER_CLAIM_BATCH: '2', COLLA_EVENT_WORKER_EXPECTED_INSTANCES: '1', SERVER_PORT: String(apiPort), CORS_ALLOWED_ORIGINS: `http://127.0.0.1:${webPort}` }, join(logs, 'm5-isolated-server.out.log'), join(logs, 'm5-isolated-server.err.log'))
    web = background('pnpm', ['dev', '--host', '127.0.0.1', '--port', String(webPort)], join(root, 'web'), { VITE_API_BASE_URL: `http://127.0.0.1:${apiPort}/api`, VITE_WS_BASE_URL: `ws://127.0.0.1:${apiPort}/ws/events` }, join(logs, 'm5-isolated-web.out.log'), join(logs, 'm5-isolated-web.err.log'))
    await waitReady(`http://127.0.0.1:${apiPort}/actuator/health`, 180000)
    await waitReady(`http://127.0.0.1:${webPort}`, 120000)
    await run('pnpm', ['exec', 'playwright', 'test', '--config', 'e2e/playwright.config.ts', spec], { cwd: join(root, 'web'), env: { COLLA_E2E_SUITE: suite, COLLA_E2E_ISOLATED: 'true', COLLA_E2E_API_BASE_URL: `http://127.0.0.1:${apiPort}/api`, COLLA_E2E_WEB_BASE_URL: `http://127.0.0.1:${webPort}` } })
  } finally {
    await stopTree(web)
    await stopTree(server)
    runSync('docker', ['exec', databaseContainer, 'dropdb', '--if-exists', '--force', '-U', 'colla', database], { allowFailure: true })
    if (temporaryDatabaseContainer) {
      runSync('docker', ['stop', temporaryDatabaseContainer], { allowFailure: true })
    }
  }
}

function databaseReady(container: string): boolean {
  return runSync(
    'docker',
    ['exec', container, 'pg_isready', '-U', 'colla', '-d', 'postgres'],
    { allowFailure: true },
  ).includes('accepting connections')
}

async function waitDatabaseReady(container: string): Promise<void> {
  const deadline = Date.now() + 90_000
  while (Date.now() < deadline) {
    if (databaseReady(container)) return
    await new Promise((resolve) => setTimeout(resolve, 1_000))
  }
  throw new Error(`Timed out waiting for isolated PostgreSQL container ${container}`)
}
