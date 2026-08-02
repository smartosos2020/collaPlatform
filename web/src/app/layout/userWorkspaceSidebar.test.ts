import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

import {
  normalizeUserWorkspaceSidebarState,
  userWorkspaceSidebarStorageKey,
} from './userWorkspaceSidebar.ts'

describe('normalizeUserWorkspaceSidebarState', () => {
  it('restores only the known collapsed value', () => {
    assert.equal(normalizeUserWorkspaceSidebarState('collapsed'), 'collapsed')
    assert.equal(normalizeUserWorkspaceSidebarState('expanded'), 'expanded')
    assert.equal(normalizeUserWorkspaceSidebarState('true'), 'expanded')
    assert.equal(normalizeUserWorkspaceSidebarState(null), 'expanded')
  })
})

describe('userWorkspaceSidebarStorageKey', () => {
  it('keeps preferences isolated by user', () => {
    assert.equal(userWorkspaceSidebarStorageKey('user-1'), 'colla.user-workspace.sidebar.user-1')
    assert.equal(userWorkspaceSidebarStorageKey('user-2'), 'colla.user-workspace.sidebar.user-2')
    assert.notEqual(userWorkspaceSidebarStorageKey('user-1'), userWorkspaceSidebarStorageKey('user-2'))
  })

  it('uses an anonymous scope until the current user is available', () => {
    assert.equal(userWorkspaceSidebarStorageKey(), 'colla.user-workspace.sidebar.anonymous')
    assert.equal(userWorkspaceSidebarStorageKey('  '), 'colla.user-workspace.sidebar.anonymous')
  })
})
