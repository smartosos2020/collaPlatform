import { API_BASE_URL } from './apiBaseUrl'
import { ApiRequestError } from './apiError'
import { refreshSessionSingleFlight } from '../auth/sessionRefresh'
import { useAuthStore } from '../../modules/auth/authStore'

export { ApiRequestError } from './apiError'

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
  const accessToken = useAuthStore.getState().accessToken
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
  let sessionRefreshed = false
  let attempt = 0
  while (attempt < attempts) {
    try {
      return await sendRequest<T>(method, path, body, options, attempt, requestId)
    } catch (error) {
      lastError = error
      // 401 时先尝试单飞刷新会话，成功后用新 token 重放本次请求（不消耗重试次数）。
      // /auth/* 自身（登录/登出）的 401 属于凭证问题，不触发刷新。
      if (
        error instanceof ApiRequestError
        && error.status === 401
        && options.auth !== false
        && !path.startsWith('/auth/')
        && !sessionRefreshed
      ) {
        sessionRefreshed = true
        let refreshedToken: string | null
        try {
          refreshedToken = await refreshSessionSingleFlight()
        } catch (refreshError) {
          // 刷新服务不可用或网络中断时保留本地登录态，并把瞬时错误交给 UI 重试。
          throw refreshError instanceof Error ? refreshError : new Error('会话刷新暂不可用')
        }
        if (refreshedToken) {
          continue
        }
        // 刷新失败：会话确实失效，清空本地登录态，由路由守卫引导重新登录。
        useAuthStore.getState().clearAuth()
        throw error
      }
      attempt += 1
      if (attempt >= attempts || !isRetryableError(error)) {
        throw error
      }
      await wait(250 * 2 ** (attempt - 1))
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
  const accessToken = useAuthStore.getState().accessToken
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
