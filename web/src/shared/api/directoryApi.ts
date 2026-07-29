import { apiGet } from './httpClient'

export type DirectoryMember = {
  id: string
  username: string
  displayName: string
  status: 'active' | 'disabled'
}

export function listDirectoryMembers() {
  return apiGet<DirectoryMember[]>('/members')
}
