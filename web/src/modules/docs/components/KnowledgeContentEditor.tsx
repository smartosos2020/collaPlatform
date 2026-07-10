import { useMemo } from 'react'

import type { DocumentBlock, DocumentBlockDraft } from '../api/docsApi'
import type { DocumentCollaborationStatus, DocumentCollaborator, DocumentCursor } from '../hooks/useDocumentCollaboration'
import { DocEditor, type DocEditorBlockAnchor, type DocEditorCommentAnchor, type DocEditorSelectionAnchor } from './DocEditor'
import {
  blocksToMarkdown,
  markdownToBlockDrafts,
} from '../editor/knowledgeContentAdapter'

export type KnowledgeContentEditorProps = {
  documentId: string
  title: string
  blocks: Array<DocumentBlock | DocumentBlockDraft>
  fallbackContent: string
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
  blockAnchors?: DocEditorBlockAnchor[]
  activeCommentId?: string | null
  onTitleChange: (value: string) => void
  onBlocksChange: (blocks: DocumentBlockDraft[]) => void
  onSelectionChange?: (cursor: DocumentCursor) => void
  onCommentAnchorChange?: (anchor?: DocEditorSelectionAnchor) => void
  onSave: () => void
  onRefresh: () => void
  onOpenPermission: () => void
  onOpenRelation: () => void
}

export function KnowledgeContentEditor({
  blocks,
  fallbackContent,
  onBlocksChange,
  ...props
}: KnowledgeContentEditorProps) {
  const renderedContent = useMemo(() => {
    const blockContent = blocksToMarkdown(blocks)
    return blockContent || fallbackContent
  }, [blocks, fallbackContent])

  return (
    <DocEditor
      {...props}
      content={renderedContent}
      onContentChange={(nextContent) => onBlocksChange(preserveBlockIdentity(blocks, markdownToBlockDrafts(nextContent)))}
    />
  )
}

function preserveBlockIdentity(
  previousBlocks: Array<DocumentBlock | DocumentBlockDraft>,
  nextBlocks: DocumentBlockDraft[],
): DocumentBlockDraft[] {
  return nextBlocks.map((nextBlock, index) => {
    const previousBlock = previousBlocks[index]
    if (!previousBlock || previousBlock.blockType !== nextBlock.blockType) {
      return { ...nextBlock, sortOrder: index }
    }
    return {
      ...nextBlock,
      id: previousBlock.id ?? nextBlock.id,
      parentId: previousBlock.parentId ?? nextBlock.parentId,
      sortOrder: index,
      schemaVersion: nextBlock.schemaVersion ?? previousBlock.schemaVersion,
      attrs: { ...(previousBlock.attrs ?? {}), ...(nextBlock.attrs ?? {}) },
      richContent: nextBlock.richContent ?? previousBlock.richContent,
      anchorId: previousBlock.anchorId ?? nextBlock.anchorId,
    }
  })
}
