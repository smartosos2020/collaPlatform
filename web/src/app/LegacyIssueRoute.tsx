import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Skeleton } from 'antd'
import { useEffect } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

import { resolveLegacyIssue } from '../modules/projectSpaces/api/workItemsApi'

export function LegacyIssueRoute() {
  const { issueId } = useParams()
  const navigate = useNavigate()
  const query = useQuery({
    queryKey: ['legacy-issue-location', issueId],
    queryFn: () => resolveLegacyIssue(issueId as string),
    enabled: Boolean(issueId),
    retry: false,
  })

  useEffect(() => {
    const location = query.data?.location
    if (location && location !== `/issues/${issueId}`) {
      navigate(location, { replace: true })
    }
  }, [issueId, navigate, query.data])

  if (query.isLoading) return <Card><Skeleton active /></Card>
  if (query.isError) {
    return (
      <Card>
        <Alert
          type="error"
          showIcon
          message="工作项链接不可用"
          description="该旧链接不存在、不可访问，或迁移映射尚未就绪。"
          action={<Button onClick={() => navigate('/project-spaces')}>返回项目空间</Button>}
        />
      </Card>
    )
  }
  if (query.data?.location === `/issues/${issueId}`) {
    return (
      <Card>
        <Alert
          type="warning"
          showIcon
          message="旧事项入口已停止"
          description="该事项没有可用的规范 WorkItem 映射。旧页面和写入口已永久退出。"
          action={<Button onClick={() => navigate('/project-spaces')}>进入项目空间</Button>}
        />
      </Card>
    )
  }
  return <Card><Skeleton active /></Card>
}
