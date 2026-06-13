export type PlatformWebSocketEvent<TPayload = Record<string, unknown>> = {
  type: string
  eventId?: string
  serverTime?: string
  payload?: TPayload
}
