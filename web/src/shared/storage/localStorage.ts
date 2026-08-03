/** Browser storage can throw in private or restricted contexts. */
export function readLocalStorage(key: string): string | null {
  try {
    return globalThis.localStorage?.getItem(key) ?? null
  } catch {
    return null
  }
}

export function writeLocalStorage(key: string, value: string): boolean {
  try {
    globalThis.localStorage?.setItem(key, value)
    return true
  } catch {
    return false
  }
}

export function removeLocalStorage(key: string): boolean {
  try {
    globalThis.localStorage?.removeItem(key)
    return true
  } catch {
    return false
  }
}
