import { CheckCircleOutlined, ReloadOutlined, RetweetOutlined } from '@ant-design/icons'
import { useMutation } from '@tanstack/react-query'
import { Alert, App as AntdApp, Button, Input, Space, Tag, Typography } from 'antd'
import { useState } from 'react'

import {
  createNodeBackfill,
  resumeNodeBackfill,
  verifyNodeBackfill,
  type NodeBackfillBatch,
} from '../api/workItemNodeWorkflowApi'
import type { ConfigurationVersion } from '../api/workItemConfigurationApi'
import { errorMessage } from '../projectSpaceView'
import { CollapsibleWorkItemCard } from './CollapsibleWorkItemCard'

export function ProjectWorkItemNodeBackfillPanel({
  spaceId,
  typeId,
  currentVersion,
  readOnly,
}: {
  spaceId: string
  typeId: string
  currentVersion?: ConfigurationVersion
  readOnly: boolean
}) {
  const { message } = AntdApp.useApp()
  const [manifest, setManifest] = useState('')
  const [batch, setBatch] = useState<NodeBackfillBatch | null>(null)
  const [verification, setVerification] = useState<Awaited<ReturnType<typeof verifyNodeBackfill>> | null>(null)
  const createMutation = useMutation({
    mutationFn: () => createNodeBackfill(spaceId, {
      typeDefinitionId: typeId,
      targetTypeVersionId: currentVersion?.id ?? '',
      workItemIds: manifest.split(/[\s,]+/).map((value) => value.trim()).filter(Boolean),
    }),
    onSuccess: (result) => {
      setBatch(result)
      message.success('节点流 backfill manifest 已提交')
    },
    onError: (error) => message.error(errorMessage(error, '节点流 backfill 创建失败')),
  })
  const resumeMutation = useMutation({
    mutationFn: () => resumeNodeBackfill(spaceId, batch?.id ?? ''),
    onSuccess: setBatch,
    onError: (error) => message.error(errorMessage(error, '续跑失败')),
  })
  const verifyMutation = useMutation({
    mutationFn: () => verifyNodeBackfill(spaceId, batch?.id ?? ''),
    onSuccess: setVerification,
    onError: (error) => message.error(errorMessage(error, '验证失败')),
  })
  return (
    <CollapsibleWorkItemCard
      collapseLabel="存量节点流初始化"
      className="node-flow-backfill-panel"
      title={<Space><RetweetOutlined />存量节点流初始化<Tag>显式 manifest</Tag></Space>}
    >
      <Typography.Paragraph type="secondary">
        只处理显式列出的 WorkItem UUID；失败清单可续跑，验证不会静默切换绑定版本。
      </Typography.Paragraph>
      <Input.TextArea
        aria-label="节点流 backfill WorkItem UUID manifest"
        rows={3}
        value={manifest}
        disabled={readOnly || !currentVersion}
        onChange={(event) => setManifest(event.target.value)}
        placeholder="每行一个 WorkItem UUID"
      />
      <Space wrap>
        <Button
          type="primary"
          disabled={readOnly || !currentVersion || !manifest.trim()}
          loading={createMutation.isPending}
          onClick={() => createMutation.mutate()}
        >
          创建批次
        </Button>
        <Button
          icon={<ReloadOutlined />}
          disabled={!batch || batch.status === 'completed'}
          loading={resumeMutation.isPending}
          onClick={() => resumeMutation.mutate()}
        >
          续跑
        </Button>
        <Button
          icon={<CheckCircleOutlined />}
          disabled={!batch}
          loading={verifyMutation.isPending}
          onClick={() => verifyMutation.mutate()}
        >
          验证
        </Button>
      </Space>
      {batch ? (
        <Alert
          showIcon
          type={batch.failedCount ? 'warning' : batch.status === 'completed' ? 'success' : 'info'}
          message={`批次 ${batch.status} · 完成 ${batch.completedCount}/${batch.requestedCount} · 失败 ${batch.failedCount}`}
          description={verification
            ? `验证 ${verification.status}，已核对 ${verification.verifiedCount} 项，失败 ${verification.failures.length} 项`
            : '尚未执行独立验证'}
        />
      ) : null}
    </CollapsibleWorkItemCard>
  )
}
