import { Outlet } from 'react-router-dom'

import { RequireAuth } from '../../modules/auth/components/RequireAuth'

export function AuthenticatedRoot() {
  return (
    <RequireAuth>
      <Outlet />
    </RequireAuth>
  )
}
