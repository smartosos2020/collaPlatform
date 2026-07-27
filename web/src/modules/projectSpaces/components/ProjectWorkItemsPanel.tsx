import {
  ArrowLeftOutlined,
  CommentOutlined,
  FileOutlined,
  HistoryOutlined,
  InboxOutlined,
  PaperClipOutlined,
  PlusOutlined,
  ReloadOutlined,
  SaveOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntdApp,
  Avatar,
  Button,
  Card,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Progress,
  Select,
  Skeleton,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  Upload,
} from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import { ApiRequestError } from '../../../shared/api/httpClient'
import { listProjectSpaceMembers, type UserProjectSpace } from '../api/projectSpacesApi'
import {
  addWorkItemAttachment,
  addWorkItemComment,
  createWorkItem,
  getWorkItem,
  getWorkItemCreateForm,
  getWorkItemAttachmentDownloadUrl,
  listWorkItemActivities,
  listWorkItemAttachments,
  listWorkItemComments,
  listWorkItemParticipants,
  listWorkItems,
  nudgeWorkItemParticipant,
  putWorkItemParticipant,
  transitionWorkItem,
  updateWorkItem,
  uploadWorkItemFile,
  workItemKeys,
  type WorkItem,
  type WorkItemRuntime,
} from '../api/workItemsApi'
import { listActiveWorkItemTypes, workItemTypeKeys } from '../api/workItemTypesApi'
import type { ConfiguredWorkItemField } from '../api/workItemFieldsApi'
import type {
  WorkItemFieldAccessProjection,
  WorkItemLayoutNode,
} from '../api/workItemLayoutsApi'
import { WorkItemLayoutRenderer, type WorkItemLayoutValues } from './WorkItemLayoutRenderer'
import { WorkItemNodeWorkflowPanel } from './WorkItemNodeWorkflowPanel'
import { WorkItemRelationsPanel } from './WorkItemRelationsPanel'
import { WorkItemPermissionsPanel } from './WorkItemPermissionsPanel'
import { WorkItemWorkflowPanel } from './WorkItemWorkflowPanel'
import { errorMessage, formatTime } from '../projectSpaceView'

export function ProjectWorkItemsPanel({
  space,
  workItemId,
}: {
  space: UserProjectSpace
  workItemId?: string
}) {
  return workItemId
    ? <WorkItemDetail space={space} workItemId={workItemId} />
    : <WorkItemCollection space={space} />
}

