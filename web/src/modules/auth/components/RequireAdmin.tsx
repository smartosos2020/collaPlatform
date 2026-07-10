import type { ReactNode } from 'react'
import { Spin } from 'antd'
import { Navigate } from 'react-router-dom'

import { canAccessAdmin } from '../authorization'
import { useAuthStore } from '../authStore'

export function RequireAdmin({ children }: { children: ReactNode }) {
  const currentUser = useAuthStore((state) => state.currentUser)

  if (!currentUser) {
    return (
      <main className="auth-loading">
        <Spin />
      </main>
    )
  }

  if (!canAccessAdmin(currentUser)) {
    return <Navigate to="/error/403" replace />
  }

  return children
}
