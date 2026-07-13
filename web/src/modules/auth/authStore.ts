import { create } from 'zustand'

const ACCESS_TOKEN_KEY = 'colla.accessToken'
const REFRESH_TOKEN_KEY = 'colla.refreshToken'

export type CurrentUser = {
  id: string
  workspaceId: string
  username: string
  displayName: string
  avatarFileId?: string | null
  email?: string
  roles: string[]
  permissions: string[]
}

type AuthState = {
  accessToken: string | null
  refreshToken: string | null
  currentUser: CurrentUser | null
  setTokens: (accessToken: string, refreshToken: string) => void
  setCurrentUser: (currentUser: CurrentUser | null) => void
  clearAuth: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: localStorage.getItem(ACCESS_TOKEN_KEY),
  refreshToken: localStorage.getItem(REFRESH_TOKEN_KEY),
  currentUser: null,
  setTokens: (accessToken, refreshToken) => {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
    set({ accessToken, refreshToken })
  },
  setCurrentUser: (currentUser) => set({ currentUser }),
  clearAuth: () => {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    set({ accessToken: null, refreshToken: null, currentUser: null })
  },
}))
