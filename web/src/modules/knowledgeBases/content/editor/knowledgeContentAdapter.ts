import type { JSONContent } from '@tiptap/react'

import type { KnowledgeContentBlock, KnowledgeContentBlockDraft } from '../api/knowledgeContentApi'

export type KnowledgeContentEditorBlock = KnowledgeContentBlock | KnowledgeContentBlockDraft

export function blocksToTiptapDocument(blocks: KnowledgeContentEditorBlock[]): JSONContent {
  const content = blocks
    .map(blockToTiptapNode)
    .filter(Boolean) as JSONContent[]
  return { type: 'doc', content: content.length > 0 ? content : [{ type: 'paragraph' }] }
}

export function tiptapDocumentToBlockDrafts(document: JSONContent): KnowledgeContentBlockDraft[] {
  const drafts = flattenTiptapNodes(document.content ?? [])
  return drafts.length > 0 ? drafts.map((block, index) => ({ ...block, sortOrder: index })) : [{ blockType: 'paragraph', content: '', sortOrder: 0 }]
}

function blockToTiptapNode(block: KnowledgeContentEditorBlock): JSONContent | null {
  const type = normalizeBlockType(block.blockType)
  const text = block.plainText || block.content || ''
  const richNode = block.richContent && typeof block.richContent === 'object'
    ? (block.richContent as { node?: JSONContent; editorNode?: JSONContent }).node
      ?? (block.richContent as { node?: JSONContent; editorNode?: JSONContent }).editorNode
    : undefined
  if (richNode?.type) return withBlockIdentity(richNode, block)
  if (type === 'divider') return withBlockIdentity({ type: 'horizontalRule' }, block)
  if (type === 'legacy_html') return withBlockIdentity(paragraphNode(text || stripHtml(block.content)), block)
  if (type === 'heading') return withBlockIdentity({ type: 'heading', attrs: { level: Number(block.attrs?.level ?? 1) }, content: inlineText(text) }, block)
  if (type === 'quote') return withBlockIdentity({ type: 'blockquote', content: [paragraphNode(text)] }, block)
  if (type === 'code_block') return withBlockIdentity({ type: 'codeBlock', attrs: { language: block.attrs?.language ?? null }, content: text ? [{ type: 'text', text }] : undefined }, block)
  if (type === 'bullet_list') return withBlockIdentity({ type: 'bulletList', content: [{ type: 'listItem', content: [paragraphNode(text)] }] }, block)
  if (type === 'ordered_list') return withBlockIdentity({ type: 'orderedList', attrs: { start: 1 }, content: [{ type: 'listItem', content: [paragraphNode(text)] }] }, block)
  if (type === 'task_item') return withBlockIdentity({ type: 'taskList', content: [{ type: 'taskItem', attrs: { checked: Boolean(block.attrs?.checked) }, content: [paragraphNode(text)] }] }, block)
  if (type === 'table') return withBlockIdentity(tableBlockToTiptap(block), block)
  if (type === 'image') return withBlockIdentity({ type: 'image', attrs: { src: String(block.attrs?.src ?? ''), alt: text } }, block)
  if (isEmbedBlockType(type)) return withBlockIdentity(objectBlockToTiptap(block, type), block)
  return withBlockIdentity(paragraphNode(text), block)
}

