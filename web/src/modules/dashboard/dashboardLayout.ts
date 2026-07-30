/**
 * Pure helpers for the workspace dashboard card layout.
 * The backend owns the complete 12-card catalog; every dashboard content card
 * participates in the same visibility and ordering contract.
 */
export type DashboardLayoutCard = {
  cardKey: string
  title: string
  position: number
  hidden: boolean
  configurable: boolean
}

export function sortLayoutCards<T extends Pick<DashboardLayoutCard, 'position' | 'cardKey'>>(
  cards: readonly T[],
): T[] {
  return [...cards].sort(
    (left, right) => left.position - right.position || left.cardKey.localeCompare(right.cardKey),
  )
}

/** Reassign dense, unique positions (0..n-1) following the given order. */
function normalizePositions(cards: DashboardLayoutCard[]): DashboardLayoutCard[] {
  return cards.map((card, index) => ({ ...card, position: index }))
}

/** Toggle a card's visibility; positions stay dense and unchanged otherwise. */
export function toggleCardHidden(
  cards: readonly DashboardLayoutCard[],
  cardKey: string,
  hidden: boolean,
): DashboardLayoutCard[] {
  return normalizePositions(
    sortLayoutCards(cards).map((card) => (card.cardKey === cardKey ? { ...card, hidden } : card)),
  )
}

/**
 * Move `sourceKey` to the original index of `targetKey`, shifting others.
 * This supports adjacent swaps and moving a card to the very end.
 * No-op when keys are equal or unknown. Returns a new array with dense positions.
 */
export function moveCardTo(
  cards: readonly DashboardLayoutCard[],
  sourceKey: string,
  targetKey: string,
): DashboardLayoutCard[] {
  const ordered = sortLayoutCards(cards)
  const sourceIndex = ordered.findIndex((card) => card.cardKey === sourceKey)
  const targetIndex = ordered.findIndex((card) => card.cardKey === targetKey)
  if (sourceKey === targetKey || sourceIndex < 0 || targetIndex < 0) {
    return normalizePositions(ordered)
  }
  const [source] = ordered.splice(sourceIndex, 1)
  ordered.splice(targetIndex, 0, source)
  return normalizePositions(ordered)
}

/**
 * Synchronous drag session state. Pointer move/up handlers may fire before
 * React re-renders after pointerdown, so the active source key must be
 * readable synchronously — component state alone would read stale null.
 */
export function createDragSession() {
  let activeKey: string | null = null
  return {
    begin(cardKey: string) {
      activeKey = cardKey
    },
    current() {
      return activeKey
    },
    end() {
      activeKey = null
    },
  }
}

export type DragSession = ReturnType<typeof createDragSession>

/**
 * Dashboard card-area layout modes. Persisted client-side only (the backend
 * personalization contract stores card order/visibility and must not change),
 * under a stable, per-user localStorage key.
 */
export const DASHBOARD_LAYOUT_MODES = ['balanced', 'focus', 'compact'] as const
export type DashboardLayoutMode = (typeof DASHBOARD_LAYOUT_MODES)[number]
export const DEFAULT_DASHBOARD_LAYOUT_MODE: DashboardLayoutMode = 'balanced'

/** Accept only the three known modes; anything else falls back to balanced. */
export function normalizeDashboardLayoutMode(raw: unknown): DashboardLayoutMode {
  return (DASHBOARD_LAYOUT_MODES as readonly unknown[]).includes(raw)
    ? (raw as DashboardLayoutMode)
    : DEFAULT_DASHBOARD_LAYOUT_MODE
}

/** Stable, user-scoped localStorage key for the layout mode preference. */
export function dashboardLayoutModeStorageKey(userId?: string | null): string {
  const scope = userId && userId.trim() ? userId.trim() : 'anonymous'
  return `colla.dashboard.layout-mode.${scope}`
}
