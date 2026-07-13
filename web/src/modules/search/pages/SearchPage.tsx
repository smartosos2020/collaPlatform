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
          <Typography.Text type="secondary">搜索事项、知识内容、Base、数据表、表格记录和消息</Typography.Text>
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
          {searchQuery.isError ? <Alert type="error" showIcon message="搜索暂时不可用" description="请检查网络连接后重试。" /> : null}
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
                      <Space wrap>
                        <Tag>{objectTypeText[item.objectType] ?? item.objectType}</Tag>
                        {item.accessState === 'available' ? <Tag color="green">可访问</Tag> : <Tag color="orange">{accessStateText(item.accessState)}</Tag>}
                        {item.objectType === 'knowledge_content' && item.contentType ? <Tag>{contentTypeText(item.contentType)}</Tag> : null}
                        {item.objectType === 'knowledge_content' && item.knowledgeBaseName ? <Tag color="purple">{item.knowledgeBaseName}</Tag> : null}
                        <Typography.Text strong>{resultTitle(item)}</Typography.Text>
                      </Space>
                      {item.objectType === 'knowledge_content' && item.directoryPath ? (
                        <Typography.Text type="secondary">路径：{item.directoryPath}</Typography.Text>
                      ) : null}
                      <Typography.Paragraph type="secondary">{item.excerpt || item.permissionExplanation || item.webPath}</Typography.Paragraph>
                      {item.objectType === 'knowledge_content' && item.tags?.length ? (
                        <Space wrap size={4}>
                          {item.tags.map((tag) => <Tag key={tag}>{tag}</Tag>)}
                        </Space>
                      ) : null}
                      {item.permissionExplanation ? <Alert type="info" showIcon message={item.permissionExplanation} /> : null}
                    </div>
                    {item.accessState === 'available' && (item.webPath || item.deepLink) ? (
                      <Button type="link" aria-label={`打开${objectTypeText[item.objectType] ?? '对象'} ${resultTitle(item)}`} onClick={() => navigate(resolveNavigationPath(item) ?? item.webPath ?? '/')}>
                        {item.objectType === 'knowledge_content' ? '打开知识内容' : '打开'}
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

function accessStateText(value: SearchResult['accessState']) {
  return ({ forbidden: '无权限查看', deleted: '对象已删除', not_found: '对象不存在', invalid: '链接无法识别' } as Record<string, string>)[value] ?? value
}
function groupResults(items: SearchResult[]) {
  return items.reduce<Record<string, SearchResult[]>>((acc, item) => {
    acc[item.objectType] = acc[item.objectType] ?? []
    acc[item.objectType].push(item)
    return acc
  }, {})
}

function resultTitle(item: SearchResult) {
  if (item.accessState !== 'available') {
    return '不可访问对象'
  }
  if (item.objectType === 'knowledge_content' && item.directoryPath) {
    return item.directoryPath
  }
  return item.title || '未命名对象'
}

function contentTypeText(value: SearchResult['contentType']) {
  if (!value) return ''
  return ({
    space: '知识库根',
    folder: '目录',
    markdown: '知识内容',
    object_ref: '对象入口',
    external_link: '外部链接',
  } as Record<string, string>)[value] ?? value
}
