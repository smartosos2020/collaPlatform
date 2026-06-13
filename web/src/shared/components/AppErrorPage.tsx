import { Button, Result } from 'antd'
import { useNavigate, useParams } from 'react-router-dom'

type AppErrorPageProps = {
  stateOverride?: string
}

const errorCopy: Record<string, { status: '403' | '404' | '500'; title: string; subTitle: string }> = {
  '401': {
    status: '403',
    title: '需要重新登录',
    subTitle: '当前登录状态不可用，请重新登录后继续访问。',
  },
  '403': {
    status: '403',
    title: '无权限访问',
    subTitle: '你没有访问该对象或功能的权限。',
  },
  '404': {
    status: '404',
    title: '页面不存在',
    subTitle: '目标页面或对象没有找到。',
  },
  deleted: {
    status: '404',
    title: '对象已删除',
    subTitle: '目标对象已经被删除，无法继续访问。',
  },
  invalid: {
    status: '500',
    title: '链接无法识别',
    subTitle: '当前链接不是平台可识别的内部对象地址。',
  },
}

export function AppErrorPage({ stateOverride }: AppErrorPageProps) {
  const { state } = useParams()
  const navigate = useNavigate()
  const copy = errorCopy[stateOverride || state || '404'] ?? errorCopy['404']

  return (
    <div className="app-error-page">
      <Result
        status={copy.status}
        title={copy.title}
        subTitle={copy.subTitle}
        extra={[
          <Button key="home" type="primary" onClick={() => navigate('/')}>
            回到工作台
          </Button>,
          <Button key="back" onClick={() => navigate(-1)}>
            返回上一页
          </Button>,
        ]}
      />
    </div>
  )
}
