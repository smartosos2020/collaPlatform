import {
  AppstoreAddOutlined,
  ArrowDownOutlined,
  ArrowUpOutlined,
  BoldOutlined,
  CheckSquareOutlined,
  CodeOutlined,
  CommentOutlined,
  CopyOutlined,
  DeleteOutlined,
  FileImageOutlined,
  FileOutlined,
  HolderOutlined,
  ItalicOutlined,
  LinkOutlined,
  MoreOutlined,
  OrderedListOutlined,
  PlusOutlined,
  RedoOutlined,
  SaveOutlined,
  ShareAltOutlined,
  StrikethroughOutlined,
  TableOutlined,
  UndoOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons'
import { Extension, Mark, type Extensions } from '@tiptap/core'
import Collaboration from '@tiptap/extension-collaboration'
import CollaborationCaret from '@tiptap/extension-collaboration-caret'
import DragHandle from '@tiptap/extension-drag-handle-react'
import Image from '@tiptap/extension-image'
import { Table } from '@tiptap/extension-table'
import TableCell from '@tiptap/extension-table-cell'
import TableHeader from '@tiptap/extension-table-header'
import TableRow from '@tiptap/extension-table-row'
import TaskItem from '@tiptap/extension-task-item'
import TaskList from '@tiptap/extension-task-list'
import {
  EditorContent,
  Node as TiptapNode,
  NodeViewWrapper,
  ReactNodeViewRenderer,
  mergeAttributes,
  type Editor,
  type JSONContent,
  type NodeViewProps,
  useEditor,
} from '@tiptap/react'
import { BubbleMenu } from '@tiptap/react/menus'
import type { Node as ProseMirrorNode } from '@tiptap/pm/model'
import { Plugin, PluginKey, TextSelection } from '@tiptap/pm/state'
import { Decoration, DecorationSet } from '@tiptap/pm/view'
import StarterKit from '@tiptap/starter-kit'
import { useQuery } from '@tanstack/react-query'
import { Alert, Avatar, Button, Dropdown, Input, Modal, Progress, Select, Space, Tag, Tooltip, Typography } from 'antd'
import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react'

import { uploadFileForTarget, getFileDownloadUrl, getFileMetadata } from '../../../files/api/filesApi'
import { ObjectSummaryCard } from '../../../platform/components/InternalLinkCard'
import { getObjectNavigation, resolveInternalLink, type PlatformObjectSummary } from '../../../platform/api/platformObjectsApi'
import { objectTypeText } from '../../../platform/objectTypeLabels'
import type { KnowledgeContentRealtimeSession } from '../hooks/useKnowledgeContentRealtimeCollaboration'

export type KnowledgeContentEditorCoreProps = {
  itemId: string
  title: string
  document: JSONContent
  versionNo: number
  permissionLevel: string
  updatedAt: string
  canEdit: boolean
  canManage: boolean
  dirty: boolean
  saving: boolean
  conflictVisible: boolean
  saveState?: KnowledgeContentSaveState
  saveActionLabel?: string
  saveActionHint?: string
  saveActionDisabled?: boolean
  commentAnchors?: KnowledgeContentEditorCommentAnchor[]
  blockAnchors?: KnowledgeContentEditorBlockAnchor[]
  activeCommentId?: string | null
  onTitleChange: (value: string) => void
  onDocumentChange: (document: JSONContent) => void
  onCommentAnchorChange?: (anchor?: KnowledgeContentEditorSelectionAnchor) => void
  onSave: () => void
  onRefresh: () => void
  onOpenLocalDraft: () => void
  onOpenPermission: () => void
  onOpenRelation: () => void
  collaboration?: KnowledgeContentRealtimeSession
}

export type KnowledgeContentSaveState = 'clean' | 'dirty' | 'saving' | 'saved' | 'offline' | 'conflict' | 'error'

export type KnowledgeContentEditorCommentAnchor = {
  id: string
  anchorStart?: number | null
  anchorEnd?: number | null
  anchorText?: string | null
  resolved?: boolean
}

export type KnowledgeContentEditorBlockAnchor = {
  id: string
  anchorId?: string | null
}

export type KnowledgeContentEditorSelectionAnchor = {
  anchorType: 'selection'
  anchorStart: number
  anchorEnd: number
  anchorText: string
  anchorPrefix?: string
  anchorSuffix?: string
}

type ObjectInsertState = {
  open: boolean
  mode: 'object' | 'link'
  objectType: string
  objectId: string
  viewId: string
  link: string
}

type SlashCommand = {
  key: string
  label: string
  description: string
  keywords: string[]
  icon: ReactNode
  run: () => void
}

type TopLevelBlock = {
  index: number
  from: number
  to: number
  node: ProseMirrorNode
}

type EditorUploadState = {
  status: 'uploading' | 'failed' | 'completed'
  file: File
  kind: 'image' | 'file'
  progress: number
  error?: string
}

const OBJECT_INSERT_TYPES = [
  { value: 'knowledge_content', label: '知识内容' },
  { value: 'project', label: '项目' },
  { value: 'issue', label: '项目事项' },
  { value: 'message', label: '消息' },
  { value: 'base', label: 'Base' },
  { value: 'base_table', label: 'Base 视图/数据表' },
  { value: 'base_record', label: 'Base 记录' },
  { value: 'approval', label: '审批' },
  { value: 'file', label: '文件' },
]

const LARGE_DOCUMENT_BLOCK_THRESHOLD = 500
const LARGE_DOCUMENT_EMBED_THRESHOLD = 50
const LARGE_DOCUMENT_COMMENT_THRESHOLD = 100
const LARGE_DOCUMENT_PREVIEW_BLOCKS = 160

const commentAnchorPluginKey = new PluginKey<DecorationSet>('docCommentAnchors')

const CommentAnchorExtension = Extension.create({
  name: 'commentAnchors',
  addProseMirrorPlugins() {
    return [
      new Plugin({
        key: commentAnchorPluginKey,
        state: {
          init: () => DecorationSet.empty,
          apply(transaction, previous) {
            const anchors = transaction.getMeta(commentAnchorPluginKey) as KnowledgeContentEditorCommentAnchor[] | undefined
            if (anchors) {
              const decorations = anchors
                .filter((anchor) => !anchor.resolved && Number.isFinite(anchor.anchorStart) && Number.isFinite(anchor.anchorEnd))
                .flatMap((anchor) => {
                  const from = Math.max(0, Math.min(Number(anchor.anchorStart), transaction.doc.content.size))
                  const to = Math.max(0, Math.min(Number(anchor.anchorEnd), transaction.doc.content.size))
                  return to > from ? [Decoration.inline(from, to, {
                    class: 'doc-comment-anchor-highlight',
                    'data-comment-id': anchor.id,
                  })] : []
                })
              return DecorationSet.create(transaction.doc, decorations)
            }
            return previous.map(transaction.mapping, transaction.doc)
          },
        },
        props: {
          decorations(state) {
            return commentAnchorPluginKey.getState(state)
          },
        },
      }),
    ]
  },
})

const BlockIdentityExtension = Extension.create({
  name: 'blockIdentity',
  addGlobalAttributes() {
    return [
      {
        types: ['paragraph', 'heading', 'blockquote', 'callout', 'codeBlock', 'bulletList', 'orderedList', 'taskList', 'table', 'image', 'horizontalRule', 'embed', 'objectCard', 'fileCard'],
        attributes: {
          blockId: {
            default: null,
            parseHTML: (element: HTMLElement) => element.getAttribute('data-block-id'),
            renderHTML: (attributes: Record<string, unknown>) => attributes.blockId ? { 'data-block-id': attributes.blockId } : {},
          },
          parentBlockId: {
            default: null,
            parseHTML: (element: HTMLElement) => element.getAttribute('data-parent-block-id'),
            renderHTML: (attributes: Record<string, unknown>) => attributes.parentBlockId ? { 'data-parent-block-id': attributes.parentBlockId } : {},
          },
        },
      },
    ]
  },
})

const ObjectCardNode = TiptapNode.create({
  name: 'objectCard',
  group: 'block',
  atom: true,
  selectable: true,
  draggable: true,
  addAttributes() {
    return {
      objectType: { default: 'issue' },
      objectId: { default: '' },
      title: { default: '' },
      subtitle: { default: '' },
      status: { default: '' },
      webPath: { default: '' },
      viewId: { default: '' },
    }
  },
  parseHTML() {
    return [{ tag: 'div[data-doc-object-card]' }]
  },
  renderHTML({ HTMLAttributes }) {
    return ['div', mergeAttributes(HTMLAttributes, { 'data-doc-object-card': 'true' })]
  },
  addNodeView() {
    return ReactNodeViewRenderer(ObjectCardNodeView)
  },
})

const FileCardNode = TiptapNode.create({
  name: 'fileCard',
  group: 'block',
  atom: true,
  selectable: true,
  draggable: true,
  addAttributes() {
    return {
      fileId: { default: '' },
      fileName: { default: '' },
      contentType: { default: 'application/octet-stream' },
      sizeBytes: { default: 0 },
      kind: { default: 'file' },
      itemId: { default: '' },
      caption: { default: '' },
    }
  },
  parseHTML() {
    return [{ tag: 'div[data-doc-file-card]' }]
  },
  renderHTML({ HTMLAttributes }) {
    return ['div', mergeAttributes(HTMLAttributes, { 'data-doc-file-card': 'true' })]
  },
  addNodeView() {
    return ReactNodeViewRenderer(FileCardNodeView)
  },
})

const LegacyEmbedNode = TiptapNode.create({
  name: 'embed',
  group: 'block',
  atom: true,
  selectable: true,
  draggable: true,
  addAttributes() {
    return { object: { default: {} } }
  },
  parseHTML() {
    return [{ tag: 'div[data-doc-legacy-embed]' }]
  },
  renderHTML({ HTMLAttributes }) {
    return ['div', mergeAttributes(HTMLAttributes, { 'data-doc-legacy-embed': 'true' })]
  },
  addNodeView() {
    return ReactNodeViewRenderer(LegacyEmbedNodeView)
  },
})

const CalloutNode = TiptapNode.create({
  name: 'callout',
  group: 'block',
  content: 'block+',
  defining: true,
  draggable: true,
  addAttributes() {
    return { tone: { default: 'info' } }
  },
  parseHTML() {
    return [{ tag: 'aside[data-doc-callout]' }]
  },
  renderHTML({ HTMLAttributes }) {
    return ['aside', mergeAttributes(HTMLAttributes, {
      'data-doc-callout': 'true',
      class: `doc-callout doc-callout-${HTMLAttributes.tone ?? 'info'}`,
    }), 0]
  },
})

const InlineObjectMark = Mark.create({
  name: 'inlineObject',
  addAttributes() {
    return {
      objectType: { default: 'text' },
    }
  },
  parseHTML() {
    return [{ tag: 'span[data-doc-inline-object]' }]
  },
  renderHTML({ HTMLAttributes }) {
    return ['span', mergeAttributes(HTMLAttributes, {
      'data-doc-inline-object': HTMLAttributes.objectType ?? 'text',
      class: `doc-inline-object doc-inline-object-${HTMLAttributes.objectType ?? 'text'}`,
    }), 0]
  },
})

export function KnowledgeContentEditorCore({
  itemId,
  title,
  document,
  versionNo,
  permissionLevel,
  updatedAt,
  canEdit,
  canManage,
  dirty,
  saving,
  conflictVisible,
  saveState,
  saveActionLabel = '保存',
  saveActionHint,
  saveActionDisabled = false,
  commentAnchors = [],
  blockAnchors = [],
  activeCommentId,
  onTitleChange,
  onDocumentChange,
  onCommentAnchorChange,
  onSave,
  onRefresh,
  onOpenLocalDraft,
  onOpenPermission,
  onOpenRelation,
  collaboration,
}: KnowledgeContentEditorCoreProps) {
  const onDocumentChangeRef = useRef(onDocumentChange)
  const onCommentAnchorChangeRef = useRef(onCommentAnchorChange)
  const onSaveRef = useRef(onSave)
  const loadedContentKeyRef = useRef('')
  const editorRef = useRef<Editor | null>(null)
  const compositionActiveRef = useRef(false)
  const imageInputRef = useRef<HTMLInputElement | null>(null)
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const contentStats = useMemo(() => knowledgeContentEditorStatsFromDocument(document, commentAnchors.length), [commentAnchors.length, document])
  const [renderWindowState, setRenderWindowState] = useState(() => ({
    itemId,
    largeDocument: contentStats.largeDocument,
    limit: contentStats.largeDocument ? LARGE_DOCUMENT_PREVIEW_BLOCKS : contentStats.blockCount,
  }))
  const renderedBlockLimit = renderWindowState.itemId === itemId && renderWindowState.largeDocument === contentStats.largeDocument
    ? renderWindowState.limit
    : contentStats.largeDocument ? LARGE_DOCUMENT_PREVIEW_BLOCKS : contentStats.blockCount
  const fullEditorLoaded = !contentStats.largeDocument || renderedBlockLimit >= contentStats.blockCount
  const renderedDocument = fullEditorLoaded ? document : previewDocument(document, renderedBlockLimit)
  const [slashOpen, setSlashOpen] = useState(false)
  const [uploadState, setUploadState] = useState<EditorUploadState | null>(null)
  const [objectInsert, setObjectInsert] = useState<ObjectInsertState>({
    open: false,
    mode: 'object',
    objectType: 'issue',
    objectId: '',
    viewId: '',
    link: '',
  })
  const collaborationDocument = fullEditorLoaded ? collaboration?.document : undefined
  const collaborationProvider = fullEditorLoaded ? collaboration?.provider : undefined
  const collaborationLocalUser = collaboration?.localUser

  useEffect(() => {
    onDocumentChangeRef.current = onDocumentChange
  }, [onDocumentChange])

  useEffect(() => {
    onSaveRef.current = onSave
  }, [onSave])

  useEffect(() => {
    onCommentAnchorChangeRef.current = onCommentAnchorChange
  }, [onCommentAnchorChange])

  const syncDocumentFromEditor = useCallback((targetEditor: Editor) => {
    ensureEditorBlockIds(targetEditor)
    onDocumentChangeRef.current(targetEditor.getJSON())
  }, [])

  const insertUploadedFileCard = useCallback(
    (
      targetEditor: Editor,
      attrs: {
        fileId: string
        fileName: string
        contentType: string
        sizeBytes: number
        kind: string
        itemId: string
      },
    ) => {
      try {
        const fileCardType = targetEditor.schema.nodes.fileCard
        const activeBlock = getSelectedTopLevelBlock(targetEditor)
        if (!fileCardType || !activeBlock) {
          throw new Error('The current editor schema cannot insert a file card')
        }
        const insertAt = activeBlock.to
        const transaction = targetEditor.state.tr.insert(insertAt, fileCardType.create(attrs))
        transaction.setSelection(TextSelection.near(transaction.doc.resolve(Math.min(insertAt + 1, transaction.doc.content.size))))
        targetEditor.view.dispatch(transaction.scrollIntoView())
        targetEditor.commands.focus()
        syncDocumentFromEditor(targetEditor)
        return true
      } catch (error) {
        console.warn('Failed to insert uploaded file into knowledge content editor', error)
        return false
      }
    },
    [syncDocumentFromEditor],
  )

  const uploadFileIntoEditor = useCallback(
    async (targetEditor: Editor | null, file: File, preferredKind?: 'image' | 'file') => {
      if (!targetEditor || targetEditor.isDestroyed) {
        return
      }
      const kind = preferredKind ?? (file.type.startsWith('image/') ? 'image' : 'file')
      setUploadState({ status: 'uploading', file, kind, progress: 0 })
      try {
        const metadata = await uploadFileForTarget(file, 'knowledge_content', itemId, (progress) => {
          setUploadState((current) => current?.file === file ? { ...current, status: 'uploading', progress } : current)
        })
        const activeEditor = targetEditor.isDestroyed ? editorRef.current : targetEditor
        if (!activeEditor || activeEditor.isDestroyed) {
          return
        }
        const resolvedKind = preferredKind ?? (metadata.contentType.startsWith('image/') ? 'image' : 'file')
        const inserted = insertUploadedFileCard(activeEditor, {
          fileId: metadata.id,
          fileName: metadata.originalName,
          contentType: metadata.contentType,
          sizeBytes: metadata.sizeBytes,
          kind: resolvedKind,
          itemId,
        })
        if (!inserted) {
          throw new Error('文件已上传，但无法插入当前知识内容。请重试。')
        }
        window.setTimeout(() => {
          const currentEditor = editorRef.current
          if (currentEditor && !currentEditor.isDestroyed && !editorContainsText(currentEditor, metadata.id)) {
            insertUploadedFileCard(currentEditor, {
              fileId: metadata.id,
              fileName: metadata.originalName,
              contentType: metadata.contentType,
              sizeBytes: metadata.sizeBytes,
              kind: resolvedKind,
              itemId,
            })
          }
        }, 150)
        setUploadState({ status: 'completed', file, kind: resolvedKind, progress: 100 })
        window.setTimeout(() => {
          setUploadState((current) => current?.file === file && current.status === 'completed' ? null : current)
        }, 1800)
      } catch (error) {
        setUploadState({
          status: 'failed',
          file,
          kind,
          progress: 0,
          error: error instanceof Error ? error.message : '文件上传失败',
        })
      }
    },
    [itemId, insertUploadedFileCard],
  )

  const insertResolvedLink = useCallback(
    async (targetEditor: Editor, link: string) => {
      const resolved = await resolveInternalLink(link)
      if (!resolved.summary) {
        return false
      }
      insertObjectCard(targetEditor, resolved.summary)
      syncDocumentFromEditor(targetEditor)
      return true
    },
    [syncDocumentFromEditor],
  )

  const extensions = useMemo(
    () => {
      const baseExtensions: Extensions = [
      StarterKit.configure({
        heading: { levels: [1, 2, 3] },
        ...(collaborationProvider ? { undoRedo: false } : {}),
        link: {
          autolink: true,
          defaultProtocol: 'https',
          linkOnPaste: true,
          openOnClick: false,
        },
      }),
      TaskList,
      TaskItem.configure({ nested: true }),
      Table.configure({ resizable: true, allowTableNodeSelection: true }),
      TableRow,
      TableHeader,
      TableCell,
      Image.configure({ allowBase64: false }),
      ObjectCardNode,
      FileCardNode,
      LegacyEmbedNode,
      CalloutNode,
      InlineObjectMark,
      BlockIdentityExtension,
      CommentAnchorExtension,
      ]
      if (collaborationProvider && collaborationDocument) {
        baseExtensions.push(
          Collaboration.configure({ document: collaborationDocument, field: 'default' }),
          CollaborationCaret.configure({
            provider: collaborationProvider,
            user: collaborationLocalUser ?? { name: '协作者', color: '#5b5bd6' },
          }),
        )
      }
      return baseExtensions
    },
    [collaborationDocument, collaborationLocalUser, collaborationProvider],
  )

  const editor = useEditor(
    {
      extensions,
      content: collaborationProvider ? undefined : renderedDocument,
      editable: canEdit && fullEditorLoaded && (!collaborationProvider || collaboration?.canEdit),
      immediatelyRender: false,
      editorProps: {
        attributes: {
          class: 'doc-prosemirror',
          role: 'textbox',
          'aria-label': '知识内容正文编辑器',
          'aria-multiline': 'true',
        },
        handleDOMEvents: {
          compositionstart: () => {
            compositionActiveRef.current = true
            return false
          },
          compositionend: () => {
            compositionActiveRef.current = false
            window.queueMicrotask(() => {
              const activeEditor = editorRef.current
              if (activeEditor && !activeEditor.isDestroyed && !collaborationProvider) {
                syncDocumentFromEditor(activeEditor)
              }
            })
            return false
          },
        },
        handleKeyDown: (_view, event) => {
          if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
            event.preventDefault()
            onSaveRef.current()
            return true
          }
          if ((event.ctrlKey || event.metaKey) && event.altKey && ['1', '2', '3'].includes(event.key)) {
            event.preventDefault()
            editorRef.current?.chain().focus().toggleHeading({ level: Number(event.key) as 1 | 2 | 3 }).run()
            return true
          }
          if ((event.ctrlKey || event.metaKey) && event.shiftKey && event.key === '7') {
            event.preventDefault()
            editorRef.current?.chain().focus().toggleOrderedList().run()
            return true
          }
          if ((event.ctrlKey || event.metaKey) && event.shiftKey && event.key === '8') {
            event.preventDefault()
            editorRef.current?.chain().focus().toggleBulletList().run()
            return true
          }
          if (event.key === '/' && !event.ctrlKey && !event.metaKey && !event.altKey) {
            setSlashOpen(true)
          }
          if (event.key === 'Escape') {
            setSlashOpen(false)
          }
          return false
        },
        handlePaste: (_view, event) => {
          const activeEditor = editorRef.current
          if (!activeEditor) {
            return false
          }
          const text = event.clipboardData?.getData('text/plain') ?? ''
          const html = event.clipboardData?.getData('text/html') ?? ''
          if (text.trim() && editorCanResolveLink(text.trim())) {
            event.preventDefault()
            void insertResolvedLink(activeEditor, text.trim())
            return true
          }
          if (html.trim()) {
            event.preventDefault()
            activeEditor.commands.insertContent(sanitizePastedHtml(html))
            return true
          }
          const structuredContent = parseStructuredPaste(text)
          if (structuredContent) {
            event.preventDefault()
            activeEditor.commands.insertContent(structuredContent)
            return true
          }
          return false
        },
      },
      onCreate: ({ editor: nextEditor }) => {
        // In collaboration mode, the Y.Doc starts empty and ProseMirror forces an
        // empty paragraph. Running ensureEditorBlockIds here would dispatch a
        // transaction on the un-synced document, permanently inserting an empty
        // line into the shared state. Block IDs come from the server content.
        if (collaborationProvider) return
        if (ensureEditorBlockIds(nextEditor)) {
          onDocumentChangeRef.current(nextEditor.getJSON())
        }
      },
      onUpdate: ({ editor: nextEditor }) => {
        if (!fullEditorLoaded || collaborationProvider || compositionActiveRef.current) {
          return
        }
        syncDocumentFromEditor(nextEditor)
      },
      onSelectionUpdate: ({ editor: nextEditor }) => {
        const { from, to, empty } = nextEditor.state.selection
        if (empty) {
          onCommentAnchorChangeRef.current?.(undefined)
          return
        }
        const anchorText = nextEditor.state.doc.textBetween(from, to, ' ').trim()
        if (!anchorText) {
          onCommentAnchorChangeRef.current?.(undefined)
          return
        }
        const docSize = nextEditor.state.doc.content.size
        onCommentAnchorChangeRef.current?.({
          anchorType: 'selection',
          anchorStart: from,
          anchorEnd: to,
          anchorText,
          anchorPrefix: nextEditor.state.doc.textBetween(Math.max(0, from - 40), from, ' ').trim(),
          anchorSuffix: nextEditor.state.doc.textBetween(to, Math.min(docSize, to + 40), ' ').trim(),
        })
      },
    },
    // Keep the editor instance stable while parent state mirrors each keystroke.
    // Incoming content is synchronized by the effect below so changing it here
    // would recreate ProseMirror and drop the current DOM focus.
    [collaborationProvider, extensions, fullEditorLoaded, syncDocumentFromEditor],
  )

  useEffect(() => {
    if (!editor || editor.isDestroyed) {
      return
    }
    editorRef.current = editor
    editor.setEditable(canEdit && fullEditorLoaded && (!collaborationProvider || Boolean(collaboration?.canEdit)))
  }, [canEdit, collaboration?.canEdit, collaborationProvider, editor, fullEditorLoaded])

  useEffect(() => {
    if (!editor || collaborationProvider) {
      return
    }
    const contentKey = `${itemId}:${versionNo}:${renderedBlockLimit}`
    if (loadedContentKeyRef.current === contentKey) {
      return
    }
    loadedContentKeyRef.current = contentKey
    if (!documentsEqual(editor.getJSON(), renderedDocument) && !editor.isDestroyed) {
      editor.commands.setContent(renderedDocument, { emitUpdate: false })
    }
  }, [collaborationProvider, itemId, editor, renderedBlockLimit, renderedDocument, versionNo])

  useEffect(() => {
    if (!collaboration?.provider) return
    const sharedTitle = collaboration.document.getText('title')
    const syncTitle = () => {
      const nextTitle = sharedTitle.toString()
      if (nextTitle && nextTitle !== title) onTitleChange(nextTitle)
    }
    sharedTitle.observe(syncTitle)
    syncTitle()
    return () => sharedTitle.unobserve(syncTitle)
  }, [collaboration?.document, collaboration?.provider, onTitleChange, title])

  useEffect(() => {
    if (!editor || editor.isDestroyed) {
      return
    }
    editor.view.dispatch(editor.state.tr.setMeta(commentAnchorPluginKey, commentAnchors))
  }, [commentAnchors, editor])

  useEffect(() => {
    if (!editor || editor.isDestroyed) {
      return
    }
    syncEditorBlockDomAnchors(editor, blockAnchors)
  }, [blockAnchors, editor, renderedDocument])

  useEffect(() => {
    if (!editor || !activeCommentId) {
      return
    }
    const anchor = commentAnchors.find((item) => item.id === activeCommentId)
    if (anchor?.anchorStart == null || anchor.anchorEnd == null) {
      return
    }
    editor.chain().focus().setTextSelection({ from: anchor.anchorStart, to: anchor.anchorEnd }).scrollIntoView().run()
  }, [activeCommentId, commentAnchors, editor])

  useEffect(() => {
    if (!editor || editor.isDestroyed) {
      return
    }
    const hash = window.location.hash.replace(/^#/, '')
    if (!hash.startsWith('doc-block-')) {
      return
    }
    window.setTimeout(() => highlightEditorBlock(hash), 0)
  }, [blockAnchors, editor, renderedDocument])

  useEffect(() => {
    const handleHashChange = () => {
      const hash = window.location.hash.replace(/^#/, '')
      if (hash.startsWith('doc-block-')) {
        highlightEditorBlock(hash)
      }
    }
    window.addEventListener('hashchange', handleHashChange)
    return () => window.removeEventListener('hashchange', handleHashChange)
  }, [])

  const fallbackStatus = saving ? 'saving' : conflictVisible ? 'conflict' : dirty ? 'dirty' : 'saved'
  const realtimeSaveState: KnowledgeContentSaveState | undefined = collaboration?.provider
    ? collaboration.error ? 'error'
      : collaboration.status === 'disconnected' ? 'offline'
        : collaboration.unsyncedChanges > 0 ? 'saving'
          : collaboration.synced ? 'saved' : 'saving'
    : undefined
  const effectiveSaveState = realtimeSaveState ?? saveState ?? fallbackStatus
  const statusColorValue = statusColor(effectiveSaveState)
  const saveTooltip = saveActionHint ?? saveActionLabel
  return (
    <section className="doc-editor-shell">
      <div className="doc-editor-topbar">
        <div className="doc-editor-title-group">
          <Input
            className="doc-title-input"
            disabled={!canEdit || !fullEditorLoaded || Boolean(collaboration?.provider && !collaboration.canEdit)}
            aria-label="知识内容标题"
            value={title}
            placeholder="Untitled"
            onChange={(event) => {
              const value = event.target.value
              if (collaboration?.provider) {
                const sharedTitle = collaboration.document.getText('title')
                updateSharedTitle(collaboration.document, sharedTitle, value)
              }
              onTitleChange(value)
            }}
          />
          <Space wrap>
            <Tag>v{versionNo}</Tag>
            <Tag>{permissionText(permissionLevel)}</Tag>
            <Tag color={statusColorValue}>{statusText(effectiveSaveState)}</Tag>
            {contentStats.largeDocument ? <Tag color="orange">大内容 {contentStats.blockCount} 块</Tag> : null}
            <Typography.Text type="secondary">更新于 {new Date(updatedAt).toLocaleString()}</Typography.Text>
            {collaboration?.provider ? <Tag color={collaboration.synced ? 'green' : 'processing'}>{collaboration.synced ? '实时已同步' : '正在同步'}</Tag> : null}
          </Space>
        </div>
        <Space wrap className="doc-editor-actions">
          {collaboration?.provider && collaboration.onlineUsers.length > 0 ? (
            <Avatar.Group max={{ count: 4 }}>
              {collaboration.onlineUsers.map((user) => (
                <Tooltip key={`${user.id}:${user.clientId}`} title={user.name}>
                  <Avatar style={{ backgroundColor: user.color }}>{user.name.slice(0, 1).toUpperCase()}</Avatar>
                </Tooltip>
              ))}
            </Avatar.Group>
          ) : null}
          <Tooltip title={saveTooltip}>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              disabled={!canEdit || saving || saveActionDisabled || !fullEditorLoaded}
              loading={saving}
              onClick={onSave}
            >
              {saveActionLabel}
            </Button>
          </Tooltip>
          <Tooltip title="授权">
            <Button aria-label="内容分享与权限" icon={<ShareAltOutlined />} disabled={!canManage} onClick={onOpenPermission} />
          </Tooltip>
          <Tooltip title="关联对象">
            <Button aria-label="关联对象" icon={<LinkOutlined />} disabled={!canEdit} onClick={onOpenRelation} />
          </Tooltip>
        </Space>
      </div>

      {conflictVisible ? (
        <Alert
          showIcon
          type="warning"
          message="版本冲突"
          description="当前草稿基于旧版本。请刷新知识内容查看最新内容，再手动合并后保存。"
          action={(
            <Space>
              <Button onClick={onRefresh}>刷新远端</Button>
              <Button onClick={onOpenLocalDraft}>保留本地草稿</Button>
            </Space>
          )}
        />
      ) : null}
      {effectiveSaveState === 'offline' || effectiveSaveState === 'error' ? (
        <Alert
          showIcon
          type={effectiveSaveState === 'offline' ? 'info' : 'error'}
          message={effectiveSaveState === 'offline' ? '当前网络不可用' : '保存失败'}
          description={collaboration?.recovery.state === 'overflow'
            ? `离线修改已达到恢复上限（${collaboration?.recovery.queuedUpdates ?? 0} 次 / ${formatFileSize(collaboration?.recovery.queuedBytes ?? 0)}），请先导出副本。`
            : collaboration?.recovery.state === 'offline'
              ? `本地修改正在等待重连（${collaboration.recovery.queuedUpdates} 次 / ${formatFileSize(collaboration.recovery.queuedBytes)}）。恢复网络后会自动合并。`
              : '本地草稿已保留。恢复网络或修复问题后可以重试保存。'}
          action={(
            <Space>
              <Button onClick={onSave}>重试保存</Button>
              {collaboration && collaboration.recovery.state !== 'online' ? <Button onClick={collaboration.exportOfflineCopy}>导出恢复副本</Button> : null}
              <Button onClick={onOpenLocalDraft}>查看本地草稿</Button>
            </Space>
          )}
        />
      ) : null}
      {contentStats.largeDocument && !fullEditorLoaded ? (
        <Alert
          showIcon
          type="info"
          message="大内容预览模式"
          description={`当前知识内容约 ${contentStats.blockCount} 块、${contentStats.embedCount} 个嵌入。已渲染前 ${Math.min(renderedBlockLimit, contentStats.blockCount)} 块，正文仍以完整规范快照保存。`}
          action={(
            <Button onClick={() => setRenderWindowState({
              itemId,
              largeDocument: contentStats.largeDocument,
              limit: Math.min(contentStats.blockCount, renderedBlockLimit + LARGE_DOCUMENT_PREVIEW_BLOCKS),
            })}>
              继续加载 {Math.min(LARGE_DOCUMENT_PREVIEW_BLOCKS, contentStats.blockCount - renderedBlockLimit)} 块
            </Button>
          )}
        />
      ) : null}

      <div className="doc-editor-canvas">
        <TableToolbar editor={editor} canEdit={canEdit && fullEditorLoaded} />
        <BlockDragHandle editor={editor} canEdit={canEdit && fullEditorLoaded} onOpenSlash={() => setSlashOpen(true)} />
        {editor && canEdit && slashOpen ? (
          <SlashMenu
            editor={editor}
            onClose={() => setSlashOpen(false)}
            onPickImage={() => imageInputRef.current?.click()}
            onPickFile={() => fileInputRef.current?.click()}
            onInsertObject={(next) => setObjectInsert({ ...objectInsert, ...next, open: true })}
          />
        ) : null}
        {editor && canEdit && fullEditorLoaded ? <SelectionBubbleToolbar editor={editor} /> : null}
        <EditorContent editor={editor} />
      </div>

      <input
        ref={imageInputRef}
        type="file"
        accept="image/*"
        hidden
        onChange={(event) => {
          const file = event.target.files?.[0]
          event.target.value = ''
          const targetEditor = editorRef.current ?? editor
          if (targetEditor && file) {
            void uploadFileIntoEditor(targetEditor, file, 'image')
          }
        }}
      />
      <input
        ref={fileInputRef}
        type="file"
        hidden
        onChange={(event) => {
          const file = event.target.files?.[0]
          event.target.value = ''
          const targetEditor = editorRef.current ?? editor
          if (targetEditor && file) {
            void uploadFileIntoEditor(targetEditor, file, 'file')
          }
        }}
      />
      {uploadState ? (
        <div className="doc-upload-status" role="status" aria-label="文件上传状态">
          {uploadState.status === 'uploading' ? (
            <>
              <Typography.Text>正在上传 {uploadState.file.name}</Typography.Text>
              <Progress percent={uploadState.progress} size="small" />
            </>
          ) : null}
          {uploadState.status === 'completed' ? <Tag color="green">上传完成：{uploadState.file.name}</Tag> : null}
          {uploadState.status === 'failed' ? (
            <Alert
              type="error"
              showIcon
              message={`上传失败：${uploadState.file.name}`}
              description={uploadState.error}
              action={(
                <Space>
                  <Button size="small" onClick={() => void uploadFileIntoEditor(editorRef.current ?? editor, uploadState.file, uploadState.kind)}>重试上传</Button>
                  <Button size="small" onClick={() => setUploadState(null)}>取消</Button>
                </Space>
              )}
            />
          ) : null}
        </div>
      ) : null}
      {editor ? (
        <ObjectInsertModal
          state={objectInsert}
          onChange={setObjectInsert}
          onCancel={() => setObjectInsert((current) => ({ ...current, open: false }))}
          onSubmit={async () => {
            if (objectInsert.mode === 'link') {
              if (objectInsert.link.trim()) {
                await insertResolvedLink(editor, objectInsert.link.trim())
              }
            } else if (objectInsert.objectId.trim()) {
              insertObjectCard(editor, {
                objectType: objectInsert.objectType,
                objectId: objectInsert.objectId.trim(),
                accessState: 'available',
                title: objectInsert.objectId.trim(),
                metadata: objectInsert.viewId.trim() ? { viewId: objectInsert.viewId.trim() } : {},
              })
              syncDocumentFromEditor(editor)
            }
            setObjectInsert({
              open: false,
              mode: 'object',
              objectType: 'issue',
              objectId: '',
              viewId: '',
              link: '',
            })
          }}
        />
      ) : null}

    </section>
  )
}

function updateSharedTitle(document: import('yjs').Doc, sharedTitle: import('yjs').Text, nextValue: string) {
  const current = sharedTitle.toString()
  if (current === nextValue) return
  let prefix = 0
  while (prefix < current.length && prefix < nextValue.length && current[prefix] === nextValue[prefix]) prefix += 1
  let suffix = 0
  while (
    suffix < current.length - prefix
    && suffix < nextValue.length - prefix
    && current[current.length - suffix - 1] === nextValue[nextValue.length - suffix - 1]
  ) suffix += 1
  document.transact(() => {
    const removed = current.length - prefix - suffix
    if (removed > 0) sharedTitle.delete(prefix, removed)
    const inserted = nextValue.slice(prefix, nextValue.length - suffix)
    if (inserted) sharedTitle.insert(prefix, inserted)
  }, 'local-title-input')
}

function SelectionBubbleToolbar({ editor }: { editor: Editor }) {
  return (
    <BubbleMenu
      editor={editor}
      className="doc-selection-toolbar"
      options={{ placement: 'top-start', offset: 8, shift: true, flip: true }}
      shouldShow={({ editor: currentEditor }) => currentEditor.isEditable && !currentEditor.state.selection.empty}
    >
      <Space size={4} className="doc-selection-toolbar-inner">
        <Tooltip title="评论选区">
          <Button
            size="small"
            icon={<CommentOutlined />}
            onClick={() => document.querySelector<HTMLElement>('.doc-comment-composer')?.focus()}
          />
        </Tooltip>
        <EditorToolbar editor={editor} canEdit={editor.isEditable} />
      </Space>
    </BubbleMenu>
  )
}

function EditorToolbar({ editor, canEdit }: { editor: Editor | null; canEdit: boolean }) {
  return (
    <div className="doc-editor-toolbar" role="toolbar" aria-label="知识内容编辑工具栏">
      <Space wrap size={4}>
        <ToolbarButton
          title="撤销"
          active={false}
          disabled={!canEdit || !editor?.can().undo()}
          onClick={() => editor?.chain().focus().undo().run()}
          icon={<UndoOutlined />}
        />
        <ToolbarButton
          title="重做"
          active={false}
          disabled={!canEdit || !editor?.can().redo()}
          onClick={() => editor?.chain().focus().redo().run()}
          icon={<RedoOutlined />}
        />
        <ToolbarButton
          title="正文"
          active={Boolean(editor?.isActive('paragraph'))}
          disabled={!canEdit || !editor}
          onClick={() => editor?.chain().focus().setParagraph().run()}
        >
          T
        </ToolbarButton>
        <ToolbarButton
          title="一级标题"
          active={Boolean(editor?.isActive('heading', { level: 1 }))}
          disabled={!canEdit || !editor}
          onClick={() => editor?.chain().focus().toggleHeading({ level: 1 }).run()}
        >
          H1
        </ToolbarButton>
        <ToolbarButton
          title="二级标题"
          active={Boolean(editor?.isActive('heading', { level: 2 }))}
          disabled={!canEdit || !editor}
          onClick={() => editor?.chain().focus().toggleHeading({ level: 2 }).run()}
        >
          H2
        </ToolbarButton>
        <ToolbarButton
          title="三级标题"
          active={Boolean(editor?.isActive('heading', { level: 3 }))}
          disabled={!canEdit || !editor}
          onClick={() => editor?.chain().focus().toggleHeading({ level: 3 }).run()}
        >
          H3
        </ToolbarButton>
        <ToolbarButton
          title="加粗"
          active={Boolean(editor?.isActive('bold'))}
          disabled={!canEdit || !editor}
          onClick={() => editor?.chain().focus().toggleBold().run()}
          icon={<BoldOutlined />}
        />
        <ToolbarButton
          title="斜体"
          active={Boolean(editor?.isActive('italic'))}
          disabled={!canEdit || !editor}
          onClick={() => editor?.chain().focus().toggleItalic().run()}
          icon={<ItalicOutlined />}
        />
        <ToolbarButton
          title="删除线"
          active={Boolean(editor?.isActive('strike'))}
          disabled={!canEdit || !editor}
          onClick={() => editor?.chain().focus().toggleStrike().run()}
          icon={<StrikethroughOutlined />}
        />
        <ToolbarButton
          title="行内代码"
          active={Boolean(editor?.isActive('code'))}
          disabled={!canEdit || !editor}
          onClick={() => editor?.chain().focus().toggleCode().run()}
          icon={<CodeOutlined />}
        />
        <ToolbarButton
          title="链接"
          active={Boolean(editor?.isActive('link'))}
          disabled={!canEdit || !editor}
          onClick={() => toggleLink(editor)}
          icon={<LinkOutlined />}
        />
        <ToolbarButton
          title="项目列表"
          active={Boolean(editor?.isActive('bulletList'))}
          disabled={!canEdit || !editor}
          onClick={() => editor?.chain().focus().toggleBulletList().run()}
          icon={<UnorderedListOutlined />}
        />
        <ToolbarButton
          title="编号列表"
          active={Boolean(editor?.isActive('orderedList'))}
          disabled={!canEdit || !editor}
          onClick={() => editor?.chain().focus().toggleOrderedList().run()}
          icon={<OrderedListOutlined />}
        />
        <ToolbarButton
          title="任务列表"
          active={Boolean(editor?.isActive('taskList'))}
          disabled={!canEdit || !editor}
          onClick={() => editor?.chain().focus().toggleTaskList().run()}
          icon={<CheckSquareOutlined />}
        />
        <ToolbarButton
          title="引用"
          active={Boolean(editor?.isActive('blockquote'))}
          disabled={!canEdit || !editor}
          onClick={() => editor?.chain().focus().toggleBlockquote().run()}
        >
          &gt;
        </ToolbarButton>
        <ToolbarButton
          title="代码块"
          active={Boolean(editor?.isActive('codeBlock'))}
          disabled={!canEdit || !editor}
          onClick={() => editor?.chain().focus().toggleCodeBlock().run()}
          icon={<CodeOutlined />}
        />
      </Space>
    </div>
  )
}

function TableToolbar({ editor, canEdit }: { editor: Editor | null; canEdit: boolean }) {
  if (!editor || !canEdit) {
    return null
  }
  return (
    <BubbleMenu
      editor={editor}
      pluginKey="docTableToolbar"
      className="doc-table-editor-toolbar"
      options={{ placement: 'top-start', offset: 8, shift: true, flip: true }}
      shouldShow={({ editor: currentEditor }) => currentEditor.isEditable && currentEditor.isActive('table')}
    >
      <Space wrap size={4}>
        <Button aria-label="在右侧添加表格列" size="small" disabled={!canEdit} onClick={() => editor.chain().focus().addColumnAfter().run()}>
          加列
        </Button>
        <Button aria-label="删除当前表格列" size="small" disabled={!canEdit} onClick={() => editor.chain().focus().deleteColumn().run()}>
          删列
        </Button>
        <Button aria-label="在下方添加表格行" size="small" disabled={!canEdit} onClick={() => editor.chain().focus().addRowAfter().run()}>
          加行
        </Button>
        <Button aria-label="删除当前表格行" size="small" disabled={!canEdit} onClick={() => editor.chain().focus().deleteRow().run()}>
          删行
        </Button>
        <Button aria-label="删除整个表格" size="small" danger disabled={!canEdit} onClick={() => editor.chain().focus().deleteTable().run()}>
          删除表格
        </Button>
      </Space>
    </BubbleMenu>
  )
}

function BlockDragHandle({
  editor,
  canEdit,
  onOpenSlash,
}: {
  editor: Editor | null
  canEdit: boolean
  onOpenSlash: () => void
}) {
  const [activeBlock, setActiveBlock] = useState<{ pos: number } | null>(null)
  const [menuLayout, setMenuLayout] = useState<{
    placement: 'topLeft' | 'bottomLeft'
    maxHeight: number
  }>({ placement: 'bottomLeft', maxHeight: 220 })
  const activeBlockDomRef = useRef<HTMLElement | null>(null)
  const computePositionConfig = useMemo(() => ({ placement: 'right-start' as const, strategy: 'absolute' as const }), [])
  const getReferencedVirtualElement = useCallback(() => {
    if (!editor || editor.isDestroyed) {
      return null
    }
    const canvas = editor.view.dom.parentElement
    const block = activeBlockDomRef.current
    if (!canvas || !block) {
      return null
    }
    const canvasRect = canvas.getBoundingClientRect()
    const blockRect = block.getBoundingClientRect()
    const left = canvasRect.left + 8
    return {
      getBoundingClientRect: () => ({
        x: left,
        y: blockRect.top,
        top: blockRect.top,
        bottom: blockRect.bottom,
        left,
        right: left,
        width: 0,
        height: blockRect.height,
        toJSON: () => ({}),
      }),
    }
  }, [editor])
  if (!editor || !canEdit) {
    return null
  }
  const focusBlock = () => {
    if (activeBlock) {
      editor.chain().focus(activeBlock.pos + 1).run()
    } else {
      editor.chain().focus().run()
    }
  }
  return (
    <DragHandle
      editor={editor}
      className="doc-block-drag-handle"
      computePositionConfig={computePositionConfig}
      getReferencedVirtualElement={getReferencedVirtualElement}
      onNodeChange={({ pos }) => {
        const dom = pos >= 0 ? editor.view.nodeDOM(pos) : null
        activeBlockDomRef.current = dom instanceof HTMLElement ? dom : null
        setActiveBlock(pos >= 0 ? { pos } : null)
        const canvas = editor.view.dom.closest<HTMLElement>('.doc-editor-canvas')
        if (canvas && dom instanceof HTMLElement) {
          const canvasRect = canvas.getBoundingClientRect()
          const blockRect = dom.getBoundingClientRect()
          const availableAbove = Math.max(0, blockRect.top - canvasRect.top - 8)
          const availableBelow = Math.max(0, canvasRect.bottom - blockRect.bottom - 8)
          const placement = availableBelow >= availableAbove ? 'bottomLeft' : 'topLeft'
          const available = placement === 'bottomLeft' ? availableBelow : availableAbove
          setMenuLayout({ placement, maxHeight: Math.max(72, Math.min(280, Math.floor(available))) })
        }
      }}
    >
      <Space size={2} className="doc-block-action-buttons">
        <Tooltip title="插入块">
          <Button
            size="small"
            className="doc-block-insert-button"
            icon={<PlusOutlined />}
            aria-label="插入块"
            onMouseDown={(event) => event.preventDefault()}
            onClick={() => {
              focusBlock()
              onOpenSlash()
            }}
          />
        </Tooltip>
        <Dropdown
          trigger={['click']}
          placement={menuLayout.placement}
          autoAdjustOverflow={false}
          overlayStyle={{ '--doc-block-menu-max-height': `${menuLayout.maxHeight}px` } as CSSProperties}
          overlayClassName="doc-block-operation-dropdown"
          getPopupContainer={(triggerNode) => triggerNode.closest<HTMLElement>('.doc-editor-canvas') ?? document.body}
          menu={{
            items: [
              { key: 'move-up', label: '上移', icon: <ArrowUpOutlined /> },
              { key: 'move-down', label: '下移', icon: <ArrowDownOutlined /> },
              { key: 'copy', label: '复制块', icon: <CopyOutlined /> },
              { key: 'paragraph', label: '转为正文' },
              { key: 'heading', label: '转为标题' },
              { key: 'bullet', label: '转为列表' },
              { key: 'task', label: '转为任务' },
              { key: 'quote', label: '转为引用' },
              { key: 'callout', label: '转为提示块' },
              { key: 'code', label: '转为代码块' },
              { key: 'divider', label: '转为分割线' },
              { key: 'delete', label: '删除块', icon: <DeleteOutlined />, danger: true },
            ],
            onClick: ({ key }) => {
              focusBlock()
              if (key === 'move-up') {
                moveSelectedTopLevelBlock(editor, -1)
              }
              if (key === 'move-down') {
                moveSelectedTopLevelBlock(editor, 1)
              }
              if (key === 'copy') {
                duplicateSelectedTopLevelBlock(editor)
              }
              if (key === 'paragraph') {
                editor.chain().focus().setParagraph().run()
              }
              if (key === 'heading') {
                editor.chain().focus().toggleHeading({ level: 2 }).run()
              }
              if (key === 'bullet') {
                editor.chain().focus().toggleBulletList().run()
              }
              if (key === 'task') {
                editor.chain().focus().toggleTaskList().run()
              }
              if (key === 'quote') {
                editor.chain().focus().toggleBlockquote().run()
              }
              if (key === 'callout') {
                editor.chain().focus().wrapIn('callout').run()
              }
              if (key === 'code') {
                editor.chain().focus().toggleCodeBlock().run()
              }
              if (key === 'divider') {
                editor.chain().focus().setHorizontalRule().run()
              }
              if (key === 'delete') {
                deleteSelectedTopLevelBlock(editor)
              }
            },
          }}
        >
          <Button size="small" icon={<HolderOutlined />} className="doc-block-handle-button" aria-label="操作块">
            <MoreOutlined />
          </Button>
        </Dropdown>
      </Space>
    </DragHandle>
  )
}

function getSelectedTopLevelBlock(editor: Editor) {
  const { doc, selection } = editor.state
  const position = selection.$from.before(1)
  let found: TopLevelBlock | undefined
  doc.forEach((node, offset, index) => {
    const from = offset
    const to = offset + node.nodeSize
    if (position >= from && position <= to) {
      found = { index, from, to, node }
    }
  })
  return found
}

function duplicateSelectedTopLevelBlock(editor: Editor) {
  const block = getSelectedTopLevelBlock(editor)
  if (!block?.node) {
    return false
  }
  const duplicate = cloneTopLevelBlockWithFreshIds(editor, block.node)
  const transaction = editor.state.tr.insert(block.to, duplicate)
  transaction.setSelection(TextSelection.near(transaction.doc.resolve(Math.min(block.to + 1, transaction.doc.content.size))))
  editor.view.dispatch(transaction.scrollIntoView())
  return true
}

function moveSelectedTopLevelBlock(editor: Editor, direction: -1 | 1) {
  const block = getSelectedTopLevelBlock(editor)
  if (!block?.node) {
    return false
  }
  const targetIndex = block.index + direction
  if (targetIndex < 0 || targetIndex >= editor.state.doc.childCount) {
    return false
  }
  const targetNode = editor.state.doc.child(targetIndex)
  const transaction = editor.state.tr.delete(block.from, block.to)
  const insertAt = direction < 0 ? block.from - targetNode.nodeSize : block.from + targetNode.nodeSize
  transaction.insert(insertAt, block.node)
  transaction.setSelection(TextSelection.near(transaction.doc.resolve(Math.min(insertAt + 1, transaction.doc.content.size))))
  editor.view.dispatch(transaction.scrollIntoView())
  return true
}

function deleteSelectedTopLevelBlock(editor: Editor) {
  const block = getSelectedTopLevelBlock(editor)
  if (!block) {
    return false
  }
  const transaction = editor.state.tr.delete(block.from, block.to)
  transaction.setSelection(TextSelection.near(transaction.doc.resolve(Math.min(block.from, transaction.doc.content.size))))
  editor.view.dispatch(transaction.scrollIntoView())
  return true
}

function cloneTopLevelBlockWithFreshIds(editor: Editor, node: ProseMirrorNode) {
  const json = node.toJSON() as JSONContent
  const idMap = new Map<string, string>()
  visitJsonNodes(json, (current) => {
    const blockId = typeof current.attrs?.blockId === 'string' ? current.attrs.blockId : ''
    if (blockId) {
      idMap.set(blockId, createEditorBlockId())
    }
  })
  visitJsonNodes(json, (current) => {
    if (!current.attrs) {
      return
    }
    const blockId = typeof current.attrs.blockId === 'string' ? current.attrs.blockId : ''
    const parentBlockId = typeof current.attrs.parentBlockId === 'string' ? current.attrs.parentBlockId : ''
    current.attrs = {
      ...current.attrs,
      ...(blockId ? { blockId: idMap.get(blockId) ?? createEditorBlockId() } : {}),
      ...(parentBlockId && idMap.has(parentBlockId) ? { parentBlockId: idMap.get(parentBlockId) } : {}),
    }
  })
  return editor.schema.nodeFromJSON(json)
}

function visitJsonNodes(node: JSONContent, visitor: (node: JSONContent) => void) {
  visitor(node)
  node.content?.forEach((child) => visitJsonNodes(child, visitor))
}

function SlashMenu({
  editor,
  onClose,
  onPickImage,
  onPickFile,
  onInsertObject,
}: {
  editor: Editor
  onClose: () => void
  onPickImage: () => void
  onPickFile: () => void
  onInsertObject: (state: Partial<ObjectInsertState>) => void
}) {
  const [query, setQuery] = useState('')
  const [activeIndex, setActiveIndex] = useState(0)
  const menuRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target
      if (target instanceof Node && menuRef.current?.contains(target)) {
        return
      }
      onClose()
    }

    document.addEventListener('pointerdown', handlePointerDown)
    return () => document.removeEventListener('pointerdown', handlePointerDown)
  }, [onClose])

  const runCommand = (command: () => void) => {
    removeSlashBeforeCursor(editor)
    command()
    onClose()
    setQuery('')
  }
  const commands: SlashCommand[] = [
    { key: 'paragraph', label: '文本', description: '普通段落', keywords: ['text', 'paragraph', '正文', '段落'], icon: <span>T</span>, run: () => editor.chain().focus().setParagraph().run() },
    { key: 'heading', label: '标题', description: '二级标题', keywords: ['heading', 'title', '标题'], icon: <span>H</span>, run: () => editor.chain().focus().toggleHeading({ level: 2 }).run() },
    { key: 'task', label: '任务', description: '待办清单', keywords: ['task', 'todo', 'check', '任务', '待办'], icon: <CheckSquareOutlined />, run: () => editor.chain().focus().toggleTaskList().run() },
    { key: 'bullet', label: '列表', description: '项目符号列表', keywords: ['list', 'bullet', '列表'], icon: <UnorderedListOutlined />, run: () => editor.chain().focus().toggleBulletList().run() },
    { key: 'quote', label: '引用', description: '引用块', keywords: ['quote', '引用'], icon: <span>&gt;</span>, run: () => editor.chain().focus().toggleBlockquote().run() },
    { key: 'callout', label: '提示块', description: '突出显示说明或注意事项', keywords: ['callout', 'notice', '提示', '注意'], icon: <span>!</span>, run: () => editor.chain().focus().insertContent({ type: 'callout', content: [{ type: 'paragraph' }] }).run() },
    { key: 'code', label: '代码', description: '代码块', keywords: ['code', '代码'], icon: <CodeOutlined />, run: () => editor.chain().focus().toggleCodeBlock().run() },
    { key: 'table', label: '表格', description: '基础表格', keywords: ['table', '表格'], icon: <TableOutlined />, run: () => editor.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: false }).run() },
    { key: 'divider', label: '分割线', description: '水平分割线', keywords: ['divider', 'hr', 'line', '分割线'], icon: <span>--</span>, run: () => editor.chain().focus().setHorizontalRule().run() },
    { key: 'image', label: '图片', description: '上传图片', keywords: ['image', 'picture', '图片'], icon: <FileImageOutlined />, run: onPickImage },
    { key: 'file', label: '文件', description: '上传附件', keywords: ['file', 'attachment', '文件', '附件'], icon: <FileOutlined />, run: onPickFile },
    {
      key: 'base-view',
      label: 'Base 视图',
      description: '嵌入多维表格视图',
      keywords: ['base', 'table', 'view', '多维表格', '视图'],
      icon: <AppstoreAddOutlined />,
      run: () => onInsertObject({ mode: 'object', objectType: 'base_table', objectId: '', viewId: '' }),
    },
    {
      key: 'issue',
      label: '项目事项',
      description: '嵌入事项或 Bug',
      keywords: ['issue', 'project', '事项', '项目'],
      icon: <AppstoreAddOutlined />,
      run: () => onInsertObject({ mode: 'object', objectType: 'issue', objectId: '', viewId: '' }),
    },
    {
      key: 'message',
      label: '消息',
      description: '嵌入会话消息',
      keywords: ['message', 'im', '消息'],
      icon: <AppstoreAddOutlined />,
      run: () => onInsertObject({ mode: 'object', objectType: 'message', objectId: '', viewId: '' }),
    },
    {
      key: 'link',
      label: '内部链接',
      description: '粘贴系统链接并解析',
      keywords: ['link', 'url', '链接'],
      icon: <LinkOutlined />,
      run: () => onInsertObject({ mode: 'link', link: '' }),
    },
  ]
  const normalizedQuery = query.trim().toLowerCase()
  const visibleCommands = normalizedQuery
    ? commands.filter((command) =>
        [command.label, command.description, ...command.keywords].some((value) => value.toLowerCase().includes(normalizedQuery)),
      )
    : commands
  const boundedActiveIndex = visibleCommands.length === 0 ? 0 : Math.min(activeIndex, visibleCommands.length - 1)
  const menuPosition = getSlashMenuPosition(editor, visibleCommands.length)

  useEffect(() => {
    menuRef.current?.querySelector<HTMLElement>(`[data-command-index="${boundedActiveIndex}"]`)?.scrollIntoView({ block: 'nearest' })
  }, [boundedActiveIndex])

  return (
    <div ref={menuRef} className="doc-slash-menu" style={menuPosition} role="dialog" aria-label="插入内容块">
      <Input
        className="doc-slash-menu-search"
        autoFocus
        allowClear
        size="small"
        placeholder="搜索块、对象或文件"
        value={query}
        onChange={(event) => {
          setQuery(event.target.value)
          setActiveIndex(0)
        }}
        onKeyDown={(event) => {
          if (event.key === 'ArrowDown') {
            event.preventDefault()
            setActiveIndex((current) => visibleCommands.length > 0 ? (current + 1) % visibleCommands.length : 0)
          }
          if (event.key === 'ArrowUp') {
            event.preventDefault()
            setActiveIndex((current) => visibleCommands.length > 0 ? (current - 1 + visibleCommands.length) % visibleCommands.length : 0)
          }
          if (event.key === 'Enter') {
            event.preventDefault()
            if (visibleCommands[boundedActiveIndex]) {
              runCommand(visibleCommands[boundedActiveIndex].run)
            }
          }
          if (event.key === 'Escape') {
            event.preventDefault()
            onClose()
            editor.chain().focus().run()
          }
        }}
      />
      {visibleCommands.map((command, index) => (
        <button
          key={command.key}
          type="button"
          className={index === boundedActiveIndex ? 'active' : undefined}
          data-command-index={index}
          aria-selected={index === boundedActiveIndex}
          onMouseEnter={() => setActiveIndex(index)}
          onClick={() => runCommand(command.run)}
        >
          <span className="doc-slash-menu-icon">{command.icon}</span>
          <span>
            <strong>{command.label}</strong>
            <small>{command.description}</small>
          </span>
        </button>
      ))}
      {visibleCommands.length === 0 ? <Typography.Text type="secondary">没有匹配命令</Typography.Text> : null}
    </div>
  )
}

