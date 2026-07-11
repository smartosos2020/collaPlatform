import {
  BellOutlined,
  BugOutlined,
  CloseCircleOutlined,
  CopyOutlined,
  DeleteOutlined,
  DisconnectOutlined,
  LogoutOutlined,
  FileTextOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  EditOutlined,
  PlusOutlined,
  PushpinOutlined,
  SearchOutlined,
  SendOutlined,
  SmileOutlined,
  StopOutlined,
  TeamOutlined,
  UserOutlined,
  WifiOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  App as AntdApp,
  Avatar,
  Badge,
  Button,
  Dropdown,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Tag,
  Typography,
} from 'antd'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import { useAuthStore, type CurrentUser } from '../../auth/authStore'
import { ObjectSummaryCard } from '../../platform/components/InternalLinkCard'
import {
  addConversationMembers,
  closeConversation,
  convertMessageToKnowledgeContent,
  convertMessageToIssue,
  createConversation,
  editMessage,
  getConversation,
  leaveConversation,
  listConversations,
  listDirectoryMembers,
  listMessageContext,
  listMessages,
  markConversationRead,
  muteConversation,
  pinConversation,
  pinMessage,
  removeConversationMember,
  revokeMessage,
  searchConversationMessages,
  sendMessage,
  toggleReaction,
  type ConversationSummary,
  type MessagePage,
  type MessageSummary,
} from '../api/messengerApi'
import { useWebSocketConnection } from '../../../shared/websocket/useWebSocketConnection'
import type { PlatformWebSocketEvent } from '../../../shared/websocket/websocketEvents'
import { listProjects } from '../../projects/api/projectsApi'

type CreateConversationForm = {
  conversationType: 'direct' | 'group'
  title?: string
  memberIds: string[]
}

type ConvertMessageForm = {
  projectId: string
  issueType: 'requirement' | 'task' | 'bug'
  title: string
  description?: string
}

type ConvertMessageToKnowledgeContentForm = {
  title?: string
}

type MessageDeliveryStatus = 'sending' | 'failed'

type ChatMessage = MessageSummary & {
  deliveryStatus?: MessageDeliveryStatus
}

const QUICK_REACTIONS = ['👍', '❤️', '😂', '🎉', '😮', '🙏']
const COMPOSER_EMOJIS = ['😀', '😄', '😂', '😊', '👍', '❤️', '🎉', '🔥', '🙏', '💡', '✅', '🚀']

