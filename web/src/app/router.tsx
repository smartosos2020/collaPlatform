import { Suspense, lazy } from 'react'
import type { ComponentType, ReactElement } from 'react'
import { Navigate, createBrowserRouter } from 'react-router-dom'
import { Spin } from 'antd'

import { AuthenticatedRoot } from './layout/AuthenticatedRoot'
import { AdminConsoleShell } from './layout/AdminConsoleShell'
import { UserWorkspaceShell } from './layout/UserWorkspaceShell'
import { RequireAdmin } from '../modules/auth/components/RequireAdmin'

const AdminOverviewPage = lazyRoute(() => import('../modules/admin/pages/AdminOverviewPage'), 'AdminOverviewPage')
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
const AdminKnowledgeBasesPage = lazyRoute(
  () => import('../modules/admin/pages/AdminKnowledgeBasesPage'),
  'AdminKnowledgeBasesPage',
)
const AdminApplicationGovernancePage = lazyRoute(
  () => import('../modules/admin/pages/AdminApplicationGovernancePage'),
  'AdminApplicationGovernancePage',
)
const AdminProjectSpacesPage = lazyRoute(
  () => import('../modules/admin/pages/AdminProjectSpacesPage'),
  'AdminProjectSpacesPage',
)
const AdminSystemSettingsPage = lazyRoute(
  () => import('../modules/admin/pages/AdminSystemSettingsPage'),
  'AdminSystemSettingsPage',
)
const AdminSecurityPage = lazyRoute(
  () => import('../modules/admin/pages/AdminSystemSettingsPage'),
  'AdminSecurityPage',
)
const AdminBatchGovernancePage = lazyRoute(() => import('../modules/admin/pages/AdminBatchGovernancePage'), 'AdminBatchGovernancePage')
const ApprovalsPage = lazyRoute(() => import('../modules/approvals/pages/ApprovalsPage'), 'ApprovalsPage')
const AuthLoginPage = lazyRoute(() => import('../modules/auth/pages/AuthLoginPage'), 'AuthLoginPage')
const BasesPage = lazyRoute(() => import('../modules/bases/pages/BasesPage'), 'BasesPage')
const DashboardPage = lazyRoute(() => import('../modules/dashboard/pages/DashboardPage'), 'DashboardPage')
const DevicesPage = lazyRoute(() => import('../modules/devices/pages/DevicesPage'), 'DevicesPage')
const KnowledgeContentPage = lazyRoute(
  () => import('../modules/knowledgeBases/content/pages/KnowledgeContentPage'),
  'KnowledgeContentPage',
)
const KnowledgeBasesPage = lazyRoute(
  () => import('../modules/knowledgeBases/pages/KnowledgeBasesPage'),
  'KnowledgeBasesPage',
)
const KnowledgeBaseSpaceRoute = lazyRoute(
  () => import('../modules/knowledgeBases/pages/KnowledgeBaseSpaceRoute'),
  'KnowledgeBaseSpaceRoute',
)
const MessengerPage = lazyRoute(() => import('../modules/messenger/pages/MessengerPage'), 'MessengerPage')
const NotificationsPage = lazyRoute(
  () => import('../modules/notifications/pages/NotificationsPage'),
  'NotificationsPage',
)
const ProjectsPage = lazyRoute(() => import('../modules/projects/pages/ProjectsPage'), 'ProjectsPage')
const ProjectSpacesPage = lazyRoute(() => import('../modules/projectSpaces/pages/ProjectSpacesPage'), 'ProjectSpacesPage')
const SearchPage = lazyRoute(() => import('../modules/search/pages/SearchPage'), 'SearchPage')
const UserSettingsPage = lazyRoute(() => import('../modules/auth/pages/UserSettingsPage'), 'UserSettingsPage')
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
    element: <AuthenticatedRoot />,
    children: [
      {
        path: 'admin',
        element: (
          <RequireAdmin>
            <AdminConsoleShell />
          </RequireAdmin>
        ),
        children: [
          { index: true, element: <Navigate to="/admin/overview" replace /> },
          { path: 'overview', element: routeElement(<AdminOverviewPage />) },
          { path: 'users', element: routeElement(<AdminUsersPage />) },
          { path: 'departments', element: routeElement(<AdminDepartmentsPage />) },
          { path: 'user-groups', element: routeElement(<AdminUserGroupsPage />) },
          { path: 'roles', element: routeElement(<AdminRolesPage />) },
          { path: 'permission-governance', element: routeElement(<AdminPermissionGovernancePage />) },
          { path: 'knowledge-bases', element: routeElement(<AdminKnowledgeBasesPage />) },
          { path: 'app-governance', element: routeElement(<AdminApplicationGovernancePage />) },
          { path: 'project-spaces', element: routeElement(<AdminProjectSpacesPage />) },
          { path: 'project-spaces/:spaceId', element: routeElement(<AdminProjectSpacesPage />) },
          { path: 'system-settings', element: routeElement(<AdminSystemSettingsPage />) },
          { path: 'security', element: routeElement(<AdminSecurityPage />) },
          { path: 'batch-governance', element: routeElement(<AdminBatchGovernancePage />) },
          { path: 'audit-logs', element: routeElement(<AdminAuditLogsPage />) },
          { path: '*', element: routeElement(<AppErrorPage stateOverride="404" />) },
        ],
      },
      {
        element: <UserWorkspaceShell />,
        children: [
          { index: true, element: routeElement(<DashboardPage />) },
          { path: 'im', element: routeElement(<MessengerPage />) },
          { path: 'messages', element: <Navigate to="/im" replace /> },
          { path: 'projects', element: routeElement(<ProjectsPage />) },
          { path: 'projects/:projectId', element: routeElement(<ProjectsPage />) },
          { path: 'issues/:issueId', element: routeElement(<ProjectsPage />) },
          { path: 'project-spaces', element: routeElement(<ProjectSpacesPage />) },
          { path: 'project-spaces/:spaceId', element: routeElement(<ProjectSpacesPage />) },
          { path: 'project-spaces/:spaceId/members', element: routeElement(<ProjectSpacesPage />) },
          { path: 'project-spaces/:spaceId/settings', element: routeElement(<ProjectSpacesPage />) },
          { path: 'project-spaces/:spaceId/types', element: routeElement(<ProjectSpacesPage />) },
          { path: 'project-spaces/:spaceId/types/:typeId', element: routeElement(<ProjectSpacesPage />) },
          { path: 'knowledge-bases', element: routeElement(<KnowledgeBasesPage />) },
          { path: 'knowledge-bases/:spaceId', element: routeElement(<KnowledgeBaseSpaceRoute />) },
          { path: 'knowledge-bases/:spaceId/items/:itemId', element: routeElement(<KnowledgeContentPage />) },
          { path: 'bases', element: routeElement(<BasesPage />) },
          { path: 'bases/:baseId', element: routeElement(<BasesPage />) },
          { path: 'bases/:baseId/tables/:tableId', element: routeElement(<BasesPage />) },
          { path: 'bases/:baseId/tables/:tableId/records/:recordId', element: routeElement(<BasesPage />) },
          { path: 'approvals', element: routeElement(<ApprovalsPage />) },
          { path: 'approvals/:approvalId', element: routeElement(<ApprovalsPage />) },
          { path: 'notifications', element: routeElement(<NotificationsPage />) },
          { path: 'devices', element: routeElement(<DevicesPage />) },
          { path: 'settings', element: routeElement(<UserSettingsPage />) },
          { path: 'search', element: routeElement(<SearchPage />) },
          { path: 'error/:state', element: routeElement(<AppErrorPage />) },
          { path: '*', element: routeElement(<AppErrorPage stateOverride="404" />) },
        ],
      },
    ],
  },
])
