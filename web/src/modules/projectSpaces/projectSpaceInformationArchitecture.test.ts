import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

import {
  PROJECT_SPACE_ADVANCED_CONFIGURATION,
  PROJECT_SPACE_CONTENT_LAYER_BY_SECONDARY_GROUP,
  PROJECT_SPACE_PERSONA_TASK_MATRIX,
  PROJECT_SPACE_PRIMARY_NAVIGATION,
  PROJECT_SPACE_SCENARIO_PATHS,
  PROJECT_SPACE_TASK_ZONES,
  PROJECT_SPACE_TERMINOLOGY,
  PROJECT_SPACE_VIEWPORT_CONTRACT,
  getVisibleProjectSpacePrimaryNavigation,
  getVisibleProjectSpaceTaskZones,
  projectSpacePreviewAccess,
  projectSpacePrimaryPath,
  resolveProjectSpaceRouteContext,
} from './projectSpaceInformationArchitecture'
import { PROJECT_SPACE_SECONDARY_TAB_CONFIG } from './projectSpaceSecondaryTabs'

describe('project space primary information architecture', () => {
  it('freezes exactly five ordered business-language entries', () => {
    assert.deepEqual(
      PROJECT_SPACE_PRIMARY_NAVIGATION.map(({ key, label }) => ({ key, label })),
      [
        { key: 'overview', label: '概览' },
        { key: 'work-items', label: '工作项' },
        { key: 'management', label: '项目管理' },
        { key: 'members', label: '成员' },
        { key: 'settings', label: '设置' },
      ],
    )
    assert.deepEqual(
      PROJECT_SPACE_PRIMARY_NAVIGATION.map((item) => item.order),
      [10, 20, 30, 40, 50],
    )
  })

  it('groups the five canonical entries into three task zones', () => {
    assert.deepEqual(
      PROJECT_SPACE_TASK_ZONES.map((zone) => ({
        key: zone.key,
        primaryViews: zone.primaryViews,
      })),
      [
        { key: 'member-workspace', primaryViews: ['overview', 'work-items'] },
        { key: 'project-management', primaryViews: ['management'] },
        { key: 'space-management', primaryViews: ['members', 'settings'] },
      ],
    )
    const visibleZones = getVisibleProjectSpaceTaskZones({
      member: true,
      currentUserRole: 'admin',
      status: 'active',
      availableActions: ['view_overview', 'view_work_items', 'view_members', 'view_settings'],
    })
    assert.deepEqual(
      visibleZones.map((zone) => [zone.key, zone.navigation.map((item) => item.key)]),
      [
        ['member-workspace', ['overview', 'work-items']],
        ['space-management', ['members', 'settings']],
      ],
    )
  })

  it('keeps member preview presentational and can only reduce signed-in actions', () => {
    const ownerAccess = {
      member: true,
      currentUserRole: 'owner',
      status: 'active',
      availableActions: [
        'view_overview',
        'view_work_items',
        'view_project_management',
        'view_members',
        'view_settings',
        'update_space',
      ],
    } as const
    assert.deepEqual(
      projectSpacePreviewAccess(ownerAccess, 'member').availableActions,
      ['view_overview', 'view_work_items', 'view_project_management'],
    )
    assert.deepEqual(
      projectSpacePreviewAccess(ownerAccess, 'guest').availableActions,
      ['view_overview', 'view_work_items'],
    )
    assert.equal(projectSpacePreviewAccess(ownerAccess, 'member').currentUserRole, 'member')
  })

  it('uses current access facts instead of the display mode as navigation authority', () => {
    const owner = getVisibleProjectSpacePrimaryNavigation({
      member: true,
      currentUserRole: 'owner',
      status: 'active',
      availableActions: ['view_overview', 'view_work_items', 'view_project_management', 'view_members', 'view_settings'],
    })
    const member = getVisibleProjectSpacePrimaryNavigation({
      member: true,
      currentUserRole: 'member',
      status: 'active',
      availableActions: ['view_overview', 'view_work_items', 'view_project_management'],
    })
    const guest = getVisibleProjectSpacePrimaryNavigation({
      member: true,
      currentUserRole: 'guest',
      status: 'active',
      availableActions: ['view_overview', 'view_work_items'],
    })
    const discoverableOutsider = getVisibleProjectSpacePrimaryNavigation({
      member: false,
      currentUserRole: null,
      status: 'active',
      availableActions: ['view_overview'],
    })

    assert.deepEqual(owner.map((item) => item.key), ['overview', 'work-items', 'management', 'members', 'settings'])
    assert.deepEqual(member.map((item) => item.key), ['overview', 'work-items', 'management'])
    assert.deepEqual(guest.map((item) => item.key), ['overview', 'work-items'])
    assert.deepEqual(discoverableOutsider.map((item) => item.key), ['overview'])
  })

  it('lets custom roles follow server capabilities instead of a role-name allowlist', () => {
    const customManager = getVisibleProjectSpacePrimaryNavigation({
      member: true,
      currentUserRole: 'delivery-lead',
      status: 'active',
      availableActions: ['view_overview', 'view_work_items', 'view_project_management', 'view_members', 'view_settings'],
    })
    assert.deepEqual(
      customManager.map((item) => item.key),
      ['overview', 'work-items', 'management', 'members', 'settings'],
    )
  })

  it('keeps enterprise governance outside the project-space content shell', () => {
    assert.deepEqual(PROJECT_SPACE_PERSONA_TASK_MATRIX['enterprise-admin'].primaryViews, [])
    assert.match(PROJECT_SPACE_PERSONA_TASK_MATRIX['enterprise-admin'].explanation, /不获得空间内容旁路/)
    assert.deepEqual(getVisibleProjectSpacePrimaryNavigation({
      member: false,
      currentUserRole: null,
      status: 'active',
      availableActions: ['view_overview'],
      enterpriseGovernanceOnly: true,
    }), [])
  })
})

