import { apiGet } from '../../../shared/api/httpClient'
import type { ApprovalTaskSummary } from '../../approvals/api/approvalsApi'
import type { BaseSummary } from '../../bases/api/basesApi'
import type { DocumentSummary } from '../../docs/api/docsApi'
import type { ConversationSummary } from '../../messenger/api/messengerApi'
import type { NotificationItem } from '../../notifications/api/notificationsApi'
import type { PlatformObjectSummary } from '../../platform/api/platformObjectsApi'
import type { IssueSummary } from '../../projects/api/projectsApi'

export type UserWorkspaceDashboardView = {
  myIssues: IssueSummary[]
  approvalTodos: ApprovalTaskSummary[]
  unreadMessageCount: number
  unreadConversations: ConversationSummary[]
  unreadNotificationCount: number
  latestNotifications: NotificationItem[]
  recentDocuments: DocumentSummary[]
  recentKnowledgeContents: PlatformObjectSummary[]
  recentBases: BaseSummary[]
  recentObjects: PlatformObjectSummary[]
  favoriteObjects: PlatformObjectSummary[]
  navigationSummary?: {
    issueCount: number
    knowledgeContentCount: number
    baseCount: number
    unreadConversationCount: number
    unreadNotificationCount: number
  }
  availableActions?: string[]
}

export type WorkspaceDashboard = UserWorkspaceDashboardView

export function getWorkspaceDashboard() {
  return apiGet<UserWorkspaceDashboardView>('/workspace/dashboard')
}
