import { create } from 'zustand'

import { readLocalStorage, removeLocalStorage, writeLocalStorage } from '../../shared/storage/localStorage'

const ACCESS_TOKEN_KEY = 'colla.accessToken'
const REFRESH_TOKEN_KEY = 'colla.refreshToken'

export const AUTH_STORAGE_KEYS = {
  accessToken: ACCESS_TOKEN_KEY,
  refreshToken: REFRESH_TOKEN_KEY,
  deviceFingerprint: 'colla.deviceFingerprint',
} as const

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
  contextVersion: number
  setTokens: (accessToken: string, refreshToken: string) => void
  setCurrentUser: (currentUser: CurrentUser | null) => void
  syncFromStorage: () => void
  invalidateContext: () => void
  clearAuth: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: readLocalStorage(ACCESS_TOKEN_KEY),
  refreshToken: readLocalStorage(REFRESH_TOKEN_KEY),
  currentUser: null,
  contextVersion: 0,
  setTokens: (accessToken, refreshToken) => {
    writeLocalStorage(ACCESS_TOKEN_KEY, accessToken)
    writeLocalStorage(REFRESH_TOKEN_KEY, refreshToken)
    set((state) => ({
      accessToken,
      refreshToken,
      currentUser: null,
      contextVersion: state.contextVersion + 1,
    }))
  },
  setCurrentUser: (currentUser) => set({ currentUser }),
  syncFromStorage: () => {
    const accessToken = readLocalStorage(ACCESS_TOKEN_KEY)
    const refreshToken = readLocalStorage(REFRESH_TOKEN_KEY)
    set((state) => {
      if (state.accessToken === accessToken && state.refreshToken === refreshToken) {
        return state
      }
      return {
        accessToken,
        refreshToken,
        currentUser: null,
        contextVersion: state.contextVersion + 1,
      }
    })
  },
  invalidateContext: () => set((state) => ({
    currentUser: null,
    contextVersion: state.contextVersion + 1,
  })),
  clearAuth: () => {
    removeLocalStorage(ACCESS_TOKEN_KEY)
    removeLocalStorage(REFRESH_TOKEN_KEY)
    set((state) => ({
      accessToken: null,
      refreshToken: null,
      currentUser: null,
      contextVersion: state.contextVersion + 1,
    }))
  },
}))
