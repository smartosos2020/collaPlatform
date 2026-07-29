import {
  ApartmentOutlined,
  CheckCircleOutlined,
  CloudSyncOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  Input,
  List,
  Row,
  Space,
  Tag,
  Typography,
} from 'antd'
import { useEffect, useMemo, useState } from 'react'

import { useRealtimeSubscription } from '../../../shared/realtime'
import {
  getScenarioTemplateFoundation,
  getScenarioInstallation,
  runScenarioCommand,
  scenarioTemplateKeys,
  type ScenarioInstallResult,
  validateScenarioTemplate,
  type ScenarioTemplate,
} from '../api/scenarioTemplatesApi'
import type { UserProjectSpace } from '../api/projectSpacesApi'

const kindLabels: Record<string, string> = {
  work_item_type: '工作项类型',
  relation: '关系',
  saved_view: '保存视图',
  board: '看板',
  project_plan: '项目计划',
  workflow: '流程',
  calendar: '日历',
  automation: '自动化',
  notification: '受控通知',
  risk_policy: '风险策略',
  metric: '指标',
  dashboard: '仪表盘',
}

export function ScenarioTemplatesPanel({ space }: { space: UserProjectSpace }) {
  const client = useQueryClient()
  const [online, setOnline] = useState(() => navigator.onLine)
  const [selectedKey, setSelectedKey] = useState('development')
  const [localManifestHash, setLocalManifestHash] = useState('')
  const [lastRun, setLastRun] = useState<ScenarioInstallResult | null>(null)
  const query = useQuery({
    queryKey: scenarioTemplateKeys.foundation(space.id),
    queryFn: () => getScenarioTemplateFoundation(space.id),
    retry: false,
  })
  const selected = useMemo(
    () => query.data?.templates.find(template => template.scenarioKey === selectedKey)
      ?? query.data?.templates[0],
    [query.data?.templates, selectedKey],
  )
  const validation = useQuery({
    queryKey: scenarioTemplateKeys.validation(space.id, selected?.scenarioKey ?? ''),
    queryFn: () => validateScenarioTemplate(space.id, selected!.scenarioKey),
    enabled: Boolean(selected && online),
    retry: false,
  })
  const canManage = ['owner', 'admin'].includes(space.currentUserRole ?? '')
  const installation = useQuery({
    queryKey: [...scenarioTemplateKeys.foundation(space.id), selected?.scenarioKey, 'installation'],
    queryFn: () => getScenarioInstallation(space.id, selected!.scenarioKey),
    enabled: Boolean(selected && online && canManage),
    retry: false,
  })
  const command = useMutation({
    mutationFn: (input: {
      operation: 'dry-run' | 'install' | 'retry' | 'upgrade' | 'detach'
      localHash?: string
      resolutions?: Record<string, string>
    }) => runScenarioCommand(space.id, selected!.scenarioKey, input.operation, {
      requestId: crypto.randomUUID(),
      localManifestHash: input.localHash,
      conflictResolutions: input.resolutions,
    }),
    onSuccess: (result) => {
      setLastRun(result)
      void installation.refetch()
    },
  })

  useRealtimeSubscription(['project_space.changed'], (signal) => {
    if (signal.objectType === 'project_space' && signal.objectId === space.id) {
      void client.invalidateQueries({ queryKey: scenarioTemplateKeys.foundation(space.id) })
    }
  })

  useEffect(() => {
    const calibrate = () => {
      setOnline(navigator.onLine)
      if (navigator.onLine) {
        void client.invalidateQueries({ queryKey: scenarioTemplateKeys.foundation(space.id) })
      }
    }
    window.addEventListener('online', calibrate)
    window.addEventListener('offline', calibrate)
    window.addEventListener('focus', calibrate)
    return () => {
      window.removeEventListener('online', calibrate)
      window.removeEventListener('offline', calibrate)
      window.removeEventListener('focus', calibrate)
    }
  }, [client, space.id])

  return (
    <Card
      className="content-card scenario-templates-panel"
      data-testid="scenario-templates-panel"
      title={<Space><ApartmentOutlined />场景模板目录</Space>}
      extra={(
        <Tag icon={<CloudSyncOutlined />} color={online ? 'green' : 'orange'}>
          {online ? 'REST 已校准' : '离线只读'}
        </Tag>
      )}
    >
      {!online ? <Alert type="warning" showIcon message="当前离线：目录可保留查看，但不能验证或安装模板" /> : null}
      {query.isError ? <Alert type="error" showIcon message="场景模板不可用或无权访问" /> : null}
      {query.data?.truncated ? <Alert type="warning" showIcon message="模板目录已截断；不得视为完整目录" /> : null}
      <Row gutter={[16, 16]}>
        <Col xs={24} md={7}>
          <List
            aria-label="场景模板"
            loading={query.isLoading}
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无场景模板" /> }}
            dataSource={query.data?.templates ?? []}
            renderItem={(template) => (
              <List.Item>
                <Button
                  type={selected?.scenarioKey === template.scenarioKey ? 'primary' : 'text'}
                  block
                  className="scenario-template-selector"
                  onClick={() => setSelectedKey(template.scenarioKey)}
                >
                  <span>{template.name}</span>
                  <small>v{template.currentVersion.versionNumber}</small>
                </Button>
              </List.Item>
            )}
          />
          <Typography.Paragraph type="secondary">
            目录和预览不授权；安装将在执行时重新校准空间角色及每个 owner capability。
          </Typography.Paragraph>
        </Col>
        <Col xs={24} md={17}>
          {selected ? (
            <>
              <ScenarioDetail template={selected} validation={validation.data} />
              {canManage ? (
                <div className="scenario-install-workbench" data-testid="scenario-install-workbench">
                  <Typography.Title level={5}>安装与升级</Typography.Title>
                  <Input
                    aria-label="本地清单指纹"
                    value={localManifestHash}
                    placeholder={selected.currentVersion.manifestHash}
                    onChange={event => setLocalManifestHash(event.target.value.trim())}
                  />
                  <Space wrap>
                    <Button
                      disabled={!online}
                      loading={command.isPending}
                      onClick={() => command.mutate({ operation: 'dry-run' })}
                    >
                      预检
                    </Button>
                    <Button
                      type="primary"
                      disabled={!online}
                      loading={command.isPending}
                      onClick={() => command.mutate({ operation: 'install' })}
                    >
                      安装模板
                    </Button>
                    <Button
                      disabled={!online || !installation.data}
                      onClick={() => command.mutate({
                        operation: 'upgrade',
                        localHash: localManifestHash || selected.currentVersion.manifestHash,
                      })}
                    >
                      检查升级
                    </Button>
                    <Button
                      disabled={!online || !lastRun?.conflicts.length}
                      onClick={() => command.mutate({
                        operation: 'upgrade',
                        localHash: localManifestHash || selected.currentVersion.manifestHash,
                        resolutions: { local_manifest: 'local' },
                      })}
                    >
                      保留本地并升级
                    </Button>
                    <Button
                      danger
                      disabled={!online || !installation.data}
                      onClick={() => command.mutate({ operation: 'detach' })}
                    >
                      解绑引用
                    </Button>
                  </Space>
                  {command.isError ? (
                    <Alert type="error" showIcon message="安装命令失败；未伪造成功，可安全重试" />
                  ) : null}
                  {lastRun ? <ScenarioRunResult result={lastRun} /> : null}
                </div>
              ) : (
                <Alert type="info" showIcon message="仅空间 owner/admin 可执行安装；目录预览不授权" />
              )}
            </>
          ) : null}
        </Col>
      </Row>
    </Card>
  )
}

