import {
  CheckOutlined,
  FileDoneOutlined,
  PlusOutlined,
  ReloadOutlined,
  SendOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  App as AntdApp,
  Button,
  Card,
  Empty,
  Input,
  List,
  Select,
  Space,
  Tag,
  Typography,
} from 'antd'
import { useState } from 'react'

import {
  listProjectSpaceMembers,
  type UserProjectSpace,
} from '../api/projectSpacesApi'
import {
  createProjectDeliverable,
  getProjectDeliverable,
  listProjectDeliverables,
  mutateProjectDeliverable,
  projectDeliveryKeys,
  type ProjectDeliverable,
} from '../api/projectDeliveriesApi'
import { errorMessage, formatTime } from '../projectSpaceView'

export function ProjectDeliveryPanel({ space }: { space: UserProjectSpace }) {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const [selectedId, setSelectedId] = useState<string>()
  const [title, setTitle] = useState('')
  const [summary, setSummary] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [signerIds, setSignerIds] = useState<string[]>([])
  const writable = space.status === 'active' && space.currentUserRole !== 'guest'
  const listQuery = useQuery({
    queryKey: projectDeliveryKeys.list(space.id),
    queryFn: () => listProjectDeliverables(space.id),
  })
  const detailQuery = useQuery({
    queryKey: projectDeliveryKeys.detail(space.id, selectedId ?? 'none'),
    queryFn: () => getProjectDeliverable(space.id, selectedId!),
    enabled: Boolean(selectedId),
  })
  const membersQuery = useQuery({
    queryKey: ['project-space-members', space.id, 'delivery-review'],
    queryFn: () => listProjectSpaceMembers(space.id),
  })
  const refresh = async (value: ProjectDeliverable) => {
    setSelectedId(value.deliverable.id)
    await queryClient.invalidateQueries({ queryKey: projectDeliveryKeys.all })
  }
  const createMutation = useMutation({
    mutationFn: () => createProjectDeliverable(space.id, {
      title,
      summary,
      dueDate: dueDate || undefined,
    }),
    onSuccess: async (value) => {
      await refresh(value)
      setTitle('')
      setSummary('')
      message.success('交付物已创建')
    },
    onError: (error) => message.error(errorMessage(error, '交付物创建失败')),
  })
  const mutateMutation = useMutation({
    mutationFn: ({ current, operation, conclusion }: {
      current: ProjectDeliverable
      operation: string
      conclusion?: string
    }) => mutateProjectDeliverable(space.id, current, operation, {
      signerIds,
      conclusion,
      comment: operation === 'accept' ? '验收条件全部满足' : `Web ${operation}`,
    }),
    onSuccess: async (value) => {
      await refresh(value)
      message.success('交付评审状态已更新')
    },
    onError: (error) => message.error(errorMessage(error, '交付评审操作失败，请 REST 校准后重试')),
  })
  const current = detailQuery.data

  return (
    <Card
      className="content-card project-delivery-panel"
      data-testid="project-delivery-panel"
      title={<Space><FileDoneOutlined />交付物、评审、会签与验收</Space>}
      extra={<Tag color="cyan">S15 · 版本不可变</Tag>}
    >
      <div className="project-delivery-layout">
        <section className="project-delivery-create" aria-label="创建交付物">
          <Input
            aria-label="交付物标题"
            value={title}
            maxLength={160}
            placeholder="交付物标题"
            onChange={(event) => setTitle(event.target.value)}
          />
          <Input
            aria-label="交付物摘要"
            value={summary}
            maxLength={2000}
            placeholder="范围、材料和验收摘要"
            onChange={(event) => setSummary(event.target.value)}
          />
          <Input
            aria-label="交付物到期日期"
            type="date"
            value={dueDate}
            onChange={(event) => setDueDate(event.target.value)}
          />
          <Button
            type="primary"
            icon={<PlusOutlined />}
            disabled={!writable || !title.trim()}
            loading={createMutation.isPending}
            onClick={() => createMutation.mutate()}
          >
            创建交付物
          </Button>
          {!writable && <Typography.Text type="secondary">当前角色只读；结论由服务端判定。</Typography.Text>}
        </section>
        <section className="project-delivery-list" aria-label="交付物目录">
          <Button
            icon={<ReloadOutlined />}
            onClick={() => void listQuery.refetch()}
          >
            REST 校准
          </Button>
          <List
            loading={listQuery.isLoading}
            dataSource={listQuery.data ?? []}
            locale={{ emptyText: <Empty description="暂无交付物" /> }}
            renderItem={(value) => (
              <List.Item
                className={value.id === selectedId ? 'is-selected' : ''}
                onClick={() => setSelectedId(value.id)}
              >
                <List.Item.Meta
                  title={value.title}
                  description={`${value.status} · v${value.version}`}
                />
              </List.Item>
            )}
          />
        </section>
        <section className="project-delivery-detail" aria-label="交付评审详情">
          {!current && <Empty description="选择交付物查看版本、评审和验收" />}
          {current && (
            <Space direction="vertical" size="small" style={{ width: '100%' }}>
              <Space wrap>
                <Tag color="blue">{current.deliverable.status}</Tag>
                <Tag>聚合 v{current.deliverable.version}</Tag>
                <Tag>版本 {current.versions.length}</Tag>
                <Tag>评审轮次 {current.reviews.length}</Tag>
              </Space>
              <Typography.Title level={5}>{current.deliverable.title}</Typography.Title>
              <Typography.Paragraph>{current.deliverable.summary || '暂无摘要'}</Typography.Paragraph>
              {current.materialsTruncated && (
                <Typography.Text type="secondary">部分物料因当前权限隐藏。</Typography.Text>
              )}
              <List
                size="small"
                header="不可变版本"
                dataSource={current.versions}
                locale={{ emptyText: '尚未提交版本' }}
                renderItem={(version) => (
                  <List.Item>
                    {version.label} · 物料 {version.materials.length} · {formatTime(version.submittedAt)}
                  </List.Item>
                )}
              />
              <List
                size="small"
                header="评审与会签"
                dataSource={current.reviews}
                locale={{ emptyText: '尚未发起评审' }}
                renderItem={(review) => (
                  <List.Item>
                    轮次 {review.round} · {review.status} · 会签 {review.signoffs.length}/{review.quorum}
                  </List.Item>
                )}
              />
              <List
                size="small"
                header="验收结论"
                dataSource={current.acceptances}
                locale={{ emptyText: '尚无验收结论' }}
                renderItem={(acceptance) => (
                  <List.Item>{acceptance.conclusion} · {acceptance.comment}</List.Item>
                )}
              />
              {current.deliverable.status === 'draft' && (
                <Button
                  icon={<SendOutlined />}
                  disabled={!writable}
                  onClick={() => mutateMutation.mutate({
                    current, operation: 'submit_version',
                  })}
                >
                  提交不可变版本
                </Button>
              )}
              {current.deliverable.status === 'submitted' && (
                <>
                  <Select
                    mode="multiple"
                    aria-label="评审会签人"
                    placeholder="选择必签成员"
                    value={signerIds}
                    onChange={setSignerIds}
                    options={(membersQuery.data ?? []).filter((member) => member.effective)
                      .map((member) => ({
                        value: member.userId,
                        label: member.displayName,
                      }))}
                  />
                  <Button
                    disabled={!writable || signerIds.length === 0}
                    onClick={() => mutateMutation.mutate({
                      current, operation: 'open_review',
                    })}
                  >
                    发起评审
                  </Button>
                </>
              )}
              {current.deliverable.status === 'reviewing' && (
                <Space wrap>
                  <Button
                    icon={<CheckOutlined />}
                    disabled={!writable}
                    onClick={() => mutateMutation.mutate({
                      current, operation: 'sign', conclusion: 'approve',
                    })}
                  >
                    同意会签
                  </Button>
                  <Button
                    disabled={!writable}
                    onClick={() => mutateMutation.mutate({
                      current, operation: 'close_review',
                    })}
                  >
                    关闭评审
                  </Button>
                </Space>
              )}
              {current.deliverable.status === 'reviewed' && (
                <Button
                  type="primary"
                  disabled={!writable}
                  onClick={() => mutateMutation.mutate({
                    current, operation: 'accept',
                  })}
                >
                  记录验收通过
                </Button>
              )}
            </Space>
          )}
        </section>
      </div>
    </Card>
  )
}