function getSlashMenuPosition(editor: Editor, commandCount: number): CSSProperties {
  try {
    const canvas = editor.view.dom.closest<HTMLElement>('.doc-editor-canvas')
    if (!canvas) {
      return {}
    }
    const canvasRect = canvas.getBoundingClientRect()
    const caretRect = editor.view.coordsAtPos(editor.state.selection.from)
    const width = Math.min(420, Math.max(260, canvasRect.width - 32))
    const estimatedHeight = Math.min(430, 56 + Math.ceil(Math.max(1, commandCount) / 2) * 52)
    const preferredLeft = caretRect.left - canvasRect.left
    const left = Math.max(8, Math.min(preferredLeft, canvasRect.width - width - 8))
    const below = caretRect.bottom - canvasRect.top + 8
    const above = caretRect.top - canvasRect.top - estimatedHeight - 8
    const top = below + estimatedHeight <= canvasRect.height ? below : Math.max(8, above)
    return { left, top, width }
  } catch {
    return {}
  }
}

function ObjectInsertModal({
  state,
  onChange,
  onCancel,
  onSubmit,
}: {
  state: ObjectInsertState
  onChange: (state: ObjectInsertState) => void
  onCancel: () => void
  onSubmit: () => Promise<void>
}) {
  return (
    <Modal title={state.mode === 'link' ? '插入内部链接' : '插入对象卡片'} open={state.open} onCancel={onCancel} onOk={onSubmit}>
      {state.mode === 'link' ? (
        <Input
          value={state.link}
          placeholder="粘贴事项、知识内容、表格、消息或完整链接"
          onChange={(event) => onChange({ ...state, link: event.target.value })}
        />
      ) : (
        <Space orientation="vertical" className="doc-object-insert-form">
          <Select
            value={state.objectType}
            options={OBJECT_INSERT_TYPES}
            onChange={(objectType) => onChange({ ...state, objectType })}
          />
          <Input
            value={state.objectId}
            placeholder="对象 ID"
            onChange={(event) => onChange({ ...state, objectId: event.target.value })}
          />
          {state.objectType === 'base_table' ? (
            <Input
              value={state.viewId}
              placeholder="视图 ID，可选"
              onChange={(event) => onChange({ ...state, viewId: event.target.value })}
            />
          ) : null}
        </Space>
      )}
    </Modal>
  )
}