export function MessengerPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()
  const [draft, setDraft] = useState('')
  const [messageSearchText, setMessageSearchText] = useState('')
  const [messageSearchQueryText, setMessageSearchQueryText] = useState('')
  const [messageSearchTargetType, setMessageSearchTargetType] = useState<string>()
  const [createOpen, setCreateOpen] = useState(false)
  const [convertMessage, setConvertMessage] = useState<MessageSummary | null>(null)
  const [convertDocumentMessage, setConvertDocumentMessage] = useState<MessageSummary | null>(null)
  const [editingMessage, setEditingMessage] = useState<MessageSummary | null>(null)
  const [editDraft, setEditDraft] = useState('')
  const [conversationListCollapsed, setConversationListCollapsed] = useState(false)
  const [memberPanelOpen, setMemberPanelOpen] = useState(false)
  const [highlightedMessageId, setHighlightedMessageId] = useState<string | null>(null)
  const [directMemberId, setDirectMemberId] = useState<string>()
  const [memberToAddId, setMemberToAddId] = useState<string>()
  const [composerEmojiOpen, setComposerEmojiOpen] = useState(false)
  const [localMessages, setLocalMessages] = useState<ChatMessage[]>([])
  const [syncingAfterReconnect, setSyncingAfterReconnect] = useState(false)
  const [form] = Form.useForm<CreateConversationForm>()
  const [convertForm] = Form.useForm<ConvertMessageForm>()
  const [convertDocumentForm] = Form.useForm<ConvertMessageToKnowledgeContentForm>()
  const messageListRef = useRef<HTMLElement | null>(null)
  const messageRefs = useRef<Record<string, HTMLElement | null>>({})
  const autoReadMessageIdRef = useRef<string | null>(null)
  const focusedMessageIdRef = useRef<string | null>(null)
  const previousWsStatusRef = useRef<string>('idle')
  const queryClient = useQueryClient()
  const currentUser = useAuthStore((state) => state.currentUser)
  const { message } = AntdApp.useApp()

  const selectedConversationId = searchParams.get('conversationId')
  const selectedMessageId = searchParams.get('messageId')

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

  const messagesQueryKey = useMemo(
    () => ['im', 'messages', selectedConversationId, selectedMessageId] as const,
    [selectedConversationId, selectedMessageId],
  )

  const messagesQuery = useQuery({
    queryKey: messagesQueryKey,
    queryFn: () =>
      selectedConversationId && selectedMessageId
        ? listMessageContext(selectedConversationId, selectedMessageId)
        : listMessages(selectedConversationId || ''),
    enabled: Boolean(selectedConversationId),
  })

  const messageSearchActive = Boolean(messageSearchQueryText.trim() || messageSearchTargetType)
  const messageSearchQuery = useQuery({
    queryKey: ['im', 'message-search', selectedConversationId, messageSearchQueryText, messageSearchTargetType],
    queryFn: () =>
      searchConversationMessages(selectedConversationId || '', {
        q: messageSearchQueryText,
        targetType: messageSearchTargetType,
      }),
    enabled: Boolean(selectedConversationId && messageSearchActive),
  })

  const refreshIm = useCallback(async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['im', 'conversations'] }),
      selectedConversationId
        ? queryClient.invalidateQueries({ queryKey: ['im', 'conversation', selectedConversationId] })
        : Promise.resolve(),
      selectedConversationId
        ? queryClient.invalidateQueries({ queryKey: ['im', 'messages', selectedConversationId] })
        : Promise.resolve(),
    ])
  }, [queryClient, selectedConversationId])

  const wsStatus = useWebSocketConnection((event: PlatformWebSocketEvent) => {
    if (
      [
        'message.created',
        'message.edited',
        'message.revoked',
        'message.pinned',
        'message.unpinned',
        'message.reaction.toggled',
        'conversation.updated',
        'conversation.read',
        'unread.changed',
      ].includes(event.type)
    ) {
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

  const createDirectMutation = useMutation({
    mutationFn: (memberId: string) => {
      const member = (directoryQuery.data ?? []).find((item) => item.id === memberId)
      return createConversation({
        conversationType: 'direct',
        title: member?.displayName || member?.username || '单聊',
        memberIds: [memberId],
      })
    },
    onSuccess: async (conversation) => {
      setDirectMemberId(undefined)
      setSearchParams({ conversationId: conversation.id })
      await refreshIm()
    },
    onError: () => message.error('创建单聊失败，请稍后重试'),
  })

  const sendMutation = useMutation({
    mutationFn: ({
      conversationId,
      content,
      clientMessageId,
    }: {
      conversationId: string
      content: string
      clientMessageId: string
    }) => sendMessage(conversationId, content, clientMessageId),
    onSuccess: async (serverMessage) => {
      setLocalMessages((items) => items.filter((item) => item.clientMessageId !== serverMessage.clientMessageId))
      await refreshIm()
    },
    onError: (_error, variables) => {
      setLocalMessages((items) =>
        items.map((item) =>
          item.clientMessageId === variables.clientMessageId
            ? { ...item, deliveryStatus: 'failed' }
            : item,
        ),
      )
      message.error('消息发送失败，可在消息气泡中重试')
    },
  })

  const convertMutation = useMutation({
    mutationFn: (values: ConvertMessageForm) =>
      convertMessageToIssue(convertMessage?.conversationId || selectedConversationId || '', convertMessage?.id || '', {
        projectId: values.projectId,
        issueType: values.issueType,
        title: values.title,
        description: values.description,
        priority: 'medium',
      }),
    onSuccess: async () => {
      message.success('已从消息创建事项')
      setConvertMessage(null)
      convertForm.resetFields()
      await refreshIm()
    },
    onError: () => message.error('从消息创建事项失败，请检查项目权限'),
  })

  const convertDocumentMutation = useMutation({
    mutationFn: (values: ConvertMessageToKnowledgeContentForm) =>
      convertMessageToKnowledgeContent(
        convertDocumentMessage?.conversationId || selectedConversationId || '',
        convertDocumentMessage?.id || '',
        { title: values.title },
      ),
    onSuccess: async (detail) => {
      message.success('已从消息沉淀为知识内容')
      setConvertDocumentMessage(null)
      convertDocumentForm.resetFields()
      await refreshIm()
      navigate(detail.context?.webPath ?? '/knowledge-bases')
    },
    onError: () => message.error('从消息沉淀知识内容失败'),
  })

  const readMutation = useMutation({
    mutationFn: (messageId?: string) => markConversationRead(selectedConversationId || '', messageId),
    onSuccess: refreshIm,
  })

  const addMemberMutation = useMutation({
    mutationFn: (memberId: string) => addConversationMembers(selectedConversationId || '', [memberId]),
    onSuccess: async () => {
      setMemberToAddId(undefined)
      await refreshIm()
    },
    onError: () => message.error('添加成员失败，请稍后重试'),
  })

  const removeMemberMutation = useMutation({
    mutationFn: (memberId: string) => removeConversationMember(selectedConversationId || '', memberId),
    onSuccess: refreshIm,
    onError: () => message.error('移除成员失败，请稍后重试'),
  })

  const leaveMutation = useMutation({
    mutationFn: (conversationId?: string) => leaveConversation(conversationId || selectedConversationId || ''),
    onSuccess: async (_data, conversationId) => {
      setMemberPanelOpen(false)
      const leavingConversationId = conversationId || selectedConversationId
      if (!leavingConversationId || leavingConversationId === selectedConversationId) {
        setSearchParams({})
      }
      await refreshIm()
    },
    onError: () => message.error('退出群聊失败，请稍后重试'),
  })

  const closeConversationMutation = useMutation({
    mutationFn: (conversationId: string) => closeConversation(conversationId),
    onSuccess: async (_data, conversationId) => {
      if (conversationId === selectedConversationId) {
        setSearchParams({})
      }
      await refreshIm()
    },
    onError: () => message.error('关闭单聊失败，请稍后重试'),
  })

  const muteConversationMutation = useMutation({
    mutationFn: ({ conversationId, muted }: { conversationId: string; muted: boolean }) =>
      muteConversation(conversationId, muted),
    onSuccess: refreshIm,
    onError: () => message.error('更新静音失败，请稍后重试'),
  })

  const pinConversationMutation = useMutation({
    mutationFn: ({ conversationId, pinned }: { conversationId: string; pinned: boolean }) =>
      pinConversation(conversationId, pinned),
    onSuccess: refreshIm,
    onError: () => message.error('更新置顶失败，请稍后重试'),
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
    onError: () => message.error('点赞失败，请稍后重试'),
  })

  useEffect(() => {
    const conversations = conversationsQuery.data ?? []
    if (!selectedConversationId && conversations.length > 0) {
      setSearchParams({ conversationId: conversations[0].id })
    }
  }, [conversationsQuery.data, selectedConversationId, setSearchParams])

  const messages = useMemo<ChatMessage[]>(() => {
    const serverMessages = [...(messagesQuery.data?.items ?? [])].reverse()
    const serverClientIds = new Set(serverMessages.map((item) => item.clientMessageId))
    const pendingMessages = localMessages.filter(
      (item) => item.conversationId === selectedConversationId && !serverClientIds.has(item.clientMessageId),
    )
    return [...serverMessages, ...pendingMessages].sort(
      (left, right) => new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime(),
    )
  }, [localMessages, messagesQuery.data?.items, selectedConversationId])
  const selectedConversation = conversationQuery.data
  const latestServerMessageSeq = useMemo(
    () =>
      messages.reduce(
        (latest, item) => (item.deliveryStatus ? latest : Math.max(latest, item.messageSeq)),
        0,
      ),
    [messages],
  )
  const selectedMemberIds = useMemo(
    () => new Set((selectedConversation?.members ?? []).map((member) => member.userId)),
    [selectedConversation?.members],
  )
  const pinnedMessage = useMemo(
    () =>
      messages
        .filter((item) => item.pinnedAt && !item.revokedAt)
        .sort((left, right) => new Date(right.pinnedAt || '').getTime() - new Date(left.pinnedAt || '').getTime())[0],
    [messages],
  )
  const conversationGroups = useMemo(() => {
    const conversations = conversationsQuery.data ?? []
    return [
      { key: 'pinned', title: '置顶', items: conversations.filter((item) => Boolean(item.pinnedAt)) },
      { key: 'recent', title: '最近', items: conversations.filter((item) => !item.pinnedAt) },
    ].filter((group) => group.items.length > 0)
  }, [conversationsQuery.data])
  const directMemberOptions = useMemo(
    () =>
      (directoryQuery.data ?? [])
        .filter((member) => member.id !== currentUser?.id)
        .map((member) => ({
          value: member.id,
          label: `${member.displayName} @${member.username}`,
        })),
    [currentUser?.id, directoryQuery.data],
  )
  const addableMemberOptions = useMemo(
    () =>
      (directoryQuery.data ?? [])
        .filter((member) => !selectedMemberIds.has(member.id))
        .map((member) => ({
          value: member.id,
          label: `${member.displayName} @${member.username}`,
        })),
    [directoryQuery.data, selectedMemberIds],
  )
  const mentionQuery = useMemo(() => {
    const match = draft.match(/(^|\s)@([a-zA-Z0-9_.-]*)$/)
    return match ? match[2].toLowerCase() : null
  }, [draft])
  const mentionSuggestions = useMemo(
    () =>
      mentionQuery === null
        ? []
        : (selectedConversation?.members ?? [])
            .filter((member) => member.userId !== currentUser?.id)
            .filter((member) =>
              `${member.displayName} ${member.username}`.toLowerCase().includes(mentionQuery),
            )
            .slice(0, 6),
    [currentUser?.id, mentionQuery, selectedConversation?.members],
  )

  const focusMessage = useCallback((messageId: string) => {
    messageRefs.current[messageId]?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    setHighlightedMessageId(messageId)
    window.setTimeout(() => setHighlightedMessageId(null), 1600)
  }, [])

  const syncAfterReconnect = useCallback(async () => {
    if (!selectedConversationId || latestServerMessageSeq <= 0) {
      await refreshIm()
      return
    }
    setSyncingAfterReconnect(true)
    try {
      const page = await listMessages(selectedConversationId, null, latestServerMessageSeq)
      if (page.items.length > 0) {
        queryClient.setQueryData<MessagePage>(messagesQueryKey, (current) => mergeMessagePage(current, page))
      }
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['im', 'conversations'] }),
        queryClient.invalidateQueries({ queryKey: ['im', 'conversation', selectedConversationId] }),
      ])
    } finally {
      setSyncingAfterReconnect(false)
    }
  }, [latestServerMessageSeq, messagesQueryKey, queryClient, refreshIm, selectedConversationId])

  useEffect(() => {
    autoReadMessageIdRef.current = null
    focusedMessageIdRef.current = null
  }, [selectedConversationId, selectedMessageId])

  useEffect(() => {
    const previousStatus = previousWsStatusRef.current
    previousWsStatusRef.current = wsStatus
    if (wsStatus === 'connected' && previousStatus !== 'connected') {
      void syncAfterReconnect()
    }
  }, [syncAfterReconnect, wsStatus])

  useEffect(() => {
    if (!selectedMessageId || focusedMessageIdRef.current === selectedMessageId) {
      return
    }
    if (!messages.some((item) => item.id === selectedMessageId)) {
      return
    }
    focusedMessageIdRef.current = selectedMessageId
    window.setTimeout(() => focusMessage(selectedMessageId), 80)
  }, [focusMessage, messages, selectedMessageId])

  useEffect(() => {
    if (!selectedConversationId || messages.length === 0 || readMutation.isPending) {
      return undefined
    }
    const root = messageListRef.current
    if (!root) {
      return undefined
    }
    const readableMessages = messages.filter((item) => item.senderId !== currentUser?.id && !item.deliveryStatus)
    if (readableMessages.length === 0) {
      return undefined
    }
    const observer = new IntersectionObserver(
      (entries) => {
        const visibleMessageIds = new Set(
          entries
            .filter((entry) => entry.isIntersecting && entry.intersectionRatio >= 0.98)
            .map((entry) => (entry.target as HTMLElement).dataset.messageId)
            .filter(Boolean),
        )
        const latestVisibleMessage = [...readableMessages].reverse().find((item) => visibleMessageIds.has(item.id))
        if (!latestVisibleMessage || latestVisibleMessage.id === autoReadMessageIdRef.current) {
          return
        }
        autoReadMessageIdRef.current = latestVisibleMessage.id
        readMutation.mutate(latestVisibleMessage.id)
      },
      { root, threshold: [0.98, 1] },
    )

    for (const item of readableMessages) {
      const node = messageRefs.current[item.id]
      if (node) {
        observer.observe(node)
      }
    }
    return () => observer.disconnect()
  }, [currentUser?.id, messages, readMutation, selectedConversationId])

  const submitMessage = () => {
    if (!draft.trim() || !selectedConversationId) {
      return
    }
    const content = draft.trim()
    const clientMessageId = crypto.randomUUID()
    setDraft('')
    setLocalMessages((items) => [
      ...items,
      createLocalMessage({
        clientMessageId,
        conversationId: selectedConversationId,
        content,
        currentUser,
        status: 'sending',
      }),
    ])
    sendMutation.mutate({ conversationId: selectedConversationId, content, clientMessageId })
  }

  const retryMessage = (item: ChatMessage) => {
    setLocalMessages((items) =>
      items.map((messageItem) =>
        messageItem.clientMessageId === item.clientMessageId
          ? { ...messageItem, deliveryStatus: 'sending' }
          : messageItem,
      ),
    )
    sendMutation.mutate({
      conversationId: item.conversationId,
      content: item.content,
      clientMessageId: item.clientMessageId,
    })
  }

  const insertTextToDraft = (value: string) => {
    setDraft((current) => `${current}${value}`)
  }

  const insertMention = (username: string) => {
    setDraft((current) => current.replace(/(^|\s)@([a-zA-Z0-9_.-]*)$/, `$1@${username} `))
  }

  return (
    <div
      className={[
        'im-workspace',
        conversationListCollapsed ? 'conversation-list-collapsed' : '',
        memberPanelOpen ? 'members-open' : '',
      ].join(' ')}
    >
      <aside className="im-conversation-list">
        <Space className="im-panel-header">
          {conversationListCollapsed ? null : (
            <Space>
              <Typography.Title level={3}>消息</Typography.Title>
              <ConnectionTag status={wsStatus} />
            </Space>
          )}
          <Space>
            <Button
              icon={conversationListCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setConversationListCollapsed((value) => !value)}
            />
            {conversationListCollapsed ? null : (
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)} />
            )}
          </Space>
        </Space>
        {conversationListCollapsed ? null : (
          <Select
            className="im-direct-search"
            showSearch
            allowClear
            placeholder="搜索成员开始单聊"
            optionFilterProp="label"
            loading={directoryQuery.isLoading || createDirectMutation.isPending}
            options={directMemberOptions}
            value={directMemberId}
            onChange={(memberId) => setDirectMemberId(memberId)}
            onSelect={(memberId) => createDirectMutation.mutate(memberId)}
          />
        )}
        <div className="im-conversation-items">
          {conversationsQuery.isLoading ? <Typography.Text type="secondary">加载中...</Typography.Text> : null}
          {(conversationsQuery.data ?? []).length === 0 && !conversationsQuery.isLoading ? (
            <Empty description="暂无会话" />
          ) : null}
          {conversationGroups.map((group) => (
            <div className="im-conversation-group" key={group.key}>
              {conversationListCollapsed ? null : <span className="im-conversation-group-title">{group.title}</span>}
              {group.items.map((conversation) => (
                <ConversationListItem
                  key={conversation.id}
                  conversation={conversation}
                  active={conversation.id === selectedConversationId}
                  collapsed={conversationListCollapsed}
                  onClick={() => setSearchParams({ conversationId: conversation.id })}
                  onPin={() => pinConversationMutation.mutate({ conversationId: conversation.id, pinned: !conversation.pinnedAt })}
                  onMute={() => muteConversationMutation.mutate({ conversationId: conversation.id, muted: !conversation.muted })}
                  onLeave={() => leaveMutation.mutate(conversation.id)}
                  onClose={() => closeConversationMutation.mutate(conversation.id)}
                />
              ))}
            </div>
          ))}
        </div>
      </aside>

      <main className="im-message-panel">
        {selectedConversation ? (
          <>
            <header className="im-info-bar">
              <Space>
                <ConversationAvatar conversation={selectedConversation} />
                <Typography.Title level={4}>{selectedConversation.title}</Typography.Title>
                {syncingAfterReconnect ? <Tag color="blue">同步中</Tag> : null}
              </Space>
              <Space>
                <Button
                  icon={<TeamOutlined />}
                  type={memberPanelOpen ? 'primary' : 'default'}
                  onClick={() => setMemberPanelOpen((value) => !value)}
                >
                  {selectedConversation.memberCount}
                </Button>
                <Button
                  loading={readMutation.isPending}
                  onClick={() => readMutation.mutate(messages.at(-1)?.id)}
                >
                  标为已读
                </Button>
              </Space>
            </header>
            <div className="im-message-search">
              <Input.Search
                allowClear
                size="small"
                prefix={<SearchOutlined />}
                placeholder="搜索当前会话消息"
                value={messageSearchText}
                loading={messageSearchQuery.isFetching}
                onChange={(event) => {
                  setMessageSearchText(event.target.value)
                  if (!event.target.value.trim()) {
                    setMessageSearchQueryText('')
                  }
                }}
                onSearch={(value) => setMessageSearchQueryText(value.trim())}
              />
              <Select
                allowClear
                size="small"
                className="im-message-search-target"
                placeholder="对象"
                value={messageSearchTargetType}
                onChange={(value) => setMessageSearchTargetType(value)}
                options={[
                  { value: 'issue', label: '事项' },
                  { value: 'knowledge_content', label: '知识内容' },
                  { value: 'base', label: '表格' },
                  { value: 'approval', label: '审批' },
                  { value: 'message', label: '消息' },
                ]}
              />
            </div>
            {messageSearchActive ? (
              <div className="im-message-search-results">
                {messageSearchQuery.isLoading ? <Typography.Text type="secondary">搜索中...</Typography.Text> : null}
                {!messageSearchQuery.isLoading && (messageSearchQuery.data?.items ?? []).length === 0 ? (
                  <Typography.Text type="secondary">没有匹配消息</Typography.Text>
                ) : null}
                {(messageSearchQuery.data?.items ?? []).map((item) => {
                  const linkSummary = item.links
                    .map((link) => link.summary?.title || link.webPath || link.sourceUrl)
                    .filter(Boolean)
                    .join(' · ')
                  return (
                    <button
                      key={item.id}
                      type="button"
                      onClick={() => setSearchParams({ conversationId: item.conversationId, messageId: item.id })}
                    >
                      <span>{item.senderName}</span>
                      <span className="im-message-search-main">
                        <strong>{item.content || '消息已撤回'}</strong>
                        {linkSummary ? <small>{linkSummary}</small> : null}
                      </span>
                      <time>{formatMessageTime(item.createdAt)}</time>
                    </button>
                  )
                })}
              </div>
            ) : null}
            {pinnedMessage ? (
              <button className="im-pinned-bar" onClick={() => focusMessage(pinnedMessage.id)}>
                <PushpinOutlined />
                <span>{pinnedMessage.content || '消息已撤回'}</span>
              </button>
            ) : null}

            <section className="im-message-list" ref={messageListRef}>
              {messages.length === 0 ? (
                <Empty description="还没有消息" />
              ) : (
                messages.map((item, index) => (
                  <div key={item.id} className="im-message-row">
                    {isFirstMessageOfDay(messages[index - 1], item) ? (
                      <div className="im-message-date-separator">{formatMessageDate(item.createdAt)}</div>
                    ) : null}
                    <MessageBubble
                      refCallback={(node) => {
                        messageRefs.current[item.id] = node
                      }}
                      item={item}
                      mine={item.senderId === currentUser?.id}
                      highlighted={highlightedMessageId === item.id}
                      onCreateIssue={() => {
                        setConvertMessage(item)
                        convertForm.setFieldsValue({
                          issueType: 'bug',
                          title: item.content.slice(0, 120),
                          description: item.content,
                        })
                      }}
                      onCreateDocument={() => {
                        setConvertDocumentMessage(item)
                        convertDocumentForm.setFieldsValue({ title: item.content.replace(/\s+/g, ' ').trim().slice(0, 80) })
                      }}
                      onCopyLink={() => {
                        const link = `/im?conversationId=${item.conversationId}&messageId=${item.id}`
                        void navigator.clipboard.writeText(link)
                        message.success('已复制消息链接')
                      }}
                      onEdit={() => {
                        setEditingMessage(item)
                        setEditDraft(item.content)
                      }}
                      onRevoke={() => revokeMutation.mutate(item.id)}
                      onPin={() => pinMutation.mutate({ messageId: item.id, pinned: !item.pinnedAt })}
                      onReact={(emoji) => reactionMutation.mutate({ messageId: item.id, emoji })}
                      onRetry={() => retryMessage(item)}
                      reactionPending={reactionMutation.isPending}
                    />
                  </div>
                ))
              )}
            </section>

            <footer className="im-message-composer">
              <div className="im-composer-input-shell">
                {mentionSuggestions.length > 0 ? (
                  <div className="im-mention-popover">
                    {mentionSuggestions.map((member) => (
                      <button key={member.userId} type="button" onClick={() => insertMention(member.username)}>
                        <Avatar size="small">{member.displayName.slice(0, 1)}</Avatar>
                        <span>{member.displayName}</span>
                        <small>@{member.username}</small>
                      </button>
                    ))}
                  </div>
                ) : null}
                <Input.TextArea
                  value={draft}
                  autoSize={{ minRows: 1, maxRows: 5 }}
                  placeholder="输入消息，使用 @username 提醒成员，粘贴事项、知识内容或表格链接生成卡片"
                  onChange={(event) => setDraft(event.target.value)}
                  onPressEnter={(event) => {
                    if (event.altKey) {
                      return
                    }
                    event.preventDefault()
                    submitMessage()
                  }}
                />
              </div>
              <div className="im-composer-emoji-shell">
                {composerEmojiOpen ? (
                  <div className="im-composer-emoji-popover">
                    {COMPOSER_EMOJIS.map((emoji) => (
                      <button
                        key={emoji}
                        type="button"
                        onClick={() => {
                          insertTextToDraft(emoji)
                          setComposerEmojiOpen(false)
                        }}
                      >
                        {emoji}
                      </button>
                    ))}
                  </div>
                ) : null}
                <Button
                  icon={<SmileOutlined />}
                  onClick={() => setComposerEmojiOpen((value) => !value)}
                />
              </div>
              <Button
                type="primary"
                icon={<SendOutlined />}
                loading={sendMutation.isPending}
                disabled={!draft.trim() || !selectedConversationId}
                onClick={submitMessage}
              />
            </footer>
          </>
        ) : (
          <div className="im-empty-state">
            <Empty description="选择或创建一个会话" />
          </div>
        )}
      </main>

      {memberPanelOpen ? (
        <aside className="im-member-panel">
          <Space className="im-member-panel-header">
            <Typography.Title level={4}>成员</Typography.Title>
            {selectedConversation?.conversationType === 'group' ? (
              <Button
                danger
                size="small"
                icon={<LogoutOutlined />}
                loading={leaveMutation.isPending}
                onClick={() => leaveMutation.mutate(selectedConversationId || undefined)}
              >
                退出
              </Button>
            ) : null}
          </Space>
          <Select
            className="im-member-add"
            showSearch
            allowClear
            placeholder="搜索成员添加"
            optionFilterProp="label"
            loading={directoryQuery.isLoading || addMemberMutation.isPending}
            options={addableMemberOptions}
            value={memberToAddId}
            onChange={(memberId) => setMemberToAddId(memberId)}
            onSelect={(memberId) => addMemberMutation.mutate(memberId)}
          />
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
                {member.userId !== currentUser?.id && currentUser?.id === selectedConversation?.members.find((item) => item.memberRole === 'owner')?.userId ? (
                  <Button
                    danger
                    size="small"
                    icon={<DeleteOutlined />}
                    loading={removeMemberMutation.isPending}
                    onClick={() => removeMemberMutation.mutate(member.userId)}
                  />
                ) : null}
              </div>
            ))}
          </div>
        </aside>
      ) : null}

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
              options={directMemberOptions}
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
                { value: 'requirement', label: '需求' },
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
        title="从消息沉淀知识内容"
        open={Boolean(convertDocumentMessage)}
        confirmLoading={convertDocumentMutation.isPending}
        onCancel={() => setConvertDocumentMessage(null)}
        onOk={() => convertDocumentForm.validateFields().then((values) => convertDocumentMutation.mutate(values))}
      >
        <Form form={convertDocumentForm} layout="vertical">
          <Form.Item label="标题" name="title">
            <Input placeholder="默认使用消息内容生成标题" />
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

