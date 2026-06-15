import { apiDelete, apiGet, apiPatch, apiPost } from '../../../shared/api/httpClient'
import type { PlatformObjectSummary } from '../../platform/api/platformObjectsApi'

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

export type MessageSummary = {
  id: string
  conversationId: string
  senderId: string
  senderName: string
  messageType: string
  content: string
  clientMessageId: string
  createdAt: string
  editedAt?: string | null
  revokedAt?: string | null
  pinnedAt?: string | null
  pinnedBy?: string | null
  mentions: MessageMention[]
  links: MessageLink[]
  reactions: Array<{ emoji: string; count: number; reactedByMe: boolean }>
}

export type ConversationSummary = {
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
}

export type ConversationDetail = ConversationSummary & {
  members: ConversationMember[]
}

export type MessagePage = {
  items: MessageSummary[]
  nextCursor?: string | null
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
  return apiGet<ConversationSummary[]>('/conversations')
}

export function createConversation(request: { conversationType: string; title?: string; memberIds: string[] }) {
  return apiPost<ConversationDetail>('/conversations', request)
}

export function getConversation(conversationId: string) {
  return apiGet<ConversationDetail>(`/conversations/${conversationId}`)
}

export function addConversationMembers(conversationId: string, memberIds: string[]) {
  return apiPost<ConversationDetail>(`/conversations/${conversationId}/members`, { memberIds })
}

export function removeConversationMember(conversationId: string, memberId: string) {
  return apiDelete<ConversationDetail>(`/conversations/${conversationId}/members/${memberId}`)
}

export function leaveConversation(conversationId: string) {
  return apiPost<void>(`/conversations/${conversationId}/leave`, {})
}

export function closeConversation(conversationId: string) {
  return apiPost<void>(`/conversations/${conversationId}/close`, {})
}

export function muteConversation(conversationId: string, muted: boolean) {
  return apiPost<ConversationDetail>(`/conversations/${conversationId}/mute`, { muted })
}

export function pinConversation(conversationId: string, pinned: boolean) {
  return apiPost<ConversationDetail>(`/conversations/${conversationId}/pin`, { pinned })
}

export function listMessages(conversationId: string, beforeId?: string | null) {
  const params = new URLSearchParams({ limit: '50' })
  if (beforeId) {
    params.set('beforeId', beforeId)
  }
  return apiGet<MessagePage>(`/conversations/${conversationId}/messages?${params}`)
}

export function sendMessage(conversationId: string, content: string) {
  return apiPost<MessageSummary>(`/conversations/${conversationId}/messages`, {
    clientMessageId: crypto.randomUUID(),
    messageType: 'text',
    content,
  })
}

export function editMessage(conversationId: string, messageId: string, content: string) {
  return apiPatch<MessageSummary>(`/conversations/${conversationId}/messages/${messageId}`, { content })
}

export function revokeMessage(conversationId: string, messageId: string) {
  return apiPost<MessageSummary>(`/conversations/${conversationId}/messages/${messageId}/revoke`, {})
}

export function pinMessage(conversationId: string, messageId: string, pinned: boolean) {
  return apiPost<MessageSummary>(`/conversations/${conversationId}/messages/${messageId}/pin`, { pinned })
}

export function toggleReaction(conversationId: string, messageId: string, emoji: string) {
  return apiPost<MessageSummary>(`/conversations/${conversationId}/messages/${messageId}/reactions`, { emoji })
}

export function markConversationRead(conversationId: string, messageId?: string) {
  return apiPost<UnreadState>(`/conversations/${conversationId}/read`, { messageId })
}

export function listDirectoryMembers() {
  return apiGet<MemberSummary[]>('/members')
}