function ObjectCardNodeView({ node }: NodeViewProps) {
  const objectType = String(node.attrs.objectType ?? '')
  const objectId = String(node.attrs.objectId ?? '')
  const viewId = String(node.attrs.viewId ?? '')
  const needsConfiguration = objectType === 'base_table' && !objectId.trim()
  const objectQuery = useQuery({
    queryKey: ['knowledge-content', 'object-card', objectType, objectId],
    queryFn: () => getObjectNavigation(objectType, objectId),
    enabled: Boolean(objectType && objectId),
  })
  const fallbackSummary: PlatformObjectSummary = {
    objectType,
    objectId,
    accessState: 'available',
    title: String(node.attrs.title || objectId),
    subtitle: String(node.attrs.subtitle || ''),
    status: String(node.attrs.status || ''),
    webPath: String(node.attrs.webPath || ''),
    deepLink: null,
    metadata: viewId ? { viewId } : {},
  }
  const summary = objectQuery.data?.summary ?? fallbackSummary

  return (
    <NodeViewWrapper className="doc-object-card-node">
      {needsConfiguration ? (
        <Alert
          type="info"
          showIcon
          message="请选择数据表"
          description="Base 视图块需要绑定一个具体数据表，可选绑定默认视图。"
        />
      ) : objectQuery.isError ? (
        <Alert type="warning" showIcon message="对象不可用" description={objectTypeText[objectType] ?? '协作对象'} />
      ) : summary.accessState !== 'available' ? (
        <Alert type="warning" showIcon message="对象不可访问" description={objectTypeText[summary.objectType] ?? '协作对象'} />
      ) : (
        <ObjectSummaryCard summary={summary} />
      )}
      {viewId ? <Tag className="doc-object-view-tag">视图 {viewId}</Tag> : null}
    </NodeViewWrapper>
  )
}

