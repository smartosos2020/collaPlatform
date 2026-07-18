import { mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { randomUUID } from 'node:crypto'
import { compose, composeModel, resolveFrom, type OperationPaths } from './common.js'

export interface HealthOptions { composeFile?: string; envFile?: string; baseUrl?: string; metricsBaseUrl?: string; expectedProjectName?: string; skipCompose?: boolean; requirePrometheus?: boolean; requireLogCorrelation?: boolean }
async function checkedGet(url: string, headers: Record<string, string> = {}): Promise<Response> { const response = await fetch(url, { headers, signal: AbortSignal.timeout(15000) }); if (!response.ok) throw new Error(`Unexpected status ${response.status} from ${url}`); return response }

export async function healthCheck(root: string, options: HealthOptions): Promise<string> {
  const paths: OperationPaths = { root, composePath: resolveFrom(root, options.composeFile ?? 'deploy/docker-compose.prod.yml'), envPath: resolveFrom(root, options.envFile ?? 'deploy/.env.prod') }
  const base = (options.baseUrl ?? 'http://localhost').replace(/\/$/, ''); const metrics = (options.metricsBaseUrl ?? base).replace(/\/$/, '')
  const requestId = `health-${randomUUID().replaceAll('-', '')}`; const results: string[] = []; let project = 'not-checked'; let passed = false
  const directory = join(root, '.local-reports'); mkdirSync(directory, { recursive: true }); const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, '')
  try {
    if (!options.skipCompose) {
      const model = composeModel(paths); project = String(model.name)
      if (options.expectedProjectName && project !== options.expectedProjectName) throw new Error(`Health target mismatch: ${project}`)
      const raw = compose(paths, ['ps', '--all', '--format', 'json']); let containers: any[]
      try { const parsed = JSON.parse(raw); containers = Array.isArray(parsed) ? parsed : [parsed] } catch { containers = raw.split(/\r?\n/).filter(Boolean).map((line) => JSON.parse(line)) }
      for (const [name, service] of Object.entries<any>(model.services ?? {})) {
        const container = containers.find((item) => item.Service === name); if (!container || container.State !== 'running') throw new Error(`Compose service is not running: ${name}`)
        if (service.healthcheck && container.Health !== 'healthy') throw new Error(`Compose service is not healthy: ${name}`)
      }
      results.push('all compose services are running and healthy')
    }
    const apiResponse = await checkedGet(`${base}/api/health`, { 'X-Colla-Request-Id': requestId }); const api = await apiResponse.json() as any
    if (api.status !== 'ok' || api.service !== 'colla-platform' || Number.isNaN(Date.parse(api.time)) || apiResponse.headers.get('X-Colla-Request-Id') !== requestId) throw new Error('API health payload or request-id echo is invalid')
    results.push('API health payload and request-id echo are valid')
    const actuator = await (await checkedGet(`${base}/actuator/health`)).json() as any; if (actuator.status !== 'UP') throw new Error('Actuator health is not UP'); results.push('Actuator health is UP')
    if (options.requirePrometheus) { const text = await (await checkedGet(`${metrics}/actuator/prometheus`)).text(); if (!/^jvm_/m.test(text)) throw new Error('Prometheus output has no JVM metrics'); results.push('Prometheus exposes JVM metrics') }
    if (options.requireLogCorrelation) {
      if (options.skipCompose) throw new Error('Log correlation requires compose inspection')
      let matched = false; for (let attempt = 0; attempt < 10 && !matched; attempt += 1) { matched = compose(paths, ['logs', '--since', '2m', 'server']).includes(requestId); if (!matched) await new Promise((resolve) => setTimeout(resolve, 500)) }
      if (!matched) throw new Error(`Request id not found in server logs: ${requestId}`); results.push('request id is present in server logs')
    }
    passed = true
  } finally {
    const report = join(directory, `health-check-${stamp}.md`); writeFileSync(report, ['# Health Check', '', `- Time: ${new Date().toISOString()}`, `- BaseUrl: ${base}`, `- Compose project: ${project}`, `- Request id: ${requestId}`, `- Decision: ${passed ? 'PASS' : 'FAIL'}`, '', ...results.map((value) => `- PASS: ${value}`), ''].join('\n'))
  }
  return join(directory, `health-check-${stamp}.md`)
}
