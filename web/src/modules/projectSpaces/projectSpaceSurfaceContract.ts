import {
  projectSpacePrimaryPath,
  resolveProjectSpaceRouteContext,
  type ProjectSpacePrimaryView,
} from './projectSpaceInformationArchitecture'
import {
  patchProjectSpaceSearch,
  projectSpaceLocationWithContext,
  sanitizeProjectSpaceSearch,
} from './projectSpaceRouteContract'

export type ProjectSpaceSurfaceOwner = Readonly<{
  panel: string
  view: ProjectSpacePrimaryView
}>

/**
 * Every editable product fact has one canonical owning surface. Other screens
 * may link to it or summarize it, but must not mount a second editor.
 */
export const PROJECT_SPACE_SURFACE_OWNERS = [
  { panel: 'member-home', view: 'overview' },
  { panel: 'activity', view: 'overview' },
  { panel: 'work-item-collection', view: 'work-items' },
  { panel: 'project-detail', view: 'management' },
  { panel: 'project-plan', view: 'management' },
  { panel: 'resource-planning', view: 'management' },
  { panel: 'resource-worklog', view: 'management' },
  { panel: 'resource-capacity', view: 'management' },
  { panel: 'resource-schedule', view: 'management' },
  { panel: 'project-register', view: 'management' },
  { panel: 'project-delivery', view: 'management' },
  { panel: 'cross-space-relations', view: 'management' },
  { panel: 'cross-space-sync', view: 'management' },
  { panel: 'cross-team-panorama', view: 'management' },
  { panel: 'metric-dashboards', view: 'management' },
  { panel: 'metric-risks', view: 'management' },
  { panel: 'member-list', view: 'members' },
  { panel: 'invitations', view: 'members' },
  { panel: 'management-home', view: 'settings' },
  { panel: 'work-model', view: 'settings' },
  { panel: 'automation-collaboration', view: 'settings' },
  { panel: 'metrics-governance', view: 'settings' },
  { panel: 'scenario-templates', view: 'settings' },
] as const satisfies readonly ProjectSpaceSurfaceOwner[]

const OWNER_BY_PANEL = new Map<string, ProjectSpaceSurfaceOwner>(
  PROJECT_SPACE_SURFACE_OWNERS.map((owner) => [owner.panel, owner]),
)

const LEGACY_PANEL_COMPATIBILITY = new Map<string, string>([
  ['active-types', 'member-home'],
  ['collaboration-boundary', 'activity'],
  ['general', 'management-home'],
  ['lifecycle', 'management-home'],
  ['flow-access', 'work-model'],
  ['cross-space-grants', 'automation-collaboration'],
  ['automation-rules', 'automation-collaboration'],
  ['automation-execution', 'automation-collaboration'],
  ['automation-connectors', 'automation-collaboration'],
  ['automation-management', 'automation-collaboration'],
  ['metric-semantics', 'metrics-governance'],
  ['metric-governance', 'metrics-governance'],
])

export function projectSpaceSurfaceOwner(panel: string): ProjectSpaceSurfaceOwner | null {
  const canonicalPanel = LEGACY_PANEL_COMPATIBILITY.get(panel) ?? panel
  return OWNER_BY_PANEL.get(canonicalPanel) ?? null
}

/**
 * Returns a compatible canonical location only when the current panel belongs
 * to another surface or uses a retired name. Returning null means no redirect,
 * which keeps the mapping idempotent and prevents navigation loops.
 */
export function canonicalProjectSpaceSurfaceLocation(input: Readonly<{
  spaceId: string
  pathname: string
  search: URLSearchParams | string
  hash?: string
}>): string | null {
  const search = sanitizeProjectSpaceSearch(input.search)
  const requestedPanel = search.get('panel')
  if (!requestedPanel) return null

  const owner = projectSpaceSurfaceOwner(requestedPanel)
  if (!owner) return null
  const currentView = resolveProjectSpaceRouteContext(input.pathname).primaryView
  if (currentView === owner.view && requestedPanel === owner.panel) return null

  const nextSearch = patchProjectSpaceSearch(search, {
    panel: owner.panel,
    source: search.get('source') ?? 'surface-compat',
    workModelTab: requestedPanel === 'flow-access'
      ? 'flow-access'
      : undefined,
    automationPanel: owner.panel === 'automation-collaboration'
      ? requestedPanel
      : undefined,
    metricConfig: owner.panel === 'metrics-governance'
      ? requestedPanel
      : undefined,
  })
  return projectSpaceLocationWithContext(
    projectSpacePrimaryPath(input.spaceId, owner.view),
    nextSearch,
    input.hash,
  )
}
