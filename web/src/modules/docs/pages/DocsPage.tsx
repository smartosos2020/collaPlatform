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
import type { FormInstance } from 'antd/es/form'
import type { ReactNode } from 'react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'

import { InternalLinkCard, ObjectSummaryCard } from '../../platform/components/InternalLinkCard'
import type { PlatformObjectSummary } from '../../platform/api/platformObjectsApi'
import { getTable, queryRecords, type BaseField, type BaseRecord } from '../../bases/api/basesApi'
import { objectTypeText } from '../../platform/objectTypeLabels'
import { useAuthStore } from '../../auth/authStore'
import { DocEditor, type DocEditorSelectionAnchor } from '../components/DocEditor'
import { useDocumentCollaboration } from '../hooks/useDocumentCollaboration'
import {
  addObjectFavorite,
  listFavoriteObjects,
  listRecentObjects,
  markObjectAccessed,
  removeObjectFavorite,
} from '../../platform/api/platformObjectsApi'
import { listDirectoryMembers, listProjects } from '../../projects/api/projectsApi'
import { listUserGroups } from '../../admin/api/userGroupsApi'
import { ResourcePermissionsModal } from '../../permissions/components/ResourcePermissionsModal'
import {
  addDocumentComment,
  addDocumentCommentReply,
  addDocumentRelation,
  archiveDocument,
  createDocumentCheckpoint,
  createDocument,
  createDocumentFromTemplate,
  createIssueFromDocumentSelection,
  createNamedDocumentVersion,
  diffDocumentVersions,
  getDocument,
  getDocumentAcceptanceReport,
  getDocumentPath,
  importDocumentMarkdown,
  grantDocumentPermission,
  listDocumentTemplates,
  listDocumentTree,
  listDocumentVersions,
  listDocuments,
  moveDocument,
  requestDocumentPermission,
  restoreDocument,
  restoreDocumentVersion,
  reopenDocumentComment,
  resolveDocumentComment,
  saveDocument,
  saveDocumentBlocks,
  setDocumentShareLinkEnabled,
  updateDocumentKnowledgeBase,
  updateDocumentKnowledgeMetadata,
  updateDocumentShareLink,
  type DocumentAcceptanceReport,
  type DocumentBlockDraft,
  type DocumentComment,
  type DocumentDetail,
  type DocumentShareLink,
  type DocumentSummary,
  type DocumentTemplate,
  type DocumentTreeNode,
  type DocumentVersion,
} from '../api/docsApi'

type CreateDocForm = {
  title: string
  content?: string
  docType?: DocumentSummary['docType']
  parentId?: string
  description?: string
  coverUrl?: string
  defaultPermissionLevel?: DocumentSummary['defaultPermissionLevel']
  knowledgeBase?: boolean
}

type PermissionForm = {
  subjectType: 'user' | 'user_group'
  subjectId: string
  permissionLevel: DocumentSummary['permissionLevel']
}

type ShareLinkForm = {
  permissionLevel: 'view' | 'comment' | 'edit'
  enabled?: boolean
  expiresAt?: string
}

type KnowledgeBaseForm = {
  description?: string
  coverUrl?: string
  defaultPermissionLevel?: DocumentSummary['defaultPermissionLevel']
  knowledgeBase?: boolean
}

type KnowledgeMetadataForm = {
  maintainerId?: string
  tags?: string[]
  category?: string
  knowledgeStatus?: DocumentSummary['knowledgeStatus']
  reviewDueAt?: string
}

type PermissionRequestForm = {
  permissionLevel: 'view' | 'comment' | 'edit'
  reason?: string
}

type NamedVersionForm = {
  versionName: string
  summary?: string
}

type ImportMarkdownForm = {
  title?: string
  content: string
}

type TemplateForm = {
  templateId: string
  parentId?: string
  title?: string
}

type SelectionIssueForm = {
  projectId: string
  issueType: 'requirement' | 'task' | 'bug'
  title?: string
  description?: string
  priority?: 'low' | 'medium' | 'high' | 'urgent'
  assigneeId?: string
  dueAt?: string
}

