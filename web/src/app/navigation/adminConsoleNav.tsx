import {
  ApartmentOutlined,
  AppstoreOutlined,
  AuditOutlined,
  DashboardOutlined,
  FileSearchOutlined,
  PartitionOutlined,
  SafetyCertificateOutlined,
  SecurityScanOutlined,
  SettingOutlined,
  TeamOutlined,
  UserOutlined,
  SlidersOutlined,
} from '@ant-design/icons'
import type { MenuProps } from 'antd'
import type { ReactNode } from 'react'

export type AdminPageNavEntry = {
  key: string
  icon: ReactNode
  label: string
  section: string
  description: string
}

export const adminPages: AdminPageNavEntry[] = [
  {
    key: '/admin/overview',
    icon: <DashboardOutlined />,
    label: '企业概览',
    section: '企业概览',
    description: '组织健康、权限风险、审计摘要和待处理治理事项',
  },
  {
    key: '/admin/departments',
    icon: <ApartmentOutlined />,
    label: '组织架构',
    section: '组织与成员',
    description: '部门树、部门成员和负责人维护',
  },
  {
    key: '/admin/users',
    icon: <UserOutlined />,
    label: '成员管理',
    section: '组织与成员',
    description: '成员档案、账号状态、部门归属和密码重置',
  },
  {
    key: '/admin/user-groups',
    icon: <TeamOutlined />,
    label: '用户组',
    section: '组织与成员',
    description: '授权主体、直接成员和展开成员治理',
  },
  {
    key: '/admin/roles',
    icon: <SafetyCertificateOutlined />,
    label: '角色权限',
    section: '权限与安全',
    description: '角色、权限矩阵和角色分配',
  },
  {
    key: '/admin/permission-governance',
    icon: <SafetyCertificateOutlined />,
    label: '权限治理',
    section: '权限与安全',
    description: '权限排查、风险巡检和风险导出',
  },
  {
    key: '/admin/batch-governance',
    icon: <SlidersOutlined />,
    label: '批量治理',
    section: '权限与安全',
    description: '组织、成员、用户组和角色的批量预览、确认与结果报告',
  },
  {
    key: '/admin/security',
    icon: <SecurityScanOutlined />,
    label: '安全策略',
    section: '权限与安全',
    description: '密码、会话、登录设备和必要通知策略',
  },
  {
    key: '/admin/audit-logs',
    icon: <AuditOutlined />,
    label: '审计日志',
    section: '审计与报表',
    description: '后台审计检索、快捷筛选和上下文排查',
  },
  {
    key: '/admin/knowledge-bases',
    icon: <FileSearchOutlined />,
    label: '知识库治理',
    section: '内容与数据治理',
    description: '知识库空间、健康度、访问低效、搜索无结果和批量治理',
  },
  {
    key: '/admin/app-governance',
    icon: <PartitionOutlined />,
    label: '应用治理',
    section: '应用配置',
    description: 'Base、项目、消息、审批的配置、策略、审计和治理边界',
  },
  {
    key: '/admin/system-settings',
    icon: <SettingOutlined />,
    label: '系统设置',
    section: '系统设置',
    description: '系统级基础配置和运行参数查看',
  },
]

export const groupedAdminNavItems: MenuProps['items'] = [
  {
    type: 'group',
    label: '企业概览',
    children: [adminPages[0]],
  },
  {
    type: 'group',
    label: '组织与成员',
    children: [adminPages[1], adminPages[2], adminPages[3]],
  },
  {
    type: 'group',
    label: '权限与安全',
    children: [adminPages[4], adminPages[5], adminPages[6], adminPages[7]],
  },
  {
    type: 'group',
    label: '应用配置',
    children: [adminPages[10], { key: '/admin/app-config', icon: <AppstoreOutlined />, label: '配置中心', disabled: true }],
  },
  {
    type: 'group',
    label: '内容与数据治理',
    children: [adminPages[9]],
  },
  {
    type: 'group',
    label: '审计与报表',
    children: [adminPages[8]],
  },
  {
    type: 'group',
    label: '系统设置',
    children: [adminPages[11]],
  },
]
