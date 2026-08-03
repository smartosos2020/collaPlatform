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
          borderRadiusSM: 6,
          borderRadiusXS: 4,
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
          controlHeight: 34,
          controlHeightSM: 26,
          controlHeightLG: 40,
          fontSize: 14,
          fontSizeHeading2: 22,
          fontSizeHeading3: 18,
          fontSizeHeading4: 16,
          marginXXS: 2,
          marginXS: 4,
          marginSM: 6,
          margin: 8,
          marginMD: 10,
          marginLG: 12,
          marginXL: 16,
          marginXXL: 24,
          paddingXXS: 2,
          paddingXS: 4,
          paddingSM: 6,
          padding: 8,
          paddingMD: 10,
          paddingLG: 12,
          paddingXL: 16,
          fontFamily:
            'Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
          // Flat design: no elevation shadows anywhere; depth is expressed
          // with borders and background layering instead.
          boxShadow: 'none',
          boxShadowSecondary: 'none',
        },
        components: {
          Layout: {
            headerBg: '#ffffff',
            headerHeight: 56,
            headerPadding: '0 12px',
            siderBg: '#ffffff',
            bodyBg: '#f6f7f9',
          },
          Menu: {
            collapsedIconSize: 20,
            collapsedWidth: 56,
            itemHeight: 40,
            itemBorderRadius: 10,
            itemMarginBlock: 3,
            itemMarginInline: 4,
            itemColor: '#334155',
            itemHoverBg: '#f4f7fb',
            itemSelectedBg: '#e8f1ff',
            itemSelectedColor: '#2563eb',
          },
          Button: {
            controlHeight: 34,
            fontWeight: 500,
            primaryShadow: 'none',
            dangerShadow: 'none',
            defaultShadow: 'none',
          },
          Card: {
            paddingLG: 8,
          },
          Table: {
            headerBg: '#f8fafc',
            headerColor: '#334155',
            rowHoverBg: '#f1f5f9',
            cellPaddingBlock: 5,
          },
          Input: {
            controlHeight: 34,
          },
          Modal: {
            contentBg: '#ffffff',
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
