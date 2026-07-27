import { apiGet, apiPost } from '../../../shared/api/httpClient'
import type { ApprovalTaskSummary } from '../../approvals/api/approvalsApi'
import type { BaseSummary } from '../../bases/api/basesApi'
import type { ConversationSummary } from '../../messenger/api/messengerApi'
import type { NotificationItem } from '../../notifications/api/notificationsApi'
import type { PlatformObjectSummary } from '../../platform/api/platformObjectsApi'
import type { IssueSummary } from '../../projects/api/projectsApi'

export type UserWorkspaceDashboardView = {
  personalWork: PersonalWorkPage
  myIssues: IssueSummary[]
  approvalTodos: ApprovalTaskSummary[]
  unreadMessageCount: number
  unreadConversations: ConversationSummary[]
  unreadNotificationCount: number
  latestNotifications: NotificationItem[]
  recentKnowledgeContents: PlatformObjectSummary[]
  recentBases: BaseSummary[]
  recentObjects: PlatformObjectSummary[]
  favoriteObjects: PlatformObjectSummary[]
  draftSummaries: DraftSummary[]
  dashboardLayout: DashboardLayout
  navigationSummary?: {
    issueCount: number
    knowledgeContentCount: number
    baseCount: number
    unreadConversationCount: number
    unreadNotificationCount: number
  }
  availableActions?: string[]
}

export type DraftSummary = {
  draftId: string
  spaceId: string
  spaceName: string
  typeId: string
  typeName: string
  status: 'editing' | 'validating' | 'valid' | 'invalid'
  version: number
  updatedAt: string
  recoveryPath: string
}

export type DashboardLayout = {
  version: number
  cards: Array<{
    cardKey: string
    title: string
    position: number
    hidden: boolean
    configurable: boolean
  }>
  updatedAt: string
}

export type PersonalWorkBucket = 'todo' | 'responsible' | 'participating' | 'watching'

export type PersonalWorkItem = {
  workItemId: string
  spaceId: string
  spaceName: string
  typeKey: string
  typeName: string
  displayKey: string
  title: string
  lifecycle: string
  version: number
  updatedAt: string
  reasons: Array<{
    bucket: PersonalWorkBucket
    source: 'node_task' | 'participant'
    sourceState: string
    sourceVersion: number
    dueAt?: string | null
  }>
  capabilities: string[]
  deepLink: string
}

export type PersonalWorkPage = {
  buckets: Array<{
    bucket: PersonalWorkBucket
    visibleCount: number
    items: PersonalWorkItem[]
  }>
  nextCursor?: string | null
  truncated: boolean
  generatedAt: string
}

export type WorkspaceDashboard = UserWorkspaceDashboardView

export function getWorkspaceDashboard() {
  return apiGet<UserWorkspaceDashboardView>('/workspace/dashboard')
}

export function updateWorkspaceDashboardLayout(
  requestId: string,
  expectedVersion: number,
  cards: DashboardLayout['cards'],
) {
  return apiPost<DashboardLayout>('/platform/personalization/dashboard', {
    requestId,
    expectedVersion,
    cards: cards.map(({ cardKey, position, hidden }) => ({ cardKey, position, hidden })),
  })
}
