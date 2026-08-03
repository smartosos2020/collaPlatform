/**
 * 判断 /auth/me 等会话探测请求的错误是否代表会话确实失效（401/403）。
 * 网络抖动、5xx、超时等瞬时错误不属于会话失效，调用方不得因此登出用户。
 */
export function isSessionExpiredError(error: unknown): boolean {
  if (!error || typeof error !== 'object' || !('status' in error)) {
    return false
  }
  const status = (error as { status?: unknown }).status
  return status === 401 || status === 403
}