function WorkItemCollection({ space }: { space: UserProjectSpace }) {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [createOpen, setCreateOpen] = useState(searchParams.get('create') === '1')
  const [selectedTypeOverride, setSelectedTypeOverride] = useState(searchParams.get('typeId') ?? undefined)
  const typesQuery = useQuery({
    queryKey: workItemTypeKeys.active(space.id),
    queryFn: () => listActiveWorkItemTypes(space.id),
  })
  const selectedTypeId = selectedTypeOverride ?? typesQuery.data?.[0]?.id
  const itemsQuery = useQuery({
    queryKey: workItemKeys.list(space.id, selectedTypeId),
    queryFn: () => listWorkItems(space.id, selectedTypeId),
  })

  const openCreate = (typeId?: string) => {
    const nextType = typeId ?? selectedTypeId ?? typesQuery.data?.[0]?.id
    setSelectedTypeOverride(nextType)
    setCreateOpen(true)
    setSearchParams(nextType ? { typeId: nextType, create: '1' } : { create: '1' }, { replace: true })
  }

  return (
    <section className="project-work-items" data-testid="project-work-items">
      <Card
        className="content-card"
        title={<Space><FileOutlined />工作项</Space>}
        extra={space.status === 'active' && space.currentUserRole !== 'guest'
          ? <Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate()}>新建工作项</Button>
          : null}
      >
        <div className="project-work-item-filter">
          <Select
            aria-label="按工作项类型筛选"
            allowClear
            value={selectedTypeId}
            placeholder="全部类型"
            loading={typesQuery.isLoading}
            options={typesQuery.data?.map((type) => ({ label: type.name, value: type.id }))}
            onChange={setSelectedTypeOverride}
          />
          <Typography.Text type="secondary">列表只展示当前空间内你有权访问的工作项。</Typography.Text>
        </div>
        {itemsQuery.isLoading ? <Skeleton active paragraph={{ rows: 5 }} /> : null}
        {itemsQuery.isError ? (
          <Alert
            type="error"
            showIcon
            message="工作项加载失败"
            description={errorMessage(itemsQuery.error, '请稍后重试')}
            action={<Button size="small" onClick={() => itemsQuery.refetch()}>重试</Button>}
          />
        ) : null}
        {!itemsQuery.isLoading && !itemsQuery.isError && itemsQuery.data?.items.length === 0 ? (
          <Empty description="当前类型还没有工作项">
            {space.status === 'active' && space.currentUserRole !== 'guest'
              ? <Button type="primary" onClick={() => openCreate()}>创建第一条工作项</Button>
              : null}
          </Empty>
        ) : null}
        {itemsQuery.data?.items.length ? (
          <Table
            rowKey="id"
            dataSource={itemsQuery.data.items}
            pagination={false}
            scroll={{ x: 760 }}
            onRow={(item) => ({
              tabIndex: 0,
              onClick: () => navigate(`/project-spaces/${space.id}/work-items/${item.id}`),
              onKeyDown: (event) => {
                if (event.key === 'Enter') navigate(`/project-spaces/${space.id}/work-items/${item.id}`)
              },
            })}
            columns={[
              {
                title: '工作项',
                key: 'item',
                render: (_, item: WorkItem) => (
                  <Space>
                    <Avatar>{item.typeName.slice(0, 1)}</Avatar>
                    <span><strong>{item.title}</strong><small className="project-work-item-subline">{item.displayKey}</small></span>
                  </Space>
                ),
              },
              { title: '类型', dataIndex: 'typeName', width: 160, render: (value: string) => <Tag color="purple">{value}</Tag> },
              { title: '状态', dataIndex: 'status', width: 120, render: (value: string) => <Tag color={value === 'active' ? 'green' : 'default'}>{value}</Tag> },
              { title: '更新于', dataIndex: 'updatedAt', width: 190, render: formatTime },
            ]}
          />
        ) : null}
      </Card>
      <CreateWorkItemModal
        key={selectedTypeId ?? 'no-type'}
        space={space}
        typeId={selectedTypeId}
        open={createOpen}
        onCancel={() => {
          setCreateOpen(false)
          setSearchParams(selectedTypeId ? { typeId: selectedTypeId } : {}, { replace: true })
        }}
      />
    </section>
  )
}