function LegacyEmbedNodeView({ node }: NodeViewProps) {
  const object = node.attrs.object && typeof node.attrs.object === 'object'
    ? node.attrs.object as Record<string, unknown>
    : {}
  const objectType = String(object.objectType ?? object.targetType ?? '协作对象')
  return (
    <NodeViewWrapper className="doc-object-card-node">
      <Alert type="warning" showIcon message="对象不可访问" description={objectTypeText[objectType] ?? objectType} />
    </NodeViewWrapper>
  )
}

function FileCardNodeView({ editor, node, updateAttributes }: NodeViewProps) {
  const fileId = String(node.attrs.fileId ?? '')
  const itemId = String(node.attrs.itemId ?? '')
  const replaceInputRef = useRef<HTMLInputElement | null>(null)
  const [replacing, setReplacing] = useState(false)
  const [replaceProgress, setReplaceProgress] = useState(0)
  const [replaceError, setReplaceError] = useState('')
  const metadataQuery = useQuery({
    queryKey: ['knowledge-content', 'file-card', fileId, 'metadata'],
    queryFn: () => getFileMetadata(fileId),
    enabled: Boolean(fileId),
  })
  const downloadQuery = useQuery({
    queryKey: ['knowledge-content', 'file-card', fileId, 'download'],
    queryFn: () => getFileDownloadUrl(fileId),
    enabled: Boolean(fileId && String(node.attrs.kind) === 'image'),
  })
  const fileName = metadataQuery.data?.originalName ?? String(node.attrs.fileName || '文件')
  const contentType = metadataQuery.data?.contentType ?? String(node.attrs.contentType || '')
  const sizeBytes = metadataQuery.data?.sizeBytes ?? Number(node.attrs.sizeBytes || 0)
  const isImage = String(node.attrs.kind) === 'image' || contentType.startsWith('image/')
  const caption = String(node.attrs.caption ?? '')
  const replaceFile = async (file: File) => {
    if (!itemId) {
      return
    }
    setReplacing(true)
    setReplaceProgress(0)
    setReplaceError('')
    try {
      const metadata = await uploadFileForTarget(file, 'knowledge_content', itemId, setReplaceProgress)
      updateAttributes({
        fileId: metadata.id,
        fileName: metadata.originalName,
        contentType: metadata.contentType,
        sizeBytes: metadata.sizeBytes,
        kind: metadata.contentType.startsWith('image/') ? 'image' : 'file',
        itemId,
      })
    } catch (error) {
      setReplaceError(error instanceof Error ? error.message : '替换文件失败')
    } finally {
      setReplacing(false)
    }
  }

  return (
    <NodeViewWrapper className={`doc-file-card-node${isImage ? ' image' : ''}`} data-file-id={fileId}>
      <input
        ref={replaceInputRef}
        type="file"
        hidden
        onChange={(event) => {
          const file = event.target.files?.[0]
          event.target.value = ''
          if (file) {
            void replaceFile(file)
          }
        }}
      />
      {isImage && downloadQuery.data?.downloadUrl ? (
        <img src={downloadQuery.data.downloadUrl} alt={fileName} />
      ) : (
        <FileOutlined />
      )}
      <div className="doc-file-card-meta">
        <strong>{fileName}</strong>
        <span>{contentType || 'application/octet-stream'} · {formatFileSize(sizeBytes)}</span>
        <Input
          size="small"
          aria-label={isImage ? '图片说明' : '文件说明'}
          value={caption}
          disabled={!editor.isEditable}
          placeholder={isImage ? '添加图片说明' : '添加文件说明'}
          onChange={(event) => updateAttributes({ caption: event.target.value })}
        />
      </div>
      {fileId ? (
        <Button
          size="small"
          onClick={() => {
            void getFileDownloadUrl(fileId).then((response) => window.open(response.downloadUrl, '_blank', 'noopener,noreferrer'))
          }}
        >
          下载
        </Button>
      ) : null}
      {itemId && editor.isEditable ? (
        <Button size="small" loading={replacing} onClick={() => replaceInputRef.current?.click()}>
          替换
        </Button>
      ) : null}
      {replacing ? <Progress className="doc-file-replace-progress" percent={replaceProgress} size="small" /> : null}
      {replaceError && editor.isEditable ? (
        <Button size="small" danger onClick={() => replaceInputRef.current?.click()} title={replaceError}>重试替换</Button>
      ) : null}
    </NodeViewWrapper>
  )
}

