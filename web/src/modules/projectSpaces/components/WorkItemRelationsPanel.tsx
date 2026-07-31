import {
  ApartmentOutlined,
  BranchesOutlined,
  DeleteOutlined,
  DeploymentUnitOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SwapOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Collapse,
  Empty,
  Input,
  List,
  Select,
  Space,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import { useMemo, useState } from 'react'

import type { UserProjectSpace } from '../api/projectSpacesApi'
import {
  createRelation,
  getHierarchyNavigation,
  getRelationImpact,
  getRelationSummary,
  mutateRelationMigration,
  planRelationMigration,
  reparentWorkItem,
  searchRelationTargets,
  splitWorkItemChild,
  withdrawRelation,
  workItemRelationKeys,
  type RelationMigrationState,
  type WorkItemRelation,
} from '../api/workItemRelationsApi'
import { listWorkItems, type WorkItem } from '../api/workItemsApi'
import { listActiveWorkItemTypes, workItemTypeKeys } from '../api/workItemTypesApi'
import { errorMessage } from '../projectSpaceView'

type RelationDefinition = {
  relationKey: string
  kind: 'normal' | 'parent_child' | 'dependency' | 'blocking'
  forwardName: string
  reverseName: string
}

export function WorkItemRelationsPanel({
  space,
  item,
  online,
  refreshItem,
}: {
  space: UserProjectSpace
  item: WorkItem
  online: boolean
  refreshItem: () => Promise<void>
}) {
  const definitions = useMemo(
    () => relationDefinitions(item.runtime.snapshot.relationDefinitions),
    [item.runtime.snapshot.relationDefinitions],
  )
  if (!definitions.length) return null
  return (
    <Card className="content-card work-item-relations-panel" title={<Space><BranchesOutlined />关系与层级</Space>}>
      <Tabs
        items={[
          {
            key: 'relations',
            label: '关系',
            children: <RelationsTab space={space} item={item} online={online} definitions={definitions} refreshItem={refreshItem} />,
          },
          {
            key: 'hierarchy',
            label: '局部层级',
            children: <HierarchyTab space={space} item={item} online={online} definitions={definitions} refreshItem={refreshItem} />,
          },
          {
            key: 'impact',
            label: '影响分析',
            children: <ImpactTab space={space} item={item} definitions={definitions} />,
          },
          ...(isManager(space) ? [{
            key: 'migration',
            label: 'Legacy 承接',
            children: <MigrationTab space={space} definitions={definitions} />,
          }] : []),
        ]}
      />
    </Card>
  )
}

function RelationsTab({
  space,
  item,
  online,
  definitions,
  refreshItem,
}: {
  space: UserProjectSpace
  item: WorkItem
  online: boolean
  definitions: RelationDefinition[]
  refreshItem: () => Promise<void>
}) {
  const { message, modal } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const [relationKey, setRelationKey] = useState(definitions[0].relationKey)
  const [search, setSearch] = useState('')
  const [targetId, setTargetId] = useState<string>()
  const summary = useQuery({
    queryKey: workItemRelationKeys.summary(space.id, item.id),
    queryFn: () => getRelationSummary(space.id, item.id),
    retry: false,
  })
  const targets = useQuery({
    queryKey: workItemRelationKeys.targets(space.id, item.id, relationKey, search),
    queryFn: () => searchRelationTargets(space.id, item.id, relationKey, search),
    enabled: online && item.availableActions.includes('edit') && Boolean(relationKey),
    retry: false,
  })
  const selected = targets.data?.items.find((candidate) => candidate.id === targetId)
  const invalidate = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: workItemRelationKeys.summary(space.id, item.id) }),
      refreshItem(),
    ])
  }
  const createMutation = useMutation({
    mutationFn: () => {
      if (!selected) throw new Error('请选择目标工作项')
      return createRelation(space.id, {
        relationKey,
        sourceWorkItemId: item.id,
        targetWorkItemId: selected.id,
        expectedSourceVersion: item.version,
        expectedTargetVersion: selected.version,
      })
    },
    onSuccess: async () => {
      await invalidate()
      setTargetId(undefined)
      message.success('关系已创建')
    },
    onError: async (error) => {
      await summary.refetch()
      message.error(errorMessage(error, '关系创建失败；目标选择已保留，请按最新事实重试'))
    },
  })
  const withdrawMutation = useMutation({
    mutationFn: ({ relation, reason }: { relation: WorkItemRelation; reason: string }) =>
      withdrawRelation(space.id, relation, reason),
    onSuccess: async () => {
      await invalidate()
      message.success('关系已撤销')
    },
    onError: async (error) => {
      await summary.refetch()
      message.error(errorMessage(error, '撤销失败；原因已保留，请刷新后重试'))
    },
  })
  const confirmWithdraw = (relation: WorkItemRelation) => {
    let reason = ''
    modal.confirm({
      title: `撤销“${relation.displayName}”关系？`,
      content: (
        <Input.TextArea
          aria-label="撤销关系原因"
          autoFocus
          placeholder="输入审计原因（至少 3 个字符）"
          onChange={(event) => { reason = event.target.value }}
        />
      ),
      okText: '确认撤销',
      okButtonProps: { danger: true },
      onOk: async () => {
        if (reason.trim().length < 3) throw new Error('请输入至少 3 个字符的原因')
        await withdrawMutation.mutateAsync({ relation, reason: reason.trim() })
      },
    })
  }
  return (
    <div className="work-item-relation-tab">
      {item.availableActions.includes('edit') ? (
        <div className="work-item-relation-compose">
          <Select
            aria-label="关系定义"
            value={relationKey}
            options={definitions.map((definition) => ({
              value: definition.relationKey,
              label: `${definition.forwardName} · ${definition.relationKey}`,
            }))}
            onChange={(value) => {
              setRelationKey(value)
              setTargetId(undefined)
            }}
          />
          <Select
            showSearch
            filterOption={false}
            aria-label="关系目标"
            value={targetId}
            placeholder="搜索有权访问的目标"
            loading={targets.isFetching}
            options={targets.data?.items.map((candidate) => ({
              value: candidate.id,
              label: `${candidate.displayKey} · ${candidate.title}`,
            }))}
            onSearch={setSearch}
            onChange={setTargetId}
            notFoundContent={search ? '没有匹配且可见的目标' : '输入标题或编号搜索'}
          />
          <Button
            type="primary"
            icon={<PlusOutlined />}
            disabled={!online || !selected}
            loading={createMutation.isPending}
            onClick={() => createMutation.mutate()}
          >
            建立关系
          </Button>
        </div>
      ) : null}
      {summary.isError ? (
        <Alert type="error" showIcon message="关系加载失败" action={<Button icon={<ReloadOutlined />} onClick={() => summary.refetch()}>重试</Button>} />
      ) : null}
      <List
        loading={summary.isLoading}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无关系" /> }}
        dataSource={summary.data?.items ?? []}
        renderItem={(relation) => {
          const peer = relation.perspective === 'source' ? relation.target : relation.source
          return (
            <List.Item
              actions={relation.availableActions.includes('withdraw') ? [
                <Button
                  key="withdraw"
                  danger
                  type="text"
                  icon={<DeleteOutlined />}
                  onClick={() => confirmWithdraw(relation)}
                >
                  撤销
                </Button>,
              ] : undefined}
            >
              <List.Item.Meta
                title={<Space wrap><Tag color={relation.reverse ? 'gold' : 'purple'}>{relation.displayName}</Tag><strong>{peer.title}</strong></Space>}
                description={<Space wrap><Typography.Text code>{peer.displayKey}</Typography.Text><Tag>{peer.typeKey}</Tag><Tag>{peer.status}</Tag></Space>}
              />
            </List.Item>
          )
        }}
      />
      {summary.data?.truncated ? <Alert type="info" showIcon message="关系数量达到展示硬限，请按 relation key 缩小范围" /> : null}
    </div>
  )
}

