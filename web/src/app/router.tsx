import { Navigate, createBrowserRouter } from 'react-router-dom'

import { AppLayout } from './layout/AppLayout'
import { AdminUsersPage } from '../modules/admin/pages/AdminUsersPage'
import { AuthLoginPage } from '../modules/auth/pages/AuthLoginPage'
import { BasesPage } from '../modules/bases/pages/BasesPage'
import { DashboardPage } from '../modules/dashboard/pages/DashboardPage'
import { DocsPage } from '../modules/docs/pages/DocsPage'
import { MessengerPage } from '../modules/messenger/pages/MessengerPage'
import { NotificationsPage } from '../modules/notifications/pages/NotificationsPage'
import { ProjectsPage } from '../modules/projects/pages/ProjectsPage'

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <AuthLoginPage />,
  },
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'im', element: <MessengerPage /> },
      { path: 'messages', element: <Navigate to="/im" replace /> },
      { path: 'projects', element: <ProjectsPage /> },
      { path: 'projects/:projectId', element: <ProjectsPage /> },
      { path: 'issues/:issueId', element: <ProjectsPage /> },
      { path: 'docs', element: <DocsPage /> },
      { path: 'docs/:docId', element: <DocsPage /> },
      { path: 'bases', element: <BasesPage /> },
      { path: 'bases/:baseId', element: <BasesPage /> },
      { path: 'bases/:baseId/tables/:tableId', element: <BasesPage /> },
      { path: 'notifications', element: <NotificationsPage /> },
      { path: 'admin/users', element: <AdminUsersPage /> },
      { path: 'admin/roles', element: <AdminUsersPage /> },
    ],
  },
])
