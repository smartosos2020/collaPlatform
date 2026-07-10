import {
  AppstoreOutlined,
  AuditOutlined,
  CheckCircleOutlined,
  ClusterOutlined,
  DatabaseOutlined,
  ExclamationCircleOutlined,
  MessageOutlined,
  ProjectOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Button, Card, Segmented, Space, Statistic, Tag, Typography } from 'antd'
import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { getApplicationGovernance, type AdminApplicationModuleGovernance } from '../api/applicationGovernanceApi'
import { TableEmptyState } from '../../../shared/components/TableEmptyState'

const moduleOptions = [
  { label: 'Base', value: 'base', icon: <DatabaseOutlined /> },
  { label: '项目', value: 'project', icon: <ProjectOutlined /> },
  { label: '消息', value: 'message', icon: <MessageOutlined /> },
  { label: '审批', value: 'approval', icon: <ClusterOutlined /> },
]

export function AdminApplicationGovernancePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const initialModule = searchParams.get('module') ?? 'base'
  const [selectedKey, setSelectedKey] = useState(initialModule)
  const governanceQuery = useQuery({ queryKey: ['admin', 'application-governance'], queryFn: getApplicationGovernance })
  const modules = useMemo(() => governanceQuery.data?.modules ?? [], [governanceQuery.data?.modules])
  const selectedModule = useMemo(
    () => modules.find((module) => module.key === selectedKey) ?? modules[0],
    [modules, selectedKey],
  )

  const selectModule = (value: string | number) => {
    const key = String(value)
    setSelectedKey(key)
    setSearchParams({ module: key })
  }

  if (!governanceQuery.isLoading && modules.length === 0) {
    return (
      <Space orientation="vertical" size={16} className="page-stack admin-org-page">
        <Card className="content-card">
          <TableEmptyState description="暂无应用治理数据" />
        </Card>
      </Space>
    )
  }

  return (
    <Space orientation="vertical" size={16} className="page-stack admin-org-page admin-application-governance-page">
      <Card className="content-card admin-app-governance-hero" loading={governanceQuery.isLoading}>
        <div>
          <Space align="center">
            <span className="admin-app-governance-icon"><AppstoreOutlined /></span>
            <div>
              <Typography.Title level={3}>应用治理</Typography.Title>
              <Typography.Text type="secondary">Base、项目、消息和审批的后台治理入口，不复用用户侧协作页面。</Typography.Text>
            </div>
          </Space>
        </div>
        <Segmented
          options={moduleOptions}
          value={selectedModule?.key ?? selectedKey}
          onChange={selectModule}
        />
      </Card>

      {selectedModule ? <ApplicationGovernanceDetail module={selectedModule} /> : null}
    </Space>
  )
}

function ApplicationGovernanceDetail({ module }: { module: AdminApplicationModuleGovernance }) {
  return (
    <>
      <section className="admin-app-governance-metrics">
        <Card className="admin-overview-metric-card">
          <Statistic title={module.metrics.primaryLabel} value={module.metrics.primary} />
          <Typography.Text type="secondary">{module.moduleName}主对象</Typography.Text>
        </Card>
        <Card className="admin-overview-metric-card">
          <Statistic title={module.metrics.secondaryLabel} value={module.metrics.secondary} />
          <Typography.Text type="secondary">治理影响范围</Typography.Text>
        </Card>
        <Card className="admin-overview-metric-card">
          <Statistic title={module.metrics.tertiaryLabel} value={module.metrics.tertiary} />
          <Typography.Text type="secondary">需要持续观测</Typography.Text>
        </Card>
      </section>

      <Card className="content-card admin-app-governance-summary">
        <div>
          <Space align="center">
            {moduleIcon(module.key)}
            <div>
              <Typography.Title level={4}>{module.title}</Typography.Title>
              <Typography.Text type="secondary">{module.description}</Typography.Text>
            </div>
          </Space>
        </div>
        <Space wrap>
          <Tag color="purple">后台治理</Tag>
          <Tag>用户路由 {module.userRoute}</Tag>
          <Tag>后台路由 {module.adminRoute}</Tag>
        </Space>
      </Card>

      <section className="admin-detail-card-grid admin-app-governance-grid">
        <Card className="admin-data-card" title="治理策略">
          <div className="admin-app-list">
            {module.policies.map((policy) => (
              <div className="admin-app-list-item" key={policy}>
                <CheckCircleOutlined className="admin-app-policy-icon" />
                <div>
                  <Typography.Text code>{policy}</Typography.Text>
                  <Typography.Paragraph type="secondary">{policyDescription(policy)}</Typography.Paragraph>
                </div>
              </div>
            ))}
          </div>
        </Card>

        <Card className="admin-data-card" title="风险与待办">
          {module.risks.length === 0 ? (
            <TableEmptyState description="暂无治理风险" />
          ) : (
            <div className="admin-app-list">
              {module.risks.map((risk) => (
                <div className="admin-app-list-item" key={`${risk.severity}-${risk.title}`}>
                  <ExclamationCircleOutlined className={`admin-overview-risk-icon ${risk.severity}`} />
                  <div>
                    <Space>
                      <Tag color={severityColor(risk.severity)}>{risk.severity}</Tag>
                      <Typography.Text strong>{risk.title}</Typography.Text>
                    </Space>
                    <Typography.Paragraph type="secondary">{risk.reason}</Typography.Paragraph>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card className="admin-data-card" title="后台深链">
          <Space wrap>
            {module.adminLinks.map((link) => (
              <Link to={link.path} key={link.path}>
                <Button icon={link.label.includes('审计') ? <AuditOutlined /> : <SafetyCertificateOutlined />}>{link.label}</Button>
              </Link>
            ))}
          </Space>
          <Typography.Paragraph className="admin-app-route-note" type="secondary">
            用户侧路由仅作为协作入口说明，不作为后台页面主体或默认跳转目标。
          </Typography.Paragraph>
        </Card>

        <Card className="admin-data-card" title="边界规则">
          <div className="admin-app-list compact">
            {module.boundaryRules.map((rule) => (
              <div className="admin-app-list-item" key={rule}>
                <Typography.Text>{rule}</Typography.Text>
              </div>
            ))}
          </div>
        </Card>
      </section>
    </>
  )
}

function moduleIcon(key: string) {
  if (key === 'base') return <DatabaseOutlined className="admin-app-summary-icon" />
  if (key === 'project') return <ProjectOutlined className="admin-app-summary-icon" />
  if (key === 'message') return <MessageOutlined className="admin-app-summary-icon" />
  return <ClusterOutlined className="admin-app-summary-icon" />
}

function severityColor(severity: string) {
  if (severity === 'critical') return 'red'
  if (severity === 'high') return 'orange'
  if (severity === 'medium') return 'gold'
  return 'green'
}

function policyDescription(policy: string) {
  if (policy.includes('permission')) return '权限、主体和可见范围需要进入后台治理和审计。'
  if (policy.includes('audit')) return '后台只展示审计检索和风险排查，不展示用户协作正文。'
  if (policy.includes('template')) return '模板生命周期、发布、停用和版本应由后台配置承载。'
  if (policy.includes('retention')) return '留存、保留期和导出边界应由后台策略统一控制。'
  return '作为后台治理能力登记，后续扩展策略表单和自动检查。'
}
