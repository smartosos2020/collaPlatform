const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? 'http://127.0.0.1:8080/api'

export type AuthTokens = {
  accessToken: string
  refreshToken: string
  deviceId: string
}

export type ConversationSummary = {
  id: string
  title: string
  unreadCount: number
  lastMessage?: { content: string } | null
}

export type NotificationItem = {
  id: string
  title: string
  body?: string | null
  readAt?: string | null
  webPath?: string | null
}

export type ApprovalTaskSummary = {
  id: string
  instanceId: string
  instanceTitle: string
  formName: string
  applicantName: string
  status: string
}

export type ApprovalInstanceDetail = {
  instance: {
    id: string
    title: string
    status: string
    formName: string
    applicantName: string
  }
  payload: Record<string, unknown>
}

export type IssueDetail = {
  issue: {
    id: string
    issueKey: string
    title: string
    status: string
    priority: string
  }
}

export async function login(username: string, password: string, deviceFingerprint: string) {
  return request<AuthTokens>('/auth/login', {
    method: 'POST',
    body: {
      username,
      password,
      deviceType: 'android',
      deviceFingerprint,
      deviceName: 'Expo Spike',
      appVersion: 'm8-spike',
    },
  })
}

export function listConversations(accessToken: string) {
  return request<ConversationSummary[]>('/conversations', { accessToken })
}

export function listNotifications(accessToken: string) {
  return request<NotificationItem[]>('/notifications?limit=50', { accessToken })
}

export function listApprovalTodos(accessToken: string) {
  return request<ApprovalTaskSummary[]>('/approvals/todos', { accessToken })
}

export function getApproval(accessToken: string, approvalId: string) {
  return request<ApprovalInstanceDetail>(`/approvals/instances/${approvalId}`, { accessToken })
}

export function approveApproval(accessToken: string, approvalId: string) {
  return request<ApprovalInstanceDetail>(`/approvals/instances/${approvalId}/approve`, {
    method: 'POST',
    accessToken,
    body: { comment: '移动端通过' },
  })
}

export function rejectApproval(accessToken: string, approvalId: string) {
  return request<ApprovalInstanceDetail>(`/approvals/instances/${approvalId}/reject`, {
    method: 'POST',
    accessToken,
    body: { comment: '移动端拒绝' },
  })
}

export function getIssue(accessToken: string, issueId: string) {
  return request<IssueDetail>(`/issues/${issueId}`, { accessToken })
}

export function registerPushToken(accessToken: string, deviceId: string, token: string) {
  return request(`/devices/${deviceId}/push-token`, {
    method: 'POST',
    accessToken,
    body: { provider: 'fake', token },
  })
}

async function request<T>(
  path: string,
  options: { method?: string; accessToken?: string; body?: unknown } = {},
): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (options.accessToken) {
    headers.Authorization = `Bearer ${options.accessToken}`
  }
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })
  if (!response.ok) {
    throw new Error(`API ${response.status}`)
  }
  const text = await response.text()
  return text ? JSON.parse(text) as T : undefined as T
}
