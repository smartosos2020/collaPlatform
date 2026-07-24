import type { RealtimeEnvelope } from '../realtime/protocol'

export type PlatformWebSocketEvent<
  TPayload extends Record<string, unknown> = Record<string, unknown>,
> = RealtimeEnvelope<TPayload>
