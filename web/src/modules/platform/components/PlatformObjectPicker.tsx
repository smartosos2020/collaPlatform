import { useQuery } from '@tanstack/react-query'
import { Empty, Input, List, Pagination, Segmented, Space, Spin, Tag, Typography } from 'antd'
import { useState } from 'react'

import { listPlatformObjectChoices, type PlatformObjectSummary } from '../api/platformObjectsApi'

type Props = {
  objectTypes: string[]
  value?: string
  onChange?: (objectId: string, object?: PlatformObjectSummary) => void
}

const PAGE_SIZE = 8

export function PlatformObjectPicker({ objectTypes, value, onChange }: Props) {
  const [query, setQuery] = useState('')
  const [source, setSource] = useState<'all' | 'recent'>('all')
  const [page, setPage] = useState(1)
  const typesKey = objectTypes.join(',')

  const choices = useQuery({
    queryKey: ['platform', 'object-choices', typesKey, query, source, page],
    queryFn: () => listPlatformObjectChoices({
      types: objectTypes,
      query,
      source,
      limit: PAGE_SIZE,
      offset: (page - 1) * PAGE_SIZE,
    }),
    enabled: objectTypes.length > 0,
  })

  return (
    <Space direction="vertical" size={8} style={{ width: '100%' }}>
      <Space.Compact style={{ width: '100%' }}>
        <Segmented
          value={source}
          options={[{ label: '全部', value: 'all' }, { label: '最近', value: 'recent' }]}
          onChange={(next) => {
            setSource(next as 'all' | 'recent')
            setPage(1)
          }}
        />
        <Input.Search allowClear placeholder="搜索名称" value={query} onChange={(event) => {
          setQuery(event.target.value)
          setPage(1)
        }} />
      </Space.Compact>
      <Spin spinning={choices.isLoading}>
        <List
          size="small"
          bordered
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有可引用对象" /> }}
          dataSource={choices.data?.items ?? []}
          renderItem={(item) => (
            <List.Item
              role="option"
              aria-selected={value === item.objectId}
              onClick={() => onChange?.(item.objectId, item)}
              style={{ cursor: 'pointer', background: value === item.objectId ? '#f3f0ff' : undefined }}
              extra={<Tag>{objectTypeText(item.objectType)}</Tag>}
            >
              <List.Item.Meta
                title={<Typography.Text strong={value === item.objectId}>{item.title}</Typography.Text>}
                description={item.subtitle || item.status}
              />
            </List.Item>
          )}
        />
      </Spin>
      {(choices.data?.total ?? 0) > PAGE_SIZE ? (
        <Pagination size="small" current={page} pageSize={PAGE_SIZE} total={choices.data?.total ?? 0} showSizeChanger={false} onChange={setPage} />
      ) : null}
    </Space>
  )
}

function objectTypeText(type: string) {
  return { base: '多维表格', project: '项目', file: '文件', knowledge_content: '知识内容' }[type] ?? type
}
