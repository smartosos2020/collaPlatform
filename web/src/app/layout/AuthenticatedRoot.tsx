import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { Outlet } from 'react-router-dom'

import { AppRealtimeBoundary } from '../realtime/AppRealtimeBoundary'
import { RequireAuth } from '../../modules/auth/components/RequireAuth'
import { AUTH_STORAGE_KEYS, useAuthStore } from '../../modules/auth/authStore'

export function AuthenticatedRoot() {
  const queryClient = useQueryClient()
  const syncFromStorage = useAuthStore((state) => state.syncFromStorage)
  const invalidateContext = useAuthStore((state) => state.invalidateContext)

  useEffect(() => {
    const contextStorageKeys: Array<string | null> = [
      AUTH_STORAGE_KEYS.accessToken,
      AUTH_STORAGE_KEYS.refreshToken,
      AUTH_STORAGE_KEYS.deviceFingerprint,
    ]
    const handleStorage = (event: StorageEvent) => {
      if (!contextStorageKeys.includes(event.key)) {
        return
      }
      queryClient.clear()
      if (event.key === AUTH_STORAGE_KEYS.deviceFingerprint) {
        invalidateContext()
      } else {
        syncFromStorage()
      }
    }
    window.addEventListener('storage', handleStorage)
    return () => window.removeEventListener('storage', handleStorage)
  }, [invalidateContext, queryClient, syncFromStorage])

  return (
    <RequireAuth>
      <AppRealtimeBoundary>
        <Outlet />
      </AppRealtimeBoundary>
    </RequireAuth>
  )
}
