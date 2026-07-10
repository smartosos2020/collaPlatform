import { apiGet } from '../../../shared/api/httpClient'

export type AdminApplicationGovernanceView = {
  modules: AdminApplicationModuleGovernance[]
}

export type AdminApplicationModuleGovernance = {
  key: 'base' | 'project' | 'message' | 'approval'
  title: string
  moduleName: string
  description: string
  userRoute: string
  adminRoute: string
  metrics: {
    primary: number
    secondary: number
    tertiary: number
    primaryLabel: string
    secondaryLabel: string
    tertiaryLabel: string
  }
  policies: string[]
  risks: Array<{
    severity: 'low' | 'medium' | 'high' | 'critical'
    title: string
    reason: string
  }>
  adminLinks: Array<{
    label: string
    path: string
  }>
  boundaryRules: string[]
}

export function getApplicationGovernance() {
  return apiGet<AdminApplicationGovernanceView>('/admin/application-governance')
}
