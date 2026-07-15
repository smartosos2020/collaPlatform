import assert from 'node:assert/strict'
import test from 'node:test'
import * as Y from 'yjs'

import {
  assertUpdateSize,
  mergeDocumentUpdates,
  parseDocumentName,
  permissionStateMessage,
  updateHash,
} from '../src/protocol.js'

test('concurrent and duplicate Yjs updates converge without data loss', () => {
  const seed = new Y.Doc()
  seed.getText('title').insert(0, 'base')
  const seedUpdate = Y.encodeStateAsUpdate(seed)
  const left = new Y.Doc()
  const right = new Y.Doc()
  Y.applyUpdate(left, seedUpdate)
  Y.applyUpdate(right, seedUpdate)
  left.getText('title').insert(left.getText('title').length, '-left')
  right.getText('title').insert(right.getText('title').length, '-right')
  const leftUpdate = Y.encodeStateAsUpdate(left, Y.encodeStateVector(seed))
  const rightUpdate = Y.encodeStateAsUpdate(right, Y.encodeStateVector(seed))
  Y.applyUpdate(left, rightUpdate)
  Y.applyUpdate(right, leftUpdate)
  Y.applyUpdate(right, leftUpdate)
  assert.equal(left.getText('title').toString(), right.getText('title').toString())
  assert.deepEqual(Y.encodeStateAsUpdate(left), Y.encodeStateAsUpdate(right))
})

test('document names and update bounds are validated', () => {
  const ids = '00000000-0000-0000-0000-000000000001'
  assert.deepEqual(parseDocumentName(`knowledge:${ids}:${ids}`), { workspaceId: ids, itemId: ids })
  assert.throws(() => parseDocumentName(`knowledge:${ids}:../other`), /Invalid collaboration document name/)
  assert.throws(() => assertUpdateSize(new Uint8Array(), 100), /empty/)
  assert.throws(() => assertUpdateSize(new Uint8Array(101), 100), /exceeds/)
  assert.equal(updateHash(new Uint8Array([1, 2, 3])), updateHash(new Uint8Array([1, 2, 3])))
})

test('out-of-order property and structure updates converge after a brief disconnect', () => {
  const left = new Y.Doc()
  const right = new Y.Doc()
  const seed = left.getMap('document')
  seed.set('status', 'draft')
  seed.set('blocks', ['a', 'b'])
  Y.applyUpdate(right, Y.encodeStateAsUpdate(left))
  const baseVector = Y.encodeStateVector(left)

  left.getMap('document').set('status', 'review')
  left.getMap('document').set('blocks', ['a', 'b', 'left'])
  right.getMap('document').set('status', 'published')
  right.getMap('document').set('blocks', ['a', 'right', 'b'])
  const delayedLeft = Y.encodeStateAsUpdate(left, baseVector)
  const delayedRight = Y.encodeStateAsUpdate(right, baseVector)

  Y.applyUpdate(right, delayedLeft)
  Y.applyUpdate(left, delayedRight)
  Y.applyUpdate(left, delayedRight)
  assert.deepEqual(left.getMap('document').toJSON(), right.getMap('document').toJSON())
  assert.deepEqual(Y.encodeStateVector(left), Y.encodeStateVector(right))
})

test('a restarted room reconstructs its state from snapshot and pending updates', () => {
  const original = new Y.Doc()
  original.getText('title').insert(0, 'snapshot')
  const snapshot = Y.encodeStateAsUpdate(original)
  const snapshotVector = Y.encodeStateVector(original)
  original.getText('title').insert(original.getText('title').length, '-pending')
  const pending = Y.encodeStateAsUpdate(original, snapshotVector)

  const restarted = new Y.Doc()
  Y.applyUpdate(restarted, mergeDocumentUpdates(snapshot, [pending, pending]))

  assert.equal(restarted.getText('title').toString(), 'snapshot-pending')
  assert.deepEqual(Y.encodeStateAsUpdate(restarted), Y.encodeStateAsUpdate(original))
})

test('permission refresh messages are versioned and deny editing explicitly', () => {
  assert.deepEqual(JSON.parse(permissionStateMessage({ canView: true, canEdit: false, expiresAt: '2026-07-15T00:00:00Z' })), {
    type: 'permission',
    protocolVersion: 'colla-yjs-v1',
    canView: true,
    canEdit: false,
    expiresAt: '2026-07-15T00:00:00Z',
  })
})

test('same-block and different-block concurrent text updates converge', () => {
  const seed = new Y.Doc()
  const blocks = seed.getMap('blocks')
  const first = new Y.Text('first')
  const second = new Y.Text('second')
  blocks.set('block-a', first)
  blocks.set('block-b', second)
  const baseline = Y.encodeStateAsUpdate(seed)
  const baselineVector = Y.encodeStateVector(seed)
  const left = new Y.Doc()
  const right = new Y.Doc()
  Y.applyUpdate(left, baseline)
  Y.applyUpdate(right, baseline)

  left.getMap('blocks').get('block-a').insert(5, '-left')
  right.getMap('blocks').get('block-a').insert(5, '-right')
  left.getMap('blocks').get('block-b').insert(6, '-A')
  right.getMap('blocks').get('block-b').insert(6, '-B')
  exchangeOutOfOrder(left, right, baselineVector)

  assert.deepEqual(left.getMap('blocks').toJSON(), right.getMap('blocks').toJSON())
  assert.match(left.getMap('blocks').get('block-a').toString(), /left/)
  assert.match(left.getMap('blocks').get('block-a').toString(), /right/)
  assert.match(left.getMap('blocks').get('block-b').toString(), /-A/)
  assert.match(left.getMap('blocks').get('block-b').toString(), /-B/)
})

test('concurrent block deletion and table-cell edits converge without resurrection', () => {
  const seed = new Y.Doc()
  const blocks = seed.getMap('blocks')
  const deletedCandidate = new Y.Map()
  deletedCandidate.set('text', 'remove me')
  blocks.set('deleted-block', deletedCandidate)
  const table = new Y.Map()
  table.set('cell-a', new Y.Text('A'))
  table.set('cell-b', new Y.Text('B'))
  blocks.set('table-block', table)
  const baseline = Y.encodeStateAsUpdate(seed)
  const baselineVector = Y.encodeStateVector(seed)
  const left = new Y.Doc()
  const right = new Y.Doc()
  Y.applyUpdate(left, baseline)
  Y.applyUpdate(right, baseline)

  left.getMap('blocks').delete('deleted-block')
  right.getMap('blocks').get('deleted-block').set('format', 'bold')
  left.getMap('blocks').get('table-block').get('cell-a').insert(1, '1')
  right.getMap('blocks').get('table-block').get('cell-b').insert(1, '2')
  exchangeOutOfOrder(left, right, baselineVector)

  assert.deepEqual(left.getMap('blocks').toJSON(), right.getMap('blocks').toJSON())
  assert.equal(left.getMap('blocks').has('deleted-block'), false)
  assert.equal(left.getMap('blocks').get('table-block').get('cell-a').toString(), 'A1')
  assert.equal(left.getMap('blocks').get('table-block').get('cell-b').toString(), 'B2')
})

function exchangeOutOfOrder(left, right, baselineVector) {
  const leftUpdate = Y.encodeStateAsUpdate(left, baselineVector)
  const rightUpdate = Y.encodeStateAsUpdate(right, baselineVector)
  Y.applyUpdate(right, leftUpdate)
  Y.applyUpdate(left, rightUpdate)
  Y.applyUpdate(left, rightUpdate)
  Y.applyUpdate(right, leftUpdate)
}
