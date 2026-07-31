import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

import {
  PROJECT_SPACE_SURFACE_OWNERS,
  canonicalProjectSpaceSurfaceLocation,
  projectSpaceSurfaceOwner,
} from './projectSpaceSurfaceContract.ts'

describe('project space surface ownership', () => {
  it('assigns every canonical panel to one and only one owning route', () => {
    assert.equal(
      new Set(PROJECT_SPACE_SURFACE_OWNERS.map((owner) => owner.panel)).size,
      PROJECT_SPACE_SURFACE_OWNERS.length,
    )
    assert.deepEqual(projectSpaceSurfaceOwner('project-plan'), {
      panel: 'project-plan',
      view: 'management',
    })
    assert.deepEqual(projectSpaceSurfaceOwner('automation-rules'), {
      panel: 'automation-collaboration',
      view: 'settings',
    })
  })

  it('moves a legacy panel to its owner while preserving allowlisted context and hash', () => {
    assert.equal(
      canonicalProjectSpaceSurfaceLocation({
        spaceId: 'space-1',
        pathname: '/project-spaces/space-1/work-items',
        search: '?panel=automation-rules&typeId=type-1&create=1&savedViewId=view-1&drop=secret',
        hash: '#focus',
      }),
      '/project-spaces/space-1/settings?source=surface-compat&panel=automation-collaboration&automationPanel=automation-rules&typeId=type-1&create=1&savedViewId=view-1#focus',
    )
  })

  it('moves a management panel out of overview without losing its source', () => {
    assert.equal(
      canonicalProjectSpaceSurfaceLocation({
        spaceId: 'space-1',
        pathname: '/project-spaces/space-1',
        search: '?source=bookmark&panel=metric-dashboards',
      }),
      '/project-spaces/space-1/management?source=bookmark&panel=metric-dashboards',
    )
  })

  it('is idempotent on canonical surfaces and ignores unknown panels', () => {
    assert.equal(
      canonicalProjectSpaceSurfaceLocation({
        spaceId: 'space-1',
        pathname: '/project-spaces/space-1/management',
        search: '?panel=project-plan',
      }),
      null,
    )
    assert.equal(
      canonicalProjectSpaceSurfaceLocation({
        spaceId: 'space-1',
        pathname: '/project-spaces/space-1',
        search: '?panel=unknown-panel',
      }),
      null,
    )
  })
})
