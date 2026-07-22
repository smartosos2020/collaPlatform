const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? (import.meta.env.PROD ? '/api' : 'http://localhost:8080/api')
const API_REQUEST_TIMEOUT_MS = 5_000

export type RequestOptions = {
  auth?: boolean
  retry?: boolean
  requestId?: string
}

export async function apiGet<T>(path: string): Promise<T> {
  return apiRequest<T>('GET', path)
}

export async function apiGetText(path: string): Promise<string> {
  const accessToken = localStorage.getItem('colla.accessToken')
  const headers = new Headers({
    Accept: 'text/plain, text/csv, */*',
    'X-Colla-Client': 'web',
    'X-Colla-Retry-Attempt': '0',
  })

  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: 'GET',
    headers: {
      ...Object.fromEntries(headers.entries()),
    },
  })

  if (!response.ok) {
    throw new ApiRequestError(response.status)
  }

  return response.text()
}

export async function apiPost<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> {
  return apiRequest<T>('POST', path, body, options)
}

export async function apiPut<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> {
  return apiRequest<T>('PUT', path, body, options)
}

export async function apiDelete<T>(path: string, options?: RequestOptions): Promise<T> {
  return apiRequest<T>('DELETE', path, undefined, options)
}

export async function apiPatch<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> {
  return apiRequest<T>('PATCH', path, body, options)
}

async function apiRequest<T>(
  method: string,
  path: string,
  body?: unknown,
  options: RequestOptions = {},
): Promise<T> {
  const attempts = shouldRetry(method, options) ? 3 : 1
  const requestId = options.requestId ?? (isWriteMethod(method) ? createRequestId() : undefined)
  let lastError: unknown
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    try {
      return await sendRequest<T>(method, path, body, options, attempt, requestId)
    } catch (error) {
      lastError = error
      if (attempt >= attempts - 1 || !isRetryableError(error)) {
        throw error
      }
      await wait(250 * 2 ** attempt)
    }
  }
  throw lastError instanceof Error ? lastError : new Error('API request failed')
}

async function sendRequest<T>(
  method: string,
  path: string,
  body: unknown,
  options: RequestOptions,
  attempt: number,
  requestId?: string,
): Promise<T> {
  const accessToken = localStorage.getItem('colla.accessToken')
  const headers = new Headers({
    Accept: 'application/json',
    'X-Colla-Client': 'web',
    'X-Colla-Retry-Attempt': String(attempt),
  })

  if (body !== undefined) {
    headers.set('Content-Type', 'application/json')
  }

  if (requestId) {
    headers.set('X-Colla-Request-Id', requestId)
  }

  if (options.auth !== false && accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }

  const controller = new AbortController()
  const timeoutId = window.setTimeout(() => controller.abort(), API_REQUEST_TIMEOUT_MS)
  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers: {
        ...Object.fromEntries(headers.entries()),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new Error('API request timed out', { cause: error })
    }
    throw error
  } finally {
    window.clearTimeout(timeoutId)
  }

  if (!response.ok) {
    const error = await readApiError(response)
    throw new ApiRequestError(response.status, error.message, error.code)
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  return text ? (JSON.parse(text) as T) : (undefined as T)
}

export class ApiRequestError extends Error {
  readonly status: number
  readonly code?: string

  constructor(status: number, message?: string, code?: string) {
    super(message ? `${message} (${status})` : `API request failed: ${status}`)
    this.status = status
    this.code = code
  }
}

async function readApiError(response: Response): Promise<{ message: string; code?: string }> {
  const fallback = `API request failed: ${response.status}`
  const text = await response.text().catch(() => '')
  if (!text) {
    return { message: fallback }
  }
  try {
    const payload = JSON.parse(text) as {
      error?: { code?: string; message?: string }
      message?: string
      detail?: string
      title?: string
    }
    return {
      code: payload.error?.code,
      message: payload.error?.message ?? payload.message ?? payload.detail ?? payload.title ?? fallback,
    }
  } catch {
    return { message: text }
  }
}

function shouldRetry(method: string, options: RequestOptions) {
  return options.retry !== false && ['GET', 'HEAD'].includes(method.toUpperCase())
}

function isWriteMethod(method: string) {
  return ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method.toUpperCase())
}

function createRequestId() {
  return globalThis.crypto?.randomUUID?.() ?? `web-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function isRetryableError(error: unknown) {
  if (error instanceof ApiRequestError) {
    return [502, 503, 504].includes(error.status)
  }
  return true
}

function wait(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}
