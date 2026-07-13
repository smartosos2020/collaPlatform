import {
  LogoutOutlined,
  MenuOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Alert, Breadcrumb, Button, Drawer, Input, Layout, Menu, Space, Typography } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useEffect, useState } from 'react'

import { logout } from '../../modules/auth/api/authApi'
import { searchAdminGovernance } from '../../modules/admin/api/adminGovernanceSearchApi'
import { useAuthStore } from '../../modules/auth/authStore'
import { adminPages, groupedAdminNavItems } from '../navigation/adminConsoleNav'

const { Header, Sider, Content } = Layout

export function AdminConsoleShell() {
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
  const governanceSearchMutation = useMutation({
    mutationFn: (query: string) => searchAdminGovernance(query),
    onSuccess: (result) => {
      const first = result.items[0]
      if (!first) {
        navigate(`/admin/audit-logs?action=${encodeURIComponent(result.query)}`)
        return
      }
      const separator = first.adminPath.includes('?') ? '&' : '?'
      navigate(`${first.adminPath}${separator}q=${encodeURIComponent(result.query)}`)
    },
  })

  const selectedKey =
    adminPages
      .slice()
      .sort((left, right) => right.key.length - left.key.length)
      .find((item) => location.pathname.startsWith(item.key))?.key ?? '/admin/overview'
  const currentPage = adminPages.find((item) => item.key === selectedKey) ?? adminPages[0]

  const goTo = (path: string) => {
    navigate(path)
    setMobileNavOpen(false)
  }

  const searchAdmin = () => {
    const query = searchDraft.trim()
    if (query.length >= 2) {
      governanceSearchMutation.mutate(query)
    }
  }

  return (
    <Layout className="app-shell admin-console-shell" data-testid="admin-console-shell">
      <Sider width={188} className="app-sidebar admin-console-sidebar">
        <div className="admin-console-brand">
          <strong>Colla</strong>
          <span>管理后台</span>
        </div>
        <Menu mode="inline" selectedKeys={[selectedKey]} items={groupedAdminNavItems} onClick={({ key }) => goTo(key)} />
      </Sider>
      <Layout>
        <Header className="app-header admin-console-header">
          <Button
            className="app-mobile-menu-button"
            type="text"
            icon={<MenuOutlined />}
            onClick={() => setMobileNavOpen(true)}
          />
          <Space className="app-header-content">
            <Typography.Text className="app-mobile-title">管理后台</Typography.Text>
            <Input
              className="app-global-search admin-global-search"
              allowClear
              prefix={<SearchOutlined />}
              placeholder="后台治理搜索：成员、部门、角色、审计动作"
              value={searchDraft}
              onChange={(event) => setSearchDraft(event.target.value)}
              onPressEnter={searchAdmin}
              status={governanceSearchMutation.isError ? 'error' : undefined}
            />
            <Space>
              <Typography.Text className="admin-console-user">{currentUser?.displayName || currentUser?.username}</Typography.Text>
              <Button onClick={() => navigate('/')}>返回工作台</Button>
              <Button type="text" icon={<LogoutOutlined />} loading={logoutMutation.isPending} onClick={() => logoutMutation.mutate()}>
                退出
              </Button>
            </Space>
          </Space>
        </Header>
        <Drawer
          title="管理后台"
          placement="left"
          open={mobileNavOpen}
          onClose={() => setMobileNavOpen(false)}
          className="app-mobile-drawer"
          size="default"
        >
          <Menu mode="inline" selectedKeys={[selectedKey]} items={groupedAdminNavItems} onClick={({ key }) => goTo(key)} />
        </Drawer>
        {!online ? (
          <Alert className="app-offline-banner" type="warning" showIcon message="当前处于离线状态，后台操作可能无法保存。" />
        ) : null}
        <Content className="app-content admin-console-content">
          <section className="admin-console-pagebar">
            <div>
              <Breadcrumb
                items={[
                  { title: '管理后台' },
                  { title: currentPage.section },
                  { title: currentPage.label },
                ]}
              />
              <Space size={10} wrap className="admin-console-page-title">
                <span className="admin-page-icon">{currentPage.icon}</span>
                <span>
                  <Typography.Title level={2}>{currentPage.label}</Typography.Title>
                  <Typography.Text type="secondary">{currentPage.description}</Typography.Text>
                </span>
              </Space>
            </div>
          </section>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
