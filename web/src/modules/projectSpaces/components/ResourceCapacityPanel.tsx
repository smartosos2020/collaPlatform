import { ReloadOutlined, TeamOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Empty,
  Input,
  InputNumber,
  List,
  Progress,
  Space,
  Tag,
  Typography,
} from 'antd'
import { useEffect, useState } from 'react'

import {
  getResourceCapacity,
  mutateAllocation,
  resourceCapacityKeys,
} from '../api/resourceCapacityApi'
import type { UserProjectSpace } from '../api/projectSpacesApi'
import { errorMessage } from '../projectSpaceView'

export function ResourceCapacityPanel({ space }: { space: UserProjectSpace }) {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const today = new Date().toISOString().slice(0, 10)
  const [workItemId, setWorkItemId] = useState('')
  const [userId, setUserId] = useState('')
  const [startDate, setStartDate] = useState(today)
  const [endDate, setEndDate] = useState(today)
  const [percent, setPercent] = useState(100)
  const [reason, setReason] = useState('')
  const [offlineDraft, setOfflineDraft] = useState('')
  const [online, setOnline] = useState(() => navigator.onLine)
  const query = useQuery({
    queryKey: resourceCapacityKeys.detail(space.id),
    queryFn: () => getResourceCapacity(space.id),
  })
  useEffect(() => {
    const markOnline = () => setOnline(true)
    const markOffline = () => setOnline(false)
    window.addEventListener('online', markOnline)
    window.addEventListener('offline', markOffline)
    return () => {
      window.removeEventListener('online', markOnline)
      window.removeEventListener('offline', markOffline)
    }
  }, [])
  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: resourceCapacityKeys.detail(space.id) })
  const mutation = useMutation({
    mutationFn: (input: Parameters<typeof mutateAllocation>[1]) =>
      mutateAllocation(space.id, input),
    onSuccess: async (_, input) => {
      await refresh()
      message.success(input.operation === 'create' ? '人员分配已创建' : '人员分配已更新')
    },
    onError: (error) => message.error(errorMessage(error, '人员分配失败，请 REST 校准后重试')),
  })
  const manageable = space.status === 'active'
    && ['owner', 'admin'].includes(space.currentUserRole ?? '')
  const create = () => mutation.mutate({
    operation: 'create',
    workItemId: workItemId.trim(),
    userId: userId.trim(),
    startDate,
    endDate,
    allocationPercent: percent,
    reason,
  })

  return (
    <Card
      className="content-card resource-capacity-panel"
      data-testid="resource-capacity-panel"
      title={<Space><TeamOutlined />人员负荷与产能冲突</Space>}
      extra={(
        <Button
          icon={<ReloadOutlined />}
          loading={query.isFetching}
          onClick={() => void query.refetch()}
        >
          REST 校准
        </Button>
      )}
    >
      {query.isError && (
        <Alert type="error" showIcon message={errorMessage(query.error, '产能加载失败')} />
      )}
      {query.data && (
        <div className="resource-capacity-layout">
          <section aria-label="人员分配">
            <Typography.Title level={5}>创建受权人员分配</Typography.Title>
            <Space wrap>
              <Input
                aria-label="分配事项 ID"
                placeholder="WorkItem UUID"
                value={workItemId}
                onChange={(event) => setWorkItemId(event.target.value)}
              />
              <Input
                aria-label="分配人员 ID"
                placeholder="空间成员 User UUID"
                value={userId}
                onChange={(event) => setUserId(event.target.value)}
              />
              <Input
                aria-label="分配开始日期"
                type="date"
                value={startDate}
                onChange={(event) => setStartDate(event.target.value)}
              />
              <Input
                aria-label="分配结束日期"
                type="date"
                value={endDate}
                onChange={(event) => setEndDate(event.target.value)}
              />
              <InputNumber
                aria-label="分配比例"
                min={0.01}
                max={100}
                precision={2}
                value={percent}
                onChange={(value) => setPercent(value ?? 100)}
                addonAfter="%"
              />
              <Input
                aria-label="分配原因"
                placeholder="不可为空的调整原因"
                value={reason}
                maxLength={500}
                onChange={(event) => setReason(event.target.value)}
              />
              <Button
                type="primary"
                disabled={!manageable || !workItemId.trim() || !userId.trim() || !reason.trim()}
                loading={mutation.isPending}
                onClick={create}
              >
                创建人员分配
              </Button>
            </Space>
            {!manageable && <Tag color="orange">仅空间 owner/admin 可调整</Tag>}
          </section>

          {query.data.truncated && (
            <Alert type="warning" showIcon message="人员分配达到 200 条查询预算" />
          )}
          <section aria-label="负荷窗口">
            <Typography.Title level={5}>当前负荷窗口</Typography.Title>
            <List
              data-testid="resource-load-list"
              dataSource={query.data.buckets}
              locale={{ emptyText: <Empty description="暂无当前受权负荷桶" /> }}
              renderItem={(bucket) => {
                const ratio = bucket.capacityMinutes === 0
                  ? (bucket.allocatedMinutes > 0 ? 100 : 0)
                  : Math.min(100, Math.round(
                    bucket.allocatedMinutes / bucket.capacityMinutes * 100,
                  ))
                return (
                  <List.Item>
                    <List.Item.Meta
                      title={(
                        <Space wrap>
                          <Typography.Text copyable>{bucket.userId}</Typography.Text>
                          <Tag>{bucket.date}</Tag>
                          <Tag color={bucket.conflict ? 'error' : 'default'}>
                            {bucket.signal}
                          </Tag>
                        </Space>
                      )}
                      description={(
                        <>
                          <Progress
                            percent={ratio}
                            status={bucket.conflict ? 'exception' : 'normal'}
                            size="small"
                          />
                          <Typography.Text type="secondary">
                            产能 {bucket.capacityMinutes} · 分配 {bucket.allocatedMinutes}
                            {' · '}已提交实际 {bucket.actualMinutes} 分钟 · {bucket.explanation}
                          </Typography.Text>
                        </>
                      )}
                    />
                  </List.Item>
                )
              }}
            />
          </section>

          <Input.TextArea
            data-testid="resource-capacity-offline-draft"
            aria-label="产能离线草稿"
            rows={2}
            value={offlineDraft}
            placeholder="离线调整草稿；恢复前不会伪造分配成功"
            onChange={(event) => setOfflineDraft(event.target.value)}
          />
          <Tag color={online ? 'green' : 'orange'}>
            {online ? '在线' : '离线 · 调整草稿保留'}
          </Tag>
        </div>
      )}
    </Card>
  )
}
