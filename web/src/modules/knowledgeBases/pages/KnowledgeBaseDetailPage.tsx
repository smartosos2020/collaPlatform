import {
  ArrowDownOutlined,
  ArrowLeftOutlined,
  ArrowUpOutlined,
  AppstoreOutlined,
  BookOutlined,
  BellOutlined,
  FileTextOutlined,
  FolderOpenOutlined,
  FolderOutlined,
  HomeOutlined,
  LinkOutlined,
  PlusOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  StarFilled,
  TableOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App as AntdApp, Button, Empty, Form, Input, Modal, Select, Space, Table, Tag, Tooltip, Tree, Typography } from 'antd'
import type { DataNode } from 'antd/es/tree'
import type { Key, ReactNode } from 'react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'

import {
  getKnowledgeContentCollaborationHealth,
  type KnowledgeBaseItem,
  type KnowledgeContentTemplate,
  type KnowledgeBaseItemTreeNode,
} from '../../knowledgeBases/content/api/knowledgeContentApi'
import { searchAll, type SearchResult } from '../../search/api/searchApi'
import { ResourcePermissionsModal } from '../../permissions/components/ResourcePermissionsModal'
import { requestResourcePermission, type ManagedResourceType } from '../../permissions/api/resourcePermissionsApi'
import { listFavoriteObjects } from '../../platform/api/platformObjectsApi'
import { PlatformObjectPicker } from '../../platform/components/PlatformObjectPicker'
import { getFileDownloadUrl } from '../../files/api/filesApi'
import { getBase, getTable, queryRecords, type BaseField, type BaseRecord } from '../../bases/api/basesApi'
import { ApiRequestError } from '../../../shared/api/httpClient'
import {
  createKnowledgeBaseItem,
  createKnowledgeBaseBaseEntry,
  createKnowledgeBaseItemFromTemplate,
  getKnowledgeBase,
  getKnowledgeBaseDiscovery,
  listKnowledgeBaseItemTree,
  listKnowledgeBaseTemplates,
  listKnowledgeBases,
  moveKnowledgeBaseItem,
  subscribeKnowledgeTarget,
  unsubscribeKnowledgeTarget,
  updateKnowledgeBase,
  updateKnowledgeObjectEntry,
  type KnowledgeBaseSpaceSummary,
} from '../api/knowledgeBasesApi'

type CreateNodeForm = {
  title: string
  contentType: 'markdown' | 'folder' | 'object_ref' | 'external_link'
  parentId?: string
  targetObjectType?: string
  targetObjectId?: string
  targetRoute?: string
  displayMode?: KnowledgeBaseItem['displayMode']
  targetTitleStrategy?: KnowledgeBaseItem['targetTitleStrategy']
  entryAlias?: string
  targetBaseMode?: 'existing' | 'new'
  targetBaseId?: string
  targetBaseTableId?: string
  targetBaseViewId?: string
  newBaseName?: string
  newBaseDescription?: string
}

type TemplateCreateForm = {
  templateId: string
  title?: string
  parentId?: string
}

type MoveNodeForm = {
  parentId?: string
}

