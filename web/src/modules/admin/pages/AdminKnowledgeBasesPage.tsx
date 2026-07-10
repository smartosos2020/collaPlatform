import {
  AuditOutlined,
  DownloadOutlined,
  ExclamationCircleOutlined,
  FileSearchOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  StopOutlined,
  SyncOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App as AntdApp, Button, Card, Input, List, Progress, Space, Statistic, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { Key } from 'react'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { EntityAvatar } from '../../../shared/components/EntityAvatar'
import { StatusBadge } from '../../../shared/components/StatusBadge'
import { TableEmptyState } from '../../../shared/components/TableEmptyState'
import {
  archiveAdminKnowledgeBase,
  bulkGovernAdminKnowledgeBase,
  disableAdminKnowledgeBase,
  exportAdminKnowledgeBaseGovernance,
  getAdminKnowledgeBaseGovernance,
  listAdminKnowledgeBases,
  restoreAdminKnowledgeBase,
  type AdminKnowledgeBaseGovernanceRiskView,
} from '../api/adminKnowledgeBasesApi'

export function AdminKnowledgeBasesPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { message, modal } = AntdApp.useApp()
  const [spaceSearch, setSpaceSearch] = useState('')
  const [selectedSpaceId, setSelectedSpaceId] = useState<string>()
  const [selectedRiskIds, setSelectedRiskIds] = useState<Key[]>([])

  const spacesQuery = useQuery({ queryKey: ['admin', 'knowledge-bases'], queryFn: () => listAdminKnowledgeBases({ includeArchived: true }) })
  const spaces = spacesQuery.data ?? []
  const visibleSpaces = spaces.filter((space) => {
    const keyword = spaceSearch.trim().toLowerCase()
    if (!keyword) return true
    return [space.name, space.code, space.description ?? '', space.ownerName ?? ''].some((value) => value.toLowerCase().includes(keyword))
  })
  const effectiveSpaceId = spaces.some((space) => space.id === selectedSpaceId) ? selectedSpaceId : spaces[0]?.id
  const selectedSpace = spaces.find((space) => space.id === effectiveSpaceId)

  const governanceQuery = useQuery({
    queryKey: ['admin', 'knowledge-bases', effectiveSpaceId, 'governance'],
    queryFn: () => getAdminKnowledgeBaseGovernance(effectiveSpaceId as string),
    enabled: Boolean(effectiveSpaceId),
  })

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['admin', 'knowledge-bases'] }),
      queryClient.invalidateQueries({ queryKey: ['admin', 'knowledge-bases', effectiveSpaceId, 'governance'] }),
      queryClient.invalidateQueries({ queryKey: ['knowledge-bases'] }),
    ])
  }

  const disableMutation = useMutation({
    mutationFn: disableAdminKnowledgeBase,
    onSuccess: async () => {
      message.success('知识库已停用')
      await refresh()
    },
  })
  const restoreMutation = useMutation({
    mutationFn: restoreAdminKnowledgeBase,
    onSuccess: async () => {
      message.success('知识库已启用')
      await refresh()
    },
  })
  const archiveMutation = useMutation({
    mutationFn: archiveAdminKnowledgeBase,
    onSuccess: async () => {
      message.success('知识库已归档')
      await refresh()
    },
  })
  const bulkReviewMutation = useMutation({
    mutationFn: (documentIds: string[]) =>
      bulkGovernAdminKnowledgeBase(effectiveSpaceId as string, {
        documentIds,
        requestReview: true,
        reviewDueAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10),
      }),
    onSuccess: async (result) => {
      message.success(`已请求复核 ${result.reviewRequestedCount} 个内容节点`)
      setSelectedRiskIds([])
      await refresh()
    },
  })

  const risks = useMemo(() => governanceQuery.data?.risks ?? [], [governanceQuery.data?.risks])
  const selectedRiskDocuments = useMemo(
    () =>
      risks
        .filter((risk) => selectedRiskIds.includes(risk.id) && risk.resourceType === 'document')
        .map((risk) => risk.resourceId),
    [risks, selectedRiskIds],
  )

  const riskColumns: ColumnsType<AdminKnowledgeBaseGovernanceRiskView> = [
    {
      title: '风险',
      dataIndex: 'ruleCode',
      key: 'ruleCode',
      render: (_value, risk) => (
        <Space direction="vertical" size={2}>
          <Space size={6}>
            <Tag color={severityColor(risk.severity)}>{risk.severity}</Tag>
            <strong>{risk.ruleCode}</strong>
          </Space>
          <Typography.Text type="secondary">{risk.reason}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '对象',
      key: 'resource',
      width: 220,
      render: (_, risk) => (
        <Space direction="vertical" size={2}>
          <Typography.Text>{risk.title || risk.resourceType}</Typography.Text>
          <Typography.Text type="secondary">{risk.resourceType}:{risk.resourceId}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '主体',
      key: 'subject',
      width: 190,
      render: (_, risk) => (
        <Space direction="vertical" size={2}>
          <Typography.Text>{risk.subjectName || '-'}</Typography.Text>
          <Typography.Text type="secondary">{risk.subjectType || '-'} {risk.permissionLevel ? `· ${risk.permissionLevel}` : ''}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      render: (_, risk) => (
        <Space size={6}>
          {risk.actionPath ? <Button size="small" onClick={() => navigate(risk.actionPath as string)}>打开</Button> : null}
          <Button size="small" onClick={() => navigate(`/admin/audit-logs?targetType=${risk.resourceType}&targetId=${risk.resourceId}`)}>审计</Button>
        </Space>
      ),
    },
  ]

  const health = governanceQuery.data?.health
  const loading = spacesQuery.isLoading || governanceQuery.isLoading

  return (
    <Space orientation="vertical" size={16} className="page-stack admin-org-page admin-kb-governance-page">
      <section className="admin-kb-governance-layout">
        <aside className="admin-org-panel admin-kb-space-panel">
          <Input
            allowClear
            prefix={<SearchOutlined />}
            placeholder="搜索知识库名称、编码、维护人"
            value={spaceSearch}
            onChange={(event) => setSpaceSearch(event.target.value)}
          />
          <div className="admin-kb-space-list">
            {visibleSpaces.map((space) => (
              <button
                key={space.id}
                type="button"
                className={`admin-kb-space-card ${space.id === effectiveSpaceId ? 'active' : ''}`}
                onClick={() => {
                  setSelectedSpaceId(space.id)
                  setSelectedRiskIds([])
                }}
              >
                <EntityAvatar value={space.name} />
                <span>
                  <strong>{space.name}</strong>
                  <small>{space.code}</small>
                  <Space size={6} wrap>
                    <StatusBadge status={space.status} />
                    <Tag>{space.documentCount} 内容</Tag>
                  </Space>
                </span>
              </button>
            ))}
            {!visibleSpaces.length ? <TableEmptyState icon={<FileSearchOutlined />} description="暂无知识库" /> : null}
          </div>
        </aside>

        <main className="admin-kb-governance-main">
          <Card className="content-card admin-kb-governance-hero" loading={loading}>
            <Space align="start" className="admin-kb-hero-content">
              <EntityAvatar value={selectedSpace?.name} className="admin-kb-hero-avatar" />
              <div>
                <Space wrap>
                  <Typography.Title level={3}>{selectedSpace?.name ?? '选择知识库'}</Typography.Title>
                  {selectedSpace ? <StatusBadge status={selectedSpace.status} /> : null}
                  {selectedSpace ? <Tag>{selectedSpace.visibility}</Tag> : null}
                </Space>
                <Typography.Text type="secondary">{selectedSpace?.description || selectedSpace?.code || '从左侧选择一个知识库查看治理数据'}</Typography.Text>
                {selectedSpace ? (
                  <Space wrap className="admin-kb-hero-tags">
                    <Tag>维护人 {selectedSpace.ownerName || '-'}</Tag>
                    <Tag>默认权限 {selectedSpace.defaultPermissionLevel}</Tag>
                    <Tag>首页 {selectedSpace.homeDocumentId}</Tag>
                  </Space>
                ) : null}
              </div>
            </Space>
            {selectedSpace ? (
              <Space wrap>
                {selectedSpace.status === 'active' ? (
                  <Button
                    danger
                    icon={<StopOutlined />}
                    loading={disableMutation.isPending}
                    onClick={() => modal.confirm({
                      title: '停用知识库',
                      content: `确认停用 ${selectedSpace.name}？用户侧将不可继续写入。`,
                      onOk: () => disableMutation.mutate(selectedSpace.id),
                    })}
                  >
                    停用
                  </Button>
                ) : (
                  <Button icon={<SyncOutlined />} loading={restoreMutation.isPending} onClick={() => restoreMutation.mutate(selectedSpace.id)}>
                    启用
                  </Button>
                )}
                <Button
                  danger
                  loading={archiveMutation.isPending}
                  onClick={() => modal.confirm({
                    title: '归档知识库',
                    content: `确认归档 ${selectedSpace.name}？`,
                    onOk: () => archiveMutation.mutate(selectedSpace.id),
                  })}
                >
                  归档
                </Button>
                <Button icon={<SafetyCertificateOutlined />} onClick={() => navigate(`/admin/permission-governance?knowledgeBaseId=${selectedSpace.id}`)}>
                  权限风险
                </Button>
                <Button icon={<AuditOutlined />} onClick={() => navigate(`/admin/audit-logs?targetType=knowledge_base&targetId=${selectedSpace.id}`)}>
                  审计
                </Button>
                <Button icon={<DownloadOutlined />} onClick={() => exportGovernance(selectedSpace.id)}>
                  导出治理
                </Button>
              </Space>
            ) : null}
          </Card>

          <section className="admin-kb-health-grid">
            <Card loading={loading}>
              <Statistic title="内容节点" value={health?.activeDocumentCount ?? 0} suffix={`/ ${health?.documentCount ?? 0}`} />
            </Card>
            <Card loading={loading}>
              <Statistic title="维护缺口" value={(health?.unmaintainedDocumentCount ?? 0) + (health?.ownerlessDocumentCount ?? 0)} />
            </Card>
            <Card loading={loading}>
              <Statistic title="高风险权限" value={health?.highRiskPermissionCount ?? 0} prefix={<ExclamationCircleOutlined />} />
            </Card>
            <Card loading={loading}>
              <Typography.Text type="secondary">块覆盖率</Typography.Text>
              <Progress percent={Math.round(health?.blockCoveragePercent ?? 0)} size="small" />
            </Card>
          </section>

          <section className="admin-detail-card-grid admin-kb-governance-grid">
            <Card
              className="admin-data-card"
              title="知识库治理风险"
              extra={
                <Button
                  type="primary"
                  disabled={!selectedRiskDocuments.length}
                  loading={bulkReviewMutation.isPending}
                  onClick={() => bulkReviewMutation.mutate(selectedRiskDocuments)}
                >
                  请求复核选中内容
                </Button>
              }
            >
              <Table
                rowKey="id"
                size="small"
                columns={riskColumns}
                dataSource={risks}
                loading={governanceQuery.isLoading}
                rowSelection={{
                  selectedRowKeys: selectedRiskIds,
                  onChange: setSelectedRiskIds,
                  getCheckboxProps: (risk) => ({ disabled: risk.resourceType !== 'document' }),
                }}
                locale={{ emptyText: <TableEmptyState icon={<SafetyCertificateOutlined />} description="暂无治理风险" /> }}
                pagination={{ pageSize: 6, showSizeChanger: false }}
              />
            </Card>

            <Card className="admin-data-card" title="访问与搜索治理">
              <section className="admin-kb-access-grid">
                <Card size="small">
                  <Statistic title="访问次数" value={governanceQuery.data?.accessStats.accessCount ?? 0} />
                  <Typography.Text type="secondary">访客 {governanceQuery.data?.accessStats.visitorCount ?? 0}</Typography.Text>
                </Card>
                <Card size="small" title="低访问内容">
                  <List
                    size="small"
                    dataSource={governanceQuery.data?.accessStats.lowAccessDocuments ?? []}
                    locale={{ emptyText: <TableEmptyState description="暂无低访问内容" /> }}
                    renderItem={(item) => (
                      <List.Item>
                        <List.Item.Meta title={item.document.title} description={`访问 ${item.accessCount} · 访客 ${item.visitorCount}`} />
                      </List.Item>
                    )}
                  />
                </Card>
                <Card size="small" title="搜索无结果词">
                  <List
                    size="small"
                    dataSource={governanceQuery.data?.accessStats.noResultTerms ?? []}
                    locale={{ emptyText: <TableEmptyState description="暂无无结果词" /> }}
                    renderItem={(term) => (
                      <List.Item>
                        <List.Item.Meta title={term.query} description={`${term.count} 次 · ${new Date(term.lastSearchedAt).toLocaleString()}`} />
                      </List.Item>
                    )}
                  />
                </Card>
              </section>
            </Card>
          </section>
        </main>
      </section>
    </Space>
  )

  async function exportGovernance(spaceId: string) {
    const csv = await exportAdminKnowledgeBaseGovernance(spaceId)
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `knowledge-base-governance-${spaceId}.csv`
    link.click()
    URL.revokeObjectURL(url)
  }
}

function severityColor(severity: string) {
  if (severity === 'critical') return 'red'
  if (severity === 'high') return 'orange'
  if (severity === 'medium') return 'gold'
  return 'blue'
}
