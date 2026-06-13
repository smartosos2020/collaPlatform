import { apiGet, apiPatch, apiPost } from '../../../shared/api/httpClient'

export type ProjectSummary = {
  id: string
  projectKey: string
  name: string
  description?: string | null
  status: string
  conversationId?: string | null
  memberCount: number
  openIssueCount: number
  createdAt: string
  updatedAt: string
}

export type ProjectMember = {
  userId: string
  username: string
  displayName: string
  projectRole: string
  joinedAt: string
}

export type ProjectDetail = ProjectSummary & {
  members: ProjectMember[]
}

export type IssueSummary = {
  id: string
  projectId: string
  projectKey: string
  issueKey: string
  issueType: 'requirement' | 'task' | 'bug'
  title: string
  description?: string | null
  priority: 'low' | 'medium' | 'high' | 'urgent'
  status: 'open' | 'in_progress' | 'resolved' | 'closed'
  assigneeId?: string | null
  assigneeName?: string | null
  reporterId: string
  reporterName: string
  dueAt?: string | null
  createdAt: string
  updatedAt: string
}

export type IssueComment = {
  id: string
  issueId: string
  authorId: string
  authorName: string
  content: string
  createdAt: string
}

export type IssueAttachment = {
  id: string
  issueId: string
  fileId: string
  fileName: string
  createdBy: string
  createdAt: string
}

export type IssueActivity = {
  id: string
  issueId: string
  actorId?: string | null
  actorName?: string | null
  action: string
  fromValue?: string | null
  toValue?: string | null
  createdAt: string
}

export type IssueDetail = {
  issue: IssueSummary
  comments: IssueComment[]
  attachments: IssueAttachment[]
  activities: IssueActivity[]
}

export type CountBucket = {
  key: string
  label: string
  count: number
}

export type ProjectStats = {
  projectId: string
  byStatus: CountBucket[]
  byAssignee: CountBucket[]
  byIteration: CountBucket[]
  overdueCount: number
}

export type MemberSummary = {
  id: string
  username: string
  displayName: string
  status: 'active' | 'disabled'
}

export function listProjects() {
  return apiGet<ProjectSummary[]>('/projects')
}

export function createProject(request: { projectKey?: string; name: string; description?: string; memberIds: string[] }) {
  return apiPost<ProjectDetail>('/projects', request)
}

export function getProject(projectId: string) {
  return apiGet<ProjectDetail>(`/projects/${projectId}`)
}

export function getProjectStats(projectId: string) {
  return apiGet<ProjectStats>(`/projects/${projectId}/stats`)
}

export function listIssues(projectId: string) {
  return apiGet<IssueSummary[]>(`/projects/${projectId}/issues`)
}

export function createIssue(
  projectId: string,
  request: {
    issueType: string
    title: string
    description?: string
    priority?: string
    assigneeId?: string
    dueAt?: string
  },
) {
  return apiPost<IssueDetail>(`/projects/${projectId}/issues`, request)
}

export function getIssue(issueId: string) {
  return apiGet<IssueDetail>(`/issues/${issueId}`)
}

export function updateIssue(
  issueId: string,
  request: {
    title?: string
    description?: string
    priority?: string
    assigneeId?: string
    dueAt?: string
  },
) {
  return apiPatch<IssueDetail>(`/issues/${issueId}`, request)
}

export function transitionIssue(issueId: string, status: string) {
  return apiPost<IssueDetail>(`/issues/${issueId}/transition`, { status })
}

export function addIssueComment(issueId: string, content: string) {
  return apiPost<IssueDetail>(`/issues/${issueId}/comments`, { content })
}

export function addIssueAttachment(issueId: string, fileId: string) {
  return apiPost<IssueDetail>(`/issues/${issueId}/attachments`, { fileId })
}

export function listDirectoryMembers() {
  return apiGet<MemberSummary[]>('/members')
}
