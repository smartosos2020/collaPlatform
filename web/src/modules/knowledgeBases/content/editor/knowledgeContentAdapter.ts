import type { JSONContent } from '@tiptap/react'

import type { KnowledgeContentBlock, KnowledgeContentBlockDraft } from '../api/knowledgeContentApi'

export type KnowledgeEditorCapability = {
  key: string
  label: string
  status: 'ready' | 'watch' | 'defer'
  evidence: string
}

export type KnowledgeEditorSpikeReport = {
  selectedEditor: 'tiptap'
  fallbackEditor: 'lexical'
  rejectedEditors: Array<{ name: 'blocknote' | 'lexical'; reason: string }>
  capabilities: KnowledgeEditorCapability[]
  risks: KnowledgeEditorCapability[]
}

export function blocksToTiptapDocument(blocks: KnowledgeContentBlock[]): JSONContent {
  const content = blocks
    .filter((block) => block.blockType !== 'divider')
    .map(blockToTiptapNode)
    .filter(Boolean) as JSONContent[]
  return { type: 'doc', content: content.length > 0 ? content : [{ type: 'paragraph' }] }
}

export function tiptapDocumentToBlockDrafts(document: JSONContent): KnowledgeContentBlockDraft[] {
  const drafts = flattenTiptapNodes(document.content ?? [])
  return drafts.length > 0 ? drafts.map((block, index) => ({ ...block, sortOrder: index })) : [{ blockType: 'paragraph', content: '', sortOrder: 0 }]
}

export function blocksToMarkdown(blocks: Array<KnowledgeContentBlock | KnowledgeContentBlockDraft>) {
  return blocks.map(blockToMarkdown).filter((line) => line.length > 0).join('\n')
}

export function markdownToBlockDrafts(markdown: string): KnowledgeContentBlockDraft[] {
  const lines = markdown.replace(/\r\n/g, '\n').split('\n')
  const drafts: KnowledgeContentBlockDraft[] = []
  for (const line of lines) {
    const trimmed = line.trim()
    if (!trimmed) continue
    drafts.push(markdownLineToDraft(trimmed, drafts.length))
  }
  return drafts.length > 0 ? drafts : [{ blockType: 'paragraph', content: '', sortOrder: 0 }]
}

export function createKnowledgeEditorSpikeReport(): KnowledgeEditorSpikeReport {
  return {
    selectedEditor: 'tiptap',
    fallbackEditor: 'lexical',
    rejectedEditors: [
      { name: 'blocknote', reason: 'Block API fits the goal, but it adds a larger opinionated UI layer over the existing Ant Design/Tiptap stack.' },
      { name: 'lexical', reason: 'Good long-term editor core, but would duplicate the current Tiptap integration and require more node/plugin work for tables and object cards.' },
    ],
    capabilities: [
      { key: 'react', label: 'React integration', status: 'ready', evidence: 'Project already ships @tiptap/react 3.26.1 and DocEditor runs on it.' },
      { key: 'block-schema', label: 'Block schema mapping', status: 'ready', evidence: 'blocksToTiptapDocument and tiptapDocumentToBlockDrafts cover text, lists, tasks, code, quote, table, divider and legacy_html.' },
      { key: 'slash', label: 'Slash menu', status: 'ready', evidence: 'DocEditor already has slash command UI and can be moved behind KnowledgeContentEditor.' },
      { key: 'embeds', label: 'Object embeds', status: 'watch', evidence: 'Object/file cards exist; Base and generic embed_object need M8/M9 UI mapping.' },
      { key: 'comments', label: 'Comment anchors', status: 'watch', evidence: 'Backend block IDs and anchors exist; front-end selection to block ID binding is deferred to M10.' },
      { key: 'collaboration', label: 'Collaboration', status: 'defer', evidence: 'Current snapshot-v1 path remains; Yjs-style updates are not introduced in M7.' },
    ],
    risks: [
      { key: 'ime', label: 'Chinese IME', status: 'watch', evidence: 'Keep composition events inside Tiptap; avoid replacing editor node during composing.' },
      { key: 'paste', label: 'Paste fidelity', status: 'watch', evidence: 'Markdown/table parsing is acceptable for spike; rich HTML paste needs M11 import/export rules.' },
      { key: 'undo', label: 'Undo/redo', status: 'ready', evidence: 'Tiptap/ProseMirror history is already part of StarterKit.' },
      { key: 'drag', label: 'Drag and long document performance', status: 'watch', evidence: 'DragHandle exists; large docs should keep lazy-preview threshold and avoid full remount.' },
      { key: 'mobile', label: 'Mobile input', status: 'watch', evidence: 'M7 keeps existing mobile behavior; M8 must smoke-test actual knowledge page input.' },
    ],
  }
}

