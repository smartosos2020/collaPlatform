import type { RealtimeEnvelope } from './protocol'

export type RealtimeSequenceDecision =
  | { kind: 'accepted'; previousSequence: number | null }
  | { kind: 'gap'; previousSequence: number }
  | { kind: 'duplicate'; previousSequence: number | null }
  | { kind: 'stale'; previousSequence: number }

export type RealtimeSequencerOptions = {
  eventCapacity?: number
  watermarkCapacity?: number
}

export class RealtimeSequencer {
  private readonly eventCapacity: number
  private readonly watermarkCapacity: number
  private readonly eventIds = new Map<string, true>()
  private readonly watermarks = new Map<string, number>()

  constructor(options: RealtimeSequencerOptions = {}) {
    this.eventCapacity = positiveCapacity(options.eventCapacity, 2_048)
    this.watermarkCapacity = positiveCapacity(options.watermarkCapacity, 4_096)
  }

  inspect(envelope: RealtimeEnvelope): RealtimeSequenceDecision {
    if (this.eventIds.has(envelope.eventId)) {
      touch(this.eventIds, envelope.eventId, true)
      return { kind: 'duplicate', previousSequence: this.watermarks.get(watermarkKey(envelope)) ?? null }
    }

    const key = watermarkKey(envelope)
    const previousSequence = this.watermarks.get(key)
    this.rememberEvent(envelope.eventId)
    if (previousSequence !== undefined && envelope.sequence <= previousSequence) {
      touch(this.watermarks, key, previousSequence)
      return envelope.sequence === previousSequence
        ? { kind: 'duplicate', previousSequence }
        : { kind: 'stale', previousSequence }
    }

    this.rememberWatermark(key, envelope.sequence)
    if (previousSequence !== undefined && envelope.sequence > previousSequence + 1) {
      return { kind: 'gap', previousSequence }
    }
    return { kind: 'accepted', previousSequence: previousSequence ?? null }
  }

  reset() {
    this.eventIds.clear()
    this.watermarks.clear()
  }

  snapshot() {
    return {
      eventIds: this.eventIds.size,
      watermarks: this.watermarks.size,
    }
  }

  private rememberEvent(eventId: string) {
    touch(this.eventIds, eventId, true)
    trimOldest(this.eventIds, this.eventCapacity)
  }

  private rememberWatermark(key: string, sequence: number) {
    touch(this.watermarks, key, sequence)
    trimOldest(this.watermarks, this.watermarkCapacity)
  }
}

function watermarkKey(envelope: RealtimeEnvelope) {
  return `${envelope.workspaceId}\u0000${envelope.sequenceScope}\u0000${envelope.sequenceKey}`
}

function touch<T>(values: Map<string, T>, key: string, value: T) {
  values.delete(key)
  values.set(key, value)
}

function trimOldest<T>(values: Map<string, T>, capacity: number) {
  while (values.size > capacity) {
    const oldest = values.keys().next().value
    if (oldest === undefined) {
      return
    }
    values.delete(oldest)
  }
}

function positiveCapacity(value: number | undefined, fallback: number) {
  return Number.isSafeInteger(value) && (value as number) > 0 ? value as number : fallback
}
