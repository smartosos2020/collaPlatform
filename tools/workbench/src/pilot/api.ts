export class PilotApi {
  readonly root: string
  constructor(baseUrl: string, private token = '') { this.root = `${baseUrl.replace(/\/$/, '')}${baseUrl.replace(/\/$/, '').endsWith('/api') ? '' : '/api'}` }
  withToken(token: string): PilotApi { return new PilotApi(this.root, token) }
  async request<T = any>(method: string, path: string, body?: unknown): Promise<T> {
    const response = await fetch(`${this.root}${path}`, { method, headers: { 'Content-Type': 'application/json', ...(this.token ? { Authorization: `Bearer ${this.token}` } : {}) }, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(30000) })
    if (!response.ok) throw new Error(`${method} ${path} failed (${response.status}): ${await response.text()}`)
    if (response.status === 204) return undefined as T
    const text = await response.text(); return text ? JSON.parse(text) : undefined as T
  }
}
