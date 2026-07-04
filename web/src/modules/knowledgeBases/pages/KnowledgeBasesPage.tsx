import {
  BookOutlined,
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  FileTextOutlined,
  PlusOutlined,
  PoweroffOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App as AntdApp, Button, Empty, Form, Input, Modal, Select, Space, Switch, Tag, Tooltip, Typography } from 'antd'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import {
  archiveKnowledgeBase,
  createKnowledgeBase,
  disableKnowledgeBase,
  listKnowledgeBases,
  restoreKnowledgeBase,
  updateKnowledgeBase,
  type KnowledgeBaseSpaceRequest,
  type KnowledgeBaseSpaceSummary,
} from '../api/knowledgeBasesApi'

type SpaceForm = KnowledgeBaseSpaceRequest

const statusColor: Record<KnowledgeBaseSpaceSummary['status'], string> = {
  active: 'green',
  disabled: 'orange',
  archived: 'default',
}

export function KnowledgeBasesPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { message, modal } = AntdApp.useApp()
  const [includeArchived, setIncludeArchived] = useState(false)
  const [searchDraft, setSearchDraft] = useState('')
  const [editingSpace, setEditingSpace] = useState<KnowledgeBaseSpaceSummary | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [form] = Form.useForm<SpaceForm>()

  const spacesQuery = useQuery({
    queryKey: ['knowledge-bases', includeArchived],
    queryFn: () => listKnowledgeBases({ includeArchived }),
  })

  const filteredSpaces = useMemo(() => {
    const search = searchDraft.trim().toLowerCase()
    return (spacesQuery.data ?? []).filter((space) => {
      if (!search) {
        return true
      }
      return [space.name, space.code, space.description ?? '', space.ownerName]
        .some((value) => value.toLowerCase().includes(search))
    })
  }, [searchDraft, spacesQuery.data])

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['knowledge-bases'] })

  const createMutation = useMutation({
    mutationFn: (values: SpaceForm) => createKnowledgeBase(values),
    onSuccess: async (detail) => {
      setCreateOpen(false)
      form.resetFields()
      message.success('知识库已创建')
      await refresh()
      navigate(`/knowledge-bases/${detail.space.id}`)
    },
    onError: () => message.error('知识库创建失败'),
  })

  const updateMutation = useMutation({
    mutationFn: (values: SpaceForm) => updateKnowledgeBase(editingSpace?.id ?? '', values),
    onSuccess: async () => {
      setEditingSpace(null)
      form.resetFields()
      message.success('知识库设置已更新')
      await refresh()
    },
    onError: () => message.error('知识库设置更新失败'),
  })

  const disableMutation = useMutation({
    mutationFn: disableKnowledgeBase,
    onSuccess: async () => {
      message.success('知识库已停用')
      await refresh()
    },
    onError: () => message.error('知识库停用失败'),
  })

  const restoreMutation = useMutation({
    mutationFn: restoreKnowledgeBase,
    onSuccess: async () => {
      message.success('知识库已恢复')
      await refresh()
    },
    onError: () => message.error('知识库恢复失败'),
  })

  const archiveMutation = useMutation({
    mutationFn: archiveKnowledgeBase,
    onSuccess: async () => {
      message.success('知识库已归档')
      await refresh()
    },
    onError: () => message.error('知识库归档失败'),
  })

  const openCreate = () => {
    form.setFieldsValue({ visibility: 'private', defaultPermissionLevel: 'view' })
    setCreateOpen(true)
  }

  const openEdit = (space: KnowledgeBaseSpaceSummary) => {
    setEditingSpace(space)
    form.setFieldsValue({
      name: space.name,
      code: space.code,
      description: space.description ?? undefined,
      icon: space.icon ?? undefined,
      coverUrl: space.coverUrl ?? undefined,
      visibility: space.visibility,
      defaultPermissionLevel: space.defaultPermissionLevel,
      homeDocumentId: space.homeDocumentId,
    })
  }

  const confirmArchive = (space: KnowledgeBaseSpaceSummary) => {
    modal.confirm({
      title: `归档 ${space.name}`,
      content: '归档会同步归档知识库根内容和目录树，旧链接仍保留恢复路径。',
      okText: '归档',
      okButtonProps: { danger: true },
      onOk: () => archiveMutation.mutate(space.id),
    })
  }

  const submitForm = (values: SpaceForm) => {
    if (editingSpace) {
      updateMutation.mutate(values)
      return
    }
    createMutation.mutate(values)
  }

  return (
    <div className="kb-page">
      <div className="kb-toolbar">
        <div>
          <Typography.Title level={3}>知识库</Typography.Title>
          <Typography.Text type="secondary">空间、根目录和内容树共用同一套编辑、评论、版本与权限底座</Typography.Text>
        </div>
        <Space wrap>
          <Input.Search
            allowClear
            className="kb-search"
            placeholder="搜索名称、编号、维护人"
            value={searchDraft}
            onChange={(event) => setSearchDraft(event.target.value)}
          />
          <Switch checked={includeArchived} onChange={setIncludeArchived} checkedChildren="含归档" unCheckedChildren="仅有效" />
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建知识库
          </Button>
        </Space>
      </div>

      {filteredSpaces.length === 0 ? (
        <Empty className="kb-empty" image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无可见知识库" />
      ) : (
        <div className="kb-grid">
          {filteredSpaces.map((space) => (
            <article className="kb-card" key={space.id}>
              <div className="kb-card-cover" style={space.coverUrl ? { backgroundImage: `url(${space.coverUrl})` } : undefined}>
                {!space.coverUrl ? <BookOutlined /> : null}
              </div>
              <div className="kb-card-body">
                <Space className="kb-card-title" align="start">
                  <Typography.Title level={5}>{space.name}</Typography.Title>
                  <Tag color={statusColor[space.status]}>{statusText(space.status)}</Tag>
                </Space>
                <Typography.Paragraph ellipsis={{ rows: 2 }} type="secondary">
                  {space.description || '未填写描述'}
                </Typography.Paragraph>
                <Space wrap size={6} className="kb-card-meta">
                  <Tag>{space.code}</Tag>
                  <Tag>{visibilityText(space.visibility)}</Tag>
                  <Tag>{permissionText(space.defaultPermissionLevel)}</Tag>
                  <Tag>{space.documentCount} 个内容节点</Tag>
                </Space>
                <div className="kb-card-footer">
                  <Typography.Text type="secondary">维护人 {space.ownerName}</Typography.Text>
                  <Space size={4}>
                    <Tooltip title="进入知识库">
                      <Button icon={<EyeOutlined />} onClick={() => navigate(`/knowledge-bases/${space.id}`)} />
                    </Tooltip>
                    <Tooltip title="打开首页内容">
                      <Button icon={<FileTextOutlined />} onClick={() => navigate(`/knowledge-bases/${space.id}/items/${space.homeDocumentId}`)} />
                    </Tooltip>
                    <Tooltip title="编辑设置">
                      <Button icon={<EditOutlined />} onClick={() => openEdit(space)} />
                    </Tooltip>
                    {space.status === 'active' ? (
                      <Tooltip title="停用">
                        <Button icon={<PoweroffOutlined />} onClick={() => disableMutation.mutate(space.id)} />
                      </Tooltip>
                    ) : (
                      <Tooltip title="恢复">
                        <Button icon={<ReloadOutlined />} onClick={() => restoreMutation.mutate(space.id)} />
                      </Tooltip>
                    )}
                    <Tooltip title="归档">
                      <Button danger icon={<DeleteOutlined />} onClick={() => confirmArchive(space)} />
                    </Tooltip>
                  </Space>
                </div>
              </div>
            </article>
          ))}
        </div>
      )}

      <Modal
        title={editingSpace ? '编辑知识库' : '新建知识库'}
        open={createOpen || Boolean(editingSpace)}
        onCancel={() => {
          setCreateOpen(false)
          setEditingSpace(null)
          form.resetFields()
        }}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={submitForm}>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入知识库名称' }]}>
            <Input maxLength={255} />
          </Form.Item>
          <Form.Item name="code" label="编号">
            <Input maxLength={64} placeholder="自动按名称生成" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea maxLength={512} rows={3} />
          </Form.Item>
          <Space className="kb-form-row" align="start">
            <Form.Item name="icon" label="图标">
              <Input maxLength={64} placeholder="book" />
            </Form.Item>
            <Form.Item name="visibility" label="可见性">
              <Select
                options={[
                  { value: 'private', label: '私有' },
                  { value: 'workspace', label: '工作区' },
                ]}
              />
            </Form.Item>
            <Form.Item name="defaultPermissionLevel" label="默认权限">
              <Select
                options={[
                  { value: 'view', label: '可查看' },
                  { value: 'comment', label: '可评论' },
                  { value: 'edit', label: '可编辑' },
                ]}
              />
            </Form.Item>
          </Space>
          <Form.Item name="coverUrl" label="封面 URL">
            <Input maxLength={1024} />
          </Form.Item>
          {editingSpace ? (
            <Form.Item name="homeDocumentId" label="首页内容 ID">
              <Input />
            </Form.Item>
          ) : null}
        </Form>
      </Modal>
    </div>
  )
}

function statusText(status: KnowledgeBaseSpaceSummary['status']) {
  return { active: '有效', disabled: '停用', archived: '归档' }[status]
}

function visibilityText(visibility: KnowledgeBaseSpaceSummary['visibility']) {
  return { private: '私有', workspace: '工作区' }[visibility]
}

function permissionText(permission: KnowledgeBaseSpaceSummary['defaultPermissionLevel']) {
  return { view: '默认查看', comment: '默认评论', edit: '默认编辑' }[permission]
}
