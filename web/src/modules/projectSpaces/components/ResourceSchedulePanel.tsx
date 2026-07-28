import { CalendarOutlined, ReloadOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Empty,
  Input,
  InputNumber,
  Select,
  Space,
  Tag,
  Typography,
} from 'antd'
import { useMemo, useState, type CSSProperties } from 'react'

import {
  adjustResourceAllocation,
  getResourceSchedule,
  resourceScheduleKeys,
  saveResourceSchedulePreference,
} from '../api/resourceScheduleApi'
import type { UserProjectSpace } from '../api/projectSpacesApi'
import { errorMessage } from '../projectSpaceView'

function barStyle(start: string, end: string, windowStart: string, windowEnd: string) {
  const day = 24 * 60 * 60 * 1000
  const distance = (a: string, b: string) =>
    Math.round((Date.parse(`${b}T00:00:00Z`) - Date.parse(`${a}T00:00:00Z`)) / day)
  const total = Math.max(1, distance(windowStart, windowEnd) + 1)
  const left = Math.max(0, distance(windowStart, start)) / total * 100
  const width = Math.max(2, (distance(start, end) + 1) / total * 100)
  return {
    '--resource-bar-left': `${Math.min(100, left)}%`,
    '--resource-bar-width': `${Math.min(100 - left, width)}%`,
  } as CSSProperties
}

