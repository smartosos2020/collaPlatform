import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Empty, Result, Spin } from 'antd'
import { Navigate, useLocation, useNavigate, useParams } from 'react-router-dom'

import { ApiRequestError } from '../../../shared/api/httpClient'
import { getKnowledgeBase } from '../api/knowledgeBasesApi'
import { KnowledgeBaseDetailPage } from './KnowledgeBaseDetailPage'

export function KnowledgeBaseSpaceRoute() {
  const { spaceId = '' } = useParams<{ spaceId: string }>()
  const location = useLocation()
  const navigate = useNavigate()
  const params = new URLSearchParams(location.search)
  const legacyItemId = params.get('itemId')

  const spaceQuery = useQuery({
    queryKey: ['knowledge-bases', spaceId, 'entry'],
    queryFn: () => getKnowledgeBase(spaceId),
    enabled: Boolean(spaceId),
    retry: false,
  })

  if (params.get('view') === 'management' || params.get('view') === 'directory') {
    return <KnowledgeBaseDetailPage />
  }

  if (legacyItemId) {
    return <Navigate to={`/knowledge-bases/${spaceId}/items/${legacyItemId}`} replace />
  }

  if (spaceQuery.isLoading) {
    return (
      <div className="kb-entry-state" aria-live="polite">
        <Spin size="large" />
        <span>正在打开知识库首页...</span>
      </div>
    )
  }

  if (spaceQuery.isError) {
    const error = spaceQuery.error instanceof ApiRequestError ? spaceQuery.error : null
    if (error?.status === 403) {
      return (
        <Result
          status="403"
          title="无法访问这个知识库"
          subTitle="当前账号没有查看权限。你可以返回知识库列表选择其他内容。"
          extra={<Button type="primary" onClick={() => navigate('/knowledge-bases')}>返回知识库</Button>}
        />
      )
    }
    if (error?.status === 404) {
      return (
        <Result
          status="404"
          title="知识库不存在或已被移除"
          subTitle="为避免泄露目录信息，此处不会展示原知识库名称。"
          extra={<Button type="primary" onClick={() => navigate('/knowledge-bases')}>返回知识库</Button>}
        />
      )
    }
    return (
      <div className="kb-entry-state">
        <Alert
          showIcon
          type="error"
          message="知识库暂时无法加载"
          description="请检查网络后重试。当前页面不会自动跳转到不相关内容。"
          action={<Button onClick={() => spaceQuery.refetch()}>重试</Button>}
        />
      </div>
    )
  }

  const targetId = spaceQuery.data?.homeItem?.id ?? spaceQuery.data?.rootItem?.id
  if (!targetId) {
    return (
      <Empty className="kb-entry-state" description="知识库还没有可打开的首页">
        <Button type="primary" onClick={() => navigate('/knowledge-bases')}>返回知识库</Button>
      </Empty>
    )
  }

  return <Navigate to={`/knowledge-bases/${spaceId}/items/${targetId}`} replace />
}
