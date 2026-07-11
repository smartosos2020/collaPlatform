import { apiDelete, apiGet, apiPatch, apiPost } from '../../../shared/api/httpClient'
import type { PlatformObjectSummary } from '../../platform/api/platformObjectsApi'
import type { IssueDetail } from '../../projects/api/projectsApi'

export type ConversationMember = {
  userId: string
  username: string
  displayName: string
  memberRole: string
  joinedAt: string
}

export type MessageMention = {
  userId: string
  username: string
  displayName: string
}

export type MessageLink = {
  id: string
  sourceUrl: string
  targetType?: string | null
  targetId?: string | null
  webPath?: string | null
  deepLink?: string | null
  summary?: PlatformObjectSummary | null
}

export type UserMessageView = {
  id: string
  conversationId: string
  senderId: string
  senderName: string
  messageType: string
  content: string
  clientMessageId: string
  messageSeq: number
  createdAt: string
  editedAt?: string | null
  revokedAt?: string | null
  pinnedAt?: string | null
  pinnedBy?: string | null
  mentions: MessageMention[]
  links: MessageLink[]
  reactions: Array<{ emoji: string; count: number; reactedByMe: boolean }>
  availableActions?: string[]
}

export type MessageSummary = UserMessageView

export type UserConversationView = {
  id: string
  conversationType: string
  title: string
  memberCount: number
  muted: boolean
  pinnedAt?: string | null
  lastMessage?: MessageSummary | null
  unreadCount: number
  lastMessageAt?: string | null
  createdAt: string
  reminder?: {
    unreadCount: number
    muted: boolean
    pinned: boolean
  }
  availableActions?: string[]
}

export type ConversationSummary = UserConversationView

export type UserConversationDetailView = UserConversationView & {
  members: ConversationMember[]
}

export type ConversationDetail = UserConversationDetailView

export type UserMessagePageView = {
  items: UserMessageView[]
  nextCursor?: string | null
}

export type MessagePage = UserMessagePageView

export type ConvertMessageToIssueRequest = {
  projectId: string
  issueType: 'requirement' | 'task' | 'bug'
  title?: string
  description?: string
  priority?: string
  assigneeId?: string
  dueAt?: string
}

export type UnreadState = {
  conversationId: string
  unreadCount: number
  totalUnreadCount: number
}

export type MemberSummary = {
  id: string
  username: string
  displayName: string
  email?: string
  status: 'active' | 'disabled'
}

export function listConversations() {
  return apiGet<UserConversationView[]>('/conversations')
}

export function createConversation(request: { conversationType: string; title?: string; memberIds: string[] }) {
  return apiPost<UserConversationDetailView>('/conversations', request)
}

export function getConversation(conversationId: string) {
  return apiGet<UserConversationDetailView>(`/conversations/${conversationId}`)
}

export function addConversationMembers(conversationId: string, memberIds: string[]) {
  return apiPost<UserConversationDetailView>(`/conversations/${conversationId}/members`, { memberIds })
}

export function removeConversationMember(conversationId: string, memberId: string) {
  return apiDelete<UserConversationDetailView>(`/conversations/${conversationId}/members/${memberId}`)
}

export function leaveConversation(conversationId: string) {
  return apiPost<void>(`/conversations/${conversationId}/leave`, {})
}

export function closeConversation(conversationId: string) {
  return apiPost<void>(`/conversations/${conversationId}/close`, {})
}

export function muteConversation(conversationId: string, muted: boolean) {
  return apiPost<UserConversationDetailView>(`/conversations/${conversationId}/mute`, { muted })
}

export function pinConversation(conversationId: string, pinned: boolean) {
  return apiPost<UserConversationDetailView>(`/conversations/${conversationId}/pin`, { pinned })
}

export function listMessages(conversationId: string, beforeId?: string | null, afterSeq?: number | null) {
  const params = new URLSearchParams({ limit: '50' })
  if (beforeId) {
    params.set('beforeId', beforeId)
  }
  if (afterSeq !== undefined && afterSeq !== null) {
    params.set('afterSeq', String(afterSeq))
  }
  return apiGet<UserMessagePageView>(`/conversations/${conversationId}/messages?${params}`)
}

export function listMessageContext(conversationId: string, messageId: string) {
  return apiGet<UserMessagePageView>(`/conversations/${conversationId}/messages/${messageId}/context?limit=50`)
}

export function searchConversationMessages(
  conversationId: string,
  request: { q?: string; targetType?: string; limit?: number },
) {
  const params = new URLSearchParams({ limit: String(request.limit ?? 20) })
  if (request.q?.trim()) {
    params.set('q', request.q.trim())
  }
  if (request.targetType) {
    params.set('targetType', request.targetType)
  }
  return apiGet<UserMessagePageView>(`/conversations/${conversationId}/messages/search?${params}`)
}

export function sendMessage(conversationId: string, content: string, clientMessageId: string = crypto.randomUUID()) {
  return apiPost<UserMessageView>(`/conversations/${conversationId}/messages`, {
    clientMessageId,
    messageType: 'text',
    content,
  })
}

export function convertMessageToIssue(
  conversationId: string,
  messageId: string,
  request: ConvertMessageToIssueRequest,
) {
  return apiPost<IssueDetail>(`/conversations/${conversationId}/messages/${messageId}/convert-to-issue`, request)
}

export function convertMessageToKnowledgeContent(
  conversationId: string,
  messageId: string,
  request: { parentId?: string | null; title?: string },
) {
  return apiPost<ConvertedKnowledgeContentDetail>(`/conversations/${conversationId}/messages/${messageId}/convert-to-knowledge-content`, request)
}

type ConvertedKnowledgeContentDetail = {
  item: { id: string }
  context?: { webPath?: string | null } | null
}

export function editMessage(conversationId: string, messageId: string, content: string) {
  return apiPatch<UserMessageView>(`/conversations/${conversationId}/messages/${messageId}`, { content })
}

export function revokeMessage(conversationId: string, messageId: string) {
  return apiPost<UserMessageView>(`/conversations/${conversationId}/messages/${messageId}/revoke`, {})
}

export function pinMessage(conversationId: string, messageId: string, pinned: boolean) {
  return apiPost<UserMessageView>(`/conversations/${conversationId}/messages/${messageId}/pin`, { pinned })
}

export function toggleReaction(conversationId: string, messageId: string, emoji: string) {
  return apiPost<UserMessageView>(`/conversations/${conversationId}/messages/${messageId}/reactions`, { emoji })
}

export function markConversationRead(conversationId: string, messageId?: string) {
  return apiPost<UnreadState>(`/conversations/${conversationId}/read`, { messageId })
}

export function listDirectoryMembers() {
  return apiGet<MemberSummary[]>('/members')
}
