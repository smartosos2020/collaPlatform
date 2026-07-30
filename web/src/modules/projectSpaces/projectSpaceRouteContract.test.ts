import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

import {
  isCanonicalProjectSpacePath,
  legacyProjectSpaceLocation,
  patchProjectSpaceSearch,
  projectSpaceListLocation,
  projectSpaceLocationWithContext,
  resolveCanonicalProjectSpaceLocation,
  sanitizeProjectSpaceHash,
  sanitizeProjectSpaceSearch,
} from './projectSpaceRouteContract.ts'

describe('project space query contract', () => {
  it('keeps one validated value per allowlisted key and strips unknown input', () => {
    const sanitized = sanitizeProjectSpaceSearch(
      '?panel=project-plan&panel=ignored&source=m7&returnTo=https%3A%2F%2Fevil.example',
    )

    assert.equal(sanitized.toString(), 'source=m7&panel=project-plan')
  })

  it('patches only named keys and preserves the remaining navigation context', () => {
    const patched = patchProjectSpaceSearch(
      '?panel=work-item-collection&source=m7&typeId=type-1&savedViewId=view-1',
      { create: '1', savedViewId: null },
    )

    assert.equal(patched.get('panel'), 'work-item-collection')
    assert.equal(patched.get('source'), 'm7')
    assert.equal(patched.get('typeId'), 'type-1')
    assert.equal(patched.get('create'), '1')
    assert.equal(patched.has('savedViewId'), false)
  })

  it('removes invalid patched values instead of retaining ambiguous input', () => {
    const patched = patchProjectSpaceSearch('?create=1&source=ok', {
      create: 'true',
      source: 'contains a space',
    })

    assert.equal(patched.toString(), '')
  })

  it('can preserve only cross-surface trace context', () => {
    assert.equal(
      projectSpaceLocationWithContext(
        '/project-spaces/space-1/settings',
        '?source=m7&panel=project-plan&typeId=type-1',
        '#focus',
        ['source'],
      ),
      '/project-spaces/space-1/settings?source=m7#focus',
    )
  })

  it('builds the list fallback without carrying view-specific state', () => {
    assert.equal(
      projectSpaceListLocation('?source=m7&panel=project-plan&typeId=type-1', '#focus'),
      '/project-spaces?source=m7#focus',
    )
  })
})

describe('canonical legacy route contract', () => {
  it('accepts only known canonical project-space routes', () => {
    assert.equal(isCanonicalProjectSpacePath('/project-spaces/space-1'), true)
    assert.equal(isCanonicalProjectSpacePath('/project-spaces/space-1/work-items/item-1'), true)
    assert.equal(isCanonicalProjectSpacePath('/project-spaces/space-1/types/type-1/fields/field-1'), true)
    assert.equal(isCanonicalProjectSpacePath('/project-spaces/space-1/unknown'), false)
    assert.equal(isCanonicalProjectSpacePath('/project-spaces/%2e%2e/admin'), false)
  })

  it('rejects absolute, protocol-relative and non-project redirects', () => {
    for (const target of [
      'https://evil.example/project-spaces/space-1',
      '//evil.example/project-spaces/space-1',
      '/admin',
      '/project-spaces/space-1\\..\\admin',
    ]) {
      assert.equal(resolveCanonicalProjectSpaceLocation(target, '?source=m7'), null)
    }
  })

  it('merges original supported context with canonical target precedence', () => {
    assert.equal(
      resolveCanonicalProjectSpaceLocation(
        '/project-spaces/space-1/work-items/item-1?panel=workflow',
        '?panel=details&source=legacy&unknown=drop',
        '#comments',
      ),
      '/project-spaces/space-1/work-items/item-1?source=legacy&panel=workflow#comments',
    )
  })

  it('creates a mapped legacy project location without exposing invalid ids', () => {
    assert.equal(
      legacyProjectSpaceLocation(
        'space-1',
        '?source=legacy&returnTo=https%3A%2F%2Fevil.example',
        '#overview',
      ),
      '/project-spaces/space-1?source=legacy#overview',
    )
    assert.equal(legacyProjectSpaceLocation('../admin', '?source=legacy'), null)
  })

  it('keeps only bounded non-routing hashes', () => {
    assert.equal(sanitizeProjectSpaceHash('#work-item_1'), '#work-item_1')
    assert.equal(sanitizeProjectSpaceHash('#contains space'), '')
    assert.equal(sanitizeProjectSpaceHash(`#${'a'.repeat(129)}`), '')
  })
})
