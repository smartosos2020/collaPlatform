import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  CheckCircleOutlined,
  CommentOutlined,
  DeleteOutlined,
  FileTextOutlined,
  LinkOutlined,
  PlusOutlined,
  SaveOutlined,
  SearchOutlined,
  ShareAltOutlined,
  StarFilled,
  StarOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App as AntdApp, Alert, Button, Empty, Form, Input, Modal, Select, Space, Tag, Tooltip, Typography } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'

import { InternalLinkCard } from '../../platform/components/InternalLinkCard'
import {
  addObjectFavorite,
  listFavoriteObjects,
  listRecentObjects,
  markObjectAccessed,
  removeObjectFavorite,
} from '../../platform/api/platformObjectsApi'
import { listDirectoryMembers } from '../../projects/api/projectsApi'
import {
  addDocumentComment,
  addDocumentRelation,
  createDocument,
  diffDocumentVersions,
  getDocument,
  grantDocumentPermission,
  listDocumentVersions,
  listDocuments,
  restoreDocumentVersion,
  resolveDocumentComment,
  saveDocument,
  saveDocumentBlocks,
  type DocumentBlockDraft,
  type DocumentDetail,
} from '../api/docsApi'

type CreateDocForm = {
  title: string
  content?: string
}

type PermissionForm = {
  userId: string
  permissionLevel: 'view' | 'edit' | 'manage'
}

type RelationForm = {
  targetType: 'issue' | 'base' | 'base_table' | 'base_record' | 'file'
  targetId: string
}

type DraftState = {
  docId: string | null
  title: string
  content: string
  baseVersionNo: number
}

type DocListMode = 'all' | 'recent' | 'favorites'

type BlockDraftState = DocumentBlockDraft & {
  id?: string
}

