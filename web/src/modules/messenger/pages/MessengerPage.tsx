import {
  BugOutlined,
  EditOutlined,
  PlusOutlined,
  PushpinOutlined,
  SendOutlined,
  SmileOutlined,
  StopOutlined,
  TeamOutlined,
  WifiOutlined,
  DisconnectOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  App as AntdApp,
  Avatar,
  Badge,
  Button,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Tag,
  Typography,
} from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { useAuthStore } from '../../auth/authStore'
import { ObjectSummaryCard } from '../../platform/components/InternalLinkCard'
import {
  createConversation,
  editMessage,
  getConversation,
  listConversations,
  listDirectoryMembers,
  listMessages,
  markConversationRead,
  pinMessage,
  revokeMessage,
  sendMessage,
  toggleReaction,
  type ConversationSummary,
  type MessageSummary,
} from '../api/messengerApi'
import { useWebSocketConnection } from '../../../shared/websocket/useWebSocketConnection'
import type { PlatformWebSocketEvent } from '../../../shared/websocket/websocketEvents'
import { createIssue, listProjects } from '../../projects/api/projectsApi'

type CreateConversationForm = {
  conversationType: 'direct' | 'group'
  title?: string
  memberIds: string[]
}

type ConvertMessageForm = {
  projectId: string
  issueType: 'task' | 'bug'
  title: string
  description?: string
}