function ScenarioRunResult({ result }: { result: ScenarioInstallResult }) {
  return (
    <div className="scenario-run-result">
      <Alert
        type={result.status === 'completed' ? 'success' : 'warning'}
        showIcon
        message={`${result.operation} · ${result.status}${result.replayed ? ' · 精确重放' : ''}`}
        description={`运行 ${result.runId}；${result.steps.length} 个步骤；本地 ${result.localManifestHash.slice(0, 12)}`}
      />
      {result.conflicts.map(conflict => (
        <Alert
          key={conflict.keyPath}
          type={conflict.resolved ? 'success' : 'error'}
          showIcon
          message={`${conflict.reason} · ${conflict.keyPath}`}
          description={conflict.resolved ? `已选择 ${conflict.resolution}` : '未决冲突阻止升级'}
        />
      ))}
      <List
        size="small"
        aria-label="安装步骤"
        dataSource={result.steps}
        renderItem={step => (
          <List.Item>
            <Space wrap>
              <Tag color={step.status === 'completed' ? 'green' : step.status === 'planned' ? 'blue' : 'orange'}>
                {step.status}
              </Tag>
              <span>{step.componentKey}</span>
              <Typography.Text type="secondary">{step.operation} · {step.ownerContract}</Typography.Text>
            </Space>
          </List.Item>
        )}
      />
    </div>
  )
}

