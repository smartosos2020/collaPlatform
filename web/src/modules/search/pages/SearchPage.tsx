import { SearchOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Button, Empty, Input, Space, Tag, Typography } from 'antd'
import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import { searchAll, type SearchResult } from '../api/searchApi'

const typeText: Record<string, string> = {
  issue: '事项',
  document: '文档',
  base_record: '表格记录',
  message: '消息',
}

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
          <Typography.Text type="secondary">搜索事项、文档、表格记录和消息</Typography.Text>
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
                <Typography.Title level={5}>{typeText[type] ?? type}</Typography.Title>
                <Tag>{items.length}</Tag>
              </Space>
              <Space orientation="vertical" size={8} className="search-result-list">
                {searchQuery.isLoading ? <Typography.Text type="secondary">搜索中...</Typography.Text> : null}
                {items.map((item) => (
                  <div className="search-result-item" key={`${item.objectType}-${item.objectId}`}>
                    <div>
                      <Space>
                        <Tag>{typeText[item.objectType]}</Tag>
                        <Typography.Text strong>{item.title}</Typography.Text>
                      </Space>
                      <Typography.Paragraph type="secondary">{item.excerpt || item.webPath}</Typography.Paragraph>
                    </div>
                    <Button type="link" onClick={() => navigate(item.webPath)}>
                      打开
                    </Button>
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
