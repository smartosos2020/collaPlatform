import {
  BugOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  PlusOutlined,
  ProjectOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  App as AntdApp,
  Avatar,
  Button,
  Card,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

import {
  addIssueComment,
  createIssue,
  createProject,
  getIssue,
  getProject,
  getProjectStats,
  listDirectoryMembers,
  listIssues,
  listProjects,
  transitionIssue,
  type IssueDetail,
  type IssueSummary,
  type ProjectSummary,
} from '../api/projectsApi'

type CreateProjectForm = {
  projectKey?: string
  name: string
  description?: string
  memberIds: string[]
}

type CreateIssueForm = {
  issueType: 'requirement' | 'task' | 'bug'
  title: string
  description?: string
  priority: 'low' | 'medium' | 'high' | 'urgent'
  assigneeId?: string
}

const statusColumns = [
  { key: 'open', title: '待处理', icon: <ClockCircleOutlined /> },
  { key: 'in_progress', title: '处理中', icon: <ProjectOutlined /> },
  { key: 'resolved', title: '已解决', icon: <CheckCircleOutlined /> },
  { key: 'closed', title: '已关闭', icon: <CheckCircleOutlined /> },
] as const

const nextStatuses: Record<string, string[]> = {
  open: ['in_progress', 'resolved', 'closed'],
  in_progress: ['open', 'resolved', 'closed'],
  resolved: ['in_progress', 'closed'],
  closed: ['open'],
}

export function ProjectsPage() {
  const { projectId, issueId } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { message } = AntdApp.useApp()
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null)
  const [selectedIssueId, setSelectedIssueId] = useState<string | null>(null)
  const [projectModalOpen, setProjectModalOpen] = useState(false)
  const [issueModalOpen, setIssueModalOpen] = useState(false)
  const [commentDraft, setCommentDraft] = useState('')
  const [projectForm] = Form.useForm<CreateProjectForm>()
  const [issueForm] = Form.useForm<CreateIssueForm>()

  const projectsQuery = useQuery({ queryKey: ['projects'], queryFn: listProjects })
  const membersQuery = useQuery({ queryKey: ['members', 'directory'], queryFn: listDirectoryMembers })
  const routedIssueId = issueId ?? selectedIssueId
  const activeProjectId = projectId ?? selectedProjectId ?? projectsQuery.data?.[0]?.id ?? null
  const projectQuery = useQuery({
    queryKey: ['projects', activeProjectId],
    queryFn: () => getProject(activeProjectId || ''),
    enabled: Boolean(activeProjectId),
  })
  const statsQuery = useQuery({
    queryKey: ['projects', activeProjectId, 'stats'],
    queryFn: () => getProjectStats(activeProjectId || ''),
    enabled: Boolean(activeProjectId),
  })
  const issuesQuery = useQuery({
    queryKey: ['projects', activeProjectId, 'issues'],
    queryFn: () => listIssues(activeProjectId || ''),
    enabled: Boolean(activeProjectId),
  })
  const issueQuery = useQuery({
    queryKey: ['issues', routedIssueId],
    queryFn: () => getIssue(routedIssueId || ''),
    enabled: Boolean(routedIssueId),
  })

  useEffect(() => {
    const first = projectsQuery.data?.[0]
    if (!projectId && !issueId && !selectedProjectId && first) {
      navigate(`/projects/${first.id}`, { replace: true })
    }
  }, [issueId, navigate, projectId, projectsQuery.data, selectedProjectId])

  const refreshProject = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['projects'] }),
      queryClient.invalidateQueries({ queryKey: ['projects', activeProjectId] }),
      queryClient.invalidateQueries({ queryKey: ['projects', activeProjectId, 'stats'] }),
      queryClient.invalidateQueries({ queryKey: ['projects', activeProjectId, 'issues'] }),
      routedIssueId ? queryClient.invalidateQueries({ queryKey: ['issues', routedIssueId] }) : Promise.resolve(),
    ])
  }

  const createProjectMutation = useMutation({
    mutationFn: createProject,
    onSuccess: async (project) => {
      setProjectModalOpen(false)
      projectForm.resetFields()
      setSelectedProjectId(project.id)
      navigate(`/projects/${project.id}`)
      await refreshProject()
    },
  })

  const createIssueMutation = useMutation({
    mutationFn: (values: CreateIssueForm) => createIssue(activeProjectId || '', values),
    onSuccess: async (detail) => {
      setIssueModalOpen(false)
      issueForm.resetFields()
      setSelectedIssueId(detail.issue.id)
      navigate(`/issues/${detail.issue.id}`)
      await refreshProject()
    },
  })

  const transitionMutation = useMutation({
    mutationFn: ({ issue, status }: { issue: IssueSummary; status: string }) => transitionIssue(issue.id, status),
    onSuccess: async (detail) => {
      setSelectedIssueId(detail.issue.id)
      await refreshProject()
    },
  })

  const commentMutation = useMutation({
    mutationFn: () => addIssueComment(selectedIssueId || '', commentDraft),
    onSuccess: async () => {
      setCommentDraft('')
      await refreshProject()
    },
  })

  const projects = projectsQuery.data ?? []
  const issues = issuesQuery.data ?? []
  const activeProject = projectQuery.data
  const selectedIssue = issueQuery.data
  const stats = statsQuery.data

  const openIssue = useCallback((issue: IssueSummary) => {
    setSelectedIssueId(issue.id)
    navigate(`/issues/${issue.id}`)
  }, [navigate])

  const tableColumns: ColumnsType<IssueSummary> = useMemo(
    () => [
      { title: '编号', dataIndex: 'issueKey', width: 100 },
      {
        title: '标题',
        dataIndex: 'title',
        render: (_, issue) => (
          <Button type="link" onClick={() => openIssue(issue)}>
            {issue.title}
          </Button>
        ),
      },
      { title: '类型', dataIndex: 'issueType', width: 110, render: (value) => <IssueTypeTag type={value} /> },
      { title: '状态', dataIndex: 'status', width: 120, render: (value) => <IssueStatusTag status={value} /> },
      { title: '优先级', dataIndex: 'priority', width: 100, render: (value) => <PriorityTag priority={value} /> },
      { title: '负责人', dataIndex: 'assigneeName', width: 140, render: (value) => value || '未指派' },
    ],
    [openIssue],
  )

  const memberOptions = (membersQuery.data ?? []).map((member) => ({
    value: member.id,
    label: `${member.displayName} @${member.username}`,
  }))

  return (
    <div className="project-workspace">
      <aside className="project-sidebar">
        <Space className="project-toolbar">
          <Typography.Title level={3}>项目</Typography.Title>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setProjectModalOpen(true)} />
        </Space>
        <div className="project-list">
          {projects.length === 0 ? <Empty description="暂无项目" /> : null}
          {projects.map((project) => (
            <ProjectListItem
              key={project.id}
              project={project}
              active={project.id === activeProjectId}
              onClick={() => {
                setSelectedProjectId(project.id)
                navigate(`/projects/${project.id}`)
              }}
            />
          ))}
        </div>
      </aside>

      <main className="project-main">
        {activeProject ? (
          <>
            <Space className="project-detail-header">
              <Space orientation="vertical" size={2}>
                <Space>
                  <Typography.Title level={2}>{activeProject.name}</Typography.Title>
                  <Tag>{activeProject.projectKey}</Tag>
                  <Tag color="green">{activeProject.status}</Tag>
                </Space>
                <Typography.Text type="secondary">{activeProject.description || '暂无项目描述'}</Typography.Text>
              </Space>
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setIssueModalOpen(true)}>
                新建事项
              </Button>
            </Space>

            <section className="project-stats">
              <Card title="成员">{activeProject.members.length}</Card>
              <Card title="未关闭事项">{activeProject.openIssueCount}</Card>
              <Card title="延期事项">{stats?.overdueCount ?? 0}</Card>
              <Card title="按状态">
                <Space wrap>
                  {(stats?.byStatus ?? []).map((bucket) => (
                    <Tag key={bucket.key}>{statusText(bucket.key)} {bucket.count}</Tag>
                  ))}
                </Space>
              </Card>
              <Card title="按负责人">
                <Space wrap>
                  {(stats?.byAssignee ?? []).map((bucket) => (
                    <Tag key={bucket.key}>{bucket.label} {bucket.count}</Tag>
                  ))}
                </Space>
              </Card>
              <Card title="按迭代">
                <Space wrap>
                  {(stats?.byIteration ?? []).map((bucket) => (
                    <Tag key={bucket.key}>{bucket.label} {bucket.count}</Tag>
                  ))}
                </Space>
              </Card>
            </section>

            <section className="issue-board">
              {statusColumns.map((column) => (
                <div className="issue-board-column" key={column.key}>
                  <Space className="issue-board-title">
                    {column.icon}
                    <Typography.Text strong>{column.title}</Typography.Text>
                    <Tag>{issues.filter((issue) => issue.status === column.key).length}</Tag>
                  </Space>
                  <div className="issue-board-items">
                    {issues
                      .filter((issue) => issue.status === column.key)
                      .map((issue) => (
                        <IssueBoardCard
                          key={issue.id}
                          issue={issue}
                          onOpen={() => openIssue(issue)}
                          onTransition={(status) => transitionMutation.mutate({ issue, status })}
                        />
                      ))}
                  </div>
                </div>
              ))}
            </section>

            <Card title="事项列表">
              <Table rowKey="id" size="middle" columns={tableColumns} dataSource={issues} pagination={{ pageSize: 8 }} />
            </Card>
          </>
        ) : (
          <div className="project-empty-state">
            <Empty description="创建或选择一个项目" />
          </div>
        )}
      </main>

      <IssueDrawer
        detail={selectedIssue}
        loading={issueQuery.isLoading}
        commentDraft={commentDraft}
        onCommentDraftChange={setCommentDraft}
        onClose={() => {
          setSelectedIssueId(null)
          if (activeProjectId) {
            navigate(`/projects/${activeProjectId}`)
          }
        }}
        onComment={() => {
          if (!commentDraft.trim()) {
            message.warning('请输入评论')
            return
          }
          commentMutation.mutate()
        }}
        commenting={commentMutation.isPending}
        onTransition={(issue, status) => transitionMutation.mutate({ issue, status })}
        transitioning={transitionMutation.isPending}
      />

      <Modal
        title="新建项目"
        open={projectModalOpen}
        confirmLoading={createProjectMutation.isPending}
        onCancel={() => setProjectModalOpen(false)}
        onOk={() => projectForm.validateFields().then((values) => createProjectMutation.mutate(values))}
      >
        <Form form={projectForm} layout="vertical" initialValues={{ memberIds: [] }}>
          <Form.Item label="项目代号" name="projectKey">
            <Input placeholder="例如 COLLA" />
          </Form.Item>
          <Form.Item label="项目名称" name="name" rules={[{ required: true, message: '请输入项目名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="描述" name="description">
            <Input.TextArea autoSize={{ minRows: 3, maxRows: 6 }} />
          </Form.Item>
          <Form.Item label="成员" name="memberIds">
            <Select mode="multiple" showSearch optionFilterProp="label" options={memberOptions} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="新建事项"
        open={issueModalOpen}
        confirmLoading={createIssueMutation.isPending}
        onCancel={() => setIssueModalOpen(false)}
        onOk={() => issueForm.validateFields().then((values) => createIssueMutation.mutate(values))}
      >
        <Form form={issueForm} layout="vertical" initialValues={{ issueType: 'bug', priority: 'medium' }}>
          <Form.Item label="类型" name="issueType" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'requirement', label: '需求' },
                { value: 'task', label: '任务' },
                { value: 'bug', label: 'Bug' },
              ]}
            />
          </Form.Item>
          <Form.Item label="标题" name="title" rules={[{ required: true, message: '请输入标题' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="描述" name="description">
            <Input.TextArea autoSize={{ minRows: 3, maxRows: 8 }} />
          </Form.Item>
          <Form.Item label="优先级" name="priority">
            <Select
              options={[
                { value: 'low', label: '低' },
                { value: 'medium', label: '中' },
                { value: 'high', label: '高' },
                { value: 'urgent', label: '紧急' },
              ]}
            />
          </Form.Item>
          <Form.Item label="负责人" name="assigneeId">
            <Select allowClear showSearch optionFilterProp="label" options={memberOptions} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

function ProjectListItem({ project, active, onClick }: { project: ProjectSummary; active: boolean; onClick: () => void }) {
  return (
    <button className={active ? 'project-list-item active' : 'project-list-item'} onClick={onClick}>
      <ProjectOutlined />
      <span className="project-list-copy">
        <span className="project-list-title">
          <Typography.Text strong>{project.name}</Typography.Text>
          <Tag>{project.projectKey}</Tag>
        </span>
        <Typography.Text type="secondary">{project.openIssueCount} 个未关闭事项</Typography.Text>
      </span>
    </button>
  )
}

function IssueBoardCard({
  issue,
  onOpen,
  onTransition,
}: {
  issue: IssueSummary
  onOpen: () => void
  onTransition: (status: string) => void
}) {
  return (
    <article className="issue-card">
      <Space className="issue-card-header">
        <Tag>{issue.issueKey}</Tag>
        <IssueTypeTag type={issue.issueType} />
      </Space>
      <Button type="link" className="issue-card-title" onClick={onOpen}>
        {issue.title}
      </Button>
      <Space wrap>
        <PriorityTag priority={issue.priority} />
        <Typography.Text type="secondary">{issue.assigneeName || '未指派'}</Typography.Text>
      </Space>
      <Space wrap className="issue-card-actions">
        {(nextStatuses[issue.status] ?? []).map((status) => (
          <Button key={status} size="small" onClick={() => onTransition(status)}>
            {statusText(status)}
          </Button>
        ))}
      </Space>
    </article>
  )
}

function IssueDrawer({
  detail,
  loading,
  commentDraft,
  onCommentDraftChange,
  onClose,
  onComment,
  commenting,
  onTransition,
  transitioning,
}: {
  detail?: IssueDetail
  loading: boolean
  commentDraft: string
  onCommentDraftChange: (value: string) => void
  onClose: () => void
  onComment: () => void
  commenting: boolean
  onTransition: (issue: IssueSummary, status: string) => void
  transitioning: boolean
}) {
  const issue = detail?.issue
  return (
    <Drawer title={issue ? `${issue.issueKey} ${issue.title}` : '事项详情'} open={Boolean(detail) || loading} onClose={onClose} size="large">
      {issue ? (
        <Space orientation="vertical" size={16} className="issue-drawer-stack">
          <Space wrap>
            <IssueTypeTag type={issue.issueType} />
            <IssueStatusTag status={issue.status} />
            <PriorityTag priority={issue.priority} />
            <Tag>{issue.assigneeName || '未指派'}</Tag>
          </Space>
          <Typography.Paragraph>{issue.description || '暂无描述'}</Typography.Paragraph>
          <Space wrap>
            {(nextStatuses[issue.status] ?? []).map((status) => (
              <Button key={status} loading={transitioning} onClick={() => onTransition(issue, status)}>
                流转到{statusText(status)}
              </Button>
            ))}
          </Space>
          <Card title="评论">
            <Space orientation="vertical" className="issue-drawer-stack">
              {detail.comments.length === 0 ? <Empty description="暂无评论" /> : null}
              {detail.comments.map((comment) => (
                <div className="issue-comment" key={comment.id}>
                  <Avatar>{comment.authorName.slice(0, 1)}</Avatar>
                  <div>
                    <Space>
                      <Typography.Text strong>{comment.authorName}</Typography.Text>
                      <Typography.Text type="secondary">{new Date(comment.createdAt).toLocaleString()}</Typography.Text>
                    </Space>
                    <Typography.Paragraph>{comment.content}</Typography.Paragraph>
                  </div>
                </div>
              ))}
              <Input.TextArea
                value={commentDraft}
                placeholder="输入评论，使用 @username 提醒项目成员"
                autoSize={{ minRows: 3, maxRows: 6 }}
                onChange={(event) => onCommentDraftChange(event.target.value)}
              />
              <Button type="primary" loading={commenting} onClick={onComment}>
                发布评论
              </Button>
            </Space>
          </Card>
          <Card title="附件">
            {detail.attachments.length === 0 ? <Empty description="暂无附件" /> : null}
            {detail.attachments.map((attachment) => (
              <Typography.Text key={attachment.id}>{attachment.fileName}</Typography.Text>
            ))}
          </Card>
          <Card title="动态">
            <Space orientation="vertical" className="issue-drawer-stack">
              {detail.activities.map((activity) => (
                <Typography.Text key={activity.id}>
                  {activity.actorName || '系统'} {activity.action}
                  {activity.fromValue || activity.toValue ? `：${activity.fromValue || '-'} -> ${activity.toValue || '-'}` : ''}
                </Typography.Text>
              ))}
            </Space>
          </Card>
        </Space>
      ) : null}
    </Drawer>
  )
}

function IssueTypeTag({ type }: { type: string }) {
  const color = type === 'bug' ? 'red' : type === 'requirement' ? 'purple' : 'blue'
  return <Tag color={color}>{type === 'bug' ? <BugOutlined /> : null} {type}</Tag>
}

function IssueStatusTag({ status }: { status: string }) {
  const color = status === 'closed' ? 'default' : status === 'resolved' ? 'green' : status === 'in_progress' ? 'blue' : 'orange'
  return <Tag color={color}>{statusText(status)}</Tag>
}

function PriorityTag({ priority }: { priority: string }) {
  const color = priority === 'urgent' ? 'red' : priority === 'high' ? 'volcano' : priority === 'medium' ? 'gold' : 'default'
  return <Tag color={color}>{priority}</Tag>
}

function statusText(status: string) {
  return {
    open: '待处理',
    in_progress: '处理中',
    resolved: '已解决',
    closed: '已关闭',
  }[status] ?? status
}