function ScenarioDetail({
  template,
  validation,
}: {
  template: ScenarioTemplate
  validation?: {
    valid: boolean
    manifestHash: string
    installationOrder: string[]
    diagnostics: Array<{ code: string; componentKey: string; message: string }>
  }
}) {
  const manifest = template.currentVersion.manifest
  return (
    <div className="scenario-template-detail">
      <div className="scenario-template-heading">
        <div>
          <Typography.Title level={4}>{template.name}</Typography.Title>
          <Typography.Paragraph>{template.description}</Typography.Paragraph>
        </div>
        <Space wrap>
          <Tag color="blue">{template.currentVersion.catalogVersion}</Tag>
          <Tag>{manifest.components.length} 个组件</Tag>
          <Tag
            icon={validation?.valid ? <CheckCircleOutlined /> : <SafetyCertificateOutlined />}
            color={validation?.valid ? 'green' : 'default'}
          >
            {validation?.valid ? '清单有效' : '等待验证'}
          </Tag>
        </Space>
      </div>
      <Descriptions
        size="small"
        bordered
        column={{ xs: 1, sm: 2 }}
        items={[
          { key: 'scenario', label: '场景 Key', children: template.scenarioKey },
          { key: 'hash', label: '清单指纹', children: template.currentVersion.manifestHash.slice(0, 16) },
          { key: 'schema', label: 'Schema', children: `v${template.currentVersion.schemaVersion}` },
          { key: 'capabilities', label: '公共能力', children: manifest.capabilities.length },
        ]}
      />
      <List
        className="scenario-component-list"
        aria-label={`${template.name}组件`}
        dataSource={manifest.components}
        renderItem={(component, index) => (
          <List.Item>
            <List.Item.Meta
              avatar={<span className="scenario-component-order">{index + 1}</span>}
              title={(
                <Space wrap>
                  <strong>{component.description}</strong>
                  <Tag>{kindLabels[component.kind] ?? component.kind}</Tag>
                  {component.required ? <Tag color="red">必需</Tag> : null}
                </Space>
              )}
              description={(
                <Space direction="vertical" size={2}>
                  <span>{component.componentKey} · {component.ownerContract}</span>
                  {component.dependencies.length
                    ? <span>依赖：{component.dependencies.join('、')}</span>
                    : <span>无前置依赖</span>}
                </Space>
              )}
            />
          </List.Item>
        )}
      />
      {validation?.valid ? (
        <Alert
          type="success"
          showIcon
          message="依赖拓扑验证通过"
          description={`确定性顺序：${validation.installationOrder.join(' → ')}`}
        />
      ) : null}
      {validation && !validation.valid ? (
        <Alert
          type="error"
          showIcon
          message="清单验证失败"
          description={validation.diagnostics.map(item => `${item.code}: ${item.componentKey}`).join('；')}
        />
      ) : null}
    </div>
  )
}
