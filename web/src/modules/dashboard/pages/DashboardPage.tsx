import { useQuery } from '@tanstack/react-query'
import { Alert, Card, Col, Row, Space, Typography } from 'antd'

import { getHealth } from '../../platform/api/platformApi'

export function DashboardPage() {
  const healthQuery = useQuery({
    queryKey: ['health'],
    queryFn: getHealth,
  })

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Typography.Title level={2}>工作台</Typography.Title>
      {healthQuery.data ? (
        <Alert
          type="success"
          showIcon
          message={`后端服务正常：${healthQuery.data.service}`}
          description={healthQuery.data.time}
        />
      ) : null}
      {healthQuery.isError ? <Alert type="error" showIcon message="无法连接后端健康检查接口" /> : null}
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card title="我的待办">项目、Bug 和审批待办将在这里聚合。</Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="未读消息">IM 会话未读和 @提醒将在这里聚合。</Card>
        </Col>
      </Row>
    </Space>
  )
}