function syncEditorBlockDomAnchors(editor: Editor, blockAnchors: KnowledgeContentEditorBlockAnchor[]) {
  const root = editor.view.dom
  Array.from(root.children).forEach((child, index) => {
    const anchor = blockAnchors[index]
    if (!(child instanceof HTMLElement)) {
      return
    }
    child.classList.remove('doc-editor-block-highlight')
    if (!anchor?.id) {
      child.removeAttribute('id')
      child.removeAttribute('data-doc-block-id')
      child.removeAttribute('data-doc-block-anchor')
      return
    }
    child.id = `doc-block-${anchor.id}`
    child.dataset.docBlockId = anchor.id
    if (anchor.anchorId) {
      child.dataset.docBlockAnchor = anchor.anchorId
    } else {
      child.removeAttribute('data-doc-block-anchor')
    }
  })
  const hash = window.location.hash.replace(/^#/, '')
  if (hash.startsWith('doc-block-')) {
    highlightEditorBlock(hash)
  }
}

function highlightEditorBlock(elementId: string) {
  document.querySelectorAll('.doc-editor-block-highlight').forEach((element) => element.classList.remove('doc-editor-block-highlight'))
  const target = document.getElementById(elementId)
  if (!target) {
    return
  }
  target.classList.add('doc-editor-block-highlight')
  target.scrollIntoView({ block: 'center', behavior: 'smooth' })
}

function ToolbarButton({
  title,
  active,
  disabled,
  icon,
  children,
  onClick,
}: {
  title: string
  active: boolean
  disabled: boolean
  icon?: ReactNode
  children?: ReactNode
  onClick: () => void
}) {
  return (
    <Tooltip title={title}>
      <Button
        size="small"
        type={active ? 'primary' : 'default'}
        disabled={disabled}
        icon={icon}
        aria-label={title}
        aria-pressed={active}
        onClick={onClick}
      >
        {children}
      </Button>
    </Tooltip>
  )
}

function toggleLink(editor: Editor | null) {
  if (!editor) {
    return
  }
  if (editor.isActive('link')) {
    editor.chain().focus().unsetLink().run()
    return
  }
  const currentHref = editor.getAttributes('link').href as string | undefined
  const href = window.prompt('链接地址', currentHref ?? 'https://')
  if (!href) {
    return
  }
  editor.chain().focus().extendMarkRange('link').setLink({ href }).run()
}

function editorContainsText(editor: Editor, text: string) {
  return JSON.stringify(editor.getJSON()).includes(text)
}

function insertObjectCard(editor: Editor, summary: PlatformObjectSummary) {
  editor
    .chain()
    .focus()
    .insertContent({
      type: 'objectCard',
      attrs: {
        objectType: summary.objectType,
        objectId: summary.objectId,
        title: summary.title ?? '',
        subtitle: summary.subtitle ?? '',
        status: summary.status ?? '',
        webPath: summary.webPath ?? '',
        viewId: typeof summary.metadata?.viewId === 'string' ? summary.metadata.viewId : '',
      },
    })
    .run()
}

function removeSlashBeforeCursor(editor: Editor) {
  const { from } = editor.state.selection
  if (from <= 1) {
    return
  }
  const previous = editor.state.doc.textBetween(from - 1, from)
  if (previous === '/') {
    editor.commands.deleteRange({ from: from - 1, to: from })
  }
}

function editorCanResolveLink(value: string) {
  return /(^|\/)(issues|knowledge-bases|bases|base-records|im|files|approvals)\b/.test(value)
}

function sanitizePastedHtml(value: string) {
  const parsed = new DOMParser().parseFromString(value, 'text/html')
  parsed.querySelectorAll('script, style, iframe, object, embed, form, input, button').forEach((node) => node.remove())
  parsed.body.querySelectorAll<HTMLElement>('*').forEach((element) => {
    Array.from(element.attributes).forEach((attribute) => {
      const name = attribute.name.toLowerCase()
      const attributeValue = attribute.value.trim().toLowerCase()
      if (name.startsWith('on') || name === 'style' || ((name === 'href' || name === 'src') && attributeValue.startsWith('javascript:'))) {
        element.removeAttribute(attribute.name)
      }
    })
  })
  return parsed.body.innerHTML
}

function parseStructuredPaste(value: string): JSONContent[] | null {
  const lines = value.replace(/\r\n?/g, '\n').split('\n')
  const nonEmptyLines = lines.filter((line) => line.trim())
  if (nonEmptyLines.length === 0) {
    return null
  }
  if (nonEmptyLines.length >= 2 && nonEmptyLines.every((line) => line.includes('\t'))) {
    return [tableNodeFromRows(nonEmptyLines.map((line) => line.split('\t')))]
  }
  if (nonEmptyLines.length >= 2 && /^\s*\|?.+\|.+\|?\s*$/.test(nonEmptyLines[0]) && /^\s*\|?\s*:?-{3,}/.test(nonEmptyLines[1])) {
    const rows = nonEmptyLines
      .filter((_, index) => index !== 1)
      .map((line) => line.replace(/^\s*\|/, '').replace(/\|\s*$/, '').split('|').map((cell) => cell.trim()))
    return [tableNodeFromRows(rows, true)]
  }
  if (nonEmptyLines.every((line) => /^\s*[-*+]\s+/.test(line))) {
    return [{
      type: 'bulletList',
      content: nonEmptyLines.map((line) => ({ type: 'listItem', content: [paragraphJson(line.replace(/^\s*[-*+]\s+/, ''))] })),
    }]
  }
  if (nonEmptyLines.every((line) => /^\s*\d+[.)]\s+/.test(line))) {
    return [{
      type: 'orderedList',
      content: nonEmptyLines.map((line) => ({ type: 'listItem', content: [paragraphJson(line.replace(/^\s*\d+[.)]\s+/, ''))] })),
    }]
  }
  return null
}

