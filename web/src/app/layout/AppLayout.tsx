import {
  BellOutlined,
  CheckSquareOutlined,
  DatabaseOutlined,
  HomeOutlined,
  ReadOutlined,
  MenuOutlined,
  MessageOutlined,
  MobileOutlined,
  ProjectOutlined,
  SearchOutlined,
  SettingOutlined,
  LogoutOutlined,
} from '@ant-design/icons'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Alert, Button, Drawer, Input, Layout, Menu, Space, Typography } from 'antd'
import type { MenuProps } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useEffect, useState } from 'react'

import { logout } from '../../modules/auth/api/authApi'
import { useAuthStore } from '../../modules/auth/authStore'

const { Header, Sider, Content } = Layout

const navEntries = [
  { key: '/', icon: <HomeOutlined />, label: '工作台' },
  { key: '/im', icon: <MessageOutlined />, label: '消息' },
  { key: '/projects', icon: <ProjectOutlined />, label: '项目' },
  { key: '/knowledge-bases', icon: <ReadOutlined />, label: '知识库' },
  { key: '/bases', icon: <DatabaseOutlined />, label: '表格' },
  { key: '/approvals', icon: <CheckSquareOutlined />, label: '审批' },
  { key: '/notifications', icon: <BellOutlined />, label: '通知' },
  { key: '/devices', icon: <MobileOutlined />, label: '设备' },
  { key: '/search', icon: <SearchOutlined />, label: '搜索' },
  { key: '/admin/users', icon: <SettingOutlined />, label: '管理' },
]

const navItems: MenuProps['items'] = navEntries
const mobileNavEntries = navEntries.filter((item) => ['/im', '/projects', '/knowledge-bases', '/bases', '/notifications'].includes(item.key))

export function AppLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const refreshToken = useAuthStore((state) => state.refreshToken)
  const currentUser = useAuthStore((state) => state.currentUser)
  const clearAuth = useAuthStore((state) => state.clearAuth)
  const [searchDraft, setSearchDraft] = useState('')
  const [mobileNavOpen, setMobileNavOpen] = useState(false)
  const [online, setOnline] = useState(() => (typeof navigator === 'undefined' ? true : navigator.onLine))

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

  const selectedKey =
    (location.pathname.startsWith('/admin') ? '/admin/users' : undefined) ??
    navEntries.find((item) => {
      const key = item.key
      return key === '/' ? location.pathname === '/' : location.pathname.startsWith(key)
    })?.key?.toString() ?? '/'

  const goTo = (path: string) => {
    navigate(path)
    setMobileNavOpen(false)
  }

  return (
    <Layout className="app-shell">
      <Sider width={108} className="app-sidebar">
        <div className="app-brand">Colla</div>
        <Menu
          mode="inline"
          selectedKeys={[selectedKey]}
          items={navItems}
          onClick={({ key }) => goTo(key)}
        />
      </Sider>
      <Layout>
        <Header className="app-header">
          <Button
            className="app-mobile-menu-button"
            type="text"
            icon={<MenuOutlined />}
            onClick={() => setMobileNavOpen(true)}
          />
          <Space className="app-header-content">
            <Typography.Text className="app-mobile-title">Colla</Typography.Text>
            <Input
              className="app-global-search"
              allowClear
              prefix={<SearchOutlined />}
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
            <Space>
              <Typography.Text>{currentUser?.displayName}</Typography.Text>
              <Button
                type="text"
                icon={<LogoutOutlined />}
                loading={logoutMutation.isPending}
                onClick={() => logoutMutation.mutate()}
              >
                退出
              </Button>
            </Space>
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
          <Menu
            mode="inline"
            selectedKeys={[selectedKey]}
            items={navItems}
            onClick={({ key }) => goTo(key)}
          />
        </Drawer>
        {!online ? (
          <Alert
            className="app-offline-banner"
            type="warning"
            showIcon
            message="当前处于离线状态，已打开页面可继续查看，新的保存操作会失败。"
          />
        ) : null}
        <Content className="app-content">
          <Outlet />
        </Content>
        <nav className="app-mobile-bottom-nav" aria-label="移动端主导航">
          {mobileNavEntries.map((item) => (
            <button
              className={item.key === selectedKey ? 'active' : ''}
              key={item.key}
              type="button"
              onClick={() => goTo(item.key)}
            >
              {item.icon}
              <span>{item.label}</span>
            </button>
          ))}
        </nav>
      </Layout>
    </Layout>
  )
}