function blockToTiptapNode(block: KnowledgeContentBlock): JSONContent | null {
  const type = normalizeBlockType(block.blockType)
  const text = block.plainText || block.content || ''
  if (type === 'divider') return { type: 'horizontalRule' }
  if (type === 'legacy_html') return paragraphNode(text || stripHtml(block.content))
  if (type === 'heading') return { type: 'heading', attrs: { level: Number(block.attrs?.level ?? 1) }, content: inlineText(text) }
  if (type === 'quote') return { type: 'blockquote', content: [paragraphNode(text)] }
  if (type === 'code_block') return { type: 'codeBlock', attrs: { language: block.attrs?.language ?? null }, content: text ? [{ type: 'text', text }] : undefined }
  if (type === 'bullet_list') return { type: 'bulletList', content: [{ type: 'listItem', content: [paragraphNode(text)] }] }
  if (type === 'ordered_list') return { type: 'orderedList', attrs: { start: 1 }, content: [{ type: 'listItem', content: [paragraphNode(text)] }] }
  if (type === 'task_item') return { type: 'taskList', content: [{ type: 'taskItem', attrs: { checked: Boolean(block.attrs?.checked) }, content: [paragraphNode(text)] }] }
  if (type === 'table') return tableBlockToTiptap(block)
  if (type === 'image') return { type: 'image', attrs: { src: String(block.attrs?.src ?? ''), alt: text } }
  if (isEmbedBlockType(type)) return objectBlockToTiptap(block, type)
  return paragraphNode(text)
}

function flattenTiptapNodes(nodes: JSONContent[]): KnowledgeContentBlockDraft[] {
  const result: KnowledgeContentBlockDraft[] = []
  for (const node of nodes) {
    switch (node.type) {
      case 'heading':
        result.push({ blockType: 'heading', content: textFromNode(node), attrs: { level: Number(node.attrs?.level ?? 1) } })
        break
      case 'blockquote':
        result.push({ blockType: 'quote', content: textFromNode(node) })
        break
      case 'codeBlock':
        result.push({ blockType: 'code_block', content: textFromNode(node), attrs: node.attrs ?? {} })
        break
      case 'bulletList':
        result.push(...listToDrafts(node, 'bullet_list'))
        break
      case 'orderedList':
        result.push(...listToDrafts(node, 'ordered_list'))
        break
      case 'taskList':
        result.push(...(node.content ?? []).map((item) => ({
          blockType: 'task_item' as const,
          content: textFromNode(item),
          attrs: { checked: Boolean(item.attrs?.checked) },
        })))
        break
      case 'table':
        result.push({ blockType: 'table', content: JSON.stringify(tiptapTableToBlockData(node)), attrs: {}, plainText: textFromNode(node) })
        break
      case 'horizontalRule':
        result.push({ blockType: 'divider', content: '' })
        break
      case 'objectCard':
        result.push(objectCardNodeToBlockDraft(node))
        break
      case 'fileCard':
        result.push(fileCardNodeToBlockDraft(node))
        break
      default:
        result.push({ blockType: 'paragraph', content: textFromNode(node) })
    }
  }
  return result
}

function listToDrafts(node: JSONContent, blockType: 'bullet_list' | 'ordered_list') {
  return (node.content ?? []).map((item) => ({ blockType, content: textFromNode(item) }))
}

function blockToMarkdown(block: KnowledgeContentBlock | KnowledgeContentBlockDraft) {
  const type = normalizeBlockType(block.blockType)
  const rawContent = block.content ?? ''
  const content = 'plainText' in block && block.plainText ? block.plainText : rawContent
  if (type === 'heading') return `# ${content}`
  if (type === 'quote') return `> ${content}`
  if (type === 'bullet_list') return `- ${content}`
  if (type === 'ordered_list') return `1. ${content}`
  if (type === 'task_item') return `- [ ] ${content}`
  if (type === 'code_block') return `\`\`\`\n${content}\n\`\`\``
  if (type === 'divider') return '---'
  if (type === 'legacy_html') return content
  if (type === 'table') return tableJsonToMarkdown(rawContent)
  if (isEmbedBlockType(type)) return embedBlockToDirective(type, rawContent, block)
  return content ?? ''
}

