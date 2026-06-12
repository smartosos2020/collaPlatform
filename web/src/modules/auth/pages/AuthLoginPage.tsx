import { Button, Card, Form, Input, Typography } from 'antd'

export function AuthLoginPage() {
  return (
    <main className="login-page">
      <Card className="login-card">
        <Typography.Title level={3}>Colla Platform</Typography.Title>
        <Form layout="vertical">
          <Form.Item label="账号" name="username">
            <Input autoComplete="username" />
          </Form.Item>
          <Form.Item label="密码" name="password">
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Button type="primary" block>
            登录
          </Button>
        </Form>
      </Card>
    </main>
  )
}