export function DocsPage() {
  const { docId } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { message } = AntdApp.useApp()
  const [selectedDocId, setSelectedDocId] = useState<string | null>(null)
  const [draft, setDraft] = useState<DraftState>({ docId: null, title: '', content: '', baseVersionNo: 0 })
  const [createOpen, setCreateOpen] = useState(false)
  const [permissionOpen, setPermissionOpen] = useState(false)
  const [relationOpen, setRelationOpen] = useState(false)
  const [commentDraft, setCommentDraft] = useState('')
  const [commentBlockId, setCommentBlockId] = useState<string | undefined>()
  const [conflictDocId, setConflictDocId] = useState<string | null>(null)
  const [diffToVersionNo, setDiffToVersionNo] = useState<number | null>(null)
  const [docSearch, setDocSearch] = useState('')
  const [docListMode, setDocListMode] = useState<DocListMode>('all')
  const [blockDraftDocId, setBlockDraftDocId] = useState<string | null>(null)
  const [blockDraftVersionNo, setBlockDraftVersionNo] = useState(0)
  const [blockDrafts, setBlockDrafts] = useState<BlockDraftState[]>([])
  const [createForm] = Form.useForm<CreateDocForm>()
  const [permissionForm] = Form.useForm<PermissionForm>()
  const [relationForm] = Form.useForm<RelationForm>()

  const docsQuery = useQuery({ queryKey: ['docs'], queryFn: listDocuments })
  const membersQuery = useQuery({ queryKey: ['members', 'directory'], queryFn: listDirectoryMembers })
  const recentObjectsQuery = useQuery({ queryKey: ['platform', 'recent', 'docs'], queryFn: () => listRecentObjects(30) })
  const favoriteObjectsQuery = useQuery({ queryKey: ['platform', 'favorites', 'docs'], queryFn: () => listFavoriteObjects(50) })
  const activeDocId = docId ?? selectedDocId ?? docsQuery.data?.[0]?.id ?? null
  const activeCommentId = new URLSearchParams(location.search).get('commentId')
  const docQuery = useQuery({
    queryKey: ['docs', activeDocId],
    queryFn: () => getDocument(activeDocId || ''),
    enabled: Boolean(activeDocId),
  })
  const versionsQuery = useQuery({
    queryKey: ['docs', activeDocId, 'versions'],
    queryFn: () => listDocumentVersions(activeDocId || ''),
    enabled: Boolean(activeDocId),
  })
  const diffQuery = useQuery({
    queryKey: ['docs', activeDocId, 'diff', diffToVersionNo],
    queryFn: () => diffDocumentVersions(activeDocId || '', (diffToVersionNo || 1) - 1, diffToVersionNo || 1),
    enabled: Boolean(activeDocId && diffToVersionNo && diffToVersionNo > 1),
  })

  const canEdit = hasPermission(docQuery.data?.document.permissionLevel, 'edit')
  const canManage = hasPermission(docQuery.data?.document.permissionLevel, 'manage')
  const titleDraft = draft.docId === activeDocId ? draft.title : docQuery.data?.document.title ?? ''
  const contentDraft = draft.docId === activeDocId ? draft.content : docQuery.data?.content ?? ''
  const baseVersionNo =
    draft.docId === activeDocId ? draft.baseVersionNo : docQuery.data?.document.currentVersionNo ?? 0

  useEffect(() => {
    const first = docsQuery.data?.[0]
    if (!docId && !selectedDocId && first) {
      navigate(`/docs/${first.id}`, { replace: true })
    }
  }, [docId, docsQuery.data, navigate, selectedDocId])

  useEffect(() => {
    if (activeDocId) {
      markObjectAccessed('document', activeDocId).catch(() => undefined)
    }
  }, [activeDocId])

  useEffect(() => {
    if (!docQuery.data) {
      return
    }
    const detail = docQuery.data
    const timer = window.setTimeout(() => {
      setBlockDraftDocId(detail.document.id)
      setBlockDraftVersionNo(detail.document.currentVersionNo)
      setBlockDrafts(detail.blocks.map((block) => ({
        id: block.id,
        blockType: block.blockType,
        content: block.content,
        sortOrder: block.sortOrder,
      })))
      setCommentBlockId(undefined)
    }, 0)
    return () => window.clearTimeout(timer)
  }, [docQuery.data])

  const refreshDocs = async (documentId = activeDocId) => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['docs'] }),
      documentId ? queryClient.invalidateQueries({ queryKey: ['docs', documentId] }) : Promise.resolve(),
      documentId ? queryClient.invalidateQueries({ queryKey: ['docs', documentId, 'versions'] }) : Promise.resolve(),
      queryClient.invalidateQueries({ queryKey: ['platform', 'recent', 'docs'] }),
      queryClient.invalidateQueries({ queryKey: ['platform', 'favorites', 'docs'] }),
    ])
  }

  const favoriteDocumentIds = useMemo(
    () =>
      new Set(
        (favoriteObjectsQuery.data ?? [])
          .filter((item) => item.objectType === 'document' && item.accessState === 'available')
          .map((item) => item.objectId),
      ),
    [favoriteObjectsQuery.data],
  )

  const visibleDocs = useMemo(() => {
    const docs = docsQuery.data ?? []
    const search = docSearch.trim().toLowerCase()
    const filtered = docs.filter((document) => {
      if (search && !document.title.toLowerCase().includes(search)) {
        return false
      }
      if (docListMode === 'favorites') {
        return favoriteDocumentIds.has(document.id)
      }
      if (docListMode === 'recent') {
        return (recentObjectsQuery.data ?? []).some((item) => item.objectType === 'document' && item.objectId === document.id)
      }
      return true
    })
    if (docListMode !== 'all') {
      const source = docListMode === 'favorites' ? favoriteObjectsQuery.data ?? [] : recentObjectsQuery.data ?? []
      const rank = new Map(source.map((item, index) => [item.objectId, index]))
      return [...filtered].sort((left, right) => (rank.get(left.id) ?? 9999) - (rank.get(right.id) ?? 9999))
    }
    return filtered
  }, [docListMode, docSearch, docsQuery.data, favoriteDocumentIds, favoriteObjectsQuery.data, recentObjectsQuery.data])

  const createMutation = useMutation({
    mutationFn: createDocument,
    onSuccess: async (detail) => {
      setCreateOpen(false)
      createForm.resetFields()
      setSelectedDocId(detail.document.id)
      navigate(`/docs/${detail.document.id}`)
      await refreshDocs(detail.document.id)
    },
  })

  const saveMutation = useMutation({
    mutationFn: () =>
      saveDocument(activeDocId || '', {
        baseVersionNo,
        title: titleDraft,
        content: contentDraft,
      }),
    onSuccess: async (detail) => {
      setDraft({
        docId: detail.document.id,
        title: detail.document.title,
        content: detail.content,
        baseVersionNo: detail.document.currentVersionNo,
      })
      setConflictDocId(null)
      message.success('已保存')
      await refreshDocs(detail.document.id)
    },
    onError: (error) => {
      if (error.message.includes('409')) {
        setConflictDocId(activeDocId)
        message.warning('文档已被其他版本更新，请刷新后再合并保存')
        return
      }
      message.error('保存失败')
    },
  })

  const restoreMutation = useMutation({
    mutationFn: (versionNo: number) => restoreDocumentVersion(activeDocId || '', versionNo),
    onSuccess: async (detail) => {
      message.success('版本已恢复')
      await refreshDocs(detail.document.id)
    },
  })

  const permissionMutation = useMutation({
    mutationFn: (values: PermissionForm) => grantDocumentPermission(activeDocId || '', values),
    onSuccess: async () => {
      setPermissionOpen(false)
      permissionForm.resetFields()
      await refreshDocs()
    },
  })

  const relationMutation = useMutation({
    mutationFn: (values: RelationForm) => addDocumentRelation(activeDocId || '', values),
    onSuccess: async () => {
      setRelationOpen(false)
      relationForm.resetFields()
      await refreshDocs()
    },
  })

  const blockMutation = useMutation({
    mutationFn: () =>
      saveDocumentBlocks(activeDocId || '', {
        baseVersionNo: blockDraftVersionNo || baseVersionNo,
        blocks: blockDrafts.map((block, index) => ({
          blockType: block.blockType,
          content: block.content,
          sortOrder: index,
        })),
      }),
    onSuccess: async (detail) => {
      message.success('块已保存')
      await refreshDocs(detail.document.id)
    },
    onError: (error) => {
      if (error.message.includes('409')) {
        setConflictDocId(activeDocId)
        message.warning('文档已被其他版本更新，请刷新后再保存块')
        return
      }
      message.error('块保存失败')
    },
  })

  const commentMutation = useMutation({
    mutationFn: () => addDocumentComment(activeDocId || '', { content: commentDraft, blockId: commentBlockId }),
    onSuccess: async () => {
      setCommentDraft('')
      setCommentBlockId(undefined)
      await refreshDocs()
    },
  })

  const resolveCommentMutation = useMutation({
    mutationFn: (commentId: string) => resolveDocumentComment(activeDocId || '', commentId),
    onSuccess: async () => {
      await refreshDocs()
    },
  })

  const favoriteMutation = useMutation({
    mutationFn: async ({ documentId, favorite }: { documentId: string; favorite: boolean }) => {
      if (favorite) {
        await removeObjectFavorite('document', documentId)
      } else {
        await addObjectFavorite('document', documentId)
      }
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['platform', 'favorites', 'docs'] })
    },
  })

  const markdownPreview = useMemo(() => renderMarkdownPreview(contentDraft), [contentDraft])

  const updateDraft = (next: Partial<Pick<DraftState, 'title' | 'content'>>) => {
    if (!activeDocId) {
      return
    }
    setDraft({
      docId: activeDocId,
      title: next.title ?? titleDraft,
      content: next.content ?? contentDraft,
      baseVersionNo,
    })
  }

  const updateBlockDraft = (index: number, next: Partial<BlockDraftState>) => {
    setBlockDrafts((current) => current.map((block, blockIndex) => (blockIndex === index ? { ...block, ...next } : block)))
  }

  const addBlockDraft = () => {
    setBlockDrafts((current) => [...current, { blockType: 'paragraph', content: '', sortOrder: current.length }])
  }

  const removeBlockDraft = (index: number) => {
    setBlockDrafts((current) => {
      const next = current.filter((_, blockIndex) => blockIndex !== index)
      return next.length > 0 ? next : [{ blockType: 'paragraph', content: '', sortOrder: 0 }]
    })
  }

  const moveBlockDraft = (index: number, direction: -1 | 1) => {
    setBlockDrafts((current) => {
      const target = index + direction
      if (target < 0 || target >= current.length) {
        return current
      }
      const next = [...current]
      const [item] = next.splice(index, 1)
      next.splice(target, 0, item)
      return next
    })
  }

  return (
    <div className="docs-workspace">
      <aside className="docs-sidebar">
        <div className="section-heading">
          <div>
            <Typography.Title level={4}>文档</Typography.Title>
            <Typography.Text type="secondary">{visibleDocs.length} / {docsQuery.data?.length ?? 0} 篇</Typography.Text>
          </div>
          <Tooltip title="新建文档">
            <Button icon={<PlusOutlined />} type="primary" onClick={() => setCreateOpen(true)} />
          </Tooltip>
        </div>

        <Space orientation="vertical" size={8} className="docs-list-controls">
          <Input
            allowClear
            prefix={<SearchOutlined />}
            placeholder="搜索文档标题"
            value={docSearch}
            onChange={(event) => setDocSearch(event.target.value)}
          />
          <Select
            value={docListMode}
            onChange={setDocListMode}
            options={[
              { value: 'all', label: '全部文档' },
              { value: 'recent', label: '最近访问' },
              { value: 'favorites', label: '收藏文档' },
            ]}
          />
        </Space>

        <Space orientation="vertical" size={8} className="docs-list">
          {visibleDocs.map((document) => (
            <div className="doc-list-row" key={document.id}>
              <button
                className={`doc-list-item${document.id === activeDocId ? ' active' : ''}`}
                type="button"
                onClick={() => {
                  setSelectedDocId(document.id)
                  navigate(`/docs/${document.id}`)
                }}
              >
                <FileTextOutlined />
                <span>
                  <strong>{document.title}</strong>
                  <small>v{document.currentVersionNo} · {permissionText(document.permissionLevel)}</small>
                </span>
              </button>
              <Tooltip title={favoriteDocumentIds.has(document.id) ? '取消收藏' : '收藏'}>
                <Button
                  className="doc-favorite-button"
                  icon={favoriteDocumentIds.has(document.id) ? <StarFilled /> : <StarOutlined />}
                  loading={favoriteMutation.isPending && favoriteMutation.variables?.documentId === document.id}
                  onClick={() => favoriteMutation.mutate({ documentId: document.id, favorite: favoriteDocumentIds.has(document.id) })}
                />
              </Tooltip>
            </div>
          ))}
          {visibleDocs.length === 0 ? <Empty description="没有匹配文档" /> : null}
        </Space>
      </aside>

      <main className="docs-main">
        {docQuery.data ? (
          <DocumentEditor
            detail={docQuery.data}
            titleDraft={titleDraft}
            contentDraft={contentDraft}
            markdownPreview={markdownPreview}
            canEdit={canEdit}
            canManage={canManage}
            conflictVisible={conflictDocId === activeDocId}
            versions={versionsQuery.data ?? []}
            blockDrafts={blockDraftDocId === activeDocId ? blockDrafts : []}
            saving={saveMutation.isPending}
            savingBlocks={blockMutation.isPending}
            restoringVersionNo={restoreMutation.variables}
            commentDraft={commentDraft}
            commentBlockId={commentBlockId}
            activeCommentId={activeCommentId}
            onTitleChange={(value) => updateDraft({ title: value })}
            onContentChange={(value) => updateDraft({ content: value })}
            onBlockChange={updateBlockDraft}
            onAddBlock={addBlockDraft}
            onRemoveBlock={removeBlockDraft}
            onMoveBlock={moveBlockDraft}
            onSave={() => saveMutation.mutate()}
            onSaveBlocks={() => blockMutation.mutate()}
            onRefresh={() => refreshDocs()}
            onOpenPermission={() => setPermissionOpen(true)}
            onOpenRelation={() => setRelationOpen(true)}
            onRestore={(versionNo) => restoreMutation.mutate(versionNo)}
            onDiff={(versionNo) => setDiffToVersionNo(versionNo)}
            onCommentDraftChange={setCommentDraft}
            onCommentBlockChange={setCommentBlockId}
            onComment={() => commentMutation.mutate()}
            commenting={commentMutation.isPending}
            onResolveComment={(commentId) => resolveCommentMutation.mutate(commentId)}
            resolvingCommentId={resolveCommentMutation.variables}
          />
        ) : (
          <Empty description="暂无文档">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
              新建文档
            </Button>
          </Empty>
        )}
      </main>

      <Modal
        title="新建文档"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => createForm.submit()}
        confirmLoading={createMutation.isPending}
      >
        <Form form={createForm} layout="vertical" onFinish={(values) => createMutation.mutate(values)}>
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="content" label="内容">
            <Input.TextArea rows={8} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="授权"
        open={permissionOpen}
        onCancel={() => setPermissionOpen(false)}
        onOk={() => permissionForm.submit()}
        confirmLoading={permissionMutation.isPending}
      >
        <Form form={permissionForm} layout="vertical" onFinish={(values) => permissionMutation.mutate(values)}>
          <Form.Item name="userId" label="成员" rules={[{ required: true, message: '请选择成员' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={(membersQuery.data ?? []).map((member) => ({ label: member.displayName, value: member.id }))}
            />
          </Form.Item>
          <Form.Item name="permissionLevel" label="权限" initialValue="view" rules={[{ required: true }]}>
            <Select
              options={[
                { label: '查看', value: 'view' },
                { label: '编辑', value: 'edit' },
                { label: '管理', value: 'manage' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="关联对象"
        open={relationOpen}
        onCancel={() => setRelationOpen(false)}
        onOk={() => relationForm.submit()}
        confirmLoading={relationMutation.isPending}
      >
        <Form form={relationForm} layout="vertical" onFinish={(values) => relationMutation.mutate(values)}>
          <Form.Item name="targetType" label="对象类型" initialValue="issue" rules={[{ required: true }]}>
            <Select
              options={[
                { label: '事项', value: 'issue' },
                { label: '表格空间', value: 'base' },
                { label: '数据表', value: 'base_table' },
                { label: '表格记录', value: 'base_record' },
                { label: '文件', value: 'file' },
              ]}
            />
          </Form.Item>
          <Form.Item name="targetId" label="对象 ID" rules={[{ required: true, message: '请输入对象 ID' }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="版本对比"
        open={Boolean(diffToVersionNo)}
        footer={null}
        width={760}
        onCancel={() => setDiffToVersionNo(null)}
      >
        <div className="doc-diff-view">
          {diffQuery.data?.lines.map((line, index) => (
            <div className={`doc-diff-line ${line.type}`} key={`${line.type}-${index}`}>
              <span>{line.oldLineNo || ''}</span>
              <span>{line.newLineNo || ''}</span>
              <code>{line.content || ' '}</code>
            </div>
          ))}
        </div>
      </Modal>
    </div>
  )
}

function DocumentEditor({
  detail,
  titleDraft,
  contentDraft,
  markdownPreview,
  canEdit,
  canManage,
  conflictVisible,
  versions,
  blockDrafts,
  saving,
  savingBlocks,
  restoringVersionNo,
  commentDraft,
  commentBlockId,
  activeCommentId,
  onTitleChange,
  onContentChange,
  onBlockChange,
  onAddBlock,
  onRemoveBlock,
  onMoveBlock,
  onSave,
  onSaveBlocks,
  onRefresh,
  onOpenPermission,
  onOpenRelation,
  onRestore,
  onDiff,
  onCommentDraftChange,
  onCommentBlockChange,
  onComment,
  commenting,
  onResolveComment,
  resolvingCommentId,
}: {
  detail: DocumentDetail
  titleDraft: string
  contentDraft: string
  markdownPreview: string[]
  canEdit: boolean
  canManage: boolean
  conflictVisible: boolean
  versions: Array<{ versionNo: number; createdByName: string; createdAt: string }>
  blockDrafts: BlockDraftState[]
  saving: boolean
  savingBlocks: boolean
  restoringVersionNo?: number
  commentDraft: string
  commentBlockId?: string
  activeCommentId: string | null
  onTitleChange: (value: string) => void
  onContentChange: (value: string) => void
  onBlockChange: (index: number, next: Partial<BlockDraftState>) => void
  onAddBlock: () => void
  onRemoveBlock: (index: number) => void
  onMoveBlock: (index: number, direction: -1 | 1) => void
  onSave: () => void
  onSaveBlocks: () => void
  onRefresh: () => void
  onOpenPermission: () => void
  onOpenRelation: () => void
  onRestore: (versionNo: number) => void
  onDiff: (versionNo: number) => void
  onCommentDraftChange: (value: string) => void
  onCommentBlockChange: (value?: string) => void
  onComment: () => void
  commenting: boolean
  onResolveComment: (commentId: string) => void
  resolvingCommentId?: string
}) {
  useEffect(() => {
    if (!activeCommentId) {
      return
    }
    window.setTimeout(() => {
      document.getElementById(`doc-comment-${activeCommentId}`)?.scrollIntoView({ block: 'center' })
    }, 0)
  }, [activeCommentId, detail.comments.length])

  return (
    <div className="doc-editor">
      <div className="doc-editor-header">
        <div>
          <Input
            className="doc-title-input"
            disabled={!canEdit}
            value={titleDraft}
            onChange={(event) => onTitleChange(event.target.value)}
          />
          <Space wrap>
            <Tag>v{detail.document.currentVersionNo}</Tag>
            <Tag>{permissionText(detail.document.permissionLevel)}</Tag>
            <Typography.Text type="secondary">更新于 {new Date(detail.document.updatedAt).toLocaleString()}</Typography.Text>
          </Space>
        </div>
        <Space wrap>
          <Tooltip title="保存">
            <Button type="primary" icon={<SaveOutlined />} disabled={!canEdit} loading={saving} onClick={onSave} />
          </Tooltip>
          <Tooltip title="授权">
            <Button icon={<ShareAltOutlined />} disabled={!canManage} onClick={onOpenPermission} />
          </Tooltip>
          <Tooltip title="关联对象">
            <Button icon={<LinkOutlined />} disabled={!canEdit} onClick={onOpenRelation} />
          </Tooltip>
        </Space>
      </div>

      {conflictVisible ? (
        <Alert
          showIcon
          type="warning"
          message="版本冲突"
          description="当前草稿基于旧版本。请刷新文档查看最新内容，再手动合并后保存。"
          action={<Button onClick={onRefresh}>刷新</Button>}
        />
      ) : null}

      <section className="doc-editor-grid">
        <Input.TextArea
          className="doc-markdown-input"
          disabled={!canEdit}
          value={contentDraft}
          onChange={(event) => onContentChange(event.target.value)}
        />
        <div className="doc-preview">
          {markdownPreview.length > 0 ? (
            markdownPreview.map((line, index) => <p key={`${line}-${index}`}>{line}</p>)
          ) : (
            <Typography.Text type="secondary">空文档</Typography.Text>
          )}
        </div>
      </section>

      <section className="doc-meta-grid">
        <div className="doc-panel">
          <Space className="doc-panel-title">
            <Typography.Title level={5}>结构化块</Typography.Title>
            <Space>
              <Tooltip title="新增块">
                <Button size="small" icon={<PlusOutlined />} disabled={!canEdit} onClick={onAddBlock} />
              </Tooltip>
              <Tooltip title="保存块">
                <Button size="small" icon={<SaveOutlined />} disabled={!canEdit} loading={savingBlocks} onClick={onSaveBlocks} />
              </Tooltip>
            </Space>
          </Space>
          <Space orientation="vertical" size={8} className="doc-panel-list">
            {blockDrafts.length > 0 ? (
              blockDrafts.map((block, index) => (
                <div className="doc-block-editor-item" id={block.id ? `doc-block-${block.id}` : undefined} key={block.id ?? index}>
                  <Select
                    className="doc-block-type"
                    disabled={!canEdit}
                    value={block.blockType}
                    onChange={(value) => onBlockChange(index, { blockType: value as DocumentBlockDraft['blockType'] })}
                    options={[
                      { value: 'paragraph', label: '段落' },
                      { value: 'heading', label: '标题' },
                      { value: 'list', label: '列表' },
                      { value: 'task', label: '任务' },
                      { value: 'quote', label: '引用' },
                      { value: 'code', label: '代码' },
                    ]}
                  />
                  <Input.TextArea
                    className="doc-block-content"
                    disabled={!canEdit}
                    autoSize={{ minRows: 1, maxRows: 6 }}
                    value={block.content}
                    onChange={(event) => onBlockChange(index, { content: event.target.value })}
                  />
                  <Space>
                    <Tooltip title="上移">
                      <Button size="small" icon={<ArrowUpOutlined />} disabled={!canEdit || index === 0} onClick={() => onMoveBlock(index, -1)} />
                    </Tooltip>
                    <Tooltip title="下移">
                      <Button
                        size="small"
                        icon={<ArrowDownOutlined />}
                        disabled={!canEdit || index === blockDrafts.length - 1}
                        onClick={() => onMoveBlock(index, 1)}
                      />
                    </Tooltip>
                    <Tooltip title="删除">
                      <Button size="small" danger icon={<DeleteOutlined />} disabled={!canEdit} onClick={() => onRemoveBlock(index)} />
                    </Tooltip>
                  </Space>
                </div>
              ))
            ) : (
              <Typography.Text type="secondary">暂无块</Typography.Text>
            )}
          </Space>
        </div>

        <div className="doc-panel">
          <Typography.Title level={5}>版本</Typography.Title>
          <Space orientation="vertical" size={8} className="doc-panel-list">
            {versions.map((version) => (
              <div className="doc-version-item" key={version.versionNo}>
                <span>
                  <strong>v{version.versionNo}</strong>
                  <small>{version.createdByName} · {new Date(version.createdAt).toLocaleString()}</small>
                </span>
                <Button
                  size="small"
                  disabled={!canEdit || version.versionNo === detail.document.currentVersionNo}
                  loading={restoringVersionNo === version.versionNo}
                  onClick={() => onRestore(version.versionNo)}
                >
                  恢复
                </Button>
                <Button size="small" disabled={version.versionNo <= 1} onClick={() => onDiff(version.versionNo)}>
                  对比
                </Button>
              </div>
            ))}
          </Space>
        </div>

        <div className="doc-panel">
          <Typography.Title level={5}>关联对象</Typography.Title>
          <Space orientation="vertical" size={8} className="doc-panel-list">
            {detail.relations.length > 0 ? (
              detail.relations.map((relation) => (
                <div className="doc-relation-item" key={relation.id}>
                  {relation.webPath ? (
                    <InternalLinkCard link={relation.webPath} />
                  ) : (
                    <>
                      <Tag>{relation.targetType}</Tag>
                      <span>{relation.title}</span>
                    </>
                  )}
                </div>
              ))
            ) : (
              <Typography.Text type="secondary">暂无关联</Typography.Text>
            )}
          </Space>
        </div>

        <div className="doc-panel">
          <Typography.Title level={5}>权限</Typography.Title>
          <Space wrap>
            {detail.permissions.map((permission) => (
              <Tag key={permission.id}>{permission.displayName}: {permissionText(permission.permissionLevel)}</Tag>
            ))}
          </Space>
        </div>

        <div className="doc-panel">
          <Typography.Title level={5}>
            <CommentOutlined /> 评论
          </Typography.Title>
          <Space orientation="vertical" size={8} className="doc-panel-list">
            {detail.comments.map((comment) => (
              <div className={`doc-comment-item${comment.id === activeCommentId ? ' active' : ''}${comment.resolved ? ' resolved' : ''}`} key={comment.id} id={`doc-comment-${comment.id}`}>
                <Space wrap>
                  <strong>{comment.authorName}</strong>
                  {comment.blockId ? <Tag>{blockLabel(detail.blocks, comment.blockId)}</Tag> : <Tag>全文</Tag>}
                  {comment.resolved ? <Tag color="green">已解决</Tag> : <Tag color="orange">未解决</Tag>}
                </Space>
                <span>{comment.content}</span>
                <Space wrap>
                  {comment.blockId ? (
                    <Button size="small" href={`#doc-block-${comment.blockId}`}>
                      定位
                    </Button>
                  ) : null}
                  {!comment.resolved ? (
                    <Button
                      size="small"
                      icon={<CheckCircleOutlined />}
                      loading={resolvingCommentId === comment.id}
                      disabled={!canEdit}
                      onClick={() => onResolveComment(comment.id)}
                    >
                      解决
                    </Button>
                  ) : (
                    <Typography.Text type="secondary">
                      {comment.resolvedByName ? `${comment.resolvedByName} 解决` : '已解决'}
                    </Typography.Text>
                  )}
                </Space>
              </div>
            ))}
            {detail.comments.length === 0 ? <Typography.Text type="secondary">暂无评论</Typography.Text> : null}
            <Select
              allowClear
              disabled={!canEdit}
              placeholder="关联到文档块"
              value={commentBlockId}
              onChange={onCommentBlockChange}
              options={detail.blocks.map((block, index) => ({
                value: block.id,
                label: `${index + 1}. ${blockTypeText(block.blockType)} ${block.content || '空块'}`,
              }))}
            />
            <Input.TextArea
              rows={3}
              disabled={!canEdit}
              value={commentDraft}
              placeholder="评论支持 @username 提醒成员"
              onChange={(event) => onCommentDraftChange(event.target.value)}
            />
            <Button disabled={!canEdit || !commentDraft.trim()} loading={commenting} onClick={onComment}>
              发送评论
            </Button>
          </Space>
        </div>
      </section>
    </div>
  )
}

function hasPermission(current: string | undefined, required: 'view' | 'edit' | 'manage') {
  const rank = { view: 1, edit: 2, manage: 3 }
  return current ? rank[current as keyof typeof rank] >= rank[required] : false
}

function permissionText(permission: string) {
  return { view: '可查看', edit: '可编辑', manage: '可管理' }[permission] ?? permission
}

function blockTypeText(blockType: string) {
  return {
    paragraph: '段落',
    heading: '标题',
    list: '列表',
    task: '任务',
    quote: '引用',
    code: '代码',
  }[blockType] ?? blockType
}

function blockLabel(blocks: DocumentDetail['blocks'], blockId: string) {
  const index = blocks.findIndex((block) => block.id === blockId)
  if (index < 0) {
    return '已删除块'
  }
  const block = blocks[index]
  return `${index + 1}. ${blockTypeText(block.blockType)}`
}

function renderMarkdownPreview(content: string) {
  return content
    .split('\n')
    .map((line) => line.replace(/^#{1,6}\s*/, '').trim())
    .filter(Boolean)
}
