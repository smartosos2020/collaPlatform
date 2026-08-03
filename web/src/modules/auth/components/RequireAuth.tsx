import { useQuery } from '@tanstack/react-query'
import { Button, Result, Spin } from 'antd'
import { useEffect, type ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'

import { getCurrentUser } from '../api/authApi'
import { useAuthStore } from '../authStore'
import { isSessionExpiredError } from '../sessionError'

export function RequireAuth({ children }: { children: ReactNode }) {
  const location = useLocation()
  const accessToken = useAuthStore((state) => state.accessToken)
  const contextVersion = useAuthStore((state) => state.contextVersion)
  const setCurrentUser = useAuthStore((state) => state.setCurrentUser)
  const clearAuth = useAuthStore((state) => state.clearAuth)

  const meQuery = useQuery({
    queryKey: ['auth', 'me', contextVersion],
    queryFn: getCurrentUser,
    enabled: Boolean(accessToken),
    retry: false,
  })

  useEffect(() => {
    if (meQuery.data) {
      setCurrentUser(meQuery.data)
    }
  }, [meQuery.data, setCurrentUser])

  // 只有明确的会话失效（401/403）才清登录态；网络抖动/5xx 不得登出用户。
  // 注：httpClient 在 401 时会先尝试单飞刷新，走到这里说明刷新也失败了。
  useEffect(() => {
    if (meQuery.isError && isSessionExpiredError(meQuery.error)) {
      clearAuth()
    }
  }, [clearAuth, meQuery.error, meQuery.isError])

  if (!accessToken) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  if (meQuery.isLoading) {
    return (
      <main className="auth-loading">
        <Spin />
      </main>
    )
  }

  if (meQuery.isError) {
    if (isSessionExpiredError(meQuery.error)) {
      return <Navigate to="/login" state={{ from: location }} replace />
    }
    return (
      <main className="auth-loading">
        <Result
          status="warning"
          title="暂时无法验证登录状态"
          subTitle="网络或服务异常，你的登录信息已保留，请稍后重试。"
          extra={(
            <Button type="primary" onClick={() => void meQuery.refetch()}>
              重试
            </Button>
          )}
        />
      </main>
    )
  }

  return children
}
