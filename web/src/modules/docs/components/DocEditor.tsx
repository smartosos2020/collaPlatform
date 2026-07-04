import {
  AppstoreAddOutlined,
  BoldOutlined,
  CheckSquareOutlined,
  CodeOutlined,
  CommentOutlined,
  FileImageOutlined,
  FileOutlined,
  HolderOutlined,
  ItalicOutlined,
  LinkOutlined,
  MoreOutlined,
  OrderedListOutlined,
  PlusOutlined,
  SaveOutlined,
  ShareAltOutlined,
  StrikethroughOutlined,
  TableOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons'
import { Extension } from '@tiptap/core'
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
import { Plugin, PluginKey } from '@tiptap/pm/state'
import { Decoration, DecorationSet } from '@tiptap/pm/view'
import StarterKit from '@tiptap/starter-kit'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Dropdown, Input, Modal, Select, Space, Tag, Tooltip, Typography } from 'antd'
import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react'

import { uploadFileForTarget, getFileDownloadUrl, getFileMetadata } from '../../files/api/filesApi'
import { ObjectSummaryCard } from '../../platform/components/InternalLinkCard'
import { getObjectNavigation, resolveInternalLink, type PlatformObjectSummary } from '../../platform/api/platformObjectsApi'
import { objectTypeText } from '../../platform/objectTypeLabels'
import type { DocumentCollaborationStatus, DocumentCollaborator, DocumentCursor } from '../hooks/useDocumentCollaboration'

export type DocEditorProps = {
  documentId: string
  title: string
  content: string
  versionNo: number
  permissionLevel: string
  updatedAt: string
  canEdit: boolean
  canManage: boolean
  dirty: boolean
  saving: boolean
  conflictVisible: boolean
  saveActionLabel?: string
  saveActionHint?: string
  saveActionDisabled?: boolean
  collaboration?: {
    status: DocumentCollaborationStatus
    onlineUsers: DocumentCollaborator[]
    remoteCursors: DocumentCollaborator[]
    lastSavedAt?: string | null
    error?: string | null
  }
  commentAnchors?: DocEditorCommentAnchor[]
  activeCommentId?: string | null
  onTitleChange: (value: string) => void
  onContentChange: (value: string) => void
  onSelectionChange?: (cursor: DocumentCursor) => void
  onCommentAnchorChange?: (anchor?: DocEditorSelectionAnchor) => void
  onSave: () => void
  onRefresh: () => void
  onOpenPermission: () => void
  onOpenRelation: () => void
}

export type DocEditorCommentAnchor = {
  id: string
  anchorStart?: number | null
  anchorEnd?: number | null
  anchorText?: string | null
  resolved?: boolean
}

export type DocEditorSelectionAnchor = {
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
  icon: ReactNode
  run: () => void
}

const OBJECT_INSERT_TYPES = [
  { value: 'document', label: '知识内容' },
  { value: 'issue', label: '项目事项' },
  { value: 'message', label: '消息' },
  { value: 'base', label: 'Base' },
  { value: 'base_table', label: 'Base 视图/数据表' },
  { value: 'base_record', label: 'Base 记录' },
  { value: 'approval', label: '审批' },
  { value: 'file', label: '文件' },
]

const LARGE_DOCUMENT_BLOCK_THRESHOLD = 1000
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
            const anchors = transaction.getMeta(commentAnchorPluginKey) as DocEditorCommentAnchor[] | undefined
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
      documentId: { default: '' },
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

