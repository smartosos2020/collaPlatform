import { Extension, Node } from '@tiptap/core'
import Image from '@tiptap/extension-image'
import { Table } from '@tiptap/extension-table'
import TableCell from '@tiptap/extension-table-cell'
import TableHeader from '@tiptap/extension-table-header'
import TableRow from '@tiptap/extension-table-row'
import TaskItem from '@tiptap/extension-task-item'
import TaskList from '@tiptap/extension-task-list'
import StarterKit from '@tiptap/starter-kit'

const blockAttributes = {
  blockId: { default: null },
  parentBlockId: { default: null },
}

const BlockIdentity = Extension.create({
  name: 'collaborationBlockIdentity',
  addGlobalAttributes() {
    return [{
      types: ['paragraph', 'heading', 'blockquote', 'callout', 'codeBlock', 'bulletList', 'orderedList', 'taskList', 'table', 'image', 'horizontalRule', 'embed', 'objectCard', 'fileCard'],
      attributes: blockAttributes,
    }]
  },
})

const ObjectCard = Node.create({
  name: 'objectCard',
  group: 'block',
  atom: true,
  addAttributes: () => ({
    objectType: { default: 'issue' }, objectId: { default: '' }, title: { default: '' },
    subtitle: { default: '' }, status: { default: '' }, webPath: { default: '' }, viewId: { default: '' },
  }),
  parseHTML: () => [{ tag: 'div[data-doc-object-card]' }],
  renderHTML: ({ HTMLAttributes }) => ['div', { ...HTMLAttributes, 'data-doc-object-card': 'true' }],
})

const FileCard = Node.create({
  name: 'fileCard',
  group: 'block',
  atom: true,
  addAttributes: () => ({
    fileId: { default: '' }, fileName: { default: '' }, contentType: { default: 'application/octet-stream' },
    sizeBytes: { default: 0 }, kind: { default: 'file' }, itemId: { default: '' }, caption: { default: '' },
  }),
  parseHTML: () => [{ tag: 'div[data-doc-file-card]' }],
  renderHTML: ({ HTMLAttributes }) => ['div', { ...HTMLAttributes, 'data-doc-file-card': 'true' }],
})

const Callout = Node.create({
  name: 'callout',
  group: 'block',
  content: 'block+',
  defining: true,
  addAttributes: () => ({ tone: { default: 'info' } }),
  parseHTML: () => [{ tag: 'aside[data-doc-callout]' }],
  renderHTML: ({ HTMLAttributes }) => ['aside', { ...HTMLAttributes, 'data-doc-callout': 'true' }, 0],
})

const LegacyEmbed = Node.create({
  name: 'embed',
  group: 'block',
  atom: true,
  addAttributes: () => ({ object: { default: {} } }),
  parseHTML: () => [{ tag: 'div[data-doc-legacy-embed]' }],
  renderHTML: ({ HTMLAttributes }) => ['div', { ...HTMLAttributes, 'data-doc-legacy-embed': 'true' }],
})

export const collaborationExtensions = [
  StarterKit.configure({ heading: { levels: [1, 2, 3] } }),
  TaskList,
  TaskItem.configure({ nested: true }),
  Table.configure({ resizable: true, allowTableNodeSelection: true }),
  TableRow,
  TableHeader,
  TableCell,
  Image.configure({ allowBase64: false }),
  ObjectCard,
  FileCard,
  Callout,
  LegacyEmbed,
  BlockIdentity,
]