function tableNodeFromRows(rows: string[][], header = false): JSONContent {
  const columnCount = Math.max(...rows.map((row) => row.length))
  return {
    type: 'table',
    content: rows.map((row, rowIndex) => ({
      type: 'tableRow',
      content: Array.from({ length: columnCount }, (_, columnIndex) => ({
        type: header && rowIndex === 0 ? 'tableHeader' : 'tableCell',
        content: [paragraphJson(row[columnIndex] ?? '')],
      })),
    })),
  }
}

function paragraphJson(text: string): JSONContent {
  return { type: 'paragraph', content: text ? [{ type: 'text', text }] : undefined }
}

function formatFileSize(sizeBytes: number) {
  if (!Number.isFinite(sizeBytes) || sizeBytes <= 0) {
    return '0 B'
  }
  if (sizeBytes < 1024) {
    return `${sizeBytes} B`
  }
  if (sizeBytes < 1024 * 1024) {
    return `${(sizeBytes / 1024).toFixed(1)} KB`
  }
  return `${(sizeBytes / 1024 / 1024).toFixed(1)} MB`
}

// Markdown import/export is handled by the dedicated API, outside the editor main path.
function permissionText(permission: string) {
  return { view: '可查看', edit: '可编辑', manage: '可管理' }[permission] ?? permission
}

