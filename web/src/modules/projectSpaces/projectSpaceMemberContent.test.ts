import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

import {
  creatableProjectSpaceWorkItemTypes,
  visibleProjectSpacePersonalWork,
  visibleProjectSpaceWorkItemTypes,
} from './projectSpaceMemberContent.ts'

describe('project space member content decision', () => {
  const types = [
    { id: 'ready-create', configurationReady: true, availableActions: ['view', 'create'] },
    { id: 'ready-read', configurationReady: true, availableActions: ['view'] },
    { id: 'ready-hidden', configurationReady: true, availableActions: [] },
    { id: 'pending', configurationReady: false, availableActions: ['view', 'create'] },
  ] as const

  it('shows only runtime-ready types the server lets the user view', () => {
    assert.deepEqual(
      visibleProjectSpaceWorkItemTypes(types).map((type) => type.id),
      ['ready-create', 'ready-read'],
    )
  })

  it('offers create only when the same ready type has a server create action', () => {
    assert.deepEqual(
      creatableProjectSpaceWorkItemTypes(types).map((type) => type.id),
      ['ready-create'],
    )
  })

  it('filters personal work by item actions without consulting a role name', () => {
    assert.deepEqual(
      visibleProjectSpacePersonalWork([
        { id: 'visible', availableActions: ['view'] },
        { id: 'hidden', availableActions: [] },
      ]).map((item) => item.id),
      ['visible'],
    )
  })
})