function HierarchyTab({
  space,
  item,
  online,
  definitions,
  refreshItem,
}: {
  space: UserProjectSpace
  item: WorkItem
  online: boolean
  definitions: RelationDefinition[]
  refreshItem: () => Promise<void>
}) {
  const { message, modal } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const hierarchyDefinitions = definitions.filter((definition) => definition.kind === 'parent_child')
  const [relationKey, setRelationKey] = useState(hierarchyDefinitions[0]?.relationKey ?? '')
  const [collapsed, setCollapsed] = useState<string[]>([])
  const navigation = useQuery({
    queryKey: workItemRelationKeys.hierarchy(space.id, item.id, relationKey),
    queryFn: () => getHierarchyNavigation(space.id, item.id, relationKey),
    enabled: Boolean(relationKey),
    retry: false,
  })
  const items = useQuery({
    queryKey: ['project-spaces', space.id, 'work-items', 'reparent-candidates'],
    queryFn: () => listWorkItems(space.id),
  })
  const types = useQuery({
    queryKey: workItemTypeKeys.active(space.id),
    queryFn: () => listActiveWorkItemTypes(space.id),
  })
  const canMutateHierarchy = online
    && space.status === 'active'
    && item.availableActions.includes('edit')
  const refresh = async () => {
    await Promise.all([
      navigation.refetch(),
      queryClient.invalidateQueries({ queryKey: workItemRelationKeys.summary(space.id, item.id) }),
      refreshItem(),
    ])
  }
  const reparentMutation = useMutation({
    mutationFn: (newParent: WorkItem) => {
      const parent = navigation.data?.parent
      if (!parent?.directRelationId) throw new Error('当前工作项没有可替换的父关系')
      return reparentWorkItem(space.id, {
        currentRelationId: parent.directRelationId,
        newParentWorkItemId: newParent.id,
        expectedRelationVersion: 0,
        expectedCurrentParentVersion: parent.version,
        expectedNewParentVersion: newParent.version,
        expectedChildVersion: item.version,
        reason: '成员在局部层级中调整父项',
        confirmation: 'REPARENT',
      })
    },
    onSuccess: async () => {
      await refresh()
      message.success('父项已调整')
    },
    onError: (error) => message.error(errorMessage(error, '调整父项失败，请刷新关系版本后重试')),
  })
  const splitMutation = useMutation({
    mutationFn: ({ childTypeId, title }: { childTypeId: string; title: string }) =>
      splitWorkItemChild(space.id, {
        parentWorkItemId: item.id,
        relationKey,
        childTypeId,
        childTitle: title,
        childFieldValues: {},
        inheritFieldKeys: [],
        expectedParentVersion: item.version,
      }),
    onSuccess: async () => {
      await refresh()
      message.success('子工作项已拆解并建立关系')
    },
    onError: (error) => message.error(errorMessage(error, '拆解失败，服务端未创建半成品')),
  })
  const chooseParent = () => {
    let selectedId = ''
    modal.confirm({
      title: '调整父项',
      content: (
        <Select
          showSearch
          aria-label="新父项"
          style={{ width: '100%' }}
          options={items.data?.items.filter((candidate) => candidate.id !== item.id).map((candidate) => ({
            value: candidate.id,
            label: `${candidate.displayKey} · ${candidate.title}`,
          }))}
          onChange={(value) => { selectedId = value }}
        />
      ),
      okText: '确认 reparent',
      okButtonProps: { danger: true },
      onOk: async () => {
        const selected = items.data?.items.find((candidate) => candidate.id === selectedId)
        if (!selected) throw new Error('请选择新父项')
        await reparentMutation.mutateAsync(selected)
      },
    })
  }
  const splitChild = () => {
    const creatableTypes = types.data?.filter((type) => type.configurationReady) ?? []
    let childTypeId = creatableTypes[0]?.id ?? ''
    let title = ''
    modal.confirm({
      title: '拆解子工作项',
      content: (
        <Space direction="vertical" style={{ width: '100%' }}>
          <Select
            aria-label="子工作项类型"
            defaultValue={childTypeId}
            options={creatableTypes.map((type) => ({ value: type.id, label: type.name }))}
            onChange={(value) => { childTypeId = value }}
          />
          <Input aria-label="子工作项标题" placeholder="输入子工作项标题" onChange={(event) => { title = event.target.value }} />
        </Space>
      ),
      okText: '创建并绑定',
      onOk: async () => {
        if (!childTypeId || !title.trim()) throw new Error('请选择类型并输入标题')
        await splitMutation.mutateAsync({ childTypeId, title: title.trim() })
      },
    })
  }
  if (!hierarchyDefinitions.length) return <Empty description="当前发布版本没有父子关系定义" />
  return (
    <div className="work-item-hierarchy-tab">
      <Space wrap>
        <Select value={relationKey} options={hierarchyDefinitions.map((definition) => ({ value: definition.relationKey, label: definition.forwardName }))} onChange={setRelationKey} />
        <Button icon={<SwapOutlined />} disabled={!canMutateHierarchy || !navigation.data?.parent} onClick={chooseParent}>调整父项</Button>
        <Button
          type="primary"
          icon={<ApartmentOutlined />}
          disabled={!canMutateHierarchy || !types.data?.some((type) => type.configurationReady)}
          onClick={splitChild}
        >
          拆解子项
        </Button>
      </Space>
      {navigation.isError ? <Alert type="error" showIcon message="局部层级不可用" description="不会退化为全局树或猜测祖先；请刷新后重试。" /> : null}
      {navigation.data ? (
        <>
          <div className="work-item-hierarchy-breadcrumbs" aria-label="层级面包屑">
            {[...navigation.data.breadcrumbs, navigation.data.focus].map((node) => <Tag key={node.id}>{node.displayKey}</Tag>)}
          </div>
          <Collapse
            activeKey={collapsed}
            onChange={(keys) => setCollapsed(keys as string[])}
            items={[{
              key: 'local-tree',
              label: `局部树（${navigation.data.localTree.length}）`,
              children: (
                <List
                  size="small"
                  dataSource={navigation.data.localTree}
                  locale={{ emptyText: '没有子层级' }}
                  renderItem={(node) => (
                    <List.Item>
                      <span style={{ paddingInlineStart: Math.min(node.depth, 8) * 16 }}>
                        <Typography.Text code>{node.displayKey}</Typography.Text> {node.title}
                      </span>
                    </List.Item>
                  )}
                />
              ),
            }, {
              key: 'list-fallback',
              label: '替代列表（键盘可导航）',
              children: (
                <List
                  size="small"
                  dataSource={[...navigation.data.children, ...navigation.data.siblings]}
                  renderItem={(node) => <List.Item tabIndex={0}>{node.displayKey} · {node.title}</List.Item>}
                />
              ),
            }]}
          />
          {navigation.data.truncated ? <Alert type="info" showIcon message={`局部树已截断：${navigation.data.degradationReason}`} /> : null}
        </>
      ) : null}
    </div>
  )
}

