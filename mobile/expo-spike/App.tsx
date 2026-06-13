import AsyncStorage from '@react-native-async-storage/async-storage'
import * as Linking from 'expo-linking'
import * as Notifications from 'expo-notifications'
import { useEffect, useMemo, useState } from 'react'
import { Button, FlatList, SafeAreaView, Text, TextInput, View } from 'react-native'

import {
  getIssue,
  getApproval,
  approveApproval,
  listConversations,
  listApprovalTodos,
  listNotifications,
  login,
  rejectApproval,
  registerPushToken,
  type ApprovalInstanceDetail,
  type ApprovalTaskSummary,
  type AuthTokens,
  type ConversationSummary,
  type IssueDetail,
  type NotificationItem,
} from './src/api'

const TOKEN_KEY = 'colla.mobile.tokens'
const DEVICE_FINGERPRINT_KEY = 'colla.mobile.deviceFingerprint'

export default function App() {
  const [tokens, setTokens] = useState<AuthTokens | null>(null)
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('admin123456')
  const [conversations, setConversations] = useState<ConversationSummary[]>([])
  const [notifications, setNotifications] = useState<NotificationItem[]>([])
  const [approvalTodos, setApprovalTodos] = useState<ApprovalTaskSummary[]>([])
  const [issue, setIssue] = useState<IssueDetail | null>(null)
  const [approval, setApproval] = useState<ApprovalInstanceDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const unreadTotal = useMemo(
    () => conversations.reduce((total, conversation) => total + conversation.unreadCount, 0),
    [conversations],
  )

  useEffect(() => {
    AsyncStorage.getItem(TOKEN_KEY).then((value) => {
      if (value) {
        setTokens(JSON.parse(value) as AuthTokens)
      }
    })
  }, [])

  useEffect(() => {
    const subscription = Linking.addEventListener('url', ({ url }) => {
      void openDeepLink(url)
    })
    Linking.getInitialURL().then((url) => {
      if (url) {
        void openDeepLink(url)
      }
    })
    return () => subscription.remove()
  }, [tokens])

  async function submitLogin() {
    setError(null)
    const deviceFingerprint = await getDeviceFingerprint()
    const nextTokens = await login(username, password, deviceFingerprint)
    await AsyncStorage.setItem(TOKEN_KEY, JSON.stringify(nextTokens))
    setTokens(nextTokens)
    await registerPushToken(nextTokens.accessToken, nextTokens.deviceId, `fake-mobile-${deviceFingerprint}`)
    await refresh(nextTokens.accessToken)
  }

  async function refresh(accessToken = tokens?.accessToken) {
    if (!accessToken) {
      return
    }
    setError(null)
    try {
      const [nextConversations, nextNotifications, nextApprovalTodos] = await Promise.all([
        listConversations(accessToken),
        listNotifications(accessToken),
        listApprovalTodos(accessToken),
      ])
      setConversations(nextConversations)
      setNotifications(nextNotifications)
      setApprovalTodos(nextApprovalTodos)
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : '刷新失败')
    }
  }

  async function openDeepLink(url: string) {
    if (!tokens) {
      return
    }
    const match = url.match(/^colla:\/\/issue\/([0-9a-fA-F-]{36})/)
    if (match) {
      setIssue(await getIssue(tokens.accessToken, match[1]))
    }
    const approvalMatch = url.match(/^colla:\/\/approval\/([0-9a-fA-F-]{36})/)
    if (approvalMatch) {
      setApproval(await getApproval(tokens.accessToken, approvalMatch[1]))
    }
  }

  async function openApproval(approvalId: string) {
    if (!tokens) {
      return
    }
    setApproval(await getApproval(tokens.accessToken, approvalId))
  }

  async function actApproval(action: 'approve' | 'reject', approvalId: string) {
    if (!tokens) {
      return
    }
    const nextApproval = action === 'approve'
      ? await approveApproval(tokens.accessToken, approvalId)
      : await rejectApproval(tokens.accessToken, approvalId)
    setApproval(nextApproval)
    await refresh(tokens.accessToken)
  }

  if (!tokens) {
    return (
      <SafeAreaView style={{ padding: 20, gap: 12 }}>
        <Text style={{ fontSize: 24, fontWeight: '700' }}>Colla Mobile</Text>
        <TextInput value={username} onChangeText={setUsername} placeholder="账号" style={inputStyle} />
        <TextInput value={password} onChangeText={setPassword} placeholder="密码" secureTextEntry style={inputStyle} />
        <Button title="登录" onPress={() => void submitLogin()} />
        {error ? <Text>{error}</Text> : null}
      </SafeAreaView>
    )
  }

  return (
    <SafeAreaView style={{ flex: 1, padding: 20, gap: 12 }}>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
        <Text style={{ fontSize: 22, fontWeight: '700' }}>Colla</Text>
        <Button title="补拉" onPress={() => void refresh()} />
      </View>
      <Text>未读消息 {unreadTotal}，通知 {notifications.length}，审批待办 {approvalTodos.length}</Text>
      {error ? <Text>{error}</Text> : null}
      {issue ? (
        <View style={panelStyle}>
          <Text style={{ fontWeight: '700' }}>{issue.issue.issueKey}</Text>
          <Text>{issue.issue.title}</Text>
          <Text>{issue.issue.status} / {issue.issue.priority}</Text>
        </View>
      ) : null}
      {approval ? (
        <View style={panelStyle}>
          <Text style={{ fontWeight: '700' }}>{approval.instance.title}</Text>
          <Text>{approval.instance.formName} / {approval.instance.applicantName}</Text>
          <Text>{approval.instance.status}</Text>
          {approval.instance.status === 'pending' ? (
            <View style={{ flexDirection: 'row', gap: 8 }}>
              <Button title="通过" onPress={() => void actApproval('approve', approval.instance.id)} />
              <Button title="拒绝" onPress={() => void actApproval('reject', approval.instance.id)} />
            </View>
          ) : null}
        </View>
      ) : null}
      <Text style={{ fontWeight: '700' }}>审批待办</Text>
      <FlatList
        data={approvalTodos}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => (
          <View style={panelStyle}>
            <Text>{item.instanceTitle}</Text>
            <Text>{item.formName} / {item.applicantName}</Text>
            <Button title="打开" onPress={() => void openApproval(item.instanceId)} />
          </View>
        )}
      />
      <Text style={{ fontWeight: '700' }}>会话</Text>
      <FlatList
        data={conversations}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => (
          <View style={panelStyle}>
            <Text>{item.title}</Text>
            <Text>未读 {item.unreadCount}</Text>
            <Text>{item.lastMessage?.content}</Text>
          </View>
        )}
      />
      <Text style={{ fontWeight: '700' }}>通知</Text>
      <FlatList
        data={notifications}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => (
          <View style={panelStyle}>
            <Text>{item.title}</Text>
            <Text>{item.body}</Text>
          </View>
        )}
      />
    </SafeAreaView>
  )
}

async function getDeviceFingerprint() {
  const existing = await AsyncStorage.getItem(DEVICE_FINGERPRINT_KEY)
  if (existing) {
    return existing
  }
  const value = `${Date.now()}-${Math.random().toString(16).slice(2)}`
  await AsyncStorage.setItem(DEVICE_FINGERPRINT_KEY, value)
  return value
}

const inputStyle = {
  borderWidth: 1,
  borderColor: '#d0d5dd',
  borderRadius: 8,
  padding: 10,
}

const panelStyle = {
  borderWidth: 1,
  borderColor: '#e5e7eb',
  borderRadius: 8,
  padding: 10,
  marginBottom: 8,
}
