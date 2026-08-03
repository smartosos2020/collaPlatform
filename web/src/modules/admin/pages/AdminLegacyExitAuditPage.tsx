import { useEffect, useMemo, useRef, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import {
  createLegacyAuditSnapshot,
  decideLegacySurface,
  exportLegacyAuditSnapshot,
  listLegacyAuditSnapshots,
  type LegacySurface,
  type RemovalDecision,
} from '../api/legacyExitAuditApi'
import { errorMessage } from '../../../shared/api/errorMessage'

const totalCards = [
  ['legacyProjects', '旧项目'],
  ['legacyIssues', '旧事项'],
  ['activeItemMaps', '活动映射'],
  ['unmappedIssues', '未映射事项'],
  ['danglingMaps', '悬空映射'],
  ['legacyWriteScopes', '旧写范围'],
] as const

export function AdminLegacyExitAuditPage() {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const decisionRequestIdsRef = useRef(new Map<string, string>())
  const [online, setOnline] = useState(() => navigator.onLine)
  const snapshotsQuery = useQuery({
    queryKey: ['admin', 'legacy-exit-audit', 'snapshots'],
    queryFn: listLegacyAuditSnapshots,
  })
  const latest = snapshotsQuery.data?.[0]

  useEffect(() => {
    const onOnline = () => setOnline(true)
    const onOffline = () => setOnline(false)
    window.addEventListener('online', onOnline)
    window.addEventListener('offline', onOffline)
    return () => {
      window.removeEventListener('online', onOnline)
      window.removeEventListener('offline', onOffline)
    }
  }, [])

  const refresh = () => queryClient.invalidateQueries({
    queryKey: ['admin', 'legacy-exit-audit', 'snapshots'],
  })
  const snapshotMutation = useMutation({
    mutationFn: createLegacyAuditSnapshot,
    onSuccess: async () => {
      await refresh()
      message.success('迁移审计快照已生成')
    },
    onError: (error) => message.error(errorMessage(error, '迁移审计快照生成失败，请重试')),
  })
  const decisionMutation = useMutation({
    mutationFn: (input: {
      surfaceKey: string
      decision: RemovalDecision['decision']
      reason: string
    }) => {
      if (!latest) throw new Error('Legacy audit snapshot is required')
      const requestKey = `${latest.id}:${input.surfaceKey}:${input.decision}`
      let requestId = decisionRequestIdsRef.current.get(requestKey)
      if (!requestId) {
        requestId = `legacy-decision-${crypto.randomUUID()}`
        decisionRequestIdsRef.current.set(requestKey, requestId)
      }
      return decideLegacySurface(latest.id, {
        ...input,
        requestId,
      })
    },
    onSuccess: async (_, input) => {
      if (latest) decisionRequestIdsRef.current.delete(`${latest.id}:${input.surfaceKey}:${input.decision}`)
      await refresh()
      message.success('删除决定已追加')
    },
    onError: (error) => message.error(errorMessage(error, '删除决定提交失败，请重试')),
  })
  const exportMutation = useMutation({
    mutationFn: async () => {
      if (!latest) throw new Error('Legacy audit snapshot is required')
      const content = await exportLegacyAuditSnapshot(latest.id)
      const url = URL.createObjectURL(new Blob([content], { type: 'application/json;charset=utf-8' }))
      const link = document.createElement('a')
      link.href = url
      link.download = `legacy-audit-${latest.id}.json`
      link.click()
      URL.revokeObjectURL(url)
    },
    onError: (error) => message.error(errorMessage(error, '迁移审计证据导出失败，请重试')),
  })
  const latestDecision = useMemo(() => {
    const values = new Map<string, RemovalDecision>()
    for (const decision of latest?.decisions ?? []) values.set(decision.surfaceKey, decision)
    return values
  }, [latest?.decisions])

  const decide = (
    surface: LegacySurface,
    decision: RemovalDecision['decision'],
  ) => decisionMutation.mutate({
    surfaceKey: surface.key,
    decision,
    reason: decision === 'remove'
      ? 'M2 will remove this active legacy product surface after the audited dependency order is verified.'
      : decision === 'retain_history'
        ? 'Retain this immutable migration or audit evidence while removing every active product dependency.'
        : 'Block removal until the recorded migration or permission inconsistency has an explicit resolution.',
  })

  return (
    <div className="admin-page admin-legacy-exit-audit-page" data-testid="legacy-exit-audit-page">
      {!online ? <Alert type="warning" showIcon title="离线状态下不能生成快照或追加删除决定。" /> : null}
      <Space className="admin-legacy-audit-actions" wrap>
        <Button
          type="primary"
          disabled={!online}
          loading={snapshotMutation.isPending}
          onClick={() => snapshotMutation.mutate()}
        >
          生成审计快照
        </Button>
        <Button
          disabled={!latest || !online}
          loading={exportMutation.isPending}
          onClick={() => exportMutation.mutate()}
        >
          导出证据
        </Button>
      </Space>

      {!latest ? (
        <Card><Empty description="尚无迁移审计快照" /></Card>
      ) : (
        <>
          <Alert
            className="admin-legacy-audit-status"
            type={latest.status === 'ready' ? 'success' : 'error'}
            showIcon
            title={latest.status === 'ready' ? '审计无阻断' : '存在删除阻断'}
            description={`inventory ${latest.inventoryVersion} · ${latest.findings.length} 项 finding`}
          />
          <Row gutter={[12, 12]}>
            {totalCards.map(([key, label]) => (
              <Col xs={12} md={8} xl={4} key={key}>
                <Card><Statistic title={label} value={latest.totals[key] ?? 0} /></Card>
              </Col>
            ))}
          </Row>
          <Card title="快照来源" className="admin-legacy-audit-card">
            <Descriptions size="small" column={{ xs: 1, md: 2 }}>
              <Descriptions.Item label="生成时间">{new Date(latest.generatedAt).toLocaleString()}</Descriptions.Item>
              <Descriptions.Item label="来源指纹"><Typography.Text code>{latest.sourceFingerprint}</Typography.Text></Descriptions.Item>
            </Descriptions>
          </Card>
          <Card title="Legacy surface 与删除决定" className="admin-legacy-audit-card">
            <Table
              rowKey="key"
              pagination={false}
              scroll={{ x: 1040 }}
              dataSource={latest.surfaces}
              columns={[
                { title: 'Surface', dataIndex: 'key', width: 210 },
                { title: '层/Owner', render: (_, value) => `${value.layer} / ${value.owner}`, width: 170 },
                { title: '访问', dataIndex: 'accessMode', width: 110 },
                { title: '证据', dataIndex: 'evidence' },
                {
                  title: '当前决定',
                  width: 150,
                  render: (_, value) => {
                    const decision = latestDecision.get(value.key)
                    return decision ? <Tag color={decision.decision === 'blocked' ? 'red' : 'blue'}>{decision.decision}</Tag> : <Tag>未决定</Tag>
                  },
                },
                {
                  title: '操作',
                  width: 270,
                  render: (_, value) => (
                    <Space wrap>
                      <Button size="small" loading={decisionMutation.isPending} disabled={!online || decisionMutation.isPending} onClick={() => decide(value, 'remove')}>M2 删除</Button>
                      <Button size="small" loading={decisionMutation.isPending} disabled={!online || decisionMutation.isPending} onClick={() => decide(value, 'retain_history')}>保留历史</Button>
                      <Button size="small" danger loading={decisionMutation.isPending} disabled={!online || decisionMutation.isPending} onClick={() => decide(value, 'blocked')}>阻断</Button>
                    </Space>
                  ),
                },
              ]}
            />
          </Card>
          <Card title="一致性发现" className="admin-legacy-audit-card">
            <Table
              rowKey="id"
              pagination={false}
              dataSource={latest.findings}
              columns={[
                { title: 'Finding', dataIndex: 'key' },
                { title: '分类', dataIndex: 'category' },
                {
                  title: '严重度',
                  dataIndex: 'severity',
                  render: value => <Tag color={value === 'blocking' ? 'red' : value === 'warning' ? 'orange' : 'blue'}>{value}</Tag>,
                },
                { title: '影响数量', dataIndex: 'affectedCount' },
              ]}
            />
          </Card>
        </>
      )}
    </div>
  )
}
