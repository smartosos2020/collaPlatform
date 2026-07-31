import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

import { projectSpaceConfigurationLocation } from './projectSpaceConfigurationNavigation.ts'

describe('project space configuration navigation', () => {
  it('gives each settings entry a distinct and explicit destination', () => {
    assert.equal(
      projectSpaceConfigurationLocation({
        spaceId: 'space-1',
        destination: 'type-catalog',
      }),
      '/project-spaces/space-1/types?source=settings-work-model&panel=type-catalog',
    )
    assert.equal(
      projectSpaceConfigurationLocation({
        spaceId: 'space-1',
        typeId: 'type-1',
        destination: 'fields',
      }),
      '/project-spaces/space-1/types/type-1/fields?source=settings-work-model&panel=field-catalog',
    )
    assert.equal(
      projectSpaceConfigurationLocation({
        spaceId: 'space-1',
        typeId: 'type-1',
        destination: 'layouts',
      }),
      '/project-spaces/space-1/types/type-1/layouts?source=settings-work-model&panel=layout-editor',
    )
    assert.equal(
      projectSpaceConfigurationLocation({
        spaceId: 'space-1',
        typeId: 'type-1',
        destination: 'publication',
      }),
      '/project-spaces/space-1/types/type-1?source=settings-work-model&panel=configuration-draft',
    )
    assert.equal(
      projectSpaceConfigurationLocation({
        spaceId: 'space-1',
        typeId: 'type-1',
        destination: 'flow-access',
      }),
      '/project-spaces/space-1/types/type-1?source=settings-flow-access&panel=configuration-draft#flow-access',
    )
  })

  it('fails closed without a valid type-scoped path', () => {
    assert.equal(
      projectSpaceConfigurationLocation({
        spaceId: 'space-1',
        destination: 'fields',
      }),
      null,
    )
    assert.equal(
      projectSpaceConfigurationLocation({
        spaceId: '../admin',
        typeId: 'type-1',
        destination: 'flow-access',
      }),
      null,
    )
  })
})
