import { apiGet } from '../../../shared/api/httpClient'

export type HealthResponse = {
  status: string
  service: string
  time: string
}

export function getHealth() {
  return apiGet<HealthResponse>('/health')
}

