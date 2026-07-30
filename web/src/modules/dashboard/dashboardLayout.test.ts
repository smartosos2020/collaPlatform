import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

import {
  createDragSession,
  dashboardLayoutModeStorageKey,
  moveCardTo,
  normalizeDashboardLayoutMode,
  sortLayoutCards,
  toggleCardHidden,
} from './dashboardLayout.ts'
import type { DashboardLayoutCard } from './dashboardLayout.ts'

function card(cardKey: string, position: number, hidden = false): DashboardLayoutCard {
  return { cardKey, title: cardKey, position, hidden, configurable: true }
}

const fixture = [
  card('personal.todo', 2),
  card('objects.recent', 0),
  card('drafts.own', 1),
]

describe('sortLayoutCards', () => {
  it('orders by position then cardKey without mutating input', () => {
    const sorted = sortLayoutCards(fixture)
    assert.deepEqual(sorted.map((c) => c.cardKey), ['objects.recent', 'drafts.own', 'personal.todo'])
    assert.deepEqual(fixture.map((c) => c.cardKey), ['personal.todo', 'objects.recent', 'drafts.own'])
  })
})

describe('toggleCardHidden', () => {
  it('hides a card and keeps dense positions', () => {
    const next = toggleCardHidden(fixture, 'drafts.own', true)
    assert.equal(next.find((c) => c.cardKey === 'drafts.own')?.hidden, true)
    assert.deepEqual(next.map((c) => c.position), [0, 1, 2])
    assert.deepEqual(next.map((c) => c.cardKey), ['objects.recent', 'drafts.own', 'personal.todo'])
  })

  it('restores visibility when re-checked', () => {
    const hidden = toggleCardHidden(fixture, 'objects.recent', true)
    const restored = toggleCardHidden(hidden, 'objects.recent', false)
    assert.equal(restored.find((c) => c.cardKey === 'objects.recent')?.hidden, false)
    assert.deepEqual(restored.map((c) => c.position), [0, 1, 2])
  })
})

describe('moveCardTo', () => {
  it('swaps adjacent cards', () => {
    const next = moveCardTo(fixture, 'objects.recent', 'drafts.own')
    assert.deepEqual(next.map((c) => c.cardKey), ['drafts.own', 'objects.recent', 'personal.todo'])
    assert.deepEqual(next.map((c) => c.position), [0, 1, 2])
  })

  it('moves the first card to the very end', () => {
    const next = moveCardTo(fixture, 'objects.recent', 'personal.todo')
    assert.deepEqual(next.map((c) => c.cardKey), ['drafts.own', 'personal.todo', 'objects.recent'])
    assert.deepEqual(next.map((c) => c.position), [0, 1, 2])
  })

  it('moves the last card to the front', () => {
    const next = moveCardTo(fixture, 'personal.todo', 'objects.recent')
    assert.deepEqual(next.map((c) => c.cardKey), ['personal.todo', 'objects.recent', 'drafts.own'])
    assert.deepEqual(next.map((c) => c.position), [0, 1, 2])
  })

  it('is a no-op when source equals target', () => {
    const next = moveCardTo(fixture, 'drafts.own', 'drafts.own')
    assert.deepEqual(next.map((c) => c.cardKey), ['objects.recent', 'drafts.own', 'personal.todo'])
  })

  it('is a no-op for unknown keys', () => {
    const next = moveCardTo(fixture, 'missing', 'objects.recent')
    assert.deepEqual(next.map((c) => c.cardKey), ['objects.recent', 'drafts.own', 'personal.todo'])
  })

  it('preserves hidden flags while reordering', () => {
    const withHidden = fixture.map((c) => (c.cardKey === 'drafts.own' ? { ...c, hidden: true } : c))
    const next = moveCardTo(withHidden, 'drafts.own', 'objects.recent')
    assert.equal(next[0].cardKey, 'drafts.own')
    assert.equal(next[0].hidden, true)
  })

  it('keeps all twelve card positions dense when moving a newly configurable card', () => {
    const keys = [
      'drafts.own',
      'objects.favorites',
      'objects.recent',
      'personal.participating',
      'personal.responsible',
      'personal.todo',
      'personal.watching',
      'work.recent',
      'conversations.unread',
      'approvals.todo',
      'notifications.latest',
      'content.recent',
    ]
    const cards = keys.map((cardKey, position) => card(cardKey, position))

    const next = moveCardTo(cards, 'content.recent', 'personal.todo')

    assert.equal(next[5].cardKey, 'content.recent')
    assert.deepEqual(next.map((item) => item.position), Array.from({ length: 12 }, (_, index) => index))
    assert.deepEqual(new Set(next.map((item) => item.cardKey)), new Set(keys))
  })
})

describe('createDragSession', () => {
  it('exposes the source key synchronously right after pointerdown (no render needed)', () => {
    const session = createDragSession()
    session.begin('personal.todo')
    // rapid move/up arriving before any re-render still see the source
    assert.equal(session.current(), 'personal.todo')
    assert.equal(session.current(), 'personal.todo')
  })

  it('starts empty and clears on end/cancel', () => {
    const session = createDragSession()
    assert.equal(session.current(), null)
    session.begin('drafts.own')
    session.end()
    assert.equal(session.current(), null)
  })

  it('a new begin replaces the previous source', () => {
    const session = createDragSession()
    session.begin('personal.todo')
    session.begin('objects.recent')
    assert.equal(session.current(), 'objects.recent')
  })
})

describe('normalizeDashboardLayoutMode', () => {
  it('accepts the three known modes', () => {
    assert.equal(normalizeDashboardLayoutMode('balanced'), 'balanced')
    assert.equal(normalizeDashboardLayoutMode('focus'), 'focus')
    assert.equal(normalizeDashboardLayoutMode('compact'), 'compact')
  })

  it('falls back to balanced for unknown or malformed values', () => {
    assert.equal(normalizeDashboardLayoutMode('grid'), 'balanced')
    assert.equal(normalizeDashboardLayoutMode(''), 'balanced')
    assert.equal(normalizeDashboardLayoutMode(null), 'balanced')
    assert.equal(normalizeDashboardLayoutMode(undefined), 'balanced')
    assert.equal(normalizeDashboardLayoutMode(3), 'balanced')
    assert.equal(normalizeDashboardLayoutMode('{"mode":"compact"}'), 'balanced')
  })
})

describe('dashboardLayoutModeStorageKey', () => {
  it('scopes the key by user id', () => {
    assert.equal(dashboardLayoutModeStorageKey('u-1'), 'colla.dashboard.layout-mode.u-1')
    assert.equal(dashboardLayoutModeStorageKey('u-2'), 'colla.dashboard.layout-mode.u-2')
    assert.notEqual(dashboardLayoutModeStorageKey('u-1'), dashboardLayoutModeStorageKey('u-2'))
  })

  it('uses a named anonymous scope when no user id is available', () => {
    assert.equal(dashboardLayoutModeStorageKey(null), 'colla.dashboard.layout-mode.anonymous')
    assert.equal(dashboardLayoutModeStorageKey(undefined), 'colla.dashboard.layout-mode.anonymous')
    assert.equal(dashboardLayoutModeStorageKey('  '), 'colla.dashboard.layout-mode.anonymous')
  })
})