export function DocEditor({
  documentId,
  title,
  content,
  versionNo,
  permissionLevel,
  updatedAt,
  canEdit,
  canManage,
  dirty,
  saving,
  conflictVisible,
  saveActionLabel = '保存',
  saveActionHint,
  saveActionDisabled = false,
  collaboration,
  commentAnchors = [],
  activeCommentId,
  onTitleChange,
  onContentChange,
  onSelectionChange,
  onCommentAnchorChange,
  onSave,
  onRefresh,
  onOpenPermission,
  onOpenRelation,
}: DocEditorProps) {
  const onContentChangeRef = useRef(onContentChange)
  const onSelectionChangeRef = useRef(onSelectionChange)
  const onCommentAnchorChangeRef = useRef(onCommentAnchorChange)
  const onSaveRef = useRef(onSave)
  const loadedContentKeyRef = useRef('')
  const editorRef = useRef<Editor | null>(null)
  const imageInputRef = useRef<HTMLInputElement | null>(null)
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const documentStats = useMemo(() => documentEditorStats(content, commentAnchors.length), [commentAnchors.length, content])
  const [fullEditorState, setFullEditorState] = useState(() => ({
    documentId,
    largeDocument: documentStats.largeDocument,
    loaded: !documentStats.largeDocument,
  }))
  const fullEditorLoaded = fullEditorState.documentId === documentId && fullEditorState.largeDocument === documentStats.largeDocument
    ? fullEditorState.loaded
    : !documentStats.largeDocument
  const renderedContent = fullEditorLoaded ? content : previewMarkdown(content, LARGE_DOCUMENT_PREVIEW_BLOCKS)
  const [slashOpen, setSlashOpen] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [objectInsert, setObjectInsert] = useState<ObjectInsertState>({
    open: false,
    mode: 'object',
    objectType: 'issue',
    objectId: '',
    viewId: '',
    link: '',
  })

  useEffect(() => {
    onContentChangeRef.current = onContentChange
  }, [onContentChange])

  useEffect(() => {
    onSaveRef.current = onSave
  }, [onSave])

  useEffect(() => {
    onSelectionChangeRef.current = onSelectionChange
  }, [onSelectionChange])

  useEffect(() => {
    onCommentAnchorChangeRef.current = onCommentAnchorChange
  }, [onCommentAnchorChange])

  const syncContentFromEditor = useCallback((targetEditor: Editor) => {
    const sync = () => onContentChangeRef.current(tiptapDocumentToMarkdown(targetEditor.getJSON()))
    sync()
    window.setTimeout(sync, 0)
    window.setTimeout(sync, 50)
    window.setTimeout(sync, 250)
    window.setTimeout(sync, 1000)
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
        documentId: string
      },
    ) => {
      try {
        targetEditor
          .chain()
          .focus()
          .insertContent({
            type: 'fileCard',
            attrs,
          })
          .run()
        syncContentFromEditor(targetEditor)
      } catch (error) {
        console.warn('Failed to insert uploaded file into document editor', error)
      }
    },
    [syncContentFromEditor],
  )

  const uploadFileIntoEditor = useCallback(
    async (targetEditor: Editor | null, file: File, preferredKind?: 'image' | 'file') => {
      if (!targetEditor || targetEditor.isDestroyed) {
        return
      }
      setUploading(true)
      try {
        const metadata = await uploadFileForTarget(file, 'document', documentId)
        const activeEditor = targetEditor.isDestroyed ? editorRef.current : targetEditor
        if (!activeEditor || activeEditor.isDestroyed) {
          return
        }
        const kind = preferredKind ?? (metadata.contentType.startsWith('image/') ? 'image' : 'file')
        insertUploadedFileCard(activeEditor, {
          fileId: metadata.id,
          fileName: metadata.originalName,
          contentType: metadata.contentType,
          sizeBytes: metadata.sizeBytes,
          kind,
          documentId,
        })
        window.setTimeout(() => {
          const currentEditor = editorRef.current
          if (currentEditor && !currentEditor.isDestroyed && !editorContainsText(currentEditor, metadata.id)) {
            insertUploadedFileCard(currentEditor, {
              fileId: metadata.id,
              fileName: metadata.originalName,
              contentType: metadata.contentType,
              sizeBytes: metadata.sizeBytes,
              kind,
              documentId,
            })
          }
        }, 150)
      } finally {
        setUploading(false)
      }
    },
    [documentId, insertUploadedFileCard],
  )

  const insertResolvedLink = useCallback(
    async (targetEditor: Editor, link: string) => {
      const resolved = await resolveInternalLink(link)
      if (!resolved.summary) {
        return false
      }
      insertObjectCard(targetEditor, resolved.summary)
      syncContentFromEditor(targetEditor)
      return true
    },
    [syncContentFromEditor],
  )

  const extensions = useMemo(
    () => [
      StarterKit.configure({
        heading: { levels: [1, 2, 3] },
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
      CommentAnchorExtension,
    ],
    [],
  )

  const editor = useEditor(
    {
      extensions,
      content: markdownToTiptapDocument(renderedContent),
      editable: canEdit && fullEditorLoaded,
      immediatelyRender: false,
      editorProps: {
        attributes: {
          class: 'doc-prosemirror',
          role: 'textbox',
          'aria-label': '知识内容正文编辑器',
          'aria-multiline': 'true',
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
          const text = event.clipboardData?.getData('text/plain')?.trim()
          if (!text || !editorCanResolveLink(text)) {
            return false
          }
          event.preventDefault()
          if (!editorRef.current) {
            return false
          }
          void insertResolvedLink(editorRef.current, text)
          return true
        },
      },
      onUpdate: ({ editor: nextEditor }) => {
        if (!fullEditorLoaded) {
          return
        }
        onContentChangeRef.current(tiptapDocumentToMarkdown(nextEditor.getJSON()))
      },
      onSelectionUpdate: ({ editor: nextEditor }) => {
        const { from, to, empty } = nextEditor.state.selection
        onSelectionChangeRef.current?.({ from, to, empty })
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
    [extensions, fullEditorLoaded, renderedContent],
  )

  useEffect(() => {
    if (!editor || editor.isDestroyed) {
      return
    }
    editorRef.current = editor
    editor.setEditable(canEdit && fullEditorLoaded)
  }, [canEdit, editor, fullEditorLoaded])

  useEffect(() => {
    if (!editor) {
      return
    }
    const contentKey = `${documentId}:${versionNo}:${title}:${renderedContent}:${fullEditorLoaded}`
    if (loadedContentKeyRef.current === contentKey) {
      return
    }
    loadedContentKeyRef.current = contentKey
    if (tiptapDocumentToMarkdown(editor.getJSON()) !== renderedContent && !editor.isDestroyed) {
      editor.commands.setContent(markdownToTiptapDocument(renderedContent), { emitUpdate: false })
    }
  }, [documentId, editor, fullEditorLoaded, renderedContent, title, versionNo])

  useEffect(() => {
    if (!editor || editor.isDestroyed) {
      return
    }
    editor.view.dispatch(editor.state.tr.setMeta(commentAnchorPluginKey, commentAnchors))
  }, [commentAnchors, editor])

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

  const fallbackStatus = saving ? 'saving' : conflictVisible ? 'conflict' : dirty ? 'dirty' : 'saved'
  const statusLabel = collaboration ? collaborationStatusText(collaboration.status) : statusText(fallbackStatus)
  const statusColorValue = collaboration ? collaborationStatusColor(collaboration.status) : statusColor(fallbackStatus)
  const saveTooltip = saveActionHint ?? saveActionLabel
  const updateFallbackContent = (nextContent: string) => {
    onContentChange(nextContent)
    if (editor && !editor.isDestroyed) {
      editor.commands.setContent(markdownToTiptapDocument(nextContent), { emitUpdate: false })
    }
  }

  return (
    <section className="doc-editor-shell">
      <div className="doc-editor-topbar">
        <div className="doc-editor-title-group">
          <Input
            className="doc-title-input"
            disabled={!canEdit || !fullEditorLoaded}
            aria-label="知识内容标题"
            value={title}
            placeholder="Untitled"
            onChange={(event) => onTitleChange(event.target.value)}
          />
          <Space wrap>
            <Tag>v{versionNo}</Tag>
            <Tag>{permissionText(permissionLevel)}</Tag>
            <Tag color={statusColorValue}>{statusLabel}</Tag>
            {documentStats.largeDocument ? <Tag color="orange">大内容 {documentStats.blockCount} 块</Tag> : null}
            <Typography.Text type="secondary">更新于 {new Date(updatedAt).toLocaleString()}</Typography.Text>
            {collaboration?.lastSavedAt ? (
              <Typography.Text type="secondary">自动保存 {new Date(collaboration.lastSavedAt).toLocaleTimeString()}</Typography.Text>
            ) : null}
          </Space>
          {collaboration ? <CollaborationPresence users={collaboration.onlineUsers} /> : null}
        </div>
        <Space wrap className="doc-editor-actions">
          <Tooltip title={saveTooltip}>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              disabled={!canEdit || saveActionDisabled || !fullEditorLoaded}
              loading={saving}
              onClick={onSave}
            >
              {saveActionLabel}
            </Button>
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
          description="当前草稿基于旧版本。请刷新知识内容查看最新内容，再手动合并后保存。"
          action={<Button onClick={onRefresh}>刷新</Button>}
        />
      ) : null}
      {collaboration?.status === 'offline' ? (
        <Alert
          showIcon
          type="info"
          message="离线编辑中"
          description="当前改动会保留在本地编辑器中，连接恢复后会自动请求最新快照并尝试合并。"
        />
      ) : null}
      {documentStats.largeDocument && !fullEditorLoaded ? (
        <Alert
          showIcon
          type="info"
          message="大内容预览模式"
          description={`当前知识内容约 ${documentStats.blockCount} 块、${documentStats.embedCount} 个嵌入。已先渲染前 ${LARGE_DOCUMENT_PREVIEW_BLOCKS} 块，避免移动端或低性能设备卡顿。`}
          action={<Button onClick={() => setFullEditorState({ documentId, largeDocument: documentStats.largeDocument, loaded: true })}>加载完整编辑器</Button>}
        />
      ) : null}
      {collaboration?.error ? <Alert showIcon type="warning" message="协同异常" description={collaboration.error} /> : null}

      <div className="doc-editor-canvas">
        <EditorToolbar editor={editor} canEdit={canEdit && fullEditorLoaded} onOpenSlash={() => setSlashOpen(true)} />
        <TableToolbar editor={editor} canEdit={canEdit && fullEditorLoaded} />
        {collaboration?.remoteCursors.length ? <RemoteCursorStrip users={collaboration.remoteCursors} /> : null}
        <BlockDragHandle editor={editor} canEdit={canEdit} />
        {editor && canEdit && slashOpen ? (
          <SlashMenu
            editor={editor}
            onClose={() => setSlashOpen(false)}
            onPickImage={() => imageInputRef.current?.click()}
            onPickFile={() => fileInputRef.current?.click()}
            onInsertObject={(next) => setObjectInsert({ ...objectInsert, ...next, open: true })}
          />
        ) : null}
        {editor && canEdit ? <InlineBubbleMenu editor={editor} /> : null}
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
      {uploading ? <Typography.Text type="secondary">文件上传中...</Typography.Text> : null}
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
              syncContentFromEditor(editor)
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

      <details className="doc-editor-fallback">
        <summary>Markdown 兼容内容</summary>
        <Input.TextArea
          className="doc-markdown-input"
          disabled={!canEdit || !fullEditorLoaded}
          value={content}
          autoSize={{ minRows: 6, maxRows: 14 }}
          onChange={(event) => updateFallbackContent(event.target.value)}
        />
      </details>
    </section>
  )
}

function EditorToolbar({ editor, canEdit, onOpenSlash }: { editor: Editor | null; canEdit: boolean; onOpenSlash: () => void }) {
  return (
    <div className="doc-editor-toolbar" role="toolbar" aria-label="文档编辑工具栏">
      <Space wrap size={4}>
        <Tooltip title="插入块">
          <Button size="small" disabled={!canEdit || !editor} icon={<PlusOutlined />} onClick={onOpenSlash} />
        </Tooltip>
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
  if (!editor?.isActive('table')) {
    return null
  }
  return (
    <div className="doc-table-editor-toolbar" role="toolbar" aria-label="表格编辑工具栏">
      <Space wrap size={4}>
        <Button size="small" disabled={!canEdit} onClick={() => editor.chain().focus().addColumnAfter().run()}>
          加列
        </Button>
        <Button size="small" disabled={!canEdit} onClick={() => editor.chain().focus().deleteColumn().run()}>
          删列
        </Button>
        <Button size="small" disabled={!canEdit} onClick={() => editor.chain().focus().addRowAfter().run()}>
          加行
        </Button>
        <Button size="small" disabled={!canEdit} onClick={() => editor.chain().focus().deleteRow().run()}>
          删行
        </Button>
        <Button size="small" danger disabled={!canEdit} onClick={() => editor.chain().focus().deleteTable().run()}>
          删除表格
        </Button>
      </Space>
    </div>
  )
}

function BlockDragHandle({ editor, canEdit }: { editor: Editor | null; canEdit: boolean }) {
  const [activeBlock, setActiveBlock] = useState<{ pos: number } | null>(null)
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
      onNodeChange={({ pos }) => setActiveBlock(pos >= 0 ? { pos } : null)}
    >
      <Dropdown
        trigger={['click']}
        menu={{
          items: [
            { key: 'paragraph', label: '转为正文' },
            { key: 'heading', label: '转为标题' },
            { key: 'bullet', label: '转为列表' },
            { key: 'task', label: '转为任务' },
            { key: 'quote', label: '转为引用' },
            { key: 'code', label: '转为代码块' },
            { key: 'delete', label: '删除块', danger: true },
          ],
          onClick: ({ key }) => {
            focusBlock()
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
            if (key === 'code') {
              editor.chain().focus().toggleCodeBlock().run()
            }
            if (key === 'delete') {
              editor.chain().focus().deleteNode(editor.state.selection.$from.parent.type.name).run()
            }
          },
        }}
      >
        <Button size="small" icon={<HolderOutlined />} className="doc-block-handle-button">
          <MoreOutlined />
        </Button>
      </Dropdown>
    </DragHandle>
  )
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
  const runCommand = (command: () => void) => {
    removeSlashBeforeCursor(editor)
    command()
    onClose()
  }
  const commands: SlashCommand[] = [
    { key: 'paragraph', label: '文本', icon: <span>T</span>, run: () => editor.chain().focus().setParagraph().run() },
    { key: 'heading', label: '标题', icon: <span>H</span>, run: () => editor.chain().focus().toggleHeading({ level: 2 }).run() },
    { key: 'task', label: '任务', icon: <CheckSquareOutlined />, run: () => editor.chain().focus().toggleTaskList().run() },
    { key: 'bullet', label: '列表', icon: <UnorderedListOutlined />, run: () => editor.chain().focus().toggleBulletList().run() },
    { key: 'quote', label: '引用', icon: <span>&gt;</span>, run: () => editor.chain().focus().toggleBlockquote().run() },
    { key: 'code', label: '代码', icon: <CodeOutlined />, run: () => editor.chain().focus().toggleCodeBlock().run() },
    { key: 'table', label: '表格', icon: <TableOutlined />, run: () => editor.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: false }).run() },
    { key: 'image', label: '图片', icon: <FileImageOutlined />, run: onPickImage },
    { key: 'file', label: '文件', icon: <FileOutlined />, run: onPickFile },
    {
      key: 'base-view',
      label: 'Base 视图',
      icon: <AppstoreAddOutlined />,
      run: () => onInsertObject({ mode: 'object', objectType: 'base_table', objectId: '', viewId: '' }),
    },
    {
      key: 'issue',
      label: '项目事项',
      icon: <AppstoreAddOutlined />,
      run: () => onInsertObject({ mode: 'object', objectType: 'issue', objectId: '', viewId: '' }),
    },
    {
      key: 'message',
      label: '消息',
      icon: <AppstoreAddOutlined />,
      run: () => onInsertObject({ mode: 'object', objectType: 'message', objectId: '', viewId: '' }),
    },
    {
      key: 'link',
      label: '内部链接',
      icon: <LinkOutlined />,
      run: () => onInsertObject({ mode: 'link', link: '' }),
    },
  ]
  return (
    <div className="doc-slash-menu">
      {commands.map((command) => (
        <button key={command.key} type="button" onClick={() => runCommand(command.run)}>
          <span className="doc-slash-menu-icon">{command.icon}</span>
          <span>{command.label}</span>
        </button>
      ))}
    </div>
  )
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
  const objectQuery = useQuery({
    queryKey: ['docs', 'object-card', objectType, objectId],
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
    metadata: node.attrs.viewId ? { viewId: node.attrs.viewId } : {},
  }
  const summary = objectQuery.data?.summary ?? fallbackSummary

  return (
    <NodeViewWrapper className="doc-object-card-node">
      {objectQuery.isError ? (
        <Alert type="warning" showIcon message="对象不可用" description={`${objectTypeText[objectType] ?? objectType}: ${objectId}`} />
      ) : (
        <ObjectSummaryCard summary={summary} />
      )}
      {node.attrs.viewId ? <Tag className="doc-object-view-tag">视图 {String(node.attrs.viewId)}</Tag> : null}
    </NodeViewWrapper>
  )
}

