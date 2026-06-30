import {
  ArrowDownOutlined,
  ArrowLeftOutlined,
  ArrowUpOutlined,
  BookOutlined,
  BellOutlined,
  DownloadOutlined,
  FileTextOutlined,
  FolderOpenOutlined,
  FolderOutlined,
  HomeOutlined,
  PlusOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  StarFilled,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App as AntdApp, Button, Empty, Form, Input, Modal, Select, Space, Tag, Tooltip, Tree, Typography } from 'antd'
import type { DataNode } from 'antd/es/tree'
import type { FormInstance } from 'antd/es/form'
import type { Key, ReactNode } from 'react'
import { useMemo, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'

import {
  createDocument,
  createDocumentFromTemplate,
  getDocumentCollaborationHealth,
  listDocumentTemplates,
  listDocumentTree,
  moveDocument,
  type DocumentSummary,
  type DocumentTemplate,
  type DocumentTreeNode,
} from '../../docs/api/docsApi'
import { searchAll, type SearchResult } from '../../search/api/searchApi'
import { ResourcePermissionsModal } from '../../permissions/components/ResourcePermissionsModal'
import { requestResourcePermission, type ManagedResourceType } from '../../permissions/api/resourcePermissionsApi'
import { listFavoriteObjects } from '../../platform/api/platformObjectsApi'
import { ApiRequestError } from '../../../shared/api/httpClient'
import {
  bulkGovernKnowledgeBase,
  exportKnowledgeBaseGovernance,
  getKnowledgeBase,
  getKnowledgeBaseDiscovery,
  getKnowledgeBaseGovernance,
  listKnowledgeBases,
  subscribeKnowledgeTarget,
  unsubscribeKnowledgeTarget,
  updateKnowledgeBase,
  type KnowledgeBaseBulkGovernanceRequest,
  type KnowledgeBaseGovernanceDashboard,
  type KnowledgeBaseGovernanceRisk,
  type KnowledgeBaseSpaceSummary,
} from '../api/knowledgeBasesApi'

type CreateNodeForm = {
  title: string
  docType: 'markdown' | 'folder'
  parentId?: string
}

type TemplateCreateForm = {
  templateId: string
  title?: string
  parentId?: string
}

type MoveNodeForm = {
  parentId?: string
}

type GovernanceBulkForm = {
  documentIds: string[]
  maintainerId?: string
  tags?: string[]
  replaceTags?: boolean
  reviewDueAt?: string
}

type PermissionTarget = {
  resourceType: ManagedResourceType
  resourceId: string
  resourceName: string
}

const ROOT_PARENT_VALUE = '__kb_root__'

export function KnowledgeBaseDetailPage() {
  const { spaceId = '' } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { message } = AntdApp.useApp()
  const [directorySearch, setDirectorySearch] = useState('')
  const [kbSearchDraft, setKbSearchDraft] = useState('')
  const [kbSearchText, setKbSearchText] = useState('')
  const [kbSearchScope, setKbSearchScope] = useState<'knowledge_base' | 'directory'>('knowledge_base')
  const [kbSearchDocType, setKbSearchDocType] = useState<string | undefined>()
  const [kbSearchTagText, setKbSearchTagText] = useState('')
  const [kbSearchMaintainerId, setKbSearchMaintainerId] = useState<string | undefined>()
  const [kbSearchStatus, setKbSearchStatus] = useState<string | undefined>()
  const [kbSearchUpdatedFrom, setKbSearchUpdatedFrom] = useState('')
  const [kbSearchUpdatedTo, setKbSearchUpdatedTo] = useState('')
  const [selectedDocumentId, setSelectedDocumentId] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [templateOpen, setTemplateOpen] = useState(false)
  const [moveOpen, setMoveOpen] = useState(false)
  const [permissionTarget, setPermissionTarget] = useState<PermissionTarget | null>(null)
  const [expandedKeysBySpace, setExpandedKeysBySpace] = useState<Record<string, string[]>>({})
  const [createForm] = Form.useForm<CreateNodeForm>()
  const [templateForm] = Form.useForm<TemplateCreateForm>()
  const [moveForm] = Form.useForm<MoveNodeForm>()
  const [governanceForm] = Form.useForm<GovernanceBulkForm>()
  const [accessRequestForm] = Form.useForm<{ reason?: string }>()

  const spacesQuery = useQuery({ queryKey: ['knowledge-bases', false], queryFn: () => listKnowledgeBases() })
  const spaceQuery = useQuery({
    queryKey: ['knowledge-bases', spaceId],
    queryFn: () => getKnowledgeBase(spaceId),
    enabled: Boolean(spaceId),
  })
  const treeQuery = useQuery({ queryKey: ['docs', 'tree', 'kb-detail'], queryFn: () => listDocumentTree({ includeArchived: true }) })
  const templatesQuery = useQuery({
    queryKey: ['docs', 'templates', spaceId],
    queryFn: () => listDocumentTemplates({ knowledgeBaseId: spaceId }),
    enabled: Boolean(spaceId),
  })
  const favoriteObjectsQuery = useQuery({ queryKey: ['platform', 'favorites', 'kb-detail'], queryFn: () => listFavoriteObjects(50) })
  const discoveryQuery = useQuery({
    queryKey: ['knowledge-bases', spaceId, 'discovery'],
    queryFn: () => getKnowledgeBaseDiscovery(spaceId),
    enabled: Boolean(spaceId),
  })
  const governanceQuery = useQuery({
    queryKey: ['knowledge-bases', spaceId, 'governance'],
    queryFn: () => getKnowledgeBaseGovernance(spaceId),
    enabled: Boolean(spaceId),
  })
  const space = spaceQuery.data?.space ?? null
  const rootDocumentId = space?.rootDocumentId ?? null
  const homeDocumentId = space?.homeDocumentId ?? null
  const rootNode = useMemo(
    () => (rootDocumentId ? findTreeNode(treeQuery.data ?? [], rootDocumentId) : null),
    [rootDocumentId, treeQuery.data],
  )
  const flatNodes = useMemo(() => flattenTree(rootNode), [rootNode])
  const documents = useMemo(() => flatNodes.map((node) => node.document), [flatNodes])
  const queryDocumentId = useMemo(() => new URLSearchParams(location.search).get('docId'), [location.search])
  const activeDocumentId =
    selectedDocumentId && documents.some((document) => document.id === selectedDocumentId)
      ? selectedDocumentId
      : queryDocumentId && documents.some((document) => document.id === queryDocumentId)
        ? queryDocumentId
        : homeDocumentId
  const selectedDocument = documents.find((document) => document.id === activeDocumentId) ?? spaceQuery.data?.homeDocument ?? null
  const collaborationHealthQuery = useQuery({
    queryKey: ['docs', activeDocumentId, 'collaboration', 'health', 'kb-detail'],
    queryFn: () => getDocumentCollaborationHealth(activeDocumentId || ''),
    enabled: Boolean(activeDocumentId && selectedDocument?.docType === 'markdown'),
    refetchInterval: 15000,
  })
  const expandedKeys = spaceId ? expandedKeysBySpace[spaceId] ?? readExpandedKeys(spaceId) : []
  const selectedDirectoryId =
    selectedDocument?.docType === 'folder' || selectedDocument?.docType === 'space'
      ? selectedDocument.id
      : selectedDocument?.parentId ?? rootDocumentId ?? undefined
  const favoriteDocumentIds = useMemo(
    () =>
      new Set(
        (favoriteObjectsQuery.data ?? [])
          .filter((item) => item.objectType === 'document' && item.accessState === 'available')
          .map((item) => item.objectId),
      ),
    [favoriteObjectsQuery.data],
  )
  const treeData = useMemo(
    () => buildKbTreeData(rootNode ? [rootNode] : [], directorySearch, selectedDocument?.id ?? null, favoriteDocumentIds),
    [directorySearch, favoriteDocumentIds, rootNode, selectedDocument?.id],
  )
  const directoryOptions = useMemo(
    () =>
      documents
        .filter((document) => ['space', 'folder'].includes(document.docType) && !document.archived)
        .sort(documentSort)
        .map((document) => ({ label: `${docTypeText(document.docType)} / ${document.title}`, value: document.id })),
    [documents],
  )
  const movableDirectoryOptions = useMemo(
    () =>
      directoryOptions.filter((option) => {
        if (!selectedDocument || option.value === selectedDocument.id) {
          return false
        }
        return !isDescendantDocument(documents, selectedDocument.id, option.value)
      }),
    [directoryOptions, documents, selectedDocument],
  )
  const recentDocuments = useMemo(
    () =>
      documents
        .filter((document) => document.docType === 'markdown' && !document.archived)
        .sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime())
        .slice(0, 6),
    [documents],
  )
  const pinnedDocuments = useMemo(
    () =>
      documents
        .filter((document) => document.parentId === rootDocumentId && !document.archived)
        .sort(documentSort)
        .slice(0, 4),
    [documents, rootDocumentId],
  )
  const commonDirectories = useMemo(
    () =>
      documents
        .filter((document) => document.docType === 'folder' && !document.archived)
        .sort(documentSort)
        .slice(0, 6),
    [documents],
  )
  const favoriteDocuments = useMemo(
    () => documents.filter((document) => favoriteDocumentIds.has(document.id) && !document.archived).slice(0, 6),
    [documents, favoriteDocumentIds],
  )
  const activeSiblings = useMemo(() => {
    if (!selectedDocument) {
      return []
    }
    return documents
      .filter((document) => (document.parentId ?? null) === (selectedDocument.parentId ?? null) && !document.archived)
      .sort(documentSort)
  }, [documents, selectedDocument])
  const activeSiblingIndex = selectedDocument ? activeSiblings.findIndex((document) => document.id === selectedDocument.id) : -1
  const stats = useMemo(() => summarizeDocuments(documents), [documents])
  const maintainerOptions = useMemo(
    () =>
      Array.from(
        new Map(
          documents
            .filter((document) => document.maintainerId)
            .map((document) => [document.maintainerId, { value: document.maintainerId || '', label: document.maintainerName || document.maintainerId || '' }]),
        ).values(),
      ),
    [documents],
  )
  const knowledgeTags = useMemo(
    () => Array.from(new Set(documents.flatMap((document) => document.tags ?? []))).sort().map((tag) => ({ value: tag, label: tag })),
    [documents],
  )
  const governanceDocumentOptions = useMemo(
    () =>
      documents
        .filter((document) => document.id !== rootDocumentId && document.docType === 'markdown' && !document.archived)
        .sort(documentSort)
        .map((document) => ({ value: document.id, label: document.title })),
    [documents, rootDocumentId],
  )
  const parsedSearchTags = useMemo(
    () => kbSearchTagText.split(',').map((tag) => tag.trim()).filter(Boolean),
    [kbSearchTagText],
  )
  const kbSearchQuery = useQuery({
    queryKey: [
      'search',
      'knowledge-base',
      spaceId,
      kbSearchText,
      kbSearchScope,
      selectedDirectoryId,
      kbSearchDocType,
      parsedSearchTags.join(','),
      kbSearchMaintainerId,
      kbSearchStatus,
      kbSearchUpdatedFrom,
      kbSearchUpdatedTo,
    ],
    queryFn: () =>
      searchAll(kbSearchText, 30, {
        knowledgeBaseId: spaceId,
        directoryId: kbSearchScope === 'directory' ? selectedDirectoryId : undefined,
        docType: kbSearchDocType,
        tags: parsedSearchTags,
        maintainerId: kbSearchMaintainerId,
        knowledgeStatus: kbSearchStatus,
        updatedFrom: kbSearchUpdatedFrom || undefined,
        updatedTo: kbSearchUpdatedTo || undefined,
      }),
    enabled: Boolean(spaceId) && kbSearchText.trim().length >= 2,
  })

  const refresh = async (documentId = selectedDocument?.id) => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['docs'] }),
      queryClient.invalidateQueries({ queryKey: ['knowledge-bases'] }),
      queryClient.invalidateQueries({ queryKey: ['knowledge-bases', spaceId, 'discovery'] }),
      queryClient.invalidateQueries({ queryKey: ['knowledge-bases', spaceId, 'governance'] }),
      documentId ? queryClient.invalidateQueries({ queryKey: ['docs', documentId] }) : Promise.resolve(),
    ])
  }

  const createMutation = useMutation({
    mutationFn: (values: CreateNodeForm) =>
      createDocument({
        parentId: normalizeParentId(values.parentId),
        title: values.title,
        docType: values.docType,
        content: values.docType === 'folder' ? '' : `# ${values.title}`,
      }),
    onSuccess: async (detail) => {
      setCreateOpen(false)
      createForm.resetFields()
      setSelectedDocumentId(detail.document.id)
      message.success('已创建')
      await refresh(detail.document.id)
    },
    onError: () => message.error('创建失败'),
  })

  const templateMutation = useMutation({
    mutationFn: (values: TemplateCreateForm) =>
      createDocumentFromTemplate({
        templateId: values.templateId,
        parentId: normalizeParentId(values.parentId),
        title: values.title,
      }),
    onSuccess: async (detail) => {
      setTemplateOpen(false)
      templateForm.resetFields()
      setSelectedDocumentId(detail.document.id)
      message.success('已从模板创建')
      await refresh(detail.document.id)
    },
    onError: () => message.error('模板创建失败'),
  })

  const moveMutation = useMutation({
    mutationFn: ({ documentId, parentId, sortOrder }: { documentId: string; parentId?: string | null; sortOrder?: number }) =>
      moveDocument(documentId, { parentId, sortOrder }),
    onSuccess: async (detail) => {
      setMoveOpen(false)
      moveForm.resetFields()
      setSelectedDocumentId(detail.document.id)
      message.success('目录已更新')
      await refresh(detail.document.id)
    },
    onError: () => message.error('移动或排序失败'),
  })

  const homeMutation = useMutation({
    mutationFn: (documentId: string) => updateKnowledgeBase(spaceId, { homeDocumentId: documentId }),
    onSuccess: async () => {
      message.success('首页已更新')
      await refresh()
    },
    onError: () => message.error('首页更新失败'),
  })

  const accessRequestMutation = useMutation({
    mutationFn: (values: { reason?: string }) =>
      requestResourcePermission('knowledge_base', spaceId, { permissionLevel: 'view', reason: values.reason }),
    onSuccess: () => {
      accessRequestForm.resetFields()
      message.success('访问申请已提交')
    },
    onError: () => message.error('访问申请提交失败'),
  })
  const subscriptionMutation = useMutation({
    mutationFn: ({ subscribed, targetType, targetId }: { subscribed: boolean; targetType: 'knowledge_base' | 'document'; targetId?: string }) =>
      subscribed
        ? unsubscribeKnowledgeTarget(spaceId, { targetType, targetId })
        : subscribeKnowledgeTarget(spaceId, { targetType, targetId }),
    onSuccess: async () => {
      message.success('关注状态已更新')
      await queryClient.invalidateQueries({ queryKey: ['knowledge-bases', spaceId, 'discovery'] })
    },
    onError: () => message.error('关注状态更新失败'),
  })
  const governanceMutation = useMutation({
    mutationFn: (request: KnowledgeBaseBulkGovernanceRequest) => bulkGovernKnowledgeBase(spaceId, request),
    onSuccess: async (result) => {
      message.success(`治理已更新：元数据 ${result.updatedCount}，归档 ${result.archivedCount}，复核 ${result.reviewRequestedCount}`)
      governanceForm.resetFields()
      await refresh()
    },
    onError: () => message.error('批量治理失败'),
  })
  const governanceExportMutation = useMutation({
    mutationFn: () => exportKnowledgeBaseGovernance(spaceId),
    onSuccess: (csv) => {
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `knowledge-governance-${spaceId}.csv`
      link.click()
      URL.revokeObjectURL(url)
      message.success('治理报告已导出')
    },
  })

  const openCreate = (docType: CreateNodeForm['docType']) => {
    createForm.setFieldsValue({ docType, parentId: selectedDirectoryId ?? rootDocumentId ?? undefined })
    setCreateOpen(true)
  }

  const openTemplate = () => {
    templateForm.setFieldsValue({ parentId: selectedDirectoryId ?? rootDocumentId ?? undefined })
    setTemplateOpen(true)
  }

  const openMove = () => {
    if (!selectedDocument) {
      return
    }
    moveForm.setFieldsValue({ parentId: selectedDocument.parentId ?? ROOT_PARENT_VALUE })
    setMoveOpen(true)
  }

  const reorderSelected = (direction: -1 | 1) => {
    if (!selectedDocument || activeSiblingIndex < 0) {
      return
    }
    const target = activeSiblings[activeSiblingIndex + direction]
    if (!target) {
      return
    }
    moveMutation.mutate({
      documentId: selectedDocument.id,
      parentId: selectedDocument.parentId ?? null,
      sortOrder: target.sortOrder,
    })
  }

  const submitGovernance = (mode: 'metadata' | 'review' | 'archive') => {
    const values = governanceForm.getFieldsValue()
    const documentIds = values.documentIds ?? []
    if (documentIds.length === 0) {
      message.warning('请选择治理文档')
      return
    }
    governanceMutation.mutate({
      documentIds,
      maintainerId: values.maintainerId,
      tags: values.tags,
      replaceTags: Boolean(values.replaceTags),
      requestReview: mode === 'review',
      archive: mode === 'archive',
      reviewDueAt: values.reviewDueAt,
    })
  }

  const persistExpandedKeys = (keys: Key[]) => {
    const next = keys.map(String)
    setExpandedKeysBySpace((current) => ({ ...current, [spaceId]: next }))
    if (spaceId) {
      localStorage.setItem(expandedStorageKey(spaceId), JSON.stringify(next))
    }
  }

  if (!spaceId) {
    return <Empty description="知识库不存在" />
  }

  if (spaceQuery.isError) {
    const error = spaceQuery.error instanceof ApiRequestError ? spaceQuery.error : null
    return (
      <div className="kb-detail-page">
        <main className="kb-detail-main">
          <Alert
            type={error?.status === 403 ? 'warning' : 'error'}
            showIcon
            message={error?.status === 403 ? '需要访问权限' : '知识库加载失败'}
            description={error?.status === 403 ? '提交申请后，知识库负责人或管理员可以在权限面板中审批。' : '请稍后重试。'}
          />
          <Space direction="vertical" size={16} className="page-stack">
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/knowledge-bases')}>
              返回知识库
            </Button>
            {error?.status === 403 ? (
              <Form form={accessRequestForm} layout="vertical" onFinish={(values) => accessRequestMutation.mutate(values)}>
                <Form.Item name="reason" label="申请理由">
                  <Input.TextArea maxLength={512} rows={4} placeholder="说明需要查看该知识库的原因" />
                </Form.Item>
                <Button type="primary" htmlType="submit" loading={accessRequestMutation.isPending}>
                  申请查看
                </Button>
              </Form>
            ) : null}
          </Space>
        </main>
      </div>
    )
  }

  return (
    <div className="kb-detail-page">
      <aside className="kb-detail-sidebar">
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/knowledge-bases')}>
          返回
        </Button>
        <Select
          className="kb-space-switch"
          value={spaceId}
          onChange={(nextSpaceId) => navigate(`/knowledge-bases/${nextSpaceId}`)}
          options={(spacesQuery.data ?? []).map((item) => ({ value: item.id, label: item.name }))}
        />
        <Input
          allowClear
          prefix={<SearchOutlined />}
          placeholder="搜索当前知识库目录"
          value={directorySearch}
          onChange={(event) => setDirectorySearch(event.target.value)}
        />
        <Space wrap size={6}>
          <Tooltip title="新建文件夹">
            <Button icon={<FolderOutlined />} onClick={() => openCreate('folder')} />
          </Tooltip>
          <Tooltip title="新建文档">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate('markdown')} />
          </Tooltip>
          <Tooltip title="从模板创建">
            <Button icon={<FileTextOutlined />} onClick={openTemplate} />
          </Tooltip>
        </Space>
        <Space wrap size={6}>
          <Tooltip title="上移">
            <Button
              icon={<ArrowUpOutlined />}
              disabled={activeSiblingIndex <= 0 || selectedDocument?.id === rootDocumentId}
              loading={moveMutation.isPending}
              onClick={() => reorderSelected(-1)}
            />
          </Tooltip>
          <Tooltip title="下移">
            <Button
              icon={<ArrowDownOutlined />}
              disabled={
                activeSiblingIndex < 0 ||
                activeSiblingIndex >= activeSiblings.length - 1 ||
                selectedDocument?.id === rootDocumentId
              }
              loading={moveMutation.isPending}
              onClick={() => reorderSelected(1)}
            />
          </Tooltip>
          <Tooltip title="移动到">
            <Button
              icon={<FolderOpenOutlined />}
              disabled={!selectedDocument || selectedDocument.id === rootDocumentId}
              onClick={openMove}
            />
          </Tooltip>
          <Tooltip title="设为首页">
            <Button
              icon={<HomeOutlined />}
              disabled={!selectedDocument || selectedDocument.docType !== 'markdown' || selectedDocument.id === homeDocumentId}
              loading={homeMutation.isPending}
              onClick={() => selectedDocument && homeMutation.mutate(selectedDocument.id)}
            />
          </Tooltip>
        </Space>
        <div className="kb-tree-panel">
          <Tree
            blockNode
            expandedKeys={directorySearch.trim() ? undefined : expandedKeys}
            autoExpandParent={Boolean(directorySearch.trim())}
            defaultExpandAll={Boolean(directorySearch.trim())}
            selectedKeys={selectedDocument?.id ? [selectedDocument.id] : []}
            treeData={treeData}
            onExpand={persistExpandedKeys}
            onSelect={(keys) => {
              const key = String(keys[0] ?? '')
              if (key) {
                setSelectedDocumentId(key)
              }
            }}
          />
          {!treeQuery.isLoading && treeData.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无目录" /> : null}
        </div>
      </aside>

      <main className="kb-detail-main">
        <section className="kb-hero" style={space?.coverUrl ? { backgroundImage: `url(${space.coverUrl})` } : undefined}>
          <div className="kb-hero-overlay">
            <Space align="start" className="kb-hero-heading">
              <span className="kb-hero-icon">{space?.icon || <BookOutlined />}</span>
              <div>
                <Space wrap>
                  <Typography.Title level={2}>{space?.name ?? '知识库'}</Typography.Title>
                  {space ? <Tag color={statusColor(space.status)}>{statusText(space.status)}</Tag> : null}
                </Space>
                <Typography.Paragraph>{space?.description || '暂无描述'}</Typography.Paragraph>
                <Space wrap className="kb-hero-meta">
                  <Tag>维护人 {space?.ownerName ?? '-'}</Tag>
                  <Tag>{space ? permissionText(space.defaultPermissionLevel) : '-'}</Tag>
                  <Tag>{space ? visibilityText(space.visibility) : '-'}</Tag>
                  <Tag>{stats.total} 个节点</Tag>
                </Space>
              </div>
            </Space>
            <Space wrap>
              <Button
                icon={<BellOutlined />}
                loading={subscriptionMutation.isPending}
                onClick={() =>
                  space &&
                  subscriptionMutation.mutate({
                    subscribed: Boolean(discoveryQuery.data?.spaceSubscribed),
                    targetType: 'knowledge_base',
                    targetId: space.id,
                  })
                }
              >
                {discoveryQuery.data?.spaceSubscribed ? '取消关注' : '关注知识库'}
              </Button>
              <Button
                icon={<SafetyCertificateOutlined />}
                onClick={() =>
                  space &&
                  setPermissionTarget({ resourceType: 'knowledge_base', resourceId: space.id, resourceName: `${space.name} 空间权限` })
                }
              >
                空间权限
              </Button>
              <Button icon={<HomeOutlined />} onClick={() => homeDocumentId && setSelectedDocumentId(homeDocumentId)}>
                首页
              </Button>
              <Button type="primary" icon={<FileTextOutlined />} onClick={() => selectedDocument && navigate(`/docs/${selectedDocument.id}`)}>
                打开
              </Button>
            </Space>
          </div>
        </section>

        <section className="kb-search-panel">
          <Space className="kb-search-row" wrap>
            <Input.Search
              allowClear
              className="kb-search-input"
              placeholder="搜索当前知识库"
              value={kbSearchDraft}
              enterButton={<Button type="primary" icon={<SearchOutlined />} />}
              onChange={(event) => setKbSearchDraft(event.target.value)}
              onSearch={(value) => setKbSearchText(value.trim())}
            />
            <Select
              value={kbSearchScope}
              onChange={setKbSearchScope}
              options={[
                { value: 'knowledge_base', label: '整个知识库' },
                { value: 'directory', label: '当前目录' },
              ]}
            />
            <Select
              allowClear
              placeholder="类型"
              value={kbSearchDocType}
              onChange={setKbSearchDocType}
              options={[
                { value: 'markdown', label: '文档' },
                { value: 'folder', label: '目录' },
                { value: 'space', label: '空间' },
              ]}
            />
            <Select
              allowClear
              showSearch
              placeholder="标签"
              value={kbSearchTagText || undefined}
              onChange={(value) => setKbSearchTagText(value ?? '')}
              options={knowledgeTags}
            />
            <Select allowClear showSearch placeholder="维护人" value={kbSearchMaintainerId} onChange={setKbSearchMaintainerId} options={maintainerOptions} />
            <Select
              allowClear
              placeholder="状态"
              value={kbSearchStatus}
              onChange={setKbSearchStatus}
              options={[
                { value: 'draft', label: '草稿' },
                { value: 'verified', label: '已认证' },
                { value: 'needs_review', label: '待复核' },
                { value: 'outdated', label: '已过期' },
                { value: 'archived', label: '已归档' },
              ]}
            />
            <Input className="kb-date-filter" type="date" value={kbSearchUpdatedFrom} onChange={(event) => setKbSearchUpdatedFrom(event.target.value)} />
            <Input className="kb-date-filter" type="date" value={kbSearchUpdatedTo} onChange={(event) => setKbSearchUpdatedTo(event.target.value)} />
            <Button
              onClick={() => {
                setKbSearchDocType(undefined)
                setKbSearchTagText('')
                setKbSearchMaintainerId(undefined)
                setKbSearchStatus(undefined)
                setKbSearchUpdatedFrom('')
                setKbSearchUpdatedTo('')
              }}
            >
              清除筛选
            </Button>
          </Space>
          {kbSearchText.length >= 2 ? (
            <KnowledgeSearchResults
              loading={kbSearchQuery.isLoading}
              results={kbSearchQuery.data?.items ?? []}
              query={kbSearchText}
              onOpen={(path) => navigate(path)}
              onCreate={() => openCreate('markdown')}
              onClear={() => {
                setKbSearchText('')
                setKbSearchDraft('')
              }}
            />
          ) : null}
        </section>

        <section className="kb-stat-strip">
          <Metric label="文档" value={stats.markdown} />
          <Metric label="目录" value={stats.folder} />
          <Metric label="归档" value={stats.archived} />
          <Metric label="收藏" value={discoveryQuery.data?.favorites.length ?? favoriteDocuments.length} />
        </section>

        <GovernancePanel
          dashboard={governanceQuery.data}
          loading={governanceQuery.isLoading}
          documentOptions={governanceDocumentOptions}
          maintainerOptions={maintainerOptions}
          tagOptions={knowledgeTags}
          form={governanceForm}
          submitting={governanceMutation.isPending}
          exporting={governanceExportMutation.isPending}
          onSubmit={submitGovernance}
          onExport={() => governanceExportMutation.mutate()}
          onOpen={(path) => navigate(path)}
          onSelectDocuments={(ids) => governanceForm.setFieldsValue({ documentIds: ids })}
        />

        <section className="kb-home-band">
          <div className="kb-home-summary">
            <Tag color="blue">当前首页</Tag>
            <Typography.Title level={4}>{spaceQuery.data?.homeDocument.title ?? '首页'}</Typography.Title>
            <Typography.Paragraph type="secondary">
              {spaceQuery.data?.homeDocument.description || `最后更新 ${formatDate(spaceQuery.data?.homeDocument.updatedAt)}`}
            </Typography.Paragraph>
            <Space wrap>
              <Button type="primary" onClick={() => homeDocumentId && navigate(`/docs/${homeDocumentId}`)}>
                进入首页
              </Button>
              <Button onClick={() => homeDocumentId && setSelectedDocumentId(homeDocumentId)}>在目录中定位</Button>
            </Space>
          </div>
          <div className="kb-selected-summary">
            <Tag>{selectedDocument ? docTypeText(selectedDocument.docType) : '未选择'}</Tag>
            <Typography.Title level={4}>{selectedDocument?.title ?? '选择一个目录节点'}</Typography.Title>
            <Space wrap size={6}>
              {selectedDocument ? <Tag>{permissionText(selectedDocument.permissionLevel)}</Tag> : null}
              {selectedDocument?.archived ? <Tag color="default">已归档</Tag> : null}
              {selectedDocument ? <Tag>更新 {formatDate(selectedDocument.updatedAt)}</Tag> : null}
            </Space>
            {selectedDocument ? (
              <Space wrap>
                <Button
                  icon={<BellOutlined />}
                  loading={subscriptionMutation.isPending}
                  onClick={() =>
                    selectedDocument &&
                    subscriptionMutation.mutate({
                      subscribed: (discoveryQuery.data?.subscribedDocuments ?? []).some((document) => document.id === selectedDocument.id),
                      targetType: 'document',
                      targetId: selectedDocument.id,
                    })
                  }
                >
                  {(discoveryQuery.data?.subscribedDocuments ?? []).some((document) => document.id === selectedDocument.id) ? '取消关注' : '关注节点'}
                </Button>
                <Button
                  icon={<SafetyCertificateOutlined />}
                  onClick={() =>
                    setPermissionTarget({ resourceType: 'document', resourceId: selectedDocument.id, resourceName: `${selectedDocument.title} 节点权限` })
                  }
                >
                  节点权限
                </Button>
              </Space>
            ) : null}
            {selectedDocument?.docType === 'markdown' ? (
              <CollaborationHealthCard
                dirty={Boolean(collaborationHealthQuery.data?.dirty)}
                onlineCount={collaborationHealthQuery.data?.activeUsers ?? 0}
                serverClock={collaborationHealthQuery.data?.serverClock ?? 0}
                lastSavedAt={collaborationHealthQuery.data?.lastSavedAt}
                onOpen={() => navigate(`/docs/${selectedDocument.id}`)}
                onRefresh={() => collaborationHealthQuery.refetch()}
              />
            ) : null}
          </div>
        </section>

        <section className="kb-section-grid">
          <KnowledgeList title="置顶" items={pinnedDocuments} onOpen={(id) => navigate(`/docs/${id}`)} onSelect={setSelectedDocumentId} />
          <KnowledgeList title="最近访问" items={discoveryQuery.data?.recentAccessed ?? recentDocuments} onOpen={(id) => navigate(`/docs/${id}`)} onSelect={setSelectedDocumentId} />
          <KnowledgeList title="我维护的文档" items={discoveryQuery.data?.maintainedByMe ?? []} onOpen={(id) => navigate(`/docs/${id}`)} onSelect={setSelectedDocumentId} />
          <KnowledgeList title="待复核" items={discoveryQuery.data?.dueForReview ?? []} onOpen={(id) => navigate(`/docs/${id}`)} onSelect={setSelectedDocumentId} />
          <KnowledgeList title="收藏" items={discoveryQuery.data?.favorites ?? favoriteDocuments} onOpen={(id) => navigate(`/docs/${id}`)} onSelect={setSelectedDocumentId} />
          <KnowledgeList title="热门知识" items={discoveryQuery.data?.popular ?? []} onOpen={(id) => navigate(`/docs/${id}`)} onSelect={setSelectedDocumentId} />
          <KnowledgeList title="推荐阅读" items={discoveryQuery.data?.recommended ?? []} onOpen={(id) => navigate(`/docs/${id}`)} onSelect={setSelectedDocumentId} />
          <KnowledgeList title="我的关注" items={discoveryQuery.data?.subscribedDocuments ?? []} onOpen={(id) => navigate(`/docs/${id}`)} onSelect={setSelectedDocumentId} />
          <KnowledgeList title="常用目录" items={commonDirectories} onOpen={(id) => setSelectedDocumentId(id)} onSelect={setSelectedDocumentId} />
          <TemplateList templates={templatesQuery.data ?? []} onUse={(template) => {
            templateForm.setFieldsValue({
              templateId: template.id,
              title: template.title,
              parentId: selectedDirectoryId ?? rootDocumentId ?? undefined,
            })
            setTemplateOpen(true)
          }} />
        </section>
      </main>

      <Modal
        title="新建内容"
        open={createOpen}
        onCancel={() => {
          setCreateOpen(false)
          createForm.resetFields()
        }}
        onOk={() => createForm.submit()}
        confirmLoading={createMutation.isPending}
        destroyOnClose
      >
        <Form form={createForm} layout="vertical" onFinish={(values) => createMutation.mutate(values)}>
          <Form.Item name="title" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input maxLength={255} />
          </Form.Item>
          <Form.Item name="docType" label="类型" rules={[{ required: true }]}>
            <Select options={[
              { value: 'markdown', label: '文档' },
              { value: 'folder', label: '文件夹' },
            ]} />
          </Form.Item>
          <Form.Item name="parentId" label="位置">
            <Select options={directoryOptions} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="从模板创建"
        open={templateOpen}
        onCancel={() => {
          setTemplateOpen(false)
          templateForm.resetFields()
        }}
        onOk={() => templateForm.submit()}
        confirmLoading={templateMutation.isPending}
        destroyOnClose
      >
        <Form form={templateForm} layout="vertical" onFinish={(values) => templateMutation.mutate(values)}>
          <Form.Item name="templateId" label="模板" rules={[{ required: true, message: '请选择模板' }]}>
            <Select options={(templatesQuery.data ?? []).map((template) => ({ value: template.id, label: `${categoryText(template.category)} / ${template.title}` }))} />
          </Form.Item>
          <Form.Item name="title" label="标题">
            <Input maxLength={255} />
          </Form.Item>
          <Form.Item name="parentId" label="位置">
            <Select options={directoryOptions} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="移动到"
        open={moveOpen}
        onCancel={() => {
          setMoveOpen(false)
          moveForm.resetFields()
        }}
        onOk={() => moveForm.submit()}
        confirmLoading={moveMutation.isPending}
        destroyOnClose
      >
        <Form
          form={moveForm}
          layout="vertical"
          onFinish={(values) => {
            if (!selectedDocument) {
              return
            }
            moveMutation.mutate({
              documentId: selectedDocument.id,
              parentId: normalizeParentId(values.parentId),
              sortOrder: selectedDocument.sortOrder,
            })
          }}
        >
          <Form.Item name="parentId" label="目标目录" rules={[{ required: true, message: '请选择目标目录' }]}>
            <Select options={[{ label: '根层级', value: ROOT_PARENT_VALUE }, ...movableDirectoryOptions]} />
          </Form.Item>
        </Form>
      </Modal>

      {permissionTarget ? (
        <ResourcePermissionsModal
          open
          resourceType={permissionTarget.resourceType}
          resourceId={permissionTarget.resourceId}
          resourceName={permissionTarget.resourceName}
          onClose={() => setPermissionTarget(null)}
        />
      ) : null}
    </div>
  )
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div className="kb-metric">
      <strong>{value}</strong>
      <span>{label}</span>
    </div>
  )
}

