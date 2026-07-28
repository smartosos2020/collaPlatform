import { ClockCircleOutlined, ReloadOutlined } from '@ant-design/icons'
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
  Select,
  Space,
  Tag,
  Typography,
} from 'antd'
import { useEffect, useMemo, useState } from 'react'

import {
  getResourceWorklogs,
  mutateResourceWorklog,
  resourceWorklogKeys,
  type Worklog,
} from '../api/resourceWorklogApi'
import type { UserProjectSpace } from '../api/projectSpacesApi'
import { errorMessage } from '../projectSpaceView'

export function ResourceWorklogPanel({ space }: { space: UserProjectSpace }) {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const [workItemId, setWorkItemId] = useState('')
  const [workDate, setWorkDate] = useState(new Date().toISOString().slice(0, 10))
  const [minutes, setMinutes] = useState(60)
  const [source, setSource] = useState<Worklog['source']>('manual')
  const [reason, setReason] = useState('')
  const [offlineDraft, setOfflineDraft] = useState('')
  const [online, setOnline] = useState(() => navigator.onLine)
  const query = useQuery({
    queryKey: resourceWorklogKeys.detail(space.id),
    queryFn: () => getResourceWorklogs(space.id),
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
    queryClient.invalidateQueries({ queryKey: resourceWorklogKeys.detail(space.id) })
  const mutation = useMutation({
    mutationFn: (input: Parameters<typeof mutateResourceWorklog>[1]) =>
      mutateResourceWorklog(space.id, input),
    onSuccess: async (_, input) => {
      await refresh()
      setReason('')
      message.success({
        create: '工时草稿已创建',
        update: '工时修订已保存',
        submit: '工时已提交',
        withdraw: '工时已撤回',
        void: '工时已作废',
      }[input.operation])
    },
    onError: (error) => message.error(errorMessage(error, '工时操作失败，请 REST 校准后重试')),
  })
  const variance = useMemo(
    () => new Map(query.data?.variance.map((value) => [value.workItemId, value]) ?? []),
    [query.data],
  )
  const writable = space.status === 'active' && space.currentUserRole !== 'guest'
  const create = () => mutation.mutate({
    operation: 'create',
    workItemId: workItemId.trim(),
    workDate,
    durationMinutes: minutes,
    source,
    reason,
  })
  const transition = (
    current: Worklog,
    operation: 'submit' | 'withdraw' | 'void',
  ) => mutation.mutate({ operation, current, reason })

  return (
    <Card
      className="content-card resource-worklog-panel"
      data-testid="resource-worklog-panel"
      title={<Space><ClockCircleOutlined />实际工时与修订</Space>}
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
        <Alert type="error" showIcon message={errorMessage(query.error, '工时加载失败')} />
      )}
      {query.data && (
        <div className="resource-worklog-layout">
          <section aria-label="工时录入">
            <Typography.Title level={5}>录入工时草稿</Typography.Title>
            <Space wrap>
              <Input
                aria-label="工时事项 ID"
                placeholder="当前可见 WorkItem UUID"
                value={workItemId}
                onChange={(event) => setWorkItemId(event.target.value)}
              />
              <Input
                aria-label="工时日期"
                type="date"
                value={workDate}
                onChange={(event) => setWorkDate(event.target.value)}
              />
              <InputNumber
                aria-label="工时分钟"
                min={1}
                max={1440}
                value={minutes}
                onChange={(value) => setMinutes(value ?? 60)}
                addonAfter="分钟"
              />
              <Select
                aria-label="工时来源"
                value={source}
                options={[
                  { label: '手工', value: 'manual' },
                  { label: '导入', value: 'import' },
                  { label: '代理', value: 'proxy' },
                ]}
                onChange={setSource}
              />
              <Button
                type="primary"
                disabled={!writable || !workItemId.trim()}
                loading={mutation.isPending}
                onClick={create}
              >
                创建工时草稿
              </Button>
            </Space>
            <Input
              className="resource-worklog-reason"
              aria-label="工时修订原因"
              placeholder="编辑、撤回、作废或代理操作需要不可变原因"
              maxLength={500}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
            />
          </section>

          {query.data.truncated && (
            <Alert type="warning" showIcon message="工时达到 200 条查询预算，结果已截断" />
          )}
          <List
            data-testid="resource-worklog-list"
            dataSource={query.data.worklogs}
            locale={{ emptyText: <Empty description="暂无当前可见工时" /> }}
            renderItem={(value) => {
              const comparison = variance.get(value.workItemId)
              return (
                <List.Item
                  actions={[
                    <Button
                      key="submit"
                      type="link"
                      disabled={!writable || value.approvalState !== 'draft'}
                      onClick={() => transition(value, 'submit')}
                    >
                      提交
                    </Button>,
                    <Button
                      key="withdraw"
                      type="link"
                      disabled={!writable || value.approvalState !== 'submitted' || !reason.trim()}
                      onClick={() => transition(value, 'withdraw')}
                    >
                      撤回
                    </Button>,
                    <Button
                      key="void"
                      type="link"
                      danger
                      disabled={!writable || value.approvalState === 'void' || !reason.trim()}
                      onClick={() => transition(value, 'void')}
                    >
                      作废
                    </Button>,
                  ]}
                >
                  <List.Item.Meta
                    title={(
                      <Space wrap>
                        <Typography.Text copyable>{value.workItemId}</Typography.Text>
                        <Tag>{value.workDate}</Tag>
                        <Tag>{value.durationMinutes} 分钟</Tag>
                        <Tag color={value.approvalState === 'submitted' ? 'green' : 'default'}>
                          {value.approvalState}
                        </Tag>
                        <Tag>v{value.version} / r{value.currentRevision}</Tag>
                      </Space>
                    )}
                    description={(
                      <Space direction="vertical">
                        <Typography.Text type="secondary">
                          {comparison?.comparable
                            ? `估算 ${comparison.estimatedMinutes} · 实际 ${comparison.actualMinutes} · 偏差 ${comparison.varianceMinutes} 分钟`
                            : comparison?.explanation ?? '暂无可比估算'}
                        </Typography.Text>
                        <Typography.Text type="secondary">
                          修订历史：{value.revisions.map(
                            (revision) => `r${revision.revisionNumber} ${revision.approvalState}${revision.reason ? `（${revision.reason}）` : ''}`,
                          ).join(' · ')}
                        </Typography.Text>
                      </Space>
                    )}
                  />
                </List.Item>
              )
            }}
          />
          <section className="resource-worklog-offline" aria-label="工时离线草稿">
            <Input.TextArea
              data-testid="resource-worklog-offline-draft"
              aria-label="工时离线草稿"
              rows={2}
              value={offlineDraft}
              placeholder="离线临时记录；恢复前不会伪造提交成功"
              onChange={(event) => setOfflineDraft(event.target.value)}
            />
            <Tag color={online ? 'green' : 'orange'}>
              {online ? '在线' : '离线 · 草稿保留'}
            </Tag>
          </section>
        </div>
      )}
    </Card>
  )
}
