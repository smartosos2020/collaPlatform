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
    assert.deepEqual(projectSpaceSurfaceOwner('activity'), {
      panel: 'activity',
      view: 'overview',
    })
    assert.deepEqual(projectSpaceSurfaceOwner('collaboration-boundary'), {
      panel: 'activity',
      view: 'overview',
    })
    assert.equal(
      PROJECT_SPACE_SURFACE_OWNERS.some((owner) => owner.panel === 'collaboration-boundary'),
      false,
    )
    assert.deepEqual(projectSpaceSurfaceOwner('general'), {
      panel: 'management-home',
      view: 'settings',
    })
    assert.deepEqual(projectSpaceSurfaceOwner('lifecycle'), {
      panel: 'management-home',
      view: 'settings',
    })
    assert.deepEqual(projectSpaceSurfaceOwner('flow-access'), {
      panel: 'work-model',
      view: 'settings',
    })
    assert.equal(PROJECT_SPACE_SURFACE_OWNERS.some((owner) => owner.panel === 'general'), false)
    assert.equal(PROJECT_SPACE_SURFACE_OWNERS.some((owner) => owner.panel === 'lifecycle'), false)
    assert.equal(PROJECT_SPACE_SURFACE_OWNERS.some((owner) => owner.panel === 'flow-access'), false)
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

  it('consolidates retired basic-information and lifecycle bookmarks into management home', () => {
    for (const panel of ['general', 'lifecycle']) {
      assert.equal(
        canonicalProjectSpaceSurfaceLocation({
          spaceId: 'space-1',
          pathname: '/project-spaces/space-1/settings',
          search: `?source=bookmark&panel=${panel}`,
          hash: '#focus',
        }),
        '/project-spaces/space-1/settings?source=bookmark&panel=management-home#focus',
      )
    }
  })

  it('consolidates the retired flow-access panel into the nested work-model tab', () => {
    assert.equal(
      canonicalProjectSpaceSurfaceLocation({
        spaceId: 'space-1',
        pathname: '/project-spaces/space-1/settings',
        search: '?source=bookmark&panel=flow-access&typeId=type-1',
      }),
      '/project-spaces/space-1/settings?source=bookmark&panel=work-model&typeId=type-1&workModelTab=flow-access',
    )
  })

  it('consolidates the retired boundary bookmark into the combined overview tab', () => {
    assert.equal(
      canonicalProjectSpaceSurfaceLocation({
        spaceId: 'space-1',
        pathname: '/project-spaces/space-1',
        search: '?source=bookmark&panel=collaboration-boundary',
      }),
      '/project-spaces/space-1?source=bookmark&panel=activity',
    )
  })

  it('is idempotent on canonical surfaces and ignores unknown panels', () => {
    assert.equal(
      canonicalProjectSpaceSurfaceLocation({
        spaceId: 'space-1',
        pathname: '/project-spaces/space-1',
        search: '?panel=activity',
      }),
      null,
    )
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
