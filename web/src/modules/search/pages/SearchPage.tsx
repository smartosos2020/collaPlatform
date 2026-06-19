import { SearchOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Empty, Input, Space, Tag, Typography } from 'antd'
import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import { resolveNavigationPath } from '../../../shared/client/collaClient'
import { searchAll, type SearchResult } from '../api/searchApi'
import { objectTypeText } from '../../platform/objectTypeLabels'

export function SearchPage() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const queryParam = params.get('q') ?? ''
  const [draft, setDraft] = useState(queryParam)
  const normalizedQuery = queryParam.trim()

  const searchQuery = useQuery({
    queryKey: ['search', normalizedQuery],
    queryFn: () => searchAll(normalizedQuery),
    enabled: normalizedQuery.length >= 2,
  })

  const grouped = useMemo(() => groupResults(searchQuery.data?.items ?? []), [searchQuery.data?.items])

  const submit = () => {
    const next = draft.trim()
    if (next.length >= 2) {
      setParams({ q: next })
    }
  }

  return (
    <div className="page-stack">
      <Space className="page-toolbar">
        <div>
          <Typography.Title level={3}>全局搜索</Typography.Title>
          <Typography.Text type="secondary">搜索事项、文档、Base、数据表、表格记录和消息</Typography.Text>
        </div>
        <Input.Search
          className="global-search-page-input"
          allowClear
          enterButton={<Button type="primary" icon={<SearchOutlined />} />}
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          onSearch={submit}
        />
      </Space>

      {normalizedQuery.length < 2 ? (
        <Empty description="输入至少 2 个字符开始搜索" />
      ) : (
        <Space orientation="vertical" size={16} className="page-stack">
          {Object.entries(grouped).map(([type, items]) => (
            <section className="search-section" key={type}>
              <Space className="search-section-title">
                <Typography.Title level={5}>{objectTypeText[type] ?? type}</Typography.Title>
                <Tag>{items.length}</Tag>
              </Space>
              <Space orientation="vertical" size={8} className="search-result-list">
                {searchQuery.isLoading ? <Typography.Text type="secondary">搜索中...</Typography.Text> : null}
                {items.map((item) => (
                  <div className="search-result-item" key={`${item.objectType}-${item.objectId}`}>
                    <div>
                      <Space>
                        <Tag>{objectTypeText[item.objectType] ?? item.objectType}</Tag>
                        {item.accessState === 'available' ? <Tag color="green">可访问</Tag> : <Tag color="orange">{item.accessState}</Tag>}
                        <Typography.Text strong>{item.title || '不可访问对象'}</Typography.Text>
                      </Space>
                      <Typography.Paragraph type="secondary">{item.excerpt || item.permissionExplanation || item.webPath}</Typography.Paragraph>
                      {item.permissionExplanation ? <Alert type="info" showIcon message={item.permissionExplanation} /> : null}
                    </div>
                    {item.accessState === 'available' && (item.webPath || item.deepLink) ? (
                      <Button type="link" onClick={() => navigate(resolveNavigationPath(item) ?? item.webPath ?? '/')}>
                        打开
                      </Button>
                    ) : null}
                  </div>
                ))}
                {items.length === 0 && !searchQuery.isLoading ? <Empty description="暂无结果" /> : null}
              </Space>
            </section>
          ))}
          {!searchQuery.isLoading && (searchQuery.data?.items.length ?? 0) === 0 ? <Empty description="没有匹配结果" /> : null}
        </Space>
      )}
    </div>
  )
}

function groupResults(items: SearchResult[]) {
  return items.reduce<Record<string, SearchResult[]>>((acc, item) => {
    acc[item.objectType] = acc[item.objectType] ?? []
    acc[item.objectType].push(item)
    return acc
  }, {})
}