function ImpactTab({
  space,
  item,
  definitions,
}: {
  space: UserProjectSpace
  item: WorkItem
  definitions: RelationDefinition[]
}) {
  const impactDefinitions = definitions.filter((definition) =>
    ['dependency', 'blocking'].includes(definition.kind))
  const [relationKey, setRelationKey] = useState(impactDefinitions[0]?.relationKey ?? '')
  const [direction, setDirection] = useState<'upstream' | 'downstream'>('downstream')
  const impact = useQuery({
    queryKey: workItemRelationKeys.impact(space.id, item.id, relationKey, direction),
    queryFn: () => getRelationImpact(space.id, item.id, relationKey, direction),
    enabled: Boolean(relationKey),
    retry: false,
  })
  if (!impactDefinitions.length) return <Empty description="当前发布版本没有依赖或阻塞关系定义" />
  const nodes = new Map(impact.data?.nodes.map((node) => [node.id, node]))
  return (
    <div className="work-item-impact-tab">
      <Space wrap>
        <Select value={relationKey} options={impactDefinitions.map((definition) => ({ value: definition.relationKey, label: definition.forwardName }))} onChange={setRelationKey} />
        <Select value={direction} options={[{ value: 'upstream', label: '上游' }, { value: 'downstream', label: '下游' }]} onChange={setDirection} />
        <Button icon={<ReloadOutlined />} loading={impact.isFetching} onClick={() => impact.refetch()}>校准</Button>
      </Space>
      {impact.isError ? <Alert type="error" showIcon message="影响分析不可用" description="不会展示缓存推断或未受权节点。" /> : null}
      <List
        loading={impact.isLoading}
        locale={{ emptyText: '没有可见影响路径' }}
        dataSource={impact.data?.links ?? []}
        renderItem={(link) => {
          const source = nodes.get(link.sourceWorkItemId)
          const target = nodes.get(link.targetWorkItemId)
          return (
            <List.Item>
              <Space wrap>
                <Tag>深度 {link.depth}</Tag>
                <Typography.Text>{source?.displayKey ?? '受限节点'}</Typography.Text>
                <DeploymentUnitOutlined />
                <Typography.Text>{target?.displayKey ?? '受限节点'}</Typography.Text>
              </Space>
            </List.Item>
          )
        }}
      />
      {impact.data?.truncated ? <Alert type="warning" showIcon message="影响图达到节点或深度硬限" description="此结果不代表关键路径、工期或自动流转结论。" /> : null}
    </div>
  )
}

