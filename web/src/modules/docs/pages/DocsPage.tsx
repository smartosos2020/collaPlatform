import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  CheckCircleOutlined,
  CommentOutlined,
  DeleteOutlined,
  FileTextOutlined,
  FolderOpenOutlined,
  FolderOutlined,
  InboxOutlined,
  LinkOutlined,
  PlusOutlined,
  RollbackOutlined,
  SaveOutlined,
  SearchOutlined,
  ShareAltOutlined,
  StarFilled,
  StarOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App as AntdApp, Alert, Breadcrumb, Button, Empty, Form, Input, Modal, Select, Space, Switch, Tag, Tooltip, Tree, Typography } from 'antd'
import type { ReactNode } from 'react'
import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'

import { InternalLinkCard, ObjectSummaryCard } from '../../platform/components/InternalLinkCard'
import type { PlatformObjectSummary } from '../../platform/api/platformObjectsApi'
import { getTable, queryRecords, type BaseField, type BaseRecord } from '../../bases/api/basesApi'
import { objectTypeText } from '../../platform/objectTypeLabels'
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
  archiveDocument,
  createDocument,
  diffDocumentVersions,
  getDocument,
  getDocumentPath,
  grantDocumentPermission,
  listDocumentTree,
  listDocumentVersions,
  listDocuments,
  moveDocument,
  restoreDocument,
  restoreDocumentVersion,
  resolveDocumentComment,
  saveDocument,
  saveDocumentBlocks,
  type DocumentBlockDraft,
  type DocumentDetail,
  type DocumentSummary,
  type DocumentTreeNode,
} from '../api/docsApi'

type CreateDocForm = {
  title: string
  content?: string
  docType?: DocumentSummary['docType']
  parentId?: string
}

type PermissionForm = {
  userId: string
  permissionLevel: 'view' | 'edit' | 'manage'
}

type RelationForm = {
  targetType: 'issue' | 'base' | 'base_table' | 'base_record' | 'file'
  targetId: string
}