type ObjectEntryForm = {
  displayMode?: KnowledgeBaseItem['displayMode']
  targetTitleStrategy?: KnowledgeBaseItem['targetTitleStrategy']
  entryAlias?: string
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
  const [selectedItemId, setSelectedItemId] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [templateOpen, setTemplateOpen] = useState(false)
  const [moveOpen, setMoveOpen] = useState(false)
  const [objectEntryOpen, setObjectEntryOpen] = useState(false)
  const [permissionTarget, setPermissionTarget] = useState<PermissionTarget | null>(null)
  const [expandedKeysBySpace, setExpandedKeysBySpace] = useState<Record<string, string[]>>({})
  const [createForm] = Form.useForm<CreateNodeForm>()
  const [templateForm] = Form.useForm<TemplateCreateForm>()
  const [moveForm] = Form.useForm<MoveNodeForm>()
  const [objectEntryForm] = Form.useForm<ObjectEntryForm>()
  const [accessRequestForm] = Form.useForm<{ reason?: string }>()
  const mainScrollRef = useRef<HTMLElement | null>(null)
  const createContentType = Form.useWatch('contentType', createForm)
  const createTargetObjectType = Form.useWatch('targetObjectType', createForm)
  const createTargetBaseMode = Form.useWatch('targetBaseMode', createForm)
  const createTargetBaseId = Form.useWatch('targetBaseId', createForm)
  const createTargetBaseTableId = Form.useWatch('targetBaseTableId', createForm)
  const contentPath = useCallback((itemId: string) => `/knowledge-bases/${spaceId}/items/${itemId}`, [spaceId])
  const directoryPath = useCallback(
    (itemId: string, view = 'directory') => {
      const params = new URLSearchParams()
      params.set('itemId', itemId)
      params.set('view', view)
      return `/knowledge-bases/${spaceId}?${params.toString()}`
    },
    [spaceId],
  )
  const normalizeKnowledgePath = useCallback((path: string) => {
    return path
  }, [])

  const spacesQuery = useQuery({ queryKey: ['knowledge-bases', false], queryFn: () => listKnowledgeBases() })
  const spaceQuery = useQuery({
    queryKey: ['knowledge-bases', spaceId],
    queryFn: () => getKnowledgeBase(spaceId),
    enabled: Boolean(spaceId),
  })
  const treeQuery = useQuery({
    queryKey: ['knowledge-bases', spaceId, 'items', 'tree', true],
    queryFn: () => listKnowledgeBaseItemTree(spaceId, { includeArchived: true }),
    enabled: Boolean(spaceId),
  })
  const templatesQuery = useQuery({
    queryKey: ['knowledge-bases', spaceId, 'templates'],
    queryFn: () => listKnowledgeBaseTemplates(spaceId),
    enabled: Boolean(spaceId),
  })
  const createTargetBaseQuery = useQuery({
    queryKey: ['bases', createTargetBaseId, 'kb-object-picker'],
    queryFn: () => getBase(createTargetBaseId || ''),
    enabled: Boolean(createOpen && createContentType === 'object_ref' && createTargetObjectType === 'base' && createTargetBaseId),
  })
  const createTargetBaseTableQuery = useQuery({
    queryKey: ['bases', createTargetBaseId, 'tables', createTargetBaseTableId, 'kb-object-picker'],
    queryFn: () => getTable(createTargetBaseId || '', createTargetBaseTableId || ''),
    enabled: Boolean(createOpen && createContentType === 'object_ref' && createTargetObjectType === 'base' && createTargetBaseId && createTargetBaseTableId),
  })
  const favoriteObjectsQuery = useQuery({ queryKey: ['platform', 'favorites', 'kb-detail'], queryFn: () => listFavoriteObjects(50) })
  const discoveryQuery = useQuery({
    queryKey: ['knowledge-bases', spaceId, 'discovery'],
    queryFn: () => getKnowledgeBaseDiscovery(spaceId),
    enabled: Boolean(spaceId),
  })
  const space = spaceQuery.data?.space ?? null
  const rootItemId = space?.rootItemId ?? null
  const homeItemId = space?.homeItemId ?? null
  const rootNode = useMemo(
    () => (rootItemId ? findTreeNode(treeQuery.data ?? [], rootItemId) : null),
    [rootItemId, treeQuery.data],
  )
  const flatNodes = useMemo(() => flattenTree(rootNode), [rootNode])
  const items = useMemo(() => flatNodes.map((node) => node.item), [flatNodes])
  const queryState = useMemo(() => {
    const params = new URLSearchParams(location.search)
    return {
      itemId: params.get('itemId'),
      view: params.get('view') ?? 'directory',
      mode: params.get('mode') ?? 'browse',
      blockId: params.get('blockId'),
      commentId: params.get('commentId'),
    }
  }, [location.search])
  const queryItemId = queryState.itemId
  const queryItemMissing = Boolean(queryItemId && items.length > 0 && !items.some((document) => document.id === queryItemId))
  const activeItemId =
    selectedItemId && items.some((document) => document.id === selectedItemId)
      ? selectedItemId
      : queryItemId && items.some((document) => document.id === queryItemId)
        ? queryItemId
        : homeItemId
  const selectedItem = items.find((document) => document.id === activeItemId) ?? spaceQuery.data?.homeItem ?? null
  const collaborationHealthQuery = useQuery({
    queryKey: ['knowledge-content', activeItemId, 'collaboration', 'health', 'kb-detail'],
    queryFn: () => getKnowledgeContentCollaborationHealth(spaceId, activeItemId || ''),
    enabled: Boolean(activeItemId && selectedItem?.contentType === 'markdown'),
    refetchInterval: 15000,
  })
  const expandedKeys = spaceId ? expandedKeysBySpace[spaceId] ?? readExpandedKeys(spaceId) : []
  const selectedDirectoryId =
    selectedItem?.contentType === 'folder' || selectedItem?.contentType === 'space'
      ? selectedItem.id
      : selectedItem?.parentId ?? rootItemId ?? undefined
  const favoriteItemIds = useMemo(
    () =>
      new Set(
        (favoriteObjectsQuery.data ?? [])
          .filter((item) => item.objectType === 'knowledge_content' && item.accessState === 'available')
          .map((item) => item.objectId),
      ),
    [favoriteObjectsQuery.data],
  )
  const treeData = useMemo(
    () => buildKbTreeData(rootNode ? [rootNode] : [], directorySearch, selectedItem?.id ?? null, favoriteItemIds),
    [directorySearch, favoriteItemIds, rootNode, selectedItem?.id],
  )
  const directoryOptions = useMemo(
    () =>
      items
        .filter((document) => ['space', 'folder'].includes(document.contentType) && !document.archived)
        .sort(knowledgeItemSort)
        .map((document) => ({ label: `${contentTypeText(document.contentType)} / ${document.title}`, value: document.id })),
    [items],
  )
  const movableDirectoryOptions = useMemo(
    () =>
      directoryOptions.filter((option) => {
        if (!selectedItem || option.value === selectedItem.id) {
          return false
        }
        return !isDescendantDocument(items, selectedItem.id, option.value)
      }),
    [directoryOptions, items, selectedItem],
  )
  const recentItems = useMemo(
    () =>
      items
        .filter((document) => document.contentType === 'markdown' && !document.archived)
        .sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime())
        .slice(0, 6),
    [items],
  )
  const pinnedDocuments = useMemo(
    () =>
      items
        .filter((document) => document.parentId === rootItemId && !document.archived)
        .sort(knowledgeItemSort)
        .slice(0, 4),
    [items, rootItemId],
  )
  const commonDirectories = useMemo(
    () =>
      items
        .filter((document) => document.contentType === 'folder' && !document.archived)
        .sort(knowledgeItemSort)
        .slice(0, 6),
    [items],
  )
  const favoriteItems = useMemo(
    () => items.filter((document) => favoriteItemIds.has(document.id) && !document.archived).slice(0, 6),
    [items, favoriteItemIds],
  )
  const activeSiblings = useMemo(() => {
    if (!selectedItem) {
      return []
    }
    return items
      .filter((document) => (document.parentId ?? null) === (selectedItem.parentId ?? null) && !document.archived)
      .sort(knowledgeItemSort)
  }, [items, selectedItem])
  const activeSiblingIndex = selectedItem ? activeSiblings.findIndex((document) => document.id === selectedItem.id) : -1
  const selectedDirectoryChildren = useMemo(() => {
    if (!selectedDirectoryId) {
      return []
    }
    return items
      .filter((document) => document.parentId === selectedDirectoryId && !document.archived)
      .sort(knowledgeItemSort)
  }, [items, selectedDirectoryId])
  const maintainerOptions = useMemo(
    () =>
      Array.from(
        new Map(
          items
            .filter((document) => document.maintainerId)
            .map((document) => [document.maintainerId, { value: document.maintainerId || '', label: document.maintainerName || document.maintainerId || '' }]),
        ).values(),
      ),
    [items],
  )
  const knowledgeTags = useMemo(
    () => Array.from(new Set(items.flatMap((document) => document.tags ?? []))).sort().map((tag) => ({ value: tag, label: tag })),
    [items],
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
        contentType: kbSearchDocType,
        tags: parsedSearchTags,
        maintainerId: kbSearchMaintainerId,
        knowledgeStatus: kbSearchStatus,
        updatedFrom: kbSearchUpdatedFrom || undefined,
        updatedTo: kbSearchUpdatedTo || undefined,
      }),
    enabled: Boolean(spaceId) && kbSearchText.trim().length >= 2,
  })

  const refresh = async (itemId = selectedItem?.id) => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['knowledge-content'] }),
      queryClient.invalidateQueries({ queryKey: ['knowledge-bases'] }),
      queryClient.invalidateQueries({ queryKey: ['knowledge-bases', spaceId, 'discovery'] }),
      itemId ? queryClient.invalidateQueries({ queryKey: ['knowledge-content', itemId] }) : Promise.resolve(),
    ])
  }

  const openItemOrDirectory = useCallback(
    (itemId: string, options?: { replace?: boolean }) => {
      const document = items.find((item) => item.id === itemId)
      if (!document) {
        return
      }
      setSelectedItemId(itemId)
      if (document.contentType === 'markdown') {
        navigate(contentPath(itemId), { replace: options?.replace })
        return
      }
      if (document.contentType === 'object_ref') {
        if (document.targetSummary && document.targetSummary.accessState !== 'available') {
          message.warning(targetAccessText(document.targetSummary.accessState))
          return
        }
        if (document.targetObjectType === 'base') {
          navigate(directoryPath(itemId), { replace: options?.replace })
          return
        }
        if (document.targetObjectType === 'file' && document.targetObjectId) {
          void getFileDownloadUrl(document.targetObjectId)
            .then((response) => window.open(response.downloadUrl, '_blank', 'noopener,noreferrer'))
            .catch(() => message.error('文件打开失败'))
          return
        }
        if (!document.targetRoute) {
          message.warning('目标不可访问')
          return
        }
        navigate(normalizeKnowledgePath(document.targetRoute), { replace: options?.replace })
        return
      }
      if (document.contentType === 'external_link' && document.targetRoute) {
        window.open(document.targetRoute, '_blank', 'noopener,noreferrer')
        return
      }
      navigate(directoryPath(itemId), { replace: options?.replace })
    },
    [contentPath, directoryPath, items, message, navigate, normalizeKnowledgePath],
  )

  const setManagementView = useCallback(
    (open: boolean) => {
      const params = new URLSearchParams(location.search)
      if (selectedItem?.contentType !== 'markdown' && selectedItem?.id) {
        params.set('itemId', selectedItem.id)
      }
      if (open) {
        params.set('view', 'management')
      } else if (params.get('view') === 'management') {
        params.delete('view')
      }
      const search = params.toString()
      navigate(`/knowledge-bases/${spaceId}${search ? `?${search}` : ''}`, { replace: false })
    },
    [location.search, navigate, selectedItem, spaceId],
  )

  const createMutation = useMutation({
    mutationFn: async (values: CreateNodeForm) => {
      if (values.contentType === 'object_ref' && values.targetObjectType === 'base') {
        const baseId = values.targetBaseId || values.targetObjectId
        if (values.targetBaseMode !== 'new' && !baseId) {
          throw new Error('Base target is required')
        }
        return createKnowledgeBaseBaseEntry(spaceId, {
          parentId: normalizeParentId(values.parentId),
          existingBaseId: values.targetBaseMode === 'new' ? undefined : baseId,
          newBaseName: values.targetBaseMode === 'new' ? (values.newBaseName || values.title) : undefined,
          newBaseDescription: values.newBaseDescription,
          displayMode: values.displayMode ?? 'inline',
          targetTitleStrategy: values.targetTitleStrategy ?? 'alias',
          entryAlias: values.entryAlias || values.title,
        })
      }
      return createKnowledgeBaseItem(spaceId, {
        parentId: normalizeParentId(values.parentId),
        title: values.title,
        contentType: values.contentType,
        content: values.contentType === 'markdown' ? `# ${values.title}` : '',
        targetObjectType: values.targetObjectType,
        targetObjectId: values.targetObjectId,
        targetRoute: values.contentType === 'external_link' ? values.targetRoute : undefined,
        displayMode: values.displayMode,
        targetTitleStrategy: values.targetTitleStrategy,
        entryAlias: values.entryAlias,
      })
    },
    onSuccess: async (detail) => {
      setCreateOpen(false)
      createForm.resetFields()
      setSelectedItemId(detail.item.id)
      message.success('已创建')
      await queryClient.invalidateQueries({ queryKey: ['bases'] })
      await refresh(detail.item.id)
      if (detail.item.contentType === 'external_link') {
        navigate(directoryPath(detail.item.parentId ?? detail.item.id), { replace: true })
      } else if (detail.item.contentType === 'markdown') {
        navigate(contentPath(detail.item.id))
      } else if (detail.item.contentType === 'object_ref' && detail.item.targetObjectType === 'file' && detail.item.targetObjectId) {
        void getFileDownloadUrl(detail.item.targetObjectId)
          .then((response) => window.open(response.downloadUrl, '_blank', 'noopener,noreferrer'))
          .catch(() => message.error('文件打开失败'))
      } else if (detail.item.contentType === 'object_ref' && detail.item.targetObjectType !== 'base' && detail.item.targetRoute) {
        navigate(normalizeKnowledgePath(detail.item.targetRoute))
      } else if (detail.item.contentType === 'folder') {
        navigate(directoryPath(detail.item.id))
      }
    },
    onError: () => message.error('创建失败'),
  })

  const objectEntryMutation = useMutation({
    mutationFn: (values: ObjectEntryForm) => {
      if (!selectedItem?.id) throw new Error('Object entry is required')
      return updateKnowledgeObjectEntry(spaceId, selectedItem.id, values)
    },
    onSuccess: async (detail) => {
      setObjectEntryOpen(false)
      message.success('入口设置已更新')
      await refresh(detail.item.id)
    },
    onError: () => message.error('入口设置更新失败'),
  })

  const openObjectEntrySettings = () => {
    if (!selectedItem || selectedItem.contentType !== 'object_ref') return
    objectEntryForm.setFieldsValue({
      displayMode: selectedItem.displayMode,
      targetTitleStrategy: selectedItem.targetTitleStrategy,
      entryAlias: selectedItem.entryAlias ?? undefined,
    })
    setObjectEntryOpen(true)
  }

  const templateMutation = useMutation({
    mutationFn: (values: TemplateCreateForm) =>
      createKnowledgeBaseItemFromTemplate(spaceId, {
        templateId: values.templateId,
        parentId: normalizeParentId(values.parentId),
        title: values.title,
      }),
    onSuccess: async (detail) => {
      setTemplateOpen(false)
      templateForm.resetFields()
      setSelectedItemId(detail.item.id)
      message.success('已从模板创建')
      await refresh(detail.item.id)
      openItemOrDirectory(detail.item.id)
    },
    onError: () => message.error('模板创建失败'),
  })

  const moveMutation = useMutation({
    mutationFn: ({ itemId, parentId, sortOrder }: { itemId: string; parentId?: string | null; sortOrder?: number }) =>
      moveKnowledgeBaseItem(spaceId, itemId, { parentId, sortOrder }),
    onSuccess: async (detail) => {
      setMoveOpen(false)
      moveForm.resetFields()
      setSelectedItemId(detail.item.id)
      message.success('目录已更新')
      await refresh(detail.item.id)
      if (detail.item.contentType !== 'markdown') {
        navigate(directoryPath(detail.item.id), { replace: true })
      }
    },
    onError: () => message.error('移动或排序失败'),
  })

  const homeMutation = useMutation({
    mutationFn: (itemId: string) => updateKnowledgeBase(spaceId, { homeItemId: itemId }),
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
    mutationFn: ({ subscribed, targetType, targetId }: { subscribed: boolean; targetType: 'knowledge_base' | 'knowledge_content'; targetId?: string }) =>
      subscribed
        ? unsubscribeKnowledgeTarget(spaceId, { targetType, targetId })
        : subscribeKnowledgeTarget(spaceId, { targetType, targetId }),
    onSuccess: async () => {
      message.success('关注状态已更新')
      await queryClient.invalidateQueries({ queryKey: ['knowledge-bases', spaceId, 'discovery'] })
    },
    onError: () => message.error('关注状态更新失败'),
  })
  const openCreate = (contentType: CreateNodeForm['contentType'], targetObjectType?: string) => {
    createForm.setFieldsValue({
      contentType,
      parentId: selectedDirectoryId ?? rootItemId ?? undefined,
      displayMode: contentType === 'external_link' ? 'link' : targetObjectType === 'base' ? 'inline' : 'default',
      targetTitleStrategy: contentType === 'object_ref' ? 'alias' : 'manual',
      targetObjectType: contentType === 'external_link' ? 'external_link' : targetObjectType,
      targetObjectId: undefined,
      targetRoute: undefined,
      entryAlias: undefined,
      targetBaseMode: targetObjectType === 'base' ? 'existing' : undefined,
      targetBaseId: undefined,
      targetBaseTableId: undefined,
      targetBaseViewId: undefined,
      newBaseName: undefined,
      newBaseDescription: undefined,
    })
    setCreateOpen(true)
  }

  const openTemplate = () => {
    templateForm.setFieldsValue({ parentId: selectedDirectoryId ?? rootItemId ?? undefined })
    setTemplateOpen(true)
  }

  const openMove = () => {
    if (!selectedItem) {
      return
    }
    moveForm.setFieldsValue({ parentId: selectedItem.parentId ?? ROOT_PARENT_VALUE })
    setMoveOpen(true)
  }

  const reorderSelected = (direction: -1 | 1) => {
    if (!selectedItem || activeSiblingIndex < 0) {
      return
    }
    const target = activeSiblings[activeSiblingIndex + direction]
    if (!target) {
      return
    }
    moveMutation.mutate({
      itemId: selectedItem.id,
      parentId: selectedItem.parentId ?? null,
      sortOrder: target.sortOrder,
    })
  }

  const persistExpandedKeys = (keys: Key[]) => {
    const next = keys.map(String)
    setExpandedKeysBySpace((current) => ({ ...current, [spaceId]: next }))
    if (spaceId) {
      localStorage.setItem(expandedStorageKey(spaceId), JSON.stringify(next))
    }
  }

  useEffect(() => {
    if (!spaceId || spaceQuery.isLoading || treeQuery.isLoading) {
      return
    }
    if (queryItemId) {
      const queryItem = items.find((document) => document.id === queryItemId)
      if (!queryItem) {
        return
      }
      if (queryItem.contentType === 'markdown') {
        navigate(contentPath(queryItem.id), { replace: true })
      }
      return
    }
    if (queryState.view !== 'management' && !selectedItemId && homeItemId && items.some((document) => document.id === homeItemId)) {
      navigate(contentPath(homeItemId), { replace: true })
    }
  }, [
    contentPath,
    items,
    homeItemId,
    navigate,
    queryItemId,
    queryState.view,
    selectedItemId,
    spaceId,
    spaceQuery.isLoading,
    treeQuery.isLoading,
  ])

  useEffect(() => {
    const element = mainScrollRef.current
    if (!element || !spaceId) {
      return
    }
    const key = scrollStorageKey(spaceId, queryItemId ?? selectedItem?.id ?? 'home', queryState.view)
    const saved = sessionStorage.getItem(key)
    element.scrollTop = saved ? Number(saved) : 0
    const saveScroll = () => sessionStorage.setItem(key, String(element.scrollTop))
    element.addEventListener('scroll', saveScroll, { passive: true })
    return () => {
      saveScroll()
      element.removeEventListener('scroll', saveScroll)
    }
  }, [queryItemId, queryState.view, selectedItem?.id, spaceId])

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
          <Tooltip title="新建内容页">
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
              disabled={activeSiblingIndex <= 0 || selectedItem?.id === rootItemId}
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
                selectedItem?.id === rootItemId
              }
              loading={moveMutation.isPending}
              onClick={() => reorderSelected(1)}
            />
          </Tooltip>
          <Tooltip title="移动到">
            <Button
              icon={<FolderOpenOutlined />}
              disabled={!selectedItem || selectedItem.id === rootItemId}
              onClick={openMove}
            />
          </Tooltip>
          <Tooltip title="设为首页">
            <Button
              icon={<HomeOutlined />}
              disabled={!selectedItem || selectedItem.contentType !== 'markdown' || selectedItem.id === homeItemId}
              loading={homeMutation.isPending}
              onClick={() => selectedItem && homeMutation.mutate(selectedItem.id)}
            />
          </Tooltip>
        </Space>
        <div className="kb-tree-panel">
          <Tree
            blockNode
            expandedKeys={directorySearch.trim() ? undefined : expandedKeys}
            autoExpandParent={Boolean(directorySearch.trim())}
            defaultExpandAll={Boolean(directorySearch.trim())}
            selectedKeys={selectedItem?.id ? [selectedItem.id] : []}
            treeData={treeData}
            onExpand={persistExpandedKeys}
            onSelect={(keys) => {
              const key = String(keys[0] ?? '')
              if (key) {
                openItemOrDirectory(key)
              }
            }}
          />
          {!treeQuery.isLoading && treeData.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无目录" /> : null}
        </div>
      </aside>

      <main className="kb-detail-main" ref={mainScrollRef}>
        <section className="kb-content-first-card">
          <Space align="start" className="kb-content-first-heading">
            <span className="kb-content-first-icon">
              {nodeIcon(selectedItem)}
            </span>
            <div>
              <Space wrap>
                <Typography.Title level={2}>{selectedItem?.title ?? space?.name ?? '知识库'}</Typography.Title>
                {selectedItem ? <Tag>{contentTypeText(selectedItem.contentType)}</Tag> : null}
                {space ? <Tag color={statusColor(space.status)}>{statusText(space.status)}</Tag> : null}
              </Space>
              <Typography.Paragraph type="secondary">
                {queryItemMissing
                  ? '目标节点不存在、已被移除或当前账号无法访问。'
                  : selectedItem?.description ||
                  (selectedItem
                    ? `更新于 ${formatDate(selectedItem.updatedAt)}`
                    : homeItemId
                      ? '正在打开知识库首页内容...'
                      : '这个知识库还没有首页内容，请创建内容页或选择目录。')}
              </Typography.Paragraph>
              <Space wrap size={6}>
                {selectedItem ? <Tag>{permissionText(selectedItem.permissionLevel)}</Tag> : null}
                {selectedItem?.archived ? <Tag color="default">已归档</Tag> : null}
                {selectedItem ? <Tag>子内容 {selectedDirectoryChildren.length}</Tag> : null}
                {space ? <Tag>{visibilityText(space.visibility)}</Tag> : null}
              </Space>
            </div>
          </Space>
          <Space wrap>
            {homeItemId ? (
              <Button icon={<HomeOutlined />} onClick={() => openItemOrDirectory(homeItemId)}>
                打开首页
              </Button>
            ) : null}
            {selectedItem?.contentType === 'markdown' ? (
              <Button type="primary" icon={<FileTextOutlined />} onClick={() => openItemOrDirectory(selectedItem.id)}>
                打开正文
              </Button>
            ) : (
              <Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate('markdown')}>
                新建内容页
              </Button>
            )}
            <Button icon={<FolderOutlined />} onClick={() => openCreate('folder')}>
              新建目录
            </Button>
            <Button icon={<AppstoreOutlined />} onClick={() => openCreate('object_ref')}>
              挂载对象
            </Button>
            <Button icon={<TableOutlined />} onClick={() => openCreate('object_ref', 'base')}>
              多维表格
            </Button>
            <Button icon={<LinkOutlined />} onClick={() => openCreate('external_link')}>
              外部链接
            </Button>
          </Space>
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
                { value: 'markdown', label: '内容页' },
                { value: 'folder', label: '目录' },
                { value: 'space', label: '空间' },
                { value: 'object_ref', label: '对象入口' },
                { value: 'external_link', label: '外部链接' },
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
              onOpen={(path) => navigate(normalizeKnowledgePath(path))}
              onCreate={() => openCreate('markdown')}
              onClear={() => {
                setKbSearchText('')
                setKbSearchDraft('')
              }}
            />
          ) : null}
        </section>

        <section className="kb-directory-content-card">
          <Space className="kb-directory-toolbar" align="center" wrap>
            <div>
              <Typography.Title level={4}>{selectedItem?.contentType === 'markdown' ? '当前内容' : '目录内容'}</Typography.Title>
              <Typography.Text type="secondary">
                {selectedItem?.contentType === 'markdown' ? '内容页会直接进入正文编辑/阅读路由；这里保留为刷新恢复兜底。' : '点击内容页直接打开正文；目录节点继续展示子内容。'}
              </Typography.Text>
            </div>
            <Space wrap>
              <Button icon={<FileTextOutlined />} onClick={() => openCreate('markdown')}>新建内容页</Button>
              <Button icon={<FileTextOutlined />} onClick={openTemplate}>从模板创建</Button>
              <Button icon={<AppstoreOutlined />} onClick={() => openCreate('object_ref')}>挂载对象</Button>
              <Button icon={<TableOutlined />} onClick={() => openCreate('object_ref', 'base')}>多维表格</Button>
              <Button icon={<LinkOutlined />} onClick={() => openCreate('external_link')}>外部链接</Button>
            </Space>
          </Space>
          {queryItemMissing ? (
            <div className="kb-directory-empty">
              <Empty description="节点不存在或已无权限" />
              <Space wrap>
                {homeItemId ? <Button type="primary" onClick={() => openItemOrDirectory(homeItemId)}>返回首页内容</Button> : null}
                <Button onClick={() => navigate(`/knowledge-bases/${spaceId}`)}>返回知识库</Button>
              </Space>
            </div>
          ) : selectedItem?.contentType === 'markdown' ? (
            <div className="kb-content-fallback">
              <FileTextOutlined />
              <Typography.Title level={4}>{selectedItem.title}</Typography.Title>
              <Typography.Paragraph type="secondary">如未自动跳转，可直接打开正文内容。</Typography.Paragraph>
              <Button type="primary" onClick={() => openItemOrDirectory(selectedItem.id)}>打开正文</Button>
            </div>
          ) : isBaseObjectEntry(selectedItem) ? (
            <KnowledgeBaseBasePreview document={selectedItem!} onOpenFull={(path) => navigate(path)} />
          ) : selectedItem?.contentType === 'object_ref' ? (
            <div className="kb-content-fallback">
              <span className="kb-directory-item-icon">{nodeIcon(selectedItem)}</span>
              <Typography.Title level={4}>{selectedItem.title}</Typography.Title>
              <Space wrap>
                <Tag>{targetObjectTypeText(selectedItem.targetObjectType)}</Tag>
                <Tag>{displayModeText(selectedItem.displayMode)}</Tag>
                <Tag>{titleStrategyText(selectedItem.targetTitleStrategy)}</Tag>
                {selectedItem.targetSummary ? <Tag color={selectedItem.targetSummary.accessState === 'available' ? 'green' : 'default'}>{targetAccessText(selectedItem.targetSummary.accessState)}</Tag> : null}
              </Space>
              <Typography.Paragraph type="secondary">{selectedItem.targetSummary?.subtitle || '协作对象入口'}</Typography.Paragraph>
              <Space>
                <Button type="primary" disabled={selectedItem.targetSummary?.accessState !== 'available'} onClick={() => openItemOrDirectory(selectedItem.id)}>打开对象</Button>
                <Button onClick={openObjectEntrySettings}>编辑入口</Button>
              </Space>
            </div>
          ) : selectedDirectoryChildren.length > 0 ? (
            <div className="kb-directory-grid">
              {selectedDirectoryChildren.map((document) => (
                <button className="kb-directory-item" key={document.id} type="button" onClick={() => openItemOrDirectory(document.id)}>
                  <span className="kb-directory-item-icon">{nodeIcon(document)}</span>
                  <span>
                    <strong>{document.title}</strong>
                    <small>{nodeMetaText(document)} · 更新 {formatDate(document.updatedAt)}</small>
                  </span>
                  {document.targetSummary && document.targetSummary.accessState !== 'available' ? <Tag color="default">{targetAccessText(document.targetSummary.accessState)}</Tag> : null}
                  <Tag>{permissionText(document.permissionLevel)}</Tag>
                </button>
              ))}
            </div>
          ) : (
            <div className="kb-directory-empty">
              <Empty description="这个目录暂无内容" />
              <Space wrap>
                <Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate('markdown')}>创建内容页</Button>
                <Button icon={<FolderOutlined />} onClick={() => openCreate('folder')}>创建子目录</Button>
                <Button icon={<AppstoreOutlined />} onClick={() => openCreate('object_ref')}>挂载对象</Button>
                <Button icon={<TableOutlined />} onClick={() => openCreate('object_ref', 'base')}>挂载多维表格</Button>
              </Space>
            </div>
          )}
        </section>

        <details
          className="kb-management-details"
          open={queryState.view === 'management'}
          onToggle={(event) => setManagementView((event.currentTarget as HTMLDetailsElement).open)}
        >
          <summary>
            <span>空间设置</span>
            <Typography.Text type="secondary">关注、权限和当前节点操作，不展示后台治理仪表盘</Typography.Text>
          </summary>
          <section className="kb-management-summary-grid">
            <div className="kb-selected-summary">
              <Tag color="blue">当前知识库</Tag>
              <Typography.Title level={4}>{space?.name ?? '知识库'}</Typography.Title>
              <Typography.Paragraph type="secondary">{space?.description || '暂无描述'}</Typography.Paragraph>
              <Space wrap>
                <Tag>维护人 {space?.ownerName ?? '-'}</Tag>
                <Tag>{space ? permissionText(space.defaultPermissionLevel) : '-'}</Tag>
                <Tag>{space ? visibilityText(space.visibility) : '-'}</Tag>
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
              </Space>
            </div>
            <div className="kb-selected-summary">
              <Tag>{selectedItem ? contentTypeText(selectedItem.contentType) : '未选择'}</Tag>
              <Typography.Title level={4}>{selectedItem?.title ?? '选择一个目录节点'}</Typography.Title>
              <Space wrap size={6}>
                {selectedItem ? <Tag>{permissionText(selectedItem.permissionLevel)}</Tag> : null}
                {selectedItem?.archived ? <Tag color="default">已归档</Tag> : null}
                {selectedItem ? <Tag>更新 {formatDate(selectedItem.updatedAt)}</Tag> : null}
              </Space>
              {selectedItem ? (
                <Space wrap>
                  <Button
                    icon={<BellOutlined />}
                    loading={subscriptionMutation.isPending}
                    onClick={() =>
                      selectedItem &&
                      subscriptionMutation.mutate({
                        subscribed: (discoveryQuery.data?.subscribedItems ?? []).some((document) => document.id === selectedItem.id),
                        targetType: 'knowledge_content',
                        targetId: selectedItem.id,
                      })
                    }
                  >
                    {(discoveryQuery.data?.subscribedItems ?? []).some((document) => document.id === selectedItem.id) ? '取消关注' : '关注节点'}
                  </Button>
                  <Button
                    icon={<SafetyCertificateOutlined />}
                    onClick={() =>
                      setPermissionTarget({ resourceType: 'knowledge_content', resourceId: selectedItem.id, resourceName: `${selectedItem.title} 节点权限` })
                    }
                  >
                    节点权限
                  </Button>
                </Space>
              ) : null}
              {selectedItem?.contentType === 'markdown' ? (
                <CollaborationHealthCard
                  dirty={Boolean(collaborationHealthQuery.data?.dirty)}
                  onlineCount={collaborationHealthQuery.data?.activeUsers ?? 0}
                  serverClock={collaborationHealthQuery.data?.serverClock ?? 0}
                  lastSavedAt={collaborationHealthQuery.data?.lastSavedAt}
                  onOpen={() => openItemOrDirectory(selectedItem.id)}
                  onRefresh={() => collaborationHealthQuery.refetch()}
                />
              ) : null}
            </div>
          </section>
        </details>

        <section className="kb-section-grid kb-discovery-grid">
          <KnowledgeList title="置顶" items={pinnedDocuments} onOpen={openItemOrDirectory} onSelect={openItemOrDirectory} />
          <KnowledgeList title="最近访问" items={discoveryQuery.data?.recentAccessed ?? recentItems} onOpen={openItemOrDirectory} onSelect={openItemOrDirectory} />
          <KnowledgeList title="我维护的内容" items={discoveryQuery.data?.maintainedByMe ?? []} onOpen={openItemOrDirectory} onSelect={openItemOrDirectory} />
          <KnowledgeList title="待复核" items={discoveryQuery.data?.dueForReview ?? []} onOpen={openItemOrDirectory} onSelect={openItemOrDirectory} />
          <KnowledgeList title="收藏" items={discoveryQuery.data?.favorites ?? favoriteItems} onOpen={openItemOrDirectory} onSelect={openItemOrDirectory} />
          <KnowledgeList title="热门知识" items={discoveryQuery.data?.popular ?? []} onOpen={openItemOrDirectory} onSelect={openItemOrDirectory} />
          <KnowledgeList title="推荐阅读" items={discoveryQuery.data?.recommended ?? []} onOpen={openItemOrDirectory} onSelect={openItemOrDirectory} />
          <KnowledgeList title="我的关注" items={discoveryQuery.data?.subscribedItems ?? []} onOpen={openItemOrDirectory} onSelect={openItemOrDirectory} />
          <KnowledgeList title="常用目录" items={commonDirectories} onOpen={openItemOrDirectory} onSelect={openItemOrDirectory} />
          <TemplateList templates={templatesQuery.data ?? []} onUse={(template) => {
            templateForm.setFieldsValue({
              templateId: template.id,
              title: template.title,
              parentId: selectedDirectoryId ?? rootItemId ?? undefined,
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
          <Form.Item name="contentType" label="类型" rules={[{ required: true }]}>
            <Select options={[
              { value: 'markdown', label: '内容页' },
              { value: 'folder', label: '目录' },
              { value: 'object_ref', label: '对象入口' },
              { value: 'external_link', label: '外部链接' },
            ]} />
          </Form.Item>
          <Form.Item name="parentId" label="位置">
            <Select options={directoryOptions} />
          </Form.Item>
          {createContentType === 'object_ref' ? (
            <>
              <Form.Item name="targetObjectType" label="目标类型" rules={[{ required: true, message: '请选择目标类型' }]}>
                <Select
                  options={[
                    { value: 'knowledge_content', label: '知识内容' },
                    { value: 'base', label: '多维表格' },
                    { value: 'file', label: '文件' },
                    { value: 'project', label: '项目' },
                  ]}
                  onChange={(value) => {
                    createForm.setFieldsValue({
                      targetBaseMode: value === 'base' ? 'existing' : undefined,
                      displayMode: value === 'base' ? 'inline' : 'default',
                      targetObjectId: undefined,
                      targetRoute: undefined,
                      targetBaseId: undefined,
                      targetBaseTableId: undefined,
                      targetBaseViewId: undefined,
                    })
                  }}
                />
              </Form.Item>
              {createTargetObjectType === 'base' ? (
                <>
                  <Form.Item name="targetBaseMode" label="多维表格来源" rules={[{ required: true, message: '请选择来源' }]}>
                    <Select options={[
                      { value: 'existing', label: '选择已有多维表格' },
                      { value: 'new', label: '新建多维表格并挂载' },
                    ]} />
                  </Form.Item>
                  {createTargetBaseMode !== 'new' ? (
                    <>
                      <Form.Item name="targetBaseId" label="选择多维表格" rules={[{ required: true, message: '请选择多维表格' }]}>
                        <PlatformObjectPicker
                          objectTypes={['base']}
                          onChange={(baseId, base) => {
                            const currentTitle = createForm.getFieldValue('title')
                            createForm.setFieldsValue({
                              targetObjectId: baseId,
                              title: currentTitle || base?.title,
                              entryAlias: createForm.getFieldValue('entryAlias') || currentTitle || base?.title,
                              targetBaseTableId: undefined,
                              targetBaseViewId: undefined,
                            })
                          }}
                        />
                      </Form.Item>
                      <Form.Item name="targetBaseTableId" label="默认数据表">
                        <Select
                          allowClear
                          loading={createTargetBaseQuery.isLoading}
                          options={(createTargetBaseQuery.data?.tables ?? []).map((table) => ({
                            value: table.id,
                            label: `${table.name} / ${table.recordCount} 记录`,
                          }))}
                          onChange={(tableId) => {
                            const baseId = createForm.getFieldValue('targetBaseId')
                            createForm.setFieldsValue({
                              targetRoute: baseId ? baseTargetRoute(baseId, tableId) : undefined,
                              targetBaseViewId: undefined,
                            })
                          }}
                        />
                      </Form.Item>
                      <Form.Item name="targetBaseViewId" label="默认视图">
                        <Select
                          allowClear
                          disabled={!createTargetBaseTableId}
                          loading={createTargetBaseTableQuery.isLoading}
                          options={(createTargetBaseTableQuery.data?.views ?? []).map((view) => ({
                            value: view.id,
                            label: view.name,
                          }))}
                          onChange={(viewId) => {
                            const baseId = createForm.getFieldValue('targetBaseId')
                            const tableId = createForm.getFieldValue('targetBaseTableId')
                            createForm.setFieldsValue({ targetRoute: baseId ? baseTargetRoute(baseId, tableId, viewId) : undefined })
                          }}
                        />
                      </Form.Item>
                    </>
                  ) : (
                    <>
                      <Form.Item name="newBaseName" label="新多维表格名称" rules={[{ required: true, message: '请输入多维表格名称' }]}>
                        <Input maxLength={128} />
                      </Form.Item>
                      <Form.Item name="newBaseDescription" label="说明">
                        <Input.TextArea maxLength={512} rows={3} />
                      </Form.Item>
                    </>
                  )}
                </>
              ) : (
                <Form.Item name="targetObjectId" label="选择目标对象" rules={[{ required: true, message: '请选择目标对象' }]}>
                  <PlatformObjectPicker
                    key={createTargetObjectType}
                    objectTypes={createTargetObjectType ? [createTargetObjectType] : []}
                    onChange={(objectId, object) => {
                      const currentTitle = createForm.getFieldValue('title')
                      createForm.setFieldsValue({
                        targetObjectId: objectId,
                        title: currentTitle || object?.title,
                        entryAlias: createForm.getFieldValue('entryAlias') || currentTitle || object?.title,
                      })
                    }}
                  />
                </Form.Item>
              )}
              <Form.Item name="displayMode" label="展示方式">
                <Select options={[
                  { value: 'default', label: '默认' },
                  { value: 'inline', label: '嵌入预览' },
                  { value: 'preview', label: '预览卡片' },
                  { value: 'link', label: '链接' },
                ]} />
              </Form.Item>
              <Form.Item name="targetTitleStrategy" label="标题策略">
                <Select options={[
                  { value: 'manual', label: '手动标题' },
                  { value: 'alias', label: '入口别名' },
                  { value: 'follow_target', label: '跟随目标' },
                ]} />
              </Form.Item>
              <Form.Item name="entryAlias" label="入口别名">
                <Input maxLength={255} />
              </Form.Item>
            </>
          ) : null}
          {createContentType === 'external_link' ? (
            <>
              <Form.Item name="targetRoute" label="链接地址" rules={[{ required: true, message: '请输入链接地址' }]}>
                <Input placeholder="https://example.com" maxLength={1024} />
              </Form.Item>
              <Form.Item name="displayMode" label="打开方式">
                <Select options={[
                  { value: 'link', label: '链接打开' },
                  { value: 'preview', label: '预览卡片' },
                ]} />
              </Form.Item>
            </>
          ) : null}
        </Form>
      </Modal>

      <Modal
        title="编辑对象入口"
        open={objectEntryOpen}
        onCancel={() => setObjectEntryOpen(false)}
        onOk={() => objectEntryForm.submit()}
        confirmLoading={objectEntryMutation.isPending}
        destroyOnClose
      >
        <Form form={objectEntryForm} layout="vertical" onFinish={(values) => objectEntryMutation.mutate(values)}>
          <Form.Item name="displayMode" label="展示方式">
            <Select options={[
              { value: 'default', label: '默认' },
              { value: 'inline', label: '嵌入预览' },
              { value: 'preview', label: '预览卡片' },
              { value: 'link', label: '链接' },
            ]} />
          </Form.Item>
          <Form.Item name="targetTitleStrategy" label="标题策略">
            <Select options={[
              { value: 'manual', label: '手动标题' },
              { value: 'alias', label: '入口别名' },
              { value: 'follow_target', label: '跟随目标' },
            ]} />
          </Form.Item>
          <Form.Item name="entryAlias" label="入口别名">
            <Input maxLength={255} />
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
            if (!selectedItem) {
              return
            }
            moveMutation.mutate({
              itemId: selectedItem.id,
              parentId: normalizeParentId(values.parentId),
              sortOrder: selectedItem.sortOrder,
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
          <Typography.Text type="secondary">异常时可刷新状态、进入内容页重连，或按只读方式查看最近已保存版本。</Typography.Text>
          <Space size={6}>
            <Button size="small" onClick={onRefresh}>刷新状态</Button>
            <Button size="small" type="primary" onClick={onOpen}>打开重连</Button>
          </Space>
        </Space>
      }
    />
  )
}

function KnowledgeBaseBasePreview({ document, onOpenFull }: { document: KnowledgeBaseItem; onOpenFull: (path: string) => void }) {
  const target = parseBaseTargetRoute(document)
  const baseId = document.targetObjectId ?? target.baseId
  const canOpen = !document.targetSummary || document.targetSummary.accessState === 'available'
  const baseQuery = useQuery({
    queryKey: ['bases', baseId, 'kb-preview'],
    queryFn: () => getBase(baseId || ''),
    enabled: Boolean(baseId && canOpen),
  })
  const activeTableId = target.tableId ?? baseQuery.data?.tables[0]?.id ?? null
  const tableQuery = useQuery({
    queryKey: ['bases', baseId, 'tables', activeTableId, 'kb-preview'],
    queryFn: () => getTable(baseId || '', activeTableId || ''),
    enabled: Boolean(baseId && activeTableId && canOpen),
  })
  const activeView = tableQuery.data?.views.find((view) => view.id === target.viewId)
  const recordsQuery = useQuery({
    queryKey: ['bases', baseId, 'tables', activeTableId, 'records', 'kb-preview', activeView?.id],
    queryFn: () =>
      queryRecords(baseId || '', activeTableId || '', {
        filters: activeView?.filters ?? [],
        sorts: activeView?.sorts ?? [],
        limit: 20,
        offset: 0,
      }),
    enabled: Boolean(baseId && activeTableId && canOpen),
  })

  if (!canOpen) {
    return (
      <div className="kb-base-preview kb-base-preview-unavailable">
        <Empty description={document.targetSummary ? targetAccessText(document.targetSummary.accessState) : '目标不可访问'} />
      </div>
    )
  }

  const base = baseQuery.data?.base
  const table = tableQuery.data?.table
  const fields = visibleBaseFields(tableQuery.data?.fields ?? [], activeView?.visibleFieldIds ?? [])
  const rows = recordsQuery.data?.items ?? []
  const columns = [
    {
      title: '#',
      dataIndex: 'recordNo',
      key: 'recordNo',
      width: 72,
      render: (value: number) => <Tag>#{value}</Tag>,
    },
    ...fields.map((field) => ({
      title: field.name,
      dataIndex: field.id,
      key: field.id,
      width: 180,
      render: (_value: unknown, record: BaseRecord) => renderBaseValue(field, record),
    })),
    {
      title: '更新',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 170,
      render: (value: string) => formatDate(value),
    },
  ]

  return (
    <div className="kb-base-preview">
      <div className="kb-base-preview-header">
        <Space align="start">
          <span className="kb-base-preview-icon">
            <TableOutlined />
          </span>
          <div>
            <Space wrap size={6}>
              <Typography.Title level={4}>{base?.name ?? document.title}</Typography.Title>
              <Tag color="purple">多维表格</Tag>
              {activeView ? <Tag>{activeView.name}</Tag> : null}
              {base ? <Tag>{permissionText(base.permissionLevel)}</Tag> : null}
            </Space>
            <Typography.Text type="secondary">
              {base?.description || document.targetSummary?.subtitle || 'Base 数据仍归多维表格模块，知识库仅提供目录入口和上下文预览。'}
            </Typography.Text>
          </div>
        </Space>
        <Space wrap>
          {base ? <Tag>{base.tableCount} 表</Tag> : null}
          {base ? <Tag>{base.recordCount} 记录</Tag> : null}
          <Button onClick={() => onOpenFull(document.targetRoute || (baseId ? `/bases/${baseId}` : '/bases'))}>打开完整表格</Button>
        </Space>
      </div>
      <div className="kb-base-preview-tabs">
        {(baseQuery.data?.tables ?? []).map((item) => (
          <Tag color={item.id === activeTableId ? 'blue' : 'default'} key={item.id}>
            {item.name} · {item.recordCount}
          </Tag>
        ))}
      </div>
      {table ? (
        <Table
          className="kb-base-preview-table"
          rowKey="id"
          size="middle"
          columns={columns}
          dataSource={rows}
          loading={baseQuery.isLoading || tableQuery.isLoading || recordsQuery.isLoading}
          pagination={false}
          scroll={{ x: Math.max(620, fields.length * 180 + 260), y: 360 }}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No data" /> }}
        />
      ) : (
        <div className="kb-directory-empty">
          <Empty description={baseQuery.isLoading ? '多维表格加载中...' : '这个多维表格还没有数据表'} />
        </div>
      )}
    </div>
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
          <Button type="primary" onClick={onCreate}>创建内容页</Button>
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
  items: KnowledgeBaseItem[]
  onOpen: (itemId: string) => void
  onSelect: (itemId: string) => void
}) {
  return (
    <div className="kb-panel">
      <Typography.Title level={5}>{title}</Typography.Title>
      <Space orientation="vertical" size={6} className="kb-list-panel">
        {items.map((document) => (
          <button className="kb-list-item" type="button" key={document.id} onClick={() => onSelect(document.id)} onDoubleClick={() => onOpen(document.id)}>
            {nodeIcon(document)}
            <span>
              <strong>{document.title}</strong>
              <small>{nodeMetaText(document)} · {formatDate(document.updatedAt)}</small>
            </span>
          </button>
        ))}
        {items.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无内容" /> : null}
      </Space>
    </div>
  )
}

function TemplateList({ templates, onUse }: { templates: KnowledgeContentTemplate[]; onUse: (template: KnowledgeContentTemplate) => void }) {
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
  nodes: KnowledgeBaseItemTreeNode[],
  search: string,
  activeItemId: string | null,
  favoriteItemIds: Set<string>,
): DataNode[] {
  const normalizedSearch = search.trim().toLowerCase()
  return nodes
    .map((node) => buildKbTreeNode(node, normalizedSearch, activeItemId, favoriteItemIds))
    .filter(Boolean) as DataNode[]
}

function buildKbTreeNode(
  node: KnowledgeBaseItemTreeNode,
  search: string,
  activeItemId: string | null,
  favoriteItemIds: Set<string>,
): DataNode | null {
  const childNodes = node.children
    .map((child) => buildKbTreeNode(child, search, activeItemId, favoriteItemIds))
    .filter(Boolean) as DataNode[]
  const matched = !search || node.item.title.toLowerCase().includes(search)
  if (!matched && childNodes.length === 0) {
    return null
  }
  return {
    key: node.item.id,
    title: (
      <span className={`kb-tree-title${node.item.id === activeItemId ? ' active' : ''}${node.item.archived ? ' archived' : ''}`}>
        {nodeIcon(node.item)}
        <span>{node.item.title}</span>
        <Tag>{contentTypeText(node.item.contentType)}</Tag>
        {node.item.targetObjectType ? <Tag color="blue">{targetObjectTypeText(node.item.targetObjectType)}</Tag> : null}
        {node.item.targetSummary && node.item.targetSummary.accessState !== 'available' ? <Tag color="default">{targetAccessText(node.item.targetSummary.accessState)}</Tag> : null}
        <Tag>{permissionText(node.item.permissionLevel)}</Tag>
        {favoriteItemIds.has(node.item.id) ? <StarFilled className="kb-tree-star" /> : null}
        {node.item.archived ? <Tag color="default">归档</Tag> : null}
        <small>{formatDate(node.item.updatedAt)}</small>
      </span>
    ) as ReactNode,
    children: childNodes,
  }
}

function findTreeNode(nodes: KnowledgeBaseItemTreeNode[], itemId: string): KnowledgeBaseItemTreeNode | null {
  for (const node of nodes) {
    if (node.item.id === itemId) {
      return node
    }
    const child = findTreeNode(node.children, itemId)
    if (child) {
      return child
    }
  }
  return null
}

function flattenTree(root: KnowledgeBaseItemTreeNode | null): KnowledgeBaseItemTreeNode[] {
  if (!root) {
    return []
  }
  return [root, ...root.children.flatMap(flattenTree)]
}

function knowledgeItemSort(left: KnowledgeBaseItem, right: KnowledgeBaseItem) {
  if (left.sortOrder !== right.sortOrder) {
    return left.sortOrder - right.sortOrder
  }
  return left.title.localeCompare(right.title)
}

function isDescendantDocument(items: KnowledgeBaseItem[], ancestorId: string, candidateId: string) {
  let current = items.find((document) => document.id === candidateId)
  while (current?.parentId) {
    if (current.parentId === ancestorId) {
      return true
    }
    current = items.find((document) => document.id === current?.parentId)
  }
  return false
}

function normalizeParentId(value?: string | null) {
  return !value || value === ROOT_PARENT_VALUE ? null : value
}

function isBaseObjectEntry(document?: KnowledgeBaseItem | null) {
  return document?.contentType === 'object_ref' && document.targetObjectType === 'base'
}

function baseTargetRoute(baseId: string, tableId?: string, viewId?: string) {
  const path = tableId ? `/bases/${baseId}/tables/${tableId}` : `/bases/${baseId}`
  if (!viewId) {
    return path
  }
  return `${path}?${new URLSearchParams({ viewId }).toString()}`
}

function parseBaseTargetRoute(document: KnowledgeBaseItem) {
  const route = document.targetRoute ?? ''
  const [path, search = ''] = route.split('?')
  const match = path.match(/^\/bases\/([^/?#]+)(?:\/tables\/([^/?#]+))?/)
  const params = new URLSearchParams(search)
  return {
    baseId: match?.[1],
    tableId: match?.[2],
    viewId: params.get('viewId') ?? undefined,
  }
}

function visibleBaseFields(fields: BaseField[], visibleFieldIds: string[]) {
  if (visibleFieldIds.length === 0) {
    return fields
  }
  const visible = fields.filter((field) => visibleFieldIds.includes(field.id))
  return visible.length > 0 ? visible : fields
}

function renderBaseValue(field: BaseField, record: BaseRecord) {
  const value = record.values[field.id] ?? record.values[field.name]
  if (value == null || value === '') {
    return <Typography.Text type="secondary">-</Typography.Text>
  }
  if (Array.isArray(value)) {
    return (
      <Space size={4} wrap>
        {value.map((item) => <Tag key={String(item)}>{String(item)}</Tag>)}
      </Space>
    )
  }
  if (typeof value === 'object') {
    if ('title' in value && value.title) {
      return <Tag>{String(value.title)}</Tag>
    }
    if ('objectType' in value && 'objectId' in value) {
      return <Tag>{`${String(value.objectType)}:${String(value.objectId).slice(0, 8)}`}</Tag>
    }
    return <Typography.Text>{JSON.stringify(value)}</Typography.Text>
  }
  if (['single_select', 'multi_select', 'status', 'member', 'object_link'].includes(field.fieldType)) {
    return <Tag>{String(value)}</Tag>
  }
  return <Typography.Text>{String(value)}</Typography.Text>
}

function expandedStorageKey(spaceId: string) {
  return `kb-expanded-${spaceId}`
}

function scrollStorageKey(spaceId: string, itemId: string, view: string) {
  return `knowledge-content-scroll-${spaceId}-${itemId}-${view}`
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

function contentTypeText(contentType: KnowledgeBaseItem['contentType']) {
  return { space: '根目录', folder: '目录', markdown: '内容页', object_ref: '对象入口', external_link: '外部链接' }[contentType]
}

function targetObjectTypeText(targetObjectType?: string | null) {
  return {
    knowledge_content: '知识内容',
    document: '知识内容',
    base: '多维表格',
    file: '文件',
    project: '项目',
    external_link: '外链',
  }[targetObjectType ?? ''] ?? targetObjectType
}

function nodeMetaText(document: KnowledgeBaseItem) {
  const target = targetObjectTypeText(document.targetObjectType)
  const targetTitle = document.targetSummary?.accessState === 'available' && document.targetSummary.title ? ` · ${document.targetSummary.title}` : ''
  return target ? `${contentTypeText(document.contentType)} · ${target}${targetTitle}` : contentTypeText(document.contentType)
}

function nodeIcon(document?: KnowledgeBaseItem | null) {
  if (!document) {
    return <BookOutlined />
  }
  if (document.contentType === 'markdown') {
    return <FileTextOutlined />
  }
  if (document.contentType === 'object_ref') {
    if (document.targetObjectType === 'base') {
      return <TableOutlined />
    }
    return <AppstoreOutlined />
  }
  if (document.contentType === 'external_link') {
    return <LinkOutlined />
  }
  return <FolderOutlined />
}

function targetAccessText(accessState?: string | null) {
  return {
    forbidden: '目标无权限',
    disabled: '目标已停用',
    deleted: '目标已删除',
    not_found: '目标不存在',
    invalid: '目标无效',
    available: '目标可访问',
  }[accessState ?? ''] ?? '目标不可访问'
}

function displayModeText(displayMode?: string | null) {
  return { default: '默认展示', inline: '嵌入预览', preview: '预览卡片', link: '链接入口' }[displayMode ?? ''] ?? '默认展示'
}

function titleStrategyText(strategy?: string | null) {
  return { manual: '手动标题', alias: '入口别名', follow_target: '跟随目标' }[strategy ?? ''] ?? '手动标题'
}

function categoryText(category: string) {
  return {
    meeting: '会议纪要',
    requirement: '需求说明',
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

function formatDate(value?: string | null) {
  if (!value) {
    return '-'
  }
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit' }).format(new Date(value))
}