function CreateWorkItemModal({
  space,
  typeId,
  open,
  onCancel,
}: {
  space: UserProjectSpace
  typeId?: string
  open: boolean
  onCancel: () => void
}) {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const online = useOnlineStatus()
  const [title, setTitle] = useState('')
  const [draftValues, setDraftValues] = useState<{
    runtimeKey: string
    values: WorkItemLayoutValues
  } | null>(null)
  const formQuery = useQuery({
    queryKey: workItemKeys.createForm(space.id, typeId ?? ''),
    queryFn: () => getWorkItemCreateForm(space.id, typeId as string),
    enabled: open && Boolean(typeId),
    retry: false,
  })

  const runtime = formQuery.data?.runtime
  const runtimeKey = runtime ? `${runtime.typeVersionId}:${runtime.configHash}` : ''
  const values = draftValues?.runtimeKey === runtimeKey
    ? draftValues.values
    : runtime
      ? defaultValues(runtime)
      : {}
  const setValues = (next: WorkItemLayoutValues) => setDraftValues({ runtimeKey, values: next })
  const mutation = useMutation({
    mutationFn: () => {
      if (!online || !navigator.onLine) {
        throw new Error('当前处于离线状态，输入已保留，请联网后重试')
      }
      return createWorkItem(space.id, {
        typeId: typeId as string,
        title: title.trim(),
        fieldValues: values,
      })
    },
    onSuccess: async (item) => {
      await queryClient.invalidateQueries({ queryKey: workItemKeys.all })
      message.success('工作项已创建')
      navigate(`/project-spaces/${space.id}/work-items/${item.id}`)
    },
    onError: (error) => message.error(errorMessage(error, '创建失败，输入已保留')),
  })
  return (
    <Modal
      width={880}
      open={open}
      title={`新建${formQuery.data?.typeName ?? '工作项'}`}
      okText="创建"
      cancelText="取消"
      okButtonProps={{ disabled: !online || !title.trim() || !runtime }}
      confirmLoading={mutation.isPending}
      onCancel={onCancel}
      onOk={() => mutation.mutate()}
      destroyOnHidden={false}
    >
      {!typeId ? <Alert type="warning" showIcon message="请先选择工作项类型" /> : null}
      {formQuery.isLoading ? <Skeleton active /> : null}
      {formQuery.isError ? <Alert type="error" showIcon message="创建表单加载失败" description={errorMessage(formQuery.error, '请稍后重试')} /> : null}
      {runtime ? (
        <div className="project-work-item-form">
          <Form layout="vertical">
            <Form.Item label="标题" htmlFor="work-item-create-title" required>
              <Input
                id="work-item-create-title"
                autoFocus
                maxLength={500}
                value={title}
                placeholder="输入工作项标题"
                onChange={(event) => setTitle(event.target.value)}
                onPressEnter={(event) => {
                  if (!event.nativeEvent.isComposing && title.trim()) mutation.mutate()
                }}
              />
            </Form.Item>
          </Form>
          <RuntimeLayout runtime={runtime} values={values} onValuesChange={setValues} />
        </div>
      ) : null}
    </Modal>
  )
}

