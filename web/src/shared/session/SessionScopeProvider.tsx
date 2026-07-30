import type { ReactNode } from 'react'

import {
  SessionScopeContext,
  type SessionScope,
} from './SessionScopeContext'

export function SessionScopeProvider({
  scope,
  children,
}: {
  scope: SessionScope | null
  children: ReactNode
}) {
  return (
    <SessionScopeContext.Provider value={scope}>
      {children}
    </SessionScopeContext.Provider>
  )
}
