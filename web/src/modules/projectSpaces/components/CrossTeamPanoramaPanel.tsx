import { ApartmentOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Card, Empty, List, Space, Statistic, Switch, Tag, Typography } from 'antd'
import { useEffect, useState } from 'react'

import {
  crossSpaceKeys,
  getCrossTeamPanorama,
  saveCrossTeamPanoramaPreference,
} from '../api/crossSpaceCollaborationApi'
import type { UserProjectSpace } from '../api/projectSpacesApi'
import { formatTime } from '../projectSpaceView'

export function CrossTeamPanoramaPanel({ space }: { space: UserProjectSpace }) {
  const client = useQueryClient()
  const [online, setOnline] = useState(() => navigator.onLine)
  const query = useQuery({
    queryKey: crossSpaceKeys.panorama(space.id),
    queryFn: () => getCrossTeamPanorama(space.id),
  })
  const save = useMutation({
    mutationFn: (compact: boolean) =>
      saveCrossTeamPanoramaPreference(space.id, query.data!.preference, compact),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: crossSpaceKeys.panorama(space.id) })
    },
  })
  useEffect(() => {
    const calibrate = () => {
      setOnline(navigator.onLine)
      if (navigator.onLine) void query.refetch()
    }
    window.addEventListener('online', calibrate)
    window.addEventListener('offline', calibrate)
    window.addEventListener('focus', calibrate)
    return () => {
      window.removeEventListener('online', calibrate)
      window.removeEventListener('offline', calibrate)
      window.removeEventListener('focus', calibrate)
    }
  }, [query])
  const health = query.data?.health
  return (
    <Card
      className="content-card cross-team-panorama"
      data-testid="cross-team-panorama-panel"
      title={<Space><ApartmentOutlined />跨团队全景与协作审计</Space>}
      extra={query.data ? (
        <Space>
          <span>紧凑视图</span>
          <Switch
            aria-label="紧凑视图"
            disabled={!online || save.isPending}
            checked={query.data.preference.compact}
            onChange={(checked) => save.mutate(checked)}
          />
        </Space>
      ) : null}
    >
      {!online ? <Alert type="warning" showIcon message="当前离线，全景保持最后受权事实；恢复在线后 REST 重新校准" /> : null}
      {query.isError ? <Alert type="error" showIcon message="跨团队全景加载失败" /> : null}
      {health ? (
        <Space wrap>
          <Statistic title="授权" value={health.grants} />
          <Statistic title="关系策略" value={health.relations} />
          <Statistic title="同步规则" value={health.syncRules} />
          <Statistic title="待处理冲突" value={health.openConflicts} />
          <Tag color={health.status === 'healthy' ? 'green' : health.status === 'attention' ? 'orange' : 'default'}>
            {health.status}
          </Tag>
        </Space>
      ) : null}
      {!query.isLoading && query.data?.slices.length === 0
        ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无当前受权跨团队事实" />
        : null}
      <List
        loading={query.isLoading}
        size={query.data?.preference.compact ? 'small' : 'default'}
        dataSource={query.data?.slices ?? []}
        renderItem={(slice) => (
          <List.Item>
            <List.Item.Meta
              title={<Space><Tag>{slice.kind}</Tag><strong>{slice.status}</strong><span>v{slice.version}</span></Space>}
              description={`${slice.source} · ${formatTime(slice.observedAt)}`}
            />
          </List.Item>
        )}
      />
      {query.data?.audit.length ? (
        <Typography.Paragraph type="secondary">
          审计 lineage：每条结论只绑定当前受权来源 identity/version；不复制标题、字段、状态正文或权限快照。
        </Typography.Paragraph>
      ) : null}
    </Card>
  )
}