function FileCardNodeView({ node, updateAttributes }: NodeViewProps) {
  const fileId = String(node.attrs.fileId ?? '')
  const documentId = String(node.attrs.documentId ?? '')
  const replaceInputRef = useRef<HTMLInputElement | null>(null)
  const [replacing, setReplacing] = useState(false)
  const metadataQuery = useQuery({
    queryKey: ['docs', 'file-card', fileId, 'metadata'],
    queryFn: () => getFileMetadata(fileId),
    enabled: Boolean(fileId),
  })
  const downloadQuery = useQuery({
    queryKey: ['docs', 'file-card', fileId, 'download'],
    queryFn: () => getFileDownloadUrl(fileId),
    enabled: Boolean(fileId && String(node.attrs.kind) === 'image'),
  })
  const fileName = metadataQuery.data?.originalName ?? String(node.attrs.fileName || '文件')
  const contentType = metadataQuery.data?.contentType ?? String(node.attrs.contentType || '')
  const sizeBytes = metadataQuery.data?.sizeBytes ?? Number(node.attrs.sizeBytes || 0)
  const isImage = String(node.attrs.kind) === 'image' || contentType.startsWith('image/')
  const replaceFile = async (file: File) => {
    if (!documentId) {
      return
    }
    setReplacing(true)
    try {
      const metadata = await uploadFileForTarget(file, 'document', documentId)
      updateAttributes({
        fileId: metadata.id,
        fileName: metadata.originalName,
        contentType: metadata.contentType,
        sizeBytes: metadata.sizeBytes,
        kind: metadata.contentType.startsWith('image/') ? 'image' : 'file',
        documentId,
      })
    } finally {
      setReplacing(false)
    }
  }

  return (
    <NodeViewWrapper className={`doc-file-card-node${isImage ? ' image' : ''}`}>
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
      {documentId ? (
        <Button size="small" loading={replacing} onClick={() => replaceInputRef.current?.click()}>
          替换
        </Button>
      ) : null}
    </NodeViewWrapper>
  )
}