function createLocalMessage({
  clientMessageId,
  conversationId,
  content,
  currentUser,
  status,
}: {
  clientMessageId: string
  conversationId: string
  content: string
  currentUser: CurrentUser | null
  status: MessageDeliveryStatus
}): ChatMessage {
  return {
    id: `local-${clientMessageId}`,
    conversationId,
    senderId: currentUser?.id ?? 'local-user',
    senderName: currentUser?.displayName ?? currentUser?.username ?? '我',
    messageType: 'text',
    content,
    clientMessageId,
    messageSeq: 0,
    createdAt: new Date().toISOString(),
    editedAt: null,
    revokedAt: null,
    pinnedAt: null,
    pinnedBy: null,
    mentions: [],
    links: [],
    reactions: [],
    deliveryStatus: status,
  }
}

function mergeMessagePage(current: MessagePage | undefined, incoming: MessagePage): MessagePage {
  const byId = new Map<string, MessageSummary>()
  for (const item of current?.items ?? []) {
    byId.set(item.id, item)
  }
  for (const item of incoming.items) {
    byId.set(item.id, item)
  }
  return {
    items: [...byId.values()]
      .sort((left, right) => right.messageSeq - left.messageSeq)
      .slice(0, 100),
    nextCursor: current?.nextCursor ?? incoming.nextCursor ?? null,
  }
}

