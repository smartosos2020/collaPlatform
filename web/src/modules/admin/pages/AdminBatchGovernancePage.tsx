import { useMemo, useState } from 'react'
import { Alert, App as AntdApp, Button, Card, Form, Input, Select, Space, Table, Typography } from 'antd'
import { useMutation, useQuery } from '@tanstack/react-query'

import { executeBatch, listBatchCapabilities, previewBatch, type BatchReport } from '../api/batchGovernanceApi'
import { errorMessage } from '../../../shared/api/errorMessage'

type BatchCommand = Parameters<typeof previewBatch>[0]

export function AdminBatchGovernancePage() {
  const { message, modal } = AntdApp.useApp()
  const capabilitiesQuery = useQuery({ queryKey: ['admin', 'batch-governance', 'capabilities'], queryFn: listBatchCapabilities })
  const [resourceType, setResourceType] = useState('users')
  const [action, setAction] = useState('disable')
  const [targetIds, setTargetIds] = useState('')
  const [report, setReport] = useState<BatchReport | null>(null)
  const [previewCommand, setPreviewCommand] = useState<BatchCommand | null>(null)
  const command = useMemo(() => ({ resourceType, action, targetIds: targetIds.split(/[,\s]+/).map((value) => value.trim()).filter(Boolean) }), [action, resourceType, targetIds])
  const commandMatchesPreview = previewCommand !== null && JSON.stringify(previewCommand) === JSON.stringify(command)
  const previewMutation = useMutation({
    mutationFn: previewBatch,
    onSuccess: (next, submittedCommand) => {
      setReport(next)
      setPreviewCommand({ ...submittedCommand, targetIds: [...submittedCommand.targetIds] })
    },
    onError: (error) => message.error(errorMessage(error, '批量治理预览失败，请重试')),
  })
  const executeMutation = useMutation({
    mutationFn: executeBatch,
    onSuccess: () => {
      setReport(null)
      setPreviewCommand(null)
      message.success('批量治理已执行')
    },
    onError: (error) => message.error(errorMessage(error, '批量治理执行失败，请重试')),
  })
  const confirmExecute = () => {
    if (!report || !commandMatchesPreview || report.readyCount === 0) return
    modal.confirm({
      title: '确认执行批量治理？',
      content: `将对 ${report.readyCount} 个可执行目标应用“${action}”操作，此操作会写入审计记录。`,
      okText: '确认执行',
      cancelText: '取消',
      onOk: () => executeMutation.mutateAsync(previewCommand).catch(() => undefined),
    })
  }

  return (
    <div className="admin-page admin-batch-governance-page" data-testid="admin-batch-governance-page">
      <Typography.Title level={2}>批量治理</Typography.Title>
      <Typography.Paragraph type="secondary">先预览权限检查与失败项，再确认执行；每次操作均写入审计。</Typography.Paragraph>
      <Card>
        <Form layout="vertical">
          <Form.Item label="治理资源">
            <Select value={resourceType} onChange={setResourceType} options={(capabilitiesQuery.data ?? []).map((item) => ({ value: item.resourceType, label: item.label }))} />
          </Form.Item>
          <Form.Item label="动作">
            <Select value={action} onChange={setAction} options={(capabilitiesQuery.data ?? []).filter((item) => item.resourceType === resourceType).map((item) => ({ value: item.action, label: item.label }))} />
          </Form.Item>
          <Form.Item label="目标 ID" extra="可粘贴多个 UUID，以空格或逗号分隔">
            <Input.TextArea aria-label="目标 ID" value={targetIds} onChange={(event) => setTargetIds(event.target.value)} rows={3} placeholder="目标 UUID" />
          </Form.Item>
          <Space>
            <Button type="primary" onClick={() => previewMutation.mutate(command)} loading={previewMutation.isPending}>预览权限</Button>
            <Button onClick={confirmExecute} disabled={!report || report.readyCount === 0 || !commandMatchesPreview} loading={executeMutation.isPending}>确认执行</Button>
          </Space>
        </Form>
      </Card>
      {report ? (
        <Card title="结果报告" style={{ marginTop: 16 }}>
          <Alert type={report.executed ? 'success' : 'info'} showIcon title={report.executed ? '批量治理已执行' : '预览完成，等待确认'} description={`目标 ${report.targetCount} 个，可执行 ${report.readyCount} 个${commandMatchesPreview ? '' : '；输入已变化，请重新预览'}`} />
          <Table rowKey="targetId" pagination={false} style={{ marginTop: 16 }} dataSource={report.items} columns={[{ title: '目标', dataIndex: 'targetId' }, { title: '状态', dataIndex: 'status' }, { title: '说明', dataIndex: 'message' }]} />
        </Card>
      ) : null}
    </div>
  )
}
