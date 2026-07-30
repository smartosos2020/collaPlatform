import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Skeleton } from 'antd'
import { useEffect } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'

import { resolveLegacyProjectSpace } from '../modules/projectSpaces/api/projectSpacesApi'
import {
  legacyProjectSpaceLocation,
  projectSpaceListLocation,
} from '../modules/projectSpaces/projectSpaceRouteContract'

export function LegacyProjectSpaceListRoute() {
  const location = useLocation()
  const navigate = useNavigate()
  useEffect(() => {
    navigate(
      projectSpaceListLocation(location.search, location.hash),
      { replace: true },
    )
  }, [location.hash, location.search, navigate])
  return <Card><Skeleton active /></Card>
}

export function LegacyProjectSpaceRoute() {
  const { projectId } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const query = useQuery({
    queryKey: ['legacy-project-space-location', projectId],
    queryFn: () => resolveLegacyProjectSpace(projectId as string),
    enabled: Boolean(projectId),
    retry: false,
  })
  const canonicalLocation = query.data?.status === 'mapped' && query.data.spaceId
    ? legacyProjectSpaceLocation(query.data.spaceId, location.search, location.hash)
    : null
  const listLocation = projectSpaceListLocation(location.search, location.hash)

  if (query.isLoading) return <Card><Skeleton active /></Card>

  if (canonicalLocation) {
    return (
      <Card>
        <Alert
          className="project-migration-banner"
          type="info"
          showIcon
          message="该项目已迁移到项目空间"
          description="旧项目入口保持只读；继续后将进入规范项目空间。"
          action={(
            <Button
              size="small"
              type="link"
              onClick={() => navigate(canonicalLocation, { replace: true })}
            >
              前往项目空间
            </Button>
          )}
        />
      </Card>
    )
  }

  return (
    <Card>
      <Alert
        type={query.isError || query.data?.status === 'failed' ? 'error' : 'warning'}
        showIcon
        message="项目链接不可用"
        description="该旧链接不存在、不可访问，或迁移映射尚未就绪。"
        action={<Button onClick={() => navigate(listLocation, { replace: true })}>返回项目空间</Button>}
      />
    </Card>
  )
}