function CollaborationPresence({ users }: { users: DocumentCollaborator[] }) {
  if (users.length === 0) {
    return null
  }
  return (
    <div className="doc-collab-presence">
      {users.slice(0, 8).map((user) => (
        <span className="doc-collab-user" key={user.clientId || user.userId} style={{ '--collab-color': user.color } as CSSProperties}>
          <i />
          {user.displayName || user.username}
        </span>
      ))}
      {users.length > 8 ? <Tag>+{users.length - 8}</Tag> : null}
    </div>
  )
}

function RemoteCursorStrip({ users }: { users: DocumentCollaborator[] }) {
  return (
    <div className="doc-remote-cursor-strip">
      {users.slice(0, 6).map((user) => (
        <span className="doc-remote-cursor-chip" key={user.clientId || user.userId} style={{ '--cursor-color': user.color } as CSSProperties}>
          {user.displayName || user.username}
          {user.cursor ? <small>{user.cursor.from === user.cursor.to ? user.cursor.from : `${user.cursor.from}-${user.cursor.to}`}</small> : null}
        </span>
      ))}
    </div>
  )
}

function InlineBubbleMenu({ editor }: { editor: Editor }) {
  return (
    <BubbleMenu editor={editor} className="doc-bubble-menu">
      <Space size={2}>
        <Tooltip title="评论选区">
          <Button
            size="small"
            icon={<CommentOutlined />}
            onClick={() => document.querySelector<HTMLElement>('.doc-comment-composer')?.focus()}
          />
        </Tooltip>
        <Button
          size="small"
          type={editor.isActive('bold') ? 'primary' : 'default'}
          icon={<BoldOutlined />}
          onClick={() => editor.chain().focus().toggleBold().run()}
        />
        <Button
          size="small"
          type={editor.isActive('italic') ? 'primary' : 'default'}
          icon={<ItalicOutlined />}
          onClick={() => editor.chain().focus().toggleItalic().run()}
        />
        <Button
          size="small"
          type={editor.isActive('strike') ? 'primary' : 'default'}
          icon={<StrikethroughOutlined />}
          onClick={() => editor.chain().focus().toggleStrike().run()}
        />
        <Button
          size="small"
          type={editor.isActive('link') ? 'primary' : 'default'}
          icon={<LinkOutlined />}
          onClick={() => toggleLink(editor)}
        />
      </Space>
    </BubbleMenu>
  )
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
  return /(^|\/)(issues|docs|bases|base-records|im|files|approvals)\b/.test(value)
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

function markdownToTiptapDocument(markdown: string): JSONContent {
  const lines = markdown.replace(/\r\n/g, '\n').split('\n')
  const content: JSONContent[] = []
  let index = 0

  while (index < lines.length) {
    const line = lines[index]
    if (!line.trim()) {
      index += 1
      continue
    }

    const objectCard = parseDirective(line, 'object-card')
    if (objectCard) {
      content.push({
        type: 'objectCard',
        attrs: {
          objectType: objectCard.objectType ?? objectCard.type ?? 'issue',
          objectId: objectCard.objectId ?? objectCard.id ?? '',
          title: objectCard.title ?? '',
          subtitle: objectCard.subtitle ?? '',
          status: objectCard.status ?? '',
          webPath: objectCard.webPath ?? '',
          viewId: objectCard.viewId ?? '',
        },
      })
      index += 1
      continue
    }

    const fileCard = parseDirective(line, 'file-card')
    if (fileCard) {
      content.push({
        type: 'fileCard',
        attrs: {
          fileId: fileCard.fileId ?? fileCard.id ?? '',
          fileName: fileCard.fileName ?? fileCard.name ?? '',
          contentType: fileCard.contentType ?? '',
          sizeBytes: Number(fileCard.sizeBytes ?? 0),
          kind: fileCard.kind ?? 'file',
          documentId: fileCard.documentId ?? '',
        },
      })
      index += 1
      continue
    }

    const image = line.match(/^!\[([^\]]*)\]\(([^)\s]+)(?:\s+"([^"]+)")?\)$/)
    if (image) {
      content.push({
        type: 'image',
        attrs: {
          alt: image[1],
          src: image[2],
          title: image[3] ?? image[1],
        },
      })
      index += 1
      continue
    }

    const table = parseMarkdownTable(lines, index)
    if (table) {
      content.push(table.node)
      index = table.nextIndex
      continue
    }

    const fence = line.match(/^```(\w+)?\s*$/)
    if (fence) {
      const codeLines: string[] = []
      index += 1
      while (index < lines.length && !lines[index].startsWith('```')) {
        codeLines.push(lines[index])
        index += 1
      }
      index += index < lines.length ? 1 : 0
      content.push({
        type: 'codeBlock',
        attrs: fence[1] ? { language: fence[1] } : undefined,
        content: [{ type: 'text', text: codeLines.join('\n') }],
      })
      continue
    }

    const heading = line.match(/^(#{1,3})\s+(.+)$/)
    if (heading) {
      content.push({
        type: 'heading',
        attrs: { level: heading[1].length },
        content: parseInlineMarkdown(heading[2]),
      })
      index += 1
      continue
    }

    if (/^>\s?/.test(line)) {
      const quoteLines: string[] = []
      while (index < lines.length && /^>\s?/.test(lines[index])) {
        quoteLines.push(lines[index].replace(/^>\s?/, ''))
        index += 1
      }
      content.push({
        type: 'blockquote',
        content: quoteLines.map((quoteLine) => paragraphNode(quoteLine)),
      })
      continue
    }

    if (/^\s*[-*]\s+\[[ xX]\]\s+/.test(line)) {
      const items: JSONContent[] = []
      while (index < lines.length && /^\s*[-*]\s+\[[ xX]\]\s+/.test(lines[index])) {
        const task = lines[index].match(/^\s*[-*]\s+\[([ xX])\]\s+(.+)$/)
        if (task) {
          items.push({
            type: 'taskItem',
            attrs: { checked: task[1].toLowerCase() === 'x' },
            content: [paragraphNode(task[2])],
          })
        }
        index += 1
      }
      content.push({ type: 'taskList', content: items })
      continue
    }

    if (/^\s*[-*]\s+/.test(line)) {
      const items: JSONContent[] = []
      while (index < lines.length && /^\s*[-*]\s+/.test(lines[index])) {
        const itemText = lines[index].replace(/^\s*[-*]\s+/, '')
        items.push({ type: 'listItem', content: [paragraphNode(itemText)] })
        index += 1
      }
      content.push({ type: 'bulletList', content: items })
      continue
    }

    if (/^\s*\d+[.)]\s+/.test(line)) {
      const items: JSONContent[] = []
      while (index < lines.length && /^\s*\d+[.)]\s+/.test(lines[index])) {
        const itemText = lines[index].replace(/^\s*\d+[.)]\s+/, '')
        items.push({ type: 'listItem', content: [paragraphNode(itemText)] })
        index += 1
      }
      content.push({ type: 'orderedList', attrs: { start: 1 }, content: items })
      continue
    }

    content.push(paragraphNode(line))
    index += 1
  }

  return {
    type: 'doc',
    content: content.length > 0 ? content : [{ type: 'paragraph' }],
  }
}