function flattenTiptapNodes(nodes: JSONContent[]): KnowledgeContentBlockDraft[] {
  const result: KnowledgeContentBlockDraft[] = []
  for (const node of nodes) {
    switch (node.type) {
      case 'heading':
        result.push(blockDraftFromNode(node, 'heading', textFromNode(node), { level: Number(node.attrs?.level ?? 1) }))
        break
      case 'blockquote':
        result.push(blockDraftFromNode(node, 'quote', textFromNode(node)))
        break
      case 'codeBlock':
        result.push(blockDraftFromNode(node, 'code_block', textFromNode(node), node.attrs ?? {}))
        break
      case 'bulletList':
        result.push(...listToDrafts(node, 'bullet_list'))
        break
      case 'orderedList':
        result.push(...listToDrafts(node, 'ordered_list'))
        break
      case 'taskList':
        result.push(...(node.content ?? []).map((item) => blockDraftFromNode(
          item,
          'task_item',
          textFromNode(item),
          { checked: Boolean(item.attrs?.checked) },
          node,
        )))
        break
      case 'table':
        result.push(blockDraftFromNode(node, 'table', JSON.stringify(tiptapTableToBlockData(node)), {}))
        break
      case 'horizontalRule':
        result.push(blockDraftFromNode(node, 'divider', ''))
        break
      case 'objectCard':
        result.push(withNodeIdentity(objectCardNodeToBlockDraft(node), node))
        break
      case 'fileCard':
        result.push(withNodeIdentity(fileCardNodeToBlockDraft(node), node))
        break
      case 'image':
        result.push(blockDraftFromNode(node, 'image', textFromNode(node), node.attrs ?? {}))
        break
      default:
        result.push(blockDraftFromNode(node, 'paragraph', textFromNode(node)))
    }
  }
  return result
}

function listToDrafts(node: JSONContent, blockType: 'bullet_list' | 'ordered_list') {
  return (node.content ?? []).map((item) => blockDraftFromNode(item, blockType, textFromNode(item), {}, node))
}

function blockDraftFromNode(
  node: JSONContent,
  blockType: KnowledgeContentBlock['blockType'],
  content: string,
  attrs: Record<string, unknown> = node.attrs ?? {},
  richNode: JSONContent = node,
): KnowledgeContentBlockDraft {
  return {
    ...nodeIdentity(node),
    blockType,
    content,
    schemaVersion: 3,
    attrs: { ...(node.attrs ?? {}), ...attrs },
    richContent: { type: 'tiptap-node', node: richNode },
    plainText: blockType === 'divider' ? '' : textFromNode(node),
  }
}

function withBlockIdentity(node: JSONContent, block: KnowledgeContentEditorBlock): JSONContent {
  return {
    ...node,
    attrs: {
      ...(node.attrs ?? {}),
      ...(block.attrs ?? {}),
      ...(block.id ? { blockId: block.id } : {}),
      ...(block.parentId ? { parentBlockId: block.parentId } : {}),
    },
  }
}

function nodeIdentity(node: JSONContent) {
  const blockId = typeof node.attrs?.blockId === 'string' && node.attrs.blockId.trim()
    ? node.attrs.blockId
    : createClientBlockId()
  return {
    id: blockId,
    parentId: typeof node.attrs?.parentBlockId === 'string' ? node.attrs.parentBlockId : undefined,
  }
}

function withNodeIdentity(draft: KnowledgeContentBlockDraft, node: JSONContent) {
  return {
    ...draft,
    ...nodeIdentity(node),
    schemaVersion: 3,
    attrs: { ...(draft.attrs ?? {}), ...(node.attrs ?? {}) },
    richContent: { type: 'tiptap-node', node },
  }
}