function WorkItemDetail({ space, workItemId }: { space: UserProjectSpace; workItemId: string }) {
  const { message, modal } = AntdApp.useApp()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const online = useOnlineStatus()
  const [draft, setDraft] = useState<{
    version: number
    title: string
    values: WorkItemLayoutValues
  } | null>(null)
  const itemQuery = useQuery({
    queryKey: workItemKeys.detail(space.id, workItemId),
    queryFn: () => getWorkItem(space.id, workItemId),
    retry: (count, error) => !(error instanceof ApiRequestError && [403, 404].includes(error.status)) && count < 2,
  })
  const item = itemQuery.data

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: workItemKeys.detail(space.id, workItemId) }),
      queryClient.invalidateQueries({ queryKey: workItemKeys.list(space.id) }),
      queryClient.invalidateQueries({ queryKey: workItemKeys.activities(space.id, workItemId) }),
    ])
  }
  const saveMutation = useMutation({
    mutationFn: () => {
      if (!item) throw new Error('工作项尚未加载')
      if (!online || !navigator.onLine) {
        throw new Error('当前处于离线状态，输入已保留，请联网后重试')
      }
      return updateWorkItem(space.id, workItemId, {
        title: title.trim(),
        fieldValues: values,
        expectedVersion: item.version,
      })
    },
    onSuccess: async () => {
      await refresh()
      message.success('工作项已保存')
    },
    onError: (error) => {
      const conflict = error instanceof ApiRequestError
        && error.code?.toLowerCase() === 'work_item_version_conflict'
      message.error(conflict ? '工作项已被他人修改。当前输入已保留，请刷新对比后重试。' : errorMessage(error, '保存失败，输入已保留'))
    },
  })
  const transitionMutation = useMutation({
    mutationFn: (action: 'archive' | 'restore') => transitionWorkItem(space.id, workItemId, action, item?.version ?? 0),
    onSuccess: async (_, action) => {
      await refresh()
      message.success(action === 'archive' ? '工作项已归档' : '工作项已恢复')
    },
    onError: (error) => message.error(errorMessage(error, '状态变更失败')),
  })

  if (itemQuery.isLoading) return <Card><Skeleton active /></Card>
  if (itemQuery.isError || !item) {
    return (
      <Card>
        <Empty description="工作项不存在或你无权访问">
          <Button onClick={() => navigate(`/project-spaces/${space.id}/work-items`)}>返回工作项列表</Button>
        </Empty>
      </Card>
    )
  }
  const activeDraft = draft?.version === item.version ? draft : null
  const title = activeDraft?.title ?? item.title
  const values = activeDraft?.values ?? item.fieldValues
  const setTitle = (next: string) => setDraft({
    version: item.version,
    title: next,
    values,
  })
  const setValues = (next: WorkItemLayoutValues) => setDraft({
    version: item.version,
    title,
    values: next,
  })
  const writable = item.availableActions.includes('edit')
  return (
    <section className="project-work-item-detail" data-testid="project-work-item-detail">
      <Card
        className="content-card project-work-item-detail-card"
        title={(
          <Space wrap>
            <Button type="text" aria-label="返回工作项列表" icon={<ArrowLeftOutlined />} onClick={() => navigate(`/project-spaces/${space.id}/work-items`)} />
            <Tag color="purple">{item.typeName}</Tag>
            <Typography.Text type="secondary">{item.displayKey}</Typography.Text>
            <Tag color={item.status === 'active' ? 'green' : 'default'}>{item.status}</Tag>
          </Space>
        )}
        extra={(
          <Space wrap>
            {writable ? <Button type="primary" icon={<SaveOutlined />} disabled={!online} loading={saveMutation.isPending} onClick={() => saveMutation.mutate()}>保存</Button> : null}
            {item.availableActions.includes('archive') ? (
              <Button
                danger
                icon={<InboxOutlined />}
                onClick={() => modal.confirm({
                  title: '归档工作项？',
                  content: '归档后工作项只读，可稍后恢复。',
                  okText: '归档',
                  okButtonProps: { danger: true },
                  onOk: () => transitionMutation.mutateAsync('archive'),
                })}
              >
                归档
              </Button>
            ) : null}
            {item.availableActions.includes('restore') ? <Button icon={<ReloadOutlined />} onClick={() => transitionMutation.mutate('restore')}>恢复</Button> : null}
          </Space>
        )}
      >
        <Form layout="vertical">
          <Form.Item label="标题" htmlFor="work-item-detail-title" required>
            <Input
              id="work-item-detail-title"
              maxLength={500}
              disabled={!writable}
              value={title}
              onChange={(event) => setTitle(event.target.value)}
            />
          </Form.Item>
        </Form>
        <RuntimeLayout
          runtime={item.runtime}
          values={values}
          presentation={writable ? 'edit' : 'read'}
          onValuesChange={setValues}
        />
      </Card>
      <WorkItemPermissionsPanel space={space} item={item} />
      <WorkItemRelationsPanel
        space={space}
        item={item}
        online={online}
        refreshItem={refresh}
      />
      <WorkItemNodeWorkflowPanel
        spaceId={space.id}
        item={item}
        online={online}
        refreshItem={refresh}
      />
      <WorkItemWorkflowPanel
        space={space}
        item={item}
        fieldPatch={values}
        online={online}
        refreshItem={refresh}
      />
      <WorkItemCollaboration space={space} item={item} refreshItem={refresh} />
    </section>
  )
}

