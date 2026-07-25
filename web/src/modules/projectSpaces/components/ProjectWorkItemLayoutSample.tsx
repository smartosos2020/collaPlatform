import { EyeOutlined, ReloadOutlined } from '@ant-design/icons'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Empty, Segmented, Skeleton, Space, Tag, Typography } from 'antd'
import { useMemo, useState } from 'react'

import type { UserProjectSpace } from '../api/projectSpacesApi'
import {
  sampleWorkItemLayout,
  type WorkItemLayoutKind,
  type WorkItemLayoutProjection,
} from '../api/workItemLayoutsApi'
import { WorkItemLayoutRenderer, type WorkItemLayoutValues } from './WorkItemLayoutRenderer'

export function ProjectWorkItemLayoutSample({
  space,
  typeId,
}: {
  space: UserProjectSpace
  typeId: string
}) {
  const [kind, setKind] = useState<WorkItemLayoutKind>('create')
  const [values, setValues] = useState<WorkItemLayoutValues>({})
  const initialSampleQuery = useQuery({
    queryKey: ['project-spaces', space.id, typeId, 'layout-sample', kind],
    queryFn: () => sampleWorkItemLayout(space.id, typeId, kind, {}),
    retry: false,
  })
  const sampleMutation = useMutation({
    mutationFn: ({ targetKind, sampleValues }: {
      targetKind: WorkItemLayoutKind
      sampleValues: WorkItemLayoutValues
    }) => sampleWorkItemLayout(space.id, typeId, targetKind, sampleValues),
  })

  const projection = sampleMutation.variables?.targetKind === kind
    ? sampleMutation.data ?? initialSampleQuery.data
    : initialSampleQuery.data
  const loading = initialSampleQuery.isPending
    || (sampleMutation.isPending && sampleMutation.variables?.targetKind === kind)
  const failed = initialSampleQuery.isError
    || (sampleMutation.isError && sampleMutation.variables?.targetKind === kind)
  const hiddenValueKeys = useMemo(
    () => hiddenSamples(values, projection),
    [projection, values],
  )

  return (
    <section className="work-item-layout-sample" aria-label="工作项布局样例" data-testid="work-item-layout-sample">
      <Card
        className="content-card"
        title={<Space><EyeOutlined />布局样例</Space>}
        extra={(
          <Segmented
            aria-label="样例布局模式"
            value={kind}
            options={[{ label: '新建形态', value: 'create' }, { label: '详情形态', value: 'detail' }]}
            onChange={(value) => {
              setValues({})
              setKind(value as WorkItemLayoutKind)
            }}
          />
        )}
      >
        <Alert
          showIcon
          type="info"
          message="这是当前身份的非持久化布局样例"
          description="输入仅用于重新计算字段和条件显示，不会创建工作项、保存草稿、上传附件或发送协作事件。"
        />
        {projection ? (
          <div className="work-item-layout-sample-context" aria-label="服务端样例上下文">
            <Tag color="purple">{projection.context.role}</Tag>
            <Tag>{projection.context.spaceStatus}</Tag>
            <Tag>{projection.context.typeStatus}</Tag>
            <Typography.Text type="secondary">
              {projection.synthetic ? '服务端样例投影' : '运行时投影'}
            </Typography.Text>
          </div>
        ) : null}
        {loading && !projection ? <Skeleton active paragraph={{ rows: 5 }} /> : null}
        {failed ? (
          <Alert
            type="warning"
            showIcon
            message="布局样例暂不可用"
            description="布局可能尚未配置，或当前身份没有访问该类型的权限。"
            action={(
              <Button
                size="small"
                icon={<ReloadOutlined />}
                onClick={() => sampleMutation.mutate({ targetKind: kind, sampleValues: values })}
              >
                重试
              </Button>
            )}
          />
        ) : null}
        {hiddenValueKeys.length > 0 ? (
          <Alert
            type="warning"
            showIcon
            message="部分样本值当前被条件或权限隐藏"
            description="这些值仍保留在本地样本中，没有被删除或写入服务端。"
          />
        ) : null}
        {projection?.diagnostics.length ? (
          <Alert type="warning" showIcon message="部分布局内容暂不可用" />
        ) : null}
        {projection ? (
          <>
            <WorkItemLayoutRenderer
              layout={projection}
              fields={projection.fields}
              accessProjection={projection.accessProjection}
              values={values}
              onValuesChange={setValues}
              disclosure="minimal"
            />
            <div className="work-item-layout-sample-actions">
              <Button
                type="primary"
                icon={<ReloadOutlined />}
                loading={loading}
                onClick={() => sampleMutation.mutate({ targetKind: kind, sampleValues: values })}
              >
                重新计算样例
              </Button>
            </div>
          </>
        ) : null}
        {!loading && !failed && !projection ? (
          <Empty description="当前没有可展示的布局样例" />
        ) : null}
      </Card>
    </section>
  )
}

function hiddenSamples(values: WorkItemLayoutValues, projection?: WorkItemLayoutProjection) {
  if (!projection) return []
  const visible = new Set(projection.fields.map((field) => field.fieldKey))
  return Object.keys(values).filter((key) => !visible.has(key))
}