type RelationForm = {
  targetType: 'issue' | 'base' | 'base_table' | 'base_record' | 'file' | 'message' | 'approval' | 'document'
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
type CommentFilter = 'open' | 'mentions' | 'current' | 'all'

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
  const [resourcePermissionOpen, setResourcePermissionOpen] = useState(false)
  const [relationOpen, setRelationOpen] = useState(false)
  const [namedVersionOpen, setNamedVersionOpen] = useState(false)
  const [importMarkdownOpen, setImportMarkdownOpen] = useState(false)
  const [templateOpen, setTemplateOpen] = useState(false)
  const [selectionIssueOpen, setSelectionIssueOpen] = useState(false)
  const [commentDraft, setCommentDraft] = useState('')
  const [commentBlockId, setCommentBlockId] = useState<string | undefined>()
  const [commentAnchor, setCommentAnchor] = useState<DocEditorSelectionAnchor | undefined>()
  const [commentReplyDrafts, setCommentReplyDrafts] = useState<Record<string, string>>({})
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
  const [shareLinkForm] = Form.useForm<ShareLinkForm>()
  const [knowledgeBaseForm] = Form.useForm<KnowledgeBaseForm>()
  const [knowledgeMetadataForm] = Form.useForm<KnowledgeMetadataForm>()
  const [permissionRequestForm] = Form.useForm<PermissionRequestForm>()
  const [namedVersionForm] = Form.useForm<NamedVersionForm>()
  const [importMarkdownForm] = Form.useForm<ImportMarkdownForm>()
  const [templateForm] = Form.useForm<TemplateForm>()
  const [selectionIssueForm] = Form.useForm<SelectionIssueForm>()
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
  const userGroupsQuery = useQuery({
    queryKey: ['admin', 'user-groups', 'active'],
    queryFn: () => listUserGroups({ activeOnly: true }),
  })
  const projectsQuery = useQuery({ queryKey: ['projects'], queryFn: listProjects })
  const templatesQuery = useQuery({ queryKey: ['docs', 'templates'], queryFn: () => listDocumentTemplates() })
  const acceptanceQuery = useQuery({ queryKey: ['docs', 'acceptance', 'v1'], queryFn: getDocumentAcceptanceReport })
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

  const canComment = hasPermission(docQuery.data?.document.permissionLevel, 'comment')
  const canEdit = hasPermission(docQuery.data?.document.permissionLevel, 'edit')
  const canManage = hasPermission(docQuery.data?.document.permissionLevel, 'manage')
  const activeShareLink = docQuery.data?.shareLinks?.[0] ?? null
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
      knowledgeMetadataForm.setFieldsValue({
        maintainerId: detail.document.maintainerId ?? undefined,
        tags: detail.document.tags ?? [],
        category: detail.document.category ?? undefined,
        knowledgeStatus: detail.document.knowledgeStatus ?? 'draft',
        reviewDueAt: detail.document.reviewDueAt ?? undefined,
      })
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
  }, [docQuery.data, knowledgeMetadataForm])

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
      knowledgeBase: docType === 'space',
      defaultPermissionLevel: 'view',
    })
    setCreateOpen(true)
  }

  const openTemplateCreate = () => {
    templateForm.setFieldsValue({ parentId: activeDirectoryId ?? ROOT_PARENT_VALUE })
    setTemplateOpen(true)
  }

  const createMutation = useMutation({
    mutationFn: (values: CreateDocForm) =>
      createDocument({
        title: values.title,
        content: values.content,
        docType: values.docType ?? createDocType,
        parentId: values.parentId === ROOT_PARENT_VALUE ? null : values.parentId,
        description: values.description,
        coverUrl: values.coverUrl,
        defaultPermissionLevel: values.defaultPermissionLevel,
        knowledgeBase: values.knowledgeBase,
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

  const checkpointMutation = useMutation({
    mutationFn: () => createDocumentCheckpoint(activeDocId || ''),
    onSuccess: async (detail) => {
      setDraft({
        docId: detail.document.id,
        title: detail.document.title,
        content: detail.content,
        baseVersionNo: detail.document.currentVersionNo,
      })
      setConflictDocId(null)
      message.success('已生成版本')
      await refreshDocs(detail.document.id)
    },
    onError: () => message.error('生成版本失败'),
  })

  const namedVersionMutation = useMutation({
    mutationFn: (values: NamedVersionForm) => createNamedDocumentVersion(activeDocId || '', values),
    onSuccess: async (detail) => {
      setNamedVersionOpen(false)
      namedVersionForm.resetFields()
      message.success('命名版本已保存')
      await refreshDocs(detail.document.id)
    },
    onError: () => message.error('命名版本保存失败'),
  })

  const importMarkdownMutation = useMutation({
    mutationFn: (values: ImportMarkdownForm) => importDocumentMarkdown(activeDocId || '', values),
    onSuccess: async (detail) => {
      setImportMarkdownOpen(false)
      importMarkdownForm.resetFields()
      message.success('Markdown 已导入')
      await refreshDocs(detail.document.id)
    },
    onError: () => message.error('Markdown 导入失败'),
  })

  const templateMutation = useMutation({
    mutationFn: (values: TemplateForm) =>
      createDocumentFromTemplate({
        templateId: values.templateId,
        title: values.title,
        parentId: values.parentId === ROOT_PARENT_VALUE ? null : values.parentId,
      }),
    onSuccess: async (detail) => {
      setTemplateOpen(false)
      templateForm.resetFields()
      setSelectedDocId(detail.document.id)
      navigate(`/docs/${detail.document.id}`)
      message.success('已从模板创建文档')
      await refreshDocs(detail.document.id)
    },
    onError: () => message.error('从模板创建失败'),
  })

  const restoreMutation = useMutation({
    mutationFn: (versionNo: number) => restoreDocumentVersion(activeDocId || '', versionNo),
    onSuccess: async (detail) => {
      message.success('版本已恢复')
      await refreshDocs(detail.document.id)
    },
  })

  const permissionMutation = useMutation({
    mutationFn: (values: PermissionForm) =>
      grantDocumentPermission(activeDocId || '', {
        subjectType: values.subjectType,
        subjectId: values.subjectId,
        userId: values.subjectType === 'user' ? values.subjectId : undefined,
        permissionLevel: values.permissionLevel,
      }),
    onSuccess: async () => {
      permissionForm.resetFields()
      message.success('权限主体已更新')
      await refreshDocs()
    },
  })

  const shareLinkMutation = useMutation({
    mutationFn: (values: ShareLinkForm) =>
      updateDocumentShareLink(activeDocId || '', {
        scope: 'workspace',
        permissionLevel: values.permissionLevel,
        enabled: values.enabled ?? true,
        expiresAt: values.expiresAt?.trim() || null,
      }),
    onSuccess: async () => {
      message.success('分享链接已更新')
      await refreshDocs()
    },
    onError: () => message.error('分享链接更新失败'),
  })

  const shareLinkToggleMutation = useMutation({
    mutationFn: (enabled: boolean) => setDocumentShareLinkEnabled(activeDocId || '', enabled),
    onSuccess: async (shareLink) => {
      message.success(shareLink.enabled ? '分享链接已启用' : '分享链接已停用')
      await refreshDocs()
    },
    onError: () => message.error('分享链接状态更新失败'),
  })

  const knowledgeBaseMutation = useMutation({
    mutationFn: (values: KnowledgeBaseForm) => updateDocumentKnowledgeBase(activeDocId || '', values),
    onSuccess: async () => {
      message.success('知识库设置已更新')
      await refreshDocs()
    },
    onError: () => message.error('知识库设置更新失败'),
  })

  const knowledgeMetadataMutation = useMutation({
    mutationFn: (values: KnowledgeMetadataForm) =>
      updateDocumentKnowledgeMetadata(activeDocId || '', {
        maintainerId: values.maintainerId || null,
        tags: values.tags ?? [],
        category: values.category?.trim() || null,
        knowledgeStatus: values.knowledgeStatus ?? 'draft',
        reviewDueAt: values.reviewDueAt?.trim() || null,
        verifiedAt: values.knowledgeStatus === 'verified' ? new Date().toISOString() : undefined,
      }),
    onSuccess: async () => {
      message.success('知识元数据已更新')
      await refreshDocs()
    },
    onError: () => message.error('知识元数据更新失败'),
  })

  const permissionRequestMutation = useMutation({
    mutationFn: (values: PermissionRequestForm) => requestDocumentPermission(activeDocId || '', values),
    onSuccess: (result) => {
      permissionRequestForm.resetFields()
      message.success(result.notifiedCount > 0 ? '权限申请已发送' : '已记录申请，当前没有可通知的管理员')
    },
    onError: () => message.error('权限申请失败'),
  })

  const relationMutation = useMutation({
    mutationFn: (values: RelationForm) => addDocumentRelation(activeDocId || '', values),
    onSuccess: async () => {
      setRelationOpen(false)
      relationForm.resetFields()
      await refreshDocs()
    },
  })

  const selectionIssueMutation = useMutation({
    mutationFn: (values: SelectionIssueForm) =>
      createIssueFromDocumentSelection(activeDocId || '', {
        projectId: values.projectId,
        issueType: values.issueType,
        title: values.title,
        description: values.description,
        priority: values.priority ?? 'medium',
        assigneeId: values.assigneeId,
        dueAt: values.dueAt,
        anchorStart: commentAnchor?.anchorStart,
        anchorEnd: commentAnchor?.anchorEnd,
        anchorText: commentAnchor?.anchorText,
      }),
    onSuccess: async (detail) => {
      setSelectionIssueOpen(false)
      selectionIssueForm.resetFields()
      message.success('已从选区创建事项')
      await refreshDocs()
      navigate(`/issues/${detail.issue.id}`)
    },
    onError: () => message.error('从选区创建事项失败'),
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
    mutationFn: () =>
      addDocumentComment(activeDocId || '', {
        content: commentDraft,
        ...(commentBlockId
          ? { blockId: commentBlockId, anchorType: 'block' as const }
          : commentAnchor
            ? commentAnchor
            : { anchorType: 'document' as const }),
      }),
    onSuccess: async () => {
      setCommentDraft('')
      setCommentBlockId(undefined)
      setCommentAnchor(undefined)
      await refreshDocs()
    },
  })

  const replyCommentMutation = useMutation({
    mutationFn: ({ commentId, content }: { commentId: string; content: string }) =>
      addDocumentCommentReply(activeDocId || '', commentId, { content }),
    onSuccess: async (_detail, variables) => {
      setCommentReplyDrafts((current) => ({ ...current, [variables.commentId]: '' }))
      await refreshDocs()
    },
  })

  const resolveCommentMutation = useMutation({
    mutationFn: (commentId: string) => resolveDocumentComment(activeDocId || '', commentId),
    onSuccess: async () => {
      await refreshDocs()
    },
  })

  const reopenCommentMutation = useMutation({
    mutationFn: (commentId: string) => reopenDocumentComment(activeDocId || '', commentId),
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

  const updateDraft = (next: Partial<Pick<DraftState, 'title' | 'content'>>) => {
    if (!activeDocId) {
      return
    }
    setDraft((current) => ({
      docId: activeDocId,
      title: next.title ?? (current.docId === activeDocId ? current.title : titleDraft),
      content: next.content ?? (current.docId === activeDocId ? current.content : contentDraft),
      baseVersionNo,
    }))
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

  const openPermissionSettings = () => {
    setResourcePermissionOpen(true)
  }

  const copyShareLink = async (shareLink: DocumentShareLink | null) => {
    if (!activeDocId || !shareLink) {
      message.warning('请先创建分享链接')
      return
    }
    const url = `${window.location.origin}/docs/${activeDocId}?share=${shareLink.token}`
    try {
      await window.navigator.clipboard.writeText(url)
      message.success('链接已复制')
    } catch {
      message.error('复制失败')
    }
  }

  const openSelectionIssue = () => {
    if (!commentAnchor) {
      message.warning('请先选择文档正文')
      return
    }
    selectionIssueForm.setFieldsValue({
      projectId: projectsQuery.data?.[0]?.id,
      issueType: 'task',
      priority: 'medium',
      title: commentAnchor.anchorText.replace(/\s+/g, ' ').trim().slice(0, 120),
      description: '',
    })
    setSelectionIssueOpen(true)
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
            <Tooltip title="新建知识库">
              <Button icon={<FolderOpenOutlined />} onClick={() => openCreate('space')} />
            </Tooltip>
            <Tooltip title="从模板创建">
              <Button icon={<FileTextOutlined />} onClick={openTemplateCreate} />
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
            canComment={canComment}
            canEdit={canEdit}
            canManage={canManage}
            conflictVisible={conflictDocId === activeDocId}
            acceptanceReport={acceptanceQuery.data}
            versions={versionsQuery.data ?? []}
            blockDrafts={blockDraftDocId === activeDocId ? blockDrafts : []}
            saving={docQuery.data.document.docType === 'markdown' ? checkpointMutation.isPending : saveMutation.isPending}
            savingBlocks={blockMutation.isPending}
            restoringVersionNo={restoreMutation.variables}
            commentDraft={commentDraft}
            commentBlockId={commentBlockId}
            commentAnchor={commentAnchor}
            commentReplyDrafts={commentReplyDrafts}
            activeCommentId={activeCommentId}
            onTitleChange={(value) => updateDraft({ title: value })}
            onContentChange={(value) => updateDraft({ content: value })}
            onBlockChange={updateBlockDraft}
            onAddBlock={addBlockDraft}
            onRemoveBlock={removeBlockDraft}
            onMoveBlock={moveBlockDraft}
            onSave={() => {
              if (docQuery.data.document.docType === 'markdown') {
                checkpointMutation.mutate()
                return
              }
              saveMutation.mutate()
            }}
            onSaveBlocks={() => blockMutation.mutate()}
            onRefresh={() => refreshDocs()}
            onOpenPermission={openPermissionSettings}
            onOpenRelation={() => setRelationOpen(true)}
            onOpenNamedVersion={() => setNamedVersionOpen(true)}
            onOpenImportMarkdown={() => {
              importMarkdownForm.setFieldsValue({ title: titleDraft, content: contentDraft })
              setImportMarkdownOpen(true)
            }}
            onOpenSelectionIssue={openSelectionIssue}
            knowledgeMetadataForm={knowledgeMetadataForm}
            knowledgeMaintainerOptions={(membersQuery.data ?? []).map((member) => ({
              label: `${member.displayName} @${member.username}`,
              value: member.id,
            }))}
            onSaveKnowledgeMetadata={(values) => knowledgeMetadataMutation.mutate(values)}
            savingKnowledgeMetadata={knowledgeMetadataMutation.isPending}
            onRestore={(versionNo) => restoreMutation.mutate(versionNo)}
            onDiff={(versionNo) => setDiffToVersionNo(versionNo)}
            onCommentDraftChange={setCommentDraft}
            onCommentBlockChange={setCommentBlockId}
            onCommentAnchorChange={setCommentAnchor}
            onComment={() => commentMutation.mutate()}
            commenting={commentMutation.isPending}
            onReplyDraftChange={(commentId, value) => setCommentReplyDrafts((current) => ({ ...current, [commentId]: value }))}
            onReplyComment={(commentId) => replyCommentMutation.mutate({ commentId, content: commentReplyDrafts[commentId] ?? '' })}
            replyingCommentId={replyCommentMutation.variables?.commentId}
            onResolveComment={(commentId) => resolveCommentMutation.mutate(commentId)}
            resolvingCommentId={resolveCommentMutation.variables}
            onReopenComment={(commentId) => reopenCommentMutation.mutate(commentId)}
            reopeningCommentId={reopenCommentMutation.variables}
            onActivateComment={(commentId) => navigate(`/docs/${docQuery.data.document.id}?commentId=${commentId}`)}
          />
        ) : docQuery.isError && activeDocId ? (
          <div className="doc-access-request">
            <Alert
              type="warning"
              showIcon
              message="无法访问该文档"
              description="你可以向文档管理员申请查看、评论或编辑权限。"
            />
            <Form
              form={permissionRequestForm}
              layout="vertical"
              onFinish={(values) => permissionRequestMutation.mutate(values)}
              initialValues={{ permissionLevel: 'view' }}
            >
              <Form.Item name="permissionLevel" label="申请权限" rules={[{ required: true }]}>
                <Select
                  options={[
                    { label: '可查看', value: 'view' },
                    { label: '可评论', value: 'comment' },
                    { label: '可编辑', value: 'edit' },
                  ]}
                />
              </Form.Item>
              <Form.Item name="reason" label="原因">
                <Input.TextArea rows={3} />
              </Form.Item>
              <Button type="primary" loading={permissionRequestMutation.isPending} onClick={() => permissionRequestForm.submit()}>
                提交申请
              </Button>
            </Form>
          </div>
        ) : (
          <Empty description="暂无文档">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate('markdown')}>
              新建文档
            </Button>
          </Empty>
        )}
      </main>

      <Modal
        title={createDocType === 'space' ? '新建知识库' : createDocType === 'folder' ? '新建文件夹' : '新建文档'}
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
          {createDocType === 'space' ? (
            <>
              <Form.Item name="knowledgeBase" label="知识库入口" valuePropName="checked" initialValue>
                <Switch />
              </Form.Item>
              <Form.Item name="description" label="描述">
                <Input.TextArea rows={3} />
              </Form.Item>
              <Form.Item name="coverUrl" label="封面 URL">
                <Input />
              </Form.Item>
              <Form.Item name="defaultPermissionLevel" label="默认权限" initialValue="view">
                <Select
                  options={[
                    { label: '可查看', value: 'view' },
                    { label: '可评论', value: 'comment' },
                    { label: '可编辑', value: 'edit' },
                  ]}
                />
              </Form.Item>
            </>
          ) : null}
        </Form>
      </Modal>

      <Modal
        title="从模板创建"
        open={templateOpen}
        onCancel={() => setTemplateOpen(false)}
        onOk={() => templateForm.submit()}
        confirmLoading={templateMutation.isPending}
      >
        <Form form={templateForm} layout="vertical" onFinish={(values) => templateMutation.mutate(values)}>
          <Form.Item name="templateId" label="模板" rules={[{ required: true, message: '请选择模板' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={(templatesQuery.data ?? []).map((template) => ({
                label: `${templateCategoryText(template.category)} / ${template.title}`,
                value: template.id,
              }))}
            />
          </Form.Item>
          <Form.Item name="title" label="标题">
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
          {templateForm.getFieldValue('templateId') ? (
            <TemplatePreview template={templatesQuery.data?.find((template) => template.id === templateForm.getFieldValue('templateId')) ?? null} />
          ) : null}
        </Form>
      </Modal>

      <Modal
        title="保存命名版本"
        open={namedVersionOpen}
        onCancel={() => setNamedVersionOpen(false)}
        onOk={() => namedVersionForm.submit()}
        confirmLoading={namedVersionMutation.isPending}
      >
        <Form form={namedVersionForm} layout="vertical" onFinish={(values) => namedVersionMutation.mutate(values)}>
          <Form.Item name="versionName" label="版本名称" rules={[{ required: true, message: '请输入版本名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="summary" label="摘要">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="导入 Markdown"
        open={importMarkdownOpen}
        onCancel={() => setImportMarkdownOpen(false)}
        onOk={() => importMarkdownForm.submit()}
        confirmLoading={importMarkdownMutation.isPending}
        width={720}
      >
        <Form form={importMarkdownForm} layout="vertical" onFinish={(values) => importMarkdownMutation.mutate(values)}>
          <Form.Item name="title" label="标题">
            <Input />
          </Form.Item>
          <Form.Item name="content" label="Markdown 内容" rules={[{ required: true, message: '请输入 Markdown 内容' }]}>
            <Input.TextArea rows={14} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="从选区创建事项"
        open={selectionIssueOpen}
        onCancel={() => setSelectionIssueOpen(false)}
        onOk={() => selectionIssueForm.submit()}
        confirmLoading={selectionIssueMutation.isPending}
      >
        <Form form={selectionIssueForm} layout="vertical" onFinish={(values) => selectionIssueMutation.mutate(values)}>
          <Form.Item name="projectId" label="项目" rules={[{ required: true, message: '请选择项目' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={(projectsQuery.data ?? []).map((project) => ({
                value: project.id,
                label: `${project.name} (${project.projectKey})`,
              }))}
            />
          </Form.Item>
          <Form.Item name="issueType" label="类型" initialValue="task">
            <Select
              options={[
                { value: 'requirement', label: '需求' },
                { value: 'task', label: '任务' },
                { value: 'bug', label: 'Bug' },
              ]}
            />
          </Form.Item>
          <Form.Item name="title" label="标题">
            <Input />
          </Form.Item>
          <Form.Item name="priority" label="优先级" initialValue="medium">
            <Select
              options={[
                { value: 'low', label: '低' },
                { value: 'medium', label: '中' },
                { value: 'high', label: '高' },
                { value: 'urgent', label: '紧急' },
              ]}
            />
          </Form.Item>
          <Form.Item name="assigneeId" label="负责人">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              options={(membersQuery.data ?? []).map((member) => ({
                value: member.id,
                label: `${member.displayName} @${member.username}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="dueAt" label="截止时间">
            <Input type="date" />
          </Form.Item>
          <Form.Item name="description" label="补充描述">
            <Input.TextArea rows={3} />
          </Form.Item>
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

      <ResourcePermissionsModal
        open={resourcePermissionOpen}
        resourceType="document"
        resourceId={activeDocId ?? undefined}
        resourceName={activeDocument?.title}
        onClose={() => setResourcePermissionOpen(false)}
      />

      <Modal
        title="分享与权限"
        open={permissionOpen}
        onCancel={() => setPermissionOpen(false)}
        footer={null}
      >
        <Space orientation="vertical" size={16} className="doc-share-modal">
          <section>
            <Typography.Title level={5}>授权主体</Typography.Title>
            <Form form={permissionForm} layout="vertical" onFinish={(values) => permissionMutation.mutate(values)}>
              <Form.Item name="subjectType" label="主体类型" initialValue="user" rules={[{ required: true }]}>
                <Select
                  options={[
                    { label: '成员', value: 'user' },
                    { label: '用户组', value: 'user_group' },
                  ]}
                  onChange={() => permissionForm.setFieldValue('subjectId', undefined)}
                />
              </Form.Item>
              <Form.Item shouldUpdate noStyle>
                {({ getFieldValue }) => {
                  const subjectType = getFieldValue('subjectType') as PermissionForm['subjectType']
                  return (
                    <Form.Item name="subjectId" label={subjectType === 'user_group' ? '用户组' : '成员'} rules={[{ required: true, message: '请选择授权主体' }]}>
                      <Select
                        showSearch
                        optionFilterProp="label"
                        loading={subjectType === 'user_group' ? userGroupsQuery.isLoading : membersQuery.isLoading}
                        options={
                          subjectType === 'user_group'
                            ? (userGroupsQuery.data ?? []).map((group) => ({
                                label: `${group.name} (${group.code})`,
                                value: group.id,
                              }))
                            : (membersQuery.data ?? []).map((member) => ({ label: member.displayName, value: member.id }))
                        }
                      />
                    </Form.Item>
                  )
                }}
              </Form.Item>
              <Form.Item name="permissionLevel" label="权限" initialValue="view" rules={[{ required: true }]}>
                <Select
                  options={[
                    { label: '可查看', value: 'view' },
                    { label: '可评论', value: 'comment' },
                    { label: '可编辑', value: 'edit' },
                    { label: '可管理', value: 'manage' },
                    { label: '所有者', value: 'owner' },
                  ]}
                />
              </Form.Item>
              <Button type="primary" loading={permissionMutation.isPending} onClick={() => permissionForm.submit()}>
                邀请
              </Button>
            </Form>
          </section>

          <section>
            <Typography.Title level={5}>组织内链接</Typography.Title>
            <Form form={shareLinkForm} layout="vertical" onFinish={(values) => shareLinkMutation.mutate(values)}>
              <Form.Item name="enabled" label="启用链接" valuePropName="checked" initialValue>
                <Switch />
              </Form.Item>
              <Form.Item name="permissionLevel" label="链接权限" initialValue="view" rules={[{ required: true }]}>
                <Select
                  options={[
                    { label: '可查看', value: 'view' },
                    { label: '可评论', value: 'comment' },
                    { label: '可编辑', value: 'edit' },
                  ]}
                />
              </Form.Item>
              <Form.Item name="expiresAt" label="过期时间">
                <Input placeholder="2026-12-31T23:59:59Z" />
              </Form.Item>
              <Space wrap>
                <Button icon={<ShareAltOutlined />} type="primary" loading={shareLinkMutation.isPending} onClick={() => shareLinkForm.submit()}>
                  保存链接
                </Button>
                <Button icon={<LinkOutlined />} disabled={!activeShareLink} onClick={() => copyShareLink(activeShareLink)}>
                  复制链接
                </Button>
                {activeShareLink ? (
                  <Button
                    loading={shareLinkToggleMutation.isPending}
                    onClick={() => shareLinkToggleMutation.mutate(!activeShareLink.enabled)}
                  >
                    {activeShareLink.enabled ? '停用' : '启用'}
                  </Button>
                ) : null}
              </Space>
            </Form>
          </section>

          {docQuery.data?.document.docType === 'space' ? (
            <section>
              <Typography.Title level={5}>知识库设置</Typography.Title>
              <Form form={knowledgeBaseForm} layout="vertical" onFinish={(values) => knowledgeBaseMutation.mutate(values)}>
                <Form.Item name="knowledgeBase" label="知识库入口" valuePropName="checked" initialValue>
                  <Switch />
                </Form.Item>
                <Form.Item name="description" label="描述">
                  <Input.TextArea rows={3} />
                </Form.Item>
                <Form.Item name="coverUrl" label="封面 URL">
                  <Input />
                </Form.Item>
                <Form.Item name="defaultPermissionLevel" label="默认权限" initialValue="view">
                  <Select
                    options={[
                      { label: '可查看', value: 'view' },
                      { label: '可评论', value: 'comment' },
                      { label: '可编辑', value: 'edit' },
                    ]}
                  />
                </Form.Item>
                <Button loading={knowledgeBaseMutation.isPending} onClick={() => knowledgeBaseForm.submit()}>
                  保存知识库设置
                </Button>
              </Form>
            </section>
          ) : null}
        </Space>
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
                { label: '消息', value: 'message' },
                { label: '审批', value: 'approval' },
                { label: '文档', value: 'document' },
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
  canComment,
  canEdit,
  canManage,
  conflictVisible,
  acceptanceReport,
  versions,
  blockDrafts,
  saving,
  savingBlocks,
  restoringVersionNo,
  commentDraft,
  commentBlockId,
  commentAnchor,
  commentReplyDrafts,
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
  onOpenNamedVersion,
  onOpenImportMarkdown,
  onOpenSelectionIssue,
  knowledgeMetadataForm,
  knowledgeMaintainerOptions,
  onSaveKnowledgeMetadata,
  savingKnowledgeMetadata,
  onRestore,
  onDiff,
  onCommentDraftChange,
  onCommentBlockChange,
  onCommentAnchorChange,
  onComment,
  commenting,
  onReplyDraftChange,
  onReplyComment,
  replyingCommentId,
  onResolveComment,
  resolvingCommentId,
  onReopenComment,
  reopeningCommentId,
  onActivateComment,
}: {
  detail: DocumentDetail
  titleDraft: string
  contentDraft: string
  canComment: boolean
  canEdit: boolean
  canManage: boolean
  conflictVisible: boolean
  acceptanceReport?: DocumentAcceptanceReport
  versions: DocumentVersion[]
  blockDrafts: BlockDraftState[]
  saving: boolean
  savingBlocks: boolean
  restoringVersionNo?: number
  commentDraft: string
  commentBlockId?: string
  commentAnchor?: DocEditorSelectionAnchor
  commentReplyDrafts: Record<string, string>
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
  onOpenNamedVersion: () => void
  onOpenImportMarkdown: () => void
  onOpenSelectionIssue: () => void
  knowledgeMetadataForm: FormInstance<KnowledgeMetadataForm>
  knowledgeMaintainerOptions: Array<{ label: string; value: string }>
  onSaveKnowledgeMetadata: (values: KnowledgeMetadataForm) => void
  savingKnowledgeMetadata: boolean
  onRestore: (versionNo: number) => void
  onDiff: (versionNo: number) => void
  onCommentDraftChange: (value: string) => void
  onCommentBlockChange: (value?: string) => void
  onCommentAnchorChange: (anchor?: DocEditorSelectionAnchor) => void
  onComment: () => void
  commenting: boolean
  onReplyDraftChange: (commentId: string, value: string) => void
  onReplyComment: (commentId: string) => void
  replyingCommentId?: string
  onResolveComment: (commentId: string) => void
  resolvingCommentId?: string
  onReopenComment: (commentId: string) => void
  reopeningCommentId?: string
  onActivateComment: (commentId: string) => void
}) {
  const currentUser = useAuthStore((state) => state.currentUser)
  const [commentFilter, setCommentFilter] = useState<CommentFilter>('open')
  const handleRemoteSnapshot = useCallback((snapshot: { title: string; content: string }) => {
    onTitleChange(snapshot.title)
    onContentChange(snapshot.content)
  }, [onContentChange, onTitleChange])
  const collaboration = useDocumentCollaboration({
    documentId: detail.document.id,
    title: titleDraft,
    content: contentDraft,
    versionNo: detail.document.currentVersionNo,
    canEdit,
    enabled: detail.document.docType === 'markdown',
    onRemoteSnapshot: handleRemoteSnapshot,
  })
  const checkpointMode = detail.document.docType === 'markdown'
  const checkpointReady = ['joined', 'synced'].includes(collaboration.status)
  const checkpointWaiting = checkpointMode && !checkpointReady
  const saveActionLabel = checkpointMode ? '生成版本' : '保存'
  const saveActionHint = checkpointWaiting ? '等待自动保存完成后生成版本' : saveActionLabel
  const commentAnchors = useMemo(
    () =>
      detail.comments
        .filter((comment) => comment.anchorType === 'selection')
        .map((comment) => ({
          id: comment.id,
          anchorStart: comment.anchorStart,
          anchorEnd: comment.anchorEnd,
          anchorText: comment.anchorText,
          resolved: comment.resolved,
        })),
    [detail.comments],
  )
  const focusedCommentIds = useMemo(
    () => new Set(detail.comments.filter((comment) => commentAnchor && commentMatchesAnchor(comment, commentAnchor)).map((comment) => comment.id)),
    [commentAnchor, detail.comments],
  )
  const commentFilterOptions = useMemo(
    () => [
      { value: 'open', label: `未解决 ${detail.comments.filter((comment) => !comment.resolved).length}` },
      { value: 'mentions', label: `提及我 ${detail.comments.filter((comment) => commentMentionsUser(comment, currentUser?.username)).length}` },
      { value: 'current', label: `当前选区 ${focusedCommentIds.size}` },
      { value: 'all', label: `全部 ${detail.comments.length}` },
    ],
    [currentUser?.username, detail.comments, focusedCommentIds],
  )
  const filteredComments = useMemo(
    () =>
      detail.comments.filter((comment) => {
        if (commentFilter === 'open') {
          return !comment.resolved
        }
        if (commentFilter === 'mentions') {
          return commentMentionsUser(comment, currentUser?.username)
        }
        if (commentFilter === 'current') {
          return focusedCommentIds.has(comment.id)
        }
        return true
      }),
    [commentFilter, currentUser?.username, detail.comments, focusedCommentIds],
  )
  const orderedComments = useMemo(
    () =>
      [...filteredComments].sort((left, right) => {
        const focusDelta = Number(focusedCommentIds.has(right.id)) - Number(focusedCommentIds.has(left.id))
        if (focusDelta !== 0) {
          return focusDelta
        }
        return new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime()
      }),
    [filteredComments, focusedCommentIds],
  )

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
      <DocEditor
        documentId={detail.document.id}
        title={titleDraft}
        content={contentDraft}
        versionNo={detail.document.currentVersionNo}
        permissionLevel={detail.document.permissionLevel}
        updatedAt={detail.document.updatedAt}
        canEdit={canEdit}
        canManage={canManage}
        dirty={titleDraft !== detail.document.title || contentDraft !== detail.content}
        saving={saving}
        conflictVisible={conflictVisible}
        saveActionLabel={saveActionLabel}
        saveActionHint={saveActionHint}
        saveActionDisabled={checkpointWaiting}
        collaboration={{
          status: collaboration.status,
          onlineUsers: collaboration.onlineUsers,
          remoteCursors: collaboration.remoteCursors,
          lastSavedAt: collaboration.lastSavedAt,
          error: collaboration.error,
        }}
        commentAnchors={commentAnchors}
        activeCommentId={activeCommentId}
        onTitleChange={onTitleChange}
        onContentChange={onContentChange}
        onSelectionChange={collaboration.sendAwareness}
        onCommentAnchorChange={onCommentAnchorChange}
        onSave={onSave}
        onRefresh={onRefresh}
        onOpenPermission={onOpenPermission}
        onOpenRelation={onOpenRelation}
      />

      {!canEdit ? (
        <Alert
          className="doc-readonly-alert"
          showIcon
          type="info"
          message={canComment ? '当前为评论模式' : '当前为只读模式'}
          description={canComment ? '你可以发表评论和回复，正文需要编辑权限。' : '你可以阅读文档、打开对象卡片或通过分享入口申请更高权限。'}
          action={<Button onClick={onOpenPermission}>分享与权限</Button>}
        />
      ) : null}

      <div className="doc-mobile-action-bar">
        <Button onClick={() => document.querySelector<HTMLElement>('.docs-sidebar')?.scrollIntoView({ block: 'start' })}>目录</Button>
        <Button onClick={() => document.getElementById('doc-comments-panel')?.scrollIntoView({ block: 'start' })}>评论</Button>
        <Button onClick={onOpenPermission}>分享</Button>
      </div>

      <section className="doc-meta-grid">
        <DocumentAcceptancePanel report={acceptanceReport} />

        {detail.knowledgeContext ? (
          <div className="doc-panel doc-knowledge-context">
            <Typography.Title level={5}>知识库位置</Typography.Title>
            <Space orientation="vertical" size={8} className="doc-panel-list">
              <Space wrap>
                <Tag color="blue">{detail.knowledgeContext.spaceName}</Tag>
                <Tag>{detail.knowledgeContext.spaceCode}</Tag>
              </Space>
              <Typography.Text type="secondary">{detail.knowledgeContext.pathText}</Typography.Text>
              <Button size="small" href={detail.knowledgeContext.webPath}>
                进入知识库视图
              </Button>
            </Space>
          </div>
        ) : null}

        <details className="doc-panel doc-legacy-block-panel">
          <summary>兼容结构化块</summary>
          <Space className="doc-panel-title">
            <Typography.Title level={5}>兼容结构化块</Typography.Title>
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
        </details>

        <div className="doc-panel">
          <Space className="doc-panel-title">
            <Typography.Title level={5}>知识元数据</Typography.Title>
            <Button size="small" disabled={!canEdit} loading={savingKnowledgeMetadata} onClick={() => knowledgeMetadataForm.submit()}>
              保存
            </Button>
          </Space>
          <Space orientation="vertical" size={8} className="doc-panel-list">
            <Space wrap>
              <Tag color={knowledgeStatusColor(detail.document.knowledgeStatus)}>
                {knowledgeStatusText(detail.document.knowledgeStatus)}
              </Tag>
              {detail.document.category ? <Tag>{templateCategoryText(detail.document.category)}</Tag> : null}
              {detail.document.verifiedAt ? <Tag color="green">已认证 {new Date(detail.document.verifiedAt).toLocaleDateString()}</Tag> : null}
              {detail.document.reviewDueAt ? <Tag color={isReviewDueSoon(detail.document.reviewDueAt) ? 'orange' : 'blue'}>复核 {detail.document.reviewDueAt}</Tag> : null}
            </Space>
            <Typography.Text type="secondary">
              维护人 {detail.document.maintainerName ?? '未指定'}
            </Typography.Text>
            <Space wrap>
              {(detail.document.tags ?? []).map((tag) => <Tag key={tag}>{tag}</Tag>)}
              {(detail.document.tags ?? []).length === 0 ? <Typography.Text type="secondary">暂无标签</Typography.Text> : null}
            </Space>
            {canEdit ? (
              <Form form={knowledgeMetadataForm} layout="vertical" onFinish={onSaveKnowledgeMetadata}>
                <Form.Item name="maintainerId" label="维护人">
                  <Select allowClear showSearch optionFilterProp="label" options={knowledgeMaintainerOptions} />
                </Form.Item>
                <Form.Item name="tags" label="标签">
                  <Select mode="tags" tokenSeparators={[',', '，']} placeholder="输入标签后回车" />
                </Form.Item>
                <Form.Item name="category" label="类别">
                  <Select
                    allowClear
                    options={[
                      { label: '知识条目', value: 'knowledge' },
                      { label: 'FAQ', value: 'faq' },
                      { label: 'SOP', value: 'sop' },
                      { label: '故障复盘', value: 'incident' },
                      { label: '项目复盘', value: 'project_review' },
                    ]}
                  />
                </Form.Item>
                <Form.Item name="knowledgeStatus" label="状态">
                  <Select
                    options={[
                      { label: '草稿', value: 'draft' },
                      { label: '已认证', value: 'verified' },
                      { label: '待复核', value: 'needs_review' },
                      { label: '已过期', value: 'outdated' },
                    ]}
                  />
                </Form.Item>
                <Form.Item name="reviewDueAt" label="复核日期">
                  <Input placeholder="2026-12-31" />
                </Form.Item>
              </Form>
            ) : null}
          </Space>
        </div>

        <div className="doc-panel">
          <Space className="doc-panel-title">
            <Typography.Title level={5}>版本</Typography.Title>
            <Space size={4}>
              <Button size="small" disabled={!canEdit} onClick={onOpenNamedVersion}>
                命名
              </Button>
              <Button size="small" disabled={!canEdit} onClick={onOpenImportMarkdown}>
                导入
              </Button>
            </Space>
          </Space>
          <Space orientation="vertical" size={8} className="doc-panel-list">
            {versions.map((version) => (
              <div className="doc-version-item" key={version.versionNo}>
                <span>
                  <strong>v{version.versionNo}{version.versionName ? ` · ${version.versionName}` : ''}</strong>
                  <small>
                    {versionTypeText(version.versionType)}
                    {version.sourceVersionNo ? ` · 来自 v${version.sourceVersionNo}` : ''}
                    {' · '}
                    {version.createdByName} · {new Date(version.createdAt).toLocaleString()}
                  </small>
                  {version.summary ? <small>{version.summary}</small> : null}
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

        {detail.document.docType === 'space' ? (
          <div className="doc-panel">
            <Typography.Title level={5}>知识库</Typography.Title>
            <Space orientation="vertical" size={8} className="doc-panel-list">
              <Space wrap>
                <Tag color={detail.document.knowledgeBase ? 'green' : 'default'}>
                  {detail.document.knowledgeBase ? '知识库入口' : '普通空间'}
                </Tag>
                <Tag>默认 {permissionText(detail.document.defaultPermissionLevel)}</Tag>
              </Space>
              {detail.document.description ? <Typography.Paragraph>{detail.document.description}</Typography.Paragraph> : null}
              {detail.document.coverUrl ? <Typography.Text type="secondary">{detail.document.coverUrl}</Typography.Text> : null}
            </Space>
          </div>
        ) : null}

        <div className="doc-panel">
          <Typography.Title level={5}>权限</Typography.Title>
          <Space orientation="vertical" size={8} className="doc-panel-list">
            {detail.permissions.map((permission) => (
              <div className="doc-permission-row" key={permission.id}>
                <span>
                  <strong>{permission.subjectName ?? permission.displayName}</strong>
                  <small>
                    {permission.subjectType === 'user_group' ? '用户组 · ' : ''}
                    {permissionText(permission.permissionLevel)}
                    {permission.sourceType === 'inherited' && permission.sourceTitle ? ` · 继承自 ${permission.sourceTitle}` : ''}
                  </small>
                </span>
                <Tag color={permission.sourceType === 'inherited' ? 'blue' : 'default'}>
                  {permission.sourceType === 'inherited' ? '继承' : '直授'}
                </Tag>
              </div>
            ))}
            {detail.shareLinks.length > 0 ? (
              detail.shareLinks.map((shareLink) => (
                <div className="doc-share-row" key={shareLink.id}>
                  <Tag icon={<LinkOutlined />} color={shareLink.enabled ? 'green' : 'default'}>
                    {shareLink.enabled ? '组织内链接已启用' : '组织内链接已停用'}
                  </Tag>
                  {shareLink.knowledgeBaseName ? <Tag>知识库 {shareLink.knowledgeBaseName}</Tag> : null}
                  <span>{permissionText(shareLink.permissionLevel)}</span>
                  {shareLink.expiresAt ? <small>{new Date(shareLink.expiresAt).toLocaleString()} 过期</small> : null}
                </div>
              ))
            ) : canManage ? (
              <Typography.Text type="secondary">尚未创建组织内分享链接</Typography.Text>
            ) : null}
          </Space>
        </div>

        <div className="doc-panel" id="doc-comments-panel">
          <Space className="doc-panel-title" wrap>
            <Typography.Title level={5}>
              <CommentOutlined /> 评论
            </Typography.Title>
            <Select
              className="doc-comment-filter"
              value={commentFilter}
              onChange={(value) => setCommentFilter(value as CommentFilter)}
              options={commentFilterOptions}
            />
          </Space>
          <Space orientation="vertical" size={8} className="doc-panel-list">
            {orderedComments.map((comment) => (
              <div className={`doc-comment-item${comment.id === activeCommentId ? ' active' : ''}${comment.resolved ? ' resolved' : ''}`} key={comment.id} id={`doc-comment-${comment.id}`}>
                <Space wrap>
                  <strong>{comment.authorName}</strong>
                  {focusedCommentIds.has(comment.id) ? <Tag color="blue">当前位置</Tag> : null}
                  <Tag>{commentAnchorLabel(detail.blocks, comment)}</Tag>
                  {comment.resolved ? <Tag color="green">已解决</Tag> : <Tag color="orange">未解决</Tag>}
                </Space>
                {comment.anchorText ? <Typography.Text className="doc-comment-anchor-text" type="secondary">"{comment.anchorText}"</Typography.Text> : null}
                <span>{comment.content}</span>
                {comment.replies.length > 0 ? (
                  <div className="doc-comment-replies">
                    {comment.replies.map((reply) => (
                      <div className="doc-comment-reply" key={reply.id}>
                        <strong>{reply.authorName}</strong>
                        <span>{reply.content}</span>
                      </div>
                    ))}
                  </div>
                ) : null}
                <Space wrap>
                  {comment.anchorType === 'selection' || comment.blockId ? (
                    <Button
                      size="small"
                      href={comment.blockId ? `#doc-block-${comment.blockId}` : undefined}
                      onClick={() => onActivateComment(comment.id)}
                    >
                      定位
                    </Button>
                  ) : null}
                  {!comment.resolved ? (
                    <>
                      <Button
                        size="small"
                        icon={<CheckCircleOutlined />}
                        loading={resolvingCommentId === comment.id}
                        disabled={!canComment}
                        onClick={() => onResolveComment(comment.id)}
                      >
                        解决
                      </Button>
                    </>
                  ) : (
                    <Button
                      size="small"
                      icon={<RollbackOutlined />}
                      loading={reopeningCommentId === comment.id}
                      disabled={!canComment}
                      onClick={() => onReopenComment(comment.id)}
                    >
                      重新打开
                    </Button>
                  )}
                </Space>
                {!comment.resolved ? (
                  <Space.Compact className="doc-comment-reply-composer">
                    <Input
                      disabled={!canComment}
                      value={commentReplyDrafts[comment.id] ?? ''}
                      placeholder="回复线程"
                      onChange={(event) => onReplyDraftChange(comment.id, event.target.value)}
                    />
                    <Button
                      disabled={!canComment || !(commentReplyDrafts[comment.id] ?? '').trim()}
                      loading={replyingCommentId === comment.id}
                      onClick={() => onReplyComment(comment.id)}
                    >
                      回复
                    </Button>
                  </Space.Compact>
                ) : (
                  <Typography.Text type="secondary">
                    {comment.resolvedByName ? `${comment.resolvedByName} 解决` : '已解决'}
                  </Typography.Text>
                )}
              </div>
            ))}
            {detail.comments.length === 0 ? <Typography.Text type="secondary">暂无评论</Typography.Text> : null}
            {detail.comments.length > 0 && orderedComments.length === 0 ? <Typography.Text type="secondary">当前筛选没有评论</Typography.Text> : null}
            {commentAnchor && !commentBlockId ? (
              <div className="doc-comment-anchor-preview">
                <Tag color="blue">选区</Tag>
                <Typography.Text type="secondary">{commentAnchor.anchorText}</Typography.Text>
                <Button size="small" disabled={!canEdit} onClick={onOpenSelectionIssue}>
                  转事项
                </Button>
              </div>
            ) : null}
            <Select
              allowClear
              disabled={!canComment}
              placeholder="全文评论；选择块可改为块评论"
              value={commentBlockId}
              onChange={onCommentBlockChange}
              options={detail.blocks.map((block, index) => ({
                value: block.id,
                label: `${index + 1}. ${blockTypeText(block.blockType)} ${blockContentLabel(block)}`,
              }))}
            />
            <Input.TextArea
              className="doc-comment-composer"
              rows={3}
              disabled={!canComment}
              value={commentDraft}
              placeholder="评论支持 @username 提醒成员"
              onChange={(event) => onCommentDraftChange(event.target.value)}
            />
            <Button disabled={!canComment || !commentDraft.trim()} loading={commenting} onClick={onComment}>
              发送评论
            </Button>
          </Space>
        </div>
      </section>
    </div>
  )
}

function DocumentAcceptancePanel({ report }: { report?: DocumentAcceptanceReport }) {
  if (!report) {
    return null
  }
  const readyGates = report.gates.filter((gate) => gate.status !== 'blocked').length
  return (
    <div className="doc-panel doc-acceptance-panel">
      <Space className="doc-panel-title">
        <Typography.Title level={5}>
          <CheckCircleOutlined /> v1 验收
        </Typography.Title>
        <Tag color={report.frozen ? 'green' : 'blue'}>{report.frozen ? '已冻结' : report.status}</Tag>
      </Space>
      <Space wrap size={6}>
        <Tag>场景 {report.scenarios.length}</Tag>
        <Tag>验收门 {readyGates}/{report.gates.length}</Tag>
        <Tag color={report.openP0 === 0 ? 'green' : 'red'}>P0 {report.openP0}</Tag>
        <Tag color={report.openP1 === 0 ? 'green' : 'orange'}>P1 {report.openP1}</Tag>
      </Space>
      <Typography.Paragraph className="doc-acceptance-criteria" type="secondary">
        {report.frozenCriteria}
      </Typography.Paragraph>
      <Space orientation="vertical" size={6} className="doc-panel-list">
        {report.gates.map((gate) => (
          <div className="doc-acceptance-row" key={gate.key}>
            <span>
              <strong>{gate.label}</strong>
              <small>{gate.evidence}</small>
            </span>
            <Tag color={acceptanceStatusColor(gate.status)}>{acceptanceStatusText(gate.status)}</Tag>
          </div>
        ))}
      </Space>
      <details className="doc-acceptance-scenarios">
        <summary>10 个真实场景</summary>
        <Space orientation="vertical" size={6} className="doc-panel-list">
          {report.scenarios.map((scenario) => (
            <div className="doc-acceptance-row" key={scenario.key}>
              <span>
                <strong>{scenario.title}</strong>
                <small>{scenario.workflow}</small>
                <small>{scenario.evidence}</small>
              </span>
              <Tag color={acceptanceStatusColor(scenario.status)}>{acceptanceStatusText(scenario.status)}</Tag>
            </div>
          ))}
        </Space>
      </details>
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
      <Space wrap size={4}>
        <Tag color="blue">{selectedView ? selectedView.name : '默认视图'}</Tag>
        <Tag>{summary.accessState === 'available' ? '权限可见' : '权限受限'}</Tag>
        {selectedView?.filters.length ? <Tag>筛选 {selectedView.filters.length}</Tag> : null}
        {selectedView?.sorts.length ? <Tag>排序 {selectedView.sorts.length}</Tag> : null}
      </Space>
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

function TemplatePreview({ template }: { template: DocumentTemplate | null }) {
  if (!template) {
    return null
  }
  const previewLines = template.content.split('\n').filter((line) => line.trim()).slice(0, 5)
  return (
    <div className="doc-template-preview">
      <Space wrap>
        <Tag>{templateCategoryText(template.category)}</Tag>
        {template.builtIn ? <Tag color="blue">内置</Tag> : null}
      </Space>
      {template.description ? <Typography.Paragraph type="secondary">{template.description}</Typography.Paragraph> : null}
      <div className="doc-template-preview-content">
        {previewLines.map((line, index) => (
          <Typography.Text key={`${template.id}-${index}`}>{line}</Typography.Text>
        ))}
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

function commentMatchesAnchor(comment: DocumentComment, anchor: DocEditorSelectionAnchor) {
  if (comment.anchorType !== 'selection' || comment.anchorStart == null || comment.anchorEnd == null) {
    return false
  }
  return comment.anchorStart <= anchor.anchorEnd && comment.anchorEnd >= anchor.anchorStart
}

function commentMentionsUser(comment: DocumentComment, username?: string) {
  if (!username) {
    return false
  }
  const mention = `@${username}`
  return comment.content.includes(mention) || comment.replies.some((reply) => reply.content.includes(mention))
}

function commentAnchorLabel(blocks: DocumentDetail['blocks'], comment: DocumentComment) {
  if (comment.anchorType === 'selection') {
    return '选区'
  }
  if (comment.blockId) {
    return blockLabel(blocks, comment.blockId)
  }
  return '全文'
}

function hasPermission(current: string | undefined, required: 'view' | 'comment' | 'edit' | 'manage') {
  const rank = { view: 1, comment: 2, edit: 3, manage: 4, owner: 5 }
  return current ? rank[current as keyof typeof rank] >= rank[required] : false
}

function permissionText(permission: string) {
  return { view: '可查看', comment: '可评论', edit: '可编辑', manage: '可管理', owner: '所有者' }[permission] ?? permission
}

function docTypeText(docType: string) {
  return { space: '空间', folder: '文件夹', markdown: '文档' }[docType] ?? docType
}

function versionTypeText(versionType: string) {
  return {
    auto_snapshot: '自动快照',
    manual_checkpoint: '手动检查点',
    named: '命名版本',
    restore: '恢复版本',
    import: '导入版本',
  }[versionType] ?? versionType
}

function acceptanceStatusText(status: string) {
  return {
    ready: '可试运行',
    'trial-ready': '待真人试运行',
    frozen: '已冻结',
    blocked: '阻塞',
  }[status] ?? status
}

function acceptanceStatusColor(status: string) {
  return {
    ready: 'green',
    'trial-ready': 'blue',
    frozen: 'purple',
    blocked: 'red',
  }[status] ?? 'default'
}

function templateCategoryText(category: string) {
  return {
    meeting: '会议纪要',
    requirement: '需求文档',
    prd: '需求文档',
    project: '项目计划',
    review: '复盘',
    knowledge: '知识条目',
    faq: 'FAQ',
    sop: 'SOP',
    incident: '故障复盘',
    project_review: '项目复盘',
  }[category] ?? category
}

function knowledgeStatusText(status: string) {
  return {
    draft: '草稿',
    verified: '已认证',
    needs_review: '待复核',
    outdated: '已过期',
    archived: '已归档',
  }[status] ?? status
}

function knowledgeStatusColor(status: string) {
  return {
    draft: 'default',
    verified: 'green',
    needs_review: 'orange',
    outdated: 'red',
    archived: 'default',
  }[status] ?? 'default'
}

function isReviewDueSoon(value: string) {
  const due = new Date(`${value}T00:00:00`).getTime()
  const sevenDays = 7 * 24 * 60 * 60 * 1000
  return Number.isFinite(due) && due - Date.now() <= sevenDays
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
        {node.document.knowledgeBase ? <Tag color="green">知识库</Tag> : node.document.docType !== 'markdown' ? <Tag>{docTypeText(node.document.docType)}</Tag> : null}
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