function paragraphNode(text: string): JSONContent {
  return {
    type: 'paragraph',
    content: parseInlineMarkdown(text),
  }
}

function parseInlineMarkdown(value: string): JSONContent[] | undefined {
  if (!value) {
    return undefined
  }
  const tokenPattern = /(\*\*([^*]+)\*\*|~~([^~]+)~~|`([^`]+)`|\[([^\]]+)\]\(([^)\s]+)\)|\*([^*]+)\*)/g
  const nodes: JSONContent[] = []
  let cursor = 0
  let match: RegExpExecArray | null

  while ((match = tokenPattern.exec(value)) !== null) {
    if (match.index > cursor) {
      nodes.push(textNode(value.slice(cursor, match.index)))
    }
    if (match[2]) {
      nodes.push(textNode(match[2], [{ type: 'bold' }]))
    } else if (match[3]) {
      nodes.push(textNode(match[3], [{ type: 'strike' }]))
    } else if (match[4]) {
      nodes.push(textNode(match[4], [{ type: 'code' }]))
    } else if (match[5] && match[6]) {
      nodes.push(textNode(match[5], [{ type: 'link', attrs: { href: match[6] } }]))
    } else if (match[7]) {
      nodes.push(textNode(match[7], [{ type: 'italic' }]))
    }
    cursor = match.index + match[0].length
  }

  if (cursor < value.length) {
    nodes.push(textNode(value.slice(cursor)))
  }
  return nodes.length > 0 ? nodes : undefined
}

function textNode(text: string, marks?: JSONContent['marks']): JSONContent {
  return marks ? { type: 'text', text, marks } : { type: 'text', text }
}

function parseDirective(line: string, name: string) {
  const prefix = `::${name}{`
  if (!line.startsWith(prefix) || !line.endsWith('}')) {
    return null
  }
  const body = line.slice(prefix.length, -1)
  const attrs: Record<string, string> = {}
  const attrPattern = /(\w+)="([^"]*)"/g
  let attrMatch: RegExpExecArray | null
  while ((attrMatch = attrPattern.exec(body)) !== null) {
    attrs[attrMatch[1]] = decodeURIComponent(attrMatch[2])
  }
  return attrs
}

function serializeDirective(name: string, attrs: Record<string, string>) {
  const serialized = Object.entries(attrs)
    .filter(([, value]) => value !== '')
    .map(([key, value]) => `${key}="${encodeURIComponent(value)}"`)
    .join(' ')
  return `::${name}{${serialized}}`
}

function parseMarkdownTable(lines: string[], startIndex: number): { node: JSONContent; nextIndex: number } | null {
  if (startIndex + 1 >= lines.length || !isTableLine(lines[startIndex]) || !isTableSeparator(lines[startIndex + 1])) {
    return null
  }
  const header = splitTableLine(lines[startIndex])
  const rows: string[][] = []
  let index = startIndex + 2
  while (index < lines.length && isTableLine(lines[index])) {
    rows.push(splitTableLine(lines[index]))
    index += 1
  }
  const width = Math.max(header.length, ...rows.map((row) => row.length), 1)
  return {
    node: {
      type: 'table',
      content: [
        {
          type: 'tableRow',
          content: normalizeTableCells(header, width).map((cell) => tableCellNode('tableHeader', cell)),
        },
        ...rows.map((row) => ({
          type: 'tableRow',
          content: normalizeTableCells(row, width).map((cell) => tableCellNode('tableCell', cell)),
        })),
      ],
    },
    nextIndex: index,
  }
}

function tableCellNode(type: 'tableHeader' | 'tableCell', value: string): JSONContent {
  return {
    type,
    content: [paragraphNode(value)],
  }
}

function normalizeTableCells(cells: string[], width: number) {
  return Array.from({ length: width }, (_, index) => cells[index] ?? '')
}

function isTableLine(line: string) {
  return line.trim().startsWith('|') && line.trim().endsWith('|')
}

function isTableSeparator(line: string) {
  return /^\|?(\s*:?-{3,}:?\s*\|)+\s*$/.test(line.trim())
}

function splitTableLine(line: string) {
  return line
    .trim()
    .replace(/^\|/, '')
    .replace(/\|$/, '')
    .split('|')
    .map((cell) => cell.trim().replace(/\\\|/g, '|'))
}

function tiptapDocumentToMarkdown(document: JSONContent): string {
  return (document.content ?? [])
    .map((node, index) => serializeBlockNode(node, index))
    .filter((line) => line.length > 0)
    .join('\n\n')
}

function serializeBlockNode(node: JSONContent, index = 0): string {
  switch (node.type) {
    case 'heading':
      return `${'#'.repeat(Number(node.attrs?.level ?? 1))} ${serializeInlineNodes(node.content)}`
    case 'paragraph':
      return serializeInlineNodes(node.content)
    case 'blockquote':
      return (node.content ?? [])
        .map((child) => serializeBlockNode(child))
        .join('\n')
        .split('\n')
        .map((line) => `> ${line}`)
        .join('\n')
    case 'bulletList':
      return serializeListItems(node.content, '-')
    case 'orderedList':
      return serializeOrderedItems(node.content, Number(node.attrs?.start ?? 1))
    case 'taskList':
      return serializeTaskItems(node.content)
    case 'listItem':
      return serializeListItem(node)
    case 'taskItem':
      return `- [${node.attrs?.checked ? 'x' : ' '}] ${serializeListItem(node)}`
    case 'table':
      return serializeTableNode(node)
    case 'objectCard':
      return serializeDirective('object-card', {
        objectType: String(node.attrs?.objectType ?? ''),
        objectId: String(node.attrs?.objectId ?? ''),
        title: String(node.attrs?.title ?? ''),
        subtitle: String(node.attrs?.subtitle ?? ''),
        status: String(node.attrs?.status ?? ''),
        webPath: String(node.attrs?.webPath ?? ''),
        viewId: String(node.attrs?.viewId ?? ''),
      })
    case 'fileCard':
      return serializeDirective('file-card', {
        fileId: String(node.attrs?.fileId ?? ''),
        fileName: String(node.attrs?.fileName ?? ''),
        contentType: String(node.attrs?.contentType ?? ''),
        sizeBytes: String(node.attrs?.sizeBytes ?? 0),
        kind: String(node.attrs?.kind ?? 'file'),
        documentId: String(node.attrs?.documentId ?? ''),
      })
    case 'image':
      return `![${String(node.attrs?.alt ?? '')}](${String(node.attrs?.src ?? '')}${node.attrs?.title ? ` "${String(node.attrs.title)}"` : ''})`
    case 'codeBlock':
      return `\`\`\`${node.attrs?.language ?? ''}\n${serializeInlineNodes(node.content)}\n\`\`\``
    case 'horizontalRule':
      return '---'
    default:
      return serializeInlineNodes(node.content) || (index === 0 ? '' : '')
  }
}

