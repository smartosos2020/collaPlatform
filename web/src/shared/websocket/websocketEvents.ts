export type PlatformWebSocketEvent<TPayload = Record<string, unknown>> = {
  type: string
  eventId?: string
  serverTime?: string
  workspaceId?: string | null
  objectType?: string | null
  objectId?: string | null
  payload?: TPayload
}