export function ResourceSchedulePanel({ space }: { space: UserProjectSpace }) {
  const { message } = AntdApp.useApp()
  const client = useQueryClient()
  const query = useQuery({
    queryKey: resourceScheduleKeys.detail(space.id),
    queryFn: () => getResourceSchedule(space.id),
  })
  const [windowStart, setWindowStart] = useState('')
  const [windowEnd, setWindowEnd] = useState('')
  const [zoom, setZoom] = useState<'day' | 'week' | 'month'>('week')
  const [allocationId, setAllocationId] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [percent, setPercent] = useState(100)
  const [reason, setReason] = useState('')
  const [preview, setPreview] = useState('')
  const selected = useMemo(
    () => query.data?.bars.find((value) => value.allocationId === allocationId),
    [allocationId, query.data?.bars],
  )
  const refresh = () =>
    client.invalidateQueries({ queryKey: resourceScheduleKeys.detail(space.id) })
  const preference = useMutation({
    mutationFn: () => saveResourceSchedulePreference(space.id, {
      current: query.data!.preference,
      windowStart: windowStart || query.data!.windowStart,
      windowEnd: windowEnd || query.data!.windowEnd,
      zoom,
    }),
    onSuccess: async () => {
      await refresh()
      message.success('排期窗口偏好已保存')
    },
    onError: (error) => message.error(errorMessage(error, '偏好保存失败')),
  })
  const adjustment = useMutation({
    mutationFn: (commit: boolean) => adjustResourceAllocation(space.id, {
      requestId: crypto.randomUUID(),
      preview: !commit,
      allocationId,
      expectedVersion: selected?.sourceVersion ?? 0,
      startDate,
      endDate,
      allocationPercent: percent,
      reason,
    }),
    onSuccess: async (result) => {
      setPreview(`${result.provenance} · ${result.startDate} → ${result.endDate} · ${result.allocationPercent}%`)
      if (result.committed) {
        await refresh()
        message.success('资源调整已通过 canonical allocation 提交')
      }
    },
    onError: (error) => message.error(errorMessage(error, '资源调整失败，请 REST 校准')),
  })
  const manageable = space.status === 'active'
    && ['owner', 'admin'].includes(space.currentUserRole ?? '')
  const choose = (id: string) => {
    setAllocationId(id)
    const bar = query.data?.bars.find((value) => value.allocationId === id)
    if (bar) {
      setStartDate(bar.startDate)
      setEndDate(bar.endDate)
      setPercent(bar.allocationPercent)
    }
    setPreview('')
  }

  return (
    <Card
      className="content-card resource-schedule-panel"
      data-testid="resource-schedule-panel"
      title={<Space><CalendarOutlined />人员排期甘特与资源调整</Space>}
      extra={(
        <Button icon={<ReloadOutlined />} onClick={() => void query.refetch()}>
          REST 校准
        </Button>
      )}
    >
      {query.isError && <Alert type="error" showIcon message={errorMessage(query.error, '排期加载失败')} />}
      {query.data && (
        <div className="resource-schedule-layout">
          <Space wrap>
            <Input
              aria-label="排期窗口开始"
              type="date"
              value={windowStart || query.data.windowStart}
              onChange={(event) => setWindowStart(event.target.value)}
            />
            <Input
              aria-label="排期窗口结束"
              type="date"
              value={windowEnd || query.data.windowEnd}
              onChange={(event) => setWindowEnd(event.target.value)}
            />
            <Select
              aria-label="排期缩放"
              value={zoom}
              options={[
                { value: 'day', label: '日' },
                { value: 'week', label: '周' },
                { value: 'month', label: '月' },
              ]}
              onChange={setZoom}
            />
            <Button loading={preference.isPending} onClick={() => preference.mutate()}>
              保存窗口
            </Button>
          </Space>
          {query.data.truncated && <Alert type="warning" showIcon message="排期达到组合规模预算，结果已截断" />}
          <div className="resource-schedule-grid" role="table" aria-label="人员排期甘特">
            {query.data.rows.length === 0 && <Empty description="暂无当前受权排期" />}
            {query.data.rows.map((row) => (
              <div className="resource-schedule-row" role="row" key={row.userId}>
                <div className="resource-schedule-person">
                  <Typography.Text copyable>{row.userId}</Typography.Text>
                  <Tag color={row.conflictCount > 0 ? 'error' : 'green'}>
                    冲突 {row.conflictCount}
                  </Tag>
                  <Typography.Text type="secondary">
                    产能 {row.capacityMinutes} · 分配 {row.allocatedMinutes} · 实际 {row.actualMinutes}
                  </Typography.Text>
                </div>
                <div className="resource-schedule-track">
                  {query.data.bars.filter((bar) => bar.userId === row.userId).map((bar, index) => (
                    <button
                      type="button"
                      className="resource-assignment-bar"
                      data-testid="resource-assignment-bar"
                      key={bar.allocationId}
                      style={{
                        ...barStyle(
                          bar.startDate,
                          bar.endDate,
                          query.data.windowStart,
                          query.data.windowEnd,
                        ),
                        '--resource-bar-top': `${8 + index * 28}px`,
                      } as CSSProperties}
                      title={`${bar.startDate} → ${bar.endDate} · ${bar.allocationPercent}%`}
                      onClick={() => choose(bar.allocationId)}
                    >
                      {bar.allocationPercent}%
                    </button>
                  ))}
                  {query.data.conflicts.filter((marker) => marker.userId === row.userId).map((marker) => (
                    <span
                      className="resource-conflict-marker"
                      data-testid="resource-conflict-marker"
                      key={`${marker.userId}-${marker.date}`}
                      title={`${marker.date}: ${marker.allocatedMinutes}/${marker.capacityMinutes}`}
                    />
                  ))}
                </div>
              </div>
            ))}
          </div>
          <section aria-label="资源调整">
            <Typography.Title level={5}>调整预览与提交</Typography.Title>
            <Space wrap>
              <Select
                aria-label="选择分配"
                placeholder="选择当前受权分配"
                value={allocationId || undefined}
                options={query.data.bars.map((bar) => ({
                  value: bar.allocationId,
                  label: `${bar.workItemId} · ${bar.allocationPercent}%`,
                }))}
                onChange={choose}
              />
              <Input aria-label="调整开始日期" type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} />
              <Input aria-label="调整结束日期" type="date" value={endDate} onChange={(event) => setEndDate(event.target.value)} />
              <InputNumber aria-label="调整比例" min={0.01} max={100} value={percent} addonAfter="%" onChange={(value) => setPercent(value ?? 100)} />
              <Input aria-label="调整原因" value={reason} maxLength={500} onChange={(event) => setReason(event.target.value)} />
              <Button
                data-testid="resource-adjustment-preview-button"
                disabled={!manageable || !selected || !reason.trim()}
                onClick={() => adjustment.mutate(false)}
              >
                预览
              </Button>
              <Button type="primary" disabled={!manageable || !selected || !reason.trim()} onClick={() => adjustment.mutate(true)}>提交调整</Button>
            </Space>
            {!manageable && <Tag color="orange">仅空间 owner/admin 可调整；其余身份只读</Tag>}
            {preview && <Alert data-testid="resource-adjustment-preview" type="info" showIcon message={preview} />}
          </section>
        </div>
      )}
    </Card>
  )
}
