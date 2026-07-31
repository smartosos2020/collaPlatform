import {
  patchProjectSpaceSearch,
  projectSpaceLocationWithContext,
} from './projectSpaceRouteContract'

export type ProjectSpaceConfigurationDestination =
  | 'type-catalog'
  | 'fields'
  | 'layouts'
  | 'publication'
  | 'flow-access'

type ProjectSpaceConfigurationLocationInput = Readonly<{
  spaceId: string
  destination: ProjectSpaceConfigurationDestination
  typeId?: string
}>

const TYPE_SCOPED_DESTINATIONS = {
  fields: {
    pathSuffix: '/fields',
    panel: 'field-catalog',
    source: 'settings-work-model',
    hash: '',
  },
  layouts: {
    pathSuffix: '/layouts',
    panel: 'layout-editor',
    source: 'settings-work-model',
    hash: '',
  },
  publication: {
    pathSuffix: '',
    panel: 'configuration-draft',
    source: 'settings-work-model',
    hash: '',
  },
  'flow-access': {
    pathSuffix: '',
    panel: 'configuration-draft',
    source: 'settings-flow-access',
    hash: '#flow-access',
  },
} as const

/**
 * Settings entry points share configuration data, but they must not share an
 * indistinguishable destination. Keep the destination contract in one place
 * so the visible label, route panel and in-page focus remain aligned.
 */
export function projectSpaceConfigurationLocation({
  spaceId,
  destination,
  typeId,
}: ProjectSpaceConfigurationLocationInput): string | null {
  if (destination === 'type-catalog') {
    return projectSpaceLocationWithContext(
      `/project-spaces/${spaceId}/types`,
      patchProjectSpaceSearch('', {
        source: 'settings-work-model',
        panel: 'type-catalog',
      }),
    )
  }

  if (!typeId) return null
  const target = TYPE_SCOPED_DESTINATIONS[destination]
  return projectSpaceLocationWithContext(
    `/project-spaces/${spaceId}/types/${typeId}${target.pathSuffix}`,
    patchProjectSpaceSearch('', {
      source: target.source,
      panel: target.panel,
    }),
    target.hash,
  )
}