function createClientBlockId() {
  return globalThis.crypto?.randomUUID?.() ?? `client-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function normalizeBlockType(type: string): string {
  if (type === 'list' || type === 'bulleted_list') return 'bullet_list'
  if (type === 'numbered_list') return 'ordered_list'
  if (type === 'task' || type === 'todo') return 'task_item'
  if (type === 'code') return 'code_block'
  if (type === 'embed') return 'embed_object'
  if (type === 'link') return 'link_card'
  return type
}

function isEmbedBlockType(type: string) {
  return ['embed_object', 'base_view', 'issue_embed', 'message_embed', 'file_embed', 'link_card'].includes(type)
}

function objectBlockToTiptap(block: KnowledgeContentEditorBlock, type: string): JSONContent {
  const parsed = parseEmbedData(block.content)
  const objectType = parsed.objectType ?? objectTypeForBlockType(type)
  const summary = 'embedSummary' in block ? block.embedSummary : undefined
  return {
    type: 'objectCard',
    attrs: {
      objectType,
      objectId: parsed.objectId ?? '',
      title: summary?.accessState === 'available' ? summary.title ?? parsed.title ?? '' : '',
      subtitle: summary?.accessState === 'available' ? summary.subtitle ?? '' : '',
      status: summary?.accessState === 'available' ? summary.status ?? '' : '',
      webPath: summary?.accessState === 'available' ? summary.webPath ?? '' : '',
      viewId: parsed.viewId ?? '',
    },
  }
}

function objectCardNodeToBlockDraft(node: JSONContent): KnowledgeContentBlockDraft {
  const objectType = String(node.attrs?.objectType ?? 'issue')
  const objectId = String(node.attrs?.objectId ?? '')
  const viewId = String(node.attrs?.viewId ?? '')
  const blockType = blockTypeForObjectType(objectType)
  const content = JSON.stringify({
    objectType,
    objectId,
    viewId,
    title: String(node.attrs?.title ?? ''),
    webPath: String(node.attrs?.webPath ?? ''),
  })
  return {
    blockType,
    content,
    attrs: { objectType, objectId, viewId },
    plainText: String(node.attrs?.title || objectId || objectType),
  }
}

function fileCardNodeToBlockDraft(node: JSONContent): KnowledgeContentBlockDraft {
  const fileId = String(node.attrs?.fileId ?? '')
  const content = JSON.stringify({
    objectType: 'file',
    objectId: fileId,
    fileId,
    fileName: String(node.attrs?.fileName ?? ''),
    contentType: String(node.attrs?.contentType ?? ''),
    kind: String(node.attrs?.kind ?? 'file'),
  })
  return {
    blockType: 'file_embed',
    content,
    attrs: { objectType: 'file', objectId: fileId, fileId },
    plainText: String(node.attrs?.fileName || fileId || '文件'),
  }
}

function blockTypeForObjectType(objectType: string): KnowledgeContentBlockDraft['blockType'] {
  if (objectType === 'base_table') return 'base_view'
  if (objectType === 'issue') return 'issue_embed'
  if (objectType === 'message') return 'message_embed'
  if (objectType === 'file') return 'file_embed'
  if (objectType === 'external_link') return 'link_card'
  return 'embed_object'
}

function objectTypeForBlockType(blockType: string) {
  if (blockType === 'base_view') return 'base_table'
  if (blockType === 'issue_embed') return 'issue'
  if (blockType === 'message_embed') return 'message'
  if (blockType === 'file_embed') return 'file'
  if (blockType === 'link_card') return 'external_link'
  return 'document'
}

function parseEmbedData(content: string): Record<string, string> {
  try {
    const parsed = JSON.parse(content) as Record<string, unknown>
    return Object.fromEntries(Object.entries(parsed).map(([key, value]) => [key, value == null ? '' : String(value)]))
  } catch {
    return { objectId: content }
  }
}

function paragraphNode(text: string): JSONContent {
  return { type: 'paragraph', content: inlineText(text) }
}

function inlineText(text: string): JSONContent[] | undefined {
  return text ? [{ type: 'text', text }] : undefined
}

function textFromNode(node: JSONContent): string {
  if (node.type === 'text') return node.text ?? ''
  return (node.content ?? []).map(textFromNode).join(' ').replace(/\s+/g, ' ').trim()
}

function stripHtml(html: string) {
  return html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim()
}

function tableBlockToTiptap(block: KnowledgeContentEditorBlock): JSONContent {
  const data = parseTableData(block.content)
  const rows = [data.columns, ...data.rows]
  return {
    type: 'table',
    content: rows.map((row, rowIndex) => ({
      type: 'tableRow',
      content: row.map((cell) => ({
        type: rowIndex === 0 ? 'tableHeader' : 'tableCell',
        content: [paragraphNode(String(cell ?? ''))],
      })),
    })),
  }
}

function tiptapTableToBlockData(table: JSONContent) {
  const rows = (table.content ?? []).map((row) => (row.content ?? []).map((cell) => textFromNode(cell)))
  return { columns: rows[0] ?? [], rows: rows.slice(1) }
}

function parseTableData(content: string) {
  try {
    const parsed = JSON.parse(content) as { columns?: unknown[]; rows?: unknown[][] }
    return {
      columns: (parsed.columns ?? []).map(String),
      rows: (parsed.rows ?? []).map((row) => row.map(String)),
    }
  } catch {
    return { columns: ['列 1'], rows: [[content]] }
  }
}
