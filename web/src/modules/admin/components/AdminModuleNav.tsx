import {
  ApartmentOutlined,
  AuditOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Button, Space } from 'antd'
import type { ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

const adminModuleItems = [
  { label: '组织架构', path: '/admin/departments', icon: <ApartmentOutlined /> },
  { label: '成员管理', path: '/admin/users', icon: <UserOutlined /> },
  { label: '用户组', path: '/admin/user-groups', icon: <TeamOutlined /> },
  { label: '角色权限', path: '/admin/roles', icon: <SafetyCertificateOutlined /> },
  { label: '权限治理', path: '/admin/permission-governance', icon: <SafetyCertificateOutlined /> },
  { label: '审计日志', path: '/admin/audit-logs', icon: <AuditOutlined /> },
]

export function AdminModuleNav({ extra }: { extra?: ReactNode }) {
  const location = useLocation()
  const navigate = useNavigate()

  return (
    <Space className="admin-module-nav" size={8} wrap>
      {adminModuleItems.map((item) => {
        const active = location.pathname === item.path
        return (
          <Button
            key={item.path}
            className={`admin-module-nav-button ${active ? 'active' : ''}`}
            icon={item.icon}
            type={active ? 'primary' : 'default'}
            onClick={() => navigate(item.path)}
          >
            {item.label}
          </Button>
        )
      })}
      {extra ? <span className="admin-module-nav-extra">{extra}</span> : null}
    </Space>
  )
}