function ConversationListItem({
  conversation,
  active,
  collapsed,
  onClick,
  onPin,
  onMute,
  onLeave,
  onClose,
}: {
  conversation: ConversationSummary
  active: boolean
  collapsed: boolean
  onClick: () => void
  onPin: () => void
  onMute: () => void
  onLeave: () => void
  onClose: () => void
}) {
  const isDirect = conversation.conversationType === 'direct'
  const menuItems = [
    {
      key: 'pin',
      icon: <PushpinOutlined />,
      label: conversation.pinnedAt ? '取消置顶' : '置顶',
      onClick: onPin,
    },
    {
      key: 'mute',
      icon: <BellOutlined />,
      label: conversation.muted ? '取消静音' : '静音',
      onClick: onMute,
    },
    isDirect
      ? {
          key: 'close',
          icon: <CloseCircleOutlined />,
          label: '关闭',
          danger: true,
          onClick: onClose,
        }
      : {
          key: 'leave',
          icon: <LogoutOutlined />,
          label: '离开',
          danger: true,
          onClick: onLeave,
        },
  ]

  return (
    <Dropdown menu={{ items: menuItems }} trigger={['contextMenu']}>
      <button className={active ? 'im-conversation-item active' : 'im-conversation-item'} onClick={onClick}>
        <Badge count={conversation.unreadCount}>
          <ConversationAvatar conversation={conversation} />
        </Badge>
        {collapsed ? null : (
          <span className="im-conversation-copy">
            <span className="im-conversation-title">
              <Typography.Text strong>{conversation.title}</Typography.Text>
              <Tag>{isDirect ? '单聊' : '群聊'}</Tag>
              {conversation.pinnedAt ? <Tag color="gold">置顶</Tag> : null}
              {conversation.muted ? <Tag>静音</Tag> : null}
            </span>
            <Typography.Text type="secondary" ellipsis>
              {conversation.lastMessage?.content || '暂无消息'}
            </Typography.Text>
          </span>
        )}
      </button>
    </Dropdown>
  )
}