function GovernancePanel({
  dashboard,
  loading,
  documentOptions,
  maintainerOptions,
  tagOptions,
  form,
  submitting,
  exporting,
  onSubmit,
  onExport,
  onOpen,
  onSelectDocuments,
}: {
  dashboard?: KnowledgeBaseGovernanceDashboard
  loading: boolean
  documentOptions: { label: string; value: string }[]
  maintainerOptions: { label: string; value: string }[]
  tagOptions: { label: string; value: string }[]
  form: FormInstance<GovernanceBulkForm>
  submitting: boolean
  exporting: boolean
  onSubmit: (mode: 'metadata' | 'review' | 'archive') => void
  onExport: () => void
  onOpen: (path: string) => void
  onSelectDocuments: (ids: string[]) => void
}) {
  const health = dashboard?.health
  const risks = dashboard?.risks ?? []
  const riskDocumentIds = risks
    .filter((risk) => risk.resourceType === 'document' && risk.resourceId)
    .map((risk) => risk.resourceId)
    .filter((id, index, all) => all.indexOf(id) === index)

  return (
    <section className="kb-governance-panel">
      <Space className="kb-governance-heading" align="center" wrap>
        <div>
          <Typography.Title level={4}>治理</Typography.Title>
          <Typography.Text type="secondary">内容健康、权限风险、访问统计和批量处理</Typography.Text>
        </div>
        <Space wrap>
          <Button size="small" onClick={() => onSelectDocuments(riskDocumentIds)} disabled={riskDocumentIds.length === 0}>
            选择风险文档
          </Button>
          <Button size="small" icon={<DownloadOutlined />} loading={exporting} onClick={onExport}>
            导出报告
          </Button>
        </Space>
      </Space>

      {loading ? <Typography.Text type="secondary">治理数据加载中...</Typography.Text> : null}

      <div className="kb-governance-metrics">
        <Metric label="活跃文档" value={health?.activeDocumentCount ?? 0} />
        <Metric label="过期/待复核" value={health?.outdatedDocumentCount ?? 0} />
        <Metric label="无人维护" value={health?.unmaintainedDocumentCount ?? 0} />
        <Metric label="无 owner" value={health?.ownerlessDocumentCount ?? 0} />
        <Metric label="权限风险" value={health?.highRiskPermissionCount ?? 0} />
        <Metric label="访问人数" value={dashboard?.accessStats.visitorCount ?? 0} />
      </div>

      <Form form={form} layout="vertical" className="kb-governance-bulk-form">
        <Form.Item name="documentIds" label="批量治理文档">
          <Select mode="multiple" options={documentOptions} placeholder="选择文档" maxTagCount="responsive" />
        </Form.Item>
        <Space wrap align="end">
          <Form.Item name="maintainerId" label="维护人">
            <Select allowClear showSearch className="kb-governance-select" options={maintainerOptions} placeholder="维护人" optionFilterProp="label" />
          </Form.Item>
          <Form.Item name="tags" label="标签">
            <Select mode="tags" className="kb-governance-tags" options={tagOptions} placeholder="标签" />
          </Form.Item>
          <Form.Item name="replaceTags" label="标签模式" initialValue={false}>
            <Select
              className="kb-governance-mode"
              options={[
                { value: false, label: '追加标签' },
                { value: true, label: '替换标签' },
              ]}
            />
          </Form.Item>
          <Form.Item name="reviewDueAt" label="复核日期">
            <Input className="kb-date-filter" type="date" />
          </Form.Item>
        </Space>
        <Space wrap>
          <Button loading={submitting} onClick={() => onSubmit('metadata')}>更新维护信息</Button>
          <Button loading={submitting} onClick={() => onSubmit('review')}>发起复核</Button>
          <Button danger loading={submitting} onClick={() => onSubmit('archive')}>批量归档</Button>
        </Space>
      </Form>

      <div className="kb-governance-grid">
        <div className="kb-governance-section">
          <Typography.Title level={5}>风险列表</Typography.Title>
          <Space direction="vertical" size={6} className="kb-governance-list">
            {risks.slice(0, 8).map((risk) => (
              <button className="kb-governance-risk" type="button" key={risk.id} onClick={() => risk.actionPath && onOpen(risk.actionPath)}>
                <Tag color={severityColor(risk.severity)}>{risk.severity}</Tag>
                <span>
                  <strong>{risk.title ?? risk.ruleCode}</strong>
                  <small>{risk.reason}</small>
                </span>
                {risk.permissionLevel ? <Tag>{permissionText(risk.permissionLevel)}</Tag> : null}
              </button>
            ))}
            {risks.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无治理风险" /> : null}
          </Space>
        </div>
        <div className="kb-governance-section">
          <Typography.Title level={5}>访问统计</Typography.Title>
          <Space direction="vertical" size={8} className="kb-governance-list">
            <Typography.Text type="secondary">访问 {dashboard?.accessStats.accessCount ?? 0} 次 · 访问人数 {dashboard?.accessStats.visitorCount ?? 0}</Typography.Text>
            {(dashboard?.accessStats.popularDocuments ?? []).slice(0, 4).map((item) => (
              <button className="kb-list-item" type="button" key={item.document.id} onClick={() => onOpen(`/docs/${item.document.id}`)}>
                <FileTextOutlined />
                <span>
                  <strong>{item.document.title}</strong>
                  <small>{item.accessCount} 次 · {item.visitorCount} 人</small>
                </span>
              </button>
            ))}
            {(dashboard?.accessStats.lowAccessDocuments ?? []).length ? (
              <Typography.Text type="secondary">低访问：{dashboard?.accessStats.lowAccessDocuments.map((item) => item.document.title).join('、')}</Typography.Text>
            ) : null}
            {(dashboard?.accessStats.noResultTerms ?? []).length ? (
              <Space wrap size={4}>
                {(dashboard?.accessStats.noResultTerms ?? []).map((term) => <Tag key={term.query}>无结果 {term.query} · {term.count}</Tag>)}
              </Space>
            ) : null}
          </Space>
        </div>
      </div>
    </section>
  )
}

function CollaborationHealthCard({
  dirty,
  onlineCount,
  serverClock,
  lastSavedAt,
  onOpen,
  onRefresh,
}: {
  dirty: boolean
  onlineCount: number
  serverClock: number
  lastSavedAt?: string | null
  onOpen: () => void
  onRefresh: () => void
}) {
  return (
    <Alert
      className="kb-collaboration-health"
      showIcon
      type={dirty ? 'warning' : 'info'}
      message={dirty ? '协同有未落盘快照' : '协同健康'}
      description={
        <Space direction="vertical" size={4}>
          <Typography.Text type="secondary">
            在线 {onlineCount} 人 · serverClock {serverClock}
            {lastSavedAt ? ` · 保存 ${new Date(lastSavedAt).toLocaleTimeString()}` : ''}
          </Typography.Text>
          <Typography.Text type="secondary">异常时可刷新状态、进入文档重连，或按只读方式查看最近已保存版本。</Typography.Text>
          <Space size={6}>
            <Button size="small" onClick={onRefresh}>刷新状态</Button>
            <Button size="small" type="primary" onClick={onOpen}>打开重连</Button>
          </Space>
        </Space>
      }
    />
  )
}

function KnowledgeSearchResults({
  loading,
  results,
  query,
  onOpen,
  onCreate,
  onClear,
}: {
  loading: boolean
  results: SearchResult[]
  query: string
  onOpen: (path: string) => void
  onCreate: () => void
  onClear: () => void
}) {
  if (loading) {
    return <Typography.Text type="secondary">搜索中...</Typography.Text>
  }
  if (results.length === 0) {
    return (
      <div className="kb-search-empty">
        <Empty description={`没有找到 “${query}”`} />
        <Space wrap>
          <Button type="primary" onClick={onCreate}>创建文档</Button>
          <Button onClick={onClear}>扩大范围</Button>
        </Space>
      </div>
    )
  }
  return (
    <Space orientation="vertical" size={8} className="kb-search-results">
      {results.map((item) => (
        <div className="kb-search-result" key={`${item.objectType}-${item.objectId}`}>
          <div>
            <Space wrap size={6}>
              <Tag color="blue">{hitSourceText(item.hitSource)}</Tag>
              {item.knowledgeStatus ? <Tag>{knowledgeStatusText(item.knowledgeStatus)}</Tag> : null}
              <Typography.Text strong>{item.title}</Typography.Text>
            </Space>
            <Typography.Paragraph type="secondary">{item.excerpt || item.directoryPath || item.permissionExplanation}</Typography.Paragraph>
            <Space wrap size={4}>
              {item.directoryPath ? <Tag>{item.directoryPath}</Tag> : null}
              {(item.tags ?? []).map((tag) => <Tag key={tag}>{tag}</Tag>)}
              {item.maintainerName ? <Tag>维护人 {item.maintainerName}</Tag> : null}
              <Tag color={item.accessState === 'available' ? 'green' : 'orange'}>{item.accessState === 'available' ? '可访问' : item.accessState}</Tag>
            </Space>
          </div>
          {item.webPath ? <Button type="link" onClick={() => onOpen(item.webPath || '/')}>打开</Button> : null}
        </div>
      ))}
    </Space>
  )
}

function KnowledgeList({
  title,
  items,
  onOpen,
  onSelect,
}: {
  title: string
  items: DocumentSummary[]
  onOpen: (documentId: string) => void
  onSelect: (documentId: string) => void
}) {
  return (
    <div className="kb-panel">
      <Typography.Title level={5}>{title}</Typography.Title>
      <Space orientation="vertical" size={6} className="kb-list-panel">
        {items.map((document) => (
          <button className="kb-list-item" type="button" key={document.id} onClick={() => onSelect(document.id)} onDoubleClick={() => onOpen(document.id)}>
            {document.docType === 'folder' ? <FolderOutlined /> : <FileTextOutlined />}
            <span>
              <strong>{document.title}</strong>
              <small>{docTypeText(document.docType)} · {formatDate(document.updatedAt)}</small>
            </span>
          </button>
        ))}
        {items.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无内容" /> : null}
      </Space>
    </div>
  )
}

function TemplateList({ templates, onUse }: { templates: DocumentTemplate[]; onUse: (template: DocumentTemplate) => void }) {
  return (
    <div className="kb-panel">
      <Typography.Title level={5}>模板</Typography.Title>
      <Space orientation="vertical" size={6} className="kb-list-panel">
        {templates.slice(0, 6).map((template) => (
          <button className="kb-list-item" type="button" key={template.id} onClick={() => onUse(template)}>
            <FileTextOutlined />
            <span>
              <strong>{template.title}</strong>
              <small>{categoryText(template.category)}</small>
            </span>
          </button>
        ))}
      </Space>
    </div>
  )
}

function buildKbTreeData(
  nodes: DocumentTreeNode[],
  search: string,
  activeDocumentId: string | null,
  favoriteDocumentIds: Set<string>,
): DataNode[] {
  const normalizedSearch = search.trim().toLowerCase()
  return nodes
    .map((node) => buildKbTreeNode(node, normalizedSearch, activeDocumentId, favoriteDocumentIds))
    .filter(Boolean) as DataNode[]
}

function buildKbTreeNode(
  node: DocumentTreeNode,
  search: string,
  activeDocumentId: string | null,
  favoriteDocumentIds: Set<string>,
): DataNode | null {
  const childNodes = node.children
    .map((child) => buildKbTreeNode(child, search, activeDocumentId, favoriteDocumentIds))
    .filter(Boolean) as DataNode[]
  const matched = !search || node.document.title.toLowerCase().includes(search)
  if (!matched && childNodes.length === 0) {
    return null
  }
  return {
    key: node.document.id,
    title: (
      <span className={`kb-tree-title${node.document.id === activeDocumentId ? ' active' : ''}${node.document.archived ? ' archived' : ''}`}>
        {node.document.docType === 'markdown' ? <FileTextOutlined /> : <FolderOutlined />}
        <span>{node.document.title}</span>
        <Tag>{docTypeText(node.document.docType)}</Tag>
        <Tag>{permissionText(node.document.permissionLevel)}</Tag>
        {favoriteDocumentIds.has(node.document.id) ? <StarFilled className="kb-tree-star" /> : null}
        {node.document.archived ? <Tag color="default">归档</Tag> : null}
        <small>{formatDate(node.document.updatedAt)}</small>
      </span>
    ) as ReactNode,
    children: childNodes,
  }
}

function findTreeNode(nodes: DocumentTreeNode[], documentId: string): DocumentTreeNode | null {
  for (const node of nodes) {
    if (node.document.id === documentId) {
      return node
    }
    const child = findTreeNode(node.children, documentId)
    if (child) {
      return child
    }
  }
  return null
}

function flattenTree(root: DocumentTreeNode | null): DocumentTreeNode[] {
  if (!root) {
    return []
  }
  return [root, ...root.children.flatMap(flattenTree)]
}

function summarizeDocuments(documents: DocumentSummary[]) {
  return {
    total: documents.length,
    markdown: documents.filter((document) => document.docType === 'markdown' && !document.archived).length,
    folder: documents.filter((document) => document.docType === 'folder' && !document.archived).length,
    archived: documents.filter((document) => document.archived).length,
  }
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

function normalizeParentId(value?: string | null) {
  return !value || value === ROOT_PARENT_VALUE ? null : value
}

function expandedStorageKey(spaceId: string) {
  return `kb-expanded-${spaceId}`
}

function readExpandedKeys(spaceId: string) {
  if (!spaceId) {
    return []
  }
  try {
    const raw = localStorage.getItem(expandedStorageKey(spaceId))
    return raw ? JSON.parse(raw) as string[] : []
  } catch {
    return []
  }
}

function statusColor(status: KnowledgeBaseSpaceSummary['status']) {
  return { active: 'green', disabled: 'orange', archived: 'default' }[status]
}

function statusText(status: KnowledgeBaseSpaceSummary['status']) {
  return { active: '有效', disabled: '停用', archived: '归档' }[status]
}

function visibilityText(visibility: KnowledgeBaseSpaceSummary['visibility']) {
  return { private: '私有', workspace: '工作区' }[visibility]
}

function permissionText(permission?: string | null) {
  return {
    owner: '拥有者',
    manage: '可管理',
    edit: '可编辑',
    comment: '可评论',
    view: '可查看',
  }[permission ?? 'view'] ?? permission
}

function docTypeText(docType: DocumentSummary['docType']) {
  return { space: '空间', folder: '目录', markdown: '文档' }[docType]
}

function categoryText(category: string) {
  return {
    meeting: '会议纪要',
    requirement: '需求文档',
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

function hitSourceText(source?: string | null) {
  return {
    title: '标题',
    body_block: '正文块',
    comment: '评论',
    tags: '标签',
    directory_path: '目录路径',
  }[source ?? 'title'] ?? source
}

function severityColor(severity: KnowledgeBaseGovernanceRisk['severity']) {
  if (severity === 'critical') return 'red'
  if (severity === 'high') return 'orange'
  if (severity === 'medium') return 'gold'
  return 'blue'
}

function formatDate(value?: string | null) {
  if (!value) {
    return '-'
  }
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit' }).format(new Date(value))
}