function WorkItemCollaboration({
  space,
  item,
  refreshItem,
}: {
  space: UserProjectSpace
  item: WorkItem
  refreshItem: () => Promise<void>
}) {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const [comment, setComment] = useState('')
  const [uploadProgress, setUploadProgress] = useState<number | null>(null)
  const participantsQuery = useQuery({
    queryKey: workItemKeys.participants(space.id, item.id),
    queryFn: () => listWorkItemParticipants(space.id, item.id),
  })
  const activitiesQuery = useQuery({
    queryKey: workItemKeys.activities(space.id, item.id),
    queryFn: () => listWorkItemActivities(space.id, item.id),
  })
  const commentsQuery = useQuery({
    queryKey: workItemKeys.comments(space.id, item.id),
    queryFn: () => listWorkItemComments(space.id, item.id),
  })
  const attachmentsQuery = useQuery({
    queryKey: workItemKeys.attachments(space.id, item.id),
    queryFn: () => listWorkItemAttachments(space.id, item.id),
  })
  const membersQuery = useQuery({
    queryKey: ['project-spaces', space.id, 'members'],
    queryFn: () => listProjectSpaceMembers(space.id),
    enabled: item.availableActions.includes('edit'),
  })
  const invalidate = async () => {
    await refreshItem()
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: workItemKeys.participants(space.id, item.id) }),
      queryClient.invalidateQueries({ queryKey: workItemKeys.comments(space.id, item.id) }),
      queryClient.invalidateQueries({ queryKey: workItemKeys.attachments(space.id, item.id) }),
    ])
  }
  const commentMutation = useMutation({
    mutationFn: () => addWorkItemComment(space.id, item.id, comment, item.version),
    onSuccess: async () => {
      setComment('')
      await invalidate()
      message.success('评论已发布')
    },
    onError: (error) => message.error(errorMessage(error, '评论失败，内容已保留')),
  })
  const participantMutation = useMutation({
    mutationFn: (userId: string) => putWorkItemParticipant(space.id, item.id, userId, 'collaborator', item.version),
    onSuccess: async () => {
      await invalidate()
      message.success('参与者已添加')
    },
    onError: (error) => message.error(errorMessage(error, '添加参与者失败')),
  })
  const nudgeMutation = useMutation({
    mutationFn: (userId: string) => nudgeWorkItemParticipant(space.id, item.id, userId),
    onSuccess: () => message.success('催办已发送'),
    onError: (error) => message.error(errorMessage(error, '催办不可用或仍在冷却期')),
  })
  const attachmentMutation = useMutation({
    mutationFn: async (file: File) => {
      setUploadProgress(0)
      const metadata = await uploadWorkItemFile(file, item.id, setUploadProgress)
      return addWorkItemAttachment(space.id, item.id, metadata.id, item.version)
    },
    onSuccess: async () => {
      setUploadProgress(null)
      await invalidate()
      message.success('附件已上传')
    },
    onError: (error) => {
      setUploadProgress(null)
      message.error(errorMessage(error, '附件上传失败'))
    },
  })

  return (
    <Card className="content-card project-work-item-collaboration">
      <Tabs
        items={[
          {
            key: 'comments',
            label: <Space><CommentOutlined />评论</Space>,
            children: (
              <div className="project-work-item-tab">
                {item.availableActions.includes('edit') ? (
                  <div className="project-work-item-comment-compose">
                    <Input.TextArea
                      value={comment}
                      autoSize={{ minRows: 2, maxRows: 8 }}
                      maxLength={20_000}
                      placeholder="输入评论，Ctrl/⌘ + Enter 发布"
                      onChange={(event) => setComment(event.target.value)}
                      onKeyDown={(event) => {
                        if ((event.ctrlKey || event.metaKey) && event.key === 'Enter' && comment.trim()) {
                          commentMutation.mutate()
                        }
                      }}
                    />
                    <Button type="primary" disabled={!comment.trim()} loading={commentMutation.isPending} onClick={() => commentMutation.mutate()}>发布</Button>
                  </div>
                ) : null}
                <List
                  locale={{ emptyText: '暂无评论' }}
                  loading={commentsQuery.isLoading}
                  dataSource={commentsQuery.data?.items ?? []}
                  renderItem={(value) => (
                    <List.Item>
                      <List.Item.Meta
                        avatar={<Avatar>{(value.authorDisplayName ?? '?').slice(0, 1)}</Avatar>}
                        title={<Space><span>{value.authorDisplayName ?? '成员'}</span><Typography.Text type="secondary">{formatTime(value.createdAt)}</Typography.Text></Space>}
                        description={<Typography.Paragraph className="project-work-item-long-content">{value.content}</Typography.Paragraph>}
                      />
                    </List.Item>
                  )}
                />
              </div>
            ),
          },
          {
            key: 'participants',
            label: <Space><TeamOutlined />参与者</Space>,
            children: (
              <div className="project-work-item-tab">
                {item.availableActions.includes('edit') ? (
                  <Select
                    showSearch
                    aria-label="添加参与者"
                    placeholder="选择空间成员"
                    loading={membersQuery.isLoading || participantMutation.isPending}
                    optionFilterProp="label"
                    options={membersQuery.data
                      ?.filter((member) => member.effective && !participantsQuery.data?.items.some((item) => item.userId === member.userId))
                      .map((member) => ({ label: member.displayName, value: member.userId }))}
                    onChange={(userId) => participantMutation.mutate(userId)}
                  />
                ) : null}
                <List
                  locale={{ emptyText: '暂无参与者' }}
                  loading={participantsQuery.isLoading}
                  dataSource={participantsQuery.data?.items ?? []}
                  renderItem={(value) => (
                    <List.Item
                      extra={(
                        <Space>
                          <Tag>{value.role}</Tag>
                          {['owner', 'assignee'].includes(value.role) ? (
                            <Button
                              size="small"
                              loading={nudgeMutation.isPending}
                              onClick={() => nudgeMutation.mutate(value.userId)}
                            >
                              催办
                            </Button>
                          ) : null}
                        </Space>
                      )}
                    >
                      <List.Item.Meta
                        avatar={<Avatar>{(value.displayName ?? '?').slice(0, 1)}</Avatar>}
                        title={value.displayName ?? value.userId}
                      />
                    </List.Item>
                  )}
                />
              </div>
            ),
          },
          {
            key: 'attachments',
            label: <Space><PaperClipOutlined />附件</Space>,
            children: (
              <div className="project-work-item-tab">
                {item.availableActions.includes('edit') ? (
                  <Upload
                    showUploadList={false}
                    beforeUpload={(file) => {
                      attachmentMutation.mutate(file)
                      return false
                    }}
                  >
                    <Button icon={<PaperClipOutlined />} loading={attachmentMutation.isPending}>上传附件</Button>
                  </Upload>
                ) : null}
                {uploadProgress != null ? <Progress percent={uploadProgress} size="small" /> : null}
                <List
                  locale={{ emptyText: '暂无附件' }}
                  loading={attachmentsQuery.isLoading}
                  dataSource={attachmentsQuery.data?.items ?? []}
                  renderItem={(value) => (
                    <List.Item
                      actions={[
                        <Button
                          key="download"
                          type="link"
                          onClick={() => void getWorkItemAttachmentDownloadUrl(value.fileId).then((result) => window.open(result.downloadUrl, '_blank', 'noopener,noreferrer'))}
                        >
                          下载
                        </Button>,
                      ]}
                    >
                      <List.Item.Meta
                        avatar={<Avatar icon={<FileOutlined />} />}
                        title={value.fileName}
                        description={`${formatBytes(value.sizeBytes)} · ${value.createdByDisplayName ?? '成员'} · ${formatTime(value.createdAt)}`}
                      />
                    </List.Item>
                  )}
                />
              </div>
            ),
          },
          {
            key: 'activity',
            label: <Space><HistoryOutlined />活动</Space>,
            children: (
              <List
                locale={{ emptyText: '暂无活动' }}
                loading={activitiesQuery.isLoading}
                dataSource={activitiesQuery.data?.items ?? []}
                renderItem={(value) => (
                  <List.Item>
                    <List.Item.Meta
                      title={<Space><Tag>{value.type}</Tag><span>{value.actorDisplayName ?? '系统'}</span></Space>}
                      description={formatTime(value.occurredAt)}
                    />
                  </List.Item>
                )}
              />
            ),
          },
        ]}
      />
    </Card>
  )
}

