import { useMemo, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntdApp, ConfigProvider } from 'antd'

import { useAuthStore } from '../modules/auth/authStore'
import { SessionScopeProvider } from '../shared/session/SessionScopeProvider'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
    },
  },
})

export function AppProviders({ children }: { children: ReactNode }) {
  const currentUser = useAuthStore((state) => state.currentUser)
  const sessionScope = useMemo(
    () => currentUser
      ? { workspaceId: currentUser.workspaceId, userId: currentUser.id }
      : null,
    [currentUser],
  )

  return (
    <ConfigProvider
      theme={{
        token: {
          borderRadius: 8,
          borderRadiusLG: 10,
          colorPrimary: '#2563eb',
          colorInfo: '#2563eb',
          colorLink: '#2563eb',
          colorText: '#0f172a',
          colorTextSecondary: '#475569',
          colorBorder: '#e2e8f0',
          colorBorderSecondary: '#eef2f7',
          colorBgLayout: '#f6f7f9',
          colorBgContainer: '#ffffff',
          colorFillQuaternary: '#f1f5f9',
          controlHeight: 36,
          fontSize: 14,
          fontSizeHeading2: 22,
          fontSizeHeading3: 18,
          fontSizeHeading4: 16,
          fontFamily:
            'Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
        },
        components: {
          Layout: {
            headerBg: '#ffffff',
            headerHeight: 56,
            headerPadding: '0 24px',
            siderBg: '#ffffff',
            bodyBg: '#f6f7f9',
          },
          Menu: {
            itemHeight: 40,
            itemBorderRadius: 8,
            itemMarginInline: 8,
            itemColor: '#334155',
            itemHoverBg: '#f1f5f9',
            itemSelectedBg: '#eff6ff',
            itemSelectedColor: '#1d4ed8',
          },
          Button: {
            controlHeight: 36,
            fontWeight: 500,
          },
          Card: {
            paddingLG: 20,
          },
          Table: {
            headerBg: '#f8fafc',
            headerColor: '#334155',
            rowHoverBg: '#f1f5f9',
            cellPaddingBlock: 12,
          },
          Input: {
            controlHeight: 36,
          },
        },
      }}
    >
      <AntdApp>
        <QueryClientProvider client={queryClient}>
          <SessionScopeProvider scope={sessionScope}>
            {children}
          </SessionScopeProvider>
        </QueryClientProvider>
      </AntdApp>
    </ConfigProvider>
  )
}
