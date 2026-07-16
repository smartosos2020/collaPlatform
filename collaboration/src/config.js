import { hostname } from 'node:os'

const integer = (value, fallback) => {
  const parsed = Number.parseInt(value ?? '', 10)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

const nonNegativeInteger = (value, fallback) => {
  const parsed = Number.parseInt(value ?? '', 10)
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback
}

const enabled = (value, fallback = true) => value == null ? fallback : !['0', 'false', 'off', 'no'].includes(value.toLowerCase())

const port = integer(process.env.COLLA_COLLABORATION_PORT, 1234)

export const collaborationConfig = Object.freeze({
  host: process.env.COLLA_COLLABORATION_HOST ?? '0.0.0.0',
  port,
  nodeId: process.env.COLLA_COLLABORATION_NODE_ID ?? `${hostname()}-${port}`,
  backendUrl: (process.env.COLLA_BACKEND_INTERNAL_URL ?? 'http://127.0.0.1:8080/api/internal/knowledge-collaboration').replace(/\/$/, ''),
  internalSecret: process.env.COLLA_COLLABORATION_INTERNAL_SECRET ?? 'colla-local-collaboration-secret',
  maxUpdateBytes: integer(process.env.COLLA_COLLABORATION_MAX_UPDATE_BYTES, 1024 * 1024),
  debounceMs: integer(process.env.COLLA_COLLABORATION_STORE_DEBOUNCE_MS, 800),
  maxDebounceMs: integer(process.env.COLLA_COLLABORATION_STORE_MAX_DEBOUNCE_MS, 5000),
  authorizationCacheMs: integer(process.env.COLLA_COLLABORATION_AUTH_CACHE_MS, 1500),
  backendTimeoutMs: integer(process.env.COLLA_COLLABORATION_BACKEND_TIMEOUT_MS, 5000),
  backendRetries: nonNegativeInteger(process.env.COLLA_COLLABORATION_BACKEND_RETRIES, 2),
  recoveryIntervalMs: integer(process.env.COLLA_COLLABORATION_RECOVERY_INTERVAL_MS, 5000),
  roomUnloadImmediately: enabled(process.env.COLLA_COLLABORATION_UNLOAD_IMMEDIATELY, false),
  redis: Object.freeze({
    enabled: enabled(process.env.COLLA_COLLABORATION_REDIS_ENABLED, true),
    host: process.env.COLLA_COLLABORATION_REDIS_HOST ?? process.env.REDIS_HOST ?? '127.0.0.1',
    port: integer(process.env.COLLA_COLLABORATION_REDIS_PORT ?? process.env.REDIS_PORT, 6379),
    password: process.env.COLLA_COLLABORATION_REDIS_PASSWORD ?? process.env.REDIS_PASSWORD,
    db: nonNegativeInteger(process.env.COLLA_COLLABORATION_REDIS_DB, 0),
    prefix: process.env.COLLA_COLLABORATION_REDIS_PREFIX ?? 'colla:knowledge:collaboration',
    lockTimeoutMs: integer(process.env.COLLA_COLLABORATION_REDIS_LOCK_TIMEOUT_MS, 3000),
    initialSyncTimeoutMs: integer(process.env.COLLA_COLLABORATION_REDIS_INITIAL_SYNC_TIMEOUT_MS, 1500),
  }),
})
