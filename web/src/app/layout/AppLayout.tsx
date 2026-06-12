import {
  BellOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  HomeOutlined,
  MessageOutlined,
  ProjectOutlined,
  SettingOutlined,
} from '@ant-design/icons'
import { Layout, Menu, Space, Typography } from 'antd'
import type { MenuProps } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'

const { Header, Sider, Content } = Layout

const navEntries = [
  { key: '/', icon: <HomeOutlined />, label: '工作台' },
  { key: '/im', icon: <MessageOutlined />, label: '消息' },
  { key: '/projects', icon: <ProjectOutlined />, label: '项目' },
  { key: '/docs', icon: <FileTextOutlined />, label: '文档' },
  { key: '/bases', icon: <DatabaseOutlined />, label: '表格' },
  { key: '/notifications', icon: <BellOutlined />, label: '通知' },
  { key: '/admin/users', icon: <SettingOutlined />, label: '管理' },
]

const navItems: MenuProps['items'] = navEntries

export function AppLayout() {
  const navigate = useNavigate()
  const location = useLocation()

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
            <Typography.Text type="secondary">多端协同工作空间</Typography.Text>
          </Space>
        </Header>
        <Content className="app-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
