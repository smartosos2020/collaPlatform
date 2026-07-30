import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

import {
  PROJECT_SPACE_SECONDARY_TAB_CONFIG,
  createProjectSpaceSecondaryTabItems,
  getProjectSpaceSecondaryTabs,
  sortProjectSpaceSecondaryTabs,
  type ProjectSpaceSecondaryTabView,
} from './projectSpaceSecondaryTabs.ts'

const requiredViews: readonly ProjectSpaceSecondaryTabView[] = [
  'overview',
  'work-items',
  'work-item-detail',
  'types',
  'fields',
  'layouts',
  'members',
  'settings',
]

describe('PROJECT_SPACE_SECONDARY_TAB_CONFIG', () => {
  it('covers every project-space view with unique, ordered keys', () => {
    assert.deepEqual(Object.keys(PROJECT_SPACE_SECONDARY_TAB_CONFIG), requiredViews)

    for (const view of requiredViews) {
      const tabs = PROJECT_SPACE_SECONDARY_TAB_CONFIG[view]
      assert.ok(tabs.length > 0, `${view} must declare at least one secondary tab`)
      assert.equal(new Set(tabs.map((tab) => tab.key)).size, tabs.length, `${view} keys must be unique`)
      assert.deepEqual(
        tabs.map((tab) => tab.order),
        [...tabs].map((tab) => tab.order).sort((left, right) => left - right),
        `${view} must be declared in display order`,
      )
    }
  })

  it('puts the core work-item collection before project governance panels', () => {
    const tabs = getProjectSpaceSecondaryTabs('work-items')
    assert.equal(tabs[0]?.key, 'work-item-collection')
    assert.equal(tabs[0]?.group, 'core')
    assert.ok(
      tabs.findIndex((tab) => tab.key === 'project-plan')
        < tabs.findIndex((tab) => tab.key === 'automation-rules'),
    )
  })
})

describe('sortProjectSpaceSecondaryTabs', () => {
  it('sorts a copy by order and preserves source order for ties', () => {
    const source = [
      { key: 'later', order: 20 },
      { key: 'tie-a', order: 10 },
      { key: 'tie-b', order: 10 },
    ] as const

    const result = sortProjectSpaceSecondaryTabs(source)

    assert.deepEqual(result.map((tab) => tab.key), ['tie-a', 'tie-b', 'later'])
    assert.deepEqual(source.map((tab) => tab.key), ['later', 'tie-a', 'tie-b'])
    assert.notEqual(result, source)
  })
})

describe('getProjectSpaceSecondaryTabs', () => {
  it('filters manager-only content unless management access is explicit', () => {
    assert.equal(
      getProjectSpaceSecondaryTabs('overview').some((tab) => tab.key === 'cross-space-grants'),
      false,
    )
    assert.equal(
      getProjectSpaceSecondaryTabs('overview', { canManage: true })
        .some((tab) => tab.key === 'cross-space-grants'),
      true,
    )
    assert.deepEqual(getProjectSpaceSecondaryTabs('settings'), [])
    assert.deepEqual(
      getProjectSpaceSecondaryTabs('settings', { canManage: true }).map((tab) => tab.key),
      [
        'general',
        'lifecycle',
        'work-model',
        'flow-access',
        'automation-collaboration',
        'metrics-governance',
        'scenario-templates',
      ],
    )
  })

  it('keeps configured order when a view exposes only selected content blocks', () => {
    const tabs = getProjectSpaceSecondaryTabs('work-items', {
      includeKeys: ['project-delivery', 'work-item-collection', 'project-plan'],
    })

    assert.deepEqual(
      tabs.map((tab) => tab.key),
      ['work-item-collection', 'project-plan', 'project-delivery'],
    )
  })
})

describe('createProjectSpaceSecondaryTabItems', () => {
  it('creates Ant Design-compatible items from the same filtered order', () => {
    const items = createProjectSpaceSecondaryTabItems(
      'members',
      (tab) => `content:${tab.key}`,
      { canManage: true },
    )

    assert.deepEqual(items, [
      { key: 'member-list', label: '空间成员', children: 'content:member-list' },
      { key: 'invitations', label: '成员邀请', children: 'content:invitations' },
    ])
  })
})
