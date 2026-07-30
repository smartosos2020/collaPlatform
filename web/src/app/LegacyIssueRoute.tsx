import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Skeleton } from 'antd'
import { useEffect } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'

import { resolveLegacyIssue } from '../modules/projectSpaces/api/workItemsApi'
import {
  projectSpaceListLocation,
  resolveCanonicalProjectSpaceLocation,
} from '../modules/projectSpaces/projectSpaceRouteContract'

export function LegacyIssueRoute() {
  const { issueId } = useParams()
  const currentLocation = useLocation()
  const navigate = useNavigate()
  const query = useQuery({
    queryKey: ['legacy-issue-location', issueId],
    queryFn: () => resolveLegacyIssue(issueId as string),
    enabled: Boolean(issueId),
    retry: false,
  })

  useEffect(() => {
    const location = query.data?.location
    const canonical = location
      ? resolveCanonicalProjectSpaceLocation(
          location,
          currentLocation.search,
          currentLocation.hash,
        )
      : null
    if (canonical) {
      navigate(canonical, { replace: true })
    }
  }, [currentLocation.hash, currentLocation.search, navigate, query.data])

  const listLocation = projectSpaceListLocation(
    currentLocation.search,
    currentLocation.hash,
  )

  if (query.isLoading) return <Card><Skeleton active /></Card>
  const canonical = query.data?.location
    ? resolveCanonicalProjectSpaceLocation(
        query.data.location,
        currentLocation.search,
        currentLocation.hash,
      )
    : null
  const retiredLegacyLocation = query.data?.location === `/issues/${issueId}`
  if (
    query.isError
    || (query.data?.location && !canonical && !retiredLegacyLocation)
  ) {
    return (
      <Card>
        <Alert
          type="error"
          showIcon
          message="工作项链接不可用"
          description="该旧链接不存在、不可访问，或迁移映射尚未就绪。"
          action={<Button onClick={() => navigate(listLocation)}>返回项目空间</Button>}
        />
      </Card>
    )
  }
  if (retiredLegacyLocation) {
    return (
      <Card>
        <Alert
          type="warning"
          showIcon
          message="旧事项入口已停止"
          description="该事项没有可用的规范 WorkItem 映射。旧页面和写入口已永久退出。"
          action={<Button onClick={() => navigate(listLocation)}>进入项目空间</Button>}
        />
      </Card>
    )
  }
  return <Card><Skeleton active /></Card>
}
