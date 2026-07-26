#!/usr/bin/env node

const args = new Map()
for (let index = 2; index < process.argv.length; index += 1) {
  const key = process.argv[index]
  if (!key.startsWith('--')) throw new Error(`Unexpected argument: ${key}`)
  const value = process.argv[index + 1]
  if (!value || value.startsWith('--')) throw new Error(`Missing value for ${key}`)
  args.set(key.slice(2), value)
  index += 1
}

const baseUrl = (args.get('base-url') || process.env.COLLA_API_BASE_URL || '').replace(/\/$/, '')
const token = args.get('token') || process.env.COLLA_ACCESS_TOKEN
const action = args.get('action')
const batchId = args.get('batch-id')

if (!baseUrl || !token || !action) {
  throw new Error('Required: --base-url, --token and --action')
}

const routes = {
  plan: ['/api/admin/project-migrations/work-items:plan', 'POST', {
    dryRun: args.get('dry-run') === 'true',
    throttleMillis: Number(args.get('throttle-millis') || 0),
  }],
  execute: [`/api/admin/project-migrations/work-items/batches/${batchId}:execute`, 'POST', {
    confirmation: 'EXECUTE',
    workerId: args.get('worker-id') || `cli:${process.pid}`,
  }],
  pause: [`/api/admin/project-migrations/work-items/batches/${batchId}:pause`, 'POST', {
    reason: args.get('reason') || 'Paused from migration CLI',
  }],
  verify: [`/api/admin/project-migrations/work-items/batches/${batchId}:verify`, 'POST'],
  convergence: ['/api/admin/project-migrations/work-items:verify-convergence', 'POST'],
  rollback: [`/api/admin/project-migrations/work-items/batches/${batchId}:rollback`, 'POST', {
    confirmation: 'ROLLBACK',
  }],
  status: [`/api/admin/project-migrations/work-items/batches/${batchId}`, 'GET'],
  failures: [`/api/admin/project-migrations/work-items/batches/${batchId}/failures`, 'GET'],
}

const route = routes[action]
if (!route || (route[0].includes('undefined') && action !== 'plan' && action !== 'convergence')) {
  throw new Error('Invalid action or missing --batch-id')
}

const response = await fetch(`${baseUrl}${route[0]}`, {
  method: route[1],
  headers: {
    Authorization: `Bearer ${token}`,
    ...(route[2] ? { 'Content-Type': 'application/json' } : {}),
  },
  body: route[2] ? JSON.stringify(route[2]) : undefined,
})
const text = await response.text()
if (!response.ok) {
  throw new Error(`Migration API ${response.status}: ${text}`)
}
process.stdout.write(`${JSON.stringify(JSON.parse(text), null, 2)}\n`)
