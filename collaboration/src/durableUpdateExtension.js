import { isTransactionOrigin } from '@hocuspocus/server'

import { assertUpdateSize, COLLABORATION_SCHEMA_VERSION, encodeBinary, updateHash } from './protocol.js'

export class DurableUpdateExtension {
  priority = 2000

  constructor(gateway, metrics, maxUpdateBytes) {
    this.gateway = gateway
    this.metrics = metrics
    this.maxUpdateBytes = maxUpdateBytes
  }

  async onChange({ context, documentName, update, transactionOrigin }) {
    if (isTransactionOrigin(transactionOrigin) && transactionOrigin.source === 'redis') return
    if (!context?.ticket || !(update instanceof Uint8Array) || update.byteLength === 0) return
    assertUpdateSize(update, this.maxUpdateBytes)
    const startedAt = performance.now()
    const ack = await this.gateway.appendUpdate(
      context.ticket,
      documentName,
      encodeBinary(update),
      context.clientId,
      updateHash(update),
      COLLABORATION_SCHEMA_VERSION,
    )
    this.metrics.update(documentName, ack.sequence, Math.round(performance.now() - startedAt))
  }
}
