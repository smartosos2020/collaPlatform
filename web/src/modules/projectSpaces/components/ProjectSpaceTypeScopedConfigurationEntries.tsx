import { ReloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, List, Modal, Space, Tag, Typography } from 'antd'
import type { ReactNode } from 'react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

import {
  listConfiguredWorkItemTypes,
  workItemTypeKeys,
  type ConfiguredWorkItemType,
} from '../api/workItemTypesApi'
import {
  projectSpaceConfigurationLocation,
  type ProjectSpaceConfigurationDestination,
} from '../projectSpaceConfigurationNavigation'

type TypeScopedDestination = Exclude<
  ProjectSpaceConfigurationDestination,
  'type-catalog'
>

export type ProjectSpaceTypeScopedConfigurationAction = Readonly<{
  destination: TypeScopedDestination
  label: string
  icon?: ReactNode
}>

export function ProjectSpaceTypeScopedConfigurationEntries({
  spaceId,
  actions,
}: {
  spaceId: string
  actions: readonly ProjectSpaceTypeScopedConfigurationAction[]
}) {
  const navigate = useNavigate()
  const [pendingAction, setPendingAction] = useState<
    ProjectSpaceTypeScopedConfigurationAction | null
  >(null)
  const typesQuery = useQuery({
    queryKey: workItemTypeKeys.configuration(spaceId, 'all'),
    queryFn: () => listConfiguredWorkItemTypes(spaceId),
    retry: false,
  })
  const types = typesQuery.data?.items ?? []

  const openDestination = (
    action: ProjectSpaceTypeScopedConfigurationAction,
    type: ConfiguredWorkItemType,
  ) => {
    const target = projectSpaceConfigurationLocation({
      spaceId,
      typeId: type.id,
      destination: action.destination,
    })
    if (!target) return
    setPendingAction(null)
    navigate(target)
  }

  const requestDestination = (
    action: ProjectSpaceTypeScopedConfigurationAction,
  ) => {
    if (types.length === 1) {
      openDestination(action, types[0])
      return
    }
    setPendingAction(action)
  }

  return (
    <Space direction="vertical" size={10} style={{ width: '100%' }}>
      <Space wrap>
        {actions.map((action) => (
          <Button
            key={action.destination}
            icon={action.icon}
            loading={typesQuery.isLoading}
            disabled={typesQuery.isError || types.length === 0}
            onClick={() => requestDestination(action)}
          >
            {action.label}
          </Button>
        ))}
      </Space>
      {typesQuery.isError ? (
        <Alert
          type="error"
          showIcon
          message="任务模板加载失败"
          description="无法确认要配置的任务模板，未执行跳转。"
          action={(
            <Button
              size="small"
              icon={<ReloadOutlined />}
              onClick={() => typesQuery.refetch()}
            >
              重试
            </Button>
          )}
        />
      ) : null}
      {!typesQuery.isLoading && !typesQuery.isError && types.length === 0 ? (
        <Alert
          type="info"
          showIcon
          message="还没有可配置的任务模板"
          description="请先创建任务模板，再配置字段、页面、流程或权限。"
        />
      ) : null}
      <Modal
        open={Boolean(pendingAction)}
        title={pendingAction ? `选择任务模板 · ${pendingAction.label}` : '选择任务模板'}
        footer={null}
        destroyOnHidden
        onCancel={() => setPendingAction(null)}
      >
        <Typography.Paragraph type="secondary">
          配置只写入所选任务模板的草稿；发布前不会影响成员正在使用的版本。
        </Typography.Paragraph>
        <List
          dataSource={types}
          locale={{ emptyText: '暂无任务模板' }}
          renderItem={(type) => (
            <List.Item>
              <Button
                block
                type="text"
                aria-label={`选择任务模板：${type.name}`}
                onClick={() => pendingAction && openDestination(pendingAction, type)}
              >
                <Space wrap>
                  <Typography.Text strong>{type.name}</Typography.Text>
                  <Typography.Text code>{type.typeKey}</Typography.Text>
                  <Tag color={typeStatusColor(type.status)}>{typeStatusLabel(type.status)}</Tag>
                </Space>
              </Button>
            </List.Item>
          )}
        />
      </Modal>
    </Space>
  )
}

function typeStatusLabel(status: ConfiguredWorkItemType['status']) {
  return {
    active: '使用中',
    disabled: '已停用',
    retired: '已退役',
  }[status]
}

function typeStatusColor(status: ConfiguredWorkItemType['status']) {
  return {
    active: 'success',
    disabled: 'warning',
    retired: 'default',
  }[status]
}