describe('project space content classification and terminology', () => {
  it('classifies every configured secondary-tab group into one product layer', () => {
    for (const tabs of Object.values(PROJECT_SPACE_SECONDARY_TAB_CONFIG)) {
      for (const tab of tabs) {
        assert.ok(PROJECT_SPACE_CONTENT_LAYER_BY_SECONDARY_GROUP[tab.group], tab.key)
      }
    }
  })

  it('freezes five unique advanced configuration groups', () => {
    assert.equal(PROJECT_SPACE_ADVANCED_CONFIGURATION.length, 5)
    assert.equal(
      new Set(PROJECT_SPACE_ADVANCED_CONFIGURATION.map((group) => group.key)).size,
      PROJECT_SPACE_ADVANCED_CONFIGURATION.length,
    )
    assert.deepEqual(
      PROJECT_SPACE_ADVANCED_CONFIGURATION.map((group) => group.label),
      ['工作模型', '流程与权限', '自动化与协同', '度量治理', '场景模板'],
    )
  })

  it('maps every retired label to one canonical business term', () => {
    const retired = PROJECT_SPACE_TERMINOLOGY.flatMap((entry) => entry.legacy)
    assert.equal(new Set(retired).size, retired.length)
    assert.ok(PROJECT_SPACE_TERMINOLOGY.every((entry) => entry.explanation.length >= 10))
  })
})

describe('project space route and scenario contracts', () => {
  it('preserves supported work-item and configuration deep-link context', () => {
    assert.deepEqual(
      resolveProjectSpaceRouteContext('/project-spaces/space-1/work-items/item-1'),
      {
        primaryView: 'work-items',
        renderView: 'work-items',
        compatibilityRoute: false,
        preserveQuery: true,
      },
    )
    assert.deepEqual(
      resolveProjectSpaceRouteContext('/project-spaces/space-1/types/type-1/fields/field-1'),
      {
        primaryView: 'settings',
        renderView: 'fields',
        advancedGroup: 'work-model',
        compatibilityRoute: true,
        preserveQuery: true,
      },
    )
    assert.deepEqual(
      resolveProjectSpaceRouteContext('/project-spaces/space-1/types/type-1/layouts'),
      {
        primaryView: 'settings',
        renderView: 'layouts',
        advancedGroup: 'work-model',
        compatibilityRoute: true,
        preserveQuery: true,
      },
    )
    assert.equal(projectSpacePrimaryPath('space-1', 'management'), '/project-spaces/space-1/management')
  })

  it('keeps four scenario paths inside the five-entry shell', () => {
    assert.deepEqual(
      PROJECT_SPACE_SCENARIO_PATHS.map((scenario) => scenario.key),
      ['development', 'marketing', 'human-resources', 'delivery'],
    )
    const primaryKeys = new Set(PROJECT_SPACE_PRIMARY_NAVIGATION.map((item) => item.key))
    const advancedKeys = new Set(PROJECT_SPACE_ADVANCED_CONFIGURATION.map((item) => item.key))
    for (const scenario of PROJECT_SPACE_SCENARIO_PATHS) {
      assert.ok(scenario.primarySequence.every((key) => primaryKeys.has(key)))
      assert.ok(scenario.advancedSequence.every((key) => advancedKeys.has(key)))
    }
  })

  it('freezes desktop and narrow viewport behavior', () => {
    assert.deepEqual(PROJECT_SPACE_VIEWPORT_CONTRACT.map((item) => item.width), [1440, 1366, 820])
    assert.equal(PROJECT_SPACE_VIEWPORT_CONTRACT.at(-1)?.navigation, 'scrollable')
    assert.equal(PROJECT_SPACE_VIEWPORT_CONTRACT.at(-1)?.sidebar, 'collapsible')
  })
})