function ConversationAvatar({ conversation }: { conversation: Pick<ConversationSummary, 'conversationType' | 'title'> }) {
  const text = conversation.title.trim().slice(0, 1)
  return <Avatar icon={text ? undefined : conversation.conversationType === 'direct' ? <UserOutlined /> : <TeamOutlined />}>{text}</Avatar>
}

function MessageBubble({
  refCallback,
  item,
  mine,
  highlighted,
  onCreateIssue,
  onCreateDocument,
  onCopyLink,
  onEdit,
  onRevoke,
  onPin,
  onReact,
  onRetry,
  reactionPending,
}: {
  refCallback: (node: HTMLElement | null) => void
  item: ChatMessage
  mine: boolean
  highlighted: boolean
  onCreateIssue: () => void
  onCreateDocument: () => void
  onCopyLink: () => void
  onEdit: () => void
  onRevoke: () => void
  onPin: () => void
  onReact: (emoji: string) => void
  onRetry: () => void
  reactionPending: boolean
}) {
  const isLocal = Boolean(item.deliveryStatus)
  const contextItems = [
    {
      key: 'pin',
      icon: <PushpinOutlined />,
      label: item.pinnedAt ? '取消置顶' : '置顶',
      disabled: Boolean(item.revokedAt) || isLocal,
      onClick: onPin,
    },
    {
      key: 'issue',
      icon: <BugOutlined />,
      label: '转事项',
      disabled: Boolean(item.revokedAt) || isLocal,
      onClick: onCreateIssue,
    },
    {
      key: 'knowledge_content',
      icon: <FileTextOutlined />,
      label: '转知识内容',
      disabled: Boolean(item.revokedAt) || isLocal,
      onClick: onCreateDocument,
    },
    {
      key: 'copy-link',
      icon: <CopyOutlined />,
      label: '复制链接',
      disabled: Boolean(item.revokedAt) || isLocal,
      onClick: onCopyLink,
    },
    mine
      ? {
          key: 'edit',
          icon: <EditOutlined />,
          label: '编辑',
          disabled: Boolean(item.revokedAt) || isLocal,
          onClick: onEdit,
        }
      : null,
    mine
      ? {
          key: 'revoke',
          icon: <StopOutlined />,
          label: '删除',
          disabled: Boolean(item.revokedAt) || isLocal,
          danger: true,
          onClick: onRevoke,
        }
      : null,
  ].filter((item): item is NonNullable<typeof item> => Boolean(item))

  return (
    <article
      ref={refCallback}
      data-message-id={item.id}
      className={[mine ? 'im-message mine' : 'im-message', highlighted ? 'highlighted' : ''].join(' ')}
    >
      <Dropdown menu={{ items: contextItems }} trigger={['contextMenu']}>
        <div className="im-message-body">
          {!item.revokedAt && !isLocal ? (
            <div className="im-message-reaction-picker">
              {QUICK_REACTIONS.map((emoji) => (
                <button
                  key={emoji}
                  type="button"
                  disabled={reactionPending}
                  onClick={() => {
                    onReact(emoji)
                  }}
                >
                  {emoji}
                </button>
              ))}
            </div>
          ) : null}
          <Typography.Paragraph className="im-message-content">
            {item.revokedAt ? <Typography.Text type="secondary">{item.senderName}撤回一条消息</Typography.Text> : item.content}
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
                <button
                  key={reaction.emoji}
                  type="button"
                  className={reaction.reactedByMe ? 'im-message-reaction reacted' : 'im-message-reaction'}
                  disabled={reactionPending || Boolean(item.revokedAt)}
                  onClick={() => onReact(reaction.emoji)}
                >
                  {reaction.emoji} {reaction.count}
                </button>
              ))}
            </Space>
          ) : null}
          <div className="im-message-meta">
            {item.deliveryStatus === 'sending' ? <span>发送中</span> : null}
            {item.deliveryStatus === 'failed' ? (
              <button type="button" className="im-message-retry" onClick={onRetry}>
                发送失败，重试
              </button>
            ) : null}
            {item.editedAt ? <span>已编辑</span> : null}
            {item.pinnedAt ? <span>置顶</span> : null}
            <time>{formatMessageTime(item.createdAt)}</time>
          </div>
        </div>
      </Dropdown>
    </article>
  )
}

function isFirstMessageOfDay(previous: MessageSummary | undefined, current: MessageSummary) {
  if (!previous) {
    return true
  }
  return dayKey(previous.createdAt) !== dayKey(current.createdAt)
}

function dayKey(value: string) {
  const date = new Date(value)
  return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`
}

function formatMessageDate(value: string) {
  const date = new Date(value)
  const today = new Date()
  if (date.toDateString() === today.toDateString()) {
    return '今天'
  }
  return date.toLocaleDateString(undefined, { month: 'long', day: 'numeric' })
}

function formatMessageTime(value: string) {
  return new Date(value).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
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
