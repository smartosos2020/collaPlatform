import { useMemo } from 'react'
import type { JSONContent } from '@tiptap/react'

import type { KnowledgeContentBlock, KnowledgeContentBlockDraft } from '../api/knowledgeContentApi'
import { KnowledgeContentEditorCore, type KnowledgeContentEditorBlockAnchor, type KnowledgeContentEditorCommentAnchor, type KnowledgeContentEditorSelectionAnchor, type KnowledgeContentSaveState } from './KnowledgeContentEditorCore'
import {
  blocksToTiptapDocument,
  tiptapDocumentToBlockDrafts,
} from '../editor/knowledgeContentAdapter'
import type { KnowledgeContentRealtimeSession } from '../hooks/useKnowledgeContentRealtimeCollaboration'

export type KnowledgeContentEditorProps = {
  itemId: string
  title: string
  blocks: Array<KnowledgeContentBlock | KnowledgeContentBlockDraft>
  canonicalDocument?: JSONContent
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
  onBlocksChange: (blocks: KnowledgeContentBlockDraft[]) => void
  onCommentAnchorChange?: (anchor?: KnowledgeContentEditorSelectionAnchor) => void
  onSave: () => void
  onRefresh: () => void
  onOpenLocalDraft: () => void
  onOpenPermission: () => void
  onOpenRelation: () => void
  collaboration?: KnowledgeContentRealtimeSession
}

export function KnowledgeContentEditor({
  blocks,
  canonicalDocument,
  onBlocksChange,
  ...props
}: KnowledgeContentEditorProps) {
  const document = useMemo(() => {
    const hasEditorSnapshot = blocks.some((block) => block.richContent && typeof block.richContent === 'object' && block.richContent.type === 'tiptap-node')
    return canonicalDocument && !hasEditorSnapshot ? canonicalDocument : blocksToTiptapDocument(blocks)
  }, [blocks, canonicalDocument])

  return (
    <KnowledgeContentEditorCore
      {...props}
      document={document}
      onDocumentChange={(nextDocument) => onBlocksChange(tiptapDocumentToBlockDrafts(nextDocument))}
    />
  )
}