function statusText(status: KnowledgeContentSaveState) {
  return {
    clean: '未修改',
    saved: '已保存',
    saving: '保存中',
    dirty: '未保存',
    conflict: '冲突',
    offline: '离线',
    error: '保存失败',
  }[status]
}

function statusColor(status: KnowledgeContentSaveState) {
  return {
    clean: 'default',
    saved: 'green',
    saving: 'blue',
    dirty: 'orange',
    conflict: 'red',
    offline: 'gold',
    error: 'red',
  }[status]
}

function knowledgeContentEditorStatsFromDocument(document: JSONContent, commentCount: number) {
  const nodes = document.content ?? []
  const embedCount = countDocumentNodes(document, (node) => ['objectCard', 'fileCard', 'image', 'table'].includes(node.type ?? ''))
  const blockCount = nodes.length
  const serializedLength = JSON.stringify(document).length
  return {
    blockCount,
    embedCount,
    commentCount,
    largeDocument: blockCount >= LARGE_DOCUMENT_BLOCK_THRESHOLD
      || embedCount >= LARGE_DOCUMENT_EMBED_THRESHOLD
      || commentCount >= LARGE_DOCUMENT_COMMENT_THRESHOLD
      || serializedLength >= 100_000,
  }
}

function countDocumentNodes(document: JSONContent, predicate: (node: JSONContent) => boolean): number {
  return (document.content ?? []).reduce((count, node) => count + Number(predicate(node)) + countDocumentNodes(node, predicate), 0)
}

function previewDocument(document: JSONContent, maxBlocks: number): JSONContent {
  const content = document.content ?? []
  return content.length > maxBlocks ? { ...document, content: content.slice(0, maxBlocks) } : document
}

function documentsEqual(left: JSONContent, right: JSONContent) {
  return JSON.stringify(left) === JSON.stringify(right)
}

function ensureEditorBlockIds(editor: Editor) {
  if (editor.isDestroyed) {
    return false
  }
  let changed = false
  const transaction = editor.state.tr
  editor.state.doc.forEach((node, position) => {
    if (node.attrs.blockId) {
      return
    }
    changed = true
    transaction.setNodeMarkup(position, undefined, {
      ...node.attrs,
      blockId: createEditorBlockId(),
    })
  })
  if (changed) {
    editor.view.dispatch(transaction)
  }
  return changed
}

function createEditorBlockId() {
  return globalThis.crypto?.randomUUID?.() ?? `client-${Date.now()}-${Math.random().toString(36).slice(2)}`
}
