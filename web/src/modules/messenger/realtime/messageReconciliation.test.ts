import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

import type { MessageSummary } from '../api/messengerApi.ts'
import { MESSAGE_RECONCILIATION_CACHE_LIMIT, mergeMessagePages } from './messageReconciliation.ts'

function message(sequence: number, id = `message-${sequence}`): MessageSummary {
  return {
    id,
    conversationId: 'conversation-1',
    senderId: 'user-1',
    senderName: 'User',
    messageType: 'text',
    content: String(sequence),
    clientMessageId: `client-${sequence}`,
    messageSeq: sequence,
    createdAt: new Date(sequence * 1000).toISOString(),
    mentions: [],
    links: [],
    reactions: [],
  }
}

describe('mergeMessagePages', () => {
  it('限制缓存数量并保留最新消息', () => {
    const current = {
      items: Array.from({ length: MESSAGE_RECONCILIATION_CACHE_LIMIT }, (_, index) => message(index + 1)),
      nextCursor: 'older',
    }
    const result = mergeMessagePages(current, [message(MESSAGE_RECONCILIATION_CACHE_LIMIT + 1)])
    assert.equal(result.items.length, MESSAGE_RECONCILIATION_CACHE_LIMIT)
    assert.equal(result.items[0].messageSeq, MESSAGE_RECONCILIATION_CACHE_LIMIT + 1)
    assert.equal(result.items.at(-1)?.messageSeq, 2)
    assert.equal(result.nextCursor, 'older')
  })

  it('按 id 去重并采用新版本', () => {
    const result = mergeMessagePages({ items: [message(1, 'same')], nextCursor: null }, [
      { ...message(2, 'same'), content: 'updated' },
    ])
    assert.equal(result.items.length, 1)
    assert.equal(result.items[0].messageSeq, 2)
    assert.equal(result.items[0].content, 'updated')
  })
})