export function MessengerPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [draft, setDraft] = useState('')
  const [createOpen, setCreateOpen] = useState(false)
  const [convertMessage, setConvertMessage] = useState<MessageSummary | null>(null)
  const [editingMessage, setEditingMessage] = useState<MessageSummary | null>(null)
  const [editDraft, setEditDraft] = useState('')
  const [form] = Form.useForm<CreateConversationForm>()
  const [convertForm] = Form.useForm<ConvertMessageForm>()
  const queryClient = useQueryClient()
  const currentUser = useAuthStore((state) => state.currentUser)
  const { message } = AntdApp.useApp()

  const selectedConversationId = searchParams.get('conversationId')

  const conversationsQuery = useQuery({
    queryKey: ['im', 'conversations'],
    queryFn: listConversations,
  })

  const directoryQuery = useQuery({
    queryKey: ['members', 'directory'],
    queryFn: listDirectoryMembers,
  })
  const projectsQuery = useQuery({
    queryKey: ['projects'],
    queryFn: listProjects,
  })

  const conversationQuery = useQuery({
    queryKey: ['im', 'conversation', selectedConversationId],
    queryFn: () => getConversation(selectedConversationId || ''),
    enabled: Boolean(selectedConversationId),
  })

  const messagesQuery = useQuery({
    queryKey: ['im', 'messages', selectedConversationId],
    queryFn: () => listMessages(selectedConversationId || ''),
    enabled: Boolean(selectedConversationId),
  })

  const refreshIm = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['im', 'conversations'] }),
      selectedConversationId
        ? queryClient.invalidateQueries({ queryKey: ['im', 'conversation', selectedConversationId] })
        : Promise.resolve(),
      selectedConversationId
        ? queryClient.invalidateQueries({ queryKey: ['im', 'messages', selectedConversationId] })
        : Promise.resolve(),
    ])
  }

  const wsStatus = useWebSocketConnection((event: PlatformWebSocketEvent) => {
    if (['message.created', 'conversation.updated', 'conversation.read', 'unread.changed'].includes(event.type)) {
      void refreshIm()
    }
  })

  const createMutation = useMutation({
    mutationFn: createConversation,
    onSuccess: async (conversation) => {
      setCreateOpen(false)
      form.resetFields()
      setSearchParams({ conversationId: conversation.id })
      await refreshIm()
    },
  })

  const sendMutation = useMutation({
    mutationFn: () => sendMessage(selectedConversationId || '', draft),
    onSuccess: async () => {
      setDraft('')
      await refreshIm()
    },
  })

  const convertMutation = useMutation({
    mutationFn: (values: ConvertMessageForm) =>
      createIssue(values.projectId, {
        issueType: values.issueType,
        title: values.title,
        description: values.description,
        priority: 'medium',
      }),
    onSuccess: async () => {
      setConvertMessage(null)
      convertForm.resetFields()
      await refreshIm()
    },
  })

  const readMutation = useMutation({
    mutationFn: (messageId?: string) => markConversationRead(selectedConversationId || '', messageId),
    onSuccess: refreshIm,
  })

  const editMutation = useMutation({
    mutationFn: () => editMessage(selectedConversationId || '', editingMessage?.id || '', editDraft),
    onSuccess: async () => {
      setEditingMessage(null)
      setEditDraft('')
      await refreshIm()
    },
  })

  const revokeMutation = useMutation({
    mutationFn: (messageId: string) => revokeMessage(selectedConversationId || '', messageId),
    onSuccess: refreshIm,
  })

  const pinMutation = useMutation({
    mutationFn: ({ messageId, pinned }: { messageId: string; pinned: boolean }) =>
      pinMessage(selectedConversationId || '', messageId, pinned),
    onSuccess: refreshIm,
  })

  const reactionMutation = useMutation({
    mutationFn: ({ messageId, emoji }: { messageId: string; emoji: string }) =>
      toggleReaction(selectedConversationId || '', messageId, emoji),
    onSuccess: refreshIm,
  })

  useEffect(() => {
    const conversations = conversationsQuery.data ?? []
    if (!selectedConversationId && conversations.length > 0) {
      setSearchParams({ conversationId: conversations[0].id })
    }
  }, [conversationsQuery.data, selectedConversationId, setSearchParams])

  const messages = useMemo(
    () => [...(messagesQuery.data?.items ?? [])].reverse(),
    [messagesQuery.data?.items],
  )
  const selectedConversation = conversationQuery.data

  const submitMessage = () => {
    if (!draft.trim() || !selectedConversationId) {
      return
    }
    sendMutation.mutate()
  }

  return (
    <div className="im-workspace">
      <aside className="im-conversation-list">
        <Space className="im-panel-header">
          <Space>
            <Typography.Title level={3}>消息</Typography.Title>
            <ConnectionTag status={wsStatus} />
          </Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)} />
        </Space>
        <div className="im-conversation-items">
          {conversationsQuery.isLoading ? <Typography.Text type="secondary">加载中...</Typography.Text> : null}
          {(conversationsQuery.data ?? []).length === 0 && !conversationsQuery.isLoading ? (
            <Empty description="暂无会话" />
          ) : null}
          {(conversationsQuery.data ?? []).map((conversation) => (
            <ConversationListItem
              key={conversation.id}
              conversation={conversation}
              active={conversation.id === selectedConversationId}
              onClick={() => setSearchParams({ conversationId: conversation.id })}
            />
          ))}
        </div>
      </aside>

      <main className="im-message-panel">
        {selectedConversation ? (
          <>
            <header className="im-message-header">
              <Space>
                <TeamOutlined />
                <Space orientation="vertical" size={0}>
                  <Typography.Title level={4}>{selectedConversation.title}</Typography.Title>
                  <Typography.Text type="secondary">
                    {selectedConversation.memberCount} 名成员
                  </Typography.Text>
                </Space>
              </Space>
              <Button
                loading={readMutation.isPending}
                onClick={() => readMutation.mutate(messages.at(-1)?.id)}
              >
                标为已读
              </Button>
            </header>

            <section className="im-message-list">
              {messages.length === 0 ? (
                <Empty description="还没有消息" />
              ) : (
                messages.map((item) => (
                  <MessageBubble
                    key={item.id}
                    item={item}
                    mine={item.senderId === currentUser?.id}
                    onCreateIssue={() => {
                      setConvertMessage(item)
                      convertForm.setFieldsValue({
                        issueType: 'bug',
                        title: item.content.slice(0, 120),
                        description: item.content,
                      })
                    }}
                    onEdit={() => {
                      setEditingMessage(item)
                      setEditDraft(item.content)
                    }}
                    onRevoke={() => revokeMutation.mutate(item.id)}
                    onPin={() => pinMutation.mutate({ messageId: item.id, pinned: !item.pinnedAt })}
                    onReact={(emoji) => reactionMutation.mutate({ messageId: item.id, emoji })}
                  />
                ))
              )}
            </section>

            <footer className="im-message-composer">
              <Input.TextArea
                value={draft}
                autoSize={{ minRows: 2, maxRows: 5 }}
                placeholder="输入消息，使用 @username 提醒成员，粘贴 /issues、/docs 或 /bases 内部链接生成卡片"
                onChange={(event) => setDraft(event.target.value)}
                onPressEnter={(event) => {
                  if (!event.shiftKey) {
                    event.preventDefault()
                    submitMessage()
                  }
                }}
              />
              <Button
                type="primary"
                icon={<SendOutlined />}
                loading={sendMutation.isPending}
                disabled={!draft.trim() || !selectedConversationId}
                onClick={submitMessage}
              >
                发送
              </Button>
            </footer>
          </>
        ) : (
          <div className="im-empty-state">
            <Empty description="选择或创建一个会话" />
          </div>
        )}
      </main>

      <aside className="im-member-panel">
        <Typography.Title level={4}>成员</Typography.Title>
        <div className="im-member-items">
          {(selectedConversation?.members ?? []).length === 0 ? <Empty description="暂无成员" /> : null}
          {(selectedConversation?.members ?? []).map((member) => (
            <div className="im-member-item" key={member.userId}>
              <Avatar>{member.displayName.slice(0, 1)}</Avatar>
              <Space orientation="vertical" size={0} className="im-member-text">
                <Typography.Text>{member.displayName}</Typography.Text>
                <Typography.Text type="secondary">@{member.username}</Typography.Text>
              </Space>
              {member.memberRole === 'owner' ? <Tag color="blue">owner</Tag> : null}
            </div>
          ))}
        </div>
      </aside>

      <Modal
        title="新建会话"
        open={createOpen}
        confirmLoading={createMutation.isPending}
        onCancel={() => setCreateOpen(false)}
        onOk={() => {
          form.validateFields()
            .then((values) => createMutation.mutate(values))
            .catch(() => message.error('请检查会话信息'))
        }}
      >
        <Form form={form} layout="vertical" initialValues={{ conversationType: 'group', memberIds: [] }}>
          <Form.Item label="类型" name="conversationType" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'group', label: '群聊' },
                { value: 'direct', label: '单聊' },
              ]}
            />
          </Form.Item>
          <Form.Item label="名称" name="title">
            <Input placeholder="例如：IM MVP 讨论" />
          </Form.Item>
          <Form.Item label="成员" name="memberIds" rules={[{ required: true, message: '请选择成员' }]}>
            <Select
              mode="multiple"
              showSearch
              optionFilterProp="label"
              options={(directoryQuery.data ?? [])
                .filter((member) => member.id !== currentUser?.id)
                .map((member) => ({
                  value: member.id,
                  label: `${member.displayName} @${member.username}`,
                }))}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="从消息创建事项"
        open={Boolean(convertMessage)}
        confirmLoading={convertMutation.isPending}
        onCancel={() => setConvertMessage(null)}
        onOk={() => convertForm.validateFields().then((values) => convertMutation.mutate(values))}
      >
        <Form form={convertForm} layout="vertical" initialValues={{ issueType: 'bug' }}>
          <Form.Item label="项目" name="projectId" rules={[{ required: true, message: '请选择项目' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={(projectsQuery.data ?? []).map((project) => ({
                value: project.id,
                label: `${project.name} (${project.projectKey})`,
              }))}
            />
          </Form.Item>
          <Form.Item label="类型" name="issueType" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'bug', label: 'Bug' },
                { value: 'task', label: '任务' },
              ]}
            />
          </Form.Item>
          <Form.Item label="标题" name="title" rules={[{ required: true, message: '请输入标题' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="描述" name="description">
            <Input.TextArea autoSize={{ minRows: 3, maxRows: 6 }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="编辑消息"
        open={Boolean(editingMessage)}
        confirmLoading={editMutation.isPending}
        onCancel={() => setEditingMessage(null)}
        onOk={() => editMutation.mutate()}
      >
        <Input.TextArea
          value={editDraft}
          autoSize={{ minRows: 3, maxRows: 8 }}
          onChange={(event) => setEditDraft(event.target.value)}
        />
      </Modal>
    </div>
  )
}

function ConversationListItem({
  conversation,
  active,
  onClick,
}: {
  conversation: ConversationSummary
  active: boolean
  onClick: () => void
}) {
  return (
    <button className={active ? 'im-conversation-item active' : 'im-conversation-item'} onClick={onClick}>
      <Badge count={conversation.unreadCount}>
        <Avatar icon={<TeamOutlined />} />
      </Badge>
      <span className="im-conversation-copy">
        <span className="im-conversation-title">
          <Typography.Text strong>{conversation.title}</Typography.Text>
          <Tag>{conversation.conversationType}</Tag>
        </span>
        <Typography.Text type="secondary" ellipsis>
          {conversation.lastMessage?.content || '暂无消息'}
        </Typography.Text>
      </span>
    </button>
  )
}

function MessageBubble({
  item,
  mine,
  onCreateIssue,
  onEdit,
  onRevoke,
  onPin,
  onReact,
}: {
  item: MessageSummary
  mine: boolean
  onCreateIssue: () => void
  onEdit: () => void
  onRevoke: () => void
  onPin: () => void
  onReact: (emoji: string) => void
}) {
  return (
    <article className={mine ? 'im-message mine' : 'im-message'}>
      <Avatar>{item.senderName.slice(0, 1)}</Avatar>
      <div className="im-message-body">
        <Space size={8}>
          <Typography.Text strong>{item.senderName}</Typography.Text>
          <Typography.Text type="secondary">{new Date(item.createdAt).toLocaleString()}</Typography.Text>
          {item.editedAt ? <Tag>已编辑</Tag> : null}
          {item.pinnedAt ? <Tag color="gold">置顶</Tag> : null}
        </Space>
        <Typography.Paragraph className="im-message-content">
          {item.revokedAt ? <Typography.Text type="secondary">消息已撤回</Typography.Text> : item.content}
        </Typography.Paragraph>
        {item.mentions.length > 0 ? (
          <Space wrap>
            {item.mentions.map((mention) => (
              <Tag key={mention.userId} color="blue">
                @{mention.username}
              </Tag>
            ))}
          </Space>
        ) : null}
        {item.links.length > 0 ? (
          <Space orientation="vertical" className="im-message-links">
            {item.links.map((link) =>
              link.summary ? (
                <ObjectSummaryCard key={link.id} summary={link.summary} />
              ) : (
                <Typography.Link key={link.id}>{link.sourceUrl}</Typography.Link>
              ),
            )}
          </Space>
        ) : null}
        {item.reactions.length > 0 ? (
          <Space wrap size={4} className="im-message-reactions">
            {item.reactions.map((reaction) => (
              <Button
                key={reaction.emoji}
                size="small"
                type={reaction.reactedByMe ? 'primary' : 'default'}
                onClick={() => onReact(reaction.emoji)}
              >
                {reaction.emoji} {reaction.count}
              </Button>
            ))}
          </Space>
        ) : null}
        <Space wrap className="im-message-actions">
          <Button size="small" icon={<SmileOutlined />} disabled={Boolean(item.revokedAt)} onClick={() => onReact('👍')} />
          <Button size="small" icon={<PushpinOutlined />} disabled={Boolean(item.revokedAt)} onClick={onPin} />
          <Button size="small" icon={<BugOutlined />} disabled={Boolean(item.revokedAt)} onClick={onCreateIssue}>
            转事项
          </Button>
          {mine ? (
            <>
              <Button size="small" icon={<EditOutlined />} disabled={Boolean(item.revokedAt)} onClick={onEdit} />
              <Button size="small" icon={<StopOutlined />} disabled={Boolean(item.revokedAt)} onClick={onRevoke} />
            </>
          ) : null}
        </Space>
      </div>
    </article>
  )
}

function ConnectionTag({ status }: { status: string }) {
  if (status === 'connected') {
    return <Tag icon={<WifiOutlined />} color="green">在线</Tag>
  }
  if (status === 'connecting') {
    return <Tag color="blue">连接中</Tag>
  }
  return <Tag icon={<DisconnectOutlined />}>离线</Tag>
}