function serializeListItems(items: JSONContent[] | undefined, marker: string) {
  return (items ?? []).map((item) => `${marker} ${serializeListItem(item)}`).join('\n')
}

function serializeOrderedItems(items: JSONContent[] | undefined, start: number) {
  return (items ?? []).map((item, index) => `${start + index}. ${serializeListItem(item)}`).join('\n')
}

function serializeTaskItems(items: JSONContent[] | undefined) {
  return (items ?? [])
    .map((item) => `- [${item.attrs?.checked ? 'x' : ' '}] ${serializeListItem(item)}`)
    .join('\n')
}

function serializeListItem(item: JSONContent) {
  return (item.content ?? []).map((child) => serializeBlockNode(child)).join('\n')
}

function serializeTableNode(table: JSONContent) {
  const rows = (table.content ?? []).map((row) =>
    (row.content ?? []).map((cell) => serializeInlineNodesFromBlock(cell).replace(/\|/g, '\\|')),
  )
  if (rows.length === 0) {
    return ''
  }
  const width = Math.max(...rows.map((row) => row.length), 1)
  const normalizedRows = rows.map((row) => normalizeTableCells(row, width))
  const header = normalizedRows[0]
  const body = normalizedRows.slice(1)
  return [
    `| ${header.join(' | ')} |`,
    `| ${header.map(() => '---').join(' | ')} |`,
    ...body.map((row) => `| ${row.join(' | ')} |`),
  ].join('\n')
}