type MoveDocForm = {
  parentId?: string
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

type TableBlockContent = {
  columns: string[]
  rows: string[][]
}

type EmbedBlockContent = {
  objectType?: string
  objectId?: string
  viewId?: string
  title?: string
}

type TreeDataNode = {
  key: string
  title: ReactNode
  children?: TreeDataNode[]
}

const ROOT_PARENT_VALUE = '__root__'
const EMBED_BLOCK_TYPES = new Set(['embed', 'base_view', 'issue_embed', 'message_embed', 'file_embed', 'link'])

const blockAccessText: Record<string, string> = {
  forbidden: '无权限查看嵌入对象',
  deleted: '嵌入对象已删除',
  not_found: '嵌入对象不存在或不可见',
  invalid: '嵌入对象配置无效',
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
  const [createDocType, setCreateDocType] = useState<DocumentSummary['docType']>('markdown')
  const [moveOpen, setMoveOpen] = useState(false)
  const [permissionOpen, setPermissionOpen] = useState(false)
  const [relationOpen, setRelationOpen] = useState(false)
  const [commentDraft, setCommentDraft] = useState('')
  const [commentBlockId, setCommentBlockId] = useState<string | undefined>()
  const [conflictDocId, setConflictDocId] = useState<string | null>(null)
  const [diffToVersionNo, setDiffToVersionNo] = useState<number | null>(null)
  const [docSearch, setDocSearch] = useState('')
  const [docListMode, setDocListMode] = useState<DocListMode>('all')
  const [includeArchived, setIncludeArchived] = useState(false)
  const [blockDraftDocId, setBlockDraftDocId] = useState<string | null>(null)
  const [blockDraftVersionNo, setBlockDraftVersionNo] = useState(0)
  const [blockDrafts, setBlockDrafts] = useState<BlockDraftState[]>([])
  const [createForm] = Form.useForm<CreateDocForm>()
  const [moveForm] = Form.useForm<MoveDocForm>()
  const [permissionForm] = Form.useForm<PermissionForm>()
  const [relationForm] = Form.useForm<RelationForm>()

  const docsQuery = useQuery({
    queryKey: ['docs', 'list', includeArchived],
    queryFn: () => listDocuments({ includeArchived }),
  })
  const docTreeQuery = useQuery({
    queryKey: ['docs', 'tree', includeArchived],
    queryFn: () => listDocumentTree({ includeArchived }),
  })
  const membersQuery = useQuery({ queryKey: ['members', 'directory'], queryFn: listDirectoryMembers })
  const recentObjectsQuery = useQuery({ queryKey: ['platform', 'recent', 'docs'], queryFn: () => listRecentObjects(30) })
  const favoriteObjectsQuery = useQuery({ queryKey: ['platform', 'favorites', 'docs'], queryFn: () => listFavoriteObjects(50) })
  const activeDocId = docId ?? selectedDocId ?? docsQuery.data?.[0]?.id ?? null
  const activeCommentId = new URLSearchParams(location.search).get('commentId')
  const activeDocument = docsQuery.data?.find((document) => document.id === activeDocId) ?? null
  const activeDirectoryId =
    activeDocument?.docType === 'folder' || activeDocument?.docType === 'space'
      ? activeDocument.id
      : activeDocument?.parentId ?? null
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
  const pathQuery = useQuery({
    queryKey: ['docs', activeDocId, 'path'],
    queryFn: () => getDocumentPath(activeDocId || ''),
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

  const treeData = useMemo(
    () => buildDocumentTreeData(docTreeQuery.data ?? [], activeDocId, favoriteDocumentIds),
    [activeDocId, docTreeQuery.data, favoriteDocumentIds],
  )

  const folderOptions = useMemo(() => {
    const docs = docsQuery.data ?? []
    return docs
      .filter((document) => ['space', 'folder'].includes(document.docType) && !document.archived)
      .sort((left, right) => left.title.localeCompare(right.title))
      .map((document) => ({ label: `${docTypeText(document.docType)} / ${document.title}`, value: document.id }))
  }, [docsQuery.data])

  const moveFolderOptions = useMemo(() => {
    const docs = docsQuery.data ?? []
    return folderOptions.filter((option) => {
      if (!activeDocId || option.value === activeDocId) {
        return false
      }
      return !isDescendantDocument(docs, activeDocId, option.value)
    })
  }, [activeDocId, docsQuery.data, folderOptions])

  const activeSiblings = useMemo(() => {
    if (!activeDocument) {
      return []
    }
    return (docsQuery.data ?? [])
      .filter((document) => (document.parentId ?? null) === (activeDocument.parentId ?? null) && !document.archived)
      .sort(documentSort)
  }, [activeDocument, docsQuery.data])

  const activeSiblingIndex = activeDocId ? activeSiblings.findIndex((document) => document.id === activeDocId) : -1

  const openCreate = (docType: DocumentSummary['docType']) => {
    setCreateDocType(docType)
    createForm.setFieldsValue({
      docType,
      parentId: activeDirectoryId ?? ROOT_PARENT_VALUE,
      content: docType === 'markdown' ? '' : '',
    })
    setCreateOpen(true)
  }

  const createMutation = useMutation({
    mutationFn: (values: CreateDocForm) =>
      createDocument({
        title: values.title,
        content: values.content,
        docType: values.docType ?? createDocType,
        parentId: values.parentId === ROOT_PARENT_VALUE ? null : values.parentId,
      }),
    onSuccess: async (detail) => {
      setCreateOpen(false)
      createForm.resetFields()
      setSelectedDocId(detail.document.id)
      navigate(`/docs/${detail.document.id}`)
      await refreshDocs(detail.document.id)
    },
  })

  const moveMutation = useMutation({
    mutationFn: ({ documentId, parentId, sortOrder }: { documentId: string; parentId?: string | null; sortOrder?: number }) =>
      moveDocument(documentId, { parentId, sortOrder }),
    onSuccess: async (detail) => {
      setMoveOpen(false)
      moveForm.resetFields()
      message.success('位置已更新')
      await refreshDocs(detail.document.id)
    },
    onError: () => message.error('移动失败'),
  })

  const archiveMutation = useMutation({
    mutationFn: (documentId: string) => archiveDocument(documentId),
    onSuccess: async (detail) => {
      message.success('已归档')
      await refreshDocs(detail.document.id)
      if (!includeArchived) {
        navigate('/docs')
      }
    },
  })

  const restoreMutationForDocument = useMutation({
    mutationFn: (documentId: string) => restoreDocument(documentId),
    onSuccess: async (detail) => {
      message.success('已恢复')
      await refreshDocs(detail.document.id)
      navigate(`/docs/${detail.document.id}`)
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

  const openMove = () => {
    if (!activeDocument) {
      return
    }
    moveForm.setFieldsValue({ parentId: activeDocument.parentId ?? ROOT_PARENT_VALUE })
    setMoveOpen(true)
  }

  const submitMove = (values: MoveDocForm) => {
    if (!activeDocId || !activeDocument) {
      return
    }
    moveMutation.mutate({
      documentId: activeDocId,
      parentId: values.parentId === ROOT_PARENT_VALUE ? null : values.parentId,
      sortOrder: activeDocument.sortOrder,
    })
  }

  const reorderActiveDocument = (direction: -1 | 1) => {
    if (!activeDocId || !activeDocument || activeSiblingIndex < 0) {
      return
    }
    const target = activeSiblings[activeSiblingIndex + direction]
    if (!target) {
      return
    }
    moveMutation.mutate({
      documentId: activeDocId,
      parentId: activeDocument.parentId ?? null,
      sortOrder: target.sortOrder + direction,
    })
  }

  const archiveActiveDocument = () => {
    if (!activeDocId || !activeDocument) {
      return
    }
    Modal.confirm({
      title: `归档「${activeDocument.title}」`,
      content: '归档会同时隐藏其子目录和子文档。需要显示归档后才能查看或恢复。',
      okText: '归档',
      okButtonProps: { danger: true },
      onOk: () => archiveMutation.mutate(activeDocId),
    })
  }

  return (
    <div className="docs-workspace">
      <aside className="docs-sidebar">
        <div className="section-heading">
          <div>
            <Typography.Title level={4}>团队空间</Typography.Title>
            <Typography.Text type="secondary">{visibleDocs.length} / {docsQuery.data?.length ?? 0} 个节点</Typography.Text>
          </div>
          <Space>
            <Tooltip title="新建文件夹">
              <Button icon={<FolderOutlined />} onClick={() => openCreate('folder')} />
            </Tooltip>
            <Tooltip title="新建文档">
              <Button icon={<PlusOutlined />} type="primary" onClick={() => openCreate('markdown')} />
            </Tooltip>
          </Space>
        </div>

        <Space orientation="vertical" size={8} className="docs-list-controls">
          <Input
            allowClear
            prefix={<SearchOutlined />}
            placeholder="搜索标题"
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
          <div className="docs-archive-toggle">
            <span>显示归档</span>
            <Switch size="small" checked={includeArchived} onChange={setIncludeArchived} />
          </div>
        </Space>

        {activeDocument ? (
          <Space wrap className="docs-tree-actions">
            <Tooltip title="上移">
              <Button
                icon={<ArrowUpOutlined />}
                disabled={activeSiblingIndex <= 0 || activeDocument.archived}
                loading={moveMutation.isPending}
                onClick={() => reorderActiveDocument(-1)}
              />
            </Tooltip>
            <Tooltip title="下移">
              <Button
                icon={<ArrowDownOutlined />}
                disabled={activeSiblingIndex < 0 || activeSiblingIndex >= activeSiblings.length - 1 || activeDocument.archived}
                loading={moveMutation.isPending}
                onClick={() => reorderActiveDocument(1)}
              />
            </Tooltip>
            <Tooltip title="移动到">
              <Button icon={<FolderOpenOutlined />} disabled={activeDocument.archived} onClick={openMove} />
            </Tooltip>
            {activeDocument.archived ? (
              <Tooltip title="恢复">
                <Button
                  icon={<RollbackOutlined />}
                  loading={restoreMutationForDocument.isPending}
                  onClick={() => restoreMutationForDocument.mutate(activeDocument.id)}
                />
              </Tooltip>
            ) : (
              <Tooltip title="归档">
                <Button danger icon={<InboxOutlined />} loading={archiveMutation.isPending} onClick={archiveActiveDocument} />
              </Tooltip>
            )}
          </Space>
        ) : null}

        {docListMode === 'all' && !docSearch.trim() ? (
          <div className="docs-tree-panel">
            <Tree
              key={`docs-tree-${includeArchived}-${treeData.length}`}
              blockNode
              defaultExpandAll
              selectedKeys={activeDocId ? [activeDocId] : []}
              treeData={treeData}
              onSelect={(keys) => {
                const key = String(keys[0] ?? '')
                if (key) {
                  setSelectedDocId(key)
                  navigate(`/docs/${key}`)
                }
              }}
            />
            {treeData.length === 0 ? <Empty description="暂无文档空间" /> : null}
          </div>
        ) : (
          <Space orientation="vertical" size={8} className="docs-list">
            {visibleDocs.map((document) => (
              <div className="doc-list-row" key={document.id}>
                <button
                  className={`doc-list-item${document.id === activeDocId ? ' active' : ''}${document.archived ? ' archived' : ''}`}
                  type="button"
                  onClick={() => {
                    setSelectedDocId(document.id)
                    navigate(`/docs/${document.id}`)
                  }}
                >
                  {document.docType === 'markdown' ? <FileTextOutlined /> : <FolderOutlined />}
                  <span>
                    <strong>{document.title}</strong>
                    <small>{docTypeText(document.docType)} · {permissionText(document.permissionLevel)}{document.archived ? ' · 已归档' : ''}</small>
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
        )}
      </aside>

      <main className="docs-main">
        {pathQuery.data && pathQuery.data.length > 0 ? (
          <div className="docs-breadcrumb-bar">
            <Breadcrumb
              items={pathQuery.data.map((item) => ({
                title: item.id === activeDocId ? item.title : <button type="button" onClick={() => navigate(`/docs/${item.id}`)}>{item.title}</button>,
              }))}
            />
          </div>
        ) : null}
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
            <Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate('markdown')}>
              新建文档
            </Button>
          </Empty>
        )}
      </main>

      <Modal
        title={createDocType === 'folder' ? '新建文件夹' : '新建文档'}
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => createForm.submit()}
        confirmLoading={createMutation.isPending}
      >
        <Form form={createForm} layout="vertical" onFinish={(values) => createMutation.mutate(values)}>
          <Form.Item name="docType" hidden initialValue={createDocType}>
            <Input />
          </Form.Item>
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="parentId" label="父级目录" initialValue={activeDirectoryId ?? ROOT_PARENT_VALUE}>
            <Select
              options={[
                { label: '团队空间根目录', value: ROOT_PARENT_VALUE },
                ...folderOptions,
              ]}
            />
          </Form.Item>
          {createDocType === 'markdown' ? (
          <Form.Item name="content" label="内容">
            <Input.TextArea rows={8} />
          </Form.Item>
          ) : null}
        </Form>
      </Modal>

      <Modal
        title="移动文档"
        open={moveOpen}
        onCancel={() => setMoveOpen(false)}
        onOk={() => moveForm.submit()}
        confirmLoading={moveMutation.isPending}
      >
        <Form form={moveForm} layout="vertical" onFinish={submitMove}>
          <Form.Item name="parentId" label="移动到" initialValue={activeDocument?.parentId ?? ROOT_PARENT_VALUE}>
            <Select
              options={[
                { label: '团队空间根目录', value: ROOT_PARENT_VALUE },
                ...moveFolderOptions,
              ]}
            />
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
                    onChange={(value) => {
                      const nextType = value as DocumentBlockDraft['blockType']
                      onBlockChange(index, { blockType: nextType, content: defaultBlockContent(nextType, block.content) })
                    }}
                    options={[
                      { value: 'paragraph', label: '段落' },
                      { value: 'heading', label: '标题' },
                      { value: 'list', label: '列表' },
                      { value: 'task', label: '任务' },
                      { value: 'quote', label: '引用' },
                      { value: 'code', label: '代码' },
                      { value: 'table', label: '表格' },
                      { value: 'base_view', label: 'Base 视图' },
                      { value: 'issue_embed', label: '事项/BUG' },
                      { value: 'message_embed', label: '消息' },
                      { value: 'file_embed', label: '文件' },
                      { value: 'embed', label: '对象卡片' },
                    ]}
                  />
                  {block.blockType === 'table' ? (
                    <TableBlockEditor
                      value={block.content}
                      disabled={!canEdit}
                      onChange={(content) => onBlockChange(index, { content })}
                    />
                  ) : isEmbedBlockType(block.blockType) ? (
                    <EmbedBlockEditor
                      blockType={block.blockType}
                      value={block.content}
                      disabled={!canEdit}
                      summary={detail.blocks.find((item) => item.id === block.id)?.embedSummary ?? null}
                      onChange={(content) => onBlockChange(index, { content })}
                    />
                  ) : (
                    <Input.TextArea
                      className="doc-block-content"
                      disabled={!canEdit}
                      autoSize={{ minRows: 1, maxRows: 6 }}
                      value={block.content}
                      onChange={(event) => onBlockChange(index, { content: event.target.value })}
                    />
                  )}
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
                label: `${index + 1}. ${blockTypeText(block.blockType)} ${blockContentLabel(block)}`,
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

function TableBlockEditor({
  value,
  disabled,
  onChange,
}: {
  value: string
  disabled: boolean
  onChange: (content: string) => void
}) {
  const table = parseTableBlock(value)
  const emit = (next: TableBlockContent) => onChange(serializeTableBlock(normalizeTableBlock(next)))
  const updateColumn = (columnIndex: number, nextValue: string) => {
    emit({ ...table, columns: table.columns.map((column, index) => (index === columnIndex ? nextValue : column)) })
  }
  const updateCell = (rowIndex: number, columnIndex: number, nextValue: string) => {
    emit({
      ...table,
      rows: table.rows.map((row, index) =>
        index === rowIndex ? row.map((cell, cellIndex) => (cellIndex === columnIndex ? nextValue : cell)) : row,
      ),
    })
  }
  const addColumn = () => {
    emit({
      columns: [...table.columns, `列 ${table.columns.length + 1}`],
      rows: table.rows.map((row) => [...row, '']),
    })
  }
  const removeColumn = () => {
    if (table.columns.length <= 1) {
      return
    }
    emit({
      columns: table.columns.slice(0, -1),
      rows: table.rows.map((row) => row.slice(0, -1)),
    })
  }
  const addRow = () => {
    emit({ ...table, rows: [...table.rows, table.columns.map(() => '')] })
  }
  const removeRow = () => {
    if (table.rows.length <= 1) {
      return
    }
    emit({ ...table, rows: table.rows.slice(0, -1) })
  }

  return (
    <div className="doc-table-block-editor">
      <div className="doc-table-toolbar">
        <Space size={4}>
          <Tooltip title="新增行">
            <Button size="small" icon={<PlusOutlined />} disabled={disabled} onClick={addRow}>
              行
            </Button>
          </Tooltip>
          <Tooltip title="删除末行">
            <Button size="small" icon={<DeleteOutlined />} disabled={disabled || table.rows.length <= 1} onClick={removeRow}>
              行
            </Button>
          </Tooltip>
          <Tooltip title="新增列">
            <Button size="small" icon={<PlusOutlined />} disabled={disabled} onClick={addColumn}>
              列
            </Button>
          </Tooltip>
          <Tooltip title="删除末列">
            <Button size="small" icon={<DeleteOutlined />} disabled={disabled || table.columns.length <= 1} onClick={removeColumn}>
              列
            </Button>
          </Tooltip>
        </Space>
      </div>
      <div className="doc-table-block-scroll">
        <table className="doc-table-block">
          <thead>
            <tr>
              {table.columns.map((column, columnIndex) => (
                <th key={`column-${columnIndex}`}>
                  <Input
                    disabled={disabled}
                    value={column}
                    onChange={(event) => updateColumn(columnIndex, event.target.value)}
                  />
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {table.rows.map((row, rowIndex) => (
              <tr key={`row-${rowIndex}`}>
                {table.columns.map((_, columnIndex) => (
                  <td key={`cell-${rowIndex}-${columnIndex}`}>
                    <Input
                      disabled={disabled}
                      value={row[columnIndex] ?? ''}
                      onChange={(event) => updateCell(rowIndex, columnIndex, event.target.value)}
                    />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function EmbedBlockEditor({
  blockType,
  value,
  disabled,
  summary,
  onChange,
}: {
  blockType: DocumentBlockDraft['blockType']
  value: string
  disabled: boolean
  summary?: PlatformObjectSummary | null
  onChange: (content: string) => void
}) {
  const embed = parseEmbedBlock(value)
  const fixedObjectType = fixedEmbedObjectType(blockType)
  const objectType = fixedObjectType ?? embed.objectType ?? 'issue'
  const emit = (next: EmbedBlockContent) => onChange(serializeEmbedBlock({ ...embed, ...next }))

  return (
    <div className="doc-embed-block-editor">
      <Space wrap className="doc-embed-fields">
        {fixedObjectType ? (
          <Tag>{objectTypeText[fixedObjectType] ?? fixedObjectType}</Tag>
        ) : (
          <Select
            className="doc-embed-type"
            disabled={disabled}
            value={objectType}
            onChange={(nextType) => emit({ objectType: nextType })}
            options={[
              { value: 'issue', label: '事项/BUG' },
              { value: 'document', label: '文档' },
              { value: 'base', label: 'Base' },
              { value: 'base_table', label: '数据表' },
              { value: 'base_record', label: '表格记录' },
              { value: 'message', label: '消息' },
              { value: 'approval', label: '审批' },
              { value: 'file', label: '文件' },
            ]}
          />
        )}
        <Input
          className="doc-embed-id"
          disabled={disabled}
          placeholder="对象 ID"
          value={embed.objectId ?? ''}
          onChange={(event) => emit({ objectType, objectId: event.target.value })}
        />
        {blockType === 'base_view' ? (
          <Input
            className="doc-embed-view-id"
            disabled={disabled}
            placeholder="视图 ID"
            value={embed.viewId ?? ''}
            onChange={(event) => emit({ objectType, viewId: event.target.value })}
          />
        ) : null}
      </Space>
      {summary ? (
        <EmbedSummaryPreview summary={summary} blockType={blockType} embed={embed} />
      ) : (
        <Typography.Text type="secondary">保存后解析对象摘要</Typography.Text>
      )}
    </div>
  )
}

function EmbedSummaryPreview({
  summary,
  blockType,
  embed,
}: {
  summary: PlatformObjectSummary
  blockType: DocumentBlockDraft['blockType']
  embed: EmbedBlockContent
}) {
  const baseId = typeof summary.metadata?.baseId === 'string' ? summary.metadata.baseId : null
  const tableId = summary.objectId
  const shouldLoadBaseView = blockType === 'base_view' && summary.accessState === 'available' && Boolean(baseId && tableId)
  const tableQuery = useQuery({
    queryKey: ['docs', 'base-view', baseId, tableId, 'table'],
    queryFn: () => getTable(baseId || '', tableId),
    enabled: shouldLoadBaseView,
  })
  const selectedView = tableQuery.data?.views.find((view) => view.id === embed.viewId)
  const recordsQuery = useQuery({
    queryKey: ['docs', 'base-view', baseId, tableId, selectedView?.id ?? 'default', 'records'],
    queryFn: () =>
      queryRecords(baseId || '', tableId, {
        filters: selectedView?.filters ?? [],
        sorts: selectedView?.sorts ?? [],
        limit: 5,
        offset: 0,
      }),
    enabled: shouldLoadBaseView && tableQuery.isSuccess,
  })

  if (summary.accessState !== 'available') {
    return <Alert type="warning" showIcon message={blockAccessText[summary.accessState] ?? '嵌入对象不可访问'} />
  }
  if (!shouldLoadBaseView) {
    return <ObjectSummaryCard summary={summary} />
  }
  const fields = tableQuery.data?.fields ?? []
  const visibleFieldIds = selectedView?.visibleFieldIds ?? []
  const visibleFields = visibleFieldIds.length > 0 ? fields.filter((field) => visibleFieldIds.includes(field.id)) : fields.slice(0, 4)
  return (
    <div className="doc-base-view-preview">
      <ObjectSummaryCard summary={summary} />
      <div className="doc-base-view-grid">
        <div className="doc-base-view-row header">
          <span>#</span>
          {visibleFields.map((field) => <span key={field.id}>{field.name}</span>)}
        </div>
        {(recordsQuery.data?.items ?? []).map((record) => (
          <div className="doc-base-view-row" key={record.id}>
            <span>{record.recordNo}</span>
            {visibleFields.map((field) => <span key={field.id}>{baseCellValue(field, record)}</span>)}
          </div>
        ))}
        {recordsQuery.isLoading ? <Typography.Text type="secondary">加载视图中...</Typography.Text> : null}
        {!recordsQuery.isLoading && (recordsQuery.data?.items.length ?? 0) === 0 ? (
          <Typography.Text type="secondary">当前视图暂无记录</Typography.Text>
        ) : null}
      </div>
    </div>
  )
}

function baseCellValue(field: BaseField, record: BaseRecord) {
  const value = record.values[field.id] ?? record.values[field.name]
  if (value === undefined || value === null || value === '') {
    return '-'
  }
  if (Array.isArray(value)) {
    return value.join(', ')
  }
  if (typeof value === 'object' && 'objectType' in value && 'objectId' in value) {
    const linkValue = value as { objectType?: unknown; objectId?: unknown; title?: unknown }
    return String(linkValue.title ?? `${linkValue.objectType}:${linkValue.objectId}`)
  }
  return String(value)
}

function hasPermission(current: string | undefined, required: 'view' | 'edit' | 'manage') {
  const rank = { view: 1, edit: 2, manage: 3 }
  return current ? rank[current as keyof typeof rank] >= rank[required] : false
}

function permissionText(permission: string) {
  return { view: '可查看', edit: '可编辑', manage: '可管理' }[permission] ?? permission
}

function docTypeText(docType: string) {
  return { space: '空间', folder: '文件夹', markdown: '文档' }[docType] ?? docType
}

function documentSort(left: DocumentSummary, right: DocumentSummary) {
  if (left.sortOrder !== right.sortOrder) {
    return left.sortOrder - right.sortOrder
  }
  return left.title.localeCompare(right.title)
}

function isDescendantDocument(documents: DocumentSummary[], ancestorId: string, candidateId: string) {
  let current = documents.find((document) => document.id === candidateId)
  while (current?.parentId) {
    if (current.parentId === ancestorId) {
      return true
    }
    current = documents.find((document) => document.id === current?.parentId)
  }
  return false
}

function buildDocumentTreeData(
  nodes: DocumentTreeNode[],
  activeDocId: string | null,
  favoriteDocumentIds: Set<string>,
): TreeDataNode[] {
  return nodes.map((node) => ({
    key: node.document.id,
    title: (
      <span className={`doc-tree-title${node.document.id === activeDocId ? ' active' : ''}${node.document.archived ? ' archived' : ''}`}>
        {node.document.docType === 'markdown' ? <FileTextOutlined /> : <FolderOutlined />}
        <span>{node.document.title}</span>
        {node.document.docType !== 'markdown' ? <Tag>{docTypeText(node.document.docType)}</Tag> : null}
        {favoriteDocumentIds.has(node.document.id) ? <StarFilled className="doc-tree-star" /> : null}
        {node.document.archived ? <Tag color="default">归档</Tag> : null}
      </span>
    ),
    children: buildDocumentTreeData(node.children, activeDocId, favoriteDocumentIds),
  }))
}

function blockTypeText(blockType: string) {
  return {
    paragraph: '段落',
    heading: '标题',
    list: '列表',
    task: '任务',
    quote: '引用',
    code: '代码',
    table: '表格',
    embed: '对象卡片',
    base_view: 'Base 视图',
    issue_embed: '事项/BUG',
    message_embed: '消息',
    file_embed: '文件',
    link: '内部链接',
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

function blockContentLabel(block: DocumentDetail['blocks'][number]) {
  if (block.blockType === 'table') {
    const table = parseTableBlock(block.content)
    return `${table.columns.length} 列 / ${table.rows.length} 行`
  }
  if (isEmbedBlockType(block.blockType)) {
    return block.embedSummary?.title || blockAccessText[block.embedSummary?.accessState ?? ''] || '对象未解析'
  }
  const normalized = block.content.trim()
  return normalized.length > 32 ? `${normalized.slice(0, 32)}...` : normalized || '空块'
}

function isEmbedBlockType(blockType: string) {
  return EMBED_BLOCK_TYPES.has(blockType)
}

function fixedEmbedObjectType(blockType: string) {
  if (blockType === 'base_view') {
    return 'base_table'
  }
  if (blockType === 'issue_embed') {
    return 'issue'
  }
  if (blockType === 'message_embed') {
    return 'message'
  }
  if (blockType === 'file_embed') {
    return 'file'
  }
  return null
}

function defaultBlockContent(blockType: DocumentBlockDraft['blockType'], current: string) {
  if (blockType === 'table') {
    return serializeTableBlock(parseTableBlock(current))
  }
  if (isEmbedBlockType(blockType)) {
    const fixedObjectType = fixedEmbedObjectType(blockType)
    const parsed = parseEmbedBlock(current)
    return serializeEmbedBlock({
      objectType: fixedObjectType ?? parsed.objectType ?? 'issue',
      objectId: parsed.objectId ?? '',
      viewId: blockType === 'base_view' ? parsed.viewId ?? '' : undefined,
    })
  }
  return current
}

function parseTableBlock(value: string): TableBlockContent {
  const fallback = { columns: ['列 1', '列 2'], rows: [['', '']] }
  const parsed = parseJsonObject<TableBlockContent>(value)
  return normalizeTableBlock(parsed ?? fallback)
}

function normalizeTableBlock(table: TableBlockContent): TableBlockContent {
  const columns = Array.isArray(table.columns) && table.columns.length > 0
    ? table.columns.map((column, index) => String(column || `列 ${index + 1}`))
    : ['列 1']
  const rows = Array.isArray(table.rows) && table.rows.length > 0
    ? table.rows.map((row) => columns.map((_, index) => String(Array.isArray(row) ? row[index] ?? '' : '')))
    : [columns.map(() => '')]
  return { columns, rows }
}

function serializeTableBlock(table: TableBlockContent) {
  return JSON.stringify(normalizeTableBlock(table))
}

function parseEmbedBlock(value: string): EmbedBlockContent {
  return parseJsonObject<EmbedBlockContent>(value) ?? {}
}

function serializeEmbedBlock(embed: EmbedBlockContent) {
  return JSON.stringify({
    objectType: embed.objectType ?? 'issue',
    objectId: embed.objectId ?? '',
    ...(embed.viewId ? { viewId: embed.viewId } : {}),
    ...(embed.title ? { title: embed.title } : {}),
  })
}

function parseJsonObject<T>(value: string): T | null {
  if (!value.trim()) {
    return null
  }
  try {
    const parsed = JSON.parse(value) as T
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : null
  } catch {
    return null
  }
}

function renderMarkdownPreview(content: string) {
  return content
    .split('\n')
    .map((line) => line.replace(/^#{1,6}\s*/, '').trim())
    .filter(Boolean)
}
