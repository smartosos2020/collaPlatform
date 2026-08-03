import { Tabs } from 'antd'
import { useState, type ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import {
  getProjectSpaceSecondaryTabs,
  type ProjectSpaceSecondaryTabKey,
  type ProjectSpaceSecondaryTabView,
} from '../projectSpaceSecondaryTabs'
import {
  patchProjectSpaceSearch,
  projectSpaceLocationWithContext,
  type ProjectSpaceQueryKey,
} from '../projectSpaceRouteContract'

type ProjectSpaceSecondaryTabsProps<View extends ProjectSpaceSecondaryTabView> = {
  view: View
  panels: Partial<Record<ProjectSpaceSecondaryTabKey<View>, ReactNode>>
  canManage?: boolean
  testId: string
  ariaLabel: string
  queryParameter?: ProjectSpaceQueryKey
  navigationMode?: 'route' | 'local'
}

export function ProjectSpaceSecondaryTabs<View extends ProjectSpaceSecondaryTabView>({
  view,
  panels,
  canManage = false,
  testId,
  ariaLabel,
  queryParameter = 'panel',
  navigationMode = 'route',
}: ProjectSpaceSecondaryTabsProps<View>) {
  const location = useLocation()
  const navigate = useNavigate()
  const [localActiveKey, setLocalActiveKey] = useState<string>()
  const searchParams = new URLSearchParams(location.search)
  const includeKeys = Object.entries(panels)
    .filter(([, panel]) => panel !== null && panel !== undefined)
    .map(([key]) => key) as ProjectSpaceSecondaryTabKey<View>[]
  const tabs = getProjectSpaceSecondaryTabs(view, { canManage, includeKeys })

  if (tabs.length === 0) return null

  const requestedKey = searchParams.get(queryParameter)
  const activeKey = navigationMode === 'local'
    ? tabs.some((tab) => tab.key === localActiveKey)
      ? localActiveKey as ProjectSpaceSecondaryTabKey<View>
      : tabs[0].key
    : tabs.some((tab) => tab.key === requestedKey)
      ? requestedKey as ProjectSpaceSecondaryTabKey<View>
      : tabs[0].key

  const items = tabs.map((tab) => ({
    key: tab.key,
    label: tab.label,
    children: tab.key === activeKey
      ? panels[tab.key as ProjectSpaceSecondaryTabKey<View>]
      : null,
  }))

  if (items.length === 1) {
    return (
      <section
        className="project-space-secondary-tabs project-space-secondary-tabs-single"
        data-testid={testId}
        aria-label={ariaLabel}
      >
        {items[0].children}
      </section>
    )
  }

  const selectTab = (key: string) => {
    if (navigationMode === 'local') {
      setLocalActiveKey(key)
      return
    }
    const next = patchProjectSpaceSearch(searchParams, { [queryParameter]: key })
    const target = projectSpaceLocationWithContext(
      location.pathname,
      next,
      location.hash,
    )
    if (target) navigate(target, { replace: true })
  }

  return (
    <section
      className="project-space-secondary-tabs"
      data-testid={testId}
      aria-label={ariaLabel}
    >
      <Tabs
        activeKey={activeKey}
        items={items}
        onChange={selectTab}
        destroyOnHidden
      />
    </section>
  )
}
