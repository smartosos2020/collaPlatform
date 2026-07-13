import {
  CheckCircleOutlined,
  LockOutlined,
  MailOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  UploadOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Avatar, Button, Card, Col, Descriptions, Divider, Form, Input, Row, Space, Switch, Tag, Typography, Upload } from 'antd'
import { useEffect, useMemo, useState } from 'react'

import { changePassword, updateProfile } from '../api/authApi'
import { useAuthStore } from '../authStore'
import { completeUpload, createUploadUrl, getFileDownloadUrl } from '../../files/api/filesApi'
import {
  listNotificationPreferences,
  updateNotificationPreference,
} from '../../notifications/api/notificationsApi'
import { listDevices } from '../../devices/api/devicesApi'

type ProfileForm = {
  displayName: string
  email?: string
}

type PasswordForm = {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

const WORK_PREFERENCES_KEY = 'colla.userWorkPreferences'
const defaultWorkPreferences = {
  startPage: '/',
  compactCards: false,
  openLinksInNewTab: false,
}

export function UserSettingsPage() {
  const queryClient = useQueryClient()
  const currentUser = useAuthStore((state) => state.currentUser)
  const setCurrentUser = useAuthStore((state) => state.setCurrentUser)
  const [profileForm] = Form.useForm<ProfileForm>()
  const [passwordForm] = Form.useForm<PasswordForm>()
  const [workPreferences, setWorkPreferences] = useState(loadWorkPreferences)

  const preferencesQuery = useQuery({
    queryKey: ['notifications', 'preferences'],
    queryFn: listNotificationPreferences,
  })
  const devicesQuery = useQuery({
    queryKey: ['devices'],
    queryFn: listDevices,
  })
  const avatarQuery = useQuery({
    queryKey: ['files', currentUser?.avatarFileId, 'download-url'],
    queryFn: () => getFileDownloadUrl(currentUser?.avatarFileId as string),
    enabled: Boolean(currentUser?.avatarFileId),
    retry: false,
  })

  useEffect(() => {
    if (currentUser) {
      profileForm.setFieldsValue({ displayName: currentUser.displayName, email: currentUser.email ?? '' })
    }
  }, [currentUser, profileForm])

  const profileMutation = useMutation({
    mutationFn: (values: ProfileForm) => updateProfile({ ...values, avatarFileId: currentUser?.avatarFileId ?? null }),
    onSuccess: (updated) => {
      setCurrentUser(updated)
      queryClient.setQueryData(['auth', 'me'], updated)
    },
  })
  const passwordMutation = useMutation({
    mutationFn: changePassword,
    onSuccess: () => passwordForm.resetFields(),
  })
  const avatarMutation = useMutation({
    mutationFn: async (file: File) => {
      const upload = await createUploadUrl({
        fileName: file.name,
        contentType: file.type || 'image/png',
        sizeBytes: file.size,
      })
      await fetch(upload.uploadUrl, { method: 'PUT', headers: upload.headers, body: file })
      return completeUpload({ fileId: upload.uploadId })
    },
    onSuccess: async (file) => {
      const updated = await updateProfile({
        displayName: currentUser?.displayName ?? '',
        email: currentUser?.email ?? '',
        avatarFileId: file.id,
      })
      setCurrentUser(updated)
      queryClient.setQueryData(['auth', 'me'], updated)
      await queryClient.invalidateQueries({ queryKey: ['files', file.id, 'download-url'] })
    },
  })
  const preferenceMutation = useMutation({
    mutationFn: ({ sourceType, enabled }: { sourceType: string; enabled: boolean }) => updateNotificationPreference(sourceType, enabled),
    onSuccess: (preferences) => queryClient.setQueryData(['notifications', 'preferences'], preferences),
  })

  const notificationPreferences = preferencesQuery.data ?? []
  const currentDevice = useMemo(() => (devicesQuery.data ?? []).find((device) => device.current), [devicesQuery.data])

  const saveWorkPreference = <K extends keyof typeof defaultWorkPreferences>(key: K, value: (typeof defaultWorkPreferences)[K]) => {
    const next = { ...workPreferences, [key]: value }
    setWorkPreferences(next)
    localStorage.setItem(WORK_PREFERENCES_KEY, JSON.stringify(next))
  }

  if (!currentUser) {
    return <Alert type="warning" showIcon message="个人资料暂不可用" description="请重新加载页面后再试。" />
  }

  return (
    <div className="page-stack user-settings-page" data-testid="user-settings-page">
      <Space className="page-toolbar" align="start">
        <div>
          <Typography.Title level={2}>个人设置</Typography.Title>
          <Typography.Text type="secondary">维护资料、通知和工作方式；安全操作会单独记录。</Typography.Text>
        </div>
      </Space>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={15}>
          <Card title={<Space><UserOutlined />基本资料</Space>}>
            <Space align="start" size={20} className="user-settings-profile-header">
              <Avatar size={72} src={avatarQuery.data?.downloadUrl} icon={<UserOutlined />}>{userInitial(currentUser.displayName || currentUser.username)}</Avatar>
              <Space orientation="vertical" size={4}>
                <Typography.Text strong>{currentUser.displayName || currentUser.username}</Typography.Text>
                <Typography.Text type="secondary">账号：{currentUser.username}</Typography.Text>
                <Upload
                  accept="image/png,image/jpeg,image/webp"
                  showUploadList={false}
                  beforeUpload={(file) => {
                    avatarMutation.mutate(file)
                    return false
                  }}
                >
                  <Button size="small" icon={<UploadOutlined />} loading={avatarMutation.isPending}>更新头像</Button>
                </Upload>
              </Space>
            </Space>
            <Divider />
            <Form form={profileForm} layout="vertical" onFinish={(values) => profileMutation.mutate(values)}>
              <Form.Item label="显示名称" name="displayName" rules={[{ required: true, message: '请输入显示名称' }]}>
                <Input aria-label="显示名称" maxLength={64} />
              </Form.Item>
              <Form.Item label="邮箱" name="email" rules={[{ type: 'email', message: '请输入有效邮箱' }]}>
                <Input aria-label="邮箱" prefix={<MailOutlined />} maxLength={128} />
              </Form.Item>
              <Button type="primary" htmlType="submit" loading={profileMutation.isPending} icon={<CheckCircleOutlined />}>
                保存资料
              </Button>
              {profileMutation.isSuccess ? <Tag color="green" className="user-settings-save-state">已保存</Tag> : null}
              {profileMutation.isError ? <Alert type="error" showIcon message="资料保存失败" description="请检查网络后重试。" className="user-settings-inline-alert" /> : null}
            </Form>
          </Card>
        </Col>

        <Col xs={24} xl={9}>
          <Card title={<Space><SafetyCertificateOutlined />账号安全</Space>}>
            <Descriptions column={1} size="small">
              <Descriptions.Item label="账号状态"><Tag color="green">正常</Tag></Descriptions.Item>
              <Descriptions.Item label="当前设备">{currentDevice?.deviceName || currentDevice?.deviceType || '当前浏览器'}</Descriptions.Item>
              <Descriptions.Item label="登录设备">{devicesQuery.isLoading ? '加载中…' : `${(devicesQuery.data ?? []).length} 台`}</Descriptions.Item>
            </Descriptions>
            <Button type="link" href="#password" icon={<LockOutlined />}>修改密码</Button>
            <Button type="link" href="/devices" icon={<SettingOutlined />}>管理登录设备</Button>
          </Card>
        </Col>
      </Row>

      <Card title={<Space><SettingOutlined />通知偏好</Space>}>
        {preferencesQuery.isError ? <Alert type="error" showIcon message="通知偏好加载失败" description="保留默认通知设置，稍后可重试。" /> : null}
        <Space orientation="vertical" className="user-settings-preferences" size={12}>
          {notificationPreferences.map((preference) => (
            <div className="user-settings-preference-row" key={preference.sourceType}>
              <div>
                <Typography.Text strong>{notificationSourceText[preference.sourceType] ?? preference.sourceType}</Typography.Text>
                <Typography.Text type="secondary">{preference.required ? '必要通知始终送达' : '可按需关闭，设置立即生效'}</Typography.Text>
              </div>
              <Switch
                checked={preference.enabled}
                disabled={preference.required || preferenceMutation.isPending}
                loading={preferenceMutation.isPending && preferenceMutation.variables?.sourceType === preference.sourceType}
                onChange={(enabled) => preferenceMutation.mutate({ sourceType: preference.sourceType, enabled })}
                aria-label={`${notificationSourceText[preference.sourceType] ?? preference.sourceType}通知`}
              />
            </div>
          ))}
          {preferencesQuery.isLoading ? <Typography.Text type="secondary">正在加载通知偏好…</Typography.Text> : null}
          {!preferencesQuery.isLoading && notificationPreferences.length === 0 ? <Typography.Text type="secondary">已使用默认通知设置。</Typography.Text> : null}
        </Space>
      </Card>

      <Card title={<Space><SettingOutlined />工作偏好</Space>}>
        <Space orientation="vertical" className="user-settings-preferences" size={12}>
          <div className="user-settings-preference-row">
            <div><Typography.Text strong>打开后的默认页面</Typography.Text><Typography.Text type="secondary">下次进入系统时直接回到工作台。</Typography.Text></div>
            <Tag color="blue">工作台</Tag>
          </div>
          <div className="user-settings-preference-row">
            <div><Typography.Text strong>紧凑卡片</Typography.Text><Typography.Text type="secondary">减少列表留白，设置立即应用到当前浏览器。</Typography.Text></div>
            <Switch checked={workPreferences.compactCards} onChange={(value) => saveWorkPreference('compactCards', value)} aria-label="紧凑卡片" />
          </div>
          <div className="user-settings-preference-row">
            <div><Typography.Text strong>对象链接在新标签页打开</Typography.Text><Typography.Text type="secondary">保留当前任务上下文。</Typography.Text></div>
            <Switch checked={workPreferences.openLinksInNewTab} onChange={(value) => saveWorkPreference('openLinksInNewTab', value)} aria-label="对象链接在新标签页打开" />
          </div>
        </Space>
      </Card>

      <Card id="password" title={<Space><LockOutlined />修改密码</Space>}>
        <Form form={passwordForm} layout="vertical" onFinish={({ currentPassword, newPassword }) => passwordMutation.mutate({ currentPassword, newPassword })}>
          <Row gutter={16}>
            <Col xs={24} md={8}><Form.Item label="当前密码" name="currentPassword" rules={[{ required: true, message: '请输入当前密码' }]}><Input.Password autoComplete="current-password" /></Form.Item></Col>
            <Col xs={24} md={8}><Form.Item label="新密码" name="newPassword" rules={[{ required: true, min: 8, message: '至少 8 个字符' }]}><Input.Password autoComplete="new-password" /></Form.Item></Col>
            <Col xs={24} md={8}><Form.Item label="确认新密码" name="confirmPassword" dependencies={['newPassword']} rules={[{ required: true, message: '请再次输入新密码' }, ({ getFieldValue }) => ({ validator(_, value) { return !value || getFieldValue('newPassword') === value ? Promise.resolve() : Promise.reject(new Error('两次密码不一致')) } })]}><Input.Password autoComplete="new-password" /></Form.Item></Col>
          </Row>
          <Button htmlType="submit" loading={passwordMutation.isPending}>更新密码</Button>
          {passwordMutation.isSuccess ? <Tag color="green" className="user-settings-save-state">密码已更新</Tag> : null}
          {passwordMutation.isError ? <Alert type="error" showIcon message="密码更新失败" description="当前密码不正确或新密码不符合要求。" className="user-settings-inline-alert" /> : null}
        </Form>
      </Card>
    </div>
  )
}

const notificationSourceText: Record<string, string> = {
  im: '消息与提及',
  project: '项目动态',
  knowledge: '知识内容',
  base: '表格动态',
  approval: '审批进度',
  resource: '权限与安全',
  system: '系统通知',
}

function loadWorkPreferences() {
  try {
    const stored = JSON.parse(localStorage.getItem(WORK_PREFERENCES_KEY) ?? '{}') as Partial<typeof defaultWorkPreferences>
    return { ...defaultWorkPreferences, ...stored }
  } catch {
    return defaultWorkPreferences
  }
}

function userInitial(value?: string) {
  return (value || 'U').trim().slice(0, 1).toUpperCase()
}
