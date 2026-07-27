import {
  EyeInvisibleOutlined,
  LockOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Descriptions, List, Select, Space, Tag, Typography } from 'antd'
import { useState } from 'react'

import {
  getWorkItemPermissionExplanation,
  type WorkItem,
} from '../api/workItemsApi'
import type { UserProjectSpace } from '../api/projectSpacesApi'

const ACTIONS = [
  'view', 'edit', 'archive', 'restore', 'delete', 'comment', 'attach',
  'participant_manage', 'transition', 'workflow_manage', 'relate', 'accept_link',
  'relation_manage', 'field_read', 'field_write', 'role_assign', 'policy_manage',
  'permission_explain', 'permission_request', 'governance_inspect', 'migration_manage',
]

export function WorkItemPermissionsPanel({
  space,
  item,
}: {
  space: UserProjectSpace
  item: WorkItem
}) {
  const [action, setAction] = useState('view')
  const explanationQuery = useQuery({
    queryKey: ['work-item-permission-explanation', space.id, item.id, action, item.version],
    queryFn: () => getWorkItemPermissionExplanation(space.id, item.id, action),
    retry: false,
    refetchOnWindowFocus: false,
  })
  const fields = Object.entries(item.runtime.accessProjection)
    .sort(([left], [right]) => left.localeCompare(right))
  const explanation = explanationQuery.data
  return (
    <Card
      className="content-card work-item-permissions-panel"
      title={<Space><SafetyCertificateOutlined />权限与能力</Space>}
      extra={(
        <Button
          icon={<ReloadOutlined />}
          loading={explanationQuery.isFetching}
          onClick={() => explanationQuery.refetch()}
        >
          重新校准
        </Button>
      )}
    >
      <Alert
        type="info"
        showIcon
        message="所有能力均来自服务端决策"
        description="页面不从空间角色、按钮状态或策略名称补算授权；收权、版本或 subject 变化后请重新校准。"
      />
      <div className="work-item-permission-capabilities" aria-label="服务端能力">
        {item.availableActions.map((capability) => (
          <Tag color="green" key={capability}>{capability}</Tag>
        ))}
        {!item.availableActions.length ? <Tag icon={<LockOutlined />}>无可用动作</Tag> : null}
      </div>
      <div className="work-item-permission-explanation">
        <Typography.Text strong>安全解释</Typography.Text>
        <Select
          aria-label="选择权限动作"
          showSearch
          value={action}
          options={ACTIONS.map((value) => ({ value, label: value }))}
          onChange={setAction}
        />
        {explanationQuery.isError ? (
          <Alert
            type="warning"
            showIcon
            icon={<EyeInvisibleOutlined />}
            message="解释不可用"
            description="对象不可见、治理边界不足或服务端拒绝时，不显示隐藏策略和 subject 信息。"
          />
        ) : explanation ? (
          <Descriptions size="small" column={{ xs: 1, md: 3 }} bordered>
            <Descriptions.Item label="结果">
              <Tag color={explanation.allowed ? 'green' : 'red'}>
                {explanation.allowed ? '允许' : '拒绝'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="动作"><code>{explanation.action}</code></Descriptions.Item>
            <Descriptions.Item label="原因"><code>{explanation.reasonCode}</code></Descriptions.Item>
            <Descriptions.Item label="可披露来源" span={2}>
              {explanation.safePolicySources.length
                ? explanation.safePolicySources.map((source) => <Tag key={source}>{source}</Tag>)
                : '无'}
            </Descriptions.Item>
            <Descriptions.Item label="申请入口">
              {explanation.requestAvailable ? '可申请' : '不可用'}
            </Descriptions.Item>
          </Descriptions>
        ) : null}
      </div>
      <List
        className="work-item-permission-field-list"
        header={<Typography.Text strong>字段访问投影</Typography.Text>}
        locale={{ emptyText: '当前没有可披露字段' }}
        dataSource={fields}
        renderItem={([fieldKey, access]) => (
          <List.Item
            extra={(
              <Space>
                <Tag color={access.mode === 'write' ? 'green' : 'blue'}>{access.mode}</Tag>
                {access.required ? <Tag color="orange">required</Tag> : null}
              </Space>
            )}
          >
            <code>{fieldKey}</code>
          </List.Item>
        )}
      />
    </Card>
  )
}