function serializeInlineNodesFromBlock(node: JSONContent): string {
  if (node.type === 'paragraph') {
    return serializeInlineNodes(node.content)
  }
  return (node.content ?? []).map((child) => serializeInlineNodesFromBlock(child)).join(' ')
}

function serializeInlineNodes(nodes: JSONContent[] | undefined): string {
  return (nodes ?? []).map(serializeInlineNode).join('')
}

function serializeInlineNode(node: JSONContent): string {
  if (node.type === 'text') {
    return applyMarks(node.text ?? '', node.marks)
  }
  if (node.type === 'hardBreak') {
    return '\n'
  }
  return serializeInlineNodes(node.content)
}

function applyMarks(text: string, marks: JSONContent['marks']) {
  return (marks ?? []).reduce((current, mark) => {
    if (mark.type === 'bold') {
      return `**${current}**`
    }
    if (mark.type === 'italic') {
      return `*${current}*`
    }
    if (mark.type === 'strike') {
      return `~~${current}~~`
    }
    if (mark.type === 'code') {
      return `\`${current}\``
    }
    if (mark.type === 'link') {
      return `[${current}](${mark.attrs?.href ?? ''})`
    }
    return current
  }, text)
}

function permissionText(permission: string) {
  return { view: '可查看', edit: '可编辑', manage: '可管理' }[permission] ?? permission
}

