import { createContext, useContext } from 'react'

export type SessionScope = Readonly<{
  workspaceId: string
  userId: string
}>

export const SessionScopeContext = createContext<SessionScope | null>(null)

export function useSessionScope(): SessionScope | null {
  return useContext(SessionScopeContext)
}
