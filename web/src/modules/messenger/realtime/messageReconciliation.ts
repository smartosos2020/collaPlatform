import type { QueryClient, QueryKey } from '@tanstack/react-query'

import type { MessagePage, MessageSummary } from '../api/messengerApi'

export const MESSAGE_RECONCILIATION_PAGE_LIMIT = 100
export const MESSAGE_RECONCILIATION_MAX_PAGES = 20

export const MESSAGE_RECONCILIATION_TRIGGERS = [
  'initial-connect',
  'reconnect',
  'sequence-gap',
  'window-focus',
  'signal',
] as const

export type MessageReconciliationTrigger = typeof MESSAGE_RECONCILIATION_TRIGGERS[number]

export type FetchMessagesAfterSequence = (
  conversationId: string,
  afterSeq: number,
  limit: number,
) => Promise<MessagePage>

export type MessageCollectionStopReason = 'exhausted' | 'no-progress' | 'max-pages'

export type MessageCollectionResult = {
  items: MessageSummary[]
  nextAfterSeq: number
  pagesFetched: number
  complete: boolean
  stopReason: MessageCollectionStopReason
}

export type MessageMutationReconciliation = 'incremental' | 'full-refetch'

export type ReconcileConversationMessagesOptions = {
  queryClient: QueryClient
  queryKey: QueryKey
  conversationId: string
  afterSeq: number
  signalType: string
  trigger: MessageReconciliationTrigger
  mutationReconciliation?: MessageMutationReconciliation
  fetchPage: FetchMessagesAfterSequence
  pageLimit?: number
  maxPages?: number
}

export type MessageReconciliationOutcome =
  | {
      mode: 'incremental'
      trigger: MessageReconciliationTrigger
      collection: MessageCollectionResult
    }
  | {
      mode: 'full-refetch'
      trigger: MessageReconciliationTrigger
      collection?: MessageCollectionResult
    }

export async function collectMessagesAfterSequence(
  conversationId: string,
  afterSeq: number,
  fetchPage: FetchMessagesAfterSequence,
  pageLimit = MESSAGE_RECONCILIATION_PAGE_LIMIT,
  maxPages = MESSAGE_RECONCILIATION_MAX_PAGES,
): Promise<MessageCollectionResult> {
  const boundedLimit = Math.max(1, Math.min(pageLimit, MESSAGE_RECONCILIATION_PAGE_LIMIT))
  const boundedMaxPages = Math.max(1, maxPages)
  const byId = new Map<string, MessageSummary>()
  let cursor = Math.max(0, afterSeq)

  for (let pageIndex = 0; pageIndex < boundedMaxPages; pageIndex += 1) {
    const page = await fetchPage(conversationId, cursor, boundedLimit)
    let nextCursor = cursor
    for (const item of page.items) {
      if (item.messageSeq <= afterSeq) continue
      byId.set(item.id, item)
      nextCursor = Math.max(nextCursor, item.messageSeq)
    }

    const pagesFetched = pageIndex + 1
    if (page.items.length < boundedLimit) {
      return collectionResult(byId, nextCursor, pagesFetched, true, 'exhausted')
    }
    if (nextCursor <= cursor) {
      return collectionResult(byId, cursor, pagesFetched, false, 'no-progress')
    }
    cursor = nextCursor
  }

  return collectionResult(byId, cursor, boundedMaxPages, false, 'max-pages')
}

export function messageSignalReconciliationMode(
  signalType: string,
  mutationReconciliation: MessageMutationReconciliation = 'incremental',
) {
  if (signalType === 'message.created') return 'incremental' as const
  return mutationReconciliation
}

export function mergeMessagePages(
  current: MessagePage | undefined,
  incoming: readonly MessageSummary[],
): MessagePage {
  const byId = new Map<string, MessageSummary>()
  for (const item of current?.items ?? []) byId.set(item.id, item)
  for (const item of incoming) byId.set(item.id, item)
  return {
    items: [...byId.values()].sort((left, right) => right.messageSeq - left.messageSeq),
    nextCursor: current?.nextCursor ?? null,
  }
}

export async function reconcileConversationMessages(
  options: ReconcileConversationMessagesOptions,
): Promise<MessageReconciliationOutcome> {
  const mode = messageSignalReconciliationMode(options.signalType, options.mutationReconciliation)
  if (mode === 'full-refetch') {
    await options.queryClient.refetchQueries({ queryKey: options.queryKey, exact: false, type: 'active' })
    return { mode, trigger: options.trigger }
  }

  const result = await collectMessagesAfterSequence(
    options.conversationId,
    options.afterSeq,
    options.fetchPage,
    options.pageLimit,
    options.maxPages,
  )
  if (!result.complete) {
    await options.queryClient.refetchQueries({ queryKey: options.queryKey, exact: false, type: 'active' })
    return { mode: 'full-refetch', trigger: options.trigger, collection: result }
  }
  options.queryClient.setQueryData<MessagePage>(
    options.queryKey,
    (current) => mergeMessagePages(current, result.items),
  )
  return { mode: 'incremental', trigger: options.trigger, collection: result }
}

function collectionResult(
  byId: Map<string, MessageSummary>,
  nextAfterSeq: number,
  pagesFetched: number,
  complete: boolean,
  stopReason: MessageCollectionStopReason,
): MessageCollectionResult {
  return {
    items: [...byId.values()].sort((left, right) => left.messageSeq - right.messageSeq),
    nextAfterSeq,
    pagesFetched,
    complete,
    stopReason,
  }
}