function statusText(status: 'saved' | 'saving' | 'dirty' | 'conflict') {
  return {
    saved: '已保存',
    saving: '保存中',
    dirty: '未保存',
    conflict: '冲突',
  }[status]
}

function statusColor(status: 'saved' | 'saving' | 'dirty' | 'conflict') {
  return {
    saved: 'green',
    saving: 'blue',
    dirty: 'orange',
    conflict: 'red',
  }[status]
}

function collaborationStatusText(status: DocumentCollaborationStatus) {
  return {
    idle: '协同待连接',
    connecting: '协同连接中',
    joined: '协同已加入',
    dirty: '协同未同步',
    saving: '协同保存中',
    synced: '自动保存',
    offline: '离线待同步',
    error: '协同异常',
  }[status]
}

function collaborationStatusColor(status: DocumentCollaborationStatus) {
  return {
    idle: 'default',
    connecting: 'blue',
    joined: 'blue',
    dirty: 'orange',
    saving: 'blue',
    synced: 'green',
    offline: 'default',
    error: 'red',
  }[status]
}

function documentEditorStats(content: string, commentCount: number) {
  const lines = content.split(/\r?\n/)
  const meaningfulLines = lines.filter((line) => line.trim().length > 0)
  const embedCount = meaningfulLines.filter((line) => {
    const normalized = line.trim()
    return normalized.startsWith('[table] ')
      || normalized.startsWith('[embed] ')
      || normalized.startsWith('[base_view] ')
      || normalized.startsWith('[issue_embed] ')
      || normalized.startsWith('[message_embed] ')
      || normalized.startsWith('[file_embed] ')
      || normalized.startsWith('[link] ')
  }).length
  const blockCount = Math.max(meaningfulLines.length, content.trim() ? 1 : 0)
  return {
    blockCount,
    embedCount,
    commentCount,
    largeDocument: blockCount >= LARGE_DOCUMENT_BLOCK_THRESHOLD
      || embedCount >= LARGE_DOCUMENT_EMBED_THRESHOLD
      || commentCount >= LARGE_DOCUMENT_COMMENT_THRESHOLD
      || content.length >= 100_000,
  }
}

function previewMarkdown(content: string, maxBlocks: number) {
  const lines = content.split(/\r?\n/)
  let usedBlocks = 0
  const preview: string[] = []
  for (const line of lines) {
    if (line.trim()) {
      usedBlocks += 1
    }
    if (usedBlocks > maxBlocks) {
      break
    }
    preview.push(line)
  }
  if (preview.length < lines.length) {
    preview.push('')
    preview.push(`> 已进入大内容预览模式，仅显示前 ${maxBlocks} 块。加载完整编辑器后可继续编辑。`)
  }
  return preview.join('\n')
}
