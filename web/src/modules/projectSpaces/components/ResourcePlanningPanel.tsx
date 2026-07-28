import {
  CalendarOutlined,
  ReloadOutlined,
  SaveOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Checkbox,
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
  getResourcePlanning,
  resourcePlanningKeys,
  saveResourceCalendar,
  saveResourceEstimate,
  type CalendarException,
  type Estimate,
} from '../api/resourcePlanningApi'
import type { UserProjectSpace } from '../api/projectSpacesApi'
import { errorMessage, formatTime } from '../projectSpaceView'

const days = [
  { label: '一', value: 1 },
  { label: '二', value: 2 },
  { label: '三', value: 3 },
  { label: '四', value: 4 },
  { label: '五', value: 5 },
  { label: '六', value: 6 },
  { label: '日', value: 7 },
]

export function ResourcePlanningPanel({ space }: { space: UserProjectSpace }) {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const [timezoneOverride, setTimezoneOverride] = useState<string>()
  const [workDaysOverride, setWorkDaysOverride] = useState<number[]>()
  const [dailyMinutesOverride, setDailyMinutesOverride] = useState<number>()
  const [exceptionsOverride, setExceptionsOverride] = useState<CalendarException[]>()
  const [exceptionDate, setExceptionDate] = useState('')
  const [exceptionMinutes, setExceptionMinutes] = useState(0)
  const [exceptionNote, setExceptionNote] = useState('')
  const [workItemId, setWorkItemId] = useState('')
  const [unit, setUnit] = useState<Estimate['unit']>('hour')
  const [amount, setAmount] = useState(1)
  const [offlineDraft, setOfflineDraft] = useState('')
  const [online, setOnline] = useState(() => navigator.onLine)
  const query = useQuery({
    queryKey: resourcePlanningKeys.detail(space.id),
    queryFn: () => getResourcePlanning(space.id),
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
    queryClient.invalidateQueries({ queryKey: resourcePlanningKeys.detail(space.id) })
  const timezone = timezoneOverride ?? query.data?.calendar.timezone ?? 'UTC'
  const workDays = workDaysOverride ?? query.data?.calendar.workDays ?? [1, 2, 3, 4, 5]
  const dailyMinutes = dailyMinutesOverride ?? query.data?.calendar.dailyMinutes ?? 480
  const exceptions = exceptionsOverride ?? query.data?.calendar.exceptions ?? []
  const calendarMutation = useMutation({
    mutationFn: () => saveResourceCalendar(space.id, query.data!.calendar, {
      timezone: timezone.trim(),
      workDays,
      dailyMinutes,
      exceptions,
    }),
    onSuccess: async () => {
      await refresh()
      setTimezoneOverride(undefined)
      setWorkDaysOverride(undefined)
      setDailyMinutesOverride(undefined)
      setExceptionsOverride(undefined)
      message.success('工作日历已保存')
    },
    onError: (error) => message.error(errorMessage(error, '日历保存失败，请 REST 校准后重试')),
  })
  const existingEstimate = query.data?.estimates.find(
    (value) => value.workItemId === workItemId.trim(),
  )
  const estimateMutation = useMutation({
    mutationFn: () => saveResourceEstimate(space.id, {
      workItemId: workItemId.trim(),
      unit,
      amount,
      expectedVersion: existingEstimate?.version ?? 0,
    }),
    onSuccess: async () => {
      await refresh()
      message.success('估分已保存')
    },
    onError: (error) => message.error(errorMessage(error, '估分保存失败，请检查事项权限或版本')),
  })
  const writable = space.status === 'active' && space.currentUserRole !== 'guest'
  const schedule = useMemo(
    () => new Map(query.data?.schedule.map((value) => [value.workItemId, value]) ?? []),
    [query.data],
  )
  const addException = () => {
    if (!exceptionDate || exceptions.some((value) => value.date === exceptionDate)) return
    setExceptionsOverride([...exceptions, {
      id: crypto.randomUUID(),
      date: exceptionDate,
      availableMinutes: exceptionMinutes,
      note: exceptionNote,
    }].sort((left, right) => left.date.localeCompare(right.date)))
    setExceptionDate('')
    setExceptionNote('')
  }

  return (
    <Card
      className="content-card resource-planning-panel"
      data-testid="resource-planning-panel"
      title={<Space><CalendarOutlined />工作日历与估分</Space>}
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
        <Alert type="error" showIcon message={errorMessage(query.error, '资源排期加载失败')} />
      )}
      {query.data && (
        <div className="resource-planning-layout">
          <section aria-label="工作日历配置">
            <Typography.Title level={5}>空间工作日历</Typography.Title>
            <Space direction="vertical" className="resource-planning-form">
              <Input
                aria-label="IANA 时区"
                value={timezone}
                maxLength={80}
                onChange={(event) => setTimezoneOverride(event.target.value)}
              />
              <Checkbox.Group
                aria-label="工作周"
                options={days}
                value={workDays}
                onChange={(values) => setWorkDaysOverride(values as number[])}
              />
              <InputNumber
                aria-label="每日可用分钟"
                min={1}
                max={1440}
                value={dailyMinutes}
                onChange={(value) => setDailyMinutesOverride(value ?? 480)}
                addonAfter="分钟/日"
              />
              <Space wrap>
                <Input
                  aria-label="例外日期"
                  type="date"
                  value={exceptionDate}
                  onChange={(event) => setExceptionDate(event.target.value)}
                />
                <InputNumber
                  aria-label="例外可用分钟"
                  min={0}
                  max={1440}
                  value={exceptionMinutes}
                  onChange={(value) => setExceptionMinutes(value ?? 0)}
                />
                <Input
                  aria-label="例外说明"
                  value={exceptionNote}
                  maxLength={240}
                  onChange={(event) => setExceptionNote(event.target.value)}
                />
                <Button onClick={addException}>添加例外</Button>
              </Space>
              <List
                size="small"
                dataSource={exceptions}
                locale={{ emptyText: '暂无节假日或部分可用例外' }}
                renderItem={(value) => (
                  <List.Item
                    actions={[
                      <Button
                        key="remove"
                        type="link"
                        danger
                        onClick={() =>
                          setExceptionsOverride(exceptions.filter((item) => item.id !== value.id))}
                      >
                        移除
                      </Button>,
                    ]}
                  >
                    {value.date} · {value.availableMinutes} 分钟 · {value.note || '无说明'}
                  </List.Item>
                )}
              />
              <Button
                type="primary"
                icon={<SaveOutlined />}
                disabled={!writable || !timezone.trim() || workDays.length === 0}
                loading={calendarMutation.isPending}
                onClick={() => calendarMutation.mutate()}
              >
                保存工作日历
              </Button>
              <Typography.Text type="secondary">
                IANA 时区 · 当前版本 v{query.data.calendar.version} · 最多 366 个例外
              </Typography.Text>
            </Space>
          </section>

          <section aria-label="事项估分">
            <Typography.Title level={5}>受权事项估分</Typography.Title>
            <Space wrap>
              <Input
                aria-label="事项 ID"
                placeholder="输入当前可见 WorkItem UUID"
                value={workItemId}
                onChange={(event) => setWorkItemId(event.target.value)}
              />
              <Select
                aria-label="估分单位"
                value={unit}
                options={[
                  { label: '小时', value: 'hour' },
                  { label: '工作日', value: 'day' },
                  { label: '故事点', value: 'point' },
                ]}
                onChange={setUnit}
              />
              <InputNumber
                aria-label="估分数值"
                min={0.01}
                max={100000}
                precision={2}
                value={amount}
                onChange={(value) => setAmount(value ?? 1)}
              />
              <Button
                type="primary"
                disabled={!writable || !workItemId.trim()}
                loading={estimateMutation.isPending}
                onClick={() => estimateMutation.mutate()}
              >
                保存估分
              </Button>
            </Space>
            <Alert
              className="resource-planning-unit-note"
              type="info"
              showIcon
              message="故事点、小时和工作日保持显式单位；故事点不会被隐式换算为日期。"
            />
            <List
              data-testid="resource-estimate-list"
              dataSource={query.data.estimates}
              locale={{ emptyText: <Empty description="暂无当前可见估分" /> }}
              renderItem={(value) => {
                const projection = schedule.get(value.workItemId)
                return (
                  <List.Item>
                    <List.Item.Meta
                      title={(
                        <Space wrap>
                          <Typography.Text copyable>{value.workItemId}</Typography.Text>
                          <Tag>{value.amount} {value.unit}</Tag>
                          <Tag>v{value.version}</Tag>
                        </Space>
                      )}
                      description={projection?.timeComparable
                        ? `${projection.projectedStart} → ${projection.projectedFinish ?? '超出 730 天窗口'} · ${projection.explanation}`
                        : projection?.explanation}
                    />
                  </List.Item>
                )
              }}
            />
          </section>

          <section className="resource-planning-offline" aria-label="离线输入">
            <Input.TextArea
              aria-label="离线资源草稿"
              data-testid="resource-planning-offline-draft"
              rows={2}
              value={offlineDraft}
              placeholder="本地临时草稿；网络恢复前不会伪造保存成功"
              onChange={(event) => setOfflineDraft(event.target.value)}
            />
            <Tag color={online ? 'green' : 'orange'}>
              {online ? '在线' : '离线 · 本地输入保留'}
            </Tag>
            <Typography.Text type="secondary">
              {query.data.calendar.updatedAt && `最近校准 ${formatTime(query.data.calendar.updatedAt)}`}
            </Typography.Text>
          </section>
        </div>
      )}
    </Card>
  )
}
