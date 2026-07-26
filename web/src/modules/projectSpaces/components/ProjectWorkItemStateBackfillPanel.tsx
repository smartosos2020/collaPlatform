import { DatabaseOutlined, ReloadOutlined, SafetyCertificateOutlined } from '@ant-design/icons'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Alert, App as AntdApp, Button, Card, Input, Select, Space, Tag, Typography } from 'antd'
import { useMemo, useState } from 'react'

import { listWorkItems, workItemKeys } from '../api/workItemsApi'
import type { ConfigurationVersion } from '../api/workItemConfigurationApi'
import {
  createWorkItemStateBackfill,
  resumeWorkItemStateBackfill,
  verifyWorkItemStateBackfill,
  type WorkItemStateBackfillBatch,
  type WorkItemStateBackfillVerification,
} from '../api/workItemWorkflowApi'
import { errorMessage } from '../projectSpaceView'

type JsonRecord = Record<string, unknown>

export function ProjectWorkItemStateBackfillPanel({
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
  const [workItemIds, setWorkItemIds] = useState<string[]>([])
  const [reason, setReason] = useState('')
  const [batch, setBatch] = useState<WorkItemStateBackfillBatch | null>(null)
  const [verification, setVerification] = useState<WorkItemStateBackfillVerification | null>(null)
  const snapshot = currentVersion?.snapshot != null
    && typeof currentVersion.snapshot === 'object'
    && !Array.isArray(currentVersion.snapshot)
    ? currentVersion.snapshot as JsonRecord
    : {}
  const stateFlow = snapshot.stateFlow != null
    && typeof snapshot.stateFlow === 'object'
    && !Array.isArray(snapshot.stateFlow)
    ? snapshot.stateFlow as JsonRecord
    : {}
  const states = Array.isArray(stateFlow.states) ? stateFlow.states as JsonRecord[] : []
  const initialState = states.find((state) => state.category === 'initial')
  const initialStateKey = typeof initialState?.stateKey === 'string' ? initialState.stateKey : undefined
  const itemsQuery = useQuery({
    queryKey: workItemKeys.list(spaceId, typeId),
    queryFn: () => listWorkItems(spaceId, typeId),
    enabled: Boolean(currentVersion && initialStateKey),
  })
  const itemOptions = useMemo(() => itemsQuery.data?.items.map((item) => ({
    label: `${item.displayKey} · ${item.title}`,
    value: item.id,
  })) ?? [], [itemsQuery.data])

  const createMutation = useMutation({
    mutationFn: () => createWorkItemStateBackfill(spaceId, {
      typeDefinitionId: typeId,
      targetTypeVersionId: currentVersion!.id,
      targetStateKey: initialStateKey!,
      workItemIds,
      reason: reason.trim(),
      confirmation: 'INITIALIZE_EXISTING_WORKFLOW_STATES',
    }),
    onSuccess: (result) => {
      setBatch(result)
      setVerification(null)
      message.success('存量状态初始化批次已执行')
    },
    onError: (error) => message.error(errorMessage(error, '初始化失败，选择和原因已保留')),
  })
  const verifyMutation = useMutation({
    mutationFn: () => verifyWorkItemStateBackfill(spaceId, batch!.id),
    onSuccess: (result) => {
      setVerification(result)
      message.success(result.status === 'verified' ? '批次验证通过' : '批次验证完成，请检查失败清单')
    },
    onError: (error) => message.error(errorMessage(error, '批次验证失败')),
  })
  const resumeMutation = useMutation({
    mutationFn: () => resumeWorkItemStateBackfill(spaceId, batch!.id),
    onSuccess: (result) => {
      setBatch(result)
      setVerification(null)
      message.success('批次续跑完成')
    },
    onError: (error) => message.error(errorMessage(error, '批次续跑失败')),
  })

  if (!currentVersion || !initialStateKey) return null
  return (
    <Card
      className="work-item-state-backfill-panel"
      data-testid="work-item-state-backfill-panel"
      title={<Space><DatabaseOutlined /><span>存量实例状态初始化</span></Space>}
    >
      <Alert
        type="warning"
        showIcon
        message="仅用于没有 current state 的旧实例"
        description={`目标固定为当前不可变版本 v${currentVersion.versionNumber} 的 initial 状态 ${initialStateKey}；已初始化实例不会被静默覆盖。`}
      />
      <label className="work-item-state-backfill-field">
        <span>显式 manifest</span>
        <Select
          mode="multiple"
          showSearch
          optionFilterProp="label"
          value={workItemIds}
          disabled={readOnly}
          loading={itemsQuery.isLoading}
          placeholder="选择需要初始化的工作项"
          options={itemOptions}
          onChange={setWorkItemIds}
        />
      </label>
      <label className="work-item-state-backfill-field">
        <span>操作原因（10–500 个字符）</span>
        <Input.TextArea
          value={reason}
          disabled={readOnly}
          autoSize={{ minRows: 2, maxRows: 6 }}
          maxLength={500}
          onChange={(event) => setReason(event.target.value)}
        />
      </label>
      <Space wrap>
        <Button
          danger
          icon={<SafetyCertificateOutlined />}
          disabled={readOnly || workItemIds.length === 0 || reason.trim().length < 10}
          loading={createMutation.isPending}
          onClick={() => createMutation.mutate()}
        >
          确认初始化
        </Button>
        {batch ? (
          <>
            <Button icon={<ReloadOutlined />} loading={verifyMutation.isPending} onClick={() => verifyMutation.mutate()}>
              验证批次
            </Button>
            {batch.failedCount > 0 ? (
              <Button loading={resumeMutation.isPending} onClick={() => resumeMutation.mutate()}>
                续跑失败单元
              </Button>
            ) : null}
          </>
        ) : null}
      </Space>
      {batch ? (
        <div className="work-item-state-backfill-result" aria-live="polite">
          <Space wrap>
            <Tag color={batch.failedCount > 0 ? 'warning' : 'success'}>{batch.status}</Tag>
            <Typography.Text code>{batch.id}</Typography.Text>
            <Typography.Text>完成 {batch.completedCount}/{batch.requestedCount}</Typography.Text>
            <Typography.Text>失败 {batch.failedCount}</Typography.Text>
          </Space>
          {verification ? (
            <Alert
              type={verification.failures.length === 0 ? 'success' : 'error'}
              showIcon
              message={`验证状态 ${verification.status}，已核对 ${verification.verifiedCount} 项`}
              description={verification.failures.length === 0
                ? 'manifest、绑定版本和 current state 一致。'
                : verification.failures.map((failure) => `${failure.workItemId}: ${failure.errorCode}`).join('；')}
            />
          ) : null}
        </div>
      ) : null}
    </Card>
  )
}
