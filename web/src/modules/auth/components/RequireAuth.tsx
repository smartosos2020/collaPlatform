import { useQuery } from '@tanstack/react-query'
import { Spin } from 'antd'
import { useEffect, type ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'

import { getCurrentUser } from '../api/authApi'
import { useAuthStore } from '../authStore'

export function RequireAuth({ children }: { children: ReactNode }) {
  const location = useLocation()
  const accessToken = useAuthStore((state) => state.accessToken)
  const setCurrentUser = useAuthStore((state) => state.setCurrentUser)
  const clearAuth = useAuthStore((state) => state.clearAuth)

  const meQuery = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: getCurrentUser,
    enabled: Boolean(accessToken),
    retry: false,
  })

  useEffect(() => {
    if (meQuery.data) {
      setCurrentUser(meQuery.data)
    }
  }, [meQuery.data, setCurrentUser])

  useEffect(() => {
    if (meQuery.isError) {
      clearAuth()
    }
  }, [clearAuth, meQuery.isError])

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
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return children
}
