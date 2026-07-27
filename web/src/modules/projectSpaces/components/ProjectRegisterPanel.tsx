import {
  AlertOutlined,
  CheckCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
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

import type { UserProjectSpace } from '../api/projectSpacesApi'
import {
  createProjectRegisterEntry,
  getProjectRegisterEntry,
  listProjectRegister,
  mutateProjectRegisterEntry,
  projectRegisterKeys,
  type ProjectRegisterEntry,
  type ProjectRegisterType,
} from '../api/projectRegisterApi'
import { errorMessage, formatTime } from '../projectSpaceView'

const TYPE_LABEL: Record<ProjectRegisterType, string> = {
  risk: '风险',
  issue: '问题',
  decision: '决策',
  change: '变更',
}

function nextAction(current: ProjectRegisterEntry) {
  const { entryType, status } = current.entry
  if (entryType === 'risk') {
    if (status === 'identified') return ['assess', '完成评估']
    if (status === 'assessed') return ['monitor', '进入监控']
    if (status === 'closed') return ['reopen', '重新打开']
    return ['close', '关闭风险']
  }
  if (entryType === 'issue') {
    if (status === 'open') return ['escalate', '升级问题']
    if (status === 'escalated') return ['resolve', '标记解决']
    if (status === 'resolved') return ['verify', '验证完成']
    return ['reopen', '重新打开']
  }
  if (entryType === 'decision') {
    return status === 'proposed' ? ['adopt', '采纳决策'] : ['revoke', '撤销决策']
  }
  return status === 'proposed' ? ['analyze', '完成影响分析'] : ['reject', '拒绝变更']
}

export function ProjectRegisterPanel({ space }: { space: UserProjectSpace }) {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const [filter, setFilter] = useState('all')
  const [entryType, setEntryType] = useState<ProjectRegisterType>('risk')
  const [selectedId, setSelectedId] = useState<string>()
  const [title, setTitle] = useState('')
  const [summary, setSummary] = useState('')
  const [dueDate, setDueDate] = useState('')
  const writable = space.status === 'active' && space.currentUserRole !== 'guest'
  const listQuery = useQuery({
    queryKey: projectRegisterKeys.list(space.id, filter),
    queryFn: () => listProjectRegister(space.id, filter),
  })
  const detailQuery = useQuery({
    queryKey: projectRegisterKeys.detail(space.id, selectedId ?? 'none'),
    queryFn: () => getProjectRegisterEntry(space.id, selectedId!),
    enabled: Boolean(selectedId),
  })
  const refresh = async (entry: ProjectRegisterEntry) => {
    setSelectedId(entry.entry.id)
    await queryClient.invalidateQueries({ queryKey: projectRegisterKeys.all })
  }
  const createMutation = useMutation({
    mutationFn: () => createProjectRegisterEntry(space.id, {
      entryType,
      title,
      summary,
      dueDate: dueDate || undefined,
    }),
    onSuccess: async (entry) => {
      await refresh(entry)
      setTitle('')
      setSummary('')
      message.success('项目台账条目已创建')
    },
    onError: (error) => message.error(errorMessage(error, '台账条目创建失败')),
  })
  const mutateMutation = useMutation({
    mutationFn: ({ entry, operation }: {
      entry: ProjectRegisterEntry
      operation: string
    }) => mutateProjectRegisterEntry(
      space.id,
      entry,
      operation,
      operation === 'update' ? '成员更新' : `状态操作：${operation}`,
    ),
    onSuccess: async (entry) => {
      await refresh(entry)
      message.success('台账状态已更新')
    },
    onError: (error) => message.error(errorMessage(error, '台账状态更新失败，请刷新后重试')),
  })
  const current = detailQuery.data
  const action = current ? nextAction(current) : undefined

  return (
    <Card
      className="content-card project-register-panel"
      data-testid="project-register-panel"
      title={<Space><AlertOutlined />风险、问题、决策与变更台账</Space>}
      extra={<Tag color="purple">S15 · 当前权限校准</Tag>}
    >
      <div className="project-register-layout">
        <section className="project-register-create" aria-label="创建台账条目">
          <Select
            aria-label="台账类型"
            value={entryType}
            onChange={setEntryType}
            options={Object.entries(TYPE_LABEL).map(([value, label]) => ({ value, label }))}
          />
          <Input
            aria-label="台账标题"
            value={title}
            maxLength={160}
            placeholder="风险、问题、决策或变更标题"
            onChange={(event) => setTitle(event.target.value)}
          />
          <Input
            aria-label="台账摘要"
            value={summary}
            maxLength={2000}
            placeholder="依据、影响或处置摘要"
            onChange={(event) => setSummary(event.target.value)}
          />
          <Input
            aria-label="台账到期日期"
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
            创建条目
          </Button>
          {!writable && <Typography.Text type="secondary">当前角色只读；权限由服务端判定。</Typography.Text>}
        </section>
        <section className="project-register-list" aria-label="台账列表">
          <Space wrap>
            <Select
              aria-label="台账筛选"
              value={filter}
              onChange={setFilter}
              options={[
                { value: 'all', label: '全部类型' },
                ...Object.entries(TYPE_LABEL).map(([value, label]) => ({ value, label })),
              ]}
            />
            <Button
              icon={<ReloadOutlined />}
              onClick={() => void listQuery.refetch()}
            >
              REST 校准
            </Button>
          </Space>
          <List
            loading={listQuery.isLoading}
            dataSource={listQuery.data ?? []}
            locale={{ emptyText: <Empty description="暂无台账条目" /> }}
            renderItem={(entry) => (
              <List.Item
                className={entry.id === selectedId ? 'is-selected' : ''}
                onClick={() => setSelectedId(entry.id)}
              >
                <List.Item.Meta
                  title={<Space wrap><Tag>{TYPE_LABEL[entry.entryType]}</Tag>{entry.title}</Space>}
                  description={`${entry.status}${entry.entryType === 'risk' ? ` · 风险分 ${entry.score}` : ''}`}
                />
              </List.Item>
            )}
          />
        </section>
        <section className="project-register-detail" aria-label="台账详情">
          {!current && <Empty description="选择一个台账条目查看责任、响应和历史" />}
          {current && (
            <Space direction="vertical" size="small" style={{ width: '100%' }}>
              <Space wrap>
                <Tag color="blue">{TYPE_LABEL[current.entry.entryType]}</Tag>
                <Tag color="gold">{current.entry.status}</Tag>
                <Tag>v{current.entry.version}</Tag>
                {current.entry.entryType === 'risk' && (
                  <Tag color={current.entry.score >= 15 ? 'red' : 'orange'}>
                    P{current.entry.probability} × I{current.entry.impact} = {current.entry.score}
                  </Tag>
                )}
              </Space>
              <Typography.Title level={5}>{current.entry.title}</Typography.Title>
              <Typography.Paragraph>{current.entry.summary || '暂无摘要'}</Typography.Paragraph>
              <Typography.Text type="secondary">
                响应 {current.responses.length} · 当前可见引用 {current.references.length}
                {current.referencesTruncated ? ' · 部分引用因当前权限隐藏' : ''}
              </Typography.Text>
              <List
                size="small"
                header="响应计划"
                dataSource={current.responses}
                locale={{ emptyText: '无响应计划' }}
                renderItem={(response) => (
                  <List.Item>
                    <CheckCircleOutlined /> {response.description} · {response.status}
                  </List.Item>
                )}
              />
              <List
                size="small"
                header="不可变历史"
                dataSource={current.history}
                locale={{ emptyText: '暂无历史' }}
                renderItem={(history) => (
                  <List.Item>
                    {history.operation}: {history.fromStatus || 'new'} → {history.toStatus}
                    {' · '}{formatTime(history.occurredAt)}
                  </List.Item>
                )}
              />
              {action && (
                <Button
                  disabled={!writable}
                  loading={mutateMutation.isPending}
                  onClick={() => mutateMutation.mutate({
                    entry: current,
                    operation: action[0],
                  })}
                >
                  {action[1]}
                </Button>
              )}
            </Space>
          )}
        </section>
      </div>
    </Card>
  )
}
