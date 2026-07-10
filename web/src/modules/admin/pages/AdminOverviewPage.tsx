import {
  ApartmentOutlined,
  AuditOutlined,
  ExclamationCircleOutlined,
  FileSearchOutlined,
  PartitionOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Button, Card, List, Space, Statistic, Tag, Typography } from 'antd'
import { Link } from 'react-router-dom'

import { listMembers } from '../api/adminUsersApi'
import { flattenDepartmentTree, listDepartmentTree } from '../api/departmentsApi'
import { listPermissionRisks } from '../api/permissionGovernanceApi'
import { listRoles } from '../api/rolesApi'
import { listUserGroups } from '../api/userGroupsApi'
import { listAuditLogs } from '../api/auditLogsApi'

export function AdminOverviewPage() {
  const membersQuery = useQuery({ queryKey: ['admin', 'overview', 'members'], queryFn: () => listMembers() })
  const departmentsQuery = useQuery({ queryKey: ['admin', 'overview', 'departments'], queryFn: listDepartmentTree })
  const groupsQuery = useQuery({ queryKey: ['admin', 'overview', 'user-groups'], queryFn: () => listUserGroups() })
  const rolesQuery = useQuery({ queryKey: ['admin', 'overview', 'roles'], queryFn: listRoles })
  const risksQuery = useQuery({ queryKey: ['admin', 'overview', 'permission-risks'], queryFn: () => listPermissionRisks() })
  const auditQuery = useQuery({ queryKey: ['admin', 'overview', 'audit-logs'], queryFn: () => listAuditLogs({ limit: 8 }) })
  const departments = flattenDepartmentTree(departmentsQuery.data ?? [])
  const activeMembers = (membersQuery.data ?? []).filter((member) => member.status === 'active').length
  const disabledMembers = (membersQuery.data ?? []).filter((member) => member.status === 'disabled').length
  const activeDepartments = departments.filter((department) => department.status === 'active').length
  const disabledDepartments = departments.filter((department) => department.status === 'disabled').length
  const activeGroups = (groupsQuery.data ?? []).filter((group) => group.status === 'active').length
  const activeRoles = (rolesQuery.data ?? []).filter((role) => role.status === 'active').length
  const risks = risksQuery.data?.items ?? []
  const highRiskCount = risks.filter((risk) => ['high', 'critical'].includes(risk.severity)).length
  const recentAudits = auditQuery.data ?? []
  const loading = membersQuery.isLoading || departmentsQuery.isLoading || groupsQuery.isLoading || rolesQuery.isLoading || risksQuery.isLoading || auditQuery.isLoading

  return (
    <Space orientation="vertical" size={18} className="page-stack admin-org-page admin-overview-page">
      <section className="admin-overview-grid">
        <Card className="admin-overview-metric-card" loading={loading}>
          <Statistic title="组织健康" value={activeDepartments} suffix={`/ ${departments.length} 部门`} prefix={<ApartmentOutlined />} />
          <Typography.Text type="secondary">停用部门 {disabledDepartments}</Typography.Text>
        </Card>
        <Card className="admin-overview-metric-card" loading={loading}>
          <Statistic title="成员治理" value={activeMembers} suffix={`/ ${membersQuery.data?.length ?? 0} 成员`} prefix={<UserOutlined />} />
          <Typography.Text type="secondary">停用成员 {disabledMembers}</Typography.Text>
        </Card>
        <Card className="admin-overview-metric-card" loading={loading}>
          <Statistic title="权限风险" value={highRiskCount} suffix={`/ ${risksQuery.data?.total ?? 0} 风险`} prefix={<SafetyCertificateOutlined />} />
          <Typography.Text type="secondary">角色 {activeRoles} · 用户组 {activeGroups}</Typography.Text>
        </Card>
        <Card className="admin-overview-metric-card" loading={loading}>
          <Statistic title="审计摘要" value={recentAudits.length} suffix="条最近记录" prefix={<AuditOutlined />} />
          <Typography.Text type="secondary">用于追踪组织、权限和内容治理操作</Typography.Text>
        </Card>
      </section>

      <section className="admin-overview-panels">
        <Card title="待处理治理事项" className="admin-overview-panel" loading={loading}>
          <List
            dataSource={risks.slice(0, 5)}
            locale={{ emptyText: '暂无权限治理风险' }}
            renderItem={(risk) => (
              <List.Item>
                <List.Item.Meta
                  avatar={<ExclamationCircleOutlined className={`admin-overview-risk-icon ${risk.severity}`} />}
                  title={<Space><Tag color={severityColor(risk.severity)}>{risk.severity}</Tag><span>{risk.ruleCode}</span></Space>}
                  description={risk.reason}
                />
              </List.Item>
            )}
          />
        </Card>
        <Card title="最近审计摘要" className="admin-overview-panel" loading={loading}>
          <List
            dataSource={recentAudits.slice(0, 5)}
            locale={{ emptyText: '暂无审计记录' }}
            renderItem={(entry) => (
              <List.Item>
                <List.Item.Meta
                  title={<Space><Tag>{entry.action}</Tag><span>{entry.actorName || '系统'}</span></Space>}
                  description={`${entry.targetType}${entry.targetId ? `:${entry.targetId}` : ''} · ${new Date(entry.createdAt).toLocaleString()}`}
                />
              </List.Item>
            )}
          />
        </Card>
      </section>

      <section className="admin-overview-actions">
        <Card title="常用治理入口" className="admin-overview-panel">
          <Space wrap>
            <Link to="/admin/departments">
              <Button icon={<ApartmentOutlined />}>组织架构</Button>
            </Link>
            <Link to="/admin/users">
              <Button icon={<UserOutlined />}>成员管理</Button>
            </Link>
            <Link to="/admin/user-groups">
              <Button icon={<TeamOutlined />}>用户组</Button>
            </Link>
            <Link to="/admin/roles">
              <Button icon={<SafetyCertificateOutlined />}>角色权限</Button>
            </Link>
            <Link to="/admin/permission-governance">
              <Button type="primary" icon={<SafetyCertificateOutlined />}>权限治理</Button>
            </Link>
            <Link to="/admin/knowledge-bases">
              <Button icon={<FileSearchOutlined />}>知识库治理</Button>
            </Link>
            <Link to="/admin/app-governance">
              <Button icon={<PartitionOutlined />}>应用治理</Button>
            </Link>
          </Space>
        </Card>
      </section>
    </Space>
  )
}

function severityColor(severity: string) {
  if (severity === 'critical') return 'red'
  if (severity === 'high') return 'orange'
  if (severity === 'medium') return 'gold'
  return 'blue'
}
