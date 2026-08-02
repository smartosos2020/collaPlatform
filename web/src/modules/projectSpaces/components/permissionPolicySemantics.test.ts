import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

import {
  PERMISSION_ACTION_DEFINITIONS,
  PERMISSION_SCOPE_DEFINITIONS,
  PERMISSION_SUBJECT_DEFINITIONS,
  replacePrimarySubject,
  scopeForKind,
  semanticOptionLabel,
  subjectForKind,
  type PermissionDataScope,
  type PermissionSubjectSelector,
} from './permissionPolicySemantics.ts'

describe('permission policy semantics', () => {
  it('combines the Chinese label and stable code for every semantic option', () => {
    const definitions = [
      ...PERMISSION_ACTION_DEFINITIONS,
      ...PERMISSION_SUBJECT_DEFINITIONS,
      ...PERMISSION_SCOPE_DEFINITIONS,
    ]

    for (const definition of definitions) {
      assert.equal(
        semanticOptionLabel(definition),
        `${definition.label} · ${definition.value}`,
      )
    }
    assert.equal(
      semanticOptionLabel(PERMISSION_ACTION_DEFINITIONS.find(({ value }) => value === 'view')!),
      '查看工作项 · view',
    )
    assert.equal(
      semanticOptionLabel(PERMISSION_SUBJECT_DEFINITIONS.find(({ value }) => value === 'space_role')!),
      '空间角色 · space_role',
    )
    assert.equal(
      semanticOptionLabel(PERMISSION_SCOPE_DEFINITIONS.find(({ value }) => value === 'all')!),
      '所有工作项 · all',
    )
  })

  it('clears stale key and subject id when switching to everyone or a user', () => {
    const spaceRoleKeys = ['owner', 'guest']
    const workItemRoleKeys = ['creator']

    assert.deepEqual(subjectForKind('everyone', spaceRoleKeys, workItemRoleKeys), {
      kind: 'everyone',
      key: null,
      subjectId: null,
    })
    assert.deepEqual(subjectForKind('user', spaceRoleKeys, workItemRoleKeys), {
      kind: 'user',
      key: null,
      subjectId: null,
    })
  })

  it('selects valid defaults for space and work-item roles', () => {
    assert.deepEqual(subjectForKind('space_role', ['owner', 'guest'], ['creator']), {
      kind: 'space_role',
      key: 'guest',
      subjectId: null,
    })
    assert.deepEqual(subjectForKind('space_role', ['owner', 'member'], ['creator']), {
      kind: 'space_role',
      key: 'owner',
      subjectId: null,
    })
    assert.deepEqual(subjectForKind('work_item_role', ['guest'], ['assignee', 'creator']), {
      kind: 'work_item_role',
      key: 'creator',
      subjectId: null,
    })
  })

  it('replaces only the primary subject and preserves later selectors', () => {
    const laterSelector: PermissionSubjectSelector = {
      kind: 'user',
      key: null,
      subjectId: '00000000-0000-0000-0000-000000000123',
    }
    const selectors: PermissionSubjectSelector[] = [
      { kind: 'space_role', key: 'guest', subjectId: null },
      laterSelector,
    ]
    const replacement = subjectForKind('everyone', ['guest'], ['creator'])

    const result = replacePrimarySubject(selectors, replacement)

    assert.deepEqual(result, [replacement, laterSelector])
    assert.strictEqual(result[1], laterSelector)
    assert.deepEqual(replacePrimarySubject([], replacement), [replacement])
  })

  it('clears stale scope parameters when changing scope kind', () => {
    const previous: PermissionDataScope = {
      kind: 'field_match',
      fieldKey: 'priority',
      operator: 'equals',
      values: ['high'],
    }

    const result = { ...previous, ...scopeForKind('created_by_subject') }

    assert.deepEqual(result, {
      kind: 'created_by_subject',
      fieldKey: null,
      operator: null,
      values: [],
    })
    assert.deepEqual(scopeForKind('all'), {
      kind: 'all',
      fieldKey: null,
      operator: null,
      values: [],
    })
  })
})