function RuntimeLayout({
  runtime,
  values,
  onValuesChange,
  presentation = 'edit',
}: {
  runtime: WorkItemRuntime
  values: WorkItemLayoutValues
  onValuesChange: (values: WorkItemLayoutValues) => void
  presentation?: 'edit' | 'read'
}) {
  const projection = useMemo(() => normalizeRuntime(runtime), [runtime])
  return (
    <WorkItemLayoutRenderer
      layout={projection.layout}
      fields={projection.fields}
      accessProjection={runtime.accessProjection as Record<string, WorkItemFieldAccessProjection>}
      values={values}
      presentation={presentation}
      onValuesChange={onValuesChange}
    />
  )
}

function normalizeRuntime(runtime: WorkItemRuntime) {
  const fields = runtime.snapshot.fields as unknown as ConfiguredWorkItemField[]
  const fieldByKey = new Map(fields.map((field) => [field.fieldKey, field]))
  const source = runtime.snapshot.layouts.find((layout) => layout.layoutKind === runtime.layoutKind)
  const rawNodes = Array.isArray(source?.nodes) ? source.nodes as Array<Record<string, unknown>> : []
  const idByKey = new Map(rawNodes.map((node) => [String(node.nodeKey), String(node.id)]))
  const nodes: WorkItemLayoutNode[] = rawNodes.map((node) => {
    const fieldKey = node.fieldKey == null ? null : String(node.fieldKey)
    const parentKey = node.parentKey == null ? null : String(node.parentKey)
    return {
      id: String(node.id),
      parentId: parentKey ? idByKey.get(parentKey) ?? null : null,
      nodeKey: String(node.nodeKey),
      nodeType: String(node.nodeType) as WorkItemLayoutNode['nodeType'],
      fieldId: fieldKey ? fieldByKey.get(fieldKey)?.id ?? null : null,
      fieldKey,
      sortOrder: Number(node.sortOrder ?? 0),
      config: (node.config ?? {}) as Record<string, unknown>,
      visibilityCondition: (node.visibilityCondition ?? { schemaVersion: 1 }) as WorkItemLayoutNode['visibilityCondition'],
    }
  })
  if (nodes.length === 0) {
    const rootId = `runtime-${runtime.layoutKind}-root`
    nodes.push({
      id: rootId,
      parentId: null,
      nodeKey: 'main',
      nodeType: 'section',
      fieldId: null,
      fieldKey: null,
      sortOrder: 0,
      config: { title: '详细信息' },
      visibilityCondition: { schemaVersion: 1 },
    })
    fields.forEach((field, index) => nodes.push({
      id: `runtime-${field.id}`,
      parentId: rootId,
      nodeKey: `field-${field.fieldKey}`,
      nodeType: 'field',
      fieldId: field.id,
      fieldKey: field.fieldKey,
      sortOrder: index,
      config: {},
      visibilityCondition: { schemaVersion: 1 },
    }))
  }
  return {
    fields,
    layout: { layoutKind: runtime.layoutKind, nodes },
  }
}

function defaultValues(runtime: WorkItemRuntime) {
  return Object.fromEntries(runtime.snapshot.fields.flatMap((field) => {
    const config = field.config as Record<string, unknown> | undefined
    return config?.defaultValue == null ? [] : [[String(field.fieldKey), config.defaultValue]]
  }))
}

function formatBytes(value: number) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

function useOnlineStatus() {
  const [online, setOnline] = useState(() => navigator.onLine)
  useEffect(() => {
    const markOnline = () => setOnline(true)
    const markOffline = () => setOnline(false)
    window.addEventListener('online', markOnline)
    window.addEventListener('offline', markOffline)
    return () => {
      window.removeEventListener('online', markOnline)
      window.removeEventListener('offline', markOffline)
    }
  }, [])
  return online
}