function markdownLineToDraft(line: string, sortOrder: number): KnowledgeContentBlockDraft {
  if (line === '---') return { blockType: 'divider', content: '', sortOrder }
  const objectCard = parseDirective(line, 'object-card')
  if (objectCard) return objectDirectiveToBlockDraft(objectCard, sortOrder)
  const fileCard = parseDirective(line, 'file-card')
  if (fileCard) return fileDirectiveToBlockDraft(fileCard, sortOrder)
  if (line.startsWith('# ')) return { blockType: 'heading', content: line.replace(/^#+\s*/, ''), sortOrder, attrs: { level: 1 } }
  if (line.startsWith('> ')) return { blockType: 'quote', content: line.replace(/^>\s*/, ''), sortOrder }
  if (/^- \[[ xX]\] /.test(line)) return { blockType: 'task_item', content: line.replace(/^- \[[ xX]\] /, ''), sortOrder }
  if (/^- /.test(line)) return { blockType: 'bullet_list', content: line.replace(/^- /, ''), sortOrder }
  if (/^\d+\. /.test(line)) return { blockType: 'ordered_list', content: line.replace(/^\d+\. /, ''), sortOrder }
  if (/<[a-z][\s\S]*>/i.test(line)) return { blockType: 'legacy_html', content: line, sortOrder }
  return { blockType: 'paragraph', content: line, sortOrder }
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

function objectBlockToTiptap(block: KnowledgeContentBlock, type: string): JSONContent {
  const parsed = parseEmbedData(block.content)
  const objectType = parsed.objectType ?? objectTypeForBlockType(type)
  return {
    type: 'objectCard',
    attrs: {
      objectType,
      objectId: parsed.objectId ?? '',
      title: block.embedSummary?.accessState === 'available' ? block.embedSummary.title ?? parsed.title ?? '' : '',
      subtitle: block.embedSummary?.accessState === 'available' ? block.embedSummary.subtitle ?? '' : '',
      status: block.embedSummary?.accessState === 'available' ? block.embedSummary.status ?? '' : '',
      webPath: block.embedSummary?.accessState === 'available' ? block.embedSummary.webPath ?? '' : '',
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

function objectDirectiveToBlockDraft(attrs: Record<string, string>, sortOrder: number): KnowledgeContentBlockDraft {
  const objectType = attrs.objectType || attrs.type || 'issue'
  const objectId = attrs.objectId || attrs.id || ''
  const viewId = attrs.viewId || ''
  const blockType = blockTypeForObjectType(objectType)
  const content = JSON.stringify({
    objectType,
    objectId,
    viewId,
    title: attrs.title || '',
    webPath: attrs.webPath || '',
  })
  return {
    blockType,
    content,
    sortOrder,
    attrs: { objectType, objectId, viewId },
    plainText: attrs.title || objectId || objectType,
  }
}

function fileDirectiveToBlockDraft(attrs: Record<string, string>, sortOrder: number): KnowledgeContentBlockDraft {
  const fileId = attrs.fileId || attrs.id || ''
  const content = JSON.stringify({
    objectType: 'file',
    objectId: fileId,
    fileId,
    fileName: attrs.fileName || attrs.name || '',
    contentType: attrs.contentType || '',
    kind: attrs.kind || 'file',
  })
  return {
    blockType: 'file_embed',
    content,
    sortOrder,
    attrs: { objectType: 'file', objectId: fileId, fileId },
    plainText: attrs.fileName || attrs.name || fileId || '文件',
  }
}

function embedBlockToDirective(type: string, content: string, block: KnowledgeContentBlock | KnowledgeContentBlockDraft) {
  const parsed = parseEmbedData(content)
  if (type === 'file_embed') {
    return serializeDirective('file-card', {
      fileId: parsed.fileId ?? parsed.objectId ?? '',
      fileName: parsed.fileName ?? block.plainText ?? '',
      contentType: parsed.contentType ?? '',
      kind: parsed.kind ?? 'file',
      documentId: '',
    })
  }
  return serializeDirective('object-card', {
    objectType: parsed.objectType ?? objectTypeForBlockType(type),
    objectId: parsed.objectId ?? '',
    title: parsed.title ?? ('plainText' in block ? block.plainText ?? '' : ''),
    subtitle: '',
    status: '',
    webPath: parsed.webPath ?? '',
    viewId: parsed.viewId ?? '',
  })
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

function tableBlockToTiptap(block: KnowledgeContentBlock): JSONContent {
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

function tableJsonToMarkdown(content: string) {
  const data = parseTableData(content)
  if (data.columns.length === 0) return ''
  return [
    `| ${data.columns.join(' | ')} |`,
    `| ${data.columns.map(() => '---').join(' | ')} |`,
    ...data.rows.map((row) => `| ${row.join(' | ')} |`),
  ].join('\n')
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
