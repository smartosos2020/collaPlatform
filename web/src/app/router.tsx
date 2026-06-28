import { Suspense, lazy } from 'react'
import type { ComponentType, ReactElement } from 'react'
import { Navigate, createBrowserRouter } from 'react-router-dom'
import { Spin } from 'antd'

import { AppLayout } from './layout/AppLayout'
import { RequireAuth } from '../modules/auth/components/RequireAuth'

const AdminUsersPage = lazyRoute(() => import('../modules/admin/pages/AdminUsersPage'), 'AdminUsersPage')
const AdminAuditLogsPage = lazyRoute(
  () => import('../modules/admin/pages/AdminAuditLogsPage'),
  'AdminAuditLogsPage',
)
const AdminDepartmentsPage = lazyRoute(
  () => import('../modules/admin/pages/AdminDepartmentsPage'),
  'AdminDepartmentsPage',
)
const AdminUserGroupsPage = lazyRoute(
  () => import('../modules/admin/pages/AdminUserGroupsPage'),
  'AdminUserGroupsPage',
)
const AdminRolesPage = lazyRoute(() => import('../modules/admin/pages/AdminRolesPage'), 'AdminRolesPage')
const AdminPermissionGovernancePage = lazyRoute(
  () => import('../modules/admin/pages/AdminPermissionGovernancePage'),
  'AdminPermissionGovernancePage',
)
const ApprovalsPage = lazyRoute(() => import('../modules/approvals/pages/ApprovalsPage'), 'ApprovalsPage')
const AuthLoginPage = lazyRoute(() => import('../modules/auth/pages/AuthLoginPage'), 'AuthLoginPage')
const BasesPage = lazyRoute(() => import('../modules/bases/pages/BasesPage'), 'BasesPage')
const DashboardPage = lazyRoute(() => import('../modules/dashboard/pages/DashboardPage'), 'DashboardPage')
const DevicesPage = lazyRoute(() => import('../modules/devices/pages/DevicesPage'), 'DevicesPage')
const DocsPage = lazyRoute(() => import('../modules/docs/pages/DocsPage'), 'DocsPage')
const MessengerPage = lazyRoute(() => import('../modules/messenger/pages/MessengerPage'), 'MessengerPage')
const NotificationsPage = lazyRoute(
  () => import('../modules/notifications/pages/NotificationsPage'),
  'NotificationsPage',
)
const ProjectsPage = lazyRoute(() => import('../modules/projects/pages/ProjectsPage'), 'ProjectsPage')
const SearchPage = lazyRoute(() => import('../modules/search/pages/SearchPage'), 'SearchPage')
const AppErrorPage = lazyRoute(() => import('../shared/components/AppErrorPage'), 'AppErrorPage')
const routeLoadingElement = (
  <div className="route-loading">
    <Spin />
  </div>
)

function lazyRoute<TModule extends Record<string, ComponentType>>(
  importer: () => Promise<TModule>,
  exportName: keyof TModule,
) {
  return lazy(async () => {
    const module = await importer()
    return { default: module[exportName] }
  })
}

function routeElement(element: ReactElement) {
  return <Suspense fallback={routeLoadingElement}>{element}</Suspense>
}

export const router = createBrowserRouter([
  {
    path: '/login',
    element: routeElement(<AuthLoginPage />),
  },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: routeElement(<DashboardPage />) },
      { path: 'im', element: routeElement(<MessengerPage />) },
      { path: 'messages', element: <Navigate to="/im" replace /> },
      { path: 'projects', element: routeElement(<ProjectsPage />) },
      { path: 'projects/:projectId', element: routeElement(<ProjectsPage />) },
      { path: 'issues/:issueId', element: routeElement(<ProjectsPage />) },
      { path: 'docs', element: routeElement(<DocsPage />) },
      { path: 'docs/:docId', element: routeElement(<DocsPage />) },
      { path: 'bases', element: routeElement(<BasesPage />) },
      { path: 'bases/:baseId', element: routeElement(<BasesPage />) },
      { path: 'bases/:baseId/tables/:tableId', element: routeElement(<BasesPage />) },
      { path: 'bases/:baseId/tables/:tableId/records/:recordId', element: routeElement(<BasesPage />) },
      { path: 'approvals', element: routeElement(<ApprovalsPage />) },
      { path: 'approvals/:approvalId', element: routeElement(<ApprovalsPage />) },
      { path: 'notifications', element: routeElement(<NotificationsPage />) },
      { path: 'devices', element: routeElement(<DevicesPage />) },
      { path: 'search', element: routeElement(<SearchPage />) },
      { path: 'admin/users', element: routeElement(<AdminUsersPage />) },
      { path: 'admin/departments', element: routeElement(<AdminDepartmentsPage />) },
      { path: 'admin/user-groups', element: routeElement(<AdminUserGroupsPage />) },
      { path: 'admin/roles', element: routeElement(<AdminRolesPage />) },
      { path: 'admin/permission-governance', element: routeElement(<AdminPermissionGovernancePage />) },
      { path: 'admin/audit-logs', element: routeElement(<AdminAuditLogsPage />) },
      { path: 'error/:state', element: routeElement(<AppErrorPage />) },
      { path: '*', element: routeElement(<AppErrorPage stateOverride="404" />) },
    ],
  },
])
