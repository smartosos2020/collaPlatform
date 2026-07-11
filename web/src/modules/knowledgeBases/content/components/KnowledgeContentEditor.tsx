import { useMemo } from 'react'

import type { KnowledgeContentBlock, KnowledgeContentBlockDraft } from '../api/knowledgeContentApi'
import type { KnowledgeContentCollaborationStatus, KnowledgeContentCollaborator, KnowledgeContentCursor } from '../hooks/useKnowledgeContentCollaboration'
import { KnowledgeContentEditorCore, type KnowledgeContentEditorBlockAnchor, type KnowledgeContentEditorCommentAnchor, type KnowledgeContentEditorSelectionAnchor } from './KnowledgeContentEditorCore'
import {
  blocksToMarkdown,
  markdownToBlockDrafts,
} from '../editor/knowledgeContentAdapter'

export type KnowledgeContentEditorProps = {
  itemId: string
  title: string
  blocks: Array<KnowledgeContentBlock | KnowledgeContentBlockDraft>
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
    status: KnowledgeContentCollaborationStatus
    onlineUsers: KnowledgeContentCollaborator[]
    remoteCursors: KnowledgeContentCollaborator[]
    lastSavedAt?: string | null
    error?: string | null
  }
  commentAnchors?: KnowledgeContentEditorCommentAnchor[]
  blockAnchors?: KnowledgeContentEditorBlockAnchor[]
  activeCommentId?: string | null
  onTitleChange: (value: string) => void
  onBlocksChange: (blocks: KnowledgeContentBlockDraft[]) => void
  onSelectionChange?: (cursor: KnowledgeContentCursor) => void
  onCommentAnchorChange?: (anchor?: KnowledgeContentEditorSelectionAnchor) => void
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
    <KnowledgeContentEditorCore
      {...props}
      content={renderedContent}
      onContentChange={(nextContent) => onBlocksChange(preserveBlockIdentity(blocks, markdownToBlockDrafts(nextContent)))}
    />
  )
}

function preserveBlockIdentity(
  previousBlocks: Array<KnowledgeContentBlock | KnowledgeContentBlockDraft>,
  nextBlocks: KnowledgeContentBlockDraft[],
): KnowledgeContentBlockDraft[] {
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
