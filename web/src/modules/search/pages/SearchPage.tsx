import { SearchOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Empty, Input, Select, Space, Tag, Typography } from 'antd'
import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import { resolveNavigationPath } from '../../../shared/client/collaClient'
import { searchAll, type SearchFilters, type SearchResult } from '../api/searchApi'
import { objectTypeText } from '../../platform/objectTypeLabels'
import { listKnowledgeBases } from '../../knowledgeBases/api/knowledgeBasesApi'

export function SearchPage() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const queryParam = params.get('q') ?? ''
  const [draft, setDraft] = useState(queryParam)
  const normalizedQuery = queryParam.trim()
  const searchFilters = useMemo<SearchFilters>(() => ({
    knowledgeBaseId: params.get('knowledgeBaseId') || undefined,
    contentType: params.get('contentType') || undefined,
    knowledgeStatus: params.get('knowledgeStatus') || undefined,
    tags: params.getAll('tags').filter(Boolean),
  }), [params])

  const spacesQuery = useQuery({ queryKey: ['knowledge-bases', 'search-filter'], queryFn: () => listKnowledgeBases() })

  const searchQuery = useQuery({
    queryKey: ['search', normalizedQuery, searchFilters],
    queryFn: () => searchAll(normalizedQuery, 20, searchFilters),
    enabled: normalizedQuery.length >= 2,
  })

  const grouped = useMemo(() => groupResults(searchQuery.data?.items ?? []), [searchQuery.data?.items])

  const submit = () => {
    const next = draft.trim()
    if (next.length >= 2) {
      setParams({ q: next })
    }
  }

  const updateFilter = (key: 'knowledgeBaseId' | 'contentType' | 'knowledgeStatus' | 'tags', value?: string) => {
    const next = new URLSearchParams(params)
    next.delete(key)
    if (value?.trim()) {
      if (key === 'tags') {
        value.split(',').map((tag) => tag.trim()).filter(Boolean).forEach((tag) => next.append('tags', tag))
      } else {
        next.set(key, value)
      }
    }
    setParams(next)
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

      <Space wrap className="search-filter-bar" aria-label="知识内容筛选">
        <Select
          allowClear
          placeholder="全部知识库"
          value={searchFilters.knowledgeBaseId}
          loading={spacesQuery.isLoading}
          options={(spacesQuery.data ?? []).map((space) => ({ value: space.id, label: space.name }))}
          onChange={(value) => updateFilter('knowledgeBaseId', value)}
        />
        <Select
          allowClear
          placeholder="全部内容类型"
          value={searchFilters.contentType}
          options={[
            { value: 'markdown', label: '知识内容' },
            { value: 'folder', label: '目录' },
            { value: 'object_ref', label: '对象入口' },
            { value: 'external_link', label: '外部链接' },
          ]}
          onChange={(value) => updateFilter('contentType', value)}
        />
        <Select
          allowClear
          placeholder="全部知识状态"
          value={searchFilters.knowledgeStatus}
          options={[
            { value: 'draft', label: '草稿' },
            { value: 'verified', label: '已核验' },
            { value: 'needs_review', label: '待复核' },
            { value: 'outdated', label: '已过期' },
            { value: 'archived', label: '已归档' },
          ]}
          onChange={(value) => updateFilter('knowledgeStatus', value)}
        />
        <Input
          allowClear
          aria-label="按标签筛选"
          placeholder="标签，多个用逗号分隔"
          defaultValue={searchFilters.tags?.join(',')}
          onPressEnter={(event) => updateFilter('tags', event.currentTarget.value)}
          onBlur={(event) => updateFilter('tags', event.currentTarget.value)}
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
                        <Typography.Text strong><HighlightedText text={resultTitle(item)} query={normalizedQuery} /></Typography.Text>
                      </Space>
                      {item.objectType === 'knowledge_content' && item.directoryPath ? (
                        <Typography.Text type="secondary">路径：<HighlightedText text={item.directoryPath} query={normalizedQuery} /></Typography.Text>
                      ) : null}
                      <Typography.Paragraph type="secondary"><HighlightedText text={item.excerpt || item.permissionExplanation || item.webPath || ''} query={normalizedQuery} /></Typography.Paragraph>
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

function HighlightedText({ text, query }: { text: string; query: string }) {
  if (!query.trim()) return text
  const escaped = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const parts = text.split(new RegExp(`(${escaped})`, 'gi'))
  return <>{parts.map((part, index) => part.toLocaleLowerCase() === query.toLocaleLowerCase() ? <mark key={`${part}-${index}`}>{part}</mark> : part)}</>
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
