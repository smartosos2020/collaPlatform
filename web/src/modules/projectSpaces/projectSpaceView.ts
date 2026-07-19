export function roleLabel(role?: string | null) {
  return ({ owner: 'Owner', admin: '空间管理员', member: '成员', guest: '访客' } as Record<string, string>)[role ?? ''] ?? '非成员'
}

export function visibilityLabel(visibility: string) {
  return visibility === 'workspace' ? '企业内可发现' : '仅成员可见'
}

export function statusLabel(status: string) {
  return ({ active: '启用', disabled: '停用', archived: '已归档' } as Record<string, string>)[status] ?? status
}

export function formatTime(value?: string | null) {
  return value ? new Date(value).toLocaleString() : '-'
}

export function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message.replace(/\s*\(\d{3}\)$/, '') : fallback
}
