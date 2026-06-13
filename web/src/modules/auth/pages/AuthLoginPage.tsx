import { useMutation } from '@tanstack/react-query'
import { Alert, Button, Card, Form, Input, Typography } from 'antd'
import { useLocation, useNavigate } from 'react-router-dom'

import { login } from '../api/authApi'
import { useAuthStore } from '../authStore'

export function AuthLoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const setTokens = useAuthStore((state) => state.setTokens)
  const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? '/'

  const loginMutation = useMutation({
    mutationFn: login,
    onSuccess: (tokens) => {
      setTokens(tokens.accessToken, tokens.refreshToken)
      navigate(from, { replace: true })
    },
  })

  return (
    <main className="login-page">
      <Card className="login-card">
        <Typography.Title level={3}>Colla Platform</Typography.Title>
        {loginMutation.isError ? (
          <Alert type="error" showIcon title="登录失败" description="请检查账号和密码。" />
        ) : null}
        <Form layout="vertical" onFinish={(values) => loginMutation.mutate(values)}>
          <Form.Item label="账号" name="username" rules={[{ required: true, message: '请输入账号' }]}>
            <Input autoComplete="username" />
          </Form.Item>
          <Form.Item label="密码" name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loginMutation.isPending}>
            登录
          </Button>
        </Form>
      </Card>
    </main>
  )
}
