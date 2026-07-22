import {
  BugOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  LinkOutlined,
  PlusOutlined,
  ProjectOutlined,
  ShareAltOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  App as AntdApp,
  Alert,
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
  addIssueRelation,
  addIssueVerification,
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
  type IssueFilters,
  type IssueSummary,
  type IssueWorkflowAction,
  type ProjectSummary,
  type TransitionIssueRequest,
} from '../api/projectsApi'
import { resolveLegacyProjectSpace } from '../../projectSpaces/api/projectSpacesApi'
import { ObjectSummaryCard } from '../../platform/components/InternalLinkCard'
import { resolveInternalLink } from '../../platform/api/platformObjectsApi'
import { ResourcePermissionsModal } from '../../permissions/components/ResourcePermissionsModal'

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
  dueAt?: string
}

type WorkflowActionModalState = {
  issue: IssueSummary
  action: IssueWorkflowAction
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

function fallbackWorkflowActions(issue: IssueSummary): IssueWorkflowAction[] {
  const allTypes = ['requirement', 'task', 'bug']
  const definitions: Array<IssueWorkflowAction & { issueTypes: string[]; fromStatuses: string[] }> = [
    {
      key: 'start_progress',
      label: '开始处理',
      targetStatus: 'in_progress',
      requiresReason: false,
      requiresTargetIssue: false,
      requiresDueAt: false,
      description: '进入处理中',
      issueTypes: allTypes,
      fromStatuses: ['open'],
    },
    {
      key: 'return_open',
      label: '退回待处理',
      targetStatus: 'open',
      requiresReason: false,
      requiresTargetIssue: false,
      requiresDueAt: false,
      description: '重新打开或退回待处理',
      issueTypes: allTypes,
      fromStatuses: ['in_progress', 'resolved', 'closed'],
    },
    {
      key: issue.issueType === 'bug' ? 'mark_fixed' : 'resolve',
      label: issue.issueType === 'bug' ? '提交修复' : '标记已解决',
      targetStatus: 'resolved',
      requiresReason: false,
      requiresTargetIssue: false,
      requiresDueAt: false,
      description: issue.issueType === 'bug' ? '修复处理中，等待验证' : '处理完成，等待确认',
      issueTypes: allTypes,
      fromStatuses: ['open', 'in_progress'],
    },
    {
      key: 'close',
      label: '关闭',
      targetStatus: 'closed',
      requiresReason: false,
      requiresTargetIssue: false,
      requiresDueAt: false,
      description: '无需继续处理或已确认完成',
      issueTypes: allTypes,
      fromStatuses: ['open', 'in_progress', 'resolved'],
    },
  ]
  return definitions
    .filter((action) => action.issueTypes.includes(issue.issueType))
    .filter((action) => action.fromStatuses.includes(issue.status))
    .map((action) => ({
      key: action.key,
      label: action.label,
      targetStatus: action.targetStatus,
      requiresReason: action.requiresReason,
      requiresTargetIssue: action.requiresTargetIssue,
      requiresDueAt: action.requiresDueAt,
      description: action.description,
    }))
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
  const [resourcePermissionOpen, setResourcePermissionOpen] = useState(false)
  const [workflowAction, setWorkflowAction] = useState<WorkflowActionModalState | null>(null)
  const [workflowReason, setWorkflowReason] = useState('')
  const [workflowTargetIssueId, setWorkflowTargetIssueId] = useState<string | undefined>()
  const [workflowDueAt, setWorkflowDueAt] = useState('')
  const [commentDraft, setCommentDraft] = useState('')
  const [verificationResult, setVerificationResult] = useState<'passed' | 'failed' | 'blocked'>('passed')
  const [verificationNote, setVerificationNote] = useState('')
  const [verificationEnvironment, setVerificationEnvironment] = useState('')
  const [verificationSteps, setVerificationSteps] = useState('')
  const [verificationFixVersion, setVerificationFixVersion] = useState('')
  const [relationInput, setRelationInput] = useState('')
  const [draggingIssueId, setDraggingIssueId] = useState<string | null>(null)
  const [issueFilters, setIssueFilters] = useState<IssueFilters>({ sort: 'updated_desc' })
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
    queryKey: ['projects', activeProjectId, 'issues', issueFilters],
    queryFn: () => listIssues(activeProjectId || '', issueFilters),
    enabled: Boolean(activeProjectId),
  })
  const issueQuery = useQuery({
    queryKey: ['issues', routedIssueId],
    queryFn: () => getIssue(routedIssueId || ''),
    enabled: Boolean(routedIssueId),
  })
  const legacySpaceQuery = useQuery({
    queryKey: ['projects', activeProjectId, 'legacy-space-resolution'],
    queryFn: () => resolveLegacyProjectSpace(activeProjectId || ''),
    enabled: Boolean(activeProjectId),
    retry: false,
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
    mutationFn: ({ issue, request }: { issue: IssueSummary; request: TransitionIssueRequest }) => transitionIssue(issue.id, request),
    onSuccess: async (detail) => {
      setWorkflowAction(null)
      setWorkflowReason('')
      setWorkflowTargetIssueId(undefined)
      setWorkflowDueAt('')
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

  const verificationMutation = useMutation({
    mutationFn: () =>
      addIssueVerification(selectedIssueId || '', {
        result: verificationResult,
        note: verificationNote,
        environment: verificationEnvironment,
        reproductionSteps: verificationSteps,
        fixVersion: verificationFixVersion,
      }),
    onSuccess: async () => {
      setVerificationResult('passed')
      setVerificationNote('')
      setVerificationEnvironment('')
      setVerificationSteps('')
      setVerificationFixVersion('')
      await refreshProject()
    },
  })

  const relationMutation = useMutation({
    mutationFn: async () => {
      const link = relationInput.trim()
      if (!selectedIssueId || !link) {
        throw new Error('请粘贴内部对象链接')
      }
      const parsed = await resolveInternalLink(link)
      if (!parsed.summary || parsed.summary.accessState !== 'available') {
        throw new Error('链接不可用或无权限')
      }
      return addIssueRelation(selectedIssueId, parsed.summary.objectType, parsed.summary.objectId)
    },
    onSuccess: async () => {
      setRelationInput('')
      await refreshProject()
    },
    onError: (error) => {
      message.error(error instanceof Error ? error.message : '关联失败')
    },
  })

  const projects = projectsQuery.data ?? []
  const issues = useMemo(() => issuesQuery.data ?? [], [issuesQuery.data])
  const activeProject = projectQuery.data
  const selectedIssue = issueQuery.data
  const stats = statsQuery.data
  const issueById = useMemo(() => new Map(issues.map((issue) => [issue.id, issue])), [issues])
  const duplicateTargetOptions = issues
    .filter((issue) => issue.issueType === 'bug' && issue.id !== workflowAction?.issue.id)
    .map((issue) => ({ value: issue.id, label: `${issue.issueKey} ${issue.title}` }))

  const openIssue = useCallback((issue: IssueSummary) => {
    setSelectedIssueId(issue.id)
    navigate(`/issues/${issue.id}`)
  }, [navigate])

  const updateIssueFilter = (key: keyof IssueFilters, value?: string) => {
    setIssueFilters((current) => ({ ...current, [key]: value || undefined }))
  }

  const runWorkflowAction = (issue: IssueSummary, action: IssueWorkflowAction) => {
    if (action.requiresReason || action.requiresTargetIssue || action.requiresDueAt) {
      setWorkflowAction({ issue, action })
      setWorkflowReason('')
      setWorkflowTargetIssueId(undefined)
      setWorkflowDueAt('')
      return
    }
    transitionMutation.mutate({ issue, request: { action: action.key } })
  }

  const submitWorkflowAction = () => {
    if (!workflowAction) {
      return
    }
    if (workflowAction.action.requiresReason && !workflowReason.trim()) {
      message.warning('请输入处理原因')
      return
    }
    if (workflowAction.action.requiresTargetIssue && !workflowTargetIssueId) {
      message.warning('请选择关联的已有 BUG')
      return
    }
    if (workflowAction.action.requiresDueAt && !workflowDueAt) {
      message.warning('请选择新的截止日期')
      return
    }
    transitionMutation.mutate({
      issue: workflowAction.issue,
      request: {
        action: workflowAction.action.key,
        reason: workflowReason,
        targetIssueId: workflowTargetIssueId,
        dueAt: workflowDueAt || undefined,
      },
    })
  }

  const dropIssueToStatus = (status: string) => {
    const issue = draggingIssueId ? issueById.get(draggingIssueId) : undefined
    setDraggingIssueId(null)
    if (!issue || issue.status === status || !(nextStatuses[issue.status] ?? []).includes(status)) {
      return
    }
    transitionMutation.mutate({ issue, request: { status } })
  }

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
              <Space>
                <Button icon={<ShareAltOutlined />} onClick={() => setResourcePermissionOpen(true)}>
                  权限
                </Button>
                <Button type="primary" icon={<PlusOutlined />} onClick={() => setIssueModalOpen(true)}>
                  新建事项
                </Button>
              </Space>
            </Space>

            {legacySpaceQuery.data?.status === 'mapped' && legacySpaceQuery.data.spaceId ? (
              <Alert
                className="project-migration-banner"
                type="info"
                showIcon
                message="该项目已迁移到项目空间"
                action={
                  <Button
                    size="small"
                    type="link"
                    onClick={() => navigate(`/project-spaces/${legacySpaceQuery.data?.spaceId}`)}
                  >
                    前往项目空间
                  </Button>
                }
              />
            ) : null}

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

            <section className="issue-filters">
              <Select
                allowClear
                className="issue-filter-control"
                placeholder="状态"
                value={issueFilters.status}
                onChange={(value) => updateIssueFilter('status', value)}
                options={[
                  { value: 'open', label: '待处理' },
                  { value: 'in_progress', label: '处理中' },
                  { value: 'resolved', label: '已解决' },
                  { value: 'closed', label: '已关闭' },
                ]}
              />
              <Select
                allowClear
                className="issue-filter-control"
                placeholder="类型"
                value={issueFilters.issueType}
                onChange={(value) => updateIssueFilter('issueType', value)}
                options={[
                  { value: 'requirement', label: '需求' },
                  { value: 'task', label: '任务' },
                  { value: 'bug', label: 'Bug' },
                ]}
              />
              <Select
                allowClear
                className="issue-filter-control"
                placeholder="优先级"
                value={issueFilters.priority}
                onChange={(value) => updateIssueFilter('priority', value)}
                options={[
                  { value: 'low', label: '低' },
                  { value: 'medium', label: '中' },
                  { value: 'high', label: '高' },
                  { value: 'urgent', label: '紧急' },
                ]}
              />
              <Select
                allowClear
                showSearch
                className="issue-filter-control issue-filter-assignee"
                optionFilterProp="label"
                placeholder="负责人"
                value={issueFilters.assigneeId}
                onChange={(value) => updateIssueFilter('assigneeId', value)}
                options={memberOptions}
              />
              <Select
                className="issue-filter-control issue-filter-sort"
                value={issueFilters.sort}
                onChange={(value) => updateIssueFilter('sort', value)}
                options={[
                  { value: 'updated_desc', label: '最近更新' },
                  { value: 'created_desc', label: '最近创建' },
                  { value: 'priority_desc', label: '优先级高到低' },
                  { value: 'due_asc', label: '截止日期近到远' },
                ]}
              />
            </section>

            <section className="issue-board">
              {statusColumns.map((column) => (
                <div
                  className="issue-board-column"
                  key={column.key}
                  onDragOver={(event) => event.preventDefault()}
                  onDrop={() => dropIssueToStatus(column.key)}
                >
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
                          onRunAction={(action) => runWorkflowAction(issue, action)}
                          onDragStart={() => setDraggingIssueId(issue.id)}
                          onDragEnd={() => setDraggingIssueId(null)}
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
        verificationResult={verificationResult}
        verificationNote={verificationNote}
        verificationEnvironment={verificationEnvironment}
        verificationSteps={verificationSteps}
        verificationFixVersion={verificationFixVersion}
        relationInput={relationInput}
        onCommentDraftChange={setCommentDraft}
        onVerificationResultChange={setVerificationResult}
        onVerificationNoteChange={setVerificationNote}
        onVerificationEnvironmentChange={setVerificationEnvironment}
        onVerificationStepsChange={setVerificationSteps}
        onVerificationFixVersionChange={setVerificationFixVersion}
        onRelationInputChange={setRelationInput}
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
        onVerify={() => verificationMutation.mutate()}
        verifying={verificationMutation.isPending}
        onAddRelation={() => relationMutation.mutate()}
        relating={relationMutation.isPending}
        onRunAction={runWorkflowAction}
        transitioning={transitionMutation.isPending}
      />

      <Modal
        title={workflowAction ? workflowAction.action.label : '流程动作'}
        open={Boolean(workflowAction)}
        confirmLoading={transitionMutation.isPending}
        onCancel={() => setWorkflowAction(null)}
        onOk={submitWorkflowAction}
      >
        {workflowAction ? (
          <Space orientation="vertical" className="issue-drawer-stack">
            <Typography.Text type="secondary">{workflowAction.action.description}</Typography.Text>
            {workflowAction.action.requiresReason ? (
              <Input.TextArea
                value={workflowReason}
                placeholder="填写处理原因，便于通知、审计和后续复盘"
                autoSize={{ minRows: 3, maxRows: 6 }}
                onChange={(event) => setWorkflowReason(event.target.value)}
              />
            ) : null}
            {workflowAction.action.requiresTargetIssue ? (
              <Select
                showSearch
                optionFilterProp="label"
                value={workflowTargetIssueId}
                placeholder="选择已有 BUG"
                options={duplicateTargetOptions}
                onChange={setWorkflowTargetIssueId}
              />
            ) : null}
            {workflowAction.action.requiresDueAt ? (
              <Input type="date" value={workflowDueAt} onChange={(event) => setWorkflowDueAt(event.target.value)} />
            ) : null}
          </Space>
        ) : null}
      </Modal>

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

      <ResourcePermissionsModal
        open={resourcePermissionOpen}
        resourceType="project"
        resourceId={activeProjectId ?? undefined}
        resourceName={activeProject?.name}
        onClose={() => setResourcePermissionOpen(false)}
      />

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
          <Form.Item label="截止日期" name="dueAt">
            <Input type="date" />
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
  onRunAction,
  onDragStart,
  onDragEnd,
}: {
  issue: IssueSummary
  onOpen: () => void
  onRunAction: (action: IssueWorkflowAction) => void
  onDragStart: () => void
  onDragEnd: () => void
}) {
  const actions = fallbackWorkflowActions(issue)
  return (
    <article className="issue-card" draggable onDragStart={onDragStart} onDragEnd={onDragEnd}>
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
        {actions.slice(0, 3).map((action) => (
          <Button key={action.key} size="small" onClick={() => onRunAction(action)}>
            {action.label}
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
  verificationResult,
  verificationNote,
  verificationEnvironment,
  verificationSteps,
  verificationFixVersion,
  relationInput,
  onCommentDraftChange,
  onVerificationResultChange,
  onVerificationNoteChange,
  onVerificationEnvironmentChange,
  onVerificationStepsChange,
  onVerificationFixVersionChange,
  onRelationInputChange,
  onClose,
  onComment,
  commenting,
  onVerify,
  verifying,
  onAddRelation,
  relating,
  onRunAction,
  transitioning,
}: {
  detail?: IssueDetail
  loading: boolean
  commentDraft: string
  verificationResult: 'passed' | 'failed' | 'blocked'
  verificationNote: string
  verificationEnvironment: string
  verificationSteps: string
  verificationFixVersion: string
  relationInput: string
  onCommentDraftChange: (value: string) => void
  onVerificationResultChange: (value: 'passed' | 'failed' | 'blocked') => void
  onVerificationNoteChange: (value: string) => void
  onVerificationEnvironmentChange: (value: string) => void
  onVerificationStepsChange: (value: string) => void
  onVerificationFixVersionChange: (value: string) => void
  onRelationInputChange: (value: string) => void
  onClose: () => void
  onComment: () => void
  commenting: boolean
  onVerify: () => void
  verifying: boolean
  onAddRelation: () => void
  relating: boolean
  onRunAction: (issue: IssueSummary, action: IssueWorkflowAction) => void
  transitioning: boolean
}) {
  const issue = detail?.issue
  const documentSnippet = issue ? extractDocumentSnippet(issue.description) : ''
  const hasKnowledgeContentRelation = Boolean(detail?.relations.some((relation) => relation.targetType === 'knowledge_content'))
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
          <Card size="small" title="基础信息">
            <div className="issue-meta-grid">
              <span>创建人</span>
              <Typography.Text>{issue.reporterName}</Typography.Text>
              <span>负责人</span>
              <Typography.Text>{issue.assigneeName || '未指派'}</Typography.Text>
              <span>截止日期</span>
              <Typography.Text>{issue.dueAt || '未设置'}</Typography.Text>
              <span>最近更新</span>
              <Typography.Text>{new Date(issue.updatedAt).toLocaleString()}</Typography.Text>
              <span>流程原因</span>
              <Typography.Text>{workflowReasonText(issue.workflowReason) || '未设置'}</Typography.Text>
              <span>处理结论</span>
              <Typography.Text>{resolutionText(issue.resolution) || '未设置'}</Typography.Text>
            </div>
          </Card>
          {issue.workflowNote ? <Typography.Paragraph className="issue-workflow-note">{issue.workflowNote}</Typography.Paragraph> : null}
          <Typography.Paragraph>{issue.description || '暂无描述'}</Typography.Paragraph>
          {hasKnowledgeContentRelation && documentSnippet ? (
            <Card size="small" title="关联知识内容片段">
              <Typography.Paragraph>{documentSnippet}</Typography.Paragraph>
            </Card>
          ) : null}
          <Card size="small" title="流程动作">
            <Space wrap>
              {detail.availableActions.map((action) => (
                <Button key={action.key} loading={transitioning} onClick={() => onRunAction(issue, action)}>
                  {action.label}
                </Button>
              ))}
            </Space>
            {detail.availableActions.length === 0 ? <Empty description="暂无可执行动作" /> : null}
          </Card>
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
          <Card title="关联对象">
            <Space orientation="vertical" className="issue-drawer-stack">
              <Space.Compact className="issue-relation-input">
                <Input
                  value={relationInput}
                  placeholder="粘贴事项、知识内容、表格或消息链接"
                  onChange={(event) => onRelationInputChange(event.target.value)}
                />
                <Button icon={<LinkOutlined />} loading={relating} onClick={onAddRelation}>
                  关联
                </Button>
              </Space.Compact>
              {detail.relations.length === 0 ? <Empty description="暂无关联对象" /> : null}
              <div className="issue-relation-list">
                {detail.relations.map((relation) => (
                  <div className="issue-relation-item" key={relation.id}>
                    <ObjectSummaryCard summary={relation.target} />
                    <Typography.Text type="secondary">
                      {relation.createdByName} 关联于 {new Date(relation.createdAt).toLocaleString()}
                    </Typography.Text>
                  </div>
                ))}
              </div>
            </Space>
          </Card>
          <Card title="验证记录">
            <Space orientation="vertical" className="issue-drawer-stack">
              {issue.issueType === 'bug' ? (
                <Space orientation="vertical" className="issue-drawer-stack">
                  <Select
                    value={verificationResult}
                    onChange={onVerificationResultChange}
                    options={[
                      { value: 'passed', label: '验证通过' },
                      { value: 'failed', label: '验证失败' },
                      { value: 'blocked', label: '验证阻塞' },
                    ]}
                  />
                  <Input
                    value={verificationEnvironment}
                    placeholder="运行环境，例如 Windows / Chrome / 浏览器"
                    onChange={(event) => onVerificationEnvironmentChange(event.target.value)}
                  />
                  <Input
                    value={verificationFixVersion}
                    placeholder="修复版本，例如 v0.27.0"
                    onChange={(event) => onVerificationFixVersionChange(event.target.value)}
                  />
                  <Input.TextArea
                    value={verificationSteps}
                    placeholder="复现步骤"
                    autoSize={{ minRows: 2, maxRows: 5 }}
                    onChange={(event) => onVerificationStepsChange(event.target.value)}
                  />
                  <Input.TextArea
                    value={verificationNote}
                    placeholder="验证结论"
                    autoSize={{ minRows: 2, maxRows: 5 }}
                    onChange={(event) => onVerificationNoteChange(event.target.value)}
                  />
                  <Button type="primary" loading={verifying} onClick={onVerify}>
                    提交验证
                  </Button>
                </Space>
              ) : null}
              {detail.verifications.length === 0 ? <Empty description="暂无验证记录" /> : null}
              {detail.verifications.map((verification) => (
                <div className="issue-comment" key={verification.id}>
                  <Avatar>{verification.verifierName.slice(0, 1)}</Avatar>
                  <div>
                    <Space>
                      <Typography.Text strong>{verification.verifierName}</Typography.Text>
                      <Tag color={verification.result === 'passed' ? 'green' : verification.result === 'failed' ? 'red' : 'orange'}>
                        {verificationText(verification.result)}
                      </Tag>
                      <Typography.Text type="secondary">{new Date(verification.createdAt).toLocaleString()}</Typography.Text>
                    </Space>
                    <Space wrap>
                      {verification.environment ? <Tag>{verification.environment}</Tag> : null}
                      {verification.fixVersion ? <Tag color="blue">{verification.fixVersion}</Tag> : null}
                    </Space>
                    {verification.reproductionSteps ? (
                      <Typography.Paragraph className="issue-verification-block">{verification.reproductionSteps}</Typography.Paragraph>
                    ) : null}
                    {verification.note ? <Typography.Paragraph>{verification.note}</Typography.Paragraph> : null}
                  </div>
                </div>
              ))}
            </Space>
          </Card>
          <Card title="动态">
            <Space orientation="vertical" className="issue-drawer-stack">
              {detail.activities.map((activity) => (
                <Typography.Text key={activity.id}>
                  {activity.actorName || '系统'} {activityText(activity.action)}
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

function verificationText(result: string) {
  return {
    passed: '验证通过',
    failed: '验证失败',
    blocked: '验证阻塞',
  }[result] ?? result
}

function workflowReasonText(reason?: string | null) {
  if (!reason) {
    return ''
  }
  return {
    started: '开始处理',
    reopened: '重新打开',
    resolved: '已解决',
    closed: '已关闭',
    rework: '重新处理',
    info_required: '信息不足',
    scope_changed: '需求变更',
    delayed: '延期',
    canceled: '取消',
    duplicate: '重复',
    cannot_reproduce: '无法复现',
    fixed: '提交修复',
    verification_failed: '验证失败',
    verified: '验证通过',
    start_progress: '开始处理',
    return_open: '退回待处理',
    request_info: '信息不足',
    scope_change: '需求变更',
    delay: '延期',
    cancel: '取消需求',
    mark_duplicate: '重复 BUG',
    mark_fixed: '提交修复',
    verify_failed: '验证失败',
    verify_passed: '验证通过',
  }[reason] ?? reason
}

function resolutionText(resolution?: string | null) {
  if (!resolution) {
    return ''
  }
  return {
    done: '完成',
    fixed: '已修复',
    info_required: '需补充信息',
    scope_changed: '需求变更',
    delayed: '延期',
    canceled: '已取消',
    duplicate: '重复',
    cannot_reproduce: '无法复现',
    verification_failed: '验证失败打回',
    verified: '验证通过',
  }[resolution] ?? resolution
}

function extractDocumentSnippet(description?: string | null) {
  if (!description) {
    return ''
  }
  return description
    .split('\n')
    .filter((line) => line.trim().startsWith('>'))
    .map((line) => line.replace(/^>\s?/, '').trim())
    .join('\n')
    .slice(0, 1000)
}

function activityText(action: string) {
  if (action.startsWith('workflow.')) {
    return workflowReasonText(action.replace('workflow.', '')) || action
  }
  return {
    created: '创建',
    updated: '更新',
    commented: '评论',
    verified: '提交验证',
    'relation.added': '关联对象',
    'attachment.added': '添加附件',
  }[action] ?? action
}
