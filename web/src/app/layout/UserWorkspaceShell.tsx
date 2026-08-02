import {
  LogoutOutlined,
  MenuFoldOutlined,
  MenuOutlined,
  MenuUnfoldOutlined,
  SearchOutlined,
  SettingOutlined,
} from '@ant-design/icons'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Alert, Avatar, Button, Drawer, Dropdown, Input, Layout, Menu, Space, Typography } from 'antd'
import type { MenuProps } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useEffect, useMemo, useState } from 'react'

import { logout } from '../../modules/auth/api/authApi'
import { canAccessAdmin } from '../../modules/auth/authorization'
import { useAuthStore } from '../../modules/auth/authStore'
import { mobileUserNavEntries, userNavEntries } from '../navigation/userWorkspaceNav'
import { RealtimeHealthBanner } from '../realtime/RealtimeHealthBanner'
import {
  normalizeUserWorkspaceSidebarState,
  userWorkspaceSidebarStorageKey,
} from './userWorkspaceSidebar'

const { Header, Sider, Content } = Layout

export function UserWorkspaceShell() {
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const refreshToken = useAuthStore((state) => state.refreshToken)
  const currentUser = useAuthStore((state) => state.currentUser)
  const clearAuth = useAuthStore((state) => state.clearAuth)
  const [searchDraft, setSearchDraft] = useState('')
  const [mobileNavOpen, setMobileNavOpen] = useState(false)
  const [online, setOnline] = useState(() => (typeof navigator === 'undefined' ? true : navigator.onLine))
  const adminVisible = canAccessAdmin(currentUser)
  const sidebarStorageKey = userWorkspaceSidebarStorageKey(currentUser?.id)
  const storedSidebarState = useMemo(
    () => readStoredSidebarState(sidebarStorageKey),
    [sidebarStorageKey],
  )
  const [sidebarChoice, setSidebarChoice] = useState<{ key: string; collapsed: boolean } | null>(null)
  const sidebarCollapsed = sidebarChoice?.key === sidebarStorageKey
    ? sidebarChoice.collapsed
    : storedSidebarState === 'collapsed'
  const accountName = currentUser?.displayName || currentUser?.username || '用户'

  useEffect(() => {
    const handleOnline = () => setOnline(true)
    const handleOffline = () => setOnline(false)
    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)
    return () => {
      window.removeEventListener('online', handleOnline)
      window.removeEventListener('offline', handleOffline)
    }
  }, [])

  const logoutMutation = useMutation({
    mutationFn: () => logout(refreshToken),
    onSettled: () => {
      clearAuth()
      queryClient.clear()
      navigate('/login', { replace: true })
    },
  })

  const accountMenuItems = useMemo<MenuProps['items']>(() => {
    const items: MenuProps['items'] = [
      {
        key: 'profile-card',
        disabled: true,
        label: (
          <div className="app-account-menu-card">
            <Avatar size={44} className="app-account-avatar">
              {userInitial(currentUser?.displayName || currentUser?.username)}
            </Avatar>
            <span>
              <strong>{currentUser?.displayName || currentUser?.username || '用户'}</strong>
              <small>{currentUser?.email || currentUser?.username || '当前账号'}</small>
            </span>
          </div>
        ),
      },
      { type: 'divider' },
      { key: 'status', label: '我的状态' },
      { key: 'devices', label: '登录设备' },
      { key: 'settings', icon: <SettingOutlined />, label: '个人设置' },
      { key: 'help', label: '帮助与客服' },
      { key: 'logout', icon: <LogoutOutlined />, label: '退出登录' },
    ]
    if (adminVisible) {
      items.push({ type: 'divider' }, { key: 'admin', icon: <SettingOutlined />, label: '管理后台' })
    }
    return items
  }, [adminVisible, currentUser])

  const selectedKey =
    userNavEntries.find((item) => {
      const key = item.key
      return key === '/' ? location.pathname === '/' : location.pathname.startsWith(key)
    })?.key?.toString() ?? '/'

  const goTo = (path: string) => {
    navigate(path)
    setMobileNavOpen(false)
  }

  const setSidebarCollapsed = (collapsed: boolean) => {
    setSidebarChoice({ key: sidebarStorageKey, collapsed })
    try {
      window.localStorage.setItem(sidebarStorageKey, collapsed ? 'collapsed' : 'expanded')
    } catch {
      // Storage may be unavailable in private browsing; the in-memory choice still applies.
    }
  }

  const handleAccountMenuClick: MenuProps['onClick'] = ({ key }) => {
    if (key === 'logout') {
      logoutMutation.mutate()
      return
    }
    if (key === 'admin') {
      navigate('/admin/overview')
      return
    }
    if (key === 'devices') {
      navigate('/devices')
      return
    }
    if (key === 'settings' || key === 'status') {
      navigate('/settings')
    }
  }

  return (
    <Layout className="app-shell user-workspace-shell" data-testid="user-workspace-shell">
      <Sider
        className="app-sidebar user-workspace-sidebar"
        collapsed={sidebarCollapsed}
        collapsedWidth={56}
        data-collapsed={sidebarCollapsed}
        data-testid="user-workspace-sidebar"
        theme="light"
        trigger={null}
        width={108}
      >
        <Dropdown
          menu={{ items: accountMenuItems, onClick: handleAccountMenuClick }}
          placement="bottomLeft"
          trigger={['click']}
        >
          <button
            aria-label={`打开${accountName}的账号菜单`}
            className="app-user-menu-trigger"
            data-testid="user-account-menu-trigger"
            title={accountName}
            type="button"
          >
            <Avatar size={34} className="app-account-avatar">
              {userInitial(accountName)}
            </Avatar>
          </button>
        </Dropdown>
        <Menu mode="inline" selectedKeys={[selectedKey]} items={userNavEntries} onClick={({ key }) => goTo(key)} />
        <Button
          aria-label={sidebarCollapsed ? '展开侧栏' : '收起侧栏'}
          className="app-sidebar-collapse-trigger"
          icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
          onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
          title={sidebarCollapsed ? '展开侧栏' : '收起侧栏'}
          type="text"
        >
          {sidebarCollapsed ? null : '收起'}
        </Button>
      </Sider>
      <Layout>
        <Header className="app-header">
          <Button
            className="app-mobile-menu-button"
            type="text"
            icon={<MenuOutlined />}
            aria-label="打开导航"
            onClick={() => setMobileNavOpen(true)}
          />
          <Space className="app-header-content user-workspace-header-content">
            <Typography.Text className="app-mobile-title">Colla</Typography.Text>
            <Input
              className="app-global-search"
              allowClear
              prefix={<SearchOutlined />}
              aria-label="全局搜索"
              placeholder="搜索事项、知识内容、表格、消息"
              value={searchDraft}
              onChange={(event) => setSearchDraft(event.target.value)}
              onPressEnter={() => {
                const query = searchDraft.trim()
                if (query.length >= 2) {
                  goTo(`/search?q=${encodeURIComponent(query)}`)
                }
              }}
            />
          </Space>
        </Header>
        <Drawer
          title="导航"
          placement="left"
          open={mobileNavOpen}
          onClose={() => setMobileNavOpen(false)}
          className="app-mobile-drawer"
          size="default"
        >
          <Menu mode="inline" selectedKeys={[selectedKey]} items={userNavEntries} onClick={({ key }) => goTo(key)} />
        </Drawer>
        {!online ? (
          <Alert
            className="app-offline-banner"
            type="warning"
            showIcon
            message="当前处于离线状态，已打开页面可继续查看，新的保存操作会失败。"
          />
        ) : null}
        {online ? <RealtimeHealthBanner /> : null}
        <Content className="app-content">
          <Outlet />
        </Content>
        <nav className="app-mobile-bottom-nav" aria-label="移动端主导航">
          {mobileUserNavEntries.map((item) => (
            <button className={item.key === selectedKey ? 'active' : ''} key={item.key} type="button" onClick={() => goTo(item.key)}>
              {item.icon}
              <span>{item.label}</span>
            </button>
          ))}
        </nav>
      </Layout>
    </Layout>
  )
}

function userInitial(value?: string) {
  return (value || 'U').trim().slice(0, 1).toUpperCase()
}

function readStoredSidebarState(key: string) {
  try {
    return normalizeUserWorkspaceSidebarState(window.localStorage.getItem(key))
  } catch {
    return normalizeUserWorkspaceSidebarState(null)
  }
}