function MigrationTab({
  space,
  definitions,
}: {
  space: UserProjectSpace
  definitions: RelationDefinition[]
}) {
  const { message, modal } = AntdApp.useApp()
  const [relationKey, setRelationKey] = useState(definitions[0].relationKey)
  const [reason, setReason] = useState('承接同空间 legacy issue relation')
  const [state, setState] = useState<RelationMigrationState>()
  const planMutation = useMutation({
    mutationFn: (dryRun: boolean) => planRelationMigration(space.id, { relationKey, dryRun, reason }),
    onSuccess: setState,
    onError: (error) => message.error(errorMessage(error, '迁移 plan 失败')),
  })
  const actionMutation = useMutation({
    mutationFn: (action: 'execute' | 'resume' | 'verify' | 'rollback') => {
      if (!state) throw new Error('请先创建 plan')
      return mutateRelationMigration(space.id, state, action, reason)
    },
    onSuccess: setState,
    onError: (error) => message.error(errorMessage(error, '迁移操作失败，请按最新 batch version 重试')),
  })
  const dangerous = (action: 'execute' | 'resume' | 'rollback') => {
    modal.confirm({
      title: action === 'rollback' ? '回退已迁移的规范关系？' : '执行 legacy relation 承接？',
      content: action === 'rollback'
        ? '只撤回仍未被后续改变的迁移关系；冲突会失败关闭。精确确认词由客户端提交为 ROLLBACK_RELATIONS。'
        : '只有 canonical_work_item 分类会创建规范边；其他目标保持原语义。精确确认词由客户端提交为 MIGRATE_RELATIONS。',
      okText: action === 'rollback' ? '危险：确认回退' : '确认执行',
      okButtonProps: { danger: true },
      onOk: () => actionMutation.mutateAsync(action),
    })
  }
  return (
    <div className="work-item-relation-migration-tab">
      <Alert type="info" showIcon icon={<SafetyCertificateOutlined />} message="仅空间 owner/admin 可见" description="企业管理员若不是空间成员不会获得内容访问；非 WorkItem 目标必须保留，不能静默丢弃。" />
      <Space wrap className="work-item-relation-migration-controls">
        <Select value={relationKey} options={definitions.map((definition) => ({ value: definition.relationKey, label: definition.forwardName }))} onChange={setRelationKey} />
        <Input value={reason} aria-label="迁移原因" placeholder="输入审计原因" onChange={(event) => setReason(event.target.value)} />
        <Button onClick={() => planMutation.mutate(true)} loading={planMutation.isPending}>Dry-run plan</Button>
        <Button type="primary" onClick={() => planMutation.mutate(false)} loading={planMutation.isPending}>正式 plan</Button>
      </Space>
      {state ? (
        <>
          <Space wrap>
            <Tag color="purple">{state.batch.status}</Tag>
            <Tag>v{state.batch.version}</Tag>
            <Tag>总计 {state.batch.totalCount}</Tag>
            <Tag color="success">可迁移 {state.batch.canonicalCount}</Tag>
            <Tag color="gold">保留 {state.batch.preservedCount}</Tag>
            <Tag color={state.batch.failedCount ? 'error' : 'default'}>失败 {state.batch.failedCount}</Tag>
            <Button disabled={state.batch.dryRun} onClick={() => dangerous('execute')}>执行</Button>
            <Button disabled={!state.batch.failedCount} onClick={() => dangerous('resume')}>续跑</Button>
            <Button onClick={() => actionMutation.mutate('verify')}>Verify</Button>
            <Button danger onClick={() => dangerous('rollback')}>Rollback</Button>
          </Space>
          <Typography.Text type="secondary">manifest {state.batch.manifestHash.slice(0, 16)}…</Typography.Text>
          <List
            size="small"
            dataSource={state.units}
            locale={{ emptyText: 'manifest 中没有 legacy relation' }}
            renderItem={(unit) => (
              <List.Item>
                <Space wrap>
                  <Tag>{unit.classification}</Tag>
                  <Tag color={unit.status === 'failed' ? 'error' : unit.status === 'preserved' ? 'gold' : 'default'}>{unit.status}</Tag>
                  <Typography.Text code>{unit.sourceRelationId}</Typography.Text>
                  {unit.errorCode ? <Typography.Text type="danger">{unit.errorCode}</Typography.Text> : null}
                </Space>
              </List.Item>
            )}
          />
        </>
      ) : null}
    </div>
  )
}

function relationDefinitions(value: unknown): RelationDefinition[] {
  if (!Array.isArray(value)) return []
  return value.flatMap((item) => {
    if (!item || typeof item !== 'object' || Array.isArray(item)) return []
    const definition = item as Record<string, unknown>
    const relationKey = String(definition.relationKey ?? '')
    const kind = String(definition.kind ?? '')
    if (!relationKey || !['normal', 'parent_child', 'dependency', 'blocking'].includes(kind)) return []
    return [{
      relationKey,
      kind: kind as RelationDefinition['kind'],
      forwardName: String(definition.forwardName ?? relationKey),
      reverseName: String(definition.reverseName ?? relationKey),
    }]
  })
}

function isManager(space: UserProjectSpace) {
  return space.currentUserRole === 'owner' || space.currentUserRole === 'admin'
}
