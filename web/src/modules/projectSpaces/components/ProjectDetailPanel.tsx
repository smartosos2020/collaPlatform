import {
  DashboardOutlined,
  ReloadOutlined,
  SaveOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Checkbox,
  Input,
  List,
  Progress,
  Space,
  Statistic,
  Switch,
  Tag,
  Typography,
} from 'antd'
import { useEffect, useState } from 'react'

import {
  getProjectDetail,
  projectDetailKeys,
  saveProjectDetailPreference,
  type ProjectDetailPreference,
} from '../api/projectDetailsApi'
import type { UserProjectSpace } from '../api/projectSpacesApi'
import { errorMessage, formatTime } from '../projectSpaceView'

const sectionOptions = [
  { label: '计划', value: 'plan' },
  { label: '台账', value: 'register' },
  { label: '交付', value: 'delivery' },
  { label: '健康', value: 'health' },
] as const

const statusPresentation = {
  healthy: { color: 'success', label: '健康' },
  attention: { color: 'warning', label: '需关注' },
  critical: { color: 'error', label: '严重' },
  unknown: { color: 'default', label: '未知（数据已截断）' },
} as const

export function ProjectDetailPanel({ space }: { space: UserProjectSpace }) {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const [sectionOverride, setSectionOverride] =
    useState<ProjectDetailPreference['visibleSections']>()
  const [compactOverride, setCompactOverride] = useState<boolean>()
  const [offlineNote, setOfflineNote] = useState('')
  const [online, setOnline] = useState(() => navigator.onLine)
  const detailQuery = useQuery({
    queryKey: projectDetailKeys.detail(space.id),
    queryFn: () => getProjectDetail(space.id),
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
  const preferenceMutation = useMutation({
    mutationFn: () => saveProjectDetailPreference(
      space.id,
      detailQuery.data!.preference,
      sections,
      compact,
    ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: projectDetailKeys.detail(space.id) })
      setSectionOverride(undefined)
      setCompactOverride(undefined)
      message.success('个人详情偏好已保存')
    },
    onError: (error) => message.error(errorMessage(error, '偏好保存失败，请 REST 校准后重试')),
  })
  const detail = detailQuery.data
  const sections = sectionOverride
    ?? detail?.preference.visibleSections
    ?? ['plan', 'register', 'delivery', 'health']
  const compact = compactOverride ?? detail?.preference.compact ?? false
  const status = statusPresentation[detail?.health.status ?? 'unknown']
  const show = (section: ProjectDetailPreference['visibleSections'][number]) =>
    sections.includes(section)

  return (
    <Card
      className={`content-card project-detail-panel${compact ? ' is-compact' : ''}`}
      data-testid="project-detail-panel"
      title={<Space><DashboardOutlined />项目详情与健康</Space>}
      extra={(
        <Space wrap>
          <Tag color={status.color} data-testid="project-health-status">{status.label}</Tag>
          <Button
            icon={<ReloadOutlined />}
            loading={detailQuery.isFetching}
            onClick={() => void detailQuery.refetch()}
          >
            REST 校准
          </Button>
        </Space>
      )}
    >
      {detailQuery.isError && (
        <Alert
          type="error"
          showIcon
          message={errorMessage(detailQuery.error, '项目详情加载失败')}
        />
      )}
      {detail && (
        <div className="project-detail-layout">
          <section className="project-detail-preference" aria-label="项目详情个人偏好">
            <Typography.Title level={5}>个人视图</Typography.Title>
            <Checkbox.Group
              aria-label="可见详情区块"
              options={sectionOptions.map((value) => ({ ...value }))}
              value={sections}
              onChange={(values) =>
                setSectionOverride(values as ProjectDetailPreference['visibleSections'])}
            />
            <Space>
              <Switch
                aria-label="紧凑模式"
                checked={compact}
                onChange={setCompactOverride}
              />
              <Typography.Text>紧凑模式</Typography.Text>
            </Space>
            <Button
              icon={<SaveOutlined />}
              disabled={sections.length === 0}
              loading={preferenceMutation.isPending}
              onClick={() => preferenceMutation.mutate()}
            >
              保存个人偏好
            </Button>
            <Input.TextArea
              aria-label="离线项目笔记"
              data-testid="project-detail-offline-note"
              rows={compact ? 2 : 3}
              value={offlineNote}
              placeholder="本地临时笔记；网络恢复后仍保留在当前页面"
              onChange={(event) => setOfflineNote(event.target.value)}
            />
            <Tag color={online ? 'green' : 'orange'}>
              {online ? '在线' : '离线 · 本地输入保留'}
            </Tag>
          </section>

          {show('health') && (
            <section className="project-detail-health" aria-label="项目健康">
              <Space wrap className="project-detail-health-heading">
                <Tag color={status.color} icon={<WarningOutlined />}>{status.label}</Tag>
                <Typography.Text type="secondary">
                  {detail.health.policyVersion} · {formatTime(detail.health.derivedAt)}
                </Typography.Text>
              </Space>
              <div className="project-detail-metrics">
                <Statistic title="未验证问题" value={detail.blocking.openIssues} />
                <Statistic title="高风险" value={detail.blocking.highRisks} />
                <Statistic title="待决变更" value={detail.blocking.pendingChanges} />
                <Statistic title="待验收" value={detail.blocking.pendingAcceptances} />
                <Statistic title="驳回交付" value={detail.blocking.rejectedDeliverables} />
              </div>
              {detail.health.truncated && (
                <Alert
                  type="warning"
                  showIcon
                  message="详情达到确定性预算上限，健康状态按 unknown 处理"
                />
              )}
              <List
                size="small"
                data-testid="project-health-signals"
                header={`可解释信号（${detail.health.signals.length} / 50）`}
                dataSource={detail.health.signals}
                locale={{ emptyText: '当前没有阻塞或关注信号' }}
                renderItem={(signal) => (
                  <List.Item>
                    <List.Item.Meta
                      title={(
                        <Space wrap>
                          <Tag color={signal.severity === 'critical' ? 'error' : 'warning'}>
                            {signal.code}
                          </Tag>
                          <Typography.Text>{signal.explanation}</Typography.Text>
                        </Space>
                      )}
                      description={`${signal.rule} · ${signal.sourceType} v${signal.sourceVersion}`}
                    />
                  </List.Item>
                )}
              />
            </section>
          )}

          <section className="project-detail-sources" aria-label="项目详情来源">
            {show('plan') && (
              <div className="project-detail-source">
                <Typography.Title level={5}>计划偏差</Typography.Title>
                {detail.deviations.map((value) => (
                  <div key={value.planId}>
                    <Progress percent={value.completionPercent} size="small" />
                    <Typography.Text type="secondary">
                      里程碑 {value.visibleMilestones} · 逾期 {value.overdueMilestones}
                    </Typography.Text>
                  </div>
                ))}
                {detail.deviations.length === 0 && <Typography.Text type="secondary">暂无计划</Typography.Text>}
              </div>
            )}
            {show('register') && (
              <div className="project-detail-source">
                <Typography.Title level={5}>项目台账</Typography.Title>
                <Typography.Text>{detail.registerEntries.length} 条当前可见记录</Typography.Text>
              </div>
            )}
            {show('delivery') && (
              <div className="project-detail-source">
                <Typography.Title level={5}>交付与验收</Typography.Title>
                <Typography.Text>{detail.deliverables.length} 个当前可见交付物</Typography.Text>
              </div>
            )}
          </section>
        </div>
      )}
    </Card>
  )
}
