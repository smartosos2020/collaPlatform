import { API_BASE_URL } from '../api/apiBaseUrl'
import { useAuthStore } from '../../modules/auth/authStore'

/**
 * 单飞（single-flight）会话刷新。
 *
 * 后端契约：POST /api/auth/refresh { refreshToken } -> AuthTokens
 * （见 server AuthController#refresh）。多个并发 401 共享同一次刷新请求；
 * 成功后通过 authStore.setTokens 写回 localStorage 并触发 realtime 重连。
 */
let refreshPromise: Promise<string | null> | null = null

export function refreshSessionSingleFlight(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = performRefresh().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

async function performRefresh(): Promise<string | null> {
  const refreshToken = useAuthStore.getState().refreshToken
  if (!refreshToken) {
    return null
  }
  const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-Colla-Client': 'web',
    },
    body: JSON.stringify({ refreshToken }),
  })
  if (!response.ok) {
    // 4xx 表示 refresh token 被拒绝；5xx/网络异常必须向上传递，不能误登出用户。
    if (response.status >= 500) {
      throw new Error(`会话刷新服务暂不可用（${response.status}）`)
    }
    return null
  }
  const tokens = (await response.json()) as { accessToken?: string; refreshToken?: string }
  if (!tokens.accessToken || !tokens.refreshToken) {
    throw new Error('会话刷新响应不完整')
  }
  useAuthStore.getState().setTokens(tokens.accessToken, tokens.refreshToken)
  return tokens.accessToken
}
