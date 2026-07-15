const integer = (value, fallback) => {
  const parsed = Number.parseInt(value ?? '', 10)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

export const collaborationConfig = Object.freeze({
  host: process.env.COLLA_COLLABORATION_HOST ?? '0.0.0.0',
  port: integer(process.env.COLLA_COLLABORATION_PORT, 1234),
  backendUrl: (process.env.COLLA_BACKEND_INTERNAL_URL ?? 'http://127.0.0.1:8080/api/internal/knowledge-collaboration').replace(/\/$/, ''),
  internalSecret: process.env.COLLA_COLLABORATION_INTERNAL_SECRET ?? 'colla-local-collaboration-secret',
  maxUpdateBytes: integer(process.env.COLLA_COLLABORATION_MAX_UPDATE_BYTES, 1024 * 1024),
  debounceMs: integer(process.env.COLLA_COLLABORATION_STORE_DEBOUNCE_MS, 800),
  maxDebounceMs: integer(process.env.COLLA_COLLABORATION_STORE_MAX_DEBOUNCE_MS, 5000),
  authorizationCacheMs: integer(process.env.COLLA_COLLABORATION_AUTH_CACHE_MS, 1500),
})
