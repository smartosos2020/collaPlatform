import { apiGet } from '../../../shared/api/httpClient'
import type { ApprovalTaskSummary } from '../../approvals/api/approvalsApi'
import type { BaseSummary } from '../../bases/api/basesApi'
import type { DocumentSummary } from '../../docs/api/docsApi'
import type { ConversationSummary } from '../../messenger/api/messengerApi'
import type { NotificationItem } from '../../notifications/api/notificationsApi'
import type { PlatformObjectSummary } from '../../platform/api/platformObjectsApi'
import type { IssueSummary } from '../../projects/api/projectsApi'

export type WorkspaceDashboard = {
  myIssues: IssueSummary[]
  approvalTodos: ApprovalTaskSummary[]
  unreadMessageCount: number
  unreadConversations: ConversationSummary[]
  unreadNotificationCount: number
  latestNotifications: NotificationItem[]
  recentDocuments: DocumentSummary[]
  recentBases: BaseSummary[]
  recentObjects: PlatformObjectSummary[]
  favoriteObjects: PlatformObjectSummary[]
}

export function getWorkspaceDashboard() {
  return apiGet<WorkspaceDashboard>('/workspace/dashboard')
}
