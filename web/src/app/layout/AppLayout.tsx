import {
  BellOutlined,
  CheckSquareOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  HomeOutlined,
  MessageOutlined,
  MobileOutlined,
  ProjectOutlined,
  SearchOutlined,
  SettingOutlined,
  LogoutOutlined,
} from '@ant-design/icons'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Button, Input, Layout, Menu, Space, Typography } from 'antd'
import type { MenuProps } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useState } from 'react'

import { logout } from '../../modules/auth/api/authApi'
import { useAuthStore } from '../../modules/auth/authStore'

const { Header, Sider, Content } = Layout

const navEntries = [
  { key: '/', icon: <HomeOutlined />, label: '工作台' },
  { key: '/im', icon: <MessageOutlined />, label: '消息' },
  { key: '/projects', icon: <ProjectOutlined />, label: '项目' },
  { key: '/docs', icon: <FileTextOutlined />, label: '文档' },
  { key: '/bases', icon: <DatabaseOutlined />, label: '表格' },
  { key: '/approvals', icon: <CheckSquareOutlined />, label: '审批' },
  { key: '/notifications', icon: <BellOutlined />, label: '通知' },
  { key: '/devices', icon: <MobileOutlined />, label: '设备' },
  { key: '/search', icon: <SearchOutlined />, label: '搜索' },
  { key: '/admin/users', icon: <SettingOutlined />, label: '管理' },
]

const navItems: MenuProps['items'] = navEntries

export function AppLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const refreshToken = useAuthStore((state) => state.refreshToken)
  const currentUser = useAuthStore((state) => state.currentUser)
  const clearAuth = useAuthStore((state) => state.clearAuth)
  const [searchDraft, setSearchDraft] = useState('')

  const logoutMutation = useMutation({
    mutationFn: () => logout(refreshToken),
    onSettled: () => {
      clearAuth()
      queryClient.clear()
      navigate('/login', { replace: true })
    },
  })

  const selectedKey =
    navEntries.find((item) => {
      const key = item.key
      return key === '/' ? location.pathname === '/' : location.pathname.startsWith(key)
    })?.key?.toString() ?? '/'

  return (
    <Layout className="app-shell">
      <Sider width={216} className="app-sidebar">
        <div className="app-brand">Colla</div>
        <Menu
          mode="inline"
          selectedKeys={[selectedKey]}
          items={navItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header className="app-header">
          <Space className="app-header-content">
            <Input
              className="app-global-search"
              allowClear
              prefix={<SearchOutlined />}
              placeholder="搜索事项、文档、表格、消息"
              value={searchDraft}
              onChange={(event) => setSearchDraft(event.target.value)}
              onPressEnter={() => {
                const query = searchDraft.trim()
                if (query.length >= 2) {
                  navigate(`/search?q=${encodeURIComponent(query)}`)
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
        <Content className="app-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
